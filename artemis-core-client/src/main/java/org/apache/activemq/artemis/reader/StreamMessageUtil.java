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
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.utils.DataConstants;

public class StreamMessageUtil extends MessageUtil {

   /**
    * Method to read boolean values out of the Stream protocol existent on JMS Stream Messages Throws
    * IllegalStateException if the type was invalid
    */
   public static boolean streamReadBoolean(ActiveMQBuffer buff) {
      byte type = buff.readByte();

      return switch (type) {
         case DataConstants.BOOLEAN -> buff.readBoolean();
         case DataConstants.STRING -> {
            String s = buff.readNullableString();
            yield Boolean.valueOf(s);
         }
         default -> throw new IllegalStateException("Invalid conversion, type byte was " + type);
      };

   }

   public static byte streamReadByte(ActiveMQBuffer buff) {
      int index = buff.readerIndex();
      try {
         byte type = buff.readByte();
         return switch (type) {
            case DataConstants.BYTE -> buff.readByte();
            case DataConstants.STRING -> {
               String s = buff.readNullableString();
               yield Byte.parseByte(s);
            }
            default -> throw new IllegalStateException("Invalid conversion");
         };
      } catch (NumberFormatException e) {
         buff.readerIndex(index);
         throw e;
      }

   }

   public static short streamReadShort(ActiveMQBuffer buff) {
      byte type = buff.readByte();
      return switch (type) {
         case DataConstants.BYTE -> buff.readByte();
         case DataConstants.SHORT -> buff.readShort();
         case DataConstants.STRING -> {
            String s = buff.readNullableString();
            yield Short.parseShort(s);
         }
         default -> throw new IllegalStateException("Invalid conversion");
      };
   }

   public static char streamReadChar(ActiveMQBuffer buff) {
      return switch (buff.readByte()) {
         case DataConstants.CHAR -> (char) buff.readShort();
         case DataConstants.STRING -> {
            Objects.requireNonNull(buff.readNullableString(), "Invalid conversion");
            throw new IllegalStateException("Invalid conversion");
         }
         default -> throw new IllegalStateException("Invalid conversion");
      };

   }

   public static int streamReadInteger(ActiveMQBuffer buff) {
      byte type = buff.readByte();
      return switch (type) {
         case DataConstants.BYTE -> buff.readByte();
         case DataConstants.SHORT -> buff.readShort();
         case DataConstants.INT -> buff.readInt();
         case DataConstants.STRING -> {
            String s = buff.readNullableString();
            yield Integer.parseInt(s);
         }
         default -> throw new IllegalStateException("Invalid conversion");
      };
   }

   public static long streamReadLong(ActiveMQBuffer buff) {
      byte type = buff.readByte();
      return switch (type) {
         case DataConstants.BYTE -> buff.readByte();
         case DataConstants.SHORT -> buff.readShort();
         case DataConstants.INT -> buff.readInt();
         case DataConstants.LONG -> buff.readLong();
         case DataConstants.STRING -> {
            String s = buff.readNullableString();
            yield Long.parseLong(s);
         }
         default -> throw new IllegalStateException("Invalid conversion");
      };
   }

   public static float streamReadFloat(ActiveMQBuffer buff) {
      byte type = buff.readByte();
      return switch (type) {
         case DataConstants.FLOAT -> Float.intBitsToFloat(buff.readInt());
         case DataConstants.STRING -> {
            String s = buff.readNullableString();
            yield Float.parseFloat(s);
         }
         default -> throw new IllegalStateException("Invalid conversion");
      };
   }

   public static double streamReadDouble(ActiveMQBuffer buff) {
      byte type = buff.readByte();
      return switch (type) {
         case DataConstants.FLOAT -> Float.intBitsToFloat(buff.readInt());
         case DataConstants.DOUBLE -> Double.longBitsToDouble(buff.readLong());
         case DataConstants.STRING -> {
            String s = buff.readNullableString();
            yield Double.parseDouble(s);
         }
         default -> throw new IllegalStateException("Invalid conversion: " + type);
      };
   }

   public static String streamReadString(ActiveMQBuffer buff) {
      byte type = buff.readByte();
      return switch (type) {
         case DataConstants.BOOLEAN -> String.valueOf(buff.readBoolean());
         case DataConstants.BYTE -> String.valueOf(buff.readByte());
         case DataConstants.SHORT -> String.valueOf(buff.readShort());
         case DataConstants.CHAR -> String.valueOf((char) buff.readShort());
         case DataConstants.INT -> String.valueOf(buff.readInt());
         case DataConstants.LONG -> String.valueOf(buff.readLong());
         case DataConstants.FLOAT -> String.valueOf(Float.intBitsToFloat(buff.readInt()));
         case DataConstants.DOUBLE -> String.valueOf(Double.longBitsToDouble(buff.readLong()));
         case DataConstants.STRING -> buff.readNullableString();
         default -> throw new IllegalStateException("Invalid conversion");
      };
   }

   /**
    * Utility for reading bytes out of streaming. It will return remainingBytes, bytesRead
    *
    * @param remainingBytes remaining Bytes from previous read. Send it to 0 if it was the first call for the message
    * @return a pair of remaining bytes and bytes read
    */
   public static Pair<Integer, Integer> streamReadBytes(ActiveMQBuffer buff, int remainingBytes, byte[] value) {
      if (remainingBytes == -1) {
         return new Pair<>(0, -1);
      } else if (remainingBytes == 0) {
         byte type = buff.readByte();
         if (type != DataConstants.BYTES) {
            throw new IllegalStateException("Invalid conversion");
         }
         remainingBytes = buff.readInt();
      }
      int read = Math.min(value.length, remainingBytes);
      buff.readBytes(value, 0, read);
      remainingBytes -= read;
      if (remainingBytes == 0) {
         remainingBytes = -1;
      }
      return new Pair<>(remainingBytes, read);

   }

   public static Object streamReadObject(ActiveMQBuffer buff) {
      byte type = buff.readByte();
      switch (type) {
         case DataConstants.BOOLEAN:
            return buff.readBoolean();
         case DataConstants.BYTE:
            return buff.readByte();
         case DataConstants.SHORT:
            return buff.readShort();
         case DataConstants.CHAR:
            return (char) buff.readShort();
         case DataConstants.INT:
            return buff.readInt();
         case DataConstants.LONG:
            return buff.readLong();
         case DataConstants.FLOAT:
            return Float.intBitsToFloat(buff.readInt());
         case DataConstants.DOUBLE:
            return Double.longBitsToDouble(buff.readLong());
         case DataConstants.STRING:
            return buff.readNullableString();
         case DataConstants.BYTES:
            int bufferLen = buff.readInt();
            byte[] bytes = new byte[bufferLen];
            buff.readBytes(bytes);
            return bytes;
         default:
            throw new IllegalStateException("Invalid conversion");
      }

   }

}
