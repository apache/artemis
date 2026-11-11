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

import java.lang.ref.SoftReference;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.logs.ActiveMQUtilBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A UTF8Util
 * <p>
 * This class will write UTFs directly to the ByteOutput (through the MessageBuffer interface)
 */
public final class UTF8Util {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final ThreadLocal<SoftReference<StringUtilBuffer>> currentBuffer = new ThreadLocal<>();

   private UTF8Util() {
      // utility class
   }
   public static void writeNullableString(ByteBuf buffer, final String val) {
      if (val == null) {
         buffer.writeByte(DataConstants.NULL);
      } else {
         buffer.writeByte(DataConstants.NOT_NULL);
         writeString(buffer, val);
      }
   }

   private static void writeAsShorts(final ByteBuf buffer, final String val) {
      for (int i = 0; i < val.length(); i++) {
         buffer.writeShort((short) val.charAt(i));
      }
   }

   public static void writeString(final ByteBuf buffer, final String val) {
      int length = val.length();

      buffer.writeInt(length);

      if (length < 9) {
         // If very small it's more performant to store char by char
         writeAsShorts(buffer, val);
      } else if (length < 0xfff) {
         // Store as UTF - this is quicker than char by char for most strings
         saveUTF(buffer, val);
      } else {
         // Store as SimpleString, since can't store utf > 0xffff in length
         SimpleString.writeSimpleString(buffer, SimpleString.of(val));
      }
   }

   public static void saveUTF(final ByteBuf out, final String str) {

      if (str.length() > 0xffff) {
         throw ActiveMQUtilBundle.BUNDLE.stringTooLong(str.length());
      }

      final int len = UTF8Util.calculateUTFSize(str);

      if (len > 0xffff) {
         throw ActiveMQUtilBundle.BUNDLE.stringTooLong(len);
      }

      out.writeShort((short) len);

      final int stringLength = str.length();

      if (logger.isTraceEnabled()) {
         // This message is too verbose for debug, that's why we are using trace here
         logger.trace("Saving string with utfSize={} stringSize={}", len, stringLength);
      }

      if (out.hasArray()) {
         out.ensureWritable(len);
         final byte[] bytes = out.array();
         final int writerIndex = out.writerIndex();
         final int index = out.arrayOffset() + writerIndex;
         if (PlatformDependent.hasUnsafe()) {
            unsafeOnHeapWriteUTF(str, bytes, index, stringLength);
         } else {
            writeUTF(str, bytes, index, stringLength);
         }
         out.writerIndex(writerIndex + len);
      } else {
         if (PlatformDependent.hasUnsafe() && out.hasMemoryAddress()) {
            out.ensureWritable(len);
            final long addressBytes = out.memoryAddress();
            final int writerIndex = out.writerIndex();
            unsafeOffHeapWriteUTF(str, addressBytes, writerIndex, stringLength);
            out.writerIndex(writerIndex + len);
         } else {
            final StringUtilBuffer buffer = UTF8Util.getThreadLocalBuffer();
            final byte[] bytes = buffer.borrowByteBuffer(len);
            writeUTF(str, bytes, 0, stringLength);
            out.writeBytes(bytes, 0, len);
         }
      }
   }

   private static int writeUTF(final CharSequence str, final byte[] bytes, final int index, final int length) {
      int charCount = index;

      for (int i = 0; i < length; i++) {
         char charAtPos = str.charAt(i);
         if (charAtPos <= 0x7f) {
            bytes[charCount++] = (byte) charAtPos;
         } else if (charAtPos >= 0x800) {
            bytes[charCount++] = (byte) (0xE0 | charAtPos >> 12 & 0x0F);
            bytes[charCount++] = (byte) (0x80 | charAtPos >> 6 & 0x3F);
            bytes[charCount++] = (byte) (0x80 | charAtPos >> 0 & 0x3F);
         } else {
            bytes[charCount++] = (byte) (0xC0 | charAtPos >> 6 & 0x1F);
            bytes[charCount++] = (byte) (0x80 | charAtPos >> 0 & 0x3F);
         }
      }

      final int writtenBytes = (charCount - index);
      return writtenBytes;
   }

