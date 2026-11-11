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

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.impl.JournalCompactor;
import org.apache.activemq.artemis.core.journal.impl.JournalRecord;
import org.apache.activemq.artemis.core.journal.impl.JournalRecordProvider;
import org.apache.activemq.artemis.core.journal.impl.JournalTransaction;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashMap;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JournalTXConcurrentTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testConcurrentCompletion() throws Exception {

      int THREADS = 10;
      int COMPLETIONS = 1000;

      JournalTransaction journalTransaction = newJournalTransaction(THREADS, COMPLETIONS);

      CountDownLatch done = new CountDownLatch(1);
      journalTransaction.setDelegateCompletion(new IOCompletion() {
         @Override
         public void storeLineUp() {

         }

         @Override
         public void done() {
            done.countDown();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {

         }
      });

      CyclicBarrier startFlag = new CyclicBarrier(THREADS);
      ExecutorService executor = Executors.newFixedThreadPool(THREADS);
      runAfter(executor::shutdownNow);
      for (int t = 0; t < THREADS; t++) {
         executor.execute(() -> {
            try {
               startFlag.await(5, TimeUnit.SECONDS);
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
            }
            for (int doneI = 0; doneI < COMPLETIONS; doneI++) {
               journalTransaction.done();
            }
         });
      }

      assertTrue(done.await(10, TimeUnit.SECONDS));
   }

   private static JournalTransaction newJournalTransaction(int THREADS, int COMPLETIONS) {
      JournalRecordProvider recordProvider = new JournalRecordProvider() {
         @Override
         public JournalCompactor getCompactor() {
            return null;
         }

         @Override
         public ConcurrentLongHashMap<JournalRecord> getRecords() {
            return null;
         }
      };

      // countUp THREADS * COMPLETION
      JournalTransaction journalTransaction = new JournalTransaction(1, recordProvider);
      for (int i = 0; i < THREADS; i++) {
         for (int c = 0; c < COMPLETIONS; c++) {
            journalTransaction.countUp();
         }
      }
      return journalTransaction;
   }
}
