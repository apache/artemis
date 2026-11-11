/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.paging.cursor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ConcurrentAckTest extends ServerTestBase {

   @Test
   public void testConcurrentAddAckPaging() throws Throwable {

      ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
      runAfter(scheduledExecutorService::shutdownNow);
      ExecutorService service = Executors.newFixedThreadPool(10);
      runAfter(service::shutdownNow);

      for (int repeat = 0; repeat < 100; repeat++) {
         // I needed brute force to make this test to fail,
         // hence I am executing this method 100 times.
         testConcurrentAddAckPaging(scheduledExecutorService, service);
      }
   }

   private void testConcurrentAddAckPaging(ScheduledExecutorService scheduledExecutorService, ExecutorService service) throws Throwable {
      AtomicInteger errors = new AtomicInteger(0);
      PagingStoreImpl store = new PagingStoreImpl(SimpleString.of("TEST"), scheduledExecutorService, 100L, Mockito.mock(PagingManager.class), new NullStorageManager(), Mockito.mock(SequentialFileFactory.class), Mockito.mock(PagingStoreFactory.class), SimpleString.of("TEST"), new AddressSettings(), ArtemisExecutor.delegate(service), false);

      PageCursorProviderImpl pageCursorProvider = new PageCursorProviderImpl(store, new NullStorageManager());
      PageSubscriptionImpl subscription = (PageSubscriptionImpl) pageCursorProvider.createSubscription(1, null, true);
      PageSubscriptionImpl.PageCursorInfo cursorInfo = subscription.getPageInfo(new PagePositionImpl(1, 1));
      CountDownLatch done = new CountDownLatch(5);

      CyclicBarrier barrier = new CyclicBarrier(5);

      for (int r = 0; r < 4; r++) {
         service.execute(() -> {
            try {
               barrier.await(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            for (int i = 0; i < 5000; i++) {
               try {
                  cursorInfo.internalAddACK(new PagePositionImpl(i, i));
               } catch (Throwable e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               }
            }
            done.countDown();
         });
      }

      service.execute(() -> {
         try {
            try {
               barrier.await(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            for (int i = 0; i < 5000; i++) {
               cursorInfo.isAck(i);
               cursorInfo.isRemoved(i);
            }
         } catch (Exception e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }

         done.countDown();
      });

      assertTrue(done.await(10, TimeUnit.SECONDS));

      assertEquals(0, errors.get());
   }

}