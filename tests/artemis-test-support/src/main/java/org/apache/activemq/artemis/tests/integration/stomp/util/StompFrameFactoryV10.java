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

import java.util.StringTokenizer;

/**
 * 1.0 frames
 *
 * 1. CONNECT
 * 2. CONNECTED
 * 3. SEND
 * 4. SUBSCRIBE
 * 5. UNSUBSCRIBE
 * 6. BEGIN
 * 7. COMMIT
 * 8. ACK
 * 9. ABORT
 * 10. DISCONNECT
 * 11. MESSAGE
 * 12. RECEIPT
 * 13. ERROR
 */
public class StompFrameFactoryV10 implements StompFrameFactory {

   @Override
   public ClientStompFrame createFrame(final String data) {
      //split the string at "\n\n"
      String[] dataFields = data.split("\n\n");

      StringTokenizer tokenizer = new StringTokenizer(dataFields[0], "\n");

      String command = tokenizer.nextToken();
      ClientStompFrame frame = newFrame(command);

      while (tokenizer.hasMoreTokens()) {
         String header = tokenizer.nextToken();
         String[] fields = handleHeaders(header);
         frame.addHeader(fields[0], fields[1]);
      }

      //body (without null byte)
      if (dataFields.length == 2) {
         frame.setBody(dataFields[1]);
      }
      return frame;
   }

   @Override
   public String[] handleHeaders(String header) {
      String[] result = header.split(":");

      // handle headers with empty value
      if (result.length == 1) {
         String[] tmp = new String[2];
         tmp[0] = result[0];
         tmp[1] = "";
         result = tmp;
      }

      return result;
   }

   @Override
   public ClientStompFrame newFrame(String command) {
      return new ClientStompFrameV10(command);
   }

   @Override
   public ClientStompFrame newAnyFrame(String command) {
      return new ClientStompFrameV10(command, false);
   }

}
