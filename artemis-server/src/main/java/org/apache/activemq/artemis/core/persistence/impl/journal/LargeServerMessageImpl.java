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
package org.apache.activemq.artemis.core.persistence.impl.journal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.CoreLargeServerMessage;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public final class LargeServerMessageImpl extends CoreMessage implements CoreLargeServerMessage {

   // Given that LargeBody is never null it needs to be accounted on this instance footprint.
   // This value has been computed using https://github.com/openjdk/jol
   // with HotSpot 64-bit COOPS 8-byte align
   private static final int MEMORY_OFFSET = 112 + LargeBody.MEMORY_OFFSET;

   private static final int CHUNK_LM_SIZE = 100 * 1024;

   @Override
   public Message toMessage() {
      return this;
   }

   // When a message is stored on the journal, it will contain some header and trail on the journal
   // we need to take that into consideration if that would fit the Journal TimedBuffer.
   private static final int ESTIMATE_RECORD_TRAIL = 512;

   private final LargeBody largeBody;

   /**
    * This will check if a regular message needs to be converted as large message
    */
   public static Message checkLargeMessage(Message message, StorageManager storageManager) throws Exception {
      if (message.isLargeMessage()) {
         return message; // nothing to be done on this case
      }

      if (message.getEncodeSize() + ESTIMATE_RECORD_TRAIL > storageManager.getMaxRecordSize()) {
         return asLargeMessage(message, storageManager);
      } else {
         return message;
      }
   }

   private static Message asLargeMessage(Message message, StorageManager storageManager) throws Exception {
      ICoreMessage coreMessage = message.toCore();
      long id = storageManager.generateID();
      if (logger.isDebugEnabled()) {
         logger.debug("asLargeMessage create largeMessage with id={}", id);
      }
      LargeServerMessage lsm = storageManager.createCoreLargeMessage(id, coreMessage);
      ActiveMQBuffer messageBodyBuffer = coreMessage.getReadOnlyBodyBuffer();
      final int readableBytes = messageBodyBuffer.readableBytes();

      // I'm creating a native buffer here
      // because FileChannelImpl (which is used by NIOSequentialFile) would create a Ghost Native Buffer
      // that we would have no control. that's usually stored in a ThreadLocal within the native layer.
      // to avoid that buffer be kept in memory holding resources we will allocate our own buffer here from the NettyPool.
      // ./soakTest/OWLeakTest was written to validate this scenario here.
      ByteBuf ioBuffer = PooledByteBufAllocator.DEFAULT.ioBuffer(CHUNK_LM_SIZE, CHUNK_LM_SIZE);
      ActiveMQBuffer wrappedIOBuffer = new ChannelBufferWrapper(ioBuffer);

      try {

         // We write in chunks to avoid allocating a full NativeBody sized as the message size
         // which might lead the broker out of resources
         while (messageBodyBuffer.readableBytes() > 0) {
            wrappedIOBuffer.clear(); // equivalent to setting writingIndex=readerIndex=0;
            int bytesToRead = Math.min(CHUNK_LM_SIZE, messageBodyBuffer.readableBytes());
            messageBodyBuffer.readBytes(wrappedIOBuffer, 0, bytesToRead);
            wrappedIOBuffer.writerIndex(bytesToRead);
            lsm.addBytes(wrappedIOBuffer);
         }
      } finally {
         lsm.releaseResources(true, true);
         ioBuffer.release();
      }

      if (!coreMessage.containsProperty(Message.HDR_LARGE_BODY_SIZE)) {
         lsm.toMessage().putLongProperty(Message.HDR_LARGE_BODY_SIZE, readableBytes);
      }

      return lsm.toMessage();
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   private final StorageManager storageManager;

   public long getBodySize() throws ActiveMQException {
      return largeBody.getBodySize();
   }

   private void checkDebug() {
      if (isRefDebugEnabled()) {
         registerDebug();
      }
   }

   public LargeServerMessageImpl(final StorageManager storageManager) {
      largeBody = new LargeBody(this, storageManager);
      this.storageManager = storageManager;
   }

   /**
    * Copy constructor
    */
   public LargeServerMessageImpl(final LargeServerMessageImpl copy,
                                  TypedProperties properties,
                                  final SequentialFile fileCopy,
                                  final long newID) {
      super(copy, properties);
      storageManager = copy.storageManager;
      largeBody = new LargeBody(this, storageManager, fileCopy);
      largeBody.setBodySize(copy.largeBody.getStoredBodySize());
      setMessageID(newID);
   }

   public LargeServerMessageImpl(byte type,
                                  long id,
                                  StorageManager storageManager,
                                  final SequentialFile fileCopy) {
      super();
      this.storageManager = storageManager;
      setMessageID(id);
      setType(type);
      largeBody = new LargeBody(this, storageManager, fileCopy);
   }

   private static String toDate(long timestamp) {
      if (timestamp == 0) {
         return "0";
      } else {
         return new java.util.Date(timestamp).toString();
      }
   }

   @Override
   public LargeServerMessageImpl setMessageID(long messageID) {
      super.setMessageID(messageID);
      checkDebug();
      return this;
   }

   @Override
   public Message getMessage() {
      return this;
   }

   @Override
   public StorageManager getStorageManager() {
      return storageManager;
   }

   @Override
   public boolean isServerMessage() {
      return true;
   }

   @Override
   public void setPaged() {
      super.setPaged();
      largeBody.setPaged();
   }

   @Override
   public void addBytes(final byte[] bytes) throws Exception {
      synchronized (largeBody) {
         largeBody.addBytes(bytes);
      }
   }

   @Override
   public void addBytes(final ActiveMQBuffer bytes, boolean initialHeader) throws Exception {
      synchronized (largeBody) {
         largeBody.addBytes(bytes);
      }
   }

   @Override
   public int getEncodeSize() {
      synchronized (largeBody) {
         return getHeadersAndPropertiesEncodeSize();
      }
   }


   @Override
   public long getWholeMessageSize() {
      try {
         return getEncodeSize() + largeBody.getBodySize();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         return getEncodeSize();
      }
   }

   public void encode(final ActiveMQBuffer buffer1) {
      super.encodeHeadersAndProperties(buffer1.byteBuf());
   }

   public void decode(final ActiveMQBuffer buffer1) {
      largeBody.clearFile();
      super.decodeHeadersAndProperties(buffer1.byteBuf());
   }

   @Override
   public LargeBodyReader getLargeBodyReader() {
      return largeBody.getLargeBodyReader();
   }


   @Override
   protected void releaseComplete() {
      largeBody.releaseComplete();
   }

   // Even though not recommended, in certain instances
   // we may need to convert a large message back to a whole buffer
   // in a way you can convert
   @Override
   public ActiveMQBuffer getReadOnlyBodyBuffer() {

      return largeBody.getReadOnlyBodyBuffer();
   }

   @Override
   public int getBodyBufferSize() {
      return largeBody.getBodyBufferSize();
   }

   @Override
   public boolean isLargeMessage() {
      return true;
   }

   @Override
   public void deleteFile() throws Exception {
      released();
      synchronized (largeBody) {
         largeBody.deleteFile();
      }
   }

   @Override
   public int getMemoryEstimate() {
      synchronized (largeBody) {
         if (memoryEstimate == -1) {
            // The body won't be on memory (always on-file), so we don't consider this for paging
            memoryEstimate = MEMORY_OFFSET +
               getHeadersAndPropertiesEncodeSize() +
               DataConstants.SIZE_INT +
               getEncodeSize() +
               (16 + 4) * 2 + 1;
         }

         return memoryEstimate;
      }
   }

   @Override
   public void releaseResources(boolean sync, boolean sendEvent) {
      synchronized (largeBody) {
         largeBody.releaseResources(sync, sendEvent);
      }
   }

   @Override
   public boolean isOpen() {
      synchronized (largeBody) {
         try {
            return largeBody.getAppendFile().isOpen();
         } catch (Throwable e) {
            return false;
         }
      }
   }


   @Override
   public void referenceOriginalMessage(final Message original, final SimpleString originalQueue) {

      super.referenceOriginalMessage(original, originalQueue);

      if (original instanceof LargeServerMessageImpl largeServerMessage) {
         this.largeBody.referenceOriginalMessage(largeServerMessage.largeBody);
      }
   }

   @Override
   public void setStorageManager(StorageManager storageManager) {
      this.largeBody.setStorageManager(storageManager);
   }

   @Override
   public ICoreMessage copy() {
      SequentialFile newfile = storageManager.createFileForLargeMessage(messageID, durable);
      LargeServerMessageImpl newMessage = new LargeServerMessageImpl(this, properties, newfile, messageID);
      newMessage.setParentRef(this);
      return newMessage;
   }

   @Override
   public LargeBody getLargeBody() {
      return largeBody;
   }

   @Override
   public Message copy(final long newID) {
      try {
         if (logger.isDebugEnabled()) {
            logger.debug("Copy large message id={} as newID={}", this.getMessageID(), newID);
         }
         LargeServerMessage newMessage = storageManager.createCoreLargeMessage(newID, this);
         largeBody.copyInto(newMessage);
         newMessage.releaseResources(true, true);
         return newMessage.toMessage();

      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.lareMessageErrorCopying(this, e);
         return null;
      }
   }

   @Override
   public SequentialFile getAppendFile() throws ActiveMQException {
      return largeBody.getAppendFile();
   }

   @Override
   public long getPersistentSize() throws ActiveMQException {
      long size = super.getPersistentSize();
      size += getBodySize();

      return size;

   }

   @Override
   public String toString() {
      try {
         return "LargeServerMessage[messageID=" + messageID + ",durable=" + isDurable() + ",userID=" + getUserID() + ",priority=" + this.getPriority() + ", timestamp=" + toDate(getTimestamp()) + ",expiration=" + toDate(getExpiration()) + ", durable=" + durable + ", address=" + getAddress() + ", properties=" + (properties != null ? properties.toString() : "") + "]@" + System.identityHashCode(this);
      } catch (Exception e) {
         e.printStackTrace();
         return "LargeServerMessage[messageID=" + messageID + "]";
      }
   }
}
