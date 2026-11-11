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

import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class StompClientConnectionV10 extends AbstractStompClientConnection {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public StompClientConnectionV10(String host, int port) throws IOException {
      super("1.0", host, port);
   }

   public StompClientConnectionV10(String version, String host, int port) throws IOException {
      super(version, host, port);
   }

   public StompClientConnectionV10(URI uri) throws Exception {
      super(uri);
   }

   public StompClientConnectionV10(URI uri, boolean autoConnect) throws Exception {
      super(uri, autoConnect);
   }

   @Override
   public ClientStompFrame connect(String username, String passcode) throws IOException, InterruptedException {
      return connect(username, passcode, null);
   }

   @Override
   public ClientStompFrame connect(String username, String passcode, String clientID) throws IOException, InterruptedException {

      ClientStompFrame frame = factory.newFrame(Stomp.Commands.CONNECT);
      frame.addHeader(Stomp.Headers.Connect.LOGIN, username);
      frame.addHeader(Stomp.Headers.Connect.PASSCODE, passcode);
      if (clientID != null) {
         frame.addHeader(Stomp.Headers.Connect.CLIENT_ID, clientID);
      }

      ClientStompFrame response = this.sendFrame(frame);

      if (response.getCommand().equals(Stomp.Responses.CONNECTED)) {
         connected = true;
      } else {
         logger.warn("Connection failed with: {}", response);
         connected = false;
      }
      return response;
   }

   @Override
   public void disconnect() throws IOException, InterruptedException {
      ClientStompFrame frame = factory.newFrame(Stomp.Commands.DISCONNECT);
      this.sendFrame(frame);

      close();

      connected = false;
   }

   @Override
   public ClientStompFrame createFrame(String command) {
      return new ClientStompFrameV10(command);
   }

   @Override
   public void startPinger(long interval) {
   }

   @Override
   public void stopPinger() {
   }

   @Override
   public int getServerPingNumber() {
      return 0;
   }
}
