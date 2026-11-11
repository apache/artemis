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
package org.apache.activemq.artemis.api.core;

public enum RoutingType {

   MULTICAST, ANYCAST;

   public byte getType() {
      return switch (this) {
         case MULTICAST -> 0;
         case ANYCAST -> 1;
         default -> -1;
      };
   }

   public static RoutingType getType(byte type) {
      return switch (type) {
         case 0 -> MULTICAST;
         case 1 -> ANYCAST;
         default -> null;
      };
   }

   public static RoutingType getTypeOrDefault(byte type, RoutingType defaultType) {
      return switch (type) {
         case 0 -> MULTICAST;
         case 1 -> ANYCAST;
         default -> defaultType;
      };
   }
}
