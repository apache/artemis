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
package org.objectweb.jtests.jms.conform.message.properties;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageFormatException;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;

/**
 * Test the conversion of primitive types for the {@code javax.jms.Message} properties.
 * <p>
 * See JMS Specification, sec. 3.5.4 Property Value Conversion and the corresponding table (p.33-34).
 * <p>
 * The method name {@code testXXX2YYY} means that we test if a property which has been set as a {@code XXX} type can be
 * read as a {@code YYY} type, where {@code XXX} and {@code YYY} can be {@code boolean, byte, short, long, float
 * double} or {@code String}.
 *
 * <pre>
 *          ---------------------------------------------------------------|
 *          | boolean | byte | short | int | long | float | double | String|
 * |-----------------------------------------------------------------------|
 * |boolean |    X                                                     X   |
 * |byte    |            X       X      X     X                        X   |
 * |short   |                    X      X     X                        X   |
 * |int     |                           X     X                        X   |
 * |long    |                                 X                        X   |
 * |float   |                                         X       X        X   |
 * |double  |                                                 X        X   |
 * |String  |    Y       Y       Y      Y     Y       Y       Y        X   |
 * |-----------------------------------------------------------------------|
 * </pre>
 * A value set as the row type can be read as the column type.
 * <p>
 * The unmarked cases must throw a {@code javax.jms.MessageFormatException}
 * <p>
 * The cases marked with a Y should throw a {@code java.lang.MessageFormatException} <strong>if</strong> the String is
 * not a correct representation of the column type (otherwise, it returns the property).
 */
public class MessagePropertyConversionTest extends PTPTestCase {

