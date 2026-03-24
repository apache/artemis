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
package org.apache.activemq.artemis.protocol.amqp.converter;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.buffers.impl.ResetLimitWrappedActiveMQBuffer;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreBytesMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreMapMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreStreamMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreTextMessageWrapper;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Footer;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.AMQP_NULL;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ENCODED_DELIVERY_ANNOTATION_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ENCODED_FOOTER_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ENCODED_MESSAGE_ANNOTATION_PREFIX;
import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.JMS_AMQP_ORIGINAL_ENCODING;

public class TestConversions extends Assert {

   private static final Logger logger = Logger.getLogger(TestConversions.class);

   @Test
   public void testAmqpValueOfBooleanIsPassedThrough() throws Exception {
      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setApplicationProperties(properties);

      byte[] bodyBytes = new byte[4];

      for (int i = 0; i < bodyBytes.length; i++) {
         bodyBytes[i] = (byte) 0xff;
      }

      message.setBody(new AmqpValue(Boolean.valueOf(true)));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);

      ICoreMessage serverMessage = encodedMessage.toCore();

      verifyProperties(CoreMessageWrapper.wrap(serverMessage));
   }

   @Test
   public void testSimpleConversionBytes() throws Exception {
      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setApplicationProperties(properties);

      byte[] bodyBytes = new byte[4];

      for (int i = 0; i < bodyBytes.length; i++) {
         bodyBytes[i] = (byte) 0xff;
      }

      message.setBody(new Data(new Binary(bodyBytes)));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);

      ICoreMessage serverMessage = encodedMessage.toCore();

      CoreBytesMessageWrapper bytesMessage = (CoreBytesMessageWrapper) CoreMessageWrapper.wrap(serverMessage);

      verifyProperties(bytesMessage);

      assertEquals(bodyBytes.length, bytesMessage.getBodyLength());

      byte[] newBodyBytes = new byte[4];

      bytesMessage.readBytes(newBodyBytes);

      Assert.assertArrayEquals(bodyBytes, newBodyBytes);
   }

   private void verifyProperties(CoreMessageWrapper message) throws Exception {
      assertEquals(true, message.getBooleanProperty("true"));
      assertEquals(false, message.getBooleanProperty("false"));
      assertEquals("bar", message.getStringProperty("foo"));
   }

   private Map<String, Object> createPropertiesMap() {
      Map<String, Object> mapprop = new HashMap<>();

      mapprop.put("true", Boolean.TRUE);
      mapprop.put("false", Boolean.FALSE);
      mapprop.put("foo", "bar");
      return mapprop;
   }

   private TypedProperties createTypedPropertiesMap() {
      TypedProperties typedProperties = new TypedProperties();
      typedProperties.putBooleanProperty(new SimpleString("true"), Boolean.TRUE);
      typedProperties.putBooleanProperty(new SimpleString("false"), Boolean.FALSE);
      typedProperties.putSimpleStringProperty(new SimpleString("foo"), new SimpleString("bar"));
      return typedProperties;
   }

   @Test
   public void testSimpleConversionMap() throws Exception {
      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setApplicationProperties(properties);

      Map<String, Object> mapValues = new HashMap<>();
      mapValues.put("somestr", "value");
      mapValues.put("someint", Integer.valueOf(1));

      message.setBody(new AmqpValue(mapValues));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);

      ICoreMessage serverMessage = encodedMessage.toCore();
      serverMessage.getReadOnlyBodyBuffer();

      CoreMapMessageWrapper mapMessage = (CoreMapMessageWrapper) CoreMessageWrapper.wrap(serverMessage);
      mapMessage.decode();

      verifyProperties(mapMessage);

      assertEquals(1, mapMessage.getInt("someint"));
      assertEquals("value", mapMessage.getString("somestr"));

      AMQPMessage newAMQP = CoreAmqpConverter.fromCore(mapMessage.getInnerMessage(), null);
      assertNotNull(newAMQP.getBody());
   }

   @Test
   public void testSimpleConversionStream() throws Exception {
      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setApplicationProperties(properties);

      List<Object> objects = new LinkedList<>();
      objects.add(Integer.valueOf(10));
      objects.add("10");

      message.setBody(new AmqpSequence(objects));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);

      ICoreMessage serverMessage = encodedMessage.toCore();

      CoreStreamMessageWrapper streamMessage = (CoreStreamMessageWrapper) CoreMessageWrapper.wrap(serverMessage);

      verifyProperties(streamMessage);

      streamMessage.reset();

      assertEquals(10, streamMessage.readInt());
      assertEquals("10", streamMessage.readString());
   }

   @Test
   public void testSimpleConversionText() throws Exception {
      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setApplicationProperties(properties);

      String text = "someText";
      message.setBody(new AmqpValue(text));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);

      ICoreMessage serverMessage = encodedMessage.toCore();

      CoreTextMessageWrapper textMessage = (CoreTextMessageWrapper) CoreMessageWrapper.wrap(serverMessage);
      textMessage.decode();

      verifyProperties(textMessage);

      assertEquals(text, textMessage.getText());
   }

   @Test
   public void testSimpleConversionWithExtraProperties() throws Exception {
      MessageImpl message = (MessageImpl) Message.Factory.create();

      String text = "someText";
      message.setBody(new AmqpValue(text));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);
      TypedProperties extraProperties = createTypedPropertiesMap();
      extraProperties.putBytesProperty(new SimpleString("bytesProp"), "value".getBytes());
      encodedMessage.setExtraProperties(extraProperties);

      ICoreMessage serverMessage = encodedMessage.toCore();

      CoreTextMessageWrapper textMessage = (CoreTextMessageWrapper) CoreMessageWrapper.wrap(serverMessage);
      textMessage.decode();

      verifyProperties(textMessage);
      assertEquals("value", new String(((byte[]) textMessage.getObjectProperty("bytesProp"))));

      assertEquals(text, textMessage.getText());
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testConvertMessageWithMapInMessageAnnotations() throws Exception {
      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setApplicationProperties(properties);

      final String annotationName = "x-opt-test-annotation";
      final Symbol annotationNameSymbol = Symbol.valueOf(annotationName);

      Map<String, String> embeddedMap = new LinkedHashMap<>();
      embeddedMap.put("key1", "value1");
      embeddedMap.put("key2", "value2");
      embeddedMap.put("key3", "value3");
      Map<Symbol, Object> annotationsMap = new LinkedHashMap<>();
      annotationsMap.put(annotationNameSymbol, embeddedMap);
      MessageAnnotations messageAnnotations = new MessageAnnotations(annotationsMap);
      byte[] encodedEmbeddedMap = encodeObject(embeddedMap);

      Map<String, Object> mapValues = new HashMap<>();
      mapValues.put("somestr", "value");
      mapValues.put("someint", Integer.valueOf(1));

      message.setMessageAnnotations(messageAnnotations);
      message.setBody(new AmqpValue(mapValues));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);

      ICoreMessage serverMessage = encodedMessage.toCore();
      serverMessage.getReadOnlyBodyBuffer();

      CoreMapMessageWrapper mapMessage = (CoreMapMessageWrapper) CoreMessageWrapper.wrap(serverMessage);
      mapMessage.decode();

      verifyProperties(mapMessage);

      assertEquals(1, mapMessage.getInt("someint"));
      assertEquals("value", mapMessage.getString("somestr"));
      assertTrue(mapMessage.propertyExists(JMS_AMQP_ENCODED_MESSAGE_ANNOTATION_PREFIX + annotationName));
      assertArrayEquals(encodedEmbeddedMap, (byte[]) mapMessage.getObjectProperty(JMS_AMQP_ENCODED_MESSAGE_ANNOTATION_PREFIX + annotationName));

      AMQPMessage newAMQP = CoreAmqpConverter.fromCore(mapMessage.getInnerMessage(), null);
      assertNotNull(newAMQP.getBody());
      assertNotNull(newAMQP.getMessageAnnotations());
      assertNotNull(newAMQP.getMessageAnnotations().getValue());
      assertTrue(newAMQP.getMessageAnnotations().getValue().containsKey(annotationNameSymbol));
      Object result = newAMQP.getMessageAnnotations().getValue().get(annotationNameSymbol);
      assertTrue(result instanceof Map);
      assertEquals(embeddedMap, (Map<String, String>) result);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testConvertMessageWithMapInFooter() throws Exception {
      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setApplicationProperties(properties);

      final String footerName = "test-footer";
      final Symbol footerNameSymbol = Symbol.valueOf(footerName);

      Map<String, String> embeddedMap = new LinkedHashMap<>();
      embeddedMap.put("key1", "value1");
      embeddedMap.put("key2", "value2");
      embeddedMap.put("key3", "value3");
      Map<Symbol, Object> footerMap = new LinkedHashMap<>();
      footerMap.put(footerNameSymbol, embeddedMap);
      Footer messageFooter = new Footer(footerMap);
      byte[] encodedEmbeddedMap = encodeObject(embeddedMap);

      Map<String, Object> mapValues = new HashMap<>();
      mapValues.put("somestr", "value");
      mapValues.put("someint", Integer.valueOf(1));

      message.setFooter(messageFooter);
      message.setBody(new AmqpValue(mapValues));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);

      ICoreMessage serverMessage = encodedMessage.toCore();
      serverMessage.getReadOnlyBodyBuffer();

      CoreMapMessageWrapper mapMessage = (CoreMapMessageWrapper) CoreMessageWrapper.wrap(serverMessage);
      mapMessage.decode();

      verifyProperties(mapMessage);

      assertEquals(1, mapMessage.getInt("someint"));
      assertEquals("value", mapMessage.getString("somestr"));
      assertTrue(mapMessage.propertyExists(JMS_AMQP_ENCODED_FOOTER_PREFIX + footerName));
      assertArrayEquals(encodedEmbeddedMap, (byte[]) mapMessage.getObjectProperty(JMS_AMQP_ENCODED_FOOTER_PREFIX + footerName));

      AMQPMessage newAMQP = CoreAmqpConverter.fromCore(mapMessage.getInnerMessage(), null);
      assertNotNull(newAMQP.getBody());
      assertNotNull(newAMQP.getFooter());
      assertNotNull(newAMQP.getFooter().getValue());
      assertTrue(newAMQP.getFooter().getValue().containsKey(footerNameSymbol));
      Object result = newAMQP.getFooter().getValue().get(footerNameSymbol);
      assertTrue(result instanceof Map);
      assertEquals(embeddedMap, (Map<String, String>) result);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testConvertFromCoreWithEncodedDeliveryAnnotationProperty() throws Exception {

      final String annotationName = "x-opt-test-annotation";
      final Symbol annotationNameSymbol = Symbol.valueOf(annotationName);

      Map<String, String> embeddedMap = new LinkedHashMap<>();
      embeddedMap.put("key1", "value1");
      embeddedMap.put("key2", "value2");
      embeddedMap.put("key3", "value3");

      byte[] encodedEmbeddedMap = encodeObject(embeddedMap);

      CoreMessageWrapper serverMessage = createMessage();

      serverMessage.setShortProperty(JMS_AMQP_ORIGINAL_ENCODING, AMQP_NULL);
      serverMessage.setObjectProperty(JMS_AMQP_ENCODED_DELIVERY_ANNOTATION_PREFIX + annotationName, encodedEmbeddedMap);
      serverMessage.encode();

      AMQPMessage newAMQP = CoreAmqpConverter.fromCore(serverMessage.getInnerMessage(), null);
      assertNull(newAMQP.getBody());
      assertNotNull(newAMQP.getDeliveryAnnotations());
      assertNotNull(newAMQP.getDeliveryAnnotations().getValue());
      assertTrue(newAMQP.getDeliveryAnnotations().getValue().containsKey(annotationNameSymbol));
      Object result = newAMQP.getDeliveryAnnotations().getValue().get(annotationNameSymbol);
      assertTrue(result instanceof Map);
      assertEquals(embeddedMap, (Map<String, String>) result);
   }

   private byte[] encodeObject(Object toEncode) {
      ByteBuf scratch = Unpooled.buffer();
      EncoderImpl encoder = TLSEncode.getEncoder();
      encoder.setByteBuffer(new NettyWritable(scratch));

      try {
         encoder.writeObject(toEncode);
      } finally {
         encoder.setByteBuffer((WritableBuffer) null);
      }

      byte[] result = new byte[scratch.writerIndex()];
      scratch.readBytes(result);

      return result;
   }

   @Test
   public void testEditAndConvert() throws Exception {

      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      properties.getValue().put("hello", "hello");
      MessageImpl message = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      message.setMessageAnnotations(annotations);
      message.setApplicationProperties(properties);

      String text = "someText";
      message.setBody(new AmqpValue(text));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);
      TypedProperties extraProperties = new TypedProperties();
      encodedMessage.setAddress(SimpleString.toSimpleString("xxxx.v1.queue"));

      for (int i = 0; i < 10; i++) {
         if (logger.isDebugEnabled()) {
            logger.debug("Message encoded :: " + encodedMessage.toDebugString());
         }

         encodedMessage.messageChanged();
         AmqpValue value = (AmqpValue) encodedMessage.getProtonMessage().getBody();
         Assert.assertEquals(text, (String) value.getValue());

         // this line is needed to force a failure
         ICoreMessage coreMessage = encodedMessage.toCore();

         if (logger.isDebugEnabled()) {
            logger.debug("Converted message: " + coreMessage);
         }
      }
   }

   @Test
   public void testExpandPropertiesAndConvert() throws Exception {

      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      properties.getValue().put("hello", "hello");
      MessageImpl message = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      message.setMessageAnnotations(annotations);
      message.setApplicationProperties(properties);

      String text = "someText";
      message.setBody(new AmqpValue(text));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);
      TypedProperties extraProperties = new TypedProperties();
      encodedMessage.setAddress(SimpleString.toSimpleString("xxxx.v1.queue"));

      for (int i = 0; i < 100; i++) {
         encodedMessage.putStringProperty("another" + i, "value" + i);
         encodedMessage.messageChanged();
         encodedMessage.reencode();
         AmqpValue value = (AmqpValue) encodedMessage.getProtonMessage().getBody();
         Assert.assertEquals(text, (String) value.getValue());
         ICoreMessage coreMessage = encodedMessage.toCore();
         if (logger.isDebugEnabled()) {
            logger.debug("Converted message: " + coreMessage);
         }

         // I'm going to replace the message every 10 messages by a re-encoded version to check if the wiring still acturate.
         // I want to mix replacing and not replacing to make sure the re-encoding is not giving me any surprises
         if (i > 0 && i % 10 == 0) {
            ByteBuf buf = Unpooled.buffer(15 * 1024, 150 * 1024);
            encodedMessage.sendBuffer(buf, 1);
            byte[] messageBytes = new byte[buf.writerIndex()];
            buf.readBytes(messageBytes);

            message = (MessageImpl) Message.Factory.create();
            message.decode(ByteBuffer.wrap(messageBytes));
            // This is replacing the message by the new expanded version
            encodedMessage = encodeAndCreateAMQPMessage(message);

         }
      }
   }

   @Test
   public void testExpandNoReencode() throws Exception {

      Map<String, Object> mapprop = createPropertiesMap();
      ApplicationProperties properties = new ApplicationProperties(mapprop);
      properties.getValue().put("hello", "hello");
      MessageImpl message = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      message.setMessageAnnotations(annotations);
      message.setApplicationProperties(properties);

      String text = "someText";
      message.setBody(new AmqpValue(text));

      AMQPMessage encodedMessage = encodeAndCreateAMQPMessage(message);
      TypedProperties extraProperties = new TypedProperties();
      encodedMessage.setAddress(SimpleString.toSimpleString("xxxx.v1.queue"));

      for (int i = 0; i < 100; i++) {
         encodedMessage.setMessageID(333L);
         if (i % 3 == 0) {
            encodedMessage.referenceOriginalMessage(encodedMessage, SimpleString.toSimpleString("SOME-OTHER-QUEUE-DOES-NOT-MATTER-WHAT"));
         } else {
            encodedMessage.referenceOriginalMessage(encodedMessage, SimpleString.toSimpleString("XXX"));
         }
         encodedMessage.putStringProperty("another " + i, "value " + i);
         encodedMessage.messageChanged();
         if (i % 2 == 0) {
            encodedMessage.setAddress("THIS-IS-A-BIG-THIS-IS-A-BIG-ADDRESS-THIS-IS-A-BIG-ADDRESS-RIGHT");
         } else {
            encodedMessage.setAddress("A"); // small address
         }
         encodedMessage.messageChanged();
         ICoreMessage coreMessage = encodedMessage.toCore();
      }
   }

   private AMQPMessage encodeAndCreateAMQPMessage(MessageImpl message) {
      NettyWritable encoded = new NettyWritable(Unpooled.buffer(1024));
      message.encode(encoded);

      NettyReadable readable = new NettyReadable(encoded.getByteBuf());

      return new AMQPStandardMessage(AMQPMessage.DEFAULT_MESSAGE_FORMAT, readable, null, null);
   }

   private CoreMessageWrapper createMessage() {
      return new CoreMessageWrapper(newMessage(org.apache.activemq.artemis.api.core.Message.DEFAULT_TYPE));
   }

   private CoreMessage newMessage(byte messageType) {
      CoreMessage message = new CoreMessage(0, 512);
      message.setType(messageType);
      ((ResetLimitWrappedActiveMQBuffer) message.getBodyBuffer()).setMessage(null);
      return message;
   }
}
