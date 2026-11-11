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

/**
 * 1.1 frames
 * <p>
 * 1. CONNECT/STOMP(new)
 * 2. CONNECTED
 * 3. SEND
 * 4. SUBSCRIBE
 * 5. UNSUBSCRIBE
 * 6. BEGIN
 * 7. COMMIT
 * 8. ACK
 * 9. NACK (new)
 * 10. ABORT
 * 11. DISCONNECT
 * 12. MESSAGE
 * 13. RECEIPT
 * 14. ERROR
 */
public class StompFrameFactoryV11 extends StompFrameFactoryV10 {

   @Override
   public String[] handleHeaders(String header) {
      // split the header into the key and value at the ":" since there shouldn't be any unescaped colons in the header
      // except for the one separating the key and value
      String[] result = header.split(":");

      for (int j = 0; j < result.length; j++) {
         StringBuilder decodedHeader = new StringBuilder();
         boolean isEsc = false;

         for (int i = 0; i < result[j].length(); i++) {
            char b = result[j].charAt(i);

            switch (b) {
               //escaping
               case '\\': {
                  if (isEsc) {
                     //this is a backslash
                     decodedHeader.append(b);
                     isEsc = false;
                  } else {
                     //begin escaping
                     isEsc = true;
                  }
                  break;
               }
               case 'c': {
                  if (isEsc) {
                     decodedHeader.append(":");
                     isEsc = false;
                  } else {
                     decodedHeader.append(b);
                  }
                  break;
               }
               case 'n': {
                  if (isEsc) {
                     decodedHeader.append('\n');
                     isEsc = false;
                  } else {
                     decodedHeader.append(b);
                  }
                  break;
               }
               default: {
                  decodedHeader.append(b);
               }
            }
         }

         result[j] = decodedHeader.toString();
      }

      return result;
   }

   @Override
   public ClientStompFrame newFrame(String command) {
      return new ClientStompFrameV11(command);
   }

   @Override
   public ClientStompFrame newAnyFrame(String command) {
      return new ClientStompFrameV11(command, false);
   }

}
