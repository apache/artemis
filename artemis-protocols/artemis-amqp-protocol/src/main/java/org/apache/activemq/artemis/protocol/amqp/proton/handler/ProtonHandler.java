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
package org.apache.activemq.artemis.protocol.amqp.proton.handler;

import javax.security.auth.Subject;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.EventLoop;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPSecurityException;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonInitializable;
import org.apache.activemq.artemis.protocol.amqp.sasl.ClientSASL;
import org.apache.activemq.artemis.protocol.amqp.sasl.SASLResult;
import org.apache.activemq.artemis.protocol.amqp.sasl.ServerSASL;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Collector;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.SaslListener;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.impl.TransportInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class ProtonHandler extends ProtonInitializable implements SaslListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final byte SASL = 0x03;

   private static final byte BARE = 0x00;

   private final Transport transport = Proton.transport();

   private final Connection connection = Proton.connection();

   private final Collector collector = Proton.collector();

   private List<EventHandler> handlers = new ArrayList<>();

   private ServerSASL chosenMechanism;
   private ClientSASL clientSASLMechanism;

   private final long creationTime;

   private final boolean isServer;

   private SASLResult saslResult;

   protected volatile boolean dataReceived;

   protected boolean receivedFirstPacket = false;

   private final EventLoop workerExecutor;

   private final ArtemisExecutor poolExecutor;

   protected final ReadyListener readyListener;

   boolean inDispatch = false;

   boolean scheduledFlush = false;

   boolean flushInstantly = false;

   volatile boolean readable = true;

   /**
    * afterFlush and afterFlushSet properties are set by afterFlush methods. This is to be called after the flush loop.
    * this is usually to be used by flow control events that have to take place after the incoming bytes are settled.
    * <p>
    * There is only one afterFlush most of the time, and for that reason as an optimization we will try to use a single
    * place most of the time however if more are needed we will use the list.
    */
   private Runnable afterFlush;
   protected Set<Runnable> afterFlushSet;

   public boolean isReadable() {
      return readable;
   }

   public ProtonHandler setReadable(boolean readable) {
      this.readable = readable;
      return this;
   }

   @Override
   public void initialize() throws Exception {
      initialized = true;
   }

   public void afterFlush(Runnable runnable) {
      requireHandler();
      if (afterFlush == null) {
         afterFlush = runnable;
         return;
      } else {
         if (afterFlush != runnable) {
            if (afterFlushSet == null) {
               afterFlushSet = new HashSet<>();
            }
            afterFlushSet.add(runnable);
         }
      }
   }

   public void runAfterFlush() {
      requireHandler();
      if (afterFlush != null) {
         Runnable toRun = afterFlush;
         afterFlush = null;
         // setting it to null to avoid recursive flushes
         toRun.run();
      }

      if (afterFlushSet != null) {
         // This is not really expected to happen.
         // most of the time we will only have a single Runnable needing after flush
         // as this was written for flow control
         // however in extreme of caution, I'm dealing with a case where more than one is used.
         Set<Runnable> toRun = afterFlushSet;
         // setting it to null to avoid recursive flushes
         afterFlushSet = null;
         for (Runnable runnable : toRun) {
            runnable.run();
         }
      }
   }

   public ProtonHandler(EventLoop workerExecutor, ArtemisExecutor poolExecutor, boolean isServer) {
      this.workerExecutor = workerExecutor;
      this.poolExecutor = poolExecutor;
      this.readyListener = () -> runLater(this::flush);
      this.creationTime = System.currentTimeMillis();
      this.isServer = isServer;

      try {
         ((TransportInternal) transport).setUseReadOnlyOutputBuffer(false);
      } catch (NoSuchMethodError nsme) {
         // using a version at runtime where the optimization isn't available, ignore
         logger.trace("Proton output buffer optimisation unavailable");
      }

      transport.bind(connection);
      connection.collect(collector);
   }

   public Long tick(boolean firstTick) {
      requireHandler();
      if (!firstTick) {
         try {
            if (connection.getLocalState() != EndpointState.CLOSED) {
               long rescheduleAt = transport.tick(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
               if (transport.isClosed()) {
                  throw new IllegalStateException("Channel was inactive for to long");
               }
               return rescheduleAt;
            }
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            transport.close();
            connection.setCondition(new ErrorCondition());
         } finally {
            flush();
         }
         return 0L;
      }
      return transport.tick(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
   }

   /**
    * We cannot flush until the initial handshake was finished. If this happens before the handshake, the connection
    * response will happen without SASL and the client will respond and fail with an invalid code.
    */
   public void scheduledFlush() {
      if (receivedFirstPacket) {
         flush();
      }
   }

   public int capacity() {
      requireHandler();
      return transport.capacity();
   }

   public boolean isHandler() {
      return workerExecutor.inEventLoop();
   }

   public void requireHandler() {
      if (!workerExecutor.inEventLoop()) {
         throw new IllegalStateException("this method requires to be called within the handler, use the executor");
      }
   }

   public Transport getTransport() {
      return transport;
   }

   public Connection getConnection() {
      return connection;
   }

   public ProtonHandler addEventHandler(EventHandler handler) {
      handlers.add(handler);
      return this;
   }

   public void createServerSASL(String[] mechanisms) {
      requireHandler();
      Sasl sasl = transport.sasl();
      sasl.server();
      sasl.setMechanisms(mechanisms);
      sasl.setListener(this);
   }

   public void instantFlush() {
      this.flushInstantly = true;
      // This will perform event handling, and at some point the flushBytes will be called
      this.flush();
   }

   public void flushBytes() {
      requireHandler();

      if (flushInstantly) {
         flushInstantly = false;
         scheduledFlush = false;
         actualFlush();
      } else {
         // Under regular circunstances, it would be too costly to flush every time,
         // so we flush only once at the end of processing.

         // this decision was made after extensive performance testing.
         if (!scheduledFlush) {
            scheduledFlush = true;
            workerExecutor.execute(this::actualFlush);
         }
      }
   }

   private void actualFlush() {
      requireHandler();

      for (EventHandler handler : handlers) {
         if (!handler.flowControl(readyListener)) {
            scheduledFlush = false;
            return;
         }
      }

      try {
         while (true) {
            ByteBuffer head = transport.head();
            int pending = head.remaining();

            if (pending <= 0) {
               break;
            }

            // We allocated a Pooled Direct Buffer, that will be sent down the stream
            ByteBuf buffer = PooledByteBufAllocator.DEFAULT.directBuffer(pending);
            buffer.writeBytes(head);

            for (EventHandler handler : handlers) {
               handler.pushBytes(buffer);
            }

            transport.pop(pending);
         }
      } finally {
         scheduledFlush = false;
      }
   }

   public SASLResult getSASLResult() {
      return saslResult;
   }

   public void inputBuffer(ByteBuf buffer) {
      requireHandler();
      dataReceived = true;
      while (buffer.readableBytes() > 0) {
         int capacity = transport.capacity();

         if (!receivedFirstPacket) {
            handleFirstPacket(buffer);
            // there is a chance that if SASL Handshake has been carried out that the capacity may change.
            capacity = transport.capacity();
         }

         if (capacity > 0) {
            ByteBuffer tail = transport.tail();
            int min = Math.min(capacity, buffer.readableBytes());
            tail.limit(min);
            buffer.readBytes(tail);

            flush();
         } else {
            if (capacity == 0) {
               logger.debug("abandoning: readableBytes={}", buffer.readableBytes());
            } else {
               logger.debug("transport closed, discarding: readableBytes={}, capacity={}", buffer.readableBytes(), transport.capacity());
            }
            break;
         }
      }
   }

   public boolean checkDataReceived() {
      boolean res = dataReceived;

      dataReceived = false;

      return res;
   }

   public long getCreationTime() {
      return creationTime;
   }

   public void runOnPool(Runnable runnable) {
      poolExecutor.execute(runnable);
   }

   public void runNow(Runnable runnable) {
      if (workerExecutor.inEventLoop()) {
         runnable.run();
      } else {
         workerExecutor.execute(runnable);
      }
   }

   public void runLater(Runnable runnable) {
      workerExecutor.execute(runnable);
   }

   public void flush() {
      if (workerExecutor.inEventLoop()) {
         handleFlush();
      } else {
         runLater(this::handleFlush);
      }
   }

   private void handleFlush() {
      try {
         transport.process();
      } catch (Exception ex) {
         logger.debug("AMQP Transport threw error while processing input: {}", ex.getMessage());
         if (transport.getCondition() == null) {
            transport.setCondition(new ErrorCondition(AmqpError.INTERNAL_ERROR, ex.getMessage()));
         }
      } finally {
         dispatch();
      }
   }

   public void close(ErrorCondition errorCondition, AMQPConnectionContext connectionContext) {
      runNow(() -> {
         if (errorCondition != null) {
            connection.setCondition(errorCondition);
         }
         connection.close();
         flush();
      });

      // this needs to be done in two steps
      // we first flush what we have to the client
      // after flushed, we close the local connection
      // otherwise this could close the netty connection before the Writable is complete
      runLater(() -> {
         connectionContext.getConnectionCallback().getTransportConnection().close();
      });
   }

   // server side SASL Listener
   @Override
   public void onSaslInit(Sasl sasl, Transport transport) {
      logger.debug("onSaslInit: {}", sasl);
      dispatchRemoteMechanismChosen(sasl.getRemoteMechanisms()[0]);

      if (chosenMechanism != null) {

         processPending(sasl);

      } else {
         // no auth available, system error
         saslComplete(sasl, Sasl.SaslOutcome.PN_SASL_SYS);
      }
   }

   private void processPending(Sasl sasl) {
      byte[] dataSASL = new byte[sasl.pending()];

      int received = sasl.recv(dataSASL, 0, dataSASL.length);
      if (logger.isTraceEnabled()) {
         logger.trace("Working on sasl, length:{}", received);
      }

      byte[] response = chosenMechanism.processSASL(received != -1 ? dataSASL : null);
      if (response != null) {
         sasl.send(response, 0, response.length);
      }

      saslResult = chosenMechanism.result();
      if (saslResult != null) {
         if (saslResult.isSuccess()) {
            saslComplete(sasl, Sasl.SaslOutcome.PN_SASL_OK);
         } else {
            saslComplete(sasl, Sasl.SaslOutcome.PN_SASL_AUTH);
         }
      }
   }

   @Override
   public void onSaslResponse(Sasl sasl, Transport transport) {
      logger.debug("onSaslResponse: {}", sasl);
      processPending(sasl);
   }

   // client SASL Listener
   @Override
   public void onSaslMechanisms(Sasl sasl, Transport transport) {

      dispatchMechanismsOffered(sasl.getRemoteMechanisms());

      if (clientSASLMechanism == null) {
         logger.info("Outbound connection failed - unknown mechanism, offered mechanisms: {}",
                   Arrays.asList(sasl.getRemoteMechanisms()));
         sasl.setListener(null);
         transport.close_head();
         dispatchAuthFailed();
      } else {
         sasl.setMechanisms(clientSASLMechanism.getName());
         byte[] initialResponse = clientSASLMechanism.getInitialResponse();
         if (initialResponse != null) {
            sasl.send(initialResponse, 0, initialResponse.length);
         }
      }
   }

   @Override
   public void onSaslChallenge(Sasl sasl, Transport transport) {
      int challengeSize = sasl.pending();
      byte[] challenge = new byte[challengeSize];
      sasl.recv(challenge, 0, challengeSize);
      byte[] response = clientSASLMechanism.getResponse(challenge);
      sasl.send(response, 0, response.length);
   }

   @Override
   public void onSaslOutcome(Sasl sasl, Transport transport) {
      logger.debug("onSaslOutcome: {}", sasl);
      switch (sasl.getState()) {
         case PN_SASL_FAIL:
            logger.info("Outbound connection failed, authentication failure");
            sasl.setListener(null);
            transport.close_head();
            dispatchAuthFailed();
            break;
         case PN_SASL_PASS:
            logger.debug("Outbound connection succeeded");

            if (sasl.pending() != 0) {
               byte[] additionalData = new byte[sasl.pending()];
               sasl.recv(additionalData, 0, additionalData.length);
               clientSASLMechanism.getResponse(additionalData);
            }

            saslResult = new SASLResult() {
               @Override
               public String getUser() {
                  return null;
               }

               @Override
               public Subject getSubject() {
                  return null;
               }

               @Override
               public boolean isSuccess() {
                  return true;
               }
            };

            dispatchAuthSuccess();
            break;

         default:
            break;
      }
   }

   private void saslComplete(Sasl sasl, Sasl.SaslOutcome saslOutcome) {
      logger.debug("saslComplete: {}", sasl);
      sasl.done(saslOutcome);
      if (chosenMechanism != null) {
         chosenMechanism.done();
         chosenMechanism = null;
      }
   }

   private void dispatchAuthFailed() {
      for (EventHandler h : handlers) {
         h.onAuthFailed(this, getConnection());
      }
   }

   private void dispatchAuthSuccess() {
      for (EventHandler h : handlers) {
         h.onAuthSuccess(this, getConnection());
      }
   }

   private void dispatchMechanismsOffered(final String[] mechs) {
      for (EventHandler h : handlers) {
         h.onSaslMechanismsOffered(this, mechs);
      }
   }
   private void dispatchAuth(boolean sasl) {
      for (EventHandler h : handlers) {
         h.onAuthInit(this, getConnection(), sasl);
      }
   }

   private void dispatchRemoteMechanismChosen(final String mech) {
      for (EventHandler h : handlers) {
         h.onSaslRemoteMechanismChosen(this, mech);
      }
   }

   private void dispatch() {
      Event ev;

      if (inDispatch) {
         // Avoid recursion from events
         return;
      }
      try {
         inDispatch = true;
         if (AuditLogger.isAnyLoggingEnabled()) {
            for (EventHandler h : handlers) {
               AuditLogger.setRemoteAddress(h.getRemoteAddress());
            }
         }
         while (isReadable() && (ev = collector.peek()) != null) {
            for (EventHandler h : handlers) {
               logger.trace("Handling {} towards {}", ev, h);

               try {
                  Events.dispatch(ev, h);
               } catch (ActiveMQAMQPSecurityException | ActiveMQSecurityException e) {
                  logger.warn(e.getMessage(), e);
                  ErrorCondition error = new ErrorCondition();
                  error.setCondition(AmqpError.UNAUTHORIZED_ACCESS);
                  error.setDescription(Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName()));
                  connection.setCondition(error);
                  connection.close();
               } catch (Exception e) {
                  logger.warn(e.getMessage(), e);
                  ErrorCondition error = new ErrorCondition();
                  error.setCondition(AmqpError.INTERNAL_ERROR);
                  error.setDescription("Unrecoverable error: " + (Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName())));
                  connection.setCondition(error);
                  connection.close();
               }
            }

            collector.pop();
         }

      } finally {
         inDispatch = false;
      }

      flushBytes();

      runAfterFlush();
   }


   public void handleError(Exception e) {
      if (workerExecutor.inEventLoop()) {
         internalHandlerError(e);
      } else {
         runLater(() -> internalHandlerError(e));
      }
   }

   private void internalHandlerError(Exception e) {
      logger.warn(e.getMessage(), e);
      ErrorCondition error = new ErrorCondition();
      error.setCondition(AmqpError.INTERNAL_ERROR);
      error.setDescription("Unrecoverable error: " + (Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName())));
      connection.setCondition(error);
      connection.close();
      flush();
   }

   public void open(String containerId, Map<Symbol, Object> connectionProperties, Symbol[] offeredCapabilities, Symbol[] desiredCapabilities) {
      this.transport.open();
      this.connection.setContainer(containerId);
      this.connection.setProperties(connectionProperties);
      this.connection.setOfferedCapabilities(offeredCapabilities);
      this.connection.setDesiredCapabilities(desiredCapabilities);
      this.connection.open();
      flush();
   }

   public void setChosenMechanism(ServerSASL chosenMechanism) {
      this.chosenMechanism = chosenMechanism;
   }

   public void setClientMechanism(final ClientSASL saslClientMech) {
      this.clientSASLMechanism = saslClientMech;
   }

   public void createClientSASL() {
      Sasl sasl = transport.sasl();
      sasl.client();
      sasl.setListener(this);
   }

   private void handleFirstPacket(ByteBuf buffer) {
      try {
         byte auth = buffer.getByte(4);
         if (auth == SASL || auth == BARE) {
            if (isServer) {
               dispatchAuth(auth == SASL);
            } else if (auth == BARE && clientSASLMechanism == null) {
               dispatchAuthSuccess();
            }
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }

      receivedFirstPacket = true;
   }
}
