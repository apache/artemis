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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageExpirationTest extends ActiveMQTestBase {

   private static final int EXPIRATION = 1000;
   private static final int MIN_EXPIRATION = 500;
   private static final int MAX_EXPIRATION = 1500;

   private ActiveMQServer server;

   private ClientSession session;

   private ClientSessionFactory sf;

   private ServerLocator locator;

   @Test
   public void testMessagesExpiredNoBindings() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString expiryAddress = RandomUtil.randomUUIDSimpleString();

      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(expiryAddress);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(queue).setAddress(address));

      assertEquals(0, server.locateQueue(queue).getMessagesExpired());

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(true);
      message.setExpiration(System.currentTimeMillis() + EXPIRATION);
      producer.send(message);

      assertTrue(Wait.waitFor(() -> server.locateQueue(queue).getMessagesExpired() == 1, 2000, 100));

      assertEquals(0, server.locateQueue(queue).getMessageCount());
      assertEquals(0, server.locateQueue(queue).getDeliveringCount());

      session.deleteQueue(queue);
   }

   @Test
   public void testMessagesExpiredNoExpiryAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address));

      assertEquals(0, server.locateQueue(queue).getMessagesExpired());

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(true);
      message.setExpiration(System.currentTimeMillis() + EXPIRATION);
      producer.send(message);

      assertTrue(Wait.waitFor(() -> server.locateQueue(queue).getMessagesExpired() == 1, 2000, 100));

      assertEquals(0, server.locateQueue(queue).getMessageCount());
      assertEquals(0, server.locateQueue(queue).getDeliveringCount());

      session.deleteQueue(queue);
   }

   @Test
   public void testMessageWithNoExpirationMinExpiryDelayOverride() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.close();

      session = addClientSession(sf.createSession(false, true, false));
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);

      AddressSettings addressSettings = new AddressSettings().setMinExpiryDelay((long) MIN_EXPIRATION);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      producer.send(message);

      long start = System.currentTimeMillis();
      Wait.assertEquals(1, server.locateQueue(queue)::getMessagesExpired, 5000);
      assertTrue(System.currentTimeMillis() - start > MIN_EXPIRATION);

      session.deleteQueue(queue);
   }

   @Test
   public void testMessageWithTooSmallExpirationMinExpiryDelayOverride() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.close();

      session = addClientSession(sf.createSession(false, true, false));
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);

      AddressSettings addressSettings = new AddressSettings().setMinExpiryDelay((long) MIN_EXPIRATION);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      message.setExpiration(System.currentTimeMillis() + (MIN_EXPIRATION / 2));
      producer.send(message);

      long start = System.currentTimeMillis();
      Wait.assertEquals(1, server.locateQueue(queue)::getMessagesExpired, 5000);
      assertTrue(System.currentTimeMillis() - start > MIN_EXPIRATION);

      session.deleteQueue(queue);
   }

   @Test
   public void testMessageWithNoExpirationMaxExpiryDelayOverride() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.close();

      session = addClientSession(sf.createSession(false, true, false));
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);

      AddressSettings addressSettings = new AddressSettings().setMaxExpiryDelay((long) MAX_EXPIRATION);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      producer.send(message);

      Wait.assertEquals(1, server.locateQueue(queue)::getMessagesExpired, 5000);

      session.deleteQueue(queue);
   }

   @Test
   public void testMessageWithTooLargeExpirationMaxExpiryDelayOverride() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.close();

      session = addClientSession(sf.createSession(false, true, false));
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);

      AddressSettings addressSettings = new AddressSettings().setMaxExpiryDelay((long) MAX_EXPIRATION);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      message.setExpiration(System.currentTimeMillis() + (3600 * 1000)); // The long expiration would be one hour from now
      producer.send(message);

      Queue serverQueue = server.locateQueue(queue);
      Wait.assertEquals(1, serverQueue::getMessagesExpired, 5000);

      session.deleteQueue(queue);
   }

   @Test
   public void testMessageWithAcceptableExpirationMinMaxExpiryDelayOverride() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.close();

      session = addClientSession(sf.createSession(false, true, false));
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);

      AddressSettings addressSettings = new AddressSettings().setMinExpiryDelay((long) MIN_EXPIRATION).setMaxExpiryDelay((long) MAX_EXPIRATION);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      message.setExpiration(System.currentTimeMillis() + EXPIRATION);
      producer.send(message);

      long start = System.currentTimeMillis();
      Queue serverQueue = server.locateQueue(queue);
      Wait.assertEquals(1, serverQueue::getMessagesExpired, 5000);
      assertTrue(System.currentTimeMillis() - start > MIN_EXPIRATION);
      assertTrue(System.currentTimeMillis() - start < MAX_EXPIRATION);

      session.deleteQueue(queue);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false);

      server.getConfiguration().setMessageExpiryScanPeriod(100);

      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));
   }
}
