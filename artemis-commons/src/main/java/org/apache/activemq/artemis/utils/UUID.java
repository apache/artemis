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

import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * UUID represents Universally Unique Identifiers (aka Global UID in Windows world). UUIDs are usually generated via
 * UUIDGenerator (or in case of 'Null UUID', 16 zero bytes, via static method getNullUUID()), or received from external
 * systems.
 * <p>
 * By default class caches the string presentations of UUIDs so that description is only created the first time it's
 * needed. For memory stingy applications this caching can be turned off (note though that if uuid.toString() is never
 * called, desc is never calculated so only loss is the space allocated for the desc pointer... which can of course be
 * commented out to save memory).
 * <p>
 * Similarly, hash code is calculated when it's needed for the first time, and from thereon that value is just returned.
 * This means that using UUIDs as keys should be reasonably efficient.
 * <p>
 * UUIDs can be compared for equality, serialized, cloned and even sorted. Equality is a simple bit-wise comparison.
 * Ordering (for sorting) is done by first ordering based on type (in the order of numeric values of types), secondarily
 * by time stamp (only for time-based time stamps), and finally by straight numeric byte-by-byte comparison (from most
 * to least significant bytes).
 */
public final class UUID {

   private static final String kHexChars = "0123456789abcdef";

   public static final byte INDEX_CLOCK_HI = 6;

   public static final byte INDEX_CLOCK_MID = 4;

   public static final byte INDEX_CLOCK_LO = 0;

   public static final byte INDEX_TYPE = 6;

   // Clock seq. & variant are multiplexed...
   public static final byte INDEX_CLOCK_SEQUENCE = 8;

   public static final byte INDEX_VARIATION = 8;

   public static final byte TYPE_NULL = 0;

   public static final byte TYPE_TIME_BASED = 1;

   public static final byte TYPE_DCE = 2; // Not used

   public static final byte TYPE_NAME_BASED = 3;

   public static final byte TYPE_RANDOM_BASED = 4;

   public static final char HYPHEN = '-';

   public static final int UUID_STRING_LENGTH = 36;

   // 'Standard' namespaces defined (suggested) by UUID specs:
   public static final String NAMESPACE_DNS = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

   public static final String NAMESPACE_URL = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";

   public static final String NAMESPACE_OID = "6ba7b812-9dad-11d1-80b4-00c04fd430c8";

   public static final String NAMESPACE_X500 = "6ba7b814-9dad-11d1-80b4-00c04fd430c8";

   /*
    * By default let's cache desc, can be turned off. For hash code there's no
    * point in turning it off (since the int is already part of the instance
    * memory allocation); if you want to save those 4 bytes (or possibly bit
    * more if alignment is bad) just comment out hash caching.
    */
   private static boolean sDescCaching = true;

   private final byte[] mId;

   // Both string presentation and hash value may be cached...
   private transient String mDesc = null;

   private transient int mHashCode = 0;

   /**
    * @param type UUID type
    * @param data 16 byte UUID contents
    */
   public UUID(final int type, final byte[] data) {
      assert data.length == 16;
      mId = data;
      // Type is multiplexed with time_hi:
      mId[UUID.INDEX_TYPE] &= (byte) 0x0F;
      mId[UUID.INDEX_TYPE] |= (byte) (type << 4);
      // Variant masks first two bits of the clock_seq_hi:
      mId[UUID.INDEX_VARIATION] &= (byte) 0x3F;
      mId[UUID.INDEX_VARIATION] |= (byte) 0x80;
   }

   private UUID(final byte[] data) {
      assert data.length == 16;
      mId = data;
   }

   /**
    * This is for conversions between two types of UUID
    */
   public UUID(java.util.UUID uuid) {
      this(ByteUtil.doubleLongToBytes(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits()));
   }

   public byte[] asBytes() {
      return mId;
   }

   /**
    * Could use just the default hash code, but we can probably create a better identity hash (ie. same contents
    * generate same hash) manually, without sacrificing speed too much. Although multiplications with modulos would
    * generate better hashing, let's use just shifts, and do 2 bytes at a time.
    * <p>
    * Of course, assuming UUIDs are randomized enough, even simpler approach might be good enough?
    * <p>
    * Is this a good hash? ... one of these days I better read more about basic hashing techniques I swear!
    */
   private static final int[] kShifts = {3, 7, 17, 21, 29, 4, 9};

