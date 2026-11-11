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
package org.apache.activemq.artemis.core.protocol.stomp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.protocol.stomp.v10.StompFrameV10;

/**
 * Represents all the data in a STOMP frame.
 */
public class StompFrame {

   protected static final byte[] END_OF_FRAME = new byte[]{0, '\n'};

   protected final String command;

   protected Map<String, String> headers;

   private String body;

   protected byte[] bytesBody;

   protected ActiveMQBuffer buffer = null;

   protected int size;

   private boolean disconnect;

   private boolean isPing;

   public StompFrame(String command) {
      this(command, false);
   }

   public StompFrame(String command, boolean disconnect) {
      this.command = command;
      this.headers = new LinkedHashMap<>();
      this.disconnect = disconnect;
   }

   public StompFrame(String command, Map<String, String> headers, byte[] content) {
      this.command = command;
      this.headers = headers;
      this.bytesBody = content;
   }

   public String getCommand() {
      return command;
   }

   public int getEncodedSize() throws Exception {
      if (buffer == null) {
         buffer = toActiveMQBuffer();
      }
      return size;
   }

   @Override
   public String toString() {
      StringBuilder result = new StringBuilder()
         .append("StompFrame[command=").append(command)
         .append(", headers=").append(headers
                                         .keySet()
                                         .stream()
                                         .map(key -> key + "=" + (key.equals(Stomp.Headers.Connect.PASSCODE) ? "****" : headers.get(key)))
                                         .collect(Collectors.joining(", ", "{", "}")));

      if (command.equals(Stomp.Responses.MESSAGE) || command.equals(Stomp.Responses.ERROR) || command.equals(Stomp.Commands.SEND)) {
         result.append(", body=").append(this.getBody())
               .append(", body-bytes=").append(Arrays.toString(bytesBody))
               .append(", size=").append(size);
      }

      result.append("]");

      return result.toString();
   }

   public boolean isPing() {
      return isPing;
   }

   public void setPing(boolean ping) {
      isPing = ping;
   }

   public ActiveMQBuffer toActiveMQBuffer() throws Exception {
      if (buffer == null) {
         if (isPing()) {
            buffer = ActiveMQBuffers.fixedBuffer(1);
            buffer.writeByte((byte) 10);
            size = buffer.writerIndex();
            return buffer;
         }

         StringBuilder head = new StringBuilder(512);
         head.append(command);
         head.append(Stomp.NEWLINE);
         // Output the headers.
         encodeHeaders(head);
         if (bytesBody != null && bytesBody.length > 0 && !hasHeader(Stomp.Headers.CONTENT_LENGTH) && !(this instanceof StompFrameV10)) {
            head.append(Stomp.Headers.CONTENT_LENGTH);
            head.append(Stomp.Headers.SEPARATOR);
            head.append(bytesBody.length);
            head.append(Stomp.NEWLINE);
         }
         // Add a newline to separate the headers from the content.
         head.append(Stomp.NEWLINE);

         byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
         int bodyLength = (bytesBody == null) ? 0 : bytesBody.length;

         buffer = ActiveMQBuffers.fixedBuffer(headBytes.length + bodyLength + END_OF_FRAME.length);

         buffer.writeBytes(headBytes);
         if (bytesBody != null) {
            buffer.writeBytes(bytesBody);
         }
         buffer.writeBytes(END_OF_FRAME);

         size = buffer.writerIndex();
      } else {
         buffer.readerIndex(0);
      }
      return buffer;
   }

   protected void encodeHeaders(StringBuilder head) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
         head.append(header.getKey());
         head.append(Stomp.Headers.SEPARATOR);
         head.append(header.getValue());
         head.append(Stomp.NEWLINE);
      }
   }

   public String getHeader(String key) {
      return headers.get(key);
   }

   public void addHeader(String key, String val) {
      headers.put(key, val);
   }

   public Map<String, String> getHeadersMap() {
      return headers;
   }

   public class Header {

      public String key;
      public String val;

      public Header(String key, String val) {
         this.key = key;
         this.val = val;
      }

      public String getEncodedKey() {
         return encode(key);
      }

      public String getEncodedValue() {
         return encode(val);
      }
   }

   public String encode(String str) {
      if (str == null) {
         return "";
      }

      int len = str.length();

      char[] buffer = new char[2 * len];
      int iBuffer = 0;
      for (int i = 0; i < len; i++) {
         char c = str.charAt(i);

         // \n
         if (c == (byte) 10) {
            buffer[iBuffer] = (byte) 92;
            buffer[++iBuffer] = (byte) 110;
         } else if (c == (byte) 13) { // \r
            buffer[iBuffer] = (byte) 92;
            buffer[++iBuffer] = (byte) 114;
         } else if (c == (byte) 92) { // \

            buffer[iBuffer] = (byte) 92;
            buffer[++iBuffer] = (byte) 92;
         } else if (c == (byte) 58) { // :
            buffer[iBuffer] = (byte) 92;
            buffer[++iBuffer] = (byte) 99;
         } else {
            buffer[iBuffer] = c;
         }
         iBuffer++;
      }

      return new String(buffer, 0, iBuffer);
   }

   public void setBody(String body) {
      this.body = body;
      this.bytesBody = body.getBytes(StandardCharsets.UTF_8);
   }

   public boolean hasHeader(String key) {
      return headers.containsKey(key);
   }

   public String getBody() {
      if (body == null) {
         if (bytesBody != null) {
            body = new String(bytesBody, StandardCharsets.UTF_8);
         }
      }
      return body;
   }

   //Since 1.1, there is a content-type header that needs to take care of
   public byte[] getBodyAsBytes() {
      return bytesBody;
   }

   public boolean needsDisconnect() {
      return disconnect;
   }

   public void setByteBody(byte[] content) {
      this.bytesBody = content;
   }

   public void setNeedsDisconnect(boolean b) {
      disconnect = b;
   }
}
