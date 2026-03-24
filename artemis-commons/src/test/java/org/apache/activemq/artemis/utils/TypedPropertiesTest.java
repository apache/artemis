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

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import static org.apache.activemq.artemis.utils.collections.TypedProperties.searchProperty;
import static org.hamcrest.Matchers.greaterThan;

public class TypedPropertiesTest {

   private static void assertEqualsTypeProperties(final TypedProperties expected, final TypedProperties actual) {
      Assert.assertNotNull(expected);
      Assert.assertNotNull(actual);
      Assert.assertEquals(expected.getEncodeSize(), actual.getEncodeSize());
      Assert.assertEquals(expected.getPropertyNames(), actual.getPropertyNames());
      Iterator<SimpleString> iterator = actual.getPropertyNames().iterator();
      while (iterator.hasNext()) {
         SimpleString key = iterator.next();
         Object expectedValue = expected.getProperty(key);
         Object actualValue = actual.getProperty(key);
         if (expectedValue instanceof byte[] && actualValue instanceof byte[]) {
            byte[] expectedBytes = (byte[]) expectedValue;
            byte[] actualBytes = (byte[]) actualValue;
            Assert.assertArrayEquals(expectedBytes, actualBytes);
         } else {
            Assert.assertEquals(expectedValue, actualValue);
         }
      }
   }



   private TypedProperties props;

   private SimpleString key;

   @Test
   public void testCopyContructor() throws Exception {
      props.putSimpleStringProperty(key, RandomUtil.randomSimpleString());

      TypedProperties copy = new TypedProperties(props);

      Assert.assertEquals(props.getEncodeSize(), copy.getEncodeSize());
      Assert.assertEquals(props.getPropertyNames(), copy.getPropertyNames());

      Assert.assertTrue(copy.containsProperty(key));
      Assert.assertEquals(props.getProperty(key), copy.getProperty(key));
   }

   @Test
   public void testRemove() throws Exception {
      props.putSimpleStringProperty(key, RandomUtil.randomSimpleString());

      Assert.assertTrue(props.containsProperty(key));
      Assert.assertNotNull(props.getProperty(key));

      props.removeProperty(key);

      Assert.assertFalse(props.containsProperty(key));
      Assert.assertNull(props.getProperty(key));
   }

   @Test
   public void testClear() throws Exception {
      props.putSimpleStringProperty(key, RandomUtil.randomSimpleString());

      Assert.assertTrue(props.containsProperty(key));
      Assert.assertNotNull(props.getProperty(key));

      Assert.assertThat(props.getEncodeSize(), greaterThan(0));

      props.clear();

      Assert.assertEquals(1, props.getEncodeSize());

      Assert.assertFalse(props.containsProperty(key));
      Assert.assertNull(props.getProperty(key));
   }

   @Test
   public void testKey() throws Exception {
      props.putBooleanProperty(key, true);
      boolean bool = (Boolean) props.getProperty(key);
      Assert.assertEquals(true, bool);

      props.putCharProperty(key, 'a');
      char c = (Character) props.getProperty(key);
      Assert.assertEquals('a', c);
   }

   @Test
   public void testGetPropertyOnEmptyProperties() throws Exception {
      Assert.assertFalse(props.containsProperty(key));
      Assert.assertNull(props.getProperty(key));
   }

   @Test
   public void testRemovePropertyOnEmptyProperties() throws Exception {
      Assert.assertFalse(props.containsProperty(key));
      Assert.assertNull(props.removeProperty(key));
   }

   @Test
   public void testNullProperty() throws Exception {
      props.putSimpleStringProperty(key, null);
      Assert.assertTrue(props.containsProperty(key));
      Assert.assertNull(props.getProperty(key));
   }

   @Test
   public void testBytesPropertyWithNull() throws Exception {
      props.putBytesProperty(key, null);

      Assert.assertTrue(props.containsProperty(key));
      byte[] bb = (byte[]) props.getProperty(key);
      Assert.assertNull(bb);
   }

   @Test
   public void testTypedProperties() throws Exception {
      SimpleString longKey = RandomUtil.randomSimpleString();
      long longValue = RandomUtil.randomLong();
      SimpleString simpleStringKey = RandomUtil.randomSimpleString();
      SimpleString simpleStringValue = RandomUtil.randomSimpleString();
      TypedProperties otherProps = new TypedProperties();
      otherProps.putLongProperty(longKey, longValue);
      otherProps.putSimpleStringProperty(simpleStringKey, simpleStringValue);

      props.putTypedProperties(otherProps);

      long ll = props.getLongProperty(longKey);
      Assert.assertEquals(longValue, ll);
      SimpleString ss = props.getSimpleStringProperty(simpleStringKey);
      Assert.assertEquals(simpleStringValue, ss);
   }

   @Test
   public void testEmptyTypedProperties() throws Exception {
      Assert.assertEquals(0, props.getPropertyNames().size());

      props.putTypedProperties(new TypedProperties());

      Assert.assertEquals(0, props.getPropertyNames().size());
   }

