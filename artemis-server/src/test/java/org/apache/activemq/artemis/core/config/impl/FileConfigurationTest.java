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

import static org.apache.activemq.artemis.core.config.impl.ConfigurationImpl.REDACTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.lang.invoke.MethodHandles;
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
import org.apache.activemq.artemis.core.config.routing.ConnectionRouterConfiguration;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.SecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.routing.policies.ConsistentHashModuloPolicy;
import org.apache.activemq.artemis.core.server.routing.policies.ConsistentHashPolicy;
import org.apache.activemq.artemis.core.server.routing.policies.FirstElementPolicy;
import org.apache.activemq.artemis.core.server.routing.policies.LeastConnectionsPolicy;
import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.LegacyLDAPSecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;
import org.apache.activemq.artemis.core.server.metrics.plugins.SimpleMetricsPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerBasePlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DiskFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.XmlProvider;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class FileConfigurationTest extends AbstractConfigurationTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static Boolean origXxeEnabled;

   protected boolean xxeEnabled;

   @Parameters(name = "xxeEnabled={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Boolean[] {true, false});
   }

   @BeforeAll
   public static void beforeAll() {
      if (origXxeEnabled == null) {
         origXxeEnabled = XmlProvider.isXxeEnabled();
      }

      logger.trace("BeforeAll - origXxeEnabled={}, isXxeEnabled={}", origXxeEnabled, XmlProvider.isXxeEnabled());
   }

   @AfterAll
   public static void afterAll() {
      logger.trace("AfterAll - origXxeEnabled={}, isXxeEnabled={} ", origXxeEnabled, XmlProvider.isXxeEnabled());
      if (origXxeEnabled != null) {
         logger.trace("AfterAll - Resetting XxeEnabled={}", origXxeEnabled);
         XmlProvider.setXxeEnabled(origXxeEnabled);
      }
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      logger.trace("Running setUp - xxeEnabled={}", xxeEnabled);
      XmlProvider.setXxeEnabled(xxeEnabled);

      setupProperties();

      super.setUp();
   }

   @AfterEach
   public void afterEach() {
      clearProperties();
   }

   public void setupProperties() {
      System.setProperty("a2Prop", "a2");
      System.setProperty("falseProp", "false");
      System.setProperty("trueProp", "true");
      System.setProperty("ninetyTwoProp", "92");
   }

   public void clearProperties() {
      System.clearProperty("a2Prop");
      System.clearProperty("falseProp");
      System.clearProperty("trueProp");
      System.clearProperty("ninetyTwoProp");
   }

   public FileConfigurationTest(boolean xxeEnabled) {
      this.xxeEnabled = xxeEnabled;
   }

   protected String getConfigurationName() {
      return "ConfigurationTest-full-config.xml";
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

   @TestTemplate
   public void testFileConfiguration() {
      validateFullConfig(conf);
   }

   private void validateFullConfig(Configuration configInstance) {
      validateFullConfig(configInstance, false);
   }

   private void validateFullConfig(Configuration configInstance, boolean fromProperties) {
      // Check they match the values from the test file
      assertEquals("SomeNameForUseOnTheApplicationServer", configInstance.getName());
      assertFalse(configInstance.isPersistenceEnabled());
      assertTrue(configInstance.isClustered());
      assertEquals(12345, configInstance.getScheduledThreadPoolMaxSize());
      assertEquals(54321, configInstance.getThreadPoolMaxSize());
      assertFalse(configInstance.isSecurityEnabled());
      assertEquals(5423, configInstance.getSecurityInvalidationInterval());
      assertEquals(333, configInstance.getAuthenticationCacheSize());
      assertEquals(444, configInstance.getAuthorizationCacheSize());
      assertTrue(configInstance.isWildcardRoutingEnabled());
      assertEquals(SimpleString.of("Giraffe"), configInstance.getManagementAddress());
      assertEquals(SimpleString.of("Whatever"), configInstance.getManagementNotificationAddress());
      assertEquals("Frog", configInstance.getClusterUser());
      assertEquals("Wombat", configInstance.getClusterPassword());
      assertFalse(configInstance.isJMXManagementEnabled());
      assertEquals("gro.qtenroh", configInstance.getJMXDomain());
      assertTrue(configInstance.isMessageCounterEnabled());
      assertEquals(5, configInstance.getMessageCounterMaxDayHistory());
      assertEquals(123456, configInstance.getMessageCounterSamplePeriod());
      assertEquals(12345, configInstance.getConnectionTTLOverride());
      assertEquals(98765, configInstance.getTransactionTimeout());
      assertEquals(56789, configInstance.getTransactionTimeoutScanPeriod());
      assertEquals(10111213, configInstance.getMessageExpiryScanPeriod());
      assertEquals(25000, configInstance.getAddressQueueScanPeriod());
      assertEquals(127, configInstance.getIDCacheSize());
      assertTrue(configInstance.isPersistIDCache());
      assertEquals(Integer.valueOf(777), configInstance.getJournalDeviceBlockSize());
      assertTrue(configInstance.isPersistDeliveryCountBeforeDelivery());
      assertEquals("pagingdir", configInstance.getPagingDirectory());
      assertEquals("somedir", configInstance.getBindingsDirectory());
      assertFalse(configInstance.isCreateBindingsDir());
      assertTrue(configInstance.isAmqpUseCoreSubscriptionNaming());
      assertFalse(configInstance.isSuppressSessionNotifications());
      assertEquals("()", configInstance.getLiteralMatchMarkers());

      assertEquals(17, configInstance.getPageMaxConcurrentIO(), "max concurrent io");
      assertTrue(configInstance.isReadWholePage());
      assertEquals("somedir2", configInstance.getJournalDirectory());
      assertEquals("history", configInstance.getJournalRetentionDirectory());
      assertEquals(10L * 1024L * 1024L * 1024L, configInstance.getJournalRetentionMaxBytes());
      assertEquals(TimeUnit.DAYS.toMillis(365), configInstance.getJournalRetentionPeriod());
      assertFalse(configInstance.isCreateJournalDir());
      assertEquals(JournalType.NIO, configInstance.getJournalType());
      assertEquals(10000, configInstance.getJournalBufferSize_NIO());
      assertEquals(1000, configInstance.getJournalBufferTimeout_NIO());
      assertEquals(56546, configInstance.getJournalMaxIO_NIO());
      assertEquals(9876, configInstance.getJournalFileOpenTimeout());

      assertFalse(configInstance.isJournalSyncTransactional());
      assertTrue(configInstance.isJournalSyncNonTransactional());
      assertEquals(12345678, configInstance.getJournalFileSize());
      assertEquals(100, configInstance.getJournalMinFiles());
      assertEquals(123, configInstance.getJournalCompactMinFiles());
      assertEquals(33, configInstance.getJournalCompactPercentage());
      assertEquals(7654, configInstance.getJournalLockAcquisitionTimeout());
      assertTrue(configInstance.isGracefulShutdownEnabled());
      assertEquals(12345, configInstance.getGracefulShutdownTimeout());
      assertTrue(configInstance.isPopulateValidatedUser());
      assertFalse(configInstance.isRejectEmptyValidatedUser());
      assertEquals(123456, configInstance.getMqttSessionScanInterval());
      assertEquals(567890, configInstance.getMqttSessionStatePersistenceTimeout());
      assertFalse(configInstance.isMqttSubscriptionPersistenceEnabled());
      assertEquals(98765, configInstance.getConnectionTtlCheckInterval());
      assertEquals(1234567, configInstance.getConfigurationFileRefreshPeriod());
      assertEquals("UUID", configInstance.getTemporaryQueueNamespace());
      assertEquals("UUID", configInstance.getUuidNamespace());

      assertEquals("127.0.0.1", configInstance.getNetworkCheckList());
      assertEquals("some-nick", configInstance.getNetworkCheckNIC());
      assertEquals(123, configInstance.getNetworkCheckPeriod());
      assertEquals(321, configInstance.getNetworkCheckTimeout());
      assertEquals("ping-four", configInstance.getNetworkCheckPingCommand());
      assertEquals("ping-six", configInstance.getNetworkCheckPing6Command());

      assertEquals("largemessagesdir", configInstance.getLargeMessagesDirectory());
      assertEquals(95, configInstance.getMemoryWarningThreshold());

      assertEquals(2, configInstance.getIncomingInterceptorClassNames().size());
      assertTrue(configInstance.getIncomingInterceptorClassNames().contains("org.apache.activemq.artemis.tests.unit.core.config.impl.TestInterceptor1"));
      assertTrue(configInstance.getIncomingInterceptorClassNames().contains("org.apache.activemq.artemis.tests.unit.core.config.impl.TestInterceptor2"));

      assertEquals(2, configInstance.getConnectorConfigurations().size());

      TransportConfiguration tc = configInstance.getConnectorConfigurations().get("connector1");
      assertNotNull(tc);
      assertEquals("org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory", tc.getFactoryClassName());
      assertEquals("mylocal", tc.getParams().get("localAddress"));
      assertEquals("99", tc.getParams().get("localPort"));
      assertEquals("localhost1", tc.getParams().get("host"));
      assertEquals("5678", tc.getParams().get("port"));

      tc = configInstance.getConnectorConfigurations().get("connector2");
      assertNotNull(tc);
      assertEquals("org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory", tc.getFactoryClassName());
      assertEquals("5", tc.getParams().get("serverId"));

      assertEquals(2, configInstance.getAcceptorConfigurations().size());
      for (TransportConfiguration ac : configInstance.getAcceptorConfigurations()) {
         if (ac.getFactoryClassName().equals("org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory")) {
            assertEquals("456", ac.getParams().get("tcpNoDelay"));
            assertEquals("44", ac.getParams().get("connectionTtl"));
            assertEquals("92", ac.getParams().get(TransportConstants.CONNECTIONS_ALLOWED));
         } else {
            assertEquals("org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory", ac.getFactoryClassName());
            assertEquals("0", ac.getParams().get("serverId"));
            assertEquals("87", ac.getParams().get(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.CONNECTIONS_ALLOWED));
         }
      }

      assertEquals(2, configInstance.getBroadcastGroupConfigurations().size());
      for (BroadcastGroupConfiguration bc : configInstance.getBroadcastGroupConfigurations()) {
         UDPBroadcastEndpointFactory udpBc = (UDPBroadcastEndpointFactory) bc.getEndpointFactory();
         if (bc.getName().equals("bg1")) {
            assertEquals("bg1", bc.getName());
            assertEquals(10999, udpBc.getLocalBindPort());
            assertEquals("192.168.0.120", udpBc.getGroupAddress());
            assertEquals(11999, udpBc.getGroupPort());
            assertEquals(12345, bc.getBroadcastPeriod());
            assertEquals("connector1", bc.getConnectorInfos().get(0));
         } else {
            assertEquals("bg2", bc.getName());
            assertEquals(12999, udpBc.getLocalBindPort());
            assertEquals("192.168.0.121", udpBc.getGroupAddress());
            assertEquals(13999, udpBc.getGroupPort());
            assertEquals(23456, bc.getBroadcastPeriod());
            assertEquals("connector2", bc.getConnectorInfos().get(0));
         }
      }

      assertEquals(2, configInstance.getDiscoveryGroupConfigurations().size());
      DiscoveryGroupConfiguration dc = configInstance.getDiscoveryGroupConfigurations().get("dg1");
      assertEquals("dg1", dc.getName());
      assertEquals("192.168.0.120", ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getGroupAddress());
      assertEquals("172.16.8.10", ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getLocalBindAddress());
      assertEquals(11999, ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getGroupPort());
      assertEquals(12345, dc.getRefreshTimeout());

      dc = configInstance.getDiscoveryGroupConfigurations().get("dg2");
      assertEquals("dg2", dc.getName());
      assertEquals("192.168.0.121", ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getGroupAddress());
      assertEquals("172.16.8.11", ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getLocalBindAddress());
      assertEquals(12999, ((UDPBroadcastEndpointFactory) dc.getBroadcastEndpointFactory()).getGroupPort());
      assertEquals(23456, dc.getRefreshTimeout());

      assertEquals(3, configInstance.getDivertConfigurations().size());
      for (DivertConfiguration dic : configInstance.getDivertConfigurations()) {
         if (dic.getName().equals("divert1")) {
            assertEquals("divert1", dic.getName());
            assertEquals("routing-name1", dic.getRoutingName());
            assertEquals("address1", dic.getAddress());
            assertEquals("forwarding-address1", dic.getForwardingAddress());
            assertEquals("speed > 88", dic.getFilterString());
            assertEquals("org.foo.Transformer", dic.getTransformerConfiguration().getClassName());
            assertTrue(dic.isExclusive());
         } else if (dic.getName().equals("divert2")) {
            assertEquals("divert2", dic.getName());
            assertEquals("routing-name2", dic.getRoutingName());
            assertEquals("address2", dic.getAddress());
            assertEquals("forwarding-address2", dic.getForwardingAddress());
            assertEquals("speed < 88", dic.getFilterString());
            assertEquals("org.foo.Transformer2", dic.getTransformerConfiguration().getClassName());
            assertFalse(dic.isExclusive());
         } else {
            assertEquals("divert3", dic.getName());
            assertEquals("org.foo.DivertTransformer3", dic.getTransformerConfiguration().getClassName());
            assertEquals("divertTransformerValue1", dic.getTransformerConfiguration().getProperties().get("divertTransformerKey1"));
            assertEquals("divertTransformerValue2", dic.getTransformerConfiguration().getProperties().get("divertTransformerKey2"));
         }
      }

      assertEquals(6, configInstance.getConnectionRouters().size());
      for (ConnectionRouterConfiguration bc : configInstance.getConnectionRouters()) {
         if (bc.getName().equals("simple-local")) {
            assertEquals(KeyType.CLIENT_ID, bc.getKeyType());
            assertNotNull(bc.getLocalTargetFilter());
            assertNotNull(bc.getKeyFilter());
            assertNull(bc.getPolicyConfiguration());
         } else if (bc.getName().equals("simple-local-with-transformer")) {
            assertEquals(KeyType.CLIENT_ID, bc.getKeyType());
            assertNotNull(bc.getLocalTargetFilter());
            assertNotNull(bc.getKeyFilter());
            assertNotNull(bc.getPolicyConfiguration());
            assertNotNull(bc.getPolicyConfiguration().getProperties().get(ConsistentHashModuloPolicy.MODULO));
         } else if (bc.getName().equals("simple-router")) {
            assertEquals(KeyType.USER_NAME, bc.getKeyType());
            assertNull(bc.getLocalTargetFilter());
            assertEquals(FirstElementPolicy.NAME, bc.getPolicyConfiguration().getName());
            assertFalse(bc.getPoolConfiguration().isLocalTargetEnabled());
            assertEquals("connector1", bc.getPoolConfiguration().getStaticConnectors().get(0));
            assertNull(bc.getPoolConfiguration().getDiscoveryGroupName());
         } else if (bc.getName().equals("simple-router-connector2")) {
            assertEquals(KeyType.USER_NAME, bc.getKeyType());
            assertNull(bc.getLocalTargetFilter());
            assertEquals(FirstElementPolicy.NAME, bc.getPolicyConfiguration().getName());
            assertFalse(bc.getPoolConfiguration().isLocalTargetEnabled());
            assertEquals("connector2", bc.getPoolConfiguration().getStaticConnectors().get(0));
            assertNull(bc.getPoolConfiguration().getDiscoveryGroupName());
         } else if (bc.getName().equals("consistent-hash-router")) {
            assertEquals(KeyType.SNI_HOST, bc.getKeyType());
            assertEquals("^[^.]+", bc.getKeyFilter());
            assertEquals("DEFAULT", bc.getLocalTargetFilter());
            assertEquals(ConsistentHashPolicy.NAME, bc.getPolicyConfiguration().getName());
            assertEquals(1000, bc.getPoolConfiguration().getCheckPeriod());
            assertTrue(bc.getPoolConfiguration().isLocalTargetEnabled());
            assertNull(bc.getPoolConfiguration().getStaticConnectors());
            assertEquals("dg1", bc.getPoolConfiguration().getDiscoveryGroupName());
         } else {
            assertEquals(KeyType.SOURCE_IP, bc.getKeyType());
            assertEquals("least-connections-router", bc.getName());
            assertNotNull(bc.getCacheConfiguration());
            assertTrue(bc.getCacheConfiguration().isPersisted());
            assertEquals(60000, bc.getCacheConfiguration().getTimeout());
            assertEquals(LeastConnectionsPolicy.NAME, bc.getPolicyConfiguration().getName());
            assertEquals(3000, bc.getPoolConfiguration().getCheckPeriod());
            assertEquals(2, bc.getPoolConfiguration().getQuorumSize());
            assertEquals(1000, bc.getPoolConfiguration().getQuorumTimeout());
            assertFalse(bc.getPoolConfiguration().isLocalTargetEnabled());
            assertNull(bc.getPoolConfiguration().getStaticConnectors());
            assertEquals("dg2", bc.getPoolConfiguration().getDiscoveryGroupName());
         }
      }

      assertEquals(4, configInstance.getBridgeConfigurations().size());
      for (BridgeConfiguration bc : configInstance.getBridgeConfigurations()) {
         if (bc.getName().equals("bridge1")) {
            assertEquals("bridge1", bc.getName());
            assertEquals("queue1", bc.getQueueName());
            assertEquals(4194304, bc.getMinLargeMessageSize(), "minLargeMessageSize");
            assertEquals(31, bc.getClientFailureCheckPeriod(), "check-period");
            assertEquals(370, bc.getConnectionTTL(), "connection time-to-live");
            assertEquals("bridge-forwarding-address1", bc.getForwardingAddress());
            assertEquals("sku > 1", bc.getFilterString());
            assertEquals("org.foo.BridgeTransformer", bc.getTransformerConfiguration().getClassName());
            assertEquals(3, bc.getRetryInterval());
            assertEquals(0.2, bc.getRetryIntervalMultiplier(), 0.0001);
            assertEquals(10002, bc.getMaxRetryInterval(), "max retry interval");
            assertEquals(2, bc.getReconnectAttempts());
            assertTrue(bc.isUseDuplicateDetection());
            assertEquals("connector1", bc.getStaticConnectors().get(0));
            assertNull(bc.getDiscoveryGroupName());
            assertEquals(444, bc.getProducerWindowSize());
            assertEquals(1073741824, bc.getConfirmationWindowSize());
            assertEquals(ComponentConfigurationRoutingType.STRIP, bc.getRoutingType());
         } else if (bc.getName().equals("bridge2")) {
            assertEquals("bridge2", bc.getName());
            assertEquals("queue2", bc.getQueueName());
            assertEquals("bridge-forwarding-address2", bc.getForwardingAddress());
            assertNull(bc.getFilterString());
            assertNull(bc.getTransformerConfiguration());
            assertNull(bc.getStaticConnectors());
            assertEquals("dg1", bc.getDiscoveryGroupName());
            assertEquals(568320, bc.getProducerWindowSize());
            assertEquals(ComponentConfigurationRoutingType.PASS, bc.getRoutingType());
            assertNull(bc.getClientId());
         } else if (bc.getName().equals("bridge3")) {
            assertEquals("bridge3", bc.getName());
            assertEquals("org.foo.BridgeTransformer3", bc.getTransformerConfiguration().getClassName());
            assertEquals("bridgeTransformerValue1", bc.getTransformerConfiguration().getProperties().get("bridgeTransformerKey1"));
            assertEquals("bridgeTransformerValue2", bc.getTransformerConfiguration().getProperties().get("bridgeTransformerKey2"));
            assertEquals(123456, bc.getPendingAckTimeout());
            assertEquals("myClientID", bc.getClientId());
         }
      }

      assertEquals(3, configInstance.getClusterConfigurations().size());

      HAPolicyConfiguration pc = configInstance.getHAPolicyConfiguration();
      assertNotNull(pc);
      assertInstanceOf(PrimaryOnlyPolicyConfiguration.class, pc);
      PrimaryOnlyPolicyConfiguration lopc = (PrimaryOnlyPolicyConfiguration) pc;
      assertNotNull(lopc.getScaleDownConfiguration());
      assertEquals("boo!", lopc.getScaleDownConfiguration().getGroupName());
      assertEquals("dg1", lopc.getScaleDownConfiguration().getDiscoveryGroup());
      assertEquals(33, lopc.getScaleDownConfiguration().getCommitInterval());

      for (ClusterConnectionConfiguration ccc : configInstance.getClusterConfigurations()) {
         if (ccc.getName().equals("cluster-connection3")) {
            assertEquals(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, ccc.getMessageLoadBalancingType());
            assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterCallTimeout(), ccc.getCallTimeout());
            assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterCallFailoverTimeout(), ccc.getCallFailoverTimeout());
            assertEquals("myClientID", ccc.getClientId());
            assertEquals(ActiveMQDefaultConfiguration.getClusterTopologyScannerAttempts(), ccc.getTopologyScannerAttempts());
         } else if (ccc.getName().equals("cluster-connection1")) {
            assertEquals("cluster-connection1", ccc.getName());
            assertEquals(321, ccc.getMinLargeMessageSize(), "clusterConnectionConf minLargeMessageSize");
            assertEquals(331, ccc.getClientFailureCheckPeriod(), "check-period");
            assertEquals(3370, ccc.getConnectionTTL(), "connection time-to-live");
            assertEquals("queues1", ccc.getAddress());
            assertEquals(3, ccc.getRetryInterval());
            assertTrue(ccc.isDuplicateDetection());
            assertEquals(MessageLoadBalancingType.ON_DEMAND, ccc.getMessageLoadBalancingType());
            assertEquals(1, ccc.getMaxHops());
            assertEquals(123, ccc.getCallTimeout());
            assertEquals(123, ccc.getCallFailoverTimeout());
            assertEquals(0.25, ccc.getRetryIntervalMultiplier(), 0.00001, "multiplier");
            assertEquals(10000, ccc.getMaxRetryInterval(), "max retry interval");
            assertEquals(72, ccc.getReconnectAttempts());
            assertEquals("connector1", ccc.getStaticConnectors().get(0));
            assertEquals("connector2", ccc.getStaticConnectors().get(1));
            assertNull(ccc.getDiscoveryGroupName());
            assertEquals(222, ccc.getProducerWindowSize());
            assertTrue(ccc.isAllowDirectConnectionsOnly());
            assertEquals(-1, ccc.getTopologyScannerAttempts());
         } else {
            assertEquals("cluster-connection2", ccc.getName());
            assertEquals("queues2", ccc.getAddress());
            assertEquals(4, ccc.getRetryInterval());
            assertEquals(456, ccc.getCallTimeout());
            assertEquals(456, ccc.getCallFailoverTimeout());
            assertFalse(ccc.isDuplicateDetection());
            assertEquals(MessageLoadBalancingType.STRICT, ccc.getMessageLoadBalancingType());
            assertEquals(2, ccc.getMaxHops());
            assertEquals(Collections.emptyList(), ccc.getStaticConnectors());
            assertEquals("dg1", ccc.getDiscoveryGroupName());
            assertEquals(333, ccc.getProducerWindowSize());
            assertFalse(ccc.isAllowDirectConnectionsOnly());
            assertEquals(0, ccc.getTopologyScannerAttempts());
         }
      }

      assertEquals(2, configInstance.getAddressSettings().size());

      assertNotNull(configInstance.getAddressSettings().get("a1"));
      assertNotNull(configInstance.getAddressSettings().get("a2"));

      assertEquals("a1.1", configInstance.getAddressSettings().get("a1").getDeadLetterAddress().toString());
      assertEquals(AddressSettings.DEFAULT_AUTO_CREATE_DEAD_LETTER_RESOURCES, configInstance.getAddressSettings().get("a1").isAutoCreateDeadLetterResources());
      assertEquals(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_PREFIX, configInstance.getAddressSettings().get("a1").getDeadLetterQueuePrefix());
      assertEquals(AddressSettings.DEFAULT_DEAD_LETTER_QUEUE_SUFFIX, configInstance.getAddressSettings().get("a1").getDeadLetterQueueSuffix());
      assertEquals("a1.2", configInstance.getAddressSettings().get("a1").getExpiryAddress().toString());
      assertEquals(1L, (long) configInstance.getAddressSettings().get("a1").getExpiryDelay());
      assertEquals(2L, (long) configInstance.getAddressSettings().get("a1").getMinExpiryDelay());
      assertEquals(3L, (long) configInstance.getAddressSettings().get("a1").getMaxExpiryDelay());
      assertTrue(configInstance.getAddressSettings().get("a1").isNoExpiry());
      assertEquals(DiskFullMessagePolicy.DROP, configInstance.getAddressSettings().get("a1").getDiskFullMessagePolicy());
      assertEquals(AddressSettings.DEFAULT_AUTO_CREATE_EXPIRY_RESOURCES, configInstance.getAddressSettings().get("a1").isAutoCreateExpiryResources());
      assertEquals(AddressSettings.DEFAULT_EXPIRY_QUEUE_PREFIX, configInstance.getAddressSettings().get("a1").getExpiryQueuePrefix());
      assertEquals(AddressSettings.DEFAULT_EXPIRY_QUEUE_SUFFIX, configInstance.getAddressSettings().get("a1").getExpiryQueueSuffix());
      assertEquals(1, configInstance.getAddressSettings().get("a1").getRedeliveryDelay());
      assertEquals(0.5, configInstance.getAddressSettings().get("a1").getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(856686592L, configInstance.getAddressSettings().get("a1").getMaxSizeBytes());
      assertEquals(817381738L, configInstance.getAddressSettings().get("a1").getPageSizeBytes());
      assertEquals(10, configInstance.getAddressSettings().get("a1").getPageCacheMaxSize());
      assertEquals(4, configInstance.getAddressSettings().get("a1").getMessageCounterHistoryDayLimit());
      assertEquals(10, configInstance.getAddressSettings().get("a1").getSlowConsumerThreshold());
      assertEquals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_HOUR, configInstance.getAddressSettings().get("a1").getSlowConsumerThresholdMeasurementUnit());
      assertEquals(5, configInstance.getAddressSettings().get("a1").getSlowConsumerCheckPeriod());
      assertEquals(SlowConsumerPolicy.NOTIFY, configInstance.getAddressSettings().get("a1").getSlowConsumerPolicy());
      assertTrue(configInstance.getAddressSettings().get("a1").isAutoCreateJmsQueues());
      assertTrue(configInstance.getAddressSettings().get("a1").isAutoDeleteJmsQueues());
      assertTrue(configInstance.getAddressSettings().get("a1").isAutoCreateJmsTopics());
      assertTrue(configInstance.getAddressSettings().get("a1").isAutoDeleteJmsTopics());
      assertEquals(0, configInstance.getAddressSettings().get("a1").getAutoDeleteQueuesDelay());
      assertFalse(configInstance.getAddressSettings().get("a1").getAutoDeleteQueuesSkipUsageCheck());
      assertEquals(0, configInstance.getAddressSettings().get("a1").getAutoDeleteAddressesDelay());
      assertFalse(configInstance.getAddressSettings().get("a1").isAutoDeleteAddressesSkipUsageCheck());
      assertNotNull(configInstance.getAddressSettings().get("a1").isDefaultPurgeOnNoConsumers());
      assertFalse(configInstance.getAddressSettings().get("a1").isDefaultPurgeOnNoConsumers());
      assertEquals(Integer.valueOf(5), configInstance.getAddressSettings().get("a1").getDefaultMaxConsumers());
      assertEquals(RoutingType.ANYCAST, configInstance.getAddressSettings().get("a1").getDefaultQueueRoutingType());
      assertEquals(RoutingType.MULTICAST, configInstance.getAddressSettings().get("a1").getDefaultAddressRoutingType());
      assertEquals(3, configInstance.getAddressSettings().get("a1").getDefaultRingSize());
      assertEquals(0, configInstance.getAddressSettings().get("a1").getRetroactiveMessageCount());
      assertTrue(configInstance.getAddressSettings().get("a1").isEnableMetrics());
      assertTrue(configInstance.getAddressSettings().get("a1").isEnableIngressTimestamp());
      assertNull(configInstance.getAddressSettings().get("a1").getIDCacheSize());
      assertNull(configInstance.getAddressSettings().get("a1").getInitialQueueBufferSize());

      assertEquals("a2.1", configInstance.getAddressSettings().get("a2").getDeadLetterAddress().toString());
      assertTrue(configInstance.getAddressSettings().get("a2").isAutoCreateDeadLetterResources());
      assertEquals("", configInstance.getAddressSettings().get("a2").getDeadLetterQueuePrefix().toString());
      assertEquals(".DLQ", configInstance.getAddressSettings().get("a2").getDeadLetterQueueSuffix().toString());
      assertEquals("a2.2", configInstance.getAddressSettings().get("a2").getExpiryAddress().toString());
      assertEquals(-1L, (long) configInstance.getAddressSettings().get("a2").getExpiryDelay());
      assertEquals(-1L, (long) configInstance.getAddressSettings().get("a2").getMinExpiryDelay());
      assertEquals(-1L, (long) configInstance.getAddressSettings().get("a2").getMaxExpiryDelay());
      assertFalse(configInstance.getAddressSettings().get("a2").isNoExpiry());
      assertNull(configInstance.getAddressSettings().get("a2").getDiskFullMessagePolicy());
      assertTrue(configInstance.getAddressSettings().get("a2").isAutoCreateDeadLetterResources());
      assertEquals("", configInstance.getAddressSettings().get("a2").getExpiryQueuePrefix().toString());
      assertEquals(".EXP", configInstance.getAddressSettings().get("a2").getExpiryQueueSuffix().toString());
      assertEquals(5, configInstance.getAddressSettings().get("a2").getRedeliveryDelay());
      assertEquals(0.0, configInstance.getAddressSettings().get("a2").getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(932489234928324L, configInstance.getAddressSettings().get("a2").getMaxSizeBytes());
      assertEquals(712671626L, configInstance.getAddressSettings().get("a2").getPageSizeBytes());
      assertEquals(20, configInstance.getAddressSettings().get("a2").getPageCacheMaxSize());
      assertEquals(8, configInstance.getAddressSettings().get("a2").getMessageCounterHistoryDayLimit());
      assertEquals(20, configInstance.getAddressSettings().get("a2").getSlowConsumerThreshold());
      assertEquals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_DAY, configInstance.getAddressSettings().get("a2").getSlowConsumerThresholdMeasurementUnit());
      assertEquals(15, configInstance.getAddressSettings().get("a2").getSlowConsumerCheckPeriod());
      assertEquals(SlowConsumerPolicy.KILL, configInstance.getAddressSettings().get("a2").getSlowConsumerPolicy());
      assertFalse(configInstance.getAddressSettings().get("a2").isAutoCreateJmsQueues());
      assertFalse(configInstance.getAddressSettings().get("a2").isAutoDeleteJmsQueues());
      assertFalse(configInstance.getAddressSettings().get("a2").isAutoCreateJmsTopics());
      assertFalse(configInstance.getAddressSettings().get("a2").isAutoDeleteJmsTopics());
      assertEquals(500, configInstance.getAddressSettings().get("a2").getAutoDeleteQueuesDelay());
      assertTrue(configInstance.getAddressSettings().get("a2").getAutoDeleteQueuesSkipUsageCheck());
      assertEquals(1000, configInstance.getAddressSettings().get("a2").getAutoDeleteAddressesDelay());
      assertTrue(configInstance.getAddressSettings().get("a2").isAutoDeleteAddressesSkipUsageCheck());
      assertNotNull(configInstance.getAddressSettings().get("a2").isDefaultPurgeOnNoConsumers());
      assertTrue(configInstance.getAddressSettings().get("a2").isDefaultPurgeOnNoConsumers());
      assertEquals(Integer.valueOf(15), configInstance.getAddressSettings().get("a2").getDefaultMaxConsumers());
      assertEquals(RoutingType.MULTICAST, configInstance.getAddressSettings().get("a2").getDefaultQueueRoutingType());
      assertEquals(RoutingType.ANYCAST, configInstance.getAddressSettings().get("a2").getDefaultAddressRoutingType());
      assertEquals(10000, configInstance.getAddressSettings().get("a2").getDefaultConsumerWindowSize());
      assertEquals(-1, configInstance.getAddressSettings().get("a2").getDefaultRingSize());
      assertEquals(10, configInstance.getAddressSettings().get("a2").getRetroactiveMessageCount());
      assertFalse(configInstance.getAddressSettings().get("a2").isEnableMetrics());
      assertFalse(configInstance.getAddressSettings().get("a2").isEnableIngressTimestamp());
      assertEquals(Integer.valueOf(500), configInstance.getAddressSettings().get("a2").getIDCacheSize());
      assertEquals(Integer.valueOf(128), configInstance.getAddressSettings().get("a2").getInitialQueueBufferSize());

      assertEquals(111, configInstance.getMirrorAckManagerQueueAttempts());
      assertTrue(configInstance.isMirrorAckManagerWarnUnacked());
      assertEquals(222, configInstance.getMirrorAckManagerPageAttempts());
      assertEquals(333, configInstance.getMirrorAckManagerRetryDelay());
      assertTrue(configInstance.isMirrorPageTransaction());

      assertTrue(configInstance.getResourceLimitSettings().containsKey("myUser"));
      // continue testing deprecated method
      assertEquals(104, configInstance.getResourceLimitSettings().get("myUser").getMaxConnections());
      assertEquals(104, configInstance.getResourceLimitSettings().get("myUser").getMaxSessions());
      assertEquals(13, configInstance.getResourceLimitSettings().get("myUser").getMaxQueues());

      if (!fromProperties) {
         // we don't export deprecated entries
         assertEquals(2, configInstance.getQueueConfigs().size());

         assertEquals("queue1", configInstance.getQueueConfigs().get(0).getName().toString());
         assertEquals("address1", configInstance.getQueueConfigs().get(0).getAddress().toString());
         assertEquals("color='red'", configInstance.getQueueConfigs().get(0).getFilterString().toString());
         assertNotNull(configInstance.getQueueConfigs().get(0).isDurable());
         assertFalse(configInstance.getQueueConfigs().get(0).isDurable());

         assertEquals("queue2", configInstance.getQueueConfigs().get(1).getName().toString());
         assertEquals("address2", configInstance.getQueueConfigs().get(1).getAddress().toString());
         assertEquals("color='blue'", configInstance.getQueueConfigs().get(1).getFilterString().toString());
         assertNotNull(configInstance.getQueueConfigs().get(1).isDurable());
         assertFalse(configInstance.getQueueConfigs().get(1).isDurable());
      }
      verifyAddresses();

      Map<String, Set<Role>> roles = configInstance.getSecurityRoles();

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
      assertEquals(1234567, configInstance.getGlobalMaxSize());
      assertEquals(30, configInstance.getGlobalMaxSizePercentOfJvmMaxMemory());
      assertEquals(37, configInstance.getMaxDiskUsage());
      assertEquals(123, configInstance.getDiskScanPeriod());

      assertEquals(333, configInstance.getCriticalAnalyzerCheckPeriod());
      assertEquals(777, configInstance.getCriticalAnalyzerTimeout());
      assertFalse(configInstance.isCriticalAnalyzer());
      assertEquals(CriticalAnalyzerPolicy.HALT, configInstance.getCriticalAnalyzerPolicy());

      assertFalse(configInstance.isJournalDatasync());

      // keep test for backwards compatibility
      ActiveMQMetricsPlugin metricsPlugin = configInstance.getMetricsPlugin();
      assertInstanceOf(SimpleMetricsPlugin.class, metricsPlugin);
      Map<String, String> options = ((SimpleMetricsPlugin) metricsPlugin).getOptions();
      assertEquals("x", options.get("foo"));
      assertEquals("y", options.get("bar"));
      assertEquals("z", options.get("baz"));

      MetricsConfiguration metricsConfiguration = configInstance.getMetricsConfiguration();
      assertInstanceOf(SimpleMetricsPlugin.class, metricsConfiguration.getPlugin());
      options = ((SimpleMetricsPlugin) metricsPlugin).getOptions();
      assertEquals("x", options.get("foo"));
      assertEquals("y", options.get("bar"));
      assertEquals("z", options.get("baz"));
      assertFalse(metricsConfiguration.isJvmMemory());
      assertTrue(metricsConfiguration.isJvmGc());
      assertTrue(metricsConfiguration.isJvmThread());
      assertTrue(metricsConfiguration.isNettyPool());
      assertTrue(metricsConfiguration.isFileDescriptors());
      assertTrue(metricsConfiguration.isProcessor());
      assertTrue(metricsConfiguration.isUptime());
      assertTrue(metricsConfiguration.isLogging());
      assertTrue(metricsConfiguration.isSecurityCaches());
      assertTrue(metricsConfiguration.isExecutorServices());
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
      assertNull(queueConfiguration.getMaxConsumers());
      assertNull(queueConfiguration.isGroupRebalancePauseDispatch());

      // Addr 1 Queue 2
      queueConfiguration = addressConfiguration.getQueueConfigs().get(1);

      assertEquals("q2", queueConfiguration.getName().toString());
      assertEquals(-1, queueConfiguration.getRingSize().longValue());
      assertTrue(queueConfiguration.isDurable());
      assertEquals("color='green'", queueConfiguration.getFilterString().toString());
      assertEquals(Queue.MAX_CONSUMERS_UNLIMITED, queueConfiguration.getMaxConsumers().intValue());
      assertFalse(queueConfiguration.isPurgeOnNoConsumers());
      assertEquals("addr1", queueConfiguration.getAddress().toString());
      assertNotNull(queueConfiguration.isGroupRebalancePauseDispatch());
      assertTrue(queueConfiguration.isGroupRebalancePauseDispatch());

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
      assertNull(queueConfiguration.isGroupRebalancePauseDispatch());

      // Addr 2 Queue 2
      queueConfiguration = addressConfiguration.getQueueConfigs().get(1);

      assertEquals("q4", queueConfiguration.getName().toString());
      assertTrue(queueConfiguration.isDurable());
      assertNull(queueConfiguration.getFilterString());
      // If null, then default will be taken from address-settings (which defaults to ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers())
      assertNull(queueConfiguration.getMaxConsumers());
      assertTrue(queueConfiguration.isPurgeOnNoConsumers());
      assertEquals("addr2", queueConfiguration.getAddress().toString());
      assertNotNull(queueConfiguration.isGroupRebalancePauseDispatch());
      assertTrue(queueConfiguration.isGroupRebalancePauseDispatch());

      // Addr 3
      addressConfiguration = conf.getAddressConfigurations().get(2);
      assertEquals("addr2", addressConfiguration.getName());
      routingTypes = new HashSet<>();
      routingTypes.add(RoutingType.MULTICAST);
      routingTypes.add(RoutingType.ANYCAST);
      assertEquals(routingTypes, addressConfiguration.getRoutingTypes());
   }

   @TestTemplate
   public void testSecuritySettingPlugin() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("securitySettingPlugin.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      List<SecuritySettingPlugin> securitySettingPlugins = fc.getSecuritySettingPlugins();
      SecuritySettingPlugin securitySettingPlugin = securitySettingPlugins.get(0);
      assertInstanceOf(LegacyLDAPSecuritySettingPlugin.class, securitySettingPlugin);
      LegacyLDAPSecuritySettingPlugin legacyLDAPSecuritySettingPlugin = (LegacyLDAPSecuritySettingPlugin) securitySettingPlugin;
      assertEquals("testInitialContextFactory", legacyLDAPSecuritySettingPlugin.getInitialContextFactory());
      assertEquals("testConnectionURL", legacyLDAPSecuritySettingPlugin.getConnectionURL());
      assertEquals("testConnectionUsername", legacyLDAPSecuritySettingPlugin.getConnectionUsername());
      assertEquals("testConnectionPassword", legacyLDAPSecuritySettingPlugin.getConnectionPassword());
      assertEquals("testConnectionProtocol", legacyLDAPSecuritySettingPlugin.getConnectionProtocol());
      assertEquals("testAuthentication", legacyLDAPSecuritySettingPlugin.getAuthentication());
      assertEquals("testDestinationBase", legacyLDAPSecuritySettingPlugin.getDestinationBase());
      assertEquals("testFilter", legacyLDAPSecuritySettingPlugin.getFilter());
      assertEquals("testRoleAttribute", legacyLDAPSecuritySettingPlugin.getRoleAttribute());
      assertEquals("testAdminPermissionValue", legacyLDAPSecuritySettingPlugin.getAdminPermissionValue());
      assertEquals("testReadPermissionValue", legacyLDAPSecuritySettingPlugin.getReadPermissionValue());
      assertEquals("testWritePermissionValue", legacyLDAPSecuritySettingPlugin.getWritePermissionValue());
      assertFalse(legacyLDAPSecuritySettingPlugin.isEnableListener());
   }

   @TestTemplate
   public void testSecurityRoleMapping() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("securityRoleMappings.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      Map<String, Set<Role>> securityRoles = fc.getSecurityRoles();
      Set<Role> roles = securityRoles.get("#");

      //cn=mygroup,dc=local,dc=com = amq1
      Role testRole1 = new Role("cn=mygroup,dc=local,dc=com", false, false, false, false, true, false, false, false, false, false, false, false);

      //myrole1 = amq1 + amq2
      Role testRole2 = new Role("myrole1", false, false, false, false, true, true, false, false, false, false, false, false);

      //myrole3 = amq3 + amq4
      Role testRole3 = new Role("myrole3", false, false, true, true, false, false, false, false, false, false, false, false);

      //myrole4 = amq5 + amq!@#$%^&*() + amq6
      Role testRole4 = new Role("myrole4", true, true, false, false, false, false, false, true, true, true, false, false);

      //myrole5 = amq4 = amq3 + amq4
      Role testRole5 = new Role("myrole5", false, false, true, true, false, false, false, false, false, false, false, false);

      Role testRole6 = new Role("amq1", false, false, false, false, true, false, false, false, false, false, false, false);

      Role testRole7 = new Role("amq2", false, false, false, false, false, true, false, false, false, false, false, false);

      Role testRole8 = new Role("amq3", false, false, true, false, false, false, false, false, false, false, false, false);

      Role testRole9 = new Role("amq4", false, false, true, true, false, false, false, false, false, false, false, false);

      Role testRole10 = new Role("amq5", false, false, false, false, false, false, false, false, true, true, false, false);

      Role testRole11 = new Role("amq6", false, true, false, false, false, false, false, true, false, false, false, false);

      Role testRole12 = new Role("amq7", false, false, false, false, false, false, true, false, false, false, false, false);

      Role testRole13 = new Role("amq!@#$%^&*()", true, false, false, false, false, false, false, false, false, false, false, false);

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

   @TestTemplate
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

         final Thread webappContextThread = new Thread(() -> {
            FileConfiguration fileConfiguration = new FileConfiguration();

            try {
               FileDeploymentManager deploymentManager = new FileDeploymentManager(customConfiguration.getName());
               deploymentManager.addDeployable(fileConfiguration);
               deploymentManager.readConfiguration();
            } catch (Exception e) {
               holder.t = e;
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

   @TestTemplate
   public void testBrokerPlugin() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("brokerPlugin.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      List<ActiveMQServerBasePlugin> brokerPlugins = fc.getBrokerPlugins();
      assertEquals(2, brokerPlugins.size());
      assertInstanceOf(EmptyPlugin1.class, brokerPlugins.get(0));
      assertInstanceOf(EmptyPlugin2.class, brokerPlugins.get(1));
   }

   @TestTemplate
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
         assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalFileOpenTimeout(), journal.getFilesRepository().getJournalFileOpenTimeout());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalFileOpenTimeout(), server.getConfiguration().getJournalFileOpenTimeout());
      } finally {
         server.stop();
      }
   }

   @TestTemplate
   public void testJournalFileOpenTimeoutValue() throws Exception {
      int timeout = RandomUtil.randomPositiveInt();
      Configuration configuration = createConfiguration("shared-store-primary-hapolicy-config.xml");
      configuration.setJournalFileOpenTimeout(timeout)
                   .setJournalDirectory(getJournalDir())
                   .setPagingDirectory(getPageDir())
                   .setLargeMessagesDirectory(getLargeMessagesDir())
                   .setBindingsDirectory(getBindingsDir());
      ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
      try {
         server.start();
         JournalImpl journal = (JournalImpl) server.getStorageManager().getBindingsJournal();
         assertEquals(timeout, journal.getFilesRepository().getJournalFileOpenTimeout());
         assertEquals(timeout, server.getConfiguration().getJournalFileOpenTimeout());
      } finally {
         server.stop();
      }
   }

   @TestTemplate
   public void testMetricsPlugin() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("metricsPlugin.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      ActiveMQMetricsPlugin metricPlugin = fc.getMetricsConfiguration().getPlugin();
      assertInstanceOf(FakeMetricPlugin.class, metricPlugin);

      Map<String, String> metricPluginOptions = ((FakeMetricPlugin)metricPlugin).getOptions();
      assertEquals("value1", metricPluginOptions.get("key1"));
      assertEquals("value2", metricPluginOptions.get("key2"));
      assertEquals("value3", metricPluginOptions.get("key3"));
   }

   @TestTemplate
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
      assertTrue(metricsConfiguration.isFileDescriptors());
      assertTrue(metricsConfiguration.isProcessor());
      assertTrue(metricsConfiguration.isUptime());

      ActiveMQMetricsPlugin metricPlugin = metricsConfiguration.getPlugin();
      assertInstanceOf(FakeMetricPlugin.class, metricPlugin);

      Map<String, String> metricPluginOptions = ((FakeMetricPlugin)metricPlugin).getOptions();
      assertEquals("value1", metricPluginOptions.get("key1"));
      assertEquals("value2", metricPluginOptions.get("key2"));
      assertEquals("value3", metricPluginOptions.get("key3"));
   }

   @TestTemplate
   public void testMetricsConflict() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("metricsConflict.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      ActiveMQMetricsPlugin metricPlugin = fc.getMetricsConfiguration().getPlugin();
      assertInstanceOf(FakeMetricPlugin.class, metricPlugin);

      Map<String, String> metricPluginOptions = ((FakeMetricPlugin)metricPlugin).getOptions();
      assertEquals("value1", metricPluginOptions.get("key1"));
      assertEquals("value2", metricPluginOptions.get("key2"));
      assertEquals("value3", metricPluginOptions.get("key3"));
   }

   @TestTemplate
   public void testSetGetAttributes() throws Exception {
      doSetGetAttributesTestImpl(conf);
   }

   @TestTemplate
   public void testGetSetInterceptors() {
      doGetSetInterceptorsTestImpl(conf);
   }

   @TestTemplate
   public void testSerialize() throws Exception {
      doSerializeTestImpl(conf);
   }

   @TestTemplate
   public void testSetConnectionRoutersPolicyConfiguration() throws Throwable {
      doSetConnectionRoutersPolicyConfigurationTestImpl(new FileConfiguration());
   }

   @TestTemplate()
   public void testExportImportFromProperties() throws Exception {
      File exported = new File(getTestDirfile(), "broker_config_as_properties_export.txt");

      conf.exportAsProperties(exported);

      ConfigurationImpl configuration = new FileConfiguration();
      configuration.parseFileProperties(exported);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      assertTrue(REDACTED.equals(configuration.getClusterPassword()));
      configuration.setClusterPassword("Wombat");
      validateFullConfig(configuration, true);
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

   @TestTemplate
   public void testValidateCache() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         FileConfiguration fc = new FileConfiguration();
         FileDeploymentManager deploymentManager = new FileDeploymentManager(getConfigurationName());
         deploymentManager.addDeployable(fc);
         deploymentManager.readConfiguration();
         assertTrue(loggerHandler.findText("AMQ224117"));
         assertEquals(1, loggerHandler.countText("AMQ224117"));
      }

   }

}
