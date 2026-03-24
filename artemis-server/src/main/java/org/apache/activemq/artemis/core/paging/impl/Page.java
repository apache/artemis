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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.cursor.LivePageCache;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscriptionCounter;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.Env;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.jboss.logging.Logger;

public final class Page implements Comparable<Page> {

   private static final Logger logger = Logger.getLogger(Page.class);

   public static final int SIZE_RECORD = DataConstants.SIZE_BYTE + DataConstants.SIZE_INT + DataConstants.SIZE_BYTE;

   private static final byte START_BYTE = (byte) '{';

   private static final byte END_BYTE = (byte) '}';


   private final int pageId;

   private boolean suspiciousRecords = false;

   private final AtomicInteger numberOfMessages = new AtomicInteger(0);

   private final SequentialFile file;

   private final SequentialFileFactory fileFactory;

   /**
    * The page cache that will be filled with data as we write more data
    */
   private volatile LivePageCache pageCache;

   private final AtomicInteger size = new AtomicInteger(0);

   private final StorageManager storageManager;

   private final SimpleString storeName;

   /**
    * A list of subscriptions containing pending counters (with non tx adds) on this page
    */
   private Set<PageSubscriptionCounter> pendingCounters;

   private int lastReadMessageNumber;
   private ByteBuffer readFileBuffer;
   private final ByteBuffer headerBuffer = ByteBuffer.allocate(HEADER_SIZE);
   private ChannelBufferWrapper readFileBufferWrapper;
   private int readProcessedBytes;

   public Page(final SimpleString storeName,
               final StorageManager storageManager,
               final SequentialFileFactory factory,
               final SequentialFile file,
               final int pageId) throws Exception {
      this.pageId = pageId;
      this.file = file;
      fileFactory = factory;
      this.storageManager = storageManager;
      this.storeName = storeName;
      resetReadMessageStatus();
   }

   public int getPageId() {
      return pageId;
   }

   public void setLiveCache(LivePageCache pageCache) {
      this.pageCache = pageCache;
   }

   public LivePageCache getLiveCache() {
      return pageCache;
   }

   private synchronized void resetReadMessageStatus() {
      lastReadMessageNumber = -3;
      readProcessedBytes = 0;
   }

