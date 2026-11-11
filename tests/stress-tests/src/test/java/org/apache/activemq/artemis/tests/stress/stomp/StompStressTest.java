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
package org.apache.activemq.artemis.tests.stress.stomp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompProtocolManagerFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StompStressTest extends ActiveMQTestBase {

   private static final int COUNT = 1000;

   private final int port = 61613;

   private Socket stompSocket;

   private ByteArrayOutputStream inputBuffer;

   private final String destination = "stomp.stress.queue";

   private ActiveMQServer server;

   @Test
   public void testSendAndReceiveMessage() throws Exception {
      String frame = "CONNECT\n" + "login: brianm\n" + "passcode: wombats\n\n" + Stomp.NULL;
      sendFrame(frame);

      frame = receiveFrame(10000);
      assertTrue(frame.startsWith("CONNECTED"));

      frame = "SUBSCRIBE\n" + "destination:" + destination + "\n" + "ack:auto\n\n" + Stomp.NULL;
      sendFrame(frame);

      frame = "SEND\n" + "destination:" + destination + "\n";

      for (int i = 0; i < COUNT; i++) {
         System.out.println(">>> " + i);
         sendFrame(frame + "count:" + i + "\n\n" + Stomp.NULL);
      }

      for (int i = 0; i < COUNT; i++) {
         System.out.println("<<< " + i);
         frame = receiveFrame(10000);
         assertTrue(frame.startsWith("MESSAGE"));
         assertTrue(frame.indexOf("destination:") > 0);
         assertTrue(frame.indexOf("count:" + i) > 0);
      }

      frame = "DISCONNECT\n" + "\n\n" + Stomp.NULL;
      sendFrame(frame);
   }

   // Implementation methods
   // -------------------------------------------------------------------------
   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer();
      server.start();

      stompSocket = createSocket();
      inputBuffer = new ByteArrayOutputStream();
   }

   private ActiveMQServer createServer() throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PROTOCOLS_PROP_NAME, StompProtocolManagerFactory.STOMP_PROTOCOL_NAME);
      params.put(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_STOMP_PORT);
      TransportConfiguration stompTransport = new TransportConfiguration(NettyAcceptorFactory.class.getName(), params);

      Configuration config = createBasicConfig().setPersistenceEnabled(false).addAcceptorConfiguration(stompTransport).addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName())).addQueueConfiguration(QueueConfiguration.of(destination).setDurable(false));

      return addServer(ActiveMQServers.newActiveMQServer(config));
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      if (stompSocket != null) {
         stompSocket.close();
      }
      super.tearDown();
   }

   protected Socket createSocket() throws IOException {
      return new Socket("127.0.0.1", port);
   }

   public void sendFrame(String data) throws Exception {
      byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
      OutputStream outputStream = stompSocket.getOutputStream();
      for (byte b : bytes) {
         outputStream.write(b);
      }
      outputStream.flush();
   }

   public void sendFrame(byte[] data) throws Exception {
      OutputStream outputStream = stompSocket.getOutputStream();
      for (byte element : data) {
         outputStream.write(element);
      }
      outputStream.flush();
   }

   public String receiveFrame(long timeOut) throws Exception {
      stompSocket.setSoTimeout((int) timeOut);
      InputStream is = stompSocket.getInputStream();
      int c = 0;
      for (;;) {
         c = is.read();
         if (c < 0) {
            throw new IOException("socket closed.");
         } else if (c == 0) {
            c = is.read();
            if (c != '\n') {
               byte[] ba = inputBuffer.toByteArray();
               System.out.println(new String(ba, StandardCharsets.UTF_8));
            }
            assertEquals('\n', c, "Expecting stomp frame to terminate with \0\n");
            byte[] ba = inputBuffer.toByteArray();
            inputBuffer.reset();
            return new String(ba, StandardCharsets.UTF_8);
         } else {
            inputBuffer.write(c);
         }
      }
   }
}
