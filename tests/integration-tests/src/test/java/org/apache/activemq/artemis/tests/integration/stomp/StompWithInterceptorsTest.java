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

import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.stomp.StompFrame;
import org.apache.activemq.artemis.core.protocol.stomp.StompFrameInterceptor;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StompWithInterceptorsTest extends StompTestBase {

   public StompWithInterceptorsTest() {
      super("tcp+v10.stomp");
   }

   @Override
   public List<String> getIncomingInterceptors() {
      List<String> stompIncomingInterceptor = new ArrayList<>();
      stompIncomingInterceptor.add(IncomingStompInterceptor.class.getName());
      stompIncomingInterceptor.add(CoreInterceptor.class.getName());

      return stompIncomingInterceptor;
   }

   @Override
   public List<String> getOutgoingInterceptors() {
      List<String> stompOutgoingInterceptor = new ArrayList<>();
      stompOutgoingInterceptor.add(OutgoingStompInterceptor.class.getName());

      return stompOutgoingInterceptor;
   }

   @Test
   public void stompFrameInterceptor() throws Exception {
      IncomingStompInterceptor.interceptedFrames.clear();
      OutgoingStompInterceptor.interceptedFrames.clear();
      // wait for the SESS_START which is the last packet for the test's JMS connection
      assertTrue(Wait.waitFor(() -> {
         for (Packet packet : new ArrayList<>(CoreInterceptor.incomingInterceptedFrames)) {
            if (packet.getType() == (byte) 67) {
               return true;
            }
         }
         return false;
      }, 2000, 50));
      CoreInterceptor.incomingInterceptedFrames.clear();

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      ClientStompFrame subFrame = conn.createFrame("SUBSCRIBE");
      subFrame.addHeader("subscription-type", "ANYCAST");
      subFrame.addHeader("destination", getQueuePrefix() + getQueueName());
      subFrame.addHeader("ack", "auto");
      conn.sendFrame(subFrame);

      assertEquals(0, CoreInterceptor.incomingInterceptedFrames.size());
      sendJmsMessage(getName());

      // Something was supposed to be called on sendMessages
      assertFalse(CoreInterceptor.incomingInterceptedFrames.isEmpty(), "core interceptor is not working");

      conn.receiveFrame(10000);

      ClientStompFrame frame = conn.createFrame("SEND");
      frame.addHeader("destination", getQueuePrefix() + getQueueName());
      frame.setBody("Hello World");
      conn.sendFrame(frame);

      assertTrue(Wait.waitFor(() -> OutgoingStompInterceptor.interceptedFrames.size() == 3, 2000, 50));

      conn.disconnect();

      assertTrue(Wait.waitFor(() -> IncomingStompInterceptor.interceptedFrames.size() == 4, 2000, 50));

      List<String> incomingCommands = new ArrayList<>(4);
      incomingCommands.add("CONNECT");
      incomingCommands.add("SUBSCRIBE");
      incomingCommands.add("SEND");
      incomingCommands.add("DISCONNECT");

      for (int i = 0; i < IncomingStompInterceptor.interceptedFrames.size(); i++) {
         assertEquals(incomingCommands.get(i), IncomingStompInterceptor.interceptedFrames.get(i).getCommand());
         assertEquals("incomingInterceptedVal", IncomingStompInterceptor.interceptedFrames.get(i).getHeader("incomingInterceptedProp"));
      }

      List<String> outgoingCommands = new ArrayList<>(3);
      outgoingCommands.add("CONNECTED");
      outgoingCommands.add("MESSAGE");
      outgoingCommands.add("MESSAGE");

      for (int i = 0; i < OutgoingStompInterceptor.interceptedFrames.size(); i++) {
         assertEquals(outgoingCommands.get(i), OutgoingStompInterceptor.interceptedFrames.get(i).getCommand());
      }

      assertEquals("incomingInterceptedVal", OutgoingStompInterceptor.interceptedFrames.get(2).getHeader("incomingInterceptedProp"));
      assertEquals("outgoingInterceptedVal", OutgoingStompInterceptor.interceptedFrames.get(2).getHeader("outgoingInterceptedProp"));
   }

   public static class CoreInterceptor implements Interceptor {

      static List<Packet> incomingInterceptedFrames = new ArrayList<>();

      @Override
      public boolean intercept(Packet packet, RemotingConnection connection) {
         incomingInterceptedFrames.add(packet);
         return true;
      }
   }

   public static class IncomingStompInterceptor implements StompFrameInterceptor {

      static List<StompFrame> interceptedFrames = Collections.synchronizedList(new ArrayList<>());

      @Override
      public boolean intercept(StompFrame stompFrame, RemotingConnection connection) {
         interceptedFrames.add(stompFrame);
         stompFrame.addHeader("incomingInterceptedProp", "incomingInterceptedVal");
         return true;
      }
   }

   public static class OutgoingStompInterceptor implements StompFrameInterceptor {

      static List<StompFrame> interceptedFrames = Collections.synchronizedList(new ArrayList<>());

      @Override
      public boolean intercept(StompFrame stompFrame, RemotingConnection connection) {
         interceptedFrames.add(stompFrame);
         stompFrame.addHeader("outgoingInterceptedProp", "outgoingInterceptedVal");
         return true;
      }
   }
}
