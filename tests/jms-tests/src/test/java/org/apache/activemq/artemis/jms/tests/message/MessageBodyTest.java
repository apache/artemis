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
package org.apache.activemq.artemis.jms.tests.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class MessageBodyTest extends MessageBodyTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testSMBodyReadable() throws Exception {
      byte bValue = 123;
      StreamMessage sm = queueProducerSession.createStreamMessage();
      sm.writeByte(bValue);
      sm.setStringProperty("COM_SUN_JMS_TESTNAME", "xMessageEOFExceptionQTestforStreamMessage");
      queueProducer.send(sm);

      StreamMessage received = (StreamMessage) queueConsumer.receive(3000);
      received.readByte();
   }

   @Test
   public void testBytesMessage() throws Exception {
      BytesMessage m = queueProducerSession.createBytesMessage();

      // some arbitrary values
      boolean myBool = true;
      byte myByte = -111;
      short myShort = 15321;
      int myInt = 0x71ab6c80;
      long myLong = 0x20bf1e3fb6fa31dfL;
      float myFloat = Float.MAX_VALUE - 23465;
      double myDouble = Double.MAX_VALUE - 72387633;
      String myString = "abcdef&^*&!^ghijkl\uD5E2\uCAC7\uD2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5";
      logger.trace("String is length:{}", myString.length());
      char myChar = 'q';
      byte[] myBytes = new byte[]{-23, 114, -126, -12, 74, 87};

      m.writeBoolean(myBool);
      m.writeByte(myByte);
      m.writeShort(myShort);
      m.writeChar(myChar);
      m.writeInt(myInt);
      m.writeLong(myLong);
      m.writeFloat(myFloat);
      m.writeDouble(myDouble);
      m.writeUTF(myString);
      m.writeBytes(myBytes);
      m.writeBytes(myBytes, 2, 3);

      m.writeObject(myBool);
      m.writeObject(myByte);
      m.writeObject(myShort);
      m.writeObject(myInt);
      m.writeObject(myLong);
      m.writeObject(myFloat);
      m.writeObject(myDouble);
      m.writeObject(myString);
      m.writeObject(myBytes);

      try {
         m.writeObject(new Object());
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
         // OK
      }

      // Reading should not be possible when message is read-write
      try {
         m.readBoolean();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.readShort();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.readChar();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.readInt();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.readLong();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.readFloat();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.readDouble();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.readUTF();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.readUnsignedByte();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.readUnsignedShort();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         byte[] bytes = new byte[333];
         m.readBytes(bytes);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         byte[] bytes = new byte[333];
         m.readBytes(bytes, 111);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }
      try {
         m.getBodyLength();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
         // OK
      }

      queueProducer.send(m);

      BytesMessage m2 = (BytesMessage) queueConsumer.receive(2000);

      ProxyAssertSupport.assertNotNull(m2);

      ProxyAssertSupport.assertEquals(myBool, m2.readBoolean());
      ProxyAssertSupport.assertEquals(myByte, m2.readByte());
      ProxyAssertSupport.assertEquals(myShort, m2.readShort());
      ProxyAssertSupport.assertEquals(myChar, m2.readChar());
      ProxyAssertSupport.assertEquals(myInt, m2.readInt());
      ProxyAssertSupport.assertEquals(myLong, m2.readLong());
      ProxyAssertSupport.assertEquals(myFloat, m2.readFloat(), 0);
      ProxyAssertSupport.assertEquals(myDouble, m2.readDouble(), 0);
      ProxyAssertSupport.assertEquals(myString, m2.readUTF());

      byte[] bytes = new byte[6];
      int ret = m2.readBytes(bytes);
      ProxyAssertSupport.assertEquals(6, ret);

      assertArrayEquals(myBytes, bytes);

      byte[] bytes2 = new byte[3];
      ret = m2.readBytes(bytes2);

      ProxyAssertSupport.assertEquals(3, ret);

      ProxyAssertSupport.assertEquals(myBytes[2], bytes2[0]);
      ProxyAssertSupport.assertEquals(myBytes[3], bytes2[1]);
      ProxyAssertSupport.assertEquals(myBytes[4], bytes2[2]);

      ProxyAssertSupport.assertEquals(myBool, m2.readBoolean());
      ProxyAssertSupport.assertEquals(myByte, m2.readByte());
      ProxyAssertSupport.assertEquals(myShort, m2.readShort());
      ProxyAssertSupport.assertEquals(myInt, m2.readInt());
      ProxyAssertSupport.assertEquals(myLong, m2.readLong());
      ProxyAssertSupport.assertEquals(myFloat, m2.readFloat(), 0);
      ProxyAssertSupport.assertEquals(myDouble, m2.readDouble(), 0);
      ProxyAssertSupport.assertEquals(myString, m2.readUTF());

      bytes = new byte[6];
      ret = m2.readBytes(bytes);
      ProxyAssertSupport.assertEquals(6, ret);
      assertArrayEquals(myBytes, bytes);

      ret = m2.readBytes(bytes);
      ProxyAssertSupport.assertEquals(-1, ret);

      // Try and read past the end of the stream
      try {
         m2.readBoolean();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readByte();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readChar();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readDouble();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readFloat();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readInt();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readLong();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readShort();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readUnsignedByte();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readUnsignedShort();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readUTF();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
         // OK
      }

      // Message should not be writable in read-only mode
      try {
         m2.writeBoolean(myBool);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }
      try {
         m2.writeByte(myByte);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }
      try {
         m2.writeShort(myShort);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }
      try {
         m2.writeChar(myChar);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }

      try {
         m2.writeInt(myInt);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }
      try {
         m2.writeLong(myLong);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }
      try {
         m2.writeFloat(myFloat);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }
      try {
         m2.writeDouble(myDouble);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }
      try {
         m2.writeUTF(myString);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }

      try {
         m2.writeBytes(myBytes);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }

      try {
         m2.writeObject(myString);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
         // OK
      }

      long bodyLength = m2.getBodyLength();

      ProxyAssertSupport.assertEquals(161, bodyLength);

      m2.reset();

      // test the unsigned reads

      m2.readBoolean();
      int unsignedByte = m2.readUnsignedByte();

      ProxyAssertSupport.assertEquals(myByte & 0xFF, unsignedByte);

      int unsignedShort = m2.readUnsignedShort();

      ProxyAssertSupport.assertEquals(myShort & 0xFFFF, unsignedShort);

      m2.clearBody();

      try {
         m2.getBodyLength();
         ProxyAssertSupport.fail();
      } catch (MessageNotReadableException e) {
         // OK
      }

      m2.reset();

      ProxyAssertSupport.assertEquals(0, m2.getBodyLength());

      // Test that changing the received message doesn't affect the sent message
      m.reset();
      ProxyAssertSupport.assertEquals(161, m.getBodyLength());

      // Should be diffent object instances after sending *even* if in same JVM
      ProxyAssertSupport.assertFalse(m == m2);

   }

   @Test
   public void testMapMessage() throws Exception {
      MapMessage m1 = queueProducerSession.createMapMessage();

      // Some arbitrary values
      boolean myBool = true;
      byte myByte = 13;
      short myShort = 15321;
      int myInt = 0x71ab6c80;
      long myLong = 0x20bf1e3fb6fa31dfL;
      float myFloat = Float.MAX_VALUE - 23465;
      double myDouble = Double.MAX_VALUE - 72387633;
      String myString = "abcdef&^*&!^ghijkl\uD5E2\uCAC7\uD2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5";

      m1.setBoolean("myBool", myBool);
      m1.setByte("myByte", myByte);
      m1.setShort("myShort", myShort);
      m1.setInt("myInt", myInt);
      m1.setLong("myLong", myLong);
      m1.setFloat("myFloat", myFloat);
      m1.setDouble("myDouble", myDouble);
      m1.setString("myString", myString);

      m1.setObject("myBool", myBool);
      m1.setObject("myByte", myByte);
      m1.setObject("myShort", myShort);
      m1.setObject("myInt", myInt);
      m1.setObject("myLong", myLong);
      m1.setObject("myFloat", myFloat);
      m1.setObject("myDouble", myDouble);
      m1.setObject("myString", myString);

      try {
         m1.setObject("myIllegal", new Object());
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageFormatException e) {
      }

      queueProducer.send(m1);

      MapMessage m2 = (MapMessage) queueConsumer.receive(2000);

      ProxyAssertSupport.assertNotNull(m2);

      ProxyAssertSupport.assertEquals(myBool, m2.getBoolean("myBool"));
      ProxyAssertSupport.assertEquals(myByte, m2.getByte("myByte"));
      ProxyAssertSupport.assertEquals(myShort, m2.getShort("myShort"));
      ProxyAssertSupport.assertEquals(myInt, m2.getInt("myInt"));
      ProxyAssertSupport.assertEquals(myLong, m2.getLong("myLong"));
      ProxyAssertSupport.assertEquals(myFloat, m2.getFloat("myFloat"), 0);
      ProxyAssertSupport.assertEquals(myDouble, m2.getDouble("myDouble"), 0);
      ProxyAssertSupport.assertEquals(myString, m2.getString("myString"));

      // Properties should now be read-only
      try {
         m2.setBoolean("myBool", myBool);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setByte("myByte", myByte);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setShort("myShort", myShort);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setInt("myInt", myInt);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setLong("myLong", myLong);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setFloat("myFloat", myFloat);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setDouble("myDouble", myDouble);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setString("myString", myString);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      ProxyAssertSupport.assertTrue(m2.itemExists("myBool"));
      ProxyAssertSupport.assertTrue(m2.itemExists("myByte"));
      ProxyAssertSupport.assertTrue(m2.itemExists("myShort"));
      ProxyAssertSupport.assertTrue(m2.itemExists("myInt"));
      ProxyAssertSupport.assertTrue(m2.itemExists("myLong"));
      ProxyAssertSupport.assertTrue(m2.itemExists("myFloat"));
      ProxyAssertSupport.assertTrue(m2.itemExists("myDouble"));
      ProxyAssertSupport.assertTrue(m2.itemExists("myString"));

      ProxyAssertSupport.assertFalse(m2.itemExists("sausages"));

      Set<String> itemNames = new HashSet<>();
      Enumeration<String> en = m2.getMapNames();
      while (en.hasMoreElements()) {
         String propName = en.nextElement();
         itemNames.add(propName);
      }

      ProxyAssertSupport.assertEquals(8, itemNames.size());

      ProxyAssertSupport.assertTrue(itemNames.contains("myBool"));
      ProxyAssertSupport.assertTrue(itemNames.contains("myByte"));
      ProxyAssertSupport.assertTrue(itemNames.contains("myShort"));
      ProxyAssertSupport.assertTrue(itemNames.contains("myInt"));
      ProxyAssertSupport.assertTrue(itemNames.contains("myLong"));
      ProxyAssertSupport.assertTrue(itemNames.contains("myFloat"));
      ProxyAssertSupport.assertTrue(itemNames.contains("myDouble"));
      ProxyAssertSupport.assertTrue(itemNames.contains("myString"));

      // Check property conversions

      // Boolean property can be read as String but not anything else

      ProxyAssertSupport.assertEquals(String.valueOf(myBool), m2.getString("myBool"));

      try {
         m2.getByte("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getShort("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getInt("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getLong("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloat("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDouble("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      // byte item can be read as short, int, long or String

      ProxyAssertSupport.assertEquals(myByte, m2.getShort("myByte"));
      ProxyAssertSupport.assertEquals(myByte, m2.getInt("myByte"));
      ProxyAssertSupport.assertEquals(myByte, m2.getLong("myByte"));
      ProxyAssertSupport.assertEquals(String.valueOf(myByte), m2.getString("myByte"));

      try {
         m2.getBoolean("myByte");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloat("myByte");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDouble("myByte");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      // short item can be read as int, long or String

      ProxyAssertSupport.assertEquals(myShort, m2.getInt("myShort"));
      ProxyAssertSupport.assertEquals(myShort, m2.getLong("myShort"));
      ProxyAssertSupport.assertEquals(String.valueOf(myShort), m2.getString("myShort"));

      try {
         m2.getByte("myShort");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBoolean("myShort");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloat("myShort");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDouble("myShort");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      // int item can be read as long or String

      ProxyAssertSupport.assertEquals(myInt, m2.getLong("myInt"));
      ProxyAssertSupport.assertEquals(String.valueOf(myInt), m2.getString("myInt"));

      try {
         m2.getShort("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getByte("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBoolean("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloat("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDouble("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      // long item can be read as String

      ProxyAssertSupport.assertEquals(String.valueOf(myLong), m2.getString("myLong"));

      try {
         m2.getInt("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getShort("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getByte("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBoolean("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloat("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDouble("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      // float can be read as double or String

      ProxyAssertSupport.assertEquals(String.valueOf(myFloat), m2.getString("myFloat"));
      ProxyAssertSupport.assertEquals(myFloat, m2.getDouble("myFloat"), 0);

      try {
         m2.getInt("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getShort("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getLong("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getByte("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBoolean("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      // double can be read as String

      ProxyAssertSupport.assertEquals(String.valueOf(myDouble), m2.getString("myDouble"));

      try {
         m2.getFloat("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getInt("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getShort("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getByte("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBoolean("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloat("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      m2.clearBody();

      ProxyAssertSupport.assertFalse(m2.getMapNames().hasMoreElements());

      // Test String -> Numeric and bool conversions
      MapMessage m3 = queueProducerSession.createMapMessage();

      m3.setString("myBool", String.valueOf(myBool));
      m3.setString("myByte", String.valueOf(myByte));
      m3.setString("myShort", String.valueOf(myShort));
      m3.setString("myInt", String.valueOf(myInt));
      m3.setString("myLong", String.valueOf(myLong));
      m3.setString("myFloat", String.valueOf(myFloat));
      m3.setString("myDouble", String.valueOf(myDouble));
      m3.setString("myIllegal", "xyz123");

      ProxyAssertSupport.assertEquals(myBool, m3.getBoolean("myBool"));
      ProxyAssertSupport.assertEquals(myByte, m3.getByte("myByte"));
      ProxyAssertSupport.assertEquals(myShort, m3.getShort("myShort"));
      ProxyAssertSupport.assertEquals(myInt, m3.getInt("myInt"));
      ProxyAssertSupport.assertEquals(myLong, m3.getLong("myLong"));
      ProxyAssertSupport.assertEquals(myFloat, m3.getFloat("myFloat"), 0);
      ProxyAssertSupport.assertEquals(myDouble, m3.getDouble("myDouble"), 0);

      m3.getBoolean("myIllegal");

      try {
         m3.getByte("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getShort("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getInt("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getLong("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getFloat("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getDouble("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }

   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();
   }

   static class TestSerializable implements Serializable {

      private static final long serialVersionUID = -8641359255228705573L;

      String str;
   }

   @Test
   public void testObjectMessage() throws Exception {
      TestSerializable obj = new TestSerializable();

      obj.str = "abcdefg";

      ObjectMessage m1 = queueProducerSession.createObjectMessage(obj);

      queueProducer.send(m1);

      ObjectMessage m2 = (ObjectMessage) queueConsumer.receive(2000);

      ProxyAssertSupport.assertNotNull(m2);

      TestSerializable obj2 = (TestSerializable) m2.getObject();

      ProxyAssertSupport.assertEquals(obj.str, obj2.str);

      ObjectMessage m3 = queueProducerSession.createObjectMessage();

      m3.setObject(obj);

      queueProducer.send(m3);

      obj.str = "xyz123";

      ObjectMessage m4 = (ObjectMessage) queueConsumer.receive(2000);

      ProxyAssertSupport.assertNotNull(m4);

      TestSerializable obj3 = (TestSerializable) m4.getObject();

      ProxyAssertSupport.assertEquals("abcdefg", obj3.str);

      try {
         m4.setObject(obj);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      m4.clearBody();

      m4.setObject(obj);

      TestSerializable obj4 = (TestSerializable) m4.getObject();

      ProxyAssertSupport.assertNotNull(obj4);

   }

   @Test
   public void testStreamMessage() throws Exception {
      StreamMessage m = queueProducerSession.createStreamMessage();

      // Some arbitrary values
      boolean myBool = true;
      byte myByte = -111;
      short myShort = 15321;
      int myInt = 0x71ab6c80;
      long myLong = 0x20bf1e3fb6fa31dfL;
      float myFloat = Float.MAX_VALUE - 23465;
      double myDouble = Double.MAX_VALUE - 72387633;
      String myString = "abcdef&^*&!^ghijkl\uD5E2\uCAC7\uD2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5";
      char myChar = 'q';
      byte[] myBytes = new byte[]{-23, 114, -126, -12, 74, 87};

      m.writeBoolean(myBool);
      m.writeByte(myByte);
      m.writeShort(myShort);
      m.writeChar(myChar);
      m.writeInt(myInt);
      m.writeLong(myLong);
      m.writeFloat(myFloat);
      m.writeDouble(myDouble);
      m.writeString(myString);
      m.writeBytes(myBytes);
      m.writeBytes(myBytes, 2, 3);

      m.writeObject(myBool);
      m.writeObject(myByte);
      m.writeObject(myShort);
      m.writeObject(myInt);
      m.writeObject(myLong);
      m.writeObject(myFloat);
      m.writeObject(myDouble);
      m.writeObject(myString);
      m.writeObject(myBytes);

      try {
         m.writeObject(new Object());
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      // Reading should not be possible when message is read-write
      try {
         m.readBoolean();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
      }
      try {
         m.readShort();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
      }
      try {
         m.readChar();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
      }
      try {
         m.readInt();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
      }
      try {
         m.readLong();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
      }
      try {
         m.readFloat();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
      }
      try {
         m.readDouble();
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
      }
      try {
         byte[] bytes = new byte[333];
         m.readBytes(bytes);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotReadableException e) {
      }

      queueProducer.send(m);

      StreamMessage m2 = (StreamMessage) queueConsumer.receive(2000);

      ProxyAssertSupport.assertEquals(myBool, m2.readBoolean());
      ProxyAssertSupport.assertEquals(myByte, m2.readByte());
      ProxyAssertSupport.assertEquals(myShort, m2.readShort());
      ProxyAssertSupport.assertEquals(myChar, m2.readChar());
      ProxyAssertSupport.assertEquals(myInt, m2.readInt());
      ProxyAssertSupport.assertEquals(myLong, m2.readLong());
      ProxyAssertSupport.assertEquals(myFloat, m2.readFloat(), 0);
      ProxyAssertSupport.assertEquals(myDouble, m2.readDouble(), 0);
      ProxyAssertSupport.assertEquals(myString, m2.readString());

      byte[] bytes = new byte[6];
      int ret = m2.readBytes(bytes);
      ProxyAssertSupport.assertEquals(6, ret);

      assertArrayEquals(myBytes, bytes);

      ret = m2.readBytes(bytes);
      ProxyAssertSupport.assertEquals(-1, ret);

      byte[] bytes2 = new byte[3];
      ret = m2.readBytes(bytes2);

      ProxyAssertSupport.assertEquals(3, ret);

      ProxyAssertSupport.assertEquals(myBytes[2], bytes2[0]);
      ProxyAssertSupport.assertEquals(myBytes[3], bytes2[1]);
      ProxyAssertSupport.assertEquals(myBytes[4], bytes2[2]);

      ret = m2.readBytes(bytes2);
      ProxyAssertSupport.assertEquals(-1, ret);

      ProxyAssertSupport.assertEquals(myBool, m2.readBoolean());
      ProxyAssertSupport.assertEquals(myByte, m2.readByte());
      ProxyAssertSupport.assertEquals(myShort, m2.readShort());
      ProxyAssertSupport.assertEquals(myInt, m2.readInt());
      ProxyAssertSupport.assertEquals(myLong, m2.readLong());
      ProxyAssertSupport.assertEquals(myFloat, m2.readFloat(), 0);
      ProxyAssertSupport.assertEquals(myDouble, m2.readDouble(), 0);
      ProxyAssertSupport.assertEquals(myString, m2.readString());

      bytes = new byte[6];
      ret = m2.readBytes(bytes);
      ProxyAssertSupport.assertEquals(6, ret);
      assertArrayEquals(myBytes, bytes);

      ret = m2.readBytes(bytes);
      ProxyAssertSupport.assertEquals(-1, ret);

      // Try and read past the end of the stream
      try {
         m2.readBoolean();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
      }

      try {
         m2.readByte();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
      }

      try {
         m2.readChar();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
      }

      try {
         m2.readDouble();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
      }

      try {
         m2.readFloat();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
      }

      try {
         m2.readInt();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
      }

      try {
         m2.readLong();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
      }

      try {
         m2.readShort();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
      }

      // Message should not be writable in read-only mode
      try {
         m2.writeBoolean(myBool);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }
      try {
         m2.writeByte(myByte);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }
      try {
         m2.writeShort(myShort);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }
      try {
         m2.writeChar(myChar);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }

      try {
         m2.writeInt(myInt);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }
      try {
         m2.writeLong(myLong);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }
      try {
         m2.writeFloat(myFloat);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }
      try {
         m2.writeDouble(myDouble);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }

      try {
         m2.writeBytes(myBytes);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }

      try {
         m2.writeObject(myString);
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageNotWriteableException e) {
      }

      m2.reset();

      // check we go back to the beginning
      ProxyAssertSupport.assertEquals(myBool, m2.readBoolean());
      ProxyAssertSupport.assertEquals(myByte, m2.readByte());
      ProxyAssertSupport.assertEquals(myShort, m2.readShort());
      ProxyAssertSupport.assertEquals(myChar, m2.readChar());
      ProxyAssertSupport.assertEquals(myInt, m2.readInt());
      ProxyAssertSupport.assertEquals(myLong, m2.readLong());
      ProxyAssertSupport.assertEquals(myFloat, m2.readFloat(), 0);
      ProxyAssertSupport.assertEquals(myDouble, m2.readDouble(), 0);
      ProxyAssertSupport.assertEquals(myString, m2.readString());

      m2.clearBody();

      try {
         // Should now be write only
         m2.readBoolean();
         ProxyAssertSupport.fail();
      } catch (MessageNotReadableException e) {
      }

      m2.writeBoolean(myBool);

      m2.reset();

      ProxyAssertSupport.assertEquals(myBool, m2.readBoolean());
      try {
         m2.readBoolean();
         ProxyAssertSupport.fail();
      } catch (MessageEOFException e) {
      }

      // Test that changing the received message doesn't affect the sent message
      m.reset();
      ProxyAssertSupport.assertEquals(myBool, m.readBoolean());
      ProxyAssertSupport.assertEquals(myByte, m.readByte());
      ProxyAssertSupport.assertEquals(myShort, m.readShort());
      ProxyAssertSupport.assertEquals(myChar, m.readChar());
      ProxyAssertSupport.assertEquals(myInt, m.readInt());
      ProxyAssertSupport.assertEquals(myLong, m.readLong());
      ProxyAssertSupport.assertEquals(myFloat, m.readFloat(), 0);
      ProxyAssertSupport.assertEquals(myDouble, m.readDouble(), 0);
      ProxyAssertSupport.assertEquals(myString, m.readString());

      // Should be diffent object instances after sending *even* if in same JVM
      ProxyAssertSupport.assertFalse(m == m2);
   }

   @Test
   public void testTextMessage() throws Exception {
      TextMessage m = queueProducerSession.createTextMessage();

      // Arbitrary string with some Chinese characters to make sure UTF encoding
      // is ok
      String myString = "wwiuhdiuwhdwuhdwuhduqwhdiuwhdiuhwed8u29837482787\uD5E2\uCAC7\uD2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5";

      m.setText(myString);

      queueProducer.send(m);

      TextMessage m2 = (TextMessage) queueConsumer.receive(2000);

      ProxyAssertSupport.assertEquals(myString, m2.getText());

      m = queueProducerSession.createTextMessage(myString);
      queueProducer.send(m);

      m2 = (TextMessage) queueConsumer.receive(2000);

      ProxyAssertSupport.assertEquals(myString, m2.getText());

      try {
         m2.setText("Should be read-only");
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      m2.clearBody();
      ProxyAssertSupport.assertNull(m2.getText());
      m2.setText("Now it is read-write");
   }
}
