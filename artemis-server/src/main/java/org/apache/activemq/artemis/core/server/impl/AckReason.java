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

package org.apache.activemq.artemis.core.server.impl;

public enum AckReason {
   NORMAL((byte)0), KILLED((byte)1), EXPIRED((byte)2), REPLACED((byte)3);

   private byte value;

   AckReason(byte value) {
      this.value = value;
   }

   public byte getVal() {
      return value;
   }

   public static AckReason fromValue(byte value) {
      return switch (value) {
         case 0 -> NORMAL;
         case 1 -> KILLED;
         case 2 -> EXPIRED;
         case 3 -> REPLACED;
         default ->
            // in case a newer version connects with a not known type
            // this will just play safe and use the NORMAL ack mode
            NORMAL;
      };
   }

}