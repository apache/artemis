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
package org.apache.activemq.artemis.tests.integration.cluster.expiry;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ClusteredExpiryTest extends ClusterTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Queue snfPaused;

   @Test
   public void testExpiryOnSNF() throws Exception {
      setupServer(0, true, true);
      setupServer(1, true, true);

      setupClusterConnection("cluster0", "queues", MessageLoadBalancingType.STRICT, 1, true, 0, 1);

      setupClusterConnection("cluster1", "queues", MessageLoadBalancingType.STRICT, 1, true, 1, 0);

      servers[0].getConfiguration().setMessageExpiryScanPeriod(10);

      startServers(0, 1);

      final String queuesPrefix = "queues.";
      final String queueName = queuesPrefix + getName();
      final String expirySuffix = ".Expiry";
      final String expiryPrefix = "myEXP.";
      final String expiryAddress = "ExpiryAddress";
      final String resultingExpiryQueue = expiryPrefix + queueName + expirySuffix;

      servers[0].getAddressSettingsRepository().clear();
      servers[0].getAddressSettingsRepository().addMatch(queuesPrefix + "#", new AddressSettings().setExpiryQueueSuffix(SimpleString.of(expirySuffix)).setExpiryQueuePrefix(SimpleString.of(expiryPrefix)).setAutoCreateExpiryResources(true).setExpiryAddress(SimpleString.of(expiryAddress)));
      servers[0].getAddressSettingsRepository().addMatch("$#", new AddressSettings().setExpiryQueueSuffix(SimpleString.of(".SNFExpiry")).setAutoCreateExpiryResources(true).setExpiryAddress(SimpleString.of("SNFExpiry")));

      servers[1].getAddressSettingsRepository().clear();
      servers[1].getAddressSettingsRepository().addMatch(queuesPrefix + "#", new AddressSettings().setExpiryQueueSuffix(SimpleString.of(expirySuffix)).setExpiryQueuePrefix(SimpleString.of(expiryPrefix)).setAutoCreateExpiryResources(true).setExpiryAddress(SimpleString.of(expiryAddress)));
      servers[1].getAddressSettingsRepository().addMatch("$#", new AddressSettings().setExpiryQueueSuffix(SimpleString.of(".SNFExpiry")).setAutoCreateExpiryResources(true).setExpiryAddress(SimpleString.of("SNFExpiry")));

      Queue serverQueue0 = servers[0].createQueue(QueueConfiguration.of(queueName).setDurable(true).setRoutingType(RoutingType.ANYCAST));
      servers[1].createQueue(QueueConfiguration.of(queueName).setDurable(true).setRoutingType(RoutingType.ANYCAST));

      waitForBindings(0, queueName, 1, 0, true);
      waitForBindings(1, queueName, 1, 0, true);

      waitForBindings(0, queueName, 1, 0, false);
      waitForBindings(1, queueName, 1, 0, false);

      // pausing the SNF queue to keep messages stuck on the queue
      servers[0].getPostOffice().getAllBindings().filter(f -> f.getUniqueName().toString().startsWith("$.artemis.internal.sf")).forEach(this::pauseQueue);
      assertNotNull(snfPaused);

      long NUMBER_OF_MESSAGES = 100;

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session1 = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session1.createProducer(session1.createQueue(queueName));
         producer.setTimeToLive(100);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.send(session1.createTextMessage("hello"));
         }
         session1.commit();
      }
      Wait.assertEquals(0L, serverQueue0::getMessageCount, 5_000, 100);
      Wait.assertEquals(0L, snfPaused::getMessageCount, 5_000, 100);
      Queue expiryQueue = servers[0].locateQueue(expiryAddress, resultingExpiryQueue);
      assertNotNull(expiryQueue);
      Wait.assertEquals(NUMBER_OF_MESSAGES, expiryQueue::getMessageCount, 5_000, 100);

   }

   private void pauseQueue(Binding binding) {
      assertNull(snfPaused);
      if (binding instanceof LocalQueueBinding localQueueBinding) {
         logger.info("Pausing {}", binding.getUniqueName());
         snfPaused = localQueueBinding.getQueue();
         snfPaused.pause();
      }
   }

}