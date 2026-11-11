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
package org.apache.activemq.artemis.jms.server;

import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.spi.core.naming.BindingRegistry;

/**
 * The JMS Management interface.
 */
@Deprecated
public interface JMSServerManager extends ActiveMQComponent {

   String getVersion();

   /**
    * Has the Server been started.
    *
    * @return true if the server us running
    */
   @Override
   boolean isStarted();

   /**
    * Creates a JMS Queue.
    *
    * @param queueName The name of the queue to create
    * @return true if the queue is created or if it existed and was added to the Binding Registry
    * @throws Exception if problems were encountered creating the queue.
    */
   boolean createQueue(boolean storeConfig, String queueName, String selectorString, boolean durable, String... bindings) throws Exception;

   /**
    * Creates a JMS Queue.
    *
    * @param queueName    The name of the core queue to create
    * @param jmsQueueName the name of this JMS queue
    * @return true if the queue is created or if it existed and was added to the Binding Registry
    * @throws Exception if problems were encountered creating the queue.
    */
   boolean createQueue(boolean storeConfig, String queueName, String jmsQueueName, String selectorString, boolean durable, String... bindings) throws Exception;

   boolean addTopicToBindingRegistry(String topicName, String binding) throws Exception;

   boolean addQueueToBindingRegistry(String queueName, String binding) throws Exception;

   boolean addConnectionFactoryToBindingRegistry(String name, String binding) throws Exception;

   /**
    * Creates a JMS Topic
    *
    * @param address  the core addres of thetopic
    * @param bindings the names of the binding for the Binding Registry or BindingRegistry
    * @return true if the topic was created or if it existed and was added to the Binding Registry
    * @throws Exception if a problem occurred creating the topic
    */
   boolean createTopic(boolean storeConfig, String address, String... bindings) throws Exception;

   /**
    * Creates a JMS Topic
    *
    * @param address   the core addres of thetopic
    * @param topicName the name of the topic
    * @param bindings  the names of the binding for the Binding Registry or BindingRegistry
    * @return true if the topic was created or if it existed and was added to the Binding Registry
    * @throws Exception if a problem occurred creating the topic
    */
   boolean createTopic(String address, boolean storeConfig, String topicName, String... bindings) throws Exception;

   boolean createTopic(boolean storeConfig, String address, boolean autoCreated, String... bindings) throws Exception;

   boolean createTopic(boolean storeConfig, String address, String topicName, boolean autoCreated, String... bindings) throws Exception;

   /**
    * Remove the topic from the Binding Registry or BindingRegistry. Calling this method does <em>not</em> destroy the
    * destination.
    *
    * @param name the name of the destination to remove from the BindingRegistry
    * @return true if removed
    * @throws Exception if a problem occurred removing the destination
    */
   boolean removeTopicFromBindingRegistry(String name, String binding) throws Exception;

   /**
    * Remove the topic from the BindingRegistry. Calling this method does <em>not</em> destroy the destination.
    *
    * @param name the name of the destination to remove from the BindingRegistry
    * @return true if removed
    * @throws Exception if a problem occurred removing the destination
    */
   boolean removeTopicFromBindingRegistry(String name) throws Exception;

   /**
    * Remove the queue from the BindingRegistry. Calling this method does <em>not</em> destroy the destination.
    *
    * @param name the name of the destination to remove from the BindingRegistry
    * @return true if removed
    * @throws Exception if a problem occurred removing the destination
    */
   boolean removeQueueFromBindingRegistry(String name, String binding) throws Exception;

   /**
    * Remove the queue from the BindingRegistry. Calling this method does <em>not</em> destroy the destination.
    *
    * @param name the name of the destination to remove from the BindingRegistry
    * @return true if removed
    * @throws Exception if a problem occurred removing the destination
    */
   boolean removeQueueFromBindingRegistry(String name) throws Exception;

   boolean removeConnectionFactoryFromBindingRegistry(String name, String binding) throws Exception;

   boolean removeConnectionFactoryFromBindingRegistry(String name) throws Exception;

   /**
    * destroys a queue and removes it from the BindingRegistry
    *
    * @param name the name of the queue to destroy
    * @return true if destroyed
    * @throws Exception if a problem occurred destroying the queue
    */
   boolean destroyQueue(String name) throws Exception;

   /**
    * destroys a queue and removes it from the BindingRegistry. disconnects any consumers connected to the queue.
    *
    * @param name the name of the queue to destroy
    * @return true if destroyed
    * @throws Exception if a problem occurred destroying the queue
    */
   boolean destroyQueue(String name, boolean removeConsumers) throws Exception;

   String[] getBindingsOnQueue(String queue);

   String[] getBindingsOnTopic(String topic);

   String[] getBindingsOnConnectionFactory(String factoryName);

   /**
    * destroys a topic and removes it from the BindingRegistry
    *
    * @param name the name of the topic to destroy
    * @return true if the topic was destroyed
    * @throws Exception if a problem occurred destroying the topic
    */
   boolean destroyTopic(String name, boolean removeConsumers) throws Exception;

