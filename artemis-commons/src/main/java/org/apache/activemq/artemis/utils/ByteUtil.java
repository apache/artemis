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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.logs.ActiveMQUtilBundle;
import org.jboss.logging.Logger;

public class ByteUtil {

   public static final String NON_ASCII_STRING = "@@@@@";

   private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
   private static final String prefix = "^\\s*(\\d+)\\s*";
   private static final String suffix = "(b)?\\s*$";
   private static final Pattern ONE = Pattern.compile(prefix + suffix, Pattern.CASE_INSENSITIVE);
   private static final Pattern KILO = Pattern.compile(prefix + "k" + suffix, Pattern.CASE_INSENSITIVE);
   private static final Pattern MEGA = Pattern.compile(prefix + "m" + suffix, Pattern.CASE_INSENSITIVE);
   private static final Pattern GIGA = Pattern.compile(prefix + "g" + suffix, Pattern.CASE_INSENSITIVE);
   private static final String[] BYTE_SUFFIXES = new String[] {"E", "P", "T", "G", "M", "K", ""};
   private static final double[] BYTE_MAGNITUDES = new double[7];

   static {
      for (int i = 18, j = 0; i >= 0; i -= 3, j++) {
         BYTE_MAGNITUDES[j] = Math.pow(10, i);
      }
   }

   public static void debugFrame(Logger logger, String message, ByteBuf byteIn) {
      if (logger.isTraceEnabled()) {
         outFrame(logger, message, byteIn, false);
      }
   }

   public static void outFrame(Logger logger, String message, ByteBuf byteIn, boolean info) {
      int location = byteIn.readerIndex();
      // debugging
      byte[] frame = new byte[byteIn.writerIndex()];
      byteIn.readBytes(frame);

      try {
         if (info) {
            logger.info(message + "\n" + ByteUtil.formatGroup(ByteUtil.bytesToHex(frame), 8, 16));
         } else {
            logger.trace(message + "\n" + ByteUtil.formatGroup(ByteUtil.bytesToHex(frame), 8, 16));
         }
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }

      byteIn.readerIndex(location);
   }

   public static String formatGroup(String str, int groupSize, int lineBreak) {
      StringBuffer buffer = new StringBuffer();

      int line = 1;
      buffer.append("/*  0 */ \"");
      for (int i = 0; i < str.length(); i += groupSize) {
         buffer.append(str.substring(i, i + Math.min(str.length() - i, groupSize)));

         if ((i + groupSize) % lineBreak == 0) {
            buffer.append("\" +\n/* ");
            line++;
            if (line < 10) {
               buffer.append(" ");
            }
            buffer.append(Integer.toString(i) + " */ \"");
         } else if ((i + groupSize) % groupSize == 0 && str.length() - i > groupSize) {
            buffer.append("\" + \"");
         }
      }

      buffer.append("\";");

      return buffer.toString();

   }

   public static String maxString(String value, int size) {
      if (value.length() < size) {
         return value;
      } else {
         return value.substring(0, size / 2) + " ... " + value.substring(value.length() - size / 2);
      }
   }

   public static String bytesToHex(byte[] bytes) {
      char[] hexChars = new char[bytes.length * 2];
      for (int j = 0; j < bytes.length; j++) {
         int v = bytes[j] & 0xFF;
         hexChars[j * 2] = hexArray[v >>> 4];
         hexChars[j * 2 + 1] = hexArray[v & 0x0F];
      }
      return new String(hexChars);
   }

   /** Simplify reading of a byte array in a programmers understable way */
   public static String debugByteArray(byte[] byteArray) {
      StringWriter builder = new StringWriter();
      PrintWriter writer = new PrintWriter(builder);
      for (int i = 0; i < byteArray.length; i++) {
         writer.print("\t[" + i + "]=" + ByteUtil.byteToChar(byteArray[i]) + " / " + byteArray[i]);
         if (i > 0 && i % 8 == 0) {
            writer.println();
         } else {
            writer.print(" ");
         }
      }
      return builder.toString();
   }


   public static String byteToChar(byte value) {
      char[] hexChars = new char[2];
      int v = value & 0xFF;
      hexChars[0] = hexArray[v >>> 4];
      hexChars[1] = hexArray[v & 0x0F];
      return new String(hexChars);
   }

