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
package org.apache.activemq.artemis.protocol.amqp.broker;

import javax.security.auth.Subject;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.sasl.SASLResult;
import org.apache.activemq.artemis.spi.core.protocol.AbstractRemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.EndpointState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a Server's Connection representation used by ActiveMQ Artemis.
 */
public class ActiveMQProtonRemotingConnection extends AbstractRemotingConnection {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final AMQPConnectionContext amqpConnection;

   private final ProtonProtocolManager manager;

   public ActiveMQProtonRemotingConnection(ProtonProtocolManager manager,
                                           AMQPConnectionContext amqpConnection,
                                           Connection transportConnection,
                                           Executor connectionExecutor) {
      super(transportConnection, connectionExecutor);
      this.manager = manager;
      this.amqpConnection = amqpConnection;
   }

   public AMQPConnectionContext getAmqpConnection() {
      return amqpConnection;
   }

   public ProtonProtocolManager getManager() {
      return manager;
   }


   @Override
   public void scheduledFlush() {
      amqpConnection.scheduledFlush();
   }

   /**
    * This can be called concurrently by more than one thread so needs to be locked
    */
   @Override
   public void fail(final ActiveMQException me, String scaleDownTargetNodeID) {
      if (destroyed) {
         return;
      }

      destroyed = true;

      if (logger.isDebugEnabled()) {
         try {
            logger.debug("Connection failure detected. amqpConnection.getHandler().getConnection().getRemoteState() = {}, remoteIP={}", amqpConnection.getHandler().getConnection().getRemoteState(), amqpConnection.getConnectionCallback().getTransportConnection().getRemoteAddress());
         } catch (Throwable e) { // just to avoid a possible NPE from the debug statement itself
            logger.debug(e.getMessage(), e);
         }
      }

      try {
         if (amqpConnection.getHandler().getConnection().getRemoteState() != EndpointState.CLOSED) {
            // A remote close was received on the client, on that case it's just a normal operation and we don't need to log this.
            ActiveMQClientLogger.LOGGER.connectionFailureDetected(amqpConnection.getConnectionCallback().getTransportConnection().getProtocolConnection().getProtocolName(), amqpConnection.getConnectionCallback().getTransportConnection().getRemoteAddress(), me.getMessage(), me.getType());
         }
      } catch (Throwable e) { // avoiding NPEs from te logging statement. I don't think this would happen, but just in case
         logger.warn(e.getMessage(), e);
      }

      amqpConnection.runNow(() -> {
         // Then call the listeners
         callFailureListeners(me, scaleDownTargetNodeID);

         callClosingListeners();

         internalClose();
      });
   }

   @Override
   public void close() {
      if (destroyed) {
         return;
      }

      destroyed = true;

      if (logger.isDebugEnabled()) {
         try {
            logger.debug("Connection regular close. amqpConnection.getHandler().getConnection().getRemoteState() = {}, remoteIP={}", amqpConnection.getHandler().getConnection().getRemoteState(), amqpConnection.getConnectionCallback().getTransportConnection().getRemoteAddress());
         } catch (Throwable e) { // just to avoid a possible NPE from the debug statement itself
            logger.debug(e.getMessage(), e);
         }
      }

      amqpConnection.runNow(() -> {
         callClosingListeners();
         internalClose();
      });
   }

   @Override
   public void destroy() {
      synchronized (this) {
         if (destroyed) {
            return;
         }

         destroyed = true;
      }

      callClosingListeners();

      internalClose();
   }

   @Override
   public void disconnect(boolean criticalError) {
      if (destroyed) {
         return;
      }

      destroyed = true;

      ErrorCondition errorCondition = new ErrorCondition();
      errorCondition.setCondition(AmqpSupport.CONNECTION_FORCED);
      amqpConnection.close(errorCondition);
      // There's no need to flush, amqpConnection.close() is calling flush
      // as long this semantic is kept no need to flush here
   }

   /**
    * Disconnect the connection, closing all channels
    */
   @Override
   public void disconnect(String scaleDownNodeID, boolean criticalError) {
      disconnect(criticalError);
   }

   @Override
   public boolean checkDataReceived() {
      return amqpConnection.checkDataReceived();
   }

   @Override
   public void flush() {
      amqpConnection.flush();
   }

   @Override
   public void bufferReceived(Object connectionID, ActiveMQBuffer buffer) {
      amqpConnection.inputBuffer(buffer.byteBuf());
      super.bufferReceived(connectionID, buffer);
   }

   private void internalClose() {
      // We close the underlying transport connection
      getTransportConnection().close();
   }

   @Override
   public Subject getSubject() {
      SASLResult saslResult = amqpConnection.getSASLResult();
      if (saslResult != null && saslResult.getSubject() != null) {
         return saslResult.getSubject();
      }
      return super.getSubject();
   }

   @Override
   public boolean isSupportsFlowControl() {
      return true;
   }

   /**
    * {@return the name of the protocol for this Remoting Connection}
    */
   @Override
   public String getProtocolName() {
      return ProtonProtocolManagerFactory.AMQP_PROTOCOL_NAME;
   }

   @Override
   public String getClientID() {
      return amqpConnection.getRemoteContainer();
   }

   public void open() {
      amqpConnection.open();
   }

}