   @Test
   public void testNullTypedProperties() throws Exception {
      Assert.assertEquals(0, props.getPropertyNames().size());

      props.putTypedProperties(null);

      Assert.assertEquals(0, props.getPropertyNames().size());
   }

   @Test
   public void testEncodeDecode() throws Exception {
      props.putByteProperty(RandomUtil.randomSimpleString(), RandomUtil.randomByte());
      props.putBytesProperty(RandomUtil.randomSimpleString(), RandomUtil.randomBytes());
      props.putBytesProperty(RandomUtil.randomSimpleString(), null);
      props.putBooleanProperty(RandomUtil.randomSimpleString(), RandomUtil.randomBoolean());
      props.putShortProperty(RandomUtil.randomSimpleString(), RandomUtil.randomShort());
      props.putIntProperty(RandomUtil.randomSimpleString(), RandomUtil.randomInt());
      props.putLongProperty(RandomUtil.randomSimpleString(), RandomUtil.randomLong());
      props.putFloatProperty(RandomUtil.randomSimpleString(), RandomUtil.randomFloat());
      props.putDoubleProperty(RandomUtil.randomSimpleString(), RandomUtil.randomDouble());
      props.putCharProperty(RandomUtil.randomSimpleString(), RandomUtil.randomChar());
      props.putSimpleStringProperty(RandomUtil.randomSimpleString(), RandomUtil.randomSimpleString());
      props.putSimpleStringProperty(RandomUtil.randomSimpleString(), null);
      SimpleString keyToRemove = RandomUtil.randomSimpleString();
      props.putSimpleStringProperty(keyToRemove, RandomUtil.randomSimpleString());

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
      props.encode(buffer.byteBuf());

      Assert.assertEquals(props.getEncodeSize(), buffer.writerIndex());

      TypedProperties decodedProps = new TypedProperties();
      decodedProps.decode(buffer.byteBuf());

      TypedPropertiesTest.assertEqualsTypeProperties(props, decodedProps);

      buffer.clear();

      // After removing a property, you should still be able to encode the Property
      props.removeProperty(keyToRemove);
      props.encode(buffer.byteBuf());

      Assert.assertEquals(props.getEncodeSize(), buffer.writerIndex());
   }

   @Test
   public void testEncodeDecodeEmpty() throws Exception {
      TypedProperties emptyProps = new TypedProperties();

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
      emptyProps.encode(buffer.byteBuf());

      Assert.assertEquals(props.getEncodeSize(), buffer.writerIndex());

      TypedProperties decodedProps = new TypedProperties();
      decodedProps.decode(buffer.byteBuf());

      TypedPropertiesTest.assertEqualsTypeProperties(emptyProps, decodedProps);
   }

   private static final SimpleString PROP_NAME = SimpleString.toSimpleString("TEST_PROP");

   @Test
   public void testCannotClearInternalPropertiesIfEmpty() {
      TypedProperties properties = new TypedProperties();
      Assert.assertFalse(properties.clearInternalProperties());
   }

   @Test
   public void testClearInternalPropertiesIfAny() {
      TypedProperties properties = new TypedProperties(PROP_NAME::equals);
      properties.putBooleanProperty(PROP_NAME, RandomUtil.randomBoolean());
      Assert.assertTrue(properties.clearInternalProperties());
      Assert.assertFalse(properties.containsProperty(PROP_NAME));
   }

   @Test
   public void testCannotClearInternalPropertiesTwiceIfAny() {
      TypedProperties properties = new TypedProperties(PROP_NAME::equals);
      properties.putBooleanProperty(PROP_NAME, RandomUtil.randomBoolean());
      Assert.assertTrue(properties.clearInternalProperties());
      Assert.assertFalse(properties.clearInternalProperties());
   }

   @Test
   public void testSearchPropertyIfNone() {
      TypedProperties props = new TypedProperties();
      ByteBuf buf = Unpooled.buffer(Byte.BYTES, Byte.BYTES);
      props.encode(buf);
      buf.resetReaderIndex();
      Assert.assertFalse("There is no property", searchProperty(SimpleString.toSimpleString(""), buf, 0));
   }

