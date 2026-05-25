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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.config.routing.ConnectionRouterConfiguration;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.core.server.routing.policies.ConsistentHashModuloPolicy;
import org.apache.activemq.artemis.core.server.routing.policies.ConsistentHashPolicy;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractConfigurationTestBase extends ServerTestBase {

   protected Configuration conf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      conf = createConfiguration();
   }

   protected abstract Configuration createConfiguration() throws Exception;

   // Utility method, not itself a test; call it from actual test method
   public static void doSetGetAttributesTestImpl(Configuration configuration) throws Exception {
      for (int j = 0; j < 100; j++) {
         int i = RandomUtil.randomInt();
         configuration.setScheduledThreadPoolMaxSize(i);
         assertEquals(i, configuration.getScheduledThreadPoolMaxSize());

         long l = RandomUtil.randomLong();
         configuration.setSecurityInvalidationInterval(l);
         assertEquals(l, configuration.getSecurityInvalidationInterval());

         boolean b = RandomUtil.randomBoolean();
         configuration.setSecurityEnabled(b);
         assertEquals(b, configuration.isSecurityEnabled());

         String s = RandomUtil.randomUUIDString();
         configuration.setBindingsDirectory(s);
         assertEquals(s, configuration.getBindingsDirectory());

         b = RandomUtil.randomBoolean();
         configuration.setCreateBindingsDir(b);
         assertEquals(b, configuration.isCreateBindingsDir());

         s = RandomUtil.randomUUIDString();
         configuration.setJournalDirectory(s);
         assertEquals(s, configuration.getJournalDirectory());

         b = RandomUtil.randomBoolean();
         configuration.setCreateJournalDir(b);
         assertEquals(b, configuration.isCreateJournalDir());

         i = RandomUtil.randomInt() % 2;
         JournalType journal = i == 0 ? JournalType.ASYNCIO : JournalType.NIO;
         configuration.setJournalType(journal);
         assertEquals(journal, configuration.getJournalType());

         b = RandomUtil.randomBoolean();
         configuration.setJournalSyncTransactional(b);
         assertEquals(b, configuration.isJournalSyncTransactional());

         b = RandomUtil.randomBoolean();
         configuration.setJournalSyncNonTransactional(b);
         assertEquals(b, configuration.isJournalSyncNonTransactional());

         i = RandomUtil.randomInt();
         configuration.setJournalFileSize(i);
         assertEquals(i, configuration.getJournalFileSize());

         i = RandomUtil.randomInt();
         configuration.setJournalMinFiles(i);
         assertEquals(i, configuration.getJournalMinFiles());

         i = RandomUtil.randomInt();
         configuration.setJournalMaxIO_AIO(i);
         assertEquals(i, configuration.getJournalMaxIO_AIO());

         i = RandomUtil.randomInt();
         configuration.setJournalMaxIO_NIO(i);
         assertEquals(i, configuration.getJournalMaxIO_NIO());

         s = RandomUtil.randomUUIDString();
         configuration.setManagementAddress(SimpleString.of(s));
         assertEquals(s, configuration.getManagementAddress().toString());

         l = RandomUtil.randomLong();
         configuration.setMessageExpiryScanPeriod(l);
         assertEquals(l, configuration.getMessageExpiryScanPeriod());

         b = RandomUtil.randomBoolean();
         configuration.setPersistDeliveryCountBeforeDelivery(b);
         assertEquals(b, configuration.isPersistDeliveryCountBeforeDelivery());

         b = RandomUtil.randomBoolean();
         configuration.setEnabledAsyncConnectionExecution(b);
         assertEquals(b, configuration.isAsyncConnectionExecutionEnabled());

         b = RandomUtil.randomBoolean();
         configuration.setPersistenceEnabled(b);
         assertEquals(b, configuration.isPersistenceEnabled());

         b = RandomUtil.randomBoolean();
         configuration.setJMXManagementEnabled(b);
         assertEquals(b, configuration.isJMXManagementEnabled());

         l = RandomUtil.randomLong();
         configuration.setFileDeployerScanPeriod(l);
         assertEquals(l, configuration.getFileDeployerScanPeriod());

         l = RandomUtil.randomLong();
         configuration.setConnectionTTLOverride(l);
         assertEquals(l, configuration.getConnectionTTLOverride());

         i = RandomUtil.randomInt();
         configuration.setThreadPoolMaxSize(i);
         assertEquals(i, configuration.getThreadPoolMaxSize());

         SimpleString ss = RandomUtil.randomUUIDSimpleString();
         configuration.setManagementNotificationAddress(ss);
         assertEquals(ss, configuration.getManagementNotificationAddress());

         s = RandomUtil.randomUUIDString();
         configuration.setClusterUser(s);
         assertEquals(s, configuration.getClusterUser());

         i = RandomUtil.randomInt();
         configuration.setIDCacheSize(i);
         assertEquals(i, configuration.getIDCacheSize());

         b = RandomUtil.randomBoolean();
         configuration.setPersistIDCache(b);
         assertEquals(b, configuration.isPersistIDCache());

         i = RandomUtil.randomInt();
         configuration.setJournalCompactMinFiles(i);
         assertEquals(i, configuration.getJournalCompactMinFiles());

         i = RandomUtil.randomInt();
         configuration.setJournalCompactPercentage(i);
         assertEquals(i, configuration.getJournalCompactPercentage());

         l = RandomUtil.randomLong();
         configuration.setJournalLockAcquisitionTimeout(l);
         assertEquals(l, configuration.getJournalLockAcquisitionTimeout());

         i = RandomUtil.randomInt();
         configuration.setJournalBufferSize_AIO(i);
         assertEquals(i, configuration.getJournalBufferSize_AIO());

         i = RandomUtil.randomInt();
         configuration.setJournalBufferTimeout_AIO(i);
         assertEquals(i, configuration.getJournalBufferTimeout_AIO());

         i = RandomUtil.randomInt();
         configuration.setJournalBufferSize_NIO(i);
         assertEquals(i, configuration.getJournalBufferSize_NIO());

         i = RandomUtil.randomInt();
         configuration.setJournalBufferTimeout_NIO(i);
         assertEquals(i, configuration.getJournalBufferTimeout_NIO());

         b = RandomUtil.randomBoolean();
         configuration.setLogJournalWriteRate(b);
         assertEquals(b, configuration.isLogJournalWriteRate());

         l = RandomUtil.randomLong();
         configuration.setServerDumpInterval(l);
         assertEquals(l, configuration.getServerDumpInterval());

         s = RandomUtil.randomUUIDString();
         configuration.setPagingDirectory(s);
         assertEquals(s, configuration.getPagingDirectory());

         s = RandomUtil.randomUUIDString();
         configuration.setLargeMessagesDirectory(s);
         assertEquals(s, configuration.getLargeMessagesDirectory());

         l = RandomUtil.randomLong();
         configuration.setTransactionTimeout(l);
         assertEquals(l, configuration.getTransactionTimeout());

         b = RandomUtil.randomBoolean();
         configuration.setMessageCounterEnabled(b);
         assertEquals(b, configuration.isMessageCounterEnabled());

         l = RandomUtil.randomPositiveLong();
         configuration.setMessageCounterSamplePeriod(l);
         assertEquals(l, configuration.getMessageCounterSamplePeriod());

         i = RandomUtil.randomInt();
         configuration.setMessageCounterMaxDayHistory(i);
         assertEquals(i, configuration.getMessageCounterMaxDayHistory());

         l = RandomUtil.randomLong();
         configuration.setTransactionTimeoutScanPeriod(l);
         assertEquals(l, configuration.getTransactionTimeoutScanPeriod());

         s = RandomUtil.randomUUIDString();
         configuration.setClusterPassword(s);
         assertEquals(s, configuration.getClusterPassword());

         i = RandomUtil.randomInt();
         configuration.setPageSyncTimeout(i);
         assertEquals(i, configuration.getPageSyncTimeout());
      }
   }

   // Utility method, not itself a test; call it from actual test method
   public static void doGetSetInterceptorsTestImpl(Configuration configuration) {
      final String name1 = "uqwyuqywuy";
      final String name2 = "yugyugyguyg";

      configuration.getIncomingInterceptorClassNames().add(name1);
      configuration.getIncomingInterceptorClassNames().add(name2);

      assertTrue(configuration.getIncomingInterceptorClassNames().contains(name1));
      assertTrue(configuration.getIncomingInterceptorClassNames().contains(name2));
      assertFalse(configuration.getIncomingInterceptorClassNames().contains("iijij"));
   }

   // Utility method, not itself a test; call it from actual test method
   public static void doSerializeTestImpl(Configuration configuration) throws Exception {
      boolean b = RandomUtil.randomBoolean();

      configuration.setHAPolicyConfiguration(new PrimaryOnlyPolicyConfiguration());

      int i = RandomUtil.randomInt();
      configuration.setScheduledThreadPoolMaxSize(i);
      assertEquals(i, configuration.getScheduledThreadPoolMaxSize());

      long l = RandomUtil.randomLong();
      configuration.setSecurityInvalidationInterval(l);
      assertEquals(l, configuration.getSecurityInvalidationInterval());

      b = RandomUtil.randomBoolean();
      configuration.setSecurityEnabled(b);
      assertEquals(b, configuration.isSecurityEnabled());

      String s = RandomUtil.randomUUIDString();
      configuration.setBindingsDirectory(s);
      assertEquals(s, configuration.getBindingsDirectory());

      b = RandomUtil.randomBoolean();
      configuration.setCreateBindingsDir(b);
      assertEquals(b, configuration.isCreateBindingsDir());

      s = RandomUtil.randomUUIDString();
      configuration.setJournalDirectory(s);
      assertEquals(s, configuration.getJournalDirectory());

      b = RandomUtil.randomBoolean();
      configuration.setCreateJournalDir(b);
      assertEquals(b, configuration.isCreateJournalDir());

      i = RandomUtil.randomInt() % 2;
      JournalType journal = i == 0 ? JournalType.ASYNCIO : JournalType.NIO;
      configuration.setJournalType(journal);
      assertEquals(journal, configuration.getJournalType());

      b = RandomUtil.randomBoolean();
      configuration.setJournalSyncTransactional(b);
      assertEquals(b, configuration.isJournalSyncTransactional());

      b = RandomUtil.randomBoolean();
      configuration.setJournalSyncNonTransactional(b);
      assertEquals(b, configuration.isJournalSyncNonTransactional());

      i = RandomUtil.randomInt();
      configuration.setJournalFileSize(i);
      assertEquals(i, configuration.getJournalFileSize());

      i = RandomUtil.randomInt();
      configuration.setJournalMinFiles(i);
      assertEquals(i, configuration.getJournalMinFiles());

      i = RandomUtil.randomInt();
      configuration.setJournalMaxIO_AIO(i);
      assertEquals(i, configuration.getJournalMaxIO_AIO());

      i = RandomUtil.randomInt();
      configuration.setJournalMaxIO_NIO(i);
      assertEquals(i, configuration.getJournalMaxIO_NIO());

      s = RandomUtil.randomUUIDString();
      configuration.setManagementAddress(SimpleString.of(s));
      assertEquals(s, configuration.getManagementAddress().toString());

      l = RandomUtil.randomLong();
      configuration.setMessageExpiryScanPeriod(l);
      assertEquals(l, configuration.getMessageExpiryScanPeriod());

      b = RandomUtil.randomBoolean();
      configuration.setPersistDeliveryCountBeforeDelivery(b);
      assertEquals(b, configuration.isPersistDeliveryCountBeforeDelivery());

      b = RandomUtil.randomBoolean();
      configuration.setEnabledAsyncConnectionExecution(b);
      assertEquals(b, configuration.isAsyncConnectionExecutionEnabled());

      b = RandomUtil.randomBoolean();
      configuration.setPersistenceEnabled(b);
      assertEquals(b, configuration.isPersistenceEnabled());

      b = RandomUtil.randomBoolean();
      configuration.setJMXManagementEnabled(b);
      assertEquals(b, configuration.isJMXManagementEnabled());

      l = RandomUtil.randomLong();
      configuration.setFileDeployerScanPeriod(l);
      assertEquals(l, configuration.getFileDeployerScanPeriod());

      l = RandomUtil.randomLong();
      configuration.setConnectionTTLOverride(l);
      assertEquals(l, configuration.getConnectionTTLOverride());

      i = RandomUtil.randomInt();
      configuration.setThreadPoolMaxSize(i);
      assertEquals(i, configuration.getThreadPoolMaxSize());

      SimpleString ss = RandomUtil.randomUUIDSimpleString();
      configuration.setManagementNotificationAddress(ss);
      assertEquals(ss, configuration.getManagementNotificationAddress());

      s = RandomUtil.randomUUIDString();
      configuration.setClusterUser(s);
      assertEquals(s, configuration.getClusterUser());

      i = RandomUtil.randomInt();
      configuration.setIDCacheSize(i);
      assertEquals(i, configuration.getIDCacheSize());

      b = RandomUtil.randomBoolean();
      configuration.setPersistIDCache(b);
      assertEquals(b, configuration.isPersistIDCache());

      i = RandomUtil.randomInt();
      configuration.setJournalCompactMinFiles(i);
      assertEquals(i, configuration.getJournalCompactMinFiles());

      i = RandomUtil.randomInt();
      configuration.setJournalCompactPercentage(i);
      assertEquals(i, configuration.getJournalCompactPercentage());

      i = RandomUtil.randomInt();
      configuration.setJournalBufferSize_AIO(i);
      assertEquals(i, configuration.getJournalBufferSize_AIO());

      i = RandomUtil.randomInt();
      configuration.setJournalBufferTimeout_AIO(i);
      assertEquals(i, configuration.getJournalBufferTimeout_AIO());

      i = RandomUtil.randomInt();
      configuration.setJournalBufferSize_NIO(i);
      assertEquals(i, configuration.getJournalBufferSize_NIO());

      i = RandomUtil.randomInt();
      configuration.setJournalBufferTimeout_NIO(i);
      assertEquals(i, configuration.getJournalBufferTimeout_NIO());

      b = RandomUtil.randomBoolean();
      configuration.setLogJournalWriteRate(b);
      assertEquals(b, configuration.isLogJournalWriteRate());

      l = RandomUtil.randomLong();
      configuration.setServerDumpInterval(l);
      assertEquals(l, configuration.getServerDumpInterval());

      s = RandomUtil.randomUUIDString();
      configuration.setPagingDirectory(s);
      assertEquals(s, configuration.getPagingDirectory());

      s = RandomUtil.randomUUIDString();
      configuration.setLargeMessagesDirectory(s);
      assertEquals(s, configuration.getLargeMessagesDirectory());

      b = RandomUtil.randomBoolean();
      configuration.setWildcardRoutingEnabled(b);
      assertEquals(b, configuration.isWildcardRoutingEnabled());

      l = RandomUtil.randomLong();
      configuration.setTransactionTimeout(l);
      assertEquals(l, configuration.getTransactionTimeout());

      b = RandomUtil.randomBoolean();
      configuration.setMessageCounterEnabled(b);
      assertEquals(b, configuration.isMessageCounterEnabled());

      l = RandomUtil.randomPositiveLong();
      configuration.setMessageCounterSamplePeriod(l);
      assertEquals(l, configuration.getMessageCounterSamplePeriod());

      i = RandomUtil.randomInt();
      configuration.setMessageCounterMaxDayHistory(i);
      assertEquals(i, configuration.getMessageCounterMaxDayHistory());

      l = RandomUtil.randomLong();
      configuration.setTransactionTimeoutScanPeriod(l);
      assertEquals(l, configuration.getTransactionTimeoutScanPeriod());

      s = RandomUtil.randomUUIDString();
      configuration.setClusterPassword(s);
      assertEquals(s, configuration.getClusterPassword());

      i = RandomUtil.randomInt();
      configuration.setPageSyncTimeout(i);
      assertEquals(i, configuration.getPageSyncTimeout());

      configuration.registerBrokerPlugin(new LoggingActiveMQServerPlugin());
      assertEquals(1, configuration.getBrokerPlugins().size(), "ensure one plugin registered");
      assertEquals(1, configuration.getBrokerConnectionPlugins().size(), "ensure one connection plugin registered");


      // This will use serialization to perform a deep copy of the object
      Configuration conf2 = configuration.copy();

      assertEquals(configuration, conf2);
   }

   // Utility method, not itself a test; call it from actual test method
   public static void doSetConnectionRoutersPolicyConfigurationTestImpl(ConfigurationImpl configuration) throws Throwable {
      Properties insertionOrderedProperties = new ConfigurationImpl.InsertionOrderedProperties();
      insertionOrderedProperties.put("connectionRouters.autoShard.localTargetFilter", "NULL|$STATEFUL_SET_ORDINAL");
      insertionOrderedProperties.put("connectionRouters.autoShard.keyType", KeyType.CLIENT_ID);
      insertionOrderedProperties.put("connectionRouters.autoShard.policyConfiguration", ConsistentHashModuloPolicy.NAME);
      insertionOrderedProperties.put("connectionRouters.autoShard.policyConfiguration.properties." + ConsistentHashModuloPolicy.MODULO, 2);

      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.keyType", KeyType.CLIENT_ID);
      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.keyFilter", "^.{3}");
      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.policyConfiguration", ConsistentHashPolicy.NAME);
      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.poolConfiguration.username", "guest-username");
      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.poolConfiguration.password", "guest-password");
      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.poolConfiguration.quorumSize", "2");
      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.poolConfiguration.localTargetEnabled", Boolean.TRUE.toString());
      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.poolConfiguration.discoveryGroupName", "discovery-group-1");
      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.cacheConfiguration.persisted", Boolean.TRUE.toString());
      insertionOrderedProperties.put("connectionRouters.symmetricRedirect.cacheConfiguration.timeout", "1234");

      configuration.parsePrefixedProperties(insertionOrderedProperties, null);

      assertEquals(2, configuration.getConnectionRouters().size());

      ConnectionRouterConfiguration autoShardConfig = configuration.getConnectionRouters().stream().filter(
         connectionRouterConfig -> "autoShard".equals(connectionRouterConfig.getName())).findFirst().get();
      assertEquals(KeyType.CLIENT_ID, autoShardConfig.getKeyType());
      assertEquals("2", autoShardConfig.getPolicyConfiguration().getProperties().get(ConsistentHashModuloPolicy.MODULO));
      assertNull(autoShardConfig.getCacheConfiguration());
      assertNull(autoShardConfig.getPoolConfiguration());

      ConnectionRouterConfiguration symmetricRedirectConfig = configuration.getConnectionRouters().stream().filter(
         connectionRouterConfig -> "symmetricRedirect".equals(connectionRouterConfig.getName())).findFirst().get();
      assertEquals(KeyType.CLIENT_ID, symmetricRedirectConfig.getKeyType());
      assertEquals("^.{3}", symmetricRedirectConfig.getKeyFilter());
      assertEquals(ConsistentHashPolicy.NAME, symmetricRedirectConfig.getPolicyConfiguration().getName());
      assertNotNull(symmetricRedirectConfig.getPoolConfiguration());
      assertEquals("guest-username", symmetricRedirectConfig.getPoolConfiguration().getUsername());
      assertEquals("guest-password", symmetricRedirectConfig.getPoolConfiguration().getPassword());
      assertEquals(2, symmetricRedirectConfig.getPoolConfiguration().getQuorumSize());
      assertEquals(Boolean.TRUE, symmetricRedirectConfig.getPoolConfiguration().isLocalTargetEnabled());
      assertEquals("discovery-group-1", symmetricRedirectConfig.getPoolConfiguration().getDiscoveryGroupName());
      assertNotNull(symmetricRedirectConfig.getCacheConfiguration());
      assertEquals(Boolean.TRUE, symmetricRedirectConfig.getCacheConfiguration().isPersisted());
      assertEquals(1234, symmetricRedirectConfig.getCacheConfiguration().getTimeout());
   }
}
