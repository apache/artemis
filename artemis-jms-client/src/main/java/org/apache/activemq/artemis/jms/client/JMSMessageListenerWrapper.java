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
package org.apache.activemq.artemis.jms.client;

import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSConstants;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.jms.client.compatible1X.ActiveMQCompatibleMessage;

public class JMSMessageListenerWrapper implements MessageHandler {

   private final ConnectionFactoryOptions options;
   private final ActiveMQConnection connection;

   private final ActiveMQSession session;

   private final MessageListener listener;

   private final ClientConsumer consumer;

   private final boolean transactedOrClientAck;

   private final boolean individualACK;

   private final boolean clientACK;

   protected JMSMessageListenerWrapper(final ConnectionFactoryOptions options,
                                       final ActiveMQConnection connection,
                                       final ActiveMQSession session,
                                       final ClientConsumer consumer,
                                       final MessageListener listener,
                                       final int ackMode) {
      this.options = options;

      this.connection = connection;

      this.session = session;

      this.consumer = consumer;

      this.listener = listener;

      transactedOrClientAck = (ackMode == Session.SESSION_TRANSACTED || ackMode == Session.CLIENT_ACKNOWLEDGE) || session.isXA();

      individualACK = (ackMode == ActiveMQJMSConstants.INDIVIDUAL_ACKNOWLEDGE);

      clientACK = (ackMode == Session.CLIENT_ACKNOWLEDGE);
   }

   /**
    * In this method we apply the JMS acknowledgement and redelivery semantics
    * as per JMS spec
    */
   @Override
   public void onMessage(final ClientMessage message) {
      ActiveMQMessage msg;

      if (session.isEnable1xPrefixes()) {
         msg = ActiveMQCompatibleMessage.createMessage(message, session.getCoreSession(), options);
      } else {
         msg = ActiveMQMessage.createMessage(message, session.getCoreSession(), options);
      }

      if (individualACK) {
         msg.setIndividualAcknowledge();
      }

      if (clientACK) {
         msg.setClientAcknowledge();
      }

      try {
         msg.doBeforeReceive();
      } catch (Exception e) {
         ActiveMQJMSClientLogger.LOGGER.errorPreparingMessageForReceipt(msg.getCoreMessage().toString(), e);
         return;
      }

      if (transactedOrClientAck) {
         try {
            message.acknowledge();
         } catch (ActiveMQException e) {
            ((ClientSessionInternal) session.getCoreSession()).markRollbackOnly();
            ActiveMQJMSClientLogger.LOGGER.errorProcessingMessage(e);
         }
      }

      try {
         connection.getThreadAwareContext().setCurrentThread(false);
         listener.onMessage(msg);
      } catch (RuntimeException e) {
         // See JMS 1.1 spec, section 4.5.2

         ActiveMQJMSClientLogger.LOGGER.onMessageError(e);

         if (!transactedOrClientAck) {
            try {
               if (individualACK) {
                  message.individualAcknowledge();
               }

               session.getCoreSession().rollback(true);

               session.setRecoverCalled(true);
            } catch (Exception e2) {
               ActiveMQJMSClientLogger.LOGGER.errorRecoveringSession(e2);
            }
         }
      } finally {
         connection.getThreadAwareContext().clearCurrentThread(false);
      }
      if (!session.isRecoverCalled() && !individualACK) {
         try {
            // We don't want to call this if the consumer was closed from inside onMessage
            if (!consumer.isClosed() && !transactedOrClientAck) {
               message.acknowledge();
            }
         } catch (ActiveMQException e) {
            ((ClientSessionInternal) session.getCoreSession()).markRollbackOnly();
            ActiveMQJMSClientLogger.LOGGER.errorProcessingMessage(e);
         }
      }

      session.setRecoverCalled(false);
   }

   @Override
   public void onMessageExpired(ClientMessage message) {
      try {
         message.checkCompletion();
      } catch (ActiveMQException e) {
         ActiveMQJMSClientLogger.LOGGER.errorProcessingMessage(e);
      }
   }
}
