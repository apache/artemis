/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.protocol.core.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.protocol.core.CommandConfirmationHandler;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.ResponseHandler;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.PacketsConfirmedMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.ConcurrentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.ADDRESSING_CHANGE_VERSION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.ARTEMIS_2_18_0_VERSION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.ARTEMIS_2_29_0_VERSION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.ARTEMIS_2_37_0_VERSION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CLUSTER_TOPOLOGY_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CLUSTER_TOPOLOGY_V3;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CLUSTER_TOPOLOGY_V4;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATESESSION_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DISCONNECT_CONSUMER;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DISCONNECT_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DISCONNECT_V3;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.EXCEPTION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.PACKETS_CONFIRMED;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY_RESP_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY_RESP_V3;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY_RESP_V4;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY_RESP_V5;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_QUEUEQUERY_RESP_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_QUEUEQUERY_RESP_V3;

public final class ChannelImpl implements Channel {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public enum CHANNEL_ID {
      /**
       * Used for core protocol management.
       */
      PING(0),
      /**
       * Session creation and attachment.
       */
      SESSION(1),
      /**
       * Replication, i.e. for backups that do not share the journal.
       */
      REPLICATION(2),
      /**
       * cluster used for controlling nodes in a cluster remotely
       */
      CLUSTER(3),

      FEDERATION(4),

      /**
       * Channels [0-9] are reserved for the system, user channels must be greater than that.
       */
      USER(10);

      public final long id;

      CHANNEL_ID(long id) {
         this.id = id;
      }

      protected static String idToString(long code) {
         for (CHANNEL_ID channel : EnumSet.allOf(CHANNEL_ID.class)) {
            if (channel.id == code) {
               return channel.toString();
            }
         }
         return Long.toString(code);
      }
   }

   private volatile long id;

   private final AtomicInteger reconnectID = new AtomicInteger(0);

   private ChannelHandler handler;

   private Packet response;

   private final java.util.Queue<Packet> resendCache;

   private final ResponseCache responseAsyncCache;

   private int firstStoredCommandID;

   private final AtomicInteger lastConfirmedCommandID = new AtomicInteger(-1);

   private volatile CoreRemotingConnection connection;

   private volatile boolean closed;

   private final Lock lock = new ReentrantLock();

   private final Condition sendCondition = lock.newCondition();

   private final Condition failoverCondition = lock.newCondition();

   private final Object sendLock = new Object();

   private final Object sendBlockingLock = new Object();

   private boolean failingOver;

   private final int confWindowSize;

   private int receivedBytes;

   private CommandConfirmationHandler commandConfirmationHandler;

   private volatile boolean transferring;

   private final List<Interceptor> interceptors;

   private final AtomicLong blockingCorrelationID = new AtomicLong(-1);

   public ChannelImpl(final CoreRemotingConnection connection,
                      final long id,
                      final int confWindowSize,
                      final List<Interceptor> interceptors) {
      this.connection = connection;

      this.id = id;

      this.confWindowSize = confWindowSize;

      if (confWindowSize != -1) {
         resendCache = new ConcurrentLinkedQueue<>();
         responseAsyncCache = new ResponseCache();
      } else {
         resendCache = null;
         responseAsyncCache = null;
      }

      this.interceptors = interceptors;
   }

   @Override
   public int getReconnectID() {
      return reconnectID.get();
   }

   @Override
   public boolean supports(final byte packetType) {
      return supports(packetType, connection.getChannelVersion());
   }

   @Override
   public boolean supports(final byte packetType, int version) {
      return switch (packetType) {
         case CLUSTER_TOPOLOGY_V2 -> version >= 122;
         case DISCONNECT_CONSUMER -> version >= 124;
         case CLUSTER_TOPOLOGY_V3, DISCONNECT_V2 -> version >= 125;
         case SESS_QUEUEQUERY_RESP_V2, SESS_BINDINGQUERY_RESP_V2 -> version >= 126;
         case SESS_BINDINGQUERY_RESP_V3 -> version >= 127;
         case SESS_QUEUEQUERY_RESP_V3, SESS_BINDINGQUERY_RESP_V4 -> version >= ADDRESSING_CHANGE_VERSION;
         case CLUSTER_TOPOLOGY_V4, CREATESESSION_V2, DISCONNECT_V3 -> version >= ARTEMIS_2_18_0_VERSION;
         case SESS_BINDINGQUERY_RESP_V5 -> version >= ARTEMIS_2_29_0_VERSION;
         default -> true;
      };
   }

