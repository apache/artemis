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

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.largemessage.LargeMessageTestBase;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled
@ExtendWith(ParameterizedTestExtension.class)
public class StompWithLargeMessagesTest extends StompTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Web Socket has max frame size of 64kb.  Large message tests only available over TCP.
   @Parameters(name = "{0}")
   public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][]{{"tcp+v10.stomp"}, {"tcp+v12.stomp"}});
   }

   public StompWithLargeMessagesTest(String scheme) {
      super(scheme);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   @Override
   public boolean isCompressLargeMessages() {
      return true;
   }

   @Override
   public boolean isPersistenceEnabled() {
      return true;
   }

   @Override
   public Integer getStompMinLargeMessageSize() {
      return 2048;
   }

   @TestTemplate
   public void testSendReceiveLargeMessage() throws Exception {
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);

      try {
         String address = "testLargeMessageAddress";
         server.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST));

         // STOMP default is UTF-8 == 1 byte per char.
         String payload = "t".repeat(10 * 1024 * 1024); // 10MB

         // Set up STOMP subscription
         conn.connect(defUser, defPass);
         subscribe(conn, null, Stomp.Headers.Subscribe.AckModeValues.AUTO, null, null, address, true);

         // Send Large Message
         send(conn, address, null, payload);

         // Receive STOMP Message
         ClientStompFrame frame = conn.receiveFrame();
         assertEquals(payload, frame.getBody());
      } finally {
         conn.disconnect();
      }
   }

   //stomp sender -> large -> stomp receiver
   @TestTemplate
   public void testSendReceiveLargePersistentMessages() throws Exception {
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      int count = 10;
      int msgSize = 1024 * 1024;
      char[] contents = new char[msgSize];
      for (int i = 0; i < msgSize; i++) {
         contents[i] = 'A';
      }
      String body = new String(contents);

      for (int i = 0; i < count; i++) {
         ClientStompFrame frame = conn.createFrame("SEND");
         frame.addHeader("destination", getQueuePrefix() + getQueueName());
         frame.addHeader("persistent", "true");
         frame.setBody(body);
         conn.sendFrame(frame);
      }

      ClientStompFrame subFrame = conn.createFrame("SUBSCRIBE");
      subFrame.addHeader("subscription-type", "ANYCAST");
      subFrame.addHeader("destination", getQueuePrefix() + getQueueName());
      subFrame.addHeader("ack", "auto");
      conn.sendFrame(subFrame);

      for (int i = 0; i < count; i++) {
         ClientStompFrame frame = conn.receiveFrame(60000);
         assertNotNull(frame);
         assertEquals("MESSAGE", frame.getCommand());
         assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader("destination"));
         int index = frame.getBody().indexOf("AAAA");
         assertEquals(msgSize, (frame.getBody().length() - index));
      }

      ClientStompFrame unsubFrame = conn.createFrame("UNSUBSCRIBE");
      unsubFrame.addHeader("destination", getQueuePrefix() + getQueueName());
      unsubFrame.addHeader("receipt", "567");
      ClientStompFrame response = conn.sendFrame(unsubFrame);
      assertNotNull(response);
      assertEquals("RECEIPT", response.getCommand());

      conn.disconnect();
   }

   //core sender -> large -> stomp receiver
   @TestTemplate
   public void testReceiveLargePersistentMessagesFromCore() throws Exception {
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      int msgSize = 3 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE;
      char[] contents = new char[msgSize];
      for (int i = 0; i < msgSize; i++) {
         contents[i] = 'B';
      }
      String msg = new String(contents);

      int count = 10;
      for (int i = 0; i < count; i++) {
         this.sendJmsMessage(msg);
      }

      ClientStompFrame subFrame = conn.createFrame("SUBSCRIBE");
      subFrame.addHeader("subscription-type", "ANYCAST");
      subFrame.addHeader("destination", getQueuePrefix() + getQueueName());
      subFrame.addHeader("ack", "auto");
      conn.sendFrame(subFrame);

      for (int i = 0; i < count; i++) {
         ClientStompFrame frame = conn.receiveFrame(60000);
         assertNotNull(frame);
         assertEquals("MESSAGE", frame.getCommand());
         assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader("destination"));
         int index = frame.getBody().indexOf("BBB");
         assertEquals(msgSize, (frame.getBody().length() - index));
      }

      ClientStompFrame unsubFrame = conn.createFrame("UNSUBSCRIBE");
      unsubFrame.addHeader("destination", getQueuePrefix() + getQueueName());
      unsubFrame.addHeader("receipt", "567");
      ClientStompFrame response = conn.sendFrame(unsubFrame);
      assertNotNull(response);
      assertEquals("RECEIPT", response.getCommand());

      conn.disconnect();
   }

