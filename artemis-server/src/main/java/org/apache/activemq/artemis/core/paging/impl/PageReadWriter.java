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
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class PageReadWriter {


   private static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final int SIZE_RECORD = DataConstants.SIZE_BYTE + DataConstants.SIZE_INT + DataConstants.SIZE_BYTE;

   private static final byte START_BYTE = (byte) '{';

   private static final byte END_BYTE = (byte) '}';

   //sizeOf(START_BYTE) + sizeOf(MESSAGE LENGTH) + sizeOf(END_BYTE)
   private static final int HEADER_AND_TRAILER_SIZE = DataConstants.SIZE_INT + 2;
   private static final int MINIMUM_MSG_PERSISTENT_SIZE = HEADER_AND_TRAILER_SIZE;
   private static final int MIN_CHUNK_SIZE = Env.osPageSize();

   public interface SuspectFileCallback {
      void onSuspect(String fileName, int position, int msgNumber);
   }

   public interface PageRecordFilter {
      boolean skip(ActiveMQBuffer buffer);
   }

   public interface ReadCallback {
      void readComple(int size);
   }

   public static final PageRecordFilter ONLY_LARGE = (buffer) -> !PagedMessageImpl.isLargeMessage(buffer);

   public static final PageRecordFilter NO_SKIP = (buffer) -> false;

   public static final PageRecordFilter SKIP_ALL = (buffer) -> true;

   public static int writeMessage(PagedMessage message, SequentialFileFactory fileFactory, SequentialFile file) throws Exception {
      final int messageEncodedSize = message.getEncodeSize();
      final int bufferSize = messageEncodedSize + SIZE_RECORD;
      final ByteBuffer buffer = fileFactory.newBuffer(bufferSize);
      ChannelBufferWrapper activeMQBuffer = new ChannelBufferWrapper(Unpooled.wrappedBuffer(buffer));
      activeMQBuffer.clear();
      activeMQBuffer.writeByte(START_BYTE);
      activeMQBuffer.writeInt(messageEncodedSize);
      message.encode(activeMQBuffer);
      activeMQBuffer.writeByte(END_BYTE);
      assert (activeMQBuffer.readableBytes() == bufferSize) : "messageEncodedSize is different from expected";
      //buffer limit and position are the same
      assert (buffer.remaining() == bufferSize) : "buffer position or limit are changed";
      if (fileFactory.supportsIndividualContext()) {
         OperationContext context = OperationContextImpl.getContext();
         if (context != null) {
            context.storeLineUp();
         }
         file.writeDirect(buffer, false, context);
      } else {
         file.writeDirect(buffer, false);
      }
      return bufferSize;
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


   public static int readFromSequentialFile(StorageManager storage,
                                             SimpleString storeName,
                                             SequentialFileFactory fileFactory,
                                             SequentialFile file,
                                             long pageId,
                                             Consumer<PagedMessage> messages,
                                             PageRecordFilter skipRecord,
                                             SuspectFileCallback suspectFileCallback,
                                             ReadCallback readCallback) throws Exception {
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

               fileBuffer = readIntoFileBufferIfNecessary(fileFactory, file, fileBuffer, MINIMUM_MSG_PERSISTENT_SIZE, false);

               //change wrapper if fileBuffer has changed
               if (fileBuffer != oldFileBuffer) {
                  fileBufferWrapper = wrapWhole(fileBuffer);
               }

               final byte startByte = fileBuffer.get();
               if (startByte == START_BYTE) {

                  final int encodedSize = fileBuffer.getInt();
                  final int nextPosition = processedBytes + HEADER_AND_TRAILER_SIZE + encodedSize;

                  if (nextPosition <= fileSize) {

                     final ByteBuffer currentFileBuffer = fileBuffer;
                     fileBuffer = readIntoFileBufferIfNecessary(fileFactory, file, fileBuffer, encodedSize + 1, false);
                     //change wrapper if fileBuffer has changed
                     if (fileBuffer != currentFileBuffer) {
                        fileBufferWrapper = wrapWhole(fileBuffer);
                     }

                     final int endPosition = fileBuffer.position() + encodedSize;

                     //this check must be performed upfront decoding
                     if (fileBuffer.remaining() >= (encodedSize + 1) && fileBuffer.get(endPosition) == END_BYTE) {

                        fileBufferWrapper.setIndex(fileBuffer.position(), endPosition);

                        final boolean skipMessage = skipRecord.skip(fileBufferWrapper);

                        if (!skipMessage) {
                           final PagedMessageImpl msg = new PagedMessageImpl(encodedSize, storage);
                           msg.decode(fileBufferWrapper);

                           assert fileBuffer.get(endPosition) == END_BYTE : "decoding cannot change end byte";

                           msg.initMessage(storage);

                           assert validateLargeMessageStorageManager(msg);

                           if (logger.isTraceEnabled()) {
                              logger.trace("Reading message {} on pageId={} for address={}", msg, pageId, storeName);
                           }

                           if (messages != null) {
                              messages.accept(msg);
                           }

                           msg.setPageNumber(pageId).setMessageNumber(totalMessageCount);
                        }

                        totalMessageCount++;
                        fileBuffer.position(endPosition + 1);
                        processedBytes = nextPosition;

                     } else {

                        if (suspectFileCallback != null) {
                           suspectFileCallback.onSuspect(file.getFileName(), processedBytes, totalMessageCount + 1);
                        }

                        return totalMessageCount;

                     }
                  } else {

                     if (suspectFileCallback != null) {
                        suspectFileCallback.onSuspect(file.getFileName(), processedBytes, totalMessageCount + 1);
                     }

                     return totalMessageCount;
                  }
               } else {

                  if (suspectFileCallback != null) {
                     suspectFileCallback.onSuspect(file.getFileName(), processedBytes, totalMessageCount + 1);
                  }

                  return totalMessageCount;
               }

               remainingBytes = fileSize - processedBytes;

            }
            while (remainingBytes >= MINIMUM_MSG_PERSISTENT_SIZE);
         }

         //ignore incomplete messages at the end of the file
         if (logger.isTraceEnabled()) {
            logger.trace("{} has {} bytes of unknown data at position = {}", file.getFileName(), remainingBytes, processedBytes);
         }

         return totalMessageCount;
      } finally {
         if (fileBuffer != null) {
            fileFactory.releaseBuffer(fileBuffer);
         }
         if (readCallback != null) {
            readCallback.readComple(processedBytes);
         }
         if (file.position() != processedBytes) {
            file.position(processedBytes);
         }
      }
   }

   private static ByteBuffer readIntoFileBufferIfNecessary(SequentialFileFactory fileFactory, SequentialFile file, ByteBuffer fileBuffer, int requiredBytes, boolean direct) throws Exception {

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
            fileBuffer = allocateAndReadIntoFileBuffer(fileFactory, file, fileBuffer, requiredBytes, direct);
         }
      }
      return fileBuffer;
   }


   private static ByteBuffer allocateAndReadIntoFileBuffer(SequentialFileFactory fileFactory, SequentialFile file, ByteBuffer fileBuffer, int requiredBytes, boolean direct) throws Exception {
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



   private static boolean validateLargeMessageStorageManager(PagedMessage msg) {
      if (!(msg.getMessage() instanceof LargeServerMessage largeServerMessage)) {
         return true;
      }

      boolean storageManager = largeServerMessage.getStorageManager() != null;
      if (!storageManager) {
         logger.warn("storage manager is null at {}", msg);
      }

      return storageManager;
   }
}
