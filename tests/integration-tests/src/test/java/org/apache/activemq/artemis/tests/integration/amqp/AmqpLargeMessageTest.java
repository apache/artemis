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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessage;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

@ExtendWith(ParameterizedTestExtension.class)
public class AmqpLargeMessageTest extends AmqpClientTestSupport {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Random rand = new Random(System.currentTimeMillis());

   @Parameter(index = 0)
   public int frameSize = 32767;
   @Parameter(index = 1)
   public int payload = 110 * 1024;
   @Parameter(index = 2)
   public int amqpMinLargeMessageSize = 100 * 1024;
   @Parameter(index = 3)
   public boolean jdbc = false;

   @Parameters(name = "frameSize={0}, payload={1}, amqpMinLargeMessageSize={2}, jdbc={3}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
         {32767, 110 * 1024, 100 * 1024, false},
         {2 * 100 * 1024, 10 * 110 * 1024, 4 * 110 * 1024, false},
         {10 * 1024, 100 * 1024, 20 * 1024, true}
      });
   }

   String testQueueName = "ConnectionFrameSize";

   @Override
   protected void addConfiguration(ActiveMQServer server) {
      // Make the journal file size larger than the frame+message sizes used in the tests,
      // since it is by default for external brokers and it changes the behaviour.
      server.getConfiguration().setJournalFileSize(5 * 1024 * 1024);
      if (jdbc) {
         setDBStoreType(server.getConfiguration());
      }
   }

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("maxFrameSize", frameSize);
      params.put("amqpMinLargeMessageSize", amqpMinLargeMessageSize);
   }

   @Override
   protected void addAdditionalAcceptors(ActiveMQServer server) throws Exception {
      server.getConfiguration().addAcceptorConfiguration("tcp", "tcp://localhost:61616");
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAMQPReceiveCore() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      int nMsgs = 200;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         sendMessages(nMsgs, connection);

         int count = getMessageCount(server.getPostOffice(), testQueueName);
         assertEquals(nMsgs, count);

         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
         receiveJMS(nMsgs, factory);
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAndGetData() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      int nMsgs = 1;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         sendMessages(nMsgs, connection);

         Wait.assertEquals(nMsgs, () -> getMessageCount(server.getPostOffice(), testQueueName), 5000, 100);
         org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(testQueueName);
         serverQueue.forEach(ref -> {
            try {
               AMQPLargeMessage message = (AMQPLargeMessage) ref.getMessage();
               assertFalse(message.hasScheduledDeliveryTime());
               ReadableBuffer dataBuffer = message.getData();
               LargeBodyReader reader = message.getLargeBodyReader();
               try {
                  assertEquals(reader.getSize(), dataBuffer.remaining());
                  reader.open();
                  ByteBuffer buffer = ByteBuffer.allocate(dataBuffer.remaining());
                  reader.readInto(buffer);
                  ByteUtil.equals(buffer.array(), dataBuffer.array());
               } finally {
                  reader.close();
               }
            } catch (AssertionError assertionError) {
               throw assertionError;
            } catch (Throwable e) {
               throw new RuntimeException(e.getMessage(), e);
            }

         });
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAMQPMessageWithComplexAnnotationsReceiveCore() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         connection.connect();

         String annotation = "x-opt-embedded-map";
         Map<String, String> embeddedMap = new LinkedHashMap<>();
         embeddedMap.put("test-key-1", "value-1");
         embeddedMap.put("test-key-2", "value-2");
         embeddedMap.put("test-key-3", "value-3");

         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(testQueueName);
         AmqpMessage message = createAmqpMessage((byte) 'A', payload);

         message.setApplicationProperty("IntProperty", 42);
         message.setDurable(true);
         message.setMessageAnnotation(annotation, embeddedMap);
         sender.send(message);

         session.close();

         Wait.assertEquals(1, () -> getMessageCount(server.getPostOffice(), testQueueName));

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection2 = factory.createConnection()) {

            Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
            connection2.start();
            MessageConsumer consumer = session2.createConsumer(session2.createQueue(testQueueName));

            Message received = consumer.receive(5000);
            assertNotNull(received);
            assertEquals(42, received.getIntProperty("IntProperty"));

            connection2.close();
         }
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAMQPReceiveOpenWire() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      int nMsgs = 200;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         sendMessages(nMsgs, connection);

         int count = getMessageCount(server.getPostOffice(), testQueueName);
         assertEquals(nMsgs, count);

         ConnectionFactory factory = new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616");
         receiveJMS(nMsgs, factory);
      } finally {
         connection.close();
      }
   }

   private void sendMessages(int nMsgs, AmqpConnection connection) throws Exception {
      connection.connect();

      AmqpSession session = connection.createSession();
      AmqpSender sender = session.createSender(testQueueName);

      for (int i = 0; i < nMsgs; ++i) {
         AmqpMessage message = createAmqpMessage((byte) 'A', payload);
         message.setApplicationProperty("i", i);
         message.setDurable(true);
         sender.send(message);
      }

      session.close();
   }

   private void receiveJMS(int nMsgs, ConnectionFactory factory) throws JMSException {
      Connection connection2 = factory.createConnection();
      Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      connection2.start();
      MessageConsumer consumer = session2.createConsumer(session2.createQueue(testQueueName));

      for (int i = 0; i < nMsgs; i++) {
         Message message = consumer.receive(5000);
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("i"));
      }

      connection2.close();
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAMQPReceiveAMQP() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      String testQueueName = "ConnectionFrameSize";
      int nMsgs = 200;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         sendMessages(nMsgs, connection);

         int count = getMessageCount(server.getPostOffice(), testQueueName);
         assertEquals(nMsgs, count);

         AmqpSession session = connection.createSession();
         AmqpReceiver receiver = session.createReceiver(testQueueName);
         receiver.flow(nMsgs);

         for (int i = 0; i < nMsgs; ++i) {
            AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(message, "failed at " + i);
            MessageImpl wrapped = (MessageImpl) message.getWrappedMessage();
            if (wrapped.getBody() instanceof Data data) {
               // converters can change this to AmqValue
               logger.debug("received : message: {}", data.getValue().getLength());
               assertEquals(payload, data.getValue().getLength());
            }
            message.accept();
         }
         session.close();

      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAMQPMessageWithComplexAnnotationsReceiveAMQP() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      String testQueueName = "ConnectionFrameSize";
      int nMsgs = 200;

      AmqpClient client = createAmqpClient();

      Symbol annotation = Symbol.valueOf("x-opt-embedded-map");
      Map<String, String> embeddedMap = new LinkedHashMap<>();
      embeddedMap.put("test-key-1", "value-1");
      embeddedMap.put("test-key-2", "value-2");
      embeddedMap.put("test-key-3", "value-3");

      {
         AmqpConnection connection = addConnection(client.connect());
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(testQueueName);
         AmqpMessage message = createAmqpMessage((byte) 'A', payload);

         message.setApplicationProperty("IntProperty", 42);
         message.setDurable(true);
         message.setMessageAnnotation(annotation.toString(), embeddedMap);
         sender.send(message);
         session.close();
         connection.close();
      }

      Wait.assertEquals(1, () -> getMessageCount(server.getPostOffice(), testQueueName));

      {
         AmqpConnection connection = addConnection(client.connect());
         AmqpSession session = connection.createSession();
         AmqpReceiver receiver = session.createReceiver(testQueueName);
         receiver.flow(nMsgs);

         AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message, "Failed to read message with embedded map in annotations");
         MessageImpl wrapped = (MessageImpl) message.getWrappedMessage();
         if (wrapped.getBody() instanceof Data data) {
            logger.debug("received : message: {}", data.getValue().getLength());
            assertEquals(payload, data.getValue().getLength());
         }

         assertNotNull(message.getWrappedMessage().getMessageAnnotations());
         assertNotNull(message.getWrappedMessage().getMessageAnnotations().getValue());
         assertEquals(embeddedMap, message.getWrappedMessage().getMessageAnnotations().getValue().get(annotation));

         message.accept();
         session.close();
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testHugeString() throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:5672");
      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      Queue queue = session.createQueue(getQueueName());
      MessageProducer producer = session.createProducer(queue);

      StringBuilder unicodeStringBuilder = new StringBuilder();
      for (char c = 1000; c < 11000; c++) {
         unicodeStringBuilder.append(c);
      }

      String unicodeString = unicodeStringBuilder.toString();

      StringBuilder builder = new StringBuilder();
      while (builder.length() < 1024 * 1024) {
         builder.append("hello " + unicodeString);
      }
      producer.send(session.createTextMessage(builder.toString()));
      session.commit();

      connection.start();

      MessageConsumer consumer = session.createConsumer(queue);
      TextMessage message = (TextMessage)consumer.receive(50_000);
      assertNotNull(message);
      session.commit();

      assertEquals(builder.toString(), message.getText());
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAMQPReceiveAMQPViaJMSObjectMessage() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      String testQueueName = "ConnectionFrameSize";
      int nMsgs = 1;

      ConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");

      sendObjectMessages(nMsgs, new JmsConnectionFactory("amqp://localhost:61616"));

      int count = getMessageCount(server.getPostOffice(), testQueueName);
      assertEquals(nMsgs, count);

      receiveJMS(nMsgs, factory);
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAMQPReceiveAMQPViaJMSText() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      String testQueueName = "ConnectionFrameSize";
      int nMsgs = 1;

      ConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");

      sendTextMessages(nMsgs, new JmsConnectionFactory("amqp://localhost:61616"));

      int count = getMessageCount(server.getPostOffice(), testQueueName);
      assertEquals(nMsgs, count);

      receiveJMS(nMsgs, factory);
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAMQPReceiveAMQPViaJMSBytes() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      String testQueueName = "ConnectionFrameSize";
      int nMsgs = 1;

      ConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");

      sendBytesMessages(nMsgs, new JmsConnectionFactory("amqp://localhost:61616"));

      int count = getMessageCount(server.getPostOffice(), testQueueName);
      assertEquals(nMsgs, count);

      receiveJMS(nMsgs, factory);
   }

   private byte[] createLargePayload(int sizeInBytes) {
      byte[] payload = new byte[sizeInBytes];
      for (int i = 0; i < sizeInBytes; i++) {
         payload[i] = (byte) rand.nextInt(256);
      }

      logger.debug("Created buffer with size : {} bytes", sizeInBytes);
      return payload;
   }

   @TestTemplate
   @Timeout(60)
   public void testSendHugeHeader() throws Exception {
      assumeFalse(jdbc); // the checked rule with the property size will not be applied to JDBC, hence we skip the test
      doTestSendHugeHeader(payload);
   }

   @TestTemplate
   @Timeout(60)
   public void testSendLargeMessageWithHugeHeader() throws Exception {
      assumeFalse(jdbc); // the checked rule with the property size will not be applied to JDBC, hence we skip the test
      doTestSendHugeHeader(1024 * 1024);
   }

   public void doTestSendHugeHeader(int expectedSize) throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {

         connection.connect();

         final int strLength = 512 * 1024;
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(testQueueName);

         AmqpMessage message = createAmqpMessage((byte) 'A', expectedSize);
         message.setApplicationProperty("str", " ".repeat(strLength));
         message.setDurable(true);

         try {
            sender.send(message);
            fail();
         } catch (IOException e) {
            assertInstanceOf(JMSException.class, e.getCause());
            assertTrue(e.getMessage().contains("AMQ149005"));
         }

         session.close();
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   public void testLargeHeaderTXLargeBody() throws Exception {
      assumeFalse(jdbc); // the checked rule with the property size will not be applied to JDBC, hence we skip the test
      testLargeHeaderTX(true);
   }

   @TestTemplate
   public void testLargeHeaderTXSmallBody() throws Exception {
      assumeFalse(jdbc); // the checked rule with the property size will not be applied to JDBC, hence we skip the test
      testLargeHeaderTX(false);
   }

   private void testLargeHeaderTX(boolean largeBody) throws Exception {
      String testQueueName = RandomUtil.randomUUIDString();
      server.createQueue(QueueConfiguration.of(testQueueName).setRoutingType(RoutingType.ANYCAST));
      ConnectionFactory cf = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:5672");

      String largeString = RandomUtil.randomAlphaNumericString(1024 * 1024);
      String smallString = "small string";

      String body = largeBody ? largeString : smallString;

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(testQueueName));
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);
         Message message = session.createTextMessage(body);
         message.setStringProperty("test", largeString);
         boolean failed = false;
         try {
            producer.send(message);
            session.commit();
         } catch (Exception expected) {
            failed = true;
         }
         assertTrue(failed);
      }

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(testQueueName));

         Message message = session.createTextMessage(body);
         message.setStringProperty("test", smallString);
         producer.send(message);
         session.commit();

         connection.start();

         MessageConsumer consumer = session.createConsumer(session.createQueue(testQueueName));
         TextMessage recMessage =  (TextMessage) consumer.receive(5000);
         assertEquals(smallString, recMessage.getStringProperty("test"));
         assertEquals(body, recMessage.getText());
         session.commit();

         assertNull(consumer.receiveNoWait());
      }

      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue(testQueueName);
      Wait.assertEquals(0, serverQueue::getMessageCount);

      File largeMessageFolder = server.getConfiguration().getLargeMessagesLocation();
      File[] files = largeMessageFolder.listFiles();
      assertTrue(files == null || files.length == 0, files == null ? "Null Files" : "There are " + files.length + " files in the large message folder");
   }


   @TestTemplate
   @Timeout(60)
   public void testSendSmallerMessages() throws Exception {
      for (int i = 512; i <= (8 * 1024); i += 512) {
         doTestSendLargeMessage(i);
      }
   }

   @TestTemplate
   @Timeout(120)
   public void testSendFixedSizedMessages() throws Exception {
      doTestSendLargeMessage(65536);
      doTestSendLargeMessage(65536 * 2);
      doTestSendLargeMessage(65536 * 4);
   }

   @TestTemplate
   @Timeout(120)
   public void testSend1MBMessage() throws Exception {
      doTestSendLargeMessage(1024 * 1024);
   }

   @Disabled("Useful for performance testing")
   @TestTemplate
   @Timeout(120)
   public void testSend10MBMessage() throws Exception {
      doTestSendLargeMessage(1024 * 1024 * 10);
   }

   @Disabled("Useful for performance testing")
   @TestTemplate
   @Timeout(120)
   public void testSend100MBMessage() throws Exception {
      doTestSendLargeMessage(1024 * 1024 * 100);
   }

   public void doTestSendLargeMessage(int expectedSize) throws Exception {
      logger.debug("doTestSendLargeMessage called with expectedSize {}", expectedSize);
      byte[] payload = createLargePayload(expectedSize);
      assertEquals(expectedSize, payload.length);

      ConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");
      try (Connection connection = factory.createConnection()) {

         long startTime = System.currentTimeMillis();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(name);
         MessageProducer producer = session.createProducer(queue);
         BytesMessage message = session.createBytesMessage();
         message.writeBytes(payload);
         producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         // Set this to non-default to get a Header in the encoded message.
         producer.setPriority(4);
         producer.send(message);
         long endTime = System.currentTimeMillis();

         logger.debug("Returned from send after {} ms", endTime - startTime);
         startTime = System.currentTimeMillis();
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();

         logger.debug("Calling receive");
         Message received = consumer.receive();
         assertNotNull(received);
         assertInstanceOf(BytesMessage.class, received);
         BytesMessage bytesMessage = (BytesMessage) received;
         assertNotNull(bytesMessage);
         endTime = System.currentTimeMillis();

         logger.debug("Returned from receive after {} ms", endTime - startTime);
         byte[] bytesReceived = new byte[expectedSize];
         assertEquals(expectedSize, bytesMessage.readBytes(bytesReceived, expectedSize));
         assertArrayEquals(payload, bytesReceived);
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testReceiveRedeliveredLargeMessagesWithSessionFlowControl() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      int numMsgs = 10;
      int msgSize = 2_000_000;
      int maxFrameSize = frameSize; // Match the brokers outgoing frame size limit to make window sizing easy
      int sessionCapacity = 2_500_000; // Restrict session to 1.x messages in flight at once, make it likely send is partial.

      byte[] payload = createLargePayload(msgSize);
      assertEquals(msgSize, payload.length);

      AmqpClient client = createAmqpClient();

      AmqpConnection connection = client.createConnection();
      connection.setMaxFrameSize(maxFrameSize);
      connection.setSessionIncomingCapacity(sessionCapacity);

      connection.connect();
      addConnection(connection);
      try {
         String testQueueName = getTestName();
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(testQueueName);

         for (int i = 0; i < numMsgs; ++i) {
            AmqpMessage message = new AmqpMessage();
            message.setBytes(payload);

            sender.send(message);
         }

         Wait.assertEquals(numMsgs, () -> getMessageCount(server.getPostOffice(), testQueueName), 5000, 10);

         AmqpReceiver receiver = session.createReceiver(testQueueName);
         receiver.flow(numMsgs);

         List<AmqpMessage> messages = new ArrayList<>();
         for (int i = 0; i < numMsgs; ++i) {
            AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
            assertNotNull(message, "failed at " + i);
            messages.add(message);
         }

         for (int i = 0; i < numMsgs; ++i) {
            AmqpMessage msg = messages.get(i);
            msg.modified(true, false);
         }

         receiver.close();

         AmqpReceiver receiver2 = session.createReceiver(testQueueName);
         receiver2.flow(numMsgs);
         for (int i = 0; i < numMsgs; ++i) {
            AmqpMessage message = receiver2.receive(5, TimeUnit.SECONDS);
            validateMessage(payload, i, message);

            message.accept();
         }

         session.close();

      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testReceiveLargeMessagesMultiplexedOnSameSession() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      int numMsgs = 10;
      int maxFrameSize = frameSize; // Match the brokers outgoing frame size limit to make window sizing easy
      int msgSizeA = frameSize * 4; // Bigger multi-frame messages
      int msgSizeB = maxFrameSize / 2; // Smaller single frame messages
      int sessionCapacity = msgSizeA + maxFrameSize; // Restrict session to 1.X of the larger messages in flight at once, make it likely send is partial.

      byte[] payloadA = createLargePayload(msgSizeA);
      assertEquals(msgSizeA, payloadA.length);
      byte[] payloadB = createLargePayload(msgSizeB);
      assertEquals(msgSizeB, payloadB.length);

      String testQueueNameA = getTestName() + "A";
      String testQueueNameB = getTestName() + "B";

      AmqpClient client = createAmqpClient();

      AmqpConnection connection = client.createConnection();
      connection.setMaxFrameSize(maxFrameSize);
      connection.setSessionIncomingCapacity(sessionCapacity);

      connection.connect();
      addConnection(connection);
      try {
         AmqpSession session = connection.createSession();
         AmqpSender senderA = session.createSender(testQueueNameA);
         AmqpSender senderB = session.createSender(testQueueNameB);

         // Send in the messages
         for (int i = 0; i < numMsgs; ++i) {
            AmqpMessage messageA = new AmqpMessage();
            messageA.setBytes(payloadA);

            senderA.send(messageA);

            AmqpMessage messageB = new AmqpMessage();
            messageB.setBytes(payloadB);

            senderB.send(messageB);
         }

         Wait.assertEquals(numMsgs, () -> getMessageCount(server.getPostOffice(), testQueueNameA), 5000, 10);
         Wait.assertEquals(numMsgs, () -> getMessageCount(server.getPostOffice(), testQueueNameB), 5000, 10);

         AmqpReceiver receiverA = session.createReceiver(testQueueNameA);
         AmqpReceiver receiverB = session.createReceiver(testQueueNameB);

         // Split credit flow to encourage overlapping
         // Flow initial credit for both consumers, in the same TCP frame.
         receiverA.flow(numMsgs / 2, true);
         receiverB.flow(numMsgs / 2);

         // Flow remaining credit for both consumers, in the same TCP frame.
         receiverA.flow(numMsgs / 2, true);
         receiverB.flow(numMsgs / 2);

         List<AmqpMessage> messagesA = new ArrayList<>();
         List<AmqpMessage> messagesB = new ArrayList<>();

         long timeout = 6000;
         long start = System.nanoTime();

         // Validate the messages are all received
         boolean timeRemaining = true;
         while (timeRemaining) {
            if (messagesA.size() < numMsgs) {
               logger.debug("Attempting to receive message for receiver A");
               AmqpMessage messageA = receiverA.receive(20, TimeUnit.MILLISECONDS);
               if (messageA != null) {
                  logger.debug("Got message for receiver A");
                  messagesA.add(messageA);
                  messageA.accept();
               }
            }

            if (messagesB.size() < numMsgs) {
               logger.debug("Attempting to receive message for receiver B");
               AmqpMessage messageB = receiverB.receive(20, TimeUnit.MILLISECONDS);
               if (messageB != null) {
                  logger.debug("Got message for receiver B");
                  messagesB.add(messageB);
                  messageB.accept();
               }
            }

            if (messagesA.size() == numMsgs && messagesB.size() == numMsgs) {
               logger.debug("Received expected messages");
               break;
            }

            timeRemaining = System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(timeout);
         }

         assertTrue(timeRemaining, "Failed to receive all messages in expected time: A=" + messagesA.size() + ", B=" + messagesB.size());

         // Validate there aren't any extras
         assertNull(receiverA.receiveNoWait(), "Unexpected additional message present for A");
         assertNull(receiverB.receiveNoWait(), "Unexpected additional message present for B");

         // Validate the transfers were reconstituted to give the expected delivery payload.
         for (int i = 0; i < numMsgs; ++i) {
            AmqpMessage messageA = messagesA.get(i);
            validateMessage(payloadA, i, messageA);

            AmqpMessage messageB = messagesB.get(i);
            validateMessage(payloadB, i, messageB);
         }

         receiverA.close();
         receiverB.close();

         session.close();
      } finally {
         connection.close();
      }
   }

   private void validateMessage(byte[] expectedPayload, int msgNum, AmqpMessage message) {
      assertNotNull(message, "failed at " + msgNum);

      Section body = message.getWrappedMessage().getBody();
      assertNotNull(body, "No message body for msg " + msgNum);

      assertInstanceOf(Data.class, body, "Unexpected message body type for msg " + body.getClass());
      assertEquals(new Binary(expectedPayload, 0, expectedPayload.length), ((Data) body).getValue(), "Unexpected body content for msg");
   }

   @TestTemplate
   @Timeout(60)
   public void testMessageWithAmqpValueAndEmptyBinaryPreservesBody() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getTestName());

         AmqpMessage message = createAmqpLargeMessageWithNoBody();

         message.getWrappedMessage().setBody(new AmqpValue(new Binary(new byte[0])));

         sender.send(message);
         sender.close();

         AmqpReceiver receiver = session.createReceiver(getTestName());
         receiver.flow(1);

         AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(received, "failed to read large AMQP message");
         MessageImpl wrapped = (MessageImpl) received.getWrappedMessage();

         assertInstanceOf(AmqpValue.class, wrapped.getBody());
         AmqpValue body = (AmqpValue) wrapped.getBody();
         assertInstanceOf(Binary.class, body.getValue());
         Binary payload = (Binary) body.getValue();
         assertEquals(0, payload.getLength());

         received.accept();
         session.close();
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testMessageWithDataAndEmptyBinaryPreservesBody() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getTestName());

         AmqpMessage message = createAmqpLargeMessageWithNoBody();

         message.getWrappedMessage().setBody(new Data(new Binary(new byte[0])));

         sender.send(message);
         sender.close();

         AmqpReceiver receiver = session.createReceiver(getTestName());
         receiver.flow(1);

         AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(received, "failed to read large AMQP message");
         MessageImpl wrapped = (MessageImpl) received.getWrappedMessage();

         assertInstanceOf(Data.class, wrapped.getBody());
         Data body = (Data) wrapped.getBody();
         assertInstanceOf(Binary.class, body.getValue());
         Binary payload = body.getValue();
         assertEquals(0, payload.getLength());

         received.accept();
         session.close();
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   @Timeout(60)
   public void testMessageWithDataAndContentTypeOfTextPreservesBodyType() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getTestName());

         AmqpMessage message = createAmqpLargeMessageWithNoBody();

         String messageText = "This text will be in a Data Section";

         message.getWrappedMessage().setContentType("text/plain");
         message.getWrappedMessage().setBody(new Data(new Binary(messageText.getBytes(StandardCharsets.UTF_8))));
         //message.setApplicationProperty("_AMQ_DUPL_ID", "11");

         sender.send(message);
         sender.close();

         AmqpReceiver receiver = session.createReceiver(getTestName());
         receiver.flow(1);

         AmqpMessage received = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(received, "failed to read large AMQP message");
         MessageImpl wrapped = (MessageImpl) received.getWrappedMessage();

         assertInstanceOf(Data.class, wrapped.getBody());
         Data body = (Data) wrapped.getBody();
         assertInstanceOf(Binary.class, body.getValue());
         Binary payload = body.getValue();
         String reconstitutedString = new String(
            payload.getArray(), payload.getArrayOffset(), payload.getLength(), StandardCharsets.UTF_8);

         assertEquals(messageText, reconstitutedString);

         received.accept();
         session.close();
      } finally {
         connection.close();
      }
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @TestTemplate
   @Timeout(60)
   public void testMessageWithAmqpValueListPreservesBodyType() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getTestName());

         AmqpMessage message = createAmqpLargeMessageWithNoBody();

         List<String> values = new ArrayList<>();
         values.add("1");
         values.add("2");
         values.add("3");

         message.getWrappedMessage().setBody(new AmqpValue(values));

         sender.send(message);
         sender.close();

         AmqpReceiver receiver = session.createReceiver(getTestName());
         receiver.flow(1);

         AmqpMessage received = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(received, "failed to read large AMQP message");
         MessageImpl wrapped = (MessageImpl) received.getWrappedMessage();

         assertInstanceOf(AmqpValue.class, wrapped.getBody());
         AmqpValue body = (AmqpValue) wrapped.getBody();
         assertInstanceOf(List.class, body.getValue());
         List<String> payload = (List) body.getValue();
         assertEquals(3, payload.size());

         received.accept();
         session.close();
      } finally {
         connection.close();
      }
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @TestTemplate
   @Timeout(60)
   public void testMessageWithAmqpSequencePreservesBodyType() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getTestName());

         AmqpMessage message = createAmqpLargeMessageWithNoBody();

         List<String> values = new ArrayList<>();
         values.add("1");
         values.add("2");
         values.add("3");

         message.getWrappedMessage().setBody(new AmqpSequence(values));

         sender.send(message);
         sender.close();

         AmqpReceiver receiver = session.createReceiver(getTestName());
         receiver.flow(1);

         AmqpMessage received = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(received, "failed to read large AMQP message");
         MessageImpl wrapped = (MessageImpl) received.getWrappedMessage();

         assertInstanceOf(AmqpSequence.class, wrapped.getBody());
         AmqpSequence body = (AmqpSequence) wrapped.getBody();
         assertInstanceOf(List.class, body.getValue());
         List<String> payload = (List) body.getValue();
         assertEquals(3, payload.size());

         received.accept();
         session.close();
      } finally {
         connection.close();
      }
   }


   @TestTemplate
   public void testDeleteUnreferencedMessage() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getTestName());
         AmqpMessage message = createAmqpMessage((byte)'A', payload);
         message.setDurable(true);
         sender.send(message);
         sender.close();
      } finally {
         connection.close();
      }

      final org.apache.activemq.artemis.core.server.Queue queue = server.locateQueue(getTestName());
      queue.forEach(ref -> {
         if (ref.getMessage().isLargeMessage()) {
            try {
               // simulating an ACK but the server crashed before the delete of the record, and the large message file
               server.getStorageManager().storeAcknowledge(queue.getID(), ref.getMessageID());
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }
         }
      });

      server.stop();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler())  {
         server.start();
         assertTrue(loggerHandler.findText("AMQ221019"));
      }

      validateNoFilesOnLargeDir();
      runAfter(server::stop);
   }

   @TestTemplate
   public void testSimpleLargeMessageRestart() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getTestName());
         AmqpMessage message = createAmqpMessage((byte)'A', payload);
         message.setDurable(true);
         sender.send(message);
         sender.close();
      } finally {
         connection.close();
      }

      server.stop();

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.start();

         // These two should not happen as the consumer will receive them
         assertFalse(loggerHandler.findText("AMQ221019")); // unferenced record
         assertFalse(loggerHandler.findText("AMQ221018")); // unferenced large message
      }

      connection = addConnection(client.connect());
      try {
         AmqpSession session = connection.createSession();
         AmqpReceiver receiver = session.createReceiver(getTestName());
         receiver.flow(1);
         AmqpMessage message = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(message);
         message.accept();
         receiver.close();
         session.close();
      } finally {
         connection.close();
      }

      validateNoFilesOnLargeDir();
      runAfter(server::stop);
   }


   @Test
   public void testInterruptStreaming() throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");

      final int MESSAGE_COUNT = 20;
      final int MESSAGE_SIZE = 1500000; // 1.5 MB
      final int THREADS = 10;
      byte[] payload = createLargePayload(MESSAGE_SIZE);

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {

         final CyclicBarrier consumersReady = new CyclicBarrier(THREADS + 1);
         final CountDownLatch consumersDone = new CountDownLatch(THREADS);
         final CountDownLatch producersDone = new CountDownLatch(THREADS);
         final AtomicInteger sentCount = new AtomicInteger(0);
         final AtomicInteger countError = new AtomicInteger(0);

         ExecutorService executorService = Executors.newFixedThreadPool(THREADS * 2);
         runAfter(executorService::shutdownNow);

         for (int i = 0; i < THREADS; i++) {
            final int threadId = i;
            executorService.execute(() -> {
               try {
                  consumeForInterruptedStreaming(factory, threadId, consumersReady, MESSAGE_COUNT, payload);
               } catch (Throwable error) {
                  countError.incrementAndGet();
                  logger.warn(error.getMessage(), error);
               } finally {
                  consumersDone.countDown();
               }
            });
         }

         consumersReady.await(30, TimeUnit.SECONDS);

         for (int i = 0; i < THREADS; i++) {
            final int threadId = i;
            executorService.execute(() -> {
               try {
                  produceForInterruptedStreaming(factory, sentCount, MESSAGE_COUNT, payload);
               } catch (Throwable e) {
                  countError.incrementAndGet();
                  logger.warn(e.getMessage(), e);
               } finally {
                  producersDone.countDown();
               }
            });
         }

         assertTrue(consumersDone.await(5, TimeUnit.MINUTES));
         assertTrue(producersDone.await(5, TimeUnit.MINUTES));

         assertEquals(0, countError.get(), "There are exceptions on the producers or consumers");

         assertFalse(loggerHandler.findTrace("IllegalArgumentException"), "Server log contains IllegalArgumentException");
      }
   }

   private static void produceForInterruptedStreaming(ConnectionFactory factory, AtomicInteger sentCount, int messageCount, byte[] payload) throws Throwable {
      Connection connection = null;
      try {
         connection = factory.createConnection();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic("testTopic");
         MessageProducer producer = session.createProducer(topic);


         for (int j = 0; j < messageCount; j++) {
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(payload);
            producer.send(message);
            sentCount.incrementAndGet();

            if ((j + 1) % 10 == 0) {
               session.commit();
            }
         }
         session.commit();
      } finally {
         if (connection != null) {
            try {
               connection.close();
            } catch (Exception e) {
               logger.error("Error closing producer connection", e);
            }
         }
      }
   }


   private static void consumeForInterruptedStreaming(ConnectionFactory factory,
                               int threadId,
                               CyclicBarrier startFlag, int messageCount, byte[] expectedPayload) throws Exception {

      Connection connection = null;
      try {
         connection = factory.createConnection();
         connection.setClientID("consumer-" + threadId);

         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = session.createTopic("testTopic");

         // Create durable subscriber
         MessageConsumer consumer = session.createDurableSubscriber(topic, "sub-" + threadId);

         startFlag.await(10, TimeUnit.SECONDS);
         connection.start();

         for (int i = 0; i < messageCount; i++) {
            BytesMessage msg = (BytesMessage) consumer.receive(TimeUnit.SECONDS.toMillis(30));
            assertNotNull(msg);

            int bodySize = (int)msg.getBodyLength();

            assertEquals(bodySize, expectedPayload.length);

            byte[] receivedPayLoad =  new byte[bodySize];
            msg.readBytes(receivedPayLoad);

            assertArrayEquals(expectedPayload, receivedPayLoad);

            if (i % 10 == 0) {
               session.commit();
            }
         }
         session.commit();

      } finally {
         if (connection != null) {
            try {
               connection.close();
            } catch (Exception e) {
               logger.error("Error closing consumer connection", e);
            }
         }
      }
   }



   private void sendObjectMessages(int nMsgs, ConnectionFactory factory) throws Exception {
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue(testQueueName);
         MessageProducer producer = session.createProducer(queue);
         ObjectMessage msg = session.createObjectMessage();
         msg.setObject("A".repeat(payload));
         for (int i = 0; i < nMsgs; ++i) {
            msg.setIntProperty("i", (Integer) i);
            producer.send(msg);
         }
      }
   }

   private void sendTextMessages(int nMsgs, ConnectionFactory factory) throws Exception {
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue(testQueueName);
         MessageProducer producer = session.createProducer(queue);
         TextMessage msg = session.createTextMessage();
         msg.setText("A".repeat(payload));
         for (int i = 0; i < nMsgs; ++i) {
            msg.setIntProperty("i", (Integer) i);
            producer.send(msg);
         }
      }
   }

   private void sendBytesMessages(int nMsgs, ConnectionFactory factory) throws Exception {
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue(testQueueName);
         MessageProducer producer = session.createProducer(queue);
         BytesMessage msg = session.createBytesMessage();
         msg.writeBytes("A".repeat(payload).getBytes(StandardCharsets.UTF_8));
         for (int i = 0; i < nMsgs; ++i) {
            msg.setIntProperty("i", (Integer) i);
            producer.send(msg);
         }
      }
   }

   private AmqpMessage createAmqpMessage(byte value, int payloadSize) {
      AmqpMessage message = new AmqpMessage();
      byte[] payload = new byte[payloadSize];
      for (int i = 0; i < payload.length; i++) {
         payload[i] = value;
      }
      message.setBytes(payload);
      return message;
   }

   private AmqpMessage createAmqpLargeMessageWithNoBody() {
      AmqpMessage message = new AmqpMessage();

      byte[] payload = new byte[512 * 1024];
      for (int i = 0; i < payload.length; i++) {
         payload[i] = (byte) 65;
      }

      message.setMessageAnnotation("x-opt-big-blob", new String(payload, StandardCharsets.UTF_8));

      return message;
   }
}
