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
package org.apache.activemq.artemis.core.persistence.impl.journal;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.impl.JournalLoader;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class JournalStorageManagerTest extends ServerTestBase {

   @Parameter(index = 0)
   public JournalType journalType;

   @Parameters(name = "journal type={0}")
   public static Collection<Object[]> getParams() {
      return Stream.of(JournalType.values())
         .map(journalType -> new Object[]{journalType})
         .collect(toList());
   }

   private static ExecutorService executor;
   private static ExecutorService ioExecutor;
   private static ExecutorService testExecutor;

   @BeforeAll
   public static void initExecutors() {
      executor = Executors.newSingleThreadExecutor();
      //to allow concurrent compaction and I/O operations
      ioExecutor = Executors.newFixedThreadPool(2);
      testExecutor = Executors.newSingleThreadExecutor();
   }

   @AfterAll
   public static void destroyExecutors() {
      ioExecutor.shutdownNow();
      executor.shutdownNow();
      testExecutor.shutdownNow();
   }

   /**
    * Test of fixJournalFileSize method, of class JournalStorageManager.
    */
   @TestTemplate
   public void testFixJournalFileSize() throws Exception {
      if (journalType == JournalType.ASYNCIO) {
         assumeTrue(AIOSequentialFileFactory.isSupported(), "AIO is not supported on this platform");
      }
      final Configuration configuration = createDefaultInVMConfig().setJournalType(journalType);
      final ExecutorFactory executorFactory = new OrderedExecutorFactory(executor);
      final ExecutorFactory ioExecutorFactory = new OrderedExecutorFactory(ioExecutor);
      final JournalStorageManager manager = new JournalStorageManager(configuration, null, executorFactory, null, ioExecutorFactory);
      assertEquals(4096, manager.fixJournalFileSize(1024, 4096));
      assertEquals(4096, manager.fixJournalFileSize(4098, 4096));
      assertEquals(8192, manager.fixJournalFileSize(8192, 4096));
   }

   @TestTemplate
   public void testAddBytesToLargeMessageNotLeakingByteBuffer() throws Exception {
      if (journalType == JournalType.ASYNCIO) {
         assumeTrue(AIOSequentialFileFactory.isSupported(), "AIO is not supported on this platform");
      }
      final Configuration configuration = createDefaultInVMConfig().setJournalType(journalType);
      final ExecutorFactory executorFactory = new OrderedExecutorFactory(executor);
      final ExecutorFactory ioExecutorFactory = new OrderedExecutorFactory(ioExecutor);
      final JournalStorageManager manager = new JournalStorageManager(configuration, null, executorFactory, null, ioExecutorFactory);
      manager.largeMessagesFactory = spy(manager.largeMessagesFactory);
      manager.start();
      manager.loadBindingJournal(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
      final PostOffice postOffice = mock(PostOffice.class);
      final JournalLoader journalLoader = mock(JournalLoader.class);
      manager.loadMessageJournal(postOffice, null, null, null, null, null, null, null, journalLoader);
      final long id = manager.generateID() + 1;
      final SequentialFile file = manager.createFileForLargeMessage(id, false);
      try {
         file.open();
         doAnswer(invocation -> {
            fail("No buffer should leak into the factory pool while writing into a large message");
            return invocation.callRealMethod();
         }).when(manager.largeMessagesFactory).releaseBuffer(any(ByteBuffer.class));
         final int size = 100;
         final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size);
         final ActiveMQBuffer directBuffer = ActiveMQBuffers.wrappedBuffer(byteBuffer);
         directBuffer.writerIndex(size);
         long fileSize = file.size();
         manager.addBytesToLargeMessage(file, 1, directBuffer);
         assertEquals(fileSize + size, file.size());
         fileSize = file.size();
         final ActiveMQBuffer heapBuffer = ActiveMQBuffers.wrappedBuffer(new byte[size]);
         heapBuffer.writerIndex(size);
         manager.addBytesToLargeMessage(file, 1, heapBuffer);
         assertEquals(fileSize + size, file.size());
      } finally {
         manager.stop();
         file.close();
         file.delete();
      }
   }

}
