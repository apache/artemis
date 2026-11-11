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
package org.apache.activemq.artemis.cli.commands.tools;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.journal.impl.JournalReaderCallback;
import org.apache.activemq.artemis.core.journal.impl.dataformat.ByteArrayEncoding;
import org.apache.activemq.artemis.core.message.impl.CoreMessagePersister;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.apache.activemq.artemis.utils.ByteUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "recover", description = "Recover (undelete) every message on the journal by creating a new output journal. Rolled back and acked messages will be sent out to the output as much as possible.")
public class RecoverMessages extends DBOption {

   static {
      MessagePersister.registerPersister(CoreMessagePersister.getInstance());
   }

   @Option(names = "--reclaimed", description = "Try to recover as many records as possible from reclaimed files.")
   private boolean reclaimed = false;

   @Option(names = "--target", description = "Output folder container the new journal with all the generated messages.", required = true)
   private String outputJournal;


   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      Configuration configuration = getParameterConfiguration();

      File journalOutput = new File(outputJournal);


      try {
         if (configuration.isJDBC()) {
            throw new IllegalAccessException("JDBC Not supported on recover");
         } else {
            recover(context, configuration, getJournal(), journalOutput, new File(getLargeMessages()), reclaimed);
         }
      } catch (Exception e) {
         treatError(e, "data", "recover");
      }
      return null;
   }

   public static void recover(ActionContext context, Configuration configuration, String journallocation, File journalOutput, File largeMessage, boolean reclaimed) throws Exception {

      File journal = new File(journallocation);

      if (!journalOutput.exists()) {
         if (!journalOutput.mkdirs()) {
            throw new IllegalStateException("It was not possible to create " + journalOutput);
         }
      }

      if (journalOutput.exists() && !journalOutput.isDirectory()) {
         throw new IllegalStateException(journalOutput + " is not a directory");
      }

      SequentialFileFactory outputFF = new NIOSequentialFileFactory(journalOutput, null, 1);
      outputFF.setDatasync(false);
      JournalImpl targetJournal = new JournalImpl(configuration.getJournalFileSize(), 2, 2, -1, 0, outputFF, "activemq-data", "amq", 1);
      targetJournal.setAutoReclaim(false);

      targetJournal.start();
      targetJournal.loadInternalOnly();

      SequentialFileFactory messagesFF = new NIOSequentialFileFactory(journal, null, 1);
      SequentialFileFactory largeMessagesFF = new NIOSequentialFileFactory(largeMessage, null, 1);

      // Will use only default values. The load function should adapt to anything different
      JournalImpl messagesJournal = new JournalImpl(configuration.getJournalFileSize(), configuration.getJournalMinFiles(), configuration.getJournalPoolFiles(), 0, 0, messagesFF, "activemq-data", "amq", 1);

      List<JournalFile> files = messagesJournal.orderFiles();

      Set<Byte> userRecordsOfInterest = new HashSet<>();
      userRecordsOfInterest.add(JournalRecordIds.ADD_LARGE_MESSAGE);
      userRecordsOfInterest.add(JournalRecordIds.ADD_MESSAGE);
      userRecordsOfInterest.add(JournalRecordIds.ADD_MESSAGE_PROTOCOL);
      userRecordsOfInterest.add(JournalRecordIds.ADD_REF);
      userRecordsOfInterest.add(JournalRecordIds.PAGE_TRANSACTION);

      Set<Pair<Long, Long>> routeBindigns = new HashSet<>();

      for (JournalFile file : files) {
         // For reviewers and future maintainers: I really meant System.out.println here
         // This is part of the CLI, hence this is like user's output
         context.out.println("Recovering messages from file " + file);

         JournalImpl.readJournalFile(messagesFF, file, new JournalReaderCallback() {
            long lastlargeMessageId = -1;
            SequentialFile largeMessageFile;
            @Override
            public void done() {
               try {
                  if (largeMessageFile != null) {
                     largeMessageFile.close();
                     largeMessageFile = null;
                  }
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }
            @Override
            public void onReadEventRecord(RecordInfo info) throws Exception {
               switch (info.getUserRecordType()) {
                  case JournalRecordIds.ADD_REF:
                     onReadUpdateRecord(info);
                     break;

                  case JournalRecordIds.ADD_MESSAGE_BODY:
                     if (lastlargeMessageId != info.id || largeMessageFile == null) {
                        if (largeMessageFile != null) {
                           largeMessageFile.close();
                        }

                        largeMessageFile = largeMessagesFF.createSequentialFile(info.id + ".msg");
                        largeMessageFile.open();
                        largeMessageFile.position(largeMessageFile.size());
                        lastlargeMessageId = info.id;
                     }
                     largeMessageFile.write(new ByteArrayEncoding(info.data), false, null);
                     break;

                  default:
                     onReadAddRecord(info);
               }
            }

            @Override
            public void onReadAddRecord(RecordInfo info) throws Exception {
               if (userRecordsOfInterest.contains(info.getUserRecordType())) {

                  if (targetJournal.getRecords().get(info.id) != null) {
                     // Really meant System.out.. user's information on the CLI
                     context.out.println("RecordID " + info.id + " would been duplicated, ignoring it");
                     return;
                  }
                  try {
                     targetJournal.appendAddRecord(info.id, info.userRecordType, info.data, false);
                  } catch (Exception e) {
                     // Really meant System.out.. user's information on the CLI
                     context.out.println("Cannot append record for " + info.id + "->" + e.getMessage());
                  }
               }
            }

            @Override
            public void onReadUpdateRecord(RecordInfo info) throws Exception {
               if (userRecordsOfInterest.contains(info.getUserRecordType())) {
                  if (info.getUserRecordType() == JournalRecordIds.ADD_REF) {
                     long queue = ByteUtil.bytesToLong(info.data);
                     Pair<Long, Long> pairQueue = new Pair<>(info.id, queue);
                     if (routeBindigns.contains(pairQueue)) {
                        // really meant system.out
                        context.out.println("AddReference on " + info.id + " / queue=" + queue + " has already been recorded, ignoring it");
                        return;
                     }

                     routeBindigns.add(pairQueue);
                  }
                  try {
                     targetJournal.appendUpdateRecord(info.id, info.userRecordType, info.data, true);
                  } catch (Exception e) {
                     context.out.println("Cannot update record " + info.id + "-> " + e.getMessage());
                     e.printStackTrace(context.err);
                  }
               }
            }

            @Override
            public void onReadDeleteRecord(long recordID) throws Exception {

            }

            @Override
            public void onReadAddRecordTX(long transactionID, RecordInfo info) throws Exception {
               onReadAddRecord(info);
            }

            @Override
            public void onReadUpdateRecordTX(long transactionID, RecordInfo info) throws Exception {
               onReadUpdateRecord(info);
            }

            @Override
            public void onReadDeleteRecordTX(long transactionID, RecordInfo recordInfo) throws Exception {

            }

            @Override
            public void onReadPrepareRecord(long transactionID,
                                            byte[] extraData,
                                            int numberOfRecords) throws Exception {

            }

            @Override
            public void onReadCommitRecord(long transactionID, int numberOfRecords) throws Exception {

            }

            @Override
            public void onReadRollbackRecord(long transactionID) throws Exception {

            }

            @Override
            public void markAsDataFile(JournalFile file) {

            }
         }, null, reclaimed, null);
      }

      targetJournal.flush();

      targetJournal.stop();
      outputFF.stop();
   }



}
