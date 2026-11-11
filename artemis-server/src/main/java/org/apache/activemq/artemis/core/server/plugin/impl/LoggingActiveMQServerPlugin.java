/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.plugin.impl;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperationAbstract;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.utils.critical.CriticalComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * plugin to log various events within the broker, configured with the following booleans:
 * <ul>
 * <li>{@code LOG_CONNECTION_EVENTS} - connections creation/destroy
 * <li>{@code LOG_SESSION_EVENTS} - sessions creation/close
 * <li>{@code LOG_CONSUMER_EVENTS} - consumers creation/close
 * <li>{@code LOG_DELIVERING_EVENTS} - messages delivered to consumer, acked by consumer
 * <li>{@code LOG_SENDING_EVENTS} -  messaged is sent, message is routed
 * <li>{@code LOG_INTERNAL_EVENTS} - critical failures, bridge deployments, queue creation/destroyed, message expired
 * </ul>
 */
public class LoggingActiveMQServerPlugin implements ActiveMQServerPlugin, Serializable {

   public static final String LOG_ALL_EVENTS = "LOG_ALL_EVENTS";
   public static final String LOG_CONNECTION_EVENTS = "LOG_CONNECTION_EVENTS";
   public static final String LOG_SESSION_EVENTS = "LOG_SESSION_EVENTS";
   public static final String LOG_CONSUMER_EVENTS = "LOG_CONSUMER_EVENTS";
   public static final String LOG_DELIVERING_EVENTS = "LOG_DELIVERING_EVENTS";
   public static final String LOG_SENDING_EVENTS = "LOG_SENDING_EVENTS";
   public static final String LOG_INTERNAL_EVENTS = "LOG_INTERNAL_EVENTS";
   public static final String UNAVAILABLE = "UNAVAILABLE";
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private static final long serialVersionUID = 1L;
   private boolean logAll = false;
   private boolean logConnectionEvents = false;
   private boolean logSessionEvents = false;
   private boolean logConsumerEvents = false;
   private boolean logDeliveringEvents = false;
   private boolean logSendingEvents = false;
   private boolean logInternalEvents = false;

   public boolean isLogAll() {
      return logAll;
   }

   public boolean isLogConnectionEvents() {
      return logConnectionEvents;
   }

   public boolean isLogSessionEvents() {
      return logSessionEvents;
   }

   public boolean isLogConsumerEvents() {
      return logConsumerEvents;
   }

   public boolean isLogDeliveringEvents() {
      return logDeliveringEvents;
   }

   public boolean isLogSendingEvents() {
      return logSendingEvents;
   }

   public boolean isLogInternalEvents() {
      return logInternalEvents;
   }

   /**
    * used to pass configured properties to Plugin
    */
   @Override
   public void init(Map<String, String> properties) {

      logAll = Boolean.parseBoolean(properties.getOrDefault(LOG_ALL_EVENTS, "false"));
      logConnectionEvents = Boolean.parseBoolean(properties.getOrDefault(LOG_CONNECTION_EVENTS, "false"));
      logSessionEvents = Boolean.parseBoolean(properties.getOrDefault(LOG_SESSION_EVENTS, "false"));
      logConsumerEvents = Boolean.parseBoolean(properties.getOrDefault(LOG_CONSUMER_EVENTS, "false"));
      logDeliveringEvents = Boolean.parseBoolean(properties.getOrDefault(LOG_DELIVERING_EVENTS, "false"));
      logSendingEvents = Boolean.parseBoolean(properties.getOrDefault(LOG_SENDING_EVENTS, "false"));
      logInternalEvents = Boolean.parseBoolean(properties.getOrDefault(LOG_INTERNAL_EVENTS, "false"));

      dumpConfiguration();
   }

   /**
    * A connection has been created.
    *
    * @param connection The newly created connection
    */
   @Override
   public void afterCreateConnection(RemotingConnection connection) throws ActiveMQException {
      if (logAll || logConnectionEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterCreateConnection(connection);
      }
   }

   /**
    * A connection has been destroyed.
    */
   @Override
   public void afterDestroyConnection(RemotingConnection connection) throws ActiveMQException {
      if (logAll || logConnectionEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterDestroyConnection(connection);
      }
   }

   /**
    * Before a session is created.
    */
   @Override
   public void beforeCreateSession(String name,
                                   String username,
                                   int minLargeMessageSize,
                                   RemotingConnection connection,
                                   boolean autoCommitSends,
                                   boolean autoCommitAcks,
                                   boolean preAcknowledge,
                                   boolean xa,
                                   String publicAddress,
                                   SessionCallback callback,
                                   boolean autoCreateQueues,
                                   OperationContext context,
                                   Map<SimpleString, RoutingType> prefixes) throws ActiveMQException {

      if (logAll || logSessionEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeCreateSession(name, username, minLargeMessageSize, connection, autoCommitSends, autoCommitAcks, preAcknowledge, xa, publicAddress);
      }

   }

