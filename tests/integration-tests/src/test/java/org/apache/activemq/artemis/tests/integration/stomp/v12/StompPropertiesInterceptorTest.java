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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompFrame;
import org.apache.activemq.artemis.core.protocol.stomp.StompFrameInterceptor;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.integration.stomp.StompTestBase;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StompPropertiesInterceptorTest extends StompTestBase {

   private static volatile boolean interceptSendSuccess = false;
   private static volatile boolean interceptSubscribeSuccess = false;
   private static final String MESSAGE_TEXT = "messageText";
   private static final String MY_HEADER = "my-header";
   private static Map<String, Object> expectedProperties = new ConcurrentHashMap<>();

   public StompPropertiesInterceptorTest() {
      super("tcp+v12.stomp");
   }

   @Override
   public List<String> getIncomingInterceptors() {
      return List.of(StompFramePropertiesInterceptor.class.getName());
   }

   public static class StompFramePropertiesInterceptor implements StompFrameInterceptor {

      @Override
      public boolean intercept(StompFrame stompFrame, RemotingConnection connection) {
         if (stompFrame.getCommand().equals(Stomp.Commands.SEND)) {
            assertEquals(stompFrame.getHeader(MY_HEADER), expectedProperties.get(MY_HEADER));
            interceptSendSuccess = true;
         }
         if (stompFrame.getCommand().equals(Stomp.Commands.SUBSCRIBE)) {
            assertEquals(stompFrame.getHeader(MY_HEADER), expectedProperties.get(MY_HEADER));
            assertEquals(stompFrame.getBody(), expectedProperties.get(MESSAGE_TEXT));
            interceptSubscribeSuccess = true;
         }
         return true;
      }
   }

   @Test
   @Timeout(60)
   public void testCheckInterceptedStompMessageProperties() throws Exception {
      final String msgText = "Test intercepted message";
      final String myHeader = "TestInterceptedHeader";
      expectedProperties.put(MESSAGE_TEXT, msgText);
      expectedProperties.put(MY_HEADER, myHeader);

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      ClientStompFrame subFrame = conn.createFrame(Stomp.Commands.SUBSCRIBE);

      subFrame.addHeader(Stomp.Headers.Subscribe.SUBSCRIPTION_TYPE, "ANYCAST");
      subFrame.addHeader(Stomp.Headers.Subscribe.DESTINATION, name);
      subFrame.addHeader(Stomp.Headers.Subscribe.ACK_MODE, "auto");
      subFrame.addHeader(MY_HEADER, myHeader);
      subFrame.setBody(msgText);
      conn.sendFrame(subFrame);
      Wait.assertTrue(() -> interceptSubscribeSuccess == true, 2000, 100);

      ClientStompFrame sendFrame = conn.createFrame(Stomp.Commands.SEND);
      sendFrame.addHeader(Stomp.Headers.Send.DESTINATION, name);
      sendFrame.addHeader(MY_HEADER, myHeader);
      conn.sendFrame(sendFrame);
      Wait.assertTrue(() -> interceptSendSuccess == true, 2000, 100);

      Wait.assertEquals(1L, () -> server.locateQueue(name).getMessagesAcknowledged(), 2000, 100);

      conn.disconnect();
   }
}
