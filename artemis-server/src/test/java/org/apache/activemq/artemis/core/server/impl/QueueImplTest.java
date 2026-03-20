/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageIterator;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReferenceImpl;
import org.apache.activemq.artemis.core.paging.impl.PagedMessageImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class QueueImplTest {

   @Test
   public void deleteAllPagedReferencesTest() throws Exception {
      SimpleString address = SimpleString.of("test");
      AtomicInteger pageIteratorIndex = new AtomicInteger(0);
      PageIterator pageIterator = Mockito.mock(PageIterator.class);
      PageSubscription pageSubscription = Mockito.mock(PageSubscription.class);
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      StorageManager storageManager = Mockito.mock(StorageManager.class);
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
      ExecutorFactory executorFactory = Mockito.mock(ExecutorFactory.class);

      final int flushLimit = 100;
      final int pagedReferences = 5 * flushLimit;
      Semaphore availableTransactions = new Semaphore(3);

      //Mock pageIterator.
      Mockito.doAnswer(invocationOnMock -> pageIteratorIndex.get() < pagedReferences).
         when(pageIterator).hasNext();
      Mockito.doAnswer(invocationOnMock -> {
         pageIteratorIndex.incrementAndGet();
         return new PagedReferenceImpl(new PagedMessageImpl(
            Mockito.mock(Message.class), new long[]{0}), pageSubscription);
      }).when(pageIterator).next();
      Mockito.doReturn(pageIterator).when(pageSubscription).iterator();

      //Mock storageManager.
      Mockito.doAnswer(invocationOnMock -> {
         assertTrue(availableTransactions.tryAcquire(3000, TimeUnit.MILLISECONDS),
                           "Too transactions locked on afterCommit.");
         executorService.execute(() -> {
            ((IOCallback) invocationOnMock.getArgument(0)).done();
            availableTransactions.release();
         });
         return null;
      }).when(storageManager).afterCompleteOperations(Mockito.any(IOCallback.class));

      // Mock server
      Mockito.doReturn(executorFactory).when(server).getExecutorFactory();

      // Mock pagingStore to provide a StorageMessageReader for paged message iteration
      PagingStore pagingStore = Mockito.mock(PagingStore.class);
      Mockito.doAnswer(invocationOnMock -> new PageStorageMessageReader((QueueImpl) invocationOnMock.getArgument(0)))
         .when(pagingStore).createStorageMessageReader(Mockito.any(QueueImpl.class));

      QueueImpl queue = new QueueImpl(QueueConfiguration.of(address).setId(0L).setDurable(false), null, pagingStore,
                                      pageSubscription, Mockito.mock(ScheduledExecutorService.class), Mockito.mock(PostOffice.class),
                                      storageManager, null, Mockito.mock(ArtemisExecutor.class),
                                      server, Mockito.mock(QueueFactory.class));

      Mockito.doReturn(queue).when(pageSubscription).getQueue();

      assertEquals(pagedReferences, queue.deleteAllReferences(flushLimit));
   }
}