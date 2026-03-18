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

public class SimpleBytes {

   private final int step;
   private byte[] contents;
   private int index;

   public SimpleBytes(int initCapacity) {
      this.step = initCapacity;
      contents = new byte[initCapacity];
      index = 0;
   }

   public String getString() {
      if (index == 0) {
         return "";
      }

      return new String(contents, 0, index, StandardCharsets.UTF_8);
   }

   public void reset() {
      index = 0;
   }

   public void append(byte b) {
      if (index >= contents.length) {
         //grow
         byte[] newBuffer = new byte[contents.length + step];
         System.arraycopy(contents, 0, newBuffer, 0, contents.length);
         contents = newBuffer;
      }
      contents[index++] = b;
   }
}
