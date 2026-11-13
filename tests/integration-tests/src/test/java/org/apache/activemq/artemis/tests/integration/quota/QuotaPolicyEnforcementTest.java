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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for quota enforcement with address full policies (BLOCK/FAIL/PAGE)
 * Quota is independent
 */
public class QuotaPolicyEnforcementTest extends ActiveMQTestBase {

   /**
    * Test that FAIL policy enforces quota when quota is the ONLY limit configured.
    * evaluated to false when only quota was set, bypassing enforcement.
    */
   @Test
   public void testFAILPolicyWithQuotaOnly() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with 1KB byte limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("fail-quota");
      quotaConfig.setMaxMessageBytes(1024L); // 1KB
      config.addResourceQuota("fail-quota", quotaConfig);

      // Configure address settings: quota only, no maxSize
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("fail-quota");
      settings.setMaxSizeBytes(-1L); // No local size limit
      settings.setMaxSizeMessages(-1); // No message count limit
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("test.#", settings);
      config.setGlobalMaxSize(-1); // No global size limit

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.fail");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.fail").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         assertThrows(ActiveMQException.class, () -> {
            for (int i = 0; i < 100; i++) {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[200]); // 200 bytes each
               producer.send(message);
            }
         });

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testBLOCKPolicyIndependentOfQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with 1KB byte limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("block-quota");
      quotaConfig.setMaxMessageBytes(1024L); // 1KB
      config.addResourceQuota("block-quota", quotaConfig);

      // Configure address settings: quota only, no maxSize
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("block-quota");
      settings.setMaxSizeBytes(-1L); // No local size limit
      settings.setMaxSizeMessages(-1); // No message count limit
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
      config.addAddressSetting("test.#", settings);
      config.setGlobalMaxSize(-1); // No global size limit

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         locator.setBlockOnNonDurableSend(true);
         locator.setBlockOnDurableSend(true);
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.block");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.block").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         assertThrows(ActiveMQException.class, () -> {
            for (int i = 0; i < 100; i++) {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[200]); // 200 bytes each
               producer.send(message);
            }
         });

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testPAGEPolicyIndependentOfQuota() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Create quota with 1KB byte limit
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("page-quota");
      quotaConfig.setMaxMessageBytes(1024L); // 1KB
      config.addResourceQuota("page-quota", quotaConfig);

      // Configure address settings: quota only, no maxSize
      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("page-quota");
      settings.setMaxSizeBytes(-1L); // No local size limit
      settings.setMaxSizeMessages(-1); // No message count limit
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      config.addAddressSetting("test.#", settings);
      config.setGlobalMaxSize(-1); // No global size limit

      ActiveMQServer server = createServer(true, config); // Enable persistence for paging
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.page");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.page").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         // Send enough messages to exceed quota and potentially trigger paging
         assertThrows(ActiveMQException.class, () -> {
            for (int i = 0; i < 100; i++) {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[200]); // 200 bytes each
               producer.send(message);
            }
         });

         // Verify paging was not triggered
         assertFalse(server.getPagingManager().getPageStore(address).isPaging(),
            "PAGE policy should have started paging when quota exceeded");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   @Test
   public void testIsFullIgnoredQuotaCheck() throws Exception {
      Configuration config = createDefaultConfig(false);

      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("full-check-quota");
      quotaConfig.setMaxMessageBytes(512L); // 512 bytes
      config.addResourceQuota("full-check-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("full-check-quota");
      settings.setMaxSizeBytes(-1L);
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.full");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.full").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         assertThrows(ActiveMQException.class, () -> {
            for (int i = 0; i < 100; i++) {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[200]); // 200 bytes each
               producer.send(message);
            }
         });

         // Verify isFull() ignores quota
         assertFalse(server.getPagingManager().getPageStore(address).isFull(),
            "isFull() check when quota exceeded");

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }

   /**
    * Test that quota is enforced alongside existing maxSize limits.
    */
   @Test
   public void testQuotaWithMaxSizeCombined() throws Exception {
      Configuration config = createDefaultConfig(false);

      // Quota has higher limit than maxSize
      ResourceQuotaConfig quotaConfig = new ResourceQuotaConfig("combined-quota");
      quotaConfig.setMaxMessageBytes(2048L); // 2KB quota
      config.addResourceQuota("combined-quota", quotaConfig);

      AddressSettings settings = new AddressSettings();
      settings.setResourceQuota("combined-quota");
      settings.setMaxSizeBytes(1024L); // 1KB maxSize (lower than quota)
      settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      config.addAddressSetting("test.#", settings);

      ActiveMQServer server = createServer(false, config);
      server.start();

      try {
         ServerLocator locator = createInVMNonHALocator();
         ClientSessionFactory sf = createSessionFactory(locator);
         ClientSession session = sf.createSession(false, true, true);

         SimpleString address = SimpleString.of("test.combined");
         session.createAddress(address, RoutingType.ANYCAST, false);
         session.createQueue(QueueConfiguration.of("test.combined").setAddress(address).setRoutingType(RoutingType.ANYCAST));

         ClientProducer producer = session.createProducer(address);

         assertThrows(ActiveMQException.class, () -> {
            for (int i = 0; i < 100; i++) {
               ClientMessage message = session.createMessage(true);
               message.getBodyBuffer().writeBytes(new byte[200]); // 200 bytes each
               producer.send(message);
            }
         });

         session.close();
         locator.close();
      } finally {
         server.stop();
      }
   }
}
