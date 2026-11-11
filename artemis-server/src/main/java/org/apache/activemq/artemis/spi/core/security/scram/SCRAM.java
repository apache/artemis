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
package org.apache.activemq.artemis.spi.core.security.scram;

/**
 * Defines sets of known SCRAM types with methods to fetch matching digest and hmac names
 */
public enum SCRAM {
                   // ordered by precedence
                   SHA512,
                   SHA256;

   public String getName() {
      return switch (this) {
         case SHA256 -> "SCRAM-SHA-256";
         case SHA512 -> "SCRAM-SHA-512";
      };
   }

   public String getDigest() {
      return switch (this) {
         case SHA256 -> "SHA-256";
         case SHA512 -> "SHA-512";
      };
   }

   public String getHmac() {
      return switch (this) {
         case SHA256 -> "HmacSHA256";
         case SHA512 -> "HmacSHA512";
      };
   }
}
