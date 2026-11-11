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
package org.apache.activemq.artemis.core.persistence.impl.nullpm;

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

class NullStorageLargeServerMessage extends CoreMessage implements CoreLargeServerMessage {

   StorageManager storageManager;
   NullStorageLargeServerMessage() {
      super();
   }

   NullStorageLargeServerMessage(NullStorageLargeServerMessage other) {
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
   public Message toMessage() {
      return this;
   }

   @Override
   public synchronized void addBytes(final byte[] bytes) {
      if (buffer == null) {
         buffer = Unpooled.buffer(bytes.length);
      }

      // expand the buffer
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

      // expand the buffer
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
      // nothing to be done here.. we don really have a file on this Storage
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
      return "NullStorageLargeServerMessage[messageID=" + messageID + ", durable=" + durable + ", address=" + getAddress() + ",properties=" + properties + "]";
   }

   @Override
   public ICoreMessage copy() {
      // This is a simple copy, used only to avoid changing original properties
      return new NullStorageLargeServerMessage(this);
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
