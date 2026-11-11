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
package org.apache.activemq.artemis.core.journal.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncoderPersister;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.dataformat.ByteArrayEncoding;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalAddRecord;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalInternalRecord;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashSet;

/**
 * Super class for Journal maintenances such as clean up and Compactor
 */
public abstract class AbstractJournalUpdateTask implements JournalReaderCallback {

   public static final String FILE_COMPACT_CONTROL = "journal-rename-control.ctr";

   protected final JournalImpl journal;

   protected final SequentialFileFactory fileFactory;

   protected JournalFile currentFile;

   protected SequentialFile sequentialFile;

   protected final JournalFilesRepository filesRepository;

   protected long nextOrderingID;

   private ActiveMQBuffer writingChannel;

   private ByteBuffer bufferWrite;

   private final ConcurrentLongHashSet recordsSnapshot;

   protected final List<JournalFile> newDataFiles = new ArrayList<>();


   protected AbstractJournalUpdateTask(final SequentialFileFactory fileFactory,
                                       final JournalImpl journal,
                                       final JournalFilesRepository filesRepository,
                                       final ConcurrentLongHashSet recordsSnapshot,
                                       final long nextOrderingID) {
      super();
      this.journal = journal;
      this.filesRepository = filesRepository;
      this.fileFactory = fileFactory;
      this.nextOrderingID = nextOrderingID;
      this.recordsSnapshot = recordsSnapshot;
   }


   public static SequentialFile writeControlFile(final SequentialFileFactory fileFactory,
                                                 final List<JournalFile> files,
                                                 final List<JournalFile> newFiles,
                                                 final List<Pair<String, String>> renames) throws Exception {

      ActiveMQBuffer filesToRename = ActiveMQBuffers.dynamicBuffer(1);

      // DataFiles first

      if (files == null) {
         filesToRename.writeInt(0);
      } else {
         filesToRename.writeInt(files.size());

         for (JournalFile file : files) {
            filesToRename.writeUTF(file.getFile().getFileName());
         }
      }

      // New Files second

      if (newFiles == null) {
         filesToRename.writeInt(0);
      } else {
         filesToRename.writeInt(newFiles.size());

         for (JournalFile file : newFiles) {
            filesToRename.writeUTF(file.getFile().getFileName());
         }
      }

      // Renames from clean up third
      if (renames == null) {
         filesToRename.writeInt(0);
      } else {
         filesToRename.writeInt(renames.size());
         for (Pair<String, String> rename : renames) {
            filesToRename.writeUTF(rename.getA());
            filesToRename.writeUTF(rename.getB());
         }
      }

      JournalInternalRecord controlRecord = new JournalAddRecord(true, 1, (byte) 0, EncoderPersister.getInstance(), new ByteArrayEncoding(filesToRename.toByteBuffer().array()));

      ActiveMQBuffer renameBuffer = ActiveMQBuffers.dynamicBuffer(filesToRename.writerIndex());

      controlRecord.setFileID(0);

      controlRecord.encode(renameBuffer);

      ByteBuffer writeBuffer = fileFactory.newBuffer(renameBuffer.writerIndex());

      writeBuffer.put(renameBuffer.toByteBuffer().array(), 0, renameBuffer.writerIndex());
      int position = writeBuffer.position();

      writeBuffer.rewind();


      // the capacity here will only be applied to mapped files as they are created with the intended capacity and the control file needs to match the number of files needed
      SequentialFile controlFile = fileFactory.createSequentialFile(AbstractJournalUpdateTask.FILE_COMPACT_CONTROL, position + 1024);
      try {
         controlFile.open(1, false);
         JournalImpl.initFileHeader(fileFactory, controlFile, 0, 0);
         controlFile.writeDirect(writeBuffer, true);
      } finally {
         controlFile.close(false, false);
      }

      return controlFile;
   }