   public synchronized PagedMessage readMessage(int startOffset,
                                                int startMessageNumber,
                                                int targetMessageNumber) throws Exception {
      assert startMessageNumber <= targetMessageNumber;

      if (!file.isOpen()) {
         throw ActiveMQMessageBundle.BUNDLE.invalidPageIO();
      }
      final int fileSize = (int) file.size();
      try {
         if (readFileBuffer == null) {
            readProcessedBytes = startOffset;

            if (startOffset > fileSize) {
               return readMessage(0, 0, targetMessageNumber);
            }

            file.position(readProcessedBytes);
            readFileBuffer = fileFactory.allocateDirectBuffer(Math.min(fileSize - readProcessedBytes, MIN_CHUNK_SIZE));
            //the wrapper is reused to avoid unnecessary allocations
            readFileBufferWrapper = wrapWhole(readFileBuffer);
            readFileBuffer.limit(0);
         } else if (lastReadMessageNumber + 1 != targetMessageNumber) {
            readProcessedBytes = startOffset;
            file.position(readProcessedBytes);
            readFileBuffer.limit(0);
         } else {
            startMessageNumber = targetMessageNumber;
         }

         int remainingBytes = fileSize - readProcessedBytes;
         int currentMessageNumber = startMessageNumber;
         // First we search forward for the file position of the target number message
         while (remainingBytes >= MINIMUM_MSG_PERSISTENT_SIZE && currentMessageNumber < targetMessageNumber) {
            headerBuffer.clear();
            file.read(headerBuffer);
            headerBuffer.position(0);

            if (headerBuffer.remaining() >= HEADER_SIZE && headerBuffer.get() == START_BYTE) {
               final int encodedSize = headerBuffer.getInt();
               final int nextPosition = readProcessedBytes + HEADER_AND_TRAILER_SIZE + encodedSize;
               if (nextPosition <= fileSize) {
                  final int endPosition = nextPosition - 1;
                  file.position(endPosition);
                  headerBuffer.rewind();
                  headerBuffer.limit(1);
                  file.read(headerBuffer);
                  headerBuffer.position(0);

                  if (headerBuffer.remaining() >= 1 && headerBuffer.get() == END_BYTE) {
                     readProcessedBytes = nextPosition;
                     currentMessageNumber++;
                  } else {
                     markFileAsSuspect(file.getFileName(), readProcessedBytes, currentMessageNumber);
                     break;
                  }
               } else {
                  markFileAsSuspect(file.getFileName(), readProcessedBytes, currentMessageNumber);
                  break;
               }
            } else {
               markFileAsSuspect(file.getFileName(), readProcessedBytes, currentMessageNumber);
               break;
            }
            remainingBytes = fileSize - readProcessedBytes;
         }

         // Then we read the target message
         if (currentMessageNumber == targetMessageNumber && remainingBytes >= MINIMUM_MSG_PERSISTENT_SIZE) {
            final ByteBuffer oldFileBuffer = readFileBuffer;
            readFileBuffer = readIntoFileBufferIfNecessary(readFileBuffer, MINIMUM_MSG_PERSISTENT_SIZE, true);
            //change wrapper if fileBuffer has changed
            if (readFileBuffer != oldFileBuffer) {
               readFileBufferWrapper = wrapWhole(readFileBuffer);
            }
            final byte startByte = readFileBuffer.get();
            if (startByte == Page.START_BYTE) {
               final int encodedSize = readFileBuffer.getInt();
               final int nextPosition = readProcessedBytes + HEADER_AND_TRAILER_SIZE + encodedSize;
               if (nextPosition <= fileSize) {
                  final ByteBuffer currentFileBuffer = readFileBuffer;
                  readFileBuffer = readIntoFileBufferIfNecessary(readFileBuffer, encodedSize + 1, true);
                  //change wrapper if fileBuffer has changed
                  if (readFileBuffer != currentFileBuffer) {
                     readFileBufferWrapper = wrapWhole(readFileBuffer);
                  }
                  final int endPosition = readFileBuffer.position() + encodedSize;
                  //this check must be performed upfront decoding
                  if (readFileBuffer.remaining() >= (encodedSize + 1) && readFileBuffer.get(endPosition) == Page.END_BYTE) {
                     final PagedMessageImpl msg = new PagedMessageImpl(encodedSize, storageManager);
                     readFileBufferWrapper.setIndex(readFileBuffer.position(), endPosition);
                     msg.decode(readFileBufferWrapper);
                     readFileBuffer.position(endPosition + 1);
                     assert readFileBuffer.get(endPosition) == Page.END_BYTE : "decoding cannot change end byte";
                     msg.initMessage(storageManager);
                     assert validateLargeMessageStorageManager(msg);
                     if (logger.isTraceEnabled()) {
                        logger.tracef("Reading message %s on pageId=%d for address=%s", msg, pageId, storeName);
                     }
                     readProcessedBytes = nextPosition;
                     lastReadMessageNumber = targetMessageNumber;
                     return msg;
                  } else {
                     markFileAsSuspect(file.getFileName(), readProcessedBytes, currentMessageNumber);
                  }
               } else {
                  markFileAsSuspect(file.getFileName(), readProcessedBytes, currentMessageNumber);
               }
            } else {
               markFileAsSuspect(file.getFileName(), readProcessedBytes, currentMessageNumber);
            }
         }
      } catch (Exception e) {
         resetReadMessageStatus();
         throw e;
      }
      resetReadMessageStatus();

      ActiveMQServerLogger.LOGGER.pageLookupError(this.pageId, targetMessageNumber, startOffset, startMessageNumber);

      if (startOffset > 0) {
         return readMessage(0, 0, targetMessageNumber);
      } else {
         return null;
      }
   }

   public synchronized List<PagedMessage> read() throws Exception {
      return read(storageManager);
   }

   public synchronized List<PagedMessage> read(StorageManager storage) throws Exception {
      return read(storage, false);
   }

   public synchronized List<PagedMessage> read(StorageManager storage, boolean onlyLargeMessages) throws Exception {
      if (logger.isDebugEnabled()) {
         logger.debugf("reading page %d on address = %s onlyLargeMessages = %b",
            new Object[] {pageId, storeName, onlyLargeMessages});
      }

      if (!file.isOpen()) {
         throw ActiveMQMessageBundle.BUNDLE.invalidPageIO();
      }

      size.lazySet((int) file.size());

      final List<PagedMessage> messages = new ArrayList<>();

      final int totalMessageCount = readFromSequentialFile(storage, messages, onlyLargeMessages);

      numberOfMessages.lazySet(totalMessageCount);

      return messages;
   }

