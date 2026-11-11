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
package org.apache.activemq.command;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;

public class MessageCompressionTest extends TestCase {

   private static final String BROKER_URL = "tcp://localhost:0";
   // The following text should compress well
   private static final String TEXT = "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. " + "The quick red fox jumped over the lazy brown dog. ";

   private BrokerService broker;
   private ActiveMQQueue queue;
   private String connectionUri;

   @Override
   protected void setUp() throws Exception {
      broker = new BrokerService();
      connectionUri = broker.addConnector(BROKER_URL).getPublishableConnectString();
      broker.start();
      queue = new ActiveMQQueue("TEST." + System.currentTimeMillis());
   }

   @Override
   protected void tearDown() throws Exception {
      if (broker != null) {
         broker.stop();
      }
   }

   public void testTextMessageCompression() throws Exception {

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(true);
      sendTestMessage(factory, TEXT);
      ActiveMQTextMessage message = receiveTestMessage(factory);
      int compressedSize = message.getContent().getLength();

      factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(false);
      sendTestMessage(factory, TEXT);
      message = receiveTestMessage(factory);
      int unCompressedSize = message.getContent().getLength();
      assertEquals(TEXT, message.getText());

      assertTrue("expected: compressed Size '" + compressedSize + "' < unCompressedSize '" + unCompressedSize + "'", compressedSize < unCompressedSize);
   }

   public void testBytesMessageCompression() throws Exception {

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(true);
      sendTestBytesMessage(factory, TEXT);
      ActiveMQBytesMessage message = receiveTestBytesMessage(factory);
      int compressedSize = message.getContent().getLength();
      byte[] bytes = new byte[TEXT.getBytes(StandardCharsets.UTF_8).length];
      message.readBytes(bytes);
      assertTrue(message.readBytes(new byte[255]) == -1);
      String rcvString = new String(bytes, StandardCharsets.UTF_8);
      assertEquals(TEXT, rcvString);
      assertTrue(message.isCompressed());

      factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(false);
      sendTestBytesMessage(factory, TEXT);
      message = receiveTestBytesMessage(factory);
      int unCompressedSize = message.getContent().getLength();

      assertTrue("expected: compressed Size '" + compressedSize + "' < unCompressedSize '" + unCompressedSize + "'", compressedSize < unCompressedSize);
   }

   public void testMapMessageCompression() throws Exception {

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(true);

      sendTestMapMessage(factory, TEXT);
      ActiveMQMapMessage mapMessage = receiveTestMapMessage(factory);
      int compressedSize = mapMessage.getContent().getLength();
      assertTrue(mapMessage.isCompressed());

      boolean booleanVal = mapMessage.getBoolean("boolean-type");
      assertTrue(booleanVal);
      byte byteVal = mapMessage.getByte("byte-type");
      assertEquals((byte) 10, byteVal);
      byte[] bytesVal = mapMessage.getBytes("bytes-type");
      byte[] originVal = TEXT.getBytes();
      assertEquals(originVal.length, bytesVal.length);
      for (int i = 0; i < bytesVal.length; i++) {
         assertTrue(bytesVal[i] == originVal[i]);
      }
      char charVal = mapMessage.getChar("char-type");
      assertEquals('A', charVal);
      double doubleVal = mapMessage.getDouble("double-type");
      assertEquals(55.3D, doubleVal, 0.1D);
      float floatVal = mapMessage.getFloat("float-type");
      assertEquals(79.1F, floatVal, 0.1F);
      int intVal = mapMessage.getInt("int-type");
      assertEquals(37, intVal);
      long longVal = mapMessage.getLong("long-type");
      assertEquals(56652L, longVal);
      Object objectVal = mapMessage.getObject("object-type");
      Object origVal = new String("VVVV");
      assertTrue(objectVal.equals(origVal));
      short shortVal = mapMessage.getShort("short-type");
      assertEquals((short) 333, shortVal);
      String strVal = mapMessage.getString("string-type");
      assertEquals(TEXT, strVal);

      factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(false);
      sendTestMapMessage(factory, TEXT);
      mapMessage = receiveTestMapMessage(factory);
      int unCompressedSize = mapMessage.getContent().getLength();

      assertTrue("expected: compressed Size '" + compressedSize + "' < unCompressedSize '" + unCompressedSize + "'", compressedSize < unCompressedSize);
   }

   public void testStreamMessageCompression() throws Exception {

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(true);

      sendTestStreamMessage(factory, TEXT);
      ActiveMQStreamMessage streamMessage = receiveTestStreamMessage(factory);
      int compressedSize = streamMessage.getContent().getLength();
      assertTrue(streamMessage.isCompressed());

      boolean booleanVal = streamMessage.readBoolean();
      assertTrue(booleanVal);
      byte byteVal = streamMessage.readByte();
      assertEquals((byte) 10, byteVal);
      byte[] originVal = TEXT.getBytes();
      byte[] bytesVal = new byte[originVal.length];
      streamMessage.readBytes(bytesVal);
      for (int i = 0; i < bytesVal.length; i++) {
         assertTrue(bytesVal[i] == originVal[i]);
      }
      char charVal = streamMessage.readChar();
      assertEquals('A', charVal);
      double doubleVal = streamMessage.readDouble();
      assertEquals(55.3D, doubleVal, 0.1D);
      float floatVal = streamMessage.readFloat();
      assertEquals(79.1F, floatVal, 0.1F);
      int intVal = streamMessage.readInt();
      assertEquals(37, intVal);
      long longVal = streamMessage.readLong();
      assertEquals(56652L, longVal);
      Object objectVal = streamMessage.readObject();
      Object origVal = new String("VVVV");
      assertTrue(objectVal.equals(origVal));
      short shortVal = streamMessage.readShort();
      assertEquals((short) 333, shortVal);
      String strVal = streamMessage.readString();
      assertEquals(TEXT, strVal);

      factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(false);
      sendTestStreamMessage(factory, TEXT);
      streamMessage = receiveTestStreamMessage(factory);
      int unCompressedSize = streamMessage.getContent().getLength();

      System.out.println("compressedSize: " + compressedSize + " un: " + unCompressedSize);
      assertTrue("expected: compressed Size '" + compressedSize + "' < unCompressedSize '" + unCompressedSize + "'", compressedSize < unCompressedSize);
   }

