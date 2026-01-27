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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.beans.PropertyDescriptor;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.ArtemisConstants;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ConfigurationUtils;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationPolicySet;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ColocatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.LiveOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.deployers.impl.FileConfigurationParser;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.LegacyLDAPSecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.plugin.impl.ConnectionPeriodicExpiryPlugin;
import org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.core.server.transformer.AddHeadersTransformer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.core.settings.impl.DiskFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.ResourceLimitSettings;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit;
import org.apache.activemq.artemis.jdbc.store.drivers.JDBCDataSourceUtils;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.apache.activemq.artemis.utils.uri.FluentPropertyBeanIntrospectorWithIgnores;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationImplTest extends AbstractConfigurationTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected Configuration createConfiguration() throws Exception {
      return new ConfigurationImpl();
   }

   @Test
   public void testDefaults() {
      assertEquals(ActiveMQDefaultConfiguration.getPurgePageFolders(), conf.isPurgePageFolders());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultScheduledThreadPoolMaxSize(), conf.getScheduledThreadPoolMaxSize());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultSecurityInvalidationInterval(), conf.getSecurityInvalidationInterval());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultSecurityEnabled(), conf.isSecurityEnabled());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultBindingsDirectory(), conf.getBindingsDirectory());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultCreateBindingsDir(), conf.isCreateBindingsDir());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalDir(), conf.getJournalDirectory());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultCreateJournalDir(), conf.isCreateJournalDir());
      assertEquals(ConfigurationImpl.DEFAULT_JOURNAL_TYPE, conf.getJournalType());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultJournalSyncTransactional(), conf.isJournalSyncTransactional());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultJournalSyncNonTransactional(), conf.isJournalSyncNonTransactional());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalFileSize(), conf.getJournalFileSize());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalMinFiles(), conf.getJournalMinFiles());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalMaxIoAio(), conf.getJournalMaxIO_AIO());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalMaxIoNio(), conf.getJournalMaxIO_NIO());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultWildcardRoutingEnabled(), conf.isWildcardRoutingEnabled());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultTransactionTimeout(), conf.getTransactionTimeout());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultMessageExpiryScanPeriod(), conf.getMessageExpiryScanPeriod()); // OK
      assertEquals(ActiveMQDefaultConfiguration.getDefaultTransactionTimeoutScanPeriod(), conf.getTransactionTimeoutScanPeriod()); // OK
      assertEquals(ActiveMQDefaultConfiguration.getDefaultManagementAddress(), conf.getManagementAddress()); // OK
      assertEquals(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress(), conf.getManagementNotificationAddress()); // OK
      assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterUser(), conf.getClusterUser()); // OK
      assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterPassword(), conf.getClusterPassword()); // OK
      assertEquals(ActiveMQDefaultConfiguration.isDefaultPersistenceEnabled(), conf.isPersistenceEnabled());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultPersistDeliveryCountBeforeDelivery(), conf.isPersistDeliveryCountBeforeDelivery());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultFileDeployerScanPeriod(), conf.getFileDeployerScanPeriod());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultThreadPoolMaxSize(), conf.getThreadPoolMaxSize());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultJmxManagementEnabled(), conf.isJMXManagementEnabled());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultConnectionTtlOverride(), conf.getConnectionTTLOverride());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultAsyncConnectionExecutionEnabled(), conf.isAsyncConnectionExecutionEnabled());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultPagingDir(), conf.getPagingDirectory());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultLargeMessagesDir(), conf.getLargeMessagesDirectory());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalCompactPercentage(), conf.getJournalCompactPercentage());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalLockAcquisitionTimeout(), conf.getJournalLockAcquisitionTimeout());
      assertEquals(ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_AIO, conf.getJournalBufferTimeout_AIO());
      assertEquals(ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_NIO, conf.getJournalBufferTimeout_NIO());
      assertEquals(ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_AIO, conf.getJournalBufferSize_AIO());
      assertEquals(ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_NIO, conf.getJournalBufferSize_NIO());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultJournalLogWriteRate(), conf.isLogJournalWriteRate());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultMessageCounterEnabled(), conf.isMessageCounterEnabled());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultMessageCounterMaxDayHistory(), conf.getMessageCounterMaxDayHistory());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultMessageCounterSamplePeriod(), conf.getMessageCounterSamplePeriod());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultIdCacheSize(), conf.getIDCacheSize());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultPersistIdCache(), conf.isPersistIDCache());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultServerDumpInterval(), conf.getServerDumpInterval());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultMemoryWarningThreshold(), conf.getMemoryWarningThreshold());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultMemoryMeasureInterval(), conf.getMemoryMeasureInterval());
      assertEquals(conf.getJournalLocation(), conf.getNodeManagerLockLocation());
      assertNull(conf.getJournalDeviceBlockSize());
      assertEquals(ActiveMQDefaultConfiguration.isDefaultReadWholePage(), conf.isReadWholePage());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalBufferTimeoutNio(), conf.getPageSyncTimeout());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultTemporaryQueueNamespace(), conf.getTemporaryQueueNamespace());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultUuidNamespace(), conf.getUuidNamespace());
   }

   @Test
   public void testNullMaskPassword() {
      ConfigurationImpl impl = new ConfigurationImpl();
      impl.setMaskPassword(null);

      assertEquals(impl.hashCode(), impl.hashCode());
   }

   @Test
   public void testSetGetAttributes() throws Exception {
      doSetGetAttributesTestImpl(conf);
   }

   @Test
   public void testGetSetInterceptors() {
      doGetSetInterceptorsTestImpl(conf);
   }

   @Test
   public void testSerialize() throws Exception {
      doSerializeTestImpl(conf);
   }

   @Test
   public void testSetConnectionRoutersPolicyConfiguration() throws Throwable {
      doSetConnectionRoutersPolicyConfigurationTestImpl(new ConfigurationImpl());
   }

   @Test
   public void testResolvePath() throws Throwable {
      // Validate that the resolve method will work even with artemis.instance doesn't exist

      String oldProperty = System.getProperty("artemis.instance");

      try {
         System.setProperty("artemis.instance", "/tmp/" + RandomUtil.randomUUIDString());
         ConfigurationImpl configuration = new ConfigurationImpl();
         configuration.setJournalDirectory("./data-journal");
         File journalLocation = configuration.getJournalLocation();
         assertFalse(journalLocation.exists(), "This path shouldn't resolve to a real folder");
         assertEquals(configuration.getJournalLocation(), configuration.getNodeManagerLockLocation());
         assertFalse(configuration.getNodeManagerLockLocation().exists());
         configuration.setNodeManagerLockDirectory("./lock-folder");
         assertNotEquals(configuration.getJournalLocation(), configuration.getNodeManagerLockLocation());
         assertFalse(configuration.getNodeManagerLockLocation().exists(), "This path shouldn't resolve to a real folder");
      } finally {
         if (oldProperty == null) {
            System.clearProperty("artemis.instance");
         } else {
            System.setProperty("artemis.instance", oldProperty);
         }
      }

   }

   @Test
   public void testAbsolutePath() throws Throwable {
      // Validate that the resolve method will work even with artemis.instance doesn't exist

      String oldProperty = System.getProperty("artemis.instance");
      String oldEtc = System.getProperty("artemis.instance.etc");

      File tempFolder = null;
      try {
         System.setProperty("artemis.instance", "/tmp/" + RandomUtil.randomUUIDString());
         tempFolder = File.createTempFile("journal-folder", "", temporaryFolder);
         tempFolder.delete();

         tempFolder = new File(tempFolder.getAbsolutePath());
         tempFolder.mkdirs();

         logger.debug("TempFolder = {}", tempFolder.getAbsolutePath());

         ConfigurationImpl configuration = new ConfigurationImpl();
         configuration.setJournalDirectory(tempFolder.getAbsolutePath());
         File journalLocation = configuration.getJournalLocation();

         assertTrue(journalLocation.exists());
         assertEquals(configuration.getJournalLocation(), configuration.getNodeManagerLockLocation());
         assertTrue(configuration.getNodeManagerLockLocation().exists());

         tempFolder = File.createTempFile("lock-folder", "", temporaryFolder);
         tempFolder.delete();

         tempFolder.getAbsolutePath();

         tempFolder = new File(tempFolder.getAbsolutePath());
         tempFolder.mkdirs();

         logger.debug("TempFolder = {}", tempFolder.getAbsolutePath());
         configuration.setNodeManagerLockDirectory(tempFolder.getAbsolutePath());
         File lockLocation = configuration.getNodeManagerLockLocation();
         assertTrue(lockLocation.exists());

      } finally {
         if (oldProperty == null) {
            System.clearProperty("artemis.instance");
            System.clearProperty("artemis.instance.etc");
         } else {
            System.setProperty("artemis.instance", oldProperty);
            System.setProperty("artemis.instance.etc", oldEtc);
         }

         if (tempFolder != null) {
            tempFolder.delete();
         }
      }

   }

   @Test
   public void testRootPrimitives() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties properties = new Properties();
      Method[] declaredMethods = Configuration.class.getDeclaredMethods();
      Map<String, Object> props = new HashMap<>();
      int nextInt = 1;
      long nextLong = 1;
      // add random entries for all root primitive bean properties
      for (Method declaredMethod : declaredMethods) {
         if (declaredMethod.getName().startsWith("set") && declaredMethod.getAnnotation(Deprecated.class) == null &&
                 declaredMethod.getParameterCount() == 1 && (ClassUtils.isPrimitiveOrWrapper(declaredMethod.getParameters()[0].getType())
                 || declaredMethod.getParameters()[0].getType().equals(String.class))) {
            String prop = declaredMethod.getName().substring(3);
            prop = Character.toLowerCase(prop.charAt(0)) +
                    (prop.length() > 1 ? prop.substring(1) : "");
            Class<?> type = declaredMethod.getParameters()[0].getType();
            if (type.equals(Boolean.class)) {
               properties.put(prop, Boolean.TRUE);
            } else if (type.equals(boolean.class)) {
               properties.put(prop, true);
            } else if (type.equals(Long.class) || type.equals(long.class)) {
               properties.put(prop, nextLong++);
            } else if (type.equals(Integer.class) || type.equals(int.class)) {
               properties.put(prop, nextInt++);
            } else if (type.equals(String.class)) {
               byte[] array = new byte[7]; // length is bounded by 7
               new Random().nextBytes(array);
               String generatedString = new String(array, StandardCharsets.UTF_8);

               properties.put(prop, generatedString);
            }
         }
      }
      properties.remove("status"); // this is not a simple symmetric property
      // now parse
      configuration.parsePrefixedProperties(properties, null);

      //then call the getter to make sure it gets set
      for (Map.Entry<Object, Object> entry : properties.entrySet()) {
         String methodName = entry.getKey().toString();
         methodName = Character.toUpperCase(methodName.charAt(0)) +
                 (methodName.length() > 1 ? methodName.substring(1) : "");
         if (entry.getValue().getClass() == Boolean.class || entry.getValue().getClass() == boolean.class) {
            methodName = "is" + methodName;
         } else {
            methodName = "get" + methodName;
         }

         Method declaredMethod = ConfigurationImpl.class.getDeclaredMethod(methodName);
         Object value = declaredMethod.invoke(configuration);
         assertEquals(value, properties.get(entry.getKey()));
      }
   }

   @Test
   public void testSetSystemProperty() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties properties = new Properties();
      properties.put(configuration.getSystemPropertyPrefix() + "fileDeployerScanPeriod", "1234");
      properties.put(configuration.getSystemPropertyPrefix() + "globalMaxSize", "4321");

      configuration.parsePrefixedProperties(properties, configuration.getSystemPropertyPrefix());

      assertEquals(1234, configuration.getFileDeployerScanPeriod());
      assertEquals(4321, configuration.getGlobalMaxSize());
   }

   @Test
   public void testAMQPConnectionsConfiguration() throws Throwable {
      testAMQPConnectionsConfiguration(true);
      testAMQPConnectionsConfiguration(false);
   }

   private void testAMQPConnectionsConfiguration(boolean sync) throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("AMQPConnections.target.uri", "tcp://localhost:61617");
      insertionOrderedProperties.put("AMQPConnections.target.retryInterval", 55);
      insertionOrderedProperties.put("AMQPConnections.target.reconnectAttempts", -2);
      insertionOrderedProperties.put("AMQPConnections.target.user", "admin");
      insertionOrderedProperties.put("AMQPConnections.target.password", "password");
      insertionOrderedProperties.put("AMQPConnections.target.autostart", "false");
      insertionOrderedProperties.put("AMQPConnections.target.mirrors.mirror.type", "MIRROR");
      insertionOrderedProperties.put("AMQPConnections.target.mirrors.mirror.messageAcknowledgements", "true");
      insertionOrderedProperties.put("AMQPConnections.target.mirrors.mirror.queueCreation", "true");
      insertionOrderedProperties.put("AMQPConnections.target.mirrors.mirror.queueRemoval", "true");
      insertionOrderedProperties.put("AMQPConnections.target.mirrors.mirror.addressFilter", "foo");
      insertionOrderedProperties.put("AMQPConnections.target.mirrors.mirror.properties.a", "b");
      if (sync) {
         insertionOrderedProperties.put("AMQPConnections.target.mirrors.mirror.sync", "true");
      } // else we just use the default that is false

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(1, configuration.getAMQPConnections().size());
      AMQPBrokerConnectConfiguration connectConfiguration = configuration.getAMQPConnections().get(0);
      assertEquals("target", connectConfiguration.getName());
      assertEquals("tcp://localhost:61617", connectConfiguration.getUri());
      assertEquals(55, connectConfiguration.getRetryInterval());
      assertEquals(-2, connectConfiguration.getReconnectAttempts());
      assertEquals("admin", connectConfiguration.getUser());
      assertEquals("password", connectConfiguration.getPassword());
      assertFalse(connectConfiguration.isAutostart());
      assertEquals(1, connectConfiguration.getConnectionElements().size());
      AMQPBrokerConnectionElement amqpBrokerConnectionElement = connectConfiguration.getConnectionElements().get(0);
      assertInstanceOf(AMQPMirrorBrokerConnectionElement.class, amqpBrokerConnectionElement);
      AMQPMirrorBrokerConnectionElement amqpMirrorBrokerConnectionElement = (AMQPMirrorBrokerConnectionElement) amqpBrokerConnectionElement;
      assertEquals("mirror", amqpMirrorBrokerConnectionElement.getName());
      assertTrue(amqpMirrorBrokerConnectionElement.isMessageAcknowledgements());
      assertTrue(amqpMirrorBrokerConnectionElement.isQueueCreation());
      assertTrue(amqpMirrorBrokerConnectionElement.isQueueRemoval());
      assertEquals(sync, ((AMQPMirrorBrokerConnectionElement) amqpBrokerConnectionElement).isSync());
      assertEquals("foo", amqpMirrorBrokerConnectionElement.getAddressFilter());
      assertFalse(amqpMirrorBrokerConnectionElement.getProperties().isEmpty());
      assertEquals("b", amqpMirrorBrokerConnectionElement.getProperties().get("a"));

      File exported = new File(getTestDirfile(), "broker_config_as_properties_export.txt");
      configuration.exportAsProperties(exported);

      ConfigurationImpl loadFromExport = new ConfigurationImpl();
      loadFromExport.parseFileProperties(exported);

      assertTrue(loadFromExport.getStatus().contains("\"errors\":[]"));
   }

   @Test
   public void testAMQPFederationLocalAddressPolicyConfiguration() throws Throwable {
      doTestAMQPFederationAddressPolicyConfiguration(true);
   }

   @Test
   public void testAMQPFederationRemoteAddressPolicyConfiguration() throws Throwable {
      doTestAMQPFederationAddressPolicyConfiguration(false);
   }

   private void doTestAMQPFederationAddressPolicyConfiguration(boolean local) throws Throwable {
      final ConfigurationImpl configuration = new ConfigurationImpl();

      final String policyType = local ? "localAddressPolicies" : "remoteAddressPolicies";

      final Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("AMQPConnections.target.uri", "tcp://localhost:61617");
      insertionOrderedProperties.put("AMQPConnections.target.retryInterval", 55);
      insertionOrderedProperties.put("AMQPConnections.target.reconnectAttempts", -2);
      insertionOrderedProperties.put("AMQPConnections.target.user", "admin");
      insertionOrderedProperties.put("AMQPConnections.target.password", "password");
      insertionOrderedProperties.put("AMQPConnections.target.autostart", "false");
      // This line is unnecessary but serves as a match to what the mirror connectionElements style
      // configuration does as a way of explicitly documenting what you are configuring here.
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc.type", "FEDERATION");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.includes.m1.addressMatch", "a");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.includes.m2.addressMatch", "b");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.includes.m3.addressMatch", "c");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.maxHops", "2");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.autoDelete", "true");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.autoDeleteMessageCount", "42");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.autoDeleteDelay", "10000");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy2.includes.m4.addressMatch", "y");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy2.excludes.m5.addressMatch", "z");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy2.enableDivertBindings", "true");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy2.properties.a", "b");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertEquals(1, configuration.getAMQPConnections().size());
      AMQPBrokerConnectConfiguration connectConfiguration = configuration.getAMQPConnections().get(0);
      assertEquals("target", connectConfiguration.getName());
      assertEquals("tcp://localhost:61617", connectConfiguration.getUri());
      assertEquals(55, connectConfiguration.getRetryInterval());
      assertEquals(-2, connectConfiguration.getReconnectAttempts());
      assertEquals("admin", connectConfiguration.getUser());
      assertEquals("password", connectConfiguration.getPassword());
      assertFalse(connectConfiguration.isAutostart());
      assertEquals(1, connectConfiguration.getFederations().size());
      AMQPBrokerConnectionElement amqpBrokerConnectionElement = connectConfiguration.getConnectionElements().get(0);
      assertInstanceOf(AMQPFederatedBrokerConnectionElement.class, amqpBrokerConnectionElement);
      AMQPFederatedBrokerConnectionElement amqpFederationBrokerConnectionElement = (AMQPFederatedBrokerConnectionElement) amqpBrokerConnectionElement;
      assertEquals("abc", amqpFederationBrokerConnectionElement.getName());

      if (local) {
         assertEquals(0, amqpFederationBrokerConnectionElement.getRemoteAddressPolicies().size());
         assertEquals(2, amqpFederationBrokerConnectionElement.getLocalAddressPolicies().size());
      } else {
         assertEquals(2, amqpFederationBrokerConnectionElement.getRemoteAddressPolicies().size());
         assertEquals(0, amqpFederationBrokerConnectionElement.getLocalAddressPolicies().size());
      }

      final Set<AMQPFederationAddressPolicyElement> addressPolicies = local ? amqpFederationBrokerConnectionElement.getLocalAddressPolicies()
                                                                            : amqpFederationBrokerConnectionElement.getRemoteAddressPolicies();

      AMQPFederationAddressPolicyElement addressPolicy1 = null;
      AMQPFederationAddressPolicyElement addressPolicy2 = null;

      for (AMQPFederationAddressPolicyElement policy : addressPolicies) {
         if (policy.getName().equals("policy1")) {
            addressPolicy1 = policy;
         } else if (policy.getName().equals("policy2")) {
            addressPolicy2 = policy;
         } else {
            throw new AssertionError("Found federation queue policy with unexpected name: " + policy.getName());
         }
      }

      assertNotNull(addressPolicy1);
      assertEquals(2, addressPolicy1.getMaxHops());
      assertEquals(42L, addressPolicy1.getAutoDeleteMessageCount().longValue());
      assertEquals(10000L, addressPolicy1.getAutoDeleteDelay().longValue());
      assertNull(addressPolicy1.isEnableDivertBindings());
      assertTrue(addressPolicy1.getProperties().isEmpty());

      addressPolicy1.getIncludes().forEach(match -> {
         if (match.getName().equals("m1")) {
            assertEquals("a", match.getAddressMatch());
         } else if (match.getName().equals("m2")) {
            assertEquals("b", match.getAddressMatch());
         } else if (match.getName().equals("m3")) {
            assertEquals("c", match.getAddressMatch());
         } else {
            throw new AssertionError("Found address match that was not expected: " + match.getName());
         }
      });

      assertNotNull(addressPolicy2);
      assertEquals(0, addressPolicy2.getMaxHops());
      assertNull(addressPolicy2.getAutoDeleteMessageCount());
      assertNull(addressPolicy2.getAutoDeleteDelay());
      assertTrue(addressPolicy2.isEnableDivertBindings());
      assertFalse(addressPolicy2.getProperties().isEmpty());
      assertEquals("b", addressPolicy2.getProperties().get("a"));

      addressPolicy2.getIncludes().forEach(match -> {
         if (match.getName().equals("m4")) {
            assertEquals("y", match.getAddressMatch());
         } else {
            throw new AssertionError("Found address match that was not expected: " + match.getName());
         }
      });

      addressPolicy2.getExcludes().forEach(match -> {
         if (match.getName().equals("m5")) {
            assertEquals("z", match.getAddressMatch());
         } else {
            throw new AssertionError("Found address match that was not expected: " + match.getName());
         }
      });

      assertEquals(0, amqpFederationBrokerConnectionElement.getLocalQueuePolicies().size());
      assertEquals(0, amqpFederationBrokerConnectionElement.getRemoteQueuePolicies().size());

      File exported = new File(getTestDirfile(), "broker_config_as_properties_export.txt");
      configuration.exportAsProperties(exported);

      ConfigurationImpl loadFromExport = new ConfigurationImpl();
      loadFromExport.parseFileProperties(exported);

      assertTrue(loadFromExport.getStatus().contains("\"errors\":[]"));
   }

   @Test
   public void testAMQPFederationLocalQueuePolicyConfiguration() throws Throwable {
      doTestAMQPFederationQueuePolicyConfiguration(true);
   }

   @Test
   public void testAMQPFederationRemoteQueuePolicyConfiguration() throws Throwable {
      doTestAMQPFederationQueuePolicyConfiguration(false);
   }

   private void doTestAMQPFederationQueuePolicyConfiguration(boolean local) throws Throwable {
      final ConfigurationImpl configuration = new ConfigurationImpl();

      final String policyType = local ? "localQueuePolicies" : "remoteQueuePolicies";

      final Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("AMQPConnections.target.uri", "tcp://localhost:61617");
      insertionOrderedProperties.put("AMQPConnections.target.retryInterval", 55);
      insertionOrderedProperties.put("AMQPConnections.target.reconnectAttempts", -2);
      insertionOrderedProperties.put("AMQPConnections.target.user", "admin");
      insertionOrderedProperties.put("AMQPConnections.target.password", "password");
      insertionOrderedProperties.put("AMQPConnections.target.autostart", "false");
      // This line is unnecessary but serves as a match to what the mirror connectionElements style
      // configuration does as a way of explicitly documenting what you are configuring here.
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc.type", "FEDERATION");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.includes.m1.addressMatch", "#");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.includes.m1.queueMatch", "b");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.includes.m2.addressMatch", "a");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy1.includes.m2.queueMatch", "c");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy2.includes.m3.queueMatch", "d");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy2.excludes.m3.addressMatch", "e");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy2.excludes.m3.queueMatch", "e");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy2.properties.p1", "value1");
      insertionOrderedProperties.put("AMQPConnections.target.federations.abc." + policyType + ".policy2.properties.p2", "value2");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(1, configuration.getAMQPConnections().size());
      AMQPBrokerConnectConfiguration connectConfiguration = configuration.getAMQPConnections().get(0);
      assertEquals("target", connectConfiguration.getName());
      assertEquals("tcp://localhost:61617", connectConfiguration.getUri());
      assertEquals(55, connectConfiguration.getRetryInterval());
      assertEquals(-2, connectConfiguration.getReconnectAttempts());
      assertEquals("admin", connectConfiguration.getUser());
      assertEquals("password", connectConfiguration.getPassword());
      assertFalse(connectConfiguration.isAutostart());
      assertEquals(1, connectConfiguration.getFederations().size());
      AMQPBrokerConnectionElement amqpBrokerConnectionElement = connectConfiguration.getConnectionElements().get(0);
      assertInstanceOf(AMQPFederatedBrokerConnectionElement.class, amqpBrokerConnectionElement);
      AMQPFederatedBrokerConnectionElement amqpFederationBrokerConnectionElement = (AMQPFederatedBrokerConnectionElement) amqpBrokerConnectionElement;
      assertEquals("abc", amqpFederationBrokerConnectionElement.getName());

      if (local) {
         assertEquals(0, amqpFederationBrokerConnectionElement.getRemoteQueuePolicies().size());
         assertEquals(2, amqpFederationBrokerConnectionElement.getLocalQueuePolicies().size());
      } else {
         assertEquals(2, amqpFederationBrokerConnectionElement.getRemoteQueuePolicies().size());
         assertEquals(0, amqpFederationBrokerConnectionElement.getLocalQueuePolicies().size());
      }

      final Set<AMQPFederationQueuePolicyElement> addressPolicies = local ? amqpFederationBrokerConnectionElement.getLocalQueuePolicies() :
                                                                            amqpFederationBrokerConnectionElement.getRemoteQueuePolicies();

      AMQPFederationQueuePolicyElement queuePolicy1 = null;
      AMQPFederationQueuePolicyElement queuePolicy2 = null;

      for (AMQPFederationQueuePolicyElement policy : addressPolicies) {
         if (policy.getName().equals("policy1")) {
            queuePolicy1 = policy;
         } else if (policy.getName().equals("policy2")) {
            queuePolicy2 = policy;
         } else {
            throw new AssertionError("Found federation queue policy with unexpected name: " + policy.getName());
         }
      }

      assertNotNull(queuePolicy1);
      assertFalse(queuePolicy1.getIncludes().isEmpty());
      assertTrue(queuePolicy1.getExcludes().isEmpty());
      assertEquals(2, queuePolicy1.getIncludes().size());
      assertEquals(0, queuePolicy1.getProperties().size());

      queuePolicy1.getIncludes().forEach(match -> {
         if (match.getName().equals("m1")) {
            assertEquals("#", match.getAddressMatch());
            assertEquals("b", match.getQueueMatch());
         } else if (match.getName().equals("m2")) {
            assertEquals("a", match.getAddressMatch());
            assertEquals("c", match.getQueueMatch());
         } else {
            throw new AssertionError("Found queue match that was not expected: " + match.getName());
         }
      });

      assertNotNull(queuePolicy2);
      assertFalse(queuePolicy2.getIncludes().isEmpty());
      assertFalse(queuePolicy2.getExcludes().isEmpty());

      queuePolicy2.getIncludes().forEach(match -> {
         if (match.getName().equals("m3")) {
            assertNull(match.getAddressMatch());
            assertEquals("d", match.getQueueMatch());
         } else {
            throw new AssertionError("Found queue match that was not expected: " + match.getName());
         }
      });

      queuePolicy2.getExcludes().forEach(match -> {
         if (match.getName().equals("m3")) {
            assertEquals("e", match.getAddressMatch());
            assertEquals("e", match.getQueueMatch());
         } else {
            throw new AssertionError("Found queue match that was not expected: " + match.getName());
         }
      });

      assertEquals(2, queuePolicy2.getProperties().size());
      assertTrue(queuePolicy2.getProperties().containsKey("p1"));
      assertTrue(queuePolicy2.getProperties().containsKey("p2"));
      assertEquals("value1", queuePolicy2.getProperties().get("p1"));
      assertEquals("value2", queuePolicy2.getProperties().get("p2"));

      assertEquals(0, amqpFederationBrokerConnectionElement.getLocalAddressPolicies().size());
      assertEquals(0, amqpFederationBrokerConnectionElement.getRemoteAddressPolicies().size());

      File exported = new File(getTestDirfile(), "broker_config_as_properties_export.txt");
      configuration.exportAsProperties(exported);

      ConfigurationImpl loadFromExport = new ConfigurationImpl();
      loadFromExport.parseFileProperties(exported);

      assertTrue(loadFromExport.getStatus().contains("\"errors\":[]"));
   }

   @Test
   public void testAMQPConnectionsConfigurationUriEnc() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("AMQPConnections.target.uri", "tcp://amq-dc1-tls-amqp-${STATEFUL_SET_ORDINAL}-svc.dc1.svc.cluster.local:5673?clientFailureCheckPeriod=30000&connectionTTL=60000&sslEnabled=true&verifyHost=false&trustStorePath=/remote-cluster-truststore/client.ts");
      insertionOrderedProperties.put("AMQPConnections.target.transportConfigurations.target.params.trustStorePassword", "ENC(2a7c211d21c295cdbcde3589c205decb)");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);
      AMQPBrokerConnectConfiguration connectConfiguration = configuration.getAMQPConnections().get(0);
      assertFalse(connectConfiguration.getTransportConfigurations().get(0).getParams().get("trustStorePassword").toString().contains("ENC"));
   }

   @Test
   public void testAMQPBridgeFromAddressPolicyConfiguration() throws Throwable {
      doTestAMQPBridgeAddressPolicyConfiguration(true);
   }

   @Test
   public void testAMQPBridgeToAddressPolicyConfiguration() throws Throwable {
      doTestAMQPBridgeAddressPolicyConfiguration(false);
   }

   public void doTestAMQPBridgeAddressPolicyConfiguration(boolean fromPolicy) throws Throwable {
      final ConfigurationImpl configuration = new ConfigurationImpl();

      final String policyType = fromPolicy ? "bridgeFromAddressPolicies" : "bridgeToAddressPolicies";

      final Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("AMQPConnections.target.uri", "localhost:61617");
      insertionOrderedProperties.put("AMQPConnections.target.retryInterval", 55);
      insertionOrderedProperties.put("AMQPConnections.target.reconnectAttempts", -2);
      insertionOrderedProperties.put("AMQPConnections.target.user", "admin");
      insertionOrderedProperties.put("AMQPConnections.target.password", "password");
      insertionOrderedProperties.put("AMQPConnections.target.autostart", "false");
      // This line is unnecessary but serves as a match to what the mirror connectionElements style
      // configuration does as a way of explicitly documenting what you are configuring here.
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc.type", "BRIDGE");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy1.priority", "7");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy1.includes.m1.addressMatch", "a");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy1.includes.m2.addressMatch", "b");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy1.includes.m3.addressMatch", "c");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy2.includes.m4.addressMatch", "y");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy2.excludes.m5.addressMatch", "z");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy2.includeDivertBindings", "true");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy2.useDurableSubscriptions", "true");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy2.properties.a", "b");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertEquals(1, configuration.getAMQPConnections().size());
      AMQPBrokerConnectConfiguration connectConfiguration = configuration.getAMQPConnections().get(0);
      assertEquals("target", connectConfiguration.getName());
      assertEquals("localhost:61617", connectConfiguration.getUri());
      assertEquals(55, connectConfiguration.getRetryInterval());
      assertEquals(-2, connectConfiguration.getReconnectAttempts());
      assertEquals("admin", connectConfiguration.getUser());
      assertEquals("password", connectConfiguration.getPassword());
      assertFalse(connectConfiguration.isAutostart());
      assertEquals(1, connectConfiguration.getBridges().size());

      AMQPBrokerConnectionElement amqpBrokerConnectionElement = connectConfiguration.getConnectionElements().get(0);
      assertTrue(amqpBrokerConnectionElement instanceof AMQPBridgeBrokerConnectionElement);
      AMQPBridgeBrokerConnectionElement amqpBridgeBrokerConnectionElement = (AMQPBridgeBrokerConnectionElement) amqpBrokerConnectionElement;
      assertEquals("abc", amqpBridgeBrokerConnectionElement.getName());

      if (fromPolicy) {
         assertEquals(0, amqpBridgeBrokerConnectionElement.getBridgeToAddressPolicies().size());
         assertEquals(2, amqpBridgeBrokerConnectionElement.getBridgeFromAddressPolicies().size());
      } else {
         assertEquals(2, amqpBridgeBrokerConnectionElement.getBridgeToAddressPolicies().size());
         assertEquals(0, amqpBridgeBrokerConnectionElement.getBridgeFromAddressPolicies().size());
      }

      final Set<AMQPBridgeAddressPolicyElement> addressPolicies = fromPolicy ? amqpBridgeBrokerConnectionElement.getBridgeFromAddressPolicies()
                                                                             : amqpBridgeBrokerConnectionElement.getBridgeToAddressPolicies();

      AMQPBridgeAddressPolicyElement addressPolicy1 = null;
      AMQPBridgeAddressPolicyElement addressPolicy2 = null;

      for (AMQPBridgeAddressPolicyElement policy : addressPolicies) {
         if (policy.getName().equals("policy1")) {
            addressPolicy1 = policy;
         } else if (policy.getName().equals("policy2")) {
            addressPolicy2 = policy;
         } else {
            throw new AssertionError("Found bridge queue policy with unexpected name: " + policy.getName());
         }
      }

      assertNotNull(addressPolicy1);
      assertNull(addressPolicy1.getFilter());
      assertEquals(7, addressPolicy1.getPriority());
      assertTrue(addressPolicy1.getProperties().isEmpty());
      assertFalse(addressPolicy1.isIncludeDivertBindings());
      assertFalse(addressPolicy1.isUseDurableSubscriptions());

      addressPolicy1.getIncludes().forEach(match -> {
         if (match.getName().equals("m1")) {
            assertEquals("a", match.getAddressMatch());
         } else if (match.getName().equals("m2")) {
            assertEquals("b", match.getAddressMatch());
         } else if (match.getName().equals("m3")) {
            assertEquals("c", match.getAddressMatch());
         } else {
            throw new AssertionError("Found address match that was not expected: " + match.getName());
         }
      });

      assertNotNull(addressPolicy2);
      assertFalse(addressPolicy2.getProperties().isEmpty());
      assertTrue(addressPolicy2.isIncludeDivertBindings());
      assertTrue(addressPolicy2.isUseDurableSubscriptions());
      assertEquals("b", addressPolicy2.getProperties().get("a"));

      addressPolicy2.getIncludes().forEach(match -> {
         if (match.getName().equals("m4")) {
            assertEquals("y", match.getAddressMatch());
         } else {
            throw new AssertionError("Found address match that was not expected: " + match.getName());
         }
      });

      addressPolicy2.getExcludes().forEach(match -> {
         if (match.getName().equals("m5")) {
            assertEquals("z", match.getAddressMatch());
         } else {
            throw new AssertionError("Found address match that was not expected: " + match.getName());
         }
      });

      assertEquals(0, amqpBridgeBrokerConnectionElement.getBridgeFromQueuePolicies().size());
      assertEquals(0, amqpBridgeBrokerConnectionElement.getBridgeToQueuePolicies().size());
   }

   @Test
   public void testAMQPBridgeFromQueuePolicyConfiguration() throws Throwable {
      doTestAMQPBridgeAddressPolicyConfiguration(true);
   }

   @Test
   public void testAMQPBridgeToQueuePolicyConfiguration() throws Throwable {
      doTestAMQPBridgeAddressPolicyConfiguration(false);
   }

   public void doTestAMQPBridgeQueuePolicyConfiguration(boolean fromPolicy) throws Throwable {
      final ConfigurationImpl configuration = new ConfigurationImpl();

      final String policyType = fromPolicy ? "bridgeFromQueuePolicies" : "bridgeToQueuePolicies";

      final Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("AMQPConnections.target.uri", "localhost:61617");
      insertionOrderedProperties.put("AMQPConnections.target.retryInterval", 55);
      insertionOrderedProperties.put("AMQPConnections.target.reconnectAttempts", -2);
      insertionOrderedProperties.put("AMQPConnections.target.user", "admin");
      insertionOrderedProperties.put("AMQPConnections.target.password", "password");
      insertionOrderedProperties.put("AMQPConnections.target.autostart", "false");
      // This line is unnecessary but serves as a match to what the mirror connectionElements style
      // configuration does as a way of explicitly documenting what you are configuring here.
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc.type", "BRIDGE");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy1.priority", "7");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy1.includes.m1.addressMatch", "a");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy1.includes.m2.addressMatch", "b");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy1.includes.m3.addressMatch", "c");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy2.includes.m4.addressMatch", "y");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy2.excludes.m5.addressMatch", "z");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy2.includeDivertBindings", "true");
      insertionOrderedProperties.put("AMQPConnections.target.bridges.abc." + policyType + ".policy2.properties.a", "b");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertEquals(1, configuration.getAMQPConnections().size());
      AMQPBrokerConnectConfiguration connectConfiguration = configuration.getAMQPConnections().get(0);
      assertEquals("target", connectConfiguration.getName());
      assertEquals("localhost:61617", connectConfiguration.getUri());
      assertEquals(55, connectConfiguration.getRetryInterval());
      assertEquals(-2, connectConfiguration.getReconnectAttempts());
      assertEquals("admin", connectConfiguration.getUser());
      assertEquals("password", connectConfiguration.getPassword());
      assertFalse(connectConfiguration.isAutostart());
      assertEquals(1, connectConfiguration.getBridges().size());

      AMQPBrokerConnectionElement amqpBrokerConnectionElement = connectConfiguration.getConnectionElements().get(0);
      assertTrue(amqpBrokerConnectionElement instanceof AMQPBridgeBrokerConnectionElement);
      AMQPBridgeBrokerConnectionElement amqpBridgeBrokerConnectionElement = (AMQPBridgeBrokerConnectionElement) amqpBrokerConnectionElement;
      assertEquals("abc", amqpBridgeBrokerConnectionElement.getName());

      if (fromPolicy) {
         assertEquals(0, amqpBridgeBrokerConnectionElement.getBridgeToAddressPolicies().size());
         assertEquals(2, amqpBridgeBrokerConnectionElement.getBridgeFromAddressPolicies().size());
      } else {
         assertEquals(2, amqpBridgeBrokerConnectionElement.getBridgeToAddressPolicies().size());
         assertEquals(0, amqpBridgeBrokerConnectionElement.getBridgeFromAddressPolicies().size());
      }

      final Set<AMQPBridgeQueuePolicyElement> addressPolicies = fromPolicy ? amqpBridgeBrokerConnectionElement.getBridgeFromQueuePolicies()
                                                                           : amqpBridgeBrokerConnectionElement.getBridgeToQueuePolicies();

      AMQPBridgeQueuePolicyElement queuePolicy1 = null;
      AMQPBridgeQueuePolicyElement queuePolicy2 = null;

      for (AMQPBridgeQueuePolicyElement policy : addressPolicies) {
         if (policy.getName().equals("policy1")) {
            queuePolicy1 = policy;
         } else if (policy.getName().equals("policy2")) {
            queuePolicy2 = policy;
         } else {
            throw new AssertionError("Found bridge queue policy with unexpected name: " + policy.getName());
         }
      }

      assertNotNull(queuePolicy1);
      assertNull(queuePolicy1.getFilter());
      assertEquals(7, queuePolicy1.getPriority());
      assertTrue(queuePolicy1.getProperties().isEmpty());

      queuePolicy1.getIncludes().forEach(match -> {
         if (match.getName().equals("m1")) {
            assertEquals("a", match.getAddressMatch());
         } else if (match.getName().equals("m2")) {
            assertEquals("b", match.getAddressMatch());
         } else if (match.getName().equals("m3")) {
            assertEquals("c", match.getAddressMatch());
         } else {
            throw new AssertionError("Found address match that was not expected: " + match.getName());
         }
      });

      assertNotNull(queuePolicy2);
      assertFalse(queuePolicy2.getProperties().isEmpty());
      assertEquals("b", queuePolicy2.getProperties().get("a"));

      queuePolicy2.getIncludes().forEach(match -> {
         if (match.getName().equals("m3")) {
            assertNull(match.getAddressMatch());
            assertEquals("d", match.getQueueMatch());
         } else {
            throw new AssertionError("Found queue match that was not expected: " + match.getName());
         }
      });

      queuePolicy2.getExcludes().forEach(match -> {
         if (match.getName().equals("m3")) {
            assertEquals("e", match.getAddressMatch());
            assertEquals("e", match.getQueueMatch());
         } else {
            throw new AssertionError("Found queue match that was not expected: " + match.getName());
         }
      });

      assertEquals(0, amqpBridgeBrokerConnectionElement.getBridgeFromQueuePolicies().size());
      assertEquals(0, amqpBridgeBrokerConnectionElement.getBridgeToQueuePolicies().size());
   }

   @Test
   public void testCoreBridgeConfiguration() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      final String queueName = "q";
      final String forwardingAddress = "fa";

      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();

      properties.put("bridgeConfigurations.b1.queueName", queueName);
      properties.put("bridgeConfigurations.b1.forwardingAddress", forwardingAddress);
      properties.put("bridgeConfigurations.b1.confirmationWindowSize", "10");
      properties.put("bridgeConfigurations.b1.routingType", "STRIP");  // enum
      // this is a List<String> from comma sep value
      properties.put("bridgeConfigurations.b1.staticConnectors", "a,b");
      // flip b in place
      properties.put("bridgeConfigurations.b1.staticConnectors[1]", "c");

      properties.put("bridgeConfigurations.b1.transformerConfiguration.className", AddHeadersTransformer.class.getName());
      properties.put("bridgeConfigurations.b1.transformerConfiguration.properties", "header1=a,header2=b");
      properties.put("bridgeConfigurations.b1.transformerConfiguration.properties.header3", "c");

      configuration.parsePrefixedProperties(properties, null);
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      assertEquals(1, configuration.getBridgeConfigurations().size());
      assertEquals(queueName, configuration.getBridgeConfigurations().get(0).getQueueName());

      assertEquals(forwardingAddress, configuration.getBridgeConfigurations().get(0).getForwardingAddress());
      assertEquals(10, configuration.getBridgeConfigurations().get(0).getConfirmationWindowSize());
      assertEquals(2, configuration.getBridgeConfigurations().get(0).getStaticConnectors().size());
      assertEquals("a", configuration.getBridgeConfigurations().get(0).getStaticConnectors().get(0));
      assertEquals("c", configuration.getBridgeConfigurations().get(0).getStaticConnectors().get(1));

      assertEquals(ComponentConfigurationRoutingType.STRIP, configuration.getBridgeConfigurations().get(0).getRoutingType());

      assertEquals(3, configuration.getBridgeConfigurations().get(0).getTransformerConfiguration().getProperties().size());
      assertEquals(AddHeadersTransformer.class.getName(), configuration.getBridgeConfigurations().get(0).getTransformerConfiguration().getClassName());

      properties = new ConfigurationImpl.InsertionOrderedProperties();
      // validate out of bound is trapped as error
      properties.put("bridgeConfigurations.b1.staticConnectors[5]", "d");
      configuration.parsePrefixedProperties(properties, null);
      assertTrue(configuration.getStatus().contains("IndexOutOfBoundsException"), configuration.getStatus());
   }

   @Test
   public void testLiveOnlyPolicyConfiguration() throws Throwable {
      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("HAPolicyConfiguration", "PRIMARY_ONLY");
      addScaleDownConfigurationProperties(properties);

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parsePrefixedProperties(properties, null);

      HAPolicyConfiguration haPolicyConfiguration = configuration.getHAPolicyConfiguration();
      assertEquals(LiveOnlyPolicyConfiguration.class, haPolicyConfiguration.getClass());

      checkScaleDownConfiguration(((LiveOnlyPolicyConfiguration)haPolicyConfiguration).getScaleDownConfiguration());
   }

   @Test
   public void testReplicatedPolicyConfiguration() throws Throwable {
      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("HAPolicyConfiguration", "REPLICATION_PRIMARY_QUORUM_VOTING");
      properties.put("HAPolicyConfiguration.checkForActiveServer", "true");
      properties.put("HAPolicyConfiguration.groupName", "g0");
      properties.put("HAPolicyConfiguration.clusterName", "c0");
      properties.put("HAPolicyConfiguration.maxSavedReplicatedJournalsSize", "3");
      properties.put("HAPolicyConfiguration.voteOnReplicationFailure", "true");
      properties.put("HAPolicyConfiguration.quorumSize", "9");
      properties.put("HAPolicyConfiguration.voteRetries", "6");
      properties.put("HAPolicyConfiguration.voteRetryWait", "1");
      properties.put("HAPolicyConfiguration.quorumVoteWait", "2");
      properties.put("HAPolicyConfiguration.retryReplicationWait", "4");

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parsePrefixedProperties(properties, null);

      HAPolicyConfiguration haPolicyConfiguration = configuration.getHAPolicyConfiguration();
      assertEquals(ReplicatedPolicyConfiguration.class, haPolicyConfiguration.getClass());

      ReplicatedPolicyConfiguration replicatedPolicyConfiguration =
         (ReplicatedPolicyConfiguration)haPolicyConfiguration;
      assertTrue(replicatedPolicyConfiguration.isCheckForActiveServer());
      assertEquals("g0", replicatedPolicyConfiguration.getGroupName());
      assertEquals("c0", replicatedPolicyConfiguration.getClusterName());
      assertEquals(3, replicatedPolicyConfiguration.getMaxSavedReplicatedJournalsSize());
      assertTrue(replicatedPolicyConfiguration.getVoteOnReplicationFailure());
      assertEquals(9, replicatedPolicyConfiguration.getQuorumSize());
      assertEquals(6, replicatedPolicyConfiguration.getVoteRetries());
      assertEquals(1, replicatedPolicyConfiguration.getVoteRetryWait());
      assertEquals(2, replicatedPolicyConfiguration.getQuorumVoteWait());
      assertEquals(Long.valueOf(4), replicatedPolicyConfiguration.getRetryReplicationWait());
   }

   @Test
   public void testReplicaPolicyConfiguration() throws Throwable {
      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("HAPolicyConfiguration", "REPLICATION_BACKUP_QUORUM_VOTING");
      properties.put("HAPolicyConfiguration.clusterName", "c0");
      properties.put("HAPolicyConfiguration.maxSavedReplicatedJournalsSize", "3");
      properties.put("HAPolicyConfiguration.groupName", "g0");
      properties.put("HAPolicyConfiguration.restartBackup", "false");
      properties.put("HAPolicyConfiguration.allowFailBack", "true");
      properties.put("HAPolicyConfiguration.initialReplicationSyncTimeout", "7");
      properties.put("HAPolicyConfiguration.voteOnReplicationFailure", "true");
      properties.put("HAPolicyConfiguration.quorumSize", "9");
      properties.put("HAPolicyConfiguration.voteRetries", "6");
      properties.put("HAPolicyConfiguration.voteRetryWait", "1");
      properties.put("HAPolicyConfiguration.quorumVoteWait", "2");
      properties.put("HAPolicyConfiguration.retryReplicationWait", "4");
      addScaleDownConfigurationProperties(properties);

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parsePrefixedProperties(properties, null);

      HAPolicyConfiguration haPolicyConfiguration = configuration.getHAPolicyConfiguration();
      assertEquals(ReplicaPolicyConfiguration.class, haPolicyConfiguration.getClass());

      ReplicaPolicyConfiguration replicaPolicyConfiguration =
         (ReplicaPolicyConfiguration)haPolicyConfiguration;
      assertEquals("c0", replicaPolicyConfiguration.getClusterName());
      assertEquals(3, replicaPolicyConfiguration.getMaxSavedReplicatedJournalsSize());
      assertEquals("g0", replicaPolicyConfiguration.getGroupName());
      assertFalse(replicaPolicyConfiguration.isRestartBackup());
      assertTrue(replicaPolicyConfiguration.isAllowFailBack());
      assertEquals(7, replicaPolicyConfiguration.getInitialReplicationSyncTimeout());
      assertTrue(replicaPolicyConfiguration.getVoteOnReplicationFailure());
      assertEquals(9, replicaPolicyConfiguration.getQuorumSize());
      assertEquals(6, replicaPolicyConfiguration.getVoteRetries());
      assertEquals(1, replicaPolicyConfiguration.getVoteRetryWait());
      assertEquals(2, replicaPolicyConfiguration.getQuorumVoteWait());
      assertEquals(4, replicaPolicyConfiguration.getRetryReplicationWait());

      checkScaleDownConfiguration(replicaPolicyConfiguration.getScaleDownConfiguration());
   }

   @Test
   public void testSharedStorePrimaryConfiguration() throws Throwable {
      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("HAPolicyConfiguration", "SHARED_STORE_PRIMARY");
      properties.put("HAPolicyConfiguration.failoverOnServerShutdown", "true");
      properties.put("HAPolicyConfiguration.waitForActivation", "false");

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parsePrefixedProperties(properties, null);

      HAPolicyConfiguration haPolicyConfiguration = configuration.getHAPolicyConfiguration();
      assertEquals(SharedStorePrimaryPolicyConfiguration.class, haPolicyConfiguration.getClass());

      SharedStorePrimaryPolicyConfiguration sharedStorePrimaryPolicyConfiguration =
         (SharedStorePrimaryPolicyConfiguration)haPolicyConfiguration;
      assertTrue(sharedStorePrimaryPolicyConfiguration.isFailoverOnServerShutdown());
      assertFalse(sharedStorePrimaryPolicyConfiguration.isWaitForActivation());
   }

   @Test
   public void testSharedStoreBackupPolicyConfiguration() throws Throwable {
      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("HAPolicyConfiguration", "SHARED_STORE_BACKUP");
      properties.put("HAPolicyConfiguration.failoverOnServerShutdown", "true");
      properties.put("HAPolicyConfiguration.restartBackup", "false");
      properties.put("HAPolicyConfiguration.allowFailBack", "false");
      addScaleDownConfigurationProperties(properties);

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parsePrefixedProperties(properties, null);

      HAPolicyConfiguration haPolicyConfiguration = configuration.getHAPolicyConfiguration();
      assertEquals(SharedStoreBackupPolicyConfiguration.class, haPolicyConfiguration.getClass());

      SharedStoreBackupPolicyConfiguration sharedStoreBackupPolicyConfiguration =
         (SharedStoreBackupPolicyConfiguration)haPolicyConfiguration;
      assertTrue(sharedStoreBackupPolicyConfiguration.isFailoverOnServerShutdown());
      assertFalse(sharedStoreBackupPolicyConfiguration.isRestartBackup());
      assertFalse(sharedStoreBackupPolicyConfiguration.isAllowFailBack());

      checkScaleDownConfiguration(sharedStoreBackupPolicyConfiguration.getScaleDownConfiguration());
   }

   @Test
   public void testColocatedPolicyConfiguration() throws Throwable {
      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("HAPolicyConfiguration", "COLOCATED");
      properties.put("HAPolicyConfiguration.requestBackup", "true");
      properties.put("HAPolicyConfiguration.backupRequestRetries", "5");
      properties.put("HAPolicyConfiguration.backupRequestRetryInterval", "3");
      properties.put("HAPolicyConfiguration.maxBackups", "9");
      properties.put("HAPolicyConfiguration.backupPortOffset", "2");
      properties.put("HAPolicyConfiguration.excludedConnectors", "a,b,c");
      properties.put("HAPolicyConfiguration.portOffset", "4");
      properties.put("HAPolicyConfiguration.primaryConfig", "SHARED_STORE_PRIMARY");
      properties.put("HAPolicyConfiguration.backupConfig", "SHARED_STORE_BACKUP");

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parsePrefixedProperties(properties, null);

      HAPolicyConfiguration haPolicyConfiguration = configuration.getHAPolicyConfiguration();
      assertEquals(ColocatedPolicyConfiguration.class, haPolicyConfiguration.getClass());

      ColocatedPolicyConfiguration colocatedPolicyConfiguration =
         (ColocatedPolicyConfiguration)haPolicyConfiguration;
      assertTrue(colocatedPolicyConfiguration.isRequestBackup());
      assertEquals(5, colocatedPolicyConfiguration.getBackupRequestRetries());
      assertEquals(3, colocatedPolicyConfiguration.getBackupRequestRetryInterval());
      assertEquals(9, colocatedPolicyConfiguration.getMaxBackups());
      assertEquals(2, colocatedPolicyConfiguration.getBackupPortOffset());
      assertEquals(3, colocatedPolicyConfiguration.getExcludedConnectors().size());
      assertEquals(4, colocatedPolicyConfiguration.getPortOffset());
      assertEquals(SharedStorePrimaryPolicyConfiguration.class, colocatedPolicyConfiguration.getPrimaryConfig().getClass());
      assertEquals(SharedStoreBackupPolicyConfiguration.class, colocatedPolicyConfiguration.getBackupConfig().getClass());
   }

   @Test
   public void testReplicationPrimaryPolicyConfiguration() throws Throwable {
      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("HAPolicyConfiguration", "REPLICATION_PRIMARY_LOCK_MANAGER");
      properties.put("HAPolicyConfiguration.groupName", "g0");
      properties.put("HAPolicyConfiguration.clusterName", "c0");
      properties.put("HAPolicyConfiguration.initialReplicationSyncTimeout", "5");
      properties.put("HAPolicyConfiguration.retryReplicationWait", "2");
      properties.put("HAPolicyConfiguration.distributedManagerConfiguration.className", "class0");
      properties.put("HAPolicyConfiguration.distributedManagerConfiguration.properties.k0", "v0");
      properties.put("HAPolicyConfiguration.coordinationId", "cid0");
      properties.put("HAPolicyConfiguration.maxSavedReplicatedJournalsSize", "3");

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parsePrefixedProperties(properties, null);

      HAPolicyConfiguration haPolicyConfiguration = configuration.getHAPolicyConfiguration();
      assertEquals(ReplicationPrimaryPolicyConfiguration.class, haPolicyConfiguration.getClass());

      ReplicationPrimaryPolicyConfiguration replicationPrimaryPolicyConfiguration =
         (ReplicationPrimaryPolicyConfiguration)haPolicyConfiguration;
      assertEquals("g0", replicationPrimaryPolicyConfiguration.getGroupName());
      assertEquals("c0", replicationPrimaryPolicyConfiguration.getClusterName());
      assertEquals(5, replicationPrimaryPolicyConfiguration.getInitialReplicationSyncTimeout());
      assertEquals(Long.valueOf(2), replicationPrimaryPolicyConfiguration.getRetryReplicationWait());
      assertEquals("class0", replicationPrimaryPolicyConfiguration.getDistributedManagerConfiguration().getClassName());
      assertEquals("v0", replicationPrimaryPolicyConfiguration.getDistributedManagerConfiguration().getProperties().get("k0"));
      assertEquals("cid0", replicationPrimaryPolicyConfiguration.getCoordinationId());
      assertEquals(3, replicationPrimaryPolicyConfiguration.getMaxSavedReplicatedJournalsSize());
   }

   @Test
   public void testReplicationBackupPolicyConfiguration() throws Throwable {
      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("HAPolicyConfiguration", "REPLICATION_BACKUP_LOCK_MANAGER");
      properties.put("HAPolicyConfiguration.clusterName", "c0");
      properties.put("HAPolicyConfiguration.maxSavedReplicatedJournalsSize", "3");
      properties.put("HAPolicyConfiguration.groupName", "g0");
      properties.put("HAPolicyConfiguration.allowFailBack", "true");
      properties.put("HAPolicyConfiguration.initialReplicationSyncTimeout", "5");
      properties.put("HAPolicyConfiguration.retryReplicationWait", "2");
      properties.put("HAPolicyConfiguration.distributedManagerConfiguration.className", "class0");
      properties.put("HAPolicyConfiguration.distributedManagerConfiguration.properties.k0", "v0");

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parsePrefixedProperties(properties, null);

      HAPolicyConfiguration haPolicyConfiguration = configuration.getHAPolicyConfiguration();
      assertEquals(ReplicationBackupPolicyConfiguration.class, haPolicyConfiguration.getClass());

      ReplicationBackupPolicyConfiguration replicationBackupPolicyConfiguration =
         (ReplicationBackupPolicyConfiguration)haPolicyConfiguration;
      assertEquals("c0", replicationBackupPolicyConfiguration.getClusterName());
      assertEquals(3, replicationBackupPolicyConfiguration.getMaxSavedReplicatedJournalsSize());
      assertEquals("g0", replicationBackupPolicyConfiguration.getGroupName());
      assertTrue(replicationBackupPolicyConfiguration.isAllowFailBack());
      assertEquals(5, replicationBackupPolicyConfiguration.getInitialReplicationSyncTimeout());
      assertEquals(2, replicationBackupPolicyConfiguration.getRetryReplicationWait());
      assertEquals("class0", replicationBackupPolicyConfiguration.getDistributedManagerConfiguration().getClassName());
      assertEquals("v0", replicationBackupPolicyConfiguration.getDistributedManagerConfiguration().getProperties().get("k0"));
   }

   private void addScaleDownConfigurationProperties(Properties properties) {
      properties.put("HAPolicyConfiguration.scaleDownConfiguration.connectors", "a,b,c");
      properties.put("HAPolicyConfiguration.scaleDownConfiguration.discoveryGroup", "dg0");
      properties.put("HAPolicyConfiguration.scaleDownConfiguration.groupName", "g0");
      properties.put("HAPolicyConfiguration.scaleDownConfiguration.clusterName", "c0");
      properties.put("HAPolicyConfiguration.scaleDownConfiguration.enabled", "false");
      properties.put("HAPolicyConfiguration.scaleDownConfiguration.commitInterval", "73");
   }

   private void checkScaleDownConfiguration(ScaleDownConfiguration scaleDownConfiguration) {
      assertNotNull(scaleDownConfiguration);
      assertEquals(3, scaleDownConfiguration.getConnectors().size());
      assertEquals("dg0", scaleDownConfiguration.getDiscoveryGroup());
      assertEquals("g0", scaleDownConfiguration.getGroupName());
      assertEquals("c0", scaleDownConfiguration.getClusterName());
      assertFalse(scaleDownConfiguration.isEnabled());
      assertEquals(73, scaleDownConfiguration.getCommitInterval());
   }

   @Test
   public void testFederationUpstreamConfiguration() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();

      properties.put("federationConfigurations.f1.upstreamConfigurations.joe.connectionConfiguration.reconnectAttempts", "1");
      properties.put("federationConfigurations.f1.upstreamConfigurations.joe.connectionConfiguration.staticConnectors", "a,b,c");
      properties.put("federationConfigurations.f1.upstreamConfigurations.joe.policyRefs", "pq1,pq2");

      properties.put("federationConfigurations.f1.queuePolicies.qp1.transformerRef", "simpleTransform");
      properties.put("federationConfigurations.f1.queuePolicies.qp2.includes.all-N.queueMatch", "N#");

      properties.put("federationConfigurations.f1.addressPolicies.a1.transformerRef", "simpleTransform");
      properties.put("federationConfigurations.f1.addressPolicies.a1.excludes.just-b.addressMatch", "b");

      properties.put("federationConfigurations.f1.policySets.combined.policyRefs", "qp1,qp2,a1");

      properties.put("federationConfigurations.f1.transformerConfigurations.simpleTransform.transformerConfiguration.className", "a.b");
      properties.put("federationConfigurations.f1.transformerConfigurations.simpleTransform.transformerConfiguration.properties.a", "b");

      properties.put("federationConfigurations.f1.credentials.user", "u");
      properties.put("federationConfigurations.f1.credentials.password", "ENC(2a7c211d21c295cdbcde3589c205decb)");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getFederationConfigurations().size());
      assertEquals(1, configuration.getFederationConfigurations().get(0).getUpstreamConfigurations().get(0).getConnectionConfiguration().getReconnectAttempts());
      assertEquals(3, configuration.getFederationConfigurations().get(0).getUpstreamConfigurations().get(0).getConnectionConfiguration().getStaticConnectors().size());

      assertEquals(2, configuration.getFederationConfigurations().get(0).getUpstreamConfigurations().get(0).getPolicyRefs().size());

      assertEquals(4, configuration.getFederationConfigurations().get(0).getFederationPolicyMap().size());
      assertEquals("qp1", configuration.getFederationConfigurations().get(0).getFederationPolicyMap().get("qp1").getName());

      assertEquals("combined", configuration.getFederationConfigurations().get(0).getFederationPolicyMap().get("combined").getName());
      assertEquals(3, ((FederationPolicySet)configuration.getFederationConfigurations().get(0).getFederationPolicyMap().get("combined")).getPolicyRefs().size());

      assertEquals("simpleTransform", ((FederationQueuePolicyConfiguration)configuration.getFederationConfigurations().get(0).getFederationPolicyMap().get("qp1")).getTransformerRef());

      assertEquals("N#", ((FederationQueuePolicyConfiguration.Matcher)((FederationQueuePolicyConfiguration)configuration.getFederationConfigurations().get(0).getFederationPolicyMap().get("qp2")).getIncludes().toArray()[0]).getQueueMatch());
      assertEquals("b", ((FederationAddressPolicyConfiguration.Matcher)((FederationAddressPolicyConfiguration)configuration.getFederationConfigurations().get(0).getFederationPolicyMap().get("a1")).getExcludes().toArray()[0]).getAddressMatch());

      assertEquals("b", configuration.getFederationConfigurations().get(0).getTransformerConfigurations().get("simpleTransform").getTransformerConfiguration().getProperties().get("a"));
      assertEquals("a.b", configuration.getFederationConfigurations().get(0).getTransformerConfigurations().get("simpleTransform").getTransformerConfiguration().getClassName());

      assertEquals("u", configuration.getFederationConfigurations().get(0).getCredentials().getUser());
      assertEquals("secureexample", configuration.getFederationConfigurations().get(0).getCredentials().getPassword());
   }

   @Test
   public void testExportWithNonPasswordEnc() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("name", "ENC(2a7c211d21c295cdbcde3589c205decb)");

      properties.put("AMQPConnections.target.uri", "tcp://amq-dc1-tls-amqp-svc.dc1.svc.cluster.local:5673?clientFailureCheckPeriod=30000&connectionTTL=60000&sslEnabled=true&verifyHost=false&trustStorePath=/remote-cluster-truststore/client.ts");
      properties.put("AMQPConnections.target.transportConfigurations.target.params.enc", "ENC(2a7c211d21c295cdbcde3589c205decb)");

      configuration.parsePrefixedProperties(properties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals("secureexample", configuration.getName());
      assertEquals("secureexample", configuration.getAMQPConnections().get(0).getTransportConfigurations().get(0).getParams().get("enc"));

      File exported = new File(getTestDirfile(), "broker_config_as_properties_export.txt");
      configuration.exportAsProperties(exported);

      ConfigurationImpl loadFromExport = new ConfigurationImpl();
      loadFromExport.parseFileProperties(exported);

      assertTrue(loadFromExport.getStatus().contains("\"errors\":[]"));

      assertEquals(REDACTED, loadFromExport.getName());
      assertEquals(REDACTED, loadFromExport.getAMQPConnections().get(0).getTransportConfigurations().get(0).getParams().get("enc"));

      ConfigurationImpl configurationCopy = (ConfigurationImpl) loadFromExport.copy();
      configurationCopy.exportAsProperties(exported);

      ConfigurationImpl loadFromExportCopy = new ConfigurationImpl();
      loadFromExportCopy.parseFileProperties(exported);

      assertTrue(loadFromExportCopy.getStatus().contains("\"errors\":[]"));

      assertEquals(REDACTED, loadFromExportCopy.getName());
      assertEquals(REDACTED, loadFromExportCopy.getAMQPConnections().get(0).getTransportConfigurations().get(0).getParams().get("enc"));
   }

   @Test
   public void testAMQPBrokerConnectionMix() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();

      properties.put("AMQPConnections.brokerA.uri", "tcp://brokerA:5672");
      properties.put("AMQPConnections.brokerB.uri", "tcp://brokerB:5672");

      properties.put("AMQPConnections.brokerA.federations.abc.type", AMQPBrokerConnectionAddressType.FEDERATION.toString());
      properties.put("AMQPConnections.brokerB.mirrors.mirror.type", AMQPBrokerConnectionAddressType.MIRROR.toString());

      configuration.parsePrefixedProperties(properties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(2, configuration.getAMQPConnection().size());
      for (AMQPBrokerConnectConfiguration amqpBrokerConnectConfiguration : configuration.getAMQPConnection()) {
         if ("brokerB".equals(amqpBrokerConnectConfiguration.getName())) {
            assertEquals(AMQPBrokerConnectionAddressType.MIRROR, amqpBrokerConnectConfiguration.getConnectionElements().get(0).getType());
         } else if ("brokerA".equals(amqpBrokerConnectConfiguration.getName())) {
            assertEquals(AMQPBrokerConnectionAddressType.FEDERATION, amqpBrokerConnectConfiguration.getConnectionElements().get(0).getType());
         } else {
            fail("unexpected amqp broker connection configuration: " + amqpBrokerConnectConfiguration.getName());
         }
      }

      File exported = new File(getTestDirfile(), "broker_config_as_properties_export.txt");
      configuration.exportAsProperties(exported);
      ConfigurationImpl loadFromExport = new ConfigurationImpl();
      loadFromExport.parseFileProperties(exported);
      assertTrue(loadFromExport.getStatus().contains("\"errors\":[]"));
   }

   @Test
   public void testAMQPBrokerConnectionTypes() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();

      properties.put("AMQPConnections.brokerA.uri", "tcp://brokerA:5672");

      // they all need a unique name as they share a collection
      properties.put("AMQPConnections.brokerA.federations.a.type", AMQPBrokerConnectionAddressType.FEDERATION.toString());
      properties.put("AMQPConnections.brokerA.mirrors.b.type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      properties.put("AMQPConnections.brokerA.peers.c.type", AMQPBrokerConnectionAddressType.PEER.toString());
      properties.put("AMQPConnections.brokerA.senders.d.type", AMQPBrokerConnectionAddressType.SENDER.toString());
      properties.put("AMQPConnections.brokerA.receivers.e.type", AMQPBrokerConnectionAddressType.RECEIVER.toString());
      properties.put("AMQPConnections.brokerA.bridges.f.type", AMQPBrokerConnectionAddressType.BRIDGE.toString());

      configuration.parsePrefixedProperties(properties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(1, configuration.getAMQPConnection().size());

      Set<AMQPBrokerConnectionAddressType> typesToFind = new HashSet<>();
      for (AMQPBrokerConnectionAddressType t : AMQPBrokerConnectionAddressType.values()) {
         typesToFind.add(t);
      }
      for (AMQPBrokerConnectionElement amqpBrokerConnectConfiguration : configuration.getAMQPConnection().get(0).getConnectionElements()) {
         typesToFind.remove(amqpBrokerConnectConfiguration.getType());
      }
      assertTrue(typesToFind.isEmpty());
   }

   @Test
   public void testSetNestedPropertyOnCollections() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("connectionRouters.joe.localTargetFilter", "LF");
      properties.put("connectionRouters.joe.keyFilter", "TF");
      properties.put("connectionRouters.joe.keyType", "SOURCE_IP");

      properties.put("acceptorConfigurations.tcp.factoryClassName", NETTY_ACCEPTOR_FACTORY);
      properties.put("acceptorConfigurations.tcp.params.HOST", "LOCALHOST");
      properties.put("acceptorConfigurations.tcp.params.PORT", "61616");

      properties.put("acceptorConfigurations.invm.factoryClassName", INVM_ACCEPTOR_FACTORY);
      properties.put("acceptorConfigurations.invm.params.ID", "0");

      //   <amqp-connection uri="tcp://HOST:PORT" name="other-server" retry-interval="100" reconnect-attempts="-1" user="john" password="doe">
      properties.put("AMQPConnections.other-server.uri", "tcp://HOST:PORT");
      properties.put("AMQPConnections.other-server.retryInterval", "100");
      properties.put("AMQPConnections.other-server.reconnectAttempts", "100");
      properties.put("AMQPConnections.other-server.user", "john");
      properties.put("AMQPConnections.other-server.password", "doe");

      //   <amqp-connection uri="tcp://brokerB:5672" name="brokerB"> <mirror/> </amqp-connection>
      properties.put("AMQPConnections.brokerB.uri", "tcp://brokerB:5672");
      properties.put("AMQPConnections.brokerB.connectionElements.mirror.type", AMQPBrokerConnectionAddressType.MIRROR.toString());
      properties.put("AMQPConnections.brokerB.connectionElements.mirror.mirrorSNF", "mirrorSNFQueue");

      properties.put("resourceLimitSettings.joe.maxConnections", "100");

      configuration.parsePrefixedProperties(properties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(1, configuration.getConnectionRouters().size());
      assertEquals("LF", configuration.getConnectionRouters().get(0).getLocalTargetFilter());
      assertEquals("TF", configuration.getConnectionRouters().get(0).getKeyFilter());
      assertEquals(KeyType.SOURCE_IP, configuration.getConnectionRouters().get(0).getKeyType());

      assertEquals(2, configuration.getAcceptorConfigurations().size());

      for (TransportConfiguration acceptor : configuration.getAcceptorConfigurations()) {
         if ("tcp".equals(acceptor.getName())) {
            assertEquals("61616", acceptor.getParams().get("PORT"));
            assertEquals(NETTY_ACCEPTOR_FACTORY, acceptor.getFactoryClassName());
         }
         if ("invm".equals(acceptor.getName())) {
            assertEquals("0", acceptor.getParams().get("ID"));
            assertEquals(INVM_ACCEPTOR_FACTORY, acceptor.getFactoryClassName());
         }
      }

      assertEquals(2, configuration.getAMQPConnection().size());
      for (AMQPBrokerConnectConfiguration amqpBrokerConnectConfiguration : configuration.getAMQPConnection()) {
         if ("brokerB".equals(amqpBrokerConnectConfiguration.getName())) {
            assertEquals(AMQPBrokerConnectionAddressType.MIRROR.toString(), amqpBrokerConnectConfiguration.getConnectionElements().get(0).getType().toString());
            assertEquals("mirrorSNFQueue", ((AMQPMirrorBrokerConnectionElement)amqpBrokerConnectConfiguration.getConnectionElements().get(0)).getMirrorSNF().toString());

         } else if ("other-server".equals(amqpBrokerConnectConfiguration.getName())) {
            assertEquals(100, amqpBrokerConnectConfiguration.getReconnectAttempts());
         } else {
            fail("unexpected amqp broker connection configuration: " + amqpBrokerConnectConfiguration.getName());
         }
      }

      // continue testing deprecated method
      assertEquals(100, configuration.getResourceLimitSettings().get("joe").getMaxConnections());
      assertEquals(100, configuration.getResourceLimitSettings().get("joe").getMaxSessions());
   }

   @Test
   public void testSetNestedPropertyOnExistingCollectionEntryViaMappedNotation() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("connectionRouters.joe.localTargetFilter", "LF");
      // does not exist, ignored
      properties.put("connectionRouters(bob).keyFilter", "TF");

      configuration.parsePrefixedProperties(properties, null);
      assertFalse(configuration.getStatus().contains("\"errors\":[]"));
      assertTrue(configuration.getStatus().contains("does not exist"));
      assertTrue(configuration.getStatus().contains("mapped key: bob"));

      properties = new Properties();
      // update existing
      properties.put("connectionRouters(joe).keyFilter", "TF");

      configuration.parsePrefixedProperties(properties, null);
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      assertEquals(1, configuration.getConnectionRouters().size());
      assertEquals("LF", configuration.getConnectionRouters().get(0).getLocalTargetFilter());
      assertEquals("TF", configuration.getConnectionRouters().get(0).getKeyFilter());
   }


   @Test
   public void testAddressViaProperties() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();
      properties.put("addressConfigurations.\"LB.TEST\".name", "LB.TEST");
      properties.put("addressConfigurations.\"LB.TEST\".queueConfigs.\"LB.TEST\".routingType", "ANYCAST");
      properties.put("addressConfigurations.\"LB.TEST\".queueConfigs.\"LB.TEST\".durable", "false");

      configuration.parsePrefixedProperties(properties, null);
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      assertEquals(1, configuration.getAddressConfigurations().size());
      assertEquals(1, configuration.getAddressConfigurations().get(0).getQueueConfigs().size());
      assertEquals(SimpleString.of("LB.TEST"), configuration.getAddressConfigurations().get(0).getQueueConfigs().get(0).getAddress());
      assertNotNull(configuration.getAddressConfigurations().get(0).getQueueConfigs().get(0).isDurable());
      assertFalse(configuration.getAddressConfigurations().get(0).getQueueConfigs().get(0).isDurable());
   }

   @Test
   public void testAddressRemovalViaProperties() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      properties.put("addressConfigurations.\"LB.TEST\".queueConfigs.\"LB.TEST\".routingType", "ANYCAST");
      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressConfigurations().size());
      assertEquals(1, configuration.getAddressConfigurations().get(0).getQueueConfigs().size());
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      properties.clear();
      properties.put("addressConfigurations.\"LB.TEST\"", "-");
      configuration.parsePrefixedProperties(properties, null);

      assertEquals(0, configuration.getAddressConfigurations().size());
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));
   }

   @Test
   public void testAllAddressRemovalViaProperties() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      properties.put("addressConfigurations.\"LB.TEST\".queueConfigs.\"LB.TEST\".routingType", "ANYCAST");
      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressConfigurations().size());
      assertEquals(1, configuration.getAddressConfigurations().get(0).getQueueConfigs().size());
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      properties.clear();
      properties.put("addressConfigurations", "-");
      configuration.parsePrefixedProperties(properties, null);

      assertEquals(0, configuration.getAddressConfigurations().size());
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));
   }

   @Test
   public void testAllMapRemovalViaProperties() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();
      properties.put("addressSettings.NeedToTrackExpired.expiryAddress", "important");
      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressSettings().size());
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      properties.clear();
      properties.put("addressSettings", "-");
      configuration.parsePrefixedProperties(properties, null);

      assertEquals(0, configuration.getAddressSettings().size());
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));
   }

   @Test
   public void testRemovalInvalidViaProperties() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();
      properties.put("HAPolicyConfiguration", "PRIMARY_ONLY");
      configuration.parsePrefixedProperties(properties, null);

      assertNotNull(configuration.getHAPolicyConfiguration());
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      properties.clear();
      properties.put("HAPolicyConfiguration", "-");
      configuration.parsePrefixedProperties(properties, null);
      assertNotNull(configuration.getHAPolicyConfiguration());

      assertEquals(0, configuration.getAddressSettings().size());
      assertTrue(configuration.getStatus().contains("InvocationTargetException"));
   }

   @Test
   public void testRoleRemovalViaCustomRemoveProperties() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      properties.put("securityRoles.TEST.users.send", "true");
      configuration.parsePrefixedProperties(properties, null);
      assertEquals(1, configuration.getSecurityRoles().size());
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      properties.clear();
      properties.put(ActiveMQDefaultConfiguration.BROKER_PROPERTIES_REMOVE_VALUE_PROPERTY, "^");
      properties.put("securityRoles.TEST", "^");
      configuration.parsePrefixedProperties(properties, null);
      assertEquals(0, configuration.getSecurityRoles().size());
      assertTrue(configuration.getStatus().contains("\"errors\":[]"));
   }

   @Test
   public void testIDCacheSizeViaProperties() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      assertTrue(configuration.isPersistIDCache());
      Properties properties = new Properties();

      properties.put("iDCacheSize", "50");
      properties.put("persistIDCache", false);
      configuration.parsePrefixedProperties(properties, null);

      assertEquals(50, configuration.getIDCacheSize());
      assertFalse(configuration.isPersistIDCache());
   }

   @Test
   public void testAcceptorViaProperties() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      configuration.getAcceptorConfigurations().add(ConfigurationUtils.parseAcceptorURI(
         "artemis", "tcp://0.0.0.0:61616?protocols=CORE,AMQP,STOMP,HORNETQ,MQTT,OPENWIRE;supportAdvisory=false;suppressInternalManagementObjects=false").get(0));

      Properties properties = new Properties();

      properties.put("acceptorConfigurations.artemis.extraParams.supportAdvisory", "true");
      properties.put("acceptorConfigurations.new.extraParams.supportAdvisory", "true");

      configuration.parsePrefixedProperties(properties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(2, configuration.getAcceptorConfigurations().size());

      TransportConfiguration artemisTransportConfiguration = configuration.getAcceptorConfigurations().stream().filter(
         transportConfiguration -> transportConfiguration.getName().equals("artemis")).findFirst().get();
      assertTrue(artemisTransportConfiguration.getParams().containsKey("protocols"));
      assertEquals("CORE,AMQP,STOMP,HORNETQ,MQTT,OPENWIRE", artemisTransportConfiguration.getParams().get("protocols"));
      assertTrue(artemisTransportConfiguration.getExtraParams().containsKey("supportAdvisory"));
      assertEquals("true", artemisTransportConfiguration.getExtraParams().get("supportAdvisory"));
      assertTrue(artemisTransportConfiguration.getExtraParams().containsKey("suppressInternalManagementObjects"));
      assertEquals("false", artemisTransportConfiguration.getExtraParams().get("suppressInternalManagementObjects"));

      TransportConfiguration newTransportConfiguration = configuration.getAcceptorConfigurations().stream().filter(
         transportConfiguration -> transportConfiguration.getName().equals("new")).findFirst().get();
      assertTrue(newTransportConfiguration.getExtraParams().containsKey("supportAdvisory"));
      assertEquals("true", newTransportConfiguration.getExtraParams().get("supportAdvisory"));
      assertEquals("null", newTransportConfiguration.getFactoryClassName());

      // update with correct factoryClassName
      properties.put("acceptorConfigurations.new.factoryClassName", NETTY_ACCEPTOR_FACTORY);
      configuration.parsePrefixedProperties(properties, null);
      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());
      assertEquals(NETTY_ACCEPTOR_FACTORY, newTransportConfiguration.getFactoryClassName());
   }


   @Test
   public void testAddressSettingsViaProperties() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      properties.put("addressesSettings.#.expiryAddress", "sharedExpiry"); // verify @Deprecation double plural still works
      properties.put("addressSettings.NeedToTrackExpired.expiryAddress", "important");
      properties.put("addressSettings.\"Name.With.Dots\".expiryAddress", "moreImportant");
      properties.put("addressSettings.NeedToSet.autoCreateExpiryResources", "true");
      properties.put("addressSettings.NeedToSet.deadLetterAddress", "iamDeadLetterAdd");
      properties.put("addressSettings.NeedToSet.expiryQueuePrefix", "add1Expiry");
      properties.put("addressSettings.NeedToSet.maxReadPageBytes", 20000000);
      properties.put("addressSettings.NeedToSet.deadLetterQueuePrefix", "iamDeadLetterQueuePre");
      properties.put("addressSettings.NeedToSet.managementMessageAttributeSizeLimit", 512);
      properties.put("addressSettings.NeedToSet.pageSizeBytes", 12345);
      properties.put("addressSettings.NeedToSet.expiryQueueSuffix", "add1ExpirySuffix");
      properties.put("addressSettings.NeedToSet.expiryDelay", 44);
      properties.put("addressSettings.NeedToSet.minExpiryDelay", 5);
      properties.put("addressSettings.NeedToSet.maxExpiryDelay", 10);
      properties.put("addressSettings.NeedToSet.enableIngressTimestamp", "true");
      properties.put("addressSettings.NeedToSet.managementBrowsePageSize", 300);
      properties.put("addressSettings.NeedToSet.retroactiveMessageCount", -1);
      properties.put("addressSettings.NeedToSet.maxDeliveryAttempts", 10);
      properties.put("addressSettings.NeedToSet.defaultGroupFirstKey", "add1Key");
      properties.put("addressSettings.NeedToSet.slowConsumerCheckPeriod", 100);
      properties.put("addressSettings.NeedToSet.defaultLastValueKey", "add1Lvk");
      properties.put("addressSettings.NeedToSet.configDeleteDiverts", DeletionPolicy.FORCE);
      properties.put("addressSettings.NeedToSet.defaultConsumerWindowSize", 1000);
      properties.put("addressSettings.NeedToSet.messageCounterHistoryDayLimit", 7);
      properties.put("addressSettings.NeedToSet.defaultGroupRebalance", "true");
      properties.put("addressSettings.NeedToSet.defaultQueueRoutingType", RoutingType.ANYCAST);
      properties.put("addressSettings.NeedToSet.autoDeleteQueuesMessageCount", 6789);
      properties.put("addressSettings.NeedToSet.addressFullMessagePolicy", AddressFullMessagePolicy.DROP);
      properties.put("addressSettings.NeedToSet.diskFullMessagePolicy", DiskFullMessagePolicy.DROP);
      properties.put("addressSettings.NeedToSet.maxSizeBytes", 6666);
      properties.put("addressSettings.NeedToSet.redistributionDelay", 22);
      properties.put("addressSettings.NeedToSet.maxSizeBytesRejectThreshold", 12334);
      properties.put("addressSettings.NeedToSet.defaultAddressRoutingType", RoutingType.ANYCAST);
      properties.put("addressSettings.NeedToSet.autoCreateDeadLetterResources", "true");
      properties.put("addressSettings.NeedToSet.pageCacheMaxSize", 10);
      properties.put("addressSettings.NeedToSet.maxRedeliveryDelay", 66);
      properties.put("addressSettings.NeedToSet.maxSizeMessages", 10000000);
      properties.put("addressSettings.NeedToSet.redeliveryMultiplier", 2.1);
      properties.put("addressSettings.NeedToSet.defaultRingSize", -2);
      properties.put("addressSettings.NeedToSet.defaultLastValueQueue", "true");
      properties.put("addressSettings.NeedToSet.redeliveryCollisionAvoidanceFactor", 5.0);
      properties.put("addressSettings.NeedToSet.autoDeleteQueuesDelay", 3);
      properties.put("addressSettings.NeedToSet.autoDeleteAddressesDelay", 33);
      properties.put("addressSettings.NeedToSet.enableMetrics", "false");
      properties.put("addressSettings.NeedToSet.sendToDLAOnNoRoute", "true");
      properties.put("addressSettings.NeedToSet.slowConsumerThresholdMeasurementUnit", SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_HOUR);
      properties.put("addressSettings.NeedToSet.deadLetterQueueSuffix", "iamDeadLetterQueueSuf");
      properties.put("addressSettings.NeedToSet.queuePrefetch", 900);
      properties.put("addressSettings.NeedToSet.defaultNonDestructive", "true");
      properties.put("addressSettings.NeedToSet.configDeleteAddresses", DeletionPolicy.FORCE);
      properties.put("addressSettings.NeedToSet.slowConsumerThreshold", 10);
      properties.put("addressSettings.NeedToSet.configDeleteQueues", DeletionPolicy.FORCE);
      properties.put("addressSettings.NeedToSet.autoCreateAddresses", "false");
      properties.put("addressSettings.NeedToSet.autoDeleteQueues", "false");
      properties.put("addressSettings.NeedToSet.defaultConsumersBeforeDispatch", 1);
      properties.put("addressSettings.NeedToSet.defaultPurgeOnNoConsumers", "true");
      properties.put("addressSettings.NeedToSet.autoCreateQueues", "false");
      properties.put("addressSettings.NeedToSet.autoDeleteAddresses", "false");
      properties.put("addressSettings.NeedToSet.defaultDelayBeforeDispatch", 77);
      properties.put("addressSettings.NeedToSet.autoDeleteCreatedQueues", "true");
      properties.put("addressSettings.NeedToSet.defaultExclusiveQueue", "true");
      properties.put("addressSettings.NeedToSet.defaultMaxConsumers", 10);
      properties.put("addressSettings.NeedToSet.iDCacheSize", 10);
      properties.put("addressSettings.NeedToSet.noExpiry", true);

      configuration.parsePrefixedProperties(properties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(4, configuration.getAddressSettings().size());
      assertEquals(SimpleString.of("sharedExpiry"), configuration.getAddressSettings().get("#").getExpiryAddress());
      assertEquals(SimpleString.of("important"), configuration.getAddressSettings().get("NeedToTrackExpired").getExpiryAddress());
      assertEquals(SimpleString.of("moreImportant"), configuration.getAddressSettings().get("Name.With.Dots").getExpiryAddress());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isAutoCreateExpiryResources());
      assertEquals(SimpleString.of("iamDeadLetterAdd"), configuration.getAddressSettings().get("NeedToSet").getDeadLetterAddress());
      assertEquals(SimpleString.of("add1Expiry"), configuration.getAddressSettings().get("NeedToSet").getExpiryQueuePrefix());
      assertEquals(20000000, configuration.getAddressSettings().get("NeedToSet").getMaxReadPageBytes());
      assertEquals(512, configuration.getAddressSettings().get("NeedToSet").getManagementMessageAttributeSizeLimit());
      assertEquals(SimpleString.of("iamDeadLetterQueuePre"), configuration.getAddressSettings().get("NeedToSet").getDeadLetterQueuePrefix());
      assertEquals(12345, configuration.getAddressSettings().get("NeedToSet").getPageSizeBytes());
      assertEquals(SimpleString.of("add1ExpirySuffix"), configuration.getAddressSettings().get("NeedToSet").getExpiryQueueSuffix());
      assertEquals(Long.valueOf(44), configuration.getAddressSettings().get("NeedToSet").getExpiryDelay());
      assertEquals(Long.valueOf(5), configuration.getAddressSettings().get("NeedToSet").getMinExpiryDelay());
      assertEquals(Long.valueOf(10), configuration.getAddressSettings().get("NeedToSet").getMaxExpiryDelay());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isEnableIngressTimestamp());
      assertEquals(300, configuration.getAddressSettings().get("NeedToSet").getManagementBrowsePageSize());
      assertEquals(-1, configuration.getAddressSettings().get("NeedToSet").getRetroactiveMessageCount());
      assertEquals(10, configuration.getAddressSettings().get("NeedToSet").getMaxDeliveryAttempts());
      assertEquals(SimpleString.of("add1Key"), configuration.getAddressSettings().get("NeedToSet").getDefaultGroupFirstKey());
      assertEquals(100, configuration.getAddressSettings().get("NeedToSet").getSlowConsumerCheckPeriod());
      assertEquals(SimpleString.of("add1Lvk"), configuration.getAddressSettings().get("NeedToSet").getDefaultLastValueKey());
      assertEquals(DeletionPolicy.FORCE, configuration.getAddressSettings().get("NeedToSet").getConfigDeleteDiverts());
      assertEquals(1000, configuration.getAddressSettings().get("NeedToSet").getDefaultConsumerWindowSize());
      assertEquals(7, configuration.getAddressSettings().get("NeedToSet").getMessageCounterHistoryDayLimit());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isDefaultGroupRebalance());
      assertEquals(RoutingType.ANYCAST, configuration.getAddressSettings().get("NeedToSet").getDefaultQueueRoutingType());
      assertEquals(6789, configuration.getAddressSettings().get("NeedToSet").getAutoDeleteQueuesMessageCount());
      assertEquals(AddressFullMessagePolicy.DROP, configuration.getAddressSettings().get("NeedToSet").getAddressFullMessagePolicy());
      assertEquals(DiskFullMessagePolicy.DROP, configuration.getAddressSettings().get("NeedToSet").getDiskFullMessagePolicy());
      assertEquals(6666, configuration.getAddressSettings().get("NeedToSet").getMaxSizeBytes());
      assertEquals(22, configuration.getAddressSettings().get("NeedToSet").getRedistributionDelay());
      assertEquals(12334, configuration.getAddressSettings().get("NeedToSet").getMaxSizeBytesRejectThreshold());
      assertEquals(RoutingType.ANYCAST, configuration.getAddressSettings().get("NeedToSet").getDefaultAddressRoutingType());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isAutoCreateDeadLetterResources());
      assertEquals(10, configuration.getAddressSettings().get("NeedToSet").getPageCacheMaxSize());
      assertEquals(66, configuration.getAddressSettings().get("NeedToSet").getMaxRedeliveryDelay());
      assertEquals(10000000, configuration.getAddressSettings().get("NeedToSet").getMaxSizeMessages());
      assertEquals(2.1, configuration.getAddressSettings().get("NeedToSet").getRedeliveryMultiplier(), .01);
      assertEquals(-2, configuration.getAddressSettings().get("NeedToSet").getDefaultRingSize());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isDefaultLastValueQueue());
      assertEquals(5.0, configuration.getAddressSettings().get("NeedToSet").getRedeliveryCollisionAvoidanceFactor(), .01);
      assertEquals(3, configuration.getAddressSettings().get("NeedToSet").getAutoDeleteQueuesDelay());
      assertEquals(33, configuration.getAddressSettings().get("NeedToSet").getAutoDeleteAddressesDelay());
      assertFalse(configuration.getAddressSettings().get("NeedToSet").isEnableMetrics());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isSendToDLAOnNoRoute());
      assertEquals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_HOUR, configuration.getAddressSettings().get("NeedToSet").getSlowConsumerThresholdMeasurementUnit());
      assertEquals(900, configuration.getAddressSettings().get("NeedToSet").getQueuePrefetch());
      assertEquals(SimpleString.of("iamDeadLetterQueueSuf"), configuration.getAddressSettings().get("NeedToSet").getDeadLetterQueueSuffix());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isDefaultNonDestructive());
      assertEquals(DeletionPolicy.FORCE, configuration.getAddressSettings().get("NeedToSet").getConfigDeleteAddresses());
      assertEquals(10, configuration.getAddressSettings().get("NeedToSet").getSlowConsumerThreshold());
      assertEquals(DeletionPolicy.FORCE, configuration.getAddressSettings().get("NeedToSet").getConfigDeleteQueues());
      assertFalse(configuration.getAddressSettings().get("NeedToSet").isAutoCreateAddresses());
      assertFalse(configuration.getAddressSettings().get("NeedToSet").isAutoDeleteQueues());
      assertEquals(Integer.valueOf(1), configuration.getAddressSettings().get("NeedToSet").getDefaultConsumersBeforeDispatch());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isDefaultPurgeOnNoConsumers());
      assertFalse(configuration.getAddressSettings().get("NeedToSet").isAutoCreateQueues());
      assertFalse(configuration.getAddressSettings().get("NeedToSet").isAutoDeleteAddresses());
      assertEquals(Long.valueOf(77), configuration.getAddressSettings().get("NeedToSet").getDefaultDelayBeforeDispatch());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isAutoDeleteCreatedQueues());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isDefaultExclusiveQueue());
      assertEquals(Integer.valueOf(10), configuration.getAddressSettings().get("NeedToSet").getDefaultMaxConsumers());
      assertEquals(Integer.valueOf(10), configuration.getAddressSettings().get("NeedToSet").getIDCacheSize());
      assertTrue(configuration.getAddressSettings().get("NeedToSet").isNoExpiry());
   }

   @Test
   public void testAddressSettingsPageLimit() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      String randomString = RandomUtil.randomUUIDString();

      properties.put("addressSettings.#.expiryAddress", randomString);
      properties.put("addressSettings.#.pageLimitMessages", "300");
      properties.put("addressSettings.#.pageLimitBytes", "300000");
      properties.put("addressSettings.#.pageFullMessagePolicy", "DROP");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressSettings().size());
      assertEquals(SimpleString.of(randomString), configuration.getAddressSettings().get("#").getExpiryAddress());
      assertEquals(300L, configuration.getAddressSettings().get("#").getPageLimitMessages().longValue());
      assertEquals(300000L, configuration.getAddressSettings().get("#").getPageLimitBytes().longValue());
      assertEquals("DROP", configuration.getAddressSettings().get("#").getPageFullMessagePolicy().toString());

      PagingStore storeImpl = new PagingStoreImpl(SimpleString.of("Test"), mockScheduledExecutor(), 100L, Mockito.mock(PagingManager.class), Mockito.mock(StorageManager.class), Mockito.mock(SequentialFileFactory.class), Mockito.mock(PagingStoreFactory.class), SimpleString.of("Test"), configuration.getAddressSettings().get("#"), Mockito.mock(ArtemisExecutor.class), true);

      assertEquals(300L, storeImpl.getPageLimitMessages().longValue());
      assertEquals(300000L, storeImpl.getPageLimitBytes().longValue());
      assertEquals("DROP", storeImpl.getPageFullMessagePolicy().toString());
   }

   @Test
   public void testAddressSettingsPageLimitInvalidConfiguration1() throws Throwable {
      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      String randomString = RandomUtil.randomUUIDString();

      // not setting pageFullMessagePolicy
      properties.put("addressSettings.#.expiryAddress", randomString);
      properties.put("addressSettings.#.pageLimitMessages", "300");
      properties.put("addressSettings.#.pageLimitBytes", "300000");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressSettings().size());
      assertEquals(SimpleString.of(randomString), configuration.getAddressSettings().get("#").getExpiryAddress());
      assertEquals((Long)300L, configuration.getAddressSettings().get("#").getPageLimitMessages());
      assertEquals((Long)300000L, configuration.getAddressSettings().get("#").getPageLimitBytes());
      assertNull(configuration.getAddressSettings().get("#").getPageFullMessagePolicy());

      PagingStore storeImpl = new PagingStoreImpl(SimpleString.of("Test"), mockScheduledExecutor(), 100L, Mockito.mock(PagingManager.class), Mockito.mock(StorageManager.class), Mockito.mock(SequentialFileFactory.class), Mockito.mock(PagingStoreFactory.class), SimpleString.of("Test"), configuration.getAddressSettings().get("#"), Mockito.mock(ArtemisExecutor.class), true);

      assertNull(storeImpl.getPageLimitMessages());
      assertNull(storeImpl.getPageLimitBytes());
      assertNull(storeImpl.getPageFullMessagePolicy());
      assertTrue(loggerHandler.findText("AMQ224125"));
   }


   @Test
   public void testAddressSettingsPageLimitInvalidConfiguration2() throws Throwable {
      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      String randomString = RandomUtil.randomUUIDString();

      // pageLimitBytes and pageFullMessagePolicy not set on purpose
      properties.put("addressSettings.#.expiryAddress", randomString);
      properties.put("addressSettings.#.pageLimitMessages", "300");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressSettings().size());
      assertEquals(SimpleString.of(randomString), configuration.getAddressSettings().get("#").getExpiryAddress());
      assertEquals((Long)300L, configuration.getAddressSettings().get("#").getPageLimitMessages());
      assertNull(configuration.getAddressSettings().get("#").getPageLimitBytes());
      assertNull(configuration.getAddressSettings().get("#").getPageFullMessagePolicy());

      PagingStore storeImpl = new PagingStoreImpl(SimpleString.of("Test"), mockScheduledExecutor(), 100L, Mockito.mock(PagingManager.class), Mockito.mock(StorageManager.class), Mockito.mock(SequentialFileFactory.class), Mockito.mock(PagingStoreFactory.class), SimpleString.of("Test"), configuration.getAddressSettings().get("#"), Mockito.mock(ArtemisExecutor.class), true);

      assertNull(storeImpl.getPageLimitMessages());
      assertNull(storeImpl.getPageLimitBytes());
      assertNull(storeImpl.getPageFullMessagePolicy());
      assertTrue(loggerHandler.findText("AMQ224125"));
   }

   @Test
   public void testAddressSettingsPageLimitInvalidConfiguration3() throws Throwable {
      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      String randomString = RandomUtil.randomUUIDString();

      // pageLimitMessages and pageFullMessagePolicy not set on purpose
      properties.put("addressSettings.#.expiryAddress", randomString);
      properties.put("addressSettings.#.pageLimitBytes", "300000");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressSettings().size());
      assertEquals(SimpleString.of(randomString), configuration.getAddressSettings().get("#").getExpiryAddress());
      assertNull(configuration.getAddressSettings().get("#").getPageLimitMessages());
      assertEquals((Long)300000L, configuration.getAddressSettings().get("#").getPageLimitBytes());
      assertNull(configuration.getAddressSettings().get("#").getPageFullMessagePolicy());

      PagingStore storeImpl = new PagingStoreImpl(SimpleString.of("Test"), mockScheduledExecutor(), 100L, Mockito.mock(PagingManager.class), Mockito.mock(StorageManager.class), Mockito.mock(SequentialFileFactory.class), Mockito.mock(PagingStoreFactory.class), SimpleString.of("Test"), configuration.getAddressSettings().get("#"), Mockito.mock(ArtemisExecutor.class), true);

      assertNull(storeImpl.getPageLimitMessages());
      assertNull(storeImpl.getPageLimitBytes());
      assertNull(storeImpl.getPageFullMessagePolicy());
      assertTrue(loggerHandler.findText("AMQ224125"));
   }

   @Test
   public void testAddressSettingsPageLimitInvalidConfiguration4() throws Throwable {
      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      String randomString = RandomUtil.randomUUIDString();

      // leaving out pageLimitMessages and pageLimitBytes
      properties.put("addressSettings.#.expiryAddress", randomString);
      properties.put("addressSettings.#.pageFullMessagePolicy", "DROP");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressSettings().size());
      assertEquals(SimpleString.of(randomString), configuration.getAddressSettings().get("#").getExpiryAddress());
      assertNull(configuration.getAddressSettings().get("#").getPageLimitMessages());
      assertNull(configuration.getAddressSettings().get("#").getPageLimitBytes());
      assertEquals("DROP", configuration.getAddressSettings().get("#").getPageFullMessagePolicy().toString());

      PagingStore storeImpl = new PagingStoreImpl(SimpleString.of("Test"), mockScheduledExecutor(), 100L, Mockito.mock(PagingManager.class), Mockito.mock(StorageManager.class), Mockito.mock(SequentialFileFactory.class), Mockito.mock(PagingStoreFactory.class), SimpleString.of("Test"), configuration.getAddressSettings().get("#"), Mockito.mock(ArtemisExecutor.class), true);

      assertNull(storeImpl.getPageLimitMessages());
      assertNull(storeImpl.getPageLimitBytes());
      assertNull(storeImpl.getPageFullMessagePolicy());
      assertTrue(loggerHandler.findText("AMQ224124"));
   }


   @Test
   public void testAddressSettingsPageLimitInvalidConfiguration5() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      String randomString = RandomUtil.randomUUIDString();

      properties.put("addressSettings.#.expiryAddress", randomString);
      properties.put("addressSettings.#.pageLimitMessages", "-1"); // this should make the PagingStore to parse it as null
      properties.put("addressSettings.#.pageLimitBytes", "-1"); // this should make the PagingStore to parse it as null
      properties.put("addressSettings.#.pageFullMessagePolicy", "DROP");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressSettings().size());
      assertEquals(SimpleString.of(randomString), configuration.getAddressSettings().get("#").getExpiryAddress());
      assertEquals(-1L, configuration.getAddressSettings().get("#").getPageLimitMessages().longValue());
      assertEquals(-1L, configuration.getAddressSettings().get("#").getPageLimitBytes().longValue());
      assertEquals("DROP", configuration.getAddressSettings().get("#").getPageFullMessagePolicy().toString());

      PagingStore storeImpl = new PagingStoreImpl(SimpleString.of("Test"), mockScheduledExecutor(), 100L, Mockito.mock(PagingManager.class), Mockito.mock(StorageManager.class), Mockito.mock(SequentialFileFactory.class), Mockito.mock(PagingStoreFactory.class), SimpleString.of("Test"), configuration.getAddressSettings().get("#"), Mockito.mock(ArtemisExecutor.class), true);

      assertNull(storeImpl.getPageLimitMessages());
      assertNull(storeImpl.getPageLimitBytes());
      assertNull(storeImpl.getPageFullMessagePolicy());
   }

   @Test
   public void testPagePrefetch() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      String randomString = RandomUtil.randomUUIDString();

      properties.put("addressSettings.#.expiryAddress", randomString);
      properties.put("addressSettings.#.prefetchPageMessages", "333");
      properties.put("addressSettings.#.prefetchPageBytes", "777");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressSettings().size());
      assertEquals(SimpleString.of(randomString), configuration.getAddressSettings().get("#").getExpiryAddress());
      assertEquals(333, configuration.getAddressSettings().get("#").getPrefetchPageMessages());
      assertEquals(777, configuration.getAddressSettings().get("#").getPrefetchPageBytes());

      PagingStore storeImpl = new PagingStoreImpl(SimpleString.of("Test"), mockScheduledExecutor(), 100L, Mockito.mock(PagingManager.class), Mockito.mock(StorageManager.class), Mockito.mock(SequentialFileFactory.class), Mockito.mock(PagingStoreFactory.class), SimpleString.of("Test"), configuration.getAddressSettings().get("#"), Mockito.mock(ArtemisExecutor.class), true);

      assertEquals(333, storeImpl.getPrefetchPageMessages());
      assertEquals(777, storeImpl.getPrefetchPageBytes());
   }

   @Test
   public void testPagePrefetchDefault() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      String randomString = RandomUtil.randomUUIDString();

      properties.put("addressSettings.#.maxReadPageMessages", "333");
      properties.put("addressSettings.#.maxReadPageBytes", "777");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getAddressSettings().size());
      assertEquals(333, configuration.getAddressSettings().get("#").getPrefetchPageMessages());
      assertEquals(777, configuration.getAddressSettings().get("#").getPrefetchPageBytes());
      assertEquals(333, configuration.getAddressSettings().get("#").getMaxReadPageMessages());
      assertEquals(777, configuration.getAddressSettings().get("#").getMaxReadPageBytes());

      PagingStore storeImpl = new PagingStoreImpl(SimpleString.of("Test"), mockScheduledExecutor(), 100L, Mockito.mock(PagingManager.class), Mockito.mock(StorageManager.class), Mockito.mock(SequentialFileFactory.class), Mockito.mock(PagingStoreFactory.class), SimpleString.of("Test"), configuration.getAddressSettings().get("#"), Mockito.mock(ArtemisExecutor.class), true);

      assertEquals(333, storeImpl.getPrefetchPageMessages());
      assertEquals(777, storeImpl.getPrefetchPageBytes());
      assertEquals(333, storeImpl.getMaxPageReadMessages());
      assertEquals(777, storeImpl.getMaxPageReadBytes());
   }


   @Test
   public void testDivertViaProperties() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();


      final String routingName = "divert1";
      final String address = "testAddress";
      final String forwardAddress = "forwardAddress";
      final String className = "s.o.m.e.class";

      Properties properties = new ConfigurationImpl.InsertionOrderedProperties();

      properties.put("divertConfigurations.divert1.routingName", routingName);
      properties.put("divertConfigurations.divert1.address", address);
      properties.put("divertConfigurations.divert1.forwardingAddress", forwardAddress);
      properties.put("divertConfigurations.divert1.transformerConfiguration.className", className);
      properties.put("divertConfigurations.divert1.transformerConfiguration.properties.a", "va");
      properties.put("divertConfigurations.divert1.transformerConfiguration.properties.b", "vb");

      configuration.parsePrefixedProperties(properties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(1, configuration.getDivertConfigurations().size());
      assertEquals(routingName, configuration.getDivertConfigurations().get(0).getRoutingName());
      assertEquals(address, configuration.getDivertConfigurations().get(0).getAddress());
      assertEquals(forwardAddress, configuration.getDivertConfigurations().get(0).getForwardingAddress());

      assertEquals(className, configuration.getDivertConfigurations().get(0).getTransformerConfiguration().getClassName());
      assertEquals("va", configuration.getDivertConfigurations().get(0).getTransformerConfiguration().getProperties().get("a"));
      assertEquals("vb", configuration.getDivertConfigurations().get(0).getTransformerConfiguration().getProperties().get("b"));
   }

   @Test
   public void testRoleSettingsViaProperties() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      properties.put("securityRoles.TEST.users.send", "true");
      properties.put("securityRoles.TEST.users.consume", "true");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(1, configuration.getSecurityRoles().size());
      assertEquals(1, configuration.getSecurityRoles().get("TEST").size());

      Role role = configuration.getSecurityRoles().get("TEST").stream().findFirst().orElse(null);
      assertNotNull(role);
      assertTrue(role.isConsume());

      role = configuration.getSecurityRoles().get("TEST").stream().findFirst().orElse(null);
      assertNotNull(role);
      assertTrue(role.isSend());

      role = configuration.getSecurityRoles().get("TEST").stream().findFirst().orElse(null);
      assertNotNull(role);
      assertFalse(role.isCreateAddress());
   }

   @Test
   public void testSecurityRoleMappingsViaPropertiesNotSupported() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties properties = new Properties();

      properties.put("securityRoleNameMappings.amq", "myrole4");

      configuration.parsePrefixedProperties(properties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      Exception expected = assertThrows(ClassCastException.class, ()-> {
         configuration.getSecurityRoleNameMappings().get("amq").contains("myrole4");
      });
      assertTrue(expected.toString().contains("Set"));
   }

   @Test
   public void testRoleAugmentViaProperties() throws Exception {

      final String xmlConfig = """
         <configuration xmlns="urn:activemq"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="urn:activemq /schema/artemis-configuration.xsd">
            <security-settings>
               <security-setting match="#">
                  <permission type="consume" roles="guest"/>
                  <permission type="send" roles="guest"/>
               </security-setting>
            </security-settings>
         </configuration>""";

      FileConfigurationParser parser = new FileConfigurationParser();
      ByteArrayInputStream input = new ByteArrayInputStream(xmlConfig.getBytes(StandardCharsets.UTF_8));

      ConfigurationImpl configuration = (ConfigurationImpl) parser.parseMainConfig(input);
      Properties properties = new Properties();

      // new entry
      properties.put("securityRoles.TEST.users.send", "true");
      properties.put("securityRoles.TEST.users.consume", "false");

      // modify existing role
      properties.put("securityRoles.#.guest.consume", "false");

      // modify with new role
      properties.put("securityRoles.#.users.send", "true");

      configuration.parsePrefixedProperties(properties, null);

      // verify new addition
      assertEquals(2, configuration.getSecurityRoles().size());
      assertEquals(1, configuration.getSecurityRoles().get("TEST").size());

      Role role = configuration.getSecurityRoles().get("TEST").stream().findFirst().orElse(null);
      assertNotNull(role);
      assertFalse(role.isConsume());

      role = configuration.getSecurityRoles().get("TEST").stream().findFirst().orElse(null);
      assertNotNull(role);
      assertTrue(role.isSend());

      // verify augmentation
      assertEquals(2, configuration.getSecurityRoles().get("#").size());
      Set roles = configuration.getSecurityRoles().get("#");
      class RolePredicate implements Predicate<Role> {
         final String roleName;
         RolePredicate(String name) {
            this.roleName = name;
         }
         @Override
         public boolean test(Role role) {
            return roleName.equals(role.getName()) && !role.isConsume() && role.isSend() && !role.isCreateAddress();
         }
      }
      assertEquals(1L, roles.stream().filter(new RolePredicate("guest")).count());
      assertEquals(1L, roles.stream().filter(new RolePredicate("users")).count());
   }

   @Test
   public void testValuePostFixModifier() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      properties.put("globalMaxSize", "25K");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(25 * 1024, configuration.getGlobalMaxSize());
   }

   @Test
   public void journalRetentionPeriod() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();
      properties.put("journalRetentionPeriod", TimeUnit.DAYS.toMillis(1));

      configuration.parsePrefixedProperties(properties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());
      assertEquals(TimeUnit.DAYS.toMillis(1), configuration.getJournalRetentionPeriod());
   }

   @Test
   public void testSystemPropValueReplaced() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties properties = new Properties();
      final String homeFromDefault = "default-home";
      final String homeFromEnv = System.getenv("HOME");
      properties.put("name", "${HOME:" + homeFromDefault + "}");
      configuration.parsePrefixedProperties(properties, null);
      if (homeFromEnv != null) {
         assertEquals(homeFromEnv, configuration.getName());
      } else {
         // if $HOME is not set for some platform
         assertEquals(homeFromDefault, configuration.getName());
      }
   }

   @Test
   public void testSystemPropValueNoMatch() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties properties = new Properties();
      properties.put("name", "vv-${SOME_RANDOM_VV}");
      configuration.parsePrefixedProperties(properties, null);
      assertEquals("vv-", configuration.getName());
   }

   @Test
   public void testSystemPropValueNonExistWithDefault() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties properties = new Properties();
      properties.put("name", "vv-${SOME_RANDOM_VV:y}");
      configuration.parsePrefixedProperties(properties, null);
      assertEquals("vv-y", configuration.getName());
   }


   @Test
   public void testSystemPropKeyReplacement() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties properties = new Properties();

      final String newKeyName = RandomUtil.randomUUIDString();
      final String valueFromSysProp = "VV";
      System.setProperty(newKeyName, valueFromSysProp);

      try {
         properties.put("connectorConfigurations.KEY-${" + newKeyName + "}.name", "y");
         configuration.parsePrefixedProperties(properties, null);
         assertNotNull(configuration.connectorConfigs.get("KEY-" + valueFromSysProp), "configured new key from prop");
         assertEquals("y", configuration.connectorConfigs.get("KEY-" + valueFromSysProp).getName());
      } finally {
         System.clearProperty(newKeyName);
      }
   }

   @Test
   public void testDatabaseStoreConfigurationProps() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("storeConfiguration", "DATABASE");
      insertionOrderedProperties.put("storeConfiguration.largeMessageTableName", "lmtn");
      insertionOrderedProperties.put("storeConfiguration.messageTableName", "mtn");
      insertionOrderedProperties.put("storeConfiguration.bindingsTableName", "btn");
      insertionOrderedProperties.put("storeConfiguration.dataSourceClassName", ActiveMQDefaultConfiguration.getDefaultDataSourceClassName());
      insertionOrderedProperties.put("storeConfiguration.nodeManagerStoreTableName", "nmtn");
      insertionOrderedProperties.put("storeConfiguration.pageStoreTableName", "pstn");
      insertionOrderedProperties.put("storeConfiguration.jdbcAllowedTimeDiff", 123);
      insertionOrderedProperties.put("storeConfiguration.jdbcConnectionUrl", "url");
      insertionOrderedProperties.put("storeConfiguration.jdbcDriverClassName", "dcn");
      insertionOrderedProperties.put("storeConfiguration.jdbcJournalSyncPeriodMillis", 456);
      insertionOrderedProperties.put("storeConfiguration.jdbcLockAcquisitionTimeoutMillis", 789);
      insertionOrderedProperties.put("storeConfiguration.jdbcLockExpirationMillis", 321);
      insertionOrderedProperties.put("storeConfiguration.jdbcLockRenewPeriodMillis", 654);
      insertionOrderedProperties.put("storeConfiguration.jdbcNetworkTimeout", 987);
      insertionOrderedProperties.put("storeConfiguration.dataSourceProperties.password", "pass");
      insertionOrderedProperties.put("storeConfiguration.dataSourceProperties.initialSize", 3); // needs conversion from string to int
      insertionOrderedProperties.put("storeConfiguration.jdbcUser", "user");
      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      assertInstanceOf(DatabaseStorageConfiguration.class, configuration.getStoreConfiguration());
      DatabaseStorageConfiguration dsc = (DatabaseStorageConfiguration) configuration.getStoreConfiguration();
      assertEquals("lmtn", dsc.getLargeMessageTableName());
      assertEquals("mtn", dsc.getMessageTableName());
      assertEquals("btn", dsc.getBindingsTableName());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultDataSourceClassName(), dsc.getDataSourceClassName());
      assertEquals(123, dsc.getJdbcAllowedTimeDiff());
      assertEquals("url", dsc.getJdbcConnectionUrl());
      assertEquals("dcn", dsc.getJdbcDriverClassName());
      assertEquals(456, dsc.getJdbcJournalSyncPeriodMillis());
      assertEquals(789, dsc.getJdbcLockAcquisitionTimeoutMillis());
      assertEquals(321, dsc.getJdbcLockExpirationMillis());
      assertEquals(654, dsc.getJdbcLockRenewPeriodMillis());
      assertEquals(987, dsc.getJdbcNetworkTimeout());
      assertEquals("pass", dsc.getDataSourceProperties().get("password"));
      assertEquals("user", dsc.getJdbcUser());
      assertEquals("nmtn", dsc.getNodeManagerStoreTableName());
      assertEquals("pstn", dsc.getPageStoreTableName());

      // force load to verify dataSourceProperties are applied ok
      JDBCDataSourceUtils.getDataSource(dsc.getDataSourceClassName(), dsc.getDataSourceProperties());
   }

   @Test
   public void testInvalidStoreConfigurationProps() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("storeConfiguration", "File");
      configuration.parsePrefixedProperties(insertionOrderedProperties, null);
      String status = configuration.getStatus();
      //test for exception code
      assertTrue(status.contains("AMQ229249"));
   }

   @Test
   public void testEnumConversion() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      Properties properties = new Properties();
      properties.put("clusterConfigurations.cc.name", "cc");
      properties.put("clusterConfigurations.cc.messageLoadBalancingType", "OFF_WITH_REDISTRIBUTION");
      properties.put("criticalAnalyzerPolicy", "SHUTDOWN");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals("cc", configuration.getClusterConfigurations().get(0).getName());
      assertEquals(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, configuration.getClusterConfigurations().get(0).getMessageLoadBalancingType());
      assertEquals(CriticalAnalyzerPolicy.SHUTDOWN, configuration.getCriticalAnalyzerPolicy());
   }

   @Test
   public void testJsonInsertionOrderedProperties() throws Exception {
      ConfigurationImpl.InsertionOrderedProperties properties =
         new ConfigurationImpl.InsertionOrderedProperties();

      ConfigurationImpl configuration = new ConfigurationImpl();
      JsonObject configJsonObject = buildSimpleConfigJsonObject();
      try (StringReader stringReader = new StringReader(configJsonObject.toString())) {
         properties.loadJson(configuration, stringReader);
      }

      List<String> keys = new ArrayList<>();
      properties.entrySet().forEach(entry -> keys.add((String) entry.getKey()));

      List<String> keysFromJson = configJsonObject.keySet().stream().collect(Collectors.toList());
      // first 4 are root attributes which we can compare by key
      for (int i = 0; i < 4; i++) {
         assertEquals(keysFromJson.get(i), keys.get(i), "index " + i);
      }
   }

   @Test
   public void testJsonNeedsInsertionOrder() throws Exception {

      File tmpFile = File.createTempFile("insertion-order-props-test", ".json", temporaryFolder);
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {

         printWriter.write("{\n");
         printWriter.write("  \"AMQPConnections\" : {\n");
         printWriter.write("    \"target\" : {\n");
         printWriter.write("      \"uri\" : \"tcp://host:6449?trustStorePath=/client.ts\",\n");
         printWriter.write("      \"transportConfigurations\" : {\n");
         printWriter.write("        \"target\" : {\n");
         printWriter.write("          \"params\" : { \"trustStorePassword\" : \"pass\"\n }\n");
         printWriter.write("        }\n");
         printWriter.write("      }\n");
         printWriter.write("    }\n");
         printWriter.write("  }\n");
         printWriter.write("}\n");
      }

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parseProperties(tmpFile.getAbsolutePath());

      String matchNoErrors = "\"errors\":\\[]";
      assertEquals(3, configuration.getStatus().split(matchNoErrors, 10).length, configuration.getStatus());
      assertEquals(4, configuration.getAMQPConnections().get(0).getTransportConfigurations().get(0).getParams().size());
   }

   @Test
   public void testTextPropertiesReaderFromFile() throws Exception {
      List<String> textProperties = buildSimpleConfigTextList();
      File tmpFile = File.createTempFile("text-props-test", "", temporaryFolder);
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {
         for (String textProperty : textProperties) {
            printWriter.println(textProperty);
         }
      }

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parseProperties(tmpFile.getAbsolutePath());

      testSimpleConfig(configuration);
      assertTrue(configuration.getStatus().contains("\"fileAlder32\":\"2235122473\""));
   }

   @Test
   public void testJsonPropertiesReaderFromFile() throws Exception {

      JsonObject configJsonObject = buildSimpleConfigJsonObject();
      File tmpFile = File.createTempFile("json-props-test", ".json", temporaryFolder);
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {
         printWriter.write(configJsonObject.toString());
      }

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parseProperties(tmpFile.getAbsolutePath());

      testSimpleConfig(configuration);
      assertTrue(configuration.getStatus().contains("\"fileAlder32\":\"1145294432\""));
   }

   @Test
   public void testJsonPropertiesKeySurround() throws Exception {

      File tmpFile = File.createTempFile("json-surround-props-test", ".json", temporaryFolder);
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {
         printWriter.write("{\n" +
                              "\"key.surround\": \"$$\",\n" +
                              "\"addressSettings\": {\n" +
                              "\"$$a.\\\"with_quote\\\".b$$\": {\n" +
                                 "\"maxDeliveryAttempts\": 0\n" +
                              "}\n}\n}");
      }

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parseProperties(tmpFile.getAbsolutePath());

      assertEquals(0, configuration.getAddressSettings().get("a.\"with_quote\".b").getMaxDeliveryAttempts());
   }

   @Test
   public void testJsonPropertiesGlobalKeySurround() throws Exception {

      File tmpFile = File.createTempFile("json-global-surround-props-test", ".json", temporaryFolder);
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {
         printWriter.write("{\n" +
                              "\"addressSettings\": {\n" +
                              "\"$$a.\\\"with_quote\\\".b$$\": {\n" +
                              "\"maxDeliveryAttempts\": 0\n" +
                              "}\n}\n}");
      }

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.setBrokerPropertiesKeySurround("$$");
      configuration.parseProperties(tmpFile.getAbsolutePath());

      assertEquals(0, configuration.getAddressSettings().get("a.\"with_quote\".b").getMaxDeliveryAttempts());
   }

   @Test
   public void testJsonPropertiesUserQuoted() throws Exception {

      File tmpFile = File.createTempFile("json-user-quote-props-test", ".json", temporaryFolder);
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {
         printWriter.write("{\n" +
                              "\"addressSettings\": {\n" +
                              "\"a.b.c\": {\n" +
                              "\"maxDeliveryAttempts\": 2\n" +
                              "}\n}\n}");
      }

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parseProperties(tmpFile.getAbsolutePath());

      assertEquals(2, configuration.getAddressSettings().get("a.b.c").getMaxDeliveryAttempts());
   }

   @Test
   public void testInvalidPropertiesReaderFromFile() throws Exception {

      char[] invalidUnicode = {'\\', 'u', '9', '-', '0', 'E'};
      File tmpFile = File.createTempFile("invalid-props-test", ".properties", temporaryFolder);
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {
         printWriter.write(invalidUnicode);
         printWriter.write("\\n");
      }

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parseProperties(tmpFile.getAbsolutePath());

      assertFalse(configuration.getStatus().contains(".properties\":{\"errors\":[]"), configuration.getStatus());

      assertTrue(configuration.getStatus().contains("IllegalArgumentException"));
      assertTrue(configuration.getStatus().contains(tmpFile.getName()));

      // update to sane
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {
         printWriter.write("brokerPropertiesKeySurround=$$");
      }
      configuration.parseProperties(tmpFile.getAbsolutePath());

      assertTrue(configuration.getStatus().contains(".properties\":{\"errors\":[]"), configuration.getStatus());
      assertEquals("$$", configuration.getBrokerPropertiesKeySurround());
   }

   @Test
   public void testInvalidJsonPropertiesReaderFromFile() throws Exception {

      File tmpFile = File.createTempFile("json-props-test", ".json", temporaryFolder);
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {
         printWriter.write("INVALID_JSON");
      }

      ConfigurationImpl configuration = new ConfigurationImpl();

      configuration.parseProperties(tmpFile.getAbsolutePath());
      assertFalse(configuration.getStatus().contains(".json\":{\"errors\":[]"), configuration.getStatus());

      assertTrue(configuration.getStatus().contains("JsonParsingException"));
      assertTrue(configuration.getStatus().contains(tmpFile.getName()));

      // update to sane
      try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
           PrintWriter printWriter = new PrintWriter(fileOutputStream)) {
         printWriter.write("{\"brokerPropertiesKeySurround\": \"$$\"}");
      }
      configuration.parseProperties(tmpFile.getAbsolutePath());
      assertTrue(configuration.getStatus().contains(".json\":{\"errors\":[]"), configuration.getStatus());
      assertEquals("$$", configuration.getBrokerPropertiesKeySurround());
   }

   private JsonObject buildSimpleConfigJsonObject() {
      JsonObjectBuilder configObjectBuilder = JsonLoader.createObjectBuilder();
      {
         configObjectBuilder.add("globalMaxSize", "25K");
         configObjectBuilder.add("gracefulShutdownEnabled", true);
         configObjectBuilder.add("securityEnabled", false);
         configObjectBuilder.add("maxRedeliveryRecords", 123);

         JsonObjectBuilder addressConfigObjectBuilder = JsonLoader.createObjectBuilder();
         {
            JsonObjectBuilder lbaObjectBuilder = JsonLoader.createObjectBuilder();
            {
               JsonObjectBuilder queueConfigBuilder = JsonLoader.createObjectBuilder();
               {
                  JsonObjectBuilder lbqObjectBuilder = JsonLoader.createObjectBuilder();
                  {
                     lbqObjectBuilder.add("routingType", "ANYCAST");
                     lbqObjectBuilder.add("durable", false);
                  }
                  queueConfigBuilder.add("LB.TEST", lbqObjectBuilder.build());

                  JsonObjectBuilder myqObjectBuilder = JsonLoader.createObjectBuilder();
                  {
                     myqObjectBuilder.add("routingType", "ANYCAST");
                     myqObjectBuilder.add("durable", false);
                  }
                  queueConfigBuilder.add("my queue", myqObjectBuilder.build());
               }
               lbaObjectBuilder.add("queueConfigs", queueConfigBuilder.build());
            }
            addressConfigObjectBuilder.add("LB.TEST", lbaObjectBuilder.build());
         }
         configObjectBuilder.add("addressConfigurations", addressConfigObjectBuilder.build());

         JsonObjectBuilder clusterConfigObjectBuilder = JsonLoader.createObjectBuilder();
         {
            JsonObjectBuilder ccObjectBuilder = JsonLoader.createObjectBuilder();
            {
               ccObjectBuilder.add("name", "cc");
               ccObjectBuilder.add("messageLoadBalancingType", "OFF_WITH_REDISTRIBUTION");
            }
            clusterConfigObjectBuilder.add("cc", ccObjectBuilder.build());
         }
         configObjectBuilder.add("clusterConfigurations", clusterConfigObjectBuilder.build());

         configObjectBuilder.add("criticalAnalyzerPolicy", "SHUTDOWN");

         JsonObjectBuilder divertConfigObjectBuilder = JsonLoader.createObjectBuilder();
         {
            JsonObjectBuilder divertObjectBuilder = JsonLoader.createObjectBuilder();
            {
               divertObjectBuilder.add("address", "testAddress");
               divertObjectBuilder.add("forwardingAddress", "forwardAddress");

               JsonObjectBuilder transformerConfigObjectBuilder = JsonLoader.createObjectBuilder();
               {
                  transformerConfigObjectBuilder.add("className", "s.o.m.e.class");

                  JsonObjectBuilder transformerPropertiesObjectBuilder = JsonLoader.createObjectBuilder();
                  {
                     transformerPropertiesObjectBuilder.add("a", "va");
                     transformerPropertiesObjectBuilder.add("b.c", "vbc");
                  }
                  transformerConfigObjectBuilder.add("properties", transformerPropertiesObjectBuilder.build());
               }
               divertObjectBuilder.add("transformerConfiguration", transformerConfigObjectBuilder.build());
            }
            divertConfigObjectBuilder.add("my-divert", divertObjectBuilder.build());
         }
         configObjectBuilder.add("divertConfigurations", divertConfigObjectBuilder.build());
      }

      return configObjectBuilder.build();
   }

   private List<String> buildSimpleConfigTextList() {
      List<String> textProperties = new ArrayList<>();
      textProperties.add("addressConfigurations.\"LB.TEST\".queueConfigs.\"LB.TEST\".routingType=ANYCAST");
      textProperties.add("addressConfigurations.\"LB.TEST\".queueConfigs.\"LB.TEST\".durable=false");
      textProperties.add("addressConfigurations.\"LB.TEST\".queueConfigs.my\\ queue.routingType=ANYCAST");
      textProperties.add("addressConfigurations.\"LB.TEST\".queueConfigs.my\\ queue.durable=false");
      textProperties.add("globalMaxSize=25K");
      textProperties.add("gracefulShutdownEnabled=true");
      textProperties.add("securityEnabled=false");
      textProperties.add("maxRedeliveryRecords=123");
      textProperties.add("clusterConfigurations.cc.name=cc");
      textProperties.add("clusterConfigurations.cc.messageLoadBalancingType=OFF_WITH_REDISTRIBUTION");
      textProperties.add("criticalAnalyzerPolicy=SHUTDOWN");
      textProperties.add("divertConfigurations.my-divert.address=testAddress");
      textProperties.add("divertConfigurations.my-divert.forwardingAddress=forwardAddress");
      textProperties.add("divertConfigurations.my-divert.transformerConfiguration.className=s.o.m.e.class");
      textProperties.add("divertConfigurations.my-divert.transformerConfiguration.properties.a=va");
      textProperties.add("divertConfigurations.my-divert.transformerConfiguration.properties.\"b.c\"=vbc");

      return textProperties;
   }

   private void testSimpleConfig(Configuration configuration) {
      assertEquals(25 * 1024, configuration.getGlobalMaxSize());
      assertTrue(configuration.isGracefulShutdownEnabled());
      assertFalse(configuration.isSecurityEnabled());
      assertEquals(123, configuration.getMaxRedeliveryRecords());

      assertEquals(1, configuration.getAddressConfigurations().size());
      assertEquals(2, configuration.getAddressConfigurations().get(0).getQueueConfigs().size());
      assertEquals(SimpleString.of("LB.TEST"), configuration.getAddressConfigurations().get(0).getQueueConfigs().get(0).getAddress());
      assertEquals(false, configuration.getAddressConfigurations().get(0).getQueueConfigs().get(0).isDurable());
      assertEquals(SimpleString.of("my queue"), configuration.getAddressConfigurations().get(0).getQueueConfigs().get(1).getAddress());
      assertEquals(false, configuration.getAddressConfigurations().get(0).getQueueConfigs().get(1).isDurable());

      assertEquals("cc", configuration.getClusterConfigurations().get(0).getName());
      assertEquals(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, configuration.getClusterConfigurations().get(0).getMessageLoadBalancingType());
      assertEquals(CriticalAnalyzerPolicy.SHUTDOWN, configuration.getCriticalAnalyzerPolicy());

      assertEquals("my-divert", configuration.getDivertConfigurations().get(0).getName());
      assertEquals("testAddress", configuration.getDivertConfigurations().get(0).getAddress());
      assertEquals("forwardAddress", configuration.getDivertConfigurations().get(0).getForwardingAddress());
      assertEquals("s.o.m.e.class", configuration.getDivertConfigurations().get(0).getTransformerConfiguration().getClassName());
      assertEquals("va", configuration.getDivertConfigurations().get(0).getTransformerConfiguration().getProperties().get("a"));
      assertEquals("vbc", configuration.getDivertConfigurations().get(0).getTransformerConfiguration().getProperties().get("b.c"));

      assertTrue(configuration.getStatus().contains("\"alder32"));
      assertTrue(configuration.getStatus().contains("\"fileAlder32"));
   }

   @Test
   public void testPropertiesReaderRespectsOrderFromFile() throws Exception {

      File tmpFile = File.createTempFile("ordered-props-test", "", temporaryFolder);

      FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
      PrintWriter printWriter = new PrintWriter(fileOutputStream);

      LinkedList<String> insertionOrderedKeys = new LinkedList<>();
      char ascii = 'a';
      for (int i = 0; i < 26; i++, ascii++) {
         printWriter.println("resourceLimitSettings." + i + ".maxConnections=100");
         insertionOrderedKeys.addLast(String.valueOf(i));

         printWriter.println("resourceLimitSettings." + ascii + ".maxConnections=100");
         insertionOrderedKeys.addLast(String.valueOf(ascii));
      }
      printWriter.flush();
      fileOutputStream.flush();
      fileOutputStream.close();

      final AtomicReference<String> errorAt = new AtomicReference<>();
      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.setResourceLimitSettings(new HashMap<>() {
         @Override
         public ResourceLimitSettings put(String key, ResourceLimitSettings value) {
            if (!(key.equals(insertionOrderedKeys.remove()))) {
               errorAt.set(key);
               fail("Expected to see props applied in insertion order!, errorAt:" + errorAt.get());
            }
            return super.put(key, value);
         }
      });
      configuration.parseProperties(tmpFile.getAbsolutePath());
      assertNull(errorAt.get(), "no errors in insertion order, errorAt:" + errorAt.get());
   }


   @Test
   public void testPropertiesFiles() throws Exception {

      LinkedList<String> files = new LinkedList<>();
      LinkedList<String> names = new LinkedList<>();
      names.addLast("one");
      names.addLast("two");

      for (String suffix : names) {
         File tmpFile = File.createTempFile("props-test", suffix, temporaryFolder);

         FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
         PrintWriter printWriter = new PrintWriter(fileOutputStream);

         printWriter.println("name=" + suffix);

         printWriter.flush();
         fileOutputStream.flush();
         fileOutputStream.close();

         files.addLast(tmpFile.getAbsolutePath());
      }
      ConfigurationImpl configuration = new ConfigurationImpl() {
         @Override
         public ConfigurationImpl setName(String name) {
            if (!(name.equals(names.remove()))) {
               fail("Expected names from files in order");
            }
            return super.setName(name);
         }
      };
      configuration.parseProperties(files.stream().collect(Collectors.joining(",")));
      assertEquals("two", configuration.getName(), "second won");
   }

   @Test
   public void testPropertiesFilesInDir() throws Exception {

      LinkedList<String> files = new LinkedList<>();
      LinkedList<String> names = new LinkedList<>();
      names.addLast("a_one");
      names.addLast("b_two");

      for (String name : names) {
         File tmpFile = File.createTempFile(name, ".properties", temporaryFolder);

         FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
         PrintWriter printWriter = new PrintWriter(fileOutputStream);

         printWriter.println("name=" + name);

         printWriter.flush();
         fileOutputStream.flush();
         fileOutputStream.close();
         files.addLast(tmpFile.getAbsolutePath());
      }
      ConfigurationImpl configuration = new ConfigurationImpl() {
         @Override
         public ConfigurationImpl setName(String name) {
            if (!(name.equals(names.remove()))) {
               fail("Expected names from files in order");
            }
            return super.setName(name);
         }
      };
      configuration.parseProperties(temporaryFolder + "/");
      assertEquals("b_two", configuration.getName(), "second won");
      assertTrue(names.isEmpty(), "all names applied");
   }

   @Test
   public void testRegxPropertiesFilesInDir() throws Exception {

      File tmpFile = File.createTempFile("a_stuff", ".properties", temporaryFolder);
      Properties properties = new Properties();
      properties.put("name", "a");
      properties.store(new FileOutputStream(tmpFile), null);

      tmpFile =   File.createTempFile("b_stuff", ".0-properties", temporaryFolder);
      properties = new Properties();
      properties.put("name", "0");
      properties.store(new FileOutputStream(tmpFile), null);

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.parseProperties(temporaryFolder + "/");
      assertEquals("a", configuration.getName());

      configuration.parseProperties(temporaryFolder + "/?filter=.*\\.(json|properties|0-properties)");
      assertEquals("0", configuration.getName());
   }

   @Test
   public void testNameWithDotsSurroundWithDollarDollar() throws Throwable {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      // overrides the default surrounding string of '"' with '$$' using a property
      properties.put(ActiveMQDefaultConfiguration.BROKER_PROPERTIES_KEY_SURROUND_PROPERTY, "$$");
      properties.put("addressesSettings.#.expiryAddress", "sharedExpiry");
      properties.put("addressesSettings.NeedToTrackExpired.expiryAddress", "important");
      properties.put("addressesSettings.$$Name.With.Dots$$.expiryAddress", "moreImportant");

      configuration.parsePrefixedProperties(properties, null);

      assertEquals(3, configuration.getAddressSettings().size());
      assertEquals(SimpleString.of("sharedExpiry"), configuration.getAddressSettings().get("#").getExpiryAddress());
      assertEquals(SimpleString.of("important"), configuration.getAddressSettings().get("NeedToTrackExpired").getExpiryAddress());
      assertEquals(SimpleString.of("moreImportant"), configuration.getAddressSettings().get("Name.With.Dots").getExpiryAddress());

      assertTrue(configuration.getStatus().contains("\"errors\":[]"));
   }

   @Test
   public void testStatusOnErrorApplyingProperties() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();

      Properties properties = new Properties();

      properties.put("clusterConfigurations.cc.bonkers", "bla");

      properties.put("notValid.#.expiryAddress", "sharedExpiry");
      properties.put("addressSettings.#.bla", "bla");
      properties.put("addressSettings.#.expiryAddress", "good");

      String SHA = "34311";
      // status field json blob gives possibility for two-way interaction
      // this value is reflected back but can be augmented
      properties.put("status", "{ \"properties\": { \"sha\": \"" + SHA + "\"}}");

      configuration.parsePrefixedProperties(properties, null);

      String jsonStatus = configuration.getStatus();

      // errors reported
      assertTrue(jsonStatus.contains("notValid"));
      assertTrue(jsonStatus.contains("Unknown"));
      assertTrue(jsonStatus.contains("bonkers"));
      assertTrue(jsonStatus.contains("bla"));

      // input status reflected
      assertTrue(jsonStatus.contains(SHA));
      // only errors reported, good property goes unmentioned
      assertFalse(jsonStatus.contains("good"));

      // apply again with only good values, new sha.... verify no errors
      properties.clear();

      String UPDATED_SHA = "66666";
      // status field json blob gives possibility for two-way interaction
      // this value is reflected back but can be augmented
      properties.put("status", "{ \"properties\": { \"sha\": \"" + UPDATED_SHA + "\"}}");
      properties.put("addressSettings.#.expiryAddress", "changed");

      configuration.parsePrefixedProperties(properties, null);

      jsonStatus = configuration.getStatus();

      assertTrue(jsonStatus.contains(UPDATED_SHA));
      assertFalse(jsonStatus.contains(SHA));
      assertTrue(jsonStatus.contains("alder32"));
   }

   @Test
   public void testPlugin() throws Exception {

      final ConfigurationImpl configuration = new ConfigurationImpl();

      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();

      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPlugin.class\".init", "LOG_ALL_EVENTS=true,LOG_SESSION_EVENTS=false");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertEquals(1, configuration.getBrokerPlugins().size());
      assertTrue(((LoggingActiveMQServerPlugin)(configuration.getBrokerPlugins().get(0))).isLogAll());
      assertFalse(((LoggingActiveMQServerPlugin)(configuration.getBrokerPlugins().get(0))).isLogSessionEvents());

      // mimic server initialisePart1
      configuration.registerBrokerPlugins(configuration.getBrokerPlugins());

      assertEquals(1, configuration.getBrokerPlugins().size());
      assertEquals(1, configuration.getBrokerMessagePlugins().size());
      assertEquals(1, configuration.getBrokerConnectionPlugins().size());

      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();

      // change attribute of a plugin without the name attribute, but plugins only registered on start
      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPlugin.class\".init", "LOG_ALL_EVENTS=false");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertEquals(1, configuration.getBrokerPlugins().size());
      assertFalse(((LoggingActiveMQServerPlugin)(configuration.getBrokerPlugins().get(0))).isLogAll());

      // verify error
      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPlugin.class\".init", "LOG_ALL_EVENTS");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertFalse(configuration.getStatus().contains("\"errors\":[]"));
      assertTrue(configuration.getStatus().contains("LOG_ALL_EVENTS"));
   }

   @Test
   public void testPropsAndNamePlugin() throws Exception {

      final ConfigurationImpl configuration = new ConfigurationImpl();

      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();

      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.ConnectionPeriodicExpiryPlugin.class\".periodSeconds", "30");
      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.ConnectionPeriodicExpiryPlugin.class\".acceptorMatchRegex", "netty-.*");
      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.ConnectionPeriodicExpiryPlugin.class\".periodSeconds", "30");
      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.ConnectionPeriodicExpiryPlugin.class\".accuracyWindowSeconds", "10");


      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(1, configuration.getBrokerPlugins().size());
      assertEquals(30, ((ConnectionPeriodicExpiryPlugin)(configuration.getBrokerPlugins().get(0))).getPeriodSeconds());
      assertEquals(10, ((ConnectionPeriodicExpiryPlugin)(configuration.getBrokerPlugins().get(0))).getAccuracyWindowSeconds());
      assertEquals("netty-.*", ((ConnectionPeriodicExpiryPlugin)(configuration.getBrokerPlugins().get(0))).getAcceptorMatchRegex());
   }

   @Test
   public void testMultiplePlugins() throws Exception {
      final ConfigurationImpl configuration = new ConfigurationImpl();

      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();

      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPlugin.class\".init", "LOG_ALL_EVENTS=true");
      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.ConnectionPeriodicExpiryPlugin.class\".periodSeconds", "30");
      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.ConnectionPeriodicExpiryPlugin.class\".accuracyWindowSeconds", "10");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(2, configuration.getBrokerPlugins().size());
      assertTrue(((LoggingActiveMQServerPlugin)(configuration.getBrokerPlugins().get(0))).isLogAll());
      assertEquals(30, ((ConnectionPeriodicExpiryPlugin)(configuration.getBrokerPlugins().get(1))).getPeriodSeconds());
      assertEquals(10, ((ConnectionPeriodicExpiryPlugin)(configuration.getBrokerPlugins().get(1))).getAccuracyWindowSeconds());
   }

   @Test
   public void testConnectionExpiryPluginInit() throws Exception {

      final ConfigurationImpl configuration = new ConfigurationImpl();

      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();

      insertionOrderedProperties.put("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.ConnectionPeriodicExpiryPlugin.class\".init", "acceptorMatchRegex=netty-.*,periodSeconds=30,accuracyWindowSeconds=10");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertEquals(1, configuration.getBrokerPlugins().size());
      assertEquals(30, ((ConnectionPeriodicExpiryPlugin)(configuration.getBrokerPlugins().get(0))).getPeriodSeconds());
      assertEquals(10, ((ConnectionPeriodicExpiryPlugin)(configuration.getBrokerPlugins().get(0))).getAccuracyWindowSeconds());
      assertEquals("netty-.*", ((ConnectionPeriodicExpiryPlugin)(configuration.getBrokerPlugins().get(0))).getAcceptorMatchRegex());
   }

   @Test
   public void testMetricsPluginInit() throws Exception {

      final ConfigurationImpl configuration = new ConfigurationImpl();

      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("metricsConfiguration.plugin", "org.apache.activemq.artemis.core.config.impl.FileConfigurationTest.FakeMetricPlugin.class");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);
      assertFalse(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());
      assertTrue(configuration.getStatus().contains("NotFound"));
      assertTrue(configuration.getStatus().contains("FakeMetricPlugin"));

      // fix the typo error
      insertionOrderedProperties.put("metricsConfiguration.plugin", "org.apache.activemq.artemis.core.config.impl.FileConfigurationTest$FakeMetricPlugin.class");
      insertionOrderedProperties.put("metricsConfiguration.plugin.init", "");
      insertionOrderedProperties.put("metricsConfiguration.jvmMemory", "false");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertTrue(configuration.getStatus().contains("\"errors\":[]"), configuration.getStatus());

      assertNotNull(configuration.getMetricsConfiguration());
      assertNotNull(configuration.getMetricsConfiguration().getPlugin());
      assertNull(configuration.getMetricsConfiguration().getPlugin().getRegistry());
   }

   @Test
   public void testSecuritySettingPluginFromBrokerProperties() throws Exception {

      final ConfigurationImpl configuration = new ConfigurationImpl();

      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("securitySettingPlugins.\"org.apache.activemq.artemis.core.server.impl.LegacyLDAPSecuritySettingPlugin.class\".init", "initialContextFactory=com.sun.jndi.ldap.LdapCtxFactory,connectionURL=ldap://localhost:1024");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertEquals(1, configuration.getSecuritySettingPlugins().size());
      assertEquals("com.sun.jndi.ldap.LdapCtxFactory", ((LegacyLDAPSecuritySettingPlugin)(configuration.getSecuritySettingPlugins().get(0))).getInitialContextFactory());
      assertEquals("ldap://localhost:1024", ((LegacyLDAPSecuritySettingPlugin)(configuration.getSecuritySettingPlugins().get(0))).getConnectionURL());

      assertTrue(configuration.getStatus().contains("\"errors\":[]"));

      insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();

      // change attribute of a plugin without the name attribute, but plugins only registered on start
      insertionOrderedProperties.put("securitySettingPlugins.\"org.apache.activemq.artemis.core.server.impl.LegacyLDAPSecuritySettingPlugin.class\".init", "initialContextFactory=org.eclipse.jetty.jndi.InitialContextFactory");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertEquals(1, configuration.getSecuritySettingPlugins().size());
      assertEquals("org.eclipse.jetty.jndi.InitialContextFactory", ((LegacyLDAPSecuritySettingPlugin)(configuration.getSecuritySettingPlugins().get(0))).getInitialContextFactory());

      // verify error
      insertionOrderedProperties.put("securitySettingPlugins.\"org.apache.activemq.artemis.core.server.impl.LegacyLDAPSecuritySettingPlugin.class\".init", "initialContextFactory");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertFalse(configuration.getStatus().contains("\"errors\":[]"));
      assertTrue(configuration.getStatus().contains("initialContextFactory"));
   }

   @Test
   public void testExportInvalidPropertyOnAcceptor() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();

      // useKQueue here would generate a hashMap Value null, what would break the exportAsProperties.
      configuration.addAcceptorConfiguration("test", "tcp://0.0.0.0:61616?useKQueue");
      File fileOutput = new File(getTestDirfile(), "broker.properties");
      assertDoesNotThrow(() -> configuration.exportAsProperties(fileOutput));
   }

   /**
    * To test ARTEMIS-926
    */
   @Test
   public void testSetSystemPropertyCME() throws Throwable {
      Properties properties = new Properties();

      for (int i = 0; i < 5000; i++) {
         properties.put("key" + i, "value " + i);
      }


      final ConfigurationImpl configuration = new ConfigurationImpl();

      final AtomicBoolean running = new AtomicBoolean(true);
      final CountDownLatch latch = new CountDownLatch(1);


      Thread thread = new Thread(() -> {
         latch.countDown();
         int i = 1;
         while (running.get()) {
            properties.remove("key" + i);
            properties.put("key" + i, "new value " + i);
            i++;
            if (i > 200) {
               i = 1;
            }
         }
      });

      thread.start();
      try {

         latch.await();
         properties.put(configuration.getSystemPropertyPrefix() + "fileDeployerScanPeriod", "1234");
         properties.put(configuration.getSystemPropertyPrefix() + "globalMaxSize", "4321");

         configuration.parsePrefixedProperties(properties, configuration.getSystemPropertyPrefix());


      } finally {
         running.set(false);
         thread.join();
      }

      assertEquals(1234, configuration.getFileDeployerScanPeriod());
      assertEquals(4321, configuration.getGlobalMaxSize());
   }

   @Test
   public void testTypeMatchFluentDescriptor() throws Exception {
      BeanUtilsBean beanUtilsBean = BeanUtilsBean.getInstance();
      beanUtilsBean.getPropertyUtils().addBeanIntrospector(new FluentPropertyBeanIntrospectorWithIgnores());
      PropertyDescriptor propertyDescriptor = beanUtilsBean.getPropertyUtils().getPropertyDescriptor(new DummyConfig(), "aBooleanProperty");

      assertNotNull(propertyDescriptor);
      assertNotNull(propertyDescriptor.getReadMethod());

      propertyDescriptor = beanUtilsBean.getPropertyUtils().getPropertyDescriptor(new DummyConfig(), "IDCacheSize");

      assertNotNull(propertyDescriptor);
      assertNotNull(propertyDescriptor.getReadMethod());
      assertNotNull(propertyDescriptor.getWriteMethod());

      // legacy access to a setter for this
      propertyDescriptor = beanUtilsBean.getPropertyUtils().getPropertyDescriptor(new DummyConfig(), "iDCacheSize");
      assertNotNull(propertyDescriptor);
      assertNotNull(propertyDescriptor.getWriteMethod());
   }

   @Test
   public void testConfigWithMultipleNullDescendantChildren() throws Throwable {
      String dummyPropertyPrefix = "dummy.";
      DummyConfig dummyConfig = new DummyConfig();

      Properties properties = new Properties();
      properties.put(dummyPropertyPrefix + "childConfig.childConfig.childConfig.intProperty", "1");

      Configuration configuration = new ConfigurationImpl();
      configuration.parsePrefixedProperties(dummyConfig, "dummy", properties, dummyPropertyPrefix);

      assertNotNull(dummyConfig.getChildConfig());
      assertNotNull(dummyConfig.getChildConfig().getChildConfig());
      assertNotNull(dummyConfig.getChildConfig().getChildConfig().getChildConfig());
      assertNull(dummyConfig.getChildConfig().getChildConfig().getChildConfig().getChildConfig());
      assertEquals(1, dummyConfig.getChildConfig().getChildConfig().getChildConfig().getIntProperty());
   }

   @Test
   public void testIsClass() throws Exception {
      assertTrue(ConfigurationImpl.isClassProperty("test.class"));
      assertTrue(ConfigurationImpl.isClassProperty("foo.class.bar.class"));
      assertFalse(ConfigurationImpl.isClassProperty("test"));
      assertFalse(ConfigurationImpl.isClassProperty("foo.class.bar"));
   }

   @Test
   public void testExtractPropertyClassName() throws Exception {
      assertEquals("test", ConfigurationImpl.extractPropertyClassName("test.class"));
      assertEquals("foo.class.bar", ConfigurationImpl.extractPropertyClassName("foo.class.bar.class"));
   }

   @Test
   public void testGlobalMaxSizePercentOfJvmMaxMemory() throws Exception {

      // existing default
      long half = Runtime.getRuntime().maxMemory() / 2;
      ConfigurationImpl configuration = new ConfigurationImpl();
      assertEquals(half, configuration.getGlobalMaxSize());

      configuration.setGlobalMaxSizePercentOfJvmMaxMemory(25);
      assertEquals(half, configuration.getGlobalMaxSize());

      // needs new instance
      configuration = new ConfigurationImpl();
      configuration.setGlobalMaxSizePercentOfJvmMaxMemory(25);
      long quarter = Runtime.getRuntime().maxMemory() / 4;
      assertEquals(quarter, configuration.getGlobalMaxSize());
   }

   public static class DummyConfig {
      private int intProperty;
      private int idCacheSize;
      private Boolean aBooleanProperty;

      private DummyConfig childConfig;

      public int getIntProperty() {
         return intProperty;
      }

      public DummyConfig setIntProperty(int intProperty) {
         this.intProperty = intProperty;
         return this;
      }

      public Boolean isABooleanProperty() {
         return aBooleanProperty;
      }

      public DummyConfig setABooleanProperty(Boolean booleanProperty) {
         this.aBooleanProperty = booleanProperty;
         return this;
      }

      public Integer getIDCacheSize() {
         return idCacheSize;
      }

      public DummyConfig setIDCacheSize(Integer idCacheSize) {
         this.idCacheSize = idCacheSize;
         return this;
      }

      public DummyConfig getChildConfig() {
         return childConfig;
      }

      public DummyConfig setChildConfig(DummyConfig childConfig) {
         this.childConfig = childConfig;
         return this;
      }
   }

   private ScheduledExecutorService mockScheduledExecutor() {
      return Mockito.mock(ScheduledExecutorService.class);
   }
}
