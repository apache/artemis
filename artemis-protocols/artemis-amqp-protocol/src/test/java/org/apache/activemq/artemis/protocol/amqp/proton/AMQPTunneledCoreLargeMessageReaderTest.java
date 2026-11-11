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

package org.apache.activemq.artemis.protocol.amqp.proton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Random;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.EncodingCodes;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Test the handling of AMQP received frames and decoding them into Core large messages.
 */
public class AMQPTunneledCoreLargeMessageReaderTest {

   private static final byte DATA_DESCRIPTOR = 0x75;
   private static final int DATA_SECTION_ENCODING_BYTES = Long.BYTES;

   @Mock
   ProtonAbstractReceiver serverReceiver;

   @Mock
   AMQPSessionContext sessionContext;

   @Mock
   AMQPSessionCallback sessionSPI;

   @Mock
   AMQPConnectionContext connectionContext;

   @Spy
   NullStorageManager nullStoreManager = new NullStorageManager();

   @BeforeEach
   public void setUp() {
      MockitoAnnotations.openMocks(this);

      when(serverReceiver.getConnection()).thenReturn(connectionContext);
      when(connectionContext.isLargeMessageSync()).thenReturn(true);
      when(serverReceiver.getSessionContext()).thenReturn(sessionContext);
      when(sessionContext.getSessionSPI()).thenReturn(sessionSPI);
      when(sessionSPI.getStorageManager()).thenReturn(nullStoreManager);
   }

   @Test
   public void testReaderThrowsIllegalStateIfNotOpenedWhenReadCalled() throws Exception {
      AMQPTunneledCoreLargeMessageReader reader = new AMQPTunneledCoreLargeMessageReader(serverReceiver);
      Delivery delivery = Mockito.mock(Delivery.class);

      try {
         reader.readBytes(delivery);
         fail("Should throw as the reader was not opened.");
      } catch (IllegalStateException e) {
         // Expected
      }
   }

   @Test
   public void testReadDeliveryAnnotationsFromDeliveryBuffer() throws Exception {
      AMQPTunneledCoreLargeMessageReader reader = new AMQPTunneledCoreLargeMessageReader(serverReceiver);
      Receiver receiver = Mockito.mock(Receiver.class);
      Delivery delivery = Mockito.mock(Delivery.class);

      when(delivery.getLink()).thenReturn(receiver);
      when(receiver.recv()).thenReturn(createAnnotationsBuffer());
      when(delivery.isPartial()).thenReturn(true);

      reader.open();

      assertNull(reader.getDeliveryAnnotations());

      try {
         reader.readBytes(delivery);
      } catch (IllegalStateException e) {
         fail("Should not throw as the reader should be able to read just delivery annotations.");
      }

      assertNotNull(reader.getDeliveryAnnotations());

      final DeliveryAnnotations annotations = reader.getDeliveryAnnotations();

      assertEquals("a", annotations.getValue().get(Symbol.valueOf("a")));
      assertEquals("b", annotations.getValue().get(Symbol.valueOf("b")));
      assertEquals("c", annotations.getValue().get(Symbol.valueOf("c")));

      reader.close();

      try {
         reader.readBytes(delivery);
         fail("Should throw as the reader was closed.");
      } catch (IllegalStateException e) {
         // Expected
      }
   }

