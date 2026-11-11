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
package org.apache.activemq.artemis.api.core.client;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.config.ServerLocatorConfig;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManagerFactory;

/**
 * The serverLocator locates a server, but beyond that it locates a server based on a list.
 * <p>
 * If you are using straight TCP on the configuration, and if you configure your serverLocator to be HA, the locator
 * will always get an updated list of members to the server, the server will send the updated list to the client.
 * <p>
 * If you use UDP or JGroups (exclusively JGroups or UDP), the initial discovery is done by the grouping finder, after
 * the initial connection is made the server will always send updates to the client. But the listeners will listen for
 * updates on grouping.
 */
public interface ServerLocator extends AutoCloseable {

   /**
    * {@return {@code true} if closed, {@code false} otherwise}
    */
   boolean isClosed();

   /**
    * Just kept for compatibility.
    */
   @Deprecated
   default void disableFinalizeCheck() {
   }

   /**
    * Creates a ClientSessionFactory using whatever load balancing policy is in force
    *
    * @return The ClientSessionFactory
    */
   ClientSessionFactory createSessionFactory() throws Exception;

   /**
    * Creates a ClientSessionFactory using whatever load balancing policy is in force
    *
    * @param connectionUser connection user
    * @param connectionPassword connection password
    * @return The ClientSessionFactory
    */
   ClientSessionFactory createSessionFactory(String connectionUser, String connectionPassword) throws Exception;

   /**
    * Creates a {@link ClientSessionFactory} to a specific server. The server must already be known about by this
    * ServerLocator. This method allows the user to make a connection to a specific server bypassing any load balancing
    * policy in force
    *
    * @return a ClientSessionFactory instance or {@code null} if the node is not present on the topology
    * @throws Exception if a failure happened in creating the ClientSessionFactory or the ServerLocator does not know
    *                   about the passed in transportConfiguration
    */
   ClientSessionFactory createSessionFactory(String nodeID) throws Exception;

   /**
    * Creates a {@link ClientSessionFactory} to a specific server. The server must already be known about by this
    * ServerLocator. This method allows the user to make a connection to a specific server bypassing any load balancing
    * policy in force
    *
    * @return a {@link ClientSessionFactory} instance
    * @throws Exception if a failure happened in creating the ClientSessionFactory or the ServerLocator does not know
    *                   about the passed in transportConfiguration
    */
   ClientSessionFactory createSessionFactory(TransportConfiguration transportConfiguration) throws Exception;

   /**
    * Creates a {@link ClientSessionFactory} to a specific server. The server must already be known about by this
    * ServerLocator. This method allows the user to make a connection to a specific server bypassing any load balancing
    * policy in force
    *
    * @param reconnectAttempts number of attempts of reconnection to perform
    * @return a {@link ClientSessionFactory} instance
    * @throws Exception if a failure happened in creating the ClientSessionFactory or the ServerLocator does not know
    *                   about the passed in transportConfiguration
    */
   ClientSessionFactory createSessionFactory(TransportConfiguration transportConfiguration,
                                             int reconnectAttempts) throws Exception;

   /**
    * Creates a {@link ClientSessionFactory} to a specific server. The server must already be known about by this
    * ServerLocator. This method allows the user to make a connection to a specific server bypassing any load balancing
    * policy in force
    *
    * @param reconnectAttempts number of attempts of reconnection to perform
    * @return a {@link ClientSessionFactory} instance
    * @throws Exception if a failure happened in creating the ClientSessionFactory or the ServerLocator does not know
    *                   about the passed in transportConfiguration
    * @deprecated This method is no longer acceptable to create a client session factory. Replaced by
    * {@link ServerLocator#createSessionFactory(TransportConfiguration, int)}.
    */
   @Deprecated
   ClientSessionFactory createSessionFactory(TransportConfiguration transportConfiguration,
                                             int reconnectAttempts,
                                             boolean failoverOnInitialConnection) throws Exception;

   /**
    * Returns the period used to check if a client has failed to receive pings from the server.
    * <p>
    * Period is in milliseconds, default value is {@link ActiveMQClient#DEFAULT_CLIENT_FAILURE_CHECK_PERIOD}.
    *
    * @return the period used to check if a client has failed to receive pings from the server
    */
   long getClientFailureCheckPeriod();

