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

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeTestAccessor;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.utils.DestinationUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class JMSMessageConsumerTest extends MultiprotocolJMSClientTestSupport {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(30)
   public void testDeliveryModeAMQPProducerCoreConsumer() throws Exception {
      Connection connection = createConnection(); //AMQP
      Connection connection2 = createCoreConnection(); //CORE
      testDeliveryMode(connection, connection2);
   }

   @Test
   @Timeout(30)
   public void testDeliveryModeAMQPProducerAMQPConsumer() throws Exception {
      Connection connection = createConnection(); //AMQP
      Connection connection2 = createConnection(); //AMQP
      testDeliveryMode(connection, connection2);
   }

   @Test
   @Timeout(30)
   public void testDeliveryModeCoreProducerAMQPConsumer() throws Exception {
      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createConnection(); //AMQP
      testDeliveryMode(connection, connection2);
   }

   @Test
   @Timeout(30)
   public void testDeliveryModeCoreProducerCoreConsumer() throws Exception {
      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createCoreConnection(); //CORE
      testDeliveryMode(connection, connection2);
   }

   private void testDeliveryMode(Connection connection1, Connection connection2) throws JMSException {
      try {
         Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         javax.jms.Queue queue1 = session1.createQueue(getQueueName());
         javax.jms.Queue queue2 = session2.createQueue(getQueueName());

         final MessageConsumer consumer2 = session2.createConsumer(queue2);

         MessageProducer producer = session1.createProducer(queue1);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         connection1.start();

         TextMessage message = session1.createTextMessage();
         message.setText("hello");
         producer.send(message);

         Message received = consumer2.receive(100);

         assertNotNull(received, "Should have received a message by now.");
         assertInstanceOf(TextMessage.class, received, "Should be an instance of TextMessage");
         assertEquals(DeliveryMode.PERSISTENT, received.getJMSDeliveryMode());
      } finally {
         connection1.close();
         connection2.close();
      }
   }

   @Test
   @Timeout(30)
   public void testAnycastPlusWildcardCore() throws Exception {
      testAnycastPlusWildcard(CoreConnection);
   }

   @Test
   @Timeout(30)
   public void testAnycastPlusWildcardAMQP() throws Exception {
      testAnycastPlusWildcard(AMQPConnection);
   }

   @Test
   @Timeout(30)
   public void testAnycastPlusWildcardOpenWire() throws Exception {
      testAnycastPlusWildcard(OpenWireConnection);
   }

   /*
    * This test ensures that even if a binding for a wildcard queue already exists on an anycast address the broker will
    * still create a normal queue for a consumer.
    */
   private void testAnycastPlusWildcard(ConnectionSupplier supplier) throws Exception {
      final String wildcardQueueName = "a.*.c";
      final String normalQueueName = "a.b.c";

      Connection wildcardConnection = supplier.createConnection();
      Connection normalConnection = supplier.createConnection();
      Connection producerConnection = supplier.createConnection();

      try {
         Session wildcardSession = wildcardConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session normalSession = normalConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         javax.jms.Queue wildcardQueue = wildcardSession.createQueue(wildcardQueueName);
         javax.jms.Queue normalQueue = normalSession.createQueue(normalQueueName);

         MessageConsumer wildcardConsumer = wildcardSession.createConsumer(wildcardQueue);
         assertNotNull(server.locateQueue(wildcardQueueName));

         MessageConsumer normalConsumer = normalSession.createConsumer(normalQueue);
         assertNotNull(server.locateQueue(normalQueueName));

         normalConsumer.close();

         Wait.assertEquals(0L, () -> server.locateQueue(normalQueueName).getConsumerCount(), 3000, 20);

         PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());

         Wait.assertTrue(() -> server.locateQueue(normalQueueName) == null, 3000, 20);
         assertNotNull(server.locateQueue(wildcardQueueName));

         normalConsumer = normalSession.createConsumer(normalQueue);

         assertNotNull(server.locateQueue(normalQueueName));

         assertEquals(0L, server.locateQueue(wildcardQueueName).getMessageCount());
         assertEquals(0L, server.locateQueue(normalQueueName).getMessageCount());

         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSession.createProducer(normalQueue);
         producer.send(producerSession.createMessage());
         producer.close();

         Wait.assertEquals(1L, () -> server.locateQueue(wildcardQueueName).getMessageCount(), 1000, 20);
         Wait.assertEquals(1L, () -> server.locateQueue(normalQueueName).getMessageCount(), 1000, 20);

         assertNotNull(wildcardConsumer.receive(200));
         assertNotNull(normalConsumer.receive(200));
      } finally {
         wildcardConnection.close();
         normalConnection.close();
         producerConnection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testQueueRoutingTypeMismatchCore() throws Exception {
      testQueueRoutingTypeMismatch(createCoreConnection());
   }

   @Test
   @Timeout(30)
   public void testQueueRoutingTypeMismatchOpenWire() throws Exception {
      testQueueRoutingTypeMismatch(createOpenWireConnection());
   }

   @Test
   @Timeout(30)
   public void testQueueRoutingTypeMismatchAMQP() throws Exception {
      testQueueRoutingTypeMismatch(createConnection());
   }

   private void testQueueRoutingTypeMismatch(Connection connection) throws Exception {
      server.getAddressSettingsRepository().getMatch("#").setAutoCreateQueues(false).setAutoCreateAddresses(false);
      String name = getTopicName();
      server.createQueue(QueueConfiguration.of(name).setAddress(name).setRoutingType(RoutingType.MULTICAST).setAutoCreateAddress(true));
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         session.createConsumer(session.createQueue(name));
         fail("Should have thrown a JMSException!");
      } catch (JMSException e) {
         // expected
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testPriorityAMQPProducerCoreConsumer() throws Exception {
      Connection connection = createConnection(); //AMQP
      Connection connection2 = createCoreConnection(); //CORE
      testPriority(connection, connection2);
   }

   @Test
   @Timeout(30)
   public void testPriorityAMQPProducerAMQPConsumer() throws Exception {
      Connection connection = createConnection(); //AMQP
      Connection connection2 = createConnection(); //AMQP
      testPriority(connection, connection2);
   }

   @Test
   @Timeout(30)
   public void testPriorityModeCoreProducerAMQPConsumer() throws Exception {
      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createConnection(); //AMQP
      testPriority(connection, connection2);
   }

   @Test
   @Timeout(30)
   public void testPriorityCoreProducerCoreConsumer() throws Exception {
      Connection connection = createCoreConnection(); //CORE
      Connection connection2 = createCoreConnection(); //CORE
      testPriority(connection, connection2);
   }

   private void testPriority(Connection connection1, Connection connection2) throws JMSException {
      try {
         Session session1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         javax.jms.Queue queue1 = session1.createQueue(getQueueName());
         javax.jms.Queue queue2 = session2.createQueue(getQueueName());

         final MessageConsumer consumer2 = session2.createConsumer(queue2);

         MessageProducer producer = session1.createProducer(queue1);
         producer.setPriority(2);
         connection1.start();

         TextMessage message = session1.createTextMessage();
         message.setText("hello");
         producer.send(message);

         Message received = consumer2.receive(100);

         assertNotNull(received, "Should have received a message by now.");
         assertInstanceOf(TextMessage.class, received, "Should be an instance of TextMessage");
         assertEquals(2, received.getJMSPriority());
      } finally {
         connection1.close();
         connection2.close();
      }
   }

   @Test
   @Timeout(60)
   public void testDurableSubscriptionWithConfigurationManagedQueueWithCore() throws Exception {
      testDurableSubscriptionWithConfigurationManagedQueue(() -> createCoreConnection(false));

   }

   @Test
   @Timeout(60)
   public void testDurableSubscriptionWithConfigurationManagedQueueWithOpenWire() throws Exception {
      testDurableSubscriptionWithConfigurationManagedQueue(() -> createOpenWireConnection(false));

   }

   @Test
   @Timeout(60)
   public void testDurableSubscriptionWithConfigurationManagedQueueWithAMQP() throws Exception {
      testDurableSubscriptionWithConfigurationManagedQueue(() -> JMSMessageConsumerTest.super.createConnection(false));
   }

   private void testDurableSubscriptionWithConfigurationManagedQueue(ConnectionSupplier connectionSupplier) throws Exception {
      final String clientId = "bar";
      final String subName = "foo";
      final String queueName = DestinationUtil.createQueueNameForSubscription(true, clientId, subName).toString();
      server.stop();
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(queueName).setAddress("myTopic").setFilterString("color = 'BLUE'").setRoutingType(RoutingType.MULTICAST));
      server.getConfiguration().setAmqpUseCoreSubscriptionNaming(true);
      server.start();

      try (Connection connection = connectionSupplier.createConnection()) {
         connection.setClientID(clientId);
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic destination = session.createTopic("myTopic");

         MessageConsumer messageConsumer = session.createDurableSubscriber(destination, subName);
         messageConsumer.close();

         Queue queue = server.locateQueue(queueName);
         assertNotNull(queue);
         assertNotNull(queue.getFilter());
         assertEquals("color = 'BLUE'", queue.getFilter().getFilterString().toString());
      }
   }

   @Test
   @Timeout(30)
   public void testEmptyMapMessageConversionBetweenOpenWireAndAMQP() throws Exception {
      testEmptyMapMessageConversion(createOpenWireConnection(), createConnection());
   }

   @Test
   @Timeout(30)
   public void testEmptyMapMessageConversionBetweenAMQPAndOpenWire() throws Exception {
      testEmptyMapMessageConversion(createConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(30)
   public void testEmptyMapMessageConversionBetweenCoreAndAMQP() throws Exception {
      testEmptyMapMessageConversion(createCoreConnection(), createConnection());
   }

   @Test
   @Timeout(30)
   public void testEmptyMapMessageConversionBetweenAMQPAndCore() throws Exception {
      testEmptyMapMessageConversion(createConnection(), createCoreConnection());
   }

   @Test
   @Timeout(30)
   public void testEmptyMapMessageConversionBetweenCoreAndOpenWire() throws Exception {
      testEmptyMapMessageConversion(createCoreConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(30)
   public void testEmptyMapMessageConversionBetweenOpenWireAndCore() throws Exception {
      testEmptyMapMessageConversion(createOpenWireConnection(), createCoreConnection());
   }

   private void testEmptyMapMessageConversion(Connection senderConnection, Connection consumerConnection) throws Exception {
      try {
         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSession.createConsumer(consumerSession.createQueue(getQueueName()));

         Session senderSession = senderConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = senderSession.createProducer(senderSession.createQueue(getQueueName()));
         MapMessage message = senderSession.createMapMessage();
         producer.send(message);

         Message received = consumer.receive(1000);

         assertNotNull(received, "Should have received a message by now.");
         assertInstanceOf(MapMessage.class, received, "Should be an instance of MapMessage");
      } finally {
         senderConnection.close();
         consumerConnection.close();
      }
   }

   @Test
   @Timeout(30)
   public void testMapMessageConversionBetweenAMQPAndOpenWire() throws Exception {
      testMapMessageConversion(createConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(30)
   public void testMapMessageConversionBetweenCoreAndAMQP() throws Exception {
      testMapMessageConversion(createCoreConnection(), createConnection());
   }

   @Test
   @Timeout(30)
   public void testMapMessageConversionBetweenAMQPAndCore() throws Exception {
      testMapMessageConversion(createConnection(), createCoreConnection());
   }

   @Test
   @Timeout(30)
   public void testMapMessageConversionBetweenCoreAndOpenWire() throws Exception {
      testMapMessageConversion(createCoreConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(30)
   public void testMapMessageConversionBetweenOpenWireAndCore() throws Exception {
      testMapMessageConversion(createOpenWireConnection(), createCoreConnection());
   }

   private void testMapMessageConversion(Connection senderConnection, Connection consumerConnection) throws Exception {
      final boolean BOOLEAN_VALUE = RandomUtil.randomBoolean();
      final String BOOLEAN_KEY = "myBoolean";
      final byte BYTE_VALUE = RandomUtil.randomByte();
      final String BYTE_KEY = "myByte";
      final byte[] BYTES_VALUE = RandomUtil.randomBytes();
      final String BYTES_KEY = "myBytes";
      final char CHAR_VALUE = RandomUtil.randomChar();
      final String CHAR_KEY = "myChar";
      final double DOUBLE_VALUE = RandomUtil.randomDouble();
      final String DOUBLE_KEY = "myDouble";
      final float FLOAT_VALUE = RandomUtil.randomFloat();
      final String FLOAT_KEY = "myFloat";
      final int INT_VALUE = RandomUtil.randomInt();
      final String INT_KEY = "myInt";
      final long LONG_VALUE = RandomUtil.randomLong();
      final String LONG_KEY = "myLong";
      final Boolean OBJECT_VALUE = RandomUtil.randomBoolean();
      final String OBJECT_KEY = "myObject";
      final short SHORT_VALUE = RandomUtil.randomShort();
      final String SHORT_KEY = "myShort";
      final String STRING_VALUE = RandomUtil.randomUUIDString();
      final String STRING_KEY = "myString";

      try {
         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSession.createConsumer(consumerSession.createQueue(getQueueName()));

         Session senderSession = senderConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = senderSession.createProducer(senderSession.createQueue(getQueueName()));
         MapMessage message = senderSession.createMapMessage();message.setBoolean(BOOLEAN_KEY, BOOLEAN_VALUE);
         message.setByte(BYTE_KEY, BYTE_VALUE);
         message.setBytes(BYTES_KEY, BYTES_VALUE);
         message.setChar(CHAR_KEY, CHAR_VALUE);
         message.setDouble(DOUBLE_KEY, DOUBLE_VALUE);
         message.setFloat(FLOAT_KEY, FLOAT_VALUE);
         message.setInt(INT_KEY, INT_VALUE);
         message.setLong(LONG_KEY, LONG_VALUE);
         message.setObject(OBJECT_KEY, OBJECT_VALUE);
         message.setShort(SHORT_KEY, SHORT_VALUE);
         message.setString(STRING_KEY, STRING_VALUE);
         producer.send(message);

         Message received = consumer.receive(1000);

         assertNotNull(received, "Should have received a message by now.");
         assertInstanceOf(MapMessage.class, received, "Should be an instance of MapMessage");
         MapMessage receivedMapMessage = (MapMessage) received;

         assertEquals(BOOLEAN_VALUE, receivedMapMessage.getBoolean(BOOLEAN_KEY));
         assertEquals(BYTE_VALUE, receivedMapMessage.getByte(BYTE_KEY));
         assertEqualsByteArrays(BYTES_VALUE, receivedMapMessage.getBytes(BYTES_KEY));
         assertEquals(CHAR_VALUE, receivedMapMessage.getChar(CHAR_KEY));
         assertEquals(DOUBLE_VALUE, receivedMapMessage.getDouble(DOUBLE_KEY), 0);
         assertEquals(FLOAT_VALUE, receivedMapMessage.getFloat(FLOAT_KEY), 0);
         assertEquals(INT_VALUE, receivedMapMessage.getInt(INT_KEY));
         assertEquals(LONG_VALUE, receivedMapMessage.getLong(LONG_KEY));
         assertInstanceOf(Boolean.class, receivedMapMessage.getObject(OBJECT_KEY));
         assertEquals(OBJECT_VALUE, receivedMapMessage.getObject(OBJECT_KEY));
         assertEquals(SHORT_VALUE, receivedMapMessage.getShort(SHORT_KEY));
         assertEquals(STRING_VALUE, receivedMapMessage.getString(STRING_KEY));
      } finally {
         senderConnection.close();
         consumerConnection.close();
      }
   }


   @Test
   public void testConvertedAndPaging() throws Exception {
      final int MESSAGE_COUNT = 1;
      server.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST));
      PagingStore store = server.getPagingManager().getPageStore(SimpleString.of(getQueueName()));
      store.startPaging();
      try (Connection senderConnection = createConnection(); Connection consumerConnection = createCoreConnection()) {
         Session consumerSession = consumerConnection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = consumerSession.createConsumer(consumerSession.createQueue(getQueueName()));

         Session senderSession = senderConnection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = senderSession.createProducer(senderSession.createQueue(getQueueName()));
         for (int i = 0; i < MESSAGE_COUNT; i++) {
            Message message = senderSession.createMessage();
            message.setIntProperty("count", i); // test will also pass if this is removed
            producer.send(message);
         }
         senderSession.commit();

         for (int i = 0; i < MESSAGE_COUNT; i++) {
            Message received = consumer.receive(1000);
            assertNotNull(received);
         }
         consumerSession.commit();
         consumer.close();

         assertEquals(0, server.locateQueue(getQueueName()).getMessageCount());
         Wait.assertEquals(0, store::getAddressSize, 5000);
         assertEquals(0, (server.getManagementService().getAddressControl(getQueueName())).getAddressSize());
      }
   }

}
