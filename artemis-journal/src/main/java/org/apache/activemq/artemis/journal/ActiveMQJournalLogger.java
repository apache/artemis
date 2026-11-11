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
package org.apache.activemq.artemis.journal;

import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.BundleFactory;

/**
 * Logger Codes 140000 - 148999
 */
@LogBundle(projectCode = "AMQ", regexID = "14[0-8][0-9]{3}", retiredIDs = {141000, 141002, 142000, 142001, 142002, 142003, 142016, 142017, 142020, 142030, 142034, 142035, 144001, 144005})
public interface ActiveMQJournalLogger {

   ActiveMQJournalLogger LOGGER = BundleFactory.newBundle(ActiveMQJournalLogger.class, ActiveMQJournalLogger.class.getPackage().getName());

   @LogMessage(id = 141003, value = "Write rate = {} bytes / sec or {} MiB / sec", level = LogMessage.Level.INFO)
   void writeRate(Double rate, Long l);

   @LogMessage(id = 141004, value = "Flush rate = {} flushes / sec", level = LogMessage.Level.INFO)
   void flushRate(Double rate);

   @LogMessage(id = 141005, value = "Check Data Files:", level = LogMessage.Level.INFO)
   void checkFiles();

   @LogMessage(id = 141006, value = "Sequence out of order on journal", level = LogMessage.Level.INFO)
   void seqOutOfOrder();

   @LogMessage(id = 141007, value = "Current File on the journal is <= the sequence file.getFileID={} on the dataFiles" + "\nCurrentfile.getFileId={} while the file.getFileID()={}" + "\nIs same = ({})", level = LogMessage.Level.INFO)
   void currentFile(Long fileID, Long id, Long fileFileID, Boolean b);

   @LogMessage(id = 141008, value = "Free File ID out of order", level = LogMessage.Level.INFO)
   void fileIdOutOfOrder();

   @LogMessage(id = 141009, value = "A Free File is less than the maximum data", level = LogMessage.Level.INFO)
   void fileTooSmall();

   @LogMessage(id = 141010, value = "Initialising JDBC data source {} with properties {}", level = LogMessage.Level.INFO)
   void initializingJdbcDataSource(String dataSourceClassName, String dataSourceProperties);

   @LogMessage(id = 142004, value = "Inconsistency during compacting: CommitRecord ID = {} for an already committed transaction during compacting", level = LogMessage.Level.WARN)
   void inconsistencyDuringCompacting(Long transactionID);

   @LogMessage(id = 142005, value = "Inconsistency during compacting: Delete record being read on an existent record (id={})", level = LogMessage.Level.WARN)
   void inconsistencyDuringCompactingDelete(Long recordID);

   @LogMessage(id = 142006, value = "Could not find add Record information for record {} during compacting", level = LogMessage.Level.WARN)
   void compactingWithNoAddRecord(Long id);

   @LogMessage(id = 142007, value = "Can not find record {} during compact replay", level = LogMessage.Level.WARN)
   void noRecordDuringCompactReplay(Long id);

   @LogMessage(id = 142008, value = "Could not remove file {} from the list of data files", level = LogMessage.Level.WARN)
   void couldNotRemoveFile(JournalFile file);

   @LogMessage(id = 142009, value = "*******************************************************************************************************************************\nThe File Storage Attic is full, as the file {}  does not have the configured size, and the file will be removed\n*******************************************************************************************************************************", level = LogMessage.Level.WARN)
   void deletingFile(JournalFile file);

   @LogMessage(id = 142010, value = "Failed to add file to opened files queue: {}. This should NOT happen!", level = LogMessage.Level.WARN)
   void failedToAddFile(JournalFile nextOpenedFile);

   @LogMessage(id = 142011, value = "Error on reading compacting for {}", level = LogMessage.Level.WARN)
   void compactReadError(JournalFile file);

   @LogMessage(id = 142012, value = "Couldn't find tx={} to merge after compacting", level = LogMessage.Level.WARN)
   void compactMergeError(Long id);

   @LogMessage(id = 142013, value = "Prepared transaction {} was not considered completed, it will be ignored", level = LogMessage.Level.WARN)
   void preparedTXIncomplete(Long id);

   @LogMessage(id = 142014, value = "Transaction {} is missing elements so the transaction is being ignored", level = LogMessage.Level.WARN)
   void txMissingElements(Long id);

   @LogMessage(id = 142015, value = "Uncommitted transaction with id {} found and discarded", level = LogMessage.Level.WARN)
   void uncomittedTxFound(Long id);

