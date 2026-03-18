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

package org.apache.activemq.artemis.protocol.amqp.broker;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeBody;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.codec.CompositeReadableBuffer;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.TypeConstructor;
import org.apache.qpid.proton.codec.WritableBuffer;

public class AMQPLargeMessage extends AMQPMessage implements LargeServerMessage {

   @Override
   public ICoreMessage toCore(CoreMessageObjectPools coreMessageObjectPools) {

      try {
         AMQPStandardMessage standardMessage = new AMQPStandardMessage(messageFormat, getData().array(), extraProperties, coreMessageObjectPools);
         if (this.getExpiration() > 0) {
            standardMessage.reloadExpiration(this.getExpiration());
         }
         standardMessage.setMessageAnnotations(messageAnnotations);
         standardMessage.setMessageID(messageID);
         standardMessage.setPriority(getPriority());

         return standardMessage.toCore();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public Message getMessage() {
      return this;
   }

   private boolean reencoded = false;

   /**
    * AMQPLargeMessagePersister will save the buffer here.
    */
   private ByteBuf temporaryBuffer;

   private final LargeBody largeBody;
   /**
    * We control durability on a separate property here, as we need to know if it's durable ahead of the header parsing.
    * This will be the case when restarting a server
    */
   private Boolean fileDurable;

   private StorageManager storageManager;

   /**
    * this is used to parse the initial packets from the buffer
    */
   private CompositeReadableBuffer parsingBuffer;

   private void checkDebug() {
      if (isRefDebugEnabled()) {
         registerDebug();
      }
   }

   public AMQPLargeMessage(long id,
                           long messageFormat,
                           TypedProperties extraProperties,
                           CoreMessageObjectPools coreMessageObjectPools,
                           StorageManager storageManager) {
      super(messageFormat, extraProperties, coreMessageObjectPools);
      this.setMessageID(id);
      largeBody = new LargeBody(this, storageManager);
      this.storageManager = storageManager;
      checkDebug();
   }

   public AMQPLargeMessage(long id,
                           long messageFormat,
                           TypedProperties extraProperties,
                           CoreMessageObjectPools coreMessageObjectPools,
                           StorageManager storageManager,
                           LargeBody largeBody) {
      super(messageFormat, extraProperties, coreMessageObjectPools);
      this.setMessageID(id);
      this.largeBody = largeBody;
      this.storageManager = storageManager;
      checkDebug();
   }

   /**
    * Copy constructor
    */
   private AMQPLargeMessage(final AMQPLargeMessage copy,
                                  final SequentialFile fileCopy,
                                  final long newID) {
      super(copy);
      largeBody = new LargeBody(this, copy.largeBody.getStorageManager(), fileCopy);
      largeBody.setBodySize(copy.largeBody.getStoredBodySize());
      this.storageManager = copy.largeBody.getStorageManager();
      this.reencoded = copy.reencoded;
      setMessageID(newID);
      checkDebug();
   }

   public void releaseEncodedBuffer() {
      internalReleaseBuffer(1);
   }

   /**
    * {@link #getSavedEncodeBuffer()} will retain two counters from the buffer, one meant for the call, and one that
    * must be released only after encoding.
    * <p>
    * This method is meant to be called when the buffer is actually encoded on the journal, meaning both refs are gone.
    * and the actual buffer can be released.
    */
   public void releaseEncodedBufferAfterWrite() {
      internalReleaseBuffer(2);
   }

   /**
    * This method check the reference for specifics on protocolData.
    *
    * It was written to check the deliveryAnnotationsForSendBuffer and eventually move it to the protocolData.
    */
   public void checkReference(MessageReference reference) {
      if (reference.getProtocolData(DeliveryAnnotations.class) == null && deliveryAnnotationsForSendBuffer != null) {
         reference.setProtocolData(DeliveryAnnotations.class, deliveryAnnotationsForSendBuffer);
      }
   }

   /**
    * during large message deliver, we need this calculation to place a new delivery annotation
    */
   public int getPositionAfterDeliveryAnnotations() {
      return encodedHeaderSize + encodedDeliveryAnnotationsSize;
   }

   private void internalReleaseBuffer(int releases) {
      synchronized (largeBody) {
         for (int i = 0; i < releases; i++) {
            if (temporaryBuffer != null && temporaryBuffer.release()) {
               temporaryBuffer = null;
            }
         }
      }
   }

   /**
    * This is used on test assertions to make sure the buffers are released corrected
    */
   public ByteBuf inspectTemporaryBuffer() {
      return temporaryBuffer;
   }

   public ByteBuf getSavedEncodeBuffer() {
      synchronized (largeBody) {
         if (temporaryBuffer == null) {
            temporaryBuffer = PooledByteBufAllocator.DEFAULT.buffer(getEstimateSavedEncode());
            saveEncoding(temporaryBuffer);
         }
         return temporaryBuffer.retain(1);
      }
   }

   private void saveEncoding(ByteBuf buf) {
      WritableBuffer oldBuffer = TLSEncode.getEncoder().getBuffer();

      TLSEncode.getEncoder().setByteBuffer(new NettyWritable(buf));

      try {
         buf.writeInt(headerPosition);
         buf.writeInt(encodedHeaderSize);
         TLSEncode.getEncoder().writeObject(header);

         buf.writeInt(deliveryAnnotationsPosition);
         buf.writeInt(encodedDeliveryAnnotationsSize);

         buf.writeInt(messageAnnotationsPosition);
         TLSEncode.getEncoder().writeObject(messageAnnotations);


         buf.writeInt(propertiesPosition);
         TLSEncode.getEncoder().writeObject(properties);

         buf.writeInt(applicationPropertiesPosition);
         buf.writeInt(remainingBodyPosition);

         TLSEncode.getEncoder().writeObject(applicationProperties);

      } finally {
         TLSEncode.getEncoder().setByteBuffer(oldBuffer);
      }
   }

   protected void readSavedEncoding(ByteBuf buf) {
      ReadableBuffer oldBuffer = TLSEncode.getDecoder().getBuffer();

      TLSEncode.getDecoder().setBuffer(new NettyReadable(buf));

      try {
         messageDataScanned = MessageDataScanningStatus.SCANNED.code;

         headerPosition = buf.readInt();
         encodedHeaderSize = buf.readInt();
         header = (Header)TLSEncode.getDecoder().readObject();

         // Recover message priority from saved encoding as we store that separately
         if (header != null && header.getPriority() != null) {
            priority = (byte) Math.min(header.getPriority().byteValue(), MAX_MESSAGE_PRIORITY);
         }

         deliveryAnnotationsPosition = buf.readInt();
         encodedDeliveryAnnotationsSize = buf.readInt();

         messageAnnotationsPosition = buf.readInt();
         messageAnnotations = (MessageAnnotations)TLSEncode.getDecoder().readObject();

         propertiesPosition = buf.readInt();
         properties = (Properties)TLSEncode.getDecoder().readObject();

         applicationPropertiesPosition = buf.readInt();
         remainingBodyPosition = buf.readInt();

         applicationProperties = (ApplicationProperties)TLSEncode.getDecoder().readObject();

         if (properties != null && properties.getAbsoluteExpiryTime() != null && properties.getAbsoluteExpiryTime().getTime() > 0) {
            if (!expirationReload) {
               expiration = properties.getAbsoluteExpiryTime().getTime();
            }
         } else if (header != null && header.getTtl() != null) {
            if (!expirationReload) {
               expiration = System.currentTimeMillis() + header.getTtl().intValue();
            }
         }
      } finally {
         TLSEncode.getDecoder().setBuffer(oldBuffer);
      }
   }

   public void setFileDurable(boolean value) {
      this.fileDurable = value;
   }

   @Override
   public StorageManager getStorageManager() {
      return largeBody.getStorageManager();
   }

   @Override
   public void setStorageManager(StorageManager storageManager) {
      largeBody.setStorageManager(storageManager);
      this.storageManager = storageManager;
   }

   @Override
   public final boolean isDurable() {
      if (fileDurable != null) {
         return fileDurable.booleanValue();
      } else {
         return super.isDurable();
      }
   }

   @Override
   public ReadableBuffer getData() {
      LargeBodyReader reader = largeBody.getLargeBodyReader();

      try {
         long size = reader.getSize();
         if (size > Integer.MAX_VALUE) {
            throw new RuntimeException("AMQP Large Message Body is too large to be read into memory");
         }
         byte[] buffer = new byte[(int) size];
         ByteBuffer wrapbuffer = ByteBuffer.wrap(buffer);

         reader.open();
         reader.readInto(wrapbuffer);
         return new ReadableBuffer.ByteBufferReader(wrapbuffer.rewind());
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      } finally {
         try {
            reader.close();
         } catch (Exception ignored) {
            logger.debug(ignored.getMessage(), ignored);
         }
      }
   }

   public void parseHeader(ReadableBuffer buffer) {
      DecoderImpl decoder = TLSEncode.getDecoder();
      decoder.setBuffer(buffer);

      try {
         TypeConstructor<?> constructor = decoder.readConstructor();
         if (Header.class.equals(constructor.getTypeClass())) {
            header = (Header) constructor.readValue();
            if (header.getTtl() != null) {
               if (!expirationReload) {
                  expiration = System.currentTimeMillis() + header.getTtl().intValue();
               }
            }
            if (header.getPriority() != null) {
               priority = (byte) Math.min(header.getPriority().intValue(), MAX_MESSAGE_PRIORITY);
            }
         }
      } finally {
         decoder.setBuffer(null);
         buffer.rewind();
      }
   }

   public void addBytes(ReadableBuffer data) throws Exception {
      parseLargeMessage(data);

      final int remaining = data.remaining();
      final ByteBuf writeBuffer = PooledByteBufAllocator.DEFAULT.directBuffer(remaining, remaining);
      try {
         // perform copy of data
         data.get(new NettyWritable(writeBuffer));
         largeBody.addBytes(new ChannelBufferWrapper(writeBuffer, true, true));
      } finally {
         writeBuffer.release();
      }
   }

   protected void parseLargeMessage(ActiveMQBuffer data, boolean initialHeader) {
      MessageDataScanningStatus status = getDataScanningStatus();
      if (status == MessageDataScanningStatus.NOT_SCANNED) {
         ByteBuf buffer = data.byteBuf().duplicate();
         if (parsingBuffer == null) {
            parsingBuffer = new CompositeReadableBuffer();
         }
         byte[] parsingData = new byte[buffer.readableBytes()];
         buffer.readBytes(parsingData);

         parsingBuffer.append(parsingData);
         if (!initialHeader) {
            genericParseLargeMessage();
         }
      }
   }

   protected void parseLargeMessage(byte[] data, boolean initialHeader) {
      MessageDataScanningStatus status = getDataScanningStatus();
      if (status == MessageDataScanningStatus.NOT_SCANNED) {
         byte[] copy = new byte[data.length];
         System.arraycopy(data, 0, copy, 0, data.length);
         if (parsingBuffer == null) {
            parsingBuffer = new CompositeReadableBuffer();
         }

         parsingBuffer.append(copy);
         if (!initialHeader) {
            genericParseLargeMessage();
         }
      }
   }

   private void genericParseLargeMessage() {
      try {
         parsingBuffer.position(0);
         scanMessageData(parsingBuffer);
         lazyDecodeApplicationProperties(parsingBuffer);
         parsingBuffer = null;
      } catch (RuntimeException expected) {
         // this would mean the buffer is not complete yet, so we keep parsing it, until we can get enough bytes
         logger.debug("The buffer for AMQP Large Message was probably not complete, so an exception eventually would be expected", expected);
      }
   }

   protected void parseLargeMessage(ReadableBuffer data) {
      MessageDataScanningStatus status = getDataScanningStatus();
      if (status == MessageDataScanningStatus.NOT_SCANNED) {
         if (parsingBuffer == null) {
            parsingBuffer = new CompositeReadableBuffer();
         }

         parsingBuffer.append(data.duplicate());
         genericParseLargeMessage();
      }
   }

   @Override
   public Message toMessage() {
      return this;
   }

   @Override
   public void addBytes(byte[] bytes) throws Exception {
      parseLargeMessage(bytes, false);
      largeBody.addBytes(bytes);
   }

   @Override
   public void addBytes(ActiveMQBuffer bytes, boolean initialHeader) throws Exception {
      parseLargeMessage(bytes, initialHeader);
      largeBody.addBytes(bytes);

   }

   @Override
   public void setPaged() {
      super.setPaged();
      largeBody.setPaged();
   }

   @Override
   public void releaseResources(boolean sync, boolean sendEvent) {
      largeBody.releaseResources(sync, sendEvent);
   }

   @Override
   public boolean isOpen() {
      try {
         return largeBody.getAppendFile().isOpen();
      } catch (Throwable e) {
         return false;
      }
   }

   @Override
   public void deleteFile() throws Exception {
      disableErrorCheck(); // if LargeServerMessage.DEBUG this will make sure this message is not reported
      largeBody.deleteFile();
   }

   @Override
   public SequentialFile getAppendFile() throws ActiveMQException {
      return largeBody.getAppendFile();
   }

   @Override
   public boolean isLargeMessage() {
      return true;
   }

   @Override
   public LargeBodyReader getLargeBodyReader() {
      return largeBody.getLargeBodyReader();
   }

   @Override
   public LargeBody getLargeBody() {
      return largeBody;
   }

   @Override
   protected void releaseComplete() {
      largeBody.releaseComplete();
   }

   @Override
   public Message copy() {
      SequentialFile newfile = largeBody.createFile();
      AMQPLargeMessage newMessage = new AMQPLargeMessage(this, newfile, messageID);
      newMessage.setParentRef(this);
      newMessage.setFileDurable(this.isDurable());
      newMessage.reloadExpiration(this.expiration);
      if (priority != AMQPMessage.DEFAULT_MESSAGE_PRIORITY) {
         newMessage.setPriority(priority);
      }
      return newMessage;
   }

   @Override
   public Message copy(final long newID) {
      return copy(newID, false);
   }

   @Override
   public Message copy(final long newID, boolean isDLQOrExpiry) {
      try {
         AMQPLargeMessage copy = new AMQPLargeMessage(newID, messageFormat, null, coreMessageObjectPools, storageManager);
         copy.setDurable(this.isDurable());
         if (priority != AMQPMessage.DEFAULT_MESSAGE_PRIORITY) {
            copy.setPriority(priority);
         }

         final AtomicInteger place = new AtomicInteger(0);
         ByteBuf bufferNewHeader = null;
         if (isDLQOrExpiry) {
            bufferNewHeader = newHeaderWithoutExpiry(place);
         }

         largeBody.copyInto(copy, bufferNewHeader, place.intValue());
         copy.releaseResources(true, true);
         return copy;

      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.lareMessageErrorCopying(this, e);
         return null;
      }
   }

   protected ByteBuf newHeaderWithoutExpiry(AtomicInteger placeOutput) {
      ByteBuf bufferNewHeader;
      Header headerCopy = null;
      if (header != null) {
         headerCopy = new Header(header);
         headerCopy.setTtl(null); // just in case
      }

      MessageAnnotations messageAnnotationsRef = this.messageAnnotations;

      Properties propertiesCopy = null;
      if (properties != null) {
         propertiesCopy = new Properties(properties);
         propertiesCopy.setAbsoluteExpiryTime(null); // just in case
      }

      if (applicationPropertiesPosition != VALUE_NOT_PRESENT) {
         placeOutput.set(applicationPropertiesPosition);
      } else {
         placeOutput.set(remainingBodyPosition);
      }

      if (placeOutput.get() < 0) {
         placeOutput.set(0);
         bufferNewHeader = null;
      } else {
         bufferNewHeader = Unpooled.buffer(placeOutput.get());
      }

      if (bufferNewHeader != null) {
         TLSEncode.getEncoder().setByteBuffer(new NettyWritable(bufferNewHeader));
         if (headerCopy != null) {
            TLSEncode.getEncoder().writeObject(headerCopy);
         }
         if (messageAnnotationsRef != null) {
            TLSEncode.getEncoder().writeObject(messageAnnotationsRef);
         }
         if (propertiesCopy != null) {
            TLSEncode.getEncoder().writeObject(propertiesCopy);
         }
      }
      return bufferNewHeader;
   }

   @Override
   public void messageChanged() {

   }

   @Override
   public int getEncodeSize() {
      return 0;
   }

   @Override
   public long getWholeMessageSize() {
      try {
         return largeBody.getBodySize();
      } catch (Exception e) {
         logger.warn(e.getMessage());
         return -1;
      }
   }


   @Override
   public synchronized int getMemoryEstimate() {
      if (memoryEstimate == -1) {
         memoryEstimate = memoryOffset * 2 + (extraProperties != null ? extraProperties.getEncodeSize() : 0);
         originalEstimate = memoryEstimate;
      }
      return memoryEstimate;
   }

   @Override
   public void persist(ActiveMQBuffer targetRecord) {

   }

   @Override
   public int getPersistSize() {
      return 0;
   }

   @Override
   public void reloadPersistence(ActiveMQBuffer record, CoreMessageObjectPools pools) {

   }

   @Override
   public long getPersistentSize() {
      try {
         return largeBody.getBodySize();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         return 0;
      }
   }

   @Override
   public Persister<Message> getPersister() {
      return AMQPLargeMessagePersister.getInstance();
   }

   @Override
   public void reencode() {
      reencoded = true;
   }

   public void setReencoded(boolean reencoded) {
      this.reencoded = reencoded;
   }

   public boolean isReencoded() {
      return reencoded;
   }

   @Override
   protected void ensureDataIsValid() {

   }

   @Override
   protected void encodeMessage() {
   }

}
