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
package org.apache.activemq.artemis.core.server;

/**
 * This class essentially mirrors {@code RoutingType} except it has some additional members to support special
 * configuration semantics for diverts and bridges.  These additional members weren't put in {@code RoutingType} so as
 * to not confuse users.
 */
public enum ComponentConfigurationRoutingType {

   MULTICAST, ANYCAST, STRIP, PASS;

   public byte getType() {
      return switch (this) {
         case MULTICAST -> 0;
         case ANYCAST -> 1;
         case STRIP -> 2;
         case PASS -> 3;
         default -> -1;
      };
   }

   public static ComponentConfigurationRoutingType getType(byte type) {
      return switch (type) {
         case 0 -> MULTICAST;
         case 1 -> ANYCAST;
         case 2 -> STRIP;
         case 3 -> PASS;
         default -> null;
      };
   }
}
