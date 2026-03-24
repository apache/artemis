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
package org.apache.activemq.artemis.tests.integration.scheduling;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ScheduledMessageTest extends ActiveMQTestBase {

   private final SimpleString atestq = new SimpleString("ascheduledtestq");

   private final SimpleString atestq2 = new SimpleString("ascheduledtestq2");

   private Configuration configuration;

   private ActiveMQServer server;

   private ServerLocator locator;

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      startServer();
   }

   /**
    * @throws Exception
    */
   protected void startServer() throws Exception {
      configuration = createDefaultInVMConfig();
      server = createServer(true, configuration);
      server.start();
      locator = createInVMNonHALocator();
   }

   @Test
   public void testRecoveredMessageDeliveredCorrectly() throws Exception {
      testMessageDeliveredCorrectly(true);
   }

   @Test
   public void testMessageDeliveredCorrectly() throws Exception {
      testMessageDeliveredCorrectly(false);
   }

   @Test
   public void testScheduledMessagesDeliveredCorrectly() throws Exception {
      testScheduledMessagesDeliveredCorrectly(false);
   }

   @Test
   public void testRecoveredScheduledMessagesDeliveredCorrectly() throws Exception {
      testScheduledMessagesDeliveredCorrectly(true);
   }

   @Test
   public void testScheduledMessagesDeliveredCorrectlyDifferentOrder() throws Exception {
      testScheduledMessagesDeliveredCorrectlyDifferentOrder(false);
   }

   @Test
   public void testRecoveredScheduledMessagesDeliveredCorrectlyDifferentOrder() throws Exception {
      testScheduledMessagesDeliveredCorrectlyDifferentOrder(true);
   }

   @Test
   public void testScheduledAndNormalMessagesDeliveredCorrectly() throws Exception {
      testScheduledAndNormalMessagesDeliveredCorrectly(false);
   }

   @Test
   public void testRecoveredScheduledAndNormalMessagesDeliveredCorrectly() throws Exception {
      testScheduledAndNormalMessagesDeliveredCorrectly(true);
   }

   @Test
   public void testTxMessageDeliveredCorrectly() throws Exception {
      testTxMessageDeliveredCorrectly(false);
   }

   @Test
   public void testRecoveredTxMessageDeliveredCorrectly() throws Exception {
      testTxMessageDeliveredCorrectly(true);
   }

   @Test
   public void testPagedMessageDeliveredCorrectly() throws Exception {
      // then we create a client as normal
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, true, false);
      session.createQueue(new QueueConfiguration(atestq));
      ClientProducer producer = session.createProducer(atestq);
      ClientMessage message = createDurableMessage(session, "m1");
      long time = System.currentTimeMillis();
      time += 10000;
      message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(message);

      producer.close();

      ClientConsumer consumer = session.createConsumer(atestq);

      session.start();

      ClientMessage message2 = consumer.receive(10250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m1", message2.getBodyBuffer().readString());

      message2.acknowledge();

      // Make sure no more messages
      consumer.close();
      consumer = session.createConsumer(atestq);
      Assert.assertNull(consumer.receiveImmediate());

      session.close();
   }

   @Test
   public void testPagedMessageDeliveredMultipleConsumersCorrectly() throws Exception {
      AddressSettings qs = new AddressSettings().setRedeliveryDelay(5000L);
      server.getAddressSettingsRepository().addMatch(atestq.toString(), qs);
      // then we create a client as normal
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, true, false);
      session.createQueue(new QueueConfiguration(atestq));
      session.createQueue(new QueueConfiguration(atestq2).setAddress(atestq));
      ClientProducer producer = session.createProducer(atestq);
      ClientMessage message = createDurableMessage(session, "m1");
      producer.send(message);

      producer.close();

      ClientConsumer consumer = session.createConsumer(atestq);
      ClientConsumer consumer2 = session.createConsumer(atestq2);

      session.start();
      ClientMessage message3 = consumer.receive(1000);
      message3.acknowledge();
      ClientMessage message2 = consumer2.receive(1000);
      message2.acknowledge();
      Assert.assertEquals("m1", message3.getBodyBuffer().readString());
      Assert.assertEquals("m1", message2.getBodyBuffer().readString());
      long time = System.currentTimeMillis();
      // force redelivery
      consumer.close();
      consumer2.close();
      // this will make it redelivery-delay
      session.rollback();
      consumer = session.createConsumer(atestq);
      consumer2 = session.createConsumer(atestq2);
      message3 = consumer.receive(5250);
      message2 = consumer2.receive(1000);
      time += 5000;
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m1", message3.getBodyBuffer().readString());
      Assert.assertEquals("m1", message2.getBodyBuffer().readString());
      message2.acknowledge();
      message3.acknowledge();

      // Make sure no more messages
      consumer.close();
      consumer2.close();
      consumer = session.createConsumer(atestq);
      Assert.assertNull(consumer.receiveImmediate());

      session.close();
   }

   @Test
   public void testPagedMessageDeliveredMultipleConsumersAfterRecoverCorrectly() throws Exception {

      AddressSettings qs = new AddressSettings().setRedeliveryDelay(5000L);
      server.getAddressSettingsRepository().addMatch(atestq.toString(), qs);
      // then we create a client as normal
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, true, false);
      session.createQueue(new QueueConfiguration(atestq));
      session.createQueue(new QueueConfiguration(atestq2).setAddress(atestq));
      ClientProducer producer = session.createProducer(atestq);
      ClientMessage message = createDurableMessage(session, "m1");
      producer.send(message);

      producer.close();

      ClientConsumer consumer = session.createConsumer(atestq);
      ClientConsumer consumer2 = session.createConsumer(atestq2);

      session.start();
      ClientMessage message3 = consumer.receive(1000);
      Assert.assertNotNull(message3);
      message3.acknowledge();
      ClientMessage message2 = consumer2.receive(1000);
      Assert.assertNotNull(message2);
      message2.acknowledge();
      Assert.assertEquals("m1", message3.getBodyBuffer().readString());
      Assert.assertEquals("m1", message2.getBodyBuffer().readString());
      long time = System.currentTimeMillis();
      // force redelivery
      consumer.close();
      consumer2.close();
      session.rollback();
      producer.close();
      session.close();
      server.stop();
      server = null;
      server = createServer(true, configuration);
      server.start();
      sessionFactory = createSessionFactory(locator);
      session = sessionFactory.createSession(false, true, true);
      consumer = session.createConsumer(atestq);
      consumer2 = session.createConsumer(atestq2);
      session.start();
      message3 = consumer.receive(5250);
      Assert.assertNotNull(message3);
      message2 = consumer2.receive(1000);
      Assert.assertNotNull(message2);
      time += 5000;
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m1", message3.getBodyBuffer().readString());
      Assert.assertEquals("m1", message2.getBodyBuffer().readString());
      message2.acknowledge();
      message3.acknowledge();

      // Make sure no more messages
      consumer.close();
      consumer2.close();
      consumer = session.createConsumer(atestq);
      Assert.assertNull(consumer.receiveImmediate());

      session.close();
   }

   public void testMessageDeliveredCorrectly(final boolean recover) throws Exception {

      // then we create a client as normal
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, true, false);
      session.createQueue(new QueueConfiguration(atestq));
      ClientProducer producer = session.createProducer(atestq);
      ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
      message.getBodyBuffer().writeString("testINVMCoreClient");
      message.setDurable(true);
      long time = System.currentTimeMillis();
      time += 10000;
      message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(message);

      if (recover) {
         producer.close();
         session.close();
         server.stop();
         server = null;
         server = createServer(true, configuration);
         server.start();
         sessionFactory = createSessionFactory(locator);
         session = sessionFactory.createSession(false, true, true);
      }
      ClientConsumer consumer = session.createConsumer(atestq);

      session.start();

      ClientMessage message2 = consumer.receive(11000);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("testINVMCoreClient", message2.getBodyBuffer().readString());

      message2.acknowledge();

      // Make sure no more messages
      consumer.close();
      consumer = session.createConsumer(atestq);
      Assert.assertNull(consumer.receiveImmediate());

      session.close();
   }

   public void testScheduledMessagesDeliveredCorrectly(final boolean recover) throws Exception {

      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, true, false);
      session.createQueue(new QueueConfiguration(atestq));
      ClientProducer producer = session.createProducer(atestq);
      ClientMessage m1 = createDurableMessage(session, "m1");
      ClientMessage m2 = createDurableMessage(session, "m2");
      ClientMessage m3 = createDurableMessage(session, "m3");
      ClientMessage m4 = createDurableMessage(session, "m4");
      ClientMessage m5 = createDurableMessage(session, "m5");
      long time = System.currentTimeMillis();
      time += 10000;
      m1.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m1);
      time += 1000;
      m2.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m2);
      time += 1000;
      m3.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m3);
      time += 1000;
      m4.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m4);
      time += 1000;
      m5.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m5);
      time -= 4000;
      if (recover) {
         producer.close();
         session.close();
         server.stop();
         server = null;
         server = createServer(true, configuration);
         server.start();

         sessionFactory = createSessionFactory(locator);

         session = sessionFactory.createSession(false, true, true);
      }

      ClientConsumer consumer = session.createConsumer(atestq);

      session.start();

      ClientMessage message = consumer.receive(11000);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m1", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m2", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m3", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m4", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m5", message.getBodyBuffer().readString());
      message.acknowledge();

      // Make sure no more messages
      consumer.close();
      consumer = session.createConsumer(atestq);
      Assert.assertNull(consumer.receiveImmediate());

      session.close();
   }

   public void testScheduledMessagesDeliveredCorrectlyDifferentOrder(final boolean recover) throws Exception {

      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, true, false);
      session.createQueue(new QueueConfiguration(atestq));
      ClientProducer producer = session.createProducer(atestq);
      ClientMessage m1 = createDurableMessage(session, "m1");
      ClientMessage m2 = createDurableMessage(session, "m2");
      ClientMessage m3 = createDurableMessage(session, "m3");
      ClientMessage m4 = createDurableMessage(session, "m4");
      ClientMessage m5 = createDurableMessage(session, "m5");
      long time = System.currentTimeMillis();
      time += 10000;
      m1.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m1);
      time += 3000;
      m2.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m2);
      time -= 2000;
      m3.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m3);
      time += 3000;
      m4.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m4);
      time -= 2000;
      m5.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m5);
      time -= 2000;
      ClientConsumer consumer = null;
      if (recover) {
         producer.close();
         session.close();
         server.stop();
         server = null;
         server = createServer(true, configuration);
         server.start();

         sessionFactory = createSessionFactory(locator);

         session = sessionFactory.createSession(false, true, true);

      }
      consumer = session.createConsumer(atestq);

      session.start();

      ClientMessage message = consumer.receive(10250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m1", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m3", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m5", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m2", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m4", message.getBodyBuffer().readString());
      message.acknowledge();

      // Make sure no more messages
      consumer.close();
      consumer = session.createConsumer(atestq);
      Assert.assertNull(consumer.receiveImmediate());

      session.close();
   }

   @Test
   public void testManyMessagesSameTime() throws Exception {

      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, false, false);
      session.createQueue(new QueueConfiguration(atestq));
      ClientProducer producer = session.createProducer(atestq);
      long time = System.currentTimeMillis();
      time += 1000;

      for (int i = 0; i < 10; i++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("value", i);
         message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
         producer.send(message);
      }

      session.commit();

      session.start();
      ClientConsumer consumer = session.createConsumer(atestq);

      for (int i = 0; i < 10; i++) {
         ClientMessage message = consumer.receive(15000);
         assertNotNull(message);
         message.acknowledge();

         assertEquals(i, message.getIntProperty("value").intValue());
      }

      session.commit();

      Assert.assertNull(consumer.receiveImmediate());

      session.close();
   }

   public void testScheduledAndNormalMessagesDeliveredCorrectly(final boolean recover) throws Exception {

      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, true, false);
      session.createQueue(new QueueConfiguration(atestq));
      ClientProducer producer = session.createProducer(atestq);
      ClientMessage m1 = createDurableMessage(session, "m1");
      ClientMessage m2 = createDurableMessage(session, "m2");
      ClientMessage m3 = createDurableMessage(session, "m3");
      ClientMessage m4 = createDurableMessage(session, "m4");
      ClientMessage m5 = createDurableMessage(session, "m5");
      long time = System.currentTimeMillis();
      time += 10000;
      m1.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m1);
      producer.send(m2);
      time += 1000;
      m3.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m3);
      producer.send(m4);
      time += 1000;
      m5.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(m5);
      time -= 2000;
      ClientConsumer consumer = null;
      if (recover) {
         producer.close();
         session.close();
         server.stop();
         server = null;
         server = createServer(true, configuration);
         server.start();

         sessionFactory = createSessionFactory(locator);

         session = sessionFactory.createSession(false, true, true);
      }

      consumer = session.createConsumer(atestq);
      session.start();

      ClientMessage message = consumer.receive(1000);
      Assert.assertEquals("m2", message.getBodyBuffer().readString());
      message.acknowledge();
      message = consumer.receive(1000);
      Assert.assertEquals("m4", message.getBodyBuffer().readString());
      message.acknowledge();
      message = consumer.receive(10250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m1", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m3", message.getBodyBuffer().readString());
      message.acknowledge();
      time += 1000;
      message = consumer.receive(1250);
      Assert.assertTrue(System.currentTimeMillis() >= time);
      Assert.assertEquals("m5", message.getBodyBuffer().readString());
      message.acknowledge();

      // Make sure no more messages
      consumer.close();
      consumer = session.createConsumer(atestq);
      Assert.assertNull(consumer.receiveImmediate());

      session.close();
   }

   public void testTxMessageDeliveredCorrectly(final boolean recover) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(true, false, false);
      session.createQueue(new QueueConfiguration(atestq));
      session.start(xid, XAResource.TMNOFLAGS);
      ClientProducer producer = session.createProducer(atestq);
      ClientMessage message = createDurableMessage(session, "testINVMCoreClient");
      long time = System.currentTimeMillis() + 1000;
      message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      producer.send(message);
      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      if (recover) {
         producer.close();
         session.close();
         server.stop();
         server = null;
         server = createServer(true, configuration);
         server.start();

         sessionFactory = createSessionFactory(locator);

         session = sessionFactory.createSession(true, false, false);
      }
      session.commit(xid, false);
      ClientConsumer consumer = session.createConsumer(atestq);

      session.start();
      session.start(xid2, XAResource.TMNOFLAGS);
      ClientMessage message2 = consumer.receive(11000);
      long end = System.currentTimeMillis();
      Assert.assertTrue(end >= time);
      Assert.assertNotNull(message2);
      Assert.assertEquals("testINVMCoreClient", message2.getBodyBuffer().readString());

      message2.acknowledge();
      session.end(xid2, XAResource.TMSUCCESS);
      session.prepare(xid2);
      session.commit(xid2, false);
      consumer.close();
      // Make sure no more messages
      consumer = session.createConsumer(atestq);
      Assert.assertNull(consumer.receiveImmediate());
      session.close();
   }

   @Test
   public void testPendingACKOnPrepared() throws Exception {

      int NUMBER_OF_MESSAGES = 100;

      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(true, false, false);
      session.createQueue(new QueueConfiguration(atestq));

      ClientProducer producer = session.createProducer(atestq);

      long scheduled = System.currentTimeMillis() + 1000;
      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("value", i);
         msg.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, scheduled);
         producer.send(msg);
      }

      session.close();

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         Xid xid = newXID();

         session = sessionFactory.createSession(true, false, false);

         ClientConsumer consumer = session.createConsumer(atestq);

         session.start();

         session.start(xid, XAResource.TMNOFLAGS);

         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         session.end(xid, XAResource.TMSUCCESS);

         session.prepare(xid);

         session.close();
      }

      sessionFactory.close();
      locator.close();

      server.stop();

      startServer();

      sessionFactory = createSessionFactory(locator);

      session = sessionFactory.createSession(false, false);

      ClientConsumer consumer = session.createConsumer(atestq);

      session.start();

      assertNull(consumer.receive(1000));

      session.close();

      sessionFactory.close();

   }

   @Test
   public void testScheduledDeliveryTX() throws Exception {
      scheduledDelivery(true);
   }

   @Test
   public void testScheduledDeliveryNoTX() throws Exception {
      scheduledDelivery(false);
   }

   @Test
   public void testRedeliveryAfterPrepare() throws Exception {
      AddressSettings qs = new AddressSettings().setRedeliveryDelay(5000L);
      server.getAddressSettingsRepository().addMatch(atestq.toString(), qs);

      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, false, false);

      session.createQueue(new QueueConfiguration(atestq));

      ClientProducer producer = session.createProducer(atestq);
      for (int i = 0; i < 100; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("key", i);
         producer.send(msg);
         session.commit();
      }

      session.close();

      session = sessionFactory.createSession(true, false, false);

      ClientConsumer consumer = session.createConsumer(atestq);

      ArrayList<Xid> xids = new ArrayList<>();

      session.start();

      for (int i = 0; i < 100; i++) {
         Xid xid = newXID();
         session.start(xid, XAResource.TMNOFLAGS);
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         session.end(xid, XAResource.TMSUCCESS);
         session.prepare(xid);
         xids.add(xid);
      }

      session.rollback(xids.get(0));
      xids.set(0, null);
      session.close();

      server.stop();

      configuration = createDefaultInVMConfig().addAddressesSetting(atestq.toString(), qs);

      server = createServer(true, configuration);
      server.start();

      locator = createInVMNonHALocator();

      final AtomicInteger count = new AtomicInteger(0);
      Thread t = new Thread() {
         @Override
         public void run() {
            try {
               ClientSessionFactory sf = createSessionFactory(locator);
               ClientSession session = sf.createSession(false, false);
               session.start();
               ClientConsumer cons = session.createConsumer(atestq);
               for (int i = 0; i < 100; i++) {
                  ClientMessage msg = cons.receive(100000);
                  assertNotNull(msg);
                  count.incrementAndGet();
                  msg.acknowledge();
                  session.commit();
               }
               session.close();
               sf.close();
            } catch (Throwable e) {
               e.printStackTrace();
               count.set(-1);
            }
         }
      };

      t.start();

      sessionFactory = createSessionFactory(locator);

      session = sessionFactory.createSession(true, false, false);

      for (Xid xid : xids) {
         if (xid != null) {
            session.rollback(xid);
         }
      }

      session.close();

      t.join();

      assertEquals(100, count.get());
   }


   private void scheduledDelivery(final boolean tx) throws Exception {
      ActiveMQTestBase.forceGC();

      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(tx, false, false);
      session.createQueue(new QueueConfiguration(atestq));
      ClientProducer producer = session.createProducer(atestq);
      ClientConsumer consumer = session.createConsumer(atestq);

      session.start();
      if (tx) {
         session.start(xid, XAResource.TMNOFLAGS);
      }

      // Send one scheduled
      long now = System.currentTimeMillis();

      ClientMessage tm1 = createDurableMessage(session, "testScheduled1");
      tm1.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, now + 7000);
      producer.send(tm1);

      // First send some non scheduled messages

      ClientMessage tm2 = createDurableMessage(session, "testScheduled2");
      producer.send(tm2);

      ClientMessage tm3 = createDurableMessage(session, "testScheduled3");
      producer.send(tm3);

      ClientMessage tm4 = createDurableMessage(session, "testScheduled4");
      producer.send(tm4);

      // Now send some more scheduled messages

      ClientMessage tm5 = createDurableMessage(session, "testScheduled5");
      tm5.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, now + 5000);
      producer.send(tm5);

      ClientMessage tm6 = createDurableMessage(session, "testScheduled6");
      tm6.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, now + 4000);
      producer.send(tm6);

      ClientMessage tm7 = createDurableMessage(session, "testScheduled7");
      tm7.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, now + 3000);
      producer.send(tm7);

      ClientMessage tm8 = createDurableMessage(session, "testScheduled8");
      tm8.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, now + 6000);
      producer.send(tm8);

      // And one scheduled with a -ve number

      ClientMessage tm9 = createDurableMessage(session, "testScheduled9");
      tm9.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, -3);
      producer.send(tm9);

      if (tx) {
         session.end(xid, XAResource.TMSUCCESS);
         session.prepare(xid);
         session.commit(xid, false);
      } else {
         session.commit();
      }

      // First the non scheduled messages should be received

      if (tx) {
         session.start(xid, XAResource.TMNOFLAGS);
      }

      ClientMessage rm1 = consumer.receive(250);
      Assert.assertNotNull(rm1);
      Assert.assertEquals("testScheduled2", rm1.getBodyBuffer().readString());

      ClientMessage rm2 = consumer.receive(250);
      Assert.assertNotNull(rm2);
      Assert.assertEquals("testScheduled3", rm2.getBodyBuffer().readString());

      ClientMessage rm3 = consumer.receive(250);
      Assert.assertNotNull(rm3);
      Assert.assertEquals("testScheduled4", rm3.getBodyBuffer().readString());

      // Now the one with a scheduled with a -ve number
      ClientMessage rm5 = consumer.receive(250);
      Assert.assertNotNull(rm5);
      Assert.assertEquals("testScheduled9", rm5.getBodyBuffer().readString());

      // Now the scheduled
      ClientMessage rm6 = consumer.receive(3250);
      Assert.assertNotNull(rm6);
      Assert.assertEquals("testScheduled7", rm6.getBodyBuffer().readString());

      long now2 = System.currentTimeMillis();

      Assert.assertTrue(now2 - now >= 3000);

      ClientMessage rm7 = consumer.receive(1250);
      Assert.assertNotNull(rm7);
      Assert.assertEquals("testScheduled6", rm7.getBodyBuffer().readString());

      now2 = System.currentTimeMillis();

      Assert.assertTrue(now2 - now >= 4000);

      ClientMessage rm8 = consumer.receive(1250);
      Assert.assertNotNull(rm8);
      Assert.assertEquals("testScheduled5", rm8.getBodyBuffer().readString());

      now2 = System.currentTimeMillis();

      Assert.assertTrue(now2 - now >= 5000);

      ClientMessage rm9 = consumer.receive(1250);
      Assert.assertNotNull(rm9);
      Assert.assertEquals("testScheduled8", rm9.getBodyBuffer().readString());

      now2 = System.currentTimeMillis();

      Assert.assertTrue(now2 - now >= 6000);

      ClientMessage rm10 = consumer.receive(1250);
      Assert.assertNotNull(rm10);
      Assert.assertEquals("testScheduled1", rm10.getBodyBuffer().readString());

      now2 = System.currentTimeMillis();

      Assert.assertTrue(now2 - now >= 7000);

      if (tx) {
         session.end(xid, XAResource.TMSUCCESS);
         session.prepare(xid);
         session.commit(xid, false);
      }

      session.close();
      sessionFactory.close();
   }

   private ClientMessage createDurableMessage(final ClientSession session, final String body) {
      ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 1);
      message.getBodyBuffer().writeString(body);
      return message;
   }
}
