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
package org.objectweb.jtests.jms.conform.message;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.util.Enumeration;
import java.util.Vector;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

/**
 * Test the different types of messages provided by JMS.
 * <p>
 * JMS provides 6 types of messages which differs by the type of their body:
 * <ol>
 * <li>{@code Message} which doesn't have a body</li>
 * <li>{@code TextMessage} with a {@code String} as body</li>
 * <li>{@code ObjectMessage} with any {@code Object} as body</li>
 * <li>{@code BytesMessage} with a body made of {@code bytes}</li>
 * <li>{@code MapMessage} with name-value pairs of Java primitives in its body</li>
 * <li>{@code StreamMessage} with a stream of Java primitives as body</li>
 * </ol>
 * For each of this type of message, we test that a message can be sent and received with an empty body or not.
 */
public class MessageTypeTest extends PTPTestCase {

   /**
    * Send a {@code StreamMessage} with 2 Java primitives in its body (a {@code String} and a {@code double}).
    * <p>
    * Receive it and test that the values of the primitives of the body are correct
    */
   @Test
   public void testStreamMessage_2() {
      try {
         StreamMessage message = senderSession.createStreamMessage();
         message.writeString("pi");
         message.writeDouble(3.14159);
         sender.send(message);

         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of StreamMessage.\n", m instanceof StreamMessage);
         StreamMessage msg = (StreamMessage) m;
         Assert.assertEquals("pi", msg.readString());
         Assert.assertEquals(3.14159, msg.readDouble(), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Send a {@code StreamMessage} with an empty body.
    * <p>
    * Receive it and test if the message is effectively an instance of {@code StreamMessage}
    */
   @Test
   public void testStreamMessage_1() {
      try {
         StreamMessage message = senderSession.createStreamMessage();
         sender.send(message);

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of StreamMessage.\n", msg instanceof StreamMessage);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test in MapMessage the conversion between {@code getObject("foo")} and {@code getDouble("foo")} (the later
    * returning a java.lang.Double and the former a double)
    */
   @Test
   public void testMapMessageConversion() {
      try {
         MapMessage message = senderSession.createMapMessage();
         message.setDouble("pi", 3.14159);
         sender.send(message);

         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of MapMessage.\n", m instanceof MapMessage);
         MapMessage msg = (MapMessage) m;
         Assert.assertTrue(msg.getObject("pi") instanceof Double);
         Assert.assertEquals(3.14159, ((Double) msg.getObject("pi")).doubleValue(), 0);
         Assert.assertEquals(3.14159, msg.getDouble("pi"), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the if the name parameter of the set methods of a {@code MapMessage} is {@code null}, the method must
    * throw the error {@code java.lang.IllegalArgumentException}.
    */
   @Test
   public void testNullInSetMethodsForMapMessage() {
      try {
         MapMessage message = senderSession.createMapMessage();
         message.setBoolean(null, true);
         Assert.fail("Should throw an IllegalArgumentException");
      } catch (IllegalArgumentException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw an IllegalArgumentException, not a" + e);
      }
   }

   /**
    * Test that the if the name parameter of the set methods of a {@code MapMessage} is an empty String, the method must
    * throw the error {@code java.lang.IllegalArgumentException}.
    */
   @Test
   public void testEmptyStringInSetMethodsForMapMessage() {
      try {
         MapMessage message = senderSession.createMapMessage();
         message.setBoolean("", true);
         Assert.fail("Should throw an IllegalArgumentException");
      } catch (IllegalArgumentException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw an IllegalArgumentException, not a" + e);
      }
   }

   /**
    * Test that the {@code MapMessage.getMapNames()} method returns an empty {@code Enumeration} when no map has been
    * defined before.
    * <p>
    * Also test that the same method returns the correct names of the map.
    */
   @Test
   public void testgetMapNames() {
      try {
         MapMessage message = senderSession.createMapMessage();
         Enumeration<?> e = message.getMapNames();
         Assert.assertFalse("No map yet defined.\n", e.hasMoreElements());
         message.setDouble("pi", 3.14159);
         e = message.getMapNames();
         Assert.assertEquals("pi", e.nextElement());
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Send a {@code MapMessage} with 2 Java primitives in its body (a {@code String} and a {@code double}).
    * <p>
    * Receive it and test that the values of the primitives of the body are correct
    */
   @Test
   public void testMapMessage_2() {
      try {
         MapMessage message = senderSession.createMapMessage();
         message.setString("name", "pi");
         message.setDouble("value", 3.14159);
         sender.send(message);

         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of MapMessage.\n", m instanceof MapMessage);
         MapMessage msg = (MapMessage) m;
         Assert.assertEquals("pi", msg.getString("name"));
         Assert.assertEquals(3.14159, msg.getDouble("value"), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Send a {@code MapMessage} with an empty body.
    * <p>
    * Receive it and test if the message is effectively an instance of {@code MapMessage}
    */
   @Test
   public void testMapMessage_1() {
      try {
         MapMessage message = senderSession.createMapMessage();
         sender.send(message);

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of MapMessage.\n", msg instanceof MapMessage);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Send an {@code ObjectMessage} with a {@code Vector} (composed of a {@code String} and a {@code double}) in its
    * body.
    * <p>
    * Receive it and test that the values of the primitives of the body are correct
    */
   @Test
   public void testObjectMessage_2() {
      try {
         Vector<Object> vector = new Vector<>();
         vector.add("pi");
         vector.add(3.14159);

         ObjectMessage message = senderSession.createObjectMessage();
         message.setObject(vector);
         sender.send(message);

         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of ObjectMessage.\n", m instanceof ObjectMessage);
         ObjectMessage msg = (ObjectMessage) m;
         Assert.assertEquals(vector, msg.getObject());
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Send a {@code ObjectMessage} with an empty body.
    * <p>
    * Receive it and test if the message is effectively an instance of {@code ObjectMessage}
    */
   @Test
   public void testObjectMessage_1() {
      try {
         ObjectMessage message = senderSession.createObjectMessage();
         sender.send(message);

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of ObjectMessage.\n", msg instanceof ObjectMessage);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Send a {@code BytesMessage} with 2 Java primitives in its body (a {@code String} and a {@code double}).
    * <p>
    * Receive it and test that the values of the primitives of the body are correct
    */
   @Test
   public void testBytesMessage_2() {
      try {
         byte[] bytes = new String("pi").getBytes();
         BytesMessage message = senderSession.createBytesMessage();
         message.writeBytes(bytes);
         message.writeDouble(3.14159);
         sender.send(message);

         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of BytesMessage.\n", m instanceof BytesMessage);
         BytesMessage msg = (BytesMessage) m;
         byte[] receivedBytes = new byte[bytes.length];
         msg.readBytes(receivedBytes);
         Assert.assertEquals(new String(bytes), new String(receivedBytes));
         Assert.assertEquals(3.14159, msg.readDouble(), 0);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Send a {@code BytesMessage} with an empty body.
    * <p>
    * Receive it and test if the message is effectively an instance of {@code BytesMessage}
    */
   @Test
   public void testBytesMessage_1() {
      try {
         BytesMessage message = senderSession.createBytesMessage();
         sender.send(message);

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of BytesMessage.\n", msg instanceof BytesMessage);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Send a {@code TextMessage} with a {@code String} in its body.
    * <p>
    * Receive it and test that the received {@code String} corresponds to the sent one.
    */
   @Test
   public void testTextMessage_2() {
      try {
         TextMessage message = senderSession.createTextMessage();
         message.setText("testTextMessage_2");
         sender.send(message);

         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of TextMessage.\n", m instanceof TextMessage);
         TextMessage msg = (TextMessage) m;
         Assert.assertEquals("testTextMessage_2", msg.getText());
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Send a {@code TextMessage} with an empty body.
    * <p>
    * Receive it and test if the message is effectively an instance of {@code TextMessage}
    */
   @Test
   public void testTextMessage_1() {
      try {
         TextMessage message = senderSession.createTextMessage();
         sender.send(message);

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of TextMessage.\n", msg instanceof TextMessage);
      } catch (JMSException e) {
         fail(e);
      }
   }
}
