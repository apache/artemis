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

import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.AMQP_CREDITS_DEFAULT;
import static org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport.AMQP_LOW_CREDITS_DEFAULT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonAbstractReceiver;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerReceiverContext;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.qpid.proton.engine.Receiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AMQPSessionCallbackTest {

   @Mock
   private AMQPConnectionCallback protonSPI;
   @Mock
   private ProtonProtocolManager manager;
   @Mock
   private AMQPConnectionContext connection;
   @Mock
   private Connection transportConnection;
   @Mock
   private Executor executor;
   @Mock
   private OperationContext operationContext;
   @Mock
   private Receiver receiver;
   @Mock
   private ActiveMQServer server;
   @Mock
   private PagingManager pagingManager;
   @Mock
   private PagingStore pagingStore;


   @BeforeEach
   public void setRule() {
      Mockito.when(connection.isHandler()).thenReturn(true);
   }

   /**
    * Test that the AMQPSessionCallback grants no credit when not at threshold
    */
   @Test
   public void testOfferProducerWithNoAddressDoesNotTopOffCreditAboveThreshold() {
      // Mock returns to get at the runnable that grants credit.
      Mockito.when(manager.getServer()).thenReturn(server);
      Mockito.when(server.getPagingManager()).thenReturn(pagingManager);

      // Capture credit runnable and invoke to trigger credit top off
      ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
      AMQPSessionCallback session = new AMQPSessionCallback(protonSPI, manager, connection, transportConnection, executor, operationContext);
      ProtonAbstractReceiver context = Mockito.mock(ProtonAbstractReceiver.class);

      // Credit is above threshold
      Mockito.when(receiver.getCredit()).thenReturn(AMQP_LOW_CREDITS_DEFAULT + 1);
      Mockito.when(context.isStarted()).thenReturn(true);

      session.flow(null, ProtonServerReceiverContext.createCreditRunnable(AMQP_CREDITS_DEFAULT, AMQP_LOW_CREDITS_DEFAULT, receiver, connection, context));

      // Run the credit refill code.
      Mockito.verify(pagingManager).checkMemory(argument.capture());
      assertNotNull(argument.getValue());
      argument.getValue().run();

      // Ensure we aren't looking at remote credit as that gives us the wrong view of what credit is at the broker
      Mockito.verify(receiver, never()).getRemoteCredit();

      // Credit runnable should not top off credit to configured value
      Mockito.verify(receiver, never()).flow(anyInt());
   }

   /**
    * Test that when at threshold the manager tops off credit for anonymous sender
    */
   @Test
   public void testOfferProducerWithNoAddressTopsOffCreditAtThreshold() {
      // Mock returns to get at the runnable that grants credit.
      Mockito.when(manager.getServer()).thenReturn(server);
      Mockito.when(server.getPagingManager()).thenReturn(pagingManager);

      // Capture credit runnable and invoke to trigger credit top off
      ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
      AMQPSessionCallback session = new AMQPSessionCallback(protonSPI, manager, connection, transportConnection, executor, operationContext);
      ProtonAbstractReceiver context = Mockito.mock(ProtonAbstractReceiver.class);

      // Credit is at threshold
      Mockito.when(receiver.getCredit()).thenReturn(AMQP_LOW_CREDITS_DEFAULT);
      Mockito.when(context.isStarted()).thenReturn(true);

      session.flow(null, ProtonServerReceiverContext.createCreditRunnable(AMQP_CREDITS_DEFAULT, AMQP_LOW_CREDITS_DEFAULT, receiver, connection, context));

      // Run the credit refill code.
      Mockito.verify(pagingManager).checkMemory(argument.capture());
      assertNotNull(argument.getValue());
      argument.getValue().run();

      // Ensure we aren't looking at remote credit as that gives us the wrong view of what credit is at the broker
      Mockito.verify(receiver, never()).getRemoteCredit();

      // Credit runnable should top off credit to configured value
      Mockito.verify(receiver).flow(AMQP_CREDITS_DEFAULT - AMQP_LOW_CREDITS_DEFAULT);
   }

   /**
    * Test that the AMQPSessionCallback grants no credit when not at threshold
    */
   @Test
   public void testOfferProducerWithAddressDoesNotTopOffCreditAboveThreshold() throws Exception {
      // Mock returns to get at the runnable that grants credit.
      Mockito.when(manager.getServer()).thenReturn(server);
      Mockito.when(server.getPagingManager()).thenReturn(pagingManager);
      Mockito.when(pagingManager.getPageStore(any(SimpleString.class))).thenReturn(pagingStore);

      // Capture credit runnable and invoke to trigger credit top off
      ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
      AMQPSessionCallback session = new AMQPSessionCallback(protonSPI, manager, connection, transportConnection, executor, operationContext);
      ProtonAbstractReceiver context = Mockito.mock(ProtonAbstractReceiver.class);

      // Credit is above threshold
      Mockito.when(receiver.getCredit()).thenReturn(AMQP_LOW_CREDITS_DEFAULT + 1);
      Mockito.when(context.isStarted()).thenReturn(true);

      session.flow(SimpleString.of("test"), ProtonServerReceiverContext.createCreditRunnable(AMQP_CREDITS_DEFAULT, AMQP_LOW_CREDITS_DEFAULT, receiver, connection, context));

      // Run the credit refill code.
      Mockito.verify(pagingStore).checkMemory(argument.capture(), Mockito.isA(Consumer.class));
      assertNotNull(argument.getValue());
      argument.getValue().run();

      // Ensure we aren't looking at remote credit as that gives us the wrong view of what credit is at the broker
      Mockito.verify(receiver, never()).getRemoteCredit();

      // Credit runnable should not top off credit to configured value
      Mockito.verify(receiver, never()).flow(anyInt());
   }

   /**
    * Test that when at threshold the manager tops off credit for sender
    */
   @Test
   public void testOfferProducerWithAddressTopsOffCreditAtThreshold() throws Exception {
      // Mock returns to get at the runnable that grants credit.
      Mockito.when(manager.getServer()).thenReturn(server);
      Mockito.when(server.getPagingManager()).thenReturn(pagingManager);
      Mockito.when(pagingManager.getPageStore(any(SimpleString.class))).thenReturn(pagingStore);

      // Capture credit runnable and invoke to trigger credit top off
      ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
      AMQPSessionCallback session = new AMQPSessionCallback(protonSPI, manager, connection, transportConnection, executor, operationContext);
      ProtonAbstractReceiver context = Mockito.mock(ProtonAbstractReceiver.class);

      // Credit is at threshold
      Mockito.when(context.isStarted()).thenReturn(true);
      Mockito.when(receiver.getCredit()).thenReturn(AMQP_LOW_CREDITS_DEFAULT);

      session.flow(SimpleString.of("test"), ProtonServerReceiverContext.createCreditRunnable(AMQP_CREDITS_DEFAULT, AMQP_LOW_CREDITS_DEFAULT, receiver, connection, context));

      // Run the credit refill code.
      Mockito.verify(pagingStore).checkMemory(argument.capture(), Mockito.isA(Consumer.class));
      assertNotNull(argument.getValue());
      argument.getValue().run();

      // Ensure we aren't looking at remote credit as that gives us the wrong view of what credit is at the broker
      Mockito.verify(receiver, never()).getRemoteCredit();

      // Credit runnable should top off credit to configured value
      Mockito.verify(receiver).flow(AMQP_CREDITS_DEFAULT - AMQP_LOW_CREDITS_DEFAULT);
   }

   /**
    * Test that the AMQPSessionCallback grants no credit when the computation results in negative credit
    */
   @Test
   public void testOfferProducerWithNoAddressDoesNotGrantNegativeCredit() {
      // Mock returns to get at the runnable that grants credit.
      Mockito.when(manager.getServer()).thenReturn(server);
      Mockito.when(server.getPagingManager()).thenReturn(pagingManager);

      // Capture credit runnable and invoke to trigger credit top off
      ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
      AMQPSessionCallback session = new AMQPSessionCallback(protonSPI, manager, connection, transportConnection, executor, operationContext);
      ProtonAbstractReceiver context = Mockito.mock(ProtonAbstractReceiver.class);

      // Credit is at threshold
      Mockito.when(receiver.getCredit()).thenReturn(AMQP_LOW_CREDITS_DEFAULT);
      Mockito.when(context.isStarted()).thenReturn(true);

      session.flow(null, ProtonServerReceiverContext.createCreditRunnable(1, AMQP_LOW_CREDITS_DEFAULT, receiver, connection, context));

      // Run the credit refill code.
      Mockito.verify(pagingManager).checkMemory(argument.capture());
      assertNotNull(argument.getValue());
      argument.getValue().run();

      // Ensure we aren't looking at remote credit as that gives us the wrong view of what credit is at the broker
      Mockito.verify(receiver, never()).getRemoteCredit();

      // Credit runnable should not grant what would be negative credit here
      Mockito.verify(receiver, never()).flow(anyInt());
   }

   /**
    * Test that the AMQPSessionCallback grants no credit when the computation results in negative credit
    */
   @Test
   public void testOfferProducerWithAddressDoesNotGrantNegativeCredit() throws Exception {
      // Mock returns to get at the runnable that grants credit.
      Mockito.when(manager.getServer()).thenReturn(server);
      Mockito.when(server.getPagingManager()).thenReturn(pagingManager);
      Mockito.when(pagingManager.getPageStore(any(SimpleString.class))).thenReturn(pagingStore);

      // Capture credit runnable and invoke to trigger credit top off
      ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
      AMQPSessionCallback session = new AMQPSessionCallback(protonSPI, manager, connection, transportConnection, executor, operationContext);
      ProtonAbstractReceiver context = Mockito.mock(ProtonAbstractReceiver.class);

      // Credit is at threshold
      Mockito.when(receiver.getCredit()).thenReturn(AMQP_LOW_CREDITS_DEFAULT);
      Mockito.when(context.isStarted()).thenReturn(true);

      session.flow(SimpleString.of("test"), ProtonServerReceiverContext.createCreditRunnable(1, AMQP_LOW_CREDITS_DEFAULT, receiver, connection, context));

      // Run the credit refill code.
      Mockito.verify(pagingStore).checkMemory(argument.capture(), Mockito.isA(Consumer.class));
      assertNotNull(argument.getValue());
      argument.getValue().run();

      // Ensure we aren't looking at remote credit as that gives us the wrong view of what credit is at the broker
      Mockito.verify(receiver, never()).getRemoteCredit();

      // Credit runnable should not grant what would be negative credit here
      Mockito.verify(receiver, never()).flow(anyInt());
   }

   @Test
   public void testCloseBoolCallsProtonSessionClose() throws Exception {
      Mockito.reset(connection);
      Mockito.when(manager.getServer()).thenReturn(server);

      // Capture credit runnable and invoke to trigger credit top off
      ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
      AMQPSessionCallback session = new AMQPSessionCallback(protonSPI, manager, connection, transportConnection, executor, operationContext);

      AMQPSessionContext protonSession = Mockito.mock(AMQPSessionContext.class);
      session.init(protonSession, null);
      session.close(false);

      Mockito.verify(protonSession).close();
   }

   /**
    * Test that a producer that is not started will not offer credit to top off current.
    */
   @Test
   public void testProducerThatIsNotStartedDoesNotTopOffCredit() throws Exception {
      // Mock returns to get at the runnable that grants credit.
      Mockito.when(manager.getServer()).thenReturn(server);
      Mockito.when(server.getPagingManager()).thenReturn(pagingManager);
      Mockito.when(pagingManager.getPageStore(any(SimpleString.class))).thenReturn(pagingStore);

      // Capture credit runnable and invoke to trigger credit top off
      ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
      AMQPSessionCallback session = new AMQPSessionCallback(protonSPI, manager, connection, transportConnection, executor, operationContext);
      ProtonAbstractReceiver context = Mockito.mock(ProtonAbstractReceiver.class);

      // Credit is at threshold
      Mockito.when(context.isStarted()).thenReturn(false);

      session.flow(SimpleString.of("test"), ProtonServerReceiverContext.createCreditRunnable(AMQP_CREDITS_DEFAULT, AMQP_LOW_CREDITS_DEFAULT, receiver, connection, context));

      // Run the credit refill code.
      Mockito.verify(pagingStore).checkMemory(argument.capture(), Mockito.isA(Consumer.class));
      assertNotNull(argument.getValue());
      argument.getValue().run();

      // Ensure we aren't looking at remote credit as that gives us the wrong view of what credit is at the broker
      Mockito.verify(receiver, never()).getRemoteCredit();

      // Credit runnable should not top off credit to configured value
      Mockito.verifyNoInteractions(receiver);
   }
}
