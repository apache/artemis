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
package org.apache.activemq.artemis.core.client.impl;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.buffers.impl.ResetLimitWrappedActiveMQBuffer;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 * ClientLargeMessageImpl is only created when receiving large messages.
 * <p>
 * At the time of sending a regular Message is sent as we won't know the message is considered large
 * until the buffer is filled up or the user set a streaming.
 */
public final class ClientLargeMessageImpl extends ClientMessageImpl implements ClientLargeMessageInternal {

   // Used only when receiving large messages
   private LargeMessageController largeMessageController;

   private long largeMessageSize;

   /**
    * @param largeMessageSize the largeMessageSize to set
    */
   @Override
   public void setLargeMessageSize(long largeMessageSize) {
      this.largeMessageSize = largeMessageSize;
   }

   public long getLargeMessageSize() {
      return this.largeMessageSize;
   }

   // we only need this constructor as this is only used at decoding large messages on client
   public ClientLargeMessageImpl() {
      super();
   }

   @Override
   public int getEncodeSize() {
      if (writableBuffer != null) {
         return super.getEncodeSize();
      } else {
         return DataConstants.SIZE_INT + DataConstants.SIZE_INT + getHeadersAndPropertiesEncodeSize();
      }
   }

   /**
    * @return the largeMessage
    */
   @Override
   public boolean isLargeMessage() {
      return true;
   }

   @Override
   public void setLargeMessageController(final LargeMessageController controller) {
      largeMessageController = controller;
   }

   @Override
   public void checkCompletion() throws ActiveMQException {
      checkBuffer();
   }

   @Override
   public ActiveMQBuffer getBodyBuffer() {

      try {
         checkBuffer();
      } catch (ActiveMQException e) {
         throw new RuntimeException(e.getMessage(), e);
      }

      return writableBuffer;
   }

   @Override
   public int getBodySize() {
      return getLongProperty(Message.HDR_LARGE_BODY_SIZE).intValue();
   }

   @Override
   public LargeMessageController getLargeMessageController() {
      return largeMessageController;
   }

   @Override
   public void saveToOutputStream(final OutputStream out) throws ActiveMQException {
      if (writableBuffer != null) {
         // The body was rebuilt on the client, so we need to behave as a regular message on this case
         super.saveToOutputStream(out);
      } else {
         largeMessageController.saveBuffer(out);
      }
   }

   @Override
   public ClientLargeMessageImpl setOutputStream(final OutputStream out) throws ActiveMQException {
      if (writableBuffer != null) {
         super.setOutputStream(out);
      } else {
         largeMessageController.setOutputStream(out);
      }

      return this;
   }

   @Override
   public boolean waitOutputStreamCompletion(final long timeMilliseconds) throws ActiveMQException {
      if (writableBuffer != null) {
         return super.waitOutputStreamCompletion(timeMilliseconds);
      } else {
         return largeMessageController.waitCompletion(timeMilliseconds);
      }
   }

   @Override
   public void discardBody() {
      if (writableBuffer != null) {
         super.discardBody();
      } else {
         largeMessageController.discardUnusedPackets();
      }
   }

   private void checkBuffer() throws ActiveMQException {
      if (writableBuffer == null) {

         long bodySize = this.largeMessageSize + BODY_OFFSET;
         if (bodySize > Integer.MAX_VALUE) {
            bodySize = Integer.MAX_VALUE;
         }
         initBuffer((int) bodySize);

         writableBuffer = new ResetLimitWrappedActiveMQBuffer(BODY_OFFSET, buffer.duplicate(), this);

         largeMessageController.saveBuffer(new ActiveMQOutputStream(writableBuffer));
      }
   }

   private static class ActiveMQOutputStream extends OutputStream {

      private final ActiveMQBuffer bufferOut;

      ActiveMQOutputStream(ActiveMQBuffer out) {
         this.bufferOut = out;
      }

      @Override
      public void write(int b) throws IOException {
         bufferOut.writeByte((byte) (b & 0xff));
      }
   }

   public void retrieveExistingData(ClientMessageInternal clMessage) {
      this.internalSetMessageID(clMessage.getMessageID());
      this.address = clMessage.getAddressSimpleString();
      this.setUserID(clMessage.getUserID());
      this.setFlowControlSize(clMessage.getFlowControlSize());
      this.setDeliveryCount(clMessage.getDeliveryCount());
      this.type = clMessage.getType();
      this.durable = clMessage.isDurable();
      this.setExpiration(clMessage.getExpiration());
      this.timestamp = clMessage.getTimestamp();
      this.priority = clMessage.getPriority();
      this.properties = clMessage.getProperties();
      this.largeMessageSize = clMessage.getLongProperty(HDR_LARGE_BODY_SIZE);
   }
}
