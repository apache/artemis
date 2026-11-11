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

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 *  SessionSend Message for the 1.x branch
 */
public class SessionSendMessage_1X extends SessionSendMessage {

   public SessionSendMessage_1X(ICoreMessage message, boolean requiresResponse, SendAcknowledgementHandler handler) {
      super(message, requiresResponse, handler);
   }

   public SessionSendMessage_1X(CoreMessage message) {
      super(message);
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      message.sendBuffer_1X(buffer.byteBuf());
      buffer.writeBoolean(requiresResponse);
   }

   @Override
   protected void receiveMessage(ByteBuf messageBuffer) {
      message.receiveBuffer_1X(messageBuffer);
   }

   @Override
   public int expectedEncodeSize() {
      return super.expectedEncodeSize() + DataConstants.SIZE_INT;
   }

}