   @Override
   public boolean requireSpecialVotingHandling() {
      return connection.getChannelVersion() < ARTEMIS_2_37_0_VERSION;
   }

   @Override
   public long getID() {
      return id;
   }

   @Override
   public int getLastConfirmedCommandID() {
      return lastConfirmedCommandID.get();
   }

   @Override
   public Lock getLock() {
      return lock;
   }

   @Override
   public int getConfirmationWindowSize() {
      return confWindowSize;
   }

   @Override
   public void returnBlocking() {
      returnBlocking(null);
   }

   @Override
   public void returnBlocking(Throwable cause) {
      lock.lock();

      try {
         ActiveMQException activeMQException = ActiveMQClientMessageBundle.BUNDLE.unblockingACall(cause);
         if (responseAsyncCache != null) {
            responseAsyncCache.errorAll(activeMQException);
         }
         response = new ActiveMQExceptionMessage(activeMQException);

         sendCondition.signal();
      } finally {
         lock.unlock();
      }
   }

   @Override
   public void flushConnection() {
      connection.getTransportConnection().flush();
   }

   @Override
   public boolean send(Packet packet, boolean flushConnection) {
      if (invokeInterceptors(packet, interceptors, connection) != null) {
         return false;
      }

      final ResponseCache responseAsyncCache = this.responseAsyncCache;

      synchronized (sendLock) {
         packet.setChannelID(id);

         if (responseAsyncCache != null && packet.isRequiresResponse() && packet.isResponseAsync()) {
            packet.setCorrelationID(responseAsyncCache.nextCorrelationID());
         }

         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} Sending packet nonblocking {} on channelID={}", connection.getID(), packet, id);
         }

         ActiveMQBuffer buffer = packet.encode(connection);

         lock.lock();

