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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Random;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.Persister;
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
 * Checks the functionality of the tunneled core message reader
 */
public class AMQPTunneledCoreMessageReaderTest {

   private static final byte DATA_DESCRIPTOR = 0x75;
   private static final int DATA_SECTION_ENCODING_BYTES = Long.BYTES;

   @Mock
   ProtonAbstractReceiver serverReceiver;

   @Mock
   AMQPSessionContext sessionContext;

   @Mock
   AMQPSessionCallback sessionSPI;

   @Spy
   NullStorageManager nullStoreManager = new NullStorageManager();

   @BeforeEach
   public void setUp() {
      MockitoAnnotations.openMocks(this);

      when(serverReceiver.getSessionContext()).thenReturn(sessionContext);
      when(sessionContext.getSessionSPI()).thenReturn(sessionSPI);
      when(sessionSPI.getStorageManager()).thenReturn(nullStoreManager);
   }

   @Test
   public void testReaderReturnsNullIfCalledOnPartialDelivery() throws Exception {
      AMQPTunneledCoreMessageReader reader = new AMQPTunneledCoreMessageReader(serverReceiver);
      Delivery delivery = Mockito.mock(Delivery.class);

      reader.open();

      when(delivery.isPartial()).thenReturn(true);

      try {
         assertNull(reader.readBytes(delivery));
      } catch (IllegalStateException e) {
         fail("Should not throw as the delivery is partial so no data should be read.");
      }
   }

   @Test
   public void testReadMessageFromDeliveryBuffer() throws Exception {
      AMQPTunneledCoreMessageReader reader = new AMQPTunneledCoreMessageReader(serverReceiver);
      Receiver receiver = Mockito.mock(Receiver.class);
      Delivery delivery = Mockito.mock(Delivery.class);

      when(delivery.getLink()).thenReturn(receiver);

      reader.open();

      final CoreMessage message = new CoreMessage();

      message.setDurable(true);
      message.setExpiration(42);
      message.putStringProperty("a", "a");

      final ReadableBuffer coreMessageFrame = createCoreMessageDelivery(message);

      when(receiver.recv()).thenReturn(coreMessageFrame.duplicate());
      when(delivery.isPartial()).thenReturn(false);

      Message readMessage = null;

      try {
         readMessage = reader.readBytes(delivery);
         assertNotNull(readMessage);
         assertNull(reader.getDeliveryAnnotations());
      } catch (IllegalStateException e) {
         fail("Should not throw as the reader should be able to read just the core message header.");
      }

      assertTrue(readMessage.isDurable());
      assertEquals(42, readMessage.getExpiration());
      assertTrue(readMessage.containsProperty("a"));
      assertEquals("a", readMessage.getStringProperty("a"));
      assertFalse(readMessage.isLargeMessage());
   }

