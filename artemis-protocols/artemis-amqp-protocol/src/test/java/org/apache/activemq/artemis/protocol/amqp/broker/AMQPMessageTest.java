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
package org.apache.activemq.artemis.protocol.amqp.broker;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.openmbean.CompositeDataConstants;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageIdHelper;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.spi.core.protocol.EmbedMessageUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedByte;
import org.apache.qpid.proton.amqp.UnsignedInteger;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Footer;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.AMQPDefinedTypes;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.EncodingCodes;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AMQPMessageTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String TEST_TO_ADDRESS = "someAddress";

   private static final String TEST_MESSAGE_ANNOTATION_KEY = "x-opt-test-annotation";
   private static final String TEST_MESSAGE_ANNOTATION_VALUE = "test-annotation";
   private static final String TEST_MESSAGE_ANNOTATION_KEY2 = "x-opt-test-annotation2";
   private static final String TEST_MESSAGE_ANNOTATION_VALUE2 = "test-annotation2";

   private static final String TEST_EXTRA_PROPERTY_KEY1 = "extraPropertyKey1";
   private static final String TEST_EXTRA_PROPERTY_VALUE1 = "extraPropertyValue1";
   private static final String TEST_EXTRA_PROPERTY_KEY2 = "extraPropertyKey2";
   private static final String TEST_EXTRA_PROPERTY_VALUE2 = "extraPropertyValue2";

   private static final String TEST_APPLICATION_PROPERTY_KEY = "key-1";
   private static final String TEST_APPLICATION_PROPERTY_VALUE = "value-1";
   private static final String TEST_APPLICATION_PROPERTY_KEY2 = "key-2";
   private static final String TEST_APPLICATION_PROPERTY_VALUE2 = "value-2";

   private static final String TEST_STRING_BODY = "test-string-body";

   private static final String PROPERTY_MAP_APP_PROPERTIES_PREFIX = "applicationProperties.";
   private static final String PROPERTY_MAP_PROPERTIES_PREFIX = "properties.";
   private static final String PROPERTY_MAP_MESSAGE_ANNOTATIONS_PREFIX = "messageAnnotations.";
   private static final String PROPERTY_MAP_EXTRA_PROPERTIES_PREFIX = "extraProperties.";

   private byte[] encodedProtonMessage;

   private final DecoderImpl decoder = new DecoderImpl();
   private final EncoderImpl encoder = new EncoderImpl(decoder);
   {
      AMQPDefinedTypes.registerAllTypes(decoder, encoder);
   }

   @BeforeEach
   public void setUp() {
      encodedProtonMessage = encodeMessage(createProtonMessage());
   }

   //----- Test Message Creation ---------------------------------------------//

   @Test
   public void testCreateMessageFromEncodedByteArrayData() {
      // Constructor 1
      AMQPStandardMessage decoded = new AMQPStandardMessage(0, encodedProtonMessage, null);

      assertTrue(decoded.isDurable());
      assertEquals(TEST_TO_ADDRESS, decoded.getAddress());

      // Constructor 2
      decoded = new AMQPStandardMessage(0, encodedProtonMessage, null, null);

      assertTrue(decoded.isDurable());
      assertEquals(TEST_TO_ADDRESS, decoded.getAddress());
   }

   @Test
   public void testCreateMessageFromEncodedReadableBuffer() {
      AMQPStandardMessage decoded = new AMQPStandardMessage(0, ReadableBuffer.ByteBufferReader.wrap(encodedProtonMessage), null, null);

      Boolean durable = decoded.getHeader().getDurable();
      assertNotNull(durable);
      assertTrue(durable);
      assertEquals(TEST_TO_ADDRESS, decoded.getAddress());
   }

   @Test
   public void testCreateMessageFromEncodedByteArrayDataWithExtraProperties() {
      AMQPStandardMessage decoded = new AMQPStandardMessage(0, encodedProtonMessage, new TypedProperties(), null);

      Boolean durable = decoded.getHeader().getDurable();
      assertNotNull(durable);
      assertTrue(durable);
      assertEquals(TEST_TO_ADDRESS, decoded.getAddress());
      assertNotNull(decoded.getExtraProperties());
   }

   @Test
   public void testCreateMessageForPersistenceDataReload() throws ActiveMQException {
      MessageImpl protonMessage = createProtonMessage();
      ActiveMQBuffer encoded = encodeMessageAsPersistedBuffer(protonMessage);

      AMQPStandardMessage message = new AMQPStandardMessage(0);
      try {
         message.getProtonMessage();
         fail("Should throw NPE due to not being initialized yet");
      } catch (NullPointerException npe) {
      }

      final long persistedSize = (long) encoded.readableBytes();

      // Now reload from encoded data
      message.reloadPersistence(encoded, null);

      assertEquals(persistedSize, message.getPersistSize());
      assertEquals(persistedSize - Integer.BYTES, message.getPersistentSize());
      assertEquals(persistedSize - Integer.BYTES, message.getEncodeSize());
      Boolean durable = message.getHeader().getDurable();
      assertNotNull(durable);
      assertTrue(durable);
      assertEquals(TEST_TO_ADDRESS, message.getAddress());
   }

   @Test
   public void testHasScheduledDeliveryTimeReloadPersistence() {
      final long scheduledTime = System.currentTimeMillis();
      MessageImpl protonMessage = createProtonMessage();
      MessageAnnotations annotations = protonMessage.getMessageAnnotations();
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME, scheduledTime);
      ActiveMQBuffer encoded = encodeMessageAsPersistedBuffer(protonMessage);

      AMQPMessage message = new AMQPStandardMessage(0);
      try {
         message.getProtonMessage();
         fail("Should throw NPE due to not being initialized yet");
      } catch (NullPointerException npe) {
      }

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, message.getDataScanningStatus());

      // Now reload from encoded data
      message.reloadPersistence(encoded, null);

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, message.getDataScanningStatus());

      assertTrue(message.hasScheduledDeliveryTime());

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, message.getDataScanningStatus());

      message.getHeader();

      assertEquals(AMQPMessage.MessageDataScanningStatus.SCANNED, message.getDataScanningStatus());

      assertTrue(message.hasScheduledDeliveryTime());
   }

   @Test
   public void testHasScheduledDeliveryDelayReloadPersistence() {
      final long scheduledDelay = 100000;
      MessageImpl protonMessage = createProtonMessage();
      MessageAnnotations annotations = protonMessage.getMessageAnnotations();
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY, scheduledDelay);
      ActiveMQBuffer encoded = encodeMessageAsPersistedBuffer(protonMessage);

      AMQPMessage message = new AMQPStandardMessage(0);
      try {
         message.getProtonMessage();
         fail("Should throw NPE due to not being initialized yet");
      } catch (NullPointerException npe) {
      }

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, message.getDataScanningStatus());

      // Now reload from encoded data
      message.reloadPersistence(encoded, null);

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, message.getDataScanningStatus());

      assertTrue(message.hasScheduledDeliveryTime());

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, message.getDataScanningStatus());

      message.getHeader();

      assertEquals(AMQPMessage.MessageDataScanningStatus.SCANNED, message.getDataScanningStatus());

      assertTrue(message.hasScheduledDeliveryTime());
   }

   @Test
   public void testNoScheduledDeliveryTimeOrDelayReloadPersistence() {
      MessageImpl protonMessage = createProtonMessage();
      ActiveMQBuffer encoded = encodeMessageAsPersistedBuffer(protonMessage);

      AMQPMessage message = new AMQPStandardMessage(0);
      try {
         message.getProtonMessage();
         fail("Should throw NPE due to not being initialized yet");
      } catch (NullPointerException npe) {
      }

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, message.getDataScanningStatus());

      // Now reload from encoded data
      message.reloadPersistence(encoded, null);

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, message.getDataScanningStatus());

      assertFalse(message.hasScheduledDeliveryTime());

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, message.getDataScanningStatus());

      message.getHeader();

      assertEquals(AMQPMessage.MessageDataScanningStatus.SCANNED, message.getDataScanningStatus());

      assertFalse(message.hasScheduledDeliveryTime());
   }

   //----- Test Memory Estimate access ---------------------------------------//

   @Test
   public void testGetMemoryEstimate() {
      AMQPStandardMessage decoded = new AMQPStandardMessage(0, encodedProtonMessage, new TypedProperties(), null);

      int estimate = decoded.getMemoryEstimate();
      assertTrue(encodedProtonMessage.length < decoded.getMemoryEstimate());
      assertEquals(estimate, decoded.getMemoryEstimate());

      decoded.putStringProperty(SimpleString.of("newProperty"), "newValue");
      decoded.reencode();

      assertNotEquals(estimate, decoded.getMemoryEstimate());
   }


   @Test
   public void testGetMemoryEstimateWithDecodedApplicationProperties() {
      testGetMemoryEstimateWithDecodedApplicationProperties(true);
      testGetMemoryEstimateWithDecodedApplicationProperties(false);
   }


   private void testGetMemoryEstimateWithDecodedApplicationProperties(boolean paged) {
      AMQPStandardMessage decoded = new AMQPStandardMessage(0, encodedProtonMessage, new TypedProperties(), null);
      if (paged) {
         decoded.setPaged();
      }

      logger.info("Estimated size:: {}", decoded.getMemoryEstimate());

      AMQPStandardMessage decodedWithApplicationPropertiesUnmarshalled =
         new AMQPStandardMessage(0, encodeMessage(createProtonMessage()), new TypedProperties(), null);
      if (paged) {
         decodedWithApplicationPropertiesUnmarshalled.setPaged();
      }

      assertEquals(TEST_APPLICATION_PROPERTY_VALUE, decodedWithApplicationPropertiesUnmarshalled.getStringProperty(TEST_APPLICATION_PROPERTY_KEY));
      assertEquals(decodedWithApplicationPropertiesUnmarshalled.getMemoryEstimate(), decoded.getMemoryEstimate());
   }

   //----- Test Connection ID access -----------------------------------------//


   @Test
   public void testDecodeMultiThreaded() throws Exception {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      Properties properties = new Properties();
      properties.setTo("someNiceLocal");
      protonMessage.setProperties(properties);
      protonMessage.getHeader().setDeliveryCount(new UnsignedInteger(7));
      protonMessage.getHeader().setDurable(Boolean.TRUE);
      protonMessage.setApplicationProperties(new ApplicationProperties(new HashMap<>()));

      final AtomicInteger failures = new AtomicInteger(0);


      for (int testTry = 0; testTry < 100; testTry++) {
         AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
         Thread[] threads = new Thread[100];

         CountDownLatch latchAlign = new CountDownLatch(threads.length);
         CountDownLatch go = new CountDownLatch(1);

         Runnable run = () -> {
            try {

               latchAlign.countDown();
               go.await();

               assertNotNull(decoded.getHeader());
               // this is a method used by Core Converter
               decoded.getProtonMessage();
               assertNotNull(decoded.getHeader());

            } catch (Throwable e) {
               e.printStackTrace();
               failures.incrementAndGet();
            }
         };

         for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(run);
            threads[i].start();
         }

         assertTrue(latchAlign.await(10, TimeUnit.SECONDS));
         go.countDown();

         for (Thread thread : threads) {
            thread.join(5000);
            assertFalse(thread.isAlive());
         }

         assertEquals(0, failures.get());
      }
   }


   @Test
   public void testGetConnectionID() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getConnectionID());
   }

   @Test
   public void testSetConnectionID() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      final String ID = UUID.randomUUID().toString();

      assertNull(decoded.getConnectionID());
      decoded.setConnectionID(ID);
      assertEquals(ID, decoded.getConnectionID());
   }

   @Test
   public void testGetConnectionIDFromProperties() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      final String ID = UUID.randomUUID().toString();

      assertNull(decoded.getConnectionID());
      decoded.setConnectionID(ID);
      assertEquals(ID, decoded.getConnectionID());
      assertEquals(ID, decoded.getStringProperty(MessageUtil.CONNECTION_ID_PROPERTY_NAME));
   }

   //----- Test LastValueProperty access -------------------------------//

   @Test
   public void testGetLastValueFromMessageWithNone() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getLastValueProperty());
   }

   @Test
   public void testSetLastValueFromMessageWithNone() {
      SimpleString lastValue = SimpleString.of("last-address");

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getLastValueProperty());
      decoded.setLastValueProperty(lastValue);
      assertEquals(lastValue, decoded.getLastValueProperty());
   }

   //----- Test User ID access -----------------------------------------//

   @Test
   public void getUserIDWhenNoPropertiesExists() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getUserID());
      decoded.setUserID(UUID.randomUUID().toString());
      assertNull(decoded.getUserID());
   }

   @Test
   public void testSetUserIDHasNoEffectOnMessagePropertiesWhenNotPresent() {
      final String ID = UUID.randomUUID().toString();

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getUserID());
      assertNull(decoded.getProperties());

      decoded.setUserID(ID);
      decoded.reencode();

      assertNull(decoded.getUserID());
      assertNull(decoded.getProperties());
   }

   @Test
   public void testSetUserIDHasNoEffectOnMessagePropertiesWhenPresentButNoMessageID() {
      final String ID = UUID.randomUUID().toString();

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getUserID());
      assertNotNull(decoded.getProperties());
      assertNull(decoded.getProperties().getMessageId());

      decoded.setUserID(ID);
      decoded.reencode();

      assertNull(decoded.getUserID());
      assertNotNull(decoded.getProperties());
      assertNull(decoded.getProperties().getMessageId());
   }

   @Test
   public void testSetUserIDHasNoEffectOnMessagePropertiesWhenPresentWithMessageID() {
      final String ID = UUID.randomUUID().toString();

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      protonMessage.setMessageId(ID);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNotNull(decoded.getUserID());
      assertNotNull(decoded.getProperties());
      assertNotNull(decoded.getProperties().getMessageId());
      assertEquals(ID, decoded.getUserID());

      decoded.setUserID(ID);
      decoded.reencode();

      assertNotNull(decoded.getUserID());
      assertNotNull(decoded.getProperties());
      assertNotNull(decoded.getProperties().getMessageId());
      assertEquals(ID, decoded.getUserID());
   }

   //----- Test the getDuplicateProperty methods -----------------------------//

   @Test
   public void testGetDuplicateProperty() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getDuplicateProperty());
   }

   //----- Test the getAddress methods ---------------------------------------//

   @Test
   public void testGetAddressFromMessage() {
      final String ADDRESS = "myQueue";

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setAddress(ADDRESS);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(ADDRESS, decoded.getAddress());
   }

   @Test
   public void testGetAddressSimpleStringFromMessage() {
      final String ADDRESS = "myQueue";

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setAddress(ADDRESS);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(ADDRESS, decoded.getAddressSimpleString().toString());
   }

   @Test
   public void testGetAddressFromMessageWithNoValueSet() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getAddress());
      assertNull(decoded.getAddressSimpleString());
   }

   @Test
   public void testSetAddressFromMessage() {
      final String ADDRESS = "myQueue";
      final SimpleString NEW_ADDRESS = SimpleString.of("myQueue-1");

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setAddress(ADDRESS);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(ADDRESS, decoded.getAddress());
      decoded.setAddress(NEW_ADDRESS);
      assertEquals(NEW_ADDRESS, decoded.getAddressSimpleString());
   }

   @Test
   public void testSetAddressFromMessageUpdatesPropertiesOnReencode() {
      final String ADDRESS = "myQueue";
      final SimpleString NEW_ADDRESS = SimpleString.of("myQueue-1");

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setAddress(ADDRESS);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(ADDRESS, decoded.getAddress());
      decoded.setAddress(NEW_ADDRESS);
      decoded.reencode();

      assertEquals(NEW_ADDRESS.toString(), decoded.getProperties().getTo());
      assertEquals(NEW_ADDRESS, decoded.getAddressSimpleString());
   }

   //----- Test the durability set and get methods ---------------------------//

   @Test
   public void testIsDurableFromMessageWithHeaderTaggedAsTrue() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setDurable(true);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertTrue(decoded.isDurable());
   }

   @Test
   public void testIsDurableFromMessageWithHeaderTaggedAsFalse() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setDurable(false);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertFalse(decoded.isDurable());
   }

   @Test
   public void testIsDurableFromMessageWithNoValueSet() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertFalse(decoded.isDurable());
   }

   @Test
   public void testIsDuranleReturnsTrueOnceUpdated() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertFalse(decoded.isDurable());
      decoded.setDurable(true);
      assertTrue(decoded.isDurable());
   }

   @Test
   public void testNonDurableMessageReencodedToDurable() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertFalse(decoded.isDurable());

      // Underlying message data not updated yet
      assertNull(decoded.getHeader().getDurable());

      decoded.setDurable(true);
      decoded.reencode();
      assertTrue(decoded.isDurable());

      // Underlying message data now updated
      assertTrue(decoded.getHeader().getDurable());
   }

   @Test
   public void testMessageWithNoHeaderGetsOneWhenDurableSetAndReencoded() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertFalse(decoded.isDurable());

      // Underlying message data not updated yet
      assertNull(decoded.getHeader());

      decoded.setDurable(true);
      decoded.reencode();
      assertTrue(decoded.isDurable());

      // Underlying message data now updated
      Header header = decoded.getHeader();
      assertNotNull(header);
      assertTrue(header.getDurable());
   }

   //----- Test RoutingType access -------------------------------------------//

   @Test
   public void testGetRoutingTypeFromMessageWithoutIt() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getRoutingType());
   }

   @Test
   public void testSetRoutingType() {
      RoutingType type = RoutingType.ANYCAST;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getRoutingType());
      decoded.setRoutingType(type);
      assertEquals(type, decoded.getRoutingType());
   }

   @Test
   public void testSetRoutingTypeToClear() {
      RoutingType type = RoutingType.ANYCAST;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getRoutingType());
      decoded.setRoutingType(type);
      assertEquals(type, decoded.getRoutingType());
      decoded.setRoutingType(null);
      assertNull(decoded.getRoutingType());
   }

   @Test
   public void testRemoveRoutingTypeFromMessageEncodedWithOne() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.ROUTING_TYPE, RoutingType.ANYCAST.getType());
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(RoutingType.ANYCAST, decoded.getRoutingType());
      decoded.setRoutingType(null);
      decoded.reencode();
      assertNull(decoded.getRoutingType());

      assertTrue(decoded.getMessageAnnotations().getValue().isEmpty());
   }

   @Test
   public void testGetRoutingTypeFromMessageWithAnyCastType() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.ROUTING_TYPE, RoutingType.ANYCAST.getType());
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(RoutingType.ANYCAST, decoded.getRoutingType());
   }

   @Test
   public void testGetRoutingTypeFromMessageWithMulticastType() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.ROUTING_TYPE, RoutingType.MULTICAST.getType());
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(RoutingType.MULTICAST, decoded.getRoutingType());
   }

   @Test
   public void testGetRoutingTypeFromMessageWithQueueType() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION, AMQPMessageSupport.QUEUE_TYPE);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(RoutingType.ANYCAST, decoded.getRoutingType());
   }

   @Test
   public void testGetRoutingTypeFromMessageWithTempQueueType() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION, AMQPMessageSupport.TEMP_QUEUE_TYPE);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(RoutingType.ANYCAST, decoded.getRoutingType());
   }

   @Test
   public void testGetRoutingTypeFromMessageWithTopicType() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION, AMQPMessageSupport.TOPIC_TYPE);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(RoutingType.MULTICAST, decoded.getRoutingType());
   }

   @Test
   public void testGetRoutingTypeFromMessageWithTempTopicType() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION, AMQPMessageSupport.TEMP_TOPIC_TYPE);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(RoutingType.MULTICAST, decoded.getRoutingType());
   }

   @Test
   public void testGetRoutingTypeFromMessageWithUnknownType() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.JMS_DEST_TYPE_MSG_ANNOTATION, (byte) 32);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getRoutingType());
   }

   //----- Test access to message Group ID -----------------------------------//

   @Test
   public void testGetGroupIDFromMessage() {
      final String GROUP_ID = "group-1";

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setGroupId(GROUP_ID);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(GROUP_ID, decoded.getGroupID().toString());
   }

   @Test
   public void testGetGroupIDFromMessageWithNoGroupId() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertNull(decoded.getGroupID());
   }

   @Test
   public void testGetGroupIDFromMessageWithNoProperties() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertNull(decoded.getGroupID());
   }

   //----- Test access to message Group ID -----------------------------------//

   @Test
   public void testGetReplyToFromMessage() {
      final String REPLY_TO = "address-1";

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setReplyTo(REPLY_TO);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(REPLY_TO, decoded.getReplyTo().toString());
   }

   @Test
   public void testGetReplyToFromMessageWithNoReplyTo() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertNull(decoded.getReplyTo());
   }

   @Test
   public void testGetReplyToFromMessageWithNoProperties() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertNull(decoded.getReplyTo());
   }

   @Test
   public void testSetReplyToFromMessageWithProperties() {
      final String REPLY_TO = "address-1";

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertNull(decoded.getReplyTo());

      decoded.setReplyTo(SimpleString.of(REPLY_TO));
      decoded.reencode();

      assertEquals(REPLY_TO, decoded.getReplyTo().toString());
      assertEquals(REPLY_TO, decoded.getProperties().getReplyTo());
   }

   @Test
   public void testSetReplyToFromMessageWithNoProperties() {
      final String REPLY_TO = "address-1";

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertNull(decoded.getReplyTo());

      decoded.setReplyTo(SimpleString.of(REPLY_TO));
      decoded.reencode();

      assertEquals(REPLY_TO, decoded.getReplyTo().toString());
      assertEquals(REPLY_TO, decoded.getProperties().getReplyTo());
   }

   @Test
   public void testSetReplyToFromMessageWithPropertiesCanClear() {
      final String REPLY_TO = "address-1";

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      protonMessage.setReplyTo(REPLY_TO);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertEquals(REPLY_TO, decoded.getReplyTo().toString());

      decoded.setReplyTo(null);
      decoded.reencode();

      assertNull(decoded.getReplyTo());
      assertNull(decoded.getProperties().getReplyTo());
   }

   //----- Test access to User ID --------------------------------------------//

   @Test
   public void testGetUserIDFromMessage() {
      final String USER_NAME = "foo";

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setUserId(USER_NAME.getBytes(StandardCharsets.UTF_8));
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(USER_NAME, decoded.getAMQPUserID());
   }

   @Test
   public void testGetUserIDFromMessageWithNoProperties() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getAMQPUserID());
   }

   @Test
   public void testGetUserIDFromMessageWithNoUserID() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getAMQPUserID());
   }

   //----- Test access message priority --------------------------------------//

   @Test
   public void testGetPriorityFromMessage() {
      final short PRIORITY = 7;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setPriority(PRIORITY);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(PRIORITY, decoded.getPriority());
   }

   @Test
   public void testGetPriorityFromMessageWithNoHeader() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(AMQPStandardMessage.DEFAULT_MESSAGE_PRIORITY, decoded.getPriority());
   }

   @Test
   public void testGetPriorityFromMessageWithNoPrioritySet() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(AMQPStandardMessage.DEFAULT_MESSAGE_PRIORITY, decoded.getPriority());
   }

   @Test
   public void testSetPriorityOnMessageWithHeader() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(AMQPStandardMessage.DEFAULT_MESSAGE_PRIORITY, decoded.getPriority());

      decoded.setPriority((byte) 9);
      decoded.reencode();

      assertEquals(9, decoded.getPriority());
      assertEquals(9, decoded.getHeader().getPriority().byteValue());
   }

   @Test
   public void testSetPriorityOnMessageWithoutHeader() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(AMQPStandardMessage.DEFAULT_MESSAGE_PRIORITY, decoded.getPriority());

      decoded.setPriority((byte) 9);
      decoded.reencode();

      assertEquals(9, decoded.getPriority());
      assertEquals(9, decoded.getHeader().getPriority().byteValue());
   }

   //----- Test access message expiration ------------------------------------//

   @Test
   public void testGetExpirationFromMessageWithNoHeader() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0, decoded.getExpiration());
   }

   @Test
   public void testGetExpirationFromMessageWithNoTTLInHeader() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0, decoded.getExpiration());
   }

   @Test
   public void testGetExpirationFromMessageWithNoTTLInHeaderOrExpirationInProperties() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setProperties(new Properties());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0, decoded.getExpiration());
   }

   @Test
   public void testGetExpirationFromMessageUsingTTL() {
      final long ttl = 100000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setTtl(ttl);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertTrue(decoded.getExpiration() > System.currentTimeMillis());
   }

   @Test
   public void testGetExpirationFromMessageWithMaxUIntTTL() {
      final long ttl = UnsignedInteger.MAX_VALUE.longValue();

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setTtl(ttl);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertTrue(decoded.getExpiration() > System.currentTimeMillis());
   }

   @Test
   public void testGetExpirationFromCoreMessageUsingTTL() {
      final long ttl = 100000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setTtl(ttl);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      ICoreMessage coreMessage = decoded.toCore();
      assertEquals(decoded.getExpiration(), coreMessage.getExpiration());
   }

   @Test
   public void testGetExpirationFromMessageUsingAbsoluteExpiration() {
      final Date expirationTime = new Date(System.currentTimeMillis());

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      Properties properties = new Properties();
      properties.setAbsoluteExpiryTime(expirationTime);
      protonMessage.setProperties(properties);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(expirationTime.getTime(), decoded.getExpiration());
   }

   @Test
   public void testGetExpirationFromMessageUsingAbsoluteExpirationNegative() {
      final Date expirationTime = new Date(-1);

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      Properties properties = new Properties();
      properties.setAbsoluteExpiryTime(expirationTime);
      protonMessage.setProperties(properties);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0, decoded.getExpiration());
   }

   @Test
   public void testGetExpirationFromMessageAbsoluteExpirationOVerrideTTL() {
      final Date expirationTime = new Date(System.currentTimeMillis());
      final long ttl = 100000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setTtl(ttl);
      Properties properties = new Properties();
      properties.setAbsoluteExpiryTime(expirationTime);
      protonMessage.setProperties(properties);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(expirationTime.getTime(), decoded.getExpiration());
   }

   @Test
   public void testSetExpiration() {
      final Date expirationTime = new Date(System.currentTimeMillis());

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0, decoded.getExpiration());
      decoded.setExpiration(expirationTime.getTime());
      assertEquals(expirationTime.getTime(), decoded.getExpiration());
   }

   @Test
   public void testSetExpirationMaxUInt() {
      final Date expirationTime = new Date(UnsignedInteger.MAX_VALUE.longValue());

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0, decoded.getExpiration());
      decoded.setExpiration(expirationTime.getTime());
      assertEquals(expirationTime.getTime(), decoded.getExpiration());
   }

   @Test
   public void testSetExpirationUpdatesProperties() {
      final Date originalExpirationTime = new Date(System.currentTimeMillis());
      final Date expirationTime = new Date(System.currentTimeMillis());

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      protonMessage.setExpiryTime(originalExpirationTime.getTime());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(originalExpirationTime.getTime(), decoded.getExpiration());
      decoded.setExpiration(expirationTime.getTime());
      assertEquals(expirationTime.getTime(), decoded.getExpiration());

      decoded.reencode();
      assertEquals(expirationTime, decoded.getProperties().getAbsoluteExpiryTime());
   }

   @Test
   public void testSetExpirationAddsPropertiesWhenNonePresent() {
      final Date expirationTime = new Date(System.currentTimeMillis());

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0, decoded.getExpiration());
      decoded.setExpiration(expirationTime.getTime());
      assertEquals(expirationTime.getTime(), decoded.getExpiration());

      decoded.reencode();
      assertEquals(expirationTime, decoded.getProperties().getAbsoluteExpiryTime());
   }

   @Test
   public void testSetExpirationToClearUpdatesPropertiesWhenPresent() {
      final Date expirationTime = new Date(System.currentTimeMillis());

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      protonMessage.setExpiryTime(expirationTime.getTime());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(expirationTime.getTime(), decoded.getExpiration());
      decoded.setExpiration(-1);
      assertEquals(0, decoded.getExpiration());

      decoded.reencode();
      assertEquals(0, decoded.getExpiration());
      assertNull(decoded.getProperties().getAbsoluteExpiryTime());
   }

   @Test
   public void testSetExpirationToClearDoesNotAddPropertiesWhenNonePresent() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0, decoded.getExpiration());
      decoded.setExpiration(-1);
      assertEquals(0, decoded.getExpiration());

      decoded.reencode();
      assertEquals(0, decoded.getExpiration());
      assertNull(decoded.getProperties());
   }

   @Test
   public void testSetExpirationToClearUpdateHeader() {
      final long ttl = 100000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      protonMessage.setTtl(ttl);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertTrue(decoded.getExpiration() > System.currentTimeMillis());

      decoded.setExpiration(-1);
      decoded.reencode();

      assertEquals(0, decoded.getExpiration());
      assertNull(decoded.getHeader().getTtl());
   }

   //----- Test access message time stamp ------------------------------------//

   @Test
   public void testGetTimestampFromMessage() {
      Date timestamp = new Date(System.currentTimeMillis());

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      Properties properties = new Properties();
      properties.setCreationTime(timestamp);
      protonMessage.setProperties(properties);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(timestamp.getTime(), decoded.getTimestamp());
   }

   @Test
   public void testGetTimestampFromMessageWithNoCreateTimeSet() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setHeader(new Header());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0L, decoded.getTimestamp());
   }

   @Test
   public void testGetTimestampFromMessageWithNoHeader() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0L, decoded.getTimestamp());
   }

   @Test
   public void testSetTimestampOnMessage() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      protonMessage.setProperties(new Properties());
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0L, decoded.getTimestamp());

      Date createTime = new Date(System.currentTimeMillis());

      decoded.setTimestamp(createTime.getTime());
      decoded.reencode();

      assertEquals(createTime.getTime(), decoded.getTimestamp());
      assertEquals(createTime, decoded.getProperties().getCreationTime());
   }

   @Test
   public void testSetTimestampOnMessageWithNoPropertiesSection() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0L, decoded.getTimestamp());

      Date createTime = new Date(System.currentTimeMillis());

      decoded.setTimestamp(createTime.getTime());
      decoded.reencode();

      assertNotNull(decoded.getProperties());
      assertEquals(createTime.getTime(), decoded.getTimestamp());
      assertEquals(createTime, decoded.getProperties().getCreationTime());
   }

   //----- Test access to message scheduled delivery time --------------------//

   @Test
   public void testGetScheduledDeliveryTimeMessageSentWithFixedTime() {
      final long scheduledTime = System.currentTimeMillis();

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME, scheduledTime);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(scheduledTime, decoded.getScheduledDeliveryTime().longValue());
   }

   @Test
   public void testGetScheduledDeliveryTimeMessageSentWithFixedTimeAndDelay() {
      final long scheduledTime = System.currentTimeMillis();
      final long scheduledDelay = 100000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY, scheduledDelay);
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME, scheduledTime);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(scheduledTime, decoded.getScheduledDeliveryTime().longValue());
   }

   @Test
   public void testGetScheduledDeliveryTimeMessageSentWithFixedDelay() {
      final long scheduledDelay = 100000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY, scheduledDelay);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertTrue(decoded.getScheduledDeliveryTime().longValue() > System.currentTimeMillis());
   }

   @Test
   public void testGetScheduledDeliveryTimeWhenMessageHasNoSetValue() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      assertEquals(0, decoded.getScheduledDeliveryTime().longValue());
   }

   @Test
   public void testSetScheduledDeliveryTimeWhenNonPresent() {
      final long scheduledTime = System.currentTimeMillis() + 5000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(0, decoded.getScheduledDeliveryTime().longValue());
      decoded.setScheduledDeliveryTime(scheduledTime);
      assertEquals(scheduledTime, decoded.getScheduledDeliveryTime().longValue());

      decoded.reencode();

      assertEquals(scheduledTime, decoded.getMessageAnnotations().getValue().get(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME));
   }

   @Test
   public void testSetScheduledDeliveryTimeMessageSentWithFixedTime() {
      final long scheduledTime = System.currentTimeMillis();
      final long newScheduledTime = System.currentTimeMillis() + 1000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME, scheduledTime);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(scheduledTime, decoded.getScheduledDeliveryTime().longValue());

      decoded.setScheduledDeliveryTime(newScheduledTime);
      assertEquals(newScheduledTime, decoded.getScheduledDeliveryTime().longValue());
      decoded.reencode();
      assertEquals(newScheduledTime, decoded.getMessageAnnotations().getValue().get(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME));
   }

   @Test
   public void testSetScheduledDeliveryTimeMessageSentWithFixedDelay() {
      final long scheduledDelay = 100000;
      final long newScheduledTime = System.currentTimeMillis() + 1000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY, scheduledDelay);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertTrue(decoded.getScheduledDeliveryTime().longValue() > System.currentTimeMillis());

      decoded.setScheduledDeliveryTime(newScheduledTime);
      assertEquals(newScheduledTime, decoded.getScheduledDeliveryTime().longValue());
      decoded.reencode();
      assertEquals(newScheduledTime, decoded.getMessageAnnotations().getValue().get(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME));
   }

   @Test
   public void testSetScheduledDeliveryTimeToNoneClearsDelayAndTimeValues() {
      final long scheduledTime = System.currentTimeMillis();
      final long scheduledDelay = 100000;

      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY, scheduledDelay);
      annotations.getValue().put(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME, scheduledTime);
      protonMessage.setMessageAnnotations(annotations);
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertEquals(scheduledTime, decoded.getScheduledDeliveryTime().longValue());

      decoded.setScheduledDeliveryTime((long) 0);
      decoded.reencode();
      assertNull(decoded.getMessageAnnotations().getValue().get(AMQPMessageSupport.SCHEDULED_DELIVERY_TIME));
      assertNull(decoded.getMessageAnnotations().getValue().get(AMQPMessageSupport.SCHEDULED_DELIVERY_DELAY));
   }

   //----- Tests access to Message Annotations -------------------------------//

   @Test
   public void testGetAnnotation() {
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null);

      Object result = message.getAnnotation(SimpleString.of(TEST_MESSAGE_ANNOTATION_KEY));
      String stringResult = message.getAnnotationString(SimpleString.of(TEST_MESSAGE_ANNOTATION_KEY));

      assertEquals(result, stringResult);
   }

   @Test
   public void testRemoveAnnotation() {
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null);

      assertNotNull(message.getAnnotation(SimpleString.of(TEST_MESSAGE_ANNOTATION_KEY)));
      message.removeAnnotation(SimpleString.of(TEST_MESSAGE_ANNOTATION_KEY));
      assertNull(message.getAnnotation(SimpleString.of(TEST_MESSAGE_ANNOTATION_KEY)));

      message.reencode();

      assertTrue(message.getMessageAnnotations().getValue().isEmpty());
   }

   @Test
   public void testSetAnnotation() {
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null);

      final SimpleString newAnnotation = SimpleString.of("testSetAnnotation");
      final String newValue = "newValue";

      message.setAnnotation(newAnnotation, newValue);
      assertEquals(newValue, message.getAnnotation(newAnnotation));

      message.reencode();

      assertEquals(newValue, message.getMessageAnnotations().getValue().get(Symbol.valueOf(newAnnotation.toString())));
   }

   //----- Tests accessing Proton Sections from encoded data -----------------//

   @Test
   public void testGetProtonMessage() {
      MessageImpl protonMessage = createProtonMessage();
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      assertProtonMessageEquals(protonMessage, message.getProtonMessage());

      message.setAnnotation(SimpleString.of("testGetProtonMessage"), "1");
      message.messageChanged();

      assertProtonMessageNotEquals(protonMessage, message.getProtonMessage());
   }

   @Test
   public void testGetHeader() {
      MessageImpl protonMessage = createProtonMessage();
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      Header decoded = message.getHeader();
      assertNotSame(decoded, protonMessage.getHeader());
      assertHeaderEquals(protonMessage.getHeader(), decoded);

      // Update the values
      decoded.setDeliveryCount(UnsignedInteger.ZERO);
      decoded.setTtl(UnsignedInteger.valueOf(255));
      decoded.setFirstAcquirer(true);

      // Check that the message is unaffected.
      assertHeaderNotEquals(protonMessage.getHeader(), decoded);
   }

   @Test
   public void testGetProperties() {
      MessageImpl protonMessage = createProtonMessage();
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      Properties decoded = message.getProperties();
      assertNotSame(decoded, protonMessage.getProperties());
      assertPropertiesEquals(protonMessage.getProperties(), decoded);

      // Update the values
      decoded.setAbsoluteExpiryTime(new Date(System.currentTimeMillis()));
      decoded.setGroupSequence(UnsignedInteger.valueOf(255));
      decoded.setSubject(UUID.randomUUID().toString());

      // Check that the message is unaffected.
      assertPropertiesNotEquals(protonMessage.getProperties(), decoded);
   }

   @Test
   public void testGetDeliveryAnnotations() {
      MessageImpl protonMessage = createProtonMessage();
      DeliveryAnnotations deliveryAnnotations = new DeliveryAnnotations(new HashMap<>());
      deliveryAnnotations.getValue().put(Symbol.valueOf(UUID.randomUUID().toString()), "test-1");
      protonMessage.setDeliveryAnnotations(deliveryAnnotations);

      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      DeliveryAnnotations decoded = message.getDeliveryAnnotations();
      assertNotSame(decoded, protonMessage.getDeliveryAnnotations());
      assertDeliveryAnnotationsEquals(protonMessage.getDeliveryAnnotations(), decoded);

      // Update the values
      decoded.getValue().put(Symbol.valueOf(UUID.randomUUID().toString()), "test-2");

      // Check that the message is unaffected.
      assertDeliveryAnnotationsNotEquals(protonMessage.getDeliveryAnnotations(), decoded);
   }

   @Test
   public void testGetMessageAnnotations() {
      MessageImpl protonMessage = createProtonMessage();
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      MessageAnnotations decoded = message.getMessageAnnotations();
      assertNotSame(decoded, protonMessage.getMessageAnnotations());
      assertMessageAnnotationsEquals(protonMessage.getMessageAnnotations(), decoded);

      // Update the values
      decoded.getValue().put(Symbol.valueOf(UUID.randomUUID().toString()), "test");

      // Check that the message is unaffected.
      assertMessageAnnotationsNotEquals(protonMessage.getMessageAnnotations(), decoded);
   }

   @Test
   public void testGetApplicationProperties() {
      MessageImpl protonMessage = createProtonMessage();
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      ApplicationProperties decoded = message.getApplicationProperties();
      assertNotSame(decoded, protonMessage.getApplicationProperties());
      assertApplicationPropertiesEquals(protonMessage.getApplicationProperties(), decoded);

      // Update the values
      decoded.getValue().put(UUID.randomUUID().toString(), "test");

      // Check that the message is unaffected.
      assertApplicationPropertiesNotEquals(protonMessage.getApplicationProperties(), decoded);
   }

   @Test
   public void testGetBody() {
      MessageImpl protonMessage = createProtonMessage();
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      Object body = message.getBody();
      assertTrue(body instanceof AmqpValue);
      AmqpValue amqpValueBody = (AmqpValue) body;

      assertNotNull(amqpValueBody.getValue());
      assertNotSame(((AmqpValue)protonMessage.getBody()).getValue(), amqpValueBody.getValue());
      assertEquals(((AmqpValue)protonMessage.getBody()).getValue(), amqpValueBody.getValue());
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testGetFooter() {
      MessageImpl protonMessage = createProtonMessage();
      Footer footer = new Footer(new HashMap<>());
      footer.getValue().put(Symbol.valueOf(UUID.randomUUID().toString()), "test-1");
      protonMessage.setFooter(footer);

      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      Footer decoded = message.getFooter();
      assertNotSame(decoded, protonMessage.getFooter());
      assertFootersEquals(protonMessage.getFooter(), decoded);

      // Update the values
      decoded.getValue().put(Symbol.valueOf(UUID.randomUUID().toString()), "test-2");

      // Check that the message is unaffected.
      assertFootersNotEquals(protonMessage.getFooter(), decoded);
   }

   //----- Test re-encode of updated message sections ------------------------//

   @Test
   public void testApplicationPropertiesReencodeAfterUpdate() {
      MessageImpl protonMessage = createProtonMessage();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertProtonMessageEquals(protonMessage, decoded.getProtonMessage());

      decoded.putStringProperty("key-2", "value-2");
      decoded.reencode();

      assertProtonMessageNotEquals(protonMessage, decoded.getProtonMessage());

      assertEquals(TEST_APPLICATION_PROPERTY_VALUE, decoded.getStringProperty(TEST_APPLICATION_PROPERTY_KEY));
      assertEquals("value-2", decoded.getStringProperty("key-2"));
   }

   @Test
   public void testMessageAnnotationsReencodeAfterUpdate() {
      final SimpleString TEST_ANNOTATION = SimpleString.of("testMessageAnnotationsReencodeAfterUpdate");

      MessageImpl protonMessage = createProtonMessage();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertProtonMessageEquals(protonMessage, decoded.getProtonMessage());

      decoded.setAnnotation(TEST_ANNOTATION, "value-2");
      decoded.reencode();

      assertProtonMessageNotEquals(protonMessage, decoded.getProtonMessage());

      assertEquals("value-2", decoded.getAnnotation(TEST_ANNOTATION));
   }

   //----- Test handling of message extra properties -------------------------//

   @Test
   public void testExtraByteProperty() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();

      byte[] value = RandomUtil.randomBytes();
      SimpleString name = SimpleString.of("myProperty");

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      assertNull(decoded.getExtraProperties());
      assertNull(decoded.getExtraBytesProperty(name));
      assertNull(decoded.removeExtraBytesProperty(name));

      decoded.putExtraBytesProperty(name, value);
      assertFalse(decoded.getExtraProperties().isEmpty());

      assertArrayEquals(value, decoded.getExtraBytesProperty(name));
      assertArrayEquals(value, decoded.removeExtraBytesProperty(name));
   }

   @Test
   public void testJournalPersistence() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();

      byte[] original = RandomUtil.randomBytes();
      SimpleString name = SimpleString.of("myProperty");
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      decoded.setAddress("someAddress");
      decoded.setMessageID(33);
      decoded.setPriority((byte) 2);
      decoded.putStringProperty("hello", "world");
      decoded.putExtraBytesProperty(name, original);
      decoded.reencode();

      ActiveMQBuffer buffer = ActiveMQBuffers.pooledBuffer(10 * 1024);
      try {
         decoded.getPersister().encode(buffer, decoded);
         assertEquals(AMQPMessagePersisterV4.getInstance().getID(), buffer.readByte()); // the journal reader will read 1 byte to find the persister
         AMQPStandardMessage readMessage = (AMQPStandardMessage)decoded.getPersister().decode(buffer, null, null);
         assertEquals(33, readMessage.getMessageID());
         assertEquals("someAddress", readMessage.getAddress());
         assertArrayEquals(original, readMessage.getExtraBytesProperty(name));
         assertEquals((byte) 2, readMessage.getPriority());
         assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, readMessage.getDataScanningStatus());
         assertEquals("world", readMessage.getStringProperty("hello"));
         assertEquals(AMQPMessage.MessageDataScanningStatus.SCANNED, readMessage.getDataScanningStatus());

      } finally {
         buffer.release();
      }

      {
         ICoreMessage embeddedMessage = EmbedMessageUtil.embedAsCoreMessage(decoded, EmbedMessageUtil.LATEST_WIRE_VERSION);
         AMQPStandardMessage readMessage = (AMQPStandardMessage) EmbedMessageUtil.extractEmbedded(embeddedMessage, null);
         assertEquals(33, readMessage.getMessageID());
         assertEquals("someAddress", readMessage.getAddress());
         assertArrayEquals(original, readMessage.getExtraBytesProperty(name));
      }
   }

   @Test
   public void testMixEncodingNoApplicationProperties() {

      boolean[] booleanValues = new boolean[] {false, true};

      // play with permutations
      for (boolean a : booleanValues) {
         for (boolean b : booleanValues) {
            for (boolean c : booleanValues) {
               internalMessageNoApplicationProperteis(a, b, c);
            }
         }
      }
   }

   private void internalMessageNoApplicationProperteis(boolean useHeader, boolean useAnnotations, boolean useBody) {
      MessageImpl message = (MessageImpl) Proton.message();

      if (useHeader) {
         message.setHeader(new Header());
      }

      if (useAnnotations) {
         MessageAnnotations annotations = new MessageAnnotations(new LinkedHashMap<>());
         for (int i = 0; i < 100; ++i) {
            annotations.getValue().put(Symbol.valueOf(TEST_MESSAGE_ANNOTATION_KEY + ":" + i), TEST_MESSAGE_ANNOTATION_VALUE + ":" + i);
         }
         message.setMessageAnnotations(annotations);
      } else {
         message.setMessageAnnotations(null);
      }

      if (useBody) {
         message.setBody(new AmqpValue(TEST_STRING_BODY));
      } else {
         message.setBody(null);
      }

      byte[] encodedProtonMessage = encodeMessage(message);

      AMQPStandardMessage decoded = new AMQPStandardMessage(0, encodedProtonMessage, null, null);

      final int expectedEstimate = AMQPMessage.MINIMUM_ESTIMATE + encodedProtonMessage.length;
      assertEquals(expectedEstimate, decoded.getMemoryEstimate());
   }


   @Test
   public void testNonSenseNullApplicationProperties() {
      ByteBuffer buffer = ByteBuffer.allocate(4);

      // this next section is simulating an invalid encoding, to validate this portion of the decoder.
      // https://github.com/apache/qpid-proton-j/blob/6dc5587f1d1b23969a8994f1755198e638e92bc4/proton-j/src/main/java/org/apache/qpid/proton/codec/messaging/FastPathApplicationPropertiesType.java#L111-L112
      // this is basically simulating message.setApplicationProperties(new ApplicationProperties(null));
      byte[] encoded;
      {
         // based on https://github.com/apache/qpid-proton-j/blob/6dc5587f1d1b23969a8994f1755198e638e92bc4/proton-j/src/main/java/org/apache/qpid/proton/codec/messaging/FastPathApplicationPropertiesType.java#L164-L167
         buffer.put(EncodingCodes.DESCRIBED_TYPE_INDICATOR);
         buffer.put(EncodingCodes.SMALLULONG);
         buffer.put((byte) 0x74);

         buffer.put(EncodingCodes.NULL);

         encoded = new byte[buffer.position()];
         buffer.position(0);
         buffer.get(encoded);
      }

      AMQPStandardMessage decoded = new AMQPStandardMessage(0, encoded, null, null);
      assertEquals(0, decoded.getApplicationPropertiesCount());
      decoded.getMemoryEstimate(); // calling this method to make sure it won't throw exceptions

   }

   //----- Test that message decode ignores unused sections ------------------//

   private static final UnsignedLong AMQPVALUE_DESCRIPTOR = UnsignedLong.valueOf(0x0000000000000077L);
   private static final UnsignedLong APPLICATION_PROPERTIES_DESCRIPTOR = UnsignedLong.valueOf(0x0000000000000074L);

   @Test
   public void testPartialDecodeIgnoresApplicationPropertiesByDefault() {
      Header header = new Header();
      header.setDurable(true);
      header.setPriority(UnsignedByte.valueOf((byte) 6));

      ByteBuf encodedBytes = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(encodedBytes);

      EncoderImpl encoder = TLSEncode.getEncoder();
      encoder.setByteBuffer(writable);
      encoder.writeObject(header);

      // Signal body of AmqpValue but write corrupt underlying type info
      encodedBytes.writeByte(EncodingCodes.DESCRIBED_TYPE_INDICATOR);
      encodedBytes.writeByte(EncodingCodes.SMALLULONG);
      encodedBytes.writeByte(APPLICATION_PROPERTIES_DESCRIPTOR.byteValue());
      encodedBytes.writeByte(EncodingCodes.MAP8);
      encodedBytes.writeByte(2);  // Size
      encodedBytes.writeByte(2);  // Elements
      // Use bad encoding code on underlying type of map key which will fail the decode if run
      encodedBytes.writeByte(255);

      ReadableBuffer readable = new NettyReadable(encodedBytes);

      AMQPStandardMessage message = null;
      try {
         message = new AMQPStandardMessage(0, readable, null, null);
      } catch (Exception decodeError) {
         fail("Should not have encountered an exception on partial decode: " + decodeError.getMessage());
      }

      assertTrue(message.isDurable());

      try {
         // This should perform the lazy decode of the ApplicationProperties portion of the message
         message.getStringProperty("test");
         fail("Should have thrown an error when attempting to decode the ApplicationProperties which are malformed.");
      } catch (Exception ex) {
         // Expected decode to fail when building full message.
      }
   }

   @Test
   public void testPartialDecodeIgnoresBodyByDefault() {
      Header header = new Header();
      header.setDurable(true);
      header.setPriority(UnsignedByte.valueOf((byte) 6));

      ByteBuf encodedBytes = Unpooled.buffer(1024);
      NettyWritable writable = new NettyWritable(encodedBytes);

      EncoderImpl encoder = TLSEncode.getEncoder();
      encoder.setByteBuffer(writable);
      encoder.writeObject(header);

      // Signal body of AmqpValue but write corrupt underlying type info
      encodedBytes.writeByte(EncodingCodes.DESCRIBED_TYPE_INDICATOR);
      encodedBytes.writeByte(EncodingCodes.SMALLULONG);
      encodedBytes.writeByte(AMQPVALUE_DESCRIPTOR.byteValue());
      // Use bad encoding code on underlying type
      encodedBytes.writeByte(255);

      ReadableBuffer readable = new NettyReadable(encodedBytes);

      AMQPStandardMessage message = null;
      try {
         message = new AMQPStandardMessage(0, readable, null, null);
      } catch (Exception decodeError) {
         fail("Should not have encountered an exception on partial decode: " + decodeError.getMessage());
      }

      assertTrue(message.isDurable());

      try {
         // This will decode the body section if present in order to present it as a Proton Message object
         message.getBody();
         fail("Should have thrown an error when attempting to decode the body which is malformed.");
      } catch (Exception ex) {
         // Expected decode to fail when building full message.
      }
   }

   //----- Tests for message copy correctness --------------------------------//

   @Test
   public void testCopyMessage() {
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null, null);
      message.setMessageID(127);
      AMQPStandardMessage copy = (AMQPStandardMessage) message.copy();

      assertEquals(message.getMessageID(), copy.getMessageID());
      assertProtonMessageEquals(message.getProtonMessage(), copy.getProtonMessage());
   }

   @Test
   public void testCopyMessageWithNewArtemisMessageID() {
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null, null);
      message.setMessageID(127);
      AMQPStandardMessage copy = (AMQPStandardMessage) message.copy(255);

      assertNotEquals(message.getMessageID(), copy.getMessageID());
      assertProtonMessageEquals(message.getProtonMessage(), copy.getProtonMessage());
   }

   @Test
   public void testCopyMessageDoesNotRemovesMessageAnnotations() {
      MessageImpl protonMessage = createProtonMessage();
      DeliveryAnnotations deliveryAnnotations = new DeliveryAnnotations(new HashMap<>());
      deliveryAnnotations.getValue().put(Symbol.valueOf("testCopyMessageRemovesMessageAnnotations"), "1");
      protonMessage.setDeliveryAnnotations(deliveryAnnotations);

      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);
      message.setMessageID(127);
      AMQPStandardMessage copy = (AMQPStandardMessage) message.copy();

      assertEquals(message.getMessageID(), copy.getMessageID());
      assertProtonMessageEquals(message.getProtonMessage(), copy.getProtonMessage());
      assertNotNull(copy.getDeliveryAnnotations());
   }

   @Test
   public void testDecodeCopyUpdateReencodeAndThenDecodeAgain() {
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null, null);

      // Sanity checks
      assertTrue(message.isDurable());
      assertEquals(TEST_STRING_BODY, ((AmqpValue) message.getBody()).getValue());

      // Copy the message
      message = (AMQPStandardMessage) message.copy();

      // Sanity checks
      assertTrue(message.isDurable());
      assertEquals(TEST_STRING_BODY, ((AmqpValue) message.getBody()).getValue());

      // Update the message
      message.setAnnotation(SimpleString.of("x-opt-extra-1"), "test-1");
      message.setAnnotation(SimpleString.of("x-opt-extra-2"), "test-2");
      message.setAnnotation(SimpleString.of("x-opt-extra-3"), "test-3");

      // Reencode and then decode the message again
      message.reencode();

      // Sanity checks
      assertTrue(message.isDurable());
      assertEquals(TEST_STRING_BODY, ((AmqpValue) message.getBody()).getValue());
   }

   //----- Test sendBuffer method --------------------------------------------//

   @Test
   public void testSendBuffer() {
      ByteBuf buffer = Unpooled.buffer(255);
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null, null);

      message.sendBuffer(buffer, 1);

      assertNotNull(buffer);

      AMQPStandardMessage copy = new AMQPStandardMessage(0, new NettyReadable(buffer), null, null);

      assertProtonMessageEquals(message.getProtonMessage(), copy.getProtonMessage());
   }

   //----- Test getSendBuffer variations -------------------------------------//

   @Test
   public void testGetSendBuffer() {
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null, null);

      ReadableBuffer buffer = message.getSendBuffer(1, null);
      assertNotNull(buffer);
      assertTrue(buffer.hasArray());

      assertArrayEquals(encodedProtonMessage, buffer.array());

      AMQPStandardMessage copy = new AMQPStandardMessage(0, buffer, null, null);

      assertProtonMessageEquals(message.getProtonMessage(), copy.getProtonMessage());
   }

   @Test
   public void testGetSendBufferAddsDeliveryCountOnlyToSendMessage() {
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null, null);

      ReadableBuffer buffer = message.getSendBuffer(7, null);
      assertNotNull(buffer);
      message.reencode(); // Ensures Header is current if accidentally updated

      AMQPStandardMessage copy = new AMQPStandardMessage(0, buffer, null, null);

      MessageImpl originalsProtonMessage = message.getProtonMessage();
      MessageImpl copyProtonMessage = copy.getProtonMessage();
      assertProtonMessageNotEquals(originalsProtonMessage, copyProtonMessage);

      assertNull(originalsProtonMessage.getHeader().getDeliveryCount());
      assertEquals(6, copyProtonMessage.getHeader().getDeliveryCount().intValue());
   }

   @Test
   public void testGetSendBufferAddsDeliveryCountOnlyToSendMessageOriginalHadNoHeader() {
      MessageImpl protonMessage = (MessageImpl) Proton.message();
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      ReadableBuffer buffer = message.getSendBuffer(7, null);
      assertNotNull(buffer);
      message.reencode(); // Ensures Header is current if accidentally updated

      AMQPStandardMessage copy = new AMQPStandardMessage(0, buffer, null, null);

      MessageImpl originalsProtonMessage = message.getProtonMessage();
      MessageImpl copyProtonMessage = copy.getProtonMessage();
      assertProtonMessageNotEquals(originalsProtonMessage, copyProtonMessage);

      assertNull(originalsProtonMessage.getHeader());
      assertEquals(6, copyProtonMessage.getHeader().getDeliveryCount().intValue());
   }

   @Test
   public void testGetSendBufferRemoveDeliveryAnnotations() {
      MessageImpl protonMessage = createProtonMessage();
      DeliveryAnnotations deliveryAnnotations = new DeliveryAnnotations(new HashMap<>());
      deliveryAnnotations.getValue().put(Symbol.valueOf("testGetSendBufferRemoveDeliveryAnnotations"), "X");
      protonMessage.setDeliveryAnnotations(deliveryAnnotations);
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      ReadableBuffer buffer = message.getSendBuffer(1, null);
      assertNotNull(buffer);

      AMQPStandardMessage copy = new AMQPStandardMessage(0, buffer, null, null);

      MessageImpl copyProtonMessage = copy.getProtonMessage();
      assertProtonMessageNotEquals(message.getProtonMessage(), copyProtonMessage);
      assertNull(copyProtonMessage.getDeliveryAnnotations());
   }

   @Test
   public void testGetSendBufferAddsDeliveryCountOnlyToSendMessageAndTrimsDeliveryAnnotations() {
      MessageImpl protonMessage = createProtonMessage();
      DeliveryAnnotations deliveryAnnotations = new DeliveryAnnotations(new HashMap<>());
      deliveryAnnotations.getValue().put(Symbol.valueOf("testGetSendBufferRemoveDeliveryAnnotations"), "X");
      protonMessage.setDeliveryAnnotations(deliveryAnnotations);
      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      ReadableBuffer buffer = message.getSendBuffer(7, null);
      assertNotNull(buffer);
      message.reencode(); // Ensures Header is current if accidentally updated

      AMQPStandardMessage copy = new AMQPStandardMessage(0, buffer, null, null);

      MessageImpl originalsProtonMessage = message.getProtonMessage();
      MessageImpl copyProtonMessage = copy.getProtonMessage();
      assertProtonMessageNotEquals(originalsProtonMessage, copyProtonMessage);

      assertNull(originalsProtonMessage.getHeader().getDeliveryCount());
      assertEquals(6, copyProtonMessage.getHeader().getDeliveryCount().intValue());
      assertNull(copyProtonMessage.getDeliveryAnnotations());
   }

   //----- Test reencode method ----------------------------------------------//

   @Test
   public void testReencodeOnMessageWithNoPayoad() {
      doTestMessageReencodeProducesEqualMessage(false, false, false, false, false, false, false);
   }

   @Test
   public void testReencodeOnMessageWithFullPayoad() {
      doTestMessageReencodeProducesEqualMessage(true, true, true, true, true, true, true);
   }

   @Test
   public void testReencodeOnMessageWithHeadersOnly() {
      doTestMessageReencodeProducesEqualMessage(true, false, false, false, false, false, false);
   }

   @Test
   public void testReencodeOnMessageWithDeliveryAnnotationsOnly() {
      doTestMessageReencodeProducesEqualMessage(false, true, false, false, false, false, false);
   }

   @Test
   public void testReencodeOnMessageWithMessageAnnotationsOnly() {
      doTestMessageReencodeProducesEqualMessage(false, false, true, false, false, false, false);
   }

   @Test
   public void testReencodeOnMessageWithPropertiesOnly() {
      doTestMessageReencodeProducesEqualMessage(false, false, false, true, false, false, false);
   }

   @Test
   public void testReencodeOnMessageWithApplicationPropertiesOnly() {
      doTestMessageReencodeProducesEqualMessage(false, false, false, false, true, false, false);
   }

   @Test
   public void testReencodeOnMessageWithBodyOnly() {
      doTestMessageReencodeProducesEqualMessage(false, false, false, false, false, true, false);
   }

   @Test
   public void testReencodeOnMessageWithFooterOnly() {
      doTestMessageReencodeProducesEqualMessage(false, false, false, false, false, false, true);
   }

   @Test
   public void testReencodeOnMessageWithApplicationPropertiesAndBody() {
      doTestMessageReencodeProducesEqualMessage(false, false, false, false, true, true, false);
   }

   @Test
   public void testReencodeOnMessageWithApplicationPropertiesAndBodyAndFooter() {
      doTestMessageReencodeProducesEqualMessage(false, false, false, false, true, true, true);
   }

   @Test
   public void testReencodeOnMessageWithPropertiesApplicationPropertiesAndBodyAndFooter() {
      doTestMessageReencodeProducesEqualMessage(false, false, false, true, true, true, true);
   }

   @Test
   public void testReencodeOnMessageWithMessageAnnotationsPropertiesApplicationPropertiesAndBodyAndFooter() {
      doTestMessageReencodeProducesEqualMessage(false, false, true, true, true, true, true);
   }

   @Test
   public void testReencodeOnMessageWithDeliveryAnnotationsMessageAnnotationsPropertiesApplicationPropertiesBodyFooter() {
      doTestMessageReencodeProducesEqualMessage(false, true, true, true, true, true, true);
   }

   @SuppressWarnings("unchecked")
   private void doTestMessageReencodeProducesEqualMessage(
      boolean header, boolean deliveryAnnotations, boolean messageAnnotations, boolean properties, boolean applicationProperties, boolean body, boolean footer) {

      MessageImpl protonMessage = (MessageImpl) Proton.message();

      if (header) {
         Header headers = new Header();
         headers.setDurable(true);
         headers.setPriority(UnsignedByte.valueOf((byte) 9));
         protonMessage.setHeader(headers);
      }

      if (properties) {
         Properties props = new Properties();
         props.setCreationTime(new Date(System.currentTimeMillis()));
         props.setTo(TEST_TO_ADDRESS);
         props.setMessageId(UUID.randomUUID());
         protonMessage.setProperties(props);
      }

      if (deliveryAnnotations) {
         DeliveryAnnotations annotations = new DeliveryAnnotations(new HashMap<>());
         annotations.getValue().put(Symbol.valueOf(TEST_MESSAGE_ANNOTATION_KEY + "_DA"), TEST_MESSAGE_ANNOTATION_VALUE);
         protonMessage.setDeliveryAnnotations(annotations);
      }

      if (messageAnnotations) {
         MessageAnnotations annotations = new MessageAnnotations(new HashMap<>());
         annotations.getValue().put(Symbol.valueOf(TEST_MESSAGE_ANNOTATION_KEY), TEST_MESSAGE_ANNOTATION_VALUE);
         protonMessage.setMessageAnnotations(annotations);
      }

      if (applicationProperties) {
         ApplicationProperties appProps = new ApplicationProperties(new HashMap<>());
         appProps.getValue().put(TEST_APPLICATION_PROPERTY_KEY, TEST_APPLICATION_PROPERTY_VALUE);
         protonMessage.setApplicationProperties(appProps);
      }

      if (body) {
         AmqpValue text = new AmqpValue(TEST_STRING_BODY);
         protonMessage.setBody(text);
      }

      if (footer) {
         Footer foot = new Footer(new HashMap<>());
         foot.getValue().put(Symbol.valueOf(TEST_MESSAGE_ANNOTATION_KEY + "_FOOT"), TEST_MESSAGE_ANNOTATION_VALUE);
         protonMessage.setFooter(foot);
      }

      AMQPStandardMessage message = new AMQPStandardMessage(0, encodeMessage(protonMessage), null, null);

      message.reencode();

      if (deliveryAnnotations) {
         assertProtonMessageNotEquals(protonMessage, message.getProtonMessage());
      } else {
         assertProtonMessageEquals(protonMessage, message.getProtonMessage());
      }
   }

   @Test
   public void testGetSendBufferWithoutDeliveryAnnotations() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      Header header = new Header();
      header.setDeliveryCount(new UnsignedInteger(1));
      protonMessage.setHeader(header);
      Properties properties = new Properties();
      properties.setTo("someNiceLocal");
      protonMessage.setProperties(properties);
      protonMessage.setBody(new AmqpValue("Sample payload"));

      DeliveryAnnotations deliveryAnnotations = new DeliveryAnnotations(new HashMap<>());
      final String annotationKey = "annotationKey";
      final String annotationValue = "annotationValue";
      deliveryAnnotations.getValue().put(Symbol.getSymbol(annotationKey), annotationValue);
      protonMessage.setDeliveryAnnotations(deliveryAnnotations);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      ReadableBuffer sendBuffer = decoded.getSendBuffer(1, null);
      assertEquals(decoded.getEncodeSize(), sendBuffer.capacity());
      AMQPStandardMessage msgFromSendBuffer = new AMQPStandardMessage(0, sendBuffer, null, null);
      assertEquals("someNiceLocal", msgFromSendBuffer.getAddress());
      assertNull(msgFromSendBuffer.getDeliveryAnnotations());

      // again with higher deliveryCount
      ReadableBuffer sendBuffer2 = decoded.getSendBuffer(5, null);
      assertEquals(decoded.getEncodeSize(), sendBuffer2.capacity());
      AMQPStandardMessage msgFromSendBuffer2 = new AMQPStandardMessage(0, sendBuffer2, null, null);
      assertEquals("someNiceLocal", msgFromSendBuffer2.getAddress());
      assertNull(msgFromSendBuffer2.getDeliveryAnnotations());
   }

   @Test
   public void testGetSendBufferWithDeliveryAnnotations() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      Header header = new Header();
      header.setDeliveryCount(new UnsignedInteger(1));
      protonMessage.setHeader(header);
      Properties properties = new Properties();
      properties.setTo("someNiceLocal");
      protonMessage.setProperties(properties);
      protonMessage.setBody(new AmqpValue("Sample payload"));

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      DeliveryAnnotations newDeliveryAnnotations = new DeliveryAnnotations(new HashMap<>());
      final String annotationKey = "annotationKey";
      final String annotationValue = "annotationValue";
      newDeliveryAnnotations.getValue().put(Symbol.getSymbol(annotationKey), annotationValue);
      decoded.setDeliveryAnnotationsForSendBuffer(newDeliveryAnnotations);

      ReadableBuffer sendBuffer = decoded.getSendBuffer(1, null);
      assertEquals(decoded.getEncodeSize(), sendBuffer.capacity());
      AMQPStandardMessage msgFromSendBuffer = new AMQPStandardMessage(0, sendBuffer, null, null);
      assertEquals("someNiceLocal", msgFromSendBuffer.getAddress());
      assertNotNull(msgFromSendBuffer.getDeliveryAnnotations());
      assertEquals(1, msgFromSendBuffer.getDeliveryAnnotations().getValue().size());
      assertEquals(annotationValue, msgFromSendBuffer.getDeliveryAnnotations().getValue().get(Symbol.getSymbol(annotationKey)));

      // again with higher deliveryCount
      DeliveryAnnotations newDeliveryAnnotations2 = new DeliveryAnnotations(new HashMap<>());
      final String annotationKey2 = "annotationKey2";
      final String annotationValue2 = "annotationValue2";
      newDeliveryAnnotations2.getValue().put(Symbol.getSymbol(annotationKey2), annotationValue2);
      decoded.setDeliveryAnnotationsForSendBuffer(newDeliveryAnnotations2);

      ReadableBuffer sendBuffer2 = decoded.getSendBuffer(5, null);
      assertEquals(decoded.getEncodeSize(), sendBuffer2.capacity());
      AMQPStandardMessage msgFromSendBuffer2 = new AMQPStandardMessage(0, sendBuffer2, null, null);
      assertEquals("someNiceLocal", msgFromSendBuffer2.getAddress());
      assertNotNull(msgFromSendBuffer2.getDeliveryAnnotations());
      assertEquals(1, msgFromSendBuffer2.getDeliveryAnnotations().getValue().size());
      assertEquals(annotationValue2, msgFromSendBuffer2.getDeliveryAnnotations().getValue().get(Symbol.getSymbol(annotationKey2)));
   }


   /**
    * It validates we are not adding a header if we don't need to
    */
   @Test
   public void testGetSendBufferWithDeliveryAnnotationsAndNoHeader() {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();
      Properties properties = new Properties();
      properties.setTo("someNiceLocal");
      protonMessage.setProperties(properties);
      protonMessage.setBody(new AmqpValue("Sample payload"));

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      DeliveryAnnotations newDeliveryAnnotations = new DeliveryAnnotations(new HashMap<>());
      final String annotationKey = "annotationKey";
      final String annotationValue = "annotationValue";
      newDeliveryAnnotations.getValue().put(Symbol.getSymbol(annotationKey), annotationValue);
      decoded.setDeliveryAnnotationsForSendBuffer(newDeliveryAnnotations);

      ReadableBuffer sendBuffer = decoded.getSendBuffer(1, null);
      assertEquals(decoded.getEncodeSize(), sendBuffer.capacity());
      AMQPStandardMessage msgFromSendBuffer = new AMQPStandardMessage(0, sendBuffer, null, null);
      assertEquals("someNiceLocal", msgFromSendBuffer.getAddress());
      assertNull(msgFromSendBuffer.getProtonMessage().getHeader());
      assertNotNull(msgFromSendBuffer.getDeliveryAnnotations());
      assertEquals(1, msgFromSendBuffer.getDeliveryAnnotations().getValue().size());
      assertEquals(annotationValue, msgFromSendBuffer.getDeliveryAnnotations().getValue().get(Symbol.getSymbol(annotationKey)));

      // again with higher deliveryCount
      DeliveryAnnotations newDeliveryAnnotations2 = new DeliveryAnnotations(new HashMap<>());
      final String annotationKey2 = "annotationKey2";
      final String annotationValue2 = "annotationValue2";
      newDeliveryAnnotations2.getValue().put(Symbol.getSymbol(annotationKey2), annotationValue2);
      decoded.setDeliveryAnnotationsForSendBuffer(newDeliveryAnnotations2);
      ReadableBuffer sendBuffer2 = decoded.getSendBuffer(5, null);

      AMQPStandardMessage msgFromSendBuffer2 = new AMQPStandardMessage(0, sendBuffer2, null, null);
      assertEquals(4, msgFromSendBuffer2.getProtonMessage().getHeader().getDeliveryCount().intValue());
      assertEquals("someNiceLocal", msgFromSendBuffer2.getAddress());
      assertNotNull(msgFromSendBuffer2.getDeliveryAnnotations());
      assertEquals(1, msgFromSendBuffer2.getDeliveryAnnotations().getValue().size());
      assertEquals(annotationValue2, msgFromSendBuffer2.getDeliveryAnnotations().getValue().get(Symbol.getSymbol(annotationKey2)));
   }

   //-----  CompositeData handling -------------------------------------------//

   @Test
   public void testToCompositeDataHeaderSectionDurable() throws Exception {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();

      // With section missing (defaults false)
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      CompositeData cd = decoded.toCompositeData(0, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.DURABLE));
      Object durableObj = cd.get(CompositeDataConstants.DURABLE);
      assertInstanceOf(Boolean.class, durableObj);

      assertEquals(Boolean.FALSE, durableObj);

      // With section present, but value not set (defaults false)
      Header protonHeader = new Header();
      protonMessage.setHeader(protonHeader);

      decoded = encodeAndDecodeMessage(protonMessage);
      cd = decoded.toCompositeData(0, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.DURABLE));
      durableObj = cd.get(CompositeDataConstants.DURABLE);
      assertInstanceOf(Boolean.class, durableObj);

      assertEquals(Boolean.FALSE, durableObj);

      // With section present, value set False explicitly
      protonHeader = new Header();
      protonHeader.setDurable(Boolean.FALSE);
      protonMessage.setHeader(protonHeader);

      decoded = encodeAndDecodeMessage(protonMessage);
      cd = decoded.toCompositeData(0, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.DURABLE));
      durableObj = cd.get(CompositeDataConstants.DURABLE);
      assertInstanceOf(Boolean.class, durableObj);

      assertEquals(Boolean.FALSE, durableObj);

      // With section present, value set True explicitly
      protonHeader = new Header();
      protonHeader.setDurable(Boolean.TRUE);
      protonMessage.setHeader(protonHeader);

      decoded = encodeAndDecodeMessage(protonMessage);
      cd = decoded.toCompositeData(0, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.DURABLE));
      durableObj = cd.get(CompositeDataConstants.DURABLE);
      assertInstanceOf(Boolean.class, durableObj);

      assertEquals(Boolean.TRUE, durableObj);
   }

   @Test
   public void testToCompositeDataHeaderSectionPriority() throws Exception {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();

      // With section missing (defaults 4)
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      CompositeData cd = decoded.toCompositeData(0, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.PRIORITY));
      Object priorityObj = cd.get(CompositeDataConstants.PRIORITY);
      assertInstanceOf(Byte.class, priorityObj);

      assertEquals((byte) 4, priorityObj);

      // With section present, but value not set (defaults 4)
      Header protonHeader = new Header();
      protonMessage.setHeader(protonHeader);

      decoded = encodeAndDecodeMessage(protonMessage);
      cd = decoded.toCompositeData(0, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.PRIORITY));
      priorityObj = cd.get(CompositeDataConstants.PRIORITY);
      assertInstanceOf(Byte.class, priorityObj);

      assertEquals((byte) 4, priorityObj);

      // With section present, value set to 5 explicitly
      protonHeader = new Header();
      protonHeader.setPriority(UnsignedByte.valueOf((byte) 5));
      protonMessage.setHeader(protonHeader);

      decoded = encodeAndDecodeMessage(protonMessage);
      cd = decoded.toCompositeData(0, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.PRIORITY));
      priorityObj = cd.get(CompositeDataConstants.PRIORITY);
      assertInstanceOf(Byte.class, priorityObj);

      assertEquals((byte) 5, priorityObj);
   }

   @Test
   public void testToCompositeDataPropertiesSection() throws Exception {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();

      String testContentEncoding = "gzip";
      String testGroupId = "testGroupId";
      int testGroupSequence = 45678;
      String testReplyToGroupId = "testReplyToGroupId";
      long testCreationTime = System.currentTimeMillis();
      long testExpiryTime = testCreationTime + 5000;
      String testSubject = "testSubject";
      String testMessageId = "testMessageId";
      String testCorrelationId = "testCorrelationId";

      Properties protonProperties = new Properties();
      protonProperties.setContentType(Symbol.valueOf(AMQPMessageSupport.OCTET_STREAM_CONTENT_TYPE));
      protonProperties.setContentEncoding(Symbol.valueOf(testContentEncoding));
      protonProperties.setGroupId(testGroupId);
      protonProperties.setGroupSequence(UnsignedInteger.valueOf(testGroupSequence));
      protonProperties.setReplyToGroupId(testReplyToGroupId);
      protonProperties.setCreationTime(new Date(testCreationTime));
      protonProperties.setAbsoluteExpiryTime(new Date(testExpiryTime));
      protonProperties.setSubject(testSubject);
      protonProperties.setTo(TEST_TO_ADDRESS);
      protonProperties.setMessageId(testMessageId);
      protonProperties.setCorrelationId(testCorrelationId);

      protonMessage.setProperties(protonProperties);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      CompositeData cd = decoded.toCompositeData(-1, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.PROPERTIES));
      Object propsObject = cd.get(CompositeDataConstants.PROPERTIES);
      assertInstanceOf(String.class, propsObject);
      String properties = (String) propsObject;

      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "contentType" + "=" + AMQPMessageSupport.OCTET_STREAM_CONTENT_TYPE));
      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "contentEncoding" + "=" + testContentEncoding));
      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "groupId" + "=" + testGroupId));
      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "groupSequence" + "=" + testGroupSequence));
      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "replyToGroupId" + "=" + testReplyToGroupId));
      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "creationTime" + "=" + testCreationTime));
      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "absoluteExpiryTime" + "=" + testExpiryTime));
      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "to" + "=" + TEST_TO_ADDRESS));
      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "subject" + "=" + testSubject));
      assertTrue(properties.contains(PROPERTY_MAP_PROPERTIES_PREFIX + "correlationId" + "=" + testCorrelationId));

      // TODO: should these fields be included in the 'properties' string and tested above?
      // Some are shown elsewhere in a way, others missing entirely. Eg:
      //
      // message-id: included'ish, with an ID: prefix added, as the CompositeDataConstants.USER_ID.
      // user-id: not included.

      // Some fields of the properties section already align with fields given
      // their own top level entries of the CompositeData, which remain:

      // The message-id is presented via the 'user id' field, inc an added prefix.
      assertTrue(cd.containsKey(CompositeDataConstants.USER_ID));
      Object messageIdObj = cd.get(CompositeDataConstants.USER_ID);
      assertInstanceOf(String.class, messageIdObj);

      assertEquals(AMQPMessageIdHelper.JMS_ID_PREFIX + testMessageId, messageIdObj);

      // The creation-time is duplicated as the 'timestamp' field
      assertTrue(cd.containsKey(CompositeDataConstants.TIMESTAMP));
      Object timestampObj = cd.get(CompositeDataConstants.TIMESTAMP);
      assertInstanceOf(Long.class, timestampObj);

      assertEquals(testCreationTime, timestampObj);
   }

   @Test
   public void testToCompositeDataApplicationPropertiesSection() throws Exception {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();

      Map<String, Object> appPropsMap = new HashMap<>();
      appPropsMap.put(TEST_APPLICATION_PROPERTY_KEY, TEST_APPLICATION_PROPERTY_VALUE);
      appPropsMap.put(TEST_APPLICATION_PROPERTY_KEY2, TEST_APPLICATION_PROPERTY_VALUE2);
      ApplicationProperties appProps = new ApplicationProperties(appPropsMap);

      protonMessage.setApplicationProperties(appProps);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      CompositeData cd = decoded.toCompositeData(-1, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.PROPERTIES));
      Object propsObject = cd.get(CompositeDataConstants.PROPERTIES);
      assertInstanceOf(String.class, propsObject);
      String properties = (String) propsObject;

      assertTrue(properties.contains(PROPERTY_MAP_APP_PROPERTIES_PREFIX +
            TEST_APPLICATION_PROPERTY_KEY + "=" + TEST_APPLICATION_PROPERTY_VALUE));
      assertTrue(properties.contains(PROPERTY_MAP_APP_PROPERTIES_PREFIX +
            TEST_APPLICATION_PROPERTY_KEY2 + "=" + TEST_APPLICATION_PROPERTY_VALUE2));
   }

   @Test
   public void testToCompositeDataMessageAnnotationSection() throws Exception {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();

      Map<Symbol, Object> annotationsMap = new HashMap<>();
      annotationsMap.put(Symbol.valueOf(TEST_MESSAGE_ANNOTATION_KEY), TEST_MESSAGE_ANNOTATION_VALUE);
      annotationsMap.put(Symbol.valueOf(TEST_MESSAGE_ANNOTATION_KEY2), TEST_MESSAGE_ANNOTATION_VALUE2);
      MessageAnnotations annotations = new MessageAnnotations(annotationsMap);

      protonMessage.setMessageAnnotations(annotations);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      CompositeData cd = decoded.toCompositeData(-1, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.PROPERTIES));
      Object propsObject = cd.get(CompositeDataConstants.PROPERTIES);
      assertInstanceOf(String.class, propsObject);
      String properties = (String) propsObject;

      assertTrue(properties.contains(PROPERTY_MAP_MESSAGE_ANNOTATIONS_PREFIX +
            TEST_MESSAGE_ANNOTATION_KEY + "=" + TEST_MESSAGE_ANNOTATION_VALUE));
      assertTrue(properties.contains(PROPERTY_MAP_MESSAGE_ANNOTATIONS_PREFIX +
            TEST_MESSAGE_ANNOTATION_KEY2 + "=" + TEST_MESSAGE_ANNOTATION_VALUE2));

      // Now try some specific annotations with their own handling
      long testIngressTime = System.currentTimeMillis();
      long testDeliveryTime = System.currentTimeMillis() + 5678;
      long testDeliveryDelay = 6789;

      annotationsMap = new HashMap<>();
      annotationsMap.put(Symbol.valueOf(AMQPMessageSupport.X_OPT_INGRESS_TIME), testIngressTime);
      annotationsMap.put(Symbol.valueOf(AMQPMessageSupport.X_OPT_DELIVERY_TIME), testDeliveryTime);
      annotationsMap.put(Symbol.valueOf(AMQPMessageSupport.X_OPT_DELIVERY_DELAY), testDeliveryDelay);
      annotations = new MessageAnnotations(annotationsMap);
      protonMessage.setMessageAnnotations(annotations);

      decoded = encodeAndDecodeMessage(protonMessage);

      cd = decoded.toCompositeData(-1, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.PROPERTIES));
      propsObject = cd.get(CompositeDataConstants.PROPERTIES);
      assertInstanceOf(String.class, propsObject);
      properties = (String) propsObject;

      assertTrue(properties.contains(PROPERTY_MAP_MESSAGE_ANNOTATIONS_PREFIX +
            AMQPMessageSupport.X_OPT_INGRESS_TIME + "=" + testIngressTime));
      assertTrue(properties.contains(PROPERTY_MAP_MESSAGE_ANNOTATIONS_PREFIX +
            AMQPMessageSupport.X_OPT_DELIVERY_TIME + "=" + testDeliveryTime));
      assertTrue(properties.contains(PROPERTY_MAP_MESSAGE_ANNOTATIONS_PREFIX +
            AMQPMessageSupport.X_OPT_DELIVERY_DELAY + "=" + testDeliveryDelay));
   }

   @Test
   public void testToCompositeDataExtraProperties() throws Exception {
      MessageImpl protonMessage = (MessageImpl) Message.Factory.create();

      TypedProperties extraProperties = new TypedProperties();
      extraProperties.putProperty(SimpleString.of(TEST_EXTRA_PROPERTY_KEY1), TEST_EXTRA_PROPERTY_VALUE1);
      extraProperties.putProperty(SimpleString.of(TEST_EXTRA_PROPERTY_KEY2), TEST_EXTRA_PROPERTY_VALUE2);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage, extraProperties);

      CompositeData cd = decoded.toCompositeData(-1, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.PROPERTIES));
      Object propsObject = cd.get(CompositeDataConstants.PROPERTIES);
      assertInstanceOf(String.class, propsObject);
      String properties = (String) propsObject;

      assertTrue(properties.contains(PROPERTY_MAP_EXTRA_PROPERTIES_PREFIX +
            TEST_EXTRA_PROPERTY_KEY1 + "=" + TEST_EXTRA_PROPERTY_VALUE1));
      assertTrue(properties.contains(PROPERTY_MAP_EXTRA_PROPERTIES_PREFIX +
            TEST_EXTRA_PROPERTY_KEY2 + "=" + TEST_EXTRA_PROPERTY_VALUE2));
   }

   @Test
   public void testToCompositeDataWithDataBodySectionWithoutContentType() throws Exception {
      doToCompositeDataWithDataBodySectionTestImpl(null, org.apache.activemq.artemis.api.core.Message.BYTES_TYPE);
   }

   @Test
   public void testToCompositeDataWithDataBodySectionWithOctetStreamContentType() throws Exception {
      doToCompositeDataWithDataBodySectionTestImpl(AMQPMessageSupport.OCTET_STREAM_CONTENT_TYPE, org.apache.activemq.artemis.api.core.Message.BYTES_TYPE);
   }

   @Test
   public void testToCompositeDataWithDataBodySectionWithTextPlainContentType() throws Exception {
      doToCompositeDataWithDataBodySectionTestImpl("text/plain", org.apache.activemq.artemis.api.core.Message.TEXT_TYPE);
   }

   @Test
   public void testToCompositeDataWithDataBodySectionWithSerializedObjectContentType() throws Exception {
      doToCompositeDataWithDataBodySectionTestImpl(AMQPMessageSupport.SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.toString(), org.apache.activemq.artemis.api.core.Message.OBJECT_TYPE);
   }

   private void doToCompositeDataWithDataBodySectionTestImpl(String contentType, byte expectedMessageType) throws OpenDataException {
      Message protonMessage = Message.Factory.create();

      // Not the right payload for some of the content types,but it
      // doesnt matter, mainly checking type value and for lack of NPEs.
      String bytesSource = "testPayloadBytes";
      String expectedBodyText = "Data{" + bytesSource + "}";
      Data body = new Data(new Binary(bytesSource.getBytes(StandardCharsets.UTF_8)));

      protonMessage.setBody(body);
      protonMessage.setContentType(contentType);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      CompositeData cd = decoded.toCompositeData(-1, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.TEXT_BODY));
      assertEquals(expectedBodyText, cd.get(CompositeDataConstants.TEXT_BODY));

      assertTrue(cd.containsKey(CompositeDataConstants.TYPE));
      assertEquals(expectedMessageType, cd.get(CompositeDataConstants.TYPE));
   }

   @Test
   public void testToCompositeDataWithAmqpValueBinaryBodySectionWithoutContentType() throws Exception {
      doToCompositeDataWithAmqpValueBodySectionWithBinaryTestImpl(null, org.apache.activemq.artemis.api.core.Message.BYTES_TYPE);
   }

   @Test
   public void testToCompositeDataWithAmqpValueBinaryBodySectionWithSerializedObjectContentType() throws Exception {
      // Shouldnt really get in this situation, not meant to use content-type without the Data body section.
      doToCompositeDataWithAmqpValueBodySectionWithBinaryTestImpl(AMQPMessageSupport.SERIALIZED_JAVA_OBJECT_CONTENT_TYPE.toString(), org.apache.activemq.artemis.api.core.Message.OBJECT_TYPE);
   }

   private void doToCompositeDataWithAmqpValueBodySectionWithBinaryTestImpl(String contentType, byte expectedMessageType) throws OpenDataException {
      Message protonMessage = Message.Factory.create();

      // Not the right payload for some of the content types,but it
      // doesnt matter, mainly checking type value and for lack of NPEs.
      String bytesSource = "testPayloadBytes";
      AmqpValue body = new AmqpValue(new Binary(bytesSource.getBytes(StandardCharsets.UTF_8)));

      protonMessage.setBody(body);
      protonMessage.setContentType(contentType);

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      CompositeData cd = decoded.toCompositeData(-1, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.TEXT_BODY));
      assertEquals(bytesSource, cd.get(CompositeDataConstants.TEXT_BODY));

      assertTrue(cd.containsKey(CompositeDataConstants.TYPE));
      assertEquals(expectedMessageType, cd.get(CompositeDataConstants.TYPE));
   }

   @Test
   public void testToCompositeDataWithStringBodyWithoutValueSizeLimit() throws Exception {
      doToCompositeDataWithStringBodyValueSizeLimitTestImpl(-1, TEST_STRING_BODY);
   }

   @Test
   public void testToCompositeDataWithStringBodyWithValueSizeLimit() throws Exception {
      int limit = 11;
      int testBodyLength = TEST_STRING_BODY.length();
      assertTrue(testBodyLength > limit);

      String expected = TEST_STRING_BODY.substring(0, limit) + ", + " + String.valueOf(testBodyLength - limit) + " more";

      doToCompositeDataWithStringBodyValueSizeLimitTestImpl(limit, expected);
   }

   private void doToCompositeDataWithStringBodyValueSizeLimitTestImpl(int fieldsLimit, String expectedBodyText) throws OpenDataException {
      Message protonMessage = Message.Factory.create();
      protonMessage.setBody(new AmqpValue(TEST_STRING_BODY));

      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);

      CompositeData cd = decoded.toCompositeData(fieldsLimit, 0);

      assertTrue(cd.containsKey(CompositeDataConstants.TEXT_BODY));
      assertEquals(expectedBodyText, cd.get(CompositeDataConstants.TEXT_BODY));

      assertTrue(cd.containsKey(CompositeDataConstants.TYPE));
      assertEquals(org.apache.activemq.artemis.api.core.Message.TEXT_TYPE, cd.get(CompositeDataConstants.TYPE));
   }

   @Test
   public void testToPropertyMap() throws Exception {
      Message protonMessage = Message.Factory.create();
      AMQPStandardMessage decoded = encodeAndDecodeMessage(protonMessage);
      TypedProperties props = decoded.createExtraProperties();
      props.putSimpleStringProperty(SimpleString.of("firstString"), SimpleString.of("firstValue"));
      props.putLongProperty(SimpleString.of("secondLong"), 1234567);

      // same as toPropertyMap(false,5)
      Map<String, Object> map = decoded.toPropertyMap(-1);

      assertEquals(2, map.size());
      assertEquals("firstValue", map.get("firstString"));
      assertEquals(1234567L, map.get("secondLong"));
   }

   @Test
   public void testEncodedAMQPMessageHasReversedHeaderAndDA() throws Exception {
      final Header header = new Header();
      header.setDurable(true);

      final DeliveryAnnotations deliveryAnnotations = new DeliveryAnnotations(new HashMap<>());
      deliveryAnnotations.getValue().put(Symbol.valueOf("test-da"), "test-da");

      final MessageAnnotations messageAnnotations = new MessageAnnotations(new HashMap<>());
      messageAnnotations.getValue().put(Symbol.valueOf("test-ma"), "test-ma");

      final ByteBuf nettyBuffer = Unpooled.buffer(1500);
      WritableBuffer buffer = new NettyWritable(nettyBuffer);

      final MessageReference reference = Mockito.mock(MessageReference.class);

      try {
         encoder.setByteBuffer(buffer);
         encoder.writeObject(deliveryAnnotations);
         encoder.writeObject(header);
         encoder.writeObject(messageAnnotations);
      } finally {
         encoder.setByteBuffer((WritableBuffer) null);
      }

      final byte[] bytes = new byte[nettyBuffer.writerIndex()];
      nettyBuffer.readBytes(bytes);

      final AMQPMessage message = new AMQPStandardMessage(0, bytes, null);
      final ReadableBuffer encoded = message.getSendBuffer(0, reference);

      final Message protonMessage = Proton.message();
      protonMessage.decode(encoded);

      final Header readHeader = protonMessage.getHeader();
      final DeliveryAnnotations readDeliveryAnnotations = protonMessage.getDeliveryAnnotations();
      final MessageAnnotations readMessageAnnotations = protonMessage.getMessageAnnotations();

      assertTrue(readHeader.getDurable());
      assertNull(readDeliveryAnnotations);
      assertNotNull(readMessageAnnotations);
      assertEquals("test-ma", readMessageAnnotations.getValue().get(Symbol.valueOf("test-ma")));
   }

   @Test
   public void testEncodedAMQPMessageHasReversedHeaderAndDAWithOutgoingDeliveryAnnotations() throws Exception {
      final Header header = new Header();
      header.setDurable(true);

      final DeliveryAnnotations deliveryAnnotations = new DeliveryAnnotations(new HashMap<>());
      deliveryAnnotations.getValue().put(Symbol.valueOf("test-da"), "test-da");

      final MessageAnnotations messageAnnotations = new MessageAnnotations(new HashMap<>());
      messageAnnotations.getValue().put(Symbol.valueOf("test-ma"), "test-ma");

      final ByteBuf nettyBuffer = Unpooled.buffer(1500);
      WritableBuffer buffer = new NettyWritable(nettyBuffer);

      final MessageReference reference = Mockito.mock(MessageReference.class);
      final DeliveryAnnotations deliveryAnnotationsOut = new DeliveryAnnotations(new HashMap<>());
      deliveryAnnotationsOut.getValue().put(Symbol.valueOf("new-da"), "new-da");
      Mockito.when(reference.getProtocolData(DeliveryAnnotations.class)).thenReturn(deliveryAnnotationsOut);

      try {
         encoder.setByteBuffer(buffer);
         encoder.writeObject(deliveryAnnotations);
         encoder.writeObject(header);
         encoder.writeObject(messageAnnotations);
      } finally {
         encoder.setByteBuffer((WritableBuffer) null);
      }

      final byte[] bytes = new byte[nettyBuffer.writerIndex()];
      nettyBuffer.readBytes(bytes);

      final AMQPMessage message = new AMQPStandardMessage(0, bytes, null);
      final ReadableBuffer encoded = message.getSendBuffer(0, reference);

      final Message protonMessage = Proton.message();
      protonMessage.decode(encoded);

      final Header readHeader = protonMessage.getHeader();
      final DeliveryAnnotations readDeliveryAnnotations = protonMessage.getDeliveryAnnotations();
      final MessageAnnotations readMessageAnnotations = protonMessage.getMessageAnnotations();

      assertTrue(readHeader.getDurable());
      assertNotNull(readDeliveryAnnotations);
      assertEquals("new-da", readDeliveryAnnotations.getValue().get(Symbol.valueOf("new-da")));
      assertNotNull(readMessageAnnotations);
      assertEquals("test-ma", readMessageAnnotations.getValue().get(Symbol.valueOf("test-ma")));
   }

   @Test
   public void testPutAndGetOfMessageBytesProperties() {
      final byte[] empty = new byte[0];
      final byte[] array = new byte[] {0, 1, 2, 3};

      final AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null);

      assertNull(message.getBytesProperty("test"));

      // Empty byte array
      message.putBytesProperty("test", empty);
      final byte[] emptyResult = message.getBytesProperty("test");
      assertArrayEquals(empty, emptyResult);

      // Populated byte array
      message.putBytesProperty("test", array);
      final byte[] arrayResult = message.getBytesProperty("test");
      assertArrayEquals(array, arrayResult);

      // null value set and get
      message.putBytesProperty("test", null);

      assertNull(message.getBytesProperty("test"));
   }

   @Test
   public void testPutAndGetOfMessageBytesPropertiesViaObjectPropertyAccessor() {
      final byte[] empty = new byte[0];
      final byte[] array = new byte[] {0, 1, 2, 3};

      final AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null);

      assertNull(message.getObjectProperty("test"));

      // Empty byte array
      message.putObjectProperty("test", empty);
      final byte[] emptyResult = (byte[]) message.getObjectProperty("test");
      assertArrayEquals(empty, emptyResult);

      // Populated byte array
      message.putObjectProperty("test", array);
      final byte[] arrayResult = (byte[]) message.getObjectProperty("test");
      assertArrayEquals(array, arrayResult);

      // null value set and get
      message.putObjectProperty("test", null);

      assertNull(message.getObjectProperty("test"));
   }

   @Test
   public void testGetBytesPropertyHandlesOffsetArrayInBinary() {
      final byte[] array = new byte[] {0, 1, 2, 3};
      final byte[] offsetArray = new byte[] {1, 0, 1, 2, 3, 4};
      final Binary offsetBinary = new Binary(offsetArray, 1, array.length);

      final AMQPMessageTestExtension message = new AMQPMessageTestExtension(0, encodedProtonMessage, null);

      assertNull(message.getBytesProperty("test"));

      final ApplicationProperties applicationProperties = message.getDecodedApplicationProperties();
      assertNotNull(applicationProperties.getValue());

      applicationProperties.getValue().put("test", offsetBinary);

      assertTrue(message.containsProperty("test"));

      final byte[] arrayResult = message.getBytesProperty("test");

      assertArrayEquals(array, arrayResult);
   }

   @Test
   public void testGetBytesFromObjectPropertyHandlesOffsetArrayInBinary() {
      final byte[] array = new byte[] {0, 1, 2, 3};
      final byte[] offsetArray = new byte[] {1, 0, 1, 2, 3, 4};
      final Binary offsetBinary = new Binary(offsetArray, 1, array.length);

      final AMQPMessageTestExtension message = new AMQPMessageTestExtension(0, encodedProtonMessage, null);

      assertNull(message.getObjectProperty("test"));

      final ApplicationProperties applicationProperties = message.getDecodedApplicationProperties();
      assertNotNull(applicationProperties.getValue());

      applicationProperties.getValue().put("test", offsetBinary);

      assertTrue(message.containsProperty("test"));

      final Object result = message.getObjectProperty("test");

      assertInstanceOf(byte[].class, result);
      assertArrayEquals(array, (byte[]) result);
   }

   @Test
   public void testBytesPropertySetAndReencode() {
      final byte[] array = new byte[] {0, 1, 2, 3};

      final AMQPStandardMessage message = new AMQPStandardMessage(0, encodedProtonMessage, null);

      assertNull(message.getBytesProperty("test"));

      message.putBytesProperty("test", array);
      message.reencode();

      assertTrue(message.containsProperty("test"));

      // Decodes the value from the AMQP data encoding and then checks on the contents
      final Binary result = (Binary) message.getApplicationProperties().getValue().get("test");

      assertNotNull(result);
      assertEquals(array.length, result.getLength());

      // Safe copy to account for possible offset array
      final byte[] copy = new byte[array.length];
      System.arraycopy(result.getArray(), result.getArrayOffset(), copy, 0, result.getLength());

      assertArrayEquals(array, copy);
   }

   //----- Test Support ------------------------------------------------------//

   // Extension allows public access to decoded application properties that is otherwise
   // not accessible directly.
   private class AMQPMessageTestExtension extends AMQPStandardMessage {

      AMQPMessageTestExtension(long messageFormat, byte[] data, TypedProperties extraProperties) {
         super(messageFormat, data, extraProperties, null);
      }

      @Override
      public ApplicationProperties getDecodedApplicationProperties() {
         return applicationProperties;
      }
   }

   private MessageImpl createProtonMessage() {
      MessageImpl message = (MessageImpl) Proton.message();

      Header header = new Header();
      header.setDurable(true);
      header.setPriority(UnsignedByte.valueOf((byte) 9));

      Properties properties = new Properties();
      properties.setCreationTime(new Date(System.currentTimeMillis()));
      properties.setTo(TEST_TO_ADDRESS);
      properties.setMessageId(UUID.randomUUID());

      MessageAnnotations annotations = new MessageAnnotations(new LinkedHashMap<>());
      annotations.getValue().put(Symbol.valueOf(TEST_MESSAGE_ANNOTATION_KEY), TEST_MESSAGE_ANNOTATION_VALUE);

      ApplicationProperties applicationProperties = new ApplicationProperties(new LinkedHashMap<>());
      applicationProperties.getValue().put(TEST_APPLICATION_PROPERTY_KEY, TEST_APPLICATION_PROPERTY_VALUE);

      AmqpValue body = new AmqpValue(TEST_STRING_BODY);

      message.setHeader(header);
      message.setMessageAnnotations(annotations);
      message.setProperties(properties);
      message.setApplicationProperties(applicationProperties);
      message.setBody(body);

      return message;
   }

   private void assertProtonMessageEquals(MessageImpl left, MessageImpl right) {
      if (!isEquals(left, right)) {
         fail("MessageImpl values should be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private void assertProtonMessageNotEquals(MessageImpl left, MessageImpl right) {
      if (isEquals(left, right)) {
         fail("MessageImpl values should be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private boolean isEquals(MessageImpl left, MessageImpl right) {
      if (left == null && right == null) {
         return true;
      }
      if (!isNullnessEquals(left, right)) {
         return false;
      }

      try {
         assertHeaderEquals(left.getHeader(), right.getHeader());
         assertDeliveryAnnotationsEquals(left.getDeliveryAnnotations(), right.getDeliveryAnnotations());
         assertMessageAnnotationsEquals(left.getMessageAnnotations(), right.getMessageAnnotations());
         assertPropertiesEquals(left.getProperties(), right.getProperties());
         assertApplicationPropertiesEquals(left.getApplicationProperties(), right.getApplicationProperties());
         assertTrue(isEquals(left.getBody(), right.getBody()));
         assertFootersEquals(left.getFooter(), right.getFooter());
      } catch (Throwable e) {
         logger.debug(e.getMessage(), e);
         return false;
      }

      return true;
   }

   private void assertHeaderEquals(Header left, Header right) {
      if (!isEquals(left, right)) {
         fail("Header values should be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private void assertHeaderNotEquals(Header left, Header right) {
      if (isEquals(left, right)) {
         fail("Header values should not be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private boolean isEquals(Header left, Header right) {
      if (left == null && right == null) {
         return true;
      }
      if (!isNullnessEquals(left, right)) {
         return false;
      }

      try {
         assertEquals(left.getDurable(), right.getDurable());
         assertEquals(left.getDeliveryCount(), right.getDeliveryCount());
         assertEquals(left.getFirstAcquirer(), right.getFirstAcquirer());
         assertEquals(left.getPriority(), right.getPriority());
         assertEquals(left.getTtl(), right.getTtl());
      } catch (Throwable e) {
         return false;
      }

      return true;
   }

   private void assertPropertiesEquals(Properties left, Properties right) {
      if (!isEquals(left, right)) {
         fail("Properties values should be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private void assertPropertiesNotEquals(Properties left, Properties right) {
      if (isEquals(left, right)) {
         fail("Properties values should not be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private boolean isEquals(Properties left, Properties right) {
      if (left == null && right == null) {
         return true;
      }
      if (!isNullnessEquals(left, right)) {
         return false;
      }

      try {
         assertEquals(left.getAbsoluteExpiryTime(), right.getAbsoluteExpiryTime());
         assertEquals(left.getContentEncoding(), right.getAbsoluteExpiryTime());
         assertEquals(left.getContentType(), right.getContentType());
         assertEquals(left.getCorrelationId(), right.getCorrelationId());
         assertEquals(left.getCreationTime(), right.getCreationTime());
         assertEquals(left.getGroupId(), right.getGroupId());
         assertEquals(left.getGroupSequence(), right.getGroupSequence());
         assertEquals(left.getMessageId(), right.getMessageId());
         assertEquals(left.getReplyTo(), right.getReplyTo());
         assertEquals(left.getReplyToGroupId(), right.getReplyToGroupId());
         assertEquals(left.getSubject(), right.getSubject());
         assertEquals(left.getUserId(), right.getUserId());
         assertEquals(left.getTo(), right.getTo());
      } catch (Throwable e) {
         return false;
      }

      return true;
   }

   private void assertMessageAnnotationsEquals(MessageAnnotations left, MessageAnnotations right) {
      if (!isEquals(left, right)) {
         fail("MessageAnnotations values should be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private void assertMessageAnnotationsNotEquals(MessageAnnotations left, MessageAnnotations right) {
      if (isEquals(left, right)) {
         fail("MessageAnnotations values should not be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private boolean isEquals(MessageAnnotations left, MessageAnnotations right) {
      if (left == null && right == null) {
         return true;
      }
      if (!isNullnessEquals(left, right)) {
         return false;
      }

      return isEquals(left.getValue(), right.getValue());
   }

   private void assertDeliveryAnnotationsEquals(DeliveryAnnotations left, DeliveryAnnotations right) {
      if (!isEquals(left, right)) {
         fail("DeliveryAnnotations values should be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private void assertDeliveryAnnotationsNotEquals(DeliveryAnnotations left, DeliveryAnnotations right) {
      if (isEquals(left, right)) {
         fail("DeliveryAnnotations values should not be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private boolean isEquals(DeliveryAnnotations left, DeliveryAnnotations right) {
      if (left == null && right == null) {
         return true;
      }
      if (!isNullnessEquals(left, right)) {
         return false;
      }

      return isEquals(left.getValue(), right.getValue());
   }

   private void assertApplicationPropertiesEquals(ApplicationProperties left, ApplicationProperties right) {
      if (!isEquals(left, right)) {
         fail("ApplicationProperties values should be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private void assertApplicationPropertiesNotEquals(ApplicationProperties left, ApplicationProperties right) {
      if (isEquals(left, right)) {
         fail("ApplicationProperties values should not be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private boolean isEquals(ApplicationProperties left, ApplicationProperties right) {
      if (left == null && right == null) {
         return true;
      }
      if (!isNullnessEquals(left, right)) {
         return false;
      }

      return isEquals(left.getValue(), right.getValue());
   }

   private void assertFootersEquals(Footer left, Footer right) {
      if (!isEquals(left, right)) {
         fail("Footer values should be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private void assertFootersNotEquals(Footer left, Footer right) {
      if (isEquals(left, right)) {
         fail("Footer values should not be equal: left{" + left + "} right{" + right + "}");
      }
   }

   private boolean isEquals(Footer left, Footer right) {
      if (left == null && right == null) {
         return true;
      }
      if (!isNullnessEquals(left, right)) {
         return false;
      }

      return isEquals(left.getValue(), right.getValue());
   }

   private boolean isEquals(Map<?, ?> left, Map<?, ?> right) {
      if (left == null && right == null) {
         return true;
      }

      if (!isNullnessEquals(left, right)) {
         return false;
      }

      if (left.size() != right.size()) {
         return false;
      }

      for (Object leftKey : left.keySet()) {
         assertEquals(right.get(leftKey), left.get(leftKey));
      }

      for (Object rightKey : right.keySet()) {
         assertEquals(left.get(rightKey), right.get(rightKey));
      }

      return true;
   }

   @SuppressWarnings("unchecked")
   private boolean isEquals(Section left, Section right) {
      if (left == null && right == null) {
         return true;
      }
      if (!isNullnessEquals(left, right)) {
         return false;
      }

      assertEquals(left.getClass(), right.getClass());

      if (left instanceof AmqpValue leftValue) {
         AmqpValue rightValue = (AmqpValue) right;

         if (leftValue.getValue() == null && rightValue.getValue() == null) {
            return true;
         }
         if (!isNullnessEquals(leftValue.getValue(), rightValue.getValue())) {
            return false;
         }

         assertEquals(leftValue.getValue(), rightValue.getValue());
      } else if (left instanceof AmqpSequence leftValue) {
         AmqpSequence rightValue = (AmqpSequence) right;

         if (leftValue.getValue() == null && rightValue.getValue() == null) {
            return true;
         }
         if (!isNullnessEquals(leftValue.getValue(), rightValue.getValue())) {
            return false;
         }

         List<Object> leftList = leftValue.getValue();
         List<Object> rightList = leftValue.getValue();

         assertEquals(leftList.size(), rightList.size());

         for (int i = 0; i < leftList.size(); ++i) {
            assertEquals(leftList.get(i), rightList.get(i));
         }
      } else if (left instanceof Data leftValue) {
         Data rightValue = (Data) right;

         if (leftValue.getValue() == null && rightValue.getValue() == null) {
            return true;
         }
         if (!isNullnessEquals(leftValue.getValue(), rightValue.getValue())) {
            return false;
         }

         byte[] leftArray = leftValue.getValue().getArray();
         byte[] rightArray = rightValue.getValue().getArray();

         if (leftArray == null && rightArray == null) {
            return true;
         }
         if (!isNullnessEquals(leftArray, rightArray)) {
            return false;
         }

         assertArrayEquals(leftArray, rightArray);
      } else {
         return false;
      }

      return true;
   }

   private boolean isNullnessEquals(Object left, Object right) {
      if (left == null && right != null) {
         return false;
      }
      if (left != null && right == null) {
         return false;
      }

      return true;
   }

   private ActiveMQBuffer encodeMessageAsPersistedBuffer(MessageImpl message) {
      ByteBuf nettyBuffer = Unpooled.buffer(1500);

      message.encode(new NettyWritable(nettyBuffer));
      byte[] bytes = new byte[nettyBuffer.writerIndex() + Integer.BYTES];
      nettyBuffer.readBytes(bytes, Integer.BYTES, nettyBuffer.readableBytes());

      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(bytes);
      buffer.writerIndex(0);
      buffer.writeInt(bytes.length - Integer.BYTES);
      buffer.setIndex(0, bytes.length);

      return buffer;
   }

   private byte[] encodeMessage(MessageImpl message) {
      ByteBuf nettyBuffer = Unpooled.buffer(1500);

      message.encode(new NettyWritable(nettyBuffer));
      byte[] bytes = new byte[nettyBuffer.writerIndex()];
      nettyBuffer.readBytes(bytes);

      return bytes;
   }

   private AMQPStandardMessage encodeAndDecodeMessage(Message message) {
      return encodeAndDecodeMessage(message, null);
   }

   private AMQPStandardMessage encodeAndDecodeMessage(Message message, TypedProperties extraProperties) {
      ByteBuf nettyBuffer = Unpooled.buffer(1500);

      message.encode(new NettyWritable(nettyBuffer));
      byte[] bytes = new byte[nettyBuffer.writerIndex()];
      nettyBuffer.readBytes(bytes);

      return new AMQPStandardMessage(0, bytes, extraProperties);
   }
}
