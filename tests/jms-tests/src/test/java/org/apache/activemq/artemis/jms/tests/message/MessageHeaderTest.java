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
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.File;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueAttributes;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMapMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQObjectMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQStreamMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.Test;

public class MessageHeaderTest extends MessageHeaderTestBase {



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

      m1.setObjectProperty("myBool", Boolean.valueOf(myBool));
      m1.setObjectProperty("myByte", Byte.valueOf(myByte));
      m1.setObjectProperty("myShort", Short.valueOf(myShort));
      m1.setObjectProperty("myInt", Integer.valueOf(myInt));
      m1.setObjectProperty("myLong", Long.valueOf(myLong));
      m1.setObjectProperty("myFloat", Float.valueOf(myFloat));
      m1.setObjectProperty("myDouble", Double.valueOf(myDouble));
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

      log.trace("Starting da test");

      SimpleJMSMessage foreignMessage = new SimpleJMSMessage();

      foreignMessage.setStringProperty("animal", "aardvark");

      // foreign messages don't have to be serializable
      ProxyAssertSupport.assertFalse(foreignMessage instanceof Serializable);

      log.trace("Sending message");

      queueProducer.send(foreignMessage);

      log.trace("Sent message");

      Message m2 = queueConsumer.receive(3000);
      log.trace("The message is " + m2);

      ProxyAssertSupport.assertNotNull(m2);

      ProxyAssertSupport.assertEquals("aardvark", m2.getStringProperty("animal"));

      log.trace("Received message");

