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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TypedPropertiesConversionTest {


   private TypedProperties props;

   private SimpleString key;

   private final SimpleString unknownKey = SimpleString.of("this.key.is.never.used");

   @BeforeEach
   public void setUp() throws Exception {
      key = RandomUtil.randomUUIDSimpleString();
      props = new TypedProperties();
   }

   @Test
   public void testBooleanProperty() throws Exception {
      Boolean val = RandomUtil.randomBoolean();
      props.putBooleanProperty(key, val);

      assertEquals(val, props.getBooleanProperty(key));
      assertEquals(SimpleString.of(Boolean.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, SimpleString.of(Boolean.toString(val)));
      assertEquals(val, props.getBooleanProperty(key));

      try {
         props.putByteProperty(key, RandomUtil.randomByte());
         props.getBooleanProperty(key);
         fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      assertFalse(props.getBooleanProperty(unknownKey));
   }

   @Test
   public void testCharProperty() throws Exception {
      Character val = RandomUtil.randomChar();
      props.putCharProperty(key, val);

      assertEquals(val, props.getCharProperty(key));
      assertEquals(SimpleString.of(Character.toString(val)), props.getSimpleStringProperty(key));

      try {
         props.putByteProperty(key, RandomUtil.randomByte());
         props.getCharProperty(key);
         fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getCharProperty(unknownKey);
         fail();
      } catch (NullPointerException e) {
      }
   }

   @Test
   public void testByteProperty() throws Exception {
      Byte val = RandomUtil.randomByte();
      props.putByteProperty(key, val);

      assertEquals(val, props.getByteProperty(key));
      assertEquals(SimpleString.of(Byte.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, SimpleString.of(Byte.toString(val)));
      assertEquals(val, props.getByteProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getByteProperty(key);
         fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getByteProperty(unknownKey);
         fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testNoByteProperty() {
      assertEquals(0, props.size());
      assertNull(props.getByteProperty(key, () -> null));
      props.putByteProperty(key.concat('0'), RandomUtil.randomByte());
      assertEquals(1, props.size());
      assertNull(props.getByteProperty(key, () -> null));
      props.putNullValue(key);
      assertTrue(props.containsProperty(key));
      assertNull(props.getByteProperty(key, () -> null));
   }

   @Test
   public void testIntProperty() throws Exception {
      Integer val = RandomUtil.randomInt();
      props.putIntProperty(key, val);

      assertEquals(val, props.getIntProperty(key));
      assertEquals(SimpleString.of(Integer.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, SimpleString.of(Integer.toString(val)));
      assertEquals(val, props.getIntProperty(key));

      Byte byteVal = RandomUtil.randomByte();
      props.putByteProperty(key, byteVal);
      assertEquals(Integer.valueOf(byteVal), props.getIntProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getIntProperty(key);
         fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getIntProperty(unknownKey);
         fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testLongProperty() throws Exception {
      Long val = RandomUtil.randomLong();
      props.putLongProperty(key, val);

      assertEquals(val, props.getLongProperty(key));
      assertEquals(SimpleString.of(Long.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, SimpleString.of(Long.toString(val)));
      assertEquals(val, props.getLongProperty(key));

      Byte byteVal = RandomUtil.randomByte();
      props.putByteProperty(key, byteVal);
      assertEquals(Long.valueOf(byteVal), props.getLongProperty(key));

      Short shortVal = RandomUtil.randomShort();
      props.putShortProperty(key, shortVal);
      assertEquals(Long.valueOf(shortVal), props.getLongProperty(key));

      Integer intVal = RandomUtil.randomInt();
      props.putIntProperty(key, intVal);
      assertEquals(Long.valueOf(intVal), props.getLongProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getLongProperty(key);
         fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getLongProperty(unknownKey);
         fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testDoubleProperty() throws Exception {
      Double val = RandomUtil.randomDouble();
      props.putDoubleProperty(key, val);

      assertEquals(val, props.getDoubleProperty(key));
      assertEquals(SimpleString.of(Double.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, SimpleString.of(Double.toString(val)));
      assertEquals(val, props.getDoubleProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getDoubleProperty(key);
         fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getDoubleProperty(unknownKey);
         fail();
      } catch (Exception e) {
      }
   }

   @Test
   public void testFloatProperty() throws Exception {
      Float val = RandomUtil.randomFloat();
      props.putFloatProperty(key, val);

      assertEquals(val, props.getFloatProperty(key));
      assertEquals(Double.valueOf(val), props.getDoubleProperty(key));
      assertEquals(SimpleString.of(Float.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, SimpleString.of(Float.toString(val)));
      assertEquals(val, props.getFloatProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getFloatProperty(key);
         fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getFloatProperty(unknownKey);
         fail();
      } catch (Exception e) {
      }
   }

   @Test
   public void testShortProperty() throws Exception {
      Short val = RandomUtil.randomShort();
      props.putShortProperty(key, val);

      assertEquals(val, props.getShortProperty(key));
      assertEquals(Integer.valueOf(val), props.getIntProperty(key));
      assertEquals(SimpleString.of(Short.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, SimpleString.of(Short.toString(val)));
      assertEquals(val, props.getShortProperty(key));

      Byte byteVal = RandomUtil.randomByte();
      props.putByteProperty(key, byteVal);
      assertEquals(Short.valueOf(byteVal), props.getShortProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getShortProperty(key);
         fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getShortProperty(unknownKey);
         fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testSimpleStringProperty() throws Exception {
      SimpleString strVal = RandomUtil.randomUUIDSimpleString();
      props.putSimpleStringProperty(key, strVal);
      assertEquals(strVal, props.getSimpleStringProperty(key));
   }

   @Test
   public void testBytesProperty() throws Exception {
      byte[] val = RandomUtil.randomBytes();
      props.putBytesProperty(key, val);

      assertArrayEquals(val, props.getBytesProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getBytesProperty(key);
         fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      assertNull(props.getBytesProperty(unknownKey));
   }

}
