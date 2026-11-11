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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalFilesRepository;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.FakeSequentialFileFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.Test;

public class JournalFileRepositoryOrderTest extends ActiveMQTestBase {

   @Test
   public void testOrder() throws Throwable {
      ExecutorService executorService = Executors.newFixedThreadPool(3, new ActiveMQThreadFactory("test", false, JournalFileRepositoryOrderTest.class.getClassLoader()));
      final AtomicBoolean running = new AtomicBoolean(true);
      Thread t = null;
      try {
         FakeSequentialFileFactory fakeSequentialFileFactory = new FakeSequentialFileFactory();
         JournalImpl journal = new JournalImpl(new OrderedExecutorFactory(executorService), 10 * 1024, 2, -1, -1, 0, fakeSequentialFileFactory, "file", "file", 1, 0);

         final JournalFilesRepository repository = journal.getFilesRepository();
         final BlockingDeque<JournalFile> dataFiles = new LinkedBlockingDeque<>();


         // this is simulating how compating would return files into the journal
         t = new Thread(() -> {
            while (running.get()) {
               try {
                  Wait.waitFor(() -> !running.get() || dataFiles.size() > 10, 1000, 1);
                  while (running.get()) {
                     JournalFile file = dataFiles.poll();
                     if (file == null) {
                        break;
                     }
                     repository.addFreeFile(file, false);
                  }
               } catch (Throwable e) {
                  e.printStackTrace();
               }
            }
         });
         t.start();
         JournalFile file = null;
         LinkedList<Integer> values = new LinkedList<>();
         for (int i = 0; i < 5000; i++) {
            file = repository.openFile();
            assertNotNull(file);
            values.add(file.getRecordID());
            dataFiles.push(file);
         }

         int previous = Integer.MIN_VALUE;
         for (Integer v : values) {
            assertTrue(v.intValue() > previous);
            previous = v;
         }

      } finally {
         running.set(false);
         executorService.shutdownNow();
      }

   }
}
