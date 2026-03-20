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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.utils.ReferenceCounterUtil;
import org.apache.activemq.artemis.utils.collections.EmptyList;
import org.apache.activemq.artemis.utils.collections.LinkedList;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;

public abstract class Page {

   private static final AtomicInteger seqFactory = new AtomicInteger(0);

   private final int seqInt = seqFactory.incrementAndGet();

   private final ReferenceCounterUtil referenceCounter = new ReferenceCounterUtil();

   private final long pageId;

   private boolean suspiciousRecords = false;

   private volatile int numberOfMessages;

   private volatile LinkedList<PagedMessage> messages;

   private volatile long size;

   protected final StorageManager storageManager;

   protected final SimpleString storeName;

   protected Page(final SimpleString storeName,
                  final StorageManager storageManager,
                  final long pageId) {
      this.pageId = pageId;
      this.storageManager = storageManager;
      this.storeName = storeName;
   }

   // -------------------------------------------------------------------------
   // Reference counting
   // -------------------------------------------------------------------------

   public void usageExhaust() {
      referenceCounter.exhaust();
   }

   public int usageUp() {
      return referenceCounter.increment();
   }

   public int usageDown() {
      return referenceCounter.decrement();
   }

   public void releaseTask(Consumer<Page> releaseTask) {
      referenceCounter.setTask(() -> releaseTask.accept(this));
   }

   // -------------------------------------------------------------------------
   // Accessors
   // -------------------------------------------------------------------------

   public long getPageId() {
      return pageId;
   }

   public int getNumberOfMessages() {
      return numberOfMessages;
   }

   public long getSize() {
      return size;
   }

   // -------------------------------------------------------------------------
   // Message cache (shared, storage-independent)
   // -------------------------------------------------------------------------

   public LinkedListIterator<PagedMessage> iterator() throws Exception {
      return getMessages().iterator();
   }

   public synchronized LinkedList<PagedMessage> getMessages() throws Exception {
      if (messages == null) {
         boolean wasOpen = isOpen();
         if (!wasOpen) {
            if (!storageExists()) {
               return EmptyList.getEmptyList();
            }
            open(false);
         }
         messages = read(storageManager);
         if (!wasOpen) {
            close(false, false);
         }
      }
      return messages;
   }

   public synchronized LinkedList<PagedMessage> read() throws Exception {
      return read(storageManager);
   }

   public synchronized LinkedList<PagedMessage> read(StorageManager storage) throws Exception {
      return read(storage, false);
   }

   public String debugMessages() throws Exception {
      StringBuilder sb = new StringBuilder();
      LinkedListIterator<PagedMessage> iter = getMessages().iterator();
      while (iter.hasNext()) {
         PagedMessage message = iter.next();
         sb.append(message.toString()).append("\n");
      }
      iter.close();
      return sb.toString();
   }

   /**
    * Write a message to the page and notify the storage manager journal.
    */
   public synchronized void write(final PagedMessage message, boolean lineUp, boolean originallyReplicated) throws Exception {
      writeDirect(message);
      storageManager.pageWrite(storeName, message, pageId, lineUp, originallyReplicated);
   }

   // -------------------------------------------------------------------------
   // Protected helpers for subclasses
   // -------------------------------------------------------------------------

   /**
    * Append {@code message} to the in-memory list and increment the message counter.
    * Subclasses call this inside their {@link #writeDirect} implementation after persisting.
    */
   protected synchronized void addMessageToCache(PagedMessage message) {
      if (messages == null) {
         messages = new LinkedListImpl<>();
      }
      message.setMessageNumber(messages.size());
      message.setPageNumber(this.pageId);
      messages.addTail(message);
      numberOfMessages++;
   }

   /**
    * Overwrite the numberOfMessages counter. Used by subclasses after a {@link #read} call so the
    * value reflects what was actually read from storage.
    */
   protected void setNumberOfMessages(int count) {
      this.numberOfMessages = count;
   }

   protected void setSize(long size) {
      this.size = size;
   }

   protected void setSuspiciousRecords(boolean value) {
      this.suspiciousRecords = value;
   }

   protected boolean isSuspiciousRecords() {
      return suspiciousRecords;
   }

   // -------------------------------------------------------------------------
   // Object overrides
   // -------------------------------------------------------------------------

   @Override
   public String toString() {
      return "Page::seqCreation=" + seqInt + ", pageNr=" + pageId + ", storeName=" + storeName;
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

   // -------------------------------------------------------------------------
   // Abstract storage-specific operations
   // -------------------------------------------------------------------------

   /**
    * Returns {@code true} if the underlying storage for this page already exists (e.g. the file is
    * present on disk).
    */
   public abstract boolean storageExists() throws Exception;

   /**
    * Returns the current byte size of the underlying storage.
    */
   public abstract long storageSize() throws Exception;

   /**
    * Opens the page for reading/writing.
    *
    * @param createFile if {@code true} the storage is created when absent; if {@code false} the
    *                   method only opens pre-existing storage
    * @return {@code true} if the page is open after this call
    */
   public abstract boolean open(boolean createFile) throws Exception;

   /**
    * Closes the page.
    *
    * @param sendReplicaClose notify the storage manager (triggers a replica-close event)
    * @param waitSync         wait for pending I/O to flush before closing
    */
   public abstract void close(boolean sendReplicaClose, boolean waitSync) throws Exception;

   public void close(boolean sendReplicaClose) throws Exception {
      close(sendReplicaClose, true);
   }

   /**
    * Delete the page and release all associated resources (large-message files, etc.).
    */
   public abstract boolean delete(LinkedList<PagedMessage> messages) throws Exception;

   /**
    * Read messages from the page's storage, optionally skipping non-large messages.
    */
   public abstract LinkedList<PagedMessage> read(StorageManager storage,
                                                 boolean onlyLargeMessages) throws Exception;

   /**
    * Persist {@code message} directly to the page without routing the write through the storage
    * manager journal (avoids ping-pong with journal retaining events).
    */
   public abstract void writeDirect(PagedMessage message) throws Exception;

   /** Flush the page to durable storage. */
   public abstract void sync() throws Exception;

   /**
    * Flush if currently open; silently swallow {@link java.nio.channels.ClosedChannelException}.
    */
   public abstract void trySync() throws IOException;

   /**
    * @return true if the page is currently open for I/O
    */
   public abstract boolean isOpen();

   /**
    * Read and return the number of messages stored in this page without populating the in-memory
    * cache.
    */
   public abstract int readNumberOfMessages() throws Exception;
}