   /**
    * Sets the period (in milliseconds) used to check if a client has failed to receive pings from the server.
    * <p>
    * Value must be -1 (to disable) or greater than 0.
    *
    * @param clientFailureCheckPeriod the period to check failure
    * @return this ServerLocator
    */
   ServerLocator setClientFailureCheckPeriod(long clientFailureCheckPeriod);

   /**
    * When {@code true}, consumers created through this factory will create temporary files to cache large messages.
    * <p>
    * There is 1 temporary file created for each large message.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_CACHE_LARGE_MESSAGE_CLIENT}.
    *
    * @return {@code true} if consumers created through this factory will cache large messages in temporary files,
    * {@code false} else
    */
   boolean isCacheLargeMessagesClient();

   /**
    * Sets whether large messages received by consumers created through this factory will be cached in temporary files
    * or not.
    *
    * @param cached {@code true} to cache large messages in temporary files, {@code false} else
    * @return this ServerLocator
    */
   ServerLocator setCacheLargeMessagesClient(boolean cached);

   /**
    * Returns the connection <em>time-to-live</em>.
    * <p>
    * This TTL determines how long the server will keep a connection alive in the absence of any data arriving from the
    * client. Value is in milliseconds, default value is {@link ActiveMQClient#DEFAULT_CONNECTION_TTL}.
    *
    * @return the connection time-to-live in milliseconds
    */
   long getConnectionTTL();

   /**
    * Sets this factory's connections <em>time-to-live</em>.
    * <p>
    * Value must be -1 (to disable) or greater or equals to 0.
    *
    * @param connectionTTL period in milliseconds
    * @return this ServerLocator
    */
   ServerLocator setConnectionTTL(long connectionTTL);

   /**
    * Returns the blocking calls timeout.
    * <p>
    * If client's blocking calls to the server take more than this timeout, the call will throw a
    * {@link org.apache.activemq.artemis.api.core.ActiveMQException} with the code
    * {@link org.apache.activemq.artemis.api.core.ActiveMQExceptionType#CONNECTION_TIMEDOUT}. Value is in milliseconds,
    * default value is {@link ActiveMQClient#DEFAULT_CALL_TIMEOUT}.
    *
    * @return the blocking calls timeout
    */
   long getCallTimeout();

   /**
    * Sets the blocking call timeout.
    * <p>
    * Value must be greater or equals to 0
    *
    * @param callTimeout blocking call timeout in milliseconds
    * @return this ServerLocator
    */
   ServerLocator setCallTimeout(long callTimeout);

   /**
    * Returns the blocking calls failover timeout when the client is awaiting failover, this is over and above the
    * normal call timeout.
    * <p>
    * If client is in the process of failing over when a blocking call is called then the client will wait this long
    * before actually trying the send.
    *
    * @return the blocking calls failover timeout
    */
   long getCallFailoverTimeout();

   /**
    * Sets the blocking call failover timeout.
    * <p>
    * When the client is awaiting failover, this is over and above the normal call timeout.
    * <p>
    * Value must be greater or equals to -1, -1 means forever
    *
    * @param callFailoverTimeout blocking call timeout in milliseconds
    * @return this ServerLocator
    */
   ServerLocator setCallFailoverTimeout(long callFailoverTimeout);

   /**
    * Returns the large message size threshold.
    * <p>
    * Messages whose size is if greater than this value will be handled as <em>large messages</em>. Value is in bytes,
    * default value is {@link ActiveMQClient#DEFAULT_MIN_LARGE_MESSAGE_SIZE}.
    *
    * @return the message size threshold to treat messages as large messages
    */
   int getMinLargeMessageSize();

   /**
    * Sets the large message size threshold.
    * <p>
    * Value must be greater than 0.
    *
    * @param minLargeMessageSize large message size threshold in bytes
    * @return this ServerLocator
    */
   ServerLocator setMinLargeMessageSize(int minLargeMessageSize);

   /**
    * Returns the window size for flow control of the consumers created through this factory.
    * <p>
    * Value is in bytes, default value is {@link ActiveMQClient#DEFAULT_CONSUMER_WINDOW_SIZE}.
    *
    * @return the window size used for consumer flow control
    */
   int getConsumerWindowSize();

