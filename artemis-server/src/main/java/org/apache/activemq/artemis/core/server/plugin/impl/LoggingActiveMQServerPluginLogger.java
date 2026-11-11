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
package org.apache.activemq.artemis.core.server.plugin.impl;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
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
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.utils.critical.CriticalComponent;

/**
 * Logger Codes 840000 - 849999
 */
@LogBundle(projectCode = "AMQ", regexID = "84[0-9]{4}")
public interface LoggingActiveMQServerPluginLogger {

   LoggingActiveMQServerPluginLogger LOGGER = BundleFactory.newBundle(LoggingActiveMQServerPluginLogger.class, LoggingActiveMQServerPluginLogger.class.getPackage().getName());

   @LogMessage(id = 841000, value = "created connection: {}", level = LogMessage.Level.INFO)
   void afterCreateConnection(RemotingConnection connection);

   @LogMessage(id = 841001, value = "destroyed connection: {}", level = LogMessage.Level.INFO)
   void afterDestroyConnection(RemotingConnection connection);

   @LogMessage(id = 841002, value = "created session name: {}, session connectionID: {}, remote address {}", level = LogMessage.Level.INFO)
   void afterCreateSession(String sessionName, Object sesssionConnectionID, String remoteAddress);

   @LogMessage(id = 841003, value = "closed session with session name: {}, failed: {}, RemoteAddress: {}", level = LogMessage.Level.INFO)
   void afterCloseSession(String sessionName, boolean sesssionConnectionID, String remoteAddress);

   @LogMessage(id = 841004, value = "added session metadata for session name : {}, key: {}, data: {}", level = LogMessage.Level.INFO)
   void afterSessionMetadataAdded(String sessionName, String key, String data);

   @LogMessage(id = 841005, value = "created consumer with ID: {}, with session name: {}", level = LogMessage.Level.INFO)
   void afterCreateConsumer(String consumerID, String sessionID);

   @LogMessage(id = 841006, value = "closed consumer ID: {}, with  consumer Session: {}, failed: {}", level = LogMessage.Level.INFO)
   void afterCloseConsumer(String consumerID, String sessionID, boolean failed);

   @LogMessage(id = 841007, value = "created queue: {}", level = LogMessage.Level.INFO)
   void afterCreateQueue(Queue queue);

   @LogMessage(id = 841008, value = "destroyed queue: {}, with args address: {}, session: {}, checkConsumerCount: {}," + " removeConsumers: {}, autoDeleteAddress: {}", level = LogMessage.Level.INFO)
   void afterDestroyQueue(Queue queue,
                          SimpleString address,
                          SecurityAuth session,
                          boolean checkConsumerCount,
                          boolean removeConsumers,
                          boolean autoDeleteAddress);

   @LogMessage(id = 841009, value = "sent message with ID: {}, result: {}, transaction: {}", level = LogMessage.Level.INFO)
   void afterSend(String messageID, RoutingStatus result, String tx);

   @LogMessage(id = 841010, value = "routed message with ID: {}, result: {}", level = LogMessage.Level.INFO)
   void afterMessageRoute(String messageID, RoutingStatus result);

   @LogMessage(id = 841011, value = "delivered message with message ID: {}, consumer info UNAVAILABLE", level = LogMessage.Level.INFO)
   void afterDeliverNoConsumer(String messageID);

   @LogMessage(id = 841012, value = "delivered message with message ID: {}, to consumer on address: {}, queue: {}," + " consumer sessionID: {}, consumerID: {}", level = LogMessage.Level.INFO)
   void afterDeliver(String messageID,
                     SimpleString queueAddress,
                     SimpleString queueName,
                     String consumerSessionID,
                     long consumerID);

   @LogMessage(id = 841013, value = "expired message: {}, messageExpiryAddress: {}", level = LogMessage.Level.INFO)
   void messageExpired(MessageReference message, SimpleString messageExpiryAddress);

   @LogMessage(id = 841014, value = "acknowledged message: {}, with transaction: {}", level = LogMessage.Level.INFO)
   void messageAcknowledged(MessageReference ref, Transaction tx);

   @LogMessage(id = 841015, value = "deployed bridge: {}", level = LogMessage.Level.INFO)
   void afterDeployBridge(Bridge config);

   @LogMessage(id = 841016, value = "criticalFailure called with criticalComponent: {}", level = LogMessage.Level.INFO)
   void criticalFailure(CriticalComponent components);

   @LogMessage(id = 841017, value = "error sending message with ID: {}, session name: {}, session connectionID: {}," + " exception: {}", level = LogMessage.Level.INFO)
   void onSendError(String messageID, String sessionName, String sessionConnectionID, Exception e);

   @LogMessage(id = 841018, value = "error routing message with ID: {}, exception: {}", level = LogMessage.Level.INFO)
   void onMessageRouteError(String messageID, Exception e);

   //DEBUG messages

   @LogMessage(id = 843000, value = "beforeCreateSession called with name: {}, username: {}, minLargeMessageSize: {}, connection: {}," + " autoCommitSends: {}, autoCommitAcks: {}, preAcknowledge: {}, xa: {}, publicAddress: {}", level = LogMessage.Level.DEBUG)
   void beforeCreateSession(String name,
                            String username,
                            int minLargeMessageSize,
                            RemotingConnection connection,
                            boolean autoCommitSends,
                            boolean autoCommitAcks,
                            boolean preAcknowledge,
                            boolean xa,
                            String publicAddress);

   @LogMessage(id = 843001, value = "beforeCloseSession called with session name : {}, session: {}, failed: {}", level = LogMessage.Level.DEBUG)
   void beforeCloseSession(String sessionName, ServerSession session, boolean failed);

