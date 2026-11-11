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
package org.apache.activemq.artemis.tests.integration.stomp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompFrame;
import org.apache.activemq.artemis.core.protocol.stomp.StompFrameInterceptor;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StompWithRejectingInterceptorTest extends StompTestBase {

   public StompWithRejectingInterceptorTest() {
      super("tcp+v10.stomp");
   }

   @Override
   public List<String> getIncomingInterceptors() {
      List<String> stompIncomingInterceptor = new ArrayList<>();
      stompIncomingInterceptor.add(IncomingStompFrameRejectInterceptor.class.getName());

      return stompIncomingInterceptor;
   }

   @Test
   public void stompFrameInterceptor() throws Exception {
      IncomingStompFrameRejectInterceptor.interceptedFrames.clear();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);
      Wait.assertEquals(1, () -> IncomingStompFrameRejectInterceptor.interceptedFrames.size(), 500, 10);
      assertEquals("CONNECT", IncomingStompFrameRejectInterceptor.interceptedFrames.get(0).getCommand());

      ClientStompFrame frame = conn.createFrame("SEND");
      frame.addHeader("destination", getQueuePrefix() + getQueueName());
      frame.setBody("Hello World");
      conn.sendFrame(frame);
      Wait.assertEquals(2, () -> IncomingStompFrameRejectInterceptor.interceptedFrames.size(), 500, 10);
      assertEquals("SEND", IncomingStompFrameRejectInterceptor.interceptedFrames.get(1).getCommand());

      conn.disconnect();
      Wait.assertEquals(3, () -> IncomingStompFrameRejectInterceptor.interceptedFrames.size(), 500, 10);
      assertEquals("DISCONNECT", IncomingStompFrameRejectInterceptor.interceptedFrames.get(2).getCommand());

      Wait.assertFalse(() -> server.locateQueue(SimpleString.of(getQueuePrefix() + getQueueName())).getMessageCount() > 0, 1000, 100);
   }

   public static class IncomingStompFrameRejectInterceptor implements StompFrameInterceptor {

      static List<StompFrame> interceptedFrames = Collections.synchronizedList(new ArrayList<>());

      @Override
      public boolean intercept(StompFrame stompFrame, RemotingConnection connection) {
         interceptedFrames.add(stompFrame);
         if (stompFrame.getCommand().equals(Stomp.Commands.SEND)) {
            return false;
         }
         return true;
      }
   }
}