   /**
    * Sets the window size for flow control of the consumers created through this factory.
    * <p>
    * Value must be -1 (to disable flow control), 0 (to not buffer any messages) or greater than 0 (to set the maximum
    * size of the buffer)
    *
    * @param consumerWindowSize window size (in bytes) used for consumer flow control
    * @return this ServerLocator
    */
   ServerLocator setConsumerWindowSize(int consumerWindowSize);

   /**
    * Returns the maximum rate of message consumption for consumers created through this factory.
    * <p>
    * This value controls the rate at which a consumer can consume messages. A consumer will never consume messages at a
    * rate faster than the rate specified.
    * <p>
    * Value is -1 (to disable) or a positive integer corresponding to the maximum desired message consumption rate
    * specified in units of messages per second. Default value is {@link ActiveMQClient#DEFAULT_CONSUMER_MAX_RATE}.
    *
    * @return the consumer max rate
    */
   int getConsumerMaxRate();

   /**
    * Sets the maximum rate of message consumption for consumers created through this factory.
    * <p>
    * Value must -1 (to disable) or a positive integer corresponding to the maximum desired message consumption rate
    * specified in units of messages per second.
    *
    * @param consumerMaxRate maximum rate of message consumption (in messages per seconds)
    * @return this ServerLocator
    */
   ServerLocator setConsumerMaxRate(int consumerMaxRate);

   /**
    * Returns the size for the confirmation window of clients using this factory.
    * <p>
    * Value is in bytes or -1 (to disable the window). Default value is
    * {@link ActiveMQClient#DEFAULT_CONFIRMATION_WINDOW_SIZE}.
    *
    * @return the size for the confirmation window of clients using this factory
    */
   int getConfirmationWindowSize();

   /**
    * Sets the size for the confirmation window buffer of clients using this factory.
    * <p>
    * Value must be -1 (to disable the window) or greater than 0.
    *
    * @param confirmationWindowSize size of the confirmation window (in bytes)
    * @return this ServerLocator
    */
   ServerLocator setConfirmationWindowSize(int confirmationWindowSize);

   /**
    * Returns the window size for flow control of the producers created through this factory.
    * <p>
    * Value must be -1 (to disable flow control) or greater than 0 to determine the maximum amount of bytes at any give
    * time (to prevent overloading the connection). Default value is
    * {@link ActiveMQClient#DEFAULT_PRODUCER_WINDOW_SIZE}.
    *
    * @return the window size for flow control of the producers created through this factory
    */
   int getProducerWindowSize();

   /**
    * Returns the window size for flow control of the producers created through this factory.
    * <p>
    * Value must be -1 (to disable flow control) or greater than 0.
    *
    * @param producerWindowSize window size (in bytest) for flow control of the producers created through this factory.
    * @return this ServerLocator
    */
   ServerLocator setProducerWindowSize(int producerWindowSize);

   /**
    * Returns the maximum rate of message production for producers created through this factory.
    * <p>
    * This value controls the rate at which a producer can produce messages. A producer will never produce messages at a
    * rate faster than the rate specified.
    * <p>
    * Value is -1 (to disable) or a positive integer corresponding to the maximum desired message production rate
    * specified in units of messages per second. Default value is {@link ActiveMQClient#DEFAULT_PRODUCER_MAX_RATE}.
    *
    * @return maximum rate of message production (in messages per seconds)
    */
   int getProducerMaxRate();

   /**
    * Sets the maximum rate of message production for producers created through this factory.
    * <p>
    * Value must -1 (to disable) or a positive integer corresponding to the maximum desired message production rate
    * specified in units of messages per second.
    *
    * @param producerMaxRate maximum rate of message production (in messages per seconds)
    * @return this ServerLocator
    */
   ServerLocator setProducerMaxRate(int producerMaxRate);

   /**
    * Returns whether consumers created through this factory will block while sending message acknowledgments or do it
    * asynchronously.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_BLOCK_ON_ACKNOWLEDGE}.
    *
    * @return whether consumers will block while sending message acknowledgments or do it asynchronously
    */
   boolean isBlockOnAcknowledge();

   /**
    * Sets whether consumers created through this factory will block while sending message acknowledgments or do it
    * asynchronously.
    *
    * @param blockOnAcknowledge {@code true} to block when sending message acknowledgments or {@code false} to send them
    *                           asynchronously
    * @return this ServerLocator
    */
   ServerLocator setBlockOnAcknowledge(boolean blockOnAcknowledge);

