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
package org.apache.activemq.artemis.api.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.utils.AbstractByteBufPool;
import org.apache.activemq.artemis.utils.AbstractPool;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 * A simple String class that can store all characters, and stores as simple {@code byte[]}, this minimises expensive
 * copying between String objects.
 * <p>
 * This object is used heavily throughout ActiveMQ Artemis for performance reasons.
 */
public final class SimpleString implements CharSequence, Serializable, Comparable<SimpleString> {

   private static final SimpleString EMPTY = SimpleString.of("");
   private static final long serialVersionUID = 4204223851422244307L;

   private final byte[] data;

   private transient int hash;

   // Cache the string
   private transient String str;

   private transient String[] paths;

   /**
    * Returns a SimpleString constructed from the {@code string} parameter.
    * <p>
    * If {@code string} is {@code null}, the return value will be {@code null} too.
    *
    * @param string String used to instantiate a SimpleString.
    * @return A new SimpleString
    */
   public static SimpleString of(final String string) {
      if (string == null) {
         return null;
      }
      return new SimpleString(string);
   }

   /**
    * Returns a SimpleString constructed from the {@code string} parameter.
    * <p>
    * If {@code string} is {@code null}, the return value will be {@code null} too.
    *
    * @param string String used to instantiate a SimpleString.
    * @param pool   The pool from which to create the SimpleString
    * @return A new SimpleString
    */
   public static SimpleString of(final String string, StringSimpleStringPool pool) {
      if (pool == null) {
         return of(string);
      }
      return pool.getOrCreate(string);
   }

   /**
    * creates a SimpleString from a byte array
    *
    * @param data the byte array to use
    */
   public static SimpleString of(final byte[] data) {
      return new SimpleString(data);
   }

   /**
    * creates a SimpleString from a character
    *
    * @param c the char to use
    */
   public static SimpleString of(final char c) {
      return new SimpleString(c);
   }

   /**
    * Returns a SimpleString constructed from the {@code string} parameter.
    * <p>
    * If {@code string} is {@code null}, the return value will be {@code null} too.
    *
    * @param string String used to instantiate a SimpleString.
    * @return A new SimpleString
    * @deprecated Use {@link #of(String)} instead.
    */
   @Deprecated(forRemoval = true)
   public static SimpleString toSimpleString(final String string) {
      return of(string);
   }

   /**
    * Returns a SimpleString constructed from the {@code string} parameter.
    * <p>
    * If {@code string} is {@code null}, the return value will be {@code null} too.
    *
    * @param string String used to instantiate a SimpleString.
    * @param pool   The pool from which to create the SimpleString
    * @return A new SimpleString
    * @deprecated Use {@link #of(String, StringSimpleStringPool)} instead.
    */
   @Deprecated(forRemoval = true)
   public static SimpleString toSimpleString(final String string, StringSimpleStringPool pool) {
      return of(string, pool);
   }

   /**
    * creates a SimpleString from a conventional String
    *
    * @param string the string to transform
    * @deprecated Use {@link #of(String)} instead.
    */
   @Deprecated(forRemoval = true)
   public SimpleString(final String string) {
      int len = string.length();

      data = new byte[len << 1];

      int j = 0;

      for (int i = 0; i < len; i++) {
         char c = string.charAt(i);

         byte low = (byte) (c & 0xFF); // low byte

         data[j++] = low;

         byte high = (byte) (c >> 8 & 0xFF); // high byte

         data[j++] = high;
      }

      str = string;
   }

   /**
    * creates a SimpleString from a byte array
    *
    * @param data the byte array to use
    * @deprecated Use {@link #of(byte[])} instead.
    */
   @Deprecated(forRemoval = true)
   public SimpleString(final byte[] data) {
      this.data = data;
   }

