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
import javax.jms.TextMessage;
import java.util.Enumeration;
import java.util.Vector;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;

/**
 * Test the {@code javax.jms.Message} properties.
 * <p>
 * See JMS Specification, sec. 3.5 Message Properties (p.32-37)
 */
public class MessagePropertyTest extends PTPTestCase {

   /**
    * Test that any other class than {@code Boolean, Byte, Short, Integer, Long, Float, Double} and {@code String} used
    * in the {@code Message.setObjectProperty()} method throws a {@code javax.jms.MessageFormatException}.
    */
   @Test
   public void testSetObjectProperty_2() {
      try {
         Message message = senderSession.createMessage();
         message.setObjectProperty("prop", new Vector());
         Assert.fail("sec. 3.5.5 An attempt to use any other class [than Boolean, Byte,...,String] must throw " + "a JMS MessageFormatException.\n");
      } catch (MessageFormatException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.MessageFormatException, not a " + e);
      }
   }

   /**
    * if a property is set as a {@code Float} with the {@code Message.setObjectProperty()} method, it can be retrieve
    * directly as a {@code double} by {@code Message.getFloatProperty()}
    */
   @Test
   public void testSetObjectProperty_1() {
      try {
         Message message = senderSession.createMessage();
         message.setObjectProperty("pi", 3.14159f);
         Assert.assertEquals(3.14159f, message.getFloatProperty("pi"), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that a {@code null} value is returned by the {@code Message.getObjectProperty()} method if a property by the
    * specified name does not exits.
    */
   @Test
   public void testGetObjectProperty() {
      try {
         Message message = senderSession.createMessage();
         Assert.assertNull("sec. 3.5.5 A null value is returned [by the getObjectProperty method] if a property by the specified " + "name does not exits.\n", message.getObjectProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that a {@code null} value is returned by the {@code Message.getStringProperty()} method if a property by the
    * specified name does not exits.
    */
   @Test
   public void testGetStringProperty() {
      try {
         Message message = senderSession.createMessage();
         Assert.assertNull("sec. 3.5.5 A null value is returned [by the getStringProperty method] if a property by the specified " + "name does not exits.\n", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that an attempt to get a {@code double} property which does not exist throw a
    * {@code java.lang.NullPointerException}
    */
   @Test
   public void testGetDoubleProperty() {
      try {
         Message message = senderSession.createMessage();
         message.getDoubleProperty("prop");
         Assert.fail("Should raise a NullPointerException.\n");
      } catch (NullPointerException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that an attempt to get a {@code float} property which does not exist throw a
    * {@code java.lang.NullPointerException}
    */
   @Test
   public void testGetFloatProperty() {
      try {
         Message message = senderSession.createMessage();
         message.getFloatProperty("prop");
         Assert.fail("Should raise a NullPointerException.\n");
      } catch (NullPointerException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that an attempt to get a {@code long} property which does not exist throw a
    * {@code java.lang.NumberFormatException}
    */
   @Test
   public void testGetLongProperty() {
      try {
         Message message = senderSession.createMessage();
         message.getLongProperty("prop");
         Assert.fail("Should raise a NumberFormatException.\n");
      } catch (NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that an attempt to get a {@code int} property which does not exist throw a
    * {@code java.lang.NumberFormatException}
    */
   @Test
   public void testGetIntProperty() {
      try {
         Message message = senderSession.createMessage();
         message.getIntProperty("prop");
         Assert.fail("Should raise a NumberFormatException.\n");
      } catch (NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that an attempt to get a {@code short} property which does not exist throw a
    * {@code java.lang.NumberFormatException}
    */
   @Test
   public void testGetShortProperty() {
      try {
         Message message = senderSession.createMessage();
         message.getShortProperty("prop");
         Assert.fail("Should raise a NumberFormatException.\n");
      } catch (NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that an attempt to get a {@code byte} property which does not exist throw a
    * {@code java.lang.NumberFormatException}
    */
   @Test
   public void testGetByteProperty() {
      try {
         Message message = senderSession.createMessage();
         message.getByteProperty("prop");
         Assert.fail("Should raise a NumberFormatException.\n");
      } catch (NumberFormatException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }

   /*
    * Test that an attempt to get a {@code boolean} property which does not exist
    * returns {@code false}
    */
   @Test
   public void testGetBooleanProperty() {
      try {
         Message message = senderSession.createMessage();
         Assert.assertFalse(message.getBooleanProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the {@code Message.getPropertyNames()} method does not return the name of the JMS standard header fields
    * (e.g. {@code JMSCorrelationID}).
    */
   @Test
   public void testGetPropertyNames() {
      try {
         Message message = senderSession.createMessage();
         message.setJMSCorrelationID("foo");
         Enumeration enumeration = message.getPropertyNames();
         while (enumeration.hasMoreElements()) {
            String propName = (String) enumeration.nextElement();
            boolean valid = !propName.startsWith("JMS") || propName.startsWith("JMSX");
            Assert.assertTrue("sec. 3.5.6 The getPropertyNames method does not return the names of " + "the JMS standard header field [e.g. JMSCorrelationID]: " +
                                 propName, valid);
         }
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the {@code Message.getPropertyNames()} methods.
    */
   @Test
   public void testPropertyIteration() {
      try {
         Message message = senderSession.createMessage();
         Enumeration enumeration = message.getPropertyNames();
         // there can be some properties already defined (e.g. JMSXDeliveryCount)
         int originalCount = 0;
         while (enumeration.hasMoreElements()) {
            enumeration.nextElement();
            originalCount++;
         }
         message.setDoubleProperty("pi", 3.14159);
         enumeration = message.getPropertyNames();
         boolean foundPiProperty = false;
         int newCount = 0;
         while (enumeration.hasMoreElements()) {
            String propName = (String) enumeration.nextElement();
            newCount++;
            if ("pi".equals(propName)) {
               foundPiProperty = true;
            }
         }
         Assert.assertEquals(originalCount + 1, newCount);
         Assert.assertTrue(foundPiProperty);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the {@code Message.clearProperties()} method does not clear the value of the Message's body.
    */
   @Test
   public void testClearProperties_2() {
      try {
         TextMessage message = senderSession.createTextMessage();
         message.setText("foo");
         message.clearProperties();
         Assert.assertEquals("sec. 3.5.7 Clearing a message's  property entries does not clear the value of its body.\n", "foo", message.getText());
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the {@code Message.clearProperties()} method deletes all the properties of the Message.
    */
   @Test
   public void testClearProperties_1() {
      try {
         TextMessage message = senderSession.createTextMessage();
         message.setStringProperty("prop", "foo");
         message.clearProperties();
         Assert.assertNull("sec. 3.5.7 A message's properties are deleted by the clearProperties method.\n", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }
}