   private ByteBuffer allocateAndReadIntoFileBuffer(ByteBuffer fileBuffer, int requiredBytes, boolean direct) throws Exception {
      ByteBuffer newFileBuffer;
      if (direct) {
         newFileBuffer = fileFactory.allocateDirectBuffer(Math.max(requiredBytes, MIN_CHUNK_SIZE));
         newFileBuffer.put(fileBuffer);
         fileFactory.releaseDirectBuffer(fileBuffer);
      } else {
         newFileBuffer = fileFactory.newBuffer(Math.max(requiredBytes, MIN_CHUNK_SIZE));
         newFileBuffer.put(fileBuffer);
         fileFactory.releaseBuffer(fileBuffer);
      }
      fileBuffer = newFileBuffer;
      //move the limit to allow reading as much as possible from the file
      fileBuffer.limit(fileBuffer.capacity());
      file.read(fileBuffer);
      fileBuffer.position(0);
      return fileBuffer;
   }

   /**
    * It returns a {@link ByteBuffer} that has {@link ByteBuffer#remaining()} bytes >= {@code requiredBytes}
    * of valid data from {@link #file}.
    */
   private ByteBuffer readIntoFileBufferIfNecessary(ByteBuffer fileBuffer, int requiredBytes, boolean direct) throws Exception {
      final int remaining = fileBuffer.remaining();
      //fileBuffer::remaining is the current size of valid data
      final int bytesToBeRead = requiredBytes - remaining;
      if (bytesToBeRead > 0) {
         final int capacity = fileBuffer.capacity();
         //fileBuffer has enough overall capacity to hold all the required bytes?
         if (capacity >= requiredBytes) {
            //we do not care to use the free space between
            //fileBuffer::limit and fileBuffer::capacity
            //to save compactions, because fileBuffer
            //is very unlikely to not be completely full
            //after each file::read
            if (fileBuffer.limit() > 0) {
               //the previous check avoid compact
               //to attempt a copy of 0 bytes
               fileBuffer.compact();
            } else {
               //compact already set the limit == capacity
               fileBuffer.limit(capacity);
            }
            file.read(fileBuffer);
            fileBuffer.position(0);
         } else {
            fileBuffer = allocateAndReadIntoFileBuffer(fileBuffer, requiredBytes, direct);
         }
      }
      return fileBuffer;
   }

   private static boolean validateLargeMessageStorageManager(PagedMessage msg) {
      if (!(msg.getMessage() instanceof LargeServerMessage)) {
         return true;
      }
      LargeServerMessage largeServerMessage = ((LargeServerMessage) msg.getMessage());
      return largeServerMessage.getStorageManager() != null;
   }

   private static ChannelBufferWrapper wrapWhole(ByteBuffer fileBuffer) {
      final int position = fileBuffer.position();
      final int limit = fileBuffer.limit();
      final int capacity = fileBuffer.capacity();
      try {
         fileBuffer.clear();
         final ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(fileBuffer);
         //this check is important to avoid next ByteBuf::setIndex
         //to fail due to ByteBuf::capacity == ByteBuffer::remaining bytes
         assert wrappedBuffer.readableBytes() == capacity;
         final ChannelBufferWrapper fileBufferWrapper = new ChannelBufferWrapper(wrappedBuffer);
         return fileBufferWrapper;
      } finally {
         fileBuffer.position(position);
         fileBuffer.limit(limit);
      }
   }

   //sizeOf(START_BYTE) + sizeOf(MESSAGE LENGTH) + sizeOf(END_BYTE)
   private static final int HEADER_AND_TRAILER_SIZE = DataConstants.SIZE_INT + 2;
   private static final int MINIMUM_MSG_PERSISTENT_SIZE = HEADER_AND_TRAILER_SIZE;
   private static final int HEADER_SIZE = HEADER_AND_TRAILER_SIZE - 1;
   private static final int MIN_CHUNK_SIZE = Env.osPageSize();