   /**
    * Returns whether producers created through this factory will block while sending <em>durable</em> messages or do it
    * asynchronously.
    * <p>
    * If the session is configured to send durable message asynchronously, the client can set a
    * SendAcknowledgementHandler on the ClientSession to be notified once the message has been handled by the server.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_BLOCK_ON_DURABLE_SEND}.
    *
    * @return whether producers will block while sending persistent messages or do it asynchronously
    */
   boolean isBlockOnDurableSend();

   /**
    * Sets whether producers created through this factory will block while sending <em>durable</em> messages or do it
    * asynchronously.
    *
    * @param blockOnDurableSend {@code true} to block when sending durable messages or {@code false} to send them
    *                           asynchronously
    * @return this ServerLocator
    */
   ServerLocator setBlockOnDurableSend(boolean blockOnDurableSend);

   /**
    * Returns whether producers created through this factory will block while sending <em>non-durable</em> messages or
    * do it asynchronously.
    * <p>
    * If the session is configured to send non-durable message asynchronously, the client can set a
    * SendAcknowledgementHandler on the ClientSession to be notified once the message has been handled by the server.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_BLOCK_ON_NON_DURABLE_SEND}.
    *
    * @return whether producers will block while sending non-durable messages or do it asynchronously
    */
   boolean isBlockOnNonDurableSend();

   /**
    * Sets whether producers created through this factory will block while sending <em>non-durable</em> messages or do
    * it asynchronously.
    *
    * @param blockOnNonDurableSend {@code true} to block when sending non-durable messages or {@code false} to send them
    *                              asynchronously
    * @return this ServerLocator
    */
   ServerLocator setBlockOnNonDurableSend(boolean blockOnNonDurableSend);

   /**
    * Returns whether producers created through this factory will automatically assign a group ID to the messages they
    * sent.
    * <p>
    * if {@code true}, a random unique group ID is created and set on each message for the property
    * {@link org.apache.activemq.artemis.api.core.Message#HDR_GROUP_ID}. Default value is
    * {@link ActiveMQClient#DEFAULT_AUTO_GROUP}.
    *
    * @return whether producers will automatically assign a group ID to their messages
    */
   boolean isAutoGroup();

   /**
    * Sets whether producers created through this factory will automatically assign a group ID to the messages they
    * sent.
    *
    * @param autoGroup {@code true} to automatically assign a group ID to each messages sent through this factory,
    *                  {@code false} else
    * @return this ServerLocator
    */
   ServerLocator setAutoGroup(boolean autoGroup);

   /**
    * Returns the group ID that will be eventually set on each message for the property
    * {@link org.apache.activemq.artemis.api.core.Message#HDR_GROUP_ID}.
    * <p>
    * Default value is is {@code null} and no group ID will be set on the messages.
    *
    * @return the group ID that will be eventually set on each message
    */
   String getGroupID();

   /**
    * Sets the group ID that will be  set on each message sent through this factory.
    *
    * @param groupID the group ID to use
    * @return this ServerLocator
    */
   ServerLocator setGroupID(String groupID);

   /**
    * Returns whether messages will pre-acknowledged on the server before they are sent to the consumers or not.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_PRE_ACKNOWLEDGE}
    */
   boolean isPreAcknowledge();

   /**
    * Sets to {@code true} to pre-acknowledge consumed messages on the server before they are sent to consumers, else
    * set to {@code false} to let clients acknowledge the message they consume.
    *
    * @param preAcknowledge {@code true} to enable pre-acknowledgment, {@code false} else
    * @return this ServerLocator
    */
   ServerLocator setPreAcknowledge(boolean preAcknowledge);

   /**
    * Returns the acknowledgments batch size.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_ACK_BATCH_SIZE}.
    *
    * @return the acknowledgments batch size
    */
   int getAckBatchSize();

   /**
    * Sets the acknowledgments batch size.
    * <p>
    * Value must be equal or greater than 0.
    *
    * @param ackBatchSize acknowledgments batch size
    * @return this ServerLocator
    */
   ServerLocator setAckBatchSize(int ackBatchSize);

   /**
    * Returns an array of TransportConfigurations representing the static list of servers used when creating this
    * object
    *
    * @return array with all static {@link TransportConfiguration}s
    */
   TransportConfiguration[] getStaticTransportConfigurations();

