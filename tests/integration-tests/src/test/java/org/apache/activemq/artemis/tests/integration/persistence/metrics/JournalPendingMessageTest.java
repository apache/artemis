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
package org.apache.activemq.artemis.tests.integration.persistence.metrics;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSession;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JournalPendingMessageTest extends AbstractPersistentStatTestSupport {
   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // protected URI brokerConnectURI;
   protected String defaultQueueName = "test.queue";
   protected String defaultTopicName = "test.topic";
   protected static int maxMessageSize = 1000;

   @BeforeEach
   public void setupAddresses() throws Exception {
      server.getPostOffice()
            .addAddressInfo(new AddressInfo(SimpleString.of(defaultQueueName), RoutingType.ANYCAST));

      server.createQueue(QueueConfiguration.of(defaultQueueName).setRoutingType(RoutingType.ANYCAST));
   }

   @Override
   protected Configuration createDefaultConfig(boolean netty) throws Exception {
      Configuration config = super.createDefaultConfig(netty);

      // Set a low max size so we page which will test the paging metrics as
      // well
      config.setGlobalMaxSize(100000);

      return config;
   }

   @Test
   public void testQueueMessageSize() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      publishTestQueueMessages(200, publishedMessageSize);

      verifyPendingStats(defaultQueueName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 200, publishedMessageSize.get());

      this.killServer();
      this.restartServer();

      verifyPendingStats(defaultQueueName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 200, publishedMessageSize.get());
   }

   @Test
   public void testQueueMessageSizeTx() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      publishTestQueueMessagesTx(200, publishedMessageSize);

      verifyPendingStats(defaultQueueName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 200, publishedMessageSize.get());

      this.killServer();
      this.restartServer();

      verifyPendingStats(defaultQueueName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 200, publishedMessageSize.get());
   }

   @Test
   public void testQueueLargeMessageSize() throws Exception {

      ActiveMQConnectionFactory acf = (ActiveMQConnectionFactory) cf;
      acf.setMinLargeMessageSize(1000);
      Connection connection = cf.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      String testText = StringUtils.repeat("t", 5000);
      ActiveMQTextMessage message = (ActiveMQTextMessage) session.createTextMessage(testText);
      session.createProducer(session.createQueue(defaultQueueName)).send(message);

      verifyPendingStats(defaultQueueName, 1, message.getCoreMessage().getPersistentSize());
      verifyPendingDurableStats(defaultQueueName, 1, message.getCoreMessage().getPersistentSize());

      connection.close();

      this.killServer();
      this.restartServer();

      verifyPendingStats(defaultQueueName, 1, message.getCoreMessage().getPersistentSize());
      verifyPendingDurableStats(defaultQueueName, 1, message.getCoreMessage().getPersistentSize());

   }

   @Test
   public void testQueueLargeMessageSizeTX() throws Exception {

      ActiveMQConnectionFactory acf = (ActiveMQConnectionFactory) cf;
      acf.setMinLargeMessageSize(1000);
      Connection connection = cf.createConnection();
      connection.start();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

      String testText = StringUtils.repeat("t", 2000);
      MessageProducer producer = session.createProducer(session.createQueue(defaultQueueName));
      ActiveMQTextMessage message = (ActiveMQTextMessage) session.createTextMessage(testText);
      for (int i = 0; i < 10; i++) {
         producer.send(message);
      }

      //not commited so should be 0
      verifyPendingStats(defaultQueueName, 0, message.getCoreMessage().getPersistentSize() * 10);
      verifyPendingDurableStats(defaultQueueName, 0, message.getCoreMessage().getPersistentSize() * 10);

      session.commit();

      verifyPendingStats(defaultQueueName, 10, message.getCoreMessage().getPersistentSize() * 10);
      verifyPendingDurableStats(defaultQueueName, 10, message.getCoreMessage().getPersistentSize() * 10);

      connection.close();

      this.killServer();
      this.restartServer();

      verifyPendingStats(defaultQueueName, 10, message.getCoreMessage().getPersistentSize());
      verifyPendingDurableStats(defaultQueueName, 10, message.getCoreMessage().getPersistentSize());
   }

   @Test
   public void testQueueBrowserMessageSize() throws Exception {

      AtomicLong publishedMessageSize = new AtomicLong();

      publishTestQueueMessages(200, publishedMessageSize);
      browseTestQueueMessages(defaultQueueName);
      verifyPendingStats(defaultQueueName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 200, publishedMessageSize.get());
   }

   @Test
   public void testQueueMessageSizeNonPersistent() throws Exception {

      AtomicLong publishedMessageSize = new AtomicLong();

      publishTestQueueMessages(10, DeliveryMode.NON_PERSISTENT, publishedMessageSize);
      verifyPendingStats(defaultQueueName, 10, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 0, 0);
   }

   @Test
   public void testQueueMessageSizePersistentAndNonPersistent() throws Exception {

      AtomicLong publishedNonPersistentMessageSize = new AtomicLong();
      AtomicLong publishedMessageSize = new AtomicLong();

      publishTestQueueMessages(5, DeliveryMode.PERSISTENT, publishedMessageSize);
      publishTestQueueMessages(10, DeliveryMode.NON_PERSISTENT, publishedNonPersistentMessageSize);
      verifyPendingStats(defaultQueueName, 15, publishedMessageSize.get() + publishedNonPersistentMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 5, publishedMessageSize.get());
   }

   @Test
   public void testQueueMessageSizeAfterConsumption() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      publishTestQueueMessages(200, publishedMessageSize);
      verifyPendingStats(defaultQueueName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 200, publishedMessageSize.get());

      consumeTestQueueMessages(200);

      verifyPendingStats(defaultQueueName, 0, 0);
      verifyPendingDurableStats(defaultQueueName, 0, 0);
   }

   @Test
   public void testScheduledStats() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      Connection connection = cf.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(defaultQueueName));
      producer.setDeliveryDelay(2000);
      producer.send(session.createTextMessage("test"));

      verifyPendingStats(defaultQueueName, 1, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 1, publishedMessageSize.get());
      verifyScheduledStats(defaultQueueName, 1, publishedMessageSize.get());

      consumeTestQueueMessages(1);

      verifyPendingStats(defaultQueueName, 0, 0);
      verifyPendingDurableStats(defaultQueueName, 0, 0);
      verifyScheduledStats(defaultQueueName, 0, 0);

      connection.close();
   }


   @Test
   public void testDeliveringStats() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      Connection connection = cf.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(defaultQueueName));
      producer.send(session.createTextMessage("test"));

      verifyPendingStats(defaultQueueName, 1, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 1, publishedMessageSize.get());
      verifyDeliveringStats(defaultQueueName, 0, 0);

      MessageConsumer consumer = session.createConsumer(session.createQueue(defaultQueueName));
      Message msg = consumer.receive();
      verifyDeliveringStats(defaultQueueName, 1, publishedMessageSize.get());
      msg.acknowledge();

      verifyPendingStats(defaultQueueName, 0, 0);
      verifyPendingDurableStats(defaultQueueName, 0, 0);
      verifyDeliveringStats(defaultQueueName, 0, 0);

      connection.close();
   }
   @Test
   public void testQueueMessageSizeAfterConsumptionNonPersistent() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      publishTestQueueMessages(200, DeliveryMode.NON_PERSISTENT, publishedMessageSize);
      verifyPendingStats(defaultQueueName, 200, publishedMessageSize.get());

      consumeTestQueueMessages(200);

      verifyPendingStats(defaultQueueName, 0, 0);
      verifyPendingDurableStats(defaultQueueName, 0, 0);
   }

   @Test
   public void testTopicMessageSize() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      Connection connection = cf.createConnection();
      connection.setClientID("clientId");
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(session.createTopic(defaultTopicName));

      publishTestTopicMessages(200, publishedMessageSize);

      verifyPendingStats(defaultTopicName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultQueueName, 0, 0);

      // consume all messages
      consumeTestMessages(consumer, 200);

      // All messages should now be gone
      verifyPendingStats(defaultTopicName, 0, 0);
      verifyPendingDurableStats(defaultQueueName, 0, 0);

      connection.close();
   }

   @Test
   public void testTopicMessageSizeShared() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      Connection connection = cf.createConnection();
      connection.setClientID("clientId");
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createSharedConsumer(session.createTopic(defaultTopicName), "sub1");
      MessageConsumer consumer2 = session.createSharedConsumer(session.createTopic(defaultTopicName), "sub1");

      publishTestTopicMessages(200, publishedMessageSize);

      verifyPendingStats(defaultTopicName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultTopicName, 0, 0);
      consumer2.close();

      // consume all messages
      consumeTestMessages(consumer, 200);

      // All messages should now be gone
      verifyPendingStats(defaultTopicName, 0, 0);
      verifyPendingDurableStats(defaultTopicName, 0, 0);

      connection.close();
   }

   @Test
   public void testTopicNonPersistentMessageSize() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      Connection connection = cf.createConnection();
      connection.setClientID("clientId");
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(session.createTopic(defaultTopicName));

      publishTestTopicMessages(200, DeliveryMode.NON_PERSISTENT, publishedMessageSize);

      verifyPendingStats(defaultTopicName, 200, publishedMessageSize.get());

      // consume all messages
      consumeTestMessages(consumer, 200);

      // All messages should now be gone
      verifyPendingStats(defaultTopicName, 0, 0);

      connection.close();
   }

   @Test
   public void testTopicPersistentAndNonPersistentMessageSize() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();
      AtomicLong publishedNonPersistentMessageSize = new AtomicLong();

      Connection connection = cf.createConnection();
      connection.setClientID("clientId");
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(session.createTopic(defaultTopicName));

      publishTestTopicMessages(100, DeliveryMode.NON_PERSISTENT, publishedNonPersistentMessageSize);
      publishTestTopicMessages(100, DeliveryMode.PERSISTENT, publishedMessageSize);

      verifyPendingStats(defaultTopicName, 200, publishedMessageSize.get() + publishedNonPersistentMessageSize.get());

      // consume all messages
      consumeTestMessages(consumer, 200);

      // All messages should now be gone
      verifyPendingStats(defaultTopicName, 0, 0);

      connection.close();
   }

   @Test
   public void testMessageSizeOneDurable() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();
      Connection connection = cf.createConnection();
      connection.setClientID("clientId");
      connection.start();

      publishTestMessagesDurable(connection, new String[] {"sub1"}, 200, publishedMessageSize,
            DeliveryMode.PERSISTENT, false);

      // verify the count and size - durable is offline so all 200 should be
      // pending since none are in prefetch
      verifyPendingStats(defaultTopicName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultTopicName, 200, publishedMessageSize.get());

      // consume all messages
      consumeDurableTestMessages(connection, "sub1", 200, publishedMessageSize);

      // All messages should now be gone
      verifyPendingStats(defaultTopicName, 0, 0);
      verifyPendingDurableStats(defaultTopicName, 0, 0);

      connection.close();
   }

   @Test
   public void testMessageSizeOneDurablePartialConsumption() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      Connection connection = cf.createConnection();
      connection.setClientID("clientId");
      connection.start();

      publishTestMessagesDurable(connection, new String[] {"sub1"}, 200, publishedMessageSize,
            DeliveryMode.PERSISTENT, false);

      verifyPendingStats(defaultTopicName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultTopicName, 200, publishedMessageSize.get());

      // consume partial messages
      consumeDurableTestMessages(connection, "sub1", 50, publishedMessageSize);

      // 150 should be left
      verifyPendingStats(defaultTopicName, 150, publishedMessageSize.get());
      // We don't really know the size here but it should be smaller than before
      // so take an average
      verifyPendingDurableStats(defaultTopicName, 150, (long) (.75 * publishedMessageSize.get()));

      connection.close();
   }

   @Test
   public void testMessageSizeTwoDurables() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      Connection connection = cf.createConnection();
      connection.setClientID("clientId");
      connection.start();

      publishTestMessagesDurable(connection, new String[] {"sub1", "sub2"}, 200, publishedMessageSize,
            DeliveryMode.PERSISTENT, false);

      // verify the count and size - double because two durables so two queue
      // bindings
      verifyPendingStats(defaultTopicName, 400, 2 * publishedMessageSize.get());
      verifyPendingDurableStats(defaultTopicName, 400, 2 * publishedMessageSize.get());

      // consume messages just for sub1
      consumeDurableTestMessages(connection, "sub1", 200, publishedMessageSize);

      // There is still a durable that hasn't consumed so the messages should
      // exist
      verifyPendingStats(defaultTopicName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultTopicName, 200, publishedMessageSize.get());

      connection.close();

      // restart and verify load
      this.killServer();
      this.restartServer();
      verifyPendingStats(defaultTopicName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultTopicName, 200, publishedMessageSize.get());
   }

   @Test
   public void testMessageSizeSharedDurable() throws Exception {
      AtomicLong publishedMessageSize = new AtomicLong();

      Connection connection = cf.createConnection();
      connection.setClientID("clientId");
      connection.start();

      // The publish method will create a second shared consumer
      Session s = connection.createSession();
      MessageConsumer c = s.createSharedDurableConsumer(s.createTopic(defaultTopicName), "sub1");
      publishTestMessagesDurable(connection, new String[] {"sub1"}, 200, publishedMessageSize,
            DeliveryMode.PERSISTENT, true);

      // verify the count and size - double because two durables so two queue
      // bindings
      verifyPendingStats(defaultTopicName, 200, publishedMessageSize.get());
      verifyPendingDurableStats(defaultTopicName, 200, publishedMessageSize.get());
      c.close();

      // consume messages for sub1
      consumeDurableTestMessages(connection, "sub1", 200, publishedMessageSize);
      verifyPendingStats(defaultTopicName, 0, publishedMessageSize.get());
      verifyPendingDurableStats(defaultTopicName, 0, publishedMessageSize.get());

      connection.close();
   }

   protected List<Queue> getQueues(final String address) throws Exception {
      final List<Queue> queues = new ArrayList<>();
      for (Binding binding : server.getPostOffice().getDirectBindings(SimpleString.of(address))) {
         if (binding.getType() == BindingType.LOCAL_QUEUE) {
            LocalQueueBinding queueBinding = (LocalQueueBinding) binding;
            queues.add(queueBinding.getQueue());
         }
      }
      return queues;
   }

   protected void verifyDeliveringStats(final String address, final int count, final long minimumSize) throws Exception {
      verifyStats(address, count, minimumSize, org.apache.activemq.artemis.core.server.Queue::getDeliveringCount,
            org.apache.activemq.artemis.core.server.Queue::getDeliveringSize);
      verifyStats(address, count, minimumSize, org.apache.activemq.artemis.core.server.Queue::getDurableDeliveringCount,
            org.apache.activemq.artemis.core.server.Queue::getDurableDeliveringSize);
   }


   protected void verifyScheduledStats(final String address, final int count, final long minimumSize) throws Exception {
      verifyStats(address, count, minimumSize, org.apache.activemq.artemis.core.server.Queue::getScheduledCount,
            org.apache.activemq.artemis.core.server.Queue::getScheduledSize);
      verifyStats(address, count, minimumSize, org.apache.activemq.artemis.core.server.Queue::getDurableScheduledCount,
            org.apache.activemq.artemis.core.server.Queue::getDurableScheduledSize);
   }

   protected void verifyPendingStats(final String address, final int count, final long minimumSize) throws Exception {
      verifyStats(address, count, minimumSize, org.apache.activemq.artemis.core.server.Queue::getMessageCount,
            org.apache.activemq.artemis.core.server.Queue::getPersistentSize);
   }

   protected void verifyPendingDurableStats(final String address, final int count, final long minimumSize)
         throws Exception {

      verifyStats(address, count, minimumSize, org.apache.activemq.artemis.core.server.Queue::getDurableMessageCount,
            org.apache.activemq.artemis.core.server.Queue::getDurablePersistentSize);
   }

   protected void verifyStats(final String address, final int count, final long minimumSize,
         ToLongFunction<Queue> countFunc, ToLongFunction<Queue> sizeFunc)
         throws Exception {
      final List<Queue> queues = getQueues(address);

      assertTrue(Wait.waitFor(() -> queues.stream().mapToLong(countFunc).sum() == count));
      verifySize(count, () -> queues.stream().mapToLong(sizeFunc).sum(), minimumSize);
   }

   protected void verifySize(final int count, final MessageSizeCalculator messageSizeCalculator, final long minimumSize)
         throws Exception {
      if (count > 0) {
         assertTrue(Wait.waitFor(() -> messageSizeCalculator.getMessageSize() > minimumSize));
      } else {
         assertTrue(Wait.waitFor(() -> messageSizeCalculator.getMessageSize() == 0));
      }
   }

   protected interface MessageSizeCalculator {
      long getMessageSize() throws Exception;
   }

   protected void consumeTestMessages(MessageConsumer consumer, int size) throws Exception {
      consumeTestMessages(consumer, size, defaultTopicName);
   }

   protected void consumeTestMessages(MessageConsumer consumer, int size, String topicName) throws Exception {
      for (int i = 0; i < size; i++) {
         consumer.receive();
      }
   }

   protected void consumeDurableTestMessages(Connection connection, String sub, int size,
         AtomicLong publishedMessageSize) throws Exception {
      consumeDurableTestMessages(connection, sub, size, defaultTopicName, publishedMessageSize);
   }

   protected void publishTestMessagesDurable(Connection connection, String[] subNames, int publishSize,
         AtomicLong publishedMessageSize, int deliveryMode, boolean shared) throws Exception {

      publishTestMessagesDurable(connection, subNames, defaultTopicName, publishSize, 0,
            AbstractPersistentStatTestSupport.defaultMessageSize, publishedMessageSize, false, deliveryMode, shared);
   }

   protected void publishTestTopicMessages(int publishSize, AtomicLong publishedMessageSize) throws Exception {
      publishTestTopicMessages(publishSize, DeliveryMode.PERSISTENT, publishedMessageSize);
   }

   protected void publishTestTopicMessages(int publishSize, int deliveryMode, AtomicLong publishedMessageSize)
         throws Exception {
      // create a new queue
      Connection connection = cf.createConnection();
      connection.setClientID("clientId2");
      connection.start();

      // Start the connection
      Session session = connection.createSession(false, TopicSession.AUTO_ACKNOWLEDGE);
      Topic topic = session.createTopic(defaultTopicName);

      try {
         // publish a bunch of non-persistent messages to fill up the temp
         // store
         MessageProducer prod = session.createProducer(topic);
         prod.setDeliveryMode(deliveryMode);
         for (int i = 0; i < publishSize; i++) {
            prod.send(createMessage(i, session, JournalPendingMessageTest.maxMessageSize, publishedMessageSize));
         }

      } finally {
         connection.close();
      }
   }

   protected void publishTestQueueMessagesTx(int count, AtomicLong publishedMessageSize) throws Exception {
      publishTestQueueMessages(count, defaultQueueName, DeliveryMode.PERSISTENT,
            JournalPendingMessageTest.maxMessageSize, publishedMessageSize, true);
   }

   protected void publishTestQueueMessages(int count, AtomicLong publishedMessageSize) throws Exception {
      publishTestQueueMessages(count, defaultQueueName, DeliveryMode.PERSISTENT,
            JournalPendingMessageTest.maxMessageSize, publishedMessageSize, false);
   }

   protected void publishTestQueueMessages(int count, int deliveryMode, AtomicLong publishedMessageSize)
         throws Exception {
      publishTestQueueMessages(count, defaultQueueName, deliveryMode, JournalPendingMessageTest.maxMessageSize,
            publishedMessageSize, false);
   }

   protected void consumeTestQueueMessages(int num) throws Exception {
      consumeTestQueueMessages(defaultQueueName, num);
   }

}
