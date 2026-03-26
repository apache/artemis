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
import org.apache.activemq.artemis.utils.collections.MapCodec;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes AMQP messages for storage using a hashmap-like encoding. It is used by {@link AMQPLargeMessagePersisterV2} and {@link AMQPMessagePersisterV4}. */
public class AMQPMessageMapCodec extends MapCodec {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final ThreadLocal<AMQPMessageMapCodec> threadLocal = ThreadLocal.withInitial(AMQPMessageMapCodec::new);

   public static AMQPMessageMapCodec getInstance() {
      AMQPMessageMapCodec reader = threadLocal.get();
      return reader;
   }

   protected static final int NUMBER_OF_LONGS = 3;
   protected static final int NUMBER_OF_INTEGERS = 1;
   protected static final int NUMBER_OF_BYTES = 1;
   protected static final int NUMBER_OF_BOOLEANS = 1;
   protected static final int NUMBER_OF_STRINGS = 1;
   protected static final int NUMBER_OF_BOOLEANS_LARGE_MESSAGE = 1; // large message has an additional boolean being used

   protected static final short KEY_MESSAGE_ID = 1;
   protected static final short KEY_MESSAGE_FORMAT = 2;
   protected static final short KEY_ADDRESS = 3;
   protected static final short KEY_EXPIRATION = 4;
   protected static final short KEY_MEMORY_ESTIMATE = 5;
   protected static final short KEY_PRIORITY = 6;
   protected static final short KEY_IS_DURABLE = 7;
   protected static final short KEY_EXTRA_PROPERTIES = 8;
   protected static final short KEY_IS_REENCODED = 9; // used on large messages only

   ///////////////////////////////////////////////////////////////////////////////////
   // only used on reading, on writing we will write directly from the message
   protected long messageID;
   protected long messageFormat;
   protected long messageExpiration;
   protected SimpleString address;
   protected int memoryEstimate;
   protected byte priority;
   protected boolean isDurable;
   protected TypedProperties extraProperties;
   protected boolean isReencoded; // used on large messages only
   // only used on reading, on writing we will write directly from the message
   ///////////////////////////////////////////////////////////////////////////////////

   protected int getPayloadSize(Message record) {
      AMQPMessage amqpMessage = (AMQPMessage) record;
      int payloadSize = payloadSizeInteger(NUMBER_OF_INTEGERS) +
         payloadSizeBoolean(NUMBER_OF_BOOLEANS) +
         payloadSizeLong(NUMBER_OF_LONGS) +
         payloadSizeByte(NUMBER_OF_BYTES) +
         payloadSizeSimpleString(record.getAddressSimpleString());

      if (record.isLargeMessage()) {
         payloadSize += payloadSizeBoolean(1);
      }

      if (amqpMessage.getExtraProperties() != null) {
         payloadSize += payloadSizeByteArray(amqpMessage.getExtraProperties().getEncodeSize());
      }

      return payloadSize;
   }

   public int getEncodeSize(Message record) {
      return headerSize() + getPayloadSize(record);
   }


   public AMQPMessageMapCodec reset() {
      messageID = 0;
      messageFormat = 0;
      messageExpiration = 0;
      address = null;
      memoryEstimate = 0;
      priority = 0;
      isDurable = false;
      isReencoded = false;
      extraProperties = null;
      return this;
   }

   public int getNumberOfElements(Message record) {
      AMQPMessage amqpMessage = (AMQPMessage) record;
      int numberOfElements = NUMBER_OF_LONGS + NUMBER_OF_INTEGERS + NUMBER_OF_BYTES + NUMBER_OF_BOOLEANS + NUMBER_OF_STRINGS;
      if (record.isLargeMessage()) {
         numberOfElements += NUMBER_OF_BOOLEANS_LARGE_MESSAGE;
      }
      if (amqpMessage.getExtraProperties() != null) {
         numberOfElements++;
      }
      return numberOfElements;
   }

   public void encode(ActiveMQBuffer buffer, Message record) {
      AMQPMessage msgEncode = (AMQPMessage) record;
      int initialPosition = buffer.writerIndex();
      int payloadSize = getPayloadSize(record);
      int mapSize = writeHeader(buffer, payloadSize, getNumberOfElements(record));

      encodeElements(buffer, msgEncode);

      assert buffer.writerIndex() == initialPosition + mapSize;
   }

   protected void encodeElements(ActiveMQBuffer buffer, AMQPMessage msgEncode) {
      writeLong(buffer, KEY_MESSAGE_ID, msgEncode.getMessageID());
      writeLong(buffer, KEY_MESSAGE_FORMAT, msgEncode.getMessageFormat());
      writeSimpleString(buffer, KEY_ADDRESS, msgEncode.getAddressSimpleString());
      writeLong(buffer, KEY_EXPIRATION, msgEncode.getExpiration());
      writeInteger(buffer, KEY_MEMORY_ESTIMATE, msgEncode.getMemoryEstimate());
      writeByte(buffer, KEY_PRIORITY, msgEncode.getPriority());
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
      }
   }
}