   /**
    * creates a SimpleString from a character
    *
    * @param c the char to use
    * @deprecated Use {@link #of(char)} instead.
    */
   @Deprecated(forRemoval = true)
   public SimpleString(final char c) {
      data = new byte[2];

      byte low = (byte) (c & 0xFF); // low byte

      data[0] = low;

      byte high = (byte) (c >> 8 & 0xFF); // high byte

      data[1] = high;
   }

   public boolean isBlank() {
      boolean result = true;
      for (int i = 0; i < length(); i++) {
         if (!Character.isWhitespace(charAt(i))) {
            result = false;
            break;
         }
      }
      return result;
   }

   @Override
   public boolean isEmpty() {
      return data.length == 0;
   }

   // CharSequence implementation
   // ---------------------------------------------------------------------------

   @Override
   public int length() {
      return data.length >> 1;
   }

   @Override
   public char charAt(int pos) {
      if (pos < 0 || pos >= data.length >> 1) {
         throw new IndexOutOfBoundsException();
      }
      pos <<= 1;

      return (char) ((data[pos] & 0xFF) | (data[pos + 1] << 8) & 0xFF00);
   }

   @Override
   public CharSequence subSequence(final int start, final int end) {
      return subSeq(start, end);
   }

   public static SimpleString readNullableSimpleString(ByteBuf buffer) {
      int b = buffer.readByte();
      if (b == DataConstants.NULL) {
         return null;
      }
      return readSimpleString(buffer);
   }

   public static SimpleString readNullableSimpleString(ByteBuf buffer, ByteBufSimpleStringPool pool) {
      int b = buffer.readByte();
      if (b == DataConstants.NULL) {
         return null;
      }
      return readSimpleString(buffer, pool);
   }

   public static SimpleString readSimpleString(ByteBuf buffer) {
      int len = buffer.readInt();
      return readSimpleString(buffer, len);
   }

   public static SimpleString readSimpleString(ByteBuf buffer, ByteBufSimpleStringPool pool) {
      if (pool == null) {
         return readSimpleString(buffer);
      }
      return pool.getOrCreate(buffer);
   }

   public static SimpleString readSimpleString(final ByteBuf buffer, final int length) {
      if (length > buffer.readableBytes()) {
         throw new IndexOutOfBoundsException("Error reading in simpleString, length=" + length + " is greater than readableBytes=" + buffer.readableBytes());
      }
      byte[] data = new byte[length];
      buffer.readBytes(data);
      return SimpleString.of(data);
   }

   public static void writeNullableSimpleString(ByteBuf buffer, SimpleString val) {
      if (val == null) {
         buffer.writeByte(DataConstants.NULL);
      } else {
         buffer.writeByte(DataConstants.NOT_NULL);
         writeSimpleString(buffer, val);
      }
   }

   public static void writeSimpleString(ByteBuf buffer, SimpleString val) {
      byte[] data = val.getData();
      buffer.writeInt(data.length);
      buffer.writeBytes(data);
   }

   public SimpleString subSeq(final int start, final int end) {
      int len = data.length >> 1;

      if (end < start || start < 0 || end > len) {
         throw new IndexOutOfBoundsException();
      } else {
         int newlen = end - start << 1;
         byte[] bytes = new byte[newlen];

         System.arraycopy(data, start << 1, bytes, 0, newlen);

         return SimpleString.of(bytes);
      }
   }

   // Comparable implementation -------------------------------------

   @Override
   public int compareTo(final SimpleString o) {
      return toString().compareTo(o.toString());
   }

   /**
    * {@return the underlying byte array of this {@code SimpleString}}
    */
   public byte[] getData() {
      return data;
   }

   /**
    * {@return {@code true} if the {@code SimpleString} parameter starts with the same data as this one, otherwise
    * {@code false}}
    *
    * @param other the SimpleString to look for
    */
   public boolean startsWith(final SimpleString other) {
      byte[] otherdata = other.data;

      if (otherdata.length > data.length) {
         return false;
      }

      for (int i = 0; i < otherdata.length; i++) {
         if (data[i] != otherdata[i]) {
            return false;
         }
      }

      return true;
   }

