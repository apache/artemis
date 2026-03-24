/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.message.impl;

import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.buffers.impl.ResetLimitWrappedActiveMQBuffer;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.message.openmbean.CompositeDataConstants;
import org.apache.activemq.artemis.core.message.openmbean.MessageOpenTypeFactory;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.jboss.logging.Logger;

import static org.apache.activemq.artemis.utils.ByteUtil.ensureExactWritable;

/** Note: you shouldn't change properties using multi-threads. Change your properties before you can send it to multiple
 *  consumers */
public class CoreMessage extends RefCountMessage implements ICoreMessage {

   public static final int BUFFER_HEADER_SPACE = PacketImpl.PACKET_HEADERS_SIZE;

   protected volatile int memoryEstimate = -1;

   private static final Logger logger = Logger.getLogger(CoreMessage.class);

   // There's an integer with the number of bytes for the body
   public static final int BODY_OFFSET = DataConstants.SIZE_INT;

   /** That is the readInto for the whole message, including properties..
       it does not include the buffer for the Packet send and receive header on core protocol */
   protected ByteBuf buffer;

   private volatile boolean validBuffer = false;

   protected volatile ResetLimitWrappedActiveMQBuffer writableBuffer;

   protected int endOfBodyPosition = -1;

   protected int messageIDPosition = -1;

   protected long messageID;

   protected SimpleString address;

   protected byte type;

   protected boolean durable;

   /**
    * GMT milliseconds at which this message expires. 0 means never expires *
    */
   private long expiration;

   protected long timestamp;

   protected byte priority;

   private UUID userID;

   private int propertiesLocation = -1;

   protected volatile TypedProperties properties;

   private final CoreMessageObjectPools coreMessageObjectPools;

   private volatile Object owner;

   public CoreMessage(final CoreMessageObjectPools coreMessageObjectPools) {
      this.coreMessageObjectPools = coreMessageObjectPools;
   }

   public CoreMessage() {
      this.coreMessageObjectPools = null;
   }

   /** On core there's no delivery annotation */
   @Override
   public Object getAnnotation(SimpleString key) {
      return getObjectProperty(key);
   }

   /** On core there's no delivery annotation */
   @Override
   public Object removeAnnotation(SimpleString key) {
      return removeProperty(key);
   }

   @Override
   public void clearInternalProperties() {
      final TypedProperties properties = this.properties;
      if (properties != null && properties.clearInternalProperties()) {
         messageChanged();
      }
   }

   @Override
   public Persister<Message> getPersister() {
      return CoreMessagePersister.getInstance();
   }

   public CoreMessage initBuffer(final int initialMessageBufferSize) {
      buffer = Unpooled.buffer(initialMessageBufferSize);

      // There's a bug in netty which means a dynamic buffer won't resize until you write a byte
      buffer.writeByte((byte) 0);

      buffer.setIndex(BODY_OFFSET, BODY_OFFSET);

      return this;
   }

   @Override
   public SimpleString getReplyTo() {
      return getSimpleStringProperty(MessageUtil.REPLYTO_HEADER_NAME);
   }

   @Override
   public RoutingType getRoutingType() {
      final Byte maybeByte = getProperties().getByteProperty(Message.HDR_ROUTING_TYPE, () -> null);
      if (maybeByte == null) {
         return null;
      }
      return RoutingType.getType(maybeByte);
   }

   @Override
   public Message setRoutingType(RoutingType routingType) {
      if (routingType == null) {
         removeProperty(Message.HDR_ROUTING_TYPE);
      } else {
         putByteProperty(Message.HDR_ROUTING_TYPE, routingType.getType());
      }
      return this;
   }

   @Override
   public CoreMessage setReplyTo(SimpleString address) {

      if (address == null) {
         getProperties().removeProperty(MessageUtil.REPLYTO_HEADER_NAME);
      } else {
         putStringProperty(MessageUtil.REPLYTO_HEADER_NAME, address);
      }
      return this;
   }

   @Override
   public void receiveBuffer(ByteBuf buffer) {
      this.buffer = buffer;
      this.buffer.retain();
      decode(false);
   }

   /** This will fix the incoming body of 1.x messages */
   @Override
   public void receiveBuffer_1X(ByteBuf buffer) {
      this.buffer = buffer;
      this.buffer.retain();
      decode(true);
      validBuffer = false;
   }

   @Override
   public ActiveMQBuffer getReadOnlyBodyBuffer() {
      checkEncode();
      return new ChannelBufferWrapper(buffer.slice(BODY_OFFSET, endOfBodyPosition - BUFFER_HEADER_SPACE).setIndex(0, endOfBodyPosition - BUFFER_HEADER_SPACE).asReadOnly());
   }

   @Override
   public int getBodyBufferSize() {
      checkEncode();
      return endOfBodyPosition - BUFFER_HEADER_SPACE;
   }

