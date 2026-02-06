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
package org.apache.activemq.artemis.tests.integration.server;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.metrics.plugins.SimpleMetricsPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.SingleServerTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class UuidNamespaceTest extends SingleServerTestBase {

   @Override
   protected ActiveMQServer createServer() throws Exception {
      ActiveMQServer server = super.createServer();
      server.getConfiguration().setMetricsConfiguration(new MetricsConfiguration().setJvmMemory(false).setPlugin(new SimpleMetricsPlugin().init(null)));
      return server;
   }

   @Test
   public void testUuidNamespace() throws Exception {
      testUuidNamespace(false);
   }

   @Test
   public void testTempQueueNamespace() throws Exception {
      testUuidNamespace(true);
   }

   private void testUuidNamespace(boolean legacy) throws Exception {
      final String UUID_NAMESPACE = "uuid";
      final SimpleString DLA = RandomUtil.randomUUIDSimpleString();
      final SimpleString EA = RandomUtil.randomUUIDSimpleString();
      final int RING_SIZE = 10;

      if (legacy) {
         server.getConfiguration().setTemporaryQueueNamespace(UUID_NAMESPACE);
      } else {
         server.getConfiguration().setUuidNamespace(UUID_NAMESPACE);
      }
      server.getAddressSettingsRepository().addMatch(UUID_NAMESPACE + ".#", new AddressSettings().setDefaultRingSize(RING_SIZE).setDeadLetterAddress(DLA).setExpiryAddress(EA));
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false).setTemporary(true));

      QueueControl queueControl = server.getManagementService().getQueueControl(queue.toString());
      assertEquals(RING_SIZE, queueControl.getRingSize());
      assertEquals(DLA.toString(), queueControl.getDeadLetterAddress());
      assertEquals(EA.toString(), queueControl.getExpiryAddress());

      session.close();
   }

   @Test
   public void testUuidNamespaceNegative() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false).setTemporary(true));

      assertNotEquals(10, (long) server.locateQueue(queue).getQueueConfiguration().getRingSize());

      session.close();
   }

   @Test
   public void testMetricsEnabled() throws Exception {
      testMetrics(true);
   }

   @Test
   public void testMetricsDisabled() throws Exception {
      testMetrics(false);
   }

   private void testMetrics(boolean enabled) throws Exception {
      final String UUID_NAMESPACE = "uuid";
      server.getConfiguration().setUuidNamespace(UUID_NAMESPACE);
      server.getAddressSettingsRepository().addMatch(UUID_NAMESPACE + ".#", new AddressSettings().setEnableMetrics(enabled));
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setEnableMetrics(false));

      AtomicBoolean testUUID = new AtomicBoolean(false);
      server.getMetricsManager().registerQueueGauge(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), (builder) -> {
         testUUID.set(true);
      });
      assertEquals(enabled, testUUID.get());

      AtomicBoolean testNormalAlwaysDisabled = new AtomicBoolean(false);
      server.getMetricsManager().registerQueueGauge(getName(), getName(), (builder) -> {
         // this should not be executed since metrics are disabled for "#"
         testNormalAlwaysDisabled.set(true);
      });
      assertFalse(testNormalAlwaysDisabled.get());
   }

   @Test
   public void testUuidNamespaceWithoutUuid() throws Exception {
      final String UUID_NAMESPACE = "uuid";
      final String QUEUE_NAME = getName();
      final SimpleString EXPECTED_KEY_NAME = SimpleString.of("key");

      server.getConfiguration().setUuidNamespace(UUID_NAMESPACE);
      server.getAddressSettingsRepository().addMatch(QUEUE_NAME, new AddressSettings().setDefaultLastValueKey(EXPECTED_KEY_NAME));
      server.getAddressSettingsRepository().addMatch(UUID_NAMESPACE + ".#", new AddressSettings().setDefaultLastValueKey(SimpleString.of("uuidKey")));
      session.createQueue(QueueConfiguration.of(QUEUE_NAME).setTemporary(true).setDurable(false));

      assertEquals(EXPECTED_KEY_NAME, server.locateQueue(QUEUE_NAME).getLastValueKey());
   }
}
