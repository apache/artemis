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

import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StompWebSocketMaxFrameTest extends StompTestBase {

   private final int wsPortWithStompMaxFrame = 61614;
   private final int wsPortWithWebSocketMaxFrame = 61615;
   private final int wsPortWithBothMaxFrameAndWorks = 61619;
   private final int wsPortWithBothMaxFrameButFails = 61620;
   private URI wsURIForStompMaxFrame;
   private URI wsURIForWebSocketMaxFrame;
   private URI wsURIForBothMaxFrameAndWorks;
   private URI wsURIForBothMaxFrameButFails;

   private final int stompWSMaxFrameSize = 131072; // 128kb

   public StompWebSocketMaxFrameTest() {
      super("ws+v10.stomp");
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      server.getRemotingService().createAcceptor("test1", "tcp://127.0.0.1:" + wsPortWithStompMaxFrame + "?stompMaxFramePayloadLength=" + stompWSMaxFrameSize).start();
      server.getRemotingService().createAcceptor("test2", "tcp://127.0.0.1:" + wsPortWithWebSocketMaxFrame + "?webSocketMaxFramePayloadLength=" + stompWSMaxFrameSize).start();
      server.getRemotingService().createAcceptor("test3", "tcp://127.0.0.1:" + wsPortWithBothMaxFrameAndWorks + "?stompMaxFramePayloadLength=" + stompWSMaxFrameSize / 2 + ";webSocketMaxFramePayloadLength=" + stompWSMaxFrameSize).start();
      server.getRemotingService().createAcceptor("test4", "tcp://127.0.0.1:" + wsPortWithBothMaxFrameButFails + "?stompMaxFramePayloadLength=" + stompWSMaxFrameSize + ";webSocketMaxFramePayloadLength=" + stompWSMaxFrameSize / 2).start();

      wsURIForStompMaxFrame = createStompClientUri(scheme, hostname, wsPortWithStompMaxFrame);
      wsURIForWebSocketMaxFrame = createStompClientUri(scheme, hostname, wsPortWithWebSocketMaxFrame);
      wsURIForBothMaxFrameAndWorks = createStompClientUri(scheme, hostname, wsPortWithBothMaxFrameAndWorks);
      wsURIForBothMaxFrameButFails = createStompClientUri(scheme, hostname, wsPortWithBothMaxFrameButFails);
   }

   @Test
   public void testStompSendReceiveWithMaxFramePayloadLength() throws Exception {
      // Assert that sending message > default 64kb fails
      int size = 65536;
      String largeString1 = RandomStringUtils.randomAlphabetic(size);
      String largeString2 = RandomStringUtils.randomAlphabetic(size);

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri, false);
      conn.getTransport().setMaxFrameSize(stompWSMaxFrameSize);
      conn.getTransport().connect();

      StompClientConnection conn2 = StompClientConnectionFactory.createClientConnection(wsURIForStompMaxFrame, false);
      conn2.getTransport().setMaxFrameSize(stompWSMaxFrameSize);
      conn2.getTransport().connect();

      StompClientConnection conn3 = StompClientConnectionFactory.createClientConnection(wsURIForWebSocketMaxFrame, false);
      conn3.getTransport().setMaxFrameSize(stompWSMaxFrameSize);
      conn3.getTransport().connect();

      StompClientConnection conn4 = StompClientConnectionFactory.createClientConnection(wsURIForBothMaxFrameAndWorks, false);
      conn4.getTransport().setMaxFrameSize(stompWSMaxFrameSize);
      conn4.getTransport().connect();

      StompClientConnection conn5 = StompClientConnectionFactory.createClientConnection(wsURIForBothMaxFrameButFails, false);
      conn5.getTransport().setMaxFrameSize(stompWSMaxFrameSize);
      conn5.getTransport().connect();

      Wait.waitFor(() -> conn5.getTransport().isConnected() && conn4.getTransport().isConnected() && conn3.getTransport().isConnected() && conn2.getTransport().isConnected() && conn.getTransport().isConnected(), 10000);
      conn.connect();
      conn2.connect();
      conn3.connect();
      conn4.connect();
      conn5.connect();

      subscribeQueue(conn2, "sub1", getQueuePrefix() + getQueueName());
      subscribeQueue(conn3, "sub2", getQueuePrefix() + getQueueName());
      subscribeQueue(conn4, "sub3", getQueuePrefix() + getQueueName());

      try {
         // Client is kicked when sending frame > largest frame size. Default 64kb
         send(conn, getQueuePrefix() + getQueueName(), "text/plain", largeString1, false);
         Wait.waitFor(() -> !conn.getTransport().isConnected(), 2000);
         assertFalse(conn.getTransport().isConnected());

         // Fails because webSocketMaxFramePayloadLength is only configured for 64kb.
         send(conn5, getQueuePrefix() + getQueueName(), "text/plain", largeString1, false);
         Wait.waitFor(() -> !conn5.getTransport().isConnected(), 2000);
         assertFalse(conn5.getTransport().isConnected());

         send(conn2, getQueuePrefix() + getQueueName(), "text/plain", largeString2, false);
         Wait.waitFor(() -> !conn2.getTransport().isConnected(), 2000);
         assertTrue(conn2.getTransport().isConnected());

         send(conn3, getQueuePrefix() + getQueueName(), "text/plain", largeString2, false);
         Wait.waitFor(() -> !conn3.getTransport().isConnected(), 2000);
         assertTrue(conn3.getTransport().isConnected());

         send(conn4, getQueuePrefix() + getQueueName(), "text/plain", largeString2, false);
         Wait.waitFor(() -> !conn4.getTransport().isConnected(), 2000);
         assertTrue(conn4.getTransport().isConnected());

         ClientStompFrame frame2 = conn2.receiveFrame();
         assertNotNull(frame2);
         assertEquals(largeString2, frame2.getBody());

         ClientStompFrame frame3 = conn3.receiveFrame();
         assertNotNull(frame3);
         assertEquals(largeString2, frame3.getBody());

         ClientStompFrame frame4 = conn4.receiveFrame();
         assertNotNull(frame4);
         assertEquals(largeString2, frame4.getBody());
      } finally {
         conn5.closeTransport();
         conn4.closeTransport();
         conn3.closeTransport();
         conn2.closeTransport();
         conn.closeTransport();
      }
   }
}