   private static int unsafeOnHeapWriteUTF(final CharSequence str, final byte[] bytes, final int index, final int length) {
      int charCount = index;
      for (int i = 0; i < length; i++) {
         char charAtPos = str.charAt(i);
         if (charAtPos <= 0x7f) {
            PlatformDependent.putByte(bytes, charCount++, (byte) charAtPos);
         } else if (charAtPos >= 0x800) {
            PlatformDependent.putByte(bytes, charCount++, (byte) (0xE0 | charAtPos >> 12 & 0x0F));
            PlatformDependent.putByte(bytes, charCount++, (byte) (0x80 | charAtPos >> 6 & 0x3F));
            PlatformDependent.putByte(bytes, charCount++, (byte) (0x80 | charAtPos >> 0 & 0x3F));
         } else {
            PlatformDependent.putByte(bytes, charCount++, (byte) (0xC0 | charAtPos >> 6 & 0x1F));
            PlatformDependent.putByte(bytes, charCount++, (byte) (0x80 | charAtPos >> 0 & 0x3F));
         }
      }

      final int writtenBytes = (charCount - index);
      return writtenBytes;
   }

   private static int unsafeOffHeapWriteUTF(final CharSequence str, final long addressBytes, final int index, final int length) {
      int charCount = index;
      for (int i = 0; i < length; i++) {
         char charAtPos = str.charAt(i);
         if (charAtPos <= 0x7f) {
            PlatformDependent.putByte(addressBytes + charCount++, (byte) charAtPos);
         } else if (charAtPos >= 0x800) {
            PlatformDependent.putByte(addressBytes + charCount++, (byte) (0xE0 | charAtPos >> 12 & 0x0F));
            PlatformDependent.putByte(addressBytes + charCount++, (byte) (0x80 | charAtPos >> 6 & 0x3F));
            PlatformDependent.putByte(addressBytes + charCount++, (byte) (0x80 | charAtPos >> 0 & 0x3F));
         } else {
            PlatformDependent.putByte(addressBytes + charCount++, (byte) (0xC0 | charAtPos >> 6 & 0x1F));
            PlatformDependent.putByte(addressBytes + charCount++, (byte) (0x80 | charAtPos >> 0 & 0x3F));
         }
      }

      final int writtenBytes = (charCount - index);
      return writtenBytes;
   }

   public static String readUTF(final ActiveMQBuffer input) {
      StringUtilBuffer buffer = UTF8Util.getThreadLocalBuffer();

      final int size = input.readUnsignedShort();

      if (logger.isTraceEnabled()) {
         // This message is too verbose for debug, that's why we are using trace here
         logger.trace("Reading string with utfSize={}", size);
      }
      if (PlatformDependent.hasUnsafe() && input.byteBuf() != null && input.byteBuf().hasMemoryAddress()) {
         final ByteBuf byteBuf = input.byteBuf();
         final long addressBytes = byteBuf.memoryAddress();
         final int index = byteBuf.readerIndex();
         byteBuf.skipBytes(size);
         final char[] chars = buffer.borrowCharBuffer(size);
         return unsafeOffHeapReadUTF(addressBytes, index, chars, size);
      }
      final byte[] bytes;
      final int index;
      if (input.byteBuf() != null && input.byteBuf().hasArray()) {
         final ByteBuf byteBuf = input.byteBuf();
         bytes = byteBuf.array();
         index = byteBuf.arrayOffset() + byteBuf.readerIndex();
         byteBuf.skipBytes(size);
      } else {
         bytes = buffer.borrowByteBuffer(size);
         index = 0;
         input.readBytes(bytes, 0, size);
      }
      final char[] chars = buffer.borrowCharBuffer(size);
      if (PlatformDependent.hasUnsafe()) {
         return unsafeOnHeapReadUTF(bytes, index, chars, size);
      } else {
         return readUTF(bytes, index, chars, size);
      }
   }

   private static String readUTF(final byte[] bytes, final int index, final char[] chars, final int size) {
      int count = index;
      final int limit = index + size;
      int byte1, byte2, byte3;
      int charCount = 0;

      while (count < limit) {
         byte1 = bytes[count++];

         if (byte1 >= 0 && byte1 <= 0x7F) {
            chars[charCount++] = (char) byte1;
         } else {
            int c = byte1 & 0xff;
            switch (c >> 4) {
               case 0xc:
               case 0xd:
                  byte2 = bytes[count++];
                  chars[charCount++] = (char) ((c & 0x1F) << 6 | byte2 & 0x3F);
                  break;
               case 0xe:
                  byte2 = bytes[count++];
                  byte3 = bytes[count++];
                  chars[charCount++] = (char) ((c & 0x0F) << 12 | (byte2 & 0x3F) << 6 | (byte3 & 0x3F) << 0);
                  break;
               default:
                  throw new InternalError("unhandled utf8 byte " + c);
            }
         }
      }

      return new String(chars, 0, charCount);
   }