   /**
    * returns true if the SimpleString parameter starts with the same char. false if not.
    *
    * @param other the char to look for
    * @return true if this SimpleString starts with the same data
    */
   public boolean startsWith(final char other) {
      return data.length > 0 && data[0] == other;
   }

   @Override
   public String toString() {
      if (str == null) {
         int len = data.length >> 1;

         char[] chars = new char[len];

         int j = 0;

         for (int i = 0; i < len; i++) {
            int low = data[j++] & 0xFF;

            int high = data[j++] << 8 & 0xFF00;

            chars[i] = (char) (low | high);
         }

         str = new String(chars);
      }

      return str;
   }

   /**
    * note the result of the first use is cached, the separator is configured on the postoffice so will be static for
    * the duration of a server instance. calling with different separator values could give invalid results
    *
    * @param separator value from wildcardConfiguration
    * @return String[] reference to the split paths or the cached value if previously called
    */
   public String[] getPaths(final char separator) {
      if (paths != null) {
         return paths;
      }
      List<String> pathsList = new ArrayList<>();
      StringBuilder pathAccumulator = new StringBuilder();
      char[] chars = toString().toCharArray();
      for (int i = 0; i < chars.length; i++) {
         char c = chars[i];
         if (c == separator && (i == 0 || chars[i - 1] != '\\')) {
            pathsList.add(pathAccumulator.toString());
            pathAccumulator.delete(0, pathAccumulator.length());
         } else {
            pathAccumulator.append(c);
         }
      }
      pathsList.add(pathAccumulator.toString());

      paths = pathsList.toArray(new String[0]);
      return paths;
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof SimpleString other)) {
         return false;
      }

      return ByteUtil.equals(data, other.data);
   }

   /**
    * Returns {@code true} if  the {@link SimpleString} encoded content into {@code bytes} is equals to {@code s},
    * {@code false} otherwise.
    * <p>
    * It assumes that the {@code bytes} content is read using {@link SimpleString#readSimpleString(ByteBuf, int)} ie
    * starting right after the length field.
    */
   public boolean equals(final ByteBuf byteBuf, final int offset, final int length) {
      return ByteUtil.equals(data, byteBuf, offset, length);
   }

   @Override
   public int hashCode() {
      if (hash == 0) {
         int tmphash = 0;
         for (byte element : data) {
            tmphash = (tmphash << 5) - tmphash + element; // (hash << 5) - hash is same as hash * 31
         }
         hash = tmphash;
      }

      return hash;
   }

   /**
    * Splits this SimpleString into an array of SimpleString using the char param as the delimiter. i.e. "a.b" would
    * return "a" and "b" if . was the delimiter
    *
    * @param delim The delimiter to split this SimpleString on.
    * @return An array of SimpleStrings
    */
   public SimpleString[] split(final char delim) {
      if (this.str != null) {
         return splitWithCachedString(this, delim);
      } else {
         return splitWithoutCachedString(delim);
      }
   }

   private SimpleString[] splitWithoutCachedString(final char delim) {
      List<SimpleString> all = null;

      byte low = (byte) (delim & 0xFF); // low byte
      byte high = (byte) (delim >> 8 & 0xFF); // high byte

      int lasPos = 0;
      for (int i = 0; i + 1 < data.length; i += 2) {
         if (data[i] == low && data[i + 1] == high) {
            byte[] bytes = new byte[i - lasPos];
            System.arraycopy(data, lasPos, bytes, 0, bytes.length);
            lasPos = i + 2;

            // We will create the ArrayList lazily
            if (all == null) {
               // There will be at least 2 strings on this case (which is the actual common usecase)
               // For that reason I'm allocating the ArrayList with 2 already
               // I have thought about using LinkedList here but I think this will be good enough already
               // Note by Clebert
               all = new ArrayList<>(2);
            }
            all.add(SimpleString.of(bytes));
         }
      }

      if (all == null) {
         return new SimpleString[]{this};
      } else {
         // Adding the last one
         byte[] bytes = new byte[data.length - lasPos];
         System.arraycopy(data, lasPos, bytes, 0, bytes.length);
         all.add(SimpleString.of(bytes));

         // Converting it to arrays
         SimpleString[] parts = new SimpleString[all.size()];
         return all.toArray(parts);
      }
   }

   private static SimpleString[] splitWithCachedString(final SimpleString simpleString, final int delim) {
      final String str = simpleString.str;
      final byte[] data = simpleString.data;
      final int length = str.length();
      List<SimpleString> all = null;
      int index = 0;
      while (index < length) {
         final int delimIndex = str.indexOf(delim, index);
         if (delimIndex == -1) {
            //just need to add the last one
            break;
         } else {
            all = addSimpleStringPart(all, data, index, delimIndex);
         }
         index = delimIndex + 1;
      }
      if (all == null) {
         return new SimpleString[]{simpleString};
      } else {
         // Adding the last one
         all = addSimpleStringPart(all, data, index, length);
         // Converting it to arrays
         final SimpleString[] parts = new SimpleString[all.size()];
         return all.toArray(parts);
      }
   }

   private static List<SimpleString> addSimpleStringPart(List<SimpleString> all,
                                                         final byte[] data,
                                                         final int startIndex,
                                                         final int endIndex) {
      final int expectedLength = endIndex - startIndex;
      final SimpleString ss;
      if (expectedLength == 0) {
         ss = EMPTY;
      } else {
         //extract a byte[] copy from this
         final int ssIndex = startIndex << 1;
         final int delIndex = endIndex << 1;
         final byte[] bytes = Arrays.copyOfRange(data, ssIndex, delIndex);
         ss = SimpleString.of(bytes);
      }
      // We will create the ArrayList lazily
      if (all == null) {
         // There will be at least 3 strings on this case (which is the actual common usecase)
         // For that reason I'm allocating the ArrayList with 3 already
         all = new ArrayList<>(3);
      }
      all.add(ss);
      return all;
   }

   /**
    * checks to see if this SimpleString contains the char parameter passed in
    *
    * @param c the char to check for
    * @return true if the char is found, false otherwise
    */
   public boolean contains(final char c) {
      if (this.str != null) {
         return this.str.indexOf(c) != -1;
      }
      final byte low = (byte) (c & 0xFF); // low byte
      final byte high = (byte) (c >> 8 & 0xFF); // high byte

      for (int i = 0; i + 1 < data.length; i += 2) {
         if (data[i] == low && data[i + 1] == high) {
            return true;
         }
      }
      return false;
   }

   /**
    * Concatenates a SimpleString and a String
    *
    * @param toAdd the String to concatenate with.
    * @return the concatenated SimpleString
    */
   public SimpleString concat(final String toAdd) {
      int len = toAdd.length();
      byte[] bytes = new byte[data.length + len * 2];
      System.arraycopy(data, 0, bytes, 0, data.length);
      for (int i = 0; i < len; i++) {
         char c = toAdd.charAt(i);
         int offset = data.length + i * 2;
         bytes[offset] = (byte) (c & 0xFF);
         bytes[offset + 1] = (byte) (c >> 8 & 0xFF);
      }
      return SimpleString.of(bytes);
   }

   /**
    * Concatenates 2 SimpleString's
    *
    * @param toAdd the SimpleString to concatenate with.
    * @return the concatenated SimpleString
    */
   public SimpleString concat(final SimpleString toAdd) {
      byte[] bytes = new byte[data.length + toAdd.getData().length];
      System.arraycopy(data, 0, bytes, 0, data.length);
      System.arraycopy(toAdd.getData(), 0, bytes, data.length, toAdd.getData().length);
      return SimpleString.of(bytes);
   }

   /**
    * Concatenates a SimpleString and a char
    *
    * @param c the char to concate with.
    * @return the concatenated SimpleString
    */
   public SimpleString concat(final char c) {
      byte[] bytes = new byte[data.length + 2];
      System.arraycopy(data, 0, bytes, 0, data.length);
      bytes[data.length] = (byte) (c & 0xFF);
      bytes[data.length + 1] = (byte) (c >> 8 & 0xFF);
      return SimpleString.of(bytes);
   }

   /**
    * {@return the size of this SimpleString}
    */
   public int sizeof() {
      return DataConstants.SIZE_INT + data.length;
   }

   /**
    * {@return the size of a SimpleString}
    *
    * @param str the SimpleString to check
    */
   public static int sizeofString(final SimpleString str) {
      return str.sizeof();
   }

   /**
    * {@return the size of a SimpleString which could be {@code null}}
    *
    * @param str the SimpleString to check
    */
   public static int sizeofNullableString(final SimpleString str) {
      if (str == null) {
         return 1;
      } else {
         return 1 + str.sizeof();
      }
   }

   /**
    * This method performs a similar function to {@link String#getChars(int, int, char[], int)}. This is mainly used by
    * the Parsers on Filters
    *
    * @param srcBegin The srcBegin
    * @param srcEnd   The srcEnd
    * @param dst      The destination array
    * @param dstPos   The destination position
    */
   public void getChars(final int srcBegin, final int srcEnd, final char[] dst, final int dstPos) {
      if (srcBegin < 0) {
         throw new StringIndexOutOfBoundsException(srcBegin);
      }
      if (srcEnd > length()) {
         throw new StringIndexOutOfBoundsException(srcEnd);
      }
      if (srcBegin > srcEnd) {
         throw new StringIndexOutOfBoundsException(srcEnd - srcBegin);
      }

      int j = srcBegin * 2;
      int d = dstPos;

      for (int i = srcBegin; i < srcEnd; i++) {
         int low = data[j++] & 0xFF;

         int high = data[j++] << 8 & 0xFF00;

         dst[d++] = (char) (low | high);
      }
   }

   public static final class ByteBufSimpleStringPool extends AbstractByteBufPool<SimpleString> {

      public static final int DEFAULT_MAX_LENGTH = 36;

      private final int maxLength;

      public ByteBufSimpleStringPool() {
         this.maxLength = DEFAULT_MAX_LENGTH;
      }

      public ByteBufSimpleStringPool(final int capacity) {
         this(capacity, DEFAULT_MAX_LENGTH);
      }

      public ByteBufSimpleStringPool(final int capacity, final int maxCharsLength) {
         super(capacity);
         this.maxLength = maxCharsLength;
      }

      @Override
      protected boolean isEqual(final SimpleString entry, final ByteBuf byteBuf, final int offset, final int length) {
         if (entry == null) {
            return false;
         }
         return entry.equals(byteBuf, offset, length);
      }

      @Override
      protected boolean canPool(final ByteBuf byteBuf, final int length) {
         assert length % 2 == 0 : "length must be a multiple of 2";
         final int expectedStringLength = length >> 1;
         return expectedStringLength <= maxLength;
      }

      @Override
      protected SimpleString create(final ByteBuf byteBuf, final int length) {
         return readSimpleString(byteBuf, length);
      }
   }

   public static final class StringSimpleStringPool extends AbstractPool<String, SimpleString> {

      public StringSimpleStringPool() {
         super();
      }

      /**
       * @see AbstractPool#AbstractPool(int)
       */
      public StringSimpleStringPool(final int capacity) {
         super(capacity);
      }

      @Override
      protected SimpleString create(String value) {
         return of(value);
      }

      @Override
      protected boolean isEqual(SimpleString entry, String value) {
         if (entry == null) {
            return false;
         }
         return entry.toString().equals(value);
      }
   }
}
