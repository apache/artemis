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
package org.apache.activemq.artemis.tests.integration.stomp.util;

import org.apache.activemq.artemis.core.protocol.stomp.Stomp;

public class ClientStompFrameV11 extends ClientStompFrameV10 {

   static {
      validCommands.add(Stomp.Commands.NACK);
      validCommands.add(Stomp.Commands.STOMP);
   }

   boolean forceOneway = false;
   boolean isPing = false;

   public ClientStompFrameV11(String command) {
      super(command);
   }

   public ClientStompFrameV11(String command, boolean validate) {
      super(command, validate);
   }

   @Override
   public ClientStompFrame setForceOneway() {
      forceOneway = true;
      return this;
   }

   @Override
   public boolean needsReply() {
      if (forceOneway) {
         return false;
      }

      if (Stomp.Commands.STOMP.equals(command)) {
         return true;
      }

      return super.needsReply();
   }

   @Override
   public ClientStompFrame setPing(boolean b) {
      isPing = b;
      return this;
   }

   @Override
   public boolean isPing() {
      return isPing;
   }

   @Override
   public String toString() {
      return "[1.1]" + super.toString();
   }
}