   /**
    * After a session has been created.
    *
    * @param session The newly created session
    */
   @Override
   public void afterCreateSession(ServerSession session) throws ActiveMQException {
      if (logAll || logSessionEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterCreateSession((session == null ? UNAVAILABLE : session.getName()), (session == null ? UNAVAILABLE : session.getConnectionID()), getRemoteAddress(session));
      }

   }

   private String getRemoteAddress(ServerSession session) {
      if (session == null) {
         return null;
      }

      RemotingConnection remotingConnection = session.getRemotingConnection();
      if (remotingConnection == null) {
         return null;
      }

      return remotingConnection.getRemoteAddress();
   }

   /**
    * Before a session is closed
    */
   @Override
   public void beforeCloseSession(ServerSession session, boolean failed) throws ActiveMQException {
      if (logAll || logSessionEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeCloseSession((session == null ? UNAVAILABLE : session.getName()), session, failed);
      }
   }

   /**
    * After a session is closed
    */
   @Override
   public void afterCloseSession(ServerSession session, boolean failed) throws ActiveMQException {
      if (logAll || logSessionEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterCloseSession((session == null ? UNAVAILABLE : session.getName()), failed, getRemoteAddress(session));
      }
   }

   /**
    * Before session metadata is added to the session
    */
   @Override
   public void beforeSessionMetadataAdded(ServerSession session, String key, String data) throws ActiveMQException {
      if (logAll || logSessionEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeSessionMetadataAdded((session == null ? UNAVAILABLE : session.getName()), session, key, data);
      }
   }

   /**
    * After session metadata is added to the session
    */
   @Override
   public void afterSessionMetadataAdded(ServerSession session, String key, String data) throws ActiveMQException {
      if (logAll || logSessionEvents) {

         //Details - debug level
         LoggingActiveMQServerPluginLogger.LOGGER.afterSessionMetadataAddedDetails((session == null ? UNAVAILABLE : session.getName()), session, key, data);

         // info level  log
         LoggingActiveMQServerPluginLogger.LOGGER.afterSessionMetadataAdded((session == null ? UNAVAILABLE : session.getName()), key, data);
      }
   }

   /**
    * Before a consumer is created
    */
   @Override
   public void beforeCreateConsumer(long consumerID,
                                    QueueBinding queueBinding,
                                    SimpleString filterString,
                                    boolean browseOnly,
                                    boolean supportLargeMessage) throws ActiveMQException {

      if (logAll || logConsumerEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeCreateConsumer(Long.toString(consumerID), queueBinding, filterString, browseOnly, supportLargeMessage);
      }

   }

   /**
    * After a consumer has been created
    *
    * @param consumer the created consumer
    */
   @Override
   public void afterCreateConsumer(ServerConsumer consumer) throws ActiveMQException {

      if (logAll || logConsumerEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterCreateConsumer((consumer == null ? UNAVAILABLE : Long.toString(consumer.getID())), (consumer == null ? UNAVAILABLE : consumer.getSessionID()));
      }

   }

   /**
    * Before a consumer is closed
    */
   @Override
   public void beforeCloseConsumer(ServerConsumer consumer, boolean failed) throws ActiveMQException {

      if (logAll || logConsumerEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeCloseConsumer(consumer, (consumer == null ? UNAVAILABLE : consumer.getSessionID()), failed);
      }
   }

   /**
    * After a consumer is closed
    */
   @Override
   public void afterCloseConsumer(ServerConsumer consumer, boolean failed) throws ActiveMQException {

      if (logAll || logConsumerEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterCloseConsumer((consumer == null ? UNAVAILABLE : Long.toString(consumer.getID())), (consumer == null ? UNAVAILABLE : consumer.getSessionID()), failed);
      }

   }

   /**
    * Before a queue is created
    */
   @Override
   public void beforeCreateQueue(QueueConfiguration queueConfig) throws ActiveMQException {
      if (logAll || logInternalEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeCreateQueue(queueConfig);
      }
   }

   /**
    * After a queue has been created
    *
    * @param queue The newly created queue
    */
   @Override
   public void afterCreateQueue(Queue queue) throws ActiveMQException {
      if (logAll || logInternalEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterCreateQueue(queue);
      }
   }

   /**
    * Before a queue is destroyed
    */
   @Override
   public void beforeDestroyQueue(SimpleString queueName,
                                  final SecurityAuth session,
                                  boolean checkConsumerCount,
                                  boolean removeConsumers,
                                  boolean autoDeleteAddress) throws ActiveMQException {
      if (logAll || logInternalEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeDestroyQueue(queueName, session, checkConsumerCount, removeConsumers, autoDeleteAddress);
      }

   }

