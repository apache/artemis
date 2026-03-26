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
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.activemq.artemis.spi.core.protocol.EmbedMessageUtil;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Footer;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.WritableBuffer;

// see https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html#section-message-format
public class AMQPStandardMessage extends AMQPMessage {


   public static AMQPStandardMessage createMessage(long messageID,
                                                   long messageFormat,
                                                   SimpleString replyTo,
                                                   Header header,
                                                   Properties properties,
                                                   Map<Symbol, Object> daMap,
                                                   Map<Symbol, Object> maMap,
                                                   Map<String, Object> apMap,
                                                   Map<Symbol, Object> footerMap,
                                                   Section body) {
      ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);

      try {
         EncoderImpl encoder = TLSEncode.getEncoder();
         encoder.setByteBuffer(new NettyWritable(buffer));

         if (header != null) {
            encoder.writeObject(header);
         }
         if (daMap != null) {
            encoder.writeObject(new DeliveryAnnotations(daMap));
         }
         if (maMap != null) {
            encoder.writeObject(new MessageAnnotations(maMap));
         }
         if (properties != null) {
            encoder.writeObject(properties);
         }
         if (apMap != null) {
            encoder.writeObject(new ApplicationProperties(apMap));
         }
         if (body != null) {
            encoder.writeObject(body);
         }
         if (footerMap != null) {
            encoder.writeObject(new Footer(footerMap));
         }

         byte[] data = new byte[buffer.writerIndex()];
         buffer.readBytes(data);

         AMQPStandardMessage amqpMessage = new AMQPStandardMessage(messageFormat, data, null);
         amqpMessage.setMessageID(messageID);
         amqpMessage.setReplyTo(replyTo);
         return amqpMessage;

      } finally {
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
         buffer.release();
      }
   }

   // Buffer and state for the data backing this message.
   protected ReadableBuffer data;

   /**
    * Creates a new {@link AMQPStandardMessage} instance from binary encoded message data.
    *
    * @param messageFormat   The Message format tag given the in Transfer that carried this message
    * @param data            The encoded AMQP message
    * @param extraProperties Broker specific extra properties that should be carried with this message
    */
   public AMQPStandardMessage(long messageFormat, byte[] data, TypedProperties extraProperties) {
      this(messageFormat, data, extraProperties, null);
   }

   /**
    * Creates a new {@link AMQPStandardMessage} instance from binary encoded message data.
    *
    * @param messageFormat          The Message format tag given the in Transfer that carried this message
    * @param data                   The encoded AMQP message
    * @param extraProperties        Broker specific extra properties that should be carried with this message
    * @param coreMessageObjectPools Object pool used to accelerate some String operations.
    */
   public AMQPStandardMessage(long messageFormat,
                              byte[] data,
                              TypedProperties extraProperties,
                              CoreMessageObjectPools coreMessageObjectPools) {
      this(messageFormat, ReadableBuffer.ByteBufferReader.wrap(data), extraProperties, coreMessageObjectPools);
   }

   /**
    * Creates a new {@link AMQPStandardMessage} instance from binary encoded message data.
    *
    * @param messageFormat          The Message format tag given the in Transfer that carried this message
    * @param data                   The encoded AMQP message in an {@link ReadableBuffer} wrapper.
    * @param extraProperties        Broker specific extra properties that should be carried with this message
    * @param coreMessageObjectPools Object pool used to accelerate some String operations.
    */
   public AMQPStandardMessage(long messageFormat,
                              ReadableBuffer data,
                              TypedProperties extraProperties,
                              CoreMessageObjectPools coreMessageObjectPools) {
      super(messageFormat, extraProperties, coreMessageObjectPools);
      this.data = data;
      ensureMessageDataScanned();
   }

   /**
    * Internal constructor used for persistence reload of the message.
    * <p>
    * The message will not be usable until the persistence mechanism populates the message data and triggers a parse of
    * the message contents to fill in the message state.
    *
    * @param messageFormat The Message format tag given the in Transfer that carried this message
    */
   AMQPStandardMessage(long messageFormat) {
      super(messageFormat);
   }

   @Override
   public org.apache.activemq.artemis.api.core.Message copy() {
      ensureDataIsValid();

      ReadableBuffer view = data.duplicate().rewind();
      byte[] newData = new byte[view.remaining()];

      // Copy the full message contents with delivery annotations as they will
      // be trimmed on send and may become useful on the broker at a later time.
      view.get(newData);

      AMQPStandardMessage newEncode = new AMQPStandardMessage(this.messageFormat, newData, extraProperties, coreMessageObjectPools);
      newEncode.setMessageID(this.getMessageID());
      return newEncode;
   }

   @Override
   public int getEncodeSize() {
      ensureDataIsValid();
      // The encoded size will exclude any delivery annotations that are present as we will clip them.
      return data.remaining() - encodedDeliveryAnnotationsSize + getDeliveryAnnotationsForSendBufferSize();
   }

   @Override
   protected ReadableBuffer getData() {
      return data;
   }

   @Override
   public synchronized int getMemoryEstimate() {
      if (memoryEstimate == VALUE_NOT_PRESENT) {
         // This estimation was tested and validated through AMQPGlobalMaxTest on soak-tests
         memoryEstimate = MINIMUM_ESTIMATE + (data != null ? data.capacity() + getApplicationPropertiesEncodingSize(data) * 2 + getApplicationPropertiesCount() * DataConstants.SIZE_INT : 0);
      }

      return memoryEstimate;
   }

   private int getApplicationPropertiesEncodingSize(ReadableBuffer data) {
      ensureScanning();

      // if still VALUE_NOT_PRESENT, it means we have no application properties, we must return 0
      if (applicationPropertiesPosition == VALUE_NOT_PRESENT) {
         return 0;
      } else if (remainingBodyPosition != VALUE_NOT_PRESENT) {
         return remainingBodyPosition - applicationPropertiesPosition;
      } else {
         return data.capacity() - applicationPropertiesPosition;
      }
   }


   @Override
   public void persist(ActiveMQBuffer targetRecord) {
      ensureDataIsValid();
      targetRecord.writeInt(internalPersistSize());
      if (data.hasArray()) {
         targetRecord.writeBytes(data.array(), data.arrayOffset(), data.remaining());
      } else {
         targetRecord.writeBytes(data.byteBuffer());
      }
   }

   @Override
   public final org.apache.activemq.artemis.api.core.Message copy(long newID) {
      return copy().setMessageID(newID);
   }

   @Override
   public int getPersistSize() {
      ensureDataIsValid();
      return DataConstants.SIZE_INT + internalPersistSize();
   }

   @Override
   public void reloadPersistence(ActiveMQBuffer record, CoreMessageObjectPools pools) {
      int size = record.readInt();
      byte[] recordArray = new byte[size];
      record.readBytes(recordArray);
      data = ReadableBuffer.ByteBufferReader.wrap(ByteBuffer.wrap(recordArray));

      // Message state is now that the underlying buffer is loaded, but the contents not yet scanned
      resetMessageData();

      modified = false;
      messageDataScanned = MessageDataScanningStatus.NOT_SCANNED.code;
   }

   @Override
   public long getPersistentSize() throws ActiveMQException {
      return getEncodeSize();
   }

   @Override
   public Persister<org.apache.activemq.artemis.api.core.Message> getPersister() {
      return AMQPMessagePersisterV4.getInstance();
   }

   @Override
   public Persister<Message> getWireCompatiblePersister(int wireVersion) {
      if (wireVersion == EmbedMessageUtil.NO_MAP_WIRE_VERSION) {
         return AMQPMessagePersisterV3.getInstance();
      } else {
         return AMQPMessagePersisterV4.getInstance();
      }
   }

   @Override
   public void reencode() {
      ensureMessageDataScanned();

      // The address was updated on a message with Properties so we update them
      // for cases where there are no properties we aren't adding a properties
      // section which seems wrong but this preserves previous behavior.
      if (properties != null && address != null) {
         properties.setTo(address.toString());
      }

      encodeMessage();
      scanMessageData();
   }

   @Override
   protected synchronized void ensureDataIsValid() {
      if (modified) {
         encodeMessage();
      }
   }

   @Override
   protected synchronized void encodeMessage() {
      this.modified = false;
      this.messageDataScanned = MessageDataScanningStatus.NOT_SCANNED.code;
      int estimated = Math.max(1500, data != null ? data.capacity() + 1000 : 0);
      ByteBuf buffer = PooledByteBufAllocator.DEFAULT.directBuffer(estimated);
      EncoderImpl encoder = TLSEncode.getEncoder();

      try {
         NettyWritable writable = new NettyWritable(buffer);

         encoder.setByteBuffer(writable);
         if (header != null) {
            encoder.writeObject(header);
         }

         // We currently do not encode any delivery annotations but it is conceivable
         // that at some point they may need to be preserved, this is where that needs
         // to happen.

         if (messageAnnotations != null) {
            encoder.writeObject(messageAnnotations);
         }
         if (properties != null) {
            encoder.writeObject(properties);
         }

         // Whenever possible avoid encoding sections we don't need to by
         // checking if application properties where loaded or added and
         // encoding only in that case.
         if (applicationProperties != null) {
            encoder.writeObject(applicationProperties);

            // Now raw write the remainder body and footer if present.
            if (data != null && remainingBodyPosition != VALUE_NOT_PRESENT) {
               writable.put(data.position(remainingBodyPosition));
            }
         } else if (data != null && applicationPropertiesPosition != VALUE_NOT_PRESENT) {
            // Writes out ApplicationProperties, Body and Footer in one go if present.
            writable.put(data.position(applicationPropertiesPosition));
         } else if (data != null && remainingBodyPosition != VALUE_NOT_PRESENT) {
            // No Application properties at all so raw write Body and Footer sections
            writable.put(data.position(remainingBodyPosition));
         }

         byte[] bytes = new byte[buffer.writerIndex()];

         buffer.readBytes(bytes);
         data = ReadableBuffer.ByteBufferReader.wrap(ByteBuffer.wrap(bytes));
      } finally {
         encoder.setByteBuffer((WritableBuffer) null);
         buffer.release();
      }
   }

   @Override
   public String toString() {
      // toString will only call ensureScanning on regular messages
      // as large messages might need to do extra work to parse it
      ensureScanning();
      return super.toString();
   }

   @Override
   public String getStringBody() {
      final Section body = getBody();
      if (body instanceof AmqpValue value && value.getValue() instanceof String) {
         return (String) value.getValue();
      } else {
         return null;
      }
   }
}