   /**
    * if a property is set as a {@code java.lang.String}, it can also be read as a {@code java.lang.String}.
    */
   @Test
   public void testString2String() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("pi", "3.14159");
         Assert.assertEquals("3.14159", message.getStringProperty("pi"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, to get it as a {@code double} throws a
    * {@code java.lang.NuberFormatException} if the {@code String} is not a correct representation for a {@code double}
    * (e.g. {@code "not a number"}).
    */
   @Test
   public void testString2Double_2() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("pi", "not_a_number");
         message.getDoubleProperty("pi");
         Assert.fail("sec. 3.5.4 The String to numeric conversions must throw the java.lang.NumberFormatException " + " if the numeric's valueOf() method does not accept the String value as a valid representation.\n");
      } catch (java.lang.NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, it can also be read as a {@code double} as long as the
    * {@code String} is a correct representation of a {@code double} (e.g. {@code "3.14159"}).
    */
   @Test
   public void testString2Double_1() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("pi", "3.14159");
         Assert.assertEquals(3.14159, message.getDoubleProperty("pi"), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, to get it as a {@code float} throws a
    * {@code java.lang.NuberFormatException} if the {@code String} is not a correct representation for a {@code float}
    * (e.g. {@code "not_a_number"}).
    */
   @Test
   public void testString2Float_2() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("pi", "not_a_number");
         message.getFloatProperty("pi");
         Assert.fail("sec. 3.5.4 The String to numeric conversions must throw the java.lang.NumberFormatException " + " if the numeric's valueOf() method does not accept the String value as a valid representation.\n");
      } catch (java.lang.NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, it can also be read as a {@code float} as long as the
    * {@code String} is a correct representation of a {@code float} (e.g. {@code "3.14159"}).
    */
   @Test
   public void testString2Float_1() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("pi", "3.14159");
         Assert.assertEquals(3.14159F, message.getFloatProperty("pi"), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, to get it as a {@code long} throws a
    * {@code java.lang.NuberFormatException} if the {@code String} is not a correct representation for a {@code long}
    * (e.g. {@code "3.14159"}).
    */
   @Test
   public void testString2Long_2() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("pi", "3.14159");
         message.getLongProperty("pi");
         Assert.fail("sec. 3.5.4 The String to numeric conversions must throw the java.lang.NumberFormatException " + " if the numeric's valueOf() method does not accept the String value as a valid representation.\n");
      } catch (java.lang.NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, it can also be read as a {@code long} as long as the
    * {@code String} is a correct representation of a {@code long} (e.g. {@code "0"}).
    */
   @Test
   public void testString2Long_1() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("prop", "0");
         Assert.assertEquals(0L, message.getLongProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, to get it as a {@code int} throws a
    * {@code java.lang.NuberFormatException} if the {@code String} is not a correct representation for a {@code int}
    * (e.g. {@code "3.14159"}).
    */
   @Test
   public void testString2Int_2() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("pi", "3.14159");
         message.getIntProperty("pi");
         Assert.fail("sec. 3.5.4 The String to numeric conversions must throw the java.lang.NumberFormatException " + " if the numeric's valueOf() method does not accept the String value as a valid representation.\n");
      } catch (java.lang.NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, it can also be read as a {@code int} as long as the
    * {@code String} is a correct representation of a {@code int} (e.g. {@code "0"}).
    */
   @Test
   public void testString2Int_1() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("prop", "0");
         Assert.assertEquals(0, message.getIntProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, to get it as a {@code short} throws a
    * {@code java.lang.NuberFormatException} if the {@code String} is not a correct representation for a {@code short}
    * (e.g. {@code "3.14159"}).
    */
   @Test
   public void testString2Short_2() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("pi", "3.14159");
         message.getShortProperty("pi");
         Assert.fail("sec. 3.5.4 The String to numeric conversions must throw the java.lang.NumberFormatException " + " if the numeric's valueOf() method does not accept the String value as a valid representation.\n");
      } catch (java.lang.NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, it can also be read as a {@code short} as long as the
    * {@code String} is a correct representation of a {@code short} (e.g. {@code "0"}).
    */
   @Test
   public void testString2Short_1() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("prop", "0");
         Assert.assertEquals((short) 0, message.getShortProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, to get it as a {@code byte} throws a
    * {@code java.lang.NuberFormatException} if the {@code String} is not a correct representation for a {@code byte}
    * (e.g. {@code "3.14159"}).
    */
   @Test
   public void testString2Byte_2() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("pi", "3.14159");
         message.getByteProperty("pi");
         Assert.fail("sec. 3.5.4 The String to numeric conversions must throw the java.lang.NumberFormatException " + " if the numeric's valueOf() method does not accept the String value as a valid representation.\n");
      } catch (java.lang.NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, it can also be read as a {@code byte} if the {@code String} is
    * a correct representation of a {@code byte} (e.g. {@code "0"}).
    */
   @Test
   public void testString2Byte_1() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("prop", "0");
         Assert.assertEquals((byte) 0, message.getByteProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, to get it as a {@code boolean} returns {@code true} if the
    * property is not null and is equal, ignoring case, to the string "true" (.eg. "True" is ok), else it returns
    * {@code false} (e.g. "test")
    */
   @Test
   public void testString2Boolean_2() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("prop", "test");
         Assert.assertFalse(message.getBooleanProperty("prop"));
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code java.lang.String}, it can also be read as a {@code boolean} if the {@code String}
    * is a correct representation of a {@code boolean} (e.g. {@code "true"}).
    */
   @Test
   public void testString2Boolean_1() {
      try {
         Message message = senderSession.createMessage();
         message.setStringProperty("prop", "true");
         Assert.assertTrue(message.getBooleanProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code double}, it can also be read as a {@code java.lang.String}.
    */
   @Test
   public void testDouble2String() {
      try {
         Message message = senderSession.createMessage();
         message.setDoubleProperty("prop", 127.0);
         Assert.assertEquals("127.0", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code double}, it can also be read as a {@code double}.
    */
   @Test
   public void testDouble2Double() {
      try {
         Message message = senderSession.createMessage();
         message.setDoubleProperty("prop", 127.0);
         Assert.assertEquals(127.0, message.getDoubleProperty("prop"), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code double}, to get is as a {@code float} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testDouble2Float() {
      try {
         Message message = senderSession.createMessage();
         message.setDoubleProperty("prop", 127.0);
         message.getFloatProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code double}, to get is as a {@code long} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testDouble2Long() {
      try {
         Message message = senderSession.createMessage();
         message.setDoubleProperty("prop", 127.0);
         message.getLongProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code double}, to get is as an {@code int} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testDouble2Int() {
      try {
         Message message = senderSession.createMessage();
         message.setDoubleProperty("prop", 127.0);
         message.getIntProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code double}, to get is as a {@code short} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testDouble2Short() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to short
         message.setDoubleProperty("prop", 127.0);
         message.getShortProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code double}, to get is as a {@code byte} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testDouble2Byte() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to byte
         message.setDoubleProperty("prop", 127.0);
         message.getByteProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code double}, to get is as a {@code boolean} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testDouble2Boolean() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can be converted to boolean
         message.setDoubleProperty("prop", 127.0);
         message.getBooleanProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code float}, it can also be read as a {@code String}.
    */
   @Test
   public void testFloat2String() {
      try {
         Message message = senderSession.createMessage();
         message.setFloatProperty("prop", 127.0F);
         Assert.assertEquals("127.0", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code float}, it can also be read as a {@code double}.
    */
   @Test
   public void testFloat2Double() {
      try {
         Message message = senderSession.createMessage();
         message.setFloatProperty("prop", 127.0F);
         Assert.assertEquals(127.0, message.getDoubleProperty("prop"), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code float}, it can also be read as a {@code float}.
    */
   @Test
   public void testFloat2Float() {
      try {
         Message message = senderSession.createMessage();
         message.setFloatProperty("prop", 127.0F);
         Assert.assertEquals(127.0F, message.getFloatProperty("prop"), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code float}, to get is as a {@code long} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testFloat2Long() {
      try {
         Message message = senderSession.createMessage();
         message.setFloatProperty("prop", 127.0F);
         message.getLongProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code float}, to get is as a {@code int} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testFloat2Int() {
      try {
         Message message = senderSession.createMessage();
         message.setFloatProperty("prop", 127.0F);
         message.getIntProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code float}, to get is as a {@code short} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testFloat2Short() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to short
         message.setFloatProperty("prop", 127.0F);
         message.getShortProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code float}, to get is as a {@code byte} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testFloat2Byte() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to byte
         message.setFloatProperty("prop", 127.0F);
         message.getByteProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code float}, to get is as a {@code boolean} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testFloat2Boolean() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can be converted to boolean
         message.setFloatProperty("prop", 127.0F);
         message.getBooleanProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code long}, it can also be read as a {@code String}.
    */
   @Test
   public void testLong2String() {
      try {
         Message message = senderSession.createMessage();
         message.setLongProperty("prop", 127L);
         Assert.assertEquals("127", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code long}, to get is as a {@code double} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testLong2Double() {
      try {
         Message message = senderSession.createMessage();
         message.setLongProperty("prop", 127L);
         message.getDoubleProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code long}, to get is as a {@code float} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testLong2Float() {
      try {
         Message message = senderSession.createMessage();
         message.setLongProperty("prop", 127L);
         message.getFloatProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code long}, it can also be read as a {@code long}.
    */
   @Test
   public void testLong2Long() {
      try {
         Message message = senderSession.createMessage();
         message.setLongProperty("prop", 127L);
         Assert.assertEquals(127L, message.getLongProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code long}, to get is as an {@code int} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testLong2Int() {
      try {
         Message message = senderSession.createMessage();
         message.setLongProperty("prop", 127L);
         message.getIntProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code long}, to get is as a {@code short} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testLong2Short() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to short
         message.setLongProperty("prop", 127L);
         message.getShortProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code long}, to get is as a {@code byte} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testLong2Byte() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to byte
         message.setLongProperty("prop", 127L);
         message.getByteProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code long}, to get is as a {@code boolean} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testLong2Boolean() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can be converted to boolean
         message.setLongProperty("prop", 127L);
         message.getBooleanProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as an {@code int}, it can also be read as a {@code String}.
    */
   @Test
   public void testInt2String() {
      try {
         Message message = senderSession.createMessage();
         message.setIntProperty("prop", 127);
         Assert.assertEquals("127", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code int}, to get is as a {@code double} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testInt2Double() {
      try {
         Message message = senderSession.createMessage();
         message.setIntProperty("prop", 127);
         message.getDoubleProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code int}, to get is as a {@code float} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testInt2Float() {
      try {
         Message message = senderSession.createMessage();
         message.setIntProperty("prop", 127);
         message.getFloatProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as an {@code int}, it can also be read as a {@code long}.
    */
   @Test
   public void testInt2Long() {
      try {
         Message message = senderSession.createMessage();
         message.setIntProperty("prop", 127);
         Assert.assertEquals(127L, message.getLongProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as an {@code int}, it can also be read as an {@code int}.
    */
   @Test
   public void testInt2Int() {
      try {
         Message message = senderSession.createMessage();
         message.setIntProperty("prop", 127);
         Assert.assertEquals(127, message.getIntProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code int}, to get is as a {@code short} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testInt2Short() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to short
         message.setIntProperty("prop", Integer.MAX_VALUE);
         message.getShortProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code int}, to get is as a {@code byte} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testInt2Byte() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to byte
         message.setIntProperty("prop", Integer.MAX_VALUE);
         message.getByteProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code int}, to get is as a {@code boolean} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testInt2Boolean() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can be converted to boolean
         message.setIntProperty("prop", Integer.MAX_VALUE);
         message.getBooleanProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code short}, it can also be read as a {@code String}.
    */
   @Test
   public void testShort2String() {
      try {
         Message message = senderSession.createMessage();
         message.setShortProperty("prop", (short) 127);
         Assert.assertEquals("127", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code short}, to get is as a {@code double} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testShort2Double() {
      try {
         Message message = senderSession.createMessage();
         message.setShortProperty("prop", (short) 127);
         message.getDoubleProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code short}, to get is as a {@code float} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testShort2Float() {
      try {
         Message message = senderSession.createMessage();
         message.setShortProperty("prop", (short) 127);
         message.getFloatProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code short}, it can also be read as a {@code long}.
    */
   @Test
   public void testShort2Long() {
      try {
         Message message = senderSession.createMessage();
         message.setShortProperty("prop", (short) 127);
         Assert.assertEquals(127L, message.getLongProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code short}, it can also be read as an {@code int}.
    */
   @Test
   public void testShort2Int() {
      try {
         Message message = senderSession.createMessage();
         message.setShortProperty("prop", (short) 127);
         Assert.assertEquals(127, message.getIntProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code short}, it can also be read as a {@code short}.
    */
   @Test
   public void testShort2Short() {
      try {
         Message message = senderSession.createMessage();
         message.setShortProperty("prop", (short) 127);
         Assert.assertEquals((short) 127, message.getShortProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code short}, to get is as a {@code byte} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testShort2Byte() {
      try {
         Message message = senderSession.createMessage();
         message.setShortProperty("prop", (short) 127);
         message.getByteProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code short}, to get is as a {@code boolean} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testShort2Boolean() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to boolean
         message.setShortProperty("prop", (short) 127);
         message.getBooleanProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code byte}, it can also be read as a {@code String}.
    */
   @Test
   public void testByte2String() {
      try {
         Message message = senderSession.createMessage();
         message.setByteProperty("prop", (byte) 127);
         Assert.assertEquals("127", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code byte}, to get is as a {@code double} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testByte2Double() {
      try {
         Message message = senderSession.createMessage();
         message.setByteProperty("prop", (byte) 127);
         message.getDoubleProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code byte}, to get is as a {@code float} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testByte2Float() {
      try {
         Message message = senderSession.createMessage();
         message.setByteProperty("prop", (byte) 127);
         message.getFloatProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code byte}, it can also be read as a {@code long}.
    */
   @Test
   public void testByte2Long() {
      try {
         Message message = senderSession.createMessage();
         message.setByteProperty("prop", (byte) 127);
         Assert.assertEquals(127L, message.getLongProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code byte}, it can also be read as an {@code int}.
    */
   @Test
   public void testByte2Int() {
      try {
         Message message = senderSession.createMessage();
         message.setByteProperty("prop", (byte) 127);
         Assert.assertEquals(127, message.getIntProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code byte}, it can also be read as a {@code short}.
    */
   @Test
   public void testByte2Short() {
      try {
         Message message = senderSession.createMessage();
         message.setByteProperty("prop", (byte) 127);
         Assert.assertEquals((short) 127, message.getShortProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code byte}, it can also be read as a {@code byte}.
    */
   @Test
   public void testByte2Byte() {
      try {
         Message message = senderSession.createMessage();
         message.setByteProperty("prop", (byte) 127);
         Assert.assertEquals((byte) 127, message.getByteProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code byte}, to get is as a {@code boolean} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testByte2Boolean() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to boolean
         message.setByteProperty("prop", (byte) 127);
         message.getBooleanProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code boolean}, it can also be read as a {@code String}.
    */
   @Test
   public void testBoolean2String() {
      try {
         Message message = senderSession.createMessage();
         message.setBooleanProperty("prop", true);
         Assert.assertEquals("true", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code boolean}, to get is as a {@code double} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testBoolean2Double() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to double
         message.setBooleanProperty("prop", true);
         message.getDoubleProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code boolean}, to get is as a {@code float} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testBoolean2Float() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to float
         message.setBooleanProperty("prop", true);
         message.getFloatProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code boolean}, to get is as a {@code long} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testBoolean2Long() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to long
         message.setBooleanProperty("true", true);
         message.getLongProperty("true");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code boolean}, to get is as a {@code int} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testBoolean2Int() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to int
         message.setBooleanProperty("prop", true);
         message.getIntProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code boolean}, to get is as a {@code short} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testBoolean2Short() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to short
         message.setBooleanProperty("prop", true);
         message.getShortProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code boolean}, to get is as a {@code byte} throws a
    * {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testBoolean2Byte() {
      try {
         Message message = senderSession.createMessage();
         // store a value that can't be converted to byte
         message.setBooleanProperty("prop", true);
         message.getByteProperty("prop");
         Assert.fail("sec. 3.5.4 The unmarked cases [of Table 0-4] should raise a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * if a property is set as a {@code boolean}, it can also be read as a {@code boolean}.
    */
   @Test
   public void testBoolean2Boolean() {
      try {
         Message message = senderSession.createMessage();
         message.setBooleanProperty("prop", true);
         Assert.assertTrue(message.getBooleanProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }
}
