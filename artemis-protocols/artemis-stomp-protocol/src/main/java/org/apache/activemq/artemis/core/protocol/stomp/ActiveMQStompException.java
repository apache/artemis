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
import java.util.ArrayList;
import java.util.List;

public class ActiveMQStompException extends Exception {

   public static final int NONE = 0;
   public static final int INVALID_EOL_V10 = 1;
   public static final int INVALID_COMMAND = 2;
   public static final int UNDEFINED_ESCAPE = 3;

   private static final long serialVersionUID = -274452327574950068L;

   private int code = NONE;
   private final List<Header> headers = new ArrayList<>(10);
   private String body;
   private VersionedStompFrameHandler handler;
   private Boolean disconnect;

   public ActiveMQStompException(StompConnection connection, String msg) {
      super(msg);
      handler = connection.getFrameHandler();
   }

   public ActiveMQStompException(String msg) {
      super(msg.replace(":", ""));
      handler = null;
   }

   public ActiveMQStompException(String msg, Throwable t) {
      super(msg.replace(":", ""), t);
      this.body = t.getMessage();
      handler = null;
   }

   //used for version control logic
   public ActiveMQStompException(int code, String details) {
      super(details);
      this.code = code;
      this.body = details;
      handler = null;
   }

   void addHeader(String header, String value) {
      headers.add(new Header(header, value));
   }

   public void setBody(String body) {
      this.body = body;
   }

   public StompFrame getFrame() {
      StompFrame frame = null;
      if (handler == null) {
         frame = new StompFrame(Stomp.Responses.ERROR);
      } else {
         frame = handler.createStompFrame(Stomp.Responses.ERROR);
      }
      frame.addHeader(Stomp.Headers.Error.MESSAGE, this.getMessage());
      for (Header header : headers) {
         frame.addHeader(header.key, header.val);
      }

      if (body != null) {
         frame.addHeader(Stomp.Headers.CONTENT_TYPE, "text/plain");
         frame.setByteBody(body.getBytes(StandardCharsets.UTF_8));
      } else {
         frame.setByteBody(new byte[0]);
      }
      if (disconnect != null) {
         frame.setNeedsDisconnect(disconnect);
      }
      return frame;
   }

   private static final class Header {

      public final String key;
      public final String val;

      private Header(String key, String val) {
         this.key = key;
         this.val = val;
      }
   }

   public void setDisconnect(boolean b) {
      disconnect = b;
   }

   public int getCode() {
      return code;
   }

   public void setCode(int newCode) {
      code = newCode;
   }

   public ActiveMQStompException setHandler(VersionedStompFrameHandler frameHandler) {
      this.handler = frameHandler;
      return this;
   }
}