   /**
    * The discovery group configuration
    */
   DiscoveryGroupConfiguration getDiscoveryGroupConfiguration();

   /**
    * Returns whether this factory will use global thread pools (shared among all the factories in the same JVM) or its
    * own pools.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_USE_GLOBAL_POOLS}.
    *
    * @return {@code true} if this factory uses global thread pools, {@code false} else
    */
   boolean isUseGlobalPools();

   /**
    * Sets whether this factory will use global thread pools (shared among all the factories in the same JVM) or its own
    * pools.
    *
    * @param useGlobalPools {@code true} to let this factory uses global thread pools, {@code false} else
    * @return this ServerLocator
    */
   ServerLocator setUseGlobalPools(boolean useGlobalPools);

   /**
    * Returns the maximum size of the scheduled thread pool.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE}.
    *
    * @return the maximum size of the scheduled thread pool
    */
   int getScheduledThreadPoolMaxSize();

   /**
    * Sets the maximum size of the scheduled thread pool.
    * <p>
    * This setting is relevant only if this factory does not use global pools. Value must be greater than 0.
    *
    * @param scheduledThreadPoolMaxSize maximum size of the scheduled thread pool.
    * @return this ServerLocator
    */
   ServerLocator setScheduledThreadPoolMaxSize(int scheduledThreadPoolMaxSize);

   /**
    * Returns the maximum size of the thread pool.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_THREAD_POOL_MAX_SIZE}.
    *
    * @return the maximum size of the thread pool
    */
   int getThreadPoolMaxSize();

   /**
    * Sets the maximum size of the thread pool.
    * <p>
    * This setting is relevant only if this factory does not use global pools. Value must be -1 (for unlimited thread
    * pool) or greater than 0.
    *
    * @param threadPoolMaxSize maximum size of the thread pool.
    * @return this ServerLocator
    */
   ServerLocator setThreadPoolMaxSize(int threadPoolMaxSize);

   /**
    * Returns the maximum size of the flow-control thread pool.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_FLOW_CONTROL_THREAD_POOL_MAX_SIZE}.
    *
    * @return the maximum size of the flow-control thread pool
    */
   int getFlowControlThreadPoolMaxSize();

   /**
    * Sets the maximum size of the flow-control thread pool.
    * <p>
    * This setting is relevant only if this factory does not use global pools. Value must be -1 (for unlimited thread
    * pool) or greater than 0.
    *
    * @param flowControlThreadPoolMaxSize maximum size of the flow-control thread pool.
    * @return this ServerLocator
    */
   ServerLocator setFlowControlThreadPoolMaxSize(int flowControlThreadPoolMaxSize);

   /**
    * Returns the time to retry connections created by this factory after failure.
    * <p>
    * Value is in milliseconds, default is {@link ActiveMQClient#DEFAULT_RETRY_INTERVAL}.
    *
    * @return the time to retry connections created by this factory after failure
    */
   long getRetryInterval();

   /**
    * Sets the time to retry connections created by this factory after failure.
    * <p>
    * Value must be greater than 0.
    *
    * @param retryInterval time (in milliseconds) to retry connections created by this factory after failure
    * @return this ServerLocator
    */
   ServerLocator setRetryInterval(long retryInterval);

   /**
    * Returns the multiplier to apply to successive retry intervals.
    * <p>
    * Default value is  {@link ActiveMQClient#DEFAULT_RETRY_INTERVAL_MULTIPLIER}.
    *
    * @return the multiplier to apply to successive retry intervals
    */
   double getRetryIntervalMultiplier();

   /**
    * Sets the multiplier to apply to successive retry intervals.
    * <p>
    * Value must be positive.
    *
    * @param retryIntervalMultiplier multiplier to apply to successive retry intervals
    * @return this ServerLocator
    */
   ServerLocator setRetryIntervalMultiplier(double retryIntervalMultiplier);

   /**
    * Returns the maximum retry interval (in the case a retry interval multiplier has been specified).
    * <p>
    * Value is in milliseconds, default value is  {@link ActiveMQClient#DEFAULT_MAX_RETRY_INTERVAL}.
    *
    * @return the maximum retry interval
    */
   long getMaxRetryInterval();

