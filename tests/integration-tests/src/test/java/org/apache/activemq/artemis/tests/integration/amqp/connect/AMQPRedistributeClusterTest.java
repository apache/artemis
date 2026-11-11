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
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.RoutingContext.MirrorOption;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.cluster.impl.RemoteQueueBindingImpl;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AMQPRedistributeClusterTest extends AmqpTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String QUEUE_NAME = "REDIST_QUEUE";
   private static final String TOPIC_NAME = "REDIST_TOPIC";
   private static final SimpleString TOPIC_NAME_SIMPLE_STRING = SimpleString.of("REDIST_TOPIC");

   protected static final int A_1_PORT = 5673;
   protected static final int A_2_PORT = 5674;

   ActiveMQServer a1;
   ActiveMQServer a2;

   protected static final int B_1_PORT = 5773;
   protected static final int B_2_PORT = 5774;

   ActiveMQServer b1;
   ActiveMQServer b2;

   @BeforeEach
   public void setCluster() throws Exception {
      a1 = createClusteredServer("A_1", A_1_PORT, A_2_PORT, B_1_PORT);
      a2 = createClusteredServer("A_2", A_2_PORT, A_1_PORT, B_2_PORT);

      a1.start();
      a2.start();

      b1 = createClusteredServer("B_1", B_1_PORT, B_2_PORT, -1);
      b2 = createClusteredServer("B_2", B_2_PORT, B_1_PORT, -1);

      b1.start();
      b2.start();
   }

   private ActiveMQServer createClusteredServer(String name, int thisPort, int clusterPort, int mirrorPort) throws Exception {
      ActiveMQServer server = createServer(thisPort, false);
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST).addQueueConfig(QueueConfiguration.of(QUEUE_NAME).setDurable(true).setRoutingType(RoutingType.ANYCAST)));
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(TOPIC_NAME).addRoutingType(RoutingType.MULTICAST));
      server.getConfiguration().clearAddressSettings();
      server.getConfiguration().addAddressSetting("#", new AddressSettings().setRedistributionDelay(0));


      server.setIdentity(name);
      server.getConfiguration().setName("node").setHAPolicyConfiguration(new PrimaryOnlyPolicyConfiguration()).addConnectorConfiguration("thisNode", "tcp://localhost:" + thisPort).addConnectorConfiguration("otherNode", "tcp://localhost:" + clusterPort);

      ClusterConnectionConfiguration clusterConfiguration = new ClusterConnectionConfiguration().setName("cluster").setConnectorName("thisNode").setMessageLoadBalancingType(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION).setStaticConnectors(Collections.singletonList("otherNode"));
      server.getConfiguration().addClusterConfiguration(clusterConfiguration);

      if (mirrorPort > 0) {
         server.getConfiguration().addAMQPConnection(new AMQPBrokerConnectConfiguration("myMirror" + mirrorPort, "tcp://localhost:" + mirrorPort).setReconnectAttempts(-1).setRetryInterval(100).addConnectionElement(new AMQPMirrorBrokerConnectionElement().setDurable(true).setMirrorSNF(SimpleString.of(mirrorName(mirrorPort)))));
      }

      return server;
   }

   private String mirrorName(int mirrorPort) {
      return "$ACTIVEMQ_ARTEMIS_MIRROR_MirrorTowards_" + mirrorPort;
   }

   @Test
   public void testQueueRedistributionAMQP() throws Exception {
      internalQueueRedistribution("AMQP");
   }

   @Test
   public void testQueueRedistributionCORE() throws Exception {
      internalQueueRedistribution("CORE");
   }

   public void internalQueueRedistribution(String protocol) throws Exception {
      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());

      ConnectionFactory cfA1 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_1_PORT);
      ConnectionFactory cfA2 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_2_PORT);
      try (Connection conn = cfA1.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
         for (int i = 0; i < 100; i++) {
            producer.send(session.createTextMessage("Hello" + i));
         }
      }

      try (Connection connA1 = cfA1.createConnection();
           Connection connA2 = cfA2.createConnection()) {

         connA1.start();
         connA2.start();

         Session sessionA1 = connA1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessionA2 = connA2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         for (int i = 0; i < 100; i++) {
            MessageConsumer consumer;
            String place;
            if (i % 2 == 0) {
               place = "A1";
               consumer = sessionA1.createConsumer(sessionA1.createQueue(QUEUE_NAME));
            } else {
               place = "A2";
               consumer = sessionA2.createConsumer(sessionA2.createQueue(QUEUE_NAME));
            }
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            logger.debug("Received message {} from {}", message, place);
            consumer.close();
         }
      }

      assertEmptyQueue(a1.locateQueue(QUEUE_NAME));
      assertEmptyQueue(a2.locateQueue(QUEUE_NAME));
      assertEmptyQueue(b1.locateQueue(QUEUE_NAME));
      assertEmptyQueue(b2.locateQueue(QUEUE_NAME));

      // if you see this message, most likely the notifications are being copied to the mirror
      assertFalse(loggerHandler.findText("AMQ222196"));
      assertFalse(loggerHandler.findText("AMQ224037"));
   }

   @Test
   public void testTopicRedistributionAMQP() throws Exception {
      internalTopicRedistribution("AMQP");
   }

   @Test
   public void testTopicRedistributionCORE() throws Exception {
      internalTopicRedistribution("CORE");
   }

   public void internalTopicRedistribution(String protocol) throws Exception {

      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());

      final int numMessages = 100;

      String subscriptionName = "my-topic-shared-subscription";

      ConnectionFactory cfA1 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_1_PORT);
      ConnectionFactory cfA2 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_2_PORT);

      Topic topic;

      try (Connection conn = cfA1.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         topic = session.createTopic(TOPIC_NAME);
         MessageConsumer consumer = session.createSharedDurableConsumer(topic, subscriptionName);
         consumer.close();
      }

      try (Connection conn = cfA2.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         topic = session.createTopic(TOPIC_NAME);
         MessageConsumer consumer = session.createSharedDurableConsumer(topic, subscriptionName);
         consumer.close();
      }

      Wait.assertTrue(() -> a1.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 2);
      Wait.assertTrue(() -> a2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 2);
      Wait.assertTrue(() -> b1.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 2);
      Wait.assertTrue(() -> b2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 2);

      // naming convention is different between the protocols, I'm navigating through the bindings to find the actual queue name
      String subscriptionQueueName;

      {
         Set<String> subscriptionSet = new HashSet<>();
         // making sure the queues created on a1 are propagated into b1
         a1.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).forEach((n, b) -> {
            logger.debug("{} = {}", n, b);
            if (b instanceof LocalQueueBinding localQueueBinding) {
               subscriptionSet.add(localQueueBinding.getUniqueName().toString());
               Wait.assertTrue(() -> b1.locateQueue(localQueueBinding.getUniqueName()) != null);
            }
         });
         assertEquals(1, subscriptionSet.size());
         subscriptionQueueName = subscriptionSet.iterator().next();
      }

      // making sure the queues created on a2 are propagated into b2
      a2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).forEach((n, b) -> {
         logger.debug("{} = {}", n, b);
         if (b instanceof LocalQueueBinding localQueueBinding) {
            Wait.assertTrue(() -> b2.locateQueue(localQueueBinding.getUniqueName()) != null);
         }
      });

      Queue a1TopicSubscription = a1.locateQueue(subscriptionQueueName);
      assertNotNull(a1TopicSubscription);
      Queue a2TopicSubscription = a2.locateQueue(subscriptionQueueName);
      assertNotNull(a2TopicSubscription);
      Queue b1TopicSubscription = b1.locateQueue(subscriptionQueueName);
      assertNotNull(b1TopicSubscription);
      Queue b2TopicSubscription = b2.locateQueue(subscriptionQueueName);
      assertNotNull(a2);


      try (Connection conn = cfA1.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(topic);
         for (int i = 0; i < numMessages; i++) {
            producer.send(session.createTextMessage("Hello" + i));
         }
      }

      assertFalse(loggerHandler.findText("AMQ222196"));
      assertFalse(loggerHandler.findText("AMQ224037"));

      assertEquals(0, a1TopicSubscription.getConsumerCount());
      Wait.assertEquals(numMessages / 2, a1TopicSubscription::getMessageCount);
      Wait.assertEquals(numMessages / 2, a2TopicSubscription::getMessageCount);

      logger.debug("b1={}. b2={}", b1TopicSubscription.getMessageCount(), b2TopicSubscription.getMessageCount());

      Wait.assertEquals(numMessages / 2, b1TopicSubscription::getMessageCount);
      Wait.assertEquals(numMessages / 2, b2TopicSubscription::getMessageCount);

      try (Connection connA1 = cfA1.createConnection();
           Connection connA2 = cfA2.createConnection()) {

         connA1.start();
         connA2.start();

         Session sessionA1 = connA1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sessionA2 = connA2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         for (int i = 0; i < numMessages; i++) {
            MessageConsumer consumer;
            String place;
            if (i % 2 == 0) {
               place = "A1";
               consumer = sessionA1.createSharedDurableConsumer(topic, subscriptionName);
            } else {
               place = "A2";
               consumer = sessionA2.createSharedDurableConsumer(topic, subscriptionName);
            }
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            logger.debug("Received message {} from {}", message, place);
            consumer.close();
         }
      }

      // if you see this message, most likely the notifications are being copied to the mirror
      assertFalse(loggerHandler.findText("AMQ222196"));
      assertFalse(loggerHandler.findText("AMQ224037"));

      assertEmptyQueue(a1TopicSubscription);
      assertEmptyQueue(a2TopicSubscription);
      assertEmptyQueue(b1TopicSubscription);
      assertEmptyQueue(b2TopicSubscription);
   }

   // This test is playing with Remote binding routing, similarly to how topic redistribution would happen
   @Test
   public void testRemoteBindingRouting() throws Exception {
      final String protocol = "AMQP";
      String subscriptionName = "my-topic-shared-subscription";

      ConnectionFactory cfA1 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_1_PORT);
      ConnectionFactory cfA2 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_2_PORT);

      Topic topic;

      try (Connection conn = cfA1.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         topic = session.createTopic(TOPIC_NAME);
         for (int i = 0; i < 10; i++) {
            session.createSharedDurableConsumer(topic, subscriptionName + "_" + i);
         }
      }

      try (Connection conn = cfA2.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         topic = session.createTopic(TOPIC_NAME);
         for (int i = 0; i < 10; i++) {
            session.createSharedDurableConsumer(topic, subscriptionName + "_" + i);
         }
      }

      Wait.assertTrue(() -> a1.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);
      Wait.assertTrue(() -> a2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);
      Wait.assertTrue(() -> b1.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);
      Wait.assertTrue(() -> b2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);

      List<RemoteQueueBinding> remoteQueueBindings_a2 = new ArrayList<>();
      // making sure the queues created on a2 are propagated into b2
      a2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).forEach((a, b) -> {
         if (b instanceof RemoteQueueBindingImpl impl && b.getClusterName().toString().startsWith(subscriptionName + "_0")) {
            logger.debug("{} = {}", a, b);
            remoteQueueBindings_a2.add(impl);
         }
      });

      assertEquals(1, remoteQueueBindings_a2.size());

      RoutingContext routingContext = new RoutingContextImpl(new TransactionImpl(a2.getStorageManager())).setMirrorOption(MirrorOption.individualRoute);

      Message directMessage = new CoreMessage(a2.getStorageManager().generateID(), 512);
      directMessage.setAddress(TOPIC_NAME);
      directMessage.putStringProperty("Test", "t1");

      // we will route a single message to subscription-0. a previous search found the RemoteBinding into remoteQueueBindins_a2;
      remoteQueueBindings_a2.get(0).route(directMessage, routingContext);
      a2.getPostOffice().processRoute(directMessage, routingContext, false);
      routingContext.getTransaction().commit();

      for (int i = 0; i < 10; i++) {
         String name = "my-topic-shared-subscription_" + i + ":global";

         if (logger.isDebugEnabled()) {
            logger.debug("a1 queue {} with {} messages", name, a1.locateQueue(name).getMessageCount());
            logger.debug("b1 queue {} with {} messages", name, b1.locateQueue(name).getMessageCount());
            logger.debug("a2 queue {} with {} messages", name, a2.locateQueue(name).getMessageCount());
            logger.debug("b2 queue {} with {} messages", name, b2.locateQueue(name).getMessageCount());
         }

         // Since we routed to subscription-0 only, the outcome mirroring should only receive the output on subscription-0 on b1;
         // When the routing happens after a clustered operation, mirror should be done individually to each routed queue.
         // this test is validating that only subscription-0 got the message on both a1 and b1;
         // notice that the initial route happened on a2, which then transfered the message towards a1.
         // a1 made the copy to b1 through mirroring, and only subscription-0 should receive a message.
         // which is exactly what should happen through message-redistribution in clustering

         Wait.assertEquals(i == 0 ? 1 : 0, a1.locateQueue(name)::getMessageCount);
         Wait.assertEquals(i == 0 ? 1 : 0, b1.locateQueue(name)::getMessageCount);
         Wait.assertEquals(0, a2.locateQueue(name)::getMessageCount);
         Wait.assertEquals(0, b2.locateQueue(name)::getMessageCount);
      }
   }



   // This test is faking a MirrorSend.
   // First it will send with an empty collection, then to a single queue
   @Test
   public void testFakeMirrorSend() throws Exception {
      final String protocol = "AMQP";
      String subscriptionName = "my-topic-shared-subscription";

      ConnectionFactory cfA1 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_1_PORT);
      ConnectionFactory cfA2 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_2_PORT);

      Topic topic;

      try (Connection conn = cfA1.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         topic = session.createTopic(TOPIC_NAME);
         for (int i = 0; i < 10; i++) {
            session.createSharedDurableConsumer(topic, subscriptionName + "_" + i);
         }
      }

      try (Connection conn = cfA2.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         topic = session.createTopic(TOPIC_NAME);
         for (int i = 0; i < 10; i++) {
            session.createSharedDurableConsumer(topic, subscriptionName + "_" + i);
         }
      }

      Wait.assertTrue(() -> a1.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);
      Wait.assertTrue(() -> a2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);
      Wait.assertTrue(() -> b1.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);
      Wait.assertTrue(() -> b2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);

      List<RemoteQueueBinding> remoteQueueBindings_a2 = new ArrayList<>();
      // making sure the queues created on a2 are propagated into b2
      a2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).forEach((a, b) -> {
         if (b instanceof RemoteQueueBindingImpl impl && b.getClusterName().toString().startsWith(subscriptionName + "_0")) {
            logger.debug("{} = {}", a, b);
            remoteQueueBindings_a2.add(impl);
         }
      });

      assertEquals(1, remoteQueueBindings_a2.size());
      AmqpConnection connection = createAmqpConnection(new URI("tcp://localhost:" + A_1_PORT));
      runAfter(connection::close);
      AmqpSession session = connection.createSession();

      AmqpMessage message = new AmqpMessage();
      message.setAddress(TOPIC_NAME);
      // this is sending an empty ArrayList for the TARGET_QUEUES.
      // no queues should be altered when there's an empty TARGET_QUEUES
      message.setDeliveryAnnotation(AMQPMirrorControllerSource.TARGET_QUEUES.toString(), new ArrayList<>());
      message.setDeliveryAnnotation(AMQPMirrorControllerSource.INTERNAL_ID.toString(), a1.getStorageManager().generateID());
      message.setDeliveryAnnotation(AMQPMirrorControllerSource.BROKER_ID.toString(), String.valueOf(b1.getNodeID()));

      AmqpSender sender = session.createSender(mirrorName(A_1_PORT), new Symbol[]{Symbol.getSymbol("amq.mirror")});
      sender.send(message);


      for (int i = 0; i < 10; i++) {
         String name = "my-topic-shared-subscription_" + i + ":global";

         // all queues should be empty
         // because the send to the mirror had an empty TARGET_QUEUES
         Wait.assertEquals(0, a1.locateQueue(name)::getMessageCount);
         Wait.assertEquals(0, b1.locateQueue(name)::getMessageCount);
         Wait.assertEquals(0, a2.locateQueue(name)::getMessageCount);
         Wait.assertEquals(0, b2.locateQueue(name)::getMessageCount);
      }

      message = new AmqpMessage();
      message.setAddress(TOPIC_NAME);
      List<String> singleQueue = new ArrayList<>();
      singleQueue.add("my-topic-shared-subscription_3:global");
      singleQueue.add("IDONTEXIST");
      message.setDeliveryAnnotation(AMQPMirrorControllerSource.TARGET_QUEUES.toString(), singleQueue);
      message.setDeliveryAnnotation(AMQPMirrorControllerSource.INTERNAL_ID.toString(), a1.getStorageManager().generateID());
      message.setDeliveryAnnotation(AMQPMirrorControllerSource.BROKER_ID.toString(), String.valueOf(b1.getNodeID())); // simulating a node from b1, so it is not sent back to b1

      sender.send(message);

      for (int i = 0; i < 10; i++) {
         String name = "my-topic-shared-subscription_" + i + ":global";

         if (i == 3) {
            // only this queue, on this server should have received a message
            // it shouldn't also be mirrored to its replica
            Wait.assertEquals(1, a1.locateQueue(name)::getMessageCount);
         } else {
            Wait.assertEquals(0, a1.locateQueue(name)::getMessageCount);
         }
         Wait.assertEquals(0, b1.locateQueue(name)::getMessageCount);
         Wait.assertEquals(0, a2.locateQueue(name)::getMessageCount);
         Wait.assertEquals(0, b2.locateQueue(name)::getMessageCount);
      }

   }



   // This test has distinct subscriptions on each node and it is making sure the Mirror Routing is working accurately
   @Test
   public void testMultiNodeSubscription() throws Exception {
      final String protocol = "AMQP";
      String subscriptionName = "my-topic-shared-subscription";

      ConnectionFactory cfA1 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_1_PORT);
      ConnectionFactory cfA2 = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + A_2_PORT);

      Topic topic;

      try (Connection conn = cfA1.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         topic = session.createTopic(TOPIC_NAME);
         for (int i = 0; i < 10; i++) {
            session.createSharedDurableConsumer(topic, subscriptionName + "_" + i);
         }
      }

      try (Connection conn = cfA2.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         topic = session.createTopic(TOPIC_NAME);
         for (int i = 10; i < 20; i++) {
            session.createSharedDurableConsumer(topic, subscriptionName + "_" + i);
         }
      }

      Wait.assertTrue(() -> a1.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);
      Wait.assertTrue(() -> a2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);
      Wait.assertTrue(() -> b1.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);
      Wait.assertTrue(() -> b2.getPostOffice().getBindingsForAddress(TOPIC_NAME_SIMPLE_STRING).size() == 20);


      try (Connection conn = cfA2.createConnection()) {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         topic = session.createTopic(TOPIC_NAME);
         MessageProducer producer = session.createProducer(topic);
         producer.send(session.createTextMessage("hello"));
      }

      for (int i = 0; i < 10; i++) {
         String name = "my-topic-shared-subscription_" + i + ":global";
         Wait.waitFor(() -> a1.locateQueue(name) != null);
         Wait.waitFor(() -> b1.locateQueue(name) != null);
         Wait.assertEquals(1, a1.locateQueue(name)::getMessageCount);
         Wait.assertEquals(1, b1.locateQueue(name)::getMessageCount);
      }
      for (int i = 10; i < 20; i++) {
         String name = "my-topic-shared-subscription_" + i + ":global";
         Wait.waitFor(() -> a2.locateQueue(name) != null);
         Wait.waitFor(() -> b2.locateQueue(name) != null);
         Wait.assertEquals(1, a2.locateQueue(name)::getMessageCount);
         Wait.assertEquals(1, b2.locateQueue(name)::getMessageCount);
      }
   }

   private void assertEmptyQueue(Queue queue) {
      assertNotNull(queue);
      try {
         Wait.assertEquals(0, queue::getMessageCount);
      } catch (Throwable e) {
         if (e instanceof AssertionError error) {
            throw error;
         } else {
            throw new RuntimeException(e.getMessage(), e);
         }
      }
   }

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,CORE";
   }
}