   public void testObjectMessageCompression() throws Exception {

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(true);

      sendTestObjectMessage(factory, TEXT);
      ActiveMQObjectMessage objectMessage = receiveTestObjectMessage(factory);
      int compressedSize = objectMessage.getContent().getLength();
      assertTrue(objectMessage.isCompressed());

      Object objectVal = objectMessage.getObject();
      assertEquals(TEXT, objectVal);

      factory = new ActiveMQConnectionFactory(connectionUri);
      factory.setUseCompression(false);
      sendTestObjectMessage(factory, TEXT);
      objectMessage = receiveTestObjectMessage(factory);
      int unCompressedSize = objectMessage.getContent().getLength();

      assertTrue("expected: compressed Size '" + compressedSize + "' < unCompressedSize '" + unCompressedSize + "'", compressedSize < unCompressedSize);
   }

   private void sendTestObjectMessage(ActiveMQConnectionFactory factory, String message) throws JMSException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(queue);
      ObjectMessage objectMessage = session.createObjectMessage();

      objectMessage.setObject(TEXT);

      producer.send(objectMessage);
      connection.close();
   }

   private ActiveMQObjectMessage receiveTestObjectMessage(ActiveMQConnectionFactory factory) throws JMSException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(queue);
      ActiveMQObjectMessage rc = (ActiveMQObjectMessage) consumer.receive();
      connection.close();
      return rc;
   }

   private void sendTestStreamMessage(ActiveMQConnectionFactory factory, String message) throws JMSException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(queue);
      StreamMessage streamMessage = session.createStreamMessage();

      streamMessage.writeBoolean(true);
      streamMessage.writeByte((byte) 10);
      streamMessage.writeBytes(TEXT.getBytes());
      streamMessage.writeChar('A');
      streamMessage.writeDouble(55.3D);
      streamMessage.writeFloat(79.1F);
      streamMessage.writeInt(37);
      streamMessage.writeLong(56652L);
      streamMessage.writeObject(new String("VVVV"));
      streamMessage.writeShort((short) 333);
      streamMessage.writeString(TEXT);

      producer.send(streamMessage);
      connection.close();
   }

   private ActiveMQStreamMessage receiveTestStreamMessage(ActiveMQConnectionFactory factory) throws JMSException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(queue);
      ActiveMQStreamMessage rc = (ActiveMQStreamMessage) consumer.receive();
      connection.close();
      return rc;
   }

   private void sendTestMapMessage(ActiveMQConnectionFactory factory, String message) throws JMSException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(queue);
      MapMessage mapMessage = session.createMapMessage();

      mapMessage.setBoolean("boolean-type", true);
      mapMessage.setByte("byte-type", (byte) 10);
      mapMessage.setBytes("bytes-type", TEXT.getBytes());
      mapMessage.setChar("char-type", 'A');
      mapMessage.setDouble("double-type", 55.3D);
      mapMessage.setFloat("float-type", 79.1F);
      mapMessage.setInt("int-type", 37);
      mapMessage.setLong("long-type", 56652L);
      mapMessage.setObject("object-type", new String("VVVV"));
      mapMessage.setShort("short-type", (short) 333);
      mapMessage.setString("string-type", TEXT);

      producer.send(mapMessage);
      connection.close();
   }

   private ActiveMQMapMessage receiveTestMapMessage(ActiveMQConnectionFactory factory) throws JMSException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(queue);
      ActiveMQMapMessage rc = (ActiveMQMapMessage) consumer.receive();
      connection.close();
      return rc;
   }

   private void sendTestMessage(ActiveMQConnectionFactory factory, String message) throws JMSException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage(message));
      connection.close();
   }

   private ActiveMQTextMessage receiveTestMessage(ActiveMQConnectionFactory factory) throws JMSException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(queue);
      ActiveMQTextMessage rc = (ActiveMQTextMessage) consumer.receive();
      connection.close();
      return rc;
   }

   private void sendTestBytesMessage(ActiveMQConnectionFactory factory,
                                     String message) throws JMSException, UnsupportedEncodingException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(queue);
      BytesMessage bytesMessage = session.createBytesMessage();
      bytesMessage.writeBytes(message.getBytes(StandardCharsets.UTF_8));
      producer.send(bytesMessage);
      connection.close();
   }

   private ActiveMQBytesMessage receiveTestBytesMessage(ActiveMQConnectionFactory factory) throws JMSException, UnsupportedEncodingException {
      ActiveMQConnection connection = (ActiveMQConnection) factory.createConnection();
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = session.createConsumer(queue);
      ActiveMQBytesMessage rc = (ActiveMQBytesMessage) consumer.receive();
      connection.close();
      return rc;
   }
}
