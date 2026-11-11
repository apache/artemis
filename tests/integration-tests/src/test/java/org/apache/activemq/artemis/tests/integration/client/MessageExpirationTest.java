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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
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
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageExpirationTest extends ActiveMQTestBase {

   private static final int EXPIRATION = 1000;

   private ActiveMQServer server;

   private ClientSession session;

   private ClientSessionFactory sf;

   private ServerLocator locator;

   @Test
   public void testMessageExpiredWithoutExpiryAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);
      message.setExpiration(System.currentTimeMillis() + MessageExpirationTest.EXPIRATION);
      producer.send(message);

      Thread.sleep(MessageExpirationTest.EXPIRATION * 2);

      session.start();

      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message2 = consumer.receiveImmediate();
      assertNull(message2);

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testAmqpJmsReloaded() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString expiry = RandomUtil.randomUUIDSimpleString();

      server.createQueue(QueueConfiguration.of(queue).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(expiry));
      server.getAddressSettingsRepository().addMatch(queue.toString(), new AddressSettings().setExpiryAddress(expiry));

      ConnectionFactory cf = new JmsConnectionFactory("amqp://localhost:61616");
      Connection connection = cf.createConnection();
      Session session = connection.createSession();
      MessageProducer producer = session.createProducer(session.createQueue(queue.toString()));
      producer.setTimeToLive(EXPIRATION);

      for (int i = 0; i < 20; i++) {
         javax.jms.Message message = session.createMessage();
         producer.send(message);
      }
      connection.close();
      Wait.assertEquals(20L, () -> server.locateQueue(queue).getMessageCount(), 2000, 100);
      Wait.assertEquals(0L, () -> server.locateQueue(expiry).getMessageCount(), 2000, 100);

      server.stop();
      server.start();

      Thread.sleep(EXPIRATION * 2);

      Wait.assertEquals(0L, () -> server.locateQueue(queue).getMessageCount(), 2000, 100);
      Wait.assertEquals(20L, () -> server.locateQueue(expiry).getMessageCount(), 2000, 100);

      connection = cf.createConnection();
      session = connection.createSession();
      MessageConsumer consumer = session.createConsumer(session.createQueue(queue.toString()));
      connection.start();

      for (int i = 0; i < 20; i++) {
         javax.jms.Message message2 = consumer.receiveNoWait();
         assertNull(message2);
      }

      consumer.close();
   }

   @Test
   public void testMessageExpiredWithoutExpiryAddressWithExpiryDelayOverride() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.close();

      session = addClientSession(sf.createSession(false, false, false));
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);

      AddressSettings addressSettings = new AddressSettings().setExpiryDelay((long) MessageExpirationTest.EXPIRATION);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      producer.send(message);

      // second message, this message shouldn't be overridden
      message = session.createMessage(false);
      message.setExpiration(System.currentTimeMillis() + EXPIRATION * 3);
      producer.send(message);
      session.commit();

      session.start();
      ClientConsumer consumer = session.createConsumer(queue);
      // non expired.. should receive both
      assertNotNull(consumer.receiveImmediate());
      assertNotNull(consumer.receiveImmediate());

      // stopping the consumer to cleanup the client's buffer
      session.stop();

      // we receive the message and then rollback...   then we wait some time > expiration, the message must be gone
      session.rollback();

      Thread.sleep(MessageExpirationTest.EXPIRATION * 2);
      session.start();

      // one expired as we changed the expiry in one of the messages... should receive just one
      assertNotNull(consumer.receiveImmediate());
      assertNull(consumer.receiveImmediate());

      session.stop();
      session.rollback();

      Thread.sleep(MessageExpirationTest.EXPIRATION * 2);
      session.start();

      // both expired... nothing should be received
      assertNull(consumer.receiveImmediate());

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testMessageExpirationOnServer() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message = session.createMessage(false);
      message.setExpiration(System.currentTimeMillis() + MessageExpirationTest.EXPIRATION);
      producer.send(message);

      Thread.sleep(MessageExpirationTest.EXPIRATION * 2);

      session.start();

      Thread.sleep(500);

      assertEquals(0, ((Queue) server.getPostOffice().getBinding(queue).getBindable()).getDeliveringCount());
      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(queue).getBindable())));

      ClientMessage message2 = consumer.receiveImmediate();
      assertNull(message2);

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testMessageExpirationOnClient() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);
      message.setExpiration(System.currentTimeMillis() + MessageExpirationTest.EXPIRATION);
      producer.send(message);

      session.start();

      Thread.sleep(MessageExpirationTest.EXPIRATION * 2);

      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message2 = consumer.receiveImmediate();
      assertNull(message2);

      assertEquals(0, ((Queue) server.getPostOffice().getBinding(queue).getBindable()).getDeliveringCount());
      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(queue).getBindable())));

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testMessageExpiredWithExpiryAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      final SimpleString expiryAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString expiryQueue = RandomUtil.randomUUIDSimpleString();

      server.getAddressSettingsRepository().addMatch(address.toString(), new AddressSettings().setExpiryAddress(expiryAddress));

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      session.createQueue(QueueConfiguration.of(expiryQueue).setAddress(expiryAddress).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);
      message.setExpiration(System.currentTimeMillis() + MessageExpirationTest.EXPIRATION);
      producer.send(message);

      Thread.sleep(MessageExpirationTest.EXPIRATION * 2);

      session.start();

      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message2 = consumer.receiveImmediate();
      assertNull(message2);

      ClientConsumer expiryConsumer = session.createConsumer(expiryQueue);
      ClientMessage expiredMessage = expiryConsumer.receive(500);
      assertNotNull(expiredMessage);
      assertNotNull(expiredMessage.getObjectProperty(Message.HDR_ACTUAL_EXPIRY_TIME));
      assertEquals(address, expiredMessage.getObjectProperty(Message.HDR_ORIGINAL_ADDRESS));
      assertEquals(queue, expiredMessage.getObjectProperty(Message.HDR_ORIGINAL_QUEUE));
      consumer.close();
      expiryConsumer.close();
      session.deleteQueue(queue);
      session.deleteQueue(expiryQueue);
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(true);
      server.getConfiguration().addAcceptorConfiguration("amqp", "tcp://127.0.0.1:61616");
      server.getConfiguration().setResolveProtocols(true);
      server.getConfiguration().setMessageExpiryScanPeriod(200);
      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));
   }
}
