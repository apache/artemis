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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.management.UnixOperatingSystemMXBean;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeadLetterAddressTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ClientSession clientSession;
   private ServerLocator locator;

   @Test
   public void testBasicSend() throws Exception {
      SimpleString dla = SimpleString.of("DLA");
      SimpleString qName = SimpleString.of("q1");
      SimpleString adName = SimpleString.of("ad1");
      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(adName.toString(), addressSettings);
      SimpleString dlq = SimpleString.of("DLQ1");
      clientSession.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(false));
      ClientProducer producer = clientSession.createProducer(adName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();
      clientConsumer = clientSession.createConsumer(dlq);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("q1", m.getStringProperty(Message.HDR_ORIGINAL_QUEUE));
      assertEquals("ad1", m.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));
      assertEquals("heyho!", m.getBodyBuffer().readString());
   }

   @Test
   public void testLargeMessageFileLeak() throws Exception {
      OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

      // only run this on *nix systems which will have the com.sun.management.UnixOperatingSystemMXBean (needed to check open file count)
      assumeTrue(os instanceof UnixOperatingSystemMXBean);

      long fdBaseline = ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount();
      final int SIZE = 2 * 1024;
      SimpleString dla = SimpleString.of("DLA");
      SimpleString qName = SimpleString.of("q1");
      SimpleString adName = SimpleString.of("ad1");

      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(adName.toString(), addressSettings);
      SimpleString dlq = SimpleString.of("DLQ1");
      clientSession.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(false));
      for (int i = 0; i < 10; i++) {
         ClientProducer producer = clientSession.createProducer(adName);
         ClientMessage clientFile = clientSession.createMessage(true);
         clientFile.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(SIZE));
         producer.send(clientFile);
         clientSession.start();
         ClientConsumer clientConsumer = clientSession.createConsumer(qName);
         ClientMessage m = clientConsumer.receive(500);
         m.acknowledge();
         assertNotNull(m);

         // force a cancel
         clientSession.rollback();
         m = clientConsumer.receiveImmediate();
         assertNull(m);
         clientConsumer.close();
      }
      Wait.assertTrue("File descriptors are leaking", () -> ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount() - fdBaseline <= 0);
   }

   // HORNETQ- 1084
   @Test
   public void testBasicSendWithDLAButNoBinding() throws Exception {
      SimpleString dla = SimpleString.of("DLA");
      SimpleString qName = SimpleString.of("q1");
      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      //SimpleString dlq = SimpleString.of("DLQ1");
      //clientSession.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();
      Queue q = (Queue) server.getPostOffice().getBinding(qName).getBindable();
      assertEquals(0, q.getDeliveringCount());
   }

   @Test
   public void testBasicSend2times() throws Exception {
      SimpleString dla = SimpleString.of("DLA");
      SimpleString qName = SimpleString.of("q1");
      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(2).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      SimpleString dlq = SimpleString.of("DLQ1");
      clientSession.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(5000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      // force a cancel
      clientSession.rollback();
      clientSession.start();
      m = clientConsumer.receive(5000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();
      clientConsumer = clientSession.createConsumer(dlq);
      m = clientConsumer.receive(5000);
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
   }

   @Test
   public void testReceiveWithListeners() throws Exception {
      SimpleString dla = SimpleString.of("DLA");
      SimpleString qName = SimpleString.of("q1");
      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(2).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      SimpleString dlq = SimpleString.of("DLQ1");
      clientSession.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      final CountDownLatch latch = new CountDownLatch(2);
      TestHandler handler = new TestHandler(latch, clientSession);
      clientConsumer.setMessageHandler(handler);
      clientSession.start();
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertEquals(2, handler.count);
      clientConsumer = clientSession.createConsumer(dlq);
      Message m = clientConsumer.receive(5000);
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
   }

   class TestHandler implements MessageHandler {

      private final CountDownLatch latch;
      int count = 0;

      private final ClientSession clientSession;

      TestHandler(CountDownLatch latch, ClientSession clientSession) {
         this.latch = latch;
         this.clientSession = clientSession;
      }

      @Override
      public void onMessage(ClientMessage message) {
         count++;
         latch.countDown();
         try {
            clientSession.rollback(true);
         } catch (ActiveMQException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
         }
         throw new RuntimeException();
      }
   }

   @Test
   public void testBasicSendToMultipleQueues() throws Exception {
      SimpleString dla = SimpleString.of("DLA");
      SimpleString qName = SimpleString.of("q1");
      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      SimpleString dlq = SimpleString.of("DLQ1");
      SimpleString dlq2 = SimpleString.of("DLQ2");
      clientSession.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(dlq2).setAddress(dla).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();
      clientConsumer = clientSession.createConsumer(dlq);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("heyho!", m.getBodyBuffer().readString());
      clientConsumer.close();
      clientConsumer = clientSession.createConsumer(dlq2);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("heyho!", m.getBodyBuffer().readString());
      clientConsumer.close();
   }

   @Test
   public void testBasicSendToNoQueue() throws Exception {
      SimpleString qName = SimpleString.of("q1");
      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      ClientProducer producer = clientSession.createProducer(qName);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
      // force a cancel
      clientSession.rollback();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();
   }

   @Test
   public void testHeadersSet() throws Exception {
      final int MAX_DELIVERIES = 16;
      final int NUM_MESSAGES = 5;
      SimpleString dla = SimpleString.of("DLA");
      SimpleString qName = SimpleString.of("q1");
      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(MAX_DELIVERIES).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(qName.toString(), addressSettings);
      SimpleString dlq = SimpleString.of("DLQ1");
      clientSession.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(qName).setDurable(false));
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession sendSession = sessionFactory.createSession(false, true, true);
      ClientProducer producer = sendSession.createProducer(qName);
      Map<String, Long> origIds = new HashMap<>();

      for (int i = 0; i < NUM_MESSAGES; i++) {
         ClientMessage tm = createTextMessage(clientSession, "Message:" + i);
         producer.send(tm);
      }

      ClientConsumer clientConsumer = clientSession.createConsumer(qName);
      clientSession.start();

      for (int i = 0; i < MAX_DELIVERIES; i++) {
         for (int j = 0; j < NUM_MESSAGES; j++) {
            ClientMessage tm = clientConsumer.receive(1000);

            assertNotNull(tm);
            tm.acknowledge();
            if (i == 0) {
               origIds.put("Message:" + j, tm.getMessageID());
            }
            assertEquals("Message:" + j, tm.getBodyBuffer().readString());
         }
         clientSession.rollback();
      }

      long timeout = System.currentTimeMillis() + 5000;

      // DLA transfer is asynchronous fired on the rollback
      while (System.currentTimeMillis() < timeout && getMessageCount(((Queue) server.getPostOffice().getBinding(qName).getBindable())) != 0) {
         Thread.sleep(1);
      }

      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(qName).getBindable())));
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
      // All the messages should now be in the DLQ

      ClientConsumer cc3 = clientSession.createConsumer(dlq);

      for (int i = 0; i < NUM_MESSAGES; i++) {
         ClientMessage tm = cc3.receive(1000);

         assertNotNull(tm);

         String text = tm.getBodyBuffer().readString();
         assertEquals("Message:" + i, text);

         // Check the headers
         SimpleString origDest = (SimpleString) tm.getObjectProperty(Message.HDR_ORIGINAL_ADDRESS);

         Long origMessageId = (Long) tm.getObjectProperty(Message.HDR_ORIG_MESSAGE_ID);

         assertEquals(qName, origDest);

         Long origId = origIds.get(text);

         assertEquals(origId, origMessageId);
      }

      sendSession.close();

   }

   @Test
   public void testDeadlLetterAddressWithDefaultAddressSettings() throws Exception {
      int deliveryAttempt = 3;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString deadLetterAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString deadLetterQueue = RandomUtil.randomUUIDSimpleString();
      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(deliveryAttempt).setDeadLetterAddress(deadLetterAddress);
      server.getAddressSettingsRepository().setDefault(addressSettings);

      clientSession.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(deadLetterQueue).setAddress(deadLetterAddress).setDurable(false));

      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(queue);
      for (int i = 0; i < deliveryAttempt; i++) {
         ClientMessage m = clientConsumer.receive(500);
         assertNotNull(m);
         assertEquals(i + 1, m.getDeliveryCount());
         m.acknowledge();
         clientSession.rollback();
      }
      ClientMessage m = clientConsumer.receive(500);
      assertNull(m, "not expecting a message");
      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(deadLetterQueue);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
   }

   @Test
   public void testDeadlLetterAddressWithWildcardAddressSettings() throws Exception {
      int deliveryAttempt = 3;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString deadLetterAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString deadLetterQueue = RandomUtil.randomUUIDSimpleString();
      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(deliveryAttempt).setDeadLetterAddress(deadLetterAddress);
      server.getAddressSettingsRepository().addMatch("*", addressSettings);

      clientSession.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(deadLetterQueue).setAddress(deadLetterAddress).setDurable(false));

      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(queue);
      for (int i = 0; i < deliveryAttempt; i++) {
         ClientMessage m = clientConsumer.receive(500);
         assertNotNull(m);
         assertEquals(i + 1, m.getDeliveryCount());
         m.acknowledge();
         clientSession.rollback();
      }
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();

      clientConsumer = clientSession.createConsumer(deadLetterQueue);
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
   }

   @Test
   public void testDeadLetterAddressWithOverridenSublevelAddressSettings() throws Exception {
      int defaultDeliveryAttempt = 3;
      int specificeDeliveryAttempt = defaultDeliveryAttempt + 1;

      SimpleString address = SimpleString.of("prefix.address");
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString defaultDeadLetterAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString defaultDeadLetterQueue = RandomUtil.randomUUIDSimpleString();
      SimpleString specificDeadLetterAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString specificDeadLetterQueue = RandomUtil.randomUUIDSimpleString();

      AddressSettings defaultAddressSettings = new AddressSettings().setMaxDeliveryAttempts(defaultDeliveryAttempt).setDeadLetterAddress(defaultDeadLetterAddress);
      server.getAddressSettingsRepository().addMatch("*", defaultAddressSettings);
      AddressSettings specificAddressSettings = new AddressSettings().setMaxDeliveryAttempts(specificeDeliveryAttempt).setDeadLetterAddress(specificDeadLetterAddress);
      server.getAddressSettingsRepository().addMatch(address.toString(), specificAddressSettings);

      clientSession.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(defaultDeadLetterQueue).setAddress(defaultDeadLetterAddress).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(specificDeadLetterQueue).setAddress(specificDeadLetterAddress).setDurable(false));

      ClientProducer producer = clientSession.createProducer(address);
      ClientMessage clientMessage = createTextMessage(clientSession, "heyho!");
      producer.send(clientMessage);

      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(queue);
      ClientConsumer defaultDeadLetterConsumer = clientSession.createConsumer(defaultDeadLetterQueue);
      ClientConsumer specificDeadLetterConsumer = clientSession.createConsumer(specificDeadLetterQueue);

      for (int i = 0; i < defaultDeliveryAttempt; i++) {
         ClientMessage m = clientConsumer.receive(500);
         assertNotNull(m);
         assertEquals(i + 1, m.getDeliveryCount());
         m.acknowledge();
         clientSession.rollback();
      }

      assertNull(defaultDeadLetterConsumer.receiveImmediate());
      assertNull(specificDeadLetterConsumer.receiveImmediate());

      // one more redelivery attempt:
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals(specificeDeliveryAttempt, m.getDeliveryCount());
      m.acknowledge();
      clientSession.rollback();

      assertNull(defaultDeadLetterConsumer.receiveImmediate());
      assertNotNull(specificDeadLetterConsumer.receive(500));
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), true));
      server.start();
      // then we create a client as normal
      locator = createInVMNonHALocator();
      locator.setMinLargeMessageSize(1024);
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      clientSession = addClientSession(sessionFactory.createSession(false, true, false));
   }
}
