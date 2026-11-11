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
package org.apache.activemq.artemis.tests.unit.jms.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.MessageFormatException;

import org.apache.activemq.artemis.jms.client.ActiveMQMapMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ActiveMQMapMessageTest extends ActiveMQTestBase {


   private String itemName;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      itemName = RandomUtil.randomUUIDString();
   }



   @Test
   public void testClearBody() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setBoolean(itemName, true);

      assertTrue(message.itemExists(itemName));

      message.clearBody();

      assertFalse(message.itemExists(itemName));
   }

   @Test
   public void testGetType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      assertEquals(ActiveMQMapMessage.TYPE, message.getType());
   }

   @Test
   public void testCheckItemNameIsNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      try {
         message.setBoolean(null, true);
         fail("item name can not be null");
      } catch (IllegalArgumentException e) {
      }

   }

   @Test
   public void testCheckItemNameIsEmpty() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      try {
         message.setBoolean("", true);
         fail("item name can not be empty");
      } catch (IllegalArgumentException e) {
      }

   }

   @Test
   public void testGetBooleanFromBoolean() throws Exception {
      boolean value = true;

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setBoolean(itemName, value);

      assertEquals(value, message.getBoolean(itemName));
   }

   @Test
   public void testGetBooleanFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      assertFalse(message.getBoolean(itemName));
   }

   @Test
   public void testGetBooleanFromString() throws Exception {
      boolean value = true;

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setString(itemName, Boolean.toString(value));

      assertEquals(value, message.getBoolean(itemName));
   }

   @Test
   public void testGetBooleanFromInvalidType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setFloat(itemName, RandomUtil.randomFloat());

      try {
         message.getBoolean(itemName);
         fail("MessageFormatException");
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testGetByteFromByte() throws Exception {
      byte value = RandomUtil.randomByte();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setByte(itemName, value);

      assertEquals(value, message.getByte(itemName));
   }

   @Test
   public void testGetByteFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();

      try {
         message.getByte(itemName);
         fail("NumberFormatException");
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testGetByteFromString() throws Exception {
      byte value = RandomUtil.randomByte();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setString(itemName, Byte.toString(value));

      assertEquals(value, message.getByte(itemName));
   }

   @Test
   public void testGetByteFromInvalidType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setFloat(itemName, RandomUtil.randomFloat());

      try {
         message.getByte(itemName);
         fail("MessageFormatException");
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testGetShortFromByte() throws Exception {
      byte value = RandomUtil.randomByte();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setByte(itemName, value);

      assertEquals(value, message.getShort(itemName));
   }

   @Test
   public void testGetShortFromShort() throws Exception {
      short value = RandomUtil.randomShort();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setShort(itemName, value);

      assertEquals(value, message.getShort(itemName));
   }

   @Test
   public void testGetShortFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();

      try {
         message.getShort(itemName);
         fail("NumberFormatException");
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testGetShortFromString() throws Exception {
      short value = RandomUtil.randomShort();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setString(itemName, Short.toString(value));

      assertEquals(value, message.getShort(itemName));
   }

   @Test
   public void testGetShortFromInvalidType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setFloat(itemName, RandomUtil.randomFloat());

      try {
         message.getShort(itemName);
         fail("MessageFormatException");
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testGetIntFromByte() throws Exception {
      byte value = RandomUtil.randomByte();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setByte(itemName, value);

      assertEquals(value, message.getInt(itemName));
   }

   @Test
   public void testGetIntFromShort() throws Exception {
      short value = RandomUtil.randomShort();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setShort(itemName, value);

      assertEquals(value, message.getInt(itemName));
   }

   @Test
   public void testGetIntFromInt() throws Exception {
      int value = RandomUtil.randomInt();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setInt(itemName, value);

      assertEquals(value, message.getInt(itemName));
   }

   @Test
   public void testGetIntFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();

      try {
         message.getInt(itemName);
         fail("NumberFormatException");
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testGetIntFromString() throws Exception {
      int value = RandomUtil.randomInt();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setString(itemName, Integer.toString(value));

      assertEquals(value, message.getInt(itemName));
   }

   @Test
   public void testGetIntFromInvalidType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setFloat(itemName, RandomUtil.randomFloat());

      try {
         message.getInt(itemName);
         fail("MessageFormatException");
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testGetCharFromChar() throws Exception {
      char value = RandomUtil.randomChar();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setChar(itemName, value);

      assertEquals(value, message.getChar(itemName));
   }

   @Test
   public void testGetCharFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();

      try {
         message.getChar(itemName);
         fail("NullPointerException");
      } catch (NullPointerException e) {
      }
   }

   @Test
   public void testGetCharFromInvalidType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setFloat(itemName, RandomUtil.randomFloat());

      try {
         message.getChar(itemName);
         fail("MessageFormatException");
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testGetLongFromByte() throws Exception {
      byte value = RandomUtil.randomByte();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setByte(itemName, value);

      assertEquals(value, message.getLong(itemName));
   }

   @Test
   public void testGetLongFromShort() throws Exception {
      short value = RandomUtil.randomShort();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setShort(itemName, value);

      assertEquals(value, message.getLong(itemName));
   }

   @Test
   public void testGetLongFromInt() throws Exception {
      int value = RandomUtil.randomInt();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setInt(itemName, value);

      assertEquals(value, message.getLong(itemName));
   }

   @Test
   public void testGetLongFromLong() throws Exception {
      long value = RandomUtil.randomLong();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setLong(itemName, value);

      assertEquals(value, message.getLong(itemName));
   }

   @Test
   public void testGetLongFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();

      try {
         message.getLong(itemName);
         fail("NumberFormatException");
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testGetLongFromString() throws Exception {
      long value = RandomUtil.randomLong();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setString(itemName, Long.toString(value));

      assertEquals(value, message.getLong(itemName));
   }

   @Test
   public void testGetLongFromInvalidType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setFloat(itemName, RandomUtil.randomFloat());

      try {
         message.getLong(itemName);
         fail("MessageFormatException");
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testGetFloatFromFloat() throws Exception {
      float value = RandomUtil.randomFloat();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setFloat(itemName, value);

      assertEquals(value, message.getFloat(itemName), 0.000001);
   }

   @Test
   public void testGetFloatFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();

      try {
         message.getFloat(itemName);
         fail("NullPointerException");
      } catch (NullPointerException e) {
      }
   }

   @Test
   public void testGetFloatFromString() throws Exception {
      float value = RandomUtil.randomFloat();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setString(itemName, Float.toString(value));

      assertEquals(value, message.getFloat(itemName), 0.000001);
   }

   @Test
   public void testGetFloatFromInvalidType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setChar(itemName, RandomUtil.randomChar());

      try {
         message.getFloat(itemName);
         fail("MessageFormatException");
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testGetDoubleFromFloat() throws Exception {
      float value = RandomUtil.randomFloat();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setFloat(itemName, value);

      assertEquals(Float.valueOf(value).doubleValue(), message.getDouble(itemName), 0.000001);
   }

   @Test
   public void testGetDoubleFromDouble() throws Exception {
      double value = RandomUtil.randomDouble();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setDouble(itemName, value);

      assertEquals(value, message.getDouble(itemName), 0.000001);
   }

   @Test
   public void testGetDoubleFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();

      try {
         message.getDouble(itemName);
         fail("NullPointerException");
      } catch (NullPointerException e) {
      }
   }

   @Test
   public void testGetDoubleFromString() throws Exception {
      double value = RandomUtil.randomDouble();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setString(itemName, Double.toString(value));

      assertEquals(value, message.getDouble(itemName), 0.000001);
   }

   @Test
   public void testGetDoubleFromInvalidType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setChar(itemName, RandomUtil.randomChar());

      try {
         message.getDouble(itemName);
         fail("MessageFormatException");
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testGetStringFromBoolean() throws Exception {
      boolean value = RandomUtil.randomBoolean();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setBoolean(itemName, value);

      assertEquals(Boolean.toString(value), message.getString(itemName));
   }

   @Test
   public void testGetStringFromByte() throws Exception {
      byte value = RandomUtil.randomByte();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setByte(itemName, value);

      assertEquals(Byte.toString(value), message.getString(itemName));
   }

   @Test
   public void testGetStringFromChar() throws Exception {
      char value = RandomUtil.randomChar();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setChar(itemName, value);

      assertEquals(Character.toString(value), message.getString(itemName));
   }

   @Test
   public void testGetStringFromShort() throws Exception {
      short value = RandomUtil.randomShort();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setShort(itemName, value);

      assertEquals(Short.toString(value), message.getString(itemName));
   }

   @Test
   public void testGetStringFromInt() throws Exception {
      int value = RandomUtil.randomInt();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setInt(itemName, value);

      assertEquals(Integer.toString(value), message.getString(itemName));
   }

   @Test
   public void testGetStringFromLong() throws Exception {
      long value = RandomUtil.randomLong();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setLong(itemName, value);

      assertEquals(Long.toString(value), message.getString(itemName));
   }

   @Test
   public void testGetStringFromFloat() throws Exception {
      float value = RandomUtil.randomFloat();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setFloat(itemName, value);

      assertEquals(Float.toString(value), message.getString(itemName));
   }

   @Test
   public void testGetStringFromDouble() throws Exception {
      double value = RandomUtil.randomByte();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setDouble(itemName, value);

      assertEquals(Double.toString(value), message.getString(itemName));
   }

   @Test
   public void testGetStringFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();

      assertNull(message.getString(itemName));
   }

   @Test
   public void testGetStringFromString() throws Exception {
      String value = RandomUtil.randomUUIDString();

      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setString(itemName, value);

      assertEquals(value, message.getString(itemName));
   }

   @Test
   public void testGetBytesFromBytes() throws Exception {
      byte[] value = RandomUtil.randomBytes();
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setBytes(itemName, value);

      ActiveMQTestBase.assertEqualsByteArrays(value, message.getBytes(itemName));
   }

   @Test
   public void testGetBytesFromNull() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();

      assertNull(message.getBytes(itemName));
   }

   @Test
   public void testGetBytesFromInvalidType() throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setChar(itemName, RandomUtil.randomChar());

      try {
         message.getBytes(itemName);
         fail("MessageFormatException");
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testSetObjectFromBoolean() throws Exception {
      boolean value = RandomUtil.randomBoolean();
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setObject(itemName, value);

      assertEquals(value, message.getObject(itemName));
   }

   @Test
   public void testSetObjectFromByte() throws Exception {
      doTestSetObject(RandomUtil.randomByte());
   }

   @Test
   public void testSetObjectFromShort() throws Exception {
      doTestSetObject(RandomUtil.randomShort());
   }

   @Test
   public void testSetObjectFromChar() throws Exception {
      doTestSetObject(RandomUtil.randomChar());
   }

   @Test
   public void testSetObjectFromInt() throws Exception {
      doTestSetObject(RandomUtil.randomInt());
   }

   @Test
   public void testSetObjectFromLong() throws Exception {
      doTestSetObject(RandomUtil.randomLong());
   }

   @Test
   public void testSetObjectFromFloat() throws Exception {
      doTestSetObject(RandomUtil.randomFloat());
   }

   @Test
   public void testSetObjectFromDouble() throws Exception {
      doTestSetObject(RandomUtil.randomDouble());
   }

   @Test
   public void testSetObjectFromString() throws Exception {
      doTestSetObject(RandomUtil.randomUUIDString());
   }

   @Test
   public void testSetObjectFromBytes() throws Exception {
      doTestSetObject(RandomUtil.randomBytes());
   }

   private void doTestSetObject(final Object value) throws Exception {
      ActiveMQMapMessage message = new ActiveMQMapMessage();
      message.setObject(itemName, value);

      assertEquals(value, message.getObject(itemName));
   }
}