   /**
    * This will return the proper buffer to represent the data of the Message. If compressed it will decompress.
    * If large, it will read from the file or streaming.
    * @return
    */
   @Override
   public ActiveMQBuffer getDataBuffer() {

      ActiveMQBuffer buffer;

      try {
         if (isLargeMessage()) {
            buffer = getLargeMessageBuffer();
         } else {
            buffer = getReadOnlyBodyBuffer();
         }

         if (Boolean.TRUE.equals(getBooleanProperty(Message.HDR_LARGE_COMPRESSED))) {
            buffer = inflate(buffer);
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         return getReadOnlyBodyBuffer();
      }

      return buffer;
   }

   private ActiveMQBuffer getLargeMessageBuffer() throws ActiveMQException {
      LargeBodyReader encoder = getLargeBodyReader();
      encoder.open();
      int bodySize = (int) encoder.getSize();
      final ActiveMQBuffer buffer = new ChannelBufferWrapper(UnpooledByteBufAllocator.DEFAULT.heapBuffer(bodySize));
      buffer.byteBuf().ensureWritable(bodySize);
      final ByteBuffer nioBuffer = buffer.byteBuf().internalNioBuffer(0, bodySize);
      encoder.readInto(nioBuffer);
      buffer.writerIndex(bodySize);
      encoder.close();
      return buffer;
   }

   private ActiveMQBuffer inflate(ActiveMQBuffer buffer) throws DataFormatException {
      final int bytesToRead = buffer.readableBytes();
      Inflater inflater = new Inflater();
      final byte[] input = new byte[bytesToRead];
      buffer.readBytes(input);
      inflater.setInput(input);

      //get the real size of large message
      long sizeBody = getLongProperty(Message.HDR_LARGE_BODY_SIZE);

      byte[] data = new byte[(int) sizeBody];
      inflater.inflate(data);
      inflater.end();
      ActiveMQBuffer qbuff = ActiveMQBuffers.wrappedBuffer(data);
      qbuff.resetReaderIndex();
      qbuff.resetWriterIndex();
      qbuff.writeBytes(data);
      buffer = qbuff;
      return buffer;
   }

   @Override
   public SimpleString getGroupID() {
      return this.getSimpleStringProperty(Message.HDR_GROUP_ID);
   }

   @Override
   public CoreMessage setGroupID(SimpleString groupId) {
      return this.putStringProperty(Message.HDR_GROUP_ID, groupId);
   }

   @Override
   public CoreMessage setGroupID(String groupId) {
      return this.setGroupID(SimpleString.toSimpleString(groupId, coreMessageObjectPools == null ? null : coreMessageObjectPools.getGroupIdStringSimpleStringPool()));
   }

   @Override
   public int getGroupSequence() {
      return containsProperty(Message.HDR_GROUP_SEQUENCE) ? getIntProperty(Message.HDR_GROUP_SEQUENCE) : 0;
   }

   @Override
   public CoreMessage setGroupSequence(int sequence) {
      return this.putIntProperty(Message.HDR_GROUP_SEQUENCE, sequence);
   }

   @Override
   public Object getCorrelationID() {
      return getObjectProperty(MessageUtil.CORRELATIONID_HEADER_NAME);
   }

   @Override
   public Message setCorrelationID(final Object correlationID) {
      putObjectProperty(MessageUtil.CORRELATIONID_HEADER_NAME, correlationID);
      return this;
   }

   /**
    * @param sendBuffer
    * @param deliveryCount Some protocols (AMQP) will have this as part of the message. ignored on core
    */
   @Override
   public synchronized void sendBuffer(ByteBuf sendBuffer, int deliveryCount) {
      checkEncode();
      sendBuffer.writeBytes(buffer, 0, buffer.writerIndex());
   }

   /**
    * Recast the message as an 1.4 message
    */
   @Override
   public synchronized void sendBuffer_1X(ByteBuf sendBuffer) {
      checkEncode();
      ByteBuf tmpBuffer = buffer.duplicate();
      sendBuffer.writeInt(endOfBodyPosition + DataConstants.SIZE_INT);
      tmpBuffer.readerIndex(DataConstants.SIZE_INT);
      tmpBuffer.readBytes(sendBuffer, endOfBodyPosition - BUFFER_HEADER_SPACE);
      sendBuffer.writeInt(tmpBuffer.writerIndex() + DataConstants.SIZE_INT + BUFFER_HEADER_SPACE);
      tmpBuffer.readBytes(sendBuffer, tmpBuffer.readableBytes());
      sendBuffer.readerIndex(0);
   }

   protected synchronized void checkEncode() {
      if (!validBuffer) {
         encode();
      }
      internalWritableBuffer();
   }

   @Override
   public Long getScheduledDeliveryTime() {
      Object property = getProperties().getProperty(Message.HDR_SCHEDULED_DELIVERY_TIME);

      if (property != null && property instanceof Number) {
         return ((Number) property).longValue();
      }

      return 0L;
   }

   @Override
   public CoreMessage setScheduledDeliveryTime(Long time) {
      if (time == null || time == 0) {
         getProperties().removeProperty(Message.HDR_SCHEDULED_DELIVERY_TIME);
      } else {
         putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time);
      }
      return this;
   }

