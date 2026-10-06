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
package org.apache.activemq.artemis.tests.integration.client;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(ParameterizedTestExtension.class)
public class ConsumerTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected boolean waitForBindings(ActiveMQServer server,
                                     String address,
                                     boolean local,
                                     int expectedBindingCount,
                                     int expectedConsumerCount,
                                     long timeout) throws Exception {
      return super.waitForBindings(server, address, local, expectedBindingCount, expectedConsumerCount, timeout);
   }

   @Parameters(name = "isNetty={0}, persistent={1}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{true, true}, {false, false}, {false, true}, {true, false}});
   }

   public ConsumerTest(boolean netty, boolean durable) {
      this.netty = netty;
      this.durable = durable;
   }

   private final boolean durable;
   private final boolean netty;
   private ActiveMQServer server;

   private final SimpleString QUEUE = SimpleString.of("ConsumerTestQueue");

   private ServerLocator locator;

   protected boolean isNetty() {
      return netty;
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(durable, isNetty());

      server.getConfiguration().setAddressQueueScanPeriod(10);

      server.start();

      locator = createFactory(isNetty());

      server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));
   }

   @TestTemplate
   public void testStressConnection() throws Exception {

      for (int i = 0; i < 10; i++) {
         ServerLocator locatorSendx = createFactory(isNetty()).setReconnectAttempts(15);
         ClientSessionFactory factoryx = locatorSendx.createSessionFactory();
         factoryx.close();
         locatorSendx.close();
      }

   }

   @TestTemplate
   public void testSimpleSend() throws Throwable {
      receive(false);
   }

   @TestTemplate
   public void testSimpleSendWithCloseConsumer() throws Throwable {
      receive(true);
   }

   private void receive(boolean cancelOnce) throws Throwable {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, false);

      ClientProducer producer = session.createProducer(QUEUE);
      ClientMessage message = session.createMessage(Message.TEXT_TYPE, true, 0, System.currentTimeMillis(), (byte) 4);
      message.getBodyBuffer().writeString("hi");
      message.putStringProperty("hello", "elo");
      producer.send(message);

      session.commit();

      session.close();
      if (durable) {
         server.stop();
         server.start();
      }
      sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true, false);
      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      if (cancelOnce) {
         final ClientConsumerInternal consumerInternal = (ClientConsumerInternal) consumer;
         Wait.waitFor(() -> consumerInternal.getBufferSize() > 0);
         consumer.close();
         consumer = session.createConsumer(QUEUE);
      }
      ClientMessage message2 = consumer.receive(1000);

      assertNotNull(message2);

      logger.debug("Id::{}", message2.getMessageID());

      logger.debug("Received {}", message2);

      logger.debug("Clie:{}", ByteUtil.bytesToHex(message2.getBuffer().array(), 4));

      logger.debug("String::{}", message2.getReadOnlyBodyBuffer().readString());

      assertEquals("elo", message2.getStringProperty("hello"));

      assertEquals("hi", message2.getReadOnlyBodyBuffer().readString());

      assertNull(consumer.receiveImmediate());

      session.close();
   }

   @TestTemplate
   public void testSendReceiveAMQP() throws Throwable {

      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      internalSend(2, 2);
   }

   @TestTemplate
   public void testSendReceiveCore() throws Throwable {

      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      internalSend(1, 1);
   }

   @TestTemplate
   public void testSendAMQPReceiveCore() throws Throwable {

      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      internalSend(2, 1);
   }

   @TestTemplate
   public void testAutoCreateMulticastAddress() throws Throwable {
      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      assertNull(server.getAddressInfo(SimpleString.of("topic")));

      ConnectionFactory factorySend = createFactory(2);
      Connection connection = factorySend.createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Topic topic = session.createTopic("topic");
         MessageProducer producer = session.createProducer(topic);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         TextMessage msg = session.createTextMessage("hello");
         msg.setIntProperty("mycount", 0);
         producer.send(msg);
      } finally {
         connection.close();
      }

      assertNotNull(server.getAddressInfo(SimpleString.of("topic")));
      assertEquals(RoutingType.MULTICAST, server.getAddressInfo(SimpleString.of("topic")).getRoutingType());
      assertEquals(0, server.getTotalMessageCount());
   }

   @TestTemplate
   public void testAutoCreateCOnConsumerAMQP() throws Throwable {
      testAutoCreate(2);
   }

   @TestTemplate
   public void testAutoCreateCOnConsumerCore() throws Throwable {
      testAutoCreate(1);
   }

   @TestTemplate
   public void testAutoCreateCOnConsumerOpenWire() throws Throwable {
      testAutoCreate(3);
   }

   private void testAutoCreate(int protocol) throws Throwable {

      final SimpleString thisQueue = SimpleString.of("ThisQueue");
      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      for (int i = 0; i < 10; i++) {
         ConnectionFactory factorySend = createFactory(protocol);
         Connection connection = factorySend.createConnection();

         try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            javax.jms.Queue queue = session.createQueue(thisQueue.toString());
            MessageProducer producer = session.createProducer(queue);

            MessageConsumer consumer = session.createConsumer(queue);
            connection.start();

            producer.send(session.createTextMessage("hello"));

            assertNotNull(consumer.receive(5000));
            consumer.close();
            session.close();
         } finally {
            connection.close();
         }

         Wait.waitFor(() -> server.getAddressInfo(thisQueue) == null, 1000, 10);
         assertNull(server.getAddressInfo(thisQueue));
         assertEquals(0, server.getTotalMessageCount());
      }
   }

   @TestTemplate
   public void testContextOnConsumerAMQP() throws Throwable {
      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      assertNull(server.getAddressInfo(SimpleString.of("queue")));

      ConnectionFactory factory = createFactory(2);
      JMSContext context = factory.createContext("admin", "admin", Session.AUTO_ACKNOWLEDGE);

      try {
         javax.jms.Queue queue = context.createQueue("queue");

         JMSConsumer consumer = context.createConsumer(queue);

         ServerConsumer serverConsumer = null;
         for (ServerSession session : server.getSessions()) {
            for (ServerConsumer sessionConsumer : session.getServerConsumers()) {
               serverConsumer = sessionConsumer;
            }
         }

         assertInstanceOf(ProtonServerSenderContext.class, serverConsumer.getProtocolContext());

         final AMQPSessionContext sessionContext = ((ProtonServerSenderContext)
            serverConsumer.getProtocolContext()).getSessionContext();

         consumer.close();
         final ServerConsumer lambdaServerConsumer = serverConsumer;
         Wait.assertTrue(() -> lambdaServerConsumer.getProtocolContext() == null);

         Wait.assertEquals(0, () -> sessionContext.getSenderCount(), 1000, 10);

      } finally {
         context.stop();
         context.close();
      }
   }

   @TestTemplate
   public void testAutoDeleteAutoCreatedAddressAndQueue() throws Throwable {
      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      assertNull(server.getAddressInfo(SimpleString.of("queue")));

      ConnectionFactory factorySend = createFactory(2);
      Connection connection = factorySend.createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue("queue");
         MessageProducer producer = session.createProducer(queue);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         TextMessage msg = session.createTextMessage("hello");
         msg.setIntProperty("mycount", 0);
         producer.send(msg);

         connection.start();
         MessageConsumer consumer = session.createConsumer(queue);
         assertNotNull(consumer.receive(1000));
      } finally {
         connection.close();
      }

      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of("queue")) == null);
      Wait.assertTrue(() -> server.locateQueue(SimpleString.of("queue")) == null);
      Wait.assertEquals(0, server::getTotalMessageCount);
   }

   @TestTemplate
   public void testSendCoreReceiveAMQP() throws Throwable {

      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      internalSend(1, 2);
   }

   @TestTemplate
   public void testSendOpenWireReceiveAMQP() throws Throwable {

      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      internalSend(3, 2);
   }

   @TestTemplate
   public void testSendAMQPReceiveOpenWire() throws Throwable {

      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      internalSend(2, 3);
   }

   @TestTemplate
   public void testOpenWireReceiveCore() throws Throwable {

      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      internalSend(3, 1);
   }

   @TestTemplate
   public void testCoreReceiveOpenwire() throws Throwable {

      if (!isNetty()) {
         // no need to run the test, there's no AMQP support
         return;
      }

      internalSend(1, 3);
   }



   public static class MyTest implements Serializable {
      int i;

      public int getI() {
         return i;
      }

      public MyTest setI(int i) {
         this.i = i;
         return this;
      }
   }

   private ConnectionFactory createFactory(int protocol) {
      switch (protocol) {
         case 1: ActiveMQConnectionFactory coreCF = new ActiveMQConnectionFactory();// core protocol
            coreCF.setCompressLargeMessage(true);
            coreCF.setMinLargeMessageSize(10 * 1024);
            return coreCF;
         case 2: return new JmsConnectionFactory("amqp://localhost:61616"); // amqp
         case 3: return new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"); // openwire
         default: return null;
      }
   }


   public void internalSimpleSend(int protocolSender, int protocolConsumer) throws Throwable {

      ConnectionFactory factorySend = createFactory(protocolSender);
      ConnectionFactory factoryConsume = protocolConsumer == protocolSender ? factorySend : createFactory(protocolConsumer);

      final String bufferLargeContent = " ".repeat(100 * 1024);

      try (Connection connection = factorySend.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            javax.jms.Queue queue = session.createQueue(QUEUE.toString());
            try (MessageProducer producer = session.createProducer(queue)) {
               producer.setDeliveryMode(DeliveryMode.PERSISTENT);

               TextMessage msg = session.createTextMessage("hello");
               msg.setIntProperty("mycount", 0);
               producer.send(msg);

               msg = session.createTextMessage(bufferLargeContent);
               msg.setIntProperty("mycount", 1);
               producer.send(msg);
            }
         }

      }
      try (Connection connection = factoryConsume.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            javax.jms.Queue queue = session.createQueue(QUEUE.toString());

            try (MessageConsumer consumer = session.createConsumer(queue)) {

               TextMessage message = (TextMessage) consumer.receive(1000);
               assertNotNull(message);
               assertEquals(0, message.getIntProperty("mycount"));
               assertEquals("hello", message.getText());

               message = (TextMessage) consumer.receive(1000);
               assertNotNull(message);
               assertEquals(1, message.getIntProperty("mycount"));
               assertEquals(bufferLargeContent, message.getText());

               Wait.waitFor(() -> server.getPagingManager().getGlobalSize() == 0, 5000, 100);
               assertEquals(0, server.getPagingManager().getGlobalSize());
            }
         }
      }
   }


   public void internalSend(int protocolSender, int protocolConsumer) throws Throwable {

      internalSimpleSend(protocolSender, protocolConsumer);

      ConnectionFactory factorySend = createFactory(protocolSender);
      ConnectionFactory factoryConsume = protocolConsumer == protocolSender ? factorySend : createFactory(protocolConsumer);


      Connection connection = factorySend.createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(QUEUE.toString());
         MessageProducer producer = session.createProducer(queue);

         if (durable) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         } else {

            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
         }

         long time = System.currentTimeMillis();
         int NUMBER_OF_MESSAGES = durable ? 5 : 50;
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage msg = session.createTextMessage("hello " + i);
            msg.setIntProperty("mycount", i);
            producer.send(msg);

            ObjectMessage objectMessage = session.createObjectMessage(new MyTest().setI(i));
            producer.send(objectMessage);

            MapMessage mapMessage = session.createMapMessage();
            mapMessage.setInt("intOne", i);
            mapMessage.setString("stringOne", Integer.toString(i));
            producer.send(mapMessage);

            StreamMessage stream = session.createStreamMessage();
            stream.writeBoolean(true);
            stream.writeInt(i);
            producer.send(stream);

            BytesMessage bytes = session.createBytesMessage();
            bytes.writeUTF("string " + i);
            producer.send(bytes);
         }
         long end = System.currentTimeMillis();

         logger.debug("Time = {}", (end - time));

         {
            TextMessage dummyMessage = session.createTextMessage();
            dummyMessage.setJMSType("car");
            dummyMessage.setStringProperty("color", "red");
            dummyMessage.setLongProperty("weight", 3000);
            dummyMessage.setText("testSelectorExampleFromSpecs:1");
            producer.send(dummyMessage);

            dummyMessage = session.createTextMessage();
            dummyMessage.setJMSType("car");
            dummyMessage.setStringProperty("color", "blue");
            dummyMessage.setLongProperty("weight", 3000);
            dummyMessage.setText("testSelectorExampleFromSpecs:2");
            producer.send(dummyMessage);
         }




         connection.close();

         if (this.durable) {
            server.stop();
            server.start();
         }

         connection = factoryConsume.createConnection();
         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         queue = session.createQueue(QUEUE.toString());

         connection.start();

         MessageConsumer consumer = session.createConsumer(queue);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            assertEquals(i, message.getIntProperty("mycount"));
            assertEquals("hello " + i, message.getText());

            ObjectMessage objectMessage = (ObjectMessage)consumer.receive(5000);
            assertNotNull(objectMessage);
            assertEquals(i, ((MyTest)objectMessage.getObject()).getI());

            MapMessage mapMessage = (MapMessage) consumer.receive(1000);
            assertNotNull(mapMessage);
            assertEquals(i, mapMessage.getInt("intOne"));
            assertEquals(Integer.toString(i), mapMessage.getString("stringOne"));

            StreamMessage stream = (StreamMessage)consumer.receive(5000);
            assertTrue(stream.readBoolean());
            assertEquals(i, stream.readInt());

            BytesMessage bytes = (BytesMessage) consumer.receive(5000);
            assertEquals("string " + i, bytes.readUTF());
         }

         consumer.close();

         consumer = session.createConsumer(queue, "JMSType = 'car' AND color = 'blue' AND weight > 2500");

         TextMessage msg = (TextMessage) consumer.receive(1000);
         assertEquals("testSelectorExampleFromSpecs:2", msg.getText());

         consumer.close();

         consumer = session.createConsumer(queue);
         msg = (TextMessage)consumer.receive(5000);
         assertNotNull(msg);

         assertNull(consumer.receiveNoWait());

         Wait.waitFor(() -> server.getPagingManager().getGlobalSize() == 0, 5000, 100);


         assertEquals(0, server.getPagingManager().getGlobalSize());

      } finally {
         connection.close();
      }
   }

   @TestTemplate
   public void testConsumerAckImmediateAutoCommitTrue() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, true);

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(session, "m" + i);
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receive(1000);

         assertEquals("m" + i, message2.getBodyBuffer().readString());
      }
      // assert that all the messages are there and none have been acked
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(QUEUE).getBindable()).getDeliveringCount());
      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(QUEUE).getBindable())));

      session.close();
   }

   @TestTemplate
   public void testConsumerAckImmediateAutoCommitFalse() throws Exception {

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, false, true);

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(session, "m" + i);
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receive(1000);

         assertEquals("m" + i, message2.getBodyBuffer().readString());
      }
      // assert that all the messages are there and none have been acked
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(QUEUE).getBindable()).getDeliveringCount());
      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(QUEUE).getBindable())));

      session.close();
   }

   @TestTemplate
   public void testConsumerAckImmediateAckIgnored() throws Exception {

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, true);

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(session, "m" + i);
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receive(1000);

         assertEquals("m" + i, message2.getBodyBuffer().readString());
         if (i < 50) {
            message2.acknowledge();
         }
      }
      // assert that all the messages are there and none have been acked
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(QUEUE).getBindable()).getDeliveringCount());
      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(QUEUE).getBindable())));

      session.close();
   }

   @TestTemplate
   public void testConsumerAckImmediateCloseSession() throws Exception {

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, true);

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(session, "m" + i);
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receive(1000);

         assertEquals("m" + i, message2.getBodyBuffer().readString());
         if (i < 50) {
            message2.acknowledge();
         }
      }
      // assert that all the messages are there and none have been acked
      assertEquals(0, ((Queue) server.getPostOffice().getBinding(QUEUE).getBindable()).getDeliveringCount());
      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(QUEUE).getBindable())));

      session.close();

      assertEquals(0, ((Queue) server.getPostOffice().getBinding(QUEUE).getBindable()).getDeliveringCount());
      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(QUEUE).getBindable())));
   }

   @TestTemplate
   public void testAcksWithSmallSendWindow() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(session, "m" + i);
         producer.send(message);
      }
      session.close();
      sf.close();
      final CountDownLatch latch = new CountDownLatch(numMessages);
      server.getRemotingService().addIncomingInterceptor((Interceptor) (packet, connection) -> {
         if (packet.getType() == PacketImpl.SESS_ACKNOWLEDGE) {
            latch.countDown();
         }
         return true;
      });
      ServerLocator locator = createInVMNonHALocator().setConfirmationWindowSize(100).setAckBatchSize(-1);
      ClientSessionFactory sfReceive = createSessionFactory(locator);
      ClientSession sessionRec = sfReceive.createSession(false, true, true);
      ClientConsumer consumer = sessionRec.createConsumer(QUEUE);
      consumer.setMessageHandler(message -> {
         try {
            message.acknowledge();
         } catch (ActiveMQException e) {
            e.printStackTrace();
         }
      });
      sessionRec.start();
      assertTrue(latch.await(60, TimeUnit.SECONDS));
      sessionRec.close();
      locator.close();
   }

   @TestTemplate
   public void testConsumeWithNoConsumerFlowControl() throws Exception {

      ServerLocator locator = createInVMNonHALocator();

      locator.setConsumerWindowSize(-1);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(session, "m" + i);
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(10000);
         assertNotNull(message);
         message.acknowledge();
      }

      session.close();
      sf.close();
      locator.close();

   }

   @TestTemplate
   public void testClearListener() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);
      session.start();

      ClientConsumer consumer = session.createConsumer(QUEUE);

      consumer.setMessageHandler(msg -> {
      });

      consumer.setMessageHandler(null);
      consumer.receiveImmediate();

      session.close();
   }

   @TestTemplate
   public void testNoReceiveWithListener() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true);

      ClientConsumer consumer = session.createConsumer(QUEUE);

      consumer.setMessageHandler(msg -> {
      });

      try {
         consumer.receiveImmediate();
         fail("Should throw exception");
      } catch (ActiveMQIllegalStateException ise) {
         //ok
      } catch (ActiveMQException me) {
         fail("Wrong exception code");
      }

      session.close();
   }

   @TestTemplate
   public void testReceiveAndResend() throws Exception {

      final Set<Object> sessions = ConcurrentHashMap.newKeySet();
      final AtomicInteger errors = new AtomicInteger(0);

      final SimpleString QUEUE_RESPONSE = SimpleString.of("QUEUE_RESPONSE");

      final int numberOfSessions = 50;
      final int numberOfMessages = 10;

      final CountDownLatch latchReceive = new CountDownLatch(numberOfSessions * numberOfMessages);

      ClientSessionFactory sf = locator.createSessionFactory();
      for (int i = 0; i < numberOfSessions; i++) {

         ClientSession session = sf.createSession(false, true, true);

         sessions.add(session);

         session.createQueue(QueueConfiguration.of(QUEUE.concat("" + i)).setAddress(QUEUE).setDurable(true));

         if (i == 0) {
            session.createQueue(QueueConfiguration.of(QUEUE_RESPONSE));
         }

         ClientConsumer consumer = session.createConsumer(QUEUE.concat("" + i));
         sessions.add(consumer);

         {

            consumer.setMessageHandler(msg -> {
               try {
                  ServerLocator locatorSendx = createFactory(isNetty()).setReconnectAttempts(15);
                  ClientSessionFactory factoryx = locatorSendx.createSessionFactory();
                  ClientSession sessionSend = factoryx.createSession(true, true);

                  sessions.add(sessionSend);
                  sessions.add(locatorSendx);
                  sessions.add(factoryx);

                  final ClientProducer prod = sessionSend.createProducer(QUEUE_RESPONSE);
                  sessionSend.start();

                  sessions.add(prod);

                  msg.acknowledge();
                  prod.send(sessionSend.createMessage(true));
                  prod.close();
                  sessionSend.commit();
                  sessionSend.close();
                  factoryx.close();
                  if (Thread.currentThread().isInterrupted()) {
                     System.err.println("Netty has interrupted a thread!!!");
                     errors.incrementAndGet();
                  }

               } catch (Throwable e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               } finally {
                  latchReceive.countDown();
               }
            });
         }

         session.start();
      }

      Thread tCons = new Thread(() -> {
         try {
            final ServerLocator locatorSend = createFactory(isNetty());
            final ClientSessionFactory factory = locatorSend.createSessionFactory();
            final ClientSession sessionSend = factory.createSession(true, true);
            ClientConsumer cons = sessionSend.createConsumer(QUEUE_RESPONSE);
            sessionSend.start();

            for (int i = 0; i < numberOfMessages * numberOfSessions; i++) {
               ClientMessage msg = cons.receive(5000);
               if (msg == null) {
                  break;
               }
               msg.acknowledge();
            }

            if (cons.receiveImmediate() != null) {
               logger.debug("ERROR: Received an extra message");
               errors.incrementAndGet();
            }
            sessionSend.close();
            factory.close();
            locatorSend.close();
         } catch (Exception e) {
            e.printStackTrace();
            errors.incrementAndGet();

         }

      });

      tCons.start();

      ClientSession mainSessionSend = sf.createSession(true, true);
      ClientProducer mainProd = mainSessionSend.createProducer(QUEUE);

      for (int i = 0; i < numberOfMessages; i++) {
         mainProd.send(mainSessionSend.createMessage(true));
      }

      latchReceive.await(2, TimeUnit.MINUTES);

      tCons.join();

      sf.close();

      assertEquals(0, errors.get(), "Had errors along the execution");
   }

   // Test that, on rollback credits are released for messages cleared in the buffer
   @TestTemplate
   public void testConsumerCreditsOnRollback() throws Exception {
      locator.setConsumerWindowSize(10000);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createTransactedSession();

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      final byte[] bytes = new byte[1000];

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.getBodyBuffer().writeBytes(bytes);

         message.putIntProperty("count", i);

         producer.send(message);
      }

      session.commit();

      ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();

      for (int i = 0; i < 110; i++) {
         ClientMessage message = consumer.receive();

         int count = message.getIntProperty("count");

         boolean redelivered = message.getDeliveryCount() > 1;

         if (count % 2 == 0 && !redelivered) {
            session.rollback();
         } else {
            session.commit();
         }
      }

      session.close();
   }

   // Test that, on rollback credits are released for messages cleared in the buffer
   @TestTemplate
   public void testInVMURI() throws Exception {
      locator.close();
      ServerLocator locator = addServerLocator(ServerLocatorImpl.newLocator("vm:/1"));
      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession();
      ClientProducer producer = session.createProducer(QUEUE);
      producer.send(session.createMessage(true));

      ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();
      assertNotNull(consumer.receiveImmediate());
      session.close();
      factory.close();

   }

   // Test that, on rollback credits are released for messages cleared in the buffer
   @TestTemplate
   public void testConsumerCreditsOnRollbackLargeMessages() throws Exception {
      locator.setConsumerWindowSize(10000).setMinLargeMessageSize(1000);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createTransactedSession();

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      final byte[] bytes = new byte[10000];

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.getBodyBuffer().writeBytes(bytes);

         message.putIntProperty("count", i);

         producer.send(message);
      }

      session.commit();

      ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();

      for (int i = 0; i < 110; i++) {
         ClientMessage message = consumer.receive();

         int count = message.getIntProperty("count");

         boolean redelivered = message.getDeliveryCount() > 1;

         if (count % 2 == 0 && !redelivered) {
            session.rollback();
         } else {
            session.commit();
         }
      }

      session.close();
   }

   @TestTemplate
   public void testMultipleConsumersOnSharedQueue() throws Throwable {
      if (!isNetty() || this.durable) {
         return;
      }
      final boolean durable = false;
      final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(1);
      final int forks = 100;
      final int queues = forks;
      final int runs = 1;
      final int messages = 1;
      final ConnectionFactory factorySend = createFactory(1);
      final AtomicLongArray receivedMessages = new AtomicLongArray(forks);
      final Thread[] producersRunners = new Thread[forks];
      final Thread[] consumersRunners = new Thread[forks];
      //parties are forks (1 producer 1 consumer) + 1 controller in the main test thread
      final CyclicBarrier onStartRun = new CyclicBarrier((forks * 2) + 1);
      final CyclicBarrier onFinishRun = new CyclicBarrier((forks * 2) + 1);

      final int messagesSent = forks * messages;
      final AtomicInteger messagesRecieved = new AtomicInteger(0);

      for (int i = 0; i < forks; i++) {
         final int forkIndex = i;
         final String queueName = "q_" + (forkIndex % queues);
         final Thread producerRunner = new Thread(() -> {
            try (Connection connection = factorySend.createConnection()) {
               connection.start();
               try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                  final javax.jms.Queue queue = session.createQueue(queueName);
                  try (MessageProducer producer = session.createProducer(queue)) {
                     producer.setDeliveryMode(durable ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                     for (int r = 0; r < runs; r++) {
                        onStartRun.await();
                        for (int m = 0; m < messages; m++) {
                           final BytesMessage bytesMessage = session.createBytesMessage();
                           bytesMessage.writeInt(forkIndex);
                           producer.send(bytesMessage);
                        }
                        onFinishRun.await();
                     }
                  } catch (InterruptedException | BrokenBarrierException e) {
                     e.printStackTrace();
                  }
               }
            } catch (JMSException e) {
               e.printStackTrace();
            }
         });

         producerRunner.setDaemon(true);

         final Thread consumerRunner = new Thread(() -> {
            try (Connection connection = factorySend.createConnection()) {
               connection.start();
               try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                  final javax.jms.Queue queue = session.createQueue(queueName);
                  try (MessageConsumer consumer = session.createConsumer(queue)) {
                     for (int r = 0; r < runs; r++) {
                        onStartRun.await();
                        while (messagesRecieved.get() != messagesSent) {
                           final BytesMessage receivedMessage = (BytesMessage) consumer.receive(1000);
                           if (receivedMessage != null) {
                              final int receivedConsumerIndex = receivedMessage.readInt();
                              receivedMessages.getAndIncrement(receivedConsumerIndex);
                              messagesRecieved.incrementAndGet();
                           }
                        }
                        onFinishRun.await();
                     }
                  } catch (InterruptedException e) {
                     e.printStackTrace();
                  } catch (BrokenBarrierException e) {
                     e.printStackTrace();
                  }
               }
            } catch (JMSException e) {
               e.printStackTrace();
            }
         });
         consumerRunner.setDaemon(true);
         consumersRunners[forkIndex] = consumerRunner;
         producersRunners[forkIndex] = producerRunner;
      }
      Stream.of(consumersRunners).forEach(Thread::start);
      Stream.of(producersRunners).forEach(Thread::start);
      final long messagesPerRun = (forks * messages);
      for (int r = 0; r < runs; r++) {
         onStartRun.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
         logger.debug("started run {}", r);
         final long start = System.currentTimeMillis();
         onFinishRun.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
         final long elapsedMillis = System.currentTimeMillis() - start;
         logger.debug("{} msg/sec", (messagesPerRun * 1000L) / elapsedMillis);
      }
      Stream.of(producersRunners).forEach(runner -> {
         try {
            runner.join(TIMEOUT_MILLIS * runs);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      });
      Stream.of(producersRunners).forEach(Thread::interrupt);
      Stream.of(consumersRunners).forEach(runner -> {
         try {
            runner.join(TIMEOUT_MILLIS * runs);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      });
      Stream.of(consumersRunners).forEach(Thread::interrupt);
      for (int i = 0; i < forks; i++) {
         assertEquals(messages * runs, receivedMessages.get(i), "The consumer " + i + " must receive all the messages sent.");
      }
   }

   @TestTemplate
   public void testConsumerXpathSelector() throws Exception {
      final SimpleString BODY = SimpleString.of("<root><a key='first' num='1'/><b key='second' num='2'>b</b></root>");
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, false, true);

      ClientProducer producer = session.createProducer(QUEUE);

      ClientMessage message = session.createMessage(false);
      message.setType(Message.TEXT_TYPE);
      message.getBodyBuffer().writeNullableSimpleString(SimpleString.of("wrong"));
      producer.send(message);
      message = session.createMessage(false);
      message.setType(Message.TEXT_TYPE);
      message.getBodyBuffer().writeNullableSimpleString(BODY);
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(QUEUE.toString(), "XPATH 'root/a'");
      session.start();
      ClientMessage message2 = consumer.receive(5000);
      assertNotNull(message2);

      assertEquals(BODY, message2.getBodyBuffer().readNullableSimpleString());
      assertEquals(1, getMessageCount(((Queue) server.getPostOffice().getBinding(QUEUE).getBindable())));

      session.close();
   }
}