   /**
    * destroys a topic and removes it from theBindingRegistry
    *
    * @param name the name of the topic to destroy
    * @return true if the topic was destroyed
    * @throws Exception if a problem occurred destroying the topic
    */
   boolean destroyTopic(String name) throws Exception;

   /**
    * Call this method to have a CF rebound to the Binding Registry and stored on the Journal
    */
   ActiveMQConnectionFactory recreateCF(String name, ConnectionFactoryConfiguration cf) throws Exception;

   void createConnectionFactory(String name,
                                boolean ha,
                                JMSFactoryType cfType,
                                String discoveryGroupName,
                                String... bindings) throws Exception;

   void createConnectionFactory(String name,
                                boolean ha,
                                JMSFactoryType cfType,
                                List<String> connectorNames,
                                String... bindings) throws Exception;

   void createConnectionFactory(String name,
                                boolean ha,
                                JMSFactoryType cfType,
                                List<String> connectorNames,
                                String clientID,
                                long clientFailureCheckPeriod,
                                long connectionTTL,
                                long callTimeout,
                                long callFailoverTimeout,
                                boolean cacheLargeMessagesClient,
                                int minLargeMessageSize,
                                boolean compressLargeMessage,
                                int compressionLevel,
                                int consumerWindowSize,
                                int consumerMaxRate,
                                int confirmationWindowSize,
                                int producerWindowSize,
                                int producerMaxRate,
                                boolean blockOnAcknowledge,
                                boolean blockOnDurableSend,
                                boolean blockOnNonDurableSend,
                                boolean autoGroup,
                                boolean preAcknowledge,
                                String loadBalancingPolicyClassName,
                                int transactionBatchSize,
                                int dupsOKBatchSize,
                                boolean useGlobalPools,
                                int scheduledThreadPoolMaxSize,
                                int threadPoolMaxSize,
                                long retryInterval,
                                double retryIntervalMultiplier,
                                long maxRetryInterval,
                                int reconnectAttempts,
                                boolean failoverOnInitialConnection,
                                String groupId,
                                String... bindings) throws Exception;

   void createConnectionFactory(String name,
                                boolean ha,
                                JMSFactoryType cfType,
                                String discoveryGroupName,
                                String clientID,
                                long clientFailureCheckPeriod,
                                long connectionTTL,
                                long callTimeout,
                                long callFailoverTimeout,
                                boolean cacheLargeMessagesClient,
                                int minLargeMessageSize,
                                boolean compressLargeMessages,
                                int compressionLevel,
                                int consumerWindowSize,
                                int consumerMaxRate,
                                int confirmationWindowSize,
                                int producerWindowSize,
                                int producerMaxRate,
                                boolean blockOnAcknowledge,
                                boolean blockOnDurableSend,
                                boolean blockOnNonDurableSend,
                                boolean autoGroup,
                                boolean preAcknowledge,
                                String loadBalancingPolicyClassName,
                                int transactionBatchSize,
                                int dupsOKBatchSize,
                                boolean useGlobalPools,
                                int scheduledThreadPoolMaxSize,
                                int threadPoolMaxSize,
                                long retryInterval,
                                double retryIntervalMultiplier,
                                long maxRetryInterval,
                                int reconnectAttempts,
                                boolean failoverOnInitialConnection,
                                String groupId,
                                String... bindings) throws Exception;

   void createConnectionFactory(boolean storeConfig,
                                ConnectionFactoryConfiguration cfConfig,
                                String... bindings) throws Exception;

   /**
    * destroys a connection factory.
    *
    * @param name the name of the connection factory to destroy
    * @return true if the connection factory was destroyed
    * @throws Exception if a problem occurred destroying the connection factory
    */
   boolean destroyConnectionFactory(String name) throws Exception;

   String[] listRemoteAddresses() throws Exception;

   String[] listRemoteAddresses(String ipAddress) throws Exception;

   boolean closeConnectionsForAddress(String ipAddress) throws Exception;

   boolean closeConsumerConnectionsForAddress(String address) throws Exception;

   boolean closeConnectionsForUser(String address) throws Exception;

   String[] listConnectionIDs() throws Exception;

   String[] listSessions(String connectionID) throws Exception;

   String listSessionsAsJSON(String connectionID) throws Exception;

   String listPreparedTransactionDetailsAsJSON() throws Exception;

   @Deprecated
   String listPreparedTransactionDetailsAsHTML() throws Exception;

   ActiveMQServer getActiveMQServer();

   void addAddressSettings(String address, AddressSettings addressSettings);

   AddressSettings getAddressSettings(String address);

   void addSecurity(String addressMatch, Set<Role> roles);

   Set<Role> getSecurity(String addressMatch);

   BindingRegistry getRegistry();

   /**
    * Set this property if you want JMS resources bound to a registry
    */
   void setRegistry(BindingRegistry registry);
}
