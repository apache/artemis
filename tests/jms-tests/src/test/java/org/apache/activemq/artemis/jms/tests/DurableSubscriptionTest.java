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
package org.apache.activemq.artemis.jms.tests;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.naming.InitialContext;
import java.util.List;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

/**
 * Tests focused on durable subscription behavior. More durable subscription tests can be found in MessageConsumerTest.
 */
public class DurableSubscriptionTest extends JMSTestCase {



   @Test
   public void testSimplestDurableSubscription() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.setClientID("brookeburke");

         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = s.createProducer(ActiveMQServerTestCase.topic1);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "monicabelucci");

         List<String> subs = listAllSubscribersForTopic("Topic1");

         ProxyAssertSupport.assertNotNull(subs);

         ProxyAssertSupport.assertEquals(1, subs.size());

         ProxyAssertSupport.assertEquals("monicabelucci", subs.get(0));

         prod.send(s.createTextMessage("k"));

         conn.close();

         subs = listAllSubscribersForTopic("Topic1");

         ProxyAssertSupport.assertEquals(1, subs.size());

         ProxyAssertSupport.assertEquals("monicabelucci", subs.get(0));

         conn = createConnection();
         conn.setClientID("brookeburke");

         s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer durable = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "monicabelucci");

         conn.start();

         TextMessage tm = (TextMessage) durable.receive(1000);
         ProxyAssertSupport.assertEquals("k", tm.getText());

         Message m = durable.receiveNoWait();
         ProxyAssertSupport.assertNull(m);

         durable.close();

         s.unsubscribe("monicabelucci");
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   // https://issues.apache.org/jira/browse/ARTEMIS-177
   @Test
   public void testDurableSubscriptionRemovalRaceCondition() throws Exception {
      final String topicName = "myTopic";
      final String clientID = "myClientID";
      final String subscriptionName = "mySub";
      createTopic(topicName);
      InitialContext ic = getInitialContext();
      Topic myTopic = (Topic) ic.lookup("/topic/" + topicName);

      Connection conn = null;

      for (int i = 0; i < 1000; i++) {
         try {
            conn = createConnection();

            conn.setClientID(clientID);

            Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer prod = s.createProducer(myTopic);
            prod.setDeliveryMode(DeliveryMode.PERSISTENT);

            s.createDurableSubscriber(myTopic, subscriptionName);

            prod.send(s.createTextMessage("k"));

            conn.close();

            destroyTopic(topicName);

            createTopic(topicName);

            conn = createConnection();
            conn.setClientID(clientID);

            s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageConsumer durable = s.createDurableSubscriber(myTopic, subscriptionName);

            conn.start();

            TextMessage tm = (TextMessage) durable.receiveNoWait();
            ProxyAssertSupport.assertNull(tm);

            durable.close();

            s.unsubscribe(subscriptionName);
         } finally {
            if (conn != null) {
               conn.close();
            }
         }
      }
   }

   /**
    * JMS 1.1 6.11.1: A client can change an existing durable subscription by creating a durable TopicSubscriber with
    * the same name and a new topic and/or message selector, or NoLocal attribute. Changing a durable subscription is
    * equivalent to deleting and recreating it.
    * <p>
    * Test with a different topic (a redeployed topic is a different topic).
    */
   @Test
   public void testDurableSubscriptionOnNewTopic() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.setClientID("brookeburke");

         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = s.createProducer(ActiveMQServerTestCase.topic1);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "monicabelucci");

         prod.send(s.createTextMessage("one"));

         conn.close();

         conn = createConnection();

         conn.setClientID("brookeburke");

         s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer durable = s.createDurableSubscriber(ActiveMQServerTestCase.topic2, "monicabelucci");

         conn.start();

         Message m = durable.receiveNoWait();
         ProxyAssertSupport.assertNull(m);

         durable.close();

         s.unsubscribe("monicabelucci");
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   /**
    * JMS 1.1 6.11.1: A client can change an existing durable subscription by creating a durable TopicSubscriber with
    * the same name and a new topic and/or message selector, or NoLocal attribute. Changing a durable subscription is
    * equivalent to deleting and recreating it.
    * <p>
    * Test with a different selector.
    */
   @Test
   public void testDurableSubscriptionDifferentSelector() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         conn.setClientID("brookeburke");

         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = s.createProducer(ActiveMQServerTestCase.topic1);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         MessageConsumer durable = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "monicabelucci", "color = 'red' AND shape = 'square'", false);

         TextMessage tm = s.createTextMessage("A red square message");
         tm.setStringProperty("color", "red");
         tm.setStringProperty("shape", "square");

         prod.send(tm);

         conn.start();

         TextMessage rm = (TextMessage) durable.receive(5000);
         ProxyAssertSupport.assertEquals("A red square message", rm.getText());

         tm = s.createTextMessage("Another red square message");
         tm.setStringProperty("color", "red");
         tm.setStringProperty("shape", "square");
         prod.send(tm);

         // TODO: when subscriptions/durable subscription will be registered as MBean, use the JMX
         // interface to make sure the 'another red square message' is maintained by the
         // durable subascription

         conn.close();

         conn = createConnection();

         conn.setClientID("brookeburke");

         s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // modify the selector
         durable = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "monicabelucci", "color = 'red'", false);

         conn.start();

         Message m = durable.receiveNoWait();

         // the durable subscription is destroyed and re-created. The red square message stored by
         // the previous durable subscription is lost and (hopefully) garbage collected.
         ProxyAssertSupport.assertNull(m);

         durable.close();

         s.unsubscribe("monicabelucci");
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testDurableSubscriptionOnTemporaryTopic() throws Exception {
      Connection conn = null;

      conn = createConnection();

      try {
         conn.setClientID("doesn't actually matter");
         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic temporaryTopic = s.createTemporaryTopic();

         try {
            s.createDurableSubscriber(temporaryTopic, "mySubscription");
            ProxyAssertSupport.fail("this should throw exception");
         } catch (InvalidDestinationException e) {
            // OK
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testUnsubscribeDurableSubscription() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();
         conn.setClientID("ak47");

         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "uzzi");
         MessageProducer prod = s.createProducer(ActiveMQServerTestCase.topic1);
         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         prod.send(s.createTextMessage("one"));

         cons.close();
         s.unsubscribe("uzzi");

         MessageConsumer ds = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "uzzi");
         conn.start();

         ProxyAssertSupport.assertNull(ds.receiveNoWait());

         ds.close();

         s.unsubscribe("uzzi");
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testInvalidSelectorException() throws Exception {
      Connection c = createConnection();
      c.setClientID("sofiavergara");
      Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);

      try {
         s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "mysubscribption", "=TEST 'test'", true);
         ProxyAssertSupport.fail("this should fail");
      } catch (InvalidSelectorException e) {
         // OK
      }
   }

   // See JMS 1.1. spec sec 6.11
   @Test
   public void testUnsubscribeWithActiveConsumer() throws Exception {
      Connection conn = createConnection();
      conn.setClientID("zeke");

      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TopicSubscriber dursub = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "dursub0");

      try {
         s.unsubscribe("dursub0");
         ProxyAssertSupport.fail();
      } catch (IllegalStateException e) {
         // Ok - it is illegal to ubscribe a subscription if it has active consumers
      }

      dursub.close();

      s.unsubscribe("dursub0");
   }

   @Test
   public void testSubscribeWithActiveSubscription() throws Exception {
      Connection conn = createConnection();
      conn.setClientID("zeke");

      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TopicSubscriber dursub1 = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "dursub1");

      try {
         s.createDurableSubscriber(ActiveMQServerTestCase.topic1, "dursub1");
         ProxyAssertSupport.fail();
      } catch (IllegalStateException e) {
         // Ok - it is illegal to have more than one active subscriber on a subscrtiption at any one time
      }

      dursub1.close();

      s.unsubscribe("dursub1");
   }

   @Test
   public void testDurableSubscriptionWithPeriodsInName() throws Exception {
      Connection conn = createConnection();
      conn.setClientID(".client.id.with.periods.");

      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TopicSubscriber subscriber = s.createDurableSubscriber(ActiveMQServerTestCase.topic1, ".subscription.name.with.periods.");

      s.createProducer(ActiveMQServerTestCase.topic1).send(s.createTextMessage("Subscription test"));

      conn.start();

      Message m = subscriber.receive(1000L);

      ProxyAssertSupport.assertNotNull(m);
      ProxyAssertSupport.assertTrue(m instanceof TextMessage);

      subscriber.close();

      s.unsubscribe(".subscription.name.with.periods.");
   }

   @Test
   public void testNoLocal() throws Exception {
      internalTestNoLocal(true);
      internalTestNoLocal(false);
   }

   private void internalTestNoLocal(final boolean noLocal) throws Exception {

      Connection conn1 = createConnection();
      conn1.setClientID(".client.id.with.periods.");

      Connection conn2 = createConnection();
      conn2.setClientID(".client.id.with.periods2.");

      Session s1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Session s2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TopicSubscriber subscriber1 = s1.createDurableSubscriber(ActiveMQServerTestCase.topic1, ".subscription.name.with.periods.", null, noLocal);
      TopicSubscriber subscriber2 = s2.createDurableSubscriber(ActiveMQServerTestCase.topic1, ".subscription.name.with.periods.", null, false);

      s1.createProducer(ActiveMQServerTestCase.topic1).send(s1.createTextMessage("Subscription test"));

      conn1.start();

      Message m = subscriber1.receive(100L);

      if (noLocal) {
         ProxyAssertSupport.assertNull(m);
      } else {
         ProxyAssertSupport.assertNotNull(m);
      }

      conn2.start();

      m = subscriber2.receive(1000L);

      ProxyAssertSupport.assertNotNull(m);
      ProxyAssertSupport.assertTrue(m instanceof TextMessage);

      subscriber1.close();
      subscriber2.close();

      s1.unsubscribe(".subscription.name.with.periods.");
      s2.unsubscribe(".subscription.name.with.periods.");
      conn1.close();
      conn2.close();
   }
}
