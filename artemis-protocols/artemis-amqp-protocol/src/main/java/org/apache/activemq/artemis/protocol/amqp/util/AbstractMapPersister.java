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

package org.apache.activemq.artemis.protocol.amqp.util;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.DataConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class encapsulates the encoding and decoding of a {@code Map<Short, Object>}.
 * It supports encoding of objects of type Boolean, SimpleString, Integer, Long, Byte and Byte Array
 * It provides support to predetermine the size of the encoding, and a callback reader for subclasses.
 */
public abstract class AbstractMapPersister<T> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   enum Datatypes {
      BOOLEAN((byte)0), SIMPLE_STRING((byte)1), INTEGER((byte)2), LONG((byte)3), BYTE((byte)4), BYTE_ARRAY((byte)5);

      private static final Datatypes[] VALUES = values();

      private byte id;

      Datatypes(byte id) {
         this.id = id;
      }

      public byte getId() {
         return id;
      }

      public static Datatypes fromId(byte id) {
         if (id < 0 || id >= VALUES.length) {
            throw new IllegalArgumentException("Unknown datatype id: " + id);
         }
         return VALUES[id];
      }
   }

   protected abstract int getMaxAllowedElements();

   protected abstract void onMapReadInteger(short key, int value, T decodingObject);
   protected abstract void onMapReadByte(short key, byte value, T decodingObject);
   protected abstract void onMapReadBoolean(short key, boolean value, T decodingObject);
   protected abstract void onMapReadLong(short key, long value, T decodingObject);
   protected abstract void onMapReadSimpleString(short key, SimpleString value, T decodingObject);
   protected abstract void onMapReadByteArray(short key, ActiveMQBuffer slice, T decodingObject);

   protected static int payloadSizeSimpleString(SimpleString string) {
      return DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + SimpleString.sizeofNullableString(string);
   }

   protected static int payloadSizeInteger() {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_INT);
   }

   protected static int payloadSizeBoolean() {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_BOOLEAN);
   }

   protected static int payloadSizeLong() {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_LONG);
   }

   protected static int payloadSizeByte() {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_BYTE);
   }

   protected static int payloadSizeByteArray(int arraySize) {
      return (DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE + DataConstants.SIZE_INT + arraySize);
   }

   protected static int headerSize() {
      return DataConstants.SIZE_INT + DataConstants.SIZE_SHORT; // record size (int) and elements (unsigned short)
   }

   protected void writeHeader(ActiveMQBuffer buffer, int recordSize, int entries) {
      buffer.writeInt(recordSize);
      buffer.writeUnsignedShort(entries);
   }

   protected void writeSimpleString(ActiveMQBuffer buffer, short key, SimpleString value) {
      buffer.writeShort(key);
      buffer.writeByte(Datatypes.SIMPLE_STRING.getId());
      SimpleString.writeNullableSimpleString(buffer.byteBuf(), value);
   }

   protected void writeByte(ActiveMQBuffer buffer, short key, byte value) {
      buffer.writeShort(key);
      buffer.writeByte(Datatypes.BYTE.getId());
      buffer.writeByte(value);
   }

   protected void writeByteArray(ActiveMQBuffer buffer, short key, int size, Consumer<ActiveMQBuffer> consumer) {
      buffer.writeShort(key);
      buffer.writeByte(Datatypes.BYTE_ARRAY.getId());
      buffer.writeInt(size);
      consumer.accept(buffer);
   }

   protected void writeLong(ActiveMQBuffer buffer, short key, long value) {
      buffer.writeShort(key);
      buffer.writeByte(Datatypes.LONG.getId());
      buffer.writeLong(value);
   }

   protected void writeInteger(ActiveMQBuffer buffer, short key, int value) {
      buffer.writeShort(key);
      buffer.writeByte(Datatypes.INTEGER.getId());
      buffer.writeInt(value);
   }

   protected void writeBoolean(ActiveMQBuffer buffer, short key, boolean value) {
      buffer.writeShort(key);
      buffer.writeByte(Datatypes.BOOLEAN.getId());
      buffer.writeBoolean(value);
   }

   public void decode(ActiveMQBuffer buffer, T decodingObject) {
      int initialPosition = buffer.readerIndex();
      int size = buffer.readInt();

      if (size < DataConstants.SIZE_INT) {
         throw new IllegalStateException("Invalid record size " + size);
      }

      if (size - DataConstants.SIZE_INT > buffer.readableBytes()) {
         throw new IllegalStateException("Invalid record size " + size + " exceeds available buffer bytes: " + buffer.readableBytes());
      }

      int endPosition = initialPosition + size;

      checkReadableBytes(buffer, DataConstants.SIZE_SHORT, endPosition);
      int entries = buffer.readUnsignedShort();

      if (entries > getMaxAllowedElements()) {
         throw new IllegalStateException("Invalid entries size " + entries + " beyond max allowed elements of " + getMaxAllowedElements());
      }

      for (int i = 0; i < entries; i++) {
         // This will check that each entry is valid. If entries is set to an invalid value through malicious data
         // this check here would interrupt any further parsing
         checkReadableBytes(buffer, DataConstants.SIZE_SHORT + DataConstants.SIZE_BYTE, endPosition);
         short key = buffer.readShort();
         byte typeUsed = buffer.readByte();

         switch (Datatypes.fromId(typeUsed)) {
            case BOOLEAN -> {
               checkReadableBytes(buffer, DataConstants.SIZE_BOOLEAN, endPosition);
               onMapReadBoolean(key, buffer.readBoolean(), decodingObject);
            }
            case LONG -> {
               checkReadableBytes(buffer, DataConstants.SIZE_LONG, endPosition);
               onMapReadLong(key, buffer.readLong(), decodingObject);
            }
            case INTEGER -> {
               checkReadableBytes(buffer, DataConstants.SIZE_INT, endPosition);
               onMapReadInteger(key, buffer.readInt(), decodingObject);
            }
            case SIMPLE_STRING -> {
               checkReadableBytes(buffer, DataConstants.SIZE_BYTE, endPosition);
               onMapReadSimpleString(key, SimpleString.readNullableSimpleString(buffer.byteBuf()), decodingObject);
            }
            case BYTE -> {
               checkReadableBytes(buffer, DataConstants.SIZE_BYTE, endPosition);
               onMapReadByte(key, buffer.readByte(), decodingObject);
            }
            case BYTE_ARRAY -> {
               checkReadableBytes(buffer, DataConstants.SIZE_INT, endPosition);
               int sizeByteArray = buffer.readInt();
               if (sizeByteArray < 0) {
                  throw new IllegalArgumentException("Negative byte array size: " + sizeByteArray);
               }
               checkReadableBytes(buffer, sizeByteArray, endPosition);
               int currentPosition = buffer.readerIndex();
               onMapReadByteArray(key, buffer.slice(buffer.readerIndex(), sizeByteArray), decodingObject);
               buffer.readerIndex(currentPosition + sizeByteArray);
            }
         }
      }

      if (endPosition != buffer.readerIndex()) {
         throw new IllegalStateException("Buffer position mismatch after decode: expected " + endPosition + " but at " + buffer.readerIndex() + " (consumed " + (buffer.readerIndex() - initialPosition) + " bytes, expected " + size + ")");
      }
   }

   final void checkReadableBytes(ActiveMQBuffer buffer, int bytesToRead, int endPosition) {
      int remainingInRecord = endPosition - buffer.readerIndex();
      if (remainingInRecord < bytesToRead) {
         throw new IllegalStateException("Insufficient bytes in record for entry: " + remainingInRecord + " bytes remaining");
      }
   }
}