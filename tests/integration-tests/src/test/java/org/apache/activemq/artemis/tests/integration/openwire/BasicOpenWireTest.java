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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class BasicOpenWireTest extends OpenWireTestBase {

   protected ActiveMQConnectionFactory factory;
   protected ActiveMQConnectionFactory looseFactory;
   protected ActiveMQXAConnectionFactory xaFactory;

   protected ActiveMQConnection connection;
   protected String topicName = "amqTestTopic1";
   protected String queueName = "amqTestQueue1";
   protected String topicName2 = "amqTestTopic2";
   protected String queueName2 = "amqTestQueue2";
   protected String durableQueueName = "durableQueueName";

   protected String messageTextPrefix = "";
   protected boolean topic = true;

   protected Map<String, SimpleString> testQueues = new HashMap<>();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      System.setProperty("org.apache.activemq.transport.AbstractInactivityMonitor.keepAliveTime", "5");
      createFactories();
      SimpleString coreQueue = SimpleString.of(queueName);
      this.server.createQueue(QueueConfiguration.of(coreQueue).setRoutingType(RoutingType.ANYCAST));
      testQueues.put(queueName, coreQueue);

      SimpleString coreQueue2 = SimpleString.of(queueName2);
      this.server.createQueue(QueueConfiguration.of(coreQueue2).setRoutingType(RoutingType.ANYCAST));
      testQueues.put(queueName2, coreQueue2);

      SimpleString durableQueue = SimpleString.of(durableQueueName);
      this.server.createQueue(QueueConfiguration.of(durableQueue).setRoutingType(RoutingType.ANYCAST));
      testQueues.put(durableQueueName, durableQueue);

      if (!enableSecurity) {
         connection = (ActiveMQConnection) factory.createConnection();
      }
   }

   protected void createFactories() {
      factory = new ActiveMQConnectionFactory(getConnectionUrl());
      looseFactory = new ActiveMQConnectionFactory(urlStringLoose);
      xaFactory = new ActiveMQXAConnectionFactory(getConnectionUrl());
   }

   protected String getConnectionUrl() {
      return urlString;
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      System.clearProperty("org.apache.activemq.transport.AbstractInactivityMonitor.keepAliveTime");
      try {
         if (connection != null) {
            connection.close();
         }

         Iterator<SimpleString> iterQueues = testQueues.values().iterator();
         while (iterQueues.hasNext()) {
            SimpleString coreQ = iterQueues.next();
            try {
               this.server.destroyQueue(coreQ, null, false, true);
            } catch (ActiveMQNonExistentQueueException idontcare) {
               // i don't care if this failed. it means it didn't find the queue
            } catch (Throwable e) {
               // just print, what else can we do?
               e.printStackTrace();
            }
         }
         testQueues.clear();
      } catch (Throwable e) {
         e.printStackTrace();
      } finally {
         super.tearDown();
      }
   }

   public ActiveMQDestination createDestination(Session session, byte type, String name) throws Exception {
      if (name == null) {
         return createDestination(session, type);
      }

      return switch (type) {
         case ActiveMQDestination.QUEUE_TYPE -> {
            makeSureCoreQueueExist(name);
            yield (ActiveMQDestination) session.createQueue(name);
         }
         case ActiveMQDestination.TOPIC_TYPE -> (ActiveMQDestination) session.createTopic(name);
         case ActiveMQDestination.TEMP_QUEUE_TYPE -> (ActiveMQDestination) session.createTemporaryQueue();
         case ActiveMQDestination.TEMP_TOPIC_TYPE -> (ActiveMQDestination) session.createTemporaryTopic();
         default -> throw new IllegalArgumentException("type: " + type);
      };
   }

   public void makeSureCoreQueueExist(String qname) throws Exception {
      SimpleString coreQ = testQueues.get(qname);
      if (coreQ == null) {
         coreQ = SimpleString.of(qname);
         this.server.createQueue(QueueConfiguration.of(coreQ).setRoutingType(RoutingType.ANYCAST));
         testQueues.put(qname, coreQ);
      }
   }

   public ActiveMQDestination createDestination(Session session, byte type) throws JMSException {
      return switch (type) {
         case ActiveMQDestination.QUEUE_TYPE -> (ActiveMQDestination) session.createQueue(queueName);
         case ActiveMQDestination.TOPIC_TYPE -> (ActiveMQDestination) session.createTopic(topicName);
         case ActiveMQDestination.TEMP_QUEUE_TYPE -> (ActiveMQDestination) session.createTemporaryQueue();
         case ActiveMQDestination.TEMP_TOPIC_TYPE -> (ActiveMQDestination) session.createTemporaryTopic();
         default -> throw new IllegalArgumentException("type: " + type);
      };
   }

   protected ActiveMQDestination createDestination2(Session session, byte type) throws JMSException {
      return switch (type) {
         case ActiveMQDestination.QUEUE_TYPE -> (ActiveMQDestination) session.createQueue(queueName2);
         case ActiveMQDestination.TOPIC_TYPE -> (ActiveMQDestination) session.createTopic(topicName2);
         case ActiveMQDestination.TEMP_QUEUE_TYPE -> (ActiveMQDestination) session.createTemporaryQueue();
         case ActiveMQDestination.TEMP_TOPIC_TYPE -> (ActiveMQDestination) session.createTemporaryTopic();
         default -> throw new IllegalArgumentException("type: " + type);
      };
   }

   protected void sendMessages(Session session, Destination destination, int count) throws JMSException {
      MessageProducer producer = session.createProducer(destination);
      sendMessages(session, producer, count);
      producer.close();
   }

   protected void sendMessages(Session session, MessageProducer producer, int count) throws JMSException {
      for (int i = 0; i < count; i++) {
         producer.send(session.createTextMessage(messageTextPrefix + i));
      }
   }

   protected void sendMessages(Connection connection, Destination destination, int count) throws JMSException {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      sendMessages(session, destination, count);
      session.close();
   }

   protected void assertTextMessagesEqual(String messsage,
                                          Message[] firstSet,
                                          Message[] secondSet) throws JMSException {
      assertEquals(firstSet.length, secondSet.length, "Message count does not match: " + messsage);
      for (int i = 0; i < secondSet.length; i++) {
         TextMessage m1 = (TextMessage) firstSet[i];
         TextMessage m2 = (TextMessage) secondSet[i];
         assertFalse(m1 == null ^ m2 == null, "Message " + (i + 1) + " did not match : " + messsage + ": expected {" + m1 + "}, but was {" + m2 + "}");
         assertEquals(m1.getText(), m2.getText(), "Message " + (i + 1) + " did not match: " + messsage + ": expected {" + m1 + "}, but was {" + m2 + "}");
      }
   }

   protected Connection createConnection() throws JMSException {
      return factory.createConnection();
   }

   protected void safeClose(Session s) {
      try {
         s.close();
      } catch (Throwable e) {
      }
   }
}


