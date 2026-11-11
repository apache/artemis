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
package org.apache.activemq.artemis.utils;

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQInvalidBufferException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.logs.ActiveMQUtilBundle;

/**
 * Helper methods to read and write from ActiveMQBuffer.
 */
public class BufferHelper {

   /**
    * Size of a String as if it was a Nullable Simple String
    */
   public static int sizeOfNullableSimpleString(String str) {
      if (str == null) {
         return DataConstants.SIZE_BOOLEAN;
      } else {
         return DataConstants.SIZE_BOOLEAN + sizeOfSimpleString(str);
      }
   }

   /**
    * Size of a String as it if was a Simple String
    */
   public static int sizeOfSimpleString(String str) {
      return DataConstants.SIZE_INT + str.length() * 2;
   }

   public static void writeAsNullableSimpleString(ActiveMQBuffer buffer, String str) {
      buffer.writeNullableSimpleString(SimpleString.of(str));
   }

   public static String readNullableSimpleStringAsString(ActiveMQBuffer buffer) {
      return Objects.toString(buffer.readNullableSimpleString(), null);
   }

   public static void writeAsSimpleString(ActiveMQBuffer buffer, String str) {
      buffer.writeSimpleString(SimpleString.of(str));
   }

   public static void writeNullableBoolean(ActiveMQBuffer buffer, Boolean value) {
      buffer.writeBoolean(value != null);

      if (value != null) {
         buffer.writeBoolean(value.booleanValue());
      }
   }

   public static int sizeOfNullableBoolean(Boolean value) {
      return DataConstants.SIZE_BOOLEAN + (value != null ? DataConstants.SIZE_BOOLEAN : 0);
   }

   public static Boolean readNullableBoolean(ActiveMQBuffer buffer) {
      boolean isNotNull = buffer.readBoolean();

      if (isNotNull) {
         return buffer.readBoolean();
      } else {
         return null;
      }
   }

   public static void writeNullableLong(ActiveMQBuffer buffer, Long value) {
      buffer.writeBoolean(value != null);

      if (value != null) {
         buffer.writeLong(value.longValue());
      }
   }

   public static void writeNullableDouble(ActiveMQBuffer buffer, Double value) {
      buffer.writeBoolean(value != null);

      if (value != null) {
         buffer.writeDouble(value.doubleValue());
      }
   }

   public static int sizeOfNullableLong(Long value) {
      return DataConstants.SIZE_BOOLEAN + (value != null ? DataConstants.SIZE_LONG : 0);
   }

   public static int sizeOfNullableDouble(Double value) {
      return DataConstants.SIZE_BOOLEAN + (value != null ? DataConstants.SIZE_DOUBLE : 0);
   }

   public static Long readNullableLong(ActiveMQBuffer buffer) {
      boolean isNotNull = buffer.readBoolean();

      if (isNotNull) {
         return buffer.readLong();
      } else {
         return null;
      }
   }

   public static void writeNullableInteger(ActiveMQBuffer buffer, Integer value) {
      buffer.writeBoolean(value != null);

      if (value != null) {
         buffer.writeInt(value.intValue());
      }
   }

   public static int sizeOfNullableInteger(Integer value) {
      return DataConstants.SIZE_BOOLEAN + (value != null ? DataConstants.SIZE_INT : 0);
   }

   public static Integer readNullableInteger(ActiveMQBuffer buffer) {
      boolean isNotNull = buffer.readBoolean();

      if (isNotNull) {
         return buffer.readInt();
      } else {
         return null;
      }
   }

   public static Double readNullableDouble(ActiveMQBuffer buffer) {
      boolean isNotNull = buffer.readBoolean();

      if (isNotNull) {
         return buffer.readDouble();
      } else {
         return null;
      }
   }

   public static int sizeOfNullableString(String s) {
      if (s == null) {
         return DataConstants.SIZE_BOOLEAN;
      }
      return DataConstants.SIZE_BOOLEAN + sizeOfString(s);
   }

   public static int sizeOfString(String s) {
      int len = s.length();
      if (len < 9) {
         return DataConstants.SIZE_INT + (len * DataConstants.SIZE_SHORT);
      }
      // 4095 == 0xfff
      if (len < 4095) {
         // beware: this one has O(n) cost: look at UTF8Util::saveUTF
         final int expectedEncodedUTF8Len = UTF8Util.calculateUTFSize(s);
         if (expectedEncodedUTF8Len > 65535) {
            throw ActiveMQUtilBundle.BUNDLE.stringTooLong(len);
         }
         return DataConstants.SIZE_INT + DataConstants.SIZE_SHORT + expectedEncodedUTF8Len;
      }
      // it seems weird but this SIZE_INT is required due to how UTF8Util is encoding UTF strings
      // so this SIZE_INT is required
      // perhaps we could optimize it and remove it, but that would break compatibility with older clients and journal
      return DataConstants.SIZE_INT + sizeOfSimpleString(s);
   }


   public static byte[] safeReadBytes(final ActiveMQBuffer in) {
      final int claimedSize = in.readInt();

      if (claimedSize < 0) {
         throw new ActiveMQInvalidBufferException("Payload size cannot be negative");
      }

      final int readableBytes = in.readableBytes();
      // We have to be defensive here and not try to allocate byte buffer straight from information available in the
      // stream. Or else, an adversary may handcraft the packet causing OOM situation for a running JVM.
      if (claimedSize > readableBytes) {
         throw new ActiveMQInvalidBufferException("Attempted to read: " + claimedSize +
                                          " which exceeds overall readable buffer size of: " + readableBytes);
      }
      final byte[] byteBuffer = new byte[claimedSize];
      in.readBytes(byteBuffer);
      return byteBuffer;
   }


}

