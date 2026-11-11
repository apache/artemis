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
package org.apache.activemq.artemis.tests.integration.stomp;

import javax.jms.BytesMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.management.impl.view.ProducerField;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompProtocolManager;
import org.apache.activemq.artemis.core.protocol.stomp.StompProtocolManagerFactory;
import org.apache.activemq.artemis.core.protocol.stomp.StompSession;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.tests.integration.mqtt.FuseMQTTClientProvider;
import org.apache.activemq.artemis.tests.integration.mqtt.MQTTClientProvider;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.utils.collections.IterableStream.iterableOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class StompTest extends StompTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected StompClientConnection conn;

   public StompTest() {
      super("tcp+v10.stomp");
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      ActiveMQServer server = super.createServer();
      server.getConfiguration().setAddressQueueScanPeriod(100);
      return server;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      conn = StompClientConnectionFactory.createClientConnection(uri);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         boolean connected = conn != null && conn.isConnected();
         logger.debug("Connection 1.0 connected: {}", connected);
         if (connected) {
            try {
               conn.disconnect();
            } catch (Exception e) {
               // ignore
            }
         }
      } finally {
         super.tearDown();
         conn.closeTransport();
      }
   }

   @Test
   public void testConnectionTTL() throws Exception {
      int port = 61614;

      URI uri = createStompClientUri(scheme, hostname, port);

      server.getRemotingService().createAcceptor("test", "tcp://127.0.0.1:" + port + "?connectionTtl=1000").start();
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect("brianm", "wombats");

      Thread.sleep(5000);

      ClientStompFrame frame = conn.receiveFrame();

      assertEquals(Stomp.Responses.ERROR, frame.getCommand());

      assertFalse(conn.isConnected());
   }

   @Test
   public void testSendManyMessages() throws Exception {
      conn.connect(defUser, defPass);

      MessageConsumer consumer = session.createConsumer(queue);

      int count = 1000;
      final CountDownLatch latch = new CountDownLatch(count);
      consumer.setMessageListener(arg0 -> latch.countDown());

      for (int i = 1; i <= count; i++) {
         send(conn, getQueuePrefix() + getQueueName(), null, "Hello World!");
      }

      assertTrue(latch.await(60, TimeUnit.SECONDS));
   }

   @Test
   public void testProducerMetrics() throws Exception {
      conn.connect(defUser, defPass);

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World!", true);

      String filterString = createJsonFilter("", "", "");
      String producersAsJsonString = server.getActiveMQServerControl().listProducers(filterString, 1, 50);
      JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
      JsonArray array = (JsonArray) producersAsJsonObject.get("data");

      assertEquals(1, array.size(), "number of producers returned from query");

      JsonObject producer = array.getJsonObject(0);
      assertEquals(1, producer.getInt(ProducerField.MESSAGE_SENT.getName()), ProducerField.MESSAGE_SENT.getName());

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World!", true);

      producersAsJsonString = server.getActiveMQServerControl().listProducers(filterString, 1, 50);
      producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
      array = (JsonArray) producersAsJsonObject.get("data");

      assertEquals(1, array.size(), "number of producers returned from query");

      producer = array.getJsonObject(0);
      assertEquals(2, producer.getInt(ProducerField.MESSAGE_SENT.getName()), ProducerField.MESSAGE_SENT.getName());

      conn.closeTransport();

      Wait.assertEquals(0, () -> ((JsonArray) JsonUtil.readJsonObject(server.getActiveMQServerControl().listProducers(filterString, 1, 50)).get("data")).size());

      producersAsJsonString = server.getActiveMQServerControl().listProducers(filterString, 1, 50);
      producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
      array = (JsonArray) producersAsJsonObject.get("data");

      assertEquals(0, array.size(), "number of producers returned from query");
   }

   @Test
   public void testSendOverDiskFull() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         MessageConsumer consumer = session.createConsumer(queue);

         conn.connect(defUser, defPass);
         int count = 1000;
         final CountDownLatch latch = new CountDownLatch(count);
         consumer.setMessageListener(arg0 -> latch.countDown());

         ((ActiveMQServerImpl) server).getMonitor().setMaxUsage(0).tick();

         // Connection should be closed by broker when disk is full and attempt to send
         Exception e = null;
         try {
            for (int i = 1; i <= count; i++) {
               send(conn, getQueuePrefix() + getQueueName(), null, "Hello World!");
            }
         } catch (Exception se) {
            e = se;
         }
         assertNotNull(e);
         // It should encounter the exception on logs
         loggerHandler.findText("AMQ119119");
      }
   }

   @Test
   public void testConnect() throws Exception {
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT).addHeader(Stomp.Headers.Connect.LOGIN, defUser).addHeader(Stomp.Headers.Connect.PASSCODE, defPass).addHeader(Stomp.Headers.Connect.REQUEST_ID, "1");
      ClientStompFrame response = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, response.getCommand());
      assertEquals("1", response.getHeader(Stomp.Headers.Connected.RESPONSE_ID));
   }

   @Test
   public void testDisconnectAndError() throws Exception {
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.CONNECT).addHeader(Stomp.Headers.Connect.LOGIN, defUser).addHeader(Stomp.Headers.Connect.PASSCODE, defPass).addHeader(Stomp.Headers.Connect.REQUEST_ID, "1");
      ClientStompFrame response = conn.sendFrame(frame);

      assertEquals(Stomp.Responses.CONNECTED, response.getCommand());
      assertEquals("1", response.getHeader(Stomp.Headers.Connected.RESPONSE_ID));

      conn.disconnect();

      // sending a message will result in an error
      try {
         send(conn, getQueuePrefix() + getQueueName(), null, "Hello World!");
         fail("Should have thrown an exception since connection is disconnected");
      } catch (Exception e) {
         // ignore
      }
   }

   @Test
   public void testSendMessage() throws Exception {

      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World");

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      // Assert default priority 4 is used when priority header is not set
      assertEquals(4, message.getJMSPriority(), "getJMSPriority");

      // Make sure that the timestamp is valid - should
      // be very close to the current time.
      long tnow = System.currentTimeMillis();
      long tmsg = message.getJMSTimestamp();
      assertTrue(Math.abs(tnow - tmsg) < 1000);
   }

   @Test
   public void testNullCorrelationIDandTypeProperties() throws Exception {

      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World");

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertFalse(((ActiveMQMessage)message).getCoreMessage().getPropertyNames().contains(MessageUtil.CORRELATIONID_HEADER_NAME));
      assertFalse(((ActiveMQMessage)message).getCoreMessage().getPropertyNames().contains(MessageUtil.TYPE_HEADER_NAME));
   }

   @Test
   public void sendSTOMPReceiveMQTT() throws Exception {
      // Set up MQTT Subscription
      MQTTClientProvider clientProvider = new FuseMQTTClientProvider();
      clientProvider.connect("tcp://" + hostname + ":" + port);
      clientProvider.subscribe(getTopicPrefix() + getTopicName(), 0);

      String stompPayload = "This is a test message";

      // Set up STOMP connection and send STOMP Message
      conn.connect(defUser, defPass);
      send(conn, getTopicPrefix() + getTopicName(), null, stompPayload);

      // Receive MQTT Message
      byte[] mqttPayload = clientProvider.receive(10000);
      clientProvider.disconnect();

      assertEquals(stompPayload, new String(mqttPayload, StandardCharsets.UTF_8));
      clientProvider.disconnect();
   }

   @Test
   public void sendMQTTReceiveSTOMP() throws Exception {
      String payload = "This is a test message";

      // Set up STOMP subscription
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      // Send MQTT Message
      MQTTClientProvider clientProvider = new FuseMQTTClientProvider();
      clientProvider.connect("tcp://" + hostname + ":" + port);
      clientProvider.publish(getQueuePrefix() + getQueueName(), payload.getBytes(), 0);
      clientProvider.disconnect();

      // Receive STOMP Message
      ClientStompFrame frame = conn.receiveFrame();
      assertTrue(frame.getBody().contains(payload));

   }

   @Test
   public void testReceiveCompressedMessage() throws Exception {
      String DATA = RandomUtil.randomAlphaNumericString(1024 * 1024);
      String queue = getQueuePrefix() + getQueueName();

      server.createQueue(QueueConfiguration.of(queue).setDurable(true).setRoutingType(RoutingType.ANYCAST), true);

      try (ServerLocator locator = ActiveMQClient.createServerLocator("tcp://" + hostname + ":" + port + "?compressLargeMessage=true");
           ClientSessionFactory sf = locator.createSessionFactory();
           ClientSession session = sf.createSession(false, true, true);
           ClientProducer producer = session.createProducer()) {

         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeNullableSimpleString(SimpleString.of(DATA));
         producer.send(queue, message);
      }

      conn.connect(defUser, defPass);

      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(DATA, frame.getBody());

      conn.disconnect();
   }

   @Test
   public void sendEmptyCoreMessage() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      // send core JMS message
      MessageProducer mp = session.createProducer(session.createQueue(getQueuePrefix() + getQueueName()));
      Message m = session.createMessage();
      mp.send(m);

      // Receive STOMP Message
      ClientStompFrame frame = conn.receiveFrame();
      assertNotNull(frame);
      assertNull(frame.getBody());
   }

   public void sendMessageToNonExistentQueue(String queuePrefix,
                                             String queue,
                                             RoutingType routingType) throws Exception {
      conn.connect(defUser, defPass);
      send(conn, queuePrefix + queue, null, "Hello World", true, routingType);

      MessageConsumer consumer = session.createConsumer(ActiveMQJMSClient.createQueue(queue));
      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      // Assert default priority 4 is used when priority header is not set
      assertEquals(4, message.getJMSPriority(), "getJMSPriority");

      // Make sure that the timestamp is valid - should
      // be very close to the current time.
      long tnow = System.currentTimeMillis();
      long tmsg = message.getJMSTimestamp();
      assertTrue(Math.abs(tnow - tmsg) < 1500);

      // closing the consumer here should trigger auto-deletion
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of(queue)));
      consumer.close();
      Wait.assertTrue(() -> server.getPostOffice().getBinding(SimpleString.of(queue)) == null);
   }

   @Test
   public void testSendMessageToNonExistentQueue() throws Exception {
      sendMessageToNonExistentQueue(getQueuePrefix(), RandomUtil.randomUUIDString(), RoutingType.ANYCAST);
   }

   @Test
   public void testSendMessageToNonExistentQueueUsingExplicitDefaultRouting() throws Exception {
      String nonExistentQueue = RandomUtil.randomUUIDString();
      server.getAddressSettingsRepository().addMatch(nonExistentQueue, new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST).setDefaultQueueRoutingType(RoutingType.ANYCAST));
      sendMessageToNonExistentQueue(getQueuePrefix(), nonExistentQueue, null);
   }

   public void sendMessageToNonExistentTopic(String topicPrefix,
                                             String topic,
                                             RoutingType routingType) throws Exception {
      conn.connect(defUser, defPass);

      // first send a message to ensure that sending to a non-existent topic won't throw an error
      send(conn, topicPrefix + topic, null, "Hello World", true, routingType);

      // create a subscription on the topic and send/receive another message
      MessageConsumer consumer = session.createConsumer(ActiveMQJMSClient.createTopic(topic));
      send(conn, topicPrefix + topic, null, "Hello World", true, routingType);
      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      // Assert default priority 4 is used when priority header is not set
      assertEquals(4, message.getJMSPriority(), "getJMSPriority");

      // Make sure that the timestamp is valid - should
      // be very close to the current time.
      long tnow = System.currentTimeMillis();
      long tmsg = message.getJMSTimestamp();
      assertTrue(Math.abs(tnow - tmsg) < 1500);

      assertNotNull(server.getAddressInfo(SimpleString.of(topic)));

      // closing the consumer here should trigger auto-deletion of the subscription queue and address
      consumer.close();
      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of(topic)) == null);
   }

   @Test
   public void testSendMessageToNonExistentTopic() throws Exception {
      sendMessageToNonExistentTopic(getTopicPrefix(), RandomUtil.randomUUIDString(), RoutingType.MULTICAST);
   }

   @Test
   public void testSendMessageToNonExistentTopicUsingExplicitDefaultRouting() throws Exception {
      String nonExistentTopic = RandomUtil.randomUUIDString();
      server.getAddressSettingsRepository().addMatch(nonExistentTopic, new AddressSettings().setDefaultAddressRoutingType(RoutingType.MULTICAST).setDefaultQueueRoutingType(RoutingType.MULTICAST));
      sendMessageToNonExistentTopic(getTopicPrefix(), nonExistentTopic, null);
   }

   @Test
   public void testSendMessageToNonExistentTopicUsingImplicitDefaultRouting() throws Exception {
      sendMessageToNonExistentTopic(getTopicPrefix(), RandomUtil.randomUUIDString(), null);
   }

   /*
    * Some STOMP clients erroneously put a new line \n *after* the terminating NUL char at the end of the frame
    * This means next frame read might have a \n a the beginning.
    * This is contrary to STOMP spec but we deal with it so we can work nicely with crappy STOMP clients
    */
   @Test
   public void testSendMessageWithLeadingNewLine() throws Exception {
      conn.connect(defUser, defPass);
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).setBody("Hello World");
      conn.sendWickedFrame(frame);

      MessageConsumer consumer = session.createConsumer(queue);
      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World");
      message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());

      // Make sure that the timestamp is valid - should
      // be very close to the current time.
      long tnow = System.currentTimeMillis();
      long tmsg = message.getJMSTimestamp();
      assertTrue(Math.abs(tnow - tmsg) < 1000);
   }

   @Test
   public void testSendMessageWithReceipt() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World", true);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());

      // Make sure that the timestamp is valid - should
      // be very close to the current time.
      long tnow = System.currentTimeMillis();
      long tmsg = message.getJMSTimestamp();
      assertTrue(Math.abs(tnow - tmsg) < 1000);
   }

   @Test
   public void testSendMessageWithContentLength() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);
      conn.connect(defUser, defPass);

      byte[] data = new byte[]{1, 0, 0, 4};
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      baos.write(data);
      baos.flush();

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader(Stomp.Headers.CONTENT_LENGTH, Integer.toString(data.length)).setBody(new String(baos.toByteArray()));
      conn.sendFrame(frame);

      BytesMessage message = (BytesMessage) consumer.receive(10000);
      assertNotNull(message);
      assertEquals(data.length, message.getBodyLength());
      assertEquals(data[0], message.readByte());
      assertEquals(data[1], message.readByte());
      assertEquals(data[2], message.readByte());
      assertEquals(data[3], message.readByte());
   }

   @Test
   public void testJMSXGroupIdCanBeSet() throws Exception {
      final String jmsxGroupID = "JMSXGroupID";
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader("JMSXGroupID", jmsxGroupID).setBody("Hello World");
      conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      // differ from StompConnect
      assertEquals(jmsxGroupID, message.getStringProperty("JMSXGroupID"));
   }

   @Test
   public void testSendMessageWithCustomHeadersAndSelector() throws Exception {

      MessageConsumer consumer = session.createConsumer(queue, "foo = 'abc'");

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader("foo", "abc").addHeader("bar", "123").setBody("Hello World");
      conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals("123", message.getStringProperty("bar"), "bar");
   }

   @Test
   public void testSendMessageWithCustomHeadersAndHyphenatedSelector() throws Exception {

      MessageConsumer consumer = session.createConsumer(queue, "hyphenated_props:b-ar = '123'");

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader("foo", "abc").addHeader("b-ar", "123").setBody("Hello World");
      conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals("123", message.getStringProperty("b-ar"), "b-ar");
   }

   @Test
   public void testSendMessageWithStandardHeaders() throws Exception {

      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader("foo", "abc").addHeader("bar", "123").addHeader("correlation-id", "c123").addHeader("persistent", "true").addHeader("type", "t345").addHeader("JMSXGroupID", "abc").addHeader("priority", "3").setBody("Hello World");
      conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("c123", message.getJMSCorrelationID(), "JMSCorrelationID");
      assertEquals("t345", message.getJMSType(), "getJMSType");
      assertEquals(3, message.getJMSPriority(), "getJMSPriority");
      assertEquals(javax.jms.DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals("123", message.getStringProperty("bar"), "bar");

      assertEquals("abc", message.getStringProperty("JMSXGroupID"), "JMSXGroupID");
      // FIXME do we support it?
      // Assert.assertEquals("GroupID", "abc", amqMessage.getGroupID());
   }

   @Test
   public void testSendMessageWithLongHeaders() throws Exception {
      final int headerSize = 1024;
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      String longHeader = "a".repeat(headerSize);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND)
         .addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName())
         .addHeader("foo", "abc")
         .addHeader("bar", "123")
         .addHeader("correlation-id", "c123")
         .addHeader("persistent", "true")
         .addHeader("type", "t345")
         .addHeader("JMSXGroupID", "abc")
         .addHeader("priority", "3")
         .addHeader("longHeader", longHeader)
         .setBody("Hello World");
      conn.sendFrame(frame);

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("c123", message.getJMSCorrelationID(), "JMSCorrelationID");
      assertEquals("t345", message.getJMSType(), "getJMSType");
      assertEquals(3, message.getJMSPriority(), "getJMSPriority");
      assertEquals(javax.jms.DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals(headerSize, message.getStringProperty("longHeader").length(), "longHeader");

      assertEquals("abc", message.getStringProperty("JMSXGroupID"), "JMSXGroupID");
   }

   @Test
   public void testSendMessageWithDelay() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader("foo", "abc").addHeader("bar", "123").addHeader("correlation-id", "c123").addHeader("persistent", "true").addHeader("type", "t345").addHeader("JMSXGroupID", "abc").addHeader("priority", "3").addHeader("AMQ_SCHEDULED_DELAY", "2000").setBody("Hello World");
      conn.sendFrame(frame);

      assertNull(consumer.receive(100), "Should not receive message yet");

      TextMessage message = (TextMessage) consumer.receive(4000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("c123", message.getJMSCorrelationID(), "JMSCorrelationID");
      assertEquals("t345", message.getJMSType(), "getJMSType");
      assertEquals(3, message.getJMSPriority(), "getJMSPriority");
      assertEquals(javax.jms.DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals("abc", message.getStringProperty("JMSXGroupID"), "JMSXGroupID");
   }

   @Test
   public void testSendMessageWithDeliveryTime() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader("foo", "abc").addHeader("bar", "123").addHeader("correlation-id", "c123").addHeader("persistent", "true").addHeader("type", "t345").addHeader("JMSXGroupID", "abc").addHeader("priority", "3").addHeader("AMQ_SCHEDULED_TIME", Long.toString(System.currentTimeMillis() + 2000)).setBody("Hello World");
      conn.sendFrame(frame);

      assertNull(consumer.receive(100), "Should not receive message yet");

      TextMessage message = (TextMessage) consumer.receive(4000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      assertEquals("c123", message.getJMSCorrelationID(), "JMSCorrelationID");
      assertEquals("t345", message.getJMSType(), "getJMSType");
      assertEquals(3, message.getJMSPriority(), "getJMSPriority");
      assertEquals(javax.jms.DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
      assertEquals("abc", message.getStringProperty("foo"), "foo");
      assertEquals("abc", message.getStringProperty("JMSXGroupID"), "JMSXGroupID");
   }

   @Test
   public void testSendMessageWithDelayWithBadValue() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader("AMQ_SCHEDULED_DELAY", "foo").setBody("Hello World");
      conn.sendFrame(frame);

      assertNull(consumer.receive(100), "Should not receive message yet");

      ClientStompFrame error = conn.receiveFrame();

      assertNotNull(error);
      assertEquals("ERROR", error.getCommand());
   }

   @Test
   public void testSendMessageWithDeliveryTimeWithBadValue() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);

      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader("AMQ_SCHEDULED_TIME", "foo").setBody("Hello World");
      conn.sendFrame(frame);

      assertNull(consumer.receive(100), "Should not receive message yet");

      ClientStompFrame error = conn.receiveFrame();

      assertNotNull(error);
      assertEquals("ERROR", error.getCommand());
   }

   @Test
   public void testSubscribeWithAutoAck() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(getName(), frame.getBody());

      Pattern cl = Pattern.compile(Stomp.Headers.CONTENT_LENGTH + ":\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
      Matcher cl_matcher = cl.matcher(frame.toString());
      assertFalse(cl_matcher.find());

      conn.disconnect();

      // message should not be received as it was auto-acked
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(100);
      assertNull(message);

   }

   @Test
   public void testNullPropertyValue() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      sendJmsMessage(getName(), "foo", null);

      ClientStompFrame frame = conn.receiveFrame(2000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(getName(), frame.getBody());
      assertEquals("", frame.getHeader("foo"));

      conn.disconnect();
   }

   @Test
   public void testTransactedSessionLeak() throws Exception {
      for (int i = 0; i < 10; i++) {
         conn = StompClientConnectionFactory.createClientConnection(uri);
         conn.connect(defUser, defPass);


         for (int s = 0; s < 10; s++) {
            String txId = "tx" + i + "_" + s;
            beginTransaction(conn, txId);
            send(conn, getQueuePrefix() + getQueueName(), null, "Hello World", true, null, txId);
            commitTransaction(conn, txId, true);
         }

         Wait.assertEquals(13, () -> server.getSessions().size(), 1000, 100);
         conn.disconnect();
      }

      if (connection != null) {
         connection.close();
      }

      Wait.assertEquals(0, () -> server.getSessions().size(), 1000, 100);

      StompProtocolManager stompProtocolManager = getStompProtocolManager();

      assertEquals(0, stompProtocolManager.getTransactedSessions().size());
   }

   @Test
   public void testIngressTimestamp() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setEnableIngressTimestamp(true));
      conn.connect(defUser, defPass);

      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);
      long beforeSend = System.currentTimeMillis();
      sendJmsMessage(getName());
      long afterSend = System.currentTimeMillis();

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      String ingressTimestampHeader = frame.getHeader(Stomp.Headers.Message.INGRESS_TIMESTAMP);
      assertNotNull(ingressTimestampHeader);
      long ingressTimestamp = Long.parseLong(ingressTimestampHeader);
      assertTrue(ingressTimestamp >= beforeSend && ingressTimestamp <= afterSend, "Ingress timstamp " + ingressTimestamp + " should be >= " + beforeSend + " and <= " + afterSend);

      conn.disconnect();
   }

   @Test
   public void testAnycastDestinationTypeMessageProperty() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      send(conn, getQueuePrefix() + getQueueName(), null, getName(), true, RoutingType.ANYCAST);

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(RoutingType.ANYCAST.toString(), frame.getHeader(Stomp.Headers.Send.DESTINATION_TYPE));
      assertNull(frame.getHeader(org.apache.activemq.artemis.api.core.Message.HDR_ROUTING_TYPE.toString()));
      assertEquals(getName(), frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testMulticastDestinationTypeMessageProperty() throws Exception {
      conn.connect(defUser, defPass);

      subscribeTopic(conn, null, null, null);

      send(conn, getTopicPrefix() + getTopicName(), null, getName(), true, RoutingType.MULTICAST);

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(RoutingType.MULTICAST.toString(), frame.getHeader(Stomp.Headers.Send.DESTINATION_TYPE));
      assertNull(frame.getHeader(org.apache.activemq.artemis.api.core.Message.HDR_ROUTING_TYPE.toString()));
      assertEquals(getName(), frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testSubscriptionQueueCreatedWhenAutoCreateDisabled() throws Exception {
      SimpleString topic = SimpleString.of(getTopicPrefix() + getTopicName());
      server.getAddressSettingsRepository().getMatch(topic.toString()).setAutoCreateQueues(false);
      conn.connect(defUser, defPass);

      assertEquals(0, server.getPostOffice().getBindingsForAddress(topic).size());
      subscribeTopic(conn, null, null, null, true);
      Wait.assertEquals(1, () -> server.getPostOffice().getBindingsForAddress(topic).size(), 2000, 100);

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithAutoAckAndBytesMessage() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      byte[] payload = new byte[]{1, 2, 3, 4, 5};

      sendJmsMessage(payload, queue);

      ClientStompFrame frame = conn.receiveFrame(10000);

      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      Pattern cl = Pattern.compile(Stomp.Headers.CONTENT_LENGTH + ":\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
      Matcher cl_matcher = cl.matcher(frame.toString());
      assertTrue(cl_matcher.find());
      assertEquals("5", cl_matcher.group(1));

      assertFalse(Pattern.compile("type:\\s*null", Pattern.CASE_INSENSITIVE).matcher(frame.toString()).find());
      assertTrue(frame.getBody().indexOf(new String(payload)) > -1);

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithMessageSentWithProperties() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      MessageProducer producer = session.createProducer(queue);
      BytesMessage message = session.createBytesMessage();
      message.setStringProperty("S", "value");
      message.setBooleanProperty("n", false);
      message.setByteProperty("byte", (byte) 9);
      message.setDoubleProperty("d", 2.0);
      message.setFloatProperty("f", (float) 6.0);
      message.setIntProperty("i", 10);
      message.setLongProperty("l", 121);
      message.setShortProperty("s", (short) 12);
      message.writeBytes("Hello World".getBytes(StandardCharsets.UTF_8));
      producer.send(message);

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertNotNull(frame);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("value", frame.getHeader("S"));
      assertEquals("false", frame.getHeader("n"));
      assertEquals("9", frame.getHeader("byte"));
      assertEquals("2.0", frame.getHeader("d"));
      assertEquals("6.0", frame.getHeader("f"));
      assertEquals("10", frame.getHeader("i"));
      assertEquals("121", frame.getHeader("l"));
      assertEquals("12", frame.getHeader("s"));
      assertEquals("Hello World", frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithID() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, "mysubid", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Message.DESTINATION));
      assertEquals("mysubid", frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));
      assertEquals(getName(), frame.getBody());

      conn.disconnect();
   }

   //
   @Test
   public void testBodyWithUTF8() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      String text = "A" + "\u00ea" + "\u00f1" + "\u00fc" + "C";
      logger.debug(text);
      sendJmsMessage(text);

      ClientStompFrame frame = conn.receiveFrame(10000);
      logger.debug("{}", frame);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Message.DESTINATION));
      assertEquals(text, frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testMessagesAreInOrder() throws Exception {
      int ctr = 10;
      String[] data = new String[ctr];

      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      for (int i = 0; i < ctr; ++i) {
         data[i] = getName() + i;
         sendJmsMessage(data[i]);
      }

      for (int i = 0; i < ctr; ++i) {
         ClientStompFrame frame = conn.receiveFrame(1000);
         assertEquals(data[i], frame.getBody(), "Message not in order");
      }

      // sleep a while before publishing another set of messages
      Thread.sleep(200);

      for (int i = 0; i < ctr; ++i) {
         data[i] = getName() + Stomp.Headers.SEPARATOR + "second:" + i;
         sendJmsMessage(data[i]);
      }

      for (int i = 0; i < ctr; ++i) {
         ClientStompFrame frame = conn.receiveFrame(1000);
         assertEquals(data[i], frame.getBody(), "Message not in order");
      }

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithAutoAckAndSelector() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO, null, "foo = 'zzz'");

      sendJmsMessage("Ignored message", "foo", "1234");
      sendJmsMessage("Real message", "foo", "zzz");

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Real message", frame.getBody(), "Should have received the real message but got: " + frame);

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithAutoAckAndHyphenatedSelector() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO, null, "hyphenated_props:foo-bar = 'zzz'");

      ServerLocator serverLocator = addServerLocator(ActiveMQClient.createServerLocator("vm://0"));
      ClientSessionFactory clientSessionFactory = serverLocator.createSessionFactory();
      ClientSession clientSession = clientSessionFactory.createSession(true, true);
      ClientProducer producer = clientSession.createProducer(getQueuePrefix() + getQueueName());

      ClientMessage ignoredMessage = clientSession.createMessage(false);
      ignoredMessage.putStringProperty("foo-bar", "1234");
      ignoredMessage.getBodyBuffer().writeNullableSimpleString(SimpleString.of("Ignored message"));

      ClientMessage realMessage = clientSession.createMessage(false);
      realMessage.putStringProperty("foo-bar", "zzz");
      realMessage.getBodyBuffer().writeNullableSimpleString(SimpleString.of("Real message"));

      producer.send(ignoredMessage);
      producer.send(realMessage);

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Real message", frame.getBody(), "Should have received the real message but got: " + frame);

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithAutoAckAndXpathSelector() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO, null, "XPATH 'root/a'");

      sendJmsMessage("<root><b key='first' num='1'/><c key='second' num='2'>c</c></root>");
      sendJmsMessage("<root><a key='first' num='1'/><b key='second' num='2'>b</b></root>");

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("<root><a key='first' num='1'/><b key='second' num='2'>b</b></root>", frame.getBody(), "Should have received the real message but got: " + frame);

      conn.disconnect();
   }

   @Test
   public void testSubscribeWithClientAck() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());
      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertNotNull(frame.getHeader(Stomp.Headers.Message.MESSAGE_ID));
      ack(conn, null, frame);

      conn.disconnect();

      // message should not be received since message was acknowledged by the client
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(100);
      assertNull(message);
   }

   @Test
   public void testRedeliveryWithClientAck() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());
      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      conn.disconnect();

      // message should be received since message was not acknowledged
      MessageConsumer consumer = session.createConsumer(queue);
      Message message = consumer.receive(1000);
      assertNotNull(message);
      assertTrue(message.getJMSRedelivered());
   }

   @Test
   public void testSubscribeWithClientAckThenConsumingAgainWithAutoAckWithNoDisconnectFrame() throws Exception {
      assertSubscribeWithClientAckThenConsumeWithAutoAck(false);
   }

   @Test
   public void testSubscribeWithClientAckThenConsumingAgainWithAutoAckWithExplicitDisconnect() throws Exception {
      assertSubscribeWithClientAckThenConsumeWithAutoAck(true);
   }

   protected void assertSubscribeWithClientAckThenConsumeWithAutoAck(boolean sendDisconnect) throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.CLIENT);
      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      logger.debug("Reconnecting!");

      if (sendDisconnect) {
         conn.disconnect();
      }
      conn.destroy();
      conn = StompClientConnectionFactory.createClientConnection(uri);

      // message should be received since message was not acknowledged
      conn.connect(defUser, defPass);

      subscribe(conn, null);

      frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      conn.disconnect();
      conn.destroy();

      conn = StompClientConnectionFactory.createClientConnection(uri);

      // now let's make sure we don't see the message again

      conn.connect(defUser, defPass);

      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO, null, true);

      sendJmsMessage("shouldBeNextMessage");

      frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("shouldBeNextMessage", frame.getBody());
   }

   @Test
   public void testUnsubscribe() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      // send a message to our queue
      sendJmsMessage("first message");

      // receive message
      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      // remove suscription
      unsubscribe(conn, null, getQueuePrefix() + getQueueName(), true, false);

      // send a message to our queue
      sendJmsMessage("second message");

      frame = conn.receiveFrame(100);
      logger.debug("Received frame: {}", frame);
      assertNull(frame, "No message should have been received since subscription was removed");
   }

   @Test
   public void testUnsubscribeWithID() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, "mysubid", Stomp.Headers.Subscribe.AckModeValues.AUTO);

      // send a message to our queue
      sendJmsMessage("first message");

      // receive message from socket
      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      // remove suscription
      unsubscribe(conn, "mysubid", null, true, false);

      // send a message to our queue
      sendJmsMessage("second message");

      frame = conn.receiveFrame(100);
      logger.debug("Received frame: {}", frame);
      assertNull(frame, "No message should have been received since subscription was removed");

   }

   @Test
   public void testTransactionCommit() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);
      conn.connect(defUser, defPass);

      beginTransaction(conn, "tx1");
      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World", true, null, "tx1");

      // check the message is not committed
      assertNull(consumer.receive(100));

      commitTransaction(conn, "tx1", true);

      Message message = consumer.receive(1000);
      assertNotNull(message, "Should have received a message");
   }

   @Test
   public void testSuccessiveTransactionsWithSameID() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);
      conn.connect(defUser, defPass);

      // first tx
      beginTransaction(conn, "tx1");
      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World", true, null, "tx1");
      commitTransaction(conn, "tx1");

      Message message = consumer.receive(1000);
      assertNotNull(message, "Should have received a message");

      // 2nd tx with same tx ID
      beginTransaction(conn, "tx1");
      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World", true, null, "tx1");
      commitTransaction(conn, "tx1");

      message = consumer.receive(1000);
      assertNotNull(message, "Should have received a message");
   }

   @Test
   public void testBeginSameTransactionTwice() throws Exception {
      conn.connect(defUser, defPass);
      beginTransaction(conn, "tx1");
      beginTransaction(conn, "tx1");

      ClientStompFrame frame = conn.receiveFrame(1000);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());
   }

   @Test
   public void testTransactionRollback() throws Exception {
      MessageConsumer consumer = session.createConsumer(queue);
      String txId = "tx1";

      conn.connect(defUser, defPass);
      beginTransaction(conn, txId);
      send(conn, getQueuePrefix() + getQueueName(), null, "first message", true, null, txId);
      abortTransaction(conn, txId);

      beginTransaction(conn, txId);
      send(conn, getQueuePrefix() + getQueueName(), null, "second message", true, null, txId);
      commitTransaction(conn, txId);

      // only second msg should be received since first msg was rolled back
      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("second message", message.getText());
   }

   @Test
   public void testSubscribeToTopic() throws Exception {
      final int baselineQueueCount = server.getActiveMQServerControl().getQueueNames().length;

      conn.connect(defUser, defPass);

      subscribeTopic(conn, null, null, null, true);

      Wait.assertTrue("Subscription queue should be created here", () -> {
         int length = server.getActiveMQServerControl().getQueueNames().length;
         if (length - baselineQueueCount == 1) {
            return true;
         } else {
            logger.debug("Queue count: {}", (length - baselineQueueCount));
            return false;
         }
      });

      sendJmsMessage(getName(), topic);

      ClientStompFrame frame = conn.receiveFrame(1000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(getName(), frame.getBody());

      unsubscribe(conn, null, getTopicPrefix() + getTopicName(), true, false);

      sendJmsMessage(getName(), topic);

      frame = conn.receiveFrame(100);
      logger.debug("Received frame: {}", frame);
      assertNull(frame, "No message should have been received since subscription was removed");

      assertEquals(0, server.getActiveMQServerControl().getQueueNames().length - baselineQueueCount, "Subscription queue should be deleted");

      conn.disconnect();
   }

   @Test
   public void testSubscribeToQueue() throws Exception {
      final int baselineQueueCount = server.getActiveMQServerControl().getQueueNames().length;

      conn.connect(defUser, defPass);
      subscribe(conn, null, null, null, true);

      Wait.assertFalse("Queue should not be created here", () -> {
         if (server.getActiveMQServerControl().getQueueNames().length - baselineQueueCount == 1) {
            return true;
         } else {
            return false;
         }
      });

      sendJmsMessage(getName(), queue);

      ClientStompFrame frame = conn.receiveFrame(1000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(getName(), frame.getBody());

      unsubscribe(conn, null, getQueuePrefix() + getQueueName(), true, false);

      sendJmsMessage(getName(), queue);

      frame = conn.receiveFrame(100);
      logger.debug("Received frame: {}", frame);
      assertNull(frame, "No message should have been received since subscription was removed");

      assertEquals(baselineQueueCount, server.getActiveMQServerControl().getQueueNames().length, "Subscription queue should not be deleted");

      conn.disconnect();
   }

   @Test
   public void testSubscribeToNonExistentQueue() throws Exception {
      String nonExistentQueue = RandomUtil.randomUUIDString();

      conn.connect(defUser, defPass);
      subscribe(conn, null, null, null, null, getQueuePrefix() + nonExistentQueue, true);

      sendJmsMessage(getName(), ActiveMQJMSClient.createQueue(nonExistentQueue));

      ClientStompFrame frame = conn.receiveFrame(1000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getQueuePrefix() + nonExistentQueue, frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(getName(), frame.getBody());

      assertNotNull(server.getPostOffice().getBinding(SimpleString.of(nonExistentQueue)));

      final Queue subscription = ((LocalQueueBinding) server.getPostOffice().getBinding(SimpleString.of(nonExistentQueue))).getQueue();

      Wait.assertEquals(0, subscription::getMessageCount);

      unsubscribe(conn, null, getQueuePrefix() + nonExistentQueue, true, false);

      Wait.assertTrue(() -> server.getPostOffice().getBinding(SimpleString.of(nonExistentQueue)) == null);

      sendJmsMessage(getName(), ActiveMQJMSClient.createQueue(nonExistentQueue));

      frame = conn.receiveFrame(100);
      logger.debug("Received frame: {}", frame);
      assertNull(frame, "No message should have been received since subscription was removed");

      conn.disconnect();
   }

   @Test
   public void testDurableSubscriberWithReconnection() throws Exception {
      conn.connect(defUser, defPass, "myclientid");
      subscribeTopic(conn, null, null, getName());

      conn.disconnect();

      Thread.sleep(500);

      // send the message when the durable subscriber is disconnected
      sendJmsMessage(getName(), topic);

      conn.destroy();
      conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass, "myclientid");

      subscribeTopic(conn, null, null, getName());

      ClientStompFrame frame = conn.receiveFrame(3000);
      assertNotNull(frame, "Should have received a message from the durable subscription");
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(getName(), frame.getBody());

      unsubscribe(conn, null, getTopicPrefix() + getTopicName(), true, true);

      conn.disconnect();
   }

   @Test
   public void testDurableSubscriberWithReconnectionLegacy() throws Exception {
      conn.connect(defUser, defPass, "myclientid");
      subscribeTopicLegacyActiveMQ(conn, null, null, getName(), true, false);

      conn.disconnect();

      Thread.sleep(500);

      // send the message when the durable subscriber is disconnected
      sendJmsMessage(getName(), topic);

      conn.destroy();
      conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass, "myclientid");

      subscribeTopicLegacyActiveMQ(conn, null, null, getName(), true, false);

      ClientStompFrame frame = conn.receiveFrame(3000);
      assertNotNull(frame, "Should have received a message from the durable subscription");
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(getName(), frame.getBody());

      unsubscribeLegacyActiveMQ(conn, null, getTopicPrefix() + getTopicName(), true, true);

      conn.disconnect();
   }

   @Test
   public void testDurableSubscriber() throws Exception {
      conn.connect(defUser, defPass, "myclientid");
      subscribeTopic(conn, null, null, getName(), true);
      ClientStompFrame response = subscribeTopic(conn, null, null, getName(), true);

      // creating a subscriber with the same durable-subscriber-name must fail
      assertEquals(Stomp.Responses.ERROR, response.getCommand());

      conn.disconnect();
   }

   @Test
   public void testDurableSubscriberLegacySubscriptionHeader() throws Exception {
      conn.connect(defUser, defPass, "myclientid");
      subscribeTopicLegacyActiveMQ(conn, null, null, getName(), true, false);
      ClientStompFrame response = subscribeTopicLegacyActiveMQ(conn, null, null, getName(), true, false);

      // creating a subscriber with the same durable-subscriber-name must fail
      assertEquals(Stomp.Responses.ERROR, response.getCommand());

      conn.disconnect();
   }

   @Test
   public void testDurableUnSubscribe() throws Exception {
      conn.connect(defUser, defPass, "myclientid");
      subscribeTopic(conn, null, null, getName(), true);
      conn.disconnect();
      Thread.sleep(500);

      assertNotNull(server.locateQueue(SimpleString.of("myclientid." + getName())));

      conn.destroy();
      conn = StompClientConnectionFactory.createClientConnection(uri);

      conn.connect(defUser, defPass, "myclientid");
      unsubscribe(conn, getName(), getTopicPrefix() + getTopicName(), false, true);
      conn.disconnect();
      Thread.sleep(500);

      Wait.assertTrue(() -> server.locateQueue(SimpleString.of("myclientid." + getName())) == null);
   }

   @Test
   public void testDurableUnSubscribeWithoutDurableSubName() throws Exception {
      server.getConfiguration().getWildcardConfiguration().setDelimiter('/');
      server.getAddressSettingsRepository().addMatch("/topic/#", new AddressSettings().setDefaultAddressRoutingType(RoutingType.MULTICAST).setDefaultQueueRoutingType(RoutingType.MULTICAST));
      conn.connect(defUser, defPass, "myclientid");
      String subId = UUID.randomUUID().toString();
      String durableSubName = UUID.randomUUID().toString();
      String receipt = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE).addHeader(Stomp.Headers.Subscribe.DESTINATION, "/topic/test.foo").addHeader(Stomp.Headers.Unsubscribe.ID, subId).addHeader(Stomp.Headers.Subscribe.ACK_MODE, Stomp.Headers.Subscribe.AckModeValues.CLIENT_INDIVIDUAL).addHeader(Stomp.Headers.Subscribe.DURABLE_SUBSCRIPTION_NAME, durableSubName).addHeader(Stomp.Headers.RECEIPT_REQUESTED, receipt);

      frame = conn.sendFrame(frame);
      assertEquals(receipt, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      Wait.assertTrue(() -> server.locateQueue(SimpleString.of("myclientid." + durableSubName)) != null);

      receipt = UUID.randomUUID().toString();
      frame = conn.createFrame(Stomp.Commands.UNSUBSCRIBE).addHeader(Stomp.Headers.Unsubscribe.ID, subId).addHeader(Stomp.Headers.RECEIPT_REQUESTED, receipt);

      frame = conn.sendFrame(frame);
      assertEquals(receipt, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      conn.disconnect();

      // make sure the durable subscription queue is still there
      Wait.assertTrue(() -> server.locateQueue(SimpleString.of("myclientid." + durableSubName)) != null);
   }

   @Test
   public void testDurableUnSubscribeLegacySubscriptionHeader() throws Exception {
      conn.connect(defUser, defPass, "myclientid");
      subscribeTopicLegacyActiveMQ(conn, null, null, getName(), true, false);
      conn.disconnect();
      Thread.sleep(500);

      assertNotNull(server.locateQueue(SimpleString.of("myclientid." + getName())));

      conn.destroy();
      conn = StompClientConnectionFactory.createClientConnection(uri);

      conn.connect(defUser, defPass, "myclientid");
      unsubscribeLegacyActiveMQ(conn, getName(), getTopicPrefix() + getTopicName(), false, true);
      conn.disconnect();
      Thread.sleep(500);

      Wait.assertTrue(() -> server.locateQueue(SimpleString.of("myclientid." + getName())) == null);
   }

   @Test
   public void testSubscribeToTopicWithNoLocalSendWithJMS() throws Exception {
      conn.connect(defUser, defPass);
      subscribeTopic(conn, null, null, null, true, true);

      // send a message on the same connection => it should not be received is noLocal = true on subscribe
      send(conn, getTopicPrefix() + getTopicName(), null, "Hello World");

      ClientStompFrame frame = conn.receiveFrame(100);
      assertNull(frame, "No message should have been received since subscription was removed");

      // send message on another JMS connection => it should be received
      // a JMS message automatically gets a property named __AMQ_CID
      sendJmsMessage(getName(), topic);
      frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(getName(), frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testSubscribeToTopicWithNoLocalSendWithStomp() throws Exception {
      conn.connect(defUser, defPass);
      StompClientConnection conn2 = StompClientConnectionFactory.createClientConnection(uri);
      conn2.connect(defUser, defPass);

      try {
         subscribeTopic(conn, null, null, null, true, true);

         // send a message on the same connection => it should not be received as noLocal = true on subscribe
         send(conn, getTopicPrefix() + getTopicName(), null, "Hello World");

         ClientStompFrame frame = conn.receiveFrame(100);
         assertNull(frame, "No message should have been received since subscription specified noLocal");

         // send message on another STOMP connection => it should be received
         // a STOMP message does *not* automatically get a property named __AMQ_CID if there aren't any noLocal subscriptions on the connection
         send(conn2, getTopicPrefix() + getTopicName(), null, getName());
         frame = conn.receiveFrame(10000);
         assertNotNull(frame);
         assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
         assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
         assertEquals(getName(), frame.getBody());
      } finally {
         conn.disconnect();
         conn2.disconnect();
      }
   }

   @Test
   public void testSubscribeToTopicWithNoLocalAndNormal() throws Exception {
      conn.connect(defUser, defPass);
      String noLocalSubscriptionId = RandomUtil.randomUUIDString();
      String normalSubscriptionId = RandomUtil.randomUUIDString();
      subscribeTopic(conn, noLocalSubscriptionId, null, null, true, true);
      subscribeTopic(conn, normalSubscriptionId, null, null, true, false);

      StompProtocolManager stompProtocolManager = getStompProtocolManager();
      int totalSubCount = 0;
      int noLocalSubCount = 0;
      for (StompSession session : stompProtocolManager.getSessions()) {
         totalSubCount += session.getSubscriptionCount();
         noLocalSubCount += session.getNoLocalSubscriptionCount();
      }
      assertEquals(1, noLocalSubCount);
      assertEquals(2, totalSubCount);

      { // Send a message on the same connection. It should be received by the normal subscription and not by the noLocal one.
         send(conn, getTopicPrefix() + getTopicName(), null, "Hello World");

         ClientStompFrame frame = conn.receiveFrame(100);
         assertNotNull(frame);
         assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
         assertEquals(normalSubscriptionId, frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));
         assertNotNull(frame.getHeader("__AMQ_CID"));
         frame = conn.receiveFrame(100);
         assertNull(frame);
      }

      unsubscribe(conn, noLocalSubscriptionId, true);

      totalSubCount = 0;
      noLocalSubCount = 0;
      for (StompSession session : stompProtocolManager.getSessions()) {
         totalSubCount += session.getSubscriptionCount();
         noLocalSubCount += session.getNoLocalSubscriptionCount();
      }
      assertEquals(0, noLocalSubCount);
      assertEquals(1, totalSubCount);

      { // Send another message on the same connection. It should be received by the normal subscription.
         send(conn, getTopicPrefix() + getTopicName(), null, "Hello World");

         ClientStompFrame frame = conn.receiveFrame(100);
         assertNotNull(frame);
         assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
         assertEquals(normalSubscriptionId, frame.getHeader(Stomp.Headers.Message.SUBSCRIPTION));
         assertNull(frame.getHeader("__AMQ_CID"));
      }

      unsubscribe(conn, normalSubscriptionId, true);

      totalSubCount = 0;
      noLocalSubCount = 0;
      for (StompSession session : stompProtocolManager.getSessions()) {
         totalSubCount += session.getSubscriptionCount();
         noLocalSubCount += session.getNoLocalSubscriptionCount();
      }
      assertEquals(0, noLocalSubCount);
      assertEquals(0, totalSubCount);

      conn.disconnect();
   }

   @Test
   public void testSubscribeToTopicWithNoLocalAndSelector() throws Exception {
      conn.connect(defUser, defPass);
      subscribeTopic(conn, null, null, null, true, true, "a=foo OR b=bar");

      // send a message on the same connection => it should not be received as noLocal = true on subscribe
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, getTopicPrefix() + getTopicName()).addHeader("b", "bar").setBody("Hello World");
      conn.sendFrame(frame);

      frame = conn.receiveFrame(100);
      assertNull(frame, "No message should have been received since noLocal=true");

      // send message on another JMS connection => it should be received
      sendJmsMessage(getName().getBytes(StandardCharsets.UTF_8), "b", "bar", topic);
      frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals(getName(), frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testTopicExistsAfterNoUnsubscribeDisconnect() throws Exception {
      conn.connect(defUser, defPass);
      subscribeTopic(conn, null, null, null, true);

      // disconnect, _without unsubscribing_
      conn.disconnect();

      Thread.sleep(500);

      conn.destroy();

      // connect again
      conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      // send a receipted message to the topic
      ClientStompFrame response = send(conn, getTopicPrefix() + getTopicName(), null, "Hello World", true);
      assertEquals(Stomp.Responses.RECEIPT, response.getCommand());

      // ...and nothing else
      ClientStompFrame frame = conn.receiveFrame(100);
      logger.debug("Received frame: {}", frame);
      assertNull(frame);

      conn.disconnect();
   }

   @Test
   public void testClientAckNotPartOfTransaction() throws Exception {
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.CLIENT);

      sendJmsMessage(getName());

      ClientStompFrame frame = conn.receiveFrame(10000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
      String messageID = frame.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      assertNotNull(messageID);
      assertEquals(getName(), frame.getBody());

      beginTransaction(conn, "tx1");
      ack(conn, null, messageID, "tx1");
      abortTransaction(conn, "tx1");

      frame = conn.receiveFrame(100);
      assertNull(frame, "No message should have been received as the message was acked even though the transaction has been aborted");

      unsubscribe(conn, null, getQueuePrefix() + getQueueName(), false, false);

      conn.disconnect();
   }

   @Test
   public void testMultiProtocolConsumers() throws Exception {
      final int TIME_OUT = 2000;
      // a timeout for when we expect negative results (like receive==null)
      final int NEGATIVE_TIME_OUT = 100;

      int count = 1000;

      // Create 2 core consumers
      MessageConsumer consumer1 = session.createConsumer(topic);
      MessageConsumer consumer2 = session.createConsumer(topic);

      // connect and subscribe STOMP consumer
      conn.connect(defUser, defPass);
      subscribeTopic(conn, null, null, null, true);

      MessageProducer producer = session.createProducer(topic);
      TextMessage message = session.createTextMessage(getName());

      for (int i = 1; i <= count; i++) {
         producer.send(message);
         assertNotNull(consumer1.receive(TIME_OUT));
         assertNotNull(consumer2.receive(TIME_OUT));
         ClientStompFrame frame = conn.receiveFrame(TIME_OUT);
         assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
         assertEquals(getTopicPrefix() + getTopicName(), frame.getHeader(Stomp.Headers.Send.DESTINATION));
         assertEquals(getName(), frame.getBody());
      }

      consumer1.close();
      consumer2.close();
      unsubscribe(conn, null, getTopicPrefix() + getTopicName(), true, false);

      sendJmsMessage(getName(), topic);

      ClientStompFrame frame = conn.receiveFrame(NEGATIVE_TIME_OUT);
      logger.debug("Received frame: {}", frame);
      assertNull(frame, "No message should have been received since subscription was removed");

      conn.disconnect();
   }

   @Test
   //stomp should return an ERROR when acking a non-existent message
   public void testUnexpectedAck() throws Exception {
      String messageID = "888888";

      conn.connect(defUser, defPass);
      ack(conn, null, messageID, null);

      ClientStompFrame frame = conn.receiveFrame(1000);
      assertNotNull(frame);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());

      conn.disconnect();
   }

   @Test
   public void testDotAnycastPrefixOnSend() throws Exception {
      testPrefix("jms.queue.", RoutingType.ANYCAST, true);
   }

   @Test
   public void testDotMulticastPrefixOnSend() throws Exception {
      testPrefix("jms.topic.", RoutingType.MULTICAST, true);
   }

   @Test
   public void testDotAnycastPrefixOnSubscribe() throws Exception {
      testPrefix("jms.queue.", RoutingType.ANYCAST, false);
   }

   @Test
   public void testDotMulticastPrefixOnSubscribe() throws Exception {
      testPrefix("jms.topic.", RoutingType.MULTICAST, false);
   }

   @Test
   public void testSlashAnycastPrefixOnSend() throws Exception {
      testPrefix("/queue/", RoutingType.ANYCAST, true);
   }

   @Test
   public void testSlashMulticastPrefixOnSend() throws Exception {
      testPrefix("/topic/", RoutingType.MULTICAST, true);
   }

   @Test
   public void testSlashAnycastPrefixOnSubscribe() throws Exception {
      testPrefix("/queue/", RoutingType.ANYCAST, false);
   }

   @Test
   public void testSlashMulticastPrefixOnSubscribe() throws Exception {
      testPrefix("/topic/", RoutingType.MULTICAST, false);
   }

   public void testPrefix(final String prefix, final RoutingType routingType, final boolean send) throws Exception {
      int port = 61614;

      URI uri = createStompClientUri(scheme, hostname, port);

      final String ADDRESS = UUID.randomUUID().toString();
      final String PREFIXED_ADDRESS = prefix + ADDRESS;
      String param = routingType.toString();
      String urlParam = param.toLowerCase() + "Prefix";
      server.getRemotingService().createAcceptor("test", "tcp://" + hostname + ":" + port + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME + "&" + urlParam + "=" + prefix).start();
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      // since this queue doesn't exist the broker should create a new address using the routing type matching the prefix
      if (send) {
         send(conn, PREFIXED_ADDRESS, null, "Hello World", true);
      } else {
         String uuid = UUID.randomUUID().toString();

         ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE).addHeader(Stomp.Headers.Subscribe.DESTINATION, PREFIXED_ADDRESS).addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);

         frame = conn.sendFrame(frame);

         assertEquals(uuid, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));
      }

      AddressInfo addressInfo = server.getAddressInfo(SimpleString.of(ADDRESS));
      assertNotNull(addressInfo, "No address was created with the name " + ADDRESS);

      Set<RoutingType> routingTypes = new HashSet<>();
      routingTypes.add(RoutingType.valueOf(param));
      assertEquals(routingTypes, addressInfo.getRoutingTypes());

      conn.disconnect();
   }

   /**
    * This test and testPrefixedAutoCreatedMulticastAndAnycastWithSameName are basically the same but doing the
    * operations in opposite order. In this test the anycast subscription is created first.
    */
   @Test
   public void testPrefixedAutoCreatedAnycastAndMulticastWithSameName() throws Exception {
      int port = 61614;

      URI uri = createStompClientUri(scheme, hostname, port);

      final String ADDRESS = UUID.randomUUID().toString();
      server.getRemotingService().createAcceptor("test", "tcp://" + hostname + ":" + port + "?protocols=STOMP&anycastPrefix=/queue/&multicastPrefix=/topic/").start();
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      // since this queue doesn't exist the broker should create a new ANYCAST address & queue
      String uuid = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE).addHeader(Stomp.Headers.Subscribe.DESTINATION, "/queue/" + ADDRESS).addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);
      frame = conn.sendFrame(frame);
      assertEquals(uuid, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      AddressInfo addressInfo = server.getAddressInfo(SimpleString.of(ADDRESS));
      assertNotNull(addressInfo, "No address was created with the name " + ADDRESS);
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.ANYCAST));
      assertFalse(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));
      assertNotNull(server.locateQueue(SimpleString.of(ADDRESS)));

      // sending a MULTICAST message should alter the address to support MULTICAST
      frame = send(conn, "/topic/" + ADDRESS, null, "Hello World 1", true);
      assertNotEquals("ERROR", frame.getCommand());
      addressInfo = server.getAddressInfo(SimpleString.of(ADDRESS));
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.ANYCAST));
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));

      // however, no message should be routed to the ANYCAST queue
      frame = conn.receiveFrame(100);
      assertNull(frame);

      // sending a message to the ANYCAST queue, should be received
      frame = send(conn, "/queue/" + ADDRESS, null, "Hello World 2", true);
      assertNotEquals("ERROR", frame.getCommand());
      frame = conn.receiveFrame(1000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Hello World 2", frame.getBody());
      assertEquals(RoutingType.ANYCAST.toString(), frame.getHeader(Stomp.Headers.Send.DESTINATION_TYPE));
      assertEquals("/queue/" + ADDRESS, frame.getHeader(Stomp.Headers.Send.DESTINATION));
      frame = conn.receiveFrame(100);
      assertNull(frame);

      unsubscribe(conn, null, "/queue/" + ADDRESS, true, false);

      Wait.assertTrue(() -> server.getAddressInfo(SimpleString.of(ADDRESS)) == null);

      // now subscribe to the address in a MULTICAST way which will create a MULTICAST queue for the subscription
      uuid = UUID.randomUUID().toString();
      frame = conn.createFrame(Stomp.Commands.SUBSCRIBE).addHeader(Stomp.Headers.Subscribe.DESTINATION, "/topic/" + ADDRESS).addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);
      frame = conn.sendFrame(frame);
      assertEquals(uuid, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      // send a message which will be routed to the MULTICAST queue
      frame = send(conn, "/topic/" + ADDRESS, null, "Hello World 3", true);
      assertNotEquals("ERROR", frame.getCommand());

      // receive that message on the topic subscription
      frame = conn.receiveFrame(1000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Hello World 3", frame.getBody());
      assertEquals(RoutingType.MULTICAST.toString(), frame.getHeader(Stomp.Headers.Send.DESTINATION_TYPE));
      assertEquals("/topic/" + ADDRESS, frame.getHeader(Stomp.Headers.Send.DESTINATION));
      frame = conn.receiveFrame(100);
      assertNull(frame);

      unsubscribe(conn, null, "/topic/" + ADDRESS, true, false);

      conn.disconnect();
   }

   /**
    * This test and testPrefixedAutoCreatedMulticastAndAnycastWithSameName are basically the same but doing the
    * operations in opposite order. In this test the multicast subscription is created first.
    */
   @Test
   public void testPrefixedAutoCreatedMulticastAndAnycastWithSameName() throws Exception {
      int port = 61614;

      URI uri = createStompClientUri(scheme, hostname, port);

      final String ADDRESS = UUID.randomUUID().toString();
      server.getRemotingService().createAcceptor("test", "tcp://" + hostname + ":" + port + "?protocols=STOMP&anycastPrefix=/queue/&multicastPrefix=/topic/").start();
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      // since this queue doesn't exist the broker should create a new MULTICAST address
      String uuid = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE).addHeader(Stomp.Headers.Subscribe.DESTINATION, "/topic/" + ADDRESS).addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);
      frame = conn.sendFrame(frame);
      assertEquals(uuid, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      AddressInfo addressInfo = server.getAddressInfo(SimpleString.of(ADDRESS));
      assertNotNull(addressInfo, "No address was created with the name " + ADDRESS);
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));
      assertFalse(addressInfo.getRoutingTypes().contains(RoutingType.ANYCAST));

      // sending an ANYCAST message should alter the address to support ANYCAST and create an ANYCAST queue
      frame = send(conn, "/queue/" + ADDRESS, null, "Hello World 1", true);
      assertNotEquals("ERROR", frame.getCommand());
      addressInfo = server.getAddressInfo(SimpleString.of(ADDRESS));
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.ANYCAST));
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));
      assertNotNull(server.locateQueue(SimpleString.of(ADDRESS)));

      // however, no message should be routed to the MULTICAST queue
      frame = conn.receiveFrame(100);
      assertNull(frame);

      // sending a message to the MULTICAST queue, should be received
      frame = send(conn, "/topic/" + ADDRESS, null, "Hello World 2", true);
      assertNotEquals("ERROR", frame.getCommand());
      frame = conn.receiveFrame(2000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Hello World 2", frame.getBody());
      assertEquals(RoutingType.MULTICAST.toString(), frame.getHeader(Stomp.Headers.Send.DESTINATION_TYPE));
      assertEquals("/topic/" + ADDRESS, frame.getHeader(Stomp.Headers.Send.DESTINATION));
      frame = conn.receiveFrame(100);
      assertNull(frame);

      frame = unsubscribe(conn, null, "/topic/" + ADDRESS, true, false);
      assertNotEquals("ERROR", frame.getCommand());

      // now subscribe to the address in an ANYCAST way
      uuid = UUID.randomUUID().toString();
      frame = conn.createFrame(Stomp.Commands.SUBSCRIBE).addHeader(Stomp.Headers.Subscribe.DESTINATION, "/queue/" + ADDRESS).addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);
      frame = conn.sendFrame(frame);
      assertEquals(uuid, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      // receive that message on the ANYCAST queue
      frame = conn.receiveFrame(1000);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Hello World 1", frame.getBody());
      assertEquals(RoutingType.ANYCAST.toString(), frame.getHeader(Stomp.Headers.Send.DESTINATION_TYPE));
      assertEquals("/queue/" + ADDRESS, frame.getHeader(Stomp.Headers.Send.DESTINATION));
      frame = conn.receiveFrame(100);
      assertNull(frame);

      unsubscribe(conn, null, "/queue/" + ADDRESS, true, false);

      conn.disconnect();
   }

   @Test
   public void testDotPrefixedSendAndRecieveAnycast() throws Exception {
      testPrefixedSendAndRecieve("jms.queue.", RoutingType.ANYCAST);
   }

   @Test
   public void testDotPrefixedSendAndRecieveMulticast() throws Exception {
      testPrefixedSendAndRecieve("jms.topic.", RoutingType.MULTICAST);
   }

   @Test
   public void testSlashPrefixedSendAndRecieveAnycast() throws Exception {
      testPrefixedSendAndRecieve("/queue/", RoutingType.ANYCAST);
   }

   @Test
   public void testSlashPrefixedSendAndRecieveMulticast() throws Exception {
      testPrefixedSendAndRecieve("/topic/", RoutingType.MULTICAST);
   }

   public void testPrefixedSendAndRecieve(final String prefix, RoutingType routingType) throws Exception {
      int port = 61614;

      URI uri = createStompClientUri(scheme, hostname, port);

      final String ADDRESS = UUID.randomUUID().toString();
      final String PREFIXED_ADDRESS = prefix + ADDRESS;
      String urlParam = routingType.toString().toLowerCase() + "Prefix";
      server.getRemotingService().createAcceptor("test", "tcp://" + hostname + ":" + port + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME + "&" + urlParam + "=" + prefix).start();
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);
      String uuid = UUID.randomUUID().toString();

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE).addHeader(Stomp.Headers.Subscribe.DESTINATION, PREFIXED_ADDRESS).addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);

      frame = conn.sendFrame(frame);
      assertEquals(uuid, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      send(conn, ADDRESS, null, "Hello World", true);

      frame = conn.receiveFrame(10000);
      assertNotNull(frame, "Should have received a message");
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals(ADDRESS, frame.getHeader(Stomp.Headers.Send.DESTINATION));
      assertEquals("Hello World", frame.getBody());

      conn.disconnect();
   }

   @Test
   public void testMulticastOperationsOnAnycastAddress() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false));
      testRoutingSemantics(getQueuePrefix() + getQueueName(), RoutingType.MULTICAST.toString());
   }

   @Test
   public void testAnycastOperationsOnMulticastAddress() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false));
      testRoutingSemantics(getTopicPrefix() + getTopicName(), RoutingType.ANYCAST.toString());
   }

   public void testRoutingSemantics(String routingType, String destination) throws Exception {
      conn.connect(defUser, defPass);

      String uuid = UUID.randomUUID().toString();

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE).addHeader(Stomp.Headers.Subscribe.SUBSCRIPTION_TYPE, routingType).addHeader(Stomp.Headers.Subscribe.DESTINATION, destination).addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);

      frame = conn.sendFrame(frame);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());

      uuid = UUID.randomUUID().toString();

      frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION_TYPE, RoutingType.MULTICAST.toString()).addHeader(Stomp.Headers.Send.DESTINATION, getQueuePrefix() + getQueueName()).addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);

      frame = conn.sendFrame(frame);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());
   }

   @Test
   public void testGetManagementAttributeFromStomp() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false));
      conn.connect(defUser, defPass);

      subscribe(conn, null);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, ActiveMQDefaultConfiguration.getDefaultManagementAddress().toString()).addHeader(Stomp.Headers.Send.REPLY_TO, getQueuePrefix() + getQueueName()).addHeader(ManagementHelper.HDR_RESOURCE_NAME.toString(), ResourceNames.QUEUE + getQueuePrefix() + getQueueName()).addHeader(ManagementHelper.HDR_ATTRIBUTE.toString(), "Address");

      conn.sendFrame(frame);

      frame = conn.receiveFrame(10000);

      logger.debug("Received: {}", frame);

      assertEquals(Boolean.TRUE.toString(), frame.getHeader(ManagementHelper.HDR_OPERATION_SUCCEEDED.toString()));
      // the address will be returned in the message body in a JSON array
      assertEquals("[\"" + getQueuePrefix() + getQueueName() + "\"]", frame.getBody());

      unsubscribe(conn, null);

      conn.disconnect();
   }

   @Test
   public void testInvokeOperationFromStomp() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, null);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND).addHeader(Stomp.Headers.Send.DESTINATION, ActiveMQDefaultConfiguration.getDefaultManagementAddress().toString()).addHeader(Stomp.Headers.Send.REPLY_TO, getQueuePrefix() + getQueueName()).addHeader(ManagementHelper.HDR_RESOURCE_NAME.toString(), ResourceNames.QUEUE + getQueuePrefix() + getQueueName()).addHeader(ManagementHelper.HDR_OPERATION_NAME.toString(), "countMessages").setBody("[\"color = 'blue'\"]");

      conn.sendFrame(frame);

      frame = conn.receiveFrame(10000);

      logger.debug("Received: {}", frame);

      assertEquals(Boolean.TRUE.toString(), frame.getHeader(ManagementHelper.HDR_OPERATION_SUCCEEDED.toString()));
      // there is no such messages => 0 returned in a JSON array
      assertEquals("[0]", frame.getBody());

      unsubscribe(conn, null);

      conn.disconnect();
   }

   @Test
   public void testAnycastMessageRoutingExclusivity() throws Exception {
      conn.connect(defUser, defPass);

      final String addressA = "addressA";
      final String queueA = "queueA";
      final String queueB = "queueB";
      final String queueC = "queueC";

      ActiveMQServerControl serverControl = server.getActiveMQServerControl();
      serverControl.createAddress(addressA, RoutingType.ANYCAST.toString() + "," + RoutingType.MULTICAST.toString());
      serverControl.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).toJSON());
      serverControl.createQueue(QueueConfiguration.of(queueB).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).toJSON());
      serverControl.createQueue(QueueConfiguration.of(queueC).setAddress(addressA).setRoutingType(RoutingType.MULTICAST).toJSON());

      send(conn, addressA, null, "Hello World!", true, RoutingType.ANYCAST);

      Wait.assertTrue(() -> server.locateQueue(SimpleString.of(queueA)).getMessageCount() + server.locateQueue(SimpleString.of(queueB)).getMessageCount() == 1);
      Wait.assertTrue(() -> server.locateQueue(SimpleString.of(queueC)).getMessageCount() == 0);
   }

   @Test
   public void testMulticastMessageRoutingExclusivity() throws Exception {
      conn.connect(defUser, defPass);

      final String addressA = "addressA";
      final String queueA = "queueA";
      final String queueB = "queueB";
      final String queueC = "queueC";

      ActiveMQServerControl serverControl = server.getActiveMQServerControl();
      serverControl.createAddress(addressA, RoutingType.ANYCAST.toString() + "," + RoutingType.MULTICAST.toString());
      serverControl.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).toJSON());
      serverControl.createQueue(QueueConfiguration.of(queueB).setAddress(addressA).setRoutingType(RoutingType.MULTICAST).toJSON());
      serverControl.createQueue(QueueConfiguration.of(queueC).setAddress(addressA).setRoutingType(RoutingType.MULTICAST).toJSON());

      send(conn, addressA, null, "Hello World!", true, RoutingType.MULTICAST);

      Wait.assertTrue(() -> server.locateQueue(SimpleString.of(queueA)).getMessageCount() == 0);
      Wait.assertTrue(() -> server.locateQueue(SimpleString.of(queueC)).getMessageCount() + server.locateQueue(SimpleString.of(queueB)).getMessageCount() == 2);
   }

   @Test
   public void testAmbiguousMessageRouting() throws Exception {
      conn.connect(defUser, defPass);

      final String addressA = "addressA";
      final String queueA = "queueA";
      final String queueB = "queueB";
      final String queueC = "queueC";
      final String queueD = "queueD";

      ActiveMQServerControl serverControl = server.getActiveMQServerControl();
      serverControl.createAddress(addressA, RoutingType.ANYCAST.toString() + "," + RoutingType.MULTICAST.toString());
      serverControl.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).toJSON());
      serverControl.createQueue(QueueConfiguration.of(queueB).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).toJSON());
      serverControl.createQueue(QueueConfiguration.of(queueC).setAddress(addressA).setRoutingType(RoutingType.MULTICAST).toJSON());
      serverControl.createQueue(QueueConfiguration.of(queueD).setAddress(addressA).setRoutingType(RoutingType.MULTICAST).toJSON());

      send(conn, addressA, null, "Hello World!", true);

      Wait.assertTrue(() -> server.locateQueue(SimpleString.of(queueA)).getMessageCount() + server.locateQueue(SimpleString.of(queueB)).getMessageCount() == 1);
      Wait.assertTrue(() -> server.locateQueue(SimpleString.of(queueC)).getMessageCount() + server.locateQueue(SimpleString.of(queueD)).getMessageCount() == 2);
   }

   @Test
   public void testAutoCreatedAnycastAddress() throws Exception {
      conn.connect(defUser, defPass);

      String queueName = UUID.randomUUID().toString();
      SimpleString simpleQueueName = SimpleString.of(queueName);

      assertNull(server.getAddressInfo(simpleQueueName));
      assertNull(server.locateQueue(simpleQueueName));

      server.getAddressSettingsRepository().addMatch(queueName, new AddressSettings().setDefaultAddressRoutingType(RoutingType.ANYCAST).setDefaultQueueRoutingType(RoutingType.ANYCAST));

      send(conn, queueName, null, "Hello ANYCAST");

      Wait.assertTrue("Address and queue should be created now", () -> (server.getAddressInfo(simpleQueueName) != null) && (server.locateQueue(simpleQueueName) != null));
      assertTrue(server.getAddressInfo(simpleQueueName).getRoutingTypes().contains(RoutingType.ANYCAST));
      assertEquals(RoutingType.ANYCAST, server.locateQueue(simpleQueueName).getRoutingType());
   }

   @Test
   public void testAutoCreatedMulticastAddress() throws Exception {
      conn.connect(defUser, defPass);

      String queueName = UUID.randomUUID().toString();
      SimpleString simpleQueueName = SimpleString.of(queueName);

      assertNull(server.getAddressInfo(simpleQueueName));
      assertNull(server.locateQueue(simpleQueueName));

      send(conn, queueName, null, "Hello MULTICAST");

      Wait.assertTrue("Address should be created now", () -> (server.getAddressInfo(simpleQueueName) != null));
      assertTrue(server.getAddressInfo(simpleQueueName).getRoutingTypes().contains(RoutingType.MULTICAST));
      assertNull(server.locateQueue(simpleQueueName));
   }

   @Test
   public void directDeliverDisabledOnStomp() throws Exception {
      String payload = "This is a test message";

      // Set up STOMP subscription
      conn.connect(defUser, defPass);
      subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      for (Binding b : iterableOf(server.getPostOffice().getAllBindings().filter(QueueBinding.class::isInstance))) {
         assertFalse(((QueueBinding) b).getQueue().isDirectDeliver(), "Queue " + ((QueueBinding) b).getQueue().getName());
      }

      // Send MQTT Message
      MQTTClientProvider clientProvider = new FuseMQTTClientProvider();
      clientProvider.connect("tcp://" + hostname + ":" + port);
      clientProvider.publish(getQueuePrefix() + getQueueName(), payload.getBytes(), 0);
      clientProvider.disconnect();

      // Receive STOMP Message
      ClientStompFrame frame = conn.receiveFrame();
      assertTrue(frame.getBody().contains(payload));

   }

   @Test
   public void testSameMessageHasDifferentMessageIdPerConsumer() throws Exception {
      conn.connect(defUser, defPass);

      subscribeTopic(conn, "sub1", "client", null);
      subscribeTopic(conn, "sub2", "client", null);

      sendJmsMessage(getName(), topic);

      ClientStompFrame frame1 = conn.receiveFrame();
      String firstMessageID = frame1.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      assertNotNull(firstMessageID);

      ClientStompFrame frame2 = conn.receiveFrame();
      String secondMessageID = frame2.getHeader(Stomp.Headers.Message.MESSAGE_ID);
      assertNotNull(secondMessageID);
      assertNotEquals(firstMessageID, secondMessageID, "MessageIDs must not equal");

      ack(conn, "sub1", frame1);
      ack(conn, "sub2", frame2);

      unsubscribe(conn, "sub1");
      unsubscribe(conn, "sub2");

      conn.disconnect();
   }

   @Test
   public void testMultipleSubscriptionsOnMulticastAddressReadSameMessage() throws Exception {
      doTestMultipleSubscriptionsOnMulticastAddressReadSameMessage(10);
   }

   @Test
   public void testMultipleSubscriptionsOnMulticastAddressReadSameLargeMessage() throws Exception {
      doTestMultipleSubscriptionsOnMulticastAddressReadSameMessage(120_000);
   }

   private void doTestMultipleSubscriptionsOnMulticastAddressReadSameMessage(int size) throws Exception {
      final String body = "A".repeat(size);

      final StompClientConnection conn_r1 = StompClientConnectionFactory.createClientConnection(uri);
      final StompClientConnection conn_r2 = StompClientConnectionFactory.createClientConnection(uri);

      try {
         conn_r1.connect(defUser, defPass);
         subscribeTopic(conn_r1, null, null, null);

         conn_r2.connect(defUser, defPass);
         subscribeTopic(conn_r2, null, null, null);

         // Sender
         conn.connect(defUser, defPass);
         send(conn, getTopicPrefix() + getTopicName(), null, body, true, RoutingType.MULTICAST);

         ClientStompFrame frame1 = conn_r1.receiveFrame(10000);
         ClientStompFrame frame2 = conn_r2.receiveFrame(10000);

         assertEquals(Stomp.Responses.MESSAGE, frame2.getCommand());
         assertEquals(Stomp.Responses.MESSAGE, frame1.getCommand());

         assertEquals(getTopicPrefix() + getTopicName(), frame2.getHeader(Stomp.Headers.Send.DESTINATION));
         assertEquals(getTopicPrefix() + getTopicName(), frame1.getHeader(Stomp.Headers.Send.DESTINATION));

         assertEquals(RoutingType.MULTICAST.toString(), frame2.getHeader(Stomp.Headers.Send.DESTINATION_TYPE));
         assertEquals(RoutingType.MULTICAST.toString(), frame1.getHeader(Stomp.Headers.Send.DESTINATION_TYPE));

         assertNull(frame2.getHeader(org.apache.activemq.artemis.api.core.Message.HDR_ROUTING_TYPE.toString()));
         assertNull(frame1.getHeader(org.apache.activemq.artemis.api.core.Message.HDR_ROUTING_TYPE.toString()));

         assertEquals(body, frame2.getBody());
         assertEquals(body, frame1.getBody());
      } finally {
         conn.disconnect();
         conn_r1.disconnect();
         conn_r2.disconnect();
      }
   }

   @Test
   public void testSubscribeToTopicWithEmptySelector() throws Exception {
      conn.connect(defUser, defPass);
      ClientStompFrame frame = subscribeTopic(conn, null, null, null, true, true, "   ");
      assertNotNull(frame);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());
      assertEquals("Selector cannot be empty", frame.getHeader(Stomp.Headers.Error.MESSAGE));
   }

   @Test
   public void testSubscribeToQueueWithEmptySelector() throws Exception {
      conn.connect(defUser, defPass);
      ClientStompFrame frame = subscribe(conn, null, null, null, "   ");
      assertNotNull(frame);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());
      assertEquals("Selector cannot be empty", frame.getHeader(Stomp.Headers.Error.MESSAGE));
   }

   @Test
   public void testSubscribeToTopicWithInvalidSelector() throws Exception {
      conn.connect(defUser, defPass);
      ClientStompFrame frame = subscribeTopic(conn, null, null, null, true, true, ") foo = 'bar' (");
      assertNotNull(frame);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());
      assertTrue(frame.getHeader(Stomp.Headers.Error.MESSAGE).contains("Invalid selector"));
   }

   @Test
   public void testSubscribeToQueueWithInvalidSelector() throws Exception {
      conn.connect(defUser, defPass);
      ClientStompFrame frame = subscribe(conn, null, null, null, ") foo = 'bar' (");
      assertNotNull(frame);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());
      assertTrue(frame.getHeader(Stomp.Headers.Error.MESSAGE).contains("Invalid selector"));
   }
}
