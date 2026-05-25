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
package org.apache.activemq.artemis.core.paging.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.utils.ReferenceCounterUtil;
import org.apache.activemq.artemis.utils.collections.EmptyList;
import org.apache.activemq.artemis.utils.collections.LinkedList;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public final class Page {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final AtomicInteger factory = new AtomicInteger(0);

   private final int seqInt = factory.incrementAndGet();

   private final ReferenceCounterUtil referenceCounter = new ReferenceCounterUtil();

   public void usageExhaust() {
      referenceCounter.exhaust();
   }

   public int usageUp() {
      return referenceCounter.increment();
   }

   public int usageDown() {
      return referenceCounter.decrement();
   }

   /**
    * to be called when the page is supposed to be released
    */
   public void releaseTask(Consumer<Page> releaseTask) {
      referenceCounter.setTask(() -> releaseTask.accept(this));
   }

   private final long pageId;

   private boolean suspiciousRecords = false;

   private volatile int numberOfMessages;

   private final SequentialFile file;

   private final SequentialFileFactory fileFactory;

   private volatile LinkedList<PagedMessage> messages;

   private volatile long size;

   private final StorageManager storageManager;

   private final SimpleString storeName;

   private ByteBuffer readFileBuffer;

   public Page(final SimpleString storeName,
               final StorageManager storageManager,
               final SequentialFileFactory factory,
               final SequentialFile file,
               final long pageId) throws Exception {
      this.pageId = pageId;
      this.file = file;
      fileFactory = factory;
      this.storageManager = storageManager;
      this.storeName = storeName;
   }

   public long getPageId() {
      return pageId;
   }

   public LinkedListIterator<PagedMessage> iterator() throws Exception {
      LinkedList<PagedMessage> messages = getMessages();
      return messages.iterator();
   }

   public synchronized LinkedList<PagedMessage> getMessages() throws Exception {
      if (messages == null) {
         boolean wasOpen = file.isOpen();
         if (!wasOpen) {
            if (!file.exists()) {
               return EmptyList.getEmptyList();
            }
            file.open();
         }
         messages = read(storageManager);
         if (!wasOpen) {
            file.close();
         }
      }

      return messages;
   }

   private void addMessage(PagedMessage message) {
      if (messages == null) {
         messages = new LinkedListImpl<>();
      }
      message.setMessageNumber(messages.size());
      message.setPageNumber(this.pageId);
      messages.addTail(message);
   }

   public synchronized LinkedList<PagedMessage> read() throws Exception {
      return read(storageManager);
   }

   public synchronized LinkedList<PagedMessage> read(StorageManager storage) throws Exception {
      return read(storage, false);
   }

   public synchronized LinkedList<PagedMessage> read(StorageManager storage, boolean onlyLargeMessages) throws Exception {

      if (!file.isOpen()) {
         if (!file.exists()) {
            return EmptyList.getEmptyList();
         }
         throw ActiveMQMessageBundle.BUNDLE.invalidPageIO();
      }

      if (logger.isTraceEnabled()) {
         logger.trace("reading page {} on address = {} onlyLargeMessages = {}", pageId, storeName, onlyLargeMessages, new Exception("trace"));
      } else if (logger.isDebugEnabled()) {
         logger.debug("reading page {} on address = {} onlyLargeMessages = {}", pageId, storeName, onlyLargeMessages);
      }

      size = file.size();

      final LinkedList<PagedMessage> messages = new LinkedListImpl<>();

      numberOfMessages = PageReadWriter.readFromSequentialFile(storage, storeName, fileFactory, file, this.pageId, messages::addTail, onlyLargeMessages ? PageReadWriter.ONLY_LARGE : PageReadWriter.NO_SKIP, this::markFileAsSuspect, this::setSize);

      return messages;
   }

   public String debugMessages() throws Exception {
      StringBuilder sb = new StringBuilder();
      LinkedListIterator<PagedMessage> iter = getMessages().iterator();
      while (iter.hasNext()) {
         PagedMessage message = iter.next();
         sb.append(message.toString() + "\n");
      }
      iter.close();
      return sb.toString();
   }

   public synchronized void write(final PagedMessage message, boolean lineUp, boolean originallyReplicated) throws Exception {
      writeDirect(message);
      storageManager.pageWrite(storeName, message, pageId, lineUp, originallyReplicated);
   }

   /**
    * This write will not interact back with the storage manager. To avoid ping pongs with Journal retaining events and
    * any other stuff.
    */
   public synchronized void writeDirect(PagedMessage message) throws Exception {
      if (!file.isOpen()) {
         throw ActiveMQMessageBundle.BUNDLE.cannotWriteToClosedFile(file);
      }
      addMessage(message);
      this.size += PageReadWriter.writeMessage(message, fileFactory, file);
      numberOfMessages++;
   }

   public void sync() throws Exception {
      file.sync();
   }

   public void trySync() throws IOException {
      try {
         if (file.isOpen()) {
            file.sync();
         }
      } catch (IOException e) {
         if (e instanceof ClosedChannelException) {
            logger.debug("file.sync on file {} thrown a ClosedChannelException that will just be ignored", file.getFileName());
         } else {
            throw e;
         }
      }
   }

   public boolean isOpen() {
      return file != null && file.isOpen();
   }


   public boolean open(boolean createFile) throws Exception {
      boolean isOpen = false;
      if (!file.isOpen() && (createFile || file.exists())) {
         file.open();
         isOpen = true;
      }
      if (file.isOpen()) {
         isOpen = true;
         size = file.size();
         file.position(0);
      }
      return isOpen;
   }

   public void close(boolean sendReplicaClose) throws Exception {
      close(sendReplicaClose, true);
   }

   /**
    * sendEvent means it's a close happening from a major event such moveNext. While reading the cache we don't need
    * (and shouldn't inform the backup
    */
   public synchronized void close(boolean sendReplicaClose, boolean waitSync) throws Exception {
      if (readFileBuffer != null) {
         fileFactory.releaseDirectBuffer(readFileBuffer);
         readFileBuffer = null;
      }

      if (sendReplicaClose && storageManager != null) {
         storageManager.pageClosed(storeName, pageId);
      }
      file.close(waitSync, waitSync);
   }

   public boolean delete(final LinkedList<PagedMessage> messages) throws Exception {
      if (storageManager != null) {
         storageManager.pageDeleted(storeName, pageId);
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Deleting pageNr={} on store {}", pageId, storeName, new Exception("trace"));
      } else if (logger.isDebugEnabled()) {
         logger.debug("Deleting pageNr={} on store {}", pageId, storeName);
      }

      if (messages != null) {
         try (LinkedListIterator<PagedMessage> iter = messages.iterator()) {
            while (iter.hasNext()) {
               PagedMessage msg = iter.next();
               if ((msg.getMessage()).isLargeMessage()) {
                  ((LargeServerMessage)(msg.getMessage())).deleteFile();
                  msg.getMessage().usageDown();
               }
            }
         }
      }

      storageManager.afterCompleteOperations(new IOCallback() {
         @Override
         public void done() {
            try {
               if (suspiciousRecords) {
                  ActiveMQServerLogger.LOGGER.pageInvalid(file.getFileName(), file.getFileName());
                  file.renameTo(file.getFileName() + ".invalidPage");
               } else {
                  file.delete();
               }
               referenceCounter.exhaust();
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.pageDeleteError(e);
            }
         }

         @Override
         public void onError(int errorCode, String errorMessage) {

         }
      });

      return true;
   }

   public int readNumberOfMessages() throws Exception {
      boolean wasOpen = isOpen();

      if (!wasOpen) {
         if (!open(false)) {
            return 0;
         }
      }

      try {
         int numberOfMessages = PageReadWriter.readFromSequentialFile(this.storageManager,
                                                                      this.storeName,
                                                                      this.fileFactory,
                                                                      this.file,
                                                                      this.pageId,
                                                                      null,
                                                                      PageReadWriter.SKIP_ALL,
                                                                      null,
                                                                      null);
         if (logger.isDebugEnabled()) {
            logger.debug(">>> Reading numberOfMessages page {}, returning {}", this.pageId, numberOfMessages);
         }
         return numberOfMessages;
      } finally {
         if (!wasOpen) {
            close(false);
         }
      }
   }

   public int getNumberOfMessages() {
      return numberOfMessages;
   }

   public long getSize() {
      return size;
   }

   private void setSize(long size) {
      this.size = size;
   }

   @Override
   public String toString() {
      return "Page::seqCreation=" + seqInt + ", pageNr=" + this.pageId + ", file=" + this.file;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof Page other)) {
         return false;
      }

      return pageId == other.pageId;
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(pageId);
   }

   private void markFileAsSuspect(final String fileName, final int position, final int msgNumber) {
      ActiveMQServerLogger.LOGGER.pageSuspectFile(fileName, position, msgNumber);
      suspiciousRecords = true;
   }

   public SequentialFile getFile() {
      return file;
   }

}
