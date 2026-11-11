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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExpiryAddressTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ClientSession clientSession;
   private ServerLocator locator;

   @Test
   public void testBasicSend() throws Exception {
      SimpleString ea = SimpleString.of("EA");
      SimpleString adSend = SimpleString.of("a1");
      SimpleString qName = SimpleString.of("q1");
      SimpleString eq = SimpleString.of("EA1");
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(ea);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);
      clientSession.createQueue(QueueConfiguration.of(eq).setAddress(ea).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(adSend).setDurable(false));

      ClientProducer producer = clientSession.createProducer(adSend);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      clientMessage.setExpiration(System.currentTimeMillis());
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();
      clientConsumer = clientSession.createConsumer(eq);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals(qName.toString(), m.getStringProperty(Message.HDR_ORIGINAL_QUEUE));
      assertEquals(adSend.toString(), m.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      m.acknowledge();
   }


   @Test
   public void testExpireSingleMessage() throws Exception {
      SimpleString ea = SimpleString.of("EA");
      SimpleString adSend = SimpleString.of("a1");
      SimpleString qName = SimpleString.of("q1");
      SimpleString eq = SimpleString.of("EA1");
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(ea);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);
      clientSession.createQueue(QueueConfiguration.of(eq).setAddress(ea).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(adSend).setDurable(false));


      ClientProducer producer = clientSession.createProducer(adSend);

      for (int i = 0; i < QueueImpl.MAX_DELIVERIES_IN_LOOP * 2 + 100; i++) {
         ClientMessage clientMessage = createTextMessage(clientSession, "notExpired!");
         clientMessage.putIntProperty("i", i);
         producer.send(clientMessage);
      }

      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      clientMessage.setExpiration(System.currentTimeMillis());
      producer.send(clientMessage);

      Queue queueQ1 = server.locateQueue("q1");

      CountDownLatch latch = new CountDownLatch(10);
      for (int i = 0; i < 10; i++) {
         // done should be called even for the ones that are ignored because the expiry is already running
         queueQ1.expireReferences(latch::countDown);
      }

      assertTrue(latch.await(10, TimeUnit.SECONDS));

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(eq);
      ClientMessage m = clientConsumer.receive(5000);
      assertNotNull(m);
      assertEquals(qName.toString(), m.getStringProperty(Message.HDR_ORIGINAL_QUEUE));
      assertEquals(adSend.toString(), m.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      m.acknowledge();
   }

   @Test
   public void testBasicSendWithRetroActiveAddressSettings() throws Exception {
      // apply "original" address settings
      SimpleString expiryAddress1 = SimpleString.of("expiryAddress1");
      SimpleString qName = SimpleString.of("q1");
      SimpleString expiryQueue1 = SimpleString.of("expiryQueue1");
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(expiryAddress1);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      clientSession.createQueue(QueueConfiguration.of(expiryQueue1).setAddress(expiryAddress1).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));

      // override "original" address settings
      SimpleString expiryAddress2 = SimpleString.of("expiryAddress2");
      SimpleString expiryQueue2 = SimpleString.of("expiryQueue2");
      addressSettings = new AddressSettings().setExpiryAddress(expiryAddress2);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      clientSession.createQueue(QueueConfiguration.of(expiryQueue2).setAddress(expiryAddress2).setDurable(false));

      // send message that will expire ASAP
      ClientProducer producer = clientSession.createProducer(qName);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      clientMessage.setExpiration(System.currentTimeMillis());
      producer.send(clientMessage);

      clientSession.start();

      // make sure the message has expired from the original queue
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();

      // make sure the message wasn't sent to the original expiry address
      clientConsumer = clientSession.createConsumer(expiryQueue1);
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();

      // make sure the message was sent to the expected expected expiry address
      clientConsumer = clientSession.createConsumer(expiryQueue2);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      m.acknowledge();
   }

   @Test
   public void testBasicSendToMultipleQueues() throws Exception {
      SimpleString ea = SimpleString.of("EA");
      SimpleString qName = SimpleString.of("q1");
      SimpleString eq = SimpleString.of("EQ1");
      SimpleString eq2 = SimpleString.of("EQ2");
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(ea);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      clientSession.createQueue(QueueConfiguration.of(eq).setAddress(ea).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(eq2).setAddress(ea).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      ClientProducer producer = clientSession.createProducer(qName);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      clientMessage.setExpiration(System.currentTimeMillis());

      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receiveImmediate();

      assertNull(m);

      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(eq);

      m = clientConsumer.receive(500);

      assertNotNull(m);

      assertNotNull(m.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));
      assertNotNull(m.getStringProperty(Message.HDR_ORIGINAL_QUEUE));

      m.acknowledge();

      assertEquals("heyho!", m.getBodyBuffer().readString());

      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(eq2);

      m = clientConsumer.receive(500);

      assertNotNull(m);

      assertNotNull(m.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));
      assertNotNull(m.getStringProperty(Message.HDR_ORIGINAL_QUEUE));

      m.acknowledge();

      assertEquals("heyho!", m.getBodyBuffer().readString());

      clientConsumer.close();

      clientSession.commit();
   }

   @Test
   public void testBasicSendToNoQueue() throws Exception {
      SimpleString ea = SimpleString.of("EA");
      SimpleString qName = SimpleString.of("q1");
      SimpleString eq = SimpleString.of("EQ1");
      SimpleString eq2 = SimpleString.of("EQ2");
      clientSession.createQueue(QueueConfiguration.of(eq).setAddress(ea).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(eq2).setAddress(ea).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      ClientProducer producer = clientSession.createProducer(qName);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      clientMessage.setExpiration(System.currentTimeMillis());
      producer.send(clientMessage);
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);

      clientConsumer.close();
   }

   @Test
   public void testHeadersSet() throws Exception {
      final int NUM_MESSAGES = 5;
      SimpleString ea = SimpleString.of("DLA");
      SimpleString qName = SimpleString.of("q1");
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(ea);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      SimpleString eq = SimpleString.of("EA1");
      clientSession.createQueue(QueueConfiguration.of(eq).setAddress(ea).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      ServerLocator locator1 = createInVMNonHALocator();

      ClientSessionFactory sessionFactory = createSessionFactory(locator1);

      ClientSession sendSession = sessionFactory.createSession(false, true, true);
      ClientProducer producer = sendSession.createProducer(qName);

      long expiration = System.currentTimeMillis();
      for (int i = 0; i < NUM_MESSAGES; i++) {
         ClientMessage tm = createTextMessage(clientSession, "Message:" + i);
         tm.setExpiration(expiration);
         producer.send(tm);
      }

      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      clientSession.start();
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
      // All the messages should now be in the EQ

      ClientConsumer cc3 = clientSession.createConsumer(eq);

      for (int i = 0; i < NUM_MESSAGES; i++) {
         ClientMessage tm = cc3.receive(1000);

         assertNotNull(tm);

         String text = tm.getBodyBuffer().readString();
         assertEquals("Message:" + i, text);

         // Check the headers
         Long actualExpiryTime = (Long) tm.getObjectProperty(Message.HDR_ACTUAL_EXPIRY_TIME);
         assertTrue(actualExpiryTime >= expiration);
      }

      sendSession.close();

      locator1.close();

   }

   @Test
   public void testExpireWithDefaultAddressSettings() throws Exception {
      SimpleString ea = SimpleString.of("EA");
      SimpleString qName = SimpleString.of("q1");
      SimpleString eq = SimpleString.of("EA1");
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(ea);
      server.getAddressSettingsRepository().setDefault(addressSettings);
      clientSession.createQueue(QueueConfiguration.of(eq).setAddress(ea).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));

      ClientProducer producer = clientSession.createProducer(qName);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      clientMessage.setExpiration(System.currentTimeMillis());
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(eq);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      m.acknowledge();
   }

   @Test
   public void testExpireWithWildcardAddressSettings() throws Exception {
      SimpleString ea = SimpleString.of("EA");
      SimpleString qName = SimpleString.of("q1");
      SimpleString eq = SimpleString.of("EA1");
      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(ea);
      server.getAddressSettingsRepository().addMatch("*", addressSettings);
      clientSession.createQueue(QueueConfiguration.of(eq).setAddress(ea).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));

      ClientProducer producer = clientSession.createProducer(qName);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      clientMessage.setExpiration(System.currentTimeMillis());
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(eq);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      m.acknowledge();
   }

   @Test
   public void testExpireWithOverridenSublevelAddressSettings() throws Exception {
      SimpleString address = SimpleString.of("prefix.address");
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString defaultExpiryAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString defaultExpiryQueue = RandomUtil.randomUUIDSimpleString();
      SimpleString specificExpiryAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString specificExpiryQueue = RandomUtil.randomUUIDSimpleString();

      AddressSettings defaultAddressSettings = new AddressSettings().setExpiryAddress(defaultExpiryAddress);
      server.getAddressSettingsRepository().addMatch("prefix.*", defaultAddressSettings);
      AddressSettings specificAddressSettings = new AddressSettings().setExpiryAddress(specificExpiryAddress);
      server.getAddressSettingsRepository().addMatch("prefix.address", specificAddressSettings);

      clientSession.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(defaultExpiryQueue).setAddress(defaultExpiryAddress).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(specificExpiryQueue).setAddress(specificExpiryAddress).setDurable(false));

      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      clientMessage.setExpiration(System.currentTimeMillis());
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(queue);
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(defaultExpiryQueue);
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(specificExpiryQueue);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      m.acknowledge();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), false));
      server.start();
      // then we create a client as normal
      locator = createInVMNonHALocator().setBlockOnAcknowledge(true);
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      // There are assertions over sizes that needs to be done after the ACK
      // was received on server
      clientSession = addClientSession(sessionFactory.createSession(null, null, false, true, true, false, 0));
   }
}
