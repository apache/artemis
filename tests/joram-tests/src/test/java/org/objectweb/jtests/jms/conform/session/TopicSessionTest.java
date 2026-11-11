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
package org.objectweb.jtests.jms.conform.session;

import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PubSubTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

/**
 * Test topic sessions
 * <p>
 * See JMS specifications, sec. 4.4 Session
 */
public class TopicSessionTest extends PubSubTestCase {

   /**
    * Test that if we rollback a transaction which has consumed a message, the message is effectively redelivered.
    */
   @Test
   public void testRollbackReceivedMessage() {
      try {
         publisherConnection.stop();
         // publisherSession has been declared has non transacted
         // we recreate it as a transacted session
         publisherSession = publisherConnection.createTopicSession(true, 0);
         Assert.assertTrue(publisherSession.getTransacted());
         // we also recreate the publisher
         publisher = publisherSession.createPublisher(publisherTopic);
         publisherConnection.start();

         subscriberConnection.stop();
         // subscriberSession has been declared has non transacted
         // we recreate it as a transacted session
         subscriberSession = subscriberConnection.createTopicSession(true, 0);
         Assert.assertTrue(subscriberSession.getTransacted());
         // we also recreate the subscriber
         subscriber = subscriberSession.createSubscriber(subscriberTopic);
         subscriberConnection.start();

         // we create a message...
         TextMessage message = publisherSession.createTextMessage();
         message.setText("testRollbackReceivedMessage");
         // ... publish it ...
         publisher.publish(message);
         // ... and commit the transaction
         publisherSession.commit();

         // we receive it
         Message msg1 = subscriber.receive(TestConfig.TIMEOUT);
         Assert.assertNotNull("no message received", msg1);
         Assert.assertTrue(msg1 instanceof TextMessage);
         Assert.assertEquals("testRollbackReceivedMessage", ((TextMessage) msg1).getText());

         // we rollback the transaction of subscriberSession
         subscriberSession.rollback();

         // we expect to receive a second time the message
         Message msg2 = subscriber.receive(TestConfig.TIMEOUT);
         Assert.assertNotNull("no message received after rollbacking subscriber session.", msg2);
         Assert.assertTrue(msg2 instanceof TextMessage);
         Assert.assertEquals("testRollbackReceivedMessage", ((TextMessage) msg2).getText());

         // finally we commit the subscriberSession transaction
         subscriberSession.commit();
      } catch (Exception e) {
         fail(e);
      }
   }

   /**
    * Test that a durable subscriber effectively receives the messages sent to its topic while it was inactive.
    */
   @Test
   public void testDurableSubscriber() {
      try {
         subscriber = subscriberSession.createDurableSubscriber(subscriberTopic, "testTopic");
         subscriberConnection.close();
         subscriberConnection = null;

         TextMessage message = publisherSession.createTextMessage();
         message.setText("test");
         publisher.publish(message);

         subscriberConnection = subscriberTCF.createTopicConnection();
         subscriberConnection.setClientID("subscriberConnection");
         subscriberSession = subscriberConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
         subscriber = subscriberSession.createDurableSubscriber(subscriberTopic, "testTopic");
         subscriberConnection.start();

         TextMessage m = (TextMessage) subscriber.receive(TestConfig.TIMEOUT);
         Assert.assertNotNull(m);
         Assert.assertEquals("test", m.getText());
         subscriber.close();
         subscriberSession.unsubscribe("testTopic");
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test the unsubscription of a durable subscriber.
    */
   @Test
   public void testUnsubscribe() {
      try {
         subscriber = subscriberSession.createDurableSubscriber(subscriberTopic, "topic");
         subscriber.close();
         // nothing should happen when unsubscribing the durable subscriber
         subscriberSession.unsubscribe("topic");
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that a call to the {@code createDurableSubscriber()} method with an invalid message selector throws a
    * {@code javax.jms.InvalidSelectorException}.
    */
   @Test
   public void testCreateDurableSubscriber_2() {
      try {
         subscriberSession.createDurableSubscriber(subscriberTopic, "topic", "definitely not a message selector!", true);
         Assert.fail("Should throw a javax.jms.InvalidSelectorException.\n");
      } catch (InvalidSelectorException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.InvalidSelectorException, not a " + e);
      }
   }

   /**
    * Test that a call to the {@code createDurableSubscriber()} method with an invalid {@code Topic} throws a
    * {@code javax.jms.InvalidDestinationException}.
    */
   @Test
   public void testCreateDurableSubscriber_1() {
      try {
         subscriberSession.createDurableSubscriber((Topic) null, "topic");
         Assert.fail("Should throw a javax.jms.InvalidDestinationException.\n");
      } catch (InvalidDestinationException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.InvalidDestinationException, not a " + e);
      }
   }

   /**
    * Test that a call to the {@code createSubscriber()} method with an invalid message selector throws a
    * {@code javax.jms.InvalidSelectorException}.
    */
   @Test
   public void testCreateSubscriber_2() {
      try {
         subscriberSession.createSubscriber(subscriberTopic, "definitely not a message selector!", true);
         Assert.fail("Should throw a javax.jms.InvalidSelectorException.\n");
      } catch (InvalidSelectorException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.InvalidSelectorException, not a " + e);
      }
   }

   /**
    * Test that a call to the {@code createSubscriber()} method with an invalid {@code Topic} throws a
    * {@code javax.jms.InvalidDestinationException}.
    */
   @Test
   public void testCreateSubscriber_1() {
      try {
         subscriberSession.createSubscriber((Topic) null);
         Assert.fail("Should throw a javax.jms.InvalidDestinationException.\n");
      } catch (InvalidDestinationException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.InvalidDestinationException, not a " + e);
      }
   }
}
