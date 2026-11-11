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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageFormatException;
import javax.jms.MessageFormatRuntimeException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.jms.tests.ActiveMQServerTestCase;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testing of message property conversion. See {@link javax.jms.Message} for details
 */
public class MessagePropertyConversionTest extends ActiveMQServerTestCase {


   private Connection producerConnection, consumerConnection;

   private Session queueProducerSession, queueConsumerSession;

   private MessageProducer queueProducer;

   private MessageConsumer queueConsumer;




   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      producerConnection = getConnectionFactory().createConnection();
      consumerConnection = getConnectionFactory().createConnection();

      queueProducerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      queueConsumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      queueProducer = queueProducerSession.createProducer(queue1);
      queueConsumer = queueConsumerSession.createConsumer(queue1);

      consumerConnection.start();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      producerConnection.close();
      consumerConnection.close();
   }

   @Test
   public void testObjectString() throws Exception {
      JMSContext ctx = addContext(getConnectionFactory().createContext());

      JMSProducer producer = ctx.createProducer();

      producer.setProperty("astring", "test");

      Object prop = producer.getObjectProperty("astring");

      ProxyAssertSupport.assertNotNull(prop);

      ProxyAssertSupport.assertTrue(prop instanceof String);
   }

   @Test
   public void msgNullPropertyConversionTests() throws Exception {
      JMSContext ctx = addContext(getConnectionFactory().createContext());

      JMSProducer producer = ctx.createProducer();

      try {
         producer.setProperty(null, true);
         ProxyAssertSupport.fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         //pass
      }
      try {
         producer.setProperty(null, "string");
         ProxyAssertSupport.fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         //pass
      }
      try {
         producer.setProperty(null, 1);
         ProxyAssertSupport.fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         //pass
      }
      try {
         producer.setProperty(null, 1.0);
         ProxyAssertSupport.fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         //pass
      }
      try {
         producer.setProperty(null, 1L);
         ProxyAssertSupport.fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         //pass
      }
      try {
         producer.setProperty(null, 1.10f);
         ProxyAssertSupport.fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         //pass
      }
      try {
         producer.setProperty(null, (byte) 1);
         ProxyAssertSupport.fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         //pass
      }
      try {
         producer.setProperty(null, (short) 1);
         ProxyAssertSupport.fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         //pass
      }
      try {
         producer.setProperty(null, SimpleString.of("foo"));
         ProxyAssertSupport.fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException e) {
         //pass
      }
   }

   @Test
   public void msgPropertyConversionTests() throws Exception {
      JMSContext ctx = addContext(getConnectionFactory().createContext());

      JMSProducer producer = ctx.createProducer();

      boolean bool = true;
      byte bValue = 1;
      short nShort = 2;
      int nInt = 3;
      long nLong = 4;
      float nFloat = 5;
      double nDouble = 6;

      producer.setProperty("aboolean", bool);
      producer.setProperty("abyte", bValue);
      producer.setProperty("ashort", nShort);
      producer.setProperty("anint", nInt);
      producer.setProperty("afloat", nFloat);
      producer.setProperty("adouble", nDouble);
      producer.setProperty("astring", "test");
      producer.setProperty("along", nLong);
      producer.setProperty("true", "true");
      producer.setProperty("false", "false");
      producer.setProperty("anotherString", "1");
      String myBool = producer.getStringProperty("aboolean");

      if (Boolean.valueOf(myBool).booleanValue() != bool) {
         ProxyAssertSupport.fail("conversion from boolean to string failed");
      }

      try {
         producer.getByteProperty("aboolean");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("did not catch expected Exception -- boolean to byte");
      }

      try {
         producer.getShortProperty("aboolean");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getIntProperty("aboolean");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getLongProperty("aboolean");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getFloatProperty("aboolean");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      // invalid - boolean to double
      try {
         producer.getDoubleProperty("aboolean");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      String myByte = producer.getStringProperty("abyte");

      if (Byte.parseByte(myByte) != bValue) {
         ProxyAssertSupport.fail("conversion from byte to string failed");
      }

      if (producer.getShortProperty("abyte") != bValue) {
         ProxyAssertSupport.fail("conversion from byte to short failed");
      }

      if (producer.getIntProperty("abyte") != bValue) {
         ProxyAssertSupport.fail("conversion from byte to int failed");
      }

      if (producer.getLongProperty("abyte") != bValue) {
         ProxyAssertSupport.fail("conversion from byte to long failed");
      }

      try {
         producer.getBooleanProperty("abyte");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getFloatProperty("abyte");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);

      }

      try {
         producer.getDoubleProperty("abyte");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      String myshort = producer.getStringProperty("ashort");

      if (Short.parseShort(myshort) != nShort) {
         ProxyAssertSupport.fail("conversion from short to string failed");
      }

      if (producer.getIntProperty("ashort") != nShort) {
         ProxyAssertSupport.fail("conversion from short to int failed");
      }

      if (producer.getLongProperty("ashort") != nShort) {
         ProxyAssertSupport.fail("conversion from short to long failed");
      }

      try {
         producer.getBooleanProperty("ashort");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);

      }

      try {
         producer.getByteProperty("ashort");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);

      }

      try {
         producer.getFloatProperty("ashort");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getDoubleProperty("ashort");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);

      }

      if (Integer.parseInt(producer.getStringProperty("anint")) != nInt) {
         ProxyAssertSupport.fail("conversion from int to string failed");
      }

      if (producer.getLongProperty("anint") != nInt) {
         ProxyAssertSupport.fail("conversion from int to long failed");
      }

      try {
         producer.getBooleanProperty("anint");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getByteProperty("anint");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getShortProperty("anint");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getFloatProperty("anint");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getDoubleProperty("anint");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      if (Long.parseLong(producer.getStringProperty("along")) != nLong) {
         ProxyAssertSupport.fail("conversion from long to string failed");
      }

      try {
         producer.getBooleanProperty("along");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getByteProperty("along");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getShortProperty("along");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getIntProperty("along");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getFloatProperty("along");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getDoubleProperty("along");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      if (Float.parseFloat(producer.getStringProperty("afloat")) != nFloat) {
         ProxyAssertSupport.fail("conversion from float to string failed");
      }

      if (producer.getDoubleProperty("afloat") != nFloat) {
         ProxyAssertSupport.fail("conversion from long to double failed");
      }

      try {
         producer.getBooleanProperty("afloat");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getByteProperty("afloat");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getShortProperty("afloat");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getIntProperty("afloat");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getLongProperty("afloat");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");

      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      if (Double.parseDouble(producer.getStringProperty("adouble")) != nDouble) {
         ProxyAssertSupport.fail("conversion from double to string failed");
      }

      try {
         producer.getBooleanProperty("adouble");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getByteProperty("adouble");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getShortProperty("adouble");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getIntProperty("adouble");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      try {
         producer.getLongProperty("adouble");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      // invalid - double to float
      try {
         producer.getFloatProperty("adouble");
         ProxyAssertSupport.fail("MessageFormatRuntimeException expected");
      } catch (MessageFormatRuntimeException me) {
         //pass
      } catch (Exception ee) {
         ProxyAssertSupport.fail("Caught unexpected exception: " + ee);
      }

      if ((producer.getBooleanProperty("true")) != true) {
         ProxyAssertSupport.fail("conversion from string to boolean - expect true  - failed");
      }
      if ((producer.getBooleanProperty("false")) != false) {
         ProxyAssertSupport.fail("conversion from string to boolean expect false - failed");
      }

      if (producer.getByteProperty("anotherString") != 1) {
         ProxyAssertSupport.fail("conversion from string to byte failed");
      }

      if (producer.getShortProperty("anotherString") != 1) {
         ProxyAssertSupport.fail("conversion from string to short failed");
      }

      if (producer.getIntProperty("anotherString") != 1) {
         ProxyAssertSupport.fail("conversion from string to int failed");
      }

      if (producer.getLongProperty("anotherString") != 1) {
         ProxyAssertSupport.fail("conversion from string to long failed");
      }

      if (producer.getFloatProperty("anotherString") != 1) {
         ProxyAssertSupport.fail("conversion from string to float failed");
      }

      if (producer.getDoubleProperty("anotherString") != 1) {
         ProxyAssertSupport.fail("conversion from string to double failed");
      }
   }

   @Test
   public void testResetToNull() throws JMSException {
      Message m1 = queueProducerSession.createMessage();
      m1.setStringProperty("key", "fish");
      m1.setBooleanProperty("key", true);
      m1.setStringProperty("key2", "fish");
      m1.setStringProperty("key2", null);
      m1.setStringProperty("key3", "fish");
      m1.setObjectProperty("key3", null);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive(1000);
      assertEquals(Boolean.TRUE, m2.getObjectProperty("key"), "key should be true");
      assertNull(m2.getObjectProperty("key2"), "key2 should be null");
      assertNull(m2.getObjectProperty("key3"), "key3 should be null");
   }

   @Test
   public void testBooleanConversion() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      boolean myBool = true;
      m1.setBooleanProperty("myBool", myBool);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive(2000);

      // Boolean property can be read as boolean and String but not anything
      // else

      ProxyAssertSupport.assertEquals(myBool, m2.getBooleanProperty("myBool"));
      ProxyAssertSupport.assertEquals(String.valueOf(myBool), m2.getStringProperty("myBool"));

      try {
         m2.getByteProperty("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getShortProperty("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getIntProperty("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getLongProperty("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloatProperty("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDoubleProperty("myBool");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testByteConversion() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      byte myByte = 13;
      m1.setByteProperty("myByte", myByte);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive(2000);

      // Byte property can be read as byte, short, int, long or String

      ProxyAssertSupport.assertEquals(myByte, m2.getByteProperty("myByte"));
      ProxyAssertSupport.assertEquals(myByte, m2.getShortProperty("myByte"));
      ProxyAssertSupport.assertEquals(myByte, m2.getIntProperty("myByte"));
      ProxyAssertSupport.assertEquals(myByte, m2.getLongProperty("myByte"));
      ProxyAssertSupport.assertEquals(String.valueOf(myByte), m2.getStringProperty("myByte"));

      try {
         m2.getBooleanProperty("myByte");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloatProperty("myByte");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDoubleProperty("myByte");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testShortConversion() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      short myShort = 15321;
      m1.setShortProperty("myShort", myShort);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive(2000);

      // Short property can be read as short, int, long or String

      ProxyAssertSupport.assertEquals(myShort, m2.getShortProperty("myShort"));
      ProxyAssertSupport.assertEquals(myShort, m2.getIntProperty("myShort"));
      ProxyAssertSupport.assertEquals(myShort, m2.getLongProperty("myShort"));
      ProxyAssertSupport.assertEquals(String.valueOf(myShort), m2.getStringProperty("myShort"));

      try {
         m2.getByteProperty("myShort");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBooleanProperty("myShort");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloatProperty("myShort");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDoubleProperty("myShort");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testIntConversion() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      int myInt = 0x71ab6c80;
      m1.setIntProperty("myInt", myInt);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive(2000);

      // Int property can be read as int, long or String

      ProxyAssertSupport.assertEquals(myInt, m2.getIntProperty("myInt"));
      ProxyAssertSupport.assertEquals(myInt, m2.getLongProperty("myInt"));
      ProxyAssertSupport.assertEquals(String.valueOf(myInt), m2.getStringProperty("myInt"));

      try {
         m2.getShortProperty("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getByteProperty("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBooleanProperty("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloatProperty("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDoubleProperty("myInt");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testLongConversion() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      long myLong = 0x20bf1e3fb6fa31dfL;
      m1.setLongProperty("myLong", myLong);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive(2000);

      // Long property can be read as long and String

      ProxyAssertSupport.assertEquals(myLong, m2.getLongProperty("myLong"));
      ProxyAssertSupport.assertEquals(String.valueOf(myLong), m2.getStringProperty("myLong"));

      try {
         m2.getIntProperty("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getShortProperty("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getByteProperty("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBooleanProperty("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloatProperty("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getDoubleProperty("myLong");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testFloatConversion() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      float myFloat = Float.MAX_VALUE - 23465;
      m1.setFloatProperty("myFloat", myFloat);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive(2000);

      // Float property can be read as float, double or String

      ProxyAssertSupport.assertEquals(myFloat, m2.getFloatProperty("myFloat"), 0);
      ProxyAssertSupport.assertEquals(String.valueOf(myFloat), m2.getStringProperty("myFloat"));
      ProxyAssertSupport.assertEquals(myFloat, m2.getDoubleProperty("myFloat"), 0);

      try {
         m2.getIntProperty("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getShortProperty("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getLongProperty("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getByteProperty("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBooleanProperty("myFloat");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testDoubleConversion() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      double myDouble = Double.MAX_VALUE - 72387633;
      m1.setDoubleProperty("myDouble", myDouble);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive(2000);

      // Double property can be read as double and String

      ProxyAssertSupport.assertEquals(myDouble, m2.getDoubleProperty("myDouble"), 0);
      ProxyAssertSupport.assertEquals(String.valueOf(myDouble), m2.getStringProperty("myDouble"));

      try {
         m2.getFloatProperty("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getIntProperty("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getShortProperty("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getByteProperty("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getBooleanProperty("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }

      try {
         m2.getFloatProperty("myDouble");
         ProxyAssertSupport.fail();
      } catch (MessageFormatException e) {
      }
   }

   @Test
   public void testStringConversion() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      boolean myBool = true;
      byte myByte = 13;
      short myShort = 15321;
      int myInt = 0x71ab6c80;
      long myLong = 0x20bf1e3fb6fa31dfL;
      float myFloat = Float.MAX_VALUE - 23465;
      double myDouble = Double.MAX_VALUE - 72387633;
      String myString = "abcdef&^*&!^ghijkl";

      m1.setStringProperty("myString", myString);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive(2000);

      ProxyAssertSupport.assertEquals(myString, m2.getStringProperty("myString"));

      // Test String -> Numeric and bool conversions.

      // String property can be read as boolean, byte, short,
      // int, long, float, double and String, with the possibility to
      // throw a runtime exception if the primitive's valueOf method does not
      // accept the String as a valid representation of the primitive

      Message m3 = queueProducerSession.createMessage();

      m3.setStringProperty("myBool", String.valueOf(myBool));
      m3.setStringProperty("myByte", String.valueOf(myByte));
      m3.setStringProperty("myShort", String.valueOf(myShort));
      m3.setStringProperty("myInt", String.valueOf(myInt));
      m3.setStringProperty("myLong", String.valueOf(myLong));
      m3.setStringProperty("myFloat", String.valueOf(myFloat));
      m3.setStringProperty("myDouble", String.valueOf(myDouble));
      m3.setStringProperty("myIllegal", "xyz123");

      queueProducer.send(m3);

      Message m4 = queueConsumer.receive(2000);

      ProxyAssertSupport.assertEquals(myBool, m4.getBooleanProperty("myBool"));
      ProxyAssertSupport.assertEquals(myByte, m4.getByteProperty("myByte"));
      ProxyAssertSupport.assertEquals(myShort, m4.getShortProperty("myShort"));
      ProxyAssertSupport.assertEquals(myInt, m4.getIntProperty("myInt"));
      ProxyAssertSupport.assertEquals(myLong, m4.getLongProperty("myLong"));
      ProxyAssertSupport.assertEquals(myFloat, m4.getFloatProperty("myFloat"), 0);
      ProxyAssertSupport.assertEquals(myDouble, m4.getDoubleProperty("myDouble"), 0);

      ProxyAssertSupport.assertFalse(m4.getBooleanProperty("myIllegal"));

      try {
         m4.getByteProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m4.getShortProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m4.getIntProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m4.getLongProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m4.getFloatProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m4.getDoubleProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testJMSXDeliveryCountConversion() throws Exception {
      Message m1 = queueProducerSession.createMessage();
      queueProducer.send(m1);

      Message m2 = queueConsumer.receive(2000);

      int count = m2.getIntProperty("JMSXDeliveryCount");
      ProxyAssertSupport.assertEquals(String.valueOf(count), m2.getStringProperty("JMSXDeliveryCount"));
      ProxyAssertSupport.assertEquals(count, m2.getLongProperty("JMSXDeliveryCount"));
   }
}