   @Override
   public int hashCode() {
      if (mHashCode == 0) {
         // Let's handle first and last byte separately:
         int result = mId[0] & 0xFF;

         result |= result << 16;
         result |= result << 8;

         for (int i = 1; i < 15; i += 2) {
            int curr = (mId[i] & 0xFF) << 8 | mId[i + 1] & 0xFF;
            int shift = UUID.kShifts[i >> 1];

            if (shift > 16) {
               result ^= curr << shift | curr >>> 32 - shift;
            } else {
               result ^= curr << shift;
            }
         }

         // and then the last byte:
         int last = mId[15] & 0xFF;
         result ^= last << 3;
         result ^= last << 13;

         result ^= last << 27;
         // Let's not accept hash 0 as it indicates 'not hashed yet':
         if (result == 0) {
            mHashCode = -1;
         } else {
            mHashCode = result;
         }
      }
      return mHashCode;
   }

   @Override
   public String toString() {
      /*
       * Could be synchronized, but there isn't much harm in just taking our
       * chances (ie. in the worst case we'll form the string more than once...
       * but result is the same)
       */

      if (mDesc == null) {
         StringBuilder b = new StringBuilder(UUID_STRING_LENGTH);

         for (int i = 0; i < 16; ++i) {
            // Need to bypass hyphens:
            switch (i) {
               case 4:
               case 6:
               case 8:
               case 10:
                  b.append('-');
                  break;
               default:
                  // no-op
            }
            int hex = mId[i] & 0xFF;
            b.append(UUID.kHexChars.charAt(hex >> 4));
            b.append(UUID.kHexChars.charAt(hex & 0x0f));
         }
         if (!UUID.sDescCaching) {
            return b.toString();
         }
         mDesc = b.toString();
      }
      return mDesc;
   }

   /**
    * Creates a 128bit number from the String representation of {@link UUID}.
    *
    * @param uuid The UUID
    * @return byte array that can be used to recreate a UUID instance from the given String representation
    */
   public static byte[] stringToBytes(String uuid) {
      byte[] data = new byte[16];
      int dataIdx = 0;
      try {
         for (int i = 0; i < uuid.length(); ) {
            while (uuid.charAt(i) == '-') {
               i++;
            }
            char c1 = uuid.charAt(i);
            char c2 = uuid.charAt(i + 1);
            i += 2;
            int c1Bytes = Character.digit(c1, 16);
            int c2Bytes = Character.digit(c2, 16);
            data[dataIdx++] = (byte) ((c1Bytes << 4) + c2Bytes);
         }
      } catch (RuntimeException e) {
         throw new IllegalArgumentException(e);
      }
      return data;
   }

   /**
    * Checking equality of UUIDs is easy; just compare the 128-bit number.
    */
   @Override
   public boolean equals(final Object obj) {
      if (!(obj instanceof UUID)) {
         return false;
      }
      byte[] otherId = ((UUID) obj).mId;
      byte[] thisId = mId;
      for (int i = 0; i < 16; ++i) {
         if (otherId[i] != thisId[i]) {
            return false;
         }
      }
      return true;
   }

   /**
    * Validates whether the input is a 36-character string that follows the pattern
    * {@code X-X-X-X-X} where:
    * <ul>
    * <li>The first group consists of 8 hex characters.
    * <li>The second, third, and fourth groups consist of 4 hex characters each.
    * <li>The fifth group consists of 12 hex characters.
    * <li>A hyphen separates each group of hex characters.
    * <li>A "hex character" is one of the following: {@code abcdef0123456789}.
    * </ul>
    * e.g.: {@code c7ef2580-210f-11f0-bef5-3ce1a1d12939}
    *
    * @param str the {@code CharSequence} to be validated as a UUID; {@code CharSequence} is used here so that
    *            both {@code String} and {@code SimpleString} are supported
    * @return {@code true} if the string is a valid UUID format; {@code false} otherwise
    */
   public static boolean isUUID(CharSequence str) {
      if (str == null || str.length() != UUID_STRING_LENGTH) {
         return false;
      }

      if (str.charAt(8) != HYPHEN || str.charAt(13) != HYPHEN || str.charAt(18) != HYPHEN || str.charAt(23) != HYPHEN) {
         return false;
      }

      for (int i = 0; i < str.length(); i++) {
         if (i == 8 || i == 13 || i == 18 || i == 23) {
            continue;
         }
         if (!isHex(str.charAt(i))) {
            return false;
         }
      }

      return true;
   }

   private static boolean isHex(char c) {
      return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
   }

   /**
    * Removes a trailing UUID from the given input, if present. The method checks whether the last characters of the
    * input adhere to a valid UUID format and removes them if true.
    *
    * @param input the input {@code SimpleString} which may contain a trailing UUID to be stripped
    * @return a {@code SimpleString} with the trailing UUID removed if present; otherwise, returns the original input
    *         unchanged
    */
   public static SimpleString stripTrailingUUID(SimpleString input) {
      int length = input.length();
      if (length >= UUID_STRING_LENGTH && UUID.isUUID(input.subSeq(length - UUID_STRING_LENGTH, length))) {
         return input.subSeq(0, length - UUID_STRING_LENGTH);
      } else {
         return input;
      }
   }
}
