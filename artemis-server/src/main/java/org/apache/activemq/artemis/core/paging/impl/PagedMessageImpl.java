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

import java.util.Arrays;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.cursor.impl.PagePositionImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.LargeMessagePersister;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 * This class represents a paged message
 */
public class PagedMessageImpl implements PagedMessage {

   // It encapsulates the logic to detect large message types
   private static final class LargeMessageType {

      private static final byte NONE = 0;
      private static final byte CORE = 1;
      private static final byte OLD_CORE = -1;
      private static final byte NOT_CORE = 2;

      public static boolean isLargeMessage(byte encodedValue) {
         return switch (encodedValue) {
            case LargeMessageType.NONE -> false;
            case LargeMessageType.CORE, LargeMessageType.OLD_CORE, LargeMessageType.NOT_CORE -> true;
            default -> throw new IllegalStateException("This largeMessageType isn't supported: " + encodedValue);
         };
      }

      public static boolean isCoreLargeMessage(Message message) {
         return message.isLargeMessage() && message instanceof ICoreMessage;
      }

      public static boolean isCoreLargeMessageType(byte encodedValue) {
         return encodedValue == LargeMessageType.CORE ||
            encodedValue == LargeMessageType.OLD_CORE;
      }

      public static byte valueOf(Message message) {
         if (!message.isLargeMessage()) {
            return NONE;
         }
         if (message instanceof ICoreMessage) {
            return CORE;
         }
         return NOT_CORE;
      }
   }

   /**
    * Large messages will need to be instantiated lazily during getMessage when the StorageManager is available
    */
   private byte[] largeMessageLazyData;

   private Message message;

   private long[] queueIDs;

   private long transactionID = 0;

   private final int storedSize;

   private final StorageManager storageManager;

   private long pageNumber;

   private int messageNumber;

   public PagedMessageImpl(final Message message, final long[] queueIDs, final long transactionID) {
      this(message, queueIDs);
      this.transactionID = transactionID;
   }

   public PagedMessageImpl(final Message message, final long[] queueIDs) {
      this.storageManager = null;
      this.queueIDs = queueIDs;
      this.message = message;
      this.message.setPaged();
      this.storedSize = 0;
      checkLargeMessage();
   }

   public PagedMessageImpl(int storedSize, StorageManager storageManager) {
      this.storageManager = storageManager;
      this.storedSize = storedSize;
   }

   @Override
   public long getPageNumber() {
      return pageNumber;
   }

   @Override
   public PagedMessageImpl setPageNumber(long pageNr) {
      this.pageNumber = pageNr;
      return this;
   }

   @Override
   public int getMessageNumber() {
      return this.messageNumber;
   }

   @Override
   public PagedMessageImpl setMessageNumber(int messageNr) {
      this.messageNumber = messageNr;
      return this;
   }


   @Override
   public int getStoredSize() {
      if (storedSize <= 0) {
         return getEncodeSize();
      } else {
         return storedSize;
      }
   }

   @Override
   public Message getMessage() {
      return message;
   }

   @Override
   public PagePosition newPositionObject() {
      return new PagePositionImpl(pageNumber, messageNumber);
   }

   @Override
   public void initMessage(StorageManager storage) {
      if (largeMessageLazyData != null) {
         LargeServerMessage lgMessage = storage.createCoreLargeMessage();

         ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(largeMessageLazyData);
         lgMessage = LargeMessagePersister.getInstance().decode(buffer, lgMessage, null);
         if (lgMessage.toMessage() instanceof LargeServerMessage) {
            ((LargeServerMessage)lgMessage.toMessage()).setStorageManager(storage);
         }
         lgMessage.toMessage().usageUp();
         lgMessage.setPaged();
         this.message = lgMessage.toMessage();
         this.message.setPaged();
         largeMessageLazyData = null;
         checkLargeMessage();
      } else {
         if (message != null && message instanceof LargeServerMessage largeServerMessage) {
            largeServerMessage.setStorageManager(storageManager);
         }
      }
   }

   @Override
   public long getTransactionID() {
      return transactionID;
   }

   @Override
   public long[] getQueueIDs() {
      return queueIDs;
   }

   // EncodingSupport implementation --------------------------------

   /**
    * This method won't move the {@link ActiveMQBuffer#readerIndex()} of {@code buffer}.
    */
   public static boolean isLargeMessage(ActiveMQBuffer buffer) {
      // skip transactionID
      return LargeMessageType.isLargeMessage(buffer.getByte(buffer.readerIndex() + Long.BYTES));
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      transactionID = buffer.readLong();

      boolean isCoreLargeMessage = LargeMessageType.isCoreLargeMessageType(buffer.readByte());

      if (isCoreLargeMessage) {
         int largeMessageHeaderSize = buffer.readInt();

         if (storageManager == null) {
            largeMessageLazyData = new byte[largeMessageHeaderSize];
            buffer.readBytes(largeMessageLazyData);
         } else {
            this.message = storageManager.createCoreLargeMessage().toMessage();
            this.message.setPaged();
            LargeMessagePersister.getInstance().decode(buffer, (LargeServerMessage) message, null);
            ((LargeServerMessage) message).setStorageManager(storageManager);
            ((LargeServerMessage) message).toMessage().usageUp();
         }
      } else {
         this.message = MessagePersister.getInstance().decode(buffer, null, null, storageManager);
         if (message.isLargeMessage()) {
            message.usageUp();
         }
      }

      checkLargeMessage();

      int queueIDsSize = buffer.readInt();

      queueIDs = new long[queueIDsSize];

      for (int i = 0; i < queueIDsSize; i++) {
         queueIDs[i] = buffer.readLong();
      }
   }

   private void checkLargeMessage() {
      if (message != null && message.isLargeMessage()) {
         // large messages will be read and released multiple times, we have to disable their checks
         // also we will ack them without refUp, so they will be reported negative
         ((RefCountMessage) message).disableErrorCheck();
      }
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      buffer.writeLong(transactionID);

      byte largeMessageType = LargeMessageType.valueOf(message);

      buffer.writeByte(largeMessageType);

      if (LargeMessageType.isCoreLargeMessageType(largeMessageType)) {
         buffer.writeInt(LargeMessagePersister.getInstance().getEncodeSize((LargeServerMessage) message));
         LargeMessagePersister.getInstance().encode(buffer, (LargeServerMessage) message);
      } else {
         message.getPersister().encode(buffer, message);
      }

      buffer.writeInt(queueIDs.length);

      for (long queueID : queueIDs) {
         buffer.writeLong(queueID);
      }
   }

   @Override
   public int getEncodeSize() {
      if (LargeMessageType.isCoreLargeMessage(message)) {
         return DataConstants.SIZE_LONG + DataConstants.SIZE_BYTE + DataConstants.SIZE_INT + LargeMessagePersister.getInstance().getEncodeSize((LargeServerMessage)message) +
            DataConstants.SIZE_INT + queueIDs.length * DataConstants.SIZE_LONG;
      } else {
         return DataConstants.SIZE_LONG + DataConstants.SIZE_BYTE + message.getPersister().getEncodeSize(message) +
            DataConstants.SIZE_INT + queueIDs.length * DataConstants.SIZE_LONG;
      }
   }

   @Override
   public String toString() {
      return "PagedMessageImpl [queueIDs=" + Arrays.toString(queueIDs) +
         ", transactionID=" +
         transactionID +
         ", page=" + pageNumber + ", message=" +
         message +
         "]";
   }

   @Override
   public long getPersistentSize() throws ActiveMQException {
      return message.getPersistentSize();
   }
}