   @LogMessage(id = 142018, value = "Temporary files were left unattended after a crash on journal directory, deleting invalid files now", level = LogMessage.Level.WARN)
   void tempFilesLeftOpen();

   @LogMessage(id = 142019, value = "Deleting orphaned file {}", level = LogMessage.Level.WARN)
   void deletingOrphanedFile(String fileToDelete);

   @LogMessage(id = 142021, value = "Error on IO callback, {}", level = LogMessage.Level.WARN)
   void errorOnIOCallback(String errorMessage);

   @LogMessage(id = 142022, value = "Timed out on AIO poller shutdown", level = LogMessage.Level.WARN)
   void timeoutOnPollerShutdown(Exception e);

   @LogMessage(id = 142023, value = "Executor on file {} couldn't complete its tasks in 60 seconds.", level = LogMessage.Level.WARN)
   void couldNotCompleteTask(String name, Exception e);

   @LogMessage(id = 142024, value = "Error completing callback", level = LogMessage.Level.WARN)
   void errorCompletingCallback(Throwable e);

   @LogMessage(id = 142025, value = "Error calling onError callback", level = LogMessage.Level.WARN)
   void errorCallingErrorCallback(Throwable e);

   @LogMessage(id = 142026, value = "Timed out on AIO writer shutdown", level = LogMessage.Level.WARN)
   void timeoutOnWriterShutdown(Throwable e);

   @LogMessage(id = 142027, value = "Error on writing data! {} code - {}", level = LogMessage.Level.WARN)
   void errorWritingData(String errorMessage, int errorCode, Throwable e);

   @LogMessage(id = 142028, value = "Error replaying pending commands after compacting", level = LogMessage.Level.WARN)
   void errorReplayingCommands(Throwable e);

   @LogMessage(id = 142029, value = "Error closing file", level = LogMessage.Level.WARN)
   void errorClosingFile(Throwable e);

   @LogMessage(id = 142031, value = "Error retrieving ID part of the file name {}", level = LogMessage.Level.WARN)
   void errorRetrievingID(String fileName, Throwable e);

   @LogMessage(id = 142032, value = "Error reading journal file", level = LogMessage.Level.WARN)
   void errorReadingFile(Throwable e);

   @LogMessage(id = 142033, value = "Error reinitializing file {}", level = LogMessage.Level.WARN)
   void errorReinitializingFile(JournalFile file, Throwable e);

   @LogMessage(id = 144000, value = "Failed to delete file {}", level = LogMessage.Level.ERROR)
   void errorDeletingFile(Object e);

   @LogMessage(id = 144002, value = "Error pushing opened file", level = LogMessage.Level.ERROR)
   void errorPushingFile(Exception e);

   @LogMessage(id = 144003, value = "Error compacting", level = LogMessage.Level.ERROR)
   void errorCompacting(Throwable e);

   @LogMessage(id = 144004, value = "Error scheduling compacting", level = LogMessage.Level.ERROR)
   void errorSchedulingCompacting(Throwable e);

   @LogMessage(id = 144006, value = "IOError code {}, {}", level = LogMessage.Level.ERROR)
   void ioError(int errorCode, String errorMessage);

   @LogMessage(id = 144007, value = "Ignoring journal file {}: file is shorter then minimum header size. This file is being removed.", level = LogMessage.Level.WARN)
   void ignoringShortFile(String fileName);

   @LogMessage(id = 144008, value = "*******************************************************************************************************************************\nFile {}: was moved under attic, please review it and remove it.\n*******************************************************************************************************************************", level = LogMessage.Level.WARN)
   void movingFileToAttic(String fileName);

   @LogMessage(id = 144009, value = "Could not get a file in {} seconds, System will retry the open but you may see increased latency in your system", level = LogMessage.Level.WARN)
   void cantOpenFileTimeout(long timeout);

   @LogMessage(id = 144010, value = "Critical IO Exception happened: {}", level = LogMessage.Level.WARN)
   void criticalIO(String message, Throwable error);

   // same as criticalIO but with the FileName associated (if there's a file available)
   @LogMessage(id = 144011, value = "Critical IO Exception happened: {} on {}", level = LogMessage.Level.WARN)
   void criticalIOFile(String message, String fileName, Throwable error);

   @LogMessage(id = 144012, value = "Journal Record sized at {}, which is too close to the max record Size at {}. Record = {}. Internal broker operations such as redistribution and DLQ may be compromised. Move large headers into the body of messages.", level = LogMessage.Level.WARN)
   void largeHeaderWarning(long recordSize, long maxRecordSize, Object originalData);
}
