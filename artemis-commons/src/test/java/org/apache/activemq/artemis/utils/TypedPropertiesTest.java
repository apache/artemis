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

import static org.apache.activemq.artemis.utils.collections.TypedProperties.searchProperty;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TypedPropertiesTest {

   private static void assertEqualsTypeProperties(final TypedProperties expected, final TypedProperties actual) {
      assertNotNull(expected);
      assertNotNull(actual);
      assertEquals(expected.getEncodeSize(), actual.getEncodeSize());
      assertEquals(expected.getPropertyNames(), actual.getPropertyNames());
      Iterator<SimpleString> iterator = actual.getPropertyNames().iterator();
      while (iterator.hasNext()) {
         SimpleString key = iterator.next();
         Object expectedValue = expected.getProperty(key);
         Object actualValue = actual.getProperty(key);
         if (expectedValue instanceof byte[] expectedBytes && actualValue instanceof byte[] actualBytes) {
            assertArrayEquals(expectedBytes, actualBytes);
         } else {
            assertEquals(expectedValue, actualValue);
         }
      }
   }



   private TypedProperties props;

   private SimpleString key;

   @Test
   public void testCopyContructor() throws Exception {
      props.putSimpleStringProperty(key, RandomUtil.randomUUIDSimpleString());

      TypedProperties copy = new TypedProperties(props);

      assertEquals(props.getEncodeSize(), copy.getEncodeSize());
      assertEquals(props.getPropertyNames(), copy.getPropertyNames());

      assertTrue(copy.containsProperty(key));
      assertEquals(props.getProperty(key), copy.getProperty(key));
   }

   @Test
   public void testRemove() throws Exception {
      props.putSimpleStringProperty(key, RandomUtil.randomUUIDSimpleString());

      assertTrue(props.containsProperty(key));
      assertNotNull(props.getProperty(key));

      props.removeProperty(key);

      assertFalse(props.containsProperty(key));
      assertNull(props.getProperty(key));
   }

   @Test
   public void testClear() throws Exception {
      props.putSimpleStringProperty(key, RandomUtil.randomUUIDSimpleString());

      assertTrue(props.containsProperty(key));
      assertNotNull(props.getProperty(key));

      assertTrue(props.getEncodeSize() > 0, "encodeSize <= " + 0);

      props.clear();

      assertEquals(1, props.getEncodeSize());

      assertFalse(props.containsProperty(key));
      assertNull(props.getProperty(key));
   }

   @Test
   public void testKey() throws Exception {
      props.putBooleanProperty(key, true);
      Boolean bool = (Boolean) props.getProperty(key);
      assertNotNull(bool);
      assertTrue(bool);

      props.putCharProperty(key, 'a');
      char c = (Character) props.getProperty(key);
      assertEquals('a', c);
   }

   @Test
   public void testGetPropertyOnEmptyProperties() throws Exception {
      assertFalse(props.containsProperty(key));
      assertNull(props.getProperty(key));
   }

   @Test
   public void testRemovePropertyOnEmptyProperties() throws Exception {
      assertFalse(props.containsProperty(key));
      assertNull(props.removeProperty(key));
   }

   @Test
   public void testNullProperty() throws Exception {
      props.putSimpleStringProperty(key, null);
      assertTrue(props.containsProperty(key));
      assertNull(props.getProperty(key));
   }

   @Test
   public void testBytesPropertyWithNull() throws Exception {
      props.putBytesProperty(key, null);

      assertTrue(props.containsProperty(key));
      byte[] bb = (byte[]) props.getProperty(key);
      assertNull(bb);
   }

   @Test
   public void testTypedProperties() throws Exception {
      SimpleString longKey = RandomUtil.randomUUIDSimpleString();
      long longValue = RandomUtil.randomLong();
      SimpleString simpleStringKey = RandomUtil.randomUUIDSimpleString();
      SimpleString simpleStringValue = RandomUtil.randomUUIDSimpleString();
      TypedProperties otherProps = new TypedProperties();
      otherProps.putLongProperty(longKey, longValue);
      otherProps.putSimpleStringProperty(simpleStringKey, simpleStringValue);

      props.putTypedProperties(otherProps);

      long ll = props.getLongProperty(longKey);
      assertEquals(longValue, ll);
      SimpleString ss = props.getSimpleStringProperty(simpleStringKey);
      assertEquals(simpleStringValue, ss);
   }

   @Test
   public void testEmptyTypedProperties() throws Exception {
      assertEquals(0, props.getPropertyNames().size());

      props.putTypedProperties(new TypedProperties());

      assertEquals(0, props.getPropertyNames().size());
   }

   @Test
   public void testNullTypedProperties() throws Exception {
      assertEquals(0, props.getPropertyNames().size());

      props.putTypedProperties(null);

      assertEquals(0, props.getPropertyNames().size());
   }

   @Test
   public void testEncodeDecode() throws Exception {
      props.putByteProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomByte());
      props.putBytesProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomBytes());
      props.putBytesProperty(RandomUtil.randomUUIDSimpleString(), null);
      props.putBooleanProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomBoolean());
      props.putShortProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomShort());
      props.putIntProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomInt());
      props.putLongProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomLong());
      props.putFloatProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomFloat());
      props.putDoubleProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomDouble());
      props.putCharProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomChar());
      props.putSimpleStringProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomUUIDSimpleString());
      props.putSimpleStringProperty(RandomUtil.randomUUIDSimpleString(), null);
      SimpleString keyToRemove = RandomUtil.randomUUIDSimpleString();
      props.putSimpleStringProperty(keyToRemove, RandomUtil.randomUUIDSimpleString());

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
      props.encode(buffer.byteBuf());

      assertEquals(props.getEncodeSize(), buffer.writerIndex());

      TypedProperties decodedProps = new TypedProperties();
      decodedProps.decode(buffer.byteBuf());

      TypedPropertiesTest.assertEqualsTypeProperties(props, decodedProps);

      buffer.clear();

      // After removing a property, you should still be able to encode the Property
      props.removeProperty(keyToRemove);
      props.encode(buffer.byteBuf());

      assertEquals(props.getEncodeSize(), buffer.writerIndex());
   }

   @Test
   public void testEncodeDecodeEmpty() throws Exception {
      TypedProperties emptyProps = new TypedProperties();

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
      emptyProps.encode(buffer.byteBuf());

      assertEquals(props.getEncodeSize(), buffer.writerIndex());

      TypedProperties decodedProps = new TypedProperties();
      decodedProps.decode(buffer.byteBuf());

      TypedPropertiesTest.assertEqualsTypeProperties(emptyProps, decodedProps);
   }

   private static final SimpleString PROP_NAME = SimpleString.of("TEST_PROP");
   private static final SimpleString AMQP_NAME = SimpleString.of("AMQP_NAME");

   @Test
   public void testCannotClearInternalPropertiesIfEmpty() {
      TypedProperties properties = new TypedProperties();
      assertFalse(properties.clearInternalProperties());
   }

   @Test
   public void testClearInternalPropertiesIfAny() {
      TypedProperties properties = new TypedProperties(PROP_NAME::equals);
      properties.putBooleanProperty(PROP_NAME, RandomUtil.randomBoolean());
      assertTrue(properties.clearInternalProperties());
      assertFalse(properties.containsProperty(PROP_NAME));
   }

   @Test
   public void testClearAMQPPropertiesIfAny() {
      TypedProperties properties = new TypedProperties(PROP_NAME::equals, AMQP_NAME::equals);
      properties.putBooleanProperty(PROP_NAME, RandomUtil.randomBoolean());
      properties.putBooleanProperty(AMQP_NAME, RandomUtil.randomBoolean());
      assertTrue(properties.clearInternalProperties());
      assertFalse(properties.containsProperty(PROP_NAME));
      assertTrue(properties.containsProperty(AMQP_NAME));
      assertTrue(properties.clearAMQPProperties());
      assertFalse(properties.clearInternalProperties());
      assertFalse(properties.containsProperty(AMQP_NAME));
   }

   @Test
   public void testCannotClearInternalPropertiesTwiceIfAny() {
      TypedProperties properties = new TypedProperties(PROP_NAME::equals);
      properties.putBooleanProperty(PROP_NAME, RandomUtil.randomBoolean());
      assertTrue(properties.clearInternalProperties());
      assertFalse(properties.clearInternalProperties());
   }

   @Test
   public void testSearchPropertyIfNone() {
      TypedProperties props = new TypedProperties();
      ByteBuf buf = Unpooled.buffer(Byte.BYTES, Byte.BYTES);
      props.encode(buf);
      buf.resetReaderIndex();
      assertFalse(searchProperty(SimpleString.of(""), buf, 0), "There is no property");
   }

   @Test
   public void testSearchAllProperties() {
      TypedProperties props = new TypedProperties();
      props.putByteProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomByte());
      props.putBytesProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomBytes());
      props.putBytesProperty(RandomUtil.randomUUIDSimpleString(), null);
      props.putBooleanProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomBoolean());
      props.putShortProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomShort());
      props.putIntProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomInt());
      props.putLongProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomLong());
      props.putFloatProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomFloat());
      props.putDoubleProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomDouble());
      props.putCharProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomChar());
      props.putSimpleStringProperty(RandomUtil.randomUUIDSimpleString(), RandomUtil.randomUUIDSimpleString());
      props.putSimpleStringProperty(RandomUtil.randomUUIDSimpleString(), null);
      final SimpleString value = RandomUtil.randomUUIDSimpleString();
      props.putSimpleStringProperty(RandomUtil.randomUUIDSimpleString(), value);
      ByteBuf buf = Unpooled.buffer();
      props.encode(buf);
      buf.resetReaderIndex();
      assertFalse(searchProperty(value, buf, 0));
      props.forEachKey(key -> {
         assertTrue(searchProperty(key, buf, 0));
         assertTrue(searchProperty(SimpleString.of(key.toString()), buf, 0));
         // concat a string just to check if the search won't perform an eager search to find the string pattern
         assertFalse(searchProperty(key.concat(" "), buf, 0));
      });
   }

   @Test
   public void testSearchPartiallyEncodedBuffer() {
      assertThrows(IndexOutOfBoundsException.class, () -> {
         final int expectedLength = Integer.BYTES + Byte.BYTES;
         ByteBuf buf = Unpooled.buffer(expectedLength, expectedLength);
         buf.writeByte(DataConstants.NOT_NULL);
         buf.writeInt(1);
         buf.resetReaderIndex();
         searchProperty(SimpleString.of(" "), buf, 0);
      });
   }

   @Test
   public void testSearchPartiallyEncodedString() {
      assertThrows(IndexOutOfBoundsException.class, () -> {
         final int expectedLength = Integer.BYTES + Byte.BYTES + Integer.BYTES;
         ByteBuf buf = Unpooled.buffer(expectedLength, expectedLength);
         buf.writeByte(DataConstants.NOT_NULL);
         buf.writeInt(1);
         //SimpleString::data length
         buf.writeInt(2);
         buf.resetReaderIndex();
         searchProperty(SimpleString.of("a"), buf, 0);
      });
   }

   @Test
   public void testSearchWithInvalidTypeBeforeEnd() {
      assertThrows(IllegalStateException.class, () -> {
         ByteBuf buf = Unpooled.buffer();
         buf.writeByte(DataConstants.NOT_NULL);
         // fake 2 properties
         buf.writeInt(2);
         // 1 key with length 2
         buf.writeInt(2);
         buf.writeShort(3);
         // invalid type
         buf.writeByte(Byte.MIN_VALUE);
         buf.resetReaderIndex();
         searchProperty(SimpleString.of(""), buf, 0);
      });
   }

   @Test
   public void testSearchWithInvalidTypeEnd() {
      ByteBuf buf = Unpooled.buffer();
      buf.writeByte(DataConstants.NOT_NULL);
      // fake 1 property
      buf.writeInt(1);
      // 1 key with length 2
      buf.writeInt(2);
      buf.writeShort(3);
      // invalid type
      buf.writeByte(Byte.MIN_VALUE);
      buf.resetReaderIndex();
      assertFalse(searchProperty(SimpleString.of(""), buf, 0));
   }

   @BeforeEach
   public void setUp() throws Exception {
      props = new TypedProperties();
      key = RandomUtil.randomUUIDSimpleString();
   }

   @Test
   public void testByteBufStringValuePool() {
      final int capacity = 8;
      final int chars = Integer.toString(capacity).length();
      final TypedProperties.StringValue.ByteBufStringValuePool pool = new TypedProperties.StringValue.ByteBufStringValuePool(capacity, chars);
      final int bytes = SimpleString.of(Integer.toString(capacity)).sizeof();
      final ByteBuf bb = Unpooled.buffer(bytes, bytes);
      for (int i = 0; i < capacity; i++) {
         final SimpleString s = SimpleString.of(Integer.toString(i));
         bb.resetWriterIndex();
         SimpleString.writeSimpleString(bb, s);
         bb.resetReaderIndex();
         final TypedProperties.StringValue expectedPooled = pool.getOrCreate(bb);
         bb.resetReaderIndex();
         assertSame(expectedPooled, pool.getOrCreate(bb));
         bb.resetReaderIndex();
      }
   }

   @Test
   public void testByteBufStringValuePoolTooLong() {
      final SimpleString tooLong = SimpleString.of("aa");
      final ByteBuf bb = Unpooled.buffer(tooLong.sizeof(), tooLong.sizeof());
      SimpleString.writeSimpleString(bb, tooLong);
      final TypedProperties.StringValue.ByteBufStringValuePool pool = new TypedProperties.StringValue.ByteBufStringValuePool(1, tooLong.length() - 1);
      assertNotSame(pool.getOrCreate(bb), pool.getOrCreate(bb.resetReaderIndex()));
   }

   @Test
   public void testCopyingWhileMessingUp() throws Exception {
      TypedProperties properties = new TypedProperties();
      AtomicBoolean running = new AtomicBoolean(true);
      AtomicLong copies = new AtomicLong(0);
      AtomicBoolean error = new AtomicBoolean(false);
      Thread t = new Thread(() -> {
         while (running.get() && !error.get()) {
            try {
               copies.incrementAndGet();
               TypedProperties copiedProperties = new TypedProperties(properties);
            } catch (Throwable e) {
               e.printStackTrace();
               error.set(true);
            }
         }
      });
      t.start();
      for (int i = 0; !error.get() && (i < 100 || copies.get() < 50); i++) {
         properties.putIntProperty(SimpleString.of("key" + i), i);
      }

      running.set(false);

      t.join();

      assertFalse(error.get());
   }
}