   @Override
   public InputStream getBodyInputStream() {
      return null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ActiveMQBuffer getBodyBuffer() {
      // if using the writable buffer, we must parse properties
      getProperties();

      internalWritableBuffer();

      return writableBuffer;
   }

   private void internalWritableBuffer() {
      if (writableBuffer == null) {
         synchronized (this) {
            if (writableBuffer == null) {
               ResetLimitWrappedActiveMQBuffer writableBuffer = new ResetLimitWrappedActiveMQBuffer(BODY_OFFSET, buffer.duplicate(), this);
               if (endOfBodyPosition > 0) {
                  writableBuffer.byteBuf().setIndex(BODY_OFFSET, endOfBodyPosition - BUFFER_HEADER_SPACE + BODY_OFFSET);
                  writableBuffer.resetReaderIndex();
               }
               this.writableBuffer = writableBuffer;
            }
         }
      }
   }

   @Override
   public int getEndOfBodyPosition() {
      if (endOfBodyPosition < 0) {
         endOfBodyPosition = getBodyBuffer().writerIndex();
      }
      return endOfBodyPosition;
   }

   @Override
   public synchronized void messageChanged() {
      //a volatile store is a costly operation: better to check if is necessary
      if (validBuffer) {
         validBuffer = false;
      }
   }

   protected CoreMessage(CoreMessage other) {
      this(other, other.properties);
   }

   public CoreMessage(long id, int bufferSize) {
      this(id, bufferSize, null);
   }

   public CoreMessage(long id, int bufferSize, CoreMessageObjectPools coreMessageObjectPools) {
      this.initBuffer(bufferSize);
      this.setMessageID(id);
      this.coreMessageObjectPools = coreMessageObjectPools;
   }

   protected CoreMessage(CoreMessage other, TypedProperties copyProperties) {
      // This MUST be synchronized using the monitor on the other message to prevent it running concurrently
      // with getEncodedBuffer(), otherwise can introduce race condition when delivering concurrently to
      // many subscriptions and bridging to other nodes in a cluster
      synchronized (other) {
         this.endOfBodyPosition = other.endOfBodyPosition;
         internalSetMessageID(other.messageID);
         this.address = other.address;
         this.type = other.type;
         this.durable = other.durable;
         this.expiration = other.expiration;
         this.timestamp = other.timestamp;
         this.priority = other.priority;
         this.userID = other.userID;
         this.coreMessageObjectPools = other.coreMessageObjectPools;
         if (copyProperties != null) {
            this.properties = new TypedProperties(copyProperties);
         }
         if (other.buffer != null) {
            this.buffer = other.buffer.copy();
         }
      }
   }

   /** This method serves as a purpose of extension.
    *   Large Message on a Core Message will have to set the messageID on the attached NewLargeMessage */
   protected void internalSetMessageID(final long messageID) {
      this.messageID = messageID;
   }

   @Override
   public void moveHeadersAndProperties(final Message msg) {
      internalSetMessageID(msg.getMessageID());
      address = msg.getAddressSimpleString();
      userID = (UUID) msg.getUserID();
      type = msg.toCore().getType();
      durable = msg.isDurable();
      expiration = msg.getExpiration();
      timestamp = msg.getTimestamp();
      priority = msg.getPriority();

      if (msg instanceof CoreMessage) {
         properties = new TypedProperties(((CoreMessage) msg).getProperties());
      }
   }

   @Override
   public Message copy() {
      getProperties();
      checkEncode();
      return new CoreMessage(this);
   }

   @Override
   public Message copy(long newID) {
      return copy().setMessageID(newID);
   }

   @Override
   public long getExpiration() {
      return expiration;
   }

   @Override
   public long getTimestamp() {
      return timestamp;
   }

   @Override
   public CoreMessage setTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
   }

   @Override
   public long getMessageID() {
      return messageID;
   }

   @Override
   public byte getPriority() {
      return priority;
   }

   @Override
   public UUID getUserID() {
      return userID;
   }

   @Override
   public CoreMessage setUserID(Object uuid) {
      this.userID = (UUID) uuid;
      return this;
   }

   @Override
   public String getValidatedUserID() {
      return getStringProperty(Message.HDR_VALIDATED_USER);
   }

   @Override
   public CoreMessage setValidatedUserID(String validatedUserID) {
      putStringProperty(Message.HDR_VALIDATED_USER, value(validatedUserID));
      return this;
   }

   @Override
   public CoreMessage setMessageID(long messageID) {
      internalSetMessageID(messageID);
      if (messageIDPosition >= 0 && validBuffer) {
         buffer.setLong(messageIDPosition, messageID);
      }
      return this;
   }

   @Override
   public CoreMessage setAddress(SimpleString address) {
      if (address == null && this.address == null) {
         // no-op so just return
         return this;
      }
      if (validBuffer && (address == null || !address.equals(this.address))) {
         messageChanged();
      }
      this.address = address;
      return this;
   }

   @Override
   public SimpleString getAddressSimpleString() {
      return address;
   }

   @Override
   public CoreMessage setExpiration(long expiration) {
      this.expiration = expiration;
      messageChanged();
      return this;
   }

