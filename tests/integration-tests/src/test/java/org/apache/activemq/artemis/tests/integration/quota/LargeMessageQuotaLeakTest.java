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
package org.apache.activemq.artemis.tests.integration.quota;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.quota.ResourceQuotaService;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LargeMessageQuotaLeakTest extends ActiveMQTestBase {

   private static final int LARGE_MESSAGE_THRESHOLD = 10 * 1024; // 10KB
   private static final int LARGE_MESSAGE_SIZE = 20 * 1024; // 20KB body

   @Test
   public void testLargeMessageQuotaDoesNotLeakOnConsumption() throws Exception {
      Configuration config = createDefaultConfig(true);

      // Create quota with 100KB byte limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("leak-test-quota");
      quotaConfig.setMaxMessageBytes(100 * 1024L);
      config.addResourceQuota("leak-test-quota", quotaConfig);

      // Configure address with quota
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("leak-test-quota");
      settings.setMaxSizeBytes(-1L); // No paging limit
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("leak.#", settings);

      ActiveMQServer server = createServer(true, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setMinLargeMessageSize(LARGE_MESSAGE_THRESHOLD);
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, false, false); // Manual acks and commits

         SimpleString address = SimpleString.of("leak.test");
         SimpleString queueName = SimpleString.of("leak.queue");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(true));

         ResourceQuotaService quotaService = server.getResourceQuotaService();
         ResourceQuota quota = quotaService.getQuotaByName("leak-test-quota");
         assertNotNull(quota);

         // Verify quota starts at zero
         assertEquals(0, quota.getCurrentMessageBytes(), "Quota should start at zero");

         ClientProducer producer = session.createProducer(address);

         // Send 3 large messages
         int numMessages = 3;
         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[LARGE_MESSAGE_SIZE]);
            producer.send(message);
         }
         session.commit();

         // Wait for quota to reflect the sent messages
         Wait.assertTrue(() -> quota.getCurrentMessageBytes() > 0, 2000, 100);

         long quotaAfterSend = quota.getCurrentMessageBytes();
         assertTrue(quotaAfterSend > numMessages * LARGE_MESSAGE_SIZE,
            "Quota should track large message body size. Expected > " + (numMessages * LARGE_MESSAGE_SIZE) +
            " bytes, got " + quotaAfterSend);

         // Now consume and acknowledge all messages
         ClientConsumer consumer = session.createConsumer(queueName);
         session.start();

         for (int i = 0; i < numMessages; i++) {
            ClientMessage received = consumer.receive(5000);
            assertNotNull(received, "Should receive message " + i);
            received.acknowledge();
         }
         session.commit();

         // Wait for quota to be decremented after consumption
         Wait.waitFor(() -> quota.getCurrentMessageBytes() == 0, 5000, 50);

         long quotaAfterConsume = quota.getCurrentMessageBytes();

         // Quota should return to zero after all messages consumed
         // If this fails, it indicates the refUp/refDown asymmetry bug
         assertEquals(0, quotaAfterConsume,
            "QUOTA LEAK DETECTED: Quota should return to zero after consuming all messages. " +
            "This indicates QueueImpl.refDown() is not properly accounting for large message body size. " +
            "Expected: 0, Actual: " + quotaAfterConsume +
            " (leak of ~" + (quotaAfterConsume / numMessages) + " bytes per message)");

         // Additional verification: Send the same messages again to verify quota is truly freed
         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[LARGE_MESSAGE_SIZE]);
            producer.send(message);
         }
         session.commit();

         long quotaAfterSecondSend = quota.getCurrentMessageBytes();

         // If there was a leak, the quota after the second send will be higher than after the first send
         assertTrue(quotaAfterSecondSend <= quotaAfterSend * 1.1,
            "Quota after second send (" + quotaAfterSecondSend + ") should be approximately equal to " +
            "quota after first send (" + quotaAfterSend + "). Higher value indicates accumulated leak.");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }
}
