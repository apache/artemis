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
package org.apache.activemq.artemis.core.config.impl;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.config.balancing.BrokerBalancerConfiguration;
import org.apache.activemq.artemis.core.config.ha.LiveOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.SecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.balancing.policies.ConsistentHashPolicy;
import org.apache.activemq.artemis.core.server.balancing.policies.FirstElementPolicy;
import org.apache.activemq.artemis.core.server.balancing.policies.LeastConnectionsPolicy;
import org.apache.activemq.artemis.core.server.balancing.targets.TargetKey;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.LegacyLDAPSecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;
import org.apache.activemq.artemis.core.server.metrics.plugins.SimpleMetricsPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FileConfigurationTest extends ConfigurationImplTest {

   @BeforeClass
   public static void setupProperties() {
      System.setProperty("a2Prop", "a2");
      System.setProperty("falseProp", "false");
      System.setProperty("trueProp", "true");
      System.setProperty("ninetyTwoProp", "92");
   }

   @AfterClass
   public static void clearProperties() {
      System.clearProperty("a2Prop");
      System.clearProperty("falseProp");
      System.clearProperty("trueProp");
      System.clearProperty("ninetyTwoProp");
   }

   protected String getConfigurationName() {
      return "ConfigurationTest-full-config.xml";
   }

   @Override
   @Test
   public void testDefaults() {
      // Check they match the values from the test file
      Assert.assertEquals("SomeNameForUseOnTheApplicationServer", conf.getName());
      Assert.assertEquals(false, conf.isPersistenceEnabled());
      Assert.assertEquals(true, conf.isClustered());
      Assert.assertEquals(12345, conf.getScheduledThreadPoolMaxSize());
      Assert.assertEquals(54321, conf.getThreadPoolMaxSize());
      Assert.assertEquals(false, conf.isSecurityEnabled());
      Assert.assertEquals(5423, conf.getSecurityInvalidationInterval());
      Assert.assertEquals(333, conf.getAuthenticationCacheSize());
      Assert.assertEquals(444, conf.getAuthorizationCacheSize());
      Assert.assertEquals(true, conf.isWildcardRoutingEnabled());
      Assert.assertEquals(new SimpleString("Giraffe"), conf.getManagementAddress());
      Assert.assertEquals(new SimpleString("Whatever"), conf.getManagementNotificationAddress());
      Assert.assertEquals("Frog", conf.getClusterUser());
      Assert.assertEquals("Wombat", conf.getClusterPassword());
      Assert.assertEquals(false, conf.isJMXManagementEnabled());
      Assert.assertEquals("gro.qtenroh", conf.getJMXDomain());
      Assert.assertEquals(true, conf.isMessageCounterEnabled());
      Assert.assertEquals(5, conf.getMessageCounterMaxDayHistory());
      Assert.assertEquals(123456, conf.getMessageCounterSamplePeriod());
      Assert.assertEquals(12345, conf.getConnectionTTLOverride());
      Assert.assertEquals(98765, conf.getTransactionTimeout());
      Assert.assertEquals(56789, conf.getTransactionTimeoutScanPeriod());
      Assert.assertEquals(10111213, conf.getMessageExpiryScanPeriod());
      Assert.assertEquals(25000, conf.getAddressQueueScanPeriod());
      Assert.assertEquals(127, conf.getIDCacheSize());
      Assert.assertEquals(true, conf.isPersistIDCache());
      Assert.assertEquals(Integer.valueOf(777), conf.getJournalDeviceBlockSize());
      Assert.assertEquals(true, conf.isPersistDeliveryCountBeforeDelivery());
      Assert.assertEquals("pagingdir", conf.getPagingDirectory());
      Assert.assertEquals("somedir", conf.getBindingsDirectory());
      Assert.assertEquals(false, conf.isCreateBindingsDir());
      Assert.assertEquals(true, conf.isAmqpUseCoreSubscriptionNaming());

      Assert.assertEquals("max concurrent io", 17, conf.getPageMaxConcurrentIO());
      Assert.assertEquals(true, conf.isReadWholePage());
      Assert.assertEquals("somedir2", conf.getJournalDirectory());
      Assert.assertEquals("history", conf.getJournalRetentionDirectory());
      Assert.assertEquals(10L * 1024L * 1024L * 1024L, conf.getJournalRetentionMaxBytes());
      Assert.assertEquals(TimeUnit.DAYS.toMillis(365), conf.getJournalRetentionPeriod());
      Assert.assertEquals(false, conf.isCreateJournalDir());
      Assert.assertEquals(JournalType.NIO, conf.getJournalType());
      Assert.assertEquals(10000, conf.getJournalBufferSize_NIO());
      Assert.assertEquals(1000, conf.getJournalBufferTimeout_NIO());
      Assert.assertEquals(56546, conf.getJournalMaxIO_NIO());
      Assert.assertEquals(9876, conf.getJournalFileOpenTimeout());

      Assert.assertEquals(false, conf.isJournalSyncTransactional());
      Assert.assertEquals(true, conf.isJournalSyncNonTransactional());
      Assert.assertEquals(12345678, conf.getJournalFileSize());
      Assert.assertEquals(100, conf.getJournalMinFiles());
      Assert.assertEquals(123, conf.getJournalCompactMinFiles());
      Assert.assertEquals(33, conf.getJournalCompactPercentage());
      Assert.assertEquals(true, conf.isGracefulShutdownEnabled());
      Assert.assertEquals(12345, conf.getGracefulShutdownTimeout());
      Assert.assertEquals(true, conf.isPopulateValidatedUser());
      Assert.assertEquals(false, conf.isRejectEmptyValidatedUser());
      Assert.assertEquals(98765, conf.getConnectionTtlCheckInterval());
      Assert.assertEquals(1234567, conf.getConfigurationFileRefreshPeriod());
      Assert.assertEquals("TEMP", conf.getTemporaryQueueNamespace());

      Assert.assertEquals("127.0.0.1", conf.getNetworkCheckList());
      Assert.assertEquals("some-nick", conf.getNetworkCheckNIC());
      Assert.assertEquals(123, conf.getNetworkCheckPeriod());
      Assert.assertEquals(321, conf.getNetworkCheckTimeout());
      Assert.assertEquals("ping-four", conf.getNetworkCheckPingCommand());
      Assert.assertEquals("ping-six", conf.getNetworkCheckPing6Command());

      Assert.assertEquals("largemessagesdir", conf.getLargeMessagesDirectory());
      Assert.assertEquals(95, conf.getMemoryWarningThreshold());

      Assert.assertEquals(2, conf.getIncomingInterceptorClassNames().size());
      Assert.assertTrue(conf.getIncomingInterceptorClassNames().contains("org.apache.activemq.artemis.tests.unit.core.config.impl.TestInterceptor1"));
      Assert.assertTrue(conf.getIncomingInterceptorClassNames().contains("org.apache.activemq.artemis.tests.unit.core.config.impl.TestInterceptor2"));

      Assert.assertEquals(2, conf.getConnectorConfigurations().size());

      TransportConfiguration tc = conf.getConnectorConfigurations().get("connector1");
      Assert.assertNotNull(tc);
      Assert.assertEquals("org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory", tc.getFactoryClassName());
      Assert.assertEquals("mylocal", tc.getParams().get("localAddress"));
      Assert.assertEquals("99", tc.getParams().get("localPort"));
      Assert.assertEquals("localhost1", tc.getParams().get("host"));
      Assert.assertEquals("5678", tc.getParams().get("port"));

      tc = conf.getConnectorConfigurations().get("connector2");
      Assert.assertNotNull(tc);
      Assert.assertEquals("org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory", tc.getFactoryClassName());
      Assert.assertEquals("5", tc.getParams().get("serverId"));

      Assert.assertEquals(2, conf.getAcceptorConfigurations().size());
      for (TransportConfiguration ac : conf.getAcceptorConfigurations()) {
         if (ac.getFactoryClassName().equals("org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory")) {
            Assert.assertEquals("456", ac.getParams().get("tcpNoDelay"));
            Assert.assertEquals("44", ac.getParams().get("connectionTtl"));
            Assert.assertEquals("92", ac.getParams().get(TransportConstants.CONNECTIONS_ALLOWED));
         } else {
            Assert.assertEquals("org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory", ac.getFactoryClassName());
            Assert.assertEquals("0", ac.getParams().get("serverId"));
            Assert.assertEquals("87", ac.getParams().get(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.CONNECTIONS_ALLOWED));
         }
      }

      Assert.assertEquals(2, conf.getBroadcastGroupConfigurations().size());
      for (BroadcastGroupConfiguration bc : conf.getBroadcastGroupConfigurations()) {
         UDPBroadcastEndpointFactory udpBc = (UDPBroadcastEndpointFactory) bc.getEndpointFactory();
         if (bc.getName().equals("bg1")) {
            Assert.assertEquals("bg1", bc.getName());
            Assert.assertEquals(10999, udpBc.getLocalBindPort());
            Assert.assertEquals("192.168.0.120", udpBc.getGroupAddress());
            Assert.assertEquals(11999, udpBc.getGroupPort());
            Assert.assertEquals(12345, bc.getBroadcastPeriod());
            Assert.assertEquals("connector1", bc.getConnectorInfos().get(0));
         } else {
            Assert.assertEquals("bg2", bc.getName());
            Assert.assertEquals(12999, udpBc.getLocalBindPort());
            Assert.assertEquals("192.168.0.121", udpBc.getGroupAddress());
            Assert.assertEquals(13999, udpBc.getGroupPort());
            Assert.assertEquals(23456, bc.getBroadcastPeriod());
            Assert.assertEquals("connector2", bc.getConnectorInfos().get(0));
         }
      }

      Assert.assertEquals(2, conf.getDiscoveryGroupConfigurations().size());
      DiscoveryGroupConfiguration dc = conf.getDiscoveryGroupConfigurations().get("dg1");
      Assert.assertEquals("dg1", dc.getName());
      Assert.assertEquals("192.168.0.120", ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getGroupAddress());
      assertEquals("172.16.8.10", ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getLocalBindAddress());
      Assert.assertEquals(11999, ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getGroupPort());
      Assert.assertEquals(12345, dc.getRefreshTimeout());

      dc = conf.getDiscoveryGroupConfigurations().get("dg2");
      Assert.assertEquals("dg2", dc.getName());
      Assert.assertEquals("192.168.0.121", ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getGroupAddress());
      assertEquals("172.16.8.11", ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getLocalBindAddress());
      Assert.assertEquals(12999, ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getGroupPort());
      Assert.assertEquals(23456, dc.getRefreshTimeout());

      Assert.assertEquals(3, conf.getDivertConfigurations().size());
      for (DivertConfiguration dic : conf.getDivertConfigurations()) {
         if (dic.getName().equals("divert1")) {
            Assert.assertEquals("divert1", dic.getName());
            Assert.assertEquals("routing-name1", dic.getRoutingName());
            Assert.assertEquals("address1", dic.getAddress());
            Assert.assertEquals("forwarding-address1", dic.getForwardingAddress());
            Assert.assertEquals("speed > 88", dic.getFilterString());
            Assert.assertEquals("org.foo.Transformer", dic.getTransformerConfiguration().getClassName());
            Assert.assertEquals(true, dic.isExclusive());
         } else if (dic.getName().equals("divert2")) {
            Assert.assertEquals("divert2", dic.getName());
            Assert.assertEquals("routing-name2", dic.getRoutingName());
            Assert.assertEquals("address2", dic.getAddress());
            Assert.assertEquals("forwarding-address2", dic.getForwardingAddress());
            Assert.assertEquals("speed < 88", dic.getFilterString());
            Assert.assertEquals("org.foo.Transformer2", dic.getTransformerConfiguration().getClassName());
            Assert.assertEquals(false, dic.isExclusive());
         } else {
            Assert.assertEquals("divert3", dic.getName());
            Assert.assertEquals("org.foo.DivertTransformer3", dic.getTransformerConfiguration().getClassName());
            Assert.assertEquals("divertTransformerValue1", dic.getTransformerConfiguration().getProperties().get("divertTransformerKey1"));
            Assert.assertEquals("divertTransformerValue2", dic.getTransformerConfiguration().getProperties().get("divertTransformerKey2"));
         }
      }

      Assert.assertEquals(4, conf.getBalancerConfigurations().size());
      for (BrokerBalancerConfiguration bc : conf.getBalancerConfigurations()) {
         if (bc.getName().equals("simple-local")) {
            Assert.assertEquals(bc.getTargetKey(), TargetKey.CLIENT_ID);
            Assert.assertNotNull(bc.getLocalTargetFilter());
            Assert.assertNotNull(bc.getTargetKeyFilter());
            Assert.assertNull(bc.getPolicyConfiguration());
         } else if (bc.getName().equals("simple-balancer")) {
            Assert.assertEquals(bc.getTargetKey(), TargetKey.USER_NAME);
            Assert.assertNull(bc.getLocalTargetFilter());
            Assert.assertEquals(bc.getPolicyConfiguration().getName(), FirstElementPolicy.NAME);
            Assert.assertEquals(false, bc.getPoolConfiguration().isLocalTargetEnabled());
            Assert.assertEquals("connector1", bc.getPoolConfiguration().getStaticConnectors().get(0));
            Assert.assertEquals(null, bc.getPoolConfiguration().getDiscoveryGroupName());
         } else if (bc.getName().equals("consistent-hash-balancer")) {
            Assert.assertEquals(bc.getTargetKey(), TargetKey.SNI_HOST);
            Assert.assertEquals(bc.getTargetKeyFilter(), "^[^.]+");
            Assert.assertEquals(bc.getLocalTargetFilter(), "DEFAULT");
            Assert.assertEquals(bc.getPolicyConfiguration().getName(), ConsistentHashPolicy.NAME);
            Assert.assertEquals(1000, bc.getPoolConfiguration().getCheckPeriod());
            Assert.assertEquals(true, bc.getPoolConfiguration().isLocalTargetEnabled());
            Assert.assertEquals(null, bc.getPoolConfiguration().getStaticConnectors());
            Assert.assertEquals("dg1", bc.getPoolConfiguration().getDiscoveryGroupName());
         } else {
            Assert.assertEquals(bc.getTargetKey(), TargetKey.SOURCE_IP);
            Assert.assertEquals("least-connections-balancer", bc.getName());
            Assert.assertEquals(60000, bc.getCacheTimeout());
            Assert.assertEquals(bc.getPolicyConfiguration().getName(), LeastConnectionsPolicy.NAME);
            Assert.assertEquals(3000, bc.getPoolConfiguration().getCheckPeriod());
            Assert.assertEquals(2, bc.getPoolConfiguration().getQuorumSize());
            Assert.assertEquals(1000, bc.getPoolConfiguration().getQuorumTimeout());
            Assert.assertEquals(false, bc.getPoolConfiguration().isLocalTargetEnabled());
            Assert.assertEquals(null, bc.getPoolConfiguration().getStaticConnectors());
            Assert.assertEquals("dg2", bc.getPoolConfiguration().getDiscoveryGroupName());
         }
      }

      Assert.assertEquals(4, conf.getBridgeConfigurations().size());
      for (BridgeConfiguration bc : conf.getBridgeConfigurations()) {
         if (bc.getName().equals("bridge1")) {
            Assert.assertEquals("bridge1", bc.getName());
            Assert.assertEquals("queue1", bc.getQueueName());
            Assert.assertEquals("minLargeMessageSize", 4194304, bc.getMinLargeMessageSize());
            assertEquals("check-period", 31, bc.getClientFailureCheckPeriod());
            assertEquals("connection time-to-live", 370, bc.getConnectionTTL());
            Assert.assertEquals("bridge-forwarding-address1", bc.getForwardingAddress());
            Assert.assertEquals("sku > 1", bc.getFilterString());
            Assert.assertEquals("org.foo.BridgeTransformer", bc.getTransformerConfiguration().getClassName());
            Assert.assertEquals(3, bc.getRetryInterval());
            Assert.assertEquals(0.2, bc.getRetryIntervalMultiplier(), 0.0001);
            assertEquals("max retry interval", 10002, bc.getMaxRetryInterval());
            Assert.assertEquals(2, bc.getReconnectAttempts());
            Assert.assertEquals(true, bc.isUseDuplicateDetection());
            Assert.assertEquals("connector1", bc.getStaticConnectors().get(0));
            Assert.assertEquals(null, bc.getDiscoveryGroupName());
            Assert.assertEquals(444, bc.getProducerWindowSize());
            Assert.assertEquals(1073741824, bc.getConfirmationWindowSize());
            Assert.assertEquals(ComponentConfigurationRoutingType.STRIP, bc.getRoutingType());
         } else if (bc.getName().equals("bridge2")) {
            Assert.assertEquals("bridge2", bc.getName());
            Assert.assertEquals("queue2", bc.getQueueName());
            Assert.assertEquals("bridge-forwarding-address2", bc.getForwardingAddress());
            Assert.assertEquals(null, bc.getFilterString());
            Assert.assertEquals(null, bc.getTransformerConfiguration());
            Assert.assertEquals(null, bc.getStaticConnectors());
            Assert.assertEquals("dg1", bc.getDiscoveryGroupName());
            Assert.assertEquals(568320, bc.getProducerWindowSize());
            Assert.assertEquals(ComponentConfigurationRoutingType.PASS, bc.getRoutingType());
         } else if (bc.getName().equals("bridge3")) {
            Assert.assertEquals("bridge3", bc.getName());
            Assert.assertEquals("org.foo.BridgeTransformer3", bc.getTransformerConfiguration().getClassName());
            Assert.assertEquals("bridgeTransformerValue1", bc.getTransformerConfiguration().getProperties().get("bridgeTransformerKey1"));
            Assert.assertEquals("bridgeTransformerValue2", bc.getTransformerConfiguration().getProperties().get("bridgeTransformerKey2"));

         }
      }

      Assert.assertEquals(3, conf.getClusterConfigurations().size());

      HAPolicyConfiguration pc = conf.getHAPolicyConfiguration();
      assertNotNull(pc);
      assertTrue(pc instanceof LiveOnlyPolicyConfiguration);
      LiveOnlyPolicyConfiguration lopc = (LiveOnlyPolicyConfiguration) pc;
      assertNotNull(lopc.getScaleDownConfiguration());
      assertEquals(lopc.getScaleDownConfiguration().getGroupName(), "boo!");
      assertEquals(lopc.getScaleDownConfiguration().getDiscoveryGroup(), "dg1");

      for (ClusterConnectionConfiguration ccc : conf.getClusterConfigurations()) {
         if (ccc.getName().equals("cluster-connection3")) {
            Assert.assertEquals(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, ccc.getMessageLoadBalancingType());
            Assert.assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterCallTimeout(), ccc.getCallTimeout());
            Assert.assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterCallFailoverTimeout(), ccc.getCallFailoverTimeout());
         } else if (ccc.getName().equals("cluster-connection1")) {
            Assert.assertEquals("cluster-connection1", ccc.getName());
            Assert.assertEquals("clusterConnectionConf minLargeMessageSize", 321, ccc.getMinLargeMessageSize());
            assertEquals("check-period", 331, ccc.getClientFailureCheckPeriod());
            assertEquals("connection time-to-live", 3370, ccc.getConnectionTTL());
            Assert.assertEquals("queues1", ccc.getAddress());
            Assert.assertEquals(3, ccc.getRetryInterval());
            Assert.assertEquals(true, ccc.isDuplicateDetection());
            Assert.assertEquals(MessageLoadBalancingType.ON_DEMAND, ccc.getMessageLoadBalancingType());
            Assert.assertEquals(1, ccc.getMaxHops());
            Assert.assertEquals(123, ccc.getCallTimeout());
            Assert.assertEquals(123, ccc.getCallFailoverTimeout());
            assertEquals("multiplier", 0.25, ccc.getRetryIntervalMultiplier(), 0.00001);
            assertEquals("max retry interval", 10000, ccc.getMaxRetryInterval());
            assertEquals(72, ccc.getReconnectAttempts());
            Assert.assertEquals("connector1", ccc.getStaticConnectors().get(0));
            Assert.assertEquals("connector2", ccc.getStaticConnectors().get(1));
            Assert.assertEquals(null, ccc.getDiscoveryGroupName());
            Assert.assertEquals(222, ccc.getProducerWindowSize());
         } else {
            Assert.assertEquals("cluster-connection2", ccc.getName());
            Assert.assertEquals("queues2", ccc.getAddress());
            Assert.assertEquals(4, ccc.getRetryInterval());
            Assert.assertEquals(456, ccc.getCallTimeout());
            Assert.assertEquals(456, ccc.getCallFailoverTimeout());
            Assert.assertEquals(false, ccc.isDuplicateDetection());
            Assert.assertEquals(MessageLoadBalancingType.STRICT, ccc.getMessageLoadBalancingType());
            Assert.assertEquals(2, ccc.getMaxHops());
            Assert.assertEquals(Collections.emptyList(), ccc.getStaticConnectors());
            Assert.assertEquals("dg1", ccc.getDiscoveryGroupName());
            Assert.assertEquals(333, ccc.getProducerWindowSize());
         }
      }

      assertEquals(2, conf.getAddressesSettings().size());

      assertTrue(conf.getAddressesSettings().get("a1") != null);
      assertTrue(conf.getAddressesSettings().get("a2") != null);

      assertEquals("a1.1", conf.getAddressesSettings().get("a1").getDeadLetterAddress().toString());
      assertEquals(AddressSettings.DEFAULT_AUTO_CREATE_DEAD_LETTER_RESOURCES, conf.getAddressesSettings().get("a1").isAutoCreateDeadLetterResources());
      assertEquals(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX, conf.getAddressesSettings().get("a1").getDeadLetterQueuePrefix());
      assertEquals(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX, conf.getAddressesSettings().get("a1").getDeadLetterQueueSuffix());
      assertEquals("a1.2", conf.getAddressesSettings().get("a1").getExpiryAddress().toString());
      assertEquals(1L, (long) conf.getAddressesSettings().get("a1").getExpiryDelay());
      assertEquals(2L, (long) conf.getAddressesSettings().get("a1").getMinExpiryDelay());
      assertEquals(3L, (long) conf.getAddressesSettings().get("a1").getMaxExpiryDelay());
      assertEquals(AddressSettings.DEFAULT_AUTO_CREATE_EXPIRY_RESOURCES, conf.getAddressesSettings().get("a1").isAutoCreateExpiryResources());
      assertEquals(AddressSettings.DEFAULT_EXPIRY_QUEUE_PREFIX, conf.getAddressesSettings().get("a1").getExpiryQueuePrefix());
      assertEquals(AddressSettings.DEFAULT_EXPIRY_QUEUE_SUFFIX, conf.getAddressesSettings().get("a1").getExpiryQueueSuffix());
      assertEquals(1, conf.getAddressesSettings().get("a1").getRedeliveryDelay());
      assertEquals(0.5, conf.getAddressesSettings().get("a1").getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(856686592L, conf.getAddressesSettings().get("a1").getMaxSizeBytes());
      assertEquals(817381738L, conf.getAddressesSettings().get("a1").getPageSizeBytes());
      assertEquals(10, conf.getAddressesSettings().get("a1").getPageCacheMaxSize());
      assertEquals(4, conf.getAddressesSettings().get("a1").getMessageCounterHistoryDayLimit());
      assertEquals(10, conf.getAddressesSettings().get("a1").getSlowConsumerThreshold());
      assertEquals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_HOUR, conf.getAddressesSettings().get("a1").getSlowConsumerThresholdMeasurementUnit());
      assertEquals(5, conf.getAddressesSettings().get("a1").getSlowConsumerCheckPeriod());
      assertEquals(SlowConsumerPolicy.NOTIFY, conf.getAddressesSettings().get("a1").getSlowConsumerPolicy());
      assertEquals(true, conf.getAddressesSettings().get("a1").isAutoCreateJmsQueues());
      assertEquals(true, conf.getAddressesSettings().get("a1").isAutoDeleteJmsQueues());
      assertEquals(true, conf.getAddressesSettings().get("a1").isAutoCreateJmsTopics());
      assertEquals(true, conf.getAddressesSettings().get("a1").isAutoDeleteJmsTopics());
      assertEquals(0, conf.getAddressesSettings().get("a1").getAutoDeleteQueuesDelay());
      assertEquals(0, conf.getAddressesSettings().get("a1").getAutoDeleteAddressesDelay());
      assertEquals(false, conf.getAddressesSettings().get("a1").isDefaultPurgeOnNoConsumers());
      assertEquals(5, conf.getAddressesSettings().get("a1").getDefaultMaxConsumers());
      assertEquals(RoutingType.ANYCAST, conf.getAddressesSettings().get("a1").getDefaultQueueRoutingType());
      assertEquals(RoutingType.MULTICAST, conf.getAddressesSettings().get("a1").getDefaultAddressRoutingType());
      assertEquals(3, conf.getAddressesSettings().get("a1").getDefaultRingSize());
      assertEquals(0, conf.getAddressesSettings().get("a1").getRetroactiveMessageCount());
      assertTrue(conf.getAddressesSettings().get("a1").isEnableMetrics());
      assertTrue(conf.getAddressesSettings().get("a1").isEnableIngressTimestamp());

      assertEquals("a2.1", conf.getAddressesSettings().get("a2").getDeadLetterAddress().toString());
      assertEquals(true, conf.getAddressesSettings().get("a2").isAutoCreateDeadLetterResources());
      assertEquals("", conf.getAddressesSettings().get("a2").getDeadLetterQueuePrefix().toString());
      assertEquals(".DLQ", conf.getAddressesSettings().get("a2").getDeadLetterQueueSuffix().toString());
      assertEquals("a2.2", conf.getAddressesSettings().get("a2").getExpiryAddress().toString());
      assertEquals(-1L, (long) conf.getAddressesSettings().get("a2").getExpiryDelay());
      assertEquals(-1L, (long) conf.getAddressesSettings().get("a2").getMinExpiryDelay());
      assertEquals(-1L, (long) conf.getAddressesSettings().get("a2").getMaxExpiryDelay());
      assertEquals(true, conf.getAddressesSettings().get("a2").isAutoCreateDeadLetterResources());
      assertEquals("", conf.getAddressesSettings().get("a2").getExpiryQueuePrefix().toString());
      assertEquals(".EXP", conf.getAddressesSettings().get("a2").getExpiryQueueSuffix().toString());
      assertEquals(5, conf.getAddressesSettings().get("a2").getRedeliveryDelay());
      assertEquals(0.0, conf.getAddressesSettings().get("a2").getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(932489234928324L, conf.getAddressesSettings().get("a2").getMaxSizeBytes());
      assertEquals(712671626L, conf.getAddressesSettings().get("a2").getPageSizeBytes());
      assertEquals(20, conf.getAddressesSettings().get("a2").getPageCacheMaxSize());
      assertEquals(8, conf.getAddressesSettings().get("a2").getMessageCounterHistoryDayLimit());
      assertEquals(20, conf.getAddressesSettings().get("a2").getSlowConsumerThreshold());
      assertEquals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_DAY, conf.getAddressesSettings().get("a2").getSlowConsumerThresholdMeasurementUnit());
      assertEquals(15, conf.getAddressesSettings().get("a2").getSlowConsumerCheckPeriod());
      assertEquals(SlowConsumerPolicy.KILL, conf.getAddressesSettings().get("a2").getSlowConsumerPolicy());
      assertEquals(false, conf.getAddressesSettings().get("a2").isAutoCreateJmsQueues());
      assertEquals(false, conf.getAddressesSettings().get("a2").isAutoDeleteJmsQueues());
      assertEquals(false, conf.getAddressesSettings().get("a2").isAutoCreateJmsTopics());
      assertEquals(false, conf.getAddressesSettings().get("a2").isAutoDeleteJmsTopics());
      assertEquals(500, conf.getAddressesSettings().get("a2").getAutoDeleteQueuesDelay());
      assertEquals(1000, conf.getAddressesSettings().get("a2").getAutoDeleteAddressesDelay());
      assertEquals(true, conf.getAddressesSettings().get("a2").isDefaultPurgeOnNoConsumers());
      assertEquals(15, conf.getAddressesSettings().get("a2").getDefaultMaxConsumers());
      assertEquals(RoutingType.MULTICAST, conf.getAddressesSettings().get("a2").getDefaultQueueRoutingType());
      assertEquals(RoutingType.ANYCAST, conf.getAddressesSettings().get("a2").getDefaultAddressRoutingType());
      assertEquals(10000, conf.getAddressesSettings().get("a2").getDefaultConsumerWindowSize());
      assertEquals(-1, conf.getAddressesSettings().get("a2").getDefaultRingSize());
      assertEquals(10, conf.getAddressesSettings().get("a2").getRetroactiveMessageCount());
      assertFalse(conf.getAddressesSettings().get("a2").isEnableMetrics());
      assertFalse(conf.getAddressesSettings().get("a2").isEnableIngressTimestamp());

      assertTrue(conf.getResourceLimitSettings().containsKey("myUser"));
      assertEquals(104, conf.getResourceLimitSettings().get("myUser").getMaxConnections());
      assertEquals(13, conf.getResourceLimitSettings().get("myUser").getMaxQueues());

      assertEquals(2, conf.getQueueConfigs().size());

      assertEquals("queue1", conf.getQueueConfigs().get(0).getName().toString());
      assertEquals("address1", conf.getQueueConfigs().get(0).getAddress().toString());
      assertEquals("color='red'", conf.getQueueConfigs().get(0).getFilterString().toString());
      assertEquals(false, conf.getQueueConfigs().get(0).isDurable());

      assertEquals("queue2", conf.getQueueConfigs().get(1).getName().toString());
      assertEquals("address2", conf.getQueueConfigs().get(1).getAddress().toString());
      assertEquals("color='blue'", conf.getQueueConfigs().get(1).getFilterString().toString());
      assertEquals(false, conf.getQueueConfigs().get(1).isDurable());

      verifyAddresses();

      Map<String, Set<Role>> roles = conf.getSecurityRoles();

      assertEquals(2, roles.size());

      assertTrue(roles.containsKey("a1"));

      assertTrue(roles.containsKey("a2"));

      Role a1Role = roles.get("a1").toArray(new Role[1])[0];

      assertFalse(a1Role.isSend());
      assertFalse(a1Role.isConsume());
      assertFalse(a1Role.isCreateDurableQueue());
      assertFalse(a1Role.isDeleteDurableQueue());
      assertTrue(a1Role.isCreateNonDurableQueue());
      assertFalse(a1Role.isDeleteNonDurableQueue());
      assertFalse(a1Role.isManage());

      Role a2Role = roles.get("a2").toArray(new Role[1])[0];

      assertFalse(a2Role.isSend());
      assertFalse(a2Role.isConsume());
      assertFalse(a2Role.isCreateDurableQueue());
      assertFalse(a2Role.isDeleteDurableQueue());
      assertFalse(a2Role.isCreateNonDurableQueue());
      assertTrue(a2Role.isDeleteNonDurableQueue());
      assertFalse(a2Role.isManage());
      assertEquals(1234567, conf.getGlobalMaxSize());
      assertEquals(37, conf.getMaxDiskUsage());
      assertEquals(123, conf.getDiskScanPeriod());

      assertEquals(333, conf.getCriticalAnalyzerCheckPeriod());
      assertEquals(777, conf.getCriticalAnalyzerTimeout());
      assertEquals(false, conf.isCriticalAnalyzer());
      assertEquals(CriticalAnalyzerPolicy.HALT, conf.getCriticalAnalyzerPolicy());

      assertEquals(false, conf.isJournalDatasync());

      // keep test for backwards compatibility
      ActiveMQMetricsPlugin metricsPlugin = conf.getMetricsPlugin();
      assertTrue(metricsPlugin instanceof SimpleMetricsPlugin);
      Map<String, String> options = ((SimpleMetricsPlugin) metricsPlugin).getOptions();
      assertEquals("x", options.get("foo"));
      assertEquals("y", options.get("bar"));
      assertEquals("z", options.get("baz"));

      MetricsConfiguration metricsConfiguration = conf.getMetricsConfiguration();
      assertTrue(metricsConfiguration.getPlugin() instanceof SimpleMetricsPlugin);
      options = ((SimpleMetricsPlugin) metricsPlugin).getOptions();
      assertEquals("x", options.get("foo"));
      assertEquals("y", options.get("bar"));
      assertEquals("z", options.get("baz"));
      assertFalse(metricsConfiguration.isJvmMemory());
      assertTrue(metricsConfiguration.isJvmGc());
      assertTrue(metricsConfiguration.isJvmThread());
      assertTrue(metricsConfiguration.isNettyPool());
   }

   private void verifyAddresses() {
      assertEquals(3, conf.getAddressConfigurations().size());

      // Addr 1
      CoreAddressConfiguration addressConfiguration = conf.getAddressConfigurations().get(0);
      assertEquals("addr1", addressConfiguration.getName());
      Set<RoutingType> routingTypes = new HashSet<>();
      routingTypes.add(RoutingType.ANYCAST);
      assertEquals(routingTypes, addressConfiguration.getRoutingTypes());
      assertEquals(2, addressConfiguration.getQueueConfigs().size());

      // Addr 1 Queue 1
      QueueConfiguration queueConfiguration = addressConfiguration.getQueueConfigs().get(0);

      assertEquals("q1", queueConfiguration.getName().toString());
      assertEquals(3L, queueConfiguration.getRingSize().longValue());
      assertFalse(queueConfiguration.isDurable());
      assertEquals("color='blue'", queueConfiguration.getFilterString().toString());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), queueConfiguration.isPurgeOnNoConsumers());
      assertEquals("addr1", queueConfiguration.getAddress().toString());
      // If null, then default will be taken from address-settings (which defaults to ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers())
      assertEquals(null, queueConfiguration.getMaxConsumers());

      // Addr 1 Queue 2
      queueConfiguration = addressConfiguration.getQueueConfigs().get(1);

      assertEquals("q2", queueConfiguration.getName().toString());
      assertEquals(-1, queueConfiguration.getRingSize().longValue());
      assertTrue(queueConfiguration.isDurable());
      assertEquals("color='green'", queueConfiguration.getFilterString().toString());
      assertEquals(Queue.MAX_CONSUMERS_UNLIMITED, queueConfiguration.getMaxConsumers().intValue());
      assertFalse(queueConfiguration.isPurgeOnNoConsumers());
      assertEquals("addr1", queueConfiguration.getAddress().toString());

      // Addr 2
      addressConfiguration = conf.getAddressConfigurations().get(1);
      assertEquals("addr2", addressConfiguration.getName());
      routingTypes = new HashSet<>();
      routingTypes.add(RoutingType.MULTICAST);
      assertEquals(routingTypes, addressConfiguration.getRoutingTypes());
      assertEquals(2, addressConfiguration.getQueueConfigs().size());

      // Addr 2 Queue 1
      queueConfiguration = addressConfiguration.getQueueConfigs().get(0);

      assertEquals("q3", queueConfiguration.getName().toString());
      assertTrue(queueConfiguration.isDurable());
      assertEquals("color='red'", queueConfiguration.getFilterString().toString());
      assertEquals(10, queueConfiguration.getMaxConsumers().intValue());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), queueConfiguration.isPurgeOnNoConsumers());
      assertEquals("addr2", queueConfiguration.getAddress().toString());

      // Addr 2 Queue 2
      queueConfiguration = addressConfiguration.getQueueConfigs().get(1);

      assertEquals("q4", queueConfiguration.getName().toString());
      assertTrue(queueConfiguration.isDurable());
      assertNull(queueConfiguration.getFilterString());
      // If null, then default will be taken from address-settings (which defaults to ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers())
      assertEquals(null, queueConfiguration.getMaxConsumers());
      assertTrue(queueConfiguration.isPurgeOnNoConsumers());
      assertEquals("addr2", queueConfiguration.getAddress().toString());

      // Addr 3
      addressConfiguration = conf.getAddressConfigurations().get(2);
      assertEquals("addr2", addressConfiguration.getName());
      routingTypes = new HashSet<>();
      routingTypes.add(RoutingType.MULTICAST);
      routingTypes.add(RoutingType.ANYCAST);
      assertEquals(routingTypes, addressConfiguration.getRoutingTypes());
   }

   @Test
   public void testSecuritySettingPlugin() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("securitySettingPlugin.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      List<SecuritySettingPlugin> securitySettingPlugins = fc.getSecuritySettingPlugins();
      SecuritySettingPlugin securitySettingPlugin = securitySettingPlugins.get(0);
      assertTrue(securitySettingPlugin instanceof LegacyLDAPSecuritySettingPlugin);
      LegacyLDAPSecuritySettingPlugin legacyLDAPSecuritySettingPlugin = (LegacyLDAPSecuritySettingPlugin) securitySettingPlugin;
      assertEquals(legacyLDAPSecuritySettingPlugin.getInitialContextFactory(), "testInitialContextFactory");
      assertEquals(legacyLDAPSecuritySettingPlugin.getConnectionURL(), "testConnectionURL");
      assertEquals(legacyLDAPSecuritySettingPlugin.getConnectionUsername(), "testConnectionUsername");
      assertEquals(legacyLDAPSecuritySettingPlugin.getConnectionPassword(), "testConnectionPassword");
      assertEquals(legacyLDAPSecuritySettingPlugin.getConnectionProtocol(), "testConnectionProtocol");
      assertEquals(legacyLDAPSecuritySettingPlugin.getAuthentication(), "testAuthentication");
      assertEquals(legacyLDAPSecuritySettingPlugin.getDestinationBase(), "testDestinationBase");
      assertEquals(legacyLDAPSecuritySettingPlugin.getFilter(), "testFilter");
      assertEquals(legacyLDAPSecuritySettingPlugin.getRoleAttribute(), "testRoleAttribute");
      assertEquals(legacyLDAPSecuritySettingPlugin.getAdminPermissionValue(), "testAdminPermissionValue");
      assertEquals(legacyLDAPSecuritySettingPlugin.getReadPermissionValue(), "testReadPermissionValue");
      assertEquals(legacyLDAPSecuritySettingPlugin.getWritePermissionValue(), "testWritePermissionValue");
      assertEquals(legacyLDAPSecuritySettingPlugin.isEnableListener(), false);
   }

   @Test
   public void testSecurityRoleMapping() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("securityRoleMappings.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      Map<String, Set<Role>> securityRoles = fc.getSecurityRoles();
      Set<Role> roles = securityRoles.get("#");

      //cn=mygroup,dc=local,dc=com = amq1
      Role testRole1 = new Role("cn=mygroup,dc=local,dc=com", false, false, false, false, true, false, false, false, false, false);

      //myrole1 = amq1 + amq2
      Role testRole2 = new Role("myrole1", false, false, false, false, true, true, false, false, false, false);

      //myrole3 = amq3 + amq4
      Role testRole3 = new Role("myrole3", false, false, true, true, false, false, false, false, false, false);

      //myrole4 = amq5 + amq!@#$%^&*() + amq6
      Role testRole4 = new Role("myrole4", true, true, false, false, false, false, false, true, true, true);

      //myrole5 = amq4 = amq3 + amq4
      Role testRole5 = new Role("myrole5", false, false, true, true, false, false, false, false, false, false);

      Role testRole6 = new Role("amq1", false, false, false, false, true, false, false, false, false, false);

      Role testRole7 = new Role("amq2", false, false, false, false, false, true, false, false, false, false);

      Role testRole8 = new Role("amq3", false, false, true, false, false, false, false, false, false, false);

      Role testRole9 = new Role("amq4", false, false, true, true, false, false, false, false, false, false);

      Role testRole10 = new Role("amq5", false, false, false, false, false, false, false, false, true, true);

      Role testRole11 = new Role("amq6", false, true, false, false, false, false, false, true, false, false);

      Role testRole12 = new Role("amq7", false, false, false, false, false, false, true, false, false, false);

      Role testRole13 = new Role("amq!@#$%^&*()", true, false, false, false, false, false, false, false, false, false);

      assertEquals(13, roles.size());
      assertTrue(roles.contains(testRole1));
      assertTrue(roles.contains(testRole2));
      assertTrue(roles.contains(testRole3));
      assertTrue(roles.contains(testRole4));
      assertTrue(roles.contains(testRole5));
      assertTrue(roles.contains(testRole6));
      assertTrue(roles.contains(testRole7));
      assertTrue(roles.contains(testRole8));
      assertTrue(roles.contains(testRole9));
      assertTrue(roles.contains(testRole10));
      assertTrue(roles.contains(testRole11));
      assertTrue(roles.contains(testRole12));
      assertTrue(roles.contains(testRole13));
   }

   @Test
   public void testContextClassLoaderUsage() throws Exception {

      final File customConfiguration = File.createTempFile("hornetq-unittest", ".xml");

      try {

         // copy working configuration to a location where the standard classloader cannot find it
         final Path workingConfiguration = new File(getClass().getResource("/" + getConfigurationName()).toURI()).toPath();
         final Path targetFile = customConfiguration.toPath();

         Files.copy(workingConfiguration, targetFile, StandardCopyOption.REPLACE_EXISTING);

         // build a custom classloader knowing the location of the config created above (used as context class loader)
         final URL customConfigurationDirUrl = customConfiguration.getParentFile().toURI().toURL();
         final ClassLoader testWebappClassLoader = new URLClassLoader(new URL[]{customConfigurationDirUrl});

         /*
            run this in an own thread, avoid polluting the class loader of the thread context of the unit test engine,
            expect no exception in this thread when the class loading works as expected
          */

         final class ThrowableHolder {

            volatile Exception t;
         }

         final ThrowableHolder holder = new ThrowableHolder();

         final Thread webappContextThread = new Thread(new Runnable() {
            @Override
            public void run() {
               FileConfiguration fileConfiguration = new FileConfiguration();

               try {
                  FileDeploymentManager deploymentManager = new FileDeploymentManager(customConfiguration.getName());
                  deploymentManager.addDeployable(fileConfiguration);
                  deploymentManager.readConfiguration();
               } catch (Exception e) {
                  holder.t = e;
               }
            }
         });

         webappContextThread.setContextClassLoader(testWebappClassLoader);

         webappContextThread.start();
         webappContextThread.join();

         if (holder.t != null) {
            fail("Exception caught while loading configuration with the context class loader: " + holder.t.getMessage());
         }

      } finally {
         customConfiguration.delete();
      }
   }

   @Test
   public void testBrokerPlugin() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("brokerPlugin.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      List<ActiveMQServerBasePlugin> brokerPlugins = fc.getBrokerPlugins();
      assertEquals(2, brokerPlugins.size());
      assertTrue(brokerPlugins.get(0) instanceof EmptyPlugin1);
      assertTrue(brokerPlugins.get(1) instanceof EmptyPlugin2);
   }

   @Test
   public void testDefaultConstraints() {
      int defaultConfirmationWinSize = ActiveMQDefaultConfiguration.getDefaultClusterConfirmationWindowSize();
      int defaultIdCacheSize = ActiveMQDefaultConfiguration.getDefaultIdCacheSize();
      assertTrue("check failed, " + defaultConfirmationWinSize + ":" + defaultIdCacheSize, ConfigurationImpl.checkoutDupCacheSize(defaultConfirmationWinSize, defaultIdCacheSize));
      defaultConfirmationWinSize = ActiveMQDefaultConfiguration.getDefaultBridgeConfirmationWindowSize();
      assertTrue("check failed, " + defaultConfirmationWinSize + ":" + defaultIdCacheSize, ConfigurationImpl.checkoutDupCacheSize(defaultConfirmationWinSize, defaultIdCacheSize));
   }

   @Test
   public void testJournalFileOpenTimeoutDefaultValue() throws Exception {
      ActiveMQServerImpl server = new ActiveMQServerImpl();
      server.getConfiguration()
            .setJournalDirectory(getJournalDir())
            .setPagingDirectory(getPageDir())
            .setLargeMessagesDirectory(getLargeMessagesDir())
            .setBindingsDirectory(getBindingsDir());
      try {
         server.start();
         JournalImpl journal = (JournalImpl) server.getStorageManager().getBindingsJournal();
         Assert.assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalFileOpenTimeout(), journal.getFilesRepository().getJournalFileOpenTimeout());
         Assert.assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalFileOpenTimeout(), server.getConfiguration().getJournalFileOpenTimeout());
      } finally {
         server.stop();
      }
   }

   @Test
   public void testJournalFileOpenTimeoutValue() throws Exception {
      int timeout = RandomUtil.randomPositiveInt();
      Configuration configuration = createConfiguration("shared-store-master-hapolicy-config.xml");
      configuration.setJournalFileOpenTimeout(timeout)
                   .setJournalDirectory(getJournalDir())
                   .setPagingDirectory(getPageDir())
                   .setLargeMessagesDirectory(getLargeMessagesDir())
                   .setBindingsDirectory(getBindingsDir());
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         JournalImpl journal = (JournalImpl) server.getStorageManager().getBindingsJournal();
         Assert.assertEquals(timeout, journal.getFilesRepository().getJournalFileOpenTimeout());
         Assert.assertEquals(timeout, server.getConfiguration().getJournalFileOpenTimeout());
      } finally {
         server.stop();
      }
   }

   @Test
   public void testMetricsPlugin() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("metricsPlugin.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      ActiveMQMetricsPlugin metricPlugin = fc.getMetricsConfiguration().getPlugin();
      assertTrue(metricPlugin instanceof FakeMetricPlugin);

      Map<String, String> metricPluginOptions = ((FakeMetricPlugin)metricPlugin).getOptions();
      assertEquals("value1", metricPluginOptions.get("key1"));
      assertEquals("value2", metricPluginOptions.get("key2"));
      assertEquals("value3", metricPluginOptions.get("key3"));
   }

   @Test
   public void testMetrics() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("metrics.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();


      MetricsConfiguration metricsConfiguration = fc.getMetricsConfiguration();
      assertTrue(metricsConfiguration.isJvmMemory());
      assertTrue(metricsConfiguration.isJvmGc());
      assertTrue(metricsConfiguration.isJvmThread());
      assertTrue(metricsConfiguration.isNettyPool());

      ActiveMQMetricsPlugin metricPlugin = metricsConfiguration.getPlugin();
      assertTrue(metricPlugin instanceof FakeMetricPlugin);

      Map<String, String> metricPluginOptions = ((FakeMetricPlugin)metricPlugin).getOptions();
      assertEquals("value1", metricPluginOptions.get("key1"));
      assertEquals("value2", metricPluginOptions.get("key2"));
      assertEquals("value3", metricPluginOptions.get("key3"));
   }

   @Test
   public void testMetricsConflict() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("metricsConflict.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      ActiveMQMetricsPlugin metricPlugin = fc.getMetricsConfiguration().getPlugin();
      assertTrue(metricPlugin instanceof FakeMetricPlugin);

      Map<String, String> metricPluginOptions = ((FakeMetricPlugin)metricPlugin).getOptions();
      assertEquals("value1", metricPluginOptions.get("key1"));
      assertEquals("value2", metricPluginOptions.get("key2"));
      assertEquals("value3", metricPluginOptions.get("key3"));
   }

   @Override
   protected Configuration createConfiguration() throws Exception {
      // This may be set for the entire testsuite, but on this test we need this out
      System.clearProperty("brokerconfig.maxDiskUsage");
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(getConfigurationName());
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();
      return fc;
   }

   private Configuration createConfiguration(String filename) throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(filename);
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();
      return fc;
   }

   public static class EmptyPlugin1 implements ActiveMQServerPlugin {

   }

   public static class EmptyPlugin2 implements ActiveMQServerPlugin {

   }

   public static class FakeMetricPlugin implements ActiveMQMetricsPlugin {
      private Map<String, String> options;

      public Map<String, String> getOptions() {
         return options;
      }

      @Override
      public ActiveMQMetricsPlugin init(Map<String, String> options) {
         this.options = options;
         return this;
      }

      @Override
      public MeterRegistry getRegistry() {
         return null;
      }
   }
}
