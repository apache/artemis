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
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.utils.collections.EmptyList;
import org.apache.activemq.artemis.utils.collections.LinkedList;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-system backed {@link Page} implementation. Stores page data in a {@link SequentialFile}
 * managed by a {@link SequentialFileFactory}.
 */
public class FilePage extends Page {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final SequentialFile file;

   private final SequentialFileFactory fileFactory;

   private ByteBuffer readFileBuffer;

   public FilePage(final SimpleString storeName,
                   final StorageManager storageManager,
                   final SequentialFileFactory factory,
                   final SequentialFile file,
                   final long pageId) {
      super(storeName, storageManager, pageId);
      this.file = file;
      this.fileFactory = factory;
   }

   /**
    * Returns the underlying {@link SequentialFile}.
    * <p>
    * Only meaningful for file-backed pages. Callers that require this (e.g. replication, JDBC
    * network-timeout tests) must cast to {@code FilePage} explicitly.
    */
   public SequentialFile getFile() {
      return file;
   }

   // -------------------------------------------------------------------------
   // Page abstract method implementations
   // -------------------------------------------------------------------------

   @Override
   public boolean storageExists() throws Exception {
      return file.exists();
   }

   @Override
   public long storageSize() throws Exception {
      return file.size();
   }

   @Override
   public boolean isOpen() {
      return file != null && file.isOpen();
   }

   @Override
   public boolean open(boolean createFile) throws Exception {
      boolean isOpen = false;
      if (!file.isOpen() && (createFile || file.exists())) {
         file.open();
         isOpen = true;
      }
      if (file.isOpen()) {
         isOpen = true;
         setSize(file.size());
         file.position(0);
      }
      return isOpen;
   }

   /**
    * sendEvent means it's a close happening from a major event such as moveNext. While reading the
    * cache we don't need (and shouldn't) inform the backup.
    */
   @Override
   public synchronized void close(boolean sendReplicaClose, boolean waitSync) throws Exception {
      if (readFileBuffer != null) {
         fileFactory.releaseDirectBuffer(readFileBuffer);
         readFileBuffer = null;
      }
      if (sendReplicaClose && storageManager != null) {
         storageManager.pageClosed(storeName, getPageId());
      }
      file.close(waitSync, waitSync);
   }

   @Override
   public boolean delete(final LinkedList<PagedMessage> messages) throws Exception {
      if (storageManager != null) {
         storageManager.pageDeleted(storeName, getPageId());
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Deleting pageNr={} on store {}", getPageId(), storeName, new Exception("trace"));
      } else if (logger.isDebugEnabled()) {
         logger.debug("Deleting pageNr={} on store {}", getPageId(), storeName);
      }

      if (messages != null) {
         try (var iter = messages.iterator()) {
            while (iter.hasNext()) {
               PagedMessage msg = iter.next();
               if (msg.getMessage().isLargeMessage()) {
                  ((LargeServerMessage) msg.getMessage()).deleteFile();
                  msg.getMessage().usageDown();
               }
            }
         }
      }

      storageManager.afterCompleteOperations(new IOCallback() {
         @Override
         public void done() {
            try {
               if (isSuspiciousRecords()) {
                  ActiveMQServerLogger.LOGGER.pageInvalid(file.getFileName(), file.getFileName());
                  file.renameTo(file.getFileName() + ".invalidPage");
               } else {
                  file.delete();
               }
               usageExhaust();
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

   @Override
   public synchronized LinkedList<PagedMessage> read(StorageManager storage, boolean onlyLargeMessages) throws Exception {
      if (!file.isOpen()) {
         if (!file.exists()) {
            return EmptyList.getEmptyList();
         }
         throw ActiveMQMessageBundle.BUNDLE.invalidPageIO();
      }

      if (logger.isTraceEnabled()) {
         logger.trace("reading page {} on address = {} onlyLargeMessages = {}", getPageId(), storeName, onlyLargeMessages, new Exception("trace"));
      } else if (logger.isDebugEnabled()) {
         logger.debug("reading page {} on address = {} onlyLargeMessages = {}", getPageId(), storeName, onlyLargeMessages);
      }

      setSize(file.size());

      final LinkedList<PagedMessage> msgs = new LinkedListImpl<>();

      int count = PageReadWriter.readFromSequentialFile(storage, storeName, fileFactory, file, getPageId(),
                                                        msgs::addTail,
                                                        onlyLargeMessages ? PageReadWriter.ONLY_LARGE : PageReadWriter.NO_SKIP,
                                                        this::markFileAsSuspect,
                                                        this::setSize);
      setNumberOfMessages(count);

      return msgs;
   }

   /**
    * This write will not interact back with the storage manager. To avoid ping pongs with Journal
    * retaining events and any other stuff.
    */
   @Override
   public synchronized void writeDirect(PagedMessage message) throws Exception {
      if (!file.isOpen()) {
         throw ActiveMQMessageBundle.BUNDLE.cannotWriteToClosedFile(file);
      }
      addMessageToCache(message);
      long written = PageReadWriter.writeMessage(message, fileFactory, file);
      // addMessageToCache already incremented numberOfMessages; just update size
      setSize(getSize() + written);
   }

   @Override
   public void sync() throws Exception {
      file.sync();
   }

   @Override
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

   @Override
   public int readNumberOfMessages() throws Exception {
      boolean wasOpen = isOpen();
      if (!wasOpen) {
         if (!open(false)) {
            return 0;
         }
      }
      try {
         int count = PageReadWriter.readFromSequentialFile(this.storageManager,
                                                           this.storeName,
                                                           this.fileFactory,
                                                           this.file,
                                                           this.getPageId(),
                                                           null,
                                                           PageReadWriter.SKIP_ALL,
                                                           null,
                                                           null);
         if (logger.isDebugEnabled()) {
            logger.debug(">>> Reading numberOfMessages page {}, returning {}", this.getPageId(), count);
         }
         return count;
      } finally {
         if (!wasOpen) {
            close(false);
         }
      }
   }

   @Override
   public String toString() {
      return "FilePage::pageNr=" + getPageId() + ", file=" + file;
   }

   // -------------------------------------------------------------------------
   // Internal helpers
   // -------------------------------------------------------------------------

   private void markFileAsSuspect(final String fileName, final int position, final int msgNumber) {
      ActiveMQServerLogger.LOGGER.pageSuspectFile(fileName, position, msgNumber);
      setSuspiciousRecords(true);
   }

   // isSuspiciousRecords / setSuspiciousRecords are needed — expose in base
}
