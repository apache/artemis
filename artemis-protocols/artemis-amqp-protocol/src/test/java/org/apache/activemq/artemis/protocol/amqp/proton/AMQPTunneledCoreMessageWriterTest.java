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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.EncodingCodes;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.WritableBuffer;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Test for the non-large tunneled core message writer
 */
public class AMQPTunneledCoreMessageWriterTest {

   private static final byte DATA_DESCRIPTOR = 0x75;

   @Mock
   ProtonServerSenderContext serverSender;

   @Mock
   Sender protonSender;

   @Mock
   Delivery protonDelivery;

   @Mock
   MessageReference reference;

   @Mock
   ICoreMessage message;

   @Mock
   AMQPSessionContext sessionContext;

   @Mock
   AMQPSessionCallback sessionSPI;

   @Spy
   NullStorageManager nullStoreManager = new NullStorageManager();

   @Captor
   ArgumentCaptor<ReadableBuffer> tunneledCaptor;

   @BeforeEach
   public void setUp() {
      MockitoAnnotations.openMocks(this);

      when(serverSender.getSessionContext()).thenReturn(sessionContext);
      when(serverSender.getSender()).thenReturn(protonSender);
      when(serverSender.createDelivery(any(), anyInt())).thenReturn(protonDelivery);
      when(reference.getMessage()).thenReturn(message);
      when(sessionContext.getSessionSPI()).thenReturn(sessionSPI);
      when(sessionSPI.getStorageManager()).thenReturn(nullStoreManager);
   }

   @Test
   public void testNoWritesWhenProtonSenderIsLocallyClosed() throws Exception {
      AMQPTunneledCoreMessageWriter writer = new AMQPTunneledCoreMessageWriter(serverSender);

      when(protonSender.getLocalState()).thenReturn(EndpointState.CLOSED);

      writer.open(Mockito.mock(MessageReference.class));

      try {
         writer.writeBytes(reference);
      } catch (IllegalStateException e) {
         fail("Should not throw as the delivery is partial so no data should be read.");
      }

      verify(protonSender).getLocalState();

      verifyNoInteractions(reference);
      verifyNoInteractions(protonDelivery);
   }

   @Test
   public void testMessageEncodingWrittenToDeliveryWithoutAnnotations() throws Exception {
      doTestMessageEncodingWrittenToDeliveryWithAnnotations(false);
   }

   @Test
   public void testMessageEncodingWrittenToDeliveryWithAnnotations() throws Exception {
      doTestMessageEncodingWrittenToDeliveryWithAnnotations(true);
   }

   private void doTestMessageEncodingWrittenToDeliveryWithAnnotations(boolean deliveryAnnotations) throws Exception {
      AMQPTunneledCoreMessageWriter writer = new AMQPTunneledCoreMessageWriter(serverSender);

      final ByteBuf expectedEncoding = Unpooled.buffer();

      final byte[] messageBytes = new byte[4];

      messageBytes[0] = 1;
      messageBytes[1] = 2;
      messageBytes[2] = 3;
      messageBytes[3] = 4;

      if (deliveryAnnotations) {
         final DeliveryAnnotations annotations = new DeliveryAnnotations(new HashMap<>());

         annotations.getValue().put(Symbol.valueOf("a"), "a");
         annotations.getValue().put(Symbol.valueOf("b"), "b");
         annotations.getValue().put(Symbol.valueOf("c"), "c");

         writeDeliveryAnnotations(expectedEncoding, annotations);

         when(reference.getProtocolData(any())).thenReturn(annotations);
      }

      writeDataSection(expectedEncoding, messageBytes);

      when(protonSender.getLocalState()).thenReturn(EndpointState.ACTIVE);
      when(message.getPersistSize()).thenReturn(messageBytes.length);

      doAnswer(invocation -> {
         final ActiveMQBuffer buffer = invocation.getArgument(0);

         buffer.writeBytes(messageBytes);

         return null;
      }).when(message).persist(any(ActiveMQBuffer.class));

      writer.open(Mockito.mock(MessageReference.class));

      try {
         writer.writeBytes(reference);
      } catch (IllegalStateException e) {
         fail("Should not throw as the delivery is complete and all data should have been written.");
      }

      verify(reference).getMessage();
      verify(reference).getProtocolData(any());
      verify(protonSender).getLocalState();
      verify(protonSender).sendNoCopy(tunneledCaptor.capture());

      final ReadableBuffer tunneledBytes = tunneledCaptor.getValue();
      final ByteBuffer tunneled = tunneledBytes.byteBuffer();
      final ByteBuf tunneledByteBuf = Unpooled.wrappedBuffer(tunneled);

      assertTrue(tunneledByteBuf.isReadable());
      assertEquals(expectedEncoding.readableBytes(), tunneledByteBuf.readableBytes());
      assertEquals(expectedEncoding, tunneledByteBuf);

      verifyNoMoreInteractions(reference);
      verifyNoInteractions(protonDelivery);
   }

   private void writeDeliveryAnnotations(ByteBuf buffer, DeliveryAnnotations annotations) {
      final EncoderImpl encoder = TLSEncode.getEncoder();

      try {
         encoder.setByteBuffer(new NettyWritable(buffer));
         encoder.writeObject(annotations);
      } finally {
         encoder.setByteBuffer((WritableBuffer) null);
      }
   }

   private void writeDataSection(ByteBuf buffer, byte[] payload) {
      buffer.writeByte(EncodingCodes.DESCRIBED_TYPE_INDICATOR);
      buffer.writeByte(EncodingCodes.SMALLULONG);
      buffer.writeByte(DATA_DESCRIPTOR);
      buffer.writeByte(EncodingCodes.VBIN32);
      buffer.writeInt(payload.length); // Core message will encode into this size.
      buffer.writeBytes(payload);
   }
}
