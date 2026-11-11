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
package org.apache.activemq.artemis.core.server.transformer;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.message.impl.MessageInternalImpl;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerMessage;

/**
 * Do not use this class.  It is for backwards compatibility with Artemis 1.x only.
 */
@Deprecated
public class ServerMessageImpl extends MessageInternalImpl implements ServerMessage {

   private CoreMessage message;

   public boolean isValid() {
      return false;
   }

   @Override
   public ICoreMessage getICoreMessage() {
      return message;
   }

   public ServerMessageImpl(Message message) {
      super(message.toCore());
      this.message = (CoreMessage) message.toCore();
   }

   @Override
   public ServerMessage setMessageID(long id) {
      message.setMessageID(id);
      return this;
   }

   @Override
   public MessageReference createReference(Queue queue) {
      throw new UnsupportedOperationException();
   }

   /**
    * This will force encoding of the address, and will re-check the buffer. This is to avoid setMessageTransient which
    * set the address without changing the buffer
    */
   @Override
   public void forceAddress(SimpleString address) {
      message.setAddress(address);
   }

   @Override
   public int refUp() {
      throw new UnsupportedOperationException();
   }

   @Override
   public int refDown() {
      throw new UnsupportedOperationException();
   }

   @Override
   public int durableUp() {
      throw new UnsupportedOperationException();
   }

   @Override
   public int durableDown() {
      throw new UnsupportedOperationException();
   }

   @Override
   public ServerMessage copy(long newID) {
      throw new UnsupportedOperationException();
   }

   @Override
   public ServerMessage copy() {
      throw new UnsupportedOperationException();
   }

   @Override
   public int getMemoryEstimate() {
      return message.getMemoryEstimate();
   }

   @Override
   public int getRefCount() {
      return message.getRefCount();
   }

   @Override
   public ServerMessage makeCopyForExpiryOrDLA(long newID,
                                               MessageReference originalReference,
                                               boolean expiry,
                                               boolean copyOriginalHeaders) throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public void setOriginalHeaders(ServerMessage otherServerMessage, MessageReference originalReference, boolean expiry) {

      ICoreMessage other = otherServerMessage.getICoreMessage();

      SimpleString originalQueue = other.getSimpleStringProperty(Message.HDR_ORIGINAL_QUEUE);

      if (originalQueue != null) {
         message.putStringProperty(Message.HDR_ORIGINAL_QUEUE, originalQueue);
      } else if (originalReference != null) {
         message.putStringProperty(Message.HDR_ORIGINAL_QUEUE, originalReference.getQueue().getName());
      }

      if (other.containsProperty(Message.HDR_ORIG_MESSAGE_ID)) {
         message.putStringProperty(Message.HDR_ORIGINAL_ADDRESS, other.getSimpleStringProperty(Message.HDR_ORIGINAL_ADDRESS));

         message.putLongProperty(Message.HDR_ORIG_MESSAGE_ID, other.getLongProperty(Message.HDR_ORIG_MESSAGE_ID));
      } else {
         message.putStringProperty(Message.HDR_ORIGINAL_ADDRESS, SimpleString.of(other.getAddress()));

         message.putLongProperty(Message.HDR_ORIG_MESSAGE_ID, other.getMessageID());
      }

      // reset expiry
      message.setExpiration(0);

      if (expiry) {
         long actualExpiryTime = System.currentTimeMillis();

         message.putLongProperty(Message.HDR_ACTUAL_EXPIRY_TIME, actualExpiryTime);
      }

      // TODO ASk clebert
      //message.bufferValid = false;
   }

   @Override
   public void setPagingStore(PagingStore store) {
      throw new UnsupportedOperationException();
   }

   @Override
   public PagingStore getPagingStore() {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean storeIsPaging() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void encodeMessageIDToBuffer() {
      throw new UnsupportedOperationException();
   }

   @Override
   public byte[] getDuplicateIDBytes() {
      return message.getDuplicateIDBytes();
   }

   @Override
   public Object getDuplicateProperty() {
      return message.getDuplicateProperty();
   }
}
