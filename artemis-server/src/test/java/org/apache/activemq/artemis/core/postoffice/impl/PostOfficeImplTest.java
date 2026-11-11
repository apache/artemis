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
package org.apache.activemq.artemis.core.postoffice.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.DivertImpl;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PostOfficeImplTest {

   private static final int EXPIRATION_DELTA = 5000;

   @Test
   public void testNoExpiryWhenExpirationSetLow() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(1L);
      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setNoExpiry(true));
      Mockito.verify(mockMessage).setExpiration(0);
   }

   @Test
   public void testNoExpiryWhenExpirationSetHigh() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(Long.MAX_VALUE);
      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setNoExpiry(true));
      Mockito.verify(mockMessage).setExpiration(0);
   }

   @Test
   public void testNoExpiryWhenExpirationNotSet() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(0L);
      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setNoExpiry(true));
      Mockito.verify(mockMessage, Mockito.never()).setExpiration(Mockito.anyLong());
   }

   @Test
   public void testExpiryDelayWhenExpirationNotSet() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(0L);
      final long expiryDelay = 123456L;
      final long startTime = System.currentTimeMillis();

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setExpiryDelay(expiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + expiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   @Test
   public void testExpiryDelayWhenExpirationSet() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(1L);
      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setExpiryDelay(9999L));
      Mockito.verify(mockMessage, Mockito.never()).setExpiration(Mockito.anyLong());
   }

   @Test
   public void testMinExpiryDelayWhenExpirationNotSet() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(0L);
      final long minExpiryDelay = 123456L;
      final long startTime = System.currentTimeMillis();

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setMinExpiryDelay(minExpiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + minExpiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   @Test
   public void testMinExpiryDelayWhenExpirationSet() {
      Message mockMessage = Mockito.mock(Message.class);
      long origExpiration = 1234L;
      Mockito.when(mockMessage.getExpiration()).thenReturn(origExpiration);
      final long minExpiryDelay = 123456L;
      assertTrue(minExpiryDelay > origExpiration);
      final long startTime = System.currentTimeMillis();

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setMinExpiryDelay(minExpiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + minExpiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   @Test
   public void testMaxExpiryDelayWhenExpirationNotSet() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(0L);
      final long maxExpiryDelay = 123456L;
      final long startTime = System.currentTimeMillis();

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setMaxExpiryDelay(maxExpiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + maxExpiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   @Test
   public void testMaxExpiryDelayWhenExpirationSet() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(Long.MAX_VALUE);
      final long maxExpiryDelay = 123456L;
      final long startTime = System.currentTimeMillis();

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setMaxExpiryDelay(maxExpiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + maxExpiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   @Test
   public void testMinAndMaxExpiryDelayWhenExpirationNotSet() {
      Message mockMessage = Mockito.mock(Message.class);
      long origExpiration = 0L;
      Mockito.when(mockMessage.getExpiration()).thenReturn(origExpiration);
      final long minExpiryDelay = 100_000L;
      final long maxExpiryDelay = 300_000L;
      final long startTime = System.currentTimeMillis();

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setMinExpiryDelay(minExpiryDelay).setMaxExpiryDelay(maxExpiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + maxExpiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   @Test
   public void testMinAndMaxExpiryDelayWhenExpirationSetInbetween() {
      Message mockMessage = Mockito.mock(Message.class);
      final long startTime = System.currentTimeMillis();
      long origExpiration = startTime + 200_000L;
      Mockito.when(mockMessage.getExpiration()).thenReturn(origExpiration);
      final long minExpiryDelay = 100_000L;
      final long maxExpiryDelay = 300_000L;

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setMinExpiryDelay(minExpiryDelay).setMaxExpiryDelay(maxExpiryDelay));

      Mockito.verify(mockMessage, Mockito.never()).setExpiration(Mockito.anyLong());
   }

   @Test
   public void testMinAndMaxExpiryDelayWhenExpirationSetAbove() {
      Message mockMessage = Mockito.mock(Message.class);
      final long startTime = System.currentTimeMillis();
      long origExpiration = startTime + 400_000L;
      Mockito.when(mockMessage.getExpiration()).thenReturn(origExpiration);
      final long minExpiryDelay = 100_000L;
      final long maxExpiryDelay = 300_000L;

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setMinExpiryDelay(minExpiryDelay).setMaxExpiryDelay(maxExpiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + maxExpiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   @Test
   public void testMinAndMaxExpiryDelayWhenExpirationSetBelow() {
      Message mockMessage = Mockito.mock(Message.class);
      final long startTime = System.currentTimeMillis();
      long origExpiration = startTime + 50_000;
      Mockito.when(mockMessage.getExpiration()).thenReturn(origExpiration);
      final long minExpiryDelay = 100_000L;
      final long maxExpiryDelay = 300_000L;

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setMinExpiryDelay(minExpiryDelay).setMaxExpiryDelay(maxExpiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + minExpiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   private void assertExpirationSetAsExpected(final long expectedExpirationLow, final long expectedExpirationHigh, final Long actualExpirationSet) {
      assertNotNull(actualExpirationSet);

      assertTrue(actualExpirationSet >= expectedExpirationLow, () -> "Expected set expiration of at least " + expectedExpirationLow + ", but was: " + actualExpirationSet);
      assertTrue(actualExpirationSet < expectedExpirationHigh, "Expected set expiration less than " + expectedExpirationHigh + ", but was: " + actualExpirationSet);
   }

   @Test
   public void testPrecedencNoExpiryOverExpiryDelay() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(0L);
      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setNoExpiry(true).setExpiryDelay(10L));
      Mockito.verify(mockMessage, Mockito.never()).setExpiration(Mockito.anyLong());
   }

   @Test
   public void testPrecedencNoExpiryOverMaxExpiryDelay() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(0L);
      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setNoExpiry(true).setMaxExpiryDelay(10L));
      Mockito.verify(mockMessage, Mockito.never()).setExpiration(Mockito.anyLong());
   }

   @Test
   public void testPrecedencNoExpiryOverMinExpiryDelay() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(0L);
      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setNoExpiry(true).setMinExpiryDelay(10L));
      Mockito.verify(mockMessage, Mockito.never()).setExpiration(Mockito.anyLong());
   }

   @Test
   public void testPrecedencExpiryDelayOverMaxExpiryDelay() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(0L);
      final long expiryDelay = 1000L;
      final long maxExpiryDelay = 999999999L;
      final long startTime = System.currentTimeMillis();

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setExpiryDelay(expiryDelay).setMaxExpiryDelay(maxExpiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + expiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   @Test
   public void testPrecedencExpiryDelayOverMinExpiryDelay() {
      Message mockMessage = Mockito.mock(Message.class);
      Mockito.when(mockMessage.getExpiration()).thenReturn(0L);
      final long expiryDelay = 1000L;
      final long minExpiryDelay = 999999999L;
      final long startTime = System.currentTimeMillis();

      PostOfficeImpl.applyExpiryDelay(mockMessage, new AddressSettings().setExpiryDelay(expiryDelay).setMinExpiryDelay(minExpiryDelay));

      final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
      Mockito.verify(mockMessage).setExpiration(captor.capture());

      final long expectedExpirationLow = startTime + expiryDelay;
      final long expectedExpirationHigh = expectedExpirationLow + EXPIRATION_DELTA; // Allowing a delta
      final Long actualExpirationSet = captor.getValue();

      assertExpirationSetAsExpected(expectedExpirationLow, expectedExpirationHigh, actualExpirationSet);
   }

   @Test
   public void testProcessRouteDoesNotCallMirrorControllerWhenMessageDropped() throws Exception {

      // Setup Members (mocks and real objects) to be used on PostOffice

      StorageManager storageManager = mock(StorageManager.class);
      AtomicLong sequence = new AtomicLong(1L);
      when(storageManager.generateID()).thenAnswer(invocation -> sequence.incrementAndGet());

      ActiveMQServer server = mock(ActiveMQServer.class);
      PagingManager pagingManager = mock(PagingManager.class);
      PagingStore pagingStore = mock(PagingStore.class);
      MirrorController mirrorController = mock(MirrorController.class);
      ManagementService managementService = mock(ManagementService.class);
      QueueFactory queueFactory = mock(QueueFactory.class);
      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
      HierarchicalRepository<AddressSettings> hierarchicalRepository = new HierarchicalObjectRepository<>();

      PostOfficeImpl postOffice = new PostOfficeImpl(server, storageManager, pagingManager, queueFactory,
                                                     managementService, 100, 100,
                                                     wildcardConfiguration, -1, false, hierarchicalRepository).setMirrorControlSource(mirrorController);

      SimpleString address = RandomUtil.randomUUIDSimpleString();

      Message message = mock(Message.class);
      final AtomicBoolean dropped = new AtomicBoolean();
      when(message.getAddressSimpleString()).thenReturn(address);
      when(message.hasScheduledDeliveryTime()).thenReturn(false);
      when(message.isDurable()).thenReturn(false);
      when(message.isDropped()).thenAnswer(invocation -> dropped.get());
      when(message.setDropped(true)).thenAnswer(invocation -> {
         dropped.set(true);
         return message;
      });

      QueueImpl mockedQueue = Mockito.mock(QueueImpl.class);

      RoutingContext context = new RoutingContextImpl(null);
      context.addQueue(address, mockedQueue);

      when(pagingStore.getAddress()).thenReturn(address);
      when(pagingManager.getPageStore(address)).thenReturn(pagingStore);

      when(storageManager.addToPage(eq(pagingStore), any(), any(), any()))
         .thenAnswer(invocation -> {
            // Simulate message.drop() being called inside addToPage
            message.setDropped(true);
            return true;
         });

      postOffice.processRoute(message, context, false);

      assertTrue(dropped.get());

      verify(mirrorController, never()).sendMessage(any(), any(), any());

      verify(mockedQueue, never()).refUp(any());
   }

   // test that when mirror is disabled, the divert routing countext will follow along with the initial configuration
   // for example, the expiration disabled mirroring, and if a 'divert' is configured on the expiration,
   // that divert operation should also have mirror disabled
   @Test
   public void testMirrorDisablePropagation() throws Exception {
      // Setup Members (mocks and real objects) to be used on PostOffice

      StorageManager storageManager = mock(StorageManager.class);
      AtomicLong sequence = new AtomicLong(1L);
      when(storageManager.generateID()).thenAnswer(invocation -> sequence.incrementAndGet());

      ActiveMQServer server = mock(ActiveMQServer.class);
      PagingManager pagingManager = mock(PagingManager.class);
      PagingStore pagingStore = mock(PagingStore.class);
      MirrorController mirrorController = mock(MirrorController.class);
      ManagementService managementService = mock(ManagementService.class);
      QueueFactory queueFactory = mock(QueueFactory.class);
      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration().setRoutingEnabled(false);
      HierarchicalRepository<AddressSettings> hierarchicalRepository = new HierarchicalObjectRepository<>();

      PostOfficeImpl postOffice = new PostOfficeImpl(server, storageManager, pagingManager, queueFactory,
                                                     managementService, 100, 100,
                                                     wildcardConfiguration, -1, false, hierarchicalRepository);


      postOffice.setMirrorControlSource(mirrorController);

      SimpleString address = SimpleString.of("sourceAddress");
      SimpleString forwardAddress = SimpleString.of("destinationForDivert");
      DivertBinding divertBinding = new DivertBinding(sequence.incrementAndGet(), address, new DivertImpl(address, address, forwardAddress, forwardAddress, true, null, null, postOffice, storageManager, ComponentConfigurationRoutingType.ANYCAST));

      QueueImpl targetDivertQueue = mock(QueueImpl.class);
      when(targetDivertQueue.getName()).thenReturn(forwardAddress);
      doAnswer(f -> {
         RoutingContext context = f.getArgument(1);
         // simulating the routing of a real queue
         context.addQueue(forwardAddress, targetDivertQueue);
         return null;
      }).when(targetDivertQueue).route(any(), any());

      when(targetDivertQueue.getAddress()).thenReturn(forwardAddress);

      QueueBinding queueBinding = new LocalQueueBinding(forwardAddress, targetDivertQueue, RandomUtil.randomUUIDSimpleString());

      postOffice.addBinding(queueBinding);
      postOffice.addBinding(divertBinding);

      LargeServerMessageImpl divertMessage = mock(LargeServerMessageImpl.class);
      when(divertMessage.getAddressSimpleString()).thenReturn(forwardAddress);
      when(divertMessage.hasScheduledDeliveryTime()).thenReturn(false);
      when(divertMessage.isDurable()).thenReturn(false);
      when(divertMessage.isLargeMessage()).thenReturn(true);
      {
         final AtomicBoolean dropped = new AtomicBoolean();
         when(divertMessage.isDropped()).thenAnswer(invocation -> dropped.get());
         when(divertMessage.setDropped(true)).thenAnswer(invocation -> {
            dropped.set(true);
            return divertMessage;
         });
      }

      Mockito.doAnswer(f -> {
         Assertions.fail("not supposed to delete the target message");
         return null;
      }).when(divertMessage).deleteFile();

      LargeServerMessageImpl message = mock(LargeServerMessageImpl.class);
      when(message.getAddressSimpleString()).thenReturn(address);
      when(message.hasScheduledDeliveryTime()).thenReturn(false);
      when(message.isDurable()).thenReturn(false);
      {
         final AtomicBoolean dropped = new AtomicBoolean();
         when(message.isDropped()).thenAnswer(invocation -> dropped.get());
         when(message.setDropped(true)).thenAnswer(invocation -> {
            dropped.set(true);
            return message;
         });
      }

      when(message.isLargeMessage()).thenReturn(true);

      AtomicBoolean messageDeleted = new AtomicBoolean();
      when(message.copy(anyLong())).thenAnswer(f -> {
         Assertions.assertFalse(messageDeleted.get()); // it would be an issue if we copied a deleted message
         return divertMessage;
      });
      Mockito.doAnswer(f -> {
         messageDeleted.set(true);
         return null;
      }).when(message).deleteFile();

      when(pagingStore.getAddress()).thenReturn(address);
      when(pagingManager.getPageStore(address)).thenReturn(pagingStore);

      when(storageManager.addToPage(eq(pagingStore), any(), any(), any()))
         .thenAnswer(invocation -> {
            return false; // Returns true indicating the message was added to page
         });

      assertFalse(messageDeleted.get());
      assertFalse(message.isDropped());

      RoutingContext routingContext = new RoutingContextImpl(null).setMirrorOption(RoutingContext.MirrorOption.disabled);
      postOffice.route(message, routingContext, false);

      assertTrue(messageDeleted.get()); // the original message should have been deleted
      assertTrue(message.isDropped()); // the original message should have been dropped
      assertFalse(divertMessage.isDropped()); // the target diverted message should not be dropped

      // We disabled mirror on this routing, so if mirrorController was called, it means that the divert is reverting the setting
      verify(mirrorController, never()).sendMessage(any(), any(), any());
      // refUp should be called on the target divert
      verify(targetDivertQueue, atLeast(1)).refUp(any());
   }
}
