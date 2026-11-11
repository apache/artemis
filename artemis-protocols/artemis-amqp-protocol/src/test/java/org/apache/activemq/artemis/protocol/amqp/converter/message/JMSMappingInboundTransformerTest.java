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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreBytesMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreMapMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreObjectMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreStreamMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreTextMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JMSMappingInboundTransformerTest {

   @BeforeEach
   public void setUp() {
   }

   // ----- Null Body Section ------------------------------------------------//

   /**
    * Test that a message with no body section, but with the content type set to
    * {@value AMQPMessageSupport#OCTET_STREAM_CONTENT_TYPE} results in a BytesMessage
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateBytesMessageFromNoBodySectionAndContentType() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setContentType(AMQPMessageSupport.OCTET_STREAM_CONTENT_TYPE);

      AMQPStandardMessage messageEncode = encodeAndCreateAMQPMessage(message);

      ICoreMessage coreMessage = messageEncode.toCore();

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(coreMessage);

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreBytesMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that a message with no body section, and no content-type results in a BytesMessage when not otherwise
    * annotated to indicate the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateBytesMessageFromNoBodySectionAndNoContentType() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreBytesMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   @Test
   public void testCreateTextMessageFromNoBodySectionAndContentType() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setContentType("text/plain");

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreTextMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   // ----- Data Body Section ------------------------------------------------//

   /**
    * Test that a data body containing nothing, but with the content type set to
    * {@value AMQPMessageSupport#OCTET_STREAM_CONTENT_TYPE} results in a BytesMessage when not otherwise annotated to
    * indicate the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateBytesMessageFromDataWithEmptyBinaryAndContentType() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      Binary binary = new Binary(new byte[0]);
      message.setBody(new Data(binary));
      message.setContentType(AMQPMessageSupport.OCTET_STREAM_CONTENT_TYPE);

      AMQPStandardMessage amqp = encodeAndCreateAMQPMessage(message);
      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(amqp.toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreBytesMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that a message with an empty data body section, and with the content type set to an unknown value results in
    * a BytesMessage when not otherwise annotated to indicate the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateBytesMessageFromDataWithUnknownContentType() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      Binary binary = new Binary(new byte[0]);
      message.setBody(new Data(binary));
      message.setContentType("unknown-content-type");

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreBytesMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that a receiving a data body containing nothing and no content type being set results in a BytesMessage when
    * not otherwise annotated to indicate the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateBytesMessageFromDataWithEmptyBinaryAndNoContentType() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      Binary binary = new Binary(new byte[0]);
      message.setBody(new Data(binary));

      assertNull(message.getContentType());

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreBytesMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   @Test
   public void testCreateObjectMessageFromDataWithContentTypeAndEmptyBinary() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      Binary binary = new Binary(new byte[0]);
      message.setBody(new Data(binary));
      message.setContentType(AMQPMessageSupport.SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.toString());

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreObjectMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeTextPlain() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/plain;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/plain;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/plain;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/plain", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeTextJson() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/json;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/json;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/json;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/json", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeTextHtml() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/html;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/html;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/html;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/html", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeTextFoo() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/foo;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/foo;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/foo;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("text/foo", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeApplicationJson() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/json;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/json;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/json;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/json", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeApplicationJsonVariant() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/something+json;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/something+json;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/something+json;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/something+json", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeApplicationJavascript() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/javascript;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/javascript;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/javascript;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/javascript", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeApplicationEcmascript() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/ecmascript;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/ecmascript;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/ecmascript;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/ecmascript", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeApplicationXml() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/xml;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/xml;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/xml;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/xml", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeApplicationXmlVariant() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/something+xml;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/something+xml;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/something+xml;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/something+xml", StandardCharsets.UTF_8);
   }

   @Test
   public void testCreateTextMessageFromDataWithContentTypeApplicationXmlDtd() throws Exception {
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/xml-dtd;charset=iso-8859-1", StandardCharsets.ISO_8859_1);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/xml-dtd;charset=us-ascii", StandardCharsets.US_ASCII);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/xml-dtd;charset=utf-8", StandardCharsets.UTF_8);
      doCreateTextMessageFromDataWithContentTypeTestImpl("application/xml-dtd", StandardCharsets.UTF_8);
   }

   private void doCreateTextMessageFromDataWithContentTypeTestImpl(String contentType, Charset expectedCharset) throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      Binary binary = new Binary(new byte[0]);
      message.setBody(new Data(binary));
      message.setContentType(contentType);

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      if (StandardCharsets.UTF_8.equals(expectedCharset)) {
         assertEquals(CoreTextMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
      } else {
         assertEquals(CoreBytesMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
      }
   }

   // ----- AmqpValue transformations ----------------------------------------//

   /**
    * Test that an amqp-value body containing a string results in a TextMessage when not otherwise annotated to indicate
    * the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateTextMessageFromAmqpValueWithString() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setBody(new AmqpValue("content"));

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreTextMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that an amqp-value body containing a null results in an TextMessage when not otherwise annotated to indicate
    * the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateTextMessageFromAmqpValueWithNull() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setBody(new AmqpValue(null));

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreTextMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that a message with an AmqpValue section containing a Binary, but with the content type set to
    * {@value AMQPMessageSupport#SERIALIZED_JAVA_OBJECT_CONTENT_TYPE} results in an ObjectMessage when not otherwise
    * annotated to indicate the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateObjectMessageFromAmqpValueWithBinaryAndContentType() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setBody(new AmqpValue(new Binary(new byte[0])));
      message.setContentType(AMQPMessageSupport.SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.toString());

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreObjectMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that an amqp-value body containing a map results in an MapMessage when not otherwise annotated to indicate
    * the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateAmqpMapMessageFromAmqpValueWithMap() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      Map<String, String> map = new HashMap<>();
      message.setBody(new AmqpValue(map));

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreMapMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that an amqp-value body containing a list results in an StreamMessage when not otherwise annotated to
    * indicate the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateAmqpStreamMessageFromAmqpValueWithList() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      List<String> list = new ArrayList<>();
      message.setBody(new AmqpValue(list));

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreStreamMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that an amqp-sequence body containing a list results in an StreamMessage when not otherwise annotated to
    * indicate the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateAmqpStreamMessageFromAmqpSequence() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      List<String> list = new ArrayList<>();
      message.setBody(new AmqpSequence(list));

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreStreamMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that an amqp-value body containing a binary value results in BytesMessage when not otherwise annotated to
    * indicate the type of JMS message it is.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateAmqpBytesMessageFromAmqpValueWithBinary() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      Binary binary = new Binary(new byte[0]);
      message.setBody(new AmqpValue(binary));

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreBytesMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that an amqp-value body containing a value which can't be categorized results in an exception from the
    * transformer and then try the transformer's own fallback transformer to result in an BytesMessage.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateBytesMessageFromAmqpValueWithUncategorisedContent() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setBody(new AmqpValue(UUID.randomUUID()));

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());

      assertNotNull(jmsMessage, "Message should not be null");
      assertEquals(CoreBytesMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");
   }

   /**
    * Test that an amqp-value body containing a value which can't be categorized results in a BytesMessage whose
    * body is the encoded amqp-value section.
    *
    * @throws Exception if an error occurs during the test.
    */
   @Test
   public void testCreateBytesMessageFromAmqpValueWithUncategorisedContentHasEncodedBody() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setBody(new AmqpValue(42));

      // hold a pooled buffer so that the converter's pooled buffer does not start at the beginning of its array
      ByteBuf held = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);
      CoreBytesMessageWrapper jmsMessage;
      try {
         jmsMessage = (CoreBytesMessageWrapper) CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());
      } finally {
         held.release();
      }

      // descriptor 0x77 (amqp-value), then smallint 42
      byte[] expected = new byte[] {0x00, 0x53, 0x77, 0x54, 0x2a};
      byte[] body = new byte[(int) jmsMessage.getBodyLength()];
      jmsMessage.readBytes(body);
      assertArrayEquals(expected, body);
   }

   @Test
   public void testTransformMessageWithAmqpValueStringCreatesTextMessage() throws Exception {
      String contentString = "myTextMessageContent";
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setBody(new AmqpValue(contentString));

      CoreTextMessageWrapper jmsMessage = (CoreTextMessageWrapper) CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());
      jmsMessage.decode();

      assertInstanceOf(CoreTextMessageWrapper.class, jmsMessage, "Expected TextMessage");
      assertEquals(CoreTextMessageWrapper.class, jmsMessage.getClass(), "Unexpected message class type");

      CoreTextMessageWrapper textMessage = jmsMessage;

      assertNotNull(textMessage.getText());
      assertEquals(contentString, textMessage.getText());
   }

   // ----- Destination Conversions ------------------------------------------//

   @Test
   public void testTransformWithNoToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithToTypeDestinationTypeAnnotationTestImpl(null);
   }

   @Test
   public void testTransformWithQueueStringToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithToTypeDestinationTypeAnnotationTestImpl("queue");
   }

   @Test
   public void testTransformWithTemporaryQueueStringToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithToTypeDestinationTypeAnnotationTestImpl("queue,temporary");
   }

   @Test
   public void testTransformWithTopicStringToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithToTypeDestinationTypeAnnotationTestImpl("topic");
   }

   @Test
   public void testTransformWithTemporaryTopicStringToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithToTypeDestinationTypeAnnotationTestImpl("topic,temporary");
   }

   private void doTransformWithToTypeDestinationTypeAnnotationTestImpl(Object toTypeAnnotationValue)
      throws Exception {

      String toAddress = "toAddress";
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setBody(new AmqpValue("myTextMessageContent"));
      message.setAddress(toAddress);
      if (toTypeAnnotationValue != null) {
         Map<Symbol, Object> map = new HashMap<>();
         map.put(Symbol.valueOf("x-opt-to-type"), toTypeAnnotationValue);
         MessageAnnotations ma = new MessageAnnotations(map);
         message.setMessageAnnotations(ma);
      }

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());
      assertInstanceOf(CoreTextMessageWrapper.class, jmsMessage, "Expected ServerJMSTextMessage");
   }

   // ----- ReplyTo Conversions ----------------------------------------------//


   @Test
   public void testTransformWithNoReplyToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithReplyToTypeDestinationTypeAnnotationTestImpl(null);
   }

   @Test
   public void testTransformWithQueueStringReplyToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithReplyToTypeDestinationTypeAnnotationTestImpl("queue");
   }

   @Test
   public void testTransformWithTemporaryQueueStringReplyToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithReplyToTypeDestinationTypeAnnotationTestImpl("queue,temporary");
   }

   @Test
   public void testTransformWithTopicStringReplyToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithReplyToTypeDestinationTypeAnnotationTestImpl("topic");
   }

   @Test
   public void testTransformWithTemporaryTopicStringReplyToTypeDestinationTypeAnnotation() throws Exception {
      doTransformWithReplyToTypeDestinationTypeAnnotationTestImpl("topic,temporary");
   }

   private void doTransformWithReplyToTypeDestinationTypeAnnotationTestImpl(Object replyToTypeAnnotationValue)
      throws Exception {

      String replyToAddress = "replyToAddress";
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setBody(new AmqpValue("myTextMessageContent"));
      message.setReplyTo(replyToAddress);
      if (replyToTypeAnnotationValue != null) {
         Map<Symbol, Object> map = new HashMap<>();
         map.put(Symbol.valueOf("x-opt-reply-type"), replyToTypeAnnotationValue);
         MessageAnnotations ma = new MessageAnnotations(map);
         message.setMessageAnnotations(ma);
      }

      CoreMessageWrapper jmsMessage = CoreMessageWrapper.wrap(encodeAndCreateAMQPMessage(message).toCore());
      assertInstanceOf(CoreTextMessageWrapper.class, jmsMessage, "Expected TextMessage");
   }

   private AMQPStandardMessage encodeAndCreateAMQPMessage(MessageImpl message) {
      NettyWritable encoded = new NettyWritable(Unpooled.buffer(1024));
      message.encode(encoded);

      NettyReadable readable = new NettyReadable(encoded.getByteBuf());

      return new AMQPStandardMessage(AMQPMessage.DEFAULT_MESSAGE_FORMAT, readable, null, null);
   }
}
