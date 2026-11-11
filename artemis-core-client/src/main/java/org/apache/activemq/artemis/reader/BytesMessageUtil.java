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
package org.apache.activemq.artemis.reader;

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

public class BytesMessageUtil extends MessageUtil {

   public static boolean bytesReadBoolean(ActiveMQBuffer message) {
      return message.readBoolean();
   }

   public static byte bytesReadByte(ActiveMQBuffer message) {
      return message.readByte();
   }

   public static int bytesReadUnsignedByte(ActiveMQBuffer message) {
      return message.readUnsignedByte();
   }

   public static short bytesReadShort(ActiveMQBuffer message) {
      return message.readShort();
   }

   public static int bytesReadUnsignedShort(ActiveMQBuffer message) {
      return message.readUnsignedShort();
   }

   public static char bytesReadChar(ActiveMQBuffer message) {
      return (char) message.readShort();
   }

   public static int bytesReadInt(ActiveMQBuffer message) {
      return message.readInt();
   }

   public static long bytesReadLong(ActiveMQBuffer message) {
      return message.readLong();
   }

   public static float bytesReadFloat(ActiveMQBuffer message) {
      return Float.intBitsToFloat(message.readInt());
   }

   public static double bytesReadDouble(ActiveMQBuffer message) {
      return Double.longBitsToDouble(message.readLong());
   }

   public static String bytesReadUTF(ActiveMQBuffer message) {
      return message.readUTF();
   }

   public static int bytesReadBytes(ActiveMQBuffer message, final byte[] value) {
      return bytesReadBytes(message, value, value.length);
   }

   public static int bytesReadBytes(ActiveMQBuffer message, final byte[] value, final int length) {
      if (!message.readable()) {
         return -1;
      }

      int read = Math.min(length, message.readableBytes());

      if (read != 0) {
         message.readBytes(value, 0, read);
      }

      return read;

   }

   public static void bytesWriteBoolean(ActiveMQBuffer message, boolean value) {
      message.writeBoolean(value);
   }

   public static void bytesWriteByte(ActiveMQBuffer message, byte value) {
      message.writeByte(value);
   }

   public static void bytesWriteShort(ActiveMQBuffer message, short value) {
      message.writeShort(value);
   }

   public static void bytesWriteChar(ActiveMQBuffer message, char value) {
      message.writeShort((short) value);
   }

   public static void bytesWriteInt(ActiveMQBuffer message, int value) {
      message.writeInt(value);
   }

   public static void bytesWriteLong(ActiveMQBuffer message, long value) {
      message.writeLong(value);
   }

   public static void bytesWriteFloat(ActiveMQBuffer message, float value) {
      message.writeInt(Float.floatToIntBits(value));
   }

   public static void bytesWriteDouble(ActiveMQBuffer message, double value) {
      message.writeLong(Double.doubleToLongBits(value));
   }

   public static void bytesWriteUTF(ActiveMQBuffer message, String value) {
      message.writeUTF(value);
   }

   public static void bytesWriteBytes(ActiveMQBuffer message, byte[] value) {
      message.writeBytes(value);
   }

   public static void bytesWriteBytes(ActiveMQBuffer message, final byte[] value, final int offset, final int length) {
      message.writeBytes(value, offset, length);
   }

   /**
    * {@return {@code true} if it could send the Object to any known format}
    */
   public static boolean bytesWriteObject(ActiveMQBuffer message, Object value) {
      Objects.requireNonNull(value, "Attempt to write a null value");
      if (value instanceof String string) {
         bytesWriteUTF(message, string);
      } else if (value instanceof Boolean booleanValue) {
         bytesWriteBoolean(message, booleanValue);
      } else if (value instanceof Character character) {
         bytesWriteChar(message, character);
      } else if (value instanceof Byte byteValue) {
         bytesWriteByte(message, byteValue);
      } else if (value instanceof Short shortValue) {
         bytesWriteShort(message, shortValue);
      } else if (value instanceof Integer integer) {
         bytesWriteInt(message, integer);
      } else if (value instanceof Long longValue) {
         bytesWriteLong(message, longValue);
      } else if (value instanceof Float floatValue) {
         bytesWriteFloat(message, floatValue);
      } else if (value instanceof Double doubleValue) {
         bytesWriteDouble(message, doubleValue);
      } else if (value instanceof byte[] bytes) {
         bytesWriteBytes(message, bytes);
      } else {
         return false;
      }

      return true;
   }

   public static void bytesMessageReset(ActiveMQBuffer message) {
      message.resetReaderIndex();
   }

}