   @Test
   public void testReadMessageByteByByteFromDeliveryBuffer() throws Exception {
      AMQPTunneledCoreLargeMessageReader reader = new AMQPTunneledCoreLargeMessageReader(serverReceiver);
      Receiver receiver = Mockito.mock(Receiver.class);
      Delivery delivery = Mockito.mock(Delivery.class);

      when(delivery.getLink()).thenReturn(receiver);
      when(delivery.isPartial()).thenReturn(true);

      reader.open();

      final ReadableBuffer deliveryAnnotations = createAnnotationsBuffer();

      for (int i = 1; i <= deliveryAnnotations.remaining(); ++i) {
         when(receiver.recv()).thenReturn(deliveryAnnotations.duplicate().position(i - 1).limit(i).slice());

         try {
            assertNull(reader.readBytes(delivery));
         } catch (IllegalStateException e) {
            fail("Should not throw as the reader should be able to read just delivery annotations.");
         }
      }

      assertNotNull(reader.getDeliveryAnnotations());

      final DeliveryAnnotations annotations = reader.getDeliveryAnnotations();

      assertEquals("a", annotations.getValue().get(Symbol.valueOf("a")));
      assertEquals("b", annotations.getValue().get(Symbol.valueOf("b")));
      assertEquals("c", annotations.getValue().get(Symbol.valueOf("c")));

      final CoreMessage message = new CoreMessage();

      message.setDurable(true);
      message.setExpiration(42);
      message.putStringProperty("a", "a");

      final ReadableBuffer coreMessageHeader = createCoreMessageEncoding(message);

      for (int i = 1; i <= coreMessageHeader.remaining(); ++i) {
         when(receiver.recv()).thenReturn(coreMessageHeader.duplicate().position(i - 1).limit(i).slice());

         try {
            assertNull(reader.readBytes(delivery));
         } catch (IllegalStateException e) {
            fail("Should not throw as the reader should be able to read just the core message header.");
         }
      }

      final ReadableBuffer coreLargeMessageBody = createRandomLargeMessagePaylod();

      Message readMessage = null;

      for (int i = 1; i <= coreLargeMessageBody.remaining(); ++i) {
         when(receiver.recv()).thenReturn(coreLargeMessageBody.duplicate().position(i - 1).limit(i).slice());

         try {
            if (i == coreLargeMessageBody.remaining()) {
               when(delivery.isPartial()).thenReturn(false);
            }

            readMessage = reader.readBytes(delivery);

            if (i < coreLargeMessageBody.remaining()) {
               assertNull(readMessage);
            } else {
               assertNotNull(readMessage);
            }
         } catch (IllegalStateException e) {
            fail("Should not throw as the reader should be able to read just the core message header.");
         }
      }

      assertTrue(readMessage.isDurable());
      assertEquals(42, readMessage.getExpiration());
      assertTrue(readMessage.containsProperty("a"));
      assertEquals("a", readMessage.getStringProperty("a"));
      assertTrue(readMessage.isLargeMessage());
   }

   @Test
   public void testReadMessageInByteChunksFromDeliveryBuffer() throws Exception {
      AMQPTunneledCoreLargeMessageReader reader = new AMQPTunneledCoreLargeMessageReader(serverReceiver);
      Receiver receiver = Mockito.mock(Receiver.class);
      Delivery delivery = Mockito.mock(Delivery.class);

      when(delivery.getLink()).thenReturn(receiver);
      when(delivery.isPartial()).thenReturn(true);

      reader.open();

      final ReadableBuffer deliveryAnnotations = createAnnotationsBuffer();

      when(receiver.recv()).thenReturn(deliveryAnnotations.duplicate());

      try {
         assertNull(reader.readBytes(delivery));
      } catch (IllegalStateException e) {
         fail("Should not throw as the reader should be able to read just delivery annotations.");
      }

      assertNotNull(reader.getDeliveryAnnotations());

      final DeliveryAnnotations annotations = reader.getDeliveryAnnotations();

      assertEquals("a", annotations.getValue().get(Symbol.valueOf("a")));
      assertEquals("b", annotations.getValue().get(Symbol.valueOf("b")));
      assertEquals("c", annotations.getValue().get(Symbol.valueOf("c")));

      final CoreMessage message = new CoreMessage();

      message.setDurable(true);
      message.setExpiration(42);
      message.putStringProperty("a", "a");

      final ReadableBuffer coreMessageHeader = createCoreMessageEncoding(message);

      when(receiver.recv()).thenReturn(coreMessageHeader.duplicate());

      try {
         assertNull(reader.readBytes(delivery));
      } catch (IllegalStateException e) {
         fail("Should not throw as the reader should be able to read just the core message header.");
      }

      final ReadableBuffer coreLargeMessageBody = createRandomLargeMessagePaylod();
      Message readMessage = null;

      when(receiver.recv()).thenReturn(coreLargeMessageBody.duplicate());
      when(delivery.isPartial()).thenReturn(false);

      try {
         readMessage = reader.readBytes(delivery);
         assertNotNull(readMessage);
      } catch (IllegalStateException e) {
         fail("Should not throw as the reader should be able to read just the core message header.");
      }

      assertTrue(readMessage.isDurable());
      assertEquals(42, readMessage.getExpiration());
      assertTrue(readMessage.containsProperty("a"));
      assertEquals("a", readMessage.getStringProperty("a"));
      assertTrue(readMessage.isLargeMessage());
   }

   @Test
   public void testReadMessageInSingleFrameFromDeliveryBufferWithAnnotations() throws Exception {
      doTestReadMessageInSingleFrameFromDeliveryBuffer(true);
   }

   @Test
   public void testReadMessageInSingleFrameFromDeliveryBufferWithoutAnnotations() throws Exception {
      doTestReadMessageInSingleFrameFromDeliveryBuffer(false);
   }