   private static String unsafeOnHeapReadUTF(final byte[] bytes, final int index, final char[] chars, final int size) {
      int count = index;
      final int limit = index + size;
      int byte1, byte2, byte3;
      int charCount = 0;

      while (count < limit) {
         byte1 = PlatformDependent.getByte(bytes, count++);

         if (byte1 >= 0 && byte1 <= 0x7F) {
            chars[charCount++] = (char) byte1;
         } else {
            int c = byte1 & 0xff;
            switch (c >> 4) {
               case 0xc:
               case 0xd:
                  byte2 = PlatformDependent.getByte(bytes, count++);
                  chars[charCount++] = (char) ((c & 0x1F) << 6 | byte2 & 0x3F);
                  break;
               case 0xe:
                  byte2 = PlatformDependent.getByte(bytes, count++);
                  byte3 = PlatformDependent.getByte(bytes, count++);
                  chars[charCount++] = (char) ((c & 0x0F) << 12 | (byte2 & 0x3F) << 6 | (byte3 & 0x3F) << 0);
                  break;
               default:
                  throw new InternalError("unhandled utf8 byte " + c);
            }
         }
      }

      return new String(chars, 0, charCount);
   }

   private static String unsafeOffHeapReadUTF(final long addressBytes,
                                              final int index,
                                              final char[] chars,
                                              final int size) {
      int count = index;
      final int limit = index + size;
      int byte1, byte2, byte3;
      int charCount = 0;

      while (count < limit) {
         byte1 = PlatformDependent.getByte(addressBytes + count++);

         if (byte1 >= 0 && byte1 <= 0x7F) {
            chars[charCount++] = (char) byte1;
         } else {
            int c = byte1 & 0xff;
            switch (c >> 4) {
               case 0xc:
               case 0xd:
                  byte2 = PlatformDependent.getByte(addressBytes + count++);
                  chars[charCount++] = (char) ((c & 0x1F) << 6 | byte2 & 0x3F);
                  break;
               case 0xe:
                  byte2 = PlatformDependent.getByte(addressBytes + count++);
                  byte3 = PlatformDependent.getByte(addressBytes + count++);
                  chars[charCount++] = (char) ((c & 0x0F) << 12 | (byte2 & 0x3F) << 6 | (byte3 & 0x3F) << 0);
                  break;
               default:
                  throw new InternalError("unhandled utf8 byte " + c);
            }
         }
      }

      return new String(chars, 0, charCount);
   }

   private static StringUtilBuffer getThreadLocalBuffer() {
      SoftReference<StringUtilBuffer> softReference = UTF8Util.currentBuffer.get();
      StringUtilBuffer value;
      if (softReference == null) {
         value = new StringUtilBuffer();
         softReference = new SoftReference<>(value);
         UTF8Util.currentBuffer.set(softReference);
      } else {
         value = softReference.get();
      }

      if (value == null) {
         value = new StringUtilBuffer();
         softReference = new SoftReference<>(value);
         UTF8Util.currentBuffer.set(softReference);
      }

      return value;
   }

   public static void clearBuffer() {
      SoftReference<StringUtilBuffer> ref = UTF8Util.currentBuffer.get();
      if (ref != null && ref.get() != null) {
         ref.clear();
      }
   }

   // TODO look at replacing this with io.netty.buffer.ByteBufUtil.utf8Bytes(java.lang.CharSequence)
   public static int calculateUTFSize(final String str) {
      int calculatedLen = 0;
      for (int i = 0, stringLength = str.length(); i < stringLength; i++) {
         final char c = str.charAt(i);
         if (c <= 0x7f) {
            calculatedLen++;
         } else if (c >= 0x800) {
            calculatedLen += 3;
         } else {
            calculatedLen += 2;
         }
      }
      return calculatedLen;
   }

   private static final class StringUtilBuffer {

      private char[] charBuffer = null;

      private byte[] byteBuffer = null;

      public char[] borrowCharBuffer(final int newSize) {
         if (charBuffer == null || newSize > charBuffer.length) {
            charBuffer = new char[newSize];
         }
         return charBuffer;
      }

      public byte[] borrowByteBuffer(final int newSize) {
         if (byteBuffer == null || newSize > byteBuffer.length) {
            byteBuffer = new byte[newSize];
         }
         return byteBuffer;
      }

   }

}
