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
package org.apache.activemq.artemis.tests.integration.openwire.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.nio.charset.StandardCharsets;

import org.apache.activemq.ActiveMQMessageProducer;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CompressedInteropTest extends BasicOpenWireTest {

   private static final String TEXT;
   private static final String LARGE_TEXT;

   static {
      TEXT = "The quick red fox jumped over the lazy brown dog. ".repeat(20);
      LARGE_TEXT = TEXT.repeat(5);
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      connection.start();
      assertTrue(connection.isUseCompression());
   }


   @Override
   protected void createFactories() {
      super.createFactories();
      factory.setUseCompression(true);
      xaFactory.setUseCompression(true);
   }

   @Test
   public void testCoreReceiveOpenWireCompressedMessages() throws Exception {
      testCompressedMessageSendReceive(true);
   }

   @Test
   public void testOpenWireReceiveOpenWireCompressedMessages() throws Exception {
      testCompressedMessageSendReceive(false);
   }

   private void testCompressedMessageSendReceive(boolean useCore) throws Exception {
      //TextMessage
      sendCompressedTextMessageUsingOpenWire();
      receiveTextMessage(useCore);
      //BytesMessage
      sendCompressedBytesMessageUsingOpenWire();
      receiveBytesMessage(useCore);
      //MapMessage
      sendCompressedMapMessageUsingOpenWire();
      receiveMapMessage(useCore);
      //StreamMessage
      sendCompressedStreamMessageUsingOpenWire();
      receiveStreamMessage(useCore);
      //ObjectMessage
      sendCompressedObjectMessageUsingOpenWire();
      receiveObjectMessage(useCore);
      //Large ObjectMessage
      sendCompressedLargeObjectMessageUsingOpenWire();
      receiveLargeObjectMessage(useCore);
   }

   private void sendCompressedStreamMessageUsingOpenWire() throws Exception {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

      final ActiveMQMessageProducer producer = (ActiveMQMessageProducer) session.createProducer(destination);

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
   }

   private void receiveStreamMessage(boolean useCore) throws Exception {
      StreamMessage streamMessage = (StreamMessage) receiveMessage(useCore);
      boolean booleanVal = streamMessage.readBoolean();
      assertTrue(booleanVal);
      byte byteVal = streamMessage.readByte();
      assertEquals((byte) 10, byteVal);
      byte[] originVal = TEXT.getBytes();
      byte[] bytesVal = new byte[originVal.length];
      streamMessage.readBytes(bytesVal);
      for (int i = 0; i < bytesVal.length; i++) {
         assertEquals(originVal[i], bytesVal[i]);
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
      assertEquals(origVal, objectVal);
      short shortVal = streamMessage.readShort();
      assertEquals((short) 333, shortVal);
      String strVal = streamMessage.readString();
      assertEquals(TEXT, strVal);
   }

   private void sendCompressedObjectMessageUsingOpenWire() throws Exception {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

      final ActiveMQMessageProducer producer = (ActiveMQMessageProducer) session.createProducer(destination);

      ObjectMessage objectMessage = session.createObjectMessage();
      objectMessage.setObject(TEXT);

      producer.send(objectMessage);
   }

   private void receiveObjectMessage(boolean useCore) throws Exception {
      ObjectMessage objectMessage = (ObjectMessage) receiveMessage(useCore);
      Object objectVal = objectMessage.getObject();
      assertEquals(TEXT, objectVal);
   }

   private void sendCompressedLargeObjectMessageUsingOpenWire() throws Exception {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

      final ActiveMQMessageProducer producer = (ActiveMQMessageProducer) session.createProducer(destination);

      ObjectMessage objectMessage = session.createObjectMessage();
      objectMessage.setObject(LARGE_TEXT);

      producer.send(objectMessage);
   }

   private void receiveLargeObjectMessage(boolean useCore) throws Exception {
      ObjectMessage objectMessage = (ObjectMessage) receiveMessage(useCore);
      Object objectVal = objectMessage.getObject();
      assertEquals(LARGE_TEXT, objectVal);
   }

   private void sendCompressedMapMessageUsingOpenWire() throws Exception {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

      final ActiveMQMessageProducer producer = (ActiveMQMessageProducer) session.createProducer(destination);

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
   }

   private void receiveMapMessage(boolean useCore) throws Exception {
      MapMessage mapMessage = (MapMessage) receiveMessage(useCore);

      boolean booleanVal = mapMessage.getBoolean("boolean-type");
      assertTrue(booleanVal);
      byte byteVal = mapMessage.getByte("byte-type");
      assertEquals((byte) 10, byteVal);
      byte[] bytesVal = mapMessage.getBytes("bytes-type");
      byte[] originVal = TEXT.getBytes();
      assertEquals(originVal.length, bytesVal.length);
      for (int i = 0; i < bytesVal.length; i++) {
         assertEquals(originVal[i], bytesVal[i]);
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
      assertEquals(origVal, objectVal);
      short shortVal = mapMessage.getShort("short-type");
      assertEquals((short) 333, shortVal);
      String strVal = mapMessage.getString("string-type");
      assertEquals(TEXT, strVal);
   }

   private void sendCompressedBytesMessageUsingOpenWire() throws Exception {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

      final ActiveMQMessageProducer producer = (ActiveMQMessageProducer) session.createProducer(destination);

      BytesMessage bytesMessage = session.createBytesMessage();
      bytesMessage.writeBytes(TEXT.getBytes());

      producer.send(bytesMessage);
   }

   private void receiveBytesMessage(boolean useCore) throws Exception {
      BytesMessage bytesMessage = (BytesMessage) receiveMessage(useCore);

      byte[] bytes = new byte[TEXT.getBytes(StandardCharsets.UTF_8).length];
      bytesMessage.readBytes(bytes);
      assertEquals(-1, bytesMessage.readBytes(new byte[255]));

      String rcvString = new String(bytes, StandardCharsets.UTF_8);
      assertEquals(TEXT, rcvString);
   }

   private void receiveTextMessage(boolean useCore) throws Exception {
      TextMessage txtMessage = (TextMessage) receiveMessage(useCore);
      assertEquals(TEXT, txtMessage.getText());
   }

   private void sendCompressedTextMessageUsingOpenWire() throws Exception {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQDestination destination = createDestination(session, ActiveMQDestination.QUEUE_TYPE);

      final ActiveMQMessageProducer producer = (ActiveMQMessageProducer) session.createProducer(destination);

      TextMessage textMessage = session.createTextMessage(TEXT);

      producer.send(textMessage);
   }

   private Message receiveMessage(boolean useCore) throws Exception {
      ConnectionFactory factoryToUse = useCore ? coreCf : factory;
      Connection jmsConn = null;
      Message message = null;
      try {
         jmsConn = factoryToUse.createConnection();
         jmsConn.start();

         Session session = jmsConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(this.queueName);
         MessageConsumer coreConsumer = session.createConsumer(queue);

         message = coreConsumer.receive(5000);
      } finally {
         if (jmsConn != null) {
            jmsConn.close();
         }
      }
      return message;
   }
}