//   //stomp v12 sender -> large -> stomp v12 receiver
//   @TestTemplate
//   public void testSendReceiveLargePersistentMessagesV12() throws Exception {
//      StompClientConnection connV12 = StompClientConnectionFactory.createClientConnection("1.2", "localhost", port);
//      connV12.connect(defUser, defPass);
//
//      int count = 10;
//      int szBody = 1024 * 1024;
//      char[] contents = new char[szBody];
//      for (int i = 0; i < szBody; i++) {
//         contents[i] = 'A';
//      }
//      String body = new String(contents);
//
//      ClientStompFrame frame = connV12.createFrame("SEND");
//      frame.addHeader("destination-type", "ANYCAST");
//      frame.addHeader("destination", getQueuePrefix() + getQueueName());
//      frame.addHeader("persistent", "true");
//      frame.setBody(body);
//
//      for (int i = 0; i < count; i++) {
//         connV12.sendFrame(frame);
//      }
//
//      ClientStompFrame subFrame = connV12.createFrame("SUBSCRIBE");
//      subFrame.addHeader("id", "a-sub");
//      subFrame.addHeader("subscription-type", "ANYCAST");
//      subFrame.addHeader("destination", getQueuePrefix() + getQueueName());
//      subFrame.addHeader("ack", "auto");
//
//      connV12.sendFrame(subFrame);
//
//      for (int i = 0; i < count; i++) {
//         ClientStompFrame receiveFrame = connV12.receiveFrame(30000);
//
//         Assert.assertNotNull(receiveFrame);
//         System.out.println("part of frame: " + receiveFrame.getBody().substring(0, 20));
//         Assert.assertTrue(receiveFrame.getCommand().equals("MESSAGE"));
//         Assert.assertEquals(receiveFrame.getHeader("destination"), getQueuePrefix() + getQueueName());
//         assertEquals(szBody, receiveFrame.getBody().length());
//      }
//
//      // remove susbcription
//      ClientStompFrame unsubFrame = connV12.createFrame("UNSUBSCRIBE");
//      unsubFrame.addHeader("id", "a-sub");
//      connV12.sendFrame(unsubFrame);
//
//      connV12.disconnect();
//   }
//
//   //core sender -> large -> stomp v12 receiver
//   @TestTemplate
//   public void testReceiveLargePersistentMessagesFromCoreV12() throws Exception {
//      int msgSize = 3 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE;
//      char[] contents = new char[msgSize];
//      for (int i = 0; i < msgSize; i++) {
//         contents[i] = 'B';
//      }
//      String msg = new String(contents);
//
//      int count = 10;
//      for (int i = 0; i < count; i++) {
//         this.sendJmsMessage(msg);
//      }
//
//      StompClientConnection connV12 = StompClientConnectionFactory.createClientConnection("1.2", "localhost", port);
//      connV12.connect(defUser, defPass);
//
//      ClientStompFrame subFrame = connV12.createFrame("SUBSCRIBE");
//      subFrame.addHeader("id", "a-sub");
//      subFrame.addHeader("subscription-type", "ANYCAST");
//      subFrame.addHeader("destination", getQueuePrefix() + getQueueName());
//      subFrame.addHeader("ack", "auto");
//      connV12.sendFrame(subFrame);
//
//      for (int i = 0; i < count; i++) {
//         ClientStompFrame receiveFrame = connV12.receiveFrame(30000);
//
//         Assert.assertNotNull(receiveFrame);
//         System.out.println("part of frame: " + receiveFrame.getBody().substring(0, 20));
//         Assert.assertTrue(receiveFrame.getCommand().equals("MESSAGE"));
//         Assert.assertEquals(receiveFrame.getHeader("destination"), getQueuePrefix() + getQueueName());
//         assertEquals(msgSize, receiveFrame.getBody().length());
//      }
//
//      // remove susbcription
//      ClientStompFrame unsubFrame = connV12.createFrame("UNSUBSCRIBE");
//      unsubFrame.addHeader("id", "a-sub");
//      connV12.sendFrame(unsubFrame);
//
//      connV12.disconnect();
//   }

   //core sender -> large (compressed regular) -> stomp v10 receiver
   @TestTemplate
   public void testReceiveLargeCompressedToRegularPersistentMessagesFromCore() throws Exception {
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      LargeMessageTestBase.TestLargeMessageInputStream input = new LargeMessageTestBase.TestLargeMessageInputStream(ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, true);
      LargeMessageTestBase.adjustLargeCompression(true, input, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      char[] contents = input.toArray();
      String msg = new String(contents);

      String leadingPart = msg.substring(0, 100);

      int count = 10;
      for (int i = 0; i < count; i++) {
         this.sendJmsMessage(msg);
      }

      ClientStompFrame subFrame = conn.createFrame("SUBSCRIBE");
      subFrame.addHeader("subscription-type", "ANYCAST");
      subFrame.addHeader("destination", getQueuePrefix() + getQueueName());
      subFrame.addHeader("ack", "auto");
      conn.sendFrame(subFrame);

      for (int i = 0; i < count; i++) {
         ClientStompFrame receiveFrame = conn.receiveFrame(30000);
         assertNotNull(receiveFrame);
         assertEquals("MESSAGE", receiveFrame.getCommand());
         assertEquals(receiveFrame.getHeader("destination"), getQueuePrefix() + getQueueName());
         int index = receiveFrame.getBody().indexOf(leadingPart);
         assertEquals(msg.length(), (receiveFrame.getBody().length() - index));
      }

      // remove suscription
      ClientStompFrame unsubFrame = conn.createFrame("UNSUBSCRIBE");
      unsubFrame.addHeader("destination", getQueuePrefix() + getQueueName());
      unsubFrame.addHeader("receipt", "567");
      ClientStompFrame response = conn.sendFrame(unsubFrame);
      assertNotNull(response);
      assertEquals("RECEIPT", response.getCommand());

      conn.disconnect();
   }

//   //core sender -> large (compressed regular) -> stomp v12 receiver
//   @TestTemplate
//   public void testReceiveLargeCompressedToRegularPersistentMessagesFromCoreV12() throws Exception {
//      LargeMessageTestBase.TestLargeMessageInputStream input = new LargeMessageTestBase.TestLargeMessageInputStream(ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, true);
//      LargeMessageTestBase.adjustLargeCompression(true, input, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);
//
//      char[] contents = input.toArray();
//      String msg = new String(contents);
//
//      int count = 10;
//      for (int i = 0; i < count; i++) {
//         this.sendJmsMessage(msg);
//      }
//
//      StompClientConnection connV12 = StompClientConnectionFactory.createClientConnection("1.2", "localhost", port);
//      connV12.connect(defUser, defPass);
//
//      ClientStompFrame subFrame = connV12.createFrame("SUBSCRIBE");
//      subFrame.addHeader("id", "a-sub");
//      subFrame.addHeader("subscription-type", "ANYCAST");
//      subFrame.addHeader("destination", getQueuePrefix() + getQueueName());
//      subFrame.addHeader("ack", "auto");
//
//      connV12.sendFrame(subFrame);
//
//      for (int i = 0; i < count; i++) {
//         ClientStompFrame receiveFrame = connV12.receiveFrame(30000);
//
//         Assert.assertNotNull(receiveFrame);
//         System.out.println("part of frame: " + receiveFrame.getBody().substring(0, 20));
//         Assert.assertTrue(receiveFrame.getCommand().equals("MESSAGE"));
//         Assert.assertEquals(receiveFrame.getHeader("destination"), getQueuePrefix() + getQueueName());
//         assertEquals(contents.length, receiveFrame.getBody().length());
//      }
//
//      // remove susbcription
//      ClientStompFrame unsubFrame = connV12.createFrame("UNSUBSCRIBE");
//      unsubFrame.addHeader("id", "a-sub");
//      connV12.sendFrame(unsubFrame);
//
//      connV12.disconnect();
//   }
//
//   //core sender -> large (compressed large) -> stomp v12 receiver
//   @TestTemplate
//   public void testReceiveLargeCompressedToLargePersistentMessagesFromCoreV12() throws Exception {
//      LargeMessageTestBase.TestLargeMessageInputStream input = new LargeMessageTestBase.TestLargeMessageInputStream(ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, true);
//      input.setSize(10 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);
//      LargeMessageTestBase.adjustLargeCompression(false, input, 10 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);
//
//      char[] contents = input.toArray();
//      String msg = new String(contents);
//
//      int count = 10;
//      for (int i = 0; i < count; i++) {
//         this.sendJmsMessage(msg);
//      }
//
//      IntegrationTestLogger.LOGGER.info("Message count for {}: {}", getQueueName(), server.getActiveMQServer().locateQueue(SimpleString.of(getQueueName())).getMessageCount());
//
//      StompClientConnection connV12 = StompClientConnectionFactory.createClientConnection("1.2", hostname, port);
//      connV12.connect(defUser, defPass);
//
//      ClientStompFrame subFrame = connV12.createFrame("SUBSCRIBE");
//      subFrame.addHeader("id", "a-sub");
//      subFrame.addHeader("subscription-type", "ANYCAST");
//      subFrame.addHeader("destination", getQueuePrefix() + getQueueName());
//      subFrame.addHeader("ack", "auto");
//
//      connV12.sendFrame(subFrame);
//
//      for (int i = 0; i < count; i++) {
//         ClientStompFrame receiveFrame = connV12.receiveFrame(30000);
//
//         Assert.assertNotNull(receiveFrame);
//         System.out.println("part of frame: " + receiveFrame.getBody().substring(0, 20));
//         Assert.assertTrue(receiveFrame.getCommand().equals("MESSAGE"));
//         Assert.assertEquals(receiveFrame.getHeader("destination"), getQueuePrefix() + getQueueName());
//         assertEquals(contents.length, receiveFrame.getBody().length());
//      }
//
//      // remove susbcription
//      ClientStompFrame unsubFrame = connV12.createFrame("UNSUBSCRIBE");
//      unsubFrame.addHeader("id", "a-sub");
//      connV12.sendFrame(unsubFrame);
//
//      connV12.disconnect();
//   }

   //core sender -> large (compressed large) -> stomp v10 receiver
   @TestTemplate
   public void testReceiveLargeCompressedToLargePersistentMessagesFromCore() throws Exception {

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      try {
         LargeMessageTestBase.TestLargeMessageInputStream input = new LargeMessageTestBase.TestLargeMessageInputStream(ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, true);
         input.setSize(10 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);
         LargeMessageTestBase.adjustLargeCompression(false, input, 10 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

         char[] contents = input.toArray();
         String msg = new String(contents);

         String leadingPart = msg.substring(0, 100);

         int count = 10;
         for (int i = 0; i < count; i++) {
            this.sendJmsMessage(msg);
         }

         conn.connect(defUser, defPass);

         ClientStompFrame subFrame = conn.createFrame("SUBSCRIBE");
         subFrame.addHeader("subscription-type", "ANYCAST");
         subFrame.addHeader("destination", getQueuePrefix() + getQueueName());
         subFrame.addHeader("ack", "auto");
         conn.sendFrame(subFrame);

         for (int i = 0; i < count; i++) {
            ClientStompFrame frame = conn.receiveFrame(60000);
            assertNotNull(frame);
            logger.debug(frame.toString());
            logger.debug("part of frame: {}", frame.getBody().substring(0, 250));
            assertEquals("MESSAGE", frame.getCommand());
            assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader("destination"));
            int index = frame.getBody().indexOf(leadingPart);
            assertEquals(msg.length(), (frame.getBody().length() - index));
         }

         ClientStompFrame unsubFrame = conn.createFrame("UNSUBSCRIBE");
         unsubFrame.addHeader("destination", getQueuePrefix() + getQueueName());
         unsubFrame.addHeader("receipt", "567");
         conn.sendFrame(unsubFrame);
      } finally {
         conn.disconnect();
         conn.closeTransport();
      }

   }
}
