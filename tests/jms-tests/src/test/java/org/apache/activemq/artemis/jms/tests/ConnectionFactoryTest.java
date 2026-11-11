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
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnection;
import javax.jms.XATopicConnectionFactory;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class ConnectionFactoryTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Random random = new Random();
   private String testClientId;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      testClientId = "amq" + random.nextInt();
   }

   /**
    * Test that ConnectionFactory can be cast to QueueConnectionFactory and QueueConnection can be created.
    */
   @Test
   public void testQueueConnectionFactory() throws Exception {
      deployConnectionFactory(0, JMSFactoryType.QUEUE_CF, "CF_QUEUE_XA_FALSE", "/CF_QUEUE_XA_FALSE");
      QueueConnectionFactory qcf = (QueueConnectionFactory) ic.lookup("/CF_QUEUE_XA_FALSE");
      QueueConnection qc = qcf.createQueueConnection();
      qc.close();
      undeployConnectionFactory("CF_QUEUE_XA_FALSE");
   }

   /**
    * Test that ConnectionFactory can be cast to TopicConnectionFactory and TopicConnection can be created.
    */
   @Test
   public void testTopicConnectionFactory() throws Exception {
      deployConnectionFactory(0, JMSFactoryType.TOPIC_CF, "CF_TOPIC_XA_FALSE", "/CF_TOPIC_XA_FALSE");
      TopicConnectionFactory qcf = (TopicConnectionFactory) ic.lookup("/CF_TOPIC_XA_FALSE");
      TopicConnection tc = qcf.createTopicConnection();
      tc.close();
      undeployConnectionFactory("CF_TOPIC_XA_FALSE");
   }

   @Test
   public void testAdministrativelyConfiguredClientID() throws Exception {
      // deploy a connection factory that has an administatively configured clientID
      ActiveMQServerTestCase.deployConnectionFactory(testClientId, "TestConnectionFactory", "TestConnectionFactory");

      ConnectionFactory cf = (ConnectionFactory) ic.lookup("/TestConnectionFactory");
      Connection c = cf.createConnection();

      ProxyAssertSupport.assertEquals(testClientId, c.getClientID());

      try {
         c.setClientID("somethingelse");
         ProxyAssertSupport.fail("should throw exception");

      } catch (javax.jms.IllegalStateException e) {
         // OK
      }
      c.close();
      ActiveMQServerTestCase.undeployConnectionFactory("TestConnectionFactory");
   }

   @Test
   public void testNoClientIDConfigured_1() throws Exception {
      // the ConnectionFactories that ship with Apache Artemis do not have their clientID
      // administratively configured.

      deployConnectionFactory(0, JMSFactoryType.TOPIC_CF, "CF_XA_FALSE", "/CF_XA_FALSE");
      ConnectionFactory cf = (ConnectionFactory) ic.lookup("/CF_XA_FALSE");
      Connection c = cf.createConnection();

      ProxyAssertSupport.assertNull(c.getClientID());

      c.close();
      undeployConnectionFactory("CF_XA_FALSE");
   }

   @Test
   public void testNoClientIDConfigured_2() throws Exception {
      // the ConnectionFactories that ship with Apache Artemis do not have their clientID
      // administratively configured.

      deployConnectionFactory(0, JMSFactoryType.TOPIC_CF, "CF_XA_FALSE", "/CF_XA_FALSE");
      ConnectionFactory cf = (ConnectionFactory) ic.lookup("/CF_XA_FALSE");
      Connection c = cf.createConnection();

      // set the client id immediately after the connection is created

      c.setClientID(testClientId);
      ProxyAssertSupport.assertEquals(testClientId, c.getClientID());

      c.close();
      undeployConnectionFactory("CF_XA_FALSE");
   }

   @Test
   public void testDurableSubscriptionOnPreConfiguredConnectionFactory() throws Exception {
      ActiveMQServerTestCase.deployConnectionFactory("TestConnectionFactory1", "cfTest", "/TestDurableCF");

      createTopic("TestSubscriber");

      Connection conn = null;

      try {
         Topic topic = (Topic) ic.lookup("/topic/TestSubscriber");
         ConnectionFactory cf = (ConnectionFactory) ic.lookup("/TestDurableCF");
         conn = cf.createConnection();

         // I have to remove this asertion, as the test would work if doing this assertion
         // as getClientID performed some operation that cleared the bug condition during
         // the creation of this testcase
         // Assert.assertEquals("cfTest", conn.getClientID());

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         session.createDurableSubscriber(topic, "durableSubscriberChangeSelectorTest", "TEST = 'test'", false);
      } finally {
         try {
            if (conn != null) {
               conn.close();
            }
         } catch (Exception e) {
            logger.warn(e.toString(), e);
         }

         try {
            destroyTopic("TestSubscriber");
         } catch (Exception e) {
            logger.warn(e.toString(), e);
         }

      }

   }

   @Test
   public void testSlowConsumers() throws Exception {
      List<String> bindings = new ArrayList<>();
      bindings.add("TestSlowConsumersCF");
      ActiveMQServerTestCase.deployConnectionFactory(0, "TestSlowConsumersCF", 1, "TestSlowConsumersCF");

      Connection conn = null;

      try {
         ConnectionFactory cf = (ConnectionFactory) ic.lookup("/TestSlowConsumersCF");

         conn = cf.createConnection();

         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final Object waitLock = new Object();

         final int numMessages = 500;

         class FastListener implements MessageListener {

            int processed;

            @Override
            public void onMessage(final Message msg) {
               processed++;

               if (processed == numMessages - 2) {
                  synchronized (waitLock) {
                     waitLock.notifyAll();
                  }
               }
            }
         }

         final FastListener fast = new FastListener();

         class SlowListener implements MessageListener {

            int processed;

            @Override
            public void onMessage(final Message msg) {
               processed++;

               synchronized (waitLock) {
                  // Should really cope with spurious wakeups
                  while (fast.processed != numMessages - 2) {
                     try {
                        waitLock.wait(20000);
                     } catch (InterruptedException e) {
                     }
                  }

                  waitLock.notify();
               }
            }
         }

         final SlowListener slow = new SlowListener();

         MessageConsumer cons1 = session1.createConsumer(queue1);

         cons1.setMessageListener(slow);

         MessageConsumer cons2 = session2.createConsumer(queue1);

         cons2.setMessageListener(fast);

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(queue1);

         conn.start();

         for (int i = 0; i < numMessages; i++) {
            TextMessage tm = sessSend.createTextMessage("message" + i);

            prod.send(tm);
         }

         // All the messages bar one should be consumed by the fast listener - since the slow listener shouldn't buffer
         // any.

         synchronized (waitLock) {
            // Should really cope with spurious wakeups
            while (fast.processed != numMessages - 2) {
               waitLock.wait(20000);
            }

            while (slow.processed != 2) {
               waitLock.wait(20000);
            }
         }

         assertEquals(numMessages - 2, fast.processed);

      } finally {
         try {
            if (conn != null) {
               conn.close();
            }
         } catch (Exception e) {
            logger.warn(e.toString(), e);
         }

         try {
            ActiveMQServerTestCase.undeployConnectionFactory("TestSlowConsumersCF");
         } catch (Exception e) {
            logger.warn(e.toString(), e);
         }

      }

   }

   @Test
   public void testFactoryTypes() throws Exception {
      deployConnectionFactory(0, JMSFactoryType.CF, "ConnectionFactory", "/ConnectionFactory");
      deployConnectionFactory(0, JMSFactoryType.QUEUE_XA_CF, "CF_QUEUE_XA_TRUE", "/CF_QUEUE_XA_TRUE");
      deployConnectionFactory(0, JMSFactoryType.QUEUE_CF, "CF_QUEUE_XA_FALSE", "/CF_QUEUE_XA_FALSE");
      deployConnectionFactory(0, JMSFactoryType.XA_CF, "CF_XA_TRUE", "/CF_XA_TRUE");
      deployConnectionFactory(0, JMSFactoryType.CF, "CF_XA_FALSE", "/CF_XA_FALSE");
      deployConnectionFactory(0, JMSFactoryType.QUEUE_CF, "CF_QUEUE", "/CF_QUEUE");
      deployConnectionFactory(0, JMSFactoryType.TOPIC_CF, "CF_TOPIC", "/CF_TOPIC");
      deployConnectionFactory(0, JMSFactoryType.TOPIC_XA_CF, "CF_TOPIC_XA_TRUE", "/CF_TOPIC_XA_TRUE");
      deployConnectionFactory(0, JMSFactoryType.CF, "CF_GENERIC", "/CF_GENERIC");
      deployConnectionFactory(0, JMSFactoryType.XA_CF, "CF_GENERIC_XA_TRUE", "/CF_GENERIC_XA_TRUE");
      deployConnectionFactory(0, JMSFactoryType.CF, "CF_GENERIC_XA_FALSE", "/CF_GENERIC_XA_FALSE");
      deployConnectionFactory(0, JMSFactoryType.TOPIC_CF, "CF_TOPIC_XA_FALSE", "/CF_TOPIC_XA_FALSE");

      ActiveMQConnectionFactory factory = null;

      factory = (ActiveMQConnectionFactory) ic.lookup("/ConnectionFactory");

      assertInstanceOf(ConnectionFactory.class, factory);
      assertNTypes(factory, 4);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_XA_TRUE");

      assertInstanceOf(XAConnectionFactory.class, factory);
      assertNTypes(factory, 6);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_XA_FALSE");

      assertInstanceOf(ConnectionFactory.class, factory);
      assertNTypes(factory, 4);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_GENERIC");

      assertInstanceOf(ConnectionFactory.class, factory);
      assertNTypes(factory, 4);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_GENERIC_XA_TRUE");

      assertInstanceOf(XAConnectionFactory.class, factory);
      assertNTypes(factory, 6);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_GENERIC_XA_FALSE");

      assertInstanceOf(ConnectionFactory.class, factory);
      assertNTypes(factory, 4);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_QUEUE");

      assertInstanceOf(QueueConnectionFactory.class, factory);
      assertNTypes(factory, 3);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_QUEUE_XA_TRUE");

      assertInstanceOf(XAQueueConnectionFactory.class, factory);
      assertNTypes(factory, 4);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_QUEUE_XA_FALSE");

      assertInstanceOf(QueueConnectionFactory.class, factory);
      assertNTypes(factory, 3);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_TOPIC");

      assertInstanceOf(TopicConnectionFactory.class, factory);
      assertNTypes(factory, 3);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_TOPIC_XA_TRUE");

      assertInstanceOf(XATopicConnectionFactory.class, factory);
      assertNTypes(factory, 4);

      factory = (ActiveMQConnectionFactory) ic.lookup("/CF_TOPIC_XA_FALSE");

      assertInstanceOf(TopicConnectionFactory.class, factory);
      assertNTypes(factory, 3);

      undeployConnectionFactory("ConnectionFactory");
      undeployConnectionFactory("CF_QUEUE_XA_TRUE");
      undeployConnectionFactory("CF_QUEUE_XA_FALSE");
      undeployConnectionFactory("CF_XA_TRUE");
      undeployConnectionFactory("CF_XA_FALSE");
      undeployConnectionFactory("CF_QUEUE");
      undeployConnectionFactory("CF_TOPIC");
      undeployConnectionFactory("CF_TOPIC_XA_TRUE");
      undeployConnectionFactory("CF_GENERIC");
      undeployConnectionFactory("CF_GENERIC_XA_TRUE");
      undeployConnectionFactory("CF_GENERIC_XA_FALSE");
      undeployConnectionFactory("CF_TOPIC_XA_FALSE");
   }

   @Test
   public void testConnectionTypes() throws Exception {
      deployConnectionFactory(0, JMSFactoryType.CF, "ConnectionFactory", "/ConnectionFactory");
      deployConnectionFactory(0, JMSFactoryType.QUEUE_XA_CF, "CF_QUEUE_XA_TRUE", "/CF_QUEUE_XA_TRUE");
      deployConnectionFactory(0, JMSFactoryType.XA_CF, "CF_XA_TRUE", "/CF_XA_TRUE");
      deployConnectionFactory(0, JMSFactoryType.QUEUE_CF, "CF_QUEUE", "/CF_QUEUE");
      deployConnectionFactory(0, JMSFactoryType.TOPIC_CF, "CF_TOPIC", "/CF_TOPIC");
      deployConnectionFactory(0, JMSFactoryType.TOPIC_XA_CF, "CF_TOPIC_XA_TRUE", "/CF_TOPIC_XA_TRUE");

      Connection genericConnection = null;
      XAConnection xaConnection = null;
      QueueConnection queueConnection = null;
      TopicConnection topicConnection = null;
      XAQueueConnection xaQueueConnection = null;
      XATopicConnection xaTopicConnection = null;

      ConnectionFactory genericFactory = (ConnectionFactory) ic.lookup("/ConnectionFactory");
      genericConnection = genericFactory.createConnection();
      assertConnectionType(genericConnection, "generic");

      XAConnectionFactory xaFactory = (XAConnectionFactory) ic.lookup("/CF_XA_TRUE");
      xaConnection = xaFactory.createXAConnection();
      assertConnectionType(xaConnection, "xa");

      QueueConnectionFactory queueCF = (QueueConnectionFactory) ic.lookup("/CF_QUEUE");
      queueConnection = queueCF.createQueueConnection();
      assertConnectionType(queueConnection, "queue");

      TopicConnectionFactory topicCF = (TopicConnectionFactory) ic.lookup("/CF_TOPIC");
      topicConnection = topicCF.createTopicConnection();
      assertConnectionType(topicConnection, "topic");

      XAQueueConnectionFactory xaQueueCF = (XAQueueConnectionFactory) ic.lookup("/CF_QUEUE_XA_TRUE");
      xaQueueConnection = xaQueueCF.createXAQueueConnection();
      assertConnectionType(xaQueueConnection, "xa-queue");

      XATopicConnectionFactory xaTopicCF = (XATopicConnectionFactory) ic.lookup("/CF_TOPIC_XA_TRUE");
      xaTopicConnection = xaTopicCF.createXATopicConnection();
      assertConnectionType(xaTopicConnection, "xa-topic");

      genericConnection.close();
      xaConnection.close();
      queueConnection.close();
      topicConnection.close();
      xaQueueConnection.close();
      xaTopicConnection.close();

      undeployConnectionFactory("ConnectionFactory");
      undeployConnectionFactory("CF_QUEUE_XA_TRUE");
      undeployConnectionFactory("CF_XA_TRUE");
      undeployConnectionFactory("CF_QUEUE");
      undeployConnectionFactory("CF_TOPIC");
      undeployConnectionFactory("CF_TOPIC_XA_TRUE");
   }

   private void assertConnectionType(Connection conn, String type) {
      if ("generic".equals(type) || "queue".equals(type) || "topic".equals(type)) {
         //generic
         assertFalse(conn instanceof XAConnection);
         assertInstanceOf(QueueConnection.class, conn);
         assertFalse(conn instanceof XAQueueConnection);
         assertInstanceOf(TopicConnection.class, conn);
         assertFalse(conn instanceof XATopicConnection);
      } else if ("xa".equals(type) || "xa-queue".equals(type) || "xa-topic".equals(type)) {
         assertInstanceOf(XAConnection.class, conn);
         assertInstanceOf(QueueConnection.class, conn);
         assertInstanceOf(XAQueueConnection.class, conn);
         assertInstanceOf(TopicConnection.class, conn);
         assertInstanceOf(XATopicConnection.class, conn);
      } else {
         fail("Unknown connection type: " + type);
      }
   }

   private void assertNTypes(ActiveMQConnectionFactory factory, final int total) {
      StringBuilder text = new StringBuilder();
      text.append(factory + "\n is instance of ");
      int num = 0;
      if (factory instanceof ConnectionFactory) {
         num++;
         text.append("ConnectionFactory ");
      }
      if (factory instanceof XAConnectionFactory) {
         num++;
         text.append("XAConnectionFactory ");
      }
      if (factory instanceof QueueConnectionFactory) {
         num++;
         text.append("QueueConnectionFactory ");
      }
      if (factory instanceof TopicConnectionFactory) {
         num++;
         text.append("TopicConnectionFactory ");
      }
      if (factory instanceof XAQueueConnectionFactory) {
         num++;
         text.append("XAQueueConnectionFactory ");
      }
      if (factory instanceof XATopicConnectionFactory) {
         num++;
         text.append("XATopicConnectionFactory ");
      }
      assertEquals(total, num, text.toString());
   }
}
