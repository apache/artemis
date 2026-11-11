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

import java.util.Objects;

import org.apache.qpid.proton.codec.ReadableBuffer;

/**
 * Abstraction of {@code byte[] }<a
 * href="https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm">Knuth-Morris-Pratt</a>'s needle to
 * be used to perform pattern matching over {@link ReadableBuffer}.
 */
final class KMPNeedle {

   private final int[] jumpTable;
   private final byte[] needle;

   private KMPNeedle(byte[] needle) {
      Objects.requireNonNull(needle);
      this.needle = needle;
      this.jumpTable = createJumpTable(needle);
   }

   private static int[] createJumpTable(byte[] needle) {
      final int[] jumpTable = new int[needle.length + 1];
      int j = 0;
      for (int i = 1; i < needle.length; i++) {
         while (j > 0 && needle[j] != needle[i]) {
            j = jumpTable[j];
         }
         if (needle[j] == needle[i]) {
            j++;
         }
         jumpTable[i + 1] = j;
      }
      for (int i = 1; i < jumpTable.length; i++) {
         if (jumpTable[i] != 0) {
            return jumpTable;
         }
      }
      // optimization over the original algorithm: it would save from accessing any jump table
      return null;
   }

   /**
    * https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm search algorithm:
    * <p>
    * This version differ from the original algorithm, because allows to fail fast (and faster) if the remaining
    * haystack to be processed is < of the remaining needle to be matched.
    */
   public int searchInto(ReadableBuffer haystack, int start, int end) {
      if (end < 0 || start < 0 || end < start) {
         return -1;
      }
      final int length = end - start;
      int j = 0;
      final int needleLength = needle.length;
      int remainingNeedle = needleLength;
      for (int i = 0; i < length; i++) {
         final int remainingHayStack = length - i;
         if (remainingNeedle > remainingHayStack) {
            return -1;
         }
         final int index = start + i;
         final byte value = haystack.get(index);
         while (j > 0 && needle[j] != value) {
            j = jumpTable == null ? 0 : jumpTable[j];
            remainingNeedle = needleLength - j;
         }
         if (needle[j] == value) {
            j++;
            remainingNeedle--;
            assert remainingNeedle >= 0;
         }
         if (j == needleLength) {
            final int startMatch = index - needleLength + 1;
            return startMatch;
         }
      }
      return -1;
   }

   public static KMPNeedle of(byte[] needle) {
      return new KMPNeedle(needle);
   }
}

