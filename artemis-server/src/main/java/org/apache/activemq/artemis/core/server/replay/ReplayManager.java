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
package org.apache.activemq.artemis.core.server.replay;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.journal.impl.JournalReaderCallback;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.LargeMessagePersister;
import org.apache.activemq.artemis.core.replication.ReplicatedJournal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ReplayManager {

   public static SimpleDateFormat newRetentionSimpleDateFormat() {
      return new SimpleDateFormat("yyyyMMddHHmmss");
   }
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ActiveMQServer server;
   private JournalImpl journal;
   private final File retentionFolder;

   public ReplayManager(ActiveMQServer server) {
      this.server = server;
      this.retentionFolder = server.getConfiguration().getJournalRetentionLocation();
   }

   public void replay(Date start, Date end, String sourceAddress, String targetAddressParameter, String filterStr) throws Exception {
      logger.debug("Replay start::sourceAddress={}", sourceAddress);

      Objects.requireNonNull(sourceAddress, "sourceAddress");

      if (targetAddressParameter == null || targetAddressParameter.trim().isEmpty()) {
         targetAddressParameter = sourceAddress;
      }

      final String targetAddress = targetAddressParameter;

      if (journal == null) {
         // notice this routing plays single threaded. no need for any sort of synchronization here
         Journal storageManageJournal =  server.getStorageManager().getMessageJournal();
         if (storageManageJournal instanceof JournalImpl impl) {
            journal = impl;
         } else if (storageManageJournal instanceof ReplicatedJournal replicatedJournal) {
            journal = (JournalImpl) replicatedJournal.getLocalJournal();
         } else {
            throw new IllegalStateException("could not local a valid journal to use with the ReplayManager");
         }
      }

      Filter filter;

      if (filterStr != null) {
         filter = FilterImpl.createFilter(filterStr);
      } else {
         filter = null;
      }

      journal.forceBackup(1, TimeUnit.MINUTES);

      SequentialFileFactory messagesFF = new NIOSequentialFileFactory(retentionFolder, null, 1);

      // Will use only default values. The load function should adapt to anything different
      JournalImpl messagesJournal = new JournalImpl(server.getConfiguration().getJournalFileSize(), server.getConfiguration().getJournalMinFiles(), server.getConfiguration().getJournalPoolFiles(), 0, 0, messagesFF, "artemis-data", "amq", 1);

      List<JournalFile> files = messagesJournal.orderFiles();

      RoutingContext context = new RoutingContextImpl(null);

      Map<Long, Set<JournalFile>> largeMessageLocations = new HashMap<>();

      for (JournalFile file : files) {
         if (start != null || end != null) {
            final String fileName = file.getFile().getFileName();
            final long fileEpochTime = journal.getDatePortionMillis(fileName);

            if (logger.isDebugEnabled()) {
               String datePortion = journal.getDatePortion(fileName);
               logger.debug("Evaluating replay for file {}, datePortion={}\n\tInterval evaluated: start({}) --- file({}) --- end({})\n\tepoch times: start({}) --- file({}) + end({})",
                             fileName, datePortion, start, new Date(fileEpochTime), end, start.getTime(), fileEpochTime, end.getTime());
            }

            if (start != null && fileEpochTime < start.getTime()) {
               logger.debug("File {} being skipped on start comparison", fileName);
               continue;
            }

            if (end != null && fileEpochTime > end.getTime()) {
               logger.debug("File {} being skipped on end comparison", fileName);
               continue;
            }
         }
         logger.debug("Reading retention file {}", file);
         JournalImpl.readJournalFile(messagesFF, file, new JournalReaderCallback() {
            @Override
            public void onReadEventRecord(RecordInfo info) throws Exception {
               switch (info.getUserRecordType()) {
                  case JournalRecordIds.ADD_MESSAGE_BODY:
                     Set<JournalFile> files = largeMessageLocations.get(info.id);
                     if (files == null) {
                        files = new LinkedHashSet<>();
                        largeMessageLocations.put(info.id, files);
                     }
                     files.add(file);
                     break;

                  default:
                     onReadAddRecord(info);
               }
            }

            @Override
            public void onReadAddRecord(RecordInfo info) throws Exception {
               if (info.getUserRecordType() == JournalRecordIds.ADD_LARGE_MESSAGE) {
                  ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(info.data);
                  LargeServerMessage message = new LargeServerMessageImpl(server.getStorageManager());
                  LargeMessagePersister.getInstance().decode(buffer, message, null);
                  route(filter, context, messagesFF, message.toMessage(), sourceAddress, targetAddress, largeMessageLocations);
               } else if (info.getUserRecordType() == JournalRecordIds.ADD_MESSAGE_PROTOCOL) {
                  ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(info.data);
                  Message message = MessagePersister.getInstance().decode(buffer, null, null, server.getStorageManager());
                  route(filter, context, messagesFF, message, sourceAddress, targetAddress, largeMessageLocations);
               }

            }

            @Override
            public void onReadUpdateRecord(RecordInfo info) throws Exception {
               onReadAddRecord(info);
            }

            @Override
            public void onReadAddRecordTX(long transactionID, RecordInfo info) throws Exception {
               onReadAddRecord(info);
            }

            @Override
            public void onReadUpdateRecordTX(long transactionID, RecordInfo info) throws Exception {
               onReadUpdateRecord(info);
            }

         }, null, false, null);
      }

      logger.debug("Replay done::sourceAddress={}", sourceAddress);
   }

   private boolean messageMatch(Filter filter, Message message, String sourceAddress, String targetAddress) {
      if (message.getAddress() != null && message.getAddress().equals(sourceAddress)) {
         if (filter != null) {
            if (!filter.match(message)) {
               return false;
            }
         }
         if (targetAddress != null && !targetAddress.equals(sourceAddress)) {
            message.setAddress(targetAddress);
            message.reencode();
         }
         return true;
      } else {
         return false;
      }
   }


   private void route(Filter filter, RoutingContext context, SequentialFileFactory messagesFF, Message message, String sourceAddress, String targetAddress, Map<Long, Set<JournalFile>> filesMap) throws Exception {
      if (messageMatch(filter, message, sourceAddress, targetAddress)) {
         final long originalMessageID = message.getMessageID();
         message.setMessageID(server.getStorageManager().generateID());
         if (message.isLargeMessage()) {
            readLargeMessageBody(messagesFF, message, filesMap, originalMessageID);
         }
         if (targetAddress != null && !sourceAddress.equals(targetAddress)) {
            message.setAddress(targetAddress);
            message.reencode();
         }
         server.getPostOffice().route(message, context, false, false, null);
         context.clear();
      } else {
         if (message.isLargeMessage()) {
            filesMap.remove(message.getMessageID());
         }
      }
   }

   private void readLargeMessageBody(SequentialFileFactory messagesFF,
                          Message message,
                          Map<Long, Set<JournalFile>> filesMap,
                          long originalMessageID) throws Exception {
      long newMessageID = message.getMessageID();
      SequentialFile largeMessageFile = server.getStorageManager().createFileForLargeMessage(newMessageID, true);
      largeMessageFile.open();

      Set<JournalFile> files = filesMap.get(originalMessageID);
      if (files != null) {
         for (JournalFile file : files) {
            JournalImpl.readJournalFile(messagesFF, file, new JournalReaderCallback() {
               @Override
               public void onReadEventRecord(RecordInfo info) throws Exception {
                  if (info.userRecordType == JournalRecordIds.ADD_MESSAGE_BODY && info.id == originalMessageID) {
                     server.getStorageManager().addBytesToLargeMessage(largeMessageFile, newMessageID, info.data);
                  }
               }
            });
         }
      }
      largeMessageFile.close();
   }

}