   private int readFromSequentialFile(StorageManager storage,
                                                     List<PagedMessage> messages,
                                                     boolean onlyLargeMessages) throws Exception {
      final int fileSize = (int) file.size();
      file.position(0);
      int processedBytes = 0;
      ByteBuffer fileBuffer = null;
      ChannelBufferWrapper fileBufferWrapper;
      int totalMessageCount = 0;
      try {
         int remainingBytes = fileSize - processedBytes;
         if (remainingBytes >= MINIMUM_MSG_PERSISTENT_SIZE) {
            fileBuffer = fileFactory.newBuffer(Math.min(remainingBytes, MIN_CHUNK_SIZE));
            //the wrapper is reused to avoid unnecessary allocations
            fileBufferWrapper = wrapWhole(fileBuffer);
            //no content is being added yet
            fileBuffer.limit(0);
            do {
               final ByteBuffer oldFileBuffer = fileBuffer;
               fileBuffer = readIntoFileBufferIfNecessary(fileBuffer, MINIMUM_MSG_PERSISTENT_SIZE, false);
               //change wrapper if fileBuffer has changed
               if (fileBuffer != oldFileBuffer) {
                  fileBufferWrapper = wrapWhole(fileBuffer);
               }
               final byte startByte = fileBuffer.get();
               if (startByte == Page.START_BYTE) {
                  final int encodedSize = fileBuffer.getInt();
                  final int nextPosition = processedBytes + HEADER_AND_TRAILER_SIZE + encodedSize;
                  if (nextPosition <= fileSize) {
                     final ByteBuffer currentFileBuffer = fileBuffer;
                     fileBuffer = readIntoFileBufferIfNecessary(fileBuffer, encodedSize + 1, false);
                     //change wrapper if fileBuffer has changed
                     if (fileBuffer != currentFileBuffer) {
                        fileBufferWrapper = wrapWhole(fileBuffer);
                     }
                     final int endPosition = fileBuffer.position() + encodedSize;
                     //this check must be performed upfront decoding
                     if (fileBuffer.remaining() >= (encodedSize + 1) && fileBuffer.get(endPosition) == Page.END_BYTE) {
                        fileBufferWrapper.setIndex(fileBuffer.position(), endPosition);
                        final boolean skipMessage;
                        if (onlyLargeMessages) {
                           skipMessage = !PagedMessageImpl.isLargeMessage(fileBufferWrapper);
                        } else {
                           skipMessage = false;
                        }
                        if (!skipMessage) {
                           final PagedMessageImpl msg = new PagedMessageImpl(encodedSize, storageManager);
                           msg.decode(fileBufferWrapper);
                           assert fileBuffer.get(endPosition) == Page.END_BYTE : "decoding cannot change end byte";
                           msg.initMessage(storage);
                           assert validateLargeMessageStorageManager(msg);
                           if (logger.isTraceEnabled()) {
                              logger.tracef("Reading message %s on pageId=%d for address=%s", msg, pageId, storeName);
                           }
                           messages.add(msg);
                        }
                        totalMessageCount++;
                        fileBuffer.position(endPosition + 1);
                        processedBytes = nextPosition;
                     } else {
                        markFileAsSuspect(file.getFileName(), processedBytes, totalMessageCount + 1);
                        return totalMessageCount;
                     }
                  } else {
                     markFileAsSuspect(file.getFileName(), processedBytes, totalMessageCount + 1);
                     return totalMessageCount;
                  }
               } else {
                  markFileAsSuspect(file.getFileName(), processedBytes, totalMessageCount + 1);
                  return totalMessageCount;
               }
               remainingBytes = fileSize - processedBytes;
            }
            while (remainingBytes >= MINIMUM_MSG_PERSISTENT_SIZE);
         }
         //ignore incomplete messages at the end of the file
         if (logger.isTraceEnabled()) {
            logger.tracef("%s has %d bytes of unknown data at position = %d", file.getFileName(), remainingBytes, processedBytes);
         }
         return totalMessageCount;
      } finally {
         if (fileBuffer != null) {
            fileFactory.releaseBuffer(fileBuffer);
         }
         size.lazySet(processedBytes);
         if (file.position() != processedBytes) {
            file.position(processedBytes);
         }
      }
   }

   public synchronized void write(final PagedMessage message) throws Exception {
      writeDirect(message);
      storageManager.pageWrite(message, pageId);
   }

   /** This write will not interact back with the storage manager.
    *  To avoid ping pongs with Journal retaining events and any other stuff. */
   public void writeDirect(PagedMessage message) throws Exception {
      if (!file.isOpen()) {
         throw ActiveMQMessageBundle.BUNDLE.cannotWriteToClosedFile(file);
      }
      final int messageEncodedSize = message.getEncodeSize();
      final int bufferSize = messageEncodedSize + Page.SIZE_RECORD;
      final ByteBuffer buffer = fileFactory.newBuffer(bufferSize);
      ChannelBufferWrapper activeMQBuffer = new ChannelBufferWrapper(Unpooled.wrappedBuffer(buffer));
      activeMQBuffer.clear();
      activeMQBuffer.writeByte(Page.START_BYTE);
      activeMQBuffer.writeInt(messageEncodedSize);
      message.encode(activeMQBuffer);
      activeMQBuffer.writeByte(Page.END_BYTE);
      assert (activeMQBuffer.readableBytes() == bufferSize) : "messageEncodedSize is different from expected";
      //buffer limit and position are the same
      assert (buffer.remaining() == bufferSize) : "buffer position or limit are changed";
      file.writeDirect(buffer, false);
      if (pageCache != null) {
         pageCache.addLiveMessage(message);
      }
      //lighter than addAndGet when single writer
      numberOfMessages.lazySet(numberOfMessages.get() + 1);
      size.lazySet(size.get() + bufferSize);
   }

