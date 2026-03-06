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

import java.util.Collections;

import org.apache.activemq.artemis.ArtemisConstants;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DefaultsFileConfigurationTest extends AbstractConfigurationTestBase {

   @Override
   protected Configuration createConfiguration() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("ConfigurationTest-defaults.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      return fc;
   }

   @Test
   public void testDefaults() {
      assertEquals(ActiveMQDefaultConfiguration.getDefaultScheduledThreadPoolMaxSize(), conf.getScheduledThreadPoolMaxSize());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultThreadPoolMaxSize(), conf.getThreadPoolMaxSize());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultSecurityInvalidationInterval(), conf.getSecurityInvalidationInterval());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultSecurityEnabled(), conf.isSecurityEnabled());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultJmxManagementEnabled(), conf.isJMXManagementEnabled());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultJmxNotificationEnabled(), conf.isJMXNotificationEnabled());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), conf.getJMXDomain());

      assertEquals(0, conf.getIncomingInterceptorClassNames().size());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultConnectionTtlOverride(), conf.getConnectionTTLOverride());

      assertEquals(0, conf.getAcceptorConfigurations().size());

      assertEquals(Collections.emptyMap(), conf.getConnectorConfigurations());

      assertEquals(Collections.emptyList(), conf.getBroadcastGroupConfigurations());

      assertEquals(Collections.emptyMap(), conf.getDiscoveryGroupConfigurations());

      assertEquals(Collections.emptyList(), conf.getBridgeConfigurations());

      assertEquals(Collections.emptyList(), conf.getDivertConfigurations());

      assertEquals(Collections.emptyList(), conf.getClusterConfigurations());

      assertEquals(Collections.emptyList(), conf.getQueueConfigs());

      assertEquals(Collections.emptyList(), conf.getAddressConfigurations());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultManagementAddress(), conf.getManagementAddress());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress(), conf.getManagementNotificationAddress());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterUser(), conf.getClusterUser());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterPassword(), conf.getClusterPassword());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultIdCacheSize(), conf.getIDCacheSize());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultPersistIdCache(), conf.isPersistIDCache());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultBindingsDirectory(), conf.getBindingsDirectory());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalDir(), conf.getJournalDirectory());

      assertEquals(getDefaultJournalType(), conf.getJournalType());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultJournalSyncTransactional(), conf.isJournalSyncTransactional());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultJournalSyncNonTransactional(), conf.isJournalSyncNonTransactional());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalFileSize(), conf.getJournalFileSize());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalMaxAtticFiles(), conf.getJournalMaxAtticFiles());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalCompactMinFiles(), conf.getJournalCompactMinFiles());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalCompactPercentage(), conf.getJournalCompactPercentage());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalLockAcquisitionTimeout(), conf.getJournalLockAcquisitionTimeout());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalMinFiles(), conf.getJournalMinFiles());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalMaxIoAio(), conf.getJournalMaxIO_AIO());

      assertEquals(ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_AIO, conf.getJournalBufferTimeout_AIO());

      assertEquals(ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_AIO, conf.getJournalBufferSize_AIO());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalMaxIoNio(), conf.getJournalMaxIO_NIO());

      assertEquals(ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_NIO, conf.getJournalBufferTimeout_NIO());

      assertEquals(ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_NIO, conf.getJournalBufferSize_NIO());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultCreateBindingsDir(), conf.isCreateBindingsDir());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultCreateJournalDir(), conf.isCreateJournalDir());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultPagingDir(), conf.getPagingDirectory());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultLargeMessagesDir(), conf.getLargeMessagesDirectory());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultWildcardRoutingEnabled(), conf.isWildcardRoutingEnabled());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultTransactionTimeout(), conf.getTransactionTimeout());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultMessageCounterEnabled(), conf.isMessageCounterEnabled());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultTransactionTimeoutScanPeriod(), conf.getTransactionTimeoutScanPeriod());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultMessageExpiryScanPeriod(), conf.getMessageExpiryScanPeriod());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultAddressQueueScanPeriod(), conf.getAddressQueueScanPeriod());

      assertInstanceOf(PrimaryOnlyPolicyConfiguration.class, conf.getHAPolicyConfiguration());

      assertEquals(ActiveMQDefaultConfiguration.isDefaultGracefulShutdownEnabled(), conf.isGracefulShutdownEnabled());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultGracefulShutdownTimeout(), conf.getGracefulShutdownTimeout());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultAmqpUseCoreSubscriptionNaming(), conf.isAmqpUseCoreSubscriptionNaming());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultJournalBufferTimeoutNio(), conf.getPageSyncTimeout());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultFileDescriptorsMetrics(), conf.getMetricsConfiguration().isFileDescriptors());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultProcessorMetrics(), conf.getMetricsConfiguration().isProcessor());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultUptimeMetrics(), conf.getMetricsConfiguration().isUptime());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultLoggingMetrics(), conf.getMetricsConfiguration().isLogging());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultSecurityCacheMetrics(), conf.getMetricsConfiguration().isSecurityCaches());

      assertEquals(ActiveMQDefaultConfiguration.getDefaultExecutorServiceMetrics(), conf.getMetricsConfiguration().isExecutorServices());
   }
}
