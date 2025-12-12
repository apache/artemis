/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.utils;

import java.util.Random;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;

public class RandomUtil {


   protected static final Random random = new Random();

   public static Random getRandom() {
      return random;
   }

   private static final String letters = "abcdefghijklmnopqrstuvwxyz";

   private static final String digits = "0123456789";

   private static final String randomBase = letters + letters.toUpperCase() + digits;

   private static final int randomBaseLength = randomBase.length();

   /**
    * Utility method to build a {@code String} filled with random alpha-numeric characters. The {@code String} will
    * contain characters from the following:
    * <ul>
    *    <li>abcdefghijklmnopqrstuvwxyz</li>
    *    <li>ABCDEFGHIJKLMNOPQRSTUVWXYZ</li>
    *    <li>0123456789</li>
    * </ul>
    *
    * @param length how long the returned {@code String} should be
    * @return a {@code String} of random alpha-numeric characters
    */
   public static String randomAlphaNumericString(int length) {
      StringBuilder result = new StringBuilder(length);
      for (int i = 0; i < length; i++) {
         result.append(randomChar());
      }
      return result.toString();
   }

   /**
    * {@return A randomly generated {@link java.util.UUID} converted to a {@code String}}
    */
   public static String randomUUIDString() {
      return java.util.UUID.randomUUID().toString();
   }

   /**
    * {@return A randomly generated {@link java.util.UUID} converted to a {@link SimpleString}}
    */
   public static SimpleString randomUUIDSimpleString() {
      return SimpleString.of(RandomUtil.randomUUIDString());
   }

   /**
    * Utility method to get a random alpha-numeric character. The {@code char} will be one of the following:
    * <ul>
    *    <li>abcdefghijklmnopqrstuvwxyz</li>
    *    <li>ABCDEFGHIJKLMNOPQRSTUVWXYZ</li>
    *    <li>0123456789</li>
    * </ul>
    *
    * @return A randomly generated alpha-numeric {@code char}
    */
   public static char randomChar() {
      return randomBase.charAt(random.nextInt(randomBaseLength));
   }

   public static long randomLong() {
      return RandomUtil.random.nextLong();
   }

   public static long randomPositiveLong() {
      return Math.abs(RandomUtil.randomLong());
   }

   public static int randomInt() {
      return RandomUtil.random.nextInt();
   }

   public static int randomPositiveInt() {
      return Math.abs(RandomUtil.randomInt());
   }

   public static Integer randomPositiveIntOrNull() {
      Integer random = RandomUtil.randomInt();
      return random % 5 == 0 ? null : Math.abs(random);
   }

   public static ActiveMQBuffer randomBuffer(final int size, final long... data) {
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(size + 8 * data.length);

      for (long d : data) {
         buffer.writeLong(d);
      }

      for (int i = 0; i < size; i++) {
         buffer.writeByte(randomByte());
      }

      return buffer;
   }

   public static int randomInterval(final int min, final int max) {
      if (min == max) return max;
      return min + random.nextInt(max - min);
   }

   public static int randomMax(final int max) {
      assert max > 0;

      int value = randomPositiveInt() % max;

      if (value == 0) {
         value = max;
      }

      return value;
   }

   public static int randomPort() {
      return RandomUtil.random.nextInt(65536);
   }

   public static short randomShort() {
      return (short) RandomUtil.random.nextInt(Short.MAX_VALUE);
   }

   public static byte randomByte() {
      return Integer.valueOf(RandomUtil.random.nextInt()).byteValue();
   }

   public static boolean randomBoolean() {
      return RandomUtil.random.nextBoolean();
   }

   public static byte[] randomBytes() {
      return RandomUtil.randomUUIDString().getBytes();
   }

   public static byte[] randomBytes(final int length) {
      byte[] bytes = new byte[length];
      for (int i = 0; i < bytes.length; i++) {
         bytes[i] = RandomUtil.randomByte();
      }
      return bytes;
   }

   public static double randomDouble() {
      return RandomUtil.random.nextDouble();
   }

   public static float randomFloat() {
      return RandomUtil.random.nextFloat();
   }

   /**
    * Generates an array of random alpha-numeric words. The words will be of a random length between 1 and 10
    * characters.
    *
    * @param wordCount the number of random words to generate
    * @return an array of randomly generated alpha-numeric words between 1 and 10 characters long
    */
   public static String[] randomWords(int wordCount) {
      String[] words = new String[wordCount];
      for (int i = 0; i < wordCount; i++) {
         words[i] = randomAlphaNumericString((randomPositiveInt() % 10) + 1);
      }
      return words;
   }
}