   @Test
   public void testSearchAllProperties() {
      TypedProperties props = new TypedProperties();
      props.putByteProperty(RandomUtil.randomSimpleString(), RandomUtil.randomByte());
      props.putBytesProperty(RandomUtil.randomSimpleString(), RandomUtil.randomBytes());
      props.putBytesProperty(RandomUtil.randomSimpleString(), null);
      props.putBooleanProperty(RandomUtil.randomSimpleString(), RandomUtil.randomBoolean());
      props.putShortProperty(RandomUtil.randomSimpleString(), RandomUtil.randomShort());
      props.putIntProperty(RandomUtil.randomSimpleString(), RandomUtil.randomInt());
      props.putLongProperty(RandomUtil.randomSimpleString(), RandomUtil.randomLong());
      props.putFloatProperty(RandomUtil.randomSimpleString(), RandomUtil.randomFloat());
      props.putDoubleProperty(RandomUtil.randomSimpleString(), RandomUtil.randomDouble());
      props.putCharProperty(RandomUtil.randomSimpleString(), RandomUtil.randomChar());
      props.putSimpleStringProperty(RandomUtil.randomSimpleString(), RandomUtil.randomSimpleString());
      props.putSimpleStringProperty(RandomUtil.randomSimpleString(), null);
      final SimpleString value = RandomUtil.randomSimpleString();
      props.putSimpleStringProperty(RandomUtil.randomSimpleString(), value);
      ByteBuf buf = Unpooled.buffer();
      props.encode(buf);
      buf.resetReaderIndex();
      Assert.assertFalse(searchProperty(value, buf, 0));
      props.forEachKey(key -> {
         Assert.assertTrue(searchProperty(key, buf, 0));
         Assert.assertTrue(searchProperty(SimpleString.toSimpleString(key.toString()), buf, 0));
         // concat a string just to check if the search won't perform an eager search to find the string pattern
         Assert.assertFalse(searchProperty(key.concat(" "), buf, 0));
      });
   }

   @Test(expected = IndexOutOfBoundsException.class)
   public void testSearchPartiallyEncodedBuffer() {
      final int expectedLength = Integer.BYTES + Byte.BYTES;
      ByteBuf buf = Unpooled.buffer(expectedLength, expectedLength);
      buf.writeByte(DataConstants.NOT_NULL);
      buf.writeInt(1);
      buf.resetReaderIndex();
      searchProperty(SimpleString.toSimpleString(" "), buf, 0);
   }

   @Test(expected = IndexOutOfBoundsException.class)
   public void testSearchPartiallyEncodedString() {
      final int expectedLength = Integer.BYTES + Byte.BYTES + Integer.BYTES;
      ByteBuf buf = Unpooled.buffer(expectedLength, expectedLength);
      buf.writeByte(DataConstants.NOT_NULL);
      buf.writeInt(1);
      //SimpleString::data length
      buf.writeInt(2);
      buf.resetReaderIndex();
      searchProperty(SimpleString.toSimpleString("a"), buf, 0);
   }

   @Test(expected = IllegalStateException.class)
   public void testSearchWithInvalidTypeBeforeEnd() {
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
      searchProperty(SimpleString.toSimpleString(""), buf, 0);
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
      Assert.assertFalse(searchProperty(SimpleString.toSimpleString(""), buf, 0));
   }

   @Before
   public void setUp() throws Exception {
      props = new TypedProperties();
      key = RandomUtil.randomSimpleString();
   }

   @Test
   public void testByteBufStringValuePool() {
      final int capacity = 8;
      final int chars = Integer.toString(capacity).length();
      final TypedProperties.StringValue.ByteBufStringValuePool pool = new TypedProperties.StringValue.ByteBufStringValuePool(capacity, chars);
      final int bytes = new SimpleString(Integer.toString(capacity)).sizeof();
      final ByteBuf bb = Unpooled.buffer(bytes, bytes);
      for (int i = 0; i < capacity; i++) {
         final SimpleString s = new SimpleString(Integer.toString(i));
         bb.resetWriterIndex();
         SimpleString.writeSimpleString(bb, s);
         bb.resetReaderIndex();
         final TypedProperties.StringValue expectedPooled = pool.getOrCreate(bb);
         bb.resetReaderIndex();
         Assert.assertSame(expectedPooled, pool.getOrCreate(bb));
         bb.resetReaderIndex();
      }
   }

   @Test
   public void testByteBufStringValuePoolTooLong() {
      final SimpleString tooLong = new SimpleString("aa");
      final ByteBuf bb = Unpooled.buffer(tooLong.sizeof(), tooLong.sizeof());
      SimpleString.writeSimpleString(bb, tooLong);
      final TypedProperties.StringValue.ByteBufStringValuePool pool = new TypedProperties.StringValue.ByteBufStringValuePool(1, tooLong.length() - 1);
      Assert.assertNotSame(pool.getOrCreate(bb), pool.getOrCreate(bb.resetReaderIndex()));
   }

   @Test
   public void testCopyingWhileMessingUp() throws Exception {
      TypedProperties properties = new TypedProperties();
      AtomicBoolean running = new AtomicBoolean(true);
      AtomicLong copies = new AtomicLong(0);
      AtomicBoolean error = new AtomicBoolean(false);
      Thread t = new Thread() {
         @Override
         public void run() {
            while (running.get() && !error.get()) {
               try {
                  copies.incrementAndGet();
                  TypedProperties copiedProperties = new TypedProperties(properties);
               } catch (Throwable e) {
                  e.printStackTrace();
                  error.set(true);
               }
            }
         }
      };
      t.start();
      for (int i = 0; !error.get() && (i < 100 || copies.get() < 50); i++) {
         properties.putIntProperty(SimpleString.toSimpleString("key" + i), i);
      }

      running.set(false);

      t.join();

      Assert.assertFalse(error.get());
   }
}
