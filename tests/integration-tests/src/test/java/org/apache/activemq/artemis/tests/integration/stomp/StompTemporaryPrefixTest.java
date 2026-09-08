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

import java.net.URI;
import java.util.Collection;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the temporaryAnycastPrefix and temporaryMulticastPrefix acceptor parameters.
 *
 * These prefixes allow STOMP clients to subscribe to destinations using a well-known prefix
 * (e.g. /temp-queue/ or /temp-topic/) to create temporary resources that are automatically
 * deleted when the client disconnects. This mirrors the behaviour of ActiveMQ 5.x.
 */
public class StompTemporaryPrefixTest extends StompTestBase {

   private static final String TEMP_QUEUE_PREFIX = "/temp-queue/";
   private static final String TEMP_TOPIC_PREFIX = "/temp-topic/";
   private static final int TEST_PORT = 61614;

   public StompTemporaryPrefixTest() {
      super("tcp+v10.stomp");
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      ActiveMQServer server = super.createServer();
      server.getConfiguration().setAddressQueueScanPeriod(100);
      return server;
   }

   // --- temporary queue (ANYCAST) tests ---

   @Test
   public void testTemporaryQueuePrefixSubscribeCreatesTemporaryAnycastQueue() throws Exception {
      URI uri = createStompClientUri(scheme, hostname, TEST_PORT);
      String address = UUID.randomUUID().toString();

      server.getRemotingService().createAcceptor("test",
         "tcp://" + hostname + ":" + TEST_PORT + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME
            + "&temporaryAnycastPrefix=" + TEMP_QUEUE_PREFIX).start();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      String receiptId = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, TEMP_QUEUE_PREFIX + address)
         .addHeader(Stomp.Headers.RECEIPT_REQUESTED, receiptId);
      frame = conn.sendFrame(frame);
      assertEquals(receiptId, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      org.apache.activemq.artemis.core.server.Queue queue = server.locateQueue(SimpleString.of(address));
      assertNotNull(queue, "Subscribing with temporaryAnycastPrefix should create an ANYCAST queue");
      assertTrue(queue.isTemporary(), "Queue created by temporaryAnycastPrefix should be temporary");
      assertEquals(RoutingType.ANYCAST, queue.getRoutingType());

      conn.disconnect();
   }