   /**
    * After a queue has been destroyed
    */
   @Override
   public void afterDestroyQueue(Queue queue,
                                 SimpleString address,
                                 final SecurityAuth session,
                                 boolean checkConsumerCount,
                                 boolean removeConsumers,
                                 boolean autoDeleteAddress) throws ActiveMQException {

      if (logAll || logInternalEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterDestroyQueue(queue, address, session, checkConsumerCount, removeConsumers, autoDeleteAddress);
      }

   }

   /**
    * Before a message is sent
    *
    * @param session the session that sends the message
    */
   @Override
   public void beforeSend(ServerSession session,
                          Transaction tx,
                          Message message,
                          boolean direct,
                          boolean noAutoCreateQueue) throws ActiveMQException {

      if (logAll || logSendingEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeSend(message, tx, session, direct, noAutoCreateQueue);
      }
   }

   /**
    * After a message is sent
    */
   @Override
   public void afterSend(ServerSession session,
                         Transaction tx,
                         Message message,
                         boolean direct,
                         boolean noAutoCreateQueue,
                         RoutingStatus result) throws ActiveMQException {
      if (logAll || logDeliveringEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterSendDetails(message,
                                                                   result.toString(),
                                                                   tx,
                                                                   (session == null ? UNAVAILABLE : session.getName()),
                                                                   (session == null ? UNAVAILABLE : session.getConnectionID().toString()),
                                                                   direct,
                                                                   noAutoCreateQueue);
         if (tx != null) {
            tx.addOperation(new TransactionOperationAbstract() {
               @Override
               public void afterCommit(Transaction tx) {
                  logSend(tx, message, result);
               }

               @Override
               public void afterRollback(Transaction tx) {
                  LoggingActiveMQServerPluginLogger.LOGGER.rolledBackTransaction(tx, message.toString());
               }
            });
         } else {
            logSend(tx, message, result);
         }
      }
   }

   private void logSend(Transaction tx,
                        Message message,
                        RoutingStatus result) {
      LoggingActiveMQServerPluginLogger.LOGGER.afterSend((message == null ? UNAVAILABLE : Long.toString(message.getMessageID())),
                                                         result,
                                                         (tx == null ? UNAVAILABLE : tx.toString()));
   }

   @Override
   public void onSendException(ServerSession session,
                               Transaction tx,
                               Message message,
                               boolean direct,
                               boolean noAutoCreateQueue,
                               Exception e) throws ActiveMQException {
      if (logAll || logSendingEvents) {

         //details - debug level
         LoggingActiveMQServerPluginLogger.LOGGER.onSendErrorDetails((message == null ? UNAVAILABLE : Long.toString(message.getMessageID())), message, (session == null ? UNAVAILABLE : session.getName()), tx, session, direct, noAutoCreateQueue);

         //info level log
         LoggingActiveMQServerPluginLogger.LOGGER.onSendError((message == null ? UNAVAILABLE : Long.toString(message.getMessageID())), (session == null ? UNAVAILABLE : session.getName()), (session == null ? UNAVAILABLE : session.getConnectionID().toString()), e);
      }
   }

   /**
    * Before a message is routed
    */
   @Override
   public void beforeMessageRoute(Message message,
                                  RoutingContext context,
                                  boolean direct,
                                  boolean rejectDuplicates) throws ActiveMQException {
      if (logAll || logSendingEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeMessageRoute(message, context, direct, rejectDuplicates);
      }
   }

   /**
    * After a message is routed
    */
   @Override
   public void afterMessageRoute(Message message,
                                 RoutingContext context,
                                 boolean direct,
                                 boolean rejectDuplicates,
                                 RoutingStatus result) throws ActiveMQException {
      if (logAll || logSendingEvents) {

         //details - debug level logging
         LoggingActiveMQServerPluginLogger.LOGGER.afterMessageRouteDetails(message, context, direct, rejectDuplicates);

         //info level log
         LoggingActiveMQServerPluginLogger.LOGGER.afterMessageRoute((message == null ? UNAVAILABLE : Long.toString(message.getMessageID())), result);

      }
   }

   @Override
   public void onMessageRouteException(Message message,
                                       RoutingContext context,
                                       boolean direct,
                                       boolean rejectDuplicates,
                                       Exception e) throws ActiveMQException {
      if (logAll || logSendingEvents) {

         //details - debug level logging
         LoggingActiveMQServerPluginLogger.LOGGER.onMessageRouteErrorDetails(message, context, direct, rejectDuplicates);

         //info level log
         LoggingActiveMQServerPluginLogger.LOGGER.onMessageRouteError((message == null ? UNAVAILABLE : Long.toString(message.getMessageID())), e);

      }
   }

   /**
    * Before a message is delivered to a client consumer
    *
    * @param consumer  the consumer the message will be delivered to
    * @param reference message reference
    */
   @Override
   public void beforeDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {

      if (logAll || logDeliveringEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeDeliver(consumer, reference);
      }
   }

