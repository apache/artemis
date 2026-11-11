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

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.UnifiedTestCase;

/**
 * Test unified JMS 1.1 sessions.
 * <p>
 * See JMS 1.1 specifications
 *
 * @since JMS 1.1
 */
public class UnifiedSessionTest extends UnifiedTestCase {

   protected QueueConnection queueConnection;

   /**
    * QueueSession (non transacted, AUTO_ACKNOWLEDGE)
    */
   protected QueueSession queueSession;

   protected TopicConnection topicConnection;

   /**
    * TopicSession (non transacted, AUTO_ACKNOWLEDGE)
    */
   protected TopicSession topicSession;

   /**
    * Test that a call to {@code createDurableConnectionConsumer()} method on a {@code QueueConnection} throws a
    * {@code javax.jms.IllegalStateException}. (see JMS 1.1 specs, table 4-1).
    *
    * @since JMS 1.1
    */
   @Test
   public void testCreateDurableConnectionConsumerOnQueueConnection() {
      try {
         queueConnection.createDurableConnectionConsumer(topic, "subscriptionName", "", (ServerSessionPool) null, 1);
         Assert.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that a call to {@code createDurableSubscriber()} method on a {@code QueueSession} throws a
    * {@code javax.jms.IllegalStateException}. (see JMS 1.1 specs, table 4-1).
    *
    * @since JMS 1.1
    */
   @Test
   public void testCreateDurableSubscriberOnQueueSession() {
      try {
         queueSession.createDurableSubscriber(topic, "subscriptionName");
         Assert.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that a call to {@code createTemporaryTopic()} method on a {@code QueueSession} throws a
    * {@code javax.jms.IllegalStateException}. (see JMS 1.1 specs, table 4-1).
    *
    * @since JMS 1.1
    */
   @Test
   public void testCreateTemporaryTopicOnQueueSession() {
      try {
         queueSession.createTemporaryTopic();
         Assert.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that a call to {@code createTopic()} method on a {@code QueueSession} throws a
    * {@code javax.jms.IllegalStateException}. (see JMS 1.1 specs, table 4-1).
    *
    * @since JMS 1.1
    */
   @Test
   public void testCreateTopicOnQueueSession() {
      try {
         queueSession.createTopic("topic_name");
         Assert.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that a call to {@code unsubscribe()} method on a {@code QueueSession} throws a
    * {@code javax.jms.IllegalStateException}. (see JMS 1.1 specs, table 4-1).
    *
    * @since JMS 1.1
    */
   @Test
   public void testUnsubscribeOnQueueSession() {
      try {
         queueSession.unsubscribe("subscriptionName");
         Assert.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that a call to {@code createBrowser()} method on a {@code TopicSession} throws a
    * {@code javax.jms.IllegalStateException}. (see JMS 1.1 specs, table 4-1).
    *
    * @since JMS 1.1
    */
   @Test
   public void testCreateBrowserOnTopicSession() {
      try {
         topicSession.createBrowser(queue);
         Assert.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that a call to {@code createQueue()} method on a {@code TopicSession} throws a
    * {@code javax.jms.IllegalStateException}. (see JMS 1.1 specs, table 4-1).
    *
    * @since JMS 1.1
    */
   @Test
   public void testCreateQueueOnTopicSession() {
      try {
         topicSession.createQueue("queue_name");
         Assert.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that a call to {@code createTemporaryQueue()} method on a {@code TopicSession} throws a
    * {@code javax.jms.IllegalStateException}. (see JMS 1.1 specs, table 4-1).
    *
    * @since JMS 1.1
    */
   @Test
   public void testCreateTemporaryQueueOnTopicSession() {
      try {
         topicSession.createTemporaryQueue();
         Assert.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.IllegalStateException, not a " + e);
      }
   }

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      try {
         queueConnection = queueConnectionFactory.createQueueConnection();
         queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
         topicConnection = topicConnectionFactory.createTopicConnection();
         topicSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

         queueConnection.start();
         topicConnection.start();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   @After
   public void tearDown() throws Exception {
      try {
         queueConnection.close();
         topicConnection.close();
      } catch (Exception ignored) {
      } finally {
         queueConnection = null;
         queueSession = null;
         topicConnection = null;
         topicSession = null;
         super.tearDown();
      }
   }
}
