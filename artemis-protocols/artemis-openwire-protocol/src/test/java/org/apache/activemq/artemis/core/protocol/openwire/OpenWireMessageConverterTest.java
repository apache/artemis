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
package org.apache.activemq.artemis.core.protocol.openwire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.apache.activemq.ActiveMQMessageAuditNoSync;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQConsumer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.MessageReferenceImpl;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.MessageId;
import org.apache.activemq.command.ProducerId;
import org.apache.activemq.openwire.OpenWireFormatFactory;
import org.apache.activemq.util.ByteSequence;
import org.apache.activemq.wireformat.WireFormat;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class OpenWireMessageConverterTest {

   final OpenWireFormatFactory formatFactory = new OpenWireFormatFactory();
   final WireFormat openWireFormat = formatFactory.createWireFormat();
   final byte[] content = new byte[] {'a', 'a'};
   final String address = "Q";
   final ActiveMQDestination destination = new ActiveMQQueue(address);
   final UUID nodeUUID = UUIDGenerator.getInstance().generateUUID();

   @Test
   public void createMessageDispatch() throws Exception {

      ActiveMQMessageAuditNoSync mqMessageAuditNoSync = new ActiveMQMessageAuditNoSync();

      for (int i = 0; i < 10; i++) {

         ICoreMessage msg = new CoreMessage().initBuffer(100);
         msg.setMessageID(i);
         msg.getBodyBuffer().writeBytes(content);
         msg.setAddress(address);

         MessageReference messageReference = new MessageReferenceImpl(msg, Mockito.mock(Queue.class));
         AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
         Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);

         MessageDispatch dispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, msg, openWireFormat, amqConsumer, nodeUUID, i);

         MessageId messageId = dispatch.getMessage().getMessageId();
         assertFalse(mqMessageAuditNoSync.isDuplicate(messageId));
      }


      for (int i = 10; i < 20; i++) {

         CoreMessage msg = new CoreMessage().initBuffer(100);
         msg.setMessageID(i);
         msg.getBodyBuffer().writeBytes(content);
         msg.setAddress(address);

         // share a connection id
         msg.getProperties().putProperty(MessageUtil.CONNECTION_ID_PROPERTY_NAME, "MyClient");


         MessageReference messageReference = new MessageReferenceImpl(msg, Mockito.mock(Queue.class));
         AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
         Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);

         MessageDispatch dispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, msg, openWireFormat, amqConsumer, nodeUUID, i);

         MessageId messageId = dispatch.getMessage().getMessageId();
         assertFalse(mqMessageAuditNoSync.isDuplicate(messageId));
      }

   }

   @Test
   public void testBytesPropertyConversionToString() throws Exception {
      final String bytesPropertyKey = "bytesProperty";

      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.putBytesProperty(bytesPropertyKey, "TEST".getBytes());

      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);

      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);

      assertInstanceOf(String.class, messageDispatch.getMessage().getProperty(bytesPropertyKey));
   }

   @Test
   public void testProperties() throws Exception {
      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      for (int i = 0; i < 5; i++) {
         coreMessage.putIntProperty(i + "", i);
      }

      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);

      MessageDispatch marshalled = (MessageDispatch) openWireFormat.unmarshal(openWireFormat.marshal(OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0)));
      assertEquals(5, marshalled.getMessage().getProperties().keySet().size());
      Message converted = OpenWireMessageConverter.inbound(marshalled.getMessage(), openWireFormat, null);
      for (int i = 0; i < 5; i++) {
         assertTrue(converted.containsProperty(i + ""));
      }
   }

   @Test
   public void testEmptyMapMessage() throws Exception {
      CoreMessage artemisMessage = (CoreMessage) OpenWireMessageConverter.inbound(new ActiveMQMapMessage().getMessage(), openWireFormat, null);
      assertEquals(Message.MAP_TYPE, artemisMessage.getType());
      ActiveMQBuffer buffer = artemisMessage.getDataBuffer();
      TypedProperties map = new TypedProperties();
      buffer.resetReaderIndex();
      map.decode(buffer.byteBuf());
   }

   @Test
   public void testProducerId() throws Exception {
      final String PRODUCER_ID = "123:456:789";

      ActiveMQMessage classicMessage = new ActiveMQMessage();
      classicMessage.setProducerId(new ProducerId(PRODUCER_ID));
      classicMessage.setMessageId(new MessageId("1:1:1"));
      Message artemisMessage = OpenWireMessageConverter.inbound(classicMessage.getMessage(), openWireFormat, null);
      assertEquals(PRODUCER_ID, artemisMessage.getStringProperty(OpenWireConstants.AMQ_MSG_PRODUCER_ID));

      MessageReference messageReference = new MessageReferenceImpl(artemisMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch classicMessageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, (ICoreMessage) artemisMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertEquals(PRODUCER_ID, classicMessageDispatch.getMessage().getProducerId().toString());
   }

   @Test
   public void testLegacyProducerId() throws Exception {
      final String PRODUCER_ID = "123:456:789";

      ActiveMQMessage classicMessage = new ActiveMQMessage();
      classicMessage.setProducerId(new ProducerId(PRODUCER_ID));
      final ByteSequence pidBytes = openWireFormat.marshal(classicMessage.getProducerId());
      pidBytes.compact();
      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.putBytesProperty(OpenWireConstants.AMQ_MSG_PRODUCER_ID, pidBytes.data);
      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertEquals(PRODUCER_ID, messageDispatch.getMessage().getProducerId().toString());
   }

   @Test
   public void testStringCorrelationId() throws Exception {
      final String CORRELATION_ID = RandomUtil.randomUUIDString();

      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.setCorrelationID(CORRELATION_ID);
      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertEquals(CORRELATION_ID, messageDispatch.getMessage().getCorrelationId());
   }

   @Test
   public void testBytesCorrelationId() throws Exception {
      final byte[] CORRELATION_ID = RandomUtil.randomUUIDString().getBytes(StandardCharsets.UTF_8);

      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.setCorrelationID(CORRELATION_ID);
      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertEquals(new String(CORRELATION_ID, StandardCharsets.UTF_8), messageDispatch.getMessage().getCorrelationId());
   }

   @Test
   public void testInvalidUtf8BytesCorrelationId() throws Exception {
      final byte[] CORRELATION_ID = new byte[]{1, (byte)0xFF, (byte)0xFF};

      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.setCorrelationID(CORRELATION_ID);
      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertNull(messageDispatch.getMessage().getCorrelationId());
   }

   @Test
   public void testLegacyCorrelationId() throws Exception {
      final String CORRELATION_ID = RandomUtil.randomUUIDString();

      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.putStringProperty(OpenWireConstants.JMS_CORRELATION_ID_PROPERTY, SimpleString.of(CORRELATION_ID));
      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertEquals(CORRELATION_ID, messageDispatch.getMessage().getCorrelationId());
   }

   @Test
   public void testMessageId() throws Exception {
      final String MESSAGE_ID = "ID:123:456:789";

      ActiveMQMessage classicMessage = new ActiveMQMessage();
      classicMessage.setMessageId(new MessageId(MESSAGE_ID));
      Message artemisMessage = OpenWireMessageConverter.inbound(classicMessage.getMessage(), openWireFormat, null);
      assertEquals(MESSAGE_ID, artemisMessage.getStringProperty(OpenWireConstants.AMQ_MSG_MESSAGE_ID));

      MessageReference messageReference = new MessageReferenceImpl(artemisMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch classicMessageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, (ICoreMessage) artemisMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertEquals(MESSAGE_ID, classicMessageDispatch.getMessage().getMessageId().toString());
   }

   @Test
   public void testLegacyMessageId() throws Exception {
      final String MESSAGE_ID = "ID:123:456:789";

      ActiveMQMessage classicMessage = new ActiveMQMessage();
      classicMessage.setMessageId(new MessageId(MESSAGE_ID));
      final ByteSequence midBytes = openWireFormat.marshal(classicMessage.getMessageId());
      midBytes.compact();
      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.putBytesProperty(OpenWireConstants.AMQ_MSG_MESSAGE_ID, midBytes.data);
      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertEquals(MESSAGE_ID, messageDispatch.getMessage().getMessageId().toString());
   }

   @Test
   public void testLegacyOriginalDestination() throws Exception {
      final String DESTINATION = RandomUtil.randomUUIDString().replace("-", "");

      ActiveMQMessage classicMessage = new ActiveMQMessage();
      classicMessage.setOriginalDestination(new ActiveMQQueue(DESTINATION));
      final ByteSequence destBytes = openWireFormat.marshal(classicMessage.getOriginalDestination());
      destBytes.compact();
      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.putBytesProperty(OpenWireConstants.AMQ_MSG_ORIG_DESTINATION, destBytes.data);
      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertEquals("queue://" + DESTINATION, messageDispatch.getMessage().getOriginalDestination().toString());
   }

   @Test
   public void testLegacyReplyTo() throws Exception {
      final String DESTINATION = RandomUtil.randomUUIDString().replace("-", "");

      ActiveMQMessage classicMessage = new ActiveMQMessage();
      classicMessage.setJMSReplyTo(new ActiveMQQueue(DESTINATION));
      final ByteSequence destBytes = openWireFormat.marshal(classicMessage.getJMSReplyTo());
      destBytes.compact();
      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.putBytesProperty(OpenWireConstants.AMQ_MSG_REPLY_TO, destBytes.data);
      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);
      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);
      assertEquals("queue://" + DESTINATION, messageDispatch.getMessage().getReplyTo().toString());
   }

   @Test
   public void testBadPropertyConversion() throws Exception {
      final String hdrArrival = "__HDR_ARRIVAL";
      final String hdrBrokerInTime = "__HDR_BROKER_IN_TIME";
      final String hdrCommandId = "__HDR_COMMAND_ID";
      final String hdrDroppable = "__HDR_DROPPABLE";

      ICoreMessage coreMessage = new CoreMessage().initBuffer(8);
      coreMessage.putStringProperty(hdrArrival, "1234");
      coreMessage.putStringProperty(hdrBrokerInTime, "5678");
      coreMessage.putStringProperty(hdrCommandId, "foo");
      coreMessage.putStringProperty(hdrDroppable, "true");

      // this has triggered a java.lang.IllegalArgumentException during conversion in the past
      coreMessage.putStringProperty("", "bar");

      MessageReference messageReference = new MessageReferenceImpl(coreMessage, Mockito.mock(Queue.class));
      AMQConsumer amqConsumer = Mockito.mock(AMQConsumer.class);
      Mockito.when(amqConsumer.getOpenwireDestination()).thenReturn(destination);

      MessageDispatch messageDispatch = OpenWireMessageConverter.createMessageDispatch(messageReference, coreMessage, openWireFormat, amqConsumer, nodeUUID, 0);

      assertNull(messageDispatch.getMessage().getProperty(hdrArrival));
      assertNull(messageDispatch.getMessage().getProperty(hdrBrokerInTime));
      assertNull(messageDispatch.getMessage().getProperty(hdrCommandId));
      assertNull(messageDispatch.getMessage().getProperty(hdrDroppable));
      assertNull(messageDispatch.getMessage().getProperty(""));
   }
}
