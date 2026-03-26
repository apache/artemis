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

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.protocol.amqp.util.AbstractMapPersister;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supports the AMQPMessagePersisters ({@link AMQPLargeMessagePersisterV2} and {@link AMQPMessagePersisterV4}) by providing hashmap-like encodings
 * on data used by the broker during reloading of AMQP messages. */
public class AMQPMapPersister
   extends AbstractMapPersister {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final ThreadLocal<AMQPMapPersister> mapPersisterThreadLocal = ThreadLocal.withInitial(AMQPMapPersister::new);

   public static AMQPMapPersister getInstance() {
      return mapPersisterThreadLocal.get();
   }

   protected static final short KEY_MESSAGE_ID = 1;
   protected static final short KEY_MESSAGE_FORMAT = 2;
   protected static final short KEY_EXPIRATION = 3;
   protected static final short KEY_MEMORY_ESTIMATE = 4;
   protected static final short KEY_PRIORITY = 5;
   protected static final short KEY_ADDRESS = 6;
   protected static final short KEY_IS_DURABLE = 7;
   protected static final short KEY_EXTRA_PROPERTIES = 8;
   protected static final short KEY_IS_REENCODED = 9; // used on large messages only

   ///////////////////////////////////////////////////////////////////////////////////
   // only used on reading, on writing we will write directly from the message
   protected long messageID; // always present
   protected long messageFormat; // always present
   protected long messageExpiration; // always present
   protected int memoryEstimate; // always present
   protected byte priority; // always present
   protected boolean isDurable; // always present
   protected SimpleString address; // variable size based on the string size
   protected TypedProperties extraProperties; // variable size based on the byte array size
   protected boolean isReencoded; // used on large messages only
   // only used on reading, on writing we will write directly from the message
   ///////////////////////////////////////////////////////////////////////////////////


   /** This is the minimum size this codec will generate, as these will always be persisted */
   private static final int BASIC_SIZE = headerSize() +
                                         payloadSizeLong() + // messageID
                                         payloadSizeLong() + // message format
                                         payloadSizeLong() + // message Expiration
                                         payloadSizeInteger() + // memory estimate
                                         payloadSizeByte() + // priority
                                         payloadSizeBoolean(); // isDurable

   private static final int BASIC_ELEMENTS = 7; // messageID, messageFormat, messageExpiration, memoryEstimate, priority, isDurable and address

   public int getEncodeSize(Message record) {
      AMQPMessage amqpMessage = (AMQPMessage) record;
      int encodeSize = BASIC_SIZE + payloadSizeSimpleString(amqpMessage.getAddressSimpleString());

      if (record.isLargeMessage()) {
         encodeSize += payloadSizeBoolean();
      }

      if (amqpMessage.getExtraProperties() != null) {
         encodeSize += payloadSizeByteArray(amqpMessage.getExtraProperties().getEncodeSize());
      }

      return encodeSize;
   }

   public AMQPMapPersister reset() {
      messageID = 0;
      messageFormat = 0;
      messageExpiration = 0;
      memoryEstimate = 0;
      priority = 0;
      address = null;
      isDurable = false;
      extraProperties = null;
      isReencoded = false;
      return this;
   }

   public int getNumberOfElements(Message record) {
      AMQPMessage amqpMessage = (AMQPMessage) record;
      int numberOfElements = BASIC_ELEMENTS;
      if (record.isLargeMessage()) {
         numberOfElements++;
      }
      if (amqpMessage.getExtraProperties() != null) {
         numberOfElements++;
      }
      return numberOfElements;
   }

   public void encode(ActiveMQBuffer buffer, Message record) {
      AMQPMessage msgEncode = (AMQPMessage) record;
      int initialPosition = buffer.writerIndex();
      int recordSize = getEncodeSize(record);
      writeHeader(buffer, recordSize, getNumberOfElements(record));
      encodeElements(buffer, msgEncode);

      assert buffer.writerIndex() == initialPosition + recordSize;
   }

   protected void encodeElements(ActiveMQBuffer buffer, AMQPMessage msgEncode) {
      writeLong(buffer, KEY_MESSAGE_ID, msgEncode.getMessageID());
      writeLong(buffer, KEY_MESSAGE_FORMAT, msgEncode.getMessageFormat());
      writeLong(buffer, KEY_EXPIRATION, msgEncode.getExpiration());
      writeInteger(buffer, KEY_MEMORY_ESTIMATE, msgEncode.getMemoryEstimate());
      writeByte(buffer, KEY_PRIORITY, msgEncode.getPriority());
      writeSimpleString(buffer, KEY_ADDRESS, msgEncode.getAddressSimpleString());
      writeBoolean(buffer, KEY_IS_DURABLE, msgEncode.isDurable());
      TypedProperties extraProperties = msgEncode.getExtraProperties();
      if (extraProperties != null) {
         writeByteArray(buffer, KEY_EXTRA_PROPERTIES, extraProperties.getEncodeSize(), extraProperties::encode);
      }
      if (msgEncode.isLargeMessage()) {
         writeBoolean(buffer, KEY_IS_REENCODED, ((AMQPLargeMessage)msgEncode).isReencoded());
      }
   }

   @Override
   public void onMapReadInteger(short key, int value) {
      switch (key) {
         case KEY_MEMORY_ESTIMATE -> memoryEstimate = value;
         default -> logger.debug("Unknown Integer key={} value={} - ignoring", key, value);
      }
   }

   @Override
   public void onMapReadByte(short key, byte value) {
      switch (key) {
         case KEY_PRIORITY -> priority = value;
         default -> logger.debug("Unknown Byte key={} value={} - ignoring", key, value);
      }
   }

   @Override
   public void onMapReadBoolean(short key, boolean value) {
      switch (key) {
         case KEY_IS_DURABLE -> isDurable = value;
         case KEY_IS_REENCODED -> isReencoded = value;
         default -> logger.debug("Unknown Boolean key={} value={} - ignoring", key, value);
      }
   }

   @Override
   public void onMapReadLong(short key, long value) {
      switch (key) {
         case KEY_MESSAGE_ID -> messageID = value;
         case KEY_MESSAGE_FORMAT -> messageFormat = value;
         case KEY_EXPIRATION -> messageExpiration = value;
         default -> logger.debug("Unknown Long key={} value={} - ignoring", key, value);
      }
   }

   @Override
   public void onMapReadSimpleString(short key, SimpleString value) {
      switch (key) {
         case KEY_ADDRESS -> address = value;
         default -> logger.debug("Unknown String key={} value={} - ignoring", key, value);
      }
   }

   @Override
   protected void onMapReadByteArray(short key, ActiveMQBuffer slice) {
      switch (key) {
         case KEY_EXTRA_PROPERTIES -> {
            extraProperties = new TypedProperties(Message.INTERNAL_PROPERTY_NAMES_PREDICATE);
            extraProperties.decode(slice.byteBuf());
         }
         default -> logger.debug("Unknown byteArray key={} value={} - ignoring", key, slice);
      }
   }
}