   @Override
   public CoreMessage setPriority(byte priority) {
      this.priority = priority;
      messageChanged();
      return this;
   }

   public CoreMessage setUserID(UUID userID) {
      this.userID = userID;
      messageChanged();
      return this;
   }

   /**
    * I am keeping this synchronized as the decode of the Properties is lazy
    */
   public final TypedProperties getProperties() {
      TypedProperties properties = this.properties;
      if (properties == null) {
         properties = getOrInitializeTypedProperties();
      }
      return properties;
   }

   private synchronized TypedProperties getOrInitializeTypedProperties() {
      try {
         TypedProperties properties = this.properties;
         if (properties == null) {
            properties = new TypedProperties(INTERNAL_PROPERTY_NAMES_PREDICATE);
            if (buffer != null && propertiesLocation >= 0) {
               final ByteBuf byteBuf = buffer.duplicate().readerIndex(propertiesLocation);
               properties.decode(byteBuf, coreMessageObjectPools == null ? null : coreMessageObjectPools.getPropertiesDecoderPools());
            }
            this.properties = properties;
         }
         return properties;
      } catch (Throwable e) {
         throw onCheckPropertiesError(e);
      }
   }

   private RuntimeException onCheckPropertiesError(Throwable e) {
      // This is not an expected error, hence no specific logger created
      logger.warn("Could not decode properties for CoreMessage[messageID=" + messageID + ",durable=" + durable + ",userID=" + userID + ",priority=" + priority +
                     ", timestamp=" + timestamp + ",expiration=" + expiration + ",address=" + address + ", propertiesLocation=" + propertiesLocation, e);
      final ByteBuf buffer = this.buffer;
      if (buffer != null) {
         //risky: a racy modification to buffer indexes could break this duplicate operation
         final ByteBuf duplicatebuffer = buffer.duplicate();
         duplicatebuffer.readerIndex(0);
         logger.warn("Failed message has messageID=" + messageID + " and the following buffer:\n" + ByteBufUtil.prettyHexDump(duplicatebuffer));
      } else {
         logger.warn("Failed message has messageID=" + messageID + " and the buffer was null");
      }
      return new RuntimeException(e.getMessage(), e);
   }

   @Override
   public int getMemoryEstimate() {
      if (memoryEstimate == -1) {
         if (buffer != null && !isLargeMessage()) {
            if (!validBuffer) {
               // this can happen if a message is modified
               // eg clustered messages get additional routing information
               // that need to be correctly accounted in memory
               checkEncode();
            }
         }
         final TypedProperties properties = this.properties;
         memoryEstimate = memoryOffset +
            (buffer != null ? buffer.capacity() : 0) +
            (properties != null ? properties.getMemoryOffset() : 0);
      }

      return memoryEstimate;
   }

   @Override
   public boolean isServerMessage() {
      // even though CoreMessage is used both on server and client
      // callers are interested in knowing if this is a server large message
      // as it will be used to send the body from the files.
      //
      // this may need further refactoring when we improve large messages
      // and expose that functionality to other protocols.
      return false;
   }

   @Override
   public byte getType() {
      return type;
   }

   @Override
   public CoreMessage setType(byte type) {
      this.type = type;
      return this;
   }

   private void decode(boolean beforeAddress) {
      decode(beforeAddress, coreMessageObjectPools);
   }

   private void decode(boolean beforeAddress, CoreMessageObjectPools pools) {
      endOfBodyPosition = buffer.readInt();

      buffer.skipBytes(endOfBodyPosition - BUFFER_HEADER_SPACE);

      decodeHeadersAndProperties(buffer, true, pools);
      buffer.readerIndex(0);
      validBuffer = true;

      if (beforeAddress) {
         endOfBodyPosition = endOfBodyPosition - DataConstants.SIZE_INT;
      }

      internalWritableBuffer();
   }

   public void decodeHeadersAndProperties(final ByteBuf buffer) {
      decodeHeadersAndProperties(buffer, false, coreMessageObjectPools);
   }

   private void decodeHeadersAndProperties(final ByteBuf buffer, boolean lazyProperties, CoreMessageObjectPools pools) {
      messageIDPosition = buffer.readerIndex();
      internalSetMessageID(buffer.readLong());

      address = SimpleString.readNullableSimpleString(buffer, pools == null ? null : pools.getAddressDecoderPool());
      if (buffer.readByte() == DataConstants.NOT_NULL) {
         byte[] bytes = new byte[16];
         buffer.readBytes(bytes);
         userID = new UUID(UUID.TYPE_TIME_BASED, bytes);
      } else {
         userID = null;
      }
      type = buffer.readByte();
      durable = buffer.readBoolean();
      expiration = buffer.readLong();
      timestamp = buffer.readLong();
      priority = buffer.readByte();
      if (lazyProperties) {
         properties = null;
         propertiesLocation = buffer.readerIndex();
      } else {
         properties = new TypedProperties(INTERNAL_PROPERTY_NAMES_PREDICATE);
         properties.decode(buffer, pools == null ? null : pools.getPropertiesDecoderPools());
      }
   }

