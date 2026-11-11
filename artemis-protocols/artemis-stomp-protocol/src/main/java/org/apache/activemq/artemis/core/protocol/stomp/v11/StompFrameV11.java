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
package org.apache.activemq.artemis.core.protocol.stomp.v11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompFrame;

public class StompFrameV11 extends StompFrame {

   //stomp 1.1 talks about repetitive headers.
   protected final List<Header> allHeaders = new ArrayList<>();

   public StompFrameV11(String command, Map<String, String> headers, byte[] content) {
      super(command, headers, content);
   }

   public StompFrameV11(String command) {
      super(command);
   }

   @Override
   protected void encodeHeaders(StringBuilder head) {
      for (Header h : allHeaders) {
         head.append(h.getEncodedKey());
         head.append(Stomp.Headers.SEPARATOR);
         head.append(h.getEncodedValue());
         head.append(Stomp.NEWLINE);
      }
   }

   @Override
   public void addHeader(String key, String val) {
      if (!headers.containsKey(key)) {
         headers.put(key, val);
         allHeaders.add(new Header(key, val));
      } else if (!key.equals(Stomp.Headers.CONTENT_LENGTH)) {
         allHeaders.add(new Header(key, val));
      }
   }
}
