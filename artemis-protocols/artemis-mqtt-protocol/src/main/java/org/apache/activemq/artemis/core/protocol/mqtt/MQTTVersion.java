/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.protocol.mqtt;

public enum MQTTVersion {

   MQTT_3_1, MQTT_3_1_1, MQTT_5;

   public int getVersion() {
      return switch (this) {
         case MQTT_3_1 -> 3;
         case MQTT_3_1_1 -> 4;
         case MQTT_5 -> 5;
         default -> -1;
      };
   }

   public static MQTTVersion getVersion(int version) {
      return switch (version) {
         case 3 -> MQTT_3_1;
         case 4 -> MQTT_3_1_1;
         case 5 -> MQTT_5;
         default -> null;
      };
   }
}