   @Test
   public void testReadMessageWithAnnotationsFromDeliveryBuffer() throws Exception {
      AMQPTunneledCoreMessageReader reader = new AMQPTunneledCoreMessageReader(serverReceiver);
      Receiver receiver = Mockito.mock(Receiver.class);
      Delivery delivery = Mockito.mock(Delivery.class);

      when(delivery.getLink()).thenReturn(receiver);

      reader.open();

      final CoreMessage message = new CoreMessage();

      message.setDurable(true);
      message.setExpiration(42);
      message.putStringProperty("a", "a");

      final ReadableBuffer coreMessageFrame = createCoreMessageDelivery(message, true);

      when(receiver.recv()).thenReturn(coreMessageFrame.duplicate());
      when(delivery.isPartial()).thenReturn(false);

      Message readMessage = null;

      try {
         readMessage = reader.readBytes(delivery);
         assertNotNull(readMessage);
         assertNotNull(reader.getDeliveryAnnotations());
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
      assertFalse(readMessage.isLargeMessage());

      reader.close();

      assertNull(reader.getDeliveryAnnotations());
   }

   @Test
   public void testReadMessageWithAdditionalSectionsThatShouldBeIgnored() throws Exception {
      AMQPTunneledCoreMessageReader reader = new AMQPTunneledCoreMessageReader(serverReceiver);
      Receiver receiver = Mockito.mock(Receiver.class);
      Delivery delivery = Mockito.mock(Delivery.class);

      when(delivery.getLink()).thenReturn(receiver);

      reader.open();

      final CoreMessage message = new CoreMessage();

      message.setDurable(true);
      message.setExpiration(42);
      message.putStringProperty("a", "a");

      final ReadableBuffer coreMessageFrame = createCoreMessageWithMoreSections(message);

      when(receiver.recv()).thenReturn(coreMessageFrame.duplicate());
      when(delivery.isPartial()).thenReturn(false);

      Message readMessage = null;

      try {
         readMessage = reader.readBytes(delivery);
         assertNotNull(readMessage);
         assertNotNull(reader.getDeliveryAnnotations());
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
      assertFalse(readMessage.isLargeMessage());

      reader.close();

      assertNull(reader.getDeliveryAnnotations());
   }

   private ReadableBuffer createCoreMessageDelivery(CoreMessage message) {
      return createCoreMessageDelivery(message, false);
   }

   private ReadableBuffer createCoreMessageDelivery(CoreMessage message, boolean addAnnotations) {
      final byte[] payload = new byte[1024];
      final Random random = new Random(System.currentTimeMillis());

      random.nextBytes(payload);

      message.initBuffer(payload.length);
      message.getBodyBuffer().writeBytes(payload);

      final Persister<Message> persister = message.getPersister();
      final int encodedSize = persister.getEncodeSize(message);
      final ByteBuf buffer = Unpooled.buffer(encodedSize + DATA_SECTION_ENCODING_BYTES); // Account for the data section

      if (addAnnotations) {
         final EncoderImpl encoder = TLSEncode.getEncoder();
         final DeliveryAnnotations annotations = new DeliveryAnnotations(new HashMap<>());

         annotations.getValue().put(Symbol.valueOf("a"), "a");
         annotations.getValue().put(Symbol.valueOf("b"), "b");
         annotations.getValue().put(Symbol.valueOf("c"), "c");

         try {
            encoder.setByteBuffer(new NettyWritable(buffer));
            encoder.writeObject(annotations);
         } finally {
            encoder.setByteBuffer((WritableBuffer) null);
         }
      }

      writeDataSectionTypeInfo(buffer, encodedSize);

      final ActiveMQBuffer bufferWrapper = ActiveMQBuffers.wrappedBuffer(buffer);

      message.persist(bufferWrapper);

      // Update the buffer that was allocated with the bytes that were written using the wrapper
      // since the wrapper doesn't update the wrapper buffer.
      buffer.writerIndex(buffer.writerIndex() + encodedSize);

      return new NettyReadable(buffer);
   }

   private ReadableBuffer createCoreMessageWithMoreSections(CoreMessage message) {
      final byte[] payload = new byte[1024];
      final Random random = new Random(System.currentTimeMillis());

      random.nextBytes(payload);

      message.initBuffer(payload.length);
      message.getBodyBuffer().writeBytes(payload);

      final Persister<Message> persister = message.getPersister();
      final int encodedSize = persister.getEncodeSize(message);
      final ByteBuf buffer = Unpooled.buffer(encodedSize + DATA_SECTION_ENCODING_BYTES); // Account for the data section

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
         encoder.setByteBuffer(new NettyWritable(buffer));
         encoder.writeObject(header);
         encoder.writeObject(annotations);
         encoder.writeObject(msgAnnotations);
         encoder.writeObject(properties);
         encoder.writeObject(appProperties);
      } finally {
         encoder.setByteBuffer((WritableBuffer) null);
      }

      writeDataSectionTypeInfo(buffer, encodedSize);

      final ActiveMQBuffer bufferWrapper = ActiveMQBuffers.wrappedBuffer(buffer);

      message.persist(bufferWrapper);

      // Update the buffer that was allocated with the bytes that were written using the wrapper
      // since the wrapper doesn't update the wrapper buffer.
      buffer.writerIndex(buffer.writerIndex() + encodedSize);

      return new NettyReadable(buffer);
   }

   private void writeDataSectionTypeInfo(ByteBuf buffer, int encodedSize) {
      buffer.writeByte(EncodingCodes.DESCRIBED_TYPE_INDICATOR);
      buffer.writeByte(EncodingCodes.SMALLULONG);
      buffer.writeByte(DATA_DESCRIPTOR);
      buffer.writeByte(EncodingCodes.VBIN32);
      buffer.writeInt(encodedSize); // Core message will encode into this size.
   }
}
