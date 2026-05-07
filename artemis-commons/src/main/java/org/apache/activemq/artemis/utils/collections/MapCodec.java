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

package org.apache.activemq.artemis.utils.collections;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.DataConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class encapsulates the encoding and decoding of a {@code Map<String, Object>}.
 * It supports encoding of objects of type Boolean, SimpleString, Integer, Long, Byte and Byte Array
 * It provides support to predetermine the size of the encoding, and a callback reader for subclasses.
 */
public abstract class MapCodec {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   enum datatypes {
      BOOLEAN((byte)0), SIMPLE_STRING((byte)1), INTEGER((byte)2), LONG((byte)3), BYTE((byte)4), BYTE_ARRAY((byte)5);

      private static final datatypes[] VALUES = values();

      private byte id;

      datatypes(byte id) {
         this.id = id;
      }

      public byte getId() {
         return id;
      }

      public static datatypes fromId(byte id) {
         if (id < 0 || id >= VALUES.length) {
            throw new IllegalArgumentException("Unknown datatype id: " + id);
         }
         return VALUES[id];
      }
   }

   protected abstract void onMapReadInteger(short key, int value);
   protected abstract void onMapReadByte(short key, byte value);
   protected abstract void onMapReadBoolean(short key, boolean value);
   protected abstract void onMapReadLong(short key, long value);
   protected abstract void onMapReadSimpleString(short key, SimpleString value);
   protected abstract void onMapReadByteArray(short key, ActiveMQBuffer slice);

   protected static int payloadSizeSimpleString(SimpleString string) {
      return DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + SimpleString.sizeofNullableString(string);
   }

   protected static int payloadSizeInteger(int count) {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_INT) * count;
   }

   protected static int payloadSizeBoolean(int count) {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_BOOLEAN) * count;
   }

   protected static int payloadSizeLong(int count) {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_LONG) * count;
   }

   protected static int payloadSizeByte(int count) {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_BYTE) * count;
   }

   protected static int payloadSizeByteArray(int arraySize) {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_INT + arraySize);
   }

   protected static int headerSize() {
      return DataConstants.SIZE_INT * 2;
   }

   protected int writeHeader(ActiveMQBuffer buffer, int payloadSize, int entries) {
      int totalSize = headerSize() + payloadSize;
      buffer.writeInt(totalSize);
      buffer.writeInt(entries);
      return totalSize;
   }

   protected void writeSimpleString(ActiveMQBuffer buffer, short key, SimpleString value) {
      buffer.writeShort(key);
      buffer.writeByte(datatypes.SIMPLE_STRING.getId());
      SimpleString.writeNullableSimpleString(buffer.byteBuf(), value);
   }

   protected void writeByte(ActiveMQBuffer buffer, short key, byte value) {
      buffer.writeShort(key);
      buffer.writeByte(datatypes.BYTE.getId());
      buffer.writeByte(value);
   }

   protected void writeByteArray(ActiveMQBuffer buffer, short key, int size, Consumer<ActiveMQBuffer> consumer) {
      buffer.writeShort(key);
      buffer.writeByte(datatypes.BYTE_ARRAY.getId());
      buffer.writeInt(size);
      consumer.accept(buffer);
   }

   protected void writeLong(ActiveMQBuffer buffer, short key, long value) {
      buffer.writeShort(key);
      buffer.writeByte(datatypes.LONG.getId());
      buffer.writeLong(value);
   }

   protected void writeInteger(ActiveMQBuffer buffer, short key, int value) {
      buffer.writeShort(key);
      buffer.writeByte(datatypes.INTEGER.getId());
      buffer.writeInt(value);
   }

   protected void writeBoolean(ActiveMQBuffer buffer, short key, boolean value) {
      buffer.writeShort(key);
      buffer.writeByte(datatypes.BOOLEAN.getId());
      buffer.writeBoolean(value);
   }

   public void decode(ActiveMQBuffer buffer) {
      int initialPosition = buffer.readerIndex();
      int size = buffer.readInt();
      int endPosition = initialPosition + size;
      int entries = buffer.readInt();

      for (int i = 0; i < entries; i++) {
         short key = buffer.readShort();
         byte typeUsed = buffer.readByte();
         switch (datatypes.fromId(typeUsed)) {
            case BOOLEAN -> onMapReadBoolean(key, buffer.readBoolean());
            case LONG ->  onMapReadLong(key, buffer.readLong());
            case INTEGER ->  onMapReadInteger(key, buffer.readInt());
            case SIMPLE_STRING ->  onMapReadSimpleString(key, SimpleString.readNullableSimpleString(buffer.byteBuf()));
            case BYTE ->  onMapReadByte(key, buffer.readByte());
            case BYTE_ARRAY -> {
               int sizeByteArray = buffer.readInt();
               int currentPosition = buffer.readerIndex();
               onMapReadByteArray(key, buffer.slice(buffer.readerIndex(), sizeByteArray));
               buffer.readerIndex(currentPosition + sizeByteArray);
            }
         }
      }

      assert endPosition == buffer.readerIndex();
      // calling this just in case (say there's a damaged record or something like that)
      buffer.readerIndex(endPosition);
   }
}