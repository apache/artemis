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
package org.apache.activemq.artemis.tests.integration.stomp.util;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.apache.activemq.artemis.core.protocol.stomp.Stomp;

public class StompClientConnectionV11 extends StompClientConnectionV10 {

   public StompClientConnectionV11(String host, int port) throws IOException {
      super("1.1", host, port);
   }

   public StompClientConnectionV11(String version, String host, int port) throws IOException {
      super(version, host, port);
   }

   public StompClientConnectionV11(URI uri) throws Exception {
      super(uri);
   }

   public StompClientConnectionV11(URI uri, boolean autoConnect) throws Exception {
      super(uri, autoConnect);
   }

   @Override
   public ClientStompFrame connect(String username, String passcode, String clientID) throws IOException, InterruptedException {
      ClientStompFrame frame = factory.newFrame(Stomp.Commands.CONNECT);
      frame.addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, getVersion());
      frame.addHeader(Stomp.Headers.Connect.HOST, "localhost");
      if (clientID != null) {
         frame.addHeader(Stomp.Headers.Connect.CLIENT_ID, clientID);
      }

      if (username != null) {
         frame.addHeader(Stomp.Headers.Connect.LOGIN, username);
         frame.addHeader(Stomp.Headers.Connect.PASSCODE, passcode);
      }

      ClientStompFrame response = this.sendFrame(frame);

      if (Stomp.Responses.CONNECTED.equals(response.getCommand())) {
         String version = response.getHeader(Stomp.Headers.Connected.VERSION);
         if (!version.equals(getVersion())) {
            throw new IllegalStateException("incorrect version!");
         }

         this.username = username;
         this.passcode = passcode;
         this.connected = true;
      } else {
         connected = false;
      }
      return response;
   }

   public void connect1(String username, String passcode) throws IOException, InterruptedException {
      ClientStompFrame frame = factory.newFrame(Stomp.Commands.STOMP);
      frame.addHeader(Stomp.Headers.Connect.ACCEPT_VERSION, "1.0,1.1");
      frame.addHeader(Stomp.Headers.Connect.HOST, "127.0.0.1");
      if (username != null) {
         frame.addHeader(Stomp.Headers.Connect.LOGIN, username);
         frame.addHeader(Stomp.Headers.Connect.PASSCODE, passcode);
      }

      ClientStompFrame response = this.sendFrame(frame);

      if (Stomp.Responses.CONNECTED.equals(response.getCommand())) {
         String version = response.getHeader(Stomp.Headers.Connected.VERSION);
         if (!version.equals(getVersion())) {
            throw new IllegalStateException("incorrect version!");
         }

         this.username = username;
         this.passcode = passcode;
         this.connected = true;
      } else {
         System.err.println("Connection failed with frame " + response);
         connected = false;
      }
   }

   @Override
   public void disconnect() throws IOException, InterruptedException {
      stopPinger();

      ClientStompFrame frame = factory.newFrame(Stomp.Commands.DISCONNECT);

      String uuid = UUID.randomUUID().toString();

      frame.addHeader(Stomp.Headers.RECEIPT_REQUESTED, uuid);

      try {
         if (transport.isConnected()) {
            ClientStompFrame result = this.sendFrame(frame);
            if (result == null || (!Stomp.Responses.RECEIPT.equals(result.getCommand())) || (!uuid.equals(result.getHeader(Stomp.Headers.Response.RECEIPT_ID)))) {
               throw new IOException("Disconnect failed! " + result);
            }
         }
      } catch (Exception e) {
         // Transport may have been closed
      }
      close();

      connected = false;
   }

   @Override
   public ClientStompFrame createFrame(String command) {
      return factory.newFrame(command);
   }

   @Override
   public void startPinger(long interval) {
      pinger = new Pinger(interval);
      pinger.startPing();
   }

   @Override
   public void stopPinger() {
      if (pinger != null) {
         pinger.stopPing();
         try {
            pinger.join();
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
         pinger = null;
      }
   }

   @Override
   public int getServerPingNumber() {
      return serverPingCounter;
   }

}
