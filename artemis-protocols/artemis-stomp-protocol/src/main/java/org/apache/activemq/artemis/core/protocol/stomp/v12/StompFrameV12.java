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
package org.apache.activemq.artemis.core.protocol.stomp.v12;

import java.util.Map;

import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.v11.StompFrameV11;

public class StompFrameV12 extends StompFrameV11 {

   public StompFrameV12(String command, Map<String, String> headers, byte[] content) {
      super(command, headers, content);
      initV12();
   }

   public StompFrameV12(String command) {
      super(command);
      initV12();
   }

   private void initV12() {
      // STOMP 1.2 requires disconnect after sending ERROR
      if (Stomp.Responses.ERROR.equals(command)) {
         setNeedsDisconnect(true);
      }
   }
}