   public void sync() throws Exception {
      file.sync();
   }

   public void open() throws Exception {
      if (!file.isOpen()) {
         file.open();
      }
      size.set((int) file.size());
      file.position(0);
   }

   public void close(boolean sendEvent) throws Exception {
      close(sendEvent, true);
   }

   /**
    * sendEvent means it's a close happening from a major event such moveNext.
    * While reading the cache we don't need (and shouldn't inform the backup
    */
   public synchronized void close(boolean sendEvent, boolean waitSync) throws Exception {
      if (readFileBuffer != null) {
         fileFactory.releaseDirectBuffer(readFileBuffer);
         readFileBuffer = null;
      }

      if (sendEvent && storageManager != null) {
         storageManager.pageClosed(storeName, pageId);
      }
      if (pageCache != null) {
         pageCache.close();
         // leave it to the soft cache to decide when to release it now
         pageCache = null;
      }
      file.close(waitSync, waitSync);

      Set<PageSubscriptionCounter> counters = getPendingCounters();
      if (counters != null) {
         for (PageSubscriptionCounter counter : counters) {
            counter.cleanupNonTXCounters(this.getPageId());
         }
      }
   }

   public boolean isLive() {
      return pageCache != null;
   }

   public boolean delete(final PagedMessage[] messages) throws Exception {
      if (storageManager != null) {
         storageManager.pageDeleted(storeName, pageId);
      }

      if (logger.isDebugEnabled()) {
         logger.debugf("Deleting pageNr=%d on store %s", pageId, storeName);
      }

      final List<Long> largeMessageIds;
      if (messages != null && messages.length > 0) {
         largeMessageIds = new ArrayList<>();
         for (PagedMessage msg : messages) {
            if ((msg.getMessage()).isLargeMessage()) {
               // this will trigger large message delete: no need to do it
               // for non-large messages!
               msg.getMessage().usageDown();
               largeMessageIds.add(msg.getMessage().getMessageID());
            }
         }
      } else {
         largeMessageIds = Collections.emptyList();
      }

      try {
         if (!storageManager.waitOnOperations(5000)) {
            ActiveMQServerLogger.LOGGER.timedOutWaitingForLargeMessagesDeletion(largeMessageIds);
         }
         if (suspiciousRecords) {
            ActiveMQServerLogger.LOGGER.pageInvalid(file.getFileName(), file.getFileName());
            file.renameTo(file.getFileName() + ".invalidPage");
         } else {
            file.delete();
         }

         return true;
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.pageDeleteError(e);
         return false;
      }
   }

   public int getNumberOfMessages() {
      return numberOfMessages.intValue();
   }

   public int getSize() {
      return size.intValue();
   }

   @Override
   public String toString() {
      return "Page::pageNr=" + this.pageId + ", file=" + this.file;
   }

   @Override
   public int compareTo(Page otherPage) {
      return otherPage.getPageId() - this.pageId;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + pageId;
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (getClass() != obj.getClass())
         return false;
      Page other = (Page) obj;
      if (pageId != other.pageId)
         return false;
      return true;
   }

   /**
    * @param position
    * @param msgNumber
    */
   private void markFileAsSuspect(final String fileName, final int position, final int msgNumber) {
      ActiveMQServerLogger.LOGGER.pageSuspectFile(fileName, position, msgNumber);
      suspiciousRecords = true;
   }

   public SequentialFile getFile() {
      return file;
   }

   /**
    * This will indicate a page that will need to be called on cleanup when the page has been closed and confirmed
    *
    * @param pageSubscriptionCounter
    */
   public void addPendingCounter(PageSubscriptionCounter pageSubscriptionCounter) {
      getOrCreatePendingCounters().add(pageSubscriptionCounter);
   }

   private synchronized Set<PageSubscriptionCounter> getPendingCounters() {
      return pendingCounters;
   }

   private synchronized Set<PageSubscriptionCounter> getOrCreatePendingCounters() {
      if (pendingCounters == null) {
         pendingCounters = new ConcurrentHashSet<>();
      }

      return pendingCounters;
   }
}