   /**
    * Sets the maximum retry interval.
    * <p>
    * Value must be greater than 0.
    *
    * @param maxRetryInterval maximum retry interval to apply in the case a retry interval multiplier has been
    *                         specified
    * @return this ServerLocator
    */
   ServerLocator setMaxRetryInterval(long maxRetryInterval);

   /**
    * Returns the maximum number of attempts to retry connection in case of failure.
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_RECONNECT_ATTEMPTS}.
    *
    * @return the maximum number of attempts to retry connection in case of failure
    */
   int getReconnectAttempts();

   /**
    * Sets the maximum number of attempts to retry connection in case of failure.
    * <p>
    * Value must be -1 (to retry infinitely), 0 (to never retry connection) or greater than 0.
    *
    * @param reconnectAttempts maximum number of attempts to retry connection in case of failure
    * @return this ServerLocator
    */
   ServerLocator setReconnectAttempts(int reconnectAttempts);

   /**
    * Sets the maximum number of attempts to establish an initial connection.
    * <p>
    * Value must be -1 (to retry infinitely), 0 (to never retry connection) or greater than 0.
    *
    * @param reconnectAttempts maximum number of attempts for the initial connection
    * @return this ServerLocator
    */
   ServerLocator setInitialConnectAttempts(int reconnectAttempts);

   /**
    * {@return the number of attempts to be made for first initial connection}
    */
   int getInitialConnectAttempts();

   /**
    * Sets the maximum number of failover attempts to establish a connection to other primary servers after a connection
    * failure.
    * <p>
    * Value must be -1 (to retry infinitely), 0 (to never retry connection) or greater than 0.
    *
    * @param attempts maximum number of failover attempts after a connection failure
    * @return this ServerLocator
    */
   ServerLocator setFailoverAttempts(int attempts);

   /**
    * {@return the number of failover attempts after a connection failure}
    */
   int getFailoverAttempts();

   /**
    * Returns true if the client will automatically attempt to connect to the backup server if the initial connection to
    * the primary server fails
    * <p>
    * Default value is {@link ActiveMQClient#DEFAULT_FAILOVER_ON_INITIAL_CONNECTION}.
    */
   @Deprecated
   boolean isFailoverOnInitialConnection();

   /**
    * Sets the value for FailoverOnInitialReconnection
    *
    * @return this ServerLocator
    */
   @Deprecated
   ServerLocator setFailoverOnInitialConnection(boolean failover);

   /**
    * Returns the class name of the connection load balancing policy.
    * <p>
    * Default value is
    * "org.apache.activemq.artemis.api.core.client.loadbalance.RoundRobinConnectionLoadBalancingPolicy".
    *
    * @return the class name of the connection load balancing policy
    */
   String getConnectionLoadBalancingPolicyClassName();

   /**
    * Sets the class name of the connection load balancing policy.
    * <p>
    * Value must be the name of a class implementing
    * {@link org.apache.activemq.artemis.api.core.client.loadbalance.ConnectionLoadBalancingPolicy}.
    *
    * @param loadBalancingPolicyClassName class name of the connection load balancing policy
    * @return this ServerLocator
    */
   ServerLocator setConnectionLoadBalancingPolicyClassName(String loadBalancingPolicyClassName);

   /**
    * Returns the initial size of messages created through this factory.
    * <p>
    * Value is in bytes, default value is  {@link ActiveMQClient#DEFAULT_INITIAL_MESSAGE_PACKET_SIZE}.
    *
    * @return the initial size of messages created through this factory
    */
   int getInitialMessagePacketSize();

   /**
    * Sets the initial size of messages created through this factory.
    * <p>
    * Value must be greater than 0.
    *
    * @param size initial size of messages created through this factory.
    * @return this ServerLocator
    */
   ServerLocator setInitialMessagePacketSize(int size);

   /**
    * Returns the timeout for onMessage completion when closing ClientConsumers created through this factory.
    * <p>
    * Value is in milliseconds, default value is {@link ActiveMQClient#DEFAULT_ONMESSAGE_CLOSE_TIMEOUT}.
    *
    * @return the timeout for onMessage completion when closing ClientConsumers created through this factory
    */
   int getOnMessageCloseTimeout();