   public synchronized CoreMessage encode() {

      getProperties();

      if (writableBuffer != null) {
         // The message encode takes into consideration the PacketImpl which is not part of this encoding
         // so we always need to take the BUFFER_HEADER_SPACE from packet impl into consideration
         endOfBodyPosition = writableBuffer.writerIndex() + BUFFER_HEADER_SPACE - 4;
      } else if (endOfBodyPosition <= 0) {
         endOfBodyPosition = BUFFER_HEADER_SPACE + DataConstants.SIZE_INT;
      }

      buffer.setInt(0, endOfBodyPosition);
      // The end of body position
      buffer.setIndex(0, endOfBodyPosition - BUFFER_HEADER_SPACE + DataConstants.SIZE_INT);

      encodeHeadersAndProperties(buffer);

      validBuffer = true;

      return this;
   }

   public void encodeHeadersAndProperties(final ByteBuf buffer) {
      final TypedProperties properties = getProperties();
      final int initialWriterIndex = buffer.writerIndex();
      messageIDPosition = initialWriterIndex;
      final UUID userID = this.userID;
      final int userIDEncodedSize = userID == null ? Byte.BYTES : Byte.BYTES + userID.asBytes().length;
      final SimpleString address = this.address;
      final int addressEncodedBytes = SimpleString.sizeofNullableString(address);
      final int headersSize =
         Long.BYTES +                                    // messageID
         addressEncodedBytes +                           // address
         userIDEncodedSize +                             // userID
         Byte.BYTES +                                    // type
         Byte.BYTES +                                    // durable
         Long.BYTES +                                    // expiration
         Long.BYTES +                                    // timestamp
         Byte.BYTES;                                     // priority
      synchronized (properties) {
         final int propertiesEncodeSize = properties.getEncodeSize();
         final int totalEncodedSize = headersSize + propertiesEncodeSize;
         ensureExactWritable(buffer, totalEncodedSize);
         buffer.writeLong(messageID);
         SimpleString.writeNullableSimpleString(buffer, address);
         if (userID == null) {
            buffer.writeByte(DataConstants.NULL);
         } else {
            buffer.writeByte(DataConstants.NOT_NULL);
            buffer.writeBytes(userID.asBytes());
         }
         buffer.writeByte(type);
         buffer.writeBoolean(durable);
         buffer.writeLong(expiration);
         buffer.writeLong(timestamp);
         buffer.writeByte(priority);
         assert buffer.writerIndex() == initialWriterIndex + headersSize : "Bad Headers encode size estimation";
         final int realPropertiesEncodeSize = properties.encode(buffer);
         assert realPropertiesEncodeSize == propertiesEncodeSize : "TypedProperties has a wrong encode size estimation or is being modified concurrently";
      }
   }

   @Override
   public int getHeadersAndPropertiesEncodeSize() {
      return DataConstants.SIZE_LONG + // Message ID
         DataConstants.SIZE_BYTE + // user id null?
         (userID == null ? 0 : 16) +
             /* address */SimpleString.sizeofNullableString(address) +
         DataConstants./* Type */SIZE_BYTE +
         DataConstants./* Durable */SIZE_BOOLEAN +
         DataConstants./* Expiration */SIZE_LONG +
         DataConstants./* Timestamp */SIZE_LONG +
         DataConstants./* Priority */SIZE_BYTE +
             /* PropertySize and Properties */getProperties().getEncodeSize();
   }

   @Override
   public Object getDuplicateProperty() {
      return getObjectProperty(Message.HDR_DUPLICATE_DETECTION_ID);
   }

   @Override
   public SimpleString getLastValueProperty() {
      return getSimpleStringProperty(Message.HDR_LAST_VALUE_NAME);
   }

   @Override
   public Message setLastValueProperty(SimpleString lastValueName) {
      return putStringProperty(Message.HDR_LAST_VALUE_NAME, lastValueName);
   }

   @Override
   public int getEncodeSize() {
      if (buffer == null) {
         return -1;
      }
      checkEncode();
      return buffer.writerIndex();
   }

   @Override
   public boolean isLargeMessage() {
      return false;
   }

   @Override
   public String getAddress() {
      if (address == null) {
         return null;
      } else {
         return address.toString();
      }
   }

   @Override
   public CoreMessage setAddress(String address) {
      messageChanged();
      this.address = SimpleString.toSimpleString(address, coreMessageObjectPools == null ? null : coreMessageObjectPools.getAddressStringSimpleStringPool());
      return this;
   }

   @Override
   public CoreMessage setBuffer(ByteBuf buffer) {
      this.buffer = buffer;

      return this;
   }

   @Override
   public ByteBuf getBuffer() {
      return buffer;
   }

   @Override
   public boolean isDurable() {
      return durable;
   }

   @Override
   public CoreMessage setDurable(boolean durable) {
      messageChanged();
      this.durable = durable;
      return this;
   }

   @Override
   public CoreMessage putBooleanProperty(final String key, final boolean value) {
      return putBooleanProperty(key(key), value);
   }

