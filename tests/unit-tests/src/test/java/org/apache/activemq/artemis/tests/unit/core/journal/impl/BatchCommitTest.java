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

package org.apache.activemq.artemis.tests.unit.core.journal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.io.mapped.MappedSequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.SimpleIDGenerator;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchCommitTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int FILE_SIZE = 10 * 1024 * 1024;
   private static final int MIN_FILES = 10;
   private static final int POOL_SIZE = 10;
   private static final String FILE_PREFIX = "journal-test";
   private static final String FILE_EXTENSION = "amq";
   private static final int BUFFER_SIZE = 100 * 1024;
   private static final int BUFFER_TIMEOUT = 10 * 1024;

   private static final int MAX_AIO = 255;

   private static final int RECORDS = 10000;

   JournalImpl journal;

   SequentialFileFactory journalFF;

   SimpleIDGenerator idGenerator = new SimpleIDGenerator(1);

   public Journal testRunNIO(String testFolder, boolean sync) throws Throwable {
      return testRun(testFolder, JournalType.NIO, sync);
   }

   public Journal testRunMapped(String testFolder, boolean sync) throws Throwable {
      return testRun(testFolder, JournalType.MAPPED, sync);
   }


   public Journal testRunAIO(String testFolder, boolean sync) throws Throwable {
      return testRun(testFolder, JournalType.ASYNCIO, sync);
   }


   public Journal testRun(String testFolder, JournalType journalType, boolean sync) throws Throwable {

      OrderedExecutorFactory orderedExecutorFactory = getExecutorFactory();
      setupJournal(journalType, testFolder, orderedExecutorFactory);
      journal.start();
      runAfter(journal::stop);
      journal.loadInternalOnly();

      CountDownLatch latch = new CountDownLatch(RECORDS);

      Set<Long> existingRecords = ConcurrentHashMap.newKeySet();

      AtomicInteger errors = new AtomicInteger(0);


      for (int i = 0; i < RECORDS; i++) {
         long tx = idGenerator.generateID();
         long id = idGenerator.generateID();
         long upid = idGenerator.generateID();
         existingRecords.add(tx);
         IOCompletion completion = new IOCompletion() {
            @Override
            public void storeLineUp() {
            }

            @Override
            public void done() {
               if (!existingRecords.remove(tx)) {
                  errors.incrementAndGet();
                  logger.warn("Id {} was removed before", tx);
               }
               latch.countDown();
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
            }
         };

         journal.appendAddRecordTransactional(tx, id, (byte) 1, ("add " + id).getBytes());
         journal.appendUpdateRecordTransactional(tx, upid, (byte) 1, ("up " + upid).getBytes());
         journal.appendCommitRecord(tx, sync, completion, true);
      }

      if (!latch.await(10, TimeUnit.SECONDS)) {
         logger.warn("latch didn't finish, count={}", latch.getCount());
         errors.incrementAndGet();
      }

      existingRecords.forEach(l -> logger.warn("id {} still in the list", l));

      assertEquals(0, errors.get());
      assertEquals(0, existingRecords.size());

      return journal;

   }

   private OrderedExecutorFactory getExecutorFactory() {
      ExecutorService service = Executors.newFixedThreadPool(10, new ThreadFactory() {
         int counter = 0;
         @Override
         public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("AsyncCommitTest" + (counter++));
            return t;
         }
      });
      OrderedExecutorFactory orderedExecutorFactory = new OrderedExecutorFactory(service);
      runAfter(service::shutdownNow);
      return orderedExecutorFactory;
   }

   @Test
   public void testNIO() throws Exception {
      internalTest(JournalType.NIO, "testRunNIO", true);
   }

   @Test
   public void testNIONoSync() throws Exception {
      internalTest(JournalType.NIO, "testRunNIO", false);
   }

   @Disabled // TODO: We should fix Mapped eventually for this case
   public void testMapped() throws Exception {
      internalTest(JournalType.MAPPED, "testRunMapped", true);
   }

   @Test
   public void testMappedNoSync() throws Exception {
      internalTest(JournalType.MAPPED, "testRunMapped", false);
   }

   @Test
   public void testAIO() throws Exception {
      assumeTrue(LibaioContext.isLoaded());
      internalTest(JournalType.ASYNCIO, "testRunAIO", true);
   }

   @Test
   public void testAIONoSync() throws Exception {
      assumeTrue(LibaioContext.isLoaded());
      internalTest(JournalType.ASYNCIO, "testRunAIO", false);
   }

   private void proceedCall(String testName, String testFolder, boolean sync) throws Exception {
      Method method = getClass().getMethod(testName, String.class, Boolean.TYPE);
      Journal journal = (Journal)method.invoke(this, testFolder, sync);
      journal.stop();
   }

   private void internalTest(JournalType journalType, String testRunName, boolean sync) throws Exception {
      proceedCall(testRunName, getTestDir(), sync);

      OrderedExecutorFactory orderedExecutorFactory = getExecutorFactory();
      setupJournal(journalType, getTestDir(), orderedExecutorFactory);

      List<RecordInfo> commited = new ArrayList<>();
      List<PreparedTransactionInfo> prepared = new ArrayList<>();
      AtomicInteger failedTX = new AtomicInteger(0);

      journal.start();
      journal.load(commited, prepared, (id, records, toDelete) -> failedTX.incrementAndGet(), false);
      runAfter(journal::stop);

      commited.forEach(r -> {
         String dataAsString = new String(r.data);
         logger.debug("data={}, isUpdate={}, id={}", dataAsString, r.isUpdate, r.id);
         if (r.isUpdate) {
            assertEquals("up " + r.id, dataAsString);
         } else {
            assertEquals("add " + r.id, dataAsString);
         }
      });
      assertEquals(RECORDS * 2, commited.size());
      assertEquals(0, failedTX.get());



   }



   public void setupJournal(JournalType journalType, String location, ExecutorFactory executorFactory) {

      File locationFile = new File(location);

      switch (journalType) {

         case NIO:
            journalFF = new NIOSequentialFileFactory(locationFile, true, BUFFER_SIZE, BUFFER_TIMEOUT, 1, true, null, null);
            break;
         case ASYNCIO:
            journalFF = new AIOSequentialFileFactory(locationFile, BUFFER_SIZE, BUFFER_TIMEOUT, MAX_AIO, true, null, null);
            break;
         case MAPPED:
            journalFF = new MappedSequentialFileFactory(locationFile, FILE_SIZE, true, BUFFER_SIZE, BUFFER_TIMEOUT, null);
            break;
         default:
            throw new IllegalStateException("invalid journal type " + journalType);
      }

      journal = new JournalImpl(executorFactory, FILE_SIZE, MIN_FILES, POOL_SIZE, 0, 0, 30_000, journalFF, FILE_PREFIX, FILE_EXTENSION, MAX_AIO, 1, null, 10);
   }

}