   /**
    * After a message is delivered to a client consumer
    *
    * @param consumer  the consumer the message was delivered to
    * @param reference message reference
    */
   @Override
   public void afterDeliver(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {

      if (logAll || logDeliveringEvents) {
         Message message = (reference == null ? null : reference.getMessage());

         if (consumer == null) {
            // log at info level and exit
            LoggingActiveMQServerPluginLogger.LOGGER.afterDeliverNoConsumer((message == null ? UNAVAILABLE : Long.toString(message.getMessageID())));
            return;
         }

         //details --- debug level
         LoggingActiveMQServerPluginLogger.LOGGER.afterDeliverDetails((message == null ? UNAVAILABLE : Long.toString(message.getMessageID())), consumer.getQueueAddress(), consumer.getQueueName(), consumer.getSessionID(), consumer.getID(), reference, consumer);

         //info level log
         LoggingActiveMQServerPluginLogger.LOGGER.afterDeliver((message == null ? UNAVAILABLE : Long.toString(message.getMessageID())), consumer.getQueueAddress(), consumer.getQueueName(), consumer.getSessionID(), consumer.getID());
      }
   }

   @Override
   public void messageExpired(MessageReference message, SimpleString messageExpiryAddress, ServerConsumer consumer) {
      if (logAll || logInternalEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.messageExpired(message, messageExpiryAddress);
      }
   }

   /**
    * A message has been acknowledged
    *
    * @param tx       The transaction for the ack
    * @param ref      The acked message
    * @param reason   The ack reason
    * @param consumer The consumer acking the ref
    */
   @Override
   public void messageAcknowledged(final Transaction tx, final MessageReference ref, final AckReason reason, final ServerConsumer consumer) throws ActiveMQException {
      if (logAll || logDeliveringEvents) {
         Message message = (ref == null ? null : ref.getMessage());
         Queue queue = (ref == null ? null : ref.getQueue());

         LoggingActiveMQServerPluginLogger.LOGGER.messageAcknowledgedDetails((message == null ? UNAVAILABLE : Long.toString(message.getMessageID())),
                                                                             (consumer == null ? UNAVAILABLE : consumer.getSessionID()),
                                                                             (consumer == null ? UNAVAILABLE : Long.toString(consumer.getID())),
                                                                             (queue == null ? UNAVAILABLE : queue.getName().toString()),
                                                                             (tx == null ? UNAVAILABLE : tx.toString()),
                                                                             reason);
         if (tx != null) {
            tx.addOperation(new TransactionOperationAbstract() {
               @Override
               public void afterCommit(Transaction tx) {
                  logAck(tx, ref);
               }

               @Override
               public void afterRollback(Transaction tx) {
                  LoggingActiveMQServerPluginLogger.LOGGER.rolledBackTransaction(tx, ref.toString());
               }
            });
         } else {
            logAck(tx, ref);
         }
      }
   }

   private void logAck(Transaction tx, MessageReference ref) {
      LoggingActiveMQServerPluginLogger.LOGGER.messageAcknowledged(ref, tx);
   }

   /**
    * Before a bridge is deployed
    *
    * @param config The bridge configuration
    */
   @Override
   public void beforeDeployBridge(BridgeConfiguration config) throws ActiveMQException {
      if (logAll || logInternalEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.beforeDeployBridge(config);
      }
   }

   /**
    * After a bridge has been deployed
    *
    * @param bridge The newly deployed bridge
    */
   @Override
   public void afterDeployBridge(Bridge bridge) throws ActiveMQException {
      if (logAll || logInternalEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.afterDeployBridge(bridge);
      }
   }

   /**
    * A Critical failure has been detected.
    * This will be called before the broker is stopped
    */
   @Override
   public void criticalFailure(CriticalComponent components) throws ActiveMQException {
      if (logAll || logInternalEvents) {
         LoggingActiveMQServerPluginLogger.LOGGER.criticalFailure(components);
      }
   }

   /**
    * dump the configuration of the logging Plugin
    */
   private void dumpConfiguration() {
      if (logger.isDebugEnabled()) {
         logger.debug("LoggingPlugin logAll={}", logAll);
         logger.debug("LoggingPlugin logConnectionEvents={}", logConnectionEvents);
         logger.debug("LoggingPlugin logSessionEvents={}", logSessionEvents);
         logger.debug("LoggingPlugin logConsumerEvents={}", logConsumerEvents);
         logger.debug("LoggingPlugin logSendingEvents={}", logSendingEvents);
         logger.debug("LoggingPlugin logDeliveringEvents={}", logDeliveringEvents);
         logger.debug("LoggingPlugin logInternalEvents={}", logInternalEvents);
      }
   }
}
