/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopicClusterTest extends OpenwireArtemisBaseTest implements MessageListener {

   protected static final int MESSAGE_COUNT = 50;
   protected static final int NUMBER_IN_CLUSTER = 3;
   private static final Logger LOG = LoggerFactory.getLogger(TopicClusterTest.class);

   protected Destination destination;
   protected boolean topic = true;
   protected final AtomicInteger receivedMessageCount = new AtomicInteger(0);
   protected int deliveryMode = DeliveryMode.NON_PERSISTENT;
   protected MessageProducer[] producers;
   protected Connection[] connections;
   protected EmbeddedJMS[] servers = new EmbeddedJMS[NUMBER_IN_CLUSTER];
   protected String groupId;

   @Before
   public void setUp() throws Exception {
      groupId = "topic-cluster-test-" + System.currentTimeMillis();
      connections = new Connection[NUMBER_IN_CLUSTER];
      producers = new MessageProducer[NUMBER_IN_CLUSTER];
      Destination destination = createDestination();
      String root = System.getProperty("activemq.store.dir");
      if (root == null) {
         root = "target/store";
      }

      this.setUpClusterServers(servers);
      try {
         for (int i = 0; i < NUMBER_IN_CLUSTER; i++) {

            System.setProperty("activemq.store.dir", root + "_broker_" + i);
            connections[i] = createConnection(i);
            connections[i].setClientID("ClusterTest" + i);
            connections[i].start();
            Session session = connections[i].createSession(false, Session.AUTO_ACKNOWLEDGE);
            producers[i] = session.createProducer(destination);
            producers[i].setDeliveryMode(deliveryMode);
            MessageConsumer consumer = createMessageConsumer(session, destination);
            consumer.setMessageListener(this);

         }
         LOG.info("Sleeping to ensure cluster is fully connected");
         Thread.sleep(5000);
      } finally {
         System.setProperty("activemq.store.dir", root);
      }
   }

   @After
   public void tearDown() throws Exception {
      if (connections != null) {
         for (int i = 0; i < connections.length; i++) {
            try {
               connections[i].close();
            } catch (Exception e) {
               //ignore.
            }
         }
      }
      this.shutDownClusterServers(servers);
   }

   protected MessageConsumer createMessageConsumer(Session session, Destination destination) throws JMSException {
      return session.createConsumer(destination);
   }

   protected ActiveMQConnectionFactory createGenericClusterFactory(int serverID) throws Exception {
      String url = newURI(serverID);
      return new ActiveMQConnectionFactory(url);
   }

   protected int expectedReceiveCount() {
      return MESSAGE_COUNT * NUMBER_IN_CLUSTER * NUMBER_IN_CLUSTER;
   }

   protected Connection createConnection(int serverID) throws Exception {
      return createGenericClusterFactory(serverID).createConnection();
   }

   protected Destination createDestination() {
      return createDestination(getClass().getName());
   }

   protected Destination createDestination(String name) {
      if (topic) {
         return new ActiveMQTopic(name);
      } else {
         return new ActiveMQQueue(name);
      }
   }

   @Override
   public void onMessage(Message msg) {
      // log.info("GOT: " + msg);
      receivedMessageCount.incrementAndGet();
      synchronized (receivedMessageCount) {
         if (receivedMessageCount.get() >= expectedReceiveCount()) {
            receivedMessageCount.notify();
         }
      }
   }

   @Test
   public void testSendReceive() throws Exception {
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         TextMessage textMessage = new ActiveMQTextMessage();
         textMessage.setText("MSG-NO:" + i);
         for (int x = 0; x < producers.length; x++) {
            producers[x].send(textMessage);
         }
      }
      synchronized (receivedMessageCount) {
         while (receivedMessageCount.get() < expectedReceiveCount()) {
            receivedMessageCount.wait(20000);
         }
      }
      // sleep a little - to check we don't get too many messages
      Thread.sleep(2000);
      LOG.info("GOT: " + receivedMessageCount.get() + " Expected: " + expectedReceiveCount());
      Assert.assertEquals("Expected message count not correct", expectedReceiveCount(), receivedMessageCount.get());
   }

}
