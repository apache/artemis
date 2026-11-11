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
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSFQQNConsumerTest extends MultiprotocolJMSClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testFQQNTopicConsumerWithSelectorAMQP() throws Exception {
      testFQQNTopicConsumerWithSelector("AMQP", true);
   }

   @Test
   public void testFQQNTopicConsumerWithSelectorOpenWire() throws Exception {
      testFQQNTopicConsumerWithSelector("OPENWIRE", false);
   }

   @Test
   public void testFQQNTopicConsumerWithSelectorCore() throws Exception {
      testFQQNTopicConsumerWithSelector("CORE", true);
   }

   private void testFQQNTopicConsumerWithSelector(String protocol, boolean validateFilterChange) throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:5672");
      final String queue = "queue";
      final String address = "address";
      final String filter = "prop='match'";
      try (Connection c = factory.createConnection()) {
         c.start();

         Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Topic t = s.createTopic(CompositeAddress.toFullyQualified(address, queue));

         MessageConsumer mc = s.createConsumer(t, filter);

         Wait.assertTrue(() -> server.locateQueue(queue) != null, 2000, 100);
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(SimpleString.of(queue));

         assertEquals(RoutingType.MULTICAST, serverQueue.getRoutingType());
         assertNotNull(serverQueue.getFilter());
         assertEquals(filter, serverQueue.getFilter().getFilterString().toString());
         assertEquals(filter, server.locateQueue(queue).getFilter().getFilterString().toString());

         MessageProducer producer = s.createProducer(s.createTopic("address"));

         Message message = s.createTextMessage("hello");
         message.setStringProperty("prop", "match");
         producer.send(message);

         assertNotNull(mc.receive(5000));

         message = s.createTextMessage("hello");
         message.setStringProperty("prop", "nomatch");
         producer.send(message);

         if (protocol.equals("OPENWIRE")) {
            assertNull(mc.receive(500)); // false negatives in openwire
         } else {
            assertNull(mc.receiveNoWait());
         }
      }

      if (validateFilterChange) {
         boolean thrownException = false;
         try (Connection c = factory.createConnection()) {
            Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic t = s.createTopic(CompositeAddress.toFullyQualified(address, queue));
            MessageConsumer mc = s.createConsumer(t, "shouldThrowException=true");
         } catch (Exception e) {
            logger.debug(e.getMessage(), e);
            thrownException = true;
         }
         assertTrue(thrownException);

         // validating the case where I am adding a consumer without a filter
         // on this case the consumer will have no filter, but the filter on the queue should take care of things
         try (Connection c = factory.createConnection()) {
            c.start();
            Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic t = s.createTopic(CompositeAddress.toFullyQualified(address, queue));
            MessageConsumer mc = s.createConsumer(t);

            Wait.assertTrue(() -> server.locateQueue(queue) != null, 2000, 100);
            org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(SimpleString.of(queue));

            Wait.assertEquals(1, () -> serverQueue.getConsumers().size());
            serverQueue.getConsumers().forEach(serverConsumer -> {
               assertNull(serverConsumer.getFilter());
            });


            MessageProducer producer = s.createProducer(s.createTopic("address"));

            Message message = s.createTextMessage("hello");
            message.setStringProperty("prop", "match");
            producer.send(message);

            assertNotNull(mc.receive(5000));

            message = s.createTextMessage("hello");
            message.setStringProperty("prop", "nomatch");
            producer.send(message);

            if (protocol.equals("OPENWIRE")) {
               assertNull(mc.receive(500)); // false negatives in openwire
            } else {
               assertNull(mc.receiveNoWait());
            }

         }
      }
   }


   @Test
   public void testFQQNTopicFilterConsumerOnlyAMQP() throws Exception {
      testFQQNTopicFilterConsumerOnly("AMQP");
   }

   @Test
   public void testFQQNTopicFilterConsumerOnlyOPENWIRE() throws Exception {
      testFQQNTopicFilterConsumerOnly("OPENWIRE");
   }

   @Test
   public void testFQQNTopicFilterConsumerOnlyCORE() throws Exception {
      testFQQNTopicFilterConsumerOnly("CORE");
   }

   private void testFQQNTopicFilterConsumerOnly(String protocol) throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:5672");
      final String queue = "queue";
      final String address = "address";
      final String filter = "prop='match'";

      // predefining the queue without a filter
      // so consumers will filter out messages
      server.addAddressInfo(new AddressInfo(address).addRoutingType(RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setRoutingType(RoutingType.MULTICAST));

      try (Connection c = factory.createConnection()) {
         c.start();

         Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Topic t = s.createTopic(CompositeAddress.toFullyQualified(address, queue));

         MessageConsumer mc = s.createConsumer(t, filter);

         Wait.assertTrue(() -> server.locateQueue(queue) != null, 2000, 100);
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(SimpleString.of(queue));
         assertEquals(RoutingType.MULTICAST, serverQueue.getRoutingType());
         assertNull(serverQueue.getFilter()); // it was pre-created without a filter, so we will just filter on the consumer

         Wait.assertEquals(1, () -> serverQueue.getConsumers().size());
         serverQueue.getConsumers().forEach(consumer -> {
            assertNotNull(consumer.getFilter());
            assertEquals(filter, consumer.getFilter().getFilterString().toString());
         });

         MessageProducer producer = s.createProducer(s.createTopic("address"));

         Message message = s.createTextMessage("hello");
         message.setStringProperty("prop", "match");
         producer.send(message);

         assertNotNull(mc.receive(5000));

         message = s.createTextMessage("hello");
         message.setStringProperty("prop", "nomatch");
         producer.send(message);

         if (protocol.equals("OPENWIRE")) {
            assertNull(mc.receive(100)); // i have had false negatives with openwire, hence this
         } else {
            assertNull(mc.receiveNoWait());
         }
      }
   }

   @Test
   public void testFQQNTopicConsumerDontExistAMQP() throws Exception {
      testFQQNTopicConsumerDontExist("AMQP");
   }

   /*
    * this commented out code is just to make a point that this test would not be valid in openwire.
      As openwire is calling the method createSubscription from its 1.1 implementation.
     Hence there's no need to test this over JMS1.1 with openWire
   @Test
   public void testFQQNTopicConsumerDontExistOPENWIRE() throws Exception {
      testFQQNTopicConsumerDontExist("OPENWIRE");
   } */

   @Test
   public void testFQQNTopicConsumerDontExistCORE() throws Exception {
      testFQQNTopicConsumerDontExist("CORE");
   }

   private void testFQQNTopicConsumerDontExist(String protocol) throws Exception {
      AddressSettings settings = new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false);
      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", settings);

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:5672");

      final String queue = "queue";
      final String address = "address";

      boolean thrownException = false;
      try (Connection c = factory.createConnection()) {
         Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic t = s.createTopic(CompositeAddress.toFullyQualified(address, queue));
         MessageConsumer mc = s.createConsumer(t);
      } catch (Exception e) {
         logger.debug(e.getMessage(), e);
         thrownException = true;
      }

      assertTrue(thrownException);
   }

   @Test
   public void testFQQNQueueConsumerWithSelectorAMQP() throws Exception {
      testFQQNQueueConsumerWithSelector("AMQP");
   }

   @Test
   public void testFQQNQueueConsumerWithSelectorOpenWire() throws Exception {
      testFQQNQueueConsumerWithSelector("OPENWIRE");
   }

   @Test
   public void testFQQNQueueConsumerWithSelectorCore() throws Exception {
      testFQQNQueueConsumerWithSelector("CORE");
   }

   private void testFQQNQueueConsumerWithSelector(String protocol) throws Exception {
      AddressSettings settings = new AddressSettings().setDefaultQueueRoutingType(RoutingType.ANYCAST).setDefaultAddressRoutingType(RoutingType.ANYCAST);
      server.getAddressSettingsRepository().addMatch("#", settings);

      final String queue = "myQueue";
      final String address = "address";
      final String filter = "prop='match'";

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:5672");

      try (Connection c = factory.createConnection()) {
         c.start();

         Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);

         String queueQuery = CompositeAddress.toFullyQualified(address, queue) + (protocol.equals("OPENWIRE") ? "?selectorAware=true" : "");

         Queue q = s.createQueue(queueQuery);

         MessageConsumer mc = s.createConsumer(q, filter);

         Wait.assertTrue(() -> server.locateQueue(queue) != null, 2000, 100);
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(SimpleString.of(queue));

         assertEquals(RoutingType.ANYCAST, serverQueue.getRoutingType());

         assertNull(serverQueue.getFilter());

         MessageProducer p = s.createProducer(q);

         Message m = s.createMessage();
         m.setStringProperty("prop", "match");
         p.send(m);

         assertNotNull(mc.receive(1000));

         m = s.createMessage();
         m.setStringProperty("prop", "no-match");
         p.send(m);

         if (protocol.equals("OPENWIRE")) {
            assertNull(mc.receive(100)); // i have had false negatives with openwire, hence this
         } else {
            assertNull(mc.receiveNoWait());
         }

         Wait.assertEquals(1, () -> serverQueue.getConsumers().size());

         serverQueue.getConsumers().forEach(queueConsumer -> {
            assertNotNull(queueConsumer.getFilter());
            assertEquals(filter, queueConsumer.getFilter().getFilterString().toString());
         });

         mc.close();

         Wait.assertEquals(0, () -> serverQueue.getConsumers().size());


         String invalidFilter = "notHappening=true";

         mc = s.createConsumer(q, invalidFilter);

         Wait.assertEquals(1, () -> serverQueue.getConsumers().size());
         serverQueue.getConsumers().forEach(queueConsumer -> {
            assertNotNull(queueConsumer.getFilter());
            assertEquals(invalidFilter, queueConsumer.getFilter().getFilterString().toString());
         });

      }
   }



   @Test
   public void testFQQNTopicMultiConsumerWithSelectorAMQP() throws Exception {
      testFQQNTopicMultiConsumerWithSelector("AMQP", true);
   }

   @Test
   public void testFQQNTopicMultiConsumerWithSelectorOpenWire() throws Exception {
      testFQQNTopicMultiConsumerWithSelector("OPENWIRE", false);
   }

   @Test
   public void testFQQNTopicMultiConsumerWithSelectorCORE() throws Exception {
      testFQQNTopicMultiConsumerWithSelector("CORE", true);
   }

   private void testFQQNTopicMultiConsumerWithSelector(String protocol, boolean validateFilterChange) throws Exception {

      class RunnableConsumer implements Runnable {
         int errors = 0;
         final int expected;
         final Connection c;
         final Session session;
         final Topic topic;
         final MessageConsumer consumer;
         final String queueName;
         final String filter;
         final CountDownLatch done;


         RunnableConsumer(Connection c, String queueName, int expected, String filter, CountDownLatch done) throws Exception {
            this.c = c;
            this.session = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
            this.queueName = queueName;
            this.expected = expected;
            this.topic = session.createTopic(queueName);
            this.consumer = session.createConsumer(topic, filter);
            this.done = done;
            this.filter = filter;
         }

         @Override
         public void run() {
            try {
               for (int i = 0; i < expected; i++) {
                  TextMessage message = (TextMessage) consumer.receive(5000);
                  logger.debug("Queue {} received message {}", queueName, message.getText());
                  assertEquals(i, message.getIntProperty("i"));
                  assertNotNull(message);
               }
               if (protocol.equals("OPENWIRE")) {
                  assertNull(consumer.receive(500)); // false negatives in openwire
               } else {
                  assertNull(consumer.receiveNoWait());
               }
            } catch (Throwable e) {
               errors++;
               logger.warn(e.getMessage(), e);
            } finally {
               done.countDown();
            }
         }
      }

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:5672");
      final String address = "address";
      int threads = 10;

      ExecutorService executor = Executors.newFixedThreadPool(threads);
      runAfter(executor::shutdownNow);
      try (Connection c = factory.createConnection()) {
         c.start();

         CountDownLatch doneLatch = new CountDownLatch(threads);

         RunnableConsumer[] consumers = new RunnableConsumer[threads];
         for (int i = 0; i < threads; i++) {
            consumers[i] = new RunnableConsumer(c, address + "::" + "queue" + i, i * 10, "i < " + (i * 10), doneLatch);
         }

         Session s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = s.createProducer(s.createTopic(address));

         for (int i = 0; i < threads * 10; i++) {
            Message message = s.createTextMessage("i=" + i);
            message.setIntProperty("i", i);
            p.send(message);
         }

         for (RunnableConsumer consumer : consumers) {
            executor.execute(consumer);
         }

         assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

         for (RunnableConsumer consumer : consumers) {
            assertEquals(0, consumer.errors, "Error on consumer for queue " + consumer.queueName);
         }
      }
   }


}