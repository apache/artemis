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
package org.apache.activemq.artemis.tests.integration.client;

import java.util.Arrays;

import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class SessionFactoryTest extends ActiveMQTestBase {

   private final DiscoveryGroupConfiguration groupConfiguration = new DiscoveryGroupConfiguration().setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(getUDPDiscoveryAddress()).setGroupPort(getUDPDiscoveryPort()));

   private ActiveMQServer liveService;

   private TransportConfiguration liveTC;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      enableDiscoveryForTest();
      super.setUp();

      startServer();
   }

   @Test
   public void testCloseUnusedClientSessionFactoryWithoutGlobalPools() throws Exception {
      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(liveTC);

      ClientSessionFactory csf = createSessionFactory(locator);
      csf.close();
   }

   @Test
   public void testDiscoveryConstructor() throws Exception {
      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(groupConfiguration);

      assertFactoryParams(locator, null, groupConfiguration, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, ActiveMQClient.DEFAULT_CALL_TIMEOUT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS);

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      assertNotNull(session);
      session.close();
      testSettersThrowException(cf);

      cf.close();

      locator.close();
   }

   @Test
   public void testStaticConnectorListConstructor() throws Exception {
      TransportConfiguration[] tc = new TransportConfiguration[]{liveTC};
      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(tc);

      assertFactoryParams(locator, tc, null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, ActiveMQClient.DEFAULT_CALL_TIMEOUT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS);

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      assertNotNull(session);
      session.close();
      testSettersThrowException(cf);

      cf.close();
   }

   @Test
   public void testGettersAndSetters() throws Exception {
      long clientFailureCheckPeriod = RandomUtil.randomPositiveLong();
      long connectionTTL = RandomUtil.randomPositiveLong();
      long callTimeout = RandomUtil.randomPositiveLong();
      int minLargeMessageSize = RandomUtil.randomPositiveInt();
      int consumerWindowSize = RandomUtil.randomPositiveInt();
      int consumerMaxRate = RandomUtil.randomPositiveInt();
      int confirmationWindowSize = RandomUtil.randomPositiveInt();
      int producerMaxRate = RandomUtil.randomPositiveInt();
      boolean blockOnAcknowledge = RandomUtil.randomBoolean();
      boolean blockOnDurableSend = RandomUtil.randomBoolean();
      boolean blockOnNonDurableSend = RandomUtil.randomBoolean();
      boolean autoGroup = RandomUtil.randomBoolean();
      boolean preAcknowledge = RandomUtil.randomBoolean();
      String loadBalancingPolicyClassName = RandomUtil.randomUUIDString();
      int ackBatchSize = RandomUtil.randomPositiveInt();
      boolean useGlobalPools = RandomUtil.randomBoolean();
      int scheduledThreadPoolMaxSize = RandomUtil.randomPositiveInt();
      int threadPoolMaxSize = RandomUtil.randomPositiveInt();
      long retryInterval = RandomUtil.randomPositiveLong();
      double retryIntervalMultiplier = RandomUtil.randomDouble();
      int reconnectAttempts = RandomUtil.randomPositiveInt();
      TransportConfiguration[] tc = new TransportConfiguration[]{liveTC};

      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(tc).setClientFailureCheckPeriod(clientFailureCheckPeriod).setConnectionTTL(connectionTTL).setCallTimeout(callTimeout).setMinLargeMessageSize(minLargeMessageSize).setConsumerWindowSize(consumerWindowSize).setConsumerMaxRate(consumerMaxRate).setConfirmationWindowSize(confirmationWindowSize).setProducerMaxRate(producerMaxRate).setBlockOnAcknowledge(blockOnAcknowledge).setBlockOnDurableSend(blockOnDurableSend).setBlockOnNonDurableSend(blockOnNonDurableSend).setAutoGroup(autoGroup).setPreAcknowledge(preAcknowledge).setConnectionLoadBalancingPolicyClassName(loadBalancingPolicyClassName).setAckBatchSize(ackBatchSize).setUseGlobalPools(useGlobalPools).setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize).setThreadPoolMaxSize(threadPoolMaxSize).setRetryInterval(retryInterval).setRetryIntervalMultiplier(retryIntervalMultiplier).setReconnectAttempts(reconnectAttempts);

      assertEqualsTransportConfigurations(tc, locator.getStaticTransportConfigurations());
      assertEquals(clientFailureCheckPeriod, locator.getClientFailureCheckPeriod());
      assertEquals(connectionTTL, locator.getConnectionTTL());
      assertEquals(callTimeout, locator.getCallTimeout());
      assertEquals(minLargeMessageSize, locator.getMinLargeMessageSize());
      assertEquals(consumerWindowSize, locator.getConsumerWindowSize());
      assertEquals(consumerMaxRate, locator.getConsumerMaxRate());
      assertEquals(confirmationWindowSize, locator.getConfirmationWindowSize());
      assertEquals(producerMaxRate, locator.getProducerMaxRate());
      assertEquals(blockOnAcknowledge, locator.isBlockOnAcknowledge());
      assertEquals(blockOnDurableSend, locator.isBlockOnDurableSend());
      assertEquals(blockOnNonDurableSend, locator.isBlockOnNonDurableSend());
      assertEquals(autoGroup, locator.isAutoGroup());
      assertEquals(preAcknowledge, locator.isPreAcknowledge());
      assertEquals(loadBalancingPolicyClassName, locator.getConnectionLoadBalancingPolicyClassName());
      assertEquals(ackBatchSize, locator.getAckBatchSize());
      assertEquals(useGlobalPools, locator.isUseGlobalPools());
      assertEquals(scheduledThreadPoolMaxSize, locator.getScheduledThreadPoolMaxSize());
      assertEquals(threadPoolMaxSize, locator.getThreadPoolMaxSize());
      assertEquals(retryInterval, locator.getRetryInterval());
      assertEquals(retryIntervalMultiplier, locator.getRetryIntervalMultiplier(), 0.000001);
      assertEquals(reconnectAttempts, locator.getReconnectAttempts());
   }

   private void testSettersThrowException(final ClientSessionFactory cf) {
      long clientFailureCheckPeriod = RandomUtil.randomPositiveLong();
      long connectionTTL = RandomUtil.randomPositiveLong();
      long callTimeout = RandomUtil.randomPositiveLong();
      int minLargeMessageSize = RandomUtil.randomPositiveInt();
      int consumerWindowSize = RandomUtil.randomPositiveInt();
      int consumerMaxRate = RandomUtil.randomPositiveInt();
      int confirmationWindowSize = RandomUtil.randomPositiveInt();
      int producerMaxRate = RandomUtil.randomPositiveInt();
      boolean blockOnAcknowledge = RandomUtil.randomBoolean();
      boolean blockOnDurableSend = RandomUtil.randomBoolean();
      boolean blockOnNonDurableSend = RandomUtil.randomBoolean();
      boolean autoGroup = RandomUtil.randomBoolean();
      boolean preAcknowledge = RandomUtil.randomBoolean();
      String loadBalancingPolicyClassName = RandomUtil.randomUUIDString();
      int ackBatchSize = RandomUtil.randomPositiveInt();
      boolean useGlobalPools = RandomUtil.randomBoolean();
      int scheduledThreadPoolMaxSize = RandomUtil.randomPositiveInt();
      int threadPoolMaxSize = RandomUtil.randomPositiveInt();
      long retryInterval = RandomUtil.randomPositiveLong();
      double retryIntervalMultiplier = RandomUtil.randomDouble();
      int reconnectAttempts = RandomUtil.randomPositiveInt();

      try {
         cf.getServerLocator().setClientFailureCheckPeriod(clientFailureCheckPeriod);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setConnectionTTL(connectionTTL);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setCallTimeout(callTimeout);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setMinLargeMessageSize(minLargeMessageSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setConsumerWindowSize(consumerWindowSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setConsumerMaxRate(consumerMaxRate);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setConfirmationWindowSize(confirmationWindowSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setProducerMaxRate(producerMaxRate);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setBlockOnAcknowledge(blockOnAcknowledge);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setBlockOnDurableSend(blockOnDurableSend);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setBlockOnNonDurableSend(blockOnNonDurableSend);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setAutoGroup(autoGroup);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setPreAcknowledge(preAcknowledge);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setConnectionLoadBalancingPolicyClassName(loadBalancingPolicyClassName);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setAckBatchSize(ackBatchSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setUseGlobalPools(useGlobalPools);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setThreadPoolMaxSize(threadPoolMaxSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setRetryInterval(retryInterval);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setRetryIntervalMultiplier(retryIntervalMultiplier);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.getServerLocator().setReconnectAttempts(reconnectAttempts);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }

      cf.getServerLocator().getStaticTransportConfigurations();
      cf.getServerLocator().getClientFailureCheckPeriod();
      cf.getServerLocator().getConnectionTTL();
      cf.getServerLocator().getCallTimeout();
      cf.getServerLocator().getMinLargeMessageSize();
      cf.getServerLocator().getConsumerWindowSize();
      cf.getServerLocator().getConsumerMaxRate();
      cf.getServerLocator().getConfirmationWindowSize();
      cf.getServerLocator().getProducerMaxRate();
      cf.getServerLocator().isBlockOnAcknowledge();
      cf.getServerLocator().isBlockOnDurableSend();
      cf.getServerLocator().isBlockOnNonDurableSend();
      cf.getServerLocator().isAutoGroup();
      cf.getServerLocator().isPreAcknowledge();
      cf.getServerLocator().getConnectionLoadBalancingPolicyClassName();
      cf.getServerLocator().getAckBatchSize();
      cf.getServerLocator().isUseGlobalPools();
      cf.getServerLocator().getScheduledThreadPoolMaxSize();
      cf.getServerLocator().getThreadPoolMaxSize();
      cf.getServerLocator().getRetryInterval();
      cf.getServerLocator().getRetryIntervalMultiplier();
      cf.getServerLocator().getReconnectAttempts();

   }

   private void assertFactoryParams(final ServerLocator locator,
                                    final TransportConfiguration[] staticConnectors,
                                    final DiscoveryGroupConfiguration discoveryGroupConfiguration,
                                    final long clientFailureCheckPeriod,
                                    final long connectionTTL,
                                    final long callTimeout,
                                    final int minLargeMessageSize,
                                    final int consumerWindowSize,
                                    final int consumerMaxRate,
                                    final int confirmationWindowSize,
                                    final int producerMaxRate,
                                    final boolean blockOnAcknowledge,
                                    final boolean blockOnDurableSend,
                                    final boolean blockOnNonDurableSend,
                                    final boolean autoGroup,
                                    final boolean preAcknowledge,
                                    final String loadBalancingPolicyClassName,
                                    final int ackBatchSize,
                                    final boolean useGlobalPools,
                                    final int scheduledThreadPoolMaxSize,
                                    final int threadPoolMaxSize,
                                    final long retryInterval,
                                    final double retryIntervalMultiplier,
                                    final int reconnectAttempts) {
      if (staticConnectors == null) {
         assertArrayEquals(new String[]{}, locator.getStaticTransportConfigurations(), "no static connectors");
      } else {
         assertEqualsTransportConfigurations(staticConnectors, locator.getStaticTransportConfigurations());
      }
      assertEquals(locator.getDiscoveryGroupConfiguration(), discoveryGroupConfiguration);
      assertEquals(locator.getClientFailureCheckPeriod(), clientFailureCheckPeriod);
      assertEquals(locator.getConnectionTTL(), connectionTTL);
      assertEquals(locator.getCallTimeout(), callTimeout);
      assertEquals(locator.getMinLargeMessageSize(), minLargeMessageSize);
      assertEquals(locator.getConsumerWindowSize(), consumerWindowSize);
      assertEquals(locator.getConsumerMaxRate(), consumerMaxRate);
      assertEquals(locator.getConfirmationWindowSize(), confirmationWindowSize);
      assertEquals(locator.getProducerMaxRate(), producerMaxRate);
      assertEquals(locator.isBlockOnAcknowledge(), blockOnAcknowledge);
      assertEquals(locator.isBlockOnDurableSend(), blockOnDurableSend);
      assertEquals(locator.isBlockOnNonDurableSend(), blockOnNonDurableSend);
      assertEquals(locator.isAutoGroup(), autoGroup);
      assertEquals(locator.isPreAcknowledge(), preAcknowledge);
      assertEquals(locator.getConnectionLoadBalancingPolicyClassName(), loadBalancingPolicyClassName);
      assertEquals(locator.getAckBatchSize(), ackBatchSize);
      assertEquals(locator.isUseGlobalPools(), useGlobalPools);
      assertEquals(locator.getScheduledThreadPoolMaxSize(), scheduledThreadPoolMaxSize);
      assertEquals(locator.getThreadPoolMaxSize(), threadPoolMaxSize);
      assertEquals(locator.getRetryInterval(), retryInterval);
      assertEquals(locator.getRetryIntervalMultiplier(), retryIntervalMultiplier, 0.000001);
      assertEquals(locator.getReconnectAttempts(), reconnectAttempts);
   }

   private void startServer() throws Exception {
      liveTC = new TransportConfiguration(INVM_CONNECTOR_FACTORY);

      final long broadcastPeriod = 250;

      final String bcGroupName = "bc1";

      final int localBindPort = 5432;

      BroadcastGroupConfiguration broadcastGroupConfiguration = new BroadcastGroupConfiguration().setName(bcGroupName).setBroadcastPeriod(broadcastPeriod).setConnectorInfos(Arrays.asList(liveTC.getName())).setEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(getUDPDiscoveryAddress()).setGroupPort(getUDPDiscoveryPort()).setLocalBindPort(localBindPort));

      Configuration primaryConf = createDefaultInVMConfig().addConnectorConfiguration(liveTC.getName(), liveTC).setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration()).addBroadcastGroupConfiguration(broadcastGroupConfiguration);

      liveService = createServer(false, primaryConf);
      liveService.start();
   }
}
