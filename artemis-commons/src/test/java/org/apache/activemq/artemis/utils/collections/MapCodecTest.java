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
package org.apache.activemq.artemis.utils.collections;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapCodecTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static class TestMapCodec extends MapCodec {

      public final Map<Short, Object> objectsHashMap = new LinkedHashMap<>();

      TestMapCodec() {
      }

      int payloadSize;

      private void calculatePayloadSize() {
         payloadSize = 0;
         objectsHashMap.forEach((a, b) -> {
            if (b instanceof Boolean) {
               payloadSize += payloadSizeBoolean(1);
            } else if (b instanceof Integer) {
               payloadSize += payloadSizeInteger(1);
            } else if (b instanceof Long) {
               payloadSize += payloadSizeLong(1);
            } else if (b instanceof Byte) {
               payloadSize += payloadSizeByte(1);
            } else if (b instanceof SimpleString || b == null) {
               payloadSize += payloadSizeSimpleString((SimpleString) b);
            } else if (b instanceof byte[]) {
               payloadSize += payloadSizeByteArray(((byte[]) b).length);
            }
         });
      }

      public int getEncodeSize() {
         if (payloadSize == 0) {
            calculatePayloadSize();
         }

         return payloadSize + headerSize();
      }

      @Override
      protected void onMapReadInteger(short key, int value) {
         int originalValue = (int) objectsHashMap.remove(key);
         assertEquals(originalValue, value);
      }

      @Override
      protected void onMapReadByte(short key, byte value) {
         byte originalValue = (byte) objectsHashMap.remove(key);
         assertEquals(originalValue, value);
      }

      @Override
      protected void onMapReadBoolean(short key, boolean value) {
         boolean originalValue = (boolean) objectsHashMap.remove(key);
         assertEquals(originalValue, value);
      }

      @Override
      protected void onMapReadLong(short key, long value) {
         long originalValue = (long) objectsHashMap.remove(key);
         assertEquals(originalValue, value);
      }

      @Override
      protected void onMapReadSimpleString(short key, SimpleString value) {
         SimpleString originalValue = (SimpleString) objectsHashMap.remove(key);
         assertEquals(originalValue, value);
      }

      @Override
      protected void onMapReadByteArray(short key, ActiveMQBuffer slice) {
         byte[] originalArray = (byte[]) objectsHashMap.remove(key);

         int arraySize = slice.readableBytes();
         byte[] readByteArray = new byte[arraySize];
         slice.readBytes(readByteArray);

         assertArrayEquals(originalArray, readByteArray);

      }

      public void encode(ActiveMQBuffer buffer) {
         int originalPosition = buffer.readerIndex();
         int totalSize = writeHeader(buffer, payloadSize, objectsHashMap.size());
         int expectedEndPosition = originalPosition + totalSize;

         objectsHashMap.forEach((a, b) -> {
            logger.info("writing header {} = {}", a, b);
            if (b instanceof Integer) {
               writeInteger(buffer, a.shortValue(), ((Integer) b).intValue());
            } else if (b instanceof Boolean) {
               writeBoolean(buffer, a.shortValue(), ((Boolean) b).booleanValue());
            } else if (b instanceof Long) {
               writeLong(buffer, a.shortValue(), ((Long) b).longValue());
            } else if (b instanceof SimpleString || b == null) {
               writeSimpleString(buffer, a.shortValue(), (SimpleString) b);
            } else if (b instanceof Byte) {
               writeByte(buffer, a.shortValue(), (byte) b);
            } else if (b instanceof byte[]) {
               byte[] byteArray = (byte[]) b;
               writeByteArray(buffer, a.shortValue(), byteArray.length, buf -> buf.writeBytes(byteArray));
            }
         });

         assertEquals(expectedEndPosition, buffer.writerIndex());
      }
   }

   @Test
   public void testMapPersisterWithIntegers() {
      testMapPersister(1, 0, 0, 0, 0, 0);
      testMapPersister(101, 0, 0, 0, 0, 0);
   }

   @Test
   public void testMapPersisterWithByteArrays() {
      testMapPersister(0, 0, 0, 0, 0, 1);
      testMapPersister(0, 0, 0, 0, 0, 101);
   }

   @Test
   public void testMapPersisterWithBooleans() {
      testMapPersister(0, 1, 0, 0, 0, 0);
      testMapPersister(0, 101, 0, 0, 0, 0);
   }

   @Test
   public void testMapPersisterWithBytes() {
      testMapPersister(0, 0, 0, 0, 1, 0);
      testMapPersister(0, 0, 0, 0, 101, 0);
   }

   @Test
   public void testMapPersisterWithLong() {
      testMapPersister(0, 0, 1, 0, 0, 0);
      testMapPersister(0, 0, 101, 0, 0, 0);
   }

   @Test
   public void testMapPersisterWithStrings() {
      testMapPersister(0, 0, 0, 1, 0, 0);
      testMapPersister(0, 0, 0, 2, 0, 0);
      testMapPersister(0, 0, 0, 101, 0, 0);
   }

   @Test
   public void testMapPersisterWithMix() {
      testMapPersister(19, 3, 7, 15, 35, 10);
   }

   public void testMapPersister(int numberOfIntegers,
                                int numberOfBooleans,
                                int numberOfLongs,
                                int numberOfStrings,
                                int numberOfBytes,
                                int numberOfByteArrays) {
      ArrayList<SimpleString> strings = new ArrayList<>();
      for (int i = 0; i < numberOfStrings; i++) {
         if (i == 0) {
            strings.add(null);
         } else {
            strings.add(RandomUtil.randomUUIDSimpleString());
         }
      }

      TestMapCodec persister = new TestMapCodec();

      short keyID = 1;
      for (int i = 0; i < numberOfIntegers; i++) {
         persister.objectsHashMap.put(keyID++, RandomUtil.randomInt());
      }

      for (int i = 0; i < numberOfBytes; i++) {
         persister.objectsHashMap.put(keyID++, RandomUtil.randomBytes(1)[0]);
      }

      for (int i = 0; i < numberOfBooleans; i++) {
         persister.objectsHashMap.put(keyID++, RandomUtil.randomBoolean());
      }

      for (int i = 0; i < numberOfLongs; i++) {
         persister.objectsHashMap.put(keyID++, RandomUtil.randomLong());
      }

      for (SimpleString s : strings) {
         persister.objectsHashMap.put(keyID++, s);
      }

      for (int i = 0; i < numberOfByteArrays; i++) {
         persister.objectsHashMap.put(keyID++, RandomUtil.randomBytes());
      }

      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(persister.getEncodeSize());
      persister.encode(buffer);

      // notice decode here will remove entries while verifying they are the same.
      // in the end it should be empty to verify everything that was persisted will also be read
      persister.decode(buffer);

      if (!persister.objectsHashMap.isEmpty()) {
         persister.objectsHashMap.forEach((a, b) -> logger.info("{} = {}", a, b));
         Assertions.fail("There are still elements in the objectsHashMap");
      }

   }
}