   /**
    * Sets the timeout in milliseconds for onMessage completion when closing ClientConsumers created through this
    * factory.
    * <p>
    * A value of -1 means wait until the onMessage completes no matter how long it takes.
    *
    * @param onMessageCloseTimeout how long to wait in milliseconds for the ClientConsumer's MessageHandler's onMessage
    *                              method to finish before closing or stopping the ClientConsumer.
    * @return this ServerLocator
    */
   ServerLocator setOnMessageCloseTimeout(int onMessageCloseTimeout);

   /**
    * Adds an interceptor which will be executed <em>after packets are received from the server</em>.
    *
    * @param interceptor an Interceptor
    * @return this ServerLocator
    */
   ServerLocator addIncomingInterceptor(Interceptor interceptor);

   /**
    * Adds an interceptor which will be executed <em>before packets are sent to the server</em>.
    *
    * @param interceptor an Interceptor
    * @return this ServerLocator
    */
   ServerLocator addOutgoingInterceptor(Interceptor interceptor);

   /**
    * Removes an incoming interceptor.
    *
    * @param interceptor interceptor to remove
    * @return {@code true} if the incoming interceptor is removed from this factory, {@code false} else
    */
   boolean removeIncomingInterceptor(Interceptor interceptor);

   /**
    * Removes an outgoing interceptor.
    *
    * @param interceptor interceptor to remove
    * @return {@code true} if the outgoing interceptor is removed from this factory, {@code false} else
    */
   boolean removeOutgoingInterceptor(Interceptor interceptor);

   /**
    * Closes this factory and release all its resources
    */
   @Override
   void close();

   ServerLocator setUseTopologyForLoadBalancing(boolean useTopologyForLoadBalancing);

   boolean getUseTopologyForLoadBalancing();

   /**
    * Exposes the Topology used by this ServerLocator.
    *
    * @return topology
    */
   Topology getTopology();

   /**
    * Whether this server receives topology notifications from the cluster as servers join or leave the cluster.
    *
    * @return {@code true} if the locator receives topology updates from the cluster
    */
   boolean isHA();

   /**
    * Verify if all of the transports are using inVM.
    *
    * @return {@code true} if the locator has all inVM transports
    */
   boolean allInVM();

   /**
    * Whether to compress large messages.
    */
   boolean isCompressLargeMessage();

   /**
    * Sets whether to compress or not large messages.
    *
    * @return this ServerLocator
    */
   ServerLocator setCompressLargeMessage(boolean compressLargeMessages);

   /**
    * What compression level is in use
    */
   int getCompressionLevel();

   /**
    * Sets what compressionLevel to use when compressing messages Value must be -1 (default), or 0-9
    *
    * @return this ServerLocator
    */
   ServerLocator setCompressionLevel(int compressionLevel);

   ServerLocator addClusterTopologyListener(ClusterTopologyListener listener);

   void removeClusterTopologyListener(ClusterTopologyListener listener);

   ClientProtocolManagerFactory getProtocolManagerFactory();

   ServerLocator setProtocolManagerFactory(ClientProtocolManagerFactory protocolManager);

   /**
    * Set the list of {@link Interceptor}s to use for incoming packets.
    *
    * @param interceptorList a comma separated string of incoming interceptor class names to be used. Each interceptor
    *                        needs a default Constructor to be used with this method.
    * @return this
    */
   ServerLocator setIncomingInterceptorList(String interceptorList);

   String getIncomingInterceptorList();

   /**
    * Set the list of {@link Interceptor}s to use for outgoing packets.
    *
    * @param interceptorList a comma separated string of incoming interceptor class names to be used. Each interceptor
    *                        needs a default Constructor to be used with this method.
    * @return this
    */
   ServerLocator setOutgoingInterceptorList(String interceptorList);

   String getOutgoingInterceptorList();

   boolean setThreadPools(Executor threadPool, ScheduledExecutorService scheduledThreadPoolExecutor, Executor flowControlThreadPool);

   /**
    * This will only instantiate internal objects such as the topology
    */
   void initialize() throws ActiveMQException;

   ServerLocatorConfig getLocatorConfig();

   void setLocatorConfig(ServerLocatorConfig serverLocatorConfig);

   ServerLocator setPasswordCodec(String passwordCodec);

   String getPasswordCodec();

   /**
    * Sets connection-level credentials sent in {@code CONNECT} after connecting.
    *
    * @return this
    */
   ServerLocator setConnectionCredentials(String connectionUser, String connectionPassword);
}
