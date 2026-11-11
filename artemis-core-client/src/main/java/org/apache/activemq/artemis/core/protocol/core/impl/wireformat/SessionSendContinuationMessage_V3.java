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
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.utils.DataConstants;

public class SessionSendContinuationMessage_V3 extends SessionSendContinuationMessage_V2 {

   private int senderID;

   public SessionSendContinuationMessage_V3() {
      super();
   }

   public SessionSendContinuationMessage_V3(final Message message,
                                            final byte[] body,
                                            final boolean continues,
                                            final boolean requiresResponse,
                                            final long messageBodySize,
                                            final int senderID,
                                            final SendAcknowledgementHandler handler) {
      super(message, body, continues, requiresResponse, messageBodySize, handler);
      this.senderID = senderID;
   }

   @Override
   public int expectedEncodeSize() {
      return super.expectedEncodeSize() + DataConstants.SIZE_INT;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeInt(senderID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      senderID = buffer.readInt();
   }

   @Override
   public int getSenderID() {
      return senderID;
   }
}
