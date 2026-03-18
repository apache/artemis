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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;

public abstract class AbstractClientStompFrame implements ClientStompFrame {

   protected static final Set<String> validCommands = new HashSet<>();
   protected String command;
   protected List<Header> headers = new ArrayList<>();
   protected Set<String> headerKeys = new HashSet<>();
   protected String body;
   protected String EOL = "\n";

   static {
      validCommands.add(Stomp.Commands.CONNECT);
      validCommands.add(Stomp.Responses.CONNECTED);
      validCommands.add(Stomp.Commands.SEND);
      validCommands.add(Stomp.Responses.RECEIPT);
      validCommands.add(Stomp.Commands.SUBSCRIBE);
      validCommands.add(Stomp.Commands.UNSUBSCRIBE);
      validCommands.add(Stomp.Responses.MESSAGE);
      validCommands.add(Stomp.Commands.BEGIN);
      validCommands.add(Stomp.Commands.COMMIT);
      validCommands.add(Stomp.Commands.ABORT);
      validCommands.add(Stomp.Commands.ACK);
      validCommands.add(Stomp.Commands.DISCONNECT);
      validCommands.add(Stomp.Responses.ERROR);
   }

   public AbstractClientStompFrame(String command) {
      this(command, true);
   }

   public AbstractClientStompFrame(String command, boolean validate) {
      if (validate && (!validate(command))) {
         throw new IllegalArgumentException("Invalid command " + command);
      }
      this.command = command;
   }

   public boolean validate(String command) {
      return validCommands.contains(command);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("Frame: <" + command + ">" + "\n");
      Iterator<Header> iter = headers.iterator();
      while (iter.hasNext()) {
         Header h = iter.next();
         sb.append(h.key + ":" + h.val + "\n");
      }
      sb.append("\n");
      sb.append("<body>" + body + "<body>");
      return sb.toString();
   }

   @Override
   public ByteBuffer toByteBuffer() {
      return toByteBufferInternal(null);
   }

   @Override
   public ByteBuffer toByteBufferWithExtra(String str) {
      return toByteBufferInternal(str);
   }

   @Override
   public ByteBuf toNettyByteBuf() {
      return Unpooled.copiedBuffer(toByteBuffer());
   }

   @Override
   public ByteBuf toNettyByteBufWithExtras(String str) {
      return Unpooled.copiedBuffer(toByteBufferWithExtra(str));
   }

   public ByteBuffer toByteBufferInternal(String str) {
      StringBuilder sb = new StringBuilder();
      sb.append(command + EOL);
      int n = headers.size();
      for (int i = 0; i < n; i++) {
         sb.append(headers.get(i).key + ":" + headers.get(i).val + EOL);
      }
      sb.append(EOL);
      if (body != null) {
         sb.append(body);
      }
      sb.append((char) 0);
      if (str != null) {
         sb.append(str);
      }

      String data = sb.toString();

      byte[] byteValue = data.getBytes(StandardCharsets.UTF_8);

      ByteBuffer buffer = ByteBuffer.allocateDirect(byteValue.length);
      buffer.put(byteValue);

      buffer.rewind();
      return buffer;
   }

   @Override
   public boolean needsReply() {
      if (Stomp.Commands.CONNECT.equals(command) || headerKeys.contains(Stomp.Headers.RECEIPT_REQUESTED)) {
         return true;
      }
      return false;
   }

   @Override
   public ClientStompFrame setCommand(String command) {
      this.command = command;
      return this;
   }

   @Override
   public ClientStompFrame addHeader(String key, String val) {
      headers.add(new Header(key, val));
      headerKeys.add(key);
      return this;
   }

   @Override
   public ClientStompFrame setBody(String body) {
      this.body = body;
      return this;
   }

   @Override
   public String getBody() {
      return body;
   }

   private class Header {

      public String key;
      public String val;

      private Header(String key, String val) {
         this.key = key;
         this.val = val;
      }
   }

   @Override
   public String getCommand() {
      return command;
   }

   @Override
   public String getHeader(String header) {
      if (headerKeys.contains(header)) {
         Iterator<Header> iter = headers.iterator();
         while (iter.hasNext()) {
            Header h = iter.next();
            if (h.key.equals(header)) {
               return h.val;
            }
         }
      }
      return null;
   }

}