   @Test
   public void testTemporaryQueueDeletedOnDisconnect() throws Exception {
      URI uri = createStompClientUri(scheme, hostname, TEST_PORT);
      String address = UUID.randomUUID().toString();

      server.getRemotingService().createAcceptor("test",
         "tcp://" + hostname + ":" + TEST_PORT + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME
            + "&temporaryAnycastPrefix=" + TEMP_QUEUE_PREFIX).start();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      String receiptId = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, TEMP_QUEUE_PREFIX + address)
         .addHeader(Stomp.Headers.RECEIPT_REQUESTED, receiptId);
      frame = conn.sendFrame(frame);
      assertEquals(receiptId, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));
      assertNotNull(server.locateQueue(SimpleString.of(address)));

      conn.disconnect();

      Wait.assertTrue("Temporary ANYCAST queue should be deleted after client disconnects",
         () -> server.locateQueue(SimpleString.of(address)) == null);
   }

   @Test
   public void testSendReceiveViaTemporaryQueue() throws Exception {
      URI uri = createStompClientUri(scheme, hostname, TEST_PORT);
      String address = UUID.randomUUID().toString();

      server.getRemotingService().createAcceptor("test",
         "tcp://" + hostname + ":" + TEST_PORT + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME
            + "&temporaryAnycastPrefix=" + TEMP_QUEUE_PREFIX).start();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      // Subscribe first so the temp queue exists before sending
      String receiptId = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, TEMP_QUEUE_PREFIX + address)
         .addHeader(Stomp.Headers.Subscribe.ID, "sub-1")
         .addHeader(Stomp.Headers.RECEIPT_REQUESTED, receiptId);
      frame = conn.sendFrame(frame);
      assertEquals(receiptId, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      send(conn, TEMP_QUEUE_PREFIX + address, null, "Hello Temp Queue", true);

      frame = conn.receiveFrame(5000);
      assertNotNull(frame, "Should have received a message on the temporary queue");
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Hello Temp Queue", frame.getBody());

      conn.disconnect();
   }

   // --- temporary topic (MULTICAST) tests ---

   @Test
   public void testTemporaryTopicPrefixSubscribeCreatesTemporaryMulticastQueue() throws Exception {
      URI uri = createStompClientUri(scheme, hostname, TEST_PORT);
      String address = UUID.randomUUID().toString();

      server.getRemotingService().createAcceptor("test",
         "tcp://" + hostname + ":" + TEST_PORT + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME
            + "&temporaryMulticastPrefix=" + TEMP_TOPIC_PREFIX).start();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      String receiptId = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, TEMP_TOPIC_PREFIX + address)
         .addHeader(Stomp.Headers.RECEIPT_REQUESTED, receiptId);
      frame = conn.sendFrame(frame);
      assertEquals(receiptId, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      AddressInfo addressInfo = server.getAddressInfo(SimpleString.of(address));
      assertNotNull(addressInfo, "Subscribing with temporaryMulticastPrefix should create an address");
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST),
         "Address created by temporaryMulticastPrefix should support MULTICAST routing");

      // A temporary MULTICAST queue (with a UUID name) should be bound to the address
      Collection<Binding> bindings = server.getPostOffice().getDirectBindings(SimpleString.of(address));
      long tempMulticastQueueCount = bindings.stream()
         .filter(b -> b instanceof QueueBinding)
         .map(b -> ((QueueBinding) b).getQueue())
         .filter(q -> q.isTemporary() && q.getRoutingType() == RoutingType.MULTICAST)
         .count();
      assertEquals(1, tempMulticastQueueCount,
         "Exactly one temporary MULTICAST queue should be bound to the address");

      conn.disconnect();
   }

   @Test
   public void testTemporaryTopicDeletedOnDisconnect() throws Exception {
      URI uri = createStompClientUri(scheme, hostname, TEST_PORT);
      String address = UUID.randomUUID().toString();

      server.getRemotingService().createAcceptor("test",
         "tcp://" + hostname + ":" + TEST_PORT + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME
            + "&temporaryMulticastPrefix=" + TEMP_TOPIC_PREFIX).start();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      String receiptId = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, TEMP_TOPIC_PREFIX + address)
         .addHeader(Stomp.Headers.RECEIPT_REQUESTED, receiptId);
      frame = conn.sendFrame(frame);
      assertEquals(receiptId, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));
      assertNotNull(server.getAddressInfo(SimpleString.of(address)));

      conn.disconnect();

      Wait.assertTrue("Temporary MULTICAST address should be deleted after client disconnects",
         () -> server.getAddressInfo(SimpleString.of(address)) == null);
   }

   @Test
   public void testSendReceiveViaTemporaryTopic() throws Exception {
      URI uri = createStompClientUri(scheme, hostname, TEST_PORT);
      String address = UUID.randomUUID().toString();

      server.getRemotingService().createAcceptor("test",
         "tcp://" + hostname + ":" + TEST_PORT + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME
            + "&temporaryMulticastPrefix=" + TEMP_TOPIC_PREFIX).start();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      // Subscribe first so the temp queue exists before sending
      String receiptId = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, TEMP_TOPIC_PREFIX + address)
         .addHeader(Stomp.Headers.Subscribe.ID, "sub-1")
         .addHeader(Stomp.Headers.RECEIPT_REQUESTED, receiptId);
      frame = conn.sendFrame(frame);
      assertEquals(receiptId, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      send(conn, TEMP_TOPIC_PREFIX + address, null, "Hello Temp Topic", true);

      frame = conn.receiveFrame(5000);
      assertNotNull(frame, "Should have received a message on the temporary topic");
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Hello Temp Topic", frame.getBody());

      conn.disconnect();
   }

   // --- combined prefix tests ---

   @Test
   public void testBothTemporaryPrefixesOnSameAcceptor() throws Exception {
      URI uri = createStompClientUri(scheme, hostname, TEST_PORT);
      String queueAddress = UUID.randomUUID().toString();
      String topicAddress = UUID.randomUUID().toString();

      server.getRemotingService().createAcceptor("test",
         "tcp://" + hostname + ":" + TEST_PORT + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME
            + "&temporaryAnycastPrefix=" + TEMP_QUEUE_PREFIX
            + "&temporaryMulticastPrefix=" + TEMP_TOPIC_PREFIX).start();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      // Subscribe to a temp-queue destination
      String receiptId1 = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, TEMP_QUEUE_PREFIX + queueAddress)
         .addHeader(Stomp.Headers.Subscribe.ID, "sub-queue")
         .addHeader(Stomp.Headers.RECEIPT_REQUESTED, receiptId1);
      frame = conn.sendFrame(frame);
      assertEquals(receiptId1, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      org.apache.activemq.artemis.core.server.Queue queue = server.locateQueue(SimpleString.of(queueAddress));
      assertNotNull(queue, "Temp ANYCAST queue should be created");
      assertTrue(queue.isTemporary());
      assertEquals(RoutingType.ANYCAST, queue.getRoutingType());

      // Subscribe to a temp-topic destination
      String receiptId2 = UUID.randomUUID().toString();
      frame = conn.createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, TEMP_TOPIC_PREFIX + topicAddress)
         .addHeader(Stomp.Headers.Subscribe.ID, "sub-topic")
         .addHeader(Stomp.Headers.RECEIPT_REQUESTED, receiptId2);
      frame = conn.sendFrame(frame);
      assertEquals(receiptId2, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID));

      AddressInfo addressInfo = server.getAddressInfo(SimpleString.of(topicAddress));
      assertNotNull(addressInfo, "Temp MULTICAST address should be created");
      assertTrue(addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));

      conn.disconnect();

      Wait.assertTrue("Temp ANYCAST queue should be deleted after disconnect",
         () -> server.locateQueue(SimpleString.of(queueAddress)) == null);
      Wait.assertTrue("Temp MULTICAST address should be deleted after disconnect",
         () -> server.getAddressInfo(SimpleString.of(topicAddress)) == null);
   }

   @Test
   public void testNonTempSubscriptionStillWorksWithTemporaryPrefixConfigured() throws Exception {
      URI uri = createStompClientUri(scheme, hostname, TEST_PORT);

      server.getRemotingService().createAcceptor("test",
         "tcp://" + hostname + ":" + TEST_PORT + "?protocols=" + StompProtocolManagerFactory.STOMP_PROTOCOL_NAME
            + "&temporaryAnycastPrefix=" + TEMP_QUEUE_PREFIX
            + "&anycastPrefix=/queue/").start();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      // Subscribe to the regular pre-existing anycast queue using the anycast prefix
      String receiptId = UUID.randomUUID().toString();
      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE)
         .addHeader(Stomp.Headers.Subscribe.DESTINATION, "/queue/" + getQueueName())
         .addHeader(Stomp.Headers.Subscribe.ID, "sub-regular")
         .addHeader(Stomp.Headers.RECEIPT_REQUESTED, receiptId);
      frame = conn.sendFrame(frame);
      assertEquals(receiptId, frame.getHeader(Stomp.Headers.Response.RECEIPT_ID),
         "Regular subscription with anycastPrefix should still work alongside temporaryAnycastPrefix");

      send(conn, "/queue/" + getQueueName(), null, "Hello Regular", true);

      frame = conn.receiveFrame(5000);
      assertNotNull(frame);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());
      assertEquals("Hello Regular", frame.getBody());

      // Verify the regular queue was NOT created as temporary
      org.apache.activemq.artemis.core.server.Queue queue = server.locateQueue(SimpleString.of(getQueueName()));
      assertNotNull(queue);
      assertFalse(queue.isTemporary(), "Regular queue should not be temporary");

      conn.disconnect();
   }
}
