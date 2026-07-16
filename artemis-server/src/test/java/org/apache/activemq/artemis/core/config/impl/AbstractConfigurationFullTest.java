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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import java.util.Map;
import java.util.Set;

import java.util.List;

import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.JaasAppConfiguration;
import org.apache.activemq.artemis.core.config.JaasAppConfigurationEntry;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.config.LockCoordinatorConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.LiveOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.config.routing.ConnectionRouterConfiguration;
import org.apache.activemq.artemis.core.server.group.impl.GroupingHandlerConfiguration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.impl.LegacyLDAPSecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.core.settings.impl.DiskFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.ResourceLimitSettings;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exhaustive integration test for broker configuration loaded from a structured
 * file format (JSON or YAML).
 *
 * Loads a hand-crafted static config covering every exportable configuration
 * section, applies it to ConfigurationImpl via parseFileProperties(), and asserts
 * every configured value.
 *
 * Subclasses provide the resource path and local file name for the format under test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractConfigurationFullTest {

   @TempDir
   static File tempDir;

   protected ConfigurationImpl configuration;

   protected abstract String getConfigResource();

   protected abstract String getConfigFileName();

   @BeforeAll
   public void loadConfiguration() throws Exception {
      configuration = new ConfigurationImpl();

      File tmpFile = new File(tempDir, getConfigFileName());
      try (InputStream is = getClass().getResourceAsStream(getConfigResource())) {
         assertNotNull(is, "Test resource " + getConfigResource() + " not found on classpath");
         Files.copy(is, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }

      configuration.parseFileProperties(tmpFile);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"),
         "Configuration apply had errors: " + configuration.getStatus());
   }

   @AfterAll
   public void restoreJaasConfiguration() {
      javax.security.auth.login.Configuration.setConfiguration(null);
   }

   @Test
   public void testRootPrimitives() {
      assertEquals("full-test-broker", configuration.getName());
      assertTrue(configuration.isPersistenceEnabled());
      assertTrue(configuration.isPersistDeliveryCountBeforeDelivery());
      assertEquals(10, configuration.getScheduledThreadPoolMaxSize());
      assertEquals(50, configuration.getThreadPoolMaxSize());
      assertFalse(configuration.isSecurityEnabled());
      assertTrue(configuration.isGracefulShutdownEnabled());
      assertEquals(30000, configuration.getGracefulShutdownTimeout());

      assertEquals(JournalType.NIO, configuration.getJournalType());
      assertTrue(configuration.isJournalSyncTransactional());
      assertFalse(configuration.isJournalSyncNonTransactional());
      assertEquals(10485760, configuration.getJournalFileSize());
      assertEquals(5, configuration.getJournalMinFiles());
      assertEquals(10, configuration.getJournalPoolFiles());
      assertEquals(15, configuration.getJournalCompactMinFiles());
      assertEquals(50, configuration.getJournalCompactPercentage());
      assertEquals(4, configuration.getJournalMaxIO_NIO());
      assertEquals(3334, configuration.getJournalBufferTimeout_NIO());
      assertEquals(307200, configuration.getJournalBufferSize_NIO());
      assertEquals(5, configuration.getJournalMaxAtticFiles());
      assertEquals(10, configuration.getJournalFileOpenTimeout());
      assertEquals(20, configuration.getJournalLockAcquisitionTimeout());
      assertTrue(configuration.isReadWholePage());
      assertFalse(configuration.isLogJournalWriteRate());
      assertTrue(configuration.isCreateBindingsDir());
      assertTrue(configuration.isCreateJournalDir());
      assertFalse(configuration.isLargeMessageSync());
      assertTrue(configuration.isJournalDatasync());

      assertEquals("test-data/bindings", configuration.getBindingsDirectory());
      assertEquals("test-data/journal", configuration.getJournalDirectory());
      assertEquals("test-data/paging", configuration.getPagingDirectory());
      assertEquals("test-data/large-messages", configuration.getLargeMessagesDirectory());
      assertEquals("test-data/lock", configuration.getNodeManagerLockDirectory());

      assertEquals(5000, configuration.getSecurityInvalidationInterval());
      assertEquals(500, configuration.getAuthenticationCacheSize());
      assertEquals(500, configuration.getAuthorizationCacheSize());
      assertEquals(60000, configuration.getConnectionTTLOverride());
      assertEquals(1000, configuration.getConnectionTtlCheckInterval());

      assertEquals(100 * 1024 * 1024, configuration.getGlobalMaxSize());
      assertEquals(5000, configuration.getGlobalMaxMessages());
      assertEquals(95, configuration.getMaxDiskUsage());
      assertEquals(1048576, configuration.getMinDiskFree());
      assertEquals(10000, configuration.getDiskScanPeriod());

      assertTrue(configuration.isJMXManagementEnabled());
      assertEquals("org.apache.activemq.test", configuration.getJMXDomain());
      assertTrue(configuration.isJMXUseBrokerName());
      assertEquals(SimpleString.of("activemq.management.test"), configuration.getManagementAddress());
      assertEquals(SimpleString.of("activemq.notifications.test"), configuration.getManagementNotificationAddress());
      assertFalse(configuration.isManagementMessageRbac());
      assertEquals("mops.rbac", configuration.getManagementRbacPrefix());
      assertEquals("^(get|is|count|list|browse|query).*$", configuration.getViewPermissionMethodMatchPattern());

      assertEquals("test-cluster-user", configuration.getClusterUser());
      assertEquals("test-cluster-password", configuration.getClusterPassword());

      assertTrue(configuration.isMessageCounterEnabled());
      assertEquals(5000, configuration.getMessageCounterSamplePeriod());
      assertEquals(5, configuration.getMessageCounterMaxDayHistory());
      assertEquals(15000, configuration.getMessageExpiryScanPeriod());
      assertEquals(5, configuration.getMessageExpiryThreadPriority());
      assertEquals(25000, configuration.getAddressQueueScanPeriod());
      assertEquals(120000, configuration.getTransactionTimeout());
      assertEquals(5000, configuration.getTransactionTimeoutScanPeriod());

      assertTrue(configuration.isCriticalAnalyzer());
      assertEquals(CriticalAnalyzerPolicy.SHUTDOWN, configuration.getCriticalAnalyzerPolicy());
      assertEquals(120000, configuration.getCriticalAnalyzerTimeout());
      assertEquals(30000, configuration.getCriticalAnalyzerCheckPeriod());

      assertEquals(7, configuration.getMirrorAckManagerQueueAttempts());
      assertEquals(3, configuration.getMirrorAckManagerPageAttempts());
      assertEquals(200, configuration.getMirrorAckManagerRetryDelay());
      assertTrue(configuration.isMirrorPageTransaction());
      assertTrue(configuration.isMirrorAckManagerWarnUnacked());

      assertEquals(2000, configuration.getMqttSessionScanInterval());
      assertTrue(configuration.isMqttSubscriptionPersistenceEnabled());

      assertEquals(20, configuration.getMaxRedeliveryRecords());
      assertEquals(500, configuration.getIDCacheSize());
      assertTrue(configuration.isPersistIDCache());
      assertEquals(30000, configuration.getServerDumpInterval());
      assertEquals(70, configuration.getMemoryWarningThreshold());
      assertEquals(5000, configuration.getMemoryMeasureInterval());
      assertTrue(configuration.isAmqpUseCoreSubscriptionNaming());
      assertTrue(configuration.isSuppressSessionNotifications());
      assertEquals(3334, configuration.getPageSyncTimeout());
      assertEquals(10, configuration.getPageMaxConcurrentIO());
      assertEquals("`", configuration.getLiteralMatchMarkers());
      assertEquals(3000, configuration.getConfigurationFileRefreshPeriod());
      assertTrue(configuration.isPurgePageFolders());
      assertEquals(1000, configuration.getFileDeployerScanPeriod());
      assertTrue(configuration.isPopulateValidatedUser());
      assertTrue(configuration.isRejectEmptyValidatedUser());
   }

   @Test
   public void testConnectorConfigurations() {
      assertEquals(2, configuration.getConnectorConfigurations().size());

      TransportConfiguration nettyConnector = configuration.getConnectorConfigurations().get("netty-connector");
      assertNotNull(nettyConnector);
      assertEquals("org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory", nettyConnector.getFactoryClassName());
      assertEquals("localhost", nettyConnector.getParams().get("HOST"));
      assertEquals("61617", nettyConnector.getParams().get("PORT"));

      TransportConfiguration invmConnector = configuration.getConnectorConfigurations().get("invm-connector");
      assertNotNull(invmConnector);
      assertEquals("org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory", invmConnector.getFactoryClassName());
      assertEquals("0", invmConnector.getParams().get("ID"));
   }

   @Test
   public void testAcceptorConfigurations() {
      assertEquals(2, configuration.getAcceptorConfigurations().size());

      TransportConfiguration nettyAcceptor = configuration.getAcceptorConfigurations().stream()
         .filter(tc -> "netty-acceptor".equals(tc.getName())).findFirst().orElse(null);
      assertNotNull(nettyAcceptor);
      assertEquals("org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory", nettyAcceptor.getFactoryClassName());
      assertEquals("0.0.0.0", nettyAcceptor.getParams().get("HOST"));
      assertEquals("61616", nettyAcceptor.getParams().get("PORT"));
      assertEquals("false", nettyAcceptor.getExtraParams().get("supportAdvisory"));

      TransportConfiguration invmAcceptor = configuration.getAcceptorConfigurations().stream()
         .filter(tc -> "invm-acceptor".equals(tc.getName())).findFirst().orElse(null);
      assertNotNull(invmAcceptor);
      assertEquals("org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory", invmAcceptor.getFactoryClassName());
      assertEquals("0", invmAcceptor.getParams().get("ID"));
   }

   @Test
   public void testAddressConfigurations() {
      assertEquals(2, configuration.getAddressConfigurations().size());

      CoreAddressConfiguration ordersAddr = configuration.getAddressConfigurations().stream()
         .filter(a -> "orders".equals(a.getName())).findFirst().orElse(null);
      assertNotNull(ordersAddr);
      assertEquals(1, ordersAddr.getQueueConfigs().size());
      assertEquals(SimpleString.of("orders"), ordersAddr.getQueueConfigs().get(0).getName());
      assertEquals(RoutingType.ANYCAST, ordersAddr.getQueueConfigs().get(0).getRoutingType());
      assertTrue(ordersAddr.getQueueConfigs().get(0).isDurable());

      CoreAddressConfiguration notifAddr = configuration.getAddressConfigurations().stream()
         .filter(a -> "notifications".equals(a.getName())).findFirst().orElse(null);
      assertNotNull(notifAddr);
      assertEquals(1, notifAddr.getQueueConfigs().size());
      assertEquals(SimpleString.of("notifications"), notifAddr.getQueueConfigs().get(0).getName());
      assertEquals(RoutingType.MULTICAST, notifAddr.getQueueConfigs().get(0).getRoutingType());
      assertFalse(notifAddr.getQueueConfigs().get(0).isDurable());
   }

   @Test
   public void testSecurityRoles() {
      Map<String, Set<Role>> roles = configuration.getSecurityRoles();
      assertEquals(2, roles.size());

      Set<Role> ordersRoles = roles.get("orders.#");
      assertNotNull(ordersRoles, "Expected security roles for wildcard 'orders.#'");
      assertEquals(2, ordersRoles.size());

      Role producers = ordersRoles.stream().filter(r -> "producers".equals(r.getName())).findFirst().orElse(null);
      assertNotNull(producers);
      assertTrue(producers.isSend());
      assertFalse(producers.isConsume());
      assertFalse(producers.isCreateDurableQueue());
      assertFalse(producers.isManage());

      Role consumers = ordersRoles.stream().filter(r -> "consumers".equals(r.getName())).findFirst().orElse(null);
      assertNotNull(consumers);
      assertFalse(consumers.isSend());
      assertTrue(consumers.isConsume());
      assertTrue(consumers.isCreateNonDurableQueue());
      assertTrue(consumers.isDeleteNonDurableQueue());
      assertTrue(consumers.isBrowse());

      Set<Role> notifRoles = roles.get("notifications.*");
      assertNotNull(notifRoles, "Expected security roles for wildcard 'notifications.*'");
      assertEquals(1, notifRoles.size());
      Role monitors = notifRoles.iterator().next();
      assertEquals("monitors", monitors.getName());
      assertTrue(monitors.isConsume());
      assertTrue(monitors.isBrowse());
      assertFalse(monitors.isSend());
   }

   @Test
   public void testBridgeConfigurations() {
      assertEquals(1, configuration.getBridgeConfigurations().size());
      BridgeConfiguration bridge = configuration.getBridgeConfigurations().get(0);
      assertEquals("orders-bridge", bridge.getName());
      assertEquals("orders", bridge.getQueueName());
      assertEquals("orders.remote", bridge.getForwardingAddress());
      assertEquals(1, bridge.getStaticConnectors().size());
      assertEquals("netty-connector", bridge.getStaticConnectors().get(0));
      assertEquals(10, bridge.getConfirmationWindowSize());
      assertEquals(ComponentConfigurationRoutingType.STRIP, bridge.getRoutingType());
      assertEquals(500, bridge.getRetryInterval());
      assertEquals(2.0, bridge.getRetryIntervalMultiplier(), 0.01);
      assertEquals(3, bridge.getReconnectAttempts());
      assertTrue(bridge.isUseDuplicateDetection());
      assertNotNull(bridge.getTransformerConfiguration());
      assertEquals("org.apache.activemq.artemis.core.server.transformer.AddHeadersTransformer",
         bridge.getTransformerConfiguration().getClassName());
      assertEquals("value1", bridge.getTransformerConfiguration().getProperties().get("header1"));
      assertEquals("value2", bridge.getTransformerConfiguration().getProperties().get("header2"));
   }

   @Test
   public void testClusterConfigurations() {
      assertEquals(1, configuration.getClusterConfigurations().size());
      ClusterConnectionConfiguration cluster = configuration.getClusterConfigurations().get(0);
      assertEquals("my-cluster", cluster.getName());
      assertEquals("netty-connector", cluster.getConnectorName());
      assertEquals(1, cluster.getStaticConnectors().size());
      assertEquals("netty-connector", cluster.getStaticConnectors().get(0));
      assertEquals(MessageLoadBalancingType.ON_DEMAND, cluster.getMessageLoadBalancingType());
      assertEquals(2, cluster.getMaxHops());
      assertEquals(1000, cluster.getRetryInterval());
      assertEquals(30000, cluster.getCallTimeout());
      assertEquals(5000, cluster.getCallFailoverTimeout());
      assertTrue(cluster.isDuplicateDetection());
      assertEquals(1048576, cluster.getConfirmationWindowSize());
   }

   @Test
   public void testDivertConfigurations() {
      assertEquals(1, configuration.getDivertConfigurations().size());
      DivertConfiguration divert = configuration.getDivertConfigurations().get(0);
      assertEquals("my-divert", divert.getName());
      assertEquals("my-divert-routing", divert.getRoutingName());
      assertEquals("orders", divert.getAddress());
      assertEquals("orders.archive", divert.getForwardingAddress());
      assertFalse(divert.isExclusive());
      assertNotNull(divert.getTransformerConfiguration());
      assertEquals("org.apache.activemq.artemis.core.server.transformer.AddHeadersTransformer",
         divert.getTransformerConfiguration().getClassName());
      assertEquals("divert", divert.getTransformerConfiguration().getProperties().get("archivedBy"));
   }

   @Test
   public void testAddressSettings() {
      Map<String, AddressSettings> settings = configuration.getAddressSettings();
      assertTrue(settings.size() >= 2);

      AddressSettings global = settings.get("#");
      assertNotNull(global);
      assertEquals(SimpleString.of("globalExpiry"), global.getExpiryAddress());
      assertEquals(SimpleString.of("globalDLQ"), global.getDeadLetterAddress());
      assertEquals(5, global.getMaxDeliveryAttempts());

      AddressSettings orders = settings.get("orders");
      assertNotNull(orders);
      assertEquals(SimpleString.of("orders.expiry"), orders.getExpiryAddress());
      assertEquals(SimpleString.of("orders.dlq"), orders.getDeadLetterAddress());
      assertEquals(10, orders.getMaxDeliveryAttempts());
      assertEquals(2000, orders.getRedeliveryDelay());
      assertEquals(2.5, orders.getRedeliveryMultiplier(), 0.01);
      assertEquals(0.25, orders.getRedeliveryCollisionAvoidanceFactor(), 0.01);
      assertEquals(30000, orders.getMaxRedeliveryDelay());
      assertEquals(10485760, orders.getMaxSizeBytes());
      assertEquals(50000, orders.getMaxSizeMessages());
      assertEquals(20971520, orders.getMaxSizeBytesRejectThreshold());
      assertEquals(65536, orders.getPageSizeBytes());
      assertEquals(10, orders.getPageCacheMaxSize());
      assertEquals(1048576, orders.getMaxReadPageBytes());
      assertEquals(500, orders.getMaxReadPageMessages());
      assertEquals(524288, orders.getPrefetchPageBytes());
      assertEquals(250, orders.getPrefetchPageMessages());
      assertEquals(Long.valueOf(104857600), orders.getPageLimitBytes());
      assertEquals(Long.valueOf(100000), orders.getPageLimitMessages());
      assertEquals(AddressFullMessagePolicy.PAGE, orders.getAddressFullMessagePolicy());
      assertEquals(DiskFullMessagePolicy.BLOCK, orders.getDiskFullMessagePolicy());
      assertEquals(50, orders.getSlowConsumerThreshold());
      assertEquals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_HOUR, orders.getSlowConsumerThresholdMeasurementUnit());
      assertEquals(30, orders.getSlowConsumerCheckPeriod());
      assertEquals(SlowConsumerPolicy.NOTIFY, orders.getSlowConsumerPolicy());
      assertEquals(10, orders.getMessageCounterHistoryDayLimit());
      assertEquals(5000, orders.getRedistributionDelay());
      assertTrue(orders.isSendToDLAOnNoRoute());
      assertTrue(orders.isAutoCreateExpiryResources());
      assertEquals(SimpleString.of("EXP."), orders.getExpiryQueuePrefix());
      assertEquals(SimpleString.of(".EXPIRED"), orders.getExpiryQueueSuffix());
      assertEquals(Long.valueOf(60000), orders.getExpiryDelay());
      assertEquals(Long.valueOf(10000), orders.getMinExpiryDelay());
      assertEquals(Long.valueOf(120000), orders.getMaxExpiryDelay());
      assertFalse(orders.isNoExpiry());
      assertTrue(orders.isAutoCreateDeadLetterResources());
      assertEquals(SimpleString.of("DLQ."), orders.getDeadLetterQueuePrefix());
      assertEquals(SimpleString.of(".DLQ"), orders.getDeadLetterQueueSuffix());
      assertEquals(200, orders.getManagementBrowsePageSize());
      assertEquals(1024, orders.getManagementMessageAttributeSizeLimit());
      assertEquals(1000, orders.getQueuePrefetch());
      assertEquals(2048, orders.getDefaultConsumerWindowSize());
      assertEquals(Integer.valueOf(25), orders.getDefaultMaxConsumers());
      assertEquals(Integer.valueOf(1), orders.getDefaultConsumersBeforeDispatch());
      assertEquals(Long.valueOf(100), orders.getDefaultDelayBeforeDispatch());
      assertFalse(orders.isDefaultPurgeOnNoConsumers());
      assertEquals(RoutingType.ANYCAST, orders.getDefaultQueueRoutingType());
      assertEquals(RoutingType.ANYCAST, orders.getDefaultAddressRoutingType());
      assertFalse(orders.isDefaultLastValueQueue());
      assertEquals(SimpleString.of("myLastValueKey"), orders.getDefaultLastValueKey());
      assertFalse(orders.isDefaultNonDestructive());
      assertFalse(orders.isDefaultExclusiveQueue());
      assertTrue(orders.isDefaultGroupRebalance());
      assertFalse(orders.isDefaultGroupRebalancePauseDispatch());
      assertEquals(64, orders.getDefaultGroupBuckets());
      assertEquals(SimpleString.of("myGroupFirstKey"), orders.getDefaultGroupFirstKey());
      assertEquals(1000, orders.getDefaultRingSize());
      assertEquals(100, orders.getRetroactiveMessageCount());
      assertTrue(orders.isAutoCreateQueues());
      assertTrue(orders.isAutoDeleteQueues());
      assertFalse(orders.isAutoDeleteCreatedQueues());
      assertEquals(30000, orders.getAutoDeleteQueuesDelay());
      assertEquals(0, orders.getAutoDeleteQueuesMessageCount());
      assertFalse(orders.getAutoDeleteQueuesSkipUsageCheck());
      assertTrue(orders.isAutoCreateAddresses());
      assertTrue(orders.isAutoDeleteAddresses());
      assertEquals(30000, orders.getAutoDeleteAddressesDelay());
      assertFalse(orders.isAutoDeleteAddressesSkipUsageCheck());
      assertEquals(DeletionPolicy.OFF, orders.getConfigDeleteQueues());
      assertEquals(DeletionPolicy.OFF, orders.getConfigDeleteAddresses());
      assertEquals(DeletionPolicy.OFF, orders.getConfigDeleteDiverts());
      assertTrue(orders.isEnableMetrics());
      assertTrue(orders.isEnableIngressTimestamp());
      assertEquals(Integer.valueOf(100), orders.getIDCacheSize());
   }

   @Test
   public void testAMQPConnections() {
      assertEquals(1, configuration.getAMQPConnections().size());
      AMQPBrokerConnectConfiguration amqp = configuration.getAMQPConnections().get(0);
      assertEquals("mirror-target", amqp.getName());
      assertEquals("tcp://mirror-host:5672", amqp.getUri());
      assertEquals(5000, amqp.getRetryInterval());
      assertEquals(-1, amqp.getReconnectAttempts());
      assertEquals("mirror-user", amqp.getUser());
      assertEquals("mirror-password", amqp.getPassword());
      assertFalse(amqp.isAutostart());

      assertEquals(1, amqp.getConnectionElements().size());
      assertTrue(amqp.getConnectionElements().get(0) instanceof AMQPMirrorBrokerConnectionElement);
      AMQPMirrorBrokerConnectionElement mirror = (AMQPMirrorBrokerConnectionElement) amqp.getConnectionElements().get(0);
      assertEquals("mirror1", mirror.getName());
      assertTrue(mirror.isMessageAcknowledgements());
      assertTrue(mirror.isQueueCreation());
      assertFalse(mirror.isQueueRemoval());
      assertEquals("orders", mirror.getAddressFilter());
      assertTrue(mirror.isSync());
      assertEquals("mirrorVal1", mirror.getProperties().get("mirrorProp1"));
   }

   @Test
   public void testHAPolicyConfiguration() {
      HAPolicyConfiguration haPolicy = configuration.getHAPolicyConfiguration();
      assertNotNull(haPolicy);
      assertTrue(haPolicy instanceof LiveOnlyPolicyConfiguration);
   }

   @Test
   public void testResourceLimitSettings() {
      Map<String, ResourceLimitSettings> limits = configuration.getResourceLimitSettings();
      assertEquals(1, limits.size());
      ResourceLimitSettings user1 = limits.get("user1");
      assertNotNull(user1);
      assertEquals(100, user1.getMaxConnections());
      assertEquals(50, user1.getMaxQueues());
   }

   @Test
   public void testWildcardConfiguration() {
      WildcardConfiguration wc = configuration.getWildcardConfiguration();
      assertNotNull(wc);
      assertTrue(wc.isRoutingEnabled());
      assertEquals('#', wc.getAnyWords());
      assertEquals('*', wc.getSingleWord());
      assertEquals('.', wc.getDelimiter());
   }

   @Test
   public void testMetricsConfiguration() {
      MetricsConfiguration mc = configuration.getMetricsConfiguration();
      assertNotNull(mc);
      assertTrue(mc.isJvmMemory());
      assertTrue(mc.isJvmGc());
      assertTrue(mc.isJvmThread());
      assertTrue(mc.isNettyPool());
   }

   @Test
   public void testFederationDownstreamAuthorization() {
      List<String> auth = configuration.getFederationDownstreamAuthorization();
      assertNotNull(auth);
      assertEquals(3, auth.size());
      assertTrue(auth.contains("admin"));
      assertTrue(auth.contains("operator"));
      assertTrue(auth.contains("monitor"));
   }

   @Test
   public void testJaasConfigs() {
      Map<String, JaasAppConfiguration> jaas = configuration.getJaasConfigs();
      assertEquals(1, jaas.size());
      JaasAppConfiguration artemisRealm = jaas.get("artemis");
      assertNotNull(artemisRealm);
      assertEquals(2, artemisRealm.getModules().size());

      JaasAppConfigurationEntry propsModule = artemisRealm.getModules().stream()
         .filter(m -> "properties-module".equals(m.getName())).findFirst().orElse(null);
      assertNotNull(propsModule);
      assertEquals("org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule", propsModule.getLoginModuleClass());
      assertEquals("required", propsModule.getControlFlag());
      assertEquals("artemis-users.properties", propsModule.getParams().get("user"));
      assertEquals("artemis-roles.properties", propsModule.getParams().get("role"));

      JaasAppConfigurationEntry guestModule = artemisRealm.getModules().stream()
         .filter(m -> "guest-module".equals(m.getName())).findFirst().orElse(null);
      assertNotNull(guestModule);
      assertEquals("org.apache.activemq.artemis.spi.core.security.jaas.GuestLoginModule", guestModule.getLoginModuleClass());
      assertEquals("optional", guestModule.getControlFlag());
      assertEquals("guest", guestModule.getParams().get("org.apache.activemq.jaas.guest.user"));
      assertEquals("guest", guestModule.getParams().get("org.apache.activemq.jaas.guest.role"));
   }

   @Test
   public void testConnectionRouters() {
      assertEquals(1, configuration.getConnectionRouters().size());
      ConnectionRouterConfiguration router = configuration.getConnectionRouters().get(0);
      assertEquals("my-router", router.getName());
      assertEquals(KeyType.SOURCE_IP, router.getKeyType());
      assertEquals("netty-acceptor|invm-acceptor", router.getLocalTargetFilter());
      assertEquals("STARTS_WITH", router.getKeyFilter());
   }

   @Test
   @SuppressWarnings("deprecation")
   public void testBrokerPlugins() {
      assertFalse(configuration.getBrokerPlugins().isEmpty());
      assertTrue(configuration.getBrokerPlugins().get(0) instanceof LoggingActiveMQServerPlugin);
      LoggingActiveMQServerPlugin plugin = (LoggingActiveMQServerPlugin) configuration.getBrokerPlugins().get(0);
      assertTrue(plugin.isLogAll());
      assertFalse(plugin.isLogSessionEvents());
   }

   @Test
   public void testSecuritySettingPlugins() {
      assertEquals(1, configuration.getSecuritySettingPlugins().size());
      assertTrue(configuration.getSecuritySettingPlugins().get(0) instanceof LegacyLDAPSecuritySettingPlugin);
      LegacyLDAPSecuritySettingPlugin ldapPlugin =
         (LegacyLDAPSecuritySettingPlugin) configuration.getSecuritySettingPlugins().get(0);
      assertEquals("com.sun.jndi.ldap.LdapCtxFactory", ldapPlugin.getInitialContextFactory());
      assertEquals("ldap://localhost:1024", ldapPlugin.getConnectionURL());
   }

   @Test
   public void testFederationConfigurations() {
      assertEquals(1, configuration.getFederationConfigurations().size());
      FederationConfiguration fed = configuration.getFederationConfigurations().get(0);
      assertEquals("f1", fed.getName());
      assertEquals("federation-user", fed.getCredentials().getUser());

      assertEquals(1, fed.getUpstreamConfigurations().size());
      assertEquals("upstream1", fed.getUpstreamConfigurations().get(0).getName());
      assertEquals(5, fed.getUpstreamConfigurations().get(0).getConnectionConfiguration().getReconnectAttempts());
      assertEquals(1, fed.getUpstreamConfigurations().get(0).getPolicyRefs().size());
      assertTrue(fed.getUpstreamConfigurations().get(0).getPolicyRefs().contains("qp1"));

      assertNotNull(fed.getFederationPolicyMap().get("qp1"));
      assertTrue(fed.getFederationPolicyMap().get("qp1") instanceof FederationQueuePolicyConfiguration);
      FederationQueuePolicyConfiguration qp1 = (FederationQueuePolicyConfiguration) fed.getFederationPolicyMap().get("qp1");
      assertEquals("myTransform", qp1.getTransformerRef());
      assertEquals(1, qp1.getIncludes().size());

      assertNotNull(fed.getFederationPolicyMap().get("ap1"));
      assertTrue(fed.getFederationPolicyMap().get("ap1") instanceof FederationAddressPolicyConfiguration);
      FederationAddressPolicyConfiguration ap1 = (FederationAddressPolicyConfiguration) fed.getFederationPolicyMap().get("ap1");
      assertEquals("myTransform", ap1.getTransformerRef());
      assertEquals(1, ap1.getExcludes().size());

      assertNotNull(fed.getTransformerConfigurations().get("myTransform"));
      assertEquals("com.example.MyTransformer",
         fed.getTransformerConfigurations().get("myTransform").getTransformerConfiguration().getClassName());
      assertEquals("value1",
         fed.getTransformerConfigurations().get("myTransform").getTransformerConfiguration().getProperties().get("key1"));
   }

   @Test
   public void testLockCoordinatorConfigurations() {
      assertEquals(1, configuration.getLockCoordinatorConfigurations().size());
      LockCoordinatorConfiguration lock = configuration.getLockCoordinatorConfigurations().iterator().next();
      assertEquals("myZK", lock.getName());
      assertEquals("org.apache.activemq.artemis.lockmanager.zookeeper.CuratorDistributedLockManager", lock.getClassName());
      assertEquals("broker-01", lock.getLockId());
      assertEquals(5000, lock.getCheckPeriod());
      assertEquals("zk1:2181,zk2:2181,zk3:2181", lock.getProperties().get("connect-string"));
      assertEquals("18000", lock.getProperties().get("session-ms"));
      assertEquals("/artemis/ha", lock.getProperties().get("namespace"));
   }

   @Test
   public void testGroupingHandlerConfiguration() {
      GroupingHandlerConfiguration ghc = configuration.getGroupingHandlerConfiguration();
      assertNotNull(ghc);
      assertEquals(SimpleString.of("my-grouping-handler"), ghc.getName());
      assertEquals(GroupingHandlerConfiguration.TYPE.LOCAL, ghc.getType());
      assertEquals(SimpleString.of("jms.queue.grouping"), ghc.getAddress());
      assertEquals(5000, ghc.getTimeout());
      assertEquals(10000, ghc.getGroupTimeout());
      assertEquals(30000, ghc.getReaperPeriod());
   }

   @Test
   public void testInterceptorClassNames() {
      List<String> incoming = configuration.getIncomingInterceptorClassNames();
      assertEquals(2, incoming.size());
      assertEquals("com.example.IncomingInterceptor1", incoming.get(0));
      assertEquals("com.example.IncomingInterceptor2", incoming.get(1));

      List<String> outgoing = configuration.getOutgoingInterceptorClassNames();
      assertEquals(1, outgoing.size());
      assertEquals("com.example.OutgoingInterceptor1", outgoing.get(0));
   }

   @Test
   public void testRoundTripExport() throws Exception {
      File exportFile = new File(tempDir, "broker-export.properties");
      configuration.exportAsProperties(exportFile);
      assertTrue(exportFile.exists());
      assertTrue(exportFile.length() > 0);

      ConfigurationImpl reloaded = new ConfigurationImpl();
      reloaded.parseFileProperties(exportFile);
      String status = reloaded.getStatus();
      if (!status.contains("\"errors\":[]")) {
         assertTrue(status.contains("securitySettingPlugins") || status.contains("SecuritySettingPlugin"),
            "Re-import had unexpected errors (not just securitySettingPlugins): " + status);
      }

      assertEquals(configuration.getName(), reloaded.getName());
      assertEquals(configuration.isPersistenceEnabled(), reloaded.isPersistenceEnabled());
      assertEquals(configuration.getScheduledThreadPoolMaxSize(), reloaded.getScheduledThreadPoolMaxSize());
      assertEquals(configuration.getThreadPoolMaxSize(), reloaded.getThreadPoolMaxSize());
      assertEquals(configuration.isSecurityEnabled(), reloaded.isSecurityEnabled());
      assertEquals(configuration.getJournalType(), reloaded.getJournalType());
      assertEquals(configuration.getJournalFileSize(), reloaded.getJournalFileSize());
      assertEquals(configuration.getJournalMinFiles(), reloaded.getJournalMinFiles());
      assertEquals(configuration.getGlobalMaxSize(), reloaded.getGlobalMaxSize());
      assertEquals(configuration.getCriticalAnalyzerPolicy(), reloaded.getCriticalAnalyzerPolicy());
      assertEquals(configuration.getCriticalAnalyzerTimeout(), reloaded.getCriticalAnalyzerTimeout());
      assertEquals(configuration.getIDCacheSize(), reloaded.getIDCacheSize());
      assertEquals(configuration.getMaxRedeliveryRecords(), reloaded.getMaxRedeliveryRecords());

      assertEquals(configuration.getConnectorConfigurations().size(), reloaded.getConnectorConfigurations().size());
      assertEquals(configuration.getAcceptorConfigurations().size(), reloaded.getAcceptorConfigurations().size());
      assertEquals(configuration.getAddressConfigurations().size(), reloaded.getAddressConfigurations().size());
      assertEquals(configuration.getSecurityRoles().size(), reloaded.getSecurityRoles().size());
      assertEquals(configuration.getBridgeConfigurations().size(), reloaded.getBridgeConfigurations().size());
      assertEquals(configuration.getBridgeConfigurations().get(0).getName(),
         reloaded.getBridgeConfigurations().get(0).getName());
      assertEquals(configuration.getClusterConfigurations().size(), reloaded.getClusterConfigurations().size());
      assertEquals(configuration.getDivertConfigurations().size(), reloaded.getDivertConfigurations().size());
      assertEquals(configuration.getAddressSettings().size(), reloaded.getAddressSettings().size());
      assertEquals(configuration.getAddressSettings().get("orders").getMaxDeliveryAttempts(),
         reloaded.getAddressSettings().get("orders").getMaxDeliveryAttempts());
      assertEquals(configuration.getAMQPConnections().size(), reloaded.getAMQPConnections().size());
      assertNotNull(reloaded.getHAPolicyConfiguration());
      assertEquals(configuration.getHAPolicyConfiguration().getType(), reloaded.getHAPolicyConfiguration().getType());
      assertEquals(configuration.getResourceLimitSettings().size(), reloaded.getResourceLimitSettings().size());
      assertEquals(configuration.getJaasConfigs().size(), reloaded.getJaasConfigs().size());
      assertEquals(configuration.getFederationDownstreamAuthorization(), reloaded.getFederationDownstreamAuthorization());
   }
}