   @Override
   public CoreMessage putBooleanProperty(final SimpleString key, final boolean value) {
      messageChanged();
      getProperties().putBooleanProperty(key, value);
      return this;
   }

   @Override
   public Boolean getBooleanProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getProperties().getBooleanProperty(key);
   }

   @Override
   public Boolean getBooleanProperty(final String key) throws ActiveMQPropertyConversionException {
      return getBooleanProperty(key(key));
   }

   @Override
   public CoreMessage putByteProperty(final SimpleString key, final byte value) {
      messageChanged();
      getProperties().putByteProperty(key, value);
      return this;
   }

   @Override
   public CoreMessage putByteProperty(final String key, final byte value) {
      return putByteProperty(key(key), value);
   }

   @Override
   public Byte getByteProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getProperties().getByteProperty(key);
   }

   @Override
   public Byte getByteProperty(final String key) throws ActiveMQPropertyConversionException {
      return getByteProperty(key(key));
   }

   @Override
   public CoreMessage putBytesProperty(final SimpleString key, final byte[] value) {
      messageChanged();
      getProperties().putBytesProperty(key, value);
      return this;
   }

   @Override
   public CoreMessage putBytesProperty(final String key, final byte[] value) {
      return putBytesProperty(key(key), value);
   }

   @Override
   public byte[] getBytesProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getProperties().getBytesProperty(key);
   }

   @Override
   public byte[] getBytesProperty(final String key) throws ActiveMQPropertyConversionException {
      return getBytesProperty(key(key));
   }

   @Override
   public CoreMessage putCharProperty(SimpleString key, char value) {
      messageChanged();
      getProperties().putCharProperty(key, value);
      return this;
   }

   @Override
   public CoreMessage putCharProperty(String key, char value) {
      return putCharProperty(key(key), value);
   }

   @Override
   public CoreMessage putShortProperty(final SimpleString key, final short value) {
      messageChanged();
      getProperties().putShortProperty(key, value);
      return this;
   }

   @Override
   public CoreMessage putShortProperty(final String key, final short value) {
      return putShortProperty(key(key), value);
   }

   @Override
   public CoreMessage putIntProperty(final SimpleString key, final int value) {
      messageChanged();
      getProperties().putIntProperty(key, value);
      return this;
   }

   @Override
   public CoreMessage putIntProperty(final String key, final int value) {
      return putIntProperty(key(key), value);
   }

   @Override
   public Integer getIntProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getProperties().getIntProperty(key);
   }

   @Override
   public Integer getIntProperty(final String key) throws ActiveMQPropertyConversionException {
      return getIntProperty(key(key));
   }

   @Override
   public CoreMessage putLongProperty(final SimpleString key, final long value) {
      messageChanged();
      getProperties().putLongProperty(key, value);
      return this;
   }

   @Override
   public CoreMessage putLongProperty(final String key, final long value) {
      return putLongProperty(key(key), value);
   }

   @Override
   public Long getLongProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getProperties().getLongProperty(key);
   }

   @Override
   public Long getLongProperty(final String key) throws ActiveMQPropertyConversionException {
      return getLongProperty(key(key));
   }

   @Override
   public CoreMessage putFloatProperty(final SimpleString key, final float value) {
      messageChanged();
      getProperties().putFloatProperty(key, value);
      return this;
   }

   @Override
   public CoreMessage putFloatProperty(final String key, final float value) {
      return putFloatProperty(key(key), value);
   }

   @Override
   public CoreMessage putDoubleProperty(final SimpleString key, final double value) {
      messageChanged();
      getProperties().putDoubleProperty(key, value);
      return this;
   }

   @Override
   public CoreMessage putDoubleProperty(final String key, final double value) {
      return putDoubleProperty(key(key), value);
   }

   @Override
   public Double getDoubleProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getProperties().getDoubleProperty(key);
   }

   @Override
   public Double getDoubleProperty(final String key) throws ActiveMQPropertyConversionException {
      return getDoubleProperty(key(key));
   }

   @Override
   public CoreMessage putStringProperty(final SimpleString key, final SimpleString value) {
      messageChanged();
      getProperties().putSimpleStringProperty(key, value);
      return this;
   }

   @Override
   public CoreMessage putStringProperty(final SimpleString key, final String value) {
      return putStringProperty(key, value(value));
   }

   @Override
   public CoreMessage putStringProperty(final String key, final String value) {
      return putStringProperty(key(key), value(value));
   }

   @Override
   public CoreMessage putObjectProperty(final SimpleString key,
                                        final Object value) throws ActiveMQPropertyConversionException {
      messageChanged();
      TypedProperties.setObjectProperty(key, value, getProperties());
      return this;
   }

   @Override
   public Object getObjectProperty(final String key) {
      return getObjectProperty(key(key));
   }

   @Override
   public Object getObjectProperty(final SimpleString key) {
      return getProperties().getProperty(key);
   }

   @Override
   public CoreMessage putObjectProperty(final String key, final Object value) throws ActiveMQPropertyConversionException {
      return putObjectProperty(key(key), value);
   }

   @Override
   public Short getShortProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getProperties().getShortProperty(key);
   }

   @Override
   public Short getShortProperty(final String key) throws ActiveMQPropertyConversionException {
      return getShortProperty(key(key));
   }

   @Override
   public Float getFloatProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getProperties().getFloatProperty(key);
   }

   @Override
   public Float getFloatProperty(final String key) throws ActiveMQPropertyConversionException {
      return getFloatProperty(key(key));
   }

   @Override
   public String getStringProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      SimpleString str = getSimpleStringProperty(key);

      if (str == null) {
         return null;
      } else {
         return str.toString();
      }
   }

   @Override
   public String getStringProperty(final String key) throws ActiveMQPropertyConversionException {
      return getStringProperty(key(key));
   }

   @Override
   public SimpleString getSimpleStringProperty(final SimpleString key) throws ActiveMQPropertyConversionException {
      return getProperties().getSimpleStringProperty(key);
   }

   @Override
   public SimpleString getSimpleStringProperty(final String key) throws ActiveMQPropertyConversionException {
      return getSimpleStringProperty(key(key));
   }

   @Override
   public Object removeProperty(final SimpleString key) {
      Object oldValue = getProperties().removeProperty(key);
      if (oldValue != null) {
         messageChanged();
      }
      return oldValue;
   }

   @Override
   public Object removeProperty(final String key) {
      return removeProperty(key(key));
   }

   @Override
   public boolean hasScheduledDeliveryTime() {
      return searchProperty(Message.HDR_SCHEDULED_DELIVERY_TIME);
   }

   /**
    * Differently from {@link #containsProperty(SimpleString)}, this method can save decoding the message,
    * performing a search of the {@code key} property and falling back to {@link #containsProperty(SimpleString)}
    * if not possible or if already decoded.
    */
   public boolean searchProperty(SimpleString key) {
      Objects.requireNonNull(key, "key cannot be null");
      TypedProperties properties = this.properties;
      if (properties != null) {
         return properties.containsProperty(key);
      }
      synchronized (this) {
         final ByteBuf buffer = this.buffer;
         // acquiring the lock here, although heavy-weight, is the safer way to do this,
         // because we cannot trust that a racing thread won't modify buffer
         if (buffer == null) {
            throw new NullPointerException("buffer cannot be null");
         }
         final int propertiesLocation = this.propertiesLocation;
         if (propertiesLocation < 0) {
            throw new IllegalStateException("propertiesLocation = " + propertiesLocation);
         }
         return TypedProperties.searchProperty(key, buffer, propertiesLocation);
      }
   }

   @Override
   public boolean containsProperty(final SimpleString key) {
      return getProperties().containsProperty(key);
   }

   @Override
   public boolean containsProperty(final String key) {
      return containsProperty(key(key));
   }

   @Override
   public Set<SimpleString> getPropertyNames() {
      return getProperties().getPropertyNames();
   }

   @Override
   public LargeBodyReader getLargeBodyReader() throws ActiveMQException {
      return new CoreLargeBodyReaderImpl();
   }

   private final class CoreLargeBodyReaderImpl implements LargeBodyReader {

      private int lastPos = 0;

      private CoreLargeBodyReaderImpl() {
      }

      @Override
      public void open() {
      }

      @Override
      public void position(long position) throws ActiveMQException {
         lastPos = (int)position;
      }

      @Override
      public long position() {
         return lastPos;
      }

      @Override
      public void close() {
      }

      @Override
      public long getSize() {
         return buffer.writerIndex();
      }

      @Override
      public int readInto(final ByteBuffer bufferRead) {
         final int remaining = bufferRead.remaining();
         buffer.getBytes(lastPos, bufferRead);
         lastPos += remaining;
         return remaining;
      }
   }

   @Override
   public int getPersistSize() {
      checkEncode();
      return buffer.writerIndex() + DataConstants.SIZE_INT;
   }

   @Override
   public void persist(ActiveMQBuffer targetRecord) {
      checkEncode();
      targetRecord.writeInt(buffer.writerIndex());
      targetRecord.writeBytes(buffer, 0, buffer.writerIndex());
   }

   @Override
   public void reloadPersistence(ActiveMQBuffer record, CoreMessageObjectPools pools) {
      int size = record.readInt();
      initBuffer(size);
      buffer.setIndex(0, 0).writeBytes(record.byteBuf(), size);
      decode(false, pools);
   }

   @Override
   public CoreMessage toCore() {
      return this;
   }


   @Override
   public CoreMessage toCore(CoreMessageObjectPools coreMessageObjectPools) {
      return this;
   }

   @Override
   public String toString() {
      try {
         final TypedProperties properties = getProperties();
         return "CoreMessage[messageID=" + messageID + ",durable=" + isDurable() + ",userID=" + getUserID() + ",priority=" + this.getPriority()  +
            ", timestamp=" + toDate(getTimestamp()) + ",expiration=" + toDate(getExpiration()) +
            ", durable=" + durable + ", address=" + getAddress() + ",size=" + getPersistentSize() + ",properties=" + properties + "]@" + System.identityHashCode(this);
      } catch (Throwable e) {
         logger.warn("Error creating String for message: ", e);
         return "ServerMessage[messageID=" + messageID + "]";
      }
   }

   private static String toDate(long timestamp) {
      if (timestamp == 0) {
         return "0";
      } else {
         return new java.util.Date(timestamp).toString();
      }
   }

   private SimpleString key(String key) {
      return SimpleString.toSimpleString(key, getPropertyKeysPool());
   }

   private SimpleString value(String value) {
      return SimpleString.toSimpleString(value, getPropertyValuesPool());
   }

   private SimpleString.StringSimpleStringPool getPropertyKeysPool() {
      return coreMessageObjectPools == null ? null : coreMessageObjectPools.getPropertiesStringSimpleStringPools().getPropertyKeysPool();
   }

   private SimpleString.StringSimpleStringPool getPropertyValuesPool() {
      return coreMessageObjectPools == null ? null : coreMessageObjectPools.getPropertiesStringSimpleStringPools().getPropertyValuesPool();
   }

   @Override
   public long getPersistentSize() throws ActiveMQException {
      return getEncodeSize();
   }

   @Override
   public Object getOwner() {
      return owner;
   }

   @Override
   public void setOwner(Object object) {
      this.owner = object;
   }

   @Override
   public String getStringBody() {
      String body = null;

      if (type == TEXT_TYPE) {
         try {
            SimpleString simpleBody = getDataBuffer().readNullableSimpleString();
            if (simpleBody != null) {
               body = simpleBody.toString();
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      return body;
   }


   // *******************************************************************************************************************************
   // Composite Data implementation

   private static MessageOpenTypeFactory TEXT_FACTORY = new TextMessageOpenTypeFactory();
   private static MessageOpenTypeFactory BYTES_FACTORY = new BytesMessageOpenTypeFactory();


   @Override
   public CompositeData toCompositeData(int fieldsLimit, int deliveryCount) throws OpenDataException {
      CompositeType ct;
      Map<String, Object> fields;
      byte type = getType();
      switch (type) {
         case Message.TEXT_TYPE:
            ct = TEXT_FACTORY.getCompositeType();
            fields = TEXT_FACTORY.getFields(this, fieldsLimit, deliveryCount);
            break;
         default:
            ct = BYTES_FACTORY.getCompositeType();
            fields = BYTES_FACTORY.getFields(this, fieldsLimit, deliveryCount);
            break;
      }
      return new CompositeDataSupport(ct, fields);

   }

   static class BytesMessageOpenTypeFactory extends MessageOpenTypeFactory<CoreMessage> {
      protected ArrayType body;

      @Override
      protected void init() throws OpenDataException {
         super.init();
         body = new ArrayType(SimpleType.BYTE, true);
         addItem(CompositeDataConstants.BODY, CompositeDataConstants.BODY_DESCRIPTION, body);
      }

      @Override
      public Map<String, Object> getFields(CoreMessage m, int valueSizeLimit, int delivery) throws OpenDataException {
         Map<String, Object> rc = super.getFields(m, valueSizeLimit, delivery);
         rc.put(CompositeDataConstants.TYPE, m.getType());
         if (!m.isLargeMessage()) {
            ActiveMQBuffer bodyCopy = m.getReadOnlyBodyBuffer();
            int arraySize;
            if (valueSizeLimit == -1 || bodyCopy.readableBytes() <= valueSizeLimit) {
               arraySize = bodyCopy.readableBytes();
            } else {
               arraySize = valueSizeLimit;
            }
            byte[] bytes = new byte[arraySize];
            bodyCopy.readBytes(bytes);
            rc.put(CompositeDataConstants.BODY, JsonUtil.truncate(bytes, valueSizeLimit));
         } else {
            rc.put(CompositeDataConstants.BODY, new byte[0]);
         }
         return rc;
      }
   }

   static class TextMessageOpenTypeFactory extends MessageOpenTypeFactory<CoreMessage> {
      @Override
      protected void init() throws OpenDataException {
         super.init();
         addItem(CompositeDataConstants.TEXT_BODY, CompositeDataConstants.TEXT_BODY, SimpleType.STRING);
      }

      @Override
      public Map<String, Object> getFields(CoreMessage m, int valueSizeLimit, int delivery) throws OpenDataException {
         Map<String, Object> rc = super.getFields(m, valueSizeLimit, delivery);
         rc.put(CompositeDataConstants.TYPE, m.getType());
         if (!m.isLargeMessage()) {
            if (m.containsProperty(Message.HDR_LARGE_COMPRESSED)) {
               rc.put(CompositeDataConstants.TEXT_BODY, "[compressed]");
            } else {
               SimpleString text = m.getReadOnlyBodyBuffer().readNullableSimpleString();
               rc.put(CompositeDataConstants.TEXT_BODY, text != null ? JsonUtil.truncate(text.toString(), valueSizeLimit) : "");
            }
         } else {
            rc.put(CompositeDataConstants.TEXT_BODY, "[large message]");
         }
         return rc;
      }
   }

   // Composite Data implementation
   // *******************************************************************************************************************************

}
