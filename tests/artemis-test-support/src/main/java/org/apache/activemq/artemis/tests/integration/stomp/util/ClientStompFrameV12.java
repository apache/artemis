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

public class ClientStompFrameV12 extends ClientStompFrameV11 {

   public ClientStompFrameV12(String command) {
      this(command, true, true);
   }

   public ClientStompFrameV12(String command, boolean newEol, boolean validate) {
      super(command, validate);
      /*
       * Stomp 1.2 frames allow a carriage return (octet 13) to optionally
       * precedes the required line feed (octet 10) as their internal line breaks.
       * Stomp 1.0 and 1.1 only allow line feeds.
       */
      if (newEol) {
         EOL = "\r\n";
      }
   }

   @Override
   public String toString() {
      return "[1.2]" + super.toString();
   }

}
