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

public class ServerSASLPlain implements ServerSASL {

   public static final String NAME = "PLAIN";
   private SASLResult result = null;

   @Override
   public String getName() {
      return NAME;
   }

   @Override
   public byte[] processSASL(byte[] data) {
      String username = null;
      String password = null;
      String bytes = new String(data);
      String[] credentials = bytes.split(Character.toString((char) 0));

      switch (credentials.length) {
         case 2:
            username = credentials[0];
            password = credentials[1];
            break;
         case 3:
            username = credentials[1];
            password = credentials[2];
            break;
         default:
            break;
      }

      boolean success = authenticate(username, password);

      result = new PlainSASLResult(success, username, password);

      return null;
   }

   @Override
   public SASLResult result() {
      return result;
   }

   @Override
   public void done() {
   }

   /**
    * Hook for subclasses to perform the authentication here
    */
   protected boolean authenticate(String user, String password) {
      return true;
   }
}
