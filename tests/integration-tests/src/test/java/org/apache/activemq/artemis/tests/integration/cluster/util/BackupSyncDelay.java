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
package org.apache.activemq.artemis.tests.integration.cluster.util;

import java.util.Objects;
import java.util.concurrent.locks.Lock;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.ChannelHandler;
import org.apache.activemq.artemis.core.protocol.core.CommandConfirmationHandler;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.ResponseHandler;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationResponseMessageV2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationStartSyncMessage;
import org.apache.activemq.artemis.core.replication.ReplicationEndpoint;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.ReplicationBackupActivation;
import org.apache.activemq.artemis.core.server.impl.SharedNothingBackupActivation;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * An interceptor to keep a replicated backup server from reaching "up-to-date" status.
 * <p>
 * There are 3 test scenarios for 'adding data to a remote backup':
 * <ol>
 * <li>sync data that already existed
 * <li>adding `new` Journal updates (that arrived after the backup appeared) WHILE sync'ing happens
 * <li>adding `new` Journal updates AFTER sync'ing is done
 * </ol>
 * These 'withDelay' tests were created to test/verify data transfers of type 2. because there is so
 * little data, if we don't delay the sync message, we cannot be sure we are testing scenario .2.
 * because the sync will be done too fast.
 * <p>
 * One problem is that we can't add an interceptor to the backup before starting it. So we add the
 * interceptor to the 'live' which will place a different {@link ChannelHandler} in the backup
 * during the initialization of replication.
 * <p>
 * We need to hijack the replication channel handler, because we need to
 * <ol>
 * <li>send an early answer to the {@link PacketImpl#REPLICATION_SYNC_FILE} packet that signals
 * being up-to-date
 * <li>not send an answer to it, when we deliver the packet later.
 * </ol>
 */
public class BackupSyncDelay implements Interceptor {

   private final ReplicationChannelHandler handler;
   private final ActiveMQServer backup;
   private final ActiveMQServer live;

   public void deliverUpToDateMsg() {
      live.getRemotingService().removeIncomingInterceptor(this);
      if (backup.isStarted()) {
         handler.deliver();
      }
   }

   /**
    * @param packetCode which packet is going to be intercepted.
    */
   public BackupSyncDelay(ActiveMQServer backup, ActiveMQServer live, byte packetCode) {
      this.backup = backup;
      this.live = live;
      live.getRemotingService().addIncomingInterceptor(this);
      handler = new ReplicationChannelHandler(packetCode);
   }

   public BackupSyncDelay(TestableServer backupServer, TestableServer liveServer) {
      this(backupServer.getServer(), liveServer.getServer(), PacketImpl.REPLICATION_START_FINISH_SYNC);
   }

   @Override
   public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
      if (packet.getType() == PacketImpl.BACKUP_REGISTRATION) {
         try {
            Activation backupActivation = backup.getActivation();
            ReplicationEndpoint repEnd = null;
            if (backupActivation instanceof SharedNothingBackupActivation activation) {
               repEnd = activation.getReplicationEndpoint();
            } else if (backupActivation instanceof ReplicationBackupActivation activation) {
               repEnd = activation.getReplicationEndpoint();
            }
            Objects.requireNonNull(repEnd, "replication endpoint isn't supposed to be null");
            handler.addSubHandler(repEnd);
            Channel repChannel = repEnd.getChannel();
            repChannel.setHandler(handler);
            handler.setChannel(repChannel);
            live.getRemotingService().removeIncomingInterceptor(this);
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
      return true;
   }

   public static class ReplicationChannelHandler implements ChannelHandler {

      public ReplicationChannelHandler(byte type) {
         this.typeToIntercept = type;
      }

      private ReplicationEndpoint handler;
      private Packet onHold;
      private Channel channel;
      public volatile boolean deliver;
      private volatile boolean delivered;
      private boolean receivedUpToDate;
      private boolean mustHold = true;
      private final byte typeToIntercept;

      public void addSubHandler(ReplicationEndpoint handler) {
         this.handler = handler;
      }

      public synchronized void deliver() {
         deliver = true;
         if (!receivedUpToDate) {
            return;
         }
         if (delivered) {
            return;
         }

         Objects.requireNonNull(onHold, "Don't have the 'sync is done' packet to deliver");
         // Use wrapper to avoid sending a response
         ChannelWrapper wrapper = new ChannelWrapper(channel);
         handler.setChannel(wrapper);
         try {
            handler.handlePacket(onHold);
            delivered = true;
         } finally {
            handler.setChannel(channel);
            channel.setHandler(handler);
            onHold = null;
         }
      }

      public void setChannel(Channel channel) {
         this.channel = channel;
      }

      public void setHold(boolean hold) {
         mustHold = hold;
      }

      @Override
      public synchronized void handlePacket(Packet packet) {
         if (onHold != null && deliver) {
            deliver();
         }

         if (typeToIntercept == PacketImpl.REPLICATION_START_FINISH_SYNC) {
            if (packet.getType() == PacketImpl.REPLICATION_START_FINISH_SYNC && mustHold) {
               ReplicationStartSyncMessage syncMsg = (ReplicationStartSyncMessage) packet;
               if (syncMsg.isSynchronizationFinished() && !deliver) {
                  receivedUpToDate = true;
                  assert onHold == null;
                  onHold = packet;
                  PacketImpl response = new ReplicationResponseMessageV2(true);
                  channel.send(response);
                  return;
               }
            }
         } else if (typeToIntercept == packet.getType()) {
            channel.send(new ReplicationResponseMessage());
            return;
         }

         handler.handlePacket(packet);
      }

   }

   public static class ChannelWrapper implements Channel {

      private final Channel channel;

      public ChannelWrapper(Channel channel) {
         this.channel = channel;
      }

      @Override
      public boolean send(Packet packet, boolean flushConnection) {
         return channel.send(packet, flushConnection);
      }

      @Override
      public String toString() {
         return "ChannelWrapper(" + channel + ")";
      }

      @Override
      public long getID() {
         return channel.getID();
      }

      @Override
      public boolean send(Packet packet) {
         // no-op
         // channel.send(packet);
         return true;
      }

      @Override
      public boolean sendBatched(Packet packet) {
         throw new UnsupportedOperationException();

      }

      @Override
      public void flushConnection() {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean sendAndFlush(Packet packet) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Packet sendBlocking(Packet packet, byte expected) throws ActiveMQException {
         throw new UnsupportedOperationException();
      }

      @Override
      public Packet sendBlocking(Packet packet,
                                 int reconnectID,
                                 byte expectedPacket,
                                 long timeout,
                                 boolean failOnTimeout) throws ActiveMQException {
         throw new UnsupportedOperationException();
      }

      @Override
      public void setHandler(ChannelHandler handler) {
         throw new UnsupportedOperationException();
      }

      @Override
      public ChannelHandler getHandler() {
         throw new UnsupportedOperationException();
      }

      @Override
      public void endOfBatch() {
         throw new UnsupportedOperationException();
      }

      @Override
      public void close() {
         throw new UnsupportedOperationException();
      }

      @Override
      public void transferConnection(CoreRemotingConnection newConnection) {
         throw new UnsupportedOperationException();
      }

      @Override
      public int getReconnectID() {
         return 0;
      }

      @Override
      public boolean send(Packet packet, int reconnectID) {
         return false;
      }

      @Override
      public Packet sendBlocking(Packet packet, int reconnectID, byte expectedPacket) throws ActiveMQException {
         return null;
      }

      @Override
      public void replayCommands(int lastConfirmedCommandID) {
         throw new UnsupportedOperationException();
      }

      @Override
      public int getLastConfirmedCommandID() {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean isLocked() {
         return false;
      }

      @Override
      public void lock() {
         throw new UnsupportedOperationException();
      }

      @Override
      public void unlock() {
         throw new UnsupportedOperationException();

      }

      @Override
      public void returnBlocking() {
         throw new UnsupportedOperationException();
      }

      @Override
      public void returnBlocking(Throwable cause) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Lock getLock() {
         throw new UnsupportedOperationException();
      }

      @Override
      public CoreRemotingConnection getConnection() {
         throw new UnsupportedOperationException();
      }

      @Override
      public void confirm(Packet packet) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void setCommandConfirmationHandler(CommandConfirmationHandler handler) {
         throw new UnsupportedOperationException();

      }

      @Override
      public void setResponseHandler(ResponseHandler handler) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void flushConfirmations() {
         throw new UnsupportedOperationException();
      }

      @Override
      public void handlePacket(Packet packet) {
         throw new UnsupportedOperationException();

      }

      @Override
      public void clearCommands() {
         throw new UnsupportedOperationException();
      }

      @Override
      public int getConfirmationWindowSize() {
         throw new UnsupportedOperationException();
      }

      @Override
      public void setTransferring(boolean transferring) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean supports(byte packetID) {
         return true;
      }

      @Override
      public boolean supports(byte packetID, int version) {
         return true;
      }

   }
}
