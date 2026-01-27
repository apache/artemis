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
package org.apache.activemq.artemis.core.management.impl.view;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ConsumerViewTest extends ViewTest {

   private static final String MOCK_VALIDATED_USER = "myValidatedUser";
   private static final String MOCK_USER = "myUser";
   private static final String MOCK_PROTOCOL = "myProtocol";
   private static final String MOCK_CLIENT_ID = "myClientId";
   private static final String MOCK_LOCAL_ADDRESS = "myLocalAddress";
   private static final String MOCK_REMOTE_ADDRESS = "myRemoteAddress";

   @Test
   public void testDefaultViewNullOptions() {
      ConsumerView consumerView = new ConsumerView(Mockito.mock(ActiveMQServer.class));
      // sanity check to ensure this doesn't just blow up
      consumerView.setOptions(null);
   }

   @Test
   public void testDefaultViewEmptyOptions() {
      ConsumerView consumerView = new ConsumerView(Mockito.mock(ActiveMQServer.class));
      // sanity check to ensure this doesn't just blow up
      consumerView.setOptions("");
   }

   @Test
   public void testViewLegacySort() {
      ConsumerView view = new ConsumerView(Mockito.mock(ActiveMQServer.class));
      assertNotEquals("user", view.getDefaultOrderColumn());
      view.setOptions(createLegacyJsonFilter("user", "EQUALS", "123", "user", "asc"));
      assertEquals("user", view.getSortField());
   }

   @Test
   public void testFilterById() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.ID.getName(),
                 sc -> Mockito.when(sc.getSequentialID()).thenReturn(123L),
                 sc -> Mockito.when(sc.getSequentialID()).thenReturn(456L),
                 "123");
   }

   @Test
   public void testFilterBySessionId() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.SESSION.getName(),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("123"),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("456"),
                 "123");
   }

   @Test
   public void testFilterByUser() {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      createMockSessionInServer(server, "123", false);
      createMockSessionInServer(server, "456", true);

      testFilter(server,
                 ConsumerField.USER.getName(),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("123"),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("456"),
                 MOCK_USER);
   }

   @Test
   public void testFilterByValidatedUser() {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      createMockSessionInServer(server, "123", false);
      createMockSessionInServer(server, "456", true);

      testFilter(server,
                 ConsumerField.VALIDATED_USER.getName(),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("123"),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("456"),
                 MOCK_VALIDATED_USER);
   }

   @Test
   public void testFilterByAddress() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.ADDRESS.getName(),
                 sc -> {
                    Queue queue = Mockito.mock(Queue.class);
                    Mockito.when(queue.getAddress()).thenReturn(SimpleString.of("123"));
                    Mockito.when(sc.getQueue()).thenReturn(queue);
                 },
                 sc -> {
                    Queue queue = Mockito.mock(Queue.class);
                    Mockito.when(queue.getAddress()).thenReturn(SimpleString.of("456"));
                    Mockito.when(sc.getQueue()).thenReturn(queue);
                 },
                 "123");
   }

   @Test
   public void testFilterByQueue() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.QUEUE.getName(),
                 sc -> {
                    Queue queue = Mockito.mock(Queue.class);
                    Mockito.when(queue.getName()).thenReturn(SimpleString.of("123"));
                    Mockito.when(sc.getQueue()).thenReturn(queue);
                 },
                 sc -> {
                    Queue queue = Mockito.mock(Queue.class);
                    Mockito.when(queue.getName()).thenReturn(SimpleString.of("456"));
                    Mockito.when(sc.getQueue()).thenReturn(queue);
                 },
                 "123");
   }

   @Test
   public void testFilterByFilterString() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.FILTER.getName(),
                 sc -> Mockito.when(sc.getFilterString()).thenReturn(SimpleString.of("123")),
                 sc -> Mockito.when(sc.getFilterString()).thenReturn(SimpleString.of("456")),
                 "123");
   }

   @Test
   public void testFilterByProtocol() {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      createMockSessionInServer(server, "123", false);
      createMockSessionInServer(server, "456", true);

      testFilter(server,
                 ConsumerField.PROTOCOL.getName(),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("123"),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("456"),
                 MOCK_PROTOCOL);
   }

   @Test
   public void testFilterByClientId() {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      createMockSessionInServer(server, "123", false);
      createMockSessionInServer(server, "456", true);

      testFilter(server,
                 ConsumerField.CLIENT_ID.getName(),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("123"),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("456"),
                 MOCK_CLIENT_ID);
   }

   @Test
   public void testFilterByLocalAddress() {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      createMockSessionInServer(server, "123", false);
      createMockSessionInServer(server, "456", true);

      testFilter(server,
                 ConsumerField.LOCAL_ADDRESS.getName(),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("123"),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("456"),
                 MOCK_LOCAL_ADDRESS);
   }

   @Test
   public void testFilterByRemoteAddress() {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);

      createMockSessionInServer(server, "123", false);
      createMockSessionInServer(server, "456", true);

      testFilter(server,
                 ConsumerField.REMOTE_ADDRESS.getName(),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("123"),
                 sc -> Mockito.when(sc.getSessionID()).thenReturn("456"),
                 MOCK_REMOTE_ADDRESS);
   }

   @Test
   public void testFilterByMessagesInTransit() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.MESSAGES_IN_TRANSIT.getName(),
                 sc -> Mockito.when(sc.getMessagesInTransit()).thenReturn(123),
                 sc -> Mockito.when(sc.getMessagesInTransit()).thenReturn(456),
                 "123");
   }

   @Test
   public void testFilterByMessagesInTransitSize() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName(),
                 sc -> Mockito.when(sc.getMessagesInTransitSize()).thenReturn(123L),
                 sc -> Mockito.when(sc.getMessagesInTransitSize()).thenReturn(456L),
                 "123");
   }

   @Test
   public void testFilterByMessagesDelivered() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.MESSAGES_DELIVERED.getName(),
                 sc -> Mockito.when(sc.getMessagesDelivered()).thenReturn(123L),
                 sc -> Mockito.when(sc.getMessagesDelivered()).thenReturn(456L),
                 "123");
   }

   @Test
   public void testFilterByMessagesDeliveredSize() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.MESSAGES_DELIVERED_SIZE.getName(),
                 sc -> Mockito.when(sc.getMessagesDeliveredSize()).thenReturn(123L),
                 sc -> Mockito.when(sc.getMessagesDeliveredSize()).thenReturn(456L),
                 "123");
   }

   @Test
   public void testFilterByMessagesAcknowledged() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.MESSAGES_ACKNOWLEDGED.getName(),
                 sc -> Mockito.when(sc.getMessagesAcknowledged()).thenReturn(123L),
                 sc -> Mockito.when(sc.getMessagesAcknowledged()).thenReturn(456L),
                 "123");
   }

   @Test
   public void testFilterByMessagesAcknowledgedAwaitingCommit() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName(),
                 sc -> Mockito.when(sc.getMessagesAcknowledgedAwaitingCommit()).thenReturn(123),
                 sc -> Mockito.when(sc.getMessagesAcknowledgedAwaitingCommit()).thenReturn(456),
                 "123");
   }

   @Test
   public void testFilterByLastAcknowledgedTime() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.LAST_ACKNOWLEDGED_TIME.getName(),
                 sc -> Mockito.when(sc.getLastAcknowledgedTime()).thenReturn(123L),
                 sc -> Mockito.when(sc.getLastAcknowledgedTime()).thenReturn(456L),
                 "123");
   }

   @Test
   public void testFilterByLastDeliveredTime() {
      testFilter(Mockito.mock(ActiveMQServer.class),
                 ConsumerField.LAST_DELIVERED_TIME.getName(),
                 sc -> Mockito.when(sc.getLastDeliveredTime()).thenReturn(123L),
                 sc -> Mockito.when(sc.getLastDeliveredTime()).thenReturn(456L),
                 "123");
   }

   private void testFilter(ActiveMQServer server, String fieldName, java.util.function.Consumer<ServerConsumer> mockMatchingConsumer, java.util.function.Consumer<ServerConsumer> mockNonMatchingConsumer, String match) {
      Collection<ServerConsumer> consumers = new ArrayList<>();

      ServerConsumer matchingConsumer = Mockito.mock(ServerConsumerImpl.class);
      mockMatchingConsumer.accept(matchingConsumer);
      consumers.add(matchingConsumer);

      ServerConsumer nonMatchingConsumer = Mockito.mock(ServerConsumerImpl.class);
      mockNonMatchingConsumer.accept(nonMatchingConsumer);
      consumers.add(nonMatchingConsumer);

      ConsumerView consumerView = new ConsumerView(server);
      consumerView.setOptions(createJsonFilter(fieldName, "EQUALS", match));
      consumerView.setCollection(consumers);
      assertEquals(1, JsonUtil.readJsonObject(consumerView.getResultsAsJson(-1, -1)).getInt("count"));

      // test again with no options to verify both sessions are returned
      consumerView = new ConsumerView(server);
      consumerView.setOptions("");
      consumerView.setCollection(consumers);
      assertEquals(2, JsonUtil.readJsonObject(consumerView.getResultsAsJson(-1, -1)).getInt("count"));
   }

   /**
    * Creates and configures the behavior of the mock objects to simulate a {@code ServerSession} managed by the
    * {@code ActiveMQServer}.
    * <p>
    * This method consolidates all the code for the mock objects so that it's not spread out and duplicated in the
    * individual tests.
    *
    * @param server          The ActiveMQServer instance where the session should be mocked.
    * @param sessionId       The identifier of the session to be mocked.
    * @param useRandomValues Indicates whether random values should be used for the mocked attributes.
    */
   private void createMockSessionInServer(ActiveMQServer server, String sessionId, boolean useRandomValues) {
      ServerSession session = Mockito.mock(ServerSession.class);
      RemotingConnection remotingConnection = Mockito.mock(RemotingConnection.class);
      Connection transportConnection = Mockito.mock(Connection.class);

      Mockito.when(server.getSessionByID(sessionId)).thenReturn(session);

      Mockito.when(session.getValidatedUser()).thenReturn(useRandomValues ? RandomUtil.randomUUIDString() : MOCK_VALIDATED_USER);
      Mockito.when(session.getUsername()).thenReturn(useRandomValues ? RandomUtil.randomUUIDString() : MOCK_USER);
      Mockito.when(session.getRemotingConnection()).thenReturn(remotingConnection);

      Mockito.when(remotingConnection.getProtocolName()).thenReturn(useRandomValues ? RandomUtil.randomUUIDString() : MOCK_PROTOCOL);
      Mockito.when(remotingConnection.getClientID()).thenReturn(useRandomValues ? RandomUtil.randomUUIDString() : MOCK_CLIENT_ID);
      Mockito.when(remotingConnection.getTransportConnection()).thenReturn(transportConnection);

      Mockito.when(transportConnection.getLocalAddress()).thenReturn(useRandomValues ? RandomUtil.randomUUIDString() : MOCK_LOCAL_ADDRESS);
      Mockito.when(transportConnection.getRemoteAddress()).thenReturn(useRandomValues ? RandomUtil.randomUUIDString() : MOCK_REMOTE_ADDRESS);
   }
}
