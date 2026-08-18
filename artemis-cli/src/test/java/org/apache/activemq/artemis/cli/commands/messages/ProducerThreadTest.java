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
package org.apache.activemq.artemis.cli.commands.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.cli.test.TestActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ProducerThreadTest {

   ProducerThread producer;
   Message mockMessage;

   @BeforeEach
   public void setUp() {
      producer = new ProducerThread(null, ActiveMQDestination.createQueue(RandomUtil.randomUUIDString()), 0, null);
      mockMessage = Mockito.mock(Message.class);
   }

   @Test
   public void testBooleanPropertyTrue() throws Exception {
      doBooleanPropertyTestImpl("myTrueBoolean", true);
   }

   @Test
   public void testBooleanPropertyFalse() throws Exception {
      doBooleanPropertyTestImpl("myFalseBoolean", false);
   }

   private void doBooleanPropertyTestImpl(String key, boolean value) throws JMSException {
      producer.setProperties(createJsonProperty(boolean.class.getSimpleName(), key, String.valueOf(value)));
      producer.applyProperties(mockMessage);

      Mockito.verify(mockMessage).setBooleanProperty(key, value);
      Mockito.verifyNoMoreInteractions(mockMessage);
   }

   @Test
   public void testIntProperty() throws Exception {
      String key = "myInt";
      int value = RandomUtil.randomInt();

      producer.setProperties(createJsonProperty(int.class.getSimpleName(), key, String.valueOf(value)));
      producer.applyProperties(mockMessage);

      Mockito.verify(mockMessage).setIntProperty(key, value);
      Mockito.verifyNoMoreInteractions(mockMessage);

      // Now with a bad int value
      producer.setProperties(createJsonProperty(int.class.getSimpleName(), key, "badInt"));
      try {
         producer.applyProperties(mockMessage);
         fail("should have thrown");
      } catch (NumberFormatException e) {
         // expected
      }

      Mockito.verifyNoMoreInteractions(mockMessage);
   }

   @Test
   public void testLongProperty() throws Exception {
      String key = "myLong";
      long value = RandomUtil.randomLong();

      producer.setProperties(createJsonProperty(long.class.getSimpleName(), key, String.valueOf(value)));
      producer.applyProperties(mockMessage);

      Mockito.verify(mockMessage).setLongProperty(key, value);
      Mockito.verifyNoMoreInteractions(mockMessage);

      // Now with a bad long value
      producer.setProperties(createJsonProperty(long.class.getSimpleName(), key, "badLong"));
      try {
         producer.applyProperties(mockMessage);
         fail("should have thrown");
      } catch (NumberFormatException e) {
         // expected
      }

      Mockito.verifyNoMoreInteractions(mockMessage);
   }

   @Test
   public void testByteProperty() throws Exception {
      String key = "myByte";
      byte value = RandomUtil.randomByte();

      producer.setProperties(createJsonProperty(byte.class.getSimpleName(), key, String.valueOf(value)));
      producer.applyProperties(mockMessage);

      Mockito.verify(mockMessage).setByteProperty(key, value);
      Mockito.verifyNoMoreInteractions(mockMessage);

      // Now with a bad byte value
      producer.setProperties(createJsonProperty(byte.class.getSimpleName(), key, "128"));
      try {
         producer.applyProperties(mockMessage);
         fail("should have thrown");
      } catch (NumberFormatException e) {
         // expected
      }

      Mockito.verifyNoMoreInteractions(mockMessage);
   }

   @Test
   public void testShortProperty() throws Exception {
      String key = "myShort";
      short value = RandomUtil.randomShort();

      producer.setProperties(createJsonProperty(short.class.getSimpleName(), key, String.valueOf(value)));
      producer.applyProperties(mockMessage);

      Mockito.verify(mockMessage).setShortProperty(key, value);
      Mockito.verifyNoMoreInteractions(mockMessage);

      // Now with a bad short value
      producer.setProperties(createJsonProperty(short.class.getSimpleName(), key, "badShort"));
      try {
         producer.applyProperties(mockMessage);
         fail("should have thrown");
      } catch (NumberFormatException e) {
         // expected
      }

      Mockito.verifyNoMoreInteractions(mockMessage);
   }

   @Test
   public void testFloatProperty() throws Exception {
      String key = "myFloat";
      float value = RandomUtil.randomFloat();

      producer.setProperties(createJsonProperty(float.class.getSimpleName(), key, String.valueOf(value)));
      producer.applyProperties(mockMessage);

      Mockito.verify(mockMessage).setFloatProperty(key, value);
      Mockito.verifyNoMoreInteractions(mockMessage);

      // Now with a bad float value
      producer.setProperties(createJsonProperty(float.class.getSimpleName(), key, "badFloat"));
      try {
         producer.applyProperties(mockMessage);
         fail("should have thrown");
      } catch (NumberFormatException e) {
         // expected
      }

      Mockito.verifyNoMoreInteractions(mockMessage);
   }

   @Test
   public void testDoubleProperty() throws Exception {
      String key = "myDouble";
      double value = RandomUtil.randomDouble();

      producer.setProperties(createJsonProperty(double.class.getSimpleName(), key, String.valueOf(value)));
      producer.applyProperties(mockMessage);

      Mockito.verify(mockMessage).setDoubleProperty(key, value);
      Mockito.verifyNoMoreInteractions(mockMessage);

      // Now with a bad double value
      producer.setProperties(createJsonProperty(double.class.getSimpleName(), key, "badDouble"));
      try {
         producer.applyProperties(mockMessage);
         fail("should have thrown");
      } catch (NumberFormatException e) {
         // expected
      }

      Mockito.verifyNoMoreInteractions(mockMessage);
   }

   @Test
   public void testStringProperty() throws Exception {
      doStringPropertyTestImpl("string");
   }

   private void doStringPropertyTestImpl(String type) throws JMSException {
      String key = "myString";
      String value = RandomUtil.randomUUIDString();

      producer.setProperties(createJsonProperty(type, key, value));
      producer.applyProperties(mockMessage);

      Mockito.verify(mockMessage).setStringProperty(key, value);
      Mockito.verifyNoMoreInteractions(mockMessage);
   }

   @Test
   public void testPropertyTypeIsCaseInsensitive() throws Exception {
      doStringPropertyTestImpl("String");
   }

   @Test
   public void testBadMessagePropertyType() throws Exception {
      TestActionContext context = new TestActionContext();
      producer = new ProducerThread(null, ActiveMQDestination.createQueue(RandomUtil.randomUUIDString()), 0, context);

      producer.setProperties(createJsonProperty("myType", "myKey", "myValue"));
      producer.applyProperties(mockMessage);

      assertEquals("Unable to set property: myKey. Did not recognize type: myType. Supported types are: boolean, int, long, byte, short, float, double, string." + System.lineSeparator(), context.getStderr());
   }

   private static String createJsonProperty(String type, String key, String value) {
      return ("[{'type':'" + type + "','key':'" + key + "','value':'" + value + "'}]").replaceAll("'", "\"");
   }
}