      log.trace("Done that test");
   }

   @Test
   public void testCopyOnJBossMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = new FakeSession(clientMessage);
      ActiveMQMessage jbossMessage = ActiveMQMessage.createMessage(clientMessage, session);
      jbossMessage.clearProperties();

      MessageHeaderTestBase.configureMessage(jbossMessage);

      ActiveMQMessage copy = new ActiveMQMessage(jbossMessage, session);

      MessageHeaderTestBase.ensureEquivalent(jbossMessage, copy);
   }

   @Test
   public void testCopyOnForeignMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = new FakeSession(clientMessage);

      Message foreignMessage = new SimpleJMSMessage();

      ActiveMQMessage copy = new ActiveMQMessage(foreignMessage, session);

      MessageHeaderTestBase.ensureEquivalent(foreignMessage, copy);

   }

   @Test
   public void testCopyOnForeignBytesMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = new FakeSession(clientMessage);

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
      ClientSession session = new FakeSession(clientMessage);
      MapMessage foreignMapMessage = new SimpleJMSMapMessage();
      foreignMapMessage.setInt("int", 1);
      foreignMapMessage.setString("string", "test");

      ActiveMQMapMessage copy = new ActiveMQMapMessage(foreignMapMessage, session);

      MessageHeaderTestBase.ensureEquivalent(foreignMapMessage, copy);
   }

   @Test
   public void testCopyOnForeignObjectMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = new FakeSession(clientMessage);

      ObjectMessage foreignObjectMessage = new SimpleJMSObjectMessage();

      ActiveMQObjectMessage copy = new ActiveMQObjectMessage(foreignObjectMessage, session, null);

      MessageHeaderTestBase.ensureEquivalent(foreignObjectMessage, copy);
   }

   @Test
   public void testCopyOnForeignStreamMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQTextMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      ClientSession session = new FakeSession(clientMessage);

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
      ClientSession session = new FakeSession(clientMessage);
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

   class FakeSession implements ClientSession {

      @Override
      public ClientConsumer createConsumer(final SimpleString queueName,
                                           final boolean browseOnly) throws ActiveMQException {
         // TODO Auto-generated method stub
         return null;
      }

      @Override
      public ClientConsumer createConsumer(final String queueName, final boolean browseOnly) throws ActiveMQException {
         // TODO Auto-generated method stub
         return null;
      }

      @Override
      public void createQueue(final String address, final String queueName) throws ActiveMQException {
         // TODO Auto-generated method stub

      }

      private final ClientMessage message;

      FakeSession(final ClientMessage message) {
         this.message = message;
      }

      @Override
      public void createQueue(final SimpleString address,
                              final SimpleString queueName,
                              final SimpleString filterString,
                              final boolean durable) throws ActiveMQException {
      }

      @Override
      public void createQueue(final SimpleString address,
                              final SimpleString queueName,
                              final boolean durable) throws ActiveMQException {
      }

      @Override
      public void createSharedQueue(SimpleString address,
                                    SimpleString queueName,
                                    boolean durable) throws ActiveMQException {
      }

      @Override
      public void createSharedQueue(SimpleString address,
                                    SimpleString queueName,
                                    SimpleString filter,
                                    boolean durable) throws ActiveMQException {
      }

      @Override
      public void createQueue(final String address,
                              final String queueName,
                              final boolean durable) throws ActiveMQException {
      }

      @Override
      public void createQueue(final String address,
                              final String queueName,
                              final String filterString,
                              final boolean durable) throws ActiveMQException {
      }

      @Override
      public void createQueue(SimpleString address,
                              SimpleString queueName,
                              SimpleString filter,
                              boolean durable,
                              boolean autoCreated) throws ActiveMQException {

      }

      @Override
      public void createQueue(String address,
                              String queueName,
                              String filter,
                              boolean durable,
                              boolean autoCreated) throws ActiveMQException {

      }

      @Override
      public void createTemporaryQueue(final SimpleString address,
                                       final SimpleString queueName) throws ActiveMQException {
      }

      @Override
      public void createTemporaryQueue(final String address, final String queueName) throws ActiveMQException {
      }

      @Override
      public void createTemporaryQueue(final SimpleString address,
                                       final SimpleString queueName,
                                       final SimpleString filter) throws ActiveMQException {
      }

      @Override
      public void createTemporaryQueue(final String address,
                                       final String queueName,
                                       final String filter) throws ActiveMQException {
      }

      /**
       * Creates a <em>non-temporary</em> queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param durable     whether the queue is durable or not
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, boolean durable) throws ActiveMQException {

      }

      /**
       * Creates a transient queue. A queue that will exist as long as there are consumers. When the last consumer is closed the queue will be deleted
       * <p>
       * Notice: you will get an exception if the address or the filter doesn't match to an already existent queue
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param durable     if the queue is durable
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString queueName, boolean durable) throws ActiveMQException {

      }

      /**
       * Creates a transient queue. A queue that will exist as long as there are consumers. When the last consumer is closed the queue will be deleted
       * <p>
       * Notice: you will get an exception if the address or the filter doesn't match to an already existent queue
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param filter      whether the queue is durable or not
       * @param durable     if the queue is durable
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                                    boolean durable) throws ActiveMQException {

      }

      @Override
      public void createSharedQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                                    boolean durable, Integer maxConsumers, Boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue) throws ActiveMQException {

      }

      @Override
      public void createSharedQueue(SimpleString address, SimpleString queueName, QueueAttributes queueAttributes) throws ActiveMQException {

      }

      /**
       * Creates a <em>non-temporary</em> queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param durable     whether the queue is durable or not
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createQueue(String address, RoutingType routingType, String queueName, boolean durable) throws ActiveMQException {

      }

      /**
       * Creates a <em>non-temporary</em> queue <em>non-durable</em> queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createQueue(String address, RoutingType routingType, String queueName) throws ActiveMQException {

      }

      /**
       * Creates a <em>non-temporary</em> queue <em>non-durable</em> queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName) throws ActiveMQException {

      }

      /**
       * Creates a <em>non-temporary</em> queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param filter      only messages which match this filter will be put in the queue
       * @param durable     whether the queue is durable or not
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                              boolean durable) throws ActiveMQException {

      }

      /**
       * Creates a <em>non-temporary</em>queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param filter      only messages which match this filter will be put in the queue
       * @param durable     whether the queue is durable or not
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createQueue(String address, RoutingType routingType, String queueName, String filter, boolean durable) throws ActiveMQException {

      }

      /**
       * Creates a <em>non-temporary</em> queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param filter      only messages which match this filter will be put in the queue
       * @param durable     whether the queue is durable or not
       * @param autoCreated whether to mark this queue as autoCreated or not
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                              boolean durable,
                              boolean autoCreated) throws ActiveMQException {

      }

      @Override
      public void createQueue(SimpleString address,
                              RoutingType routingType,
                              SimpleString queueName,
                              SimpleString filter,
                              boolean durable,
                              boolean autoCreated,
                              int maxConsumers,
                              boolean purgeOnNoConsumers) throws ActiveMQException {

      }

      @Override
      public void createQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter, boolean durable,
                              boolean autoCreated, int maxConsumers, boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue)
         throws ActiveMQException {

      }

      @Override
      public void createQueue(SimpleString address, SimpleString queueName, boolean autoCreated, QueueAttributes queueAttributes) throws ActiveMQException {

      }

      /**
       * Creates a <em>non-temporary</em>queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param filter      only messages which match this filter will be put in the queue
       * @param durable     whether the queue is durable or not
       * @param autoCreated whether to mark this queue as autoCreated or not
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createQueue(String address, RoutingType routingType, String queueName, String filter,
                              boolean durable,
                              boolean autoCreated) throws ActiveMQException {

      }

      @Override
      public void createQueue(String address,
                              RoutingType routingType,
                              String queueName,
                              String filter,
                              boolean durable,
                              boolean autoCreated,
                              int maxConsumers,
                              boolean purgeOnNoConsumers) throws ActiveMQException {

      }

      @Override
      public void createQueue(String address, RoutingType routingType, String queueName, String filter, boolean durable,
                              boolean autoCreated,
                              int maxConsumers, boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue) throws ActiveMQException {

      }

      /**
       * Creates a <em>temporary</em> queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createTemporaryQueue(SimpleString address, RoutingType routingType, SimpleString queueName) throws ActiveMQException {

      }

      /**
       * Creates a <em>temporary</em> queue.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createTemporaryQueue(String address, RoutingType routingType, String queueName) throws ActiveMQException {

      }

      @Override
      public void createTemporaryQueue(SimpleString address, RoutingType routingType, SimpleString queueName, SimpleString filter,
                                       int maxConsumers, boolean purgeOnNoConsumers, Boolean exclusive, Boolean lastValue)
         throws ActiveMQException {

      }

      @Override
      public void createTemporaryQueue(SimpleString address, SimpleString queueName, QueueAttributes queueAttributes) throws ActiveMQException {

      }

      /**
       * Creates a <em>temporary</em> queue with a filter.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param filter      only messages which match this filter will be put in the queue
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createTemporaryQueue(SimpleString address,
                                       RoutingType routingType,
                                       SimpleString queueName,
                                       SimpleString filter) throws ActiveMQException {

      }

      /**
       * Creates a <em>temporary</em> queue with a filter.
       *
       * @param address     the queue will be bound to this address
       * @param routingType the delivery mode for this queue, MULTICAST or ANYCAST
       * @param queueName   the name of the queue
       * @param filter      only messages which match this filter will be put in the queue
       * @throws ActiveMQException in an exception occurs while creating the queue
       */
      @Override
      public void createTemporaryQueue(String address, RoutingType routingType, String queueName, String filter) throws ActiveMQException {

      }

      @Override
      public void deleteQueue(final SimpleString queueName) throws ActiveMQException {
      }

      @Override
      public void deleteQueue(final String queueName) throws ActiveMQException {
      }

      @Override
      public ClientConsumer createConsumer(final SimpleString queueName) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientConsumer createConsumer(final SimpleString queueName,
                                           final SimpleString filterString) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientConsumer createConsumer(final SimpleString queueName,
                                           final SimpleString filterString,
                                           final boolean browseOnly) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientConsumer createConsumer(SimpleString queueName, SimpleString filter, int priority, boolean browseOnly) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientConsumer createConsumer(final SimpleString queueName,
                                           final SimpleString filterString,
                                           final int windowSize,
                                           final int maxRate,
                                           final boolean browseOnly) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientConsumer createConsumer(SimpleString queueName, SimpleString filter, int priority, int windowSize, int maxRate, boolean browseOnly) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientConsumer createConsumer(final String queueName) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientConsumer createConsumer(final String queueName, final String filterString) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientConsumer createConsumer(final String queueName,
                                           final String filterString,
                                           final boolean browseOnly) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientConsumer createConsumer(final String queueName,
                                           final String filterString,
                                           final int windowSize,
                                           final int maxRate,
                                           final boolean browseOnly) throws ActiveMQException {
         return null;
      }

      public ClientConsumer createFileConsumer(final File directory,
                                               final SimpleString queueName) throws ActiveMQException {
         return null;
      }

      public ClientConsumer createFileConsumer(final File directory,
                                               final SimpleString queueName,
                                               final SimpleString filterString) throws ActiveMQException {
         return null;
      }

      public ClientConsumer createFileConsumer(final File directory,
                                               final SimpleString queueName,
                                               final SimpleString filterString,
                                               final boolean browseOnly) throws ActiveMQException {
         return null;
      }

      public ClientConsumer createFileConsumer(final File directory,
                                               final SimpleString queueName,
                                               final SimpleString filterString,
                                               final int windowSize,
                                               final int maxRate,
                                               final boolean browseOnly) throws ActiveMQException {
         return null;
      }

      public ClientConsumer createFileConsumer(final File directory, final String queueName) throws ActiveMQException {
         return null;
      }

      public ClientConsumer createFileConsumer(final File directory,
                                               final String queueName,
                                               final String filterString) throws ActiveMQException {
         return null;
      }

      public ClientConsumer createFileConsumer(final File directory,
                                               final String queueName,
                                               final String filterString,
                                               final boolean browseOnly) throws ActiveMQException {
         return null;
      }

      public ClientConsumer createFileConsumer(final File directory,
                                               final String queueName,
                                               final String filterString,
                                               final int windowSize,
                                               final int maxRate,
                                               final boolean browseOnly) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientProducer createProducer() throws ActiveMQException {
         return null;
      }

      @Override
      public ClientProducer createProducer(final SimpleString address) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientProducer createProducer(final SimpleString address, final int rate) throws ActiveMQException {
         return null;
      }

      public ClientProducer createProducer(final SimpleString address,
                                           final int maxRate,
                                           final boolean blockOnNonDurableSend,
                                           final boolean blockOnDurableSend) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientProducer createProducer(final String address) throws ActiveMQException {
         return null;
      }

      public ClientProducer createProducer(final String address, final int rate) throws ActiveMQException {
         return null;
      }

      public ClientProducer createProducer(final String address,
                                           final int maxRate,
                                           final boolean blockOnNonDurableSend,
                                           final boolean blockOnDurableSend) throws ActiveMQException {
         return null;
      }

      @Override
      public QueueQuery queueQuery(final SimpleString queueName) throws ActiveMQException {
         return null;
      }

      @Override
      public AddressQuery addressQuery(final SimpleString address) throws ActiveMQException {
         return null;
      }

      @Override
      public XAResource getXAResource() {
         return null;
      }

      @Override
      public void commit() throws ActiveMQException {
      }

      @Override
      public void commit(boolean block) throws ActiveMQException {
      }

      @Override
      public boolean isRollbackOnly() {

         return false;
      }

      @Override
      public void rollback() throws ActiveMQException {
      }

      @Override
      public void rollback(final boolean considerLastMessageAsDelivered) throws ActiveMQException {
      }

      @Override
      public void close() throws ActiveMQException {
      }

      @Override
      public boolean isClosed() {
         return false;
      }

      @Override
      public boolean isAutoCommitSends() {
         return false;
      }

      @Override
      public boolean isAutoCommitAcks() {
         return false;
      }

      @Override
      public boolean isBlockOnAcknowledge() {
         return false;
      }

      @Override
      public boolean isXA() {
         return false;
      }

      @Override
      public ClientMessage createMessage(final byte type,
                                         final boolean durable,
                                         final long expiration,
                                         final long timestamp,
                                         final byte priority) {
         return message;
      }

      @Override
      public ClientMessage createMessage(final byte type, final boolean durable) {
         return message;
      }

      @Override
      public ClientMessage createMessage(final boolean durable) {
         return message;
      }

      @Override
      public FakeSession start() throws ActiveMQException {
         return this;
      }

      @Override
      public void stop() throws ActiveMQException {
      }

      public void addFailureListener(final FailureListener listener) {
      }

      @Override
      public void addFailoverListener(FailoverEventListener listener) {
      }

      public boolean removeFailureListener(final FailureListener listener) {
         return false;
      }

      @Override
      public boolean removeFailoverListener(FailoverEventListener listener) {
         return false;
      }

      @Override
      public int getVersion() {
         return 0;
      }

      @Override
      public void createAddress(SimpleString address, EnumSet<RoutingType> routingTypes, boolean autoCreated) throws ActiveMQException {
      }

      /**
       * Create Address with a single initial routing type
       *
       * @param address
       * @param routingTypes
       * @param autoCreated  @throws ActiveMQException
       */
      @Override
      public void createAddress(SimpleString address,
                                Set<RoutingType> routingTypes,
                                boolean autoCreated) throws ActiveMQException {

      }

      /**
       * Create Address with a single initial routing type
       *
       * @param address
       * @param routingType
       * @param autoCreated
       * @throws ActiveMQException
       */
      @Override
      public void createAddress(SimpleString address,
                                RoutingType routingType,
                                boolean autoCreated) throws ActiveMQException {

      }

      @Override
      public void createQueue(QueueConfiguration queueConfiguration) throws ActiveMQException {

      }

      @Override
      public void createSharedQueue(QueueConfiguration queueConfiguration) throws ActiveMQException {

      }

      @Override
      public FakeSession setSendAcknowledgementHandler(final SendAcknowledgementHandler handler) {
         return this;
      }

      @Override
      public void commit(final Xid xid, final boolean b) throws XAException {
      }

      @Override
      public void end(final Xid xid, final int i) throws XAException {
      }

      @Override
      public void forget(final Xid xid) throws XAException {
      }

      @Override
      public int getTransactionTimeout() throws XAException {
         return 0;
      }

      @Override
      public boolean isSameRM(final XAResource xaResource) throws XAException {
         return false;
      }

      @Override
      public int prepare(final Xid xid) throws XAException {
         return 0;
      }

      @Override
      public Xid[] recover(final int i) throws XAException {
         return new Xid[0];
      }

      @Override
      public void rollback(final Xid xid) throws XAException {
      }

      @Override
      public boolean setTransactionTimeout(final int i) throws XAException {
         return false;
      }

      @Override
      public void start(final Xid xid, final int i) throws XAException {
      }

      /* (non-Javadoc)
       * @see ClientSession#createTransportBuffer(byte[])
       */
      public ActiveMQBuffer createBuffer(final byte[] bytes) {
         // TODO Auto-generated method stub
         return null;
      }

      /* (non-Javadoc)
       * @see ClientSession#createTransportBuffer(int)
       */
      public ActiveMQBuffer createBuffer(final int size) {
         // TODO Auto-generated method stub
         return null;
      }

      @Override
      public void addFailureListener(final SessionFailureListener listener) {
         // TODO Auto-generated method stub

      }

      @Override
      public boolean removeFailureListener(final SessionFailureListener listener) {
         // TODO Auto-generated method stub
         return false;
      }

      /* (non-Javadoc)
       * @see ClientSession#createQueue(org.apache.activemq.artemis.utils.SimpleString, org.apache.activemq.artemis.utils.SimpleString)
       */
      @Override
      public void createQueue(SimpleString address, SimpleString queueName) throws ActiveMQException {
         // TODO Auto-generated method stub

      }

      /* (non-Javadoc)
       * @see ClientSession#setClientID(java.lang.String)
       */
      public void setClientID(String clientID) {
         // TODO Auto-generated method stub

      }

      /* (non-Javadoc)
       * @see ClientSession#addMetaData(java.lang.String, java.lang.String)
       */
      @Override
      public void addMetaData(String key, String data) throws ActiveMQException {
         // TODO Auto-generated method stub

      }

      /* (non-Javadoc)
       * @see ClientSession#addUniqueMetaData(java.lang.String, java.lang.String)
       */
      @Override
      public void addUniqueMetaData(String key, String data) throws ActiveMQException {
         // TODO Auto-generated method stub

      }

      @Override
      public ClientSessionFactory getSessionFactory() {
         return null;
      }
   }
}