         try {
            if (failingOver) {
               waitForFailOver("RemotingConnectionID=" + connection.getID() + " timed-out waiting for fail-over condition on non-blocking send");
            }

            // Sanity check
            if (transferring) {
               throw ActiveMQClientMessageBundle.BUNDLE.cannotSendPacketDuringFailover();
            }

            if (resendCache != null && packet.isRequiresConfirmations()) {
               addResendPacket(packet);
            }

         } finally {
            lock.unlock();
         }

         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} Writing buffer for channelID={}", connection.getID(), id);
         }

         //We do this outside the lock as ResponseCache is threadsafe and allows responses to come in,
         //As the send could block if the response cache cannot add, preventing responses to be handled.
         if (responseAsyncCache != null && packet.isRequiresResponse() && packet.isResponseAsync()) {
            while (!responseAsyncCache.add(packet)) {
               try {
                  Thread.sleep(1);
               } catch (Exception e) {
                  // Ignore
               }
            }
         }

         // The actual send must be outside the lock, or with OIO transport, the write can block if the tcp
         // buffer is full, preventing any incoming buffers being handled and blocking failover
         try {
            connection.getTransportConnection().write(buffer, flushConnection);
         } catch (Throwable t) {
            //If runtime exception, we must remove from the cache to avoid filling up the cache causing it to be full.
            //The client would get still know about this as the exception bubbles up the call stack instead.
            if (responseAsyncCache != null && packet.isRequiresResponse() && packet.isResponseAsync()) {
               responseAsyncCache.remove(packet.getCorrelationID());
            }
            throw t;
         }
         return true;
      }
   }

   @Override
   public boolean sendAndFlush(final Packet packet) {
      return send(packet, -1, true, false);
   }

   @Override
   public boolean send(final Packet packet) {
      return send(packet, -1, false, false);
   }

   @Override
   public boolean send(Packet packet, final int reconnectID) {
      return send(packet, reconnectID, false, false);
   }

   @Override
   public boolean sendBatched(final Packet packet) {
      return send(packet, -1, false, true);
   }

   @Override
   public void setTransferring(boolean transferring) {
      this.transferring = transferring;
   }

   protected ResponseCache getCache() {
      return responseAsyncCache;
   }

   /**
    * @param timeoutMsg message to log on blocking call failover timeout
    */
   private void waitForFailOver(String timeoutMsg) {
      try {
         if (connection.getBlockingCallFailoverTimeout() < 0) {
            while (failingOver) {
               failoverCondition.await();
            }
         } else if (!ConcurrentUtil.await(failoverCondition, connection.getBlockingCallFailoverTimeout())) {
            logger.debug(timeoutMsg);
         }
      } catch (InterruptedException e) {
         throw new ActiveMQInterruptedException(e);
      }
   }

   // This must never called by more than one thread concurrently
   private boolean send(final Packet packet, final int reconnectID, final boolean flush, final boolean batch) {
      if (invokeInterceptors(packet, interceptors, connection) != null) {
         return false;
      }

      synchronized (sendLock) {
         packet.setChannelID(id);

         if (responseAsyncCache != null && packet.isRequiresResponse() && packet.isResponseAsync()) {
            packet.setCorrelationID(responseAsyncCache.nextCorrelationID());
         }

         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} Sending packet nonblocking {} on channelID={}", connection.getID(), packet, id);
         }

         ActiveMQBuffer buffer = packet.encode(connection);

         lock.lock();

         try {
            if (failingOver) {
               waitForFailOver("RemotingConnectionID=" + connection.getID() + " timed-out waiting for fail-over condition on non-blocking send");
            }

            // Sanity check
            if (transferring) {
               throw ActiveMQClientMessageBundle.BUNDLE.cannotSendPacketDuringFailover();
            }

            if (resendCache != null && packet.isRequiresConfirmations()) {
               addResendPacket(packet);
            }

         } finally {
            lock.unlock();
         }

         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} Writing buffer for channelID={}", connection.getID(), id);
         }

         checkReconnectID(reconnectID);

         //We do this outside the lock as ResponseCache is threadsafe and allows responses to come in,
         //As the send could block if the response cache cannot add, preventing responses to be handled.
         if (responseAsyncCache != null && packet.isRequiresResponse() && packet.isResponseAsync()) {
            while (!responseAsyncCache.add(packet)) {
               try {
                  Thread.sleep(1);
               } catch (Exception e) {
                  // Ignore
               }
            }
         }

         // The actual send must be outside the lock, or with OIO transport, the write can block if the tcp
         // buffer is full, preventing any incoming buffers being handled and blocking failover
         try {
            connection.getTransportConnection().write(buffer, flush, batch);
         } catch (Throwable t) {
            //If runtime exception, we must remove from the cache to avoid filling up the cache causing it to be full.
            //The client would get still know about this as the exception bubbles up the call stack instead.
            if (responseAsyncCache != null && packet.isRequiresResponse() && packet.isResponseAsync()) {
               responseAsyncCache.remove(packet.getCorrelationID());
            }
            throw t;
         }
         return true;
      }
   }

   private void checkReconnectID(int reconnectID) {
      if (reconnectID >= 0 && reconnectID != this.reconnectID.get()) {
         throw ActiveMQClientMessageBundle.BUNDLE.packetTransmissionInterrupted();
      }
   }

   @Override
   public Packet sendBlocking(final Packet packet, byte expectedPacket) throws ActiveMQException {
      return sendBlocking(packet, -1, expectedPacket);
   }

   @Override
   public Packet sendBlocking(final Packet packet,
                              final int reconnectID,
                              final byte expectedPacket) throws ActiveMQException {

      return sendBlocking(packet, reconnectID, expectedPacket, connection.getBlockingCallTimeout(), true);

   }

   /**
    * Due to networking issues or server issues the server may take longer to answer than expected.. the client may
    * timeout the call throwing an exception and the client could eventually retry another call, but the server could
    * then answer a previous command issuing a class-cast-exception. The expectedPacket will be used to filter out
    * undesirable packets that would belong to previous calls.
    */
   @Override
   public Packet sendBlocking(final Packet packet,
                              final int reconnectID,
                              final byte expectedPacket,
                              final long timeout,
                              final boolean failOnTimeout) throws ActiveMQException {
      String interceptionResult = invokeInterceptors(packet, interceptors, connection);

      if (interceptionResult != null) {
         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} interceptionResult={}", connection.getID(), interceptionResult);
         }
         // if we don't throw an exception here the client might not unblock
         throw ActiveMQClientMessageBundle.BUNDLE.interceptorRejectedPacket(interceptionResult);
      }

      if (closed) {
         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} closed.", connection.getID());
         }
         throw ActiveMQClientMessageBundle.BUNDLE.connectionDestroyed();
      }

      if (timeout == -1) {
         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} Cannot do a blocking call timeout on a server side connection", connection.getID());
         }
         throw new IllegalStateException("Cannot do a blocking call timeout on a server side connection");
      }

      // Synchronized since can't be called concurrently by more than one thread and this can occur
      // E.g. blocking acknowledge() from inside a message handler at some time as other operation on main thread
      synchronized (sendBlockingLock) {
         packet.setChannelID(id);

         packet.setCorrelationID(blockingCorrelationID.decrementAndGet());

         final ActiveMQBuffer buffer = packet.encode(connection);

         lock.lock();

         try {
            if (failingOver) {
               waitForFailOver("RemotingConnectionID=" + connection.getID() + " timed-out waiting for fail-over condition on blocking send");
            }

            response = null;

            if (resendCache != null && packet.isRequiresConfirmations()) {
               addResendPacket(packet);
            }

            checkReconnectID(reconnectID);

            if (logger.isTraceEnabled()) {
               logger.trace("RemotingConnectionID={} Sending blocking {}", connection.getID(), packet);
            }

            connection.getTransportConnection().write(buffer, false, false);

            long toWait = timeout;

            long start = System.currentTimeMillis();

            while (!closed && (response == null || (response.getType() != EXCEPTION && (response.getType() != expectedPacket || response.getCorrelationID() != packet.getCorrelationID()))) && toWait > 0) {
               try {
                  sendCondition.await(toWait, TimeUnit.MILLISECONDS);
               } catch (InterruptedException e) {
                  throw new ActiveMQInterruptedException(e);
               }

               if (response != null && response.getType() != EXCEPTION && response.getType() != expectedPacket && !response.isResponseAsync()) {
                  ActiveMQClientLogger.LOGGER.packetOutOfOrder(response, new Exception("trace"));
               }

               if (closed) {
                  break;
               }

               final long now = System.currentTimeMillis();

               toWait -= now - start;

               start = now;
            }

            if (closed && toWait > 0 && response == null) {
               Throwable cause = ActiveMQClientMessageBundle.BUNDLE.connectionDestroyed();
               throw ActiveMQClientMessageBundle.BUNDLE.unblockingACall(cause);
            }

            if (!failOnTimeout && response == null) {
               return null;
            }

            if (response == null || (response.getType() != EXCEPTION && response.getCorrelationID() != packet.getCorrelationID())) {
               ActiveMQException e = ActiveMQClientMessageBundle.BUNDLE.timedOutSendingPacket(timeout, packet.getType());
               connection.asyncFail(e);
               throw e;
            }

            if (response.getType() == EXCEPTION) {
               final ActiveMQExceptionMessage mem = (ActiveMQExceptionMessage) response;

               ActiveMQException e = mem.getException();

               e.fillInStackTrace();

               throw e;
            }
         } finally {
            lock.unlock();
         }

         return response;
      }
   }

   /**
    * {@return the name of the interceptor that returned {@code false} or {@code null} if no interceptors returned
    * {@code false}}
    *
    * @param packet the packet to intercept
    */
   public static String invokeInterceptors(final Packet packet,
                                           final List<Interceptor> interceptors,
                                           final RemotingConnection connection) {
      if (interceptors != null && !interceptors.isEmpty()) {
         for (final Interceptor interceptor : interceptors) {
            try {
               boolean callNext = interceptor.intercept(packet, connection);

               if (logger.isDebugEnabled()) {
                  // use a StringBuilder for speed since this may be executed a lot
                  StringBuilder msg = new StringBuilder();
                  msg.append("Invocation of interceptor ").append(interceptor.getClass().getName()).append(" on ").
                     append(packet).append(" returned ").append(callNext);
                  logger.debug(msg.toString());
               }

               if (!callNext) {
                  return interceptor.getClass().getName();
               }
            } catch (final Throwable e) {
               ActiveMQClientLogger.LOGGER.errorCallingInterceptor(interceptor, e);
            }
         }
      }

      return null;
   }

   @Override
   public void setCommandConfirmationHandler(final CommandConfirmationHandler handler) {
      if (confWindowSize < 0) {
         final String msg = "You can't set confirmationHandler on a connection with confirmation-window-size < 0." + " Look at the documentation for more information.";
         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} {}", connection.getID(), msg);
         }
         throw new IllegalStateException(msg);
      }
      commandConfirmationHandler = handler;
   }

   @Override
   public void setResponseHandler(final ResponseHandler responseHandler) {
      if (confWindowSize < 0) {
         final String msg = "You can't set responseHandler on a connection with confirmation-window-size < 0." + " Look at the documentation for more information.";
         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} {}", connection.getID(), msg);
         }
         throw new IllegalStateException(msg);
      }
      responseAsyncCache.setResponseHandler(responseHandler);
   }

   @Override
   public void setHandler(final ChannelHandler handler) {
      if (logger.isTraceEnabled()) {
         logger.trace("RemotingConnectionID={} Setting handler on {} as {}", connection.getID(), this, handler);
      }

      this.handler = handler;
   }

   @Override
   public ChannelHandler getHandler() {
      return handler;
   }

   @Override
   public void endOfBatch() {
      ChannelHandler handler = this.handler;
      if (handler == null) {
         return;
      }
      handler.endOfBatch();
   }

   @Override
   public void close() {
      if (closed) {
         return;
      }

      if (!connection.isDestroyed() && !connection.removeChannel(id)) {
         throw ActiveMQClientMessageBundle.BUNDLE.noChannelToClose(id);
      }

      if (failingOver) {
         unlock();
      }
      closed = true;
      // unblock any blocked call:
      // don't move this one before closed = true, because
      // unblocked calls need to check if (closed) to "gracefully" shutdown.
      returnBlocking();
   }

   @Override
   public void transferConnection(final CoreRemotingConnection newConnection) {
      Objects.requireNonNull(newConnection);

      // Needs to synchronize on the connection to make sure no packets from
      // the old connection get processed after transfer has occurred
      synchronized (connection.getTransferLock()) {
         connection.removeChannel(id);

         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} transferConnection to new RemotingConnectionID={}", connection.getID(), newConnection.getID());
         }

         // And switch it

         newConnection.putChannel(id, this);

         connection = newConnection;

         transferring = true;
      }
   }

   @Override
   public void replayCommands(final int otherLastConfirmedCommandID) {
      if (resendCache != null) {
         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} Replaying commands on channelID={}", connection.getID(), id);
         }
         clearUpTo(otherLastConfirmedCommandID);

         for (final Packet packet : resendCache) {
            doWrite(packet);
         }
      }
   }

   @Override
   public boolean isLocked() {
      return failingOver;
   }

   @Override
   public void lock() {
      if (logger.isTraceEnabled()) {
         logger.trace("RemotingConnectionID={} lock channel {}", connection.getID(), this);
      }
      lock.lock();

      reconnectID.incrementAndGet();

      failingOver = true;

      lock.unlock();
   }

   @Override
   public void unlock() {
      if (logger.isTraceEnabled()) {
         logger.trace("RemotingConnectionID={} unlock channel {}", connection.getID(), this);
      }
      lock.lock();

      failingOver = false;

      failoverCondition.signalAll();

      lock.unlock();
   }

   @Override
   public CoreRemotingConnection getConnection() {
      return connection;
   }

   // Needs to be synchronized since can be called by remoting service timer thread too for timeout flush
   @Override
   public synchronized void flushConfirmations() {
      if (resendCache != null && receivedBytes != 0) {
         receivedBytes = 0;

         final Packet confirmed = new PacketsConfirmedMessage(lastConfirmedCommandID.get());

         confirmed.setChannelID(id);

         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} ChannelImpl::flushConfirmation flushing confirmation {}", connection.getID(), confirmed);
         }

         doWrite(confirmed);
      }
   }

   public void handleAsyncResponse(Packet packet) {
      if (responseAsyncCache != null && packet.isResponseAsync()) {
         responseAsyncCache.handleResponse(packet);
      }
   }

   @Override
   public void confirm(final Packet packet) {
      if (resendCache != null && packet.isRequiresConfirmations()) {
         lastConfirmedCommandID.incrementAndGet();

         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} ChannelImpl::confirming packet {} last commandID={}", connection.getID(), packet, lastConfirmedCommandID);
         }

         receivedBytes += packet.getPacketSize();

         if (receivedBytes >= confWindowSize) {
            receivedBytes = 0;

            final Packet confirmed = new PacketsConfirmedMessage(lastConfirmedCommandID.get());

            confirmed.setChannelID(id);

            doWrite(confirmed);
         }
      }
   }

   @Override
   public void clearCommands() {
      if (resendCache != null) {
         lastConfirmedCommandID.set(-1);

         firstStoredCommandID = 0;

         resendCache.clear();
      }
   }

   @Override
   public void handlePacket(final Packet packet) {
      if (packet.getType() == PACKETS_CONFIRMED) {
         if (resendCache != null) {
            final PacketsConfirmedMessage msg = (PacketsConfirmedMessage) packet;

            clearUpTo(msg.getCommandID());
         }

         if (!connection.isClient() && handler != null) {
            handler.handlePacket(packet);
         }

         return;
      } else {
         if (packet.isResponse()) {
            confirm(packet);

            handleAsyncResponse(packet);
            lock.lock();

            try {
               response = packet;
               sendCondition.signal();
            } finally {
               lock.unlock();
            }
         } else if (handler != null) {
            handler.handlePacket(packet);
         }
      }
   }

   private void doWrite(final Packet packet) {
      final ActiveMQBuffer buffer = packet.encode(connection);

      connection.getTransportConnection().write(buffer, false, false);

   }

   private void addResendPacket(Packet packet) {
      resendCache.add(packet);

      if (logger.isTraceEnabled()) {
         logger.trace("RemotingConnectionID={} ChannelImpl::addResendPacket adding packet {} stored commandID={} possible commandIDr={}", connection.getID(), packet, firstStoredCommandID, (firstStoredCommandID + resendCache.size()));
      }
   }

   private void clearUpTo(final int lastReceivedCommandID) {
      final int numberToClear = 1 + lastReceivedCommandID - firstStoredCommandID;

      if (logger.isTraceEnabled()) {
         logger.trace("RemotingConnectionID={} ChannelImpl::clearUpTo lastReceived commandID={} first commandID={} number to clear {}", connection.getID(), lastReceivedCommandID, firstStoredCommandID, numberToClear);
      }

      for (int i = 0; i < numberToClear; i++) {
         final Packet packet = resendCache.poll();

         if (packet == null) {
            ActiveMQClientLogger.LOGGER.cannotFindPacketToClear(lastReceivedCommandID, firstStoredCommandID);
            firstStoredCommandID = lastReceivedCommandID + 1;
            return;
         }

         if (logger.isTraceEnabled()) {
            logger.trace("RemotingConnectionID={} ChannelImpl::clearUpTo confirming {} towards {}", connection.getID(), packet, commandConfirmationHandler);
         }
         if (commandConfirmationHandler != null) {
            commandConfirmationHandler.commandConfirmed(packet);
         }
         if (responseAsyncCache != null) {
            responseAsyncCache.handleResponse(packet);
         }
      }

      firstStoredCommandID += numberToClear;
   }

   @Override
   public String toString() {
      return "Channel[id=" + CHANNEL_ID.idToString(id) + ", RemotingConnectionID=" + (connection == null ? "NULL" : connection.getID()) + ", handler=" + handler + "]";
   }
}
