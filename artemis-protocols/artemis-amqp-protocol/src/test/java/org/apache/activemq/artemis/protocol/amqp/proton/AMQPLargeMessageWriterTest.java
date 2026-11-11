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

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.engine.impl.TransportImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Tests for the AMQP Large Message Writer
 */
public class AMQPLargeMessageWriterTest {

   @Mock
   ProtonServerSenderContext serverSender;

   @Mock
   Sender protonSender;

   @Mock
   Session protonSession;

   @Mock
   Connection protonConnection;

   @Mock
   TransportImpl protonTransport;

   @Mock
   Delivery protonDelivery;

   @Mock
   MessageReference reference;

   @Mock
   AMQPLargeMessage message;

   @Mock
   LargeBodyReader bodyReader;

   @Mock
   AMQPConnectionContext connectionContext;

   @Mock
   AMQPSessionContext sessionContext;

   @Mock
   AMQPSessionCallback sessionSPI;

   @Mock
   org.apache.activemq.artemis.spi.core.remoting.Connection transportConnection;

   @Mock
   ActiveMQProtonRemotingConnection remotingConnection;

   @Spy
   NullStorageManager nullStoreManager = new NullStorageManager();

   @BeforeEach
   public void setUp() {
      MockitoAnnotations.openMocks(this);

      when(serverSender.getSessionContext()).thenReturn(sessionContext);
      when(serverSender.getSender()).thenReturn(protonSender);
      when(serverSender.createDelivery(any(), anyInt())).thenReturn(protonDelivery);

      when(protonSender.getSession()).thenReturn(protonSession);
      when(protonSession.getConnection()).thenReturn(protonConnection);
      when(protonConnection.getTransport()).thenReturn(protonTransport);
      when(protonTransport.getOutboundFrameSizeLimit()).thenReturn(65535);

      when(transportConnection.getProtocolConnection()).thenReturn(remotingConnection);
      when(reference.getMessage()).thenReturn(message);
      when(message.isLargeMessage()).thenReturn(true);
      when(message.getLargeBodyReader()).thenReturn(bodyReader);
      when(sessionContext.getSessionSPI()).thenReturn(sessionSPI);
      when(sessionContext.getAMQPConnectionContext()).thenReturn(connectionContext);
      when(connectionContext.flowControl(any())).thenReturn(true);
      when(sessionSPI.getStorageManager()).thenReturn(nullStoreManager);
      when(sessionSPI.getTransportConnection()).thenReturn(transportConnection);
   }

   @Test
   public void testWriterThrowsIllegalStateIfNotOpenedWhenWriteCalled() throws Exception {
      AMQPLargeMessageWriter writer = new AMQPLargeMessageWriter(serverSender);

      try {
         writer.writeBytes(reference);
         fail("Should throw as the writer was not opened.");
      } catch (IllegalStateException e) {
         // Expected
      }
   }

   @Test
   public void testNoWritesWhenProtonSenderIsLocallyClosed() throws Exception {
      AMQPLargeMessageWriter writer = new AMQPLargeMessageWriter(serverSender);

      when(protonSender.getLocalState()).thenReturn(EndpointState.CLOSED);

      writer.open(reference);

      try {
         writer.writeBytes(reference);
      } catch (IllegalStateException e) {
         fail("Should not throw as link was closed before write actioned.");
      }

      verify(reference).getMessage();
      verify(message).usageUp();
      verify(protonSender).getLocalState();

      verifyNoMoreInteractions(reference);
      verifyNoInteractions(protonDelivery);
   }

   @Test
   public void testLargeMessageUsageLoweredOnCloseWhenWriteNotCompleted() throws Exception {
      AMQPLargeMessageWriter writer = new AMQPLargeMessageWriter(serverSender);

      writer.open(reference);

      when(protonSender.getLocalState()).thenReturn(EndpointState.ACTIVE);
      when(protonDelivery.isPartial()).thenReturn(true);

      // The writer will wait for flow control to resume it
      when(connectionContext.flowControl(any())).thenReturn(false);

      verify(message).usageUp();

      try {
         writer.writeBytes(reference);
      } catch (IllegalStateException e) {
         fail("Should not throw as the delivery is completed so no data should be written.");
      }

      verify(message, Mockito.never()).usageDown();
      verify(reference).getMessage();
      verifyNoMoreInteractions(reference);

      try {
         writer.close();
      } catch (IllegalStateException e) {
         fail("Should not throw as the close when write wasn't completed.");
      }

      verify(message).usageDown();
      verify(protonSender).getSession();
      verify(protonDelivery).getTag();
      verify(protonSender, atLeastOnce()).getLocalState();

      verifyNoMoreInteractions(reference);
      verifyNoMoreInteractions(protonDelivery);
   }

   @Test
   public void testTryDeliveringRunAfterClosedDoesNotThrow() throws Exception {
      AMQPLargeMessageWriter writer = new AMQPLargeMessageWriter(serverSender);

      writer.open(reference);

      when(protonSender.getLocalState()).thenReturn(EndpointState.ACTIVE);
      when(protonDelivery.isPartial()).thenReturn(true);

      // The writer will wait for flow control to resume it
      when(connectionContext.flowControl(any())).then(invocation -> {
         ReadyListener listener = invocation.getArgument(0);

         writer.close();

         try {
            listener.readyForWriting();
         } catch (Exception e) {
            fail("Pending deliver should no-op if closed before completion.");
         }

         return false;
      });

      doAnswer(invocation -> {
         Runnable runnable = invocation.getArgument(0);

         try {
            runnable.run();
         } catch (Exception e) {
            fail("Queued invocation of writer tasks should not throw.");
         }

         return null;
      }).when(connectionContext).runLater(any(Runnable.class));

      try {
         writer.writeBytes(reference);
      } catch (IllegalStateException e) {
         fail("Should not throw as the delivery is completed so no data should be written.");
      }

      try {
         writer.close();
      } catch (IllegalStateException e) {
         fail("Should not throw as the close when write wasn't completed.");
      }

      verify(message).usageUp();
      verify(message).usageDown();
      verify(reference).getMessage();
      verify(protonSender).getSession();
      verify(protonDelivery).getTag();
      verify(protonSender, atLeastOnce()).getLocalState();

      verifyNoMoreInteractions(reference);
      verifyNoMoreInteractions(protonDelivery);
   }
}
