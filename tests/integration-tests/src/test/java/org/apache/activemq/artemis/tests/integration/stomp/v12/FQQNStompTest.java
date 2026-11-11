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
package org.apache.activemq.artemis.tests.integration.stomp.v12;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.tests.integration.stomp.StompTestBase;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FQQNStompTest extends StompTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private StompClientConnection conn;

   public FQQNStompTest() {
      super("tcp+v12.stomp");
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      conn = StompClientConnectionFactory.createClientConnection(uri);
      QueueQueryResult result = server.queueQuery(SimpleString.of(getQueueName()));
      assertTrue(result.isExists());
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         boolean connected = conn != null && conn.isConnected();
         if (connected) {
            try {
               conn.disconnect();
            } catch (Exception e) {
            }
         }
      } finally {
         conn.closeTransport();
         super.tearDown();
      }
   }

   @Test
   //to receive from a FQQN queue like testQueue::testQueue
   //special care is needed as ":" is a reserved character
   //in STOMP. Clients need to escape it.
   public void testReceiveFQQN() throws Exception {
      conn.connect(defUser, defPass);
      subscribeQueue(conn, "sub-01", getQueueName() + "\\c\\c" + getQueueName());
      sendJmsMessage("Hello World!");
      ClientStompFrame frame = conn.receiveFrame(2000);
      assertNotNull(frame);
      assertEquals("Hello World!", frame.getBody());
      logger.debug("frame: {}", frame);
      unsubscribe(conn, "sub-01");
   }

   @Test
   public void testReceiveFQQN2() throws Exception {
      final SimpleString myAddress = SimpleString.of("myAddress");
      final SimpleString q1Name = SimpleString.of("q1");
      final SimpleString q2Name = SimpleString.of("q2");

      Queue q1 = server.createQueue(QueueConfiguration.of(q1Name).setAddress(myAddress));
      Queue q2 = server.createQueue(QueueConfiguration.of(q2Name).setAddress(myAddress));

      sendJmsMessage("Hello World!", ActiveMQJMSClient.createTopic(myAddress.toString()));
      assertTrue(Wait.waitFor(() -> q1.getMessageCount() == 1, 2000, 100));
      assertTrue(Wait.waitFor(() -> q2.getMessageCount() == 1, 2000, 100));

      conn.connect(defUser, defPass);
      subscribeQueue(conn, "sub-01", myAddress + "\\c\\c" + q1Name);
      ClientStompFrame frame = conn.receiveFrame(2000);
      assertNotNull(frame);
      assertEquals("Hello World!", frame.getBody());
      assertTrue(Wait.waitFor(() -> q1.getMessageCount() == 0, 2000, 100));
      assertTrue(Wait.waitFor(() -> q2.getMessageCount() == 1, 2000, 100));

      unsubscribe(conn, "sub-01");
   }

   @Test
   public void testSendFQQNMulticast() throws Exception {
      final SimpleString myAddress = SimpleString.of("myAddress");
      final SimpleString q1Name = SimpleString.of("q1");
      final SimpleString q2Name = SimpleString.of("q2");

      Queue q1 = server.createQueue(QueueConfiguration.of(q1Name).setAddress(myAddress));
      Queue q2 = server.createQueue(QueueConfiguration.of(q2Name).setAddress(myAddress));

      conn.connect(defUser, defPass);
      send(conn, myAddress + "\\c\\c" + q1Name, null, "Hello World!");

      assertTrue(Wait.waitFor(() -> q1.getMessageCount() == 1, 2000, 100));
      assertTrue(Wait.waitFor(() -> q2.getMessageCount() == 0, 2000, 100));

      subscribeQueue(conn, "sub-01", myAddress + "\\c\\c" + q1Name);
      ClientStompFrame frame = conn.receiveFrame(2000);
      assertNotNull(frame);
      assertEquals("Hello World!", frame.getBody());
      assertTrue(Wait.waitFor(() -> q1.getMessageCount() == 0, 2000, 100));
      assertTrue(Wait.waitFor(() -> q2.getMessageCount() == 0, 2000, 100));

      unsubscribe(conn, "sub-01");
   }

   @Test
   public void testSendFQQNAnycast() throws Exception {
      final SimpleString myAddress = SimpleString.of("myAddress");
      final SimpleString q1Name = SimpleString.of("q1");
      final SimpleString q2Name = SimpleString.of("q2");

      Queue q1 = server.createQueue(QueueConfiguration.of(q1Name).setAddress(myAddress).setRoutingType(RoutingType.ANYCAST));
      Queue q2 = server.createQueue(QueueConfiguration.of(q2Name).setAddress(myAddress).setRoutingType(RoutingType.ANYCAST));

      conn.connect(defUser, defPass);
      send(conn, myAddress.toString(), null, "Hello World!", false, RoutingType.ANYCAST);
      assertTrue(Wait.waitFor(() -> q1.getMessageCount() == 1, 2000, 100));
      send(conn, myAddress.toString(), null, "Hello World!", false, RoutingType.ANYCAST);
      assertTrue(Wait.waitFor(() -> q2.getMessageCount() == 1, 2000, 100));

      send(conn, myAddress + "\\c\\c" + q1Name, null, "Hello World!", false, RoutingType.ANYCAST);
      assertTrue(Wait.waitFor(() -> q1.getMessageCount() == 2, 2000, 100));
      assertTrue(Wait.waitFor(() -> q2.getMessageCount() == 1, 2000, 100));

      send(conn, myAddress + "\\c\\c" + q1Name, null, "Hello World!", false, RoutingType.ANYCAST);
      assertTrue(Wait.waitFor(() -> q1.getMessageCount() == 3, 2000, 100));
      assertTrue(Wait.waitFor(() -> q2.getMessageCount() == 1, 2000, 100));

      subscribeQueue(conn, "sub-01", myAddress + "\\c\\c" + q1Name);
      ClientStompFrame frame = conn.receiveFrame(2000);
      assertNotNull(frame);
      assertEquals("Hello World!", frame.getBody());
      frame = conn.receiveFrame(2000);
      assertNotNull(frame);
      assertEquals("Hello World!", frame.getBody());
      frame = conn.receiveFrame(2000);
      assertNotNull(frame);
      assertEquals("Hello World!", frame.getBody());
      assertTrue(Wait.waitFor(() -> q1.getMessageCount() == 0, 2000, 100));
      assertTrue(Wait.waitFor(() -> q2.getMessageCount() == 1, 2000, 100));

      unsubscribe(conn, "sub-01");
   }

   @Test
   public void testReceiveFQQNSpecial() throws Exception {
      conn.connect(defUser, defPass);
      //::queue
      subscribeQueue(conn, "sub-01", "\\c\\c" + getQueueName());
      sendJmsMessage("Hello World!");
      ClientStompFrame frame = conn.receiveFrame(2000);
      assertNotNull(frame);
      assertEquals("Hello World!", frame.getBody());
      logger.debug("frame: {}", frame);
      unsubscribe(conn, "sub-01");

      //queue::
      frame = subscribeQueue(conn, "sub-01", getQueueName() + "\\c\\c");
      assertNotNull(frame);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());
      conn.closeTransport();

      //need reconnect because stomp disconnect on error
      conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      //:: will subscribe to no queue so no message received.
      frame = subscribeQueue(conn, "sub-01", "\\c\\c");
      assertNotNull(frame);
      assertEquals(Stomp.Responses.ERROR, frame.getCommand());
   }

   @Test
   public void testAutoCreateOnSendFQQN() throws Exception {
      final SimpleString myAddress = SimpleString.of("myAddress");
      final SimpleString q1Name = SimpleString.of("q1");

      conn.connect(defUser, defPass);
      send(conn, myAddress + "\\c\\c" + q1Name, null, "Hello World!");

      assertTrue(Wait.waitFor(() -> server.locateQueue(q1Name) != null, 2000, 100));
      assertTrue(Wait.waitFor(() -> server.locateQueue(q1Name).getMessageCount() == 1, 2000, 100));

      subscribeQueue(conn, "sub-01", myAddress + "\\c\\c" + q1Name);
      ClientStompFrame frame = conn.receiveFrame(2000);
      assertNotNull(frame);
      assertEquals("Hello World!", frame.getBody());
      assertTrue(Wait.waitFor(() -> server.locateQueue(q1Name).getMessageCount() == 0, 2000, 100));

      unsubscribe(conn, "sub-01");
   }

   @Test
   public void testAutoCreateOnSubscribeFQQNAnycast() throws Exception {
      internalTestAutoCreateOnSubscribeFQQN(RoutingType.ANYCAST);
   }

   @Test
   public void testAutoCreateOnSubscribeFQQNMulticast() throws Exception {
      internalTestAutoCreateOnSubscribeFQQN(RoutingType.MULTICAST);
   }

   @Test
   public void testAutoCreateOnSubscribeFQQNNoRoutingType() throws Exception {
      internalTestAutoCreateOnSubscribeFQQN(null);
   }

   private void internalTestAutoCreateOnSubscribeFQQN(RoutingType routingType) throws Exception {
      final SimpleString myAddress = SimpleString.of("myAddress");
      final SimpleString q1Name = SimpleString.of("q1");
      final SimpleString q2Name = SimpleString.of("q2");

      StompClientConnection consumer1Connection = StompClientConnectionFactory.createClientConnection(uri);
      consumer1Connection.connect(defUser, defPass);

      ClientStompFrame frame = consumer1Connection
         .createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, myAddress + "\\c\\c" + q1Name)
         .addHeader(Stomp.Headers.Subscribe.ID, "sub-01")
         .addHeader(Stomp.Headers.Subscribe.ACK_MODE, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      if (routingType != null) {
         frame.addHeader(Stomp.Headers.Subscribe.SUBSCRIPTION_TYPE, routingType.toString());
      }

      consumer1Connection.sendFrame(frame);

      assertTrue(Wait.waitFor(() -> server.locateQueue(q1Name) != null, 2000, 100));

      StompClientConnection consumer2Connection = StompClientConnectionFactory.createClientConnection(uri);
      consumer2Connection.connect(defUser, defPass);

      frame = consumer2Connection
         .createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, myAddress + "\\c\\c" + q2Name)
         .addHeader(Stomp.Headers.Subscribe.ID, "sub-02")
         .addHeader(Stomp.Headers.Subscribe.ACK_MODE, Stomp.Headers.Subscribe.AckModeValues.AUTO);

      if (routingType != null) {
         frame.addHeader(Stomp.Headers.Subscribe.SUBSCRIPTION_TYPE, routingType.toString());
      }

      consumer2Connection.sendFrame(frame);

      assertTrue(Wait.waitFor(() -> server.locateQueue(q1Name) != null, 2000, 100));
      assertTrue(Wait.waitFor(() -> server.locateQueue(q2Name) != null, 2000, 100));

      StompClientConnection senderConnection = StompClientConnectionFactory.createClientConnection(uri);
      senderConnection.connect(defUser, defPass);
      send(senderConnection, myAddress + "\\c\\c" + q1Name, null, "Hello World!", false, routingType);

      assertTrue(Wait.waitFor(() -> server.locateQueue(q1Name).getMessagesAdded() == 1, 2000, 100));
      assertTrue(Wait.waitFor(() -> server.locateQueue(q2Name).getMessagesAdded() == 0, 2000, 100));

      frame = consumer1Connection.receiveFrame(2000);
      assertNotNull(frame);
      assertEquals("Hello World!", frame.getBody());
      assertTrue(Wait.waitFor(() -> server.locateQueue(q1Name).getMessageCount() == 0, 4000, 100));

      unsubscribe(consumer1Connection, "sub-01");
      unsubscribe(consumer2Connection, "sub-02");
   }

}
