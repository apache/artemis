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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQResourceQuotaExceededException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that byte quota is enforced for non-persistent (in-memory) messages.
 *
 */
public class NonPersistentByteQuotaTest extends ActiveMQTestBase {

   @Test
   public void testNonPersistentMessagesRespectByteQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with 1KB byte limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(1024L); // 1KB
      config.addResourceQuota("test-quota", quotaConfig);

      // Configure address settings: no paging, just in-memory
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(-1L); // No local size limit
      settings.setMaxSizeMessages(-1); // No message count limit
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("test.#", settings);
      config.setGlobalMaxSize(-1); // No global size limit

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnNonDurableSend(true); // Required to get exception response for non-persistent sends
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.nonpersistent");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.queue").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         // Send non-persistent messages until quota exceeded
         boolean quotaExceeded = false;
         int messagesSent = 0;
         for (int i = 0; i < 100; i++) {
            try {
               ClientMessage message = session.createMessage(false); // NON-PERSISTENT
               message.getBodyBuffer().writeBytes(new byte[200]); // 200 bytes each
               producer.send(message);
               messagesSent++;
            } catch (ActiveMQResourceQuotaExceededException e) {
               // Expected - quota exceeded
               assertTrue(e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded"),
                  "Expected quota exception but got: " + e.getMessage());
               quotaExceeded = true;
               break;
            } catch (ActiveMQException e) {
               // May get wrapped exception
               if (e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded")) {
                  quotaExceeded = true;
                  break;
               }
               throw e;
            }
         }

         assertTrue(quotaExceeded,
            "Non-persistent messages should respect byte quota even without paging. Sent " + messagesSent + " messages without quota enforcement!");

         // Verify we sent some and not all
         assertTrue(messagesSent >= 1 && messagesSent <= 100,
            "Expected to send some messages before hitting 1KB quota, but sent " + messagesSent);

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testMixedPersistentAndNonPersistentRespectQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with 2KB byte limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("test-quota");
      quotaConfig.setMaxMessageBytes(2048L); // 2KB
      config.addResourceQuota("test-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("test-quota");
      settings.setMaxSizeBytes(-1L);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnNonDurableSend(true); // Required to get exception response for non-persistent sends
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.mixed");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.queue").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         // Send some persistent messages
         for (int i = 0; i < 2; i++) {
            ClientMessage message = session.createMessage(true); // PERSISTENT
            message.getBodyBuffer().writeBytes(new byte[212]);
            producer.send(message);
         }

         // Now try sending non-persistent messages - should also count toward quota
         boolean quotaExceeded = false;
         for (int i = 0; i < 10; i++) {
            try {
               ClientMessage message = session.createMessage(false); // NON-PERSISTENT
               message.getBodyBuffer().writeBytes(new byte[212]);
               producer.send(message);
            } catch (ActiveMQException e) {
               if (e.getMessage().contains("quota") || e.getMessage().contains("Resource quota exceeded") ||
                   e instanceof ActiveMQResourceQuotaExceededException) {
                  quotaExceeded = true;
                  break;
               }
               throw e;
            }
         }

         assertTrue(quotaExceeded,
            "Mixed persistent and non-persistent messages should both count toward quota");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }
}