   public static String bytesToHex(byte[] bytes, int groupSize) {
      if (bytes == null) {
         return "NULL";
      }

      if (bytes.length == 0) {
         return "[]";
      }

      char[] hexChars = new char[bytes.length * 2 + numberOfGroups(bytes, groupSize)];
      int outPos = 0;
      for (int j = 0; j < bytes.length; j++) {
         if (j > 0 && j % groupSize == 0) {
            hexChars[outPos++] = ' ';
         }
         int v = bytes[j] & 0xFF;
         hexChars[outPos++] = hexArray[v >>> 4];
         hexChars[outPos++] = hexArray[v & 0x0F];
      }
      return new String(hexChars);
   }

   public static String toSimpleString(byte[] bytes) {
      SimpleString simpleString = new SimpleString(bytes);
      String value = simpleString.toString();

      for (char c : value.toCharArray()) {
         if (c < ' ' || c > 127) {
            return NON_ASCII_STRING;
         }
      }

      return value;
   }

   private static int numberOfGroups(byte[] bytes, int groupSize) {
      int groups = bytes.length / groupSize;

      if (bytes.length % groupSize == 0) {
         groups--;
      }

      return groups;
   }

   public static final byte[] intToBytes(int value) {
      return new byte[] {
         (byte)(value >>> 24),
         (byte)(value >>> 16),
         (byte)(value >>> 8),
         (byte)value
      };
   }

   public static int bytesToInt(byte[] b) {
      return ((int) b[3] & 0xff)
            | ((int) b[2] & 0xff) << 8
            | ((int) b[1] & 0xff) << 16
            | ((int) b[0] & 0xff) << 24;
   }

   public static long bytesToLong(byte[] b) {
      return ((long) b[7] & 0xff)
         | ((long) b[6] & 0xff) << 8
         | ((long) b[5] & 0xff) << 16
         | ((long) b[4] & 0xff) << 24
         | ((long) b[3] & 0xff) << 32
         | ((long) b[2] & 0xff) << 40
         | ((long) b[1] & 0xff) << 48
         | ((long) b[0] & 0xff) << 56;
   }

   public static byte[] longToBytes(long value) {
      byte[] output = new byte[8];
      longToBytes(value, output, 0);
      return output;
   }

   public static void longToBytes(long x, byte[] output, int offset) {
      output[offset] = (byte)(x >>> 56);
      output[offset + 1] = (byte)(x >>> 48);
      output[offset + 2] = (byte)(x >>> 40);
      output[offset + 3] = (byte)(x >>> 32);
      output[offset + 4] = (byte)(x >>> 24);
      output[offset + 5] = (byte)(x >>> 16);
      output[offset + 6] = (byte)(x >>>  8);
      output[offset + 7] = (byte)(x);
   }

   public static byte[] doubleLongToBytes(long value1, long value2) {
      byte[] output = new byte[16];
      longToBytes(value1, output, 0);
      longToBytes(value2, output, 8);
      return output;
   }

   public static byte[] hexToBytes(String hexStr) {
      byte[] bytes = new byte[hexStr.length() / 2];
      for (int i = 0; i < bytes.length; i++) {
         bytes[i] = (byte) Integer.parseInt(hexStr.substring(2 * i, 2 * i + 2), 16);
      }
      return bytes;
   }

   public static String readLine(ActiveMQBuffer buffer) {
      StringBuilder sb = new StringBuilder("");
      char c = buffer.readChar();
      while (c != '\n') {
         sb.append(c);
         c = buffer.readChar();
      }
      return sb.toString();
   }

   public static long convertTextBytes(final String text) {
      try {
         Matcher m = ONE.matcher(text);
         if (m.matches()) {
            return Long.valueOf(Long.parseLong(m.group(1)));
         }

         m = KILO.matcher(text);
         if (m.matches()) {
            return Long.valueOf(Long.parseLong(m.group(1)) * 1024);
         }

         m = MEGA.matcher(text);
         if (m.matches()) {
            return Long.valueOf(Long.parseLong(m.group(1)) * 1024 * 1024);
         }

         m = GIGA.matcher(text);
         if (m.matches()) {
            return Long.valueOf(Long.parseLong(m.group(1)) * 1024 * 1024 * 1024);
         }

         return Long.parseLong(text);
      } catch (NumberFormatException e) {
         throw ActiveMQUtilBundle.BUNDLE.failedToParseLong(text);
      }
   }