   static SequentialFile readControlFile(final SequentialFileFactory fileFactory,
                                                final List<String> dataFiles,
                                                final List<String> newFiles,
                                                final List<Pair<String, String>> renameFile,
                                                final AtomicReference<ByteBuffer> wholeFileBufferRef) throws Exception {
      SequentialFile controlFile = fileFactory.createSequentialFile(AbstractJournalUpdateTask.FILE_COMPACT_CONTROL);

      if (controlFile.exists()) {
         JournalFile file = new JournalFileImpl(controlFile, 0, JournalImpl.FORMAT_VERSION);

         final ArrayList<RecordInfo> records = new ArrayList<>();

         JournalImpl.readJournalFile(fileFactory, file, new JournalReaderCallbackAbstract() {
            @Override
            public void onReadAddRecord(final RecordInfo info) throws Exception {
               records.add(info);
            }
         }, wholeFileBufferRef, false, null);

         if (records.isEmpty()) {
            // the record is damaged
            controlFile.delete();
            return null;
         } else {
            ActiveMQBuffer input = ActiveMQBuffers.wrappedBuffer(records.get(0).data);

            int numberDataFiles = input.readInt();

            for (int i = 0; i < numberDataFiles; i++) {
               dataFiles.add(input.readUTF());
            }

            int numberNewFiles = input.readInt();

            for (int i = 0; i < numberNewFiles; i++) {
               newFiles.add(input.readUTF());
            }

            int numberRenames = input.readInt();
            for (int i = 0; i < numberRenames; i++) {
               String from = input.readUTF();
               String to = input.readUTF();
               renameFile.add(new Pair<>(from, to));
            }

         }

         return controlFile;
      } else {
         return null;
      }
   }

   public static SequentialFile readControlFile(final SequentialFileFactory fileFactory,
                                                final List<String> dataFiles,
                                                final List<String> newFiles,
                                                final List<Pair<String, String>> renameFile) throws Exception {
      return readControlFile(fileFactory, dataFiles, newFiles, renameFile, null);
   }

   private void flush(boolean releaseWritingBuffer) throws Exception {
      if (writingChannel != null) {
         try {
            if (sequentialFile.isOpen()) {
               try {
                  sequentialFile.position(0);

                  // To Fix the size of the file
                  writingChannel.writerIndex(writingChannel.capacity());

                  final ByteBuffer byteBuffer = bufferWrite;
                  final int readerIndex = writingChannel.readerIndex();
                  byteBuffer.clear().position(readerIndex).limit(readerIndex + writingChannel.readableBytes());
                  sequentialFile.blockingWriteDirect(byteBuffer, true, false);
               } finally {
                  sequentialFile.close(false, false);
                  newDataFiles.add(currentFile);
               }
            }
         } finally {
            if (releaseWritingBuffer) {
               //deterministic release of native resources
               fileFactory.releaseDirectBuffer(bufferWrite);
               writingChannel = null;
               bufferWrite = null;
            }
         }
      }
   }

   /**
    * Write pending output into file
    */
   public void flush() throws Exception {
      flush(true);
   }

   public boolean containsRecord(final long id) {
      return recordsSnapshot.contains(id);
   }

   protected void openFile() throws Exception {
      flush(false);

      currentFile = filesRepository.openFileCMP();

      sequentialFile = currentFile.getFile();

      sequentialFile.open(1, false);

      currentFile = new JournalFileImpl(sequentialFile, nextOrderingID++, JournalImpl.FORMAT_VERSION);

      final int fileSize = journal.getFileSize();
      if (bufferWrite != null && bufferWrite.capacity() < fileSize) {
         fileFactory.releaseDirectBuffer(bufferWrite);
         bufferWrite = null;
         writingChannel = null;
      }
      if (bufferWrite == null) {
         final ByteBuffer bufferWrite = fileFactory.allocateDirectBuffer(fileSize);
         this.bufferWrite = bufferWrite;
         writingChannel = ActiveMQBuffers.wrappedBuffer(bufferWrite);
      } else {
         writingChannel.clear();
         bufferWrite.clear();
      }

      JournalImpl.writeHeader(writingChannel, journal.getUserVersion(), currentFile.getFileID());
   }

   protected void addToRecordsSnaptshot(final long id) {
      recordsSnapshot.add(id);
   }

   protected ActiveMQBuffer getWritingChannel() {
      return writingChannel;
   }

   protected void writeEncoder(final JournalInternalRecord record) throws Exception {
      record.setFileID(currentFile.getRecordID());
      record.encode(getWritingChannel());
   }

   protected void writeEncoder(final JournalInternalRecord record, final int txcounter) throws Exception {
      record.setNumberOfRecords(txcounter);
      writeEncoder(record);
   }



}
