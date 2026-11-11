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
package org.apache.activemq.artemis.protocol.amqp.converter.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.activemq.artemis.core.buffers.impl.ResetLimitWrappedActiveMQBuffer;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPConverter;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreBytesMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreMapMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreObjectMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreStreamMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreTextMessageWrapper;
import org.apache.activemq.artemis.utils.PrefixUtil;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_DATA;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_NULL;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_SEQUENCE;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_UNKNOWN;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_BINARY;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_VALUE_LIST;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ORIGINAL_ENCODING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class JMSMappingOutboundTransformerTest {

   private final UUID TEST_OBJECT_VALUE = UUID.fromString("fee14b62-09e0-4ac6-a4c3-4206c630d844");
   private final String TEST_ADDRESS = "queue://testAddress";

   public static final byte QUEUE_TYPE = 0x00;
   public static final byte TOPIC_TYPE = 0x01;
   public static final byte TEMP_QUEUE_TYPE = 0x02;
   public static final byte TEMP_TOPIC_TYPE = 0x03;

   // ----- no-body Message type tests ---------------------------------------//

   @Test
   public void testConvertMessageToAmqpMessageWithNoBody() throws Exception {
      CoreMessageWrapper outbound = createMessage();
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNull(amqp.getBody());
   }

   @Test
   public void testConvertTextMessageToAmqpMessageWithNoBodyOriginalEncodingWasNull() throws Exception {
      CoreMessageWrapper outbound = createMessage();
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_NULL);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNull(amqp.getBody());
   }

   // ----- BytesMessage type tests ---------------------------------------//

   @Test
   public void testConvertBytesMessageToAmqpMessageWithDataBody() throws Exception {
      byte[] expectedPayload = new byte[]{8, 16, 24, 32};
      CoreBytesMessageWrapper outbound = createBytesMessage();
      outbound.writeBytes(expectedPayload);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(Data.class, amqp.getBody());
      assertInstanceOf(Binary.class, ((Data) amqp.getBody()).getValue());
      assertEquals(4, ((Data) amqp.getBody()).getValue().getLength());

      Binary amqpData = ((Data) amqp.getBody()).getValue();
      Binary inputData = new Binary(expectedPayload);

      assertEquals(inputData, amqpData);
   }

   @Test
   public void testConvertEmptyBytesMessageToAmqpMessageWithAmqpValueBody() throws Exception {
      CoreBytesMessageWrapper outbound = createBytesMessage();
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_VALUE_BINARY);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertInstanceOf(Binary.class, ((AmqpValue) amqp.getBody()).getValue());
      assertEquals(0, ((Binary) ((AmqpValue) amqp.getBody()).getValue()).getLength());
   }

   @Test
   public void testConvertBytesMessageToAmqpMessageWithAmqpValueBody() throws Exception {
      byte[] expectedPayload = new byte[]{8, 16, 24, 32};
      CoreBytesMessageWrapper outbound = createBytesMessage();
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_VALUE_BINARY);
      outbound.writeBytes(expectedPayload);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertInstanceOf(Binary.class, ((AmqpValue) amqp.getBody()).getValue());
      assertEquals(4, ((Binary) ((AmqpValue) amqp.getBody()).getValue()).getLength());

      Binary amqpData = (Binary) ((AmqpValue) amqp.getBody()).getValue();
      Binary inputData = new Binary(expectedPayload);

      assertEquals(inputData, amqpData);
   }

   // ----- MapMessage type tests --------------------------------------------//

   @Test
   public void testConvertMapMessageToAmqpMessageWithNoBody() throws Exception {
      CoreMapMessageWrapper outbound = createMapMessage();
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertInstanceOf(Map.class, ((AmqpValue) amqp.getBody()).getValue());
   }

   @Test
   public void testConvertMapMessageToAmqpMessageWithByteArrayValueInBody() throws Exception {
      final byte[] byteArray = new byte[]{1, 2, 3, 4, 5};

      CoreMapMessageWrapper outbound = createMapMessage();
      outbound.setBytes("bytes", byteArray);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertInstanceOf(Map.class, ((AmqpValue) amqp.getBody()).getValue());

      @SuppressWarnings("unchecked")
      Map<Object, Object> amqpMap = (Map<Object, Object>) ((AmqpValue) amqp.getBody()).getValue();

      assertEquals(1, amqpMap.size());
      Binary readByteArray = (Binary) amqpMap.get("bytes");
      assertNotNull(readByteArray);
   }

   @Test
   public void testConvertMapMessageToAmqpMessage() throws Exception {
      CoreMapMessageWrapper outbound = createMapMessage();
      outbound.setString("property-1", "string");
      outbound.setInt("property-2", 1);
      outbound.setBoolean("property-3", true);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertInstanceOf(Map.class, ((AmqpValue) amqp.getBody()).getValue());

      @SuppressWarnings("unchecked")
      Map<Object, Object> amqpMap = (Map<Object, Object>) ((AmqpValue) amqp.getBody()).getValue();

      assertEquals(3, amqpMap.size());
      assertEquals("string", amqpMap.get("property-1"));
   }

   //----- StreamMessage type tests -----------------------------------------//

   @Test
   public void testConvertStreamMessageToAmqpMessageWithAmqpValueBodyNoPropertySet() throws Exception {
      CoreStreamMessageWrapper outbound = createStreamMessage();
      outbound.writeBoolean(false);
      outbound.writeString("test");
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());

      AmqpValue list = (AmqpValue) amqp.getBody();

      @SuppressWarnings("unchecked")
      List<Object> amqpList = (List<Object>) list.getValue();

      assertEquals(2, amqpList.size());
   }

   @Test
   public void testConvertStreamMessageToAmqpMessageWithAmqpValueBody() throws Exception {
      CoreStreamMessageWrapper outbound = createStreamMessage();
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_VALUE_LIST);
      outbound.writeBoolean(false);
      outbound.writeString("test");
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());

      AmqpValue list = (AmqpValue) amqp.getBody();

      @SuppressWarnings("unchecked")
      List<Object> amqpList = (List<Object>) list.getValue();

      assertEquals(2, amqpList.size());
   }

   @Test
   public void testConvertStreamMessageToAmqpMessageWithAmqpSequencey() throws Exception {
      CoreStreamMessageWrapper outbound = createStreamMessage();
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_SEQUENCE);
      outbound.writeBoolean(false);
      outbound.writeString("test");
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpSequence.class, amqp.getBody());
      assertInstanceOf(List.class, ((AmqpSequence) amqp.getBody()).getValue());

      @SuppressWarnings("unchecked")
      List<Object> amqpList = ((AmqpSequence) amqp.getBody()).getValue();

      assertEquals(2, amqpList.size());
   }

   // ----- ObjectMessage type tests -----------------------------------------//

   @Test
   public void testConvertEmptyObjectMessageToAmqpMessageWithDataBody() throws Exception {
      CoreObjectMessageWrapper outbound = createObjectMessage();
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(Data.class, amqp.getBody());
      assertEquals(5, ((Data) amqp.getBody()).getValue().getLength());
   }

   @Test
   public void testConvertEmptyObjectMessageToAmqpMessageUnknownEncodingGetsDataSection() throws Exception {
      CoreObjectMessageWrapper outbound = createObjectMessage();
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_UNKNOWN);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(Data.class, amqp.getBody());
      assertEquals(5, ((Data) amqp.getBody()).getValue().getLength());
   }

   @Test
   public void testConvertObjectMessageToAmqpMessageWithDataBody() throws Exception {
      CoreObjectMessageWrapper outbound = createObjectMessage(TEST_OBJECT_VALUE);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(Data.class, amqp.getBody());
      assertNotEquals(0, ((Data) amqp.getBody()).getValue().getLength());

      Object value = deserialize(((Data) amqp.getBody()).getValue().getArray());
      assertNotNull(value);
      assertInstanceOf(UUID.class, value);
   }

   @Test
   public void testConvertObjectMessageToAmqpMessageUnknownEncodingGetsDataSection() throws Exception {
      CoreObjectMessageWrapper outbound = createObjectMessage(TEST_OBJECT_VALUE);
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_UNKNOWN);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(Data.class, amqp.getBody());
      assertNotEquals(0, ((Data) amqp.getBody()).getValue().getLength());

      Object value = deserialize(((Data) amqp.getBody()).getValue().getArray());
      assertNotNull(value);
      assertInstanceOf(UUID.class, value);
   }

   @Test
   public void testConvertObjectMessageToAmqpMessageWithAmqpValueBody() throws Exception {
      CoreObjectMessageWrapper outbound = createObjectMessage(TEST_OBJECT_VALUE);
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_VALUE_BINARY);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertInstanceOf(Binary.class, ((AmqpValue) amqp.getBody()).getValue());
      assertNotEquals(0, ((Binary) ((AmqpValue) amqp.getBody()).getValue()).getLength());

      Object value = deserialize(((Binary) ((AmqpValue) amqp.getBody()).getValue()).getArray());
      assertNotNull(value);
      assertInstanceOf(UUID.class, value);
   }

   @Test
   public void testConvertEmptyObjectMessageToAmqpMessageWithAmqpValueBody() throws Exception {
      CoreObjectMessageWrapper outbound = createObjectMessage();
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_VALUE_BINARY);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertInstanceOf(Binary.class, ((AmqpValue) amqp.getBody()).getValue());
      assertEquals(5, ((Binary) ((AmqpValue) amqp.getBody()).getValue()).getLength());
   }

   // ----- TextMessage type tests -------------------------------------------//

   @Test
   public void testConvertTextMessageToAmqpMessageWithNoBody() throws Exception {
      CoreTextMessageWrapper outbound = createTextMessage();
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertNull(((AmqpValue) amqp.getBody()).getValue());
   }

   @Test
   public void testConvertTextMessageCreatesAmqpValueStringBody() throws Exception {
      String contentString = "myTextMessageContent";
      CoreTextMessageWrapper outbound = createTextMessage(contentString);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertEquals(contentString, ((AmqpValue) amqp.getBody()).getValue());
   }

   @Test
   public void testConvertTextMessageContentNotStoredCreatesAmqpValueStringBody() throws Exception {
      String contentString = "myTextMessageContent";
      CoreTextMessageWrapper outbound = createTextMessage(contentString);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());
      assertEquals(contentString, ((AmqpValue) amqp.getBody()).getValue());
   }

   @Test
   public void testConvertTextMessageCreatesDataSectionBody() throws Exception {
      String contentString = "myTextMessageContent";
      CoreTextMessageWrapper outbound = createTextMessage(contentString);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(AmqpValue.class, amqp.getBody());

      AmqpValue value = (AmqpValue) amqp.getBody();

      assertEquals(contentString, value.getValue());
   }

   @Test
   public void testConvertTextMessageCreatesBodyUsingOriginalEncodingWithDataSection() throws Exception {
      String contentString = "myTextMessageContent";
      CoreTextMessageWrapper outbound = createTextMessage(contentString);
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_DATA);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(Data.class, amqp.getBody());
      assertInstanceOf(Binary.class, ((Data) amqp.getBody()).getValue());

      Binary data = ((Data) amqp.getBody()).getValue();
      String contents = new String(data.getArray(), data.getArrayOffset(), data.getLength(), StandardCharsets.UTF_8);
      assertEquals(contentString, contents);
   }

   @Test
   public void testConvertTextMessageContentNotStoredCreatesBodyUsingOriginalEncodingWithDataSection() throws Exception {
      String contentString = "myTextMessageContent";
      CoreTextMessageWrapper outbound = createTextMessage(contentString);
      outbound.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_DATA);
      outbound.encode();

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(outbound.getInnerMessage(), null);

      assertNotNull(amqp.getBody());
      assertInstanceOf(Data.class, amqp.getBody());
      assertInstanceOf(Binary.class, ((Data) amqp.getBody()).getValue());

      Binary data = ((Data) amqp.getBody()).getValue();
      String contents = new String(data.getArray(), data.getArrayOffset(), data.getLength(), StandardCharsets.UTF_8);
      assertEquals(contentString, contents);
   }

   // ----- Test JMSDestination Handling -------------------------------------//

   @Test
   public void testConvertMessageWithJMSDestinationNull() throws Exception {
      doTestConvertMessageWithJMSDestination(null, null);
   }

   @Test
   public void testConvertMessageWithJMSDestinationQueue() throws Exception {
      doTestConvertMessageWithJMSDestination(createDestination(QUEUE_TYPE), QUEUE_TYPE);
   }


   private void doTestConvertMessageWithJMSDestination(String jmsDestination, Object expectedAnnotationValue) throws Exception {
      CoreTextMessageWrapper textMessage = createTextMessage();
      textMessage.setText("myTextMessageContent");
      textMessage.setDestination(jmsDestination);

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(textMessage.getInnerMessage(), null);

      MessageAnnotations ma = amqp.getMessageAnnotations();
      Map<Symbol, Object> maMap = ma == null ? null : ma.getValue();
      if (maMap != null) {
         Object actualValue = maMap.get(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION);
         assertEquals(expectedAnnotationValue, actualValue, "Unexpected annotation value");
      } else if (expectedAnnotationValue != null) {
         fail("Expected annotation value, but there were no annotations");
      }

      if (jmsDestination != null) {
         assertEquals(jmsDestination, amqp.getAddress(), "Unexpected 'to' address");
      }
   }

   // ----- Test JMSReplyTo Handling -----------------------------------------//

   @Test
   public void testConvertMessageWithJMSReplyToNull() throws Exception {
      doTestConvertMessageWithJMSReplyTo(null, null);
   }

   @Test
   public void testConvertMessageWithJMSReplyToQueue() throws Exception {
      doTestConvertMessageWithJMSReplyTo(createDestination(QUEUE_TYPE), QUEUE_TYPE);
   }

   private void doTestConvertMessageWithJMSReplyTo(String jmsReplyTo, Object expectedAnnotationValue) throws Exception {
      CoreTextMessageWrapper textMessage = createTextMessage();
      textMessage.setText("myTextMessageContent");
      textMessage.setJMSReplyTo(jmsReplyTo);

      AMQPMessage amqp = AMQPConverter.getInstance().fromCore(textMessage.getInnerMessage(), null);

      MessageAnnotations ma = amqp.getMessageAnnotations();
      Map<Symbol, Object> maMap = ma == null ? null : ma.getValue();
      if (maMap != null) {
         Object actualValue = maMap.get(AMQPMessageSupport.JMS_REPLY_TO_TYPE_MSG_ANNOTATION);
         assertEquals(expectedAnnotationValue, actualValue, "Unexpected annotation value");
      } else if (expectedAnnotationValue != null) {
         fail("Expected annotation value, but there were no annotations");
      }

      if (jmsReplyTo != null) {
         assertEquals(jmsReplyTo, amqp.getReplyTo().toString(), "Unexpected 'reply-to' address");
      }
   }

   // ----- Utility Methods used for this Test -------------------------------//

   private String createDestination(byte destType) {
      String prefix = PrefixUtil.getURIPrefix(TEST_ADDRESS);
      String address = PrefixUtil.removePrefix(TEST_ADDRESS, prefix);
      return switch (destType) {
         case QUEUE_TYPE, TOPIC_TYPE, TEMP_QUEUE_TYPE, TEMP_TOPIC_TYPE -> address;
         default -> throw new IllegalArgumentException("Invliad Destination Type given/");
      };
   }

   private CoreMessageWrapper createMessage() {
      return new CoreMessageWrapper(newMessage(org.apache.activemq.artemis.api.core.Message.DEFAULT_TYPE));
   }

   private CoreBytesMessageWrapper createBytesMessage() {
      return new CoreBytesMessageWrapper(newMessage(org.apache.activemq.artemis.api.core.Message.BYTES_TYPE));
   }

   private CoreMapMessageWrapper createMapMessage() {
      return new CoreMapMessageWrapper(newMessage(org.apache.activemq.artemis.api.core.Message.MAP_TYPE));
   }

   private CoreStreamMessageWrapper createStreamMessage() {
      return new CoreStreamMessageWrapper(newMessage(org.apache.activemq.artemis.api.core.Message.STREAM_TYPE));
   }

   private CoreObjectMessageWrapper createObjectMessage() {
      return createObjectMessage(null);
   }

   private CoreObjectMessageWrapper createObjectMessage(Serializable payload) {
      CoreObjectMessageWrapper result = AMQPMessageSupport.createObjectMessage(0, null);

      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
           ObjectOutputStream oos = new ObjectOutputStream(baos)) {

         oos.writeObject(payload);
         byte[] data = baos.toByteArray();
         result.setSerializedForm(new Binary(data));
      } catch (Exception ex) {
         throw new AssertionError("Should not fail to setObject in this test");
      }

      return result;
   }

   private CoreTextMessageWrapper createTextMessage() {
      return createTextMessage(null);
   }

   private CoreTextMessageWrapper createTextMessage(String text) {
      CoreTextMessageWrapper result = AMQPMessageSupport.createTextMessage(0, null);

      try {
         result.setText(text);
      } catch (Exception e) {
      }

      return result;
   }

   private Object deserialize(byte[] payload) throws Exception {
      try (ByteArrayInputStream bis = new ByteArrayInputStream(payload);
           ObjectInputStream ois = new ObjectInputStream(bis)) {

         return ois.readObject();
      }
   }

   private CoreMessage newMessage(byte messageType) {
      CoreMessage message = new CoreMessage(0, 512);
      message.setType(messageType);
      ((ResetLimitWrappedActiveMQBuffer) message.getBodyBuffer()).setMessage(null);
      return message;
   }
}
