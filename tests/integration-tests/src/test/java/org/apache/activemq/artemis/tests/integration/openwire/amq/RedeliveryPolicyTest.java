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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQConsumer;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQConsumerAccessor;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.broker.region.policy.RedeliveryPolicyMap;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * adapted from: org.apache.activemq.RedeliveryPolicyTest
 */
public class RedeliveryPolicyTest extends BasicOpenWireTest {

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      // making data persistent makes it easier to debug it with print-data
      this.realStore = true;
      super.setUp();
   }

   @Test
   public void testGetNext() throws Exception {

      RedeliveryPolicy policy = new RedeliveryPolicy();
      policy.setInitialRedeliveryDelay(0);
      policy.setRedeliveryDelay(500);
      policy.setBackOffMultiplier((short) 2);
      policy.setUseExponentialBackOff(true);

      long delay = policy.getNextRedeliveryDelay(0);
      assertEquals(500, delay);
      delay = policy.getNextRedeliveryDelay(delay);
      assertEquals(500 * 2, delay);
      delay = policy.getNextRedeliveryDelay(delay);
      assertEquals(500 * 4, delay);

      policy.setUseExponentialBackOff(false);
      delay = policy.getNextRedeliveryDelay(delay);
      assertEquals(500, delay);
   }

   @Test
   public void testExponentialRedeliveryPolicyDelaysDeliveryOnRollback() throws Exception {

      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setInitialRedeliveryDelay(0);
      policy.setRedeliveryDelay(500);
      policy.setBackOffMultiplier((short) 2);
      policy.setUseExponentialBackOff(true);

      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("testExponentialRedeliveryPolicyDelaysDeliveryOnRollback");
      this.makeSureCoreQueueExist("testExponentialRedeliveryPolicyDelaysDeliveryOnRollback");
      MessageProducer producer = session.createProducer(destination);

      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      producer.send(session.createTextMessage("1st"));
      producer.send(session.createTextMessage("2nd"));
      session.commit();

      TextMessage m;
      m = (TextMessage) consumer.receive(1000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      // No delay on first rollback..
      m = (TextMessage) consumer.receive(100);
      assertNotNull(m);
      session.rollback();

      // Show subsequent re-delivery delay is incrementing.
      m = (TextMessage) consumer.receive(100);
      assertNull(m);

      m = (TextMessage) consumer.receive(700);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      // Show re-delivery delay is incrementing exponentially
      m = (TextMessage) consumer.receive(100);
      assertNull(m);
      m = (TextMessage) consumer.receive(500);
      assertNull(m);
      m = (TextMessage) consumer.receive(700);
      assertNotNull(m);
      assertEquals("1st", m.getText());

   }

   @Test
   public void testNornalRedeliveryPolicyDelaysDeliveryOnRollback() throws Exception {

      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setInitialRedeliveryDelay(0);
      policy.setRedeliveryDelay(500);

      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("testNornalRedeliveryPolicyDelaysDeliveryOnRollback");
      this.makeSureCoreQueueExist("testNornalRedeliveryPolicyDelaysDeliveryOnRollback");
      MessageProducer producer = session.createProducer(destination);

      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      producer.send(session.createTextMessage("1st"));
      producer.send(session.createTextMessage("2nd"));
      session.commit();

      TextMessage m;
      m = (TextMessage) consumer.receive(1000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      // No delay on first rollback..
      m = (TextMessage) consumer.receive(100);
      assertNotNull(m);
      session.rollback();

      // Show subsequent re-delivery delay is incrementing.
      m = (TextMessage) consumer.receive(100);
      assertNull(m);
      m = (TextMessage) consumer.receive(700);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      // The message gets redelivered after 500 ms every time since
      // we are not using exponential backoff.
      m = (TextMessage) consumer.receive(100);
      assertNull(m);
      m = (TextMessage) consumer.receive(700);
      assertNotNull(m);
      assertEquals("1st", m.getText());

   }

   @Test
   public void testDLQHandling() throws Exception {
      this.makeSureCoreQueueExist("ActiveMQ.DLQ");
      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setInitialRedeliveryDelay(100);
      policy.setUseExponentialBackOff(false);
      policy.setMaximumRedeliveries(2);

      connection.start();
      Session session = connection.createSession(true, Session.CLIENT_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");
      MessageProducer producer = session.createProducer(destination);

      MessageConsumer consumer = session.createConsumer(destination);
      MessageConsumer dlqConsumer = session.createConsumer(new ActiveMQQueue("ActiveMQ.DLQ"));

      // Send the messages
      producer.send(session.createTextMessage("1st"));
      producer.send(session.createTextMessage("2nd"));
      session.commit();

      TextMessage m;
      m = (TextMessage) consumer.receive(1000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      m = (TextMessage) consumer.receive(1000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      // The last rollback should cause the 1st message to get sent to the DLQ
      m = (TextMessage) consumer.receive(1000);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.commit();

      // We should be able to get the message off the DLQ now.
      m = (TextMessage) dlqConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      String cause = m.getStringProperty(ActiveMQMessage.DLQ_DELIVERY_FAILURE_CAUSE_PROPERTY);
      assertTrue(cause.contains("RedeliveryPolicy"), "cause exception has policy ref");
      session.commit();
   }

   @Test
   public void testInfiniteMaximumNumberOfRedeliveries() throws Exception {

      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setInitialRedeliveryDelay(100);
      policy.setUseExponentialBackOff(false);
      // let's set the maximum redeliveries to no maximum (ie. infinite)
      policy.setMaximumRedeliveries(-1);

      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");
      MessageProducer producer = session.createProducer(destination);

      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      producer.send(session.createTextMessage("1st"));
      producer.send(session.createTextMessage("2nd"));
      session.commit();

      TextMessage m;

      m = (TextMessage) consumer.receive(1000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      // we should be able to get the 1st message redelivered until a
      // session.commit is called
      m = (TextMessage) consumer.receive(1000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.commit();

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.commit();
   }

   @Test
   public void testMaximumRedeliveryDelay() throws Exception {

      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setInitialRedeliveryDelay(10);
      policy.setUseExponentialBackOff(true);
      policy.setMaximumRedeliveries(-1);
      policy.setRedeliveryDelay(50);
      policy.setMaximumRedeliveryDelay(1000);
      policy.setBackOffMultiplier((short) 2);
      policy.setUseExponentialBackOff(true);

      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");
      MessageProducer producer = session.createProducer(destination);

      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      producer.send(session.createTextMessage("1st"));
      producer.send(session.createTextMessage("2nd"));
      session.commit();

      TextMessage m;

      for (int i = 0; i < 10; ++i) {
         // we should be able to get the 1st message redelivered until a
         // session.commit is called
         m = (TextMessage) consumer.receive(2000);
         assertNotNull(m);
         assertEquals("1st", m.getText());
         session.rollback();
      }

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.commit();

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.commit();

      assertEquals(1000, policy.getNextRedeliveryDelay(Long.MAX_VALUE));
   }

   @Test
   public void testZeroMaximumNumberOfRedeliveries() throws Exception {

      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setInitialRedeliveryDelay(100);
      policy.setUseExponentialBackOff(false);
      // let's set the maximum redeliveries to 0
      policy.setMaximumRedeliveries(0);

      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");
      MessageProducer producer = session.createProducer(destination);

      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      producer.send(session.createTextMessage("1st"));
      producer.send(session.createTextMessage("2nd"));
      session.commit();

      TextMessage m;
      m = (TextMessage) consumer.receive(1000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      // the 1st message should not be redelivered since maximumRedeliveries is
      // set to 0
      m = (TextMessage) consumer.receive(1000);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.commit();

   }

   @Test
   public void testRedeliveredMessageNotOverflowingPrefetch() throws Exception {
      final int prefetchSize = 10;
      final int messageCount = 2 * prefetchSize;

      connection.getPrefetchPolicy().setAll(prefetchSize);
      connection.start();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");

      QueueControl queueControl = server.getManagementService().getQueueControl("TEST");

      MessageProducer producer = session.createProducer(destination);
      for (int i = 0; i < messageCount; i++) {
         producer.send(session.createTextMessage("MSG" + i));
         session.commit();
      }

      Message m;
      MessageConsumer consumer = session.createConsumer(destination);
      Wait.assertEquals(prefetchSize, () -> queueControl.getDeliveringCount(), 3000, 100);

      for (int i = 0; i < messageCount; i++) {
         m = consumer.receive(2000);
         assertNotNull(m, "null@:" + i);
         if (i == 3) {
            session.rollback();
            continue;
         }
         session.commit();
         assertTrue(queueControl.getDeliveringCount() <= prefetchSize + 1);
      }

      m = consumer.receive(2000);
      assertNotNull(m);
      session.commit();

   }

   @Test
   public void testCanRollbackPastPrefetch() throws Exception {
      final int prefetchSize = 10;
      final int messageCount = 2 * prefetchSize;

      connection.getPrefetchPolicy().setAll(prefetchSize);
      connection.getRedeliveryPolicy().setMaximumRedeliveries(prefetchSize + 1);
      connection.start();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");

      QueueControl queueControl = server.getManagementService().getQueueControl("TEST");

      MessageProducer producer = session.createProducer(destination);
      for (int i = 0; i < messageCount; i++) {
         producer.send(session.createTextMessage("MSG" + i));
         session.commit();
      }

      Message m;
      MessageConsumer consumer = session.createConsumer(destination);
      Wait.assertEquals(prefetchSize, () -> queueControl.getDeliveringCount(), 3000, 100);

      // do prefetch num rollbacks
      for (int i = 0; i < prefetchSize; i++) {
         m = consumer.receive(2000);
         assertNotNull(m, "null@:" + i);
         session.rollback();
      }

      // then try and consume
      for (int i = 0; i < messageCount; i++) {
         m = consumer.receive(2000);
         assertNotNull(m, "null@:" + i);
         session.commit();

         assertTrue(queueControl.getDeliveringCount() <= prefetchSize, "deliveringCount: " + queueControl.getDeliveringCount() + " @:" + i);
      }
   }

   @Test
   public void testCountersAreCorrectAfterSendToDLQ() throws Exception {
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setMaximumRedeliveries(0);
      policy.setInitialRedeliveryDelay(0);

      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);

      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");

      QueueControl queueControl = server.getManagementService().getQueueControl("TEST");

      MessageProducer producer = session.createProducer(destination);
      producer.send(session.createTextMessage("The Message"));
      session.commit();

      Message m;
      MessageConsumer consumer = session.createConsumer(destination);
      m = consumer.receive(2000);
      assertNotNull(m);
      session.rollback();

      Wait.assertEquals(0, () -> queueControl.getMessageCount());
      session.close();

      Wait.assertEquals(0L, queueControl::getPersistentSize, 5000, 100);
   }

   @Test
   public void testRedeliveryRefCleanup() throws Exception {

      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setUseExponentialBackOff(false);
      policy.setMaximumRedeliveries(-1);
      policy.setRedeliveryDelay(50);

      connection.start();
      Session pSession = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      Session cSession = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");
      MessageProducer producer = pSession.createProducer(destination);
      MessageConsumer consumer = cSession.createConsumer(destination);

      TextMessage m;

      for (int i = 0; i < 5; ++i) {
         producer.send(pSession.createTextMessage("MessageText"));
         pSession.commit();
         m = (TextMessage) consumer.receive(2000);
         assertNotNull(m);
         cSession.rollback();
         m = (TextMessage) consumer.receive(2000);
         assertNotNull(m);
         cSession.commit();
      }

      ServerConsumer serverConsumer = null;
      for (ServerSession session : server.getSessions()) {
         for (ServerConsumer sessionConsumer : session.getServerConsumers()) {
            if (sessionConsumer.getQueue().getName().toString() == "TEST") {
               serverConsumer = sessionConsumer;
            }
         }
      }

      AMQConsumer amqConsumer = (AMQConsumer) serverConsumer.getProtocolData();
      assertTrue(AMQConsumerAccessor.getRolledbackMessageRefs(amqConsumer).isEmpty());

   }

   @Test
   public void testInitialRedeliveryDelayZero() throws Exception {
      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setInitialRedeliveryDelay(0);
      policy.setUseExponentialBackOff(false);
      policy.setMaximumRedeliveries(1);

      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");
      MessageProducer producer = session.createProducer(destination);

      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      producer.send(session.createTextMessage("1st"));
      producer.send(session.createTextMessage("2nd"));
      session.commit();

      TextMessage m;
      m = (TextMessage) consumer.receive(100);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      m = (TextMessage) consumer.receive(100);
      assertNotNull(m);
      assertEquals("1st", m.getText());

      m = (TextMessage) consumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.commit();

      session.commit();
   }

   @Test
   public void testInitialRedeliveryDelayOne() throws Exception {
      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setInitialRedeliveryDelay(1000);
      policy.setUseExponentialBackOff(false);
      policy.setMaximumRedeliveries(1);

      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");
      MessageProducer producer = session.createProducer(destination);

      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      producer.send(session.createTextMessage("1st"));
      producer.send(session.createTextMessage("2nd"));
      session.commit();

      TextMessage m;
      m = (TextMessage) consumer.receive(100);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      m = (TextMessage) consumer.receive(100);
      assertNull(m);

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());

      m = (TextMessage) consumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.commit();
   }

   @Test
   public void testRedeliveryDelayOne() throws Exception {
      // Receive a message with the JMS API
      RedeliveryPolicy policy = connection.getRedeliveryPolicy();
      policy.setInitialRedeliveryDelay(0);
      policy.setRedeliveryDelay(1000);
      policy.setUseExponentialBackOff(false);
      policy.setMaximumRedeliveries(2);

      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      this.makeSureCoreQueueExist("TEST");
      MessageProducer producer = session.createProducer(destination);

      MessageConsumer consumer = session.createConsumer(destination);

      // Send the messages
      producer.send(session.createTextMessage("1st"));
      producer.send(session.createTextMessage("2nd"));
      session.commit();

      TextMessage m;
      m = (TextMessage) consumer.receive(100);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      session.rollback();

      m = (TextMessage) consumer.receive(100);
      assertNotNull(m, "first immediate redelivery");
      session.rollback();

      m = (TextMessage) consumer.receive(100);
      assertNull(m, "second delivery delayed: " + m);

      m = (TextMessage) consumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());

      m = (TextMessage) consumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.commit();
   }

   private void send(Session session, MessageProducer producer, Destination destination, String text) throws Exception {
      Message message = session.createTextMessage(text);
      message.setStringProperty("texto", text);
      producer.send(destination, message);
   }

   @Test
   public void testRedeliveryPolicyPerDestination() throws Exception {
      RedeliveryPolicy queuePolicy = new RedeliveryPolicy();
      queuePolicy.setInitialRedeliveryDelay(0);
      queuePolicy.setRedeliveryDelay(1000);
      queuePolicy.setUseExponentialBackOff(false);
      queuePolicy.setMaximumRedeliveries(2);

      RedeliveryPolicy topicPolicy = new RedeliveryPolicy();
      topicPolicy.setInitialRedeliveryDelay(0);
      topicPolicy.setRedeliveryDelay(1000);
      topicPolicy.setUseExponentialBackOff(false);
      topicPolicy.setMaximumRedeliveries(3);

      // Receive a message with the JMS API
      RedeliveryPolicyMap map = connection.getRedeliveryPolicyMap();
      map.put(new ActiveMQTopic(">"), topicPolicy);
      map.put(new ActiveMQQueue(">"), queuePolicy);

      connection.setClientID("id1");
      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue queue = new ActiveMQQueue("TEST");
      ActiveMQTopic topic = new ActiveMQTopic("TESTTOPIC");
      this.makeSureCoreQueueExist("TEST");

      MessageProducer producer = session.createProducer(null);
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      MessageConsumer queueConsumer = session.createConsumer(queue);
      MessageConsumer topicConsumer = session.createDurableSubscriber(topic, "tp1");

      // Send the messages
      send(session, producer, queue, "1st");
      send(session, producer, queue, "2nd");
      send(session, producer, topic, "1st");
      send(session, producer, topic, "2nd");

      session.commit();

      TextMessage m;
      m = (TextMessage) queueConsumer.receive(100);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      m = (TextMessage) topicConsumer.receive(100);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      m = (TextMessage) queueConsumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      m = (TextMessage) topicConsumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.rollback();

      m = (TextMessage) queueConsumer.receive(100);
      assertNotNull(m, "first immediate redelivery");
      m = (TextMessage) topicConsumer.receive(100);
      assertNotNull(m, "first immediate redelivery");
      session.rollback();

      m = (TextMessage) queueConsumer.receive(100);
      assertNull(m, "second delivery delayed: " + m);
      m = (TextMessage) topicConsumer.receive(100);
      assertNull(m, "second delivery delayed: " + m);

      m = (TextMessage) queueConsumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      m = (TextMessage) topicConsumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());

      m = (TextMessage) queueConsumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      m = (TextMessage) topicConsumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.rollback();

      m = (TextMessage) queueConsumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());
      m = (TextMessage) topicConsumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());

      m = (TextMessage) queueConsumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      m = (TextMessage) topicConsumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.rollback();

      // No third attempt for the Queue consumer
      m = (TextMessage) queueConsumer.receive(2000);
      assertNull(m);
      m = (TextMessage) topicConsumer.receive(2000);
      assertNotNull(m);
      assertEquals("1st", m.getText());

      m = (TextMessage) queueConsumer.receive(100);
      assertNull(m);
      m = (TextMessage) topicConsumer.receive(100);
      assertNotNull(m);
      assertEquals("2nd", m.getText());
      session.commit();
   }

   @Test
   public void testClientRedlivery() throws Exception {

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         this.makeSureCoreQueueExist("TEST");

         Queue queue = session.createQueue("TEST");

         MessageProducer producer = session.createProducer(queue);

         producer.send(session.createTextMessage("test"));

      } finally {
         connection.close();
      }

      for (int i = 0; i < 10; ++i) {

         connection = (ActiveMQConnection) factory.createConnection();

         connection.start();

         try {

            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            Queue queue = session.createQueue("TEST");

            MessageConsumer consumer = session.createConsumer(queue);

            Message message = consumer.receive(1000);

            assertNotNull(message, "Message null on iteration " + i);

            if (i > 0) {
               assertTrue(message.getJMSRedelivered());
            }

         } finally {
            connection.close();
         }
      }

   }

   @Test
   public void verifyNoRedeliveryFlagAfterCloseNoReceive() throws Exception {

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         this.makeSureCoreQueueExist("TEST");

         Queue queue = session.createQueue("TEST");

         MessageProducer producer = session.createProducer(queue);

         producer.send(session.createTextMessage("test"));

      } finally {
         connection.close();
      }

      connection = (ActiveMQConnection) factory.createConnection();

      connection.start();

      try {

         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         Queue queue = session.createQueue("TEST");

         MessageConsumer consumer = session.createConsumer(queue);
         TimeUnit.MILLISECONDS.sleep(500);
         // nothing received
         consumer.close();

         // try again, expect no redelivery flag
         consumer = session.createConsumer(queue);
         Message message = consumer.receive(1000);

         assertNotNull(message, "Message null");

         assertFalse(message.getJMSRedelivered());

      } finally {
         connection.close();
      }
   }


}
