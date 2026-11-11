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
package org.apache.activemq.artemis.tests.unit.core.paging.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.message.impl.CoreMessagePersister;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.paging.impl.PagedMessageImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessagePersister;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.FakeSequentialFileFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.collections.LinkedList;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PageTest extends ActiveMQTestBase {


   @BeforeEach
   public void registerProtocols() {
      MessagePersister.registerPersister(CoreMessagePersister.getInstance());
      MessagePersister.registerPersister(AMQPMessagePersister.getInstance());
   }

   @Test
   public void testLargeMessagePageWithNIO() throws Exception {
      recreateDirectory(getTestDir());
      final ExecutorService executor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      final ExecutorService ioexecutor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      OrderedExecutorFactory factory = new OrderedExecutorFactory(executor);
      OrderedExecutorFactory iofactory = new OrderedExecutorFactory(ioexecutor);
      final JournalStorageManager storageManager = new JournalStorageManager(
         createBasicConfig(), EmptyCriticalAnalyzer.getInstance(),
         factory, iofactory);
      storageManager.start();
      storageManager.loadInternalOnly();
      try {
         testAdd(storageManager, new NIOSequentialFileFactory(getTestDirfile(), 1), 1000, true);
      } finally {
         storageManager.stop();
         executor.shutdownNow();
         ioexecutor.shutdownNow();
      }
   }

   @Test
   public void testPageWithNIO() throws Exception {
      recreateDirectory(getTestDir());
      testAdd(new NIOSequentialFileFactory(getTestDirfile(), 1), 1000);
   }

   @Test
   public void testDamagedDataWithNIO() throws Exception {
      recreateDirectory(getTestDir());
      testDamagedPage(new NIOSequentialFileFactory(getTestDirfile(), 1), 1000);
   }

   @Test
   public void testPageFakeWithoutCallbacks() throws Exception {
      testAdd(new FakeSequentialFileFactory(1, false), 10);
   }

   @Test
   public void testPageSingleMessageWithNIO() throws Exception {
      testAdd(new NIOSequentialFileFactory(getTestDirfile(), 1), 1);
   }

   /**
    * Validate if everything we add is recovered
    */
   @Test
   public void testDamagedPage() throws Exception {
      testDamagedPage(new FakeSequentialFileFactory(1, false), 100);
   }

   /**
    * Validate if everything we add is recovered
    */
   protected void testAdd(final SequentialFileFactory factory, final int numberOfElements) throws Exception {
      testAdd(new NullStorageManager(), factory, numberOfElements, false);
   }

   protected void testAdd(final StorageManager storageManager,
                          final SequentialFileFactory factory,
                          final int numberOfElements,
                          final boolean largeMessages) throws Exception {

      SequentialFile file = factory.createSequentialFile("00010.page");

      Page page = new Page(SimpleString.of("something"), storageManager, factory, file, 10);

      assertEquals(10, page.getPageId());

      page.open(true);

      assertEquals(1, factory.listFiles("page").size());

      SimpleString simpleDestination = SimpleString.of("Test");

      final long startMessageID = 1;

      addPageElements(storageManager, simpleDestination, page, numberOfElements, largeMessages, startMessageID);

      page.sync();
      page.close(false, false);

      file = factory.createSequentialFile("00010.page");
      file.open();
      page = new Page(SimpleString.of("something"), storageManager, factory, file, 10);

      LinkedList<PagedMessage> msgs = page.read(storageManager, largeMessages);

      assertEquals(numberOfElements, msgs.size());

      assertEquals(numberOfElements, page.getNumberOfMessages());

      for (int i = 0; i < msgs.size(); i++) {
         final PagedMessage pagedMessage = msgs.get(i);
         assertEquals(simpleDestination, pagedMessage.getMessage().getAddressSimpleString());
         assertEquals(largeMessages, pagedMessage.getMessage().isLargeMessage());
         assertEquals(startMessageID + i, pagedMessage.getMessage().getMessageID());
         assertEquals(largeMessages ? 1 : 0, pagedMessage.getMessage().getUsage());
      }

      if (!largeMessages) {
         Page tmpPage = new Page(SimpleString.of("something"), storageManager, factory, file, 10);
         assertEquals(0, tmpPage.read(storageManager, true).size());
         assertEquals(numberOfElements, tmpPage.getNumberOfMessages());
      }

      assertTrue(page.delete(msgs));

      try (LinkedListIterator<PagedMessage> iter = msgs.iterator()) {
         while (iter.hasNext()) {
            PagedMessage pagedMessage = iter.next();
            assertEquals(0, pagedMessage.getMessage().getUsage());
         }
      }
      assertEquals(0, factory.listFiles(".page").size());

   }

   protected void testDamagedPage(final SequentialFileFactory factory, final int numberOfElements) throws Exception {

      SequentialFile file = factory.createSequentialFile("00010.page");

      Page page = new Page(SimpleString.of("something"), new NullStorageManager(), factory, file, 10);

      assertEquals(10, page.getPageId());

      page.open(true);

      assertEquals(1, factory.listFiles("page").size());

      SimpleString simpleDestination = SimpleString.of("Test");

      addPageElements(simpleDestination, page, numberOfElements, 1);

      page.sync();

      long positionA = file.position();

      // Add one record that will be damaged
      addPageElements(simpleDestination, page, 1, numberOfElements + 1);

      long positionB = file.position();

      // Add more 10 as they will need to be ignored
      addPageElements(simpleDestination, page, 10, numberOfElements + 2);

      // Damage data... position the file on the middle between points A and B
      file.position(positionA + (positionB - positionA) / 2);

      ByteBuffer buffer = ByteBuffer.allocate((int) (positionB - file.position()));

      for (int i = 0; i < buffer.capacity(); i++) {
         buffer.put((byte) 'Z');
      }

      buffer.rewind();

      file.writeDirect(buffer, true);

      page.close(false);

      file = factory.createSequentialFile("00010.page");
      file.open();
      Page page1 = new Page(SimpleString.of("something"), new NullStorageManager(), factory, file, 10);

      LinkedList<PagedMessage> msgs = page1.read(new NullStorageManager());

      assertEquals(numberOfElements, msgs.size());

      assertEquals(numberOfElements, page1.getNumberOfMessages());

      for (int i = 0; i < msgs.size(); i++) {
         assertEquals(simpleDestination, msgs.get(i).getMessage().getAddressSimpleString());
      }

      page.close(false);
      page1.delete(null);

      assertEquals(0, factory.listFiles("page").size());

      assertEquals(1, factory.listFiles("invalidPage").size());

   }

   protected void addPageElements(final SimpleString simpleDestination,
                                  final Page page,
                                  final int numberOfElements,
                                  final long startMessageID) throws Exception {
      addPageElements(new NullStorageManager(), simpleDestination, page, numberOfElements, false, startMessageID);
   }


   @Test
   public void testAddMessages() throws Exception {
      recreateDirectory(getTestDir());
      testAddMessages(new NullStorageManager(), new NIOSequentialFileFactory(getTestDirfile(), 1), 1000, false);
   }

   protected void testAddMessages(final StorageManager storageManager,
                          final SequentialFileFactory factory,
                          final int numberOfElements,
                          final boolean largeMessages) throws Exception {

      SequentialFile file = factory.createSequentialFile("00010.page");

      Page page = new Page(SimpleString.of("something"), storageManager, factory, file, 10);

      assertEquals(10, page.getPageId());

      page.open(true);

      assertEquals(1, factory.listFiles("page").size());

      SimpleString simpleDestination = SimpleString.of("Test");

      final long startMessageID = 1;

      addPageElements(storageManager, simpleDestination, page, numberOfElements, largeMessages, startMessageID);

      LinkedList<PagedMessage> msgsBefore = page.getMessages();

      page.sync();
      page.close(false, false);

      file = factory.createSequentialFile("00010.page");
      page = new Page(SimpleString.of("something"), storageManager, factory, file, 10);

      LinkedList<PagedMessage> msgs = page.getMessages();

      assertEquals(numberOfElements, msgs.size());

      assertEquals(numberOfElements, page.getNumberOfMessages());

      for (int i = 0; i < msgs.size(); i++) {
         final PagedMessage pagedMessage = msgs.get(i);
         assertEquals(simpleDestination, pagedMessage.getMessage().getAddressSimpleString());
         assertEquals(largeMessages, pagedMessage.getMessage().isLargeMessage());
         assertEquals(startMessageID + i, pagedMessage.getMessage().getMessageID());
         assertEquals(largeMessages ? 1 : 0, pagedMessage.getMessage().getUsage());
      }


      for (int i = 0; i < msgs.size(); i++) {
         final PagedMessage pagedMessage = msgsBefore.get(i);
         assertEquals(simpleDestination, pagedMessage.getMessage().getAddressSimpleString());
         assertEquals(largeMessages, pagedMessage.getMessage().isLargeMessage());
         assertEquals(startMessageID + i, pagedMessage.getMessage().getMessageID());
         assertEquals(largeMessages ? 1 : 0, pagedMessage.getMessage().getUsage());
      }

      assertTrue(page.delete(msgs));

      try (LinkedListIterator<PagedMessage> iter = msgs.iterator()) {
         while (iter.hasNext()) {
            PagedMessage pagedMessage = iter.next();
            assertEquals(0, pagedMessage.getMessage().getUsage());
         }
      }

      assertEquals(0, factory.listFiles(".page").size());

   }




   protected void addPageElements(final StorageManager storageManager,
                                  final SimpleString simpleDestination,
                                  final Page page,
                                  final int numberOfElements,
                                  final boolean largeMessages,
                                  final long startMessageID) throws Exception {

      int initialNumberOfMessages = page.getNumberOfMessages();

      final int msgSize = 10;

      final byte[] content = new byte[msgSize];

      Arrays.fill(content, (byte) 'b');

      for (int i = 0; i < numberOfElements; i++) {
         writeMessage(storageManager, largeMessages, startMessageID + i, simpleDestination, content, page);
         assertEquals(initialNumberOfMessages + i + 1, page.getNumberOfMessages());
      }
   }

   protected void writeMessage(StorageManager storageManager,
                               boolean isLargeMessage,
                               long msgID,
                               SimpleString address,
                               byte[] content,
                               Page page) throws Exception {
      if (isLargeMessage) {
         LargeServerMessageImpl msg = new LargeServerMessageImpl(storageManager);
         msg.setMessageID(msgID);
         msg.addBytes(content);
         msg.setAddress(address);
         page.write(new PagedMessageImpl(msg, new long[0]), true, false);
         msg.releaseResources(false, false);
      } else {
         ICoreMessage msg = new CoreMessage().initBuffer(100);
         msg.setMessageID(msgID);
         msg.getBodyBuffer().writeBytes(content);
         msg.setAddress(address);
         page.write(new PagedMessageImpl(msg, new long[0]), true, false);
      }
   }
}
