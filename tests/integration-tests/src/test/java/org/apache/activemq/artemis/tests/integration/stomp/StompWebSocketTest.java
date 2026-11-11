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

import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * This is a sanity check to ensure a client can connect via WebSocket. Only basic connectivity is tested here because
 * once the connection is established the code paths should be the same as normal TCP clients. Those code paths are
 * tested elsewhere.
 */
public class StompWebSocketTest extends StompTestBase {

   protected StompClientConnection conn;

   public StompWebSocketTest() {
      super("ws+v10.stomp");
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      conn = StompClientConnectionFactory.createClientConnection(uri);
   }

   @Test
   public void testConnect() throws Exception {
      ClientStompFrame response = conn.sendFrame(conn.createFrame(Stomp.Commands.CONNECT).addHeader(Stomp.Headers.Connect.LOGIN, defUser).addHeader(Stomp.Headers.Connect.PASSCODE, defPass).addHeader(Stomp.Headers.Connect.REQUEST_ID, "1"));

      assertEquals(Stomp.Responses.CONNECTED, response.getCommand());
      assertEquals("1", response.getHeader(Stomp.Headers.Connected.RESPONSE_ID));

      conn.disconnect();
   }
}
