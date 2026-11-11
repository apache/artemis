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
package org.apache.activemq.artemis.protocol.amqp.sasl;

import java.security.Principal;

public class ExternalServerSASL implements ServerSASL {

   public static final String NAME = "EXTERNAL";
   private static final byte[] EMPTY = new byte[0];
   private Principal principal;
   private SASLResult result;

   public ExternalServerSASL() {
   }

   @Override
   public String getName() {
      return NAME;
   }

   @Override
   public byte[] processSASL(byte[] bytes) {
      if (bytes != null) {
         if (bytes.length == 0) {
            result = new PrincipalSASLResult(true, principal);
         } else {
            // we don't accept any client identity
            result = new PrincipalSASLResult(false, null);
         }
      }
      return EMPTY;
   }

   @Override
   public SASLResult result() {
      return result;
   }

   @Override
   public void done() {
   }

   public void setPrincipal(Principal principal) {
      this.principal = principal;
   }
}

