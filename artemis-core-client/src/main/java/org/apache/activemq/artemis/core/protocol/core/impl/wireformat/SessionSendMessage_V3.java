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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.utils.DataConstants;

public class SessionSendMessage_V3 extends SessionSendMessage_V2 {

   private int senderID;

   /**
    * This will be using the CoreMessage because it is meant for the core-protocol
    */
   public SessionSendMessage_V3(final ICoreMessage message,
                                final boolean requiresResponse,
                                final SendAcknowledgementHandler handler,
                                final int senderID) {
      super(message, requiresResponse, handler);
      this.senderID = senderID;
   }

   public SessionSendMessage_V3(final CoreMessage message) {
      super(message);
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeInt(senderID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      senderID = buffer.readInt();
   }

   @Override
   protected int fieldsEncodeSize() {
      return super.fieldsEncodeSize() + DataConstants.SIZE_INT;
   }

   @Override
   public int getSenderID() {
      return senderID;
   }
}