   private void doTestReadMessageInSingleFrameFromDeliveryBuffer(boolean deliveryAnnotations) throws Exception {
      AMQPTunneledCoreLargeMessageReader reader = new AMQPTunneledCoreLargeMessageReader(serverReceiver);
      Receiver receiver = Mockito.mock(Receiver.class);
      Delivery delivery = Mockito.mock(Delivery.class);

      when(delivery.getLink()).thenReturn(receiver);

      reader.open();

      final CoreMessage message = new CoreMessage();

      message.setDurable(true);
      message.setExpiration(42);
      message.putStringProperty("a", "a");

      final ReadableBuffer coreMessageFrame = createCoreLargeMessageDelivery(message, deliveryAnnotations);

      when(receiver.recv()).thenReturn(coreMessageFrame.duplicate());
      when(delivery.isPartial()).thenReturn(false);

      Message readMessage = null;

      try {
         readMessage = reader.readBytes(delivery);
         assertNotNull(readMessage);
      } catch (IllegalStateException e) {
         fail("Should not throw as the reader should be able to read just the core message header.");
      }

      if (deliveryAnnotations) {
         final DeliveryAnnotations annotations = reader.getDeliveryAnnotations();

         assertEquals("a", annotations.getValue().get(Symbol.valueOf("a")));
         assertEquals("b", annotations.getValue().get(Symbol.valueOf("b")));
         assertEquals("c", annotations.getValue().get(Symbol.valueOf("c")));
      } else {
         assertNull(reader.getDeliveryAnnotations());
      }

      assertTrue(readMessage.isDurable());
      assertEquals(42, readMessage.getExpiration());
      assertTrue(readMessage.containsProperty("a"));
      assertEquals("a", readMessage.getStringProperty("a"));
      assertTrue(readMessage.isLargeMessage());

      reader.close();

      assertNull(reader.getDeliveryAnnotations());
   }

   @Test
   public void testReadLargeMessageAndIgnoreAdditionalSections() throws Exception {
      AMQPTunneledCoreLargeMessageReader reader = new AMQPTunneledCoreLargeMessageReader(serverReceiver);
      Receiver receiver = Mockito.mock(Receiver.class);
      Delivery delivery = Mockito.mock(Delivery.class);

      when(delivery.getLink()).thenReturn(receiver);

      reader.open();

      final CoreMessage message = new CoreMessage();

      message.setDurable(true);
      message.setExpiration(42);
      message.putStringProperty("a", "a");

      final ReadableBuffer coreMessageFrame = createCoreLargeMessageWithExtraSections(message);

      when(receiver.recv()).thenReturn(coreMessageFrame.duplicate());
      when(delivery.isPartial()).thenReturn(false);

      Message readMessage = null;

      try {
         readMessage = reader.readBytes(delivery);
         assertNotNull(readMessage);
      } catch (IllegalStateException e) {
         fail("Should not throw as the reader should be able to read just the core message header.");
      }

      final DeliveryAnnotations annotations = reader.getDeliveryAnnotations();

      assertEquals("a", annotations.getValue().get(Symbol.valueOf("a")));
      assertEquals("b", annotations.getValue().get(Symbol.valueOf("b")));
      assertEquals("c", annotations.getValue().get(Symbol.valueOf("c")));

      assertTrue(readMessage.isDurable());
      assertEquals(42, readMessage.getExpiration());
      assertTrue(readMessage.containsProperty("a"));
      assertEquals("a", readMessage.getStringProperty("a"));
      assertTrue(readMessage.isLargeMessage());

      reader.close();

      assertNull(reader.getDeliveryAnnotations());
   }

   private ReadableBuffer createCoreMessageEncoding(CoreMessage message) {
      final int encodedSize = message.getHeadersAndPropertiesEncodeSize();
      final int dataSectionSize = encodedSize + DATA_SECTION_ENCODING_BYTES;
      final ByteBuf buffer = Unpooled.buffer(dataSectionSize, dataSectionSize);

      writeDataSectionTypeInfo(buffer, encodedSize);

      message.encodeHeadersAndProperties(buffer);

      return new NettyReadable(buffer);
   }

   private ReadableBuffer createRandomLargeMessagePaylod() {
      final byte[] payload = new byte[1024];
      final Random random = new Random(System.currentTimeMillis());

      random.nextBytes(payload);

      final int dataSectionSize = payload.length + DATA_SECTION_ENCODING_BYTES;
      final ByteBuf buffer = Unpooled.buffer(dataSectionSize, dataSectionSize);

      writeDataSectionTypeInfo(buffer, payload.length);
      buffer.writeBytes(payload);

      return new NettyReadable(buffer);
   }