   public static int hashCode(byte[] bytes) {
      if (PlatformDependent.hasUnsafe() && PlatformDependent.isUnaligned()) {
         return unsafeHashCode(bytes);
      }
      return Arrays.hashCode(bytes);
   }

   /**
    * This hash code computation is borrowed by {@link io.netty.buffer.ByteBufUtil#hashCode(ByteBuf)}.
    */
   private static int unsafeHashCode(byte[] bytes) {
      if (bytes == null) {
         return 0;
      }
      final int len = bytes.length;
      int hashCode = 1;
      final int intCount = len >>> 2;
      int arrayIndex = 0;
      // reading in batch both help hash code computation data dependencies and save memory bandwidth
      for (int i = 0; i < intCount; i++) {
         hashCode = 31 * hashCode + PlatformDependent.getInt(bytes, arrayIndex);
         arrayIndex += Integer.BYTES;
      }
      final byte remaining = (byte) (len & 3);
      if (remaining > 0) {
         hashCode = unsafeUnrolledHashCode(bytes, arrayIndex, remaining, hashCode);
      }
      return hashCode == 0 ? 1 : hashCode;
   }

   private static int unsafeUnrolledHashCode(byte[] bytes, int index, int bytesCount, int h) {
      // there is still the hash data dependency but is more friendly
      // then a plain loop, given that we know no loop is needed here
      assert bytesCount > 0 && bytesCount < 4;
      h = 31 * h + PlatformDependent.getByte(bytes, index);
      if (bytesCount == 1) {
         return h;
      }
      h = 31 * h + PlatformDependent.getByte(bytes, index + 1);
      if (bytesCount == 2) {
         return h;
      }
      h = 31 * h + PlatformDependent.getByte(bytes, index + 2);
      return h;
   }

   public static boolean equals(final byte[] left, final byte[] right) {
      return equals(left, right, 0, right.length);
   }

   public static boolean equals(final byte[] left,
                                final byte[] right,
                                final int rightOffset,
                                final int rightLength) {
      if (left == right)
         return true;
      if (left == null || right == null)
         return false;
      if (left.length != rightLength)
         return false;
      if (PlatformDependent.isUnaligned() && PlatformDependent.hasUnsafe()) {
         return equalsUnsafe(left, right, rightOffset, rightLength);
      } else {
         return equalsSafe(left, right, rightOffset, rightLength);
      }
   }

   private static boolean equalsSafe(byte[] left, byte[] right, int rightOffset, int rightLength) {
      for (int i = 0; i < rightLength; i++)
         if (left[i] != right[rightOffset + i])
            return false;
      return true;
   }

   private static boolean equalsUnsafe(final byte[] left,
                                       final byte[] right,
                                       final int rightOffset,
                                       final int rightLength) {
      final int longCount = rightLength >>> 3;
      final int bytesCount = rightLength & 7;
      int bytesIndex = rightOffset;
      int charsIndex = 0;
      for (int i = 0; i < longCount; i++) {
         final long charsLong = PlatformDependent.getLong(left, charsIndex);
         final long bytesLong = PlatformDependent.getLong(right, bytesIndex);
         if (charsLong != bytesLong) {
            return false;
         }
         bytesIndex += 8;
         charsIndex += 8;
      }
      for (int i = 0; i < bytesCount; i++) {
         final byte charsByte = PlatformDependent.getByte(left, charsIndex);
         final byte bytesByte = PlatformDependent.getByte(right, bytesIndex);
         if (charsByte != bytesByte) {
            return false;
         }
         bytesIndex++;
         charsIndex++;
      }
      return true;
   }

   /**
    * This ensure a more exact resizing then {@link ByteBuf#ensureWritable(int)}, if needed.<br>
    * It won't try to trim a large enough buffer.
    */
   public static void ensureExactWritable(ByteBuf buffer, int minWritableBytes) {
      if (buffer.maxFastWritableBytes() < minWritableBytes) {
         buffer.capacity(buffer.writerIndex() + minWritableBytes);
      }
   }


