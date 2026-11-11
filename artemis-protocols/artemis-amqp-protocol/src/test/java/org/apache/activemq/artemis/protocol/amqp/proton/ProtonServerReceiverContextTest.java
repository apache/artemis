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

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQAddressFullException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.util.NettyReadable;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Outcome;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ProtonServerReceiverContextTest {

   @Test
   public void testOnMessageWithAbortedDelivery() throws Exception {
      doOnMessageWithAbortedDeliveryTestImpl(false);
   }

   @Test
   public void testOnMessageWithAbortedDeliveryDrain() throws Exception {
      doOnMessageWithAbortedDeliveryTestImpl(true);
   }

   @Test
   public void addressFull_SourceSupportsModified() throws Exception {
      doOnMessageWithDeliveryException(asList(Rejected.DESCRIPTOR_SYMBOL, Accepted.DESCRIPTOR_SYMBOL, Modified.DESCRIPTOR_SYMBOL), null, new ActiveMQAddressFullException(), Modified.class);
   }

   @Test
   public void addressFull_SourceDoesNotSupportModified() throws Exception {
      doOnMessageWithDeliveryException(asList(Rejected.DESCRIPTOR_SYMBOL, Accepted.DESCRIPTOR_SYMBOL), null, new ActiveMQAddressFullException(), Rejected.class);
   }

   @Test
   public void otherFailure_SourceSupportsRejects() throws Exception {
      doOnMessageWithDeliveryException(asList(Rejected.DESCRIPTOR_SYMBOL, Accepted.DESCRIPTOR_SYMBOL, Modified.DESCRIPTOR_SYMBOL), null, new ActiveMQException(), Rejected.class);
   }

   @Test
   public void otherFailure_SourceDoesNotSupportReject() throws Exception {
      doOnMessageWithDeliveryException(singletonList(Accepted.DESCRIPTOR_SYMBOL), Accepted.getInstance(), new ActiveMQException(), Accepted.class);
      // violates AMQP specification - see explanation ProtonServerReceiverContext.determineDeliveryState
      doOnMessageWithDeliveryException(singletonList(Accepted.DESCRIPTOR_SYMBOL), null, new ActiveMQException(), Rejected.class);
   }

   @Test
   public void testClearLargeOnClose() throws Exception {
      Receiver mockReceiver = mock(Receiver.class);
      AMQPConnectionContext mockConnContext = mock(AMQPConnectionContext.class);

      when(mockConnContext.getAmqpCredits()).thenReturn(100);
      when(mockConnContext.getAmqpLowCredits()).thenReturn(30);

      when(mockConnContext.getProtocolManager()).thenReturn(mock(ProtonProtocolManager.class));

      AMQPSessionCallback mockSessionSpi = mock(AMQPSessionCallback.class);
      when(mockSessionSpi.getStorageManager()).thenReturn(new NullStorageManager());
      when(mockSessionSpi.createStandardMessage(any(), any())).thenAnswer((Answer<AMQPStandardMessage>) invocation -> new AMQPStandardMessage(0, createAMQPMessageBuffer(), null, null));

      AMQPSessionContext mockProtonContext = mock(AMQPSessionContext.class);
      when(mockProtonContext.getSessionSPI()).thenReturn(mockSessionSpi);

      AtomicInteger clearLargeMessage = new AtomicInteger(0);
      ProtonServerReceiverContext rc = new ProtonServerReceiverContext(mockSessionSpi, mockConnContext, mockProtonContext, mockReceiver) {
         @Override
         protected void closeCurrentReader() {
            super.closeCurrentReader();
            clearLargeMessage.incrementAndGet();
         }
      };

      Delivery mockDelivery = mock(Delivery.class);
      when(mockDelivery.isAborted()).thenReturn(false);
      when(mockDelivery.isPartial()).thenReturn(false);
      when(mockDelivery.getLink()).thenReturn(mockReceiver);

      when(mockReceiver.current()).thenReturn(mockDelivery);
      when(mockReceiver.recv()).thenReturn(createAMQPMessageBuffer());

      rc.onMessage(mockDelivery);

      rc.close(true);

      verify(mockReceiver, times(1)).current();
      verify(mockReceiver, times(1)).advance();

      assertTrue(clearLargeMessage.get() > 0);
   }

   private void doOnMessageWithAbortedDeliveryTestImpl(boolean drain) throws ActiveMQAMQPException {
      Receiver mockReceiver = mock(Receiver.class);
      AMQPConnectionContext mockConnContext = mock(AMQPConnectionContext.class);

      when(mockConnContext.getAmqpCredits()).thenReturn(100);
      when(mockConnContext.getAmqpLowCredits()).thenReturn(30);

      when(mockConnContext.getProtocolManager()).thenReturn(mock(ProtonProtocolManager.class));

      AtomicInteger clearLargeMessage = new AtomicInteger(0);
      ProtonServerReceiverContext rc = new ProtonServerReceiverContext(null, mockConnContext, null, mockReceiver) {
         @Override
         protected void closeCurrentReader() {
            super.closeCurrentReader();
            clearLargeMessage.incrementAndGet();
         }
      };

      Delivery mockDelivery = mock(Delivery.class);
      when(mockDelivery.isAborted()).thenReturn(true);
      when(mockDelivery.isPartial()).thenReturn(true);
      when(mockDelivery.getLink()).thenReturn(mockReceiver);

      when(mockReceiver.current()).thenReturn(mockDelivery);

      if (drain) {
         when(mockReceiver.getDrain()).thenReturn(true);
      }

      rc.onMessage(mockDelivery);

      verify(mockReceiver, times(1)).current();
      verify(mockReceiver, times(1)).advance();
      verify(mockDelivery, times(1)).settle();

      verify(mockReceiver, times(1)).getDrain();
      if (drain) {
         verify(mockReceiver, times(1)).getQueued();
         verify(mockReceiver, times(1)).getCredit();
      } else {
         verify(mockReceiver, times(1)).flow(1);
      }
      verifyNoMoreInteractions(mockReceiver);

      assertTrue(clearLargeMessage.get() > 0);
   }

   private void doOnMessageWithDeliveryException(List<Symbol> sourceSymbols,
                                                 Outcome defaultOutcome,
                                                 Exception deliveryException,
                                                 Class<? extends DeliveryState> expectedDeliveryState) throws Exception {
      AMQPConnectionContext mockConnContext = mock(AMQPConnectionContext.class);
      doAnswer((Answer<Void>) invocation -> {
         Runnable runnable = invocation.getArgument(0);
         runnable.run();
         return null;
      }).when(mockConnContext).runLater(any(Runnable.class));

      doAnswer((Answer<Void>) invocation -> {
         Runnable runnable = invocation.getArgument(0);
         runnable.run();
         return null;
      }).when(mockConnContext).runNow(any(Runnable.class));
      ProtonProtocolManager mockProtocolManager = mock(ProtonProtocolManager.class);
      when(mockProtocolManager.isUseModifiedForTransientDeliveryErrors()).thenReturn(true);
      when(mockConnContext.getProtocolManager()).thenReturn(mockProtocolManager);

      AMQPSessionCallback mockSession = mock(AMQPSessionCallback.class);
      when(mockSession.createStandardMessage(any(), any())).thenAnswer((Answer<AMQPStandardMessage>) invocation -> new AMQPStandardMessage(0, createAMQPMessageBuffer(), null, null));

      AMQPSessionContext mockSessionContext = mock(AMQPSessionContext.class);
      when(mockSessionContext.getSessionSPI()).thenReturn(mockSession);

      Receiver mockReceiver = mock(Receiver.class);
      ProtonServerReceiverContext rc = new ProtonServerReceiverContext(mockSession, mockConnContext, mockSessionContext, mockReceiver);
      rc.incrementSettle();

      Delivery mockDelivery = mock(Delivery.class);
      when(mockDelivery.getLink()).thenReturn(mockReceiver);

      when(mockReceiver.current()).thenReturn(mockDelivery);
      Source source = new Source();
      source.setOutcomes(sourceSymbols.toArray(new Symbol[]{}));
      source.setDefaultOutcome(defaultOutcome);
      when(mockReceiver.getSource()).thenReturn(source);

      doThrow(deliveryException).when(mockSession).serverSend(eq(rc), nullable(Transaction.class), eq(mockReceiver), eq(mockDelivery), nullable(SimpleString.class), any(), any(RoutingContext.class), nullable(AMQPMessage.class));

      rc.onMessage(mockDelivery);

      verify(mockDelivery, times(1)).settle();
      verify(mockDelivery, times(1)).disposition(any(expectedDeliveryState));
   }

   @Test
   public void calculateFlowControl() {
      assertFalse(ProtonServerReceiverContext.isBellowThreshold(1000, 100, 1000));
      assertTrue(ProtonServerReceiverContext.isBellowThreshold(1000, 0, 1000));

      assertEquals(1000, ProtonServerReceiverContext.calculatedUpdateRefill(2000, 1000, 0));
      assertEquals(900, ProtonServerReceiverContext.calculatedUpdateRefill(2000, 1000, 100));
   }

   @Test
   public void testStopDrainsOffCredit() throws Exception {
      final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

      try {
         ActiveMQServer mockServer = mock(ActiveMQServer.class);
         when(mockServer.getScheduledPool()).thenReturn(scheduler);

         Receiver mockReceiver = mock(Receiver.class);
         AMQPConnectionContext mockConnContext = mock(AMQPConnectionContext.class);

         when(mockConnContext.getAmqpCredits()).thenReturn(100);
         when(mockConnContext.getAmqpLowCredits()).thenReturn(30);

         when(mockConnContext.getProtocolManager()).thenReturn(mock(ProtonProtocolManager.class));

         AMQPSessionCallback mockSessionSpi = mock(AMQPSessionCallback.class);
         when(mockSessionSpi.getStorageManager()).thenReturn(new NullStorageManager());
         when(mockSessionSpi.createStandardMessage(any(), any())).thenAnswer((Answer<AMQPStandardMessage>) invocation -> new AMQPStandardMessage(0, createAMQPMessageBuffer(), null, null));

         AMQPSessionContext mockProtonContext = mock(AMQPSessionContext.class);
         when(mockProtonContext.getServer()).thenReturn(mockServer);
         when(mockProtonContext.getSessionSPI()).thenReturn(mockSessionSpi);

         ProtonServerReceiverContext rc = new ProtonServerReceiverContext(mockSessionSpi, mockConnContext, mockProtonContext, mockReceiver);

         AtomicBoolean stopped = new AtomicBoolean();

         when(mockReceiver.getCredit()).thenReturn(100);

         rc.stop(10_000, (rcvr, didStop) -> stopped.set(true));

         assertFalse(stopped.get());

         verify(mockReceiver, times(1)).drain(0);
         verify(mockReceiver, times(1)).draining();
         verify(mockReceiver, times(2)).getCredit();
         verify(mockReceiver, times(1)).getQueued();
         verify(mockReceiver, times(1)).drain(0);
      } finally {
         scheduler.shutdown();
      }
   }

   @Test
   public void testDrainInitiatedWhenDeliveryMarkedFailed() throws Exception {
      final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

      try {
         ActiveMQServer mockServer = mock(ActiveMQServer.class);
         when(mockServer.getScheduledPool()).thenReturn(scheduler);

         Source source = new Source();
         source.setOutcomes(new Symbol[]{Accepted.DESCRIPTOR_SYMBOL, Rejected.DESCRIPTOR_SYMBOL,
                                         Modified.DESCRIPTOR_SYMBOL, Released.DESCRIPTOR_SYMBOL});

         Receiver mockReceiver = mock(Receiver.class);
         when(mockReceiver.getSource()).thenReturn(source);

         ProtonProtocolManager mockProtocolManager = mock(ProtonProtocolManager.class);
         when(mockProtocolManager.getLinkQuiesceTimeout()).thenReturn(100);
         when(mockProtocolManager.isDrainOnTransientDeliveryErrors()).thenReturn(true);

         AMQPConnectionContext mockConnContext = mock(AMQPConnectionContext.class);
         when(mockConnContext.getAmqpCredits()).thenReturn(100);
         when(mockConnContext.getAmqpLowCredits()).thenReturn(30);
         when(mockConnContext.getProtocolManager()).thenReturn(mockProtocolManager);

         doAnswer(invocation -> {
            final Runnable target = invocation.getArgument(0, Runnable.class);
            target.run();
            return null;
         }).when(mockConnContext).runNow(any(Runnable.class));

         doAnswer(invocation -> {
            final Runnable target = invocation.getArgument(0, Runnable.class);
            target.run();
            return null;
         }).when(mockConnContext).runLater(any(Runnable.class));

         AMQPSessionCallback mockSessionSpi = mock(AMQPSessionCallback.class);
         when(mockSessionSpi.getStorageManager()).thenReturn(new NullStorageManager());
         when(mockSessionSpi.createStandardMessage(any(), any())).thenAnswer((Answer<AMQPStandardMessage>) invocation -> new AMQPStandardMessage(0, createAMQPMessageBuffer(), null, null));

         AMQPSessionContext mockProtonContext = mock(AMQPSessionContext.class);
         when(mockProtonContext.getServer()).thenReturn(mockServer);
         when(mockProtonContext.getSessionSPI()).thenReturn(mockSessionSpi);

         ProtonServerReceiverContext rc = new ProtonServerReceiverContext(mockSessionSpi, mockConnContext, mockProtonContext, mockReceiver);

         when(mockReceiver.getCredit()).thenReturn(100);

         Delivery mockDelivery = mock(Delivery.class);
         when(mockDelivery.getLink()).thenReturn(mockReceiver);
         when(mockReceiver.current()).thenReturn(mockDelivery);

         rc.incrementSettle();
         rc.deliveryFailed(mockDelivery, mockReceiver, new ActiveMQAddressFullException("full"));

         Wait.assertTrue(() -> rc.isClosed(), 5000, 50);

         verify(mockReceiver, times(1)).drain(0);
         verify(mockReceiver, times(2)).getCredit();
         verify(mockReceiver, times(1)).drain(0);
         verify(mockReceiver, times(1)).getSource();
      } finally {
         scheduler.shutdown();
      }
   }

   private ReadableBuffer createAMQPMessageBuffer() {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.setContentType(AMQPMessageSupport.OCTET_STREAM_CONTENT_TYPE);

      NettyWritable encoded = new NettyWritable(Unpooled.buffer(1024));
      message.encode(encoded);

      return new NettyReadable(encoded.getByteBuf());
   }
}
