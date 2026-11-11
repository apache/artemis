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

public enum DisconnectReason {
   REDIRECT((byte)0, false),
   REDIRECT_ON_CRITICAL_ERROR((byte)1, true),
   SCALE_DOWN((byte)2, false),
   SCALE_DOWN_ON_CRITICAL_ERROR((byte)3, true),
   SHUT_DOWN((byte)4, false),
   SHUT_DOWN_ON_CRITICAL_ERROR((byte)5, true);

   private final byte type;
   private final boolean criticalError;

   DisconnectReason(byte type, boolean criticalError) {
      this.type = type;
      this.criticalError = criticalError;
   }

   public byte getType() {
      return type;
   }

   public boolean isCriticalError() {
      return criticalError;
   }

   public boolean isRedirect() {
      return this == REDIRECT || this == REDIRECT_ON_CRITICAL_ERROR;
   }

   public boolean isScaleDown() {
      return this == SCALE_DOWN || this == SCALE_DOWN_ON_CRITICAL_ERROR;
   }

   public boolean isShutDown() {
      return this == SHUT_DOWN || this == SHUT_DOWN_ON_CRITICAL_ERROR;
   }

   public static DisconnectReason getType(byte type) {
      return switch (type) {
         case 0 -> REDIRECT;
         case 1 -> REDIRECT_ON_CRITICAL_ERROR;
         case 2 -> SCALE_DOWN;
         case 3 -> SCALE_DOWN_ON_CRITICAL_ERROR;
         case 4 -> SHUT_DOWN;
         case 5 -> SHUT_DOWN_ON_CRITICAL_ERROR;
         default -> null;
      };
   }
}
