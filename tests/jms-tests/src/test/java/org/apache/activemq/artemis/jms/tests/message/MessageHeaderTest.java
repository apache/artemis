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

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotWriteableException;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMapMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQObjectMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQStreamMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class MessageHeaderTest extends MessageHeaderTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static ClientSession createMockSession(ClientMessage clientMessage) {
      ClientSession session = Mockito.mock(ClientSession.class);
      Mockito.when(session.createMessage(
              Mockito.anyByte(),
              Mockito.anyBoolean(),
              Mockito.anyLong(),
              Mockito.anyLong(),
              Mockito.anyByte()))
          .thenReturn(clientMessage);
      return session;
   }

   @Test
   public void testClearMessage() throws Exception {
      queueProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      Message message = queueProducerSession.createTextMessage("some message");

      queueProducer.send(message);

      message = queueConsumer.receive(1000);

      ProxyAssertSupport.assertNotNull(message);

      message.clearProperties();

      ProxyAssertSupport.assertNotNull(message.getJMSDestination());

   }

   @Test
   public void testMessageOrderQueue() throws Exception {
      final int NUM_MESSAGES = 10;

      queueProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = queueProducerSession.createMessage();
         m.setIntProperty("count", i);
         queueProducer.send(m);
      }

      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = queueConsumer.receive(3000);
         ProxyAssertSupport.assertNotNull(m);
         int count = m.getIntProperty("count");
         ProxyAssertSupport.assertEquals(i, count);
      }

      queueProducer.setDeliveryMode(DeliveryMode.PERSISTENT);
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = queueProducerSession.createMessage();
         m.setIntProperty("count2", i);
         queueProducer.send(m);
      }

      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = queueConsumer.receive(3000);
         ProxyAssertSupport.assertNotNull(m);
         int count = m.getIntProperty("count2");
         ProxyAssertSupport.assertEquals(i, count);
      }
   }

   @Test
   public void testMessageOrderTopic() throws Exception {
      final int NUM_MESSAGES = 10;

      topicProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = topicProducerSession.createMessage();
         m.setIntProperty("count", i);
         topicProducer.send(m);
      }

      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = topicConsumer.receive(3000);
         ProxyAssertSupport.assertNotNull(m);
         int count = m.getIntProperty("count");
         ProxyAssertSupport.assertEquals(i, count);
      }

      topicProducer.setDeliveryMode(DeliveryMode.PERSISTENT);
      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = topicProducerSession.createMessage();
         m.setIntProperty("count2", i);
         topicProducer.send(m);
      }

      for (int i = 0; i < NUM_MESSAGES; i++) {
         Message m = topicConsumer.receive(3000);
         ProxyAssertSupport.assertNotNull(m);
         int count = m.getIntProperty("count2");
         ProxyAssertSupport.assertEquals(i, count);
      }
   }

   @Test
   public void testProperties() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      // Some arbitrary values
      boolean myBool = true;
      byte myByte = 13;
      short myShort = 15321;
      int myInt = 0x71ab6c80;
      long myLong = 0x20bf1e3fb6fa31dfL;
      float myFloat = Float.MAX_VALUE - 23465;
      double myDouble = Double.MAX_VALUE - 72387633;
      String myString = "abcdef&^*&!^ghijkl";

      m1.setBooleanProperty("myBool", myBool);
      m1.setByteProperty("myByte", myByte);
      m1.setShortProperty("myShort", myShort);
      m1.setIntProperty("myInt", myInt);
      m1.setLongProperty("myLong", myLong);
      m1.setFloatProperty("myFloat", myFloat);
      m1.setDoubleProperty("myDouble", myDouble);
      m1.setStringProperty("myString", myString);

      m1.setObjectProperty("myBool", myBool);
      m1.setObjectProperty("myByte", myByte);
      m1.setObjectProperty("myShort", myShort);
      m1.setObjectProperty("myInt", myInt);
      m1.setObjectProperty("myLong", myLong);
      m1.setObjectProperty("myFloat", myFloat);
      m1.setObjectProperty("myDouble", myDouble);
      m1.setObjectProperty("myString", myString);

      try {
         m1.setObjectProperty("myIllegal", new Object());
         ProxyAssertSupport.fail();
      } catch (javax.jms.MessageFormatException e) {
      }

      queueProducer.send(m1);

      Message m2 = queueConsumer.receive(2000);

      ProxyAssertSupport.assertNotNull(m2);

      ProxyAssertSupport.assertEquals(myBool, m2.getBooleanProperty("myBool"));
      ProxyAssertSupport.assertEquals(myByte, m2.getByteProperty("myByte"));
      ProxyAssertSupport.assertEquals(myShort, m2.getShortProperty("myShort"));
      ProxyAssertSupport.assertEquals(myInt, m2.getIntProperty("myInt"));
      ProxyAssertSupport.assertEquals(myLong, m2.getLongProperty("myLong"));
      ProxyAssertSupport.assertEquals(myFloat, m2.getFloatProperty("myFloat"), 0);
      ProxyAssertSupport.assertEquals(myDouble, m2.getDoubleProperty("myDouble"), 0);
      ProxyAssertSupport.assertEquals(myString, m2.getStringProperty("myString"));

      // Properties should now be read-only
      try {
         m2.setBooleanProperty("myBool", myBool);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setByteProperty("myByte", myByte);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setShortProperty("myShort", myShort);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setIntProperty("myInt", myInt);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setLongProperty("myLong", myLong);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setFloatProperty("myFloat", myFloat);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setDoubleProperty("myDouble", myDouble);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      try {
         m2.setStringProperty("myString", myString);
         ProxyAssertSupport.fail();
      } catch (MessageNotWriteableException e) {
      }

      ProxyAssertSupport.assertTrue(m2.propertyExists("myBool"));
      ProxyAssertSupport.assertTrue(m2.propertyExists("myByte"));
      ProxyAssertSupport.assertTrue(m2.propertyExists("myShort"));
      ProxyAssertSupport.assertTrue(m2.propertyExists("myInt"));
      ProxyAssertSupport.assertTrue(m2.propertyExists("myLong"));
      ProxyAssertSupport.assertTrue(m2.propertyExists("myFloat"));
      ProxyAssertSupport.assertTrue(m2.propertyExists("myDouble"));
      ProxyAssertSupport.assertTrue(m2.propertyExists("myString"));

      ProxyAssertSupport.assertFalse(m2.propertyExists("sausages"));

      Set<String> propNames = new HashSet<>();
      Enumeration en = m2.getPropertyNames();
      while (en.hasMoreElements()) {
         String propName = (String) en.nextElement();

         propNames.add(propName);
      }

      ProxyAssertSupport.assertTrue(propNames.size() >= 9);

      ProxyAssertSupport.assertTrue(propNames.contains("myBool"));
      ProxyAssertSupport.assertTrue(propNames.contains("myByte"));
      ProxyAssertSupport.assertTrue(propNames.contains("myShort"));
      ProxyAssertSupport.assertTrue(propNames.contains("myInt"));
      ProxyAssertSupport.assertTrue(propNames.contains("myLong"));
      ProxyAssertSupport.assertTrue(propNames.contains("myFloat"));
      ProxyAssertSupport.assertTrue(propNames.contains("myDouble"));
      ProxyAssertSupport.assertTrue(propNames.contains("myString"));

      // Check property conversions

      // Boolean property can be read as String but not anything else

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

      // byte property can be read as short, int, long or String

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

      // short property can be read as int, long or String

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

      // int property can be read as long or String

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

      // long property can be read as String

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

      // float property can be read as double or String

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

      // double property can be read as String

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

      m2.clearProperties();

      Enumeration en2 = m2.getPropertyNames();
      ProxyAssertSupport.assertTrue(en2.hasMoreElements());
      en2.nextElement();
      ProxyAssertSupport.assertFalse(en2.hasMoreElements());

      // Test String -> Numeric and bool conversions
      Message m3 = queueProducerSession.createMessage();

      m3.setStringProperty("myBool", String.valueOf(myBool));
      m3.setStringProperty("myByte", String.valueOf(myByte));
      m3.setStringProperty("myShort", String.valueOf(myShort));
      m3.setStringProperty("myInt", String.valueOf(myInt));
      m3.setStringProperty("myLong", String.valueOf(myLong));
      m3.setStringProperty("myFloat", String.valueOf(myFloat));
      m3.setStringProperty("myDouble", String.valueOf(myDouble));
      m3.setStringProperty("myIllegal", "xyz123");

      ProxyAssertSupport.assertEquals(myBool, m3.getBooleanProperty("myBool"));
      ProxyAssertSupport.assertEquals(myByte, m3.getByteProperty("myByte"));
      ProxyAssertSupport.assertEquals(myShort, m3.getShortProperty("myShort"));
      ProxyAssertSupport.assertEquals(myInt, m3.getIntProperty("myInt"));
      ProxyAssertSupport.assertEquals(myLong, m3.getLongProperty("myLong"));
      ProxyAssertSupport.assertEquals(myFloat, m3.getFloatProperty("myFloat"), 0);
      ProxyAssertSupport.assertEquals(myDouble, m3.getDoubleProperty("myDouble"), 0);

      m3.getBooleanProperty("myIllegal");

      try {
         m3.getByteProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getShortProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getIntProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getLongProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getFloatProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
      try {
         m3.getDoubleProperty("myIllegal");
         ProxyAssertSupport.fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testSendReceiveForeignMessage() throws JMSException {

      logger.trace("Starting da test");

      SimpleJMSMessage foreignMessage = new SimpleJMSMessage();

      foreignMessage.setStringProperty("animal", "aardvark");

      // foreign messages don't have to be serializable
      ProxyAssertSupport.assertFalse(foreignMessage instanceof Serializable);

      logger.trace("Sending message");

      queueProducer.send(foreignMessage);

      logger.trace("Sent message");

      Message m2 = queueConsumer.receive(3000);
      logger.trace("The message is {}", m2);

      ProxyAssertSupport.assertNotNull(m2);

      ProxyAssertSupport.assertEquals("aardvark", m2.getStringProperty("animal"));

      logger.trace("Received message");

      logger.trace("Done that test");
   }

   @Test
   public void testCopyOnJBossMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = createMockSession(clientMessage);
      ActiveMQMessage jbossMessage = ActiveMQMessage.createMessage(clientMessage, session);
      jbossMessage.clearProperties();

      MessageHeaderTestBase.configureMessage(jbossMessage);

      ActiveMQMessage copy = new ActiveMQMessage(jbossMessage, session);

      MessageHeaderTestBase.ensureEquivalent(jbossMessage, copy);
   }

   @Test
   public void testCopyOnForeignMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = createMockSession(clientMessage);

      Message foreignMessage = new SimpleJMSMessage();

      ActiveMQMessage copy = new ActiveMQMessage(foreignMessage, session);

      MessageHeaderTestBase.ensureEquivalent(foreignMessage, copy);

   }

   @Test
   public void testCopyOnForeignBytesMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = createMockSession(clientMessage);

      BytesMessage foreignBytesMessage = new SimpleJMSBytesMessage();
      for (int i = 0; i < 20; i++) {
         foreignBytesMessage.writeByte((byte) i);
      }

      ActiveMQBytesMessage copy = new ActiveMQBytesMessage(foreignBytesMessage, session);

      foreignBytesMessage.reset();
      copy.reset();

      MessageHeaderTestBase.ensureEquivalent(foreignBytesMessage, copy);
   }

   @Test
   public void testCopyOnForeignMapMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = createMockSession(clientMessage);

      MapMessage foreignMapMessage = new SimpleJMSMapMessage();
      foreignMapMessage.setInt("int", 1);
      foreignMapMessage.setString("string", "test");

      ActiveMQMapMessage copy = new ActiveMQMapMessage(foreignMapMessage, session);

      MessageHeaderTestBase.ensureEquivalent(foreignMapMessage, copy);
   }

   @Test
   public void testCopyOnForeignObjectMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = createMockSession(clientMessage);

      ObjectMessage foreignObjectMessage = new SimpleJMSObjectMessage();

      ActiveMQObjectMessage copy = new ActiveMQObjectMessage(foreignObjectMessage, session, null);

      MessageHeaderTestBase.ensureEquivalent(foreignObjectMessage, copy);
   }

   @Test
   public void testCopyOnForeignStreamMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = createMockSession(clientMessage);

      StreamMessage foreignStreamMessage = new SimpleJMSStreamMessage();
      foreignStreamMessage.writeByte((byte) 1);
      foreignStreamMessage.writeByte((byte) 2);
      foreignStreamMessage.writeByte((byte) 3);

      ActiveMQStreamMessage copy = new ActiveMQStreamMessage(foreignStreamMessage, session);

      MessageHeaderTestBase.ensureEquivalent(foreignStreamMessage, copy);
   }

   @Test
   public void testCopyOnForeignTextMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = createMockSession(clientMessage);

      TextMessage foreignTextMessage = new SimpleJMSTextMessage();

      ActiveMQTextMessage copy = new ActiveMQTextMessage(foreignTextMessage, session);

      MessageHeaderTestBase.ensureEquivalent(foreignTextMessage, copy);
   }

   @Test
   public void testForeignJMSDestination() throws JMSException {
      Message message = queueProducerSession.createMessage();

      Destination foreignDestination = new ForeignDestination();

      message.setJMSDestination(foreignDestination);

      ProxyAssertSupport.assertSame(foreignDestination, message.getJMSDestination());

      queueProducer.send(message);

      ProxyAssertSupport.assertSame(queue1, message.getJMSDestination());

      Message receivedMessage = queueConsumer.receive(2000);

      MessageHeaderTestBase.ensureEquivalent(receivedMessage, (ActiveMQMessage) message);
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();
   }

   private static class ForeignDestination implements Destination, Serializable {

      private static final long serialVersionUID = 5545509674580823610L;

      // A ForeignDestination equals any other ForeignDestination, for simplicity
      @Override
      public boolean equals(final Object obj) {
         return obj instanceof ForeignDestination;
      }

      @Override
      public int hashCode() {
         return 157;
      }
   }

}
