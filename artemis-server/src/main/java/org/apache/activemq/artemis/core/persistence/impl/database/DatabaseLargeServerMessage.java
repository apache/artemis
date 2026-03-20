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
package org.apache.activemq.artemis.core.persistence.impl.database;

import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeBody;
import org.apache.activemq.artemis.core.server.CoreLargeServerMessage;

class DatabaseLargeServerMessage extends CoreMessage implements CoreLargeServerMessage {

   private StorageManager storageManager;

   private volatile CoreMessage convertedMessage;

   DatabaseLargeServerMessage() {
      super();
   }

   DatabaseLargeServerMessage(DatabaseLargeServerMessage other) {
      super(other);
   }

   @Override
   public void releaseResources(boolean sync, boolean sendEvent) {
   }

   @Override
   public boolean isOpen() {
      return false;
   }

   @Override
   public LargeBody getLargeBody() {
      return null;
   }

   @Override
   public StorageManager getStorageManager() {
      return storageManager;
   }

   @Override
   public synchronized Message toMessage() {
      if (convertedMessage != null) {
         return convertedMessage;
      }

      CoreMessage coreMessage = new CoreMessage();
      coreMessage.initBuffer(buffer != null ? buffer.writerIndex() + 512 : 512);
      coreMessage.setMessageID(messageID);
      coreMessage.setAddress(getAddress());
      coreMessage.setUserID(getUserID());
      coreMessage.setType(getType());
      coreMessage.setDurable(isDurable());
      coreMessage.setExpiration(getExpiration());
      coreMessage.setTimestamp(getTimestamp());
      coreMessage.setPriority(getPriority());

      if (properties != null) {
         properties.forEach(coreMessage::putObjectProperty);
      }

      if (buffer != null && buffer.writerIndex() > 0) {
         coreMessage.getBodyBuffer().writeBytes(buffer, 0, buffer.writerIndex());
      }

      convertedMessage = coreMessage;
      return coreMessage;
   }

   @Override
   public synchronized void addBytes(final byte[] bytes) {
      if (buffer == null) {
         buffer = Unpooled.buffer(bytes.length);
      }

      buffer.writeBytes(bytes);
   }

   @Override
   public Message getMessage() {
      return this;
   }

   @Override
   public void setStorageManager(StorageManager storageManager) {
      this.storageManager = storageManager;
   }

   @Override
   public synchronized void addBytes(ActiveMQBuffer bytes, boolean initialHeader) {
      final int readableBytes = bytes.readableBytes();
      if (buffer == null) {
         buffer = Unpooled.buffer(readableBytes);
      }

      buffer.ensureWritable(readableBytes);
      assert buffer.hasArray();
      final int writerIndex = buffer.writerIndex();
      bytes.readBytes(buffer.array(), buffer.arrayOffset() + writerIndex, readableBytes);
      buffer.writerIndex(writerIndex + readableBytes);
   }

   @Override
   public synchronized ActiveMQBuffer getReadOnlyBodyBuffer() {
      return new ChannelBufferWrapper(buffer.slice(0, buffer.writerIndex()).asReadOnly());
   }

   @Override
   public synchronized int getBodyBufferSize() {
      return buffer.writerIndex();
   }

   @Override
   public void deleteFile() throws Exception {
      released();
   }

   @Override
   public boolean isLargeMessage() {
      return true;
   }

   @Override
   public boolean isServerMessage() {
      return true;
   }

   @Override
   public synchronized int getEncodeSize() {
      return getHeadersAndPropertiesEncodeSize();
   }

   @Override
   public String toString() {
      return "DatabaseLargeServerMessage[messageID=" + messageID + ", durable=" + durable + ", address=" + getAddress() + ",properties=" + properties + "]";
   }

   @Override
   public ICoreMessage copy() {
      return new DatabaseLargeServerMessage(this);
   }

   @Override
   public void setPaged() {
      super.setPaged();
   }

   @Override
   public SequentialFile getAppendFile() {
      return null;
   }
}