   private ReadableBuffer createCoreLargeMessageDelivery(CoreMessage message, boolean annotations) {
      final int encodedSize = message.getHeadersAndPropertiesEncodeSize();
      final byte[] payload = new byte[1024];
      final Random random = new Random(System.currentTimeMillis());

      final ByteBuffer deliveryAnnotations;

      if (annotations) {
         deliveryAnnotations = createAnnotationsBuffer().byteBuffer();
      } else {
         deliveryAnnotations = ByteBuffer.wrap(new byte[0]);
      }

      final int dataSection1Size = encodedSize + DATA_SECTION_ENCODING_BYTES;
      final int dataSection2Size = payload.length + DATA_SECTION_ENCODING_BYTES;

      final int bufferSize = dataSection1Size + dataSection2Size + deliveryAnnotations.remaining();

      final ByteBuf buffer = Unpooled.buffer(bufferSize, bufferSize);

      buffer.writeBytes(deliveryAnnotations);

      writeDataSectionTypeInfo(buffer, encodedSize);

      message.encodeHeadersAndProperties(buffer);

      random.nextBytes(payload);

      writeDataSectionTypeInfo(buffer, payload.length);

      buffer.writeBytes(payload);

      return new NettyReadable(buffer);
   }

   private ReadableBuffer createCoreLargeMessageWithExtraSections(CoreMessage message) {
      final int encodedSize = message.getHeadersAndPropertiesEncodeSize();
      final byte[] payload = new byte[1024];
      final Random random = new Random(System.currentTimeMillis());

      final ByteBuf sections = Unpooled.buffer();

      final EncoderImpl encoder = TLSEncode.getEncoder();
      final Header header = new Header();
      final DeliveryAnnotations annotations = new DeliveryAnnotations(new HashMap<>());
      final MessageAnnotations msgAnnotations = new MessageAnnotations(new HashMap<>());
      final Properties properties = new Properties();
      final ApplicationProperties appProperties = new ApplicationProperties(new HashMap<>());

      header.setDurable(true);

      annotations.getValue().put(Symbol.valueOf("a"), "a");
      annotations.getValue().put(Symbol.valueOf("b"), "b");
      annotations.getValue().put(Symbol.valueOf("c"), "c");

      msgAnnotations.getValue().put(Symbol.valueOf("d"), "d");

      properties.setGroupId("group");

      appProperties.getValue().put("e", "e");

      try {
         encoder.setByteBuffer(new NettyWritable(sections));
         encoder.writeObject(header);
         encoder.writeObject(annotations);
         encoder.writeObject(msgAnnotations);
         encoder.writeObject(properties);
         encoder.writeObject(appProperties);
      } finally {
         encoder.setByteBuffer((WritableBuffer) null);
      }

      final int dataSection1Size = encodedSize + DATA_SECTION_ENCODING_BYTES;
      final int dataSection2Size = payload.length + DATA_SECTION_ENCODING_BYTES;

      final int bufferSize = dataSection1Size + dataSection2Size + sections.readableBytes();

      final ByteBuf buffer = Unpooled.buffer(bufferSize, bufferSize);

      buffer.writeBytes(sections);

      writeDataSectionTypeInfo(buffer, encodedSize);

      message.encodeHeadersAndProperties(buffer);

      random.nextBytes(payload);

      writeDataSectionTypeInfo(buffer, payload.length);

      buffer.writeBytes(payload);

      return new NettyReadable(buffer);
   }

   private void writeDataSectionTypeInfo(ByteBuf buffer, int encodedSize) {
      buffer.writeByte(EncodingCodes.DESCRIBED_TYPE_INDICATOR);
      buffer.writeByte(EncodingCodes.SMALLULONG);
      buffer.writeByte(DATA_DESCRIPTOR);
      buffer.writeByte(EncodingCodes.VBIN32);
      buffer.writeInt(encodedSize); // Core message will encode into this size.
   }

   private ReadableBuffer createAnnotationsBuffer() {
      final EncoderImpl encoder = TLSEncode.getEncoder();

      final DeliveryAnnotations annotations = new DeliveryAnnotations(new HashMap<>());

      annotations.getValue().put(Symbol.valueOf("a"), "a");
      annotations.getValue().put(Symbol.valueOf("b"), "b");
      annotations.getValue().put(Symbol.valueOf("c"), "c");

      final ByteBuf buffer = Unpooled.buffer();
      final NettyWritable writable = new NettyWritable(buffer);

      try {
         encoder.setByteBuffer(writable);
         encoder.writeObject(annotations);
      } finally {
         encoder.setByteBuffer((WritableBuffer) null);
      }

      return new NettyReadable(buffer);
   }
}