   /**
    * Returns {@code true} if  the {@link SimpleString} encoded content into {@code bytes} is equals to {@code s},
    * {@code false} otherwise.
    * <p>
    * It assumes that the {@code bytes} content is read using {@link SimpleString#readSimpleString(ByteBuf, int)} ie starting right after the
    * length field.
    */
   public static boolean equals(final byte[] bytes, final ByteBuf byteBuf, final int offset, final int length) {
      if (bytes.length != length)
         return false;
      if (PlatformDependent.isUnaligned() && PlatformDependent.hasUnsafe()) {
         if ((offset + length) > byteBuf.writerIndex()) {
            throw new IndexOutOfBoundsException();
         }
         if (byteBuf.hasArray()) {
            return equals(bytes, byteBuf.array(), byteBuf.arrayOffset() + offset, length);
         } else if (byteBuf.hasMemoryAddress()) {
            return equalsOffHeap(bytes, byteBuf.memoryAddress(), offset, length);
         }
      }
      return equalsOnHeap(bytes, byteBuf, offset, length);
   }

   private static boolean equalsOnHeap(final byte[] bytes, final ByteBuf byteBuf, final int offset, final int length) {
      if (bytes.length != length)
         return false;
      for (int i = 0; i < length; i++)
         if (bytes[i] != byteBuf.getByte(offset + i))
            return false;
      return true;
   }

   private static boolean equalsOffHeap(final byte[] bytes,
                                        final long address,
                                        final int offset,
                                        final int length) {
      final int longCount = length >>> 3;
      final int bytesCount = length & 7;
      long bytesAddress = address + offset;
      int charsIndex = 0;
      for (int i = 0; i < longCount; i++) {
         final long charsLong = PlatformDependent.getLong(bytes, charsIndex);
         final long bytesLong = PlatformDependent.getLong(bytesAddress);
         if (charsLong != bytesLong) {
            return false;

         }
         bytesAddress += 8;
         charsIndex += 8;
      }
      for (int i = 0; i < bytesCount; i++) {
         final byte charsByte = PlatformDependent.getByte(bytes, charsIndex);
         final byte bytesByte = PlatformDependent.getByte(bytesAddress);
         if (charsByte != bytesByte) {
            return false;

         }
         bytesAddress++;
         charsIndex++;
      }
      return true;
   }

   public static int intFromBytes(byte b1, byte b2, byte b3, byte b4) {
      return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
   }

   /**
    * It zeroes the whole {@link ByteBuffer#capacity()} of the given {@code buffer}.
    *
    * @throws ReadOnlyBufferException if {@code buffer} is read-only
    */
   public static void zeros(final ByteBuffer buffer) {
      uncheckedZeros(buffer, 0, buffer.capacity());
   }

   /**
    * It zeroes {@code bytes} of the given {@code buffer}, starting (inclusive) from {@code offset}.
    *
    * @throws IndexOutOfBoundsException if {@code offset + bytes > }{@link ByteBuffer#capacity()} or {@code offset >= }{@link ByteBuffer#capacity()}
    * @throws IllegalArgumentException  if {@code offset} or {@code capacity} are less then 0
    * @throws ReadOnlyBufferException   if {@code buffer} is read-only
    */
   public static void zeros(final ByteBuffer buffer, int offset, int bytes) {
      if (offset < 0 || bytes < 0) {
         throw new IllegalArgumentException();
      }
      final int capacity = buffer.capacity();
      if (offset >= capacity || (offset + bytes) > capacity) {
         throw new IndexOutOfBoundsException();
      }
      uncheckedZeros(buffer, offset, bytes);
   }

   private static void uncheckedZeros(final ByteBuffer buffer, int offset, int bytes) {
      if (buffer.isReadOnly()) {
         throw new ReadOnlyBufferException();
      }
      final byte zero = (byte) 0;
      if (buffer.isDirect() && PlatformDependent.hasUnsafe()) {
         PlatformDependent.setMemory(PlatformDependent.directBufferAddress(buffer) + offset, bytes, zero);
      } else if (buffer.hasArray()) {
         //SIMD OPTIMIZATION
         final int arrayOffset = buffer.arrayOffset();
         final int start = arrayOffset + offset;
         Arrays.fill(buffer.array(), start, start + bytes, zero);
      } else {
         //slow path
         for (int i = 0; i < bytes; i++) {
            buffer.put(i + offset, zero);
         }
      }
   }

   public static String getHumanReadableByteCount(long bytes) {
      if (bytes == 0) {
         return "0B";
      }

      int i = 0;
      while (i < BYTE_MAGNITUDES.length && BYTE_MAGNITUDES[i] > bytes) {
         i++;
      }

      return String.format("%.1f%sB", bytes / BYTE_MAGNITUDES[i], BYTE_SUFFIXES[i]);
   }
}