   @LogMessage(id = 843002, value = "beforeSessionMetadataAdded called with session name: {} , session: {}, key: {}," + " data: {}", level = LogMessage.Level.DEBUG)
   void beforeSessionMetadataAdded(String sessionName, ServerSession session, String key, String data);

   @LogMessage(id = 843003, value = "added session metadata for session name : {}, session: {}, key: {}, data: {}", level = LogMessage.Level.DEBUG)
   void afterSessionMetadataAddedDetails(String sessionName, ServerSession session, String key, String data);

   @LogMessage(id = 843004, value = "beforeCreateConsumer called with ConsumerID: {}, QueueBinding: {}, filterString: {}," + " browseOnly: {}, supportLargeMessage: {}", level = LogMessage.Level.DEBUG)
   void beforeCreateConsumer(String consumerID,
                             QueueBinding queueBinding,
                             SimpleString filterString,
                             boolean browseOnly,
                             boolean supportLargeMessage);

   @LogMessage(id = 843005, value = "beforeCloseConsumer called with consumer: {}, consumer sessionID: {}, failed: {}", level = LogMessage.Level.DEBUG)
   void beforeCloseConsumer(ServerConsumer consumer, String sessionID, boolean failed);

   @LogMessage(id = 843006, value = "beforeCreateQueue called with queueConfig: {}", level = LogMessage.Level.DEBUG)
   void beforeCreateQueue(QueueConfiguration queueConfig);

   @LogMessage(id = 843007, value = "beforeDestroyQueue called with queueName: {}, session: {}, checkConsumerCount: {}," + " removeConsumers: {}, autoDeleteAddress: {}", level = LogMessage.Level.DEBUG)
   void beforeDestroyQueue(SimpleString queueName,
                           SecurityAuth session,
                           boolean checkConsumerCount,
                           boolean removeConsumers,
                           boolean autoDeleteAddress);

   @LogMessage(id = 843008, value = "beforeSend called with message: {}, tx: {}, session: {}, direct: {}," + " noAutoCreateQueue: {}", level = LogMessage.Level.DEBUG)
   void beforeSend(org.apache.activemq.artemis.api.core.Message message,
                   Transaction tx,
                   ServerSession session,
                   boolean direct,
                   boolean noAutoCreateQueue);

   @LogMessage(id = 843009, value = "afterSend message: {}, result: {}, transaction: {}, session: {}, connection: {}, direct: {}, noAutoCreateQueue: {}", level = LogMessage.Level.DEBUG)
   void afterSendDetails(org.apache.activemq.artemis.api.core.Message message,
                         String result,
                         Transaction tx,
                         String sessionName,
                         String connectionID,
                         boolean direct,
                         boolean noAutoCreateQueue);

   @LogMessage(id = 843010, value = "beforeMessageRoute called with message: {}, context: {}, direct: {}, rejectDuplicates: {}", level = LogMessage.Level.DEBUG)
   void beforeMessageRoute(org.apache.activemq.artemis.api.core.Message message,
                           RoutingContext context,
                           boolean direct,
                           boolean rejectDuplicates);

   @LogMessage(id = 843011, value = "afterMessageRoute message: {}, with context: {}, direct: {}, rejectDuplicates: {}", level = LogMessage.Level.DEBUG)
   void afterMessageRouteDetails(org.apache.activemq.artemis.api.core.Message message,
                                 RoutingContext context,
                                 boolean direct,
                                 boolean rejectDuplicates);

   @LogMessage(id = 843012, value = "beforeDeliver called with consumer: {}, reference: {}", level = LogMessage.Level.DEBUG)
   void beforeDeliver(ServerConsumer consumer, MessageReference reference);

   @LogMessage(id = 843013, value = "delivered message with message ID: {} to consumer on address: {}, queue: {}, consumer sessionID: {}," + " consumerID: {}, full message reference: {}, full consumer: {}", level = LogMessage.Level.DEBUG)
   void afterDeliverDetails(String messageID,
                            SimpleString queueAddress,
                            SimpleString queueName,
                            String consumerSessionID,
                            long consumerID,
                            MessageReference reference,
                            ServerConsumer consumer);

   @LogMessage(id = 843014, value = "messageAcknowledged ID: {}, sessionID: {}, consumerID: {}, queue: {}, transaction: {}, ackReason: {}", level = LogMessage.Level.DEBUG)
   void messageAcknowledgedDetails(String messageID, String sessionID, String consumerID, String queueName, String tx, AckReason reason);

   @LogMessage(id = 843015, value = "beforeDeployBridge called with bridgeConfiguration: {}", level = LogMessage.Level.DEBUG)
   void beforeDeployBridge(BridgeConfiguration config);

   @LogMessage(id = 843016, value = "onSendError message ID: {}, message {}, session name: {} with tx: {}, session: {}, direct: {}," + " noAutoCreateQueue: {}", level = LogMessage.Level.DEBUG)
   void onSendErrorDetails(String messageID,
                           org.apache.activemq.artemis.api.core.Message message,
                           String sessionName,
                           Transaction tx,
                           ServerSession session,
                           boolean direct,
                           boolean noAutoCreateQueue);

   @LogMessage(id = 843017, value = "onMessageRouteError message: {}, with context: {}, direct: {}, rejectDuplicates: {}", level = LogMessage.Level.DEBUG)
   void onMessageRouteErrorDetails(org.apache.activemq.artemis.api.core.Message message,
                                   RoutingContext context,
                                   boolean direct,
                                   boolean rejectDuplicates);

   @LogMessage(id = 843018, value = "rolled back transaction {} involving {}", level = LogMessage.Level.DEBUG)
   void rolledBackTransaction(Transaction tx, String resource);
}
