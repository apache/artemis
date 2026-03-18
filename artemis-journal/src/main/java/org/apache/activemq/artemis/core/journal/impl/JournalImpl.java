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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

import io.netty.util.collection.ByteObjectHashMap;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQShutdownException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.io.DummyCallback;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.JournalLoadInformation;
import org.apache.activemq.artemis.core.journal.JournalUpdateCallback;
import org.apache.activemq.artemis.core.journal.LoaderCallback;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.TestableJournal;
import org.apache.activemq.artemis.core.journal.TransactionFailureCallback;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalAddRecord;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalAddRecordTX;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalCompleteRecordTX;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalCompleteRecordTX.TX_RECORD_TYPE;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalDeleteRecord;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalDeleteRecordTX;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalInternalRecord;
import org.apache.activemq.artemis.core.journal.impl.dataformat.JournalRollbackRecordTX;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.journal.ActiveMQJournalBundle;
import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.SimpleFuture;
import org.apache.activemq.artemis.utils.SimpleFutureImpl;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashMap;
import org.apache.activemq.artemis.utils.collections.LongHashSet;
import org.apache.activemq.artemis.utils.collections.SparseArrayLinkedList;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;

import static org.apache.activemq.artemis.core.journal.impl.Reclaimer.scan;

/**
 * A circular log implementation.
 * <p>
 * Look at {@link JournalImpl#load(LoaderCallback)} for the file layout
 */
public class JournalImpl extends JournalBase implements TestableJournal, JournalRecordProvider {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * this is a factor where when you have more than UPDATE_FACTOR updates for every ADD.
    * <p>
    * When this happens we should issue a compacting event.
    * <p>
    * I don't foresee users needing to configure this value. However if this ever happens we would have a system
    * property aligned for this.
    * <p>
    * With that being said, if you needed this, please raise an issue on why you needed to use this, so we may
    * eventually add it to broker.xml when a real use case would determine the configuration exposed in there.
    * <p>
    * To update this value, define a System Property
    * org.apache.activemq.artemis.core.journal.impl.JournalImpl.UPDATE_FACTOR=YOUR VALUE
    * <p>
    * We only calculate this against replaceable updates, on this case for redelivery counts and rescheduled redelivery
    * in artemis server
    */
   public static final double UPDATE_FACTOR;
   private static final String BKP_EXTENSION = "bkp";
   public static final String BKP = "." + BKP_EXTENSION;


   static {
      String UPDATE_FACTOR_STR = System.getProperty(JournalImpl.class.getName() + ".UPDATE_FACTOR");
      double value;
      try {
         if (UPDATE_FACTOR_STR == null) {
            value = 10;
         } else {
            value = Double.parseDouble(UPDATE_FACTOR_STR);
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         value = 100;
      }

      UPDATE_FACTOR = value;
   }

   public static final int FORMAT_VERSION = 2;

   private static final int[] COMPATIBLE_VERSIONS = new int[]{1};


   // The sizes of primitive types

   public static final int MIN_FILE_SIZE = 1024;

   // FileID(Long) + JournalVersion + UserVersion
   public static final int SIZE_HEADER = DataConstants.SIZE_LONG + DataConstants.SIZE_INT + DataConstants.SIZE_INT;

   private static final int BASIC_SIZE = DataConstants.SIZE_BYTE + DataConstants.SIZE_INT + DataConstants.SIZE_INT;

   public static final int SIZE_ADD_RECORD = JournalImpl.BASIC_SIZE + DataConstants.SIZE_LONG +
      DataConstants.SIZE_BYTE +
      DataConstants.SIZE_INT /* + record.length */;

   // Record markers - they must be all unique


   public static final byte EVENT_RECORD = 10;

   public static final byte ADD_RECORD = 11;

   public static final byte UPDATE_RECORD = 12;

   public static final int SIZE_ADD_RECORD_TX = JournalImpl.BASIC_SIZE + DataConstants.SIZE_LONG +
      DataConstants.SIZE_BYTE +
      DataConstants.SIZE_LONG +
      DataConstants.SIZE_INT /* + record.length */;

   public static final byte ADD_RECORD_TX = 13;

   public static final byte UPDATE_RECORD_TX = 14;

   public static final int SIZE_DELETE_RECORD_TX = JournalImpl.BASIC_SIZE + DataConstants.SIZE_LONG +
      DataConstants.SIZE_LONG +
      DataConstants.SIZE_INT /* + record.length */;

   public static final byte DELETE_RECORD_TX = 15;

   public static final int SIZE_DELETE_RECORD = JournalImpl.BASIC_SIZE + DataConstants.SIZE_LONG;

   public static final byte DELETE_RECORD = 16;

   public static final int SIZE_COMPLETE_TRANSACTION_RECORD = JournalImpl.BASIC_SIZE + DataConstants.SIZE_LONG +
      DataConstants.SIZE_INT;

   public static final int SIZE_PREPARE_RECORD = JournalImpl.SIZE_COMPLETE_TRANSACTION_RECORD + DataConstants.SIZE_INT;

   public static final byte PREPARE_RECORD = 17;

   public static final int SIZE_COMMIT_RECORD = JournalImpl.SIZE_COMPLETE_TRANSACTION_RECORD;

   public static final byte COMMIT_RECORD = 18;

   public static final int SIZE_ROLLBACK_RECORD = JournalImpl.BASIC_SIZE + DataConstants.SIZE_LONG;

   public static final byte ROLLBACK_RECORD = 19;

   protected static final byte FILL_CHARACTER = (byte) 'J';



   private volatile boolean autoReclaim = true;

   private final int userVersion;

   private final int minFiles;

   private final float compactPercentage;

   private final int compactMinFiles;

   private final SequentialFileFactory fileFactory;

   private final JournalFilesRepository filesRepository;

   private File journalRetentionFolder;

   private long journalRetentionPeriod = -1;

   private int journalRetentionMaxFiles = -1;

   private final List<JournalFile> historyPendingFiles = Collections.synchronizedList(new LinkedList<>());

   // This is to guarantee only one thread is making a copy of a file
   // the processBackup is pretty much single threaded happening at the compactorExecutor
   // there are a few exceptions like startup, or during a replica-copy-catch-up in a small possibility
   private final Object processBackupLock = new Object();

   @Override
   public boolean isHistory() {
      return journalRetentionFolder != null;
   }

   @Override
   public File getHistoryFolder() {
      return journalRetentionFolder;
   }

   @Override
   public JournalImpl setHistoryFolder(File historyFolder, long maxBytes, long period) throws Exception {

      if (this.state != JournalState.STOPPED) {
         throw new IllegalStateException("State = " + state);
      }
      this.journalRetentionFolder = historyFolder;
      this.journalRetentionFolder.mkdirs();

      this.journalRetentionMaxFiles = (int) (maxBytes / this.fileSize);
      this.journalRetentionPeriod = period;


      try {
         List<String> files = this.fileFactory.listFiles(BKP_EXTENSION);

         for (String name : files) {
            SequentialFile file = fileFactory.createSequentialFile(name);
            JournalFileImpl journalFile;
            try {
               file.open();
               journalFile = readFileHeader(file);
            } finally {
               file.close();
            }
            historyPendingFiles.add(journalFile);
         }


         for (JournalFile file : historyPendingFiles) {
            File[] repeatFiles = historyFolder.listFiles((a, name) -> name.startsWith(getFilePrefix()) && name.endsWith(file.getFileID() + "." + filesRepository.getFileExtension()));

            for (File f : repeatFiles) {
               logger.warn("File {} was partially copied before, removing the file", f);
               f.delete();
            }
         }

         processBackup();
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         fileFactory.onIOError(e, e.getMessage());
      }

      return this;
   }



   // Compacting may replace this structure
   private final ConcurrentLongHashMap<JournalRecord> records = new ConcurrentLongHashMap<>();

   // Compacting may replace this structure
   private final ConcurrentLongHashMap<JournalTransaction> transactions = new ConcurrentLongHashMap<>();

   // This will be set only while the JournalCompactor is being executed
   private volatile JournalCompactor compactor;

   private final AtomicBoolean compactorRunning = new AtomicBoolean();

   private Executor filesExecutor = null;

   private Executor compactorExecutor = null;

   private Executor appendExecutor = null;

   private final ConcurrentHashSet<CountDownLatch> latches = new ConcurrentHashSet<>();

   private final ExecutorFactory providedIOThreadPool;
   protected ExecutorFactory ioExecutorFactory;
   private ThreadPoolExecutor threadPool;

   ThreadLocal<GregorianCalendar> calendarThreadLocal = ThreadLocal.withInitial(() -> new GregorianCalendar());

   /**
    * We don't lock the journal during the whole compacting operation. During compacting we only lock it (i) when
    * gathering the initial structure, and (ii) when replicating the structures after finished compacting.
    * <p>
    * However we need to lock it while taking and updating snapshots
    */
   private final ReadWriteLock journalLock = new ReentrantReadWriteLock();
   private final ReadWriteLock compactorLock = new ReentrantReadWriteLock();

   ByteObjectHashMap<Boolean> replaceableRecords;

   /**
    * This will declare a record type as being replaceable on updates. Certain update records only need the last value,
    * and they could be replaceable during compacting.
    */
   @Override
   public void replaceableRecord(byte recordType) {
      if (replaceableRecords == null) {
         replaceableRecords = new ByteObjectHashMap<>();
      }
      replaceableRecords.put(recordType, Boolean.TRUE);
   }

   private volatile JournalFile currentFile;

   private volatile JournalState state = JournalState.STOPPED;

   private volatile int compactCount = 0;

   public float getCompactPercentage() {
      return compactPercentage;
   }

   public int getCompactMinFiles() {
      return compactMinFiles;
   }

   public JournalFilesRepository getFilesRepository() {
      return filesRepository;
   }


   public JournalImpl(final int fileSize,
                      final int minFiles,
                      final int poolSize,
                      final int compactMinFiles,
                      final int compactPercentage,
                      final SequentialFileFactory fileFactory,
                      final String filePrefix,
                      final String fileExtension,
                      final int maxAIO) {
      this(fileSize, minFiles, poolSize, compactMinFiles, compactPercentage, fileFactory, filePrefix, fileExtension, maxAIO, 0);
   }

   public JournalImpl(final int fileSize,
                      final int minFiles,
                      final int poolSize,
                      final int compactMinFiles,
                      final int compactPercentage,
                      final SequentialFileFactory fileFactory,
                      final String filePrefix,
                      final String fileExtension,
                      final int maxAIO,
                      final int userVersion) {
      this(null, fileSize, minFiles, poolSize, compactMinFiles, compactPercentage, 5, fileFactory, filePrefix, fileExtension, maxAIO, userVersion);
   }

   public JournalImpl(final int fileSize,
                      final int minFiles,
                      final int poolSize,
                      final int compactMinFiles,
                      final int compactPercentage,
                      final int journalFileOpenTimeout,
                      final SequentialFileFactory fileFactory,
                      final String filePrefix,
                      final String fileExtension,
                      final int maxAIO,
                      final int userVersion) {
      this(null, fileSize, minFiles, poolSize, compactMinFiles, compactPercentage, journalFileOpenTimeout, fileFactory, filePrefix, fileExtension, maxAIO, userVersion);
   }

   public JournalImpl(final ExecutorFactory ioExecutors,
                      final int fileSize,
                      final int minFiles,
                      final int poolSize,
                      final int compactMinFiles,
                      final int compactPercentage,
                      final SequentialFileFactory fileFactory,
                      final String filePrefix,
                      final String fileExtension,
                      final int maxAIO,
                      final int userVersion) {
      this(ioExecutors, fileSize, minFiles, poolSize, compactMinFiles, compactPercentage, 5, fileFactory, filePrefix, fileExtension, maxAIO, userVersion);
   }


   public JournalImpl(final ExecutorFactory ioExecutors,
                      final int fileSize,
                      final int minFiles,
                      final int poolSize,
                      final int compactMinFiles,
                      final int compactPercentage,
                      final int journalFileOpenTimeout,
                      final SequentialFileFactory fileFactory,
                      final String filePrefix,
                      final String fileExtension,
                      final int maxAIO,
                      final int userVersion) {
      this(ioExecutors, fileSize, minFiles, poolSize, compactMinFiles, compactPercentage, journalFileOpenTimeout, fileFactory, filePrefix, fileExtension, maxAIO, userVersion, (a, b, c) -> logger.warn(a.getMessage(), a), 0);
   }


   public JournalImpl(final ExecutorFactory ioExecutors,
                      final int fileSize,
                      final int minFiles,
                      final int poolSize,
                      final int compactMinFiles,
                      final int compactPercentage,
                      final int journalFileOpenTimeout,
                      final SequentialFileFactory fileFactory,
                      final String filePrefix,
                      final String fileExtension,
                      final int maxAIO,
                      final int userVersion,
                      IOCriticalErrorListener criticalErrorListener,
                      final int maxAtticFiles) {

      super(fileFactory.isSupportsCallbacks(), fileSize);

      fileFactory.setCriticalErrorListener(criticalErrorListener);

      this.providedIOThreadPool = ioExecutors;

      if (fileSize % fileFactory.getAlignment() != 0) {
         throw new IllegalArgumentException("Invalid journal-file-size " + fileSize + ", It should be multiple of " +
                                               fileFactory.getAlignment());
      }
      if (minFiles < 2) {
         throw new IllegalArgumentException("minFiles cannot be less than 2");
      }
      if (compactPercentage < 0 || compactPercentage > 100) {
         throw new IllegalArgumentException("Compact Percentage out of range");
      }

      if (compactPercentage == 0) {
         this.compactPercentage = 0;
      } else {
         this.compactPercentage = compactPercentage / 100f;
      }

      this.compactMinFiles = compactMinFiles;
      this.minFiles = minFiles;

      this.fileFactory = fileFactory;

      filesRepository = new JournalFilesRepository(fileFactory, this, filePrefix, fileExtension, userVersion, maxAIO, fileSize, minFiles, poolSize, journalFileOpenTimeout, maxAtticFiles);

      this.userVersion = userVersion;
   }

   @Override
   public String toString() {
      try {
         return "JournalImpl(state=" + state + ", directory=[" + this.fileFactory.getDirectory().toString() + "], hash=" + super.toString() + ")";
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         return super.toString();
      }
   }

   @Override
   public IOCriticalErrorListener getCriticalErrorListener() {
      return fileFactory.getCriticalErrorListener();
   }

   @Override
   public JournalImpl setCriticalErrorListener(IOCriticalErrorListener criticalErrorListener) {
      fileFactory.setCriticalErrorListener(criticalErrorListener);
      return this;
   }

   @Override
   public ConcurrentLongHashMap<JournalRecord> getRecords() {
      return records;
   }

   @Override
   public JournalFile getCurrentFile() {
      return currentFile;
   }

   @Override
   public JournalCompactor getCompactor() {
      return compactor;
   }

   /**
    * this method is used internally only however tools may use it to maintenance. It won't be part of the interface as
    * the tools should be specific to the implementation
    */
   public List<JournalFile> orderFiles() throws Exception {
      List<String> fileNames = fileFactory.listFiles(filesRepository.getFileExtension());

      List<JournalFile> orderedFiles = new ArrayList<>(fileNames.size());

      for (String fileName : fileNames) {
         SequentialFile file = fileFactory.createSequentialFile(fileName);

         if (file.size() >= SIZE_HEADER) {
            file.open();

            try {
               JournalFileImpl jrnFile = readFileHeader(file);

               orderedFiles.add(jrnFile);
            } finally {
               file.close();
            }
         } else {
            ActiveMQJournalLogger.LOGGER.ignoringShortFile(fileName);
            file.delete();
         }
      }

      // Now order them by ordering id - we can't use the file name for ordering
      // since we can re-use dataFiles

      Collections.sort(orderedFiles, JOURNAL_FILE_COMPARATOR);

      return orderedFiles;
   }

   private static ByteBuffer allocateDirectBufferIfNeeded(final SequentialFileFactory fileFactory,
                                                          final int requiredCapacity,
                                                          final AtomicReference<ByteBuffer> bufferRef) {
      ByteBuffer buffer = bufferRef != null ? bufferRef.get() : null;
      if (buffer != null && buffer.capacity() < requiredCapacity) {
         fileFactory.releaseDirectBuffer(buffer);
         buffer = null;
      }
      if (buffer == null) {
         buffer = fileFactory.allocateDirectBuffer(requiredCapacity);
      } else {
         buffer.clear().limit(requiredCapacity);
      }
      if (bufferRef != null) {
         bufferRef.lazySet(buffer);
      }
      return buffer;
   }

   public static int readJournalFile(final SequentialFileFactory fileFactory,
                              final JournalFile file,
                              final JournalReaderCallback reader,
                              final AtomicReference<ByteBuffer> wholeFileBufferReference) throws Exception {
      return readJournalFile(fileFactory, file, reader, wholeFileBufferReference, false);
   }

   public static int readJournalFile(final SequentialFileFactory fileFactory,
                                     final JournalFile file,
                                     final JournalReaderCallback reader,
                                     final AtomicReference<ByteBuffer> wholeFileBufferReference,
                                     boolean reclaimed) throws Exception {
      return readJournalFile(fileFactory, file, reader, wholeFileBufferReference, reclaimed, null);
   }

   public static int readJournalFile(final SequentialFileFactory fileFactory,
                              final JournalFile file,
                              final JournalReaderCallback reader,
                              final AtomicReference<ByteBuffer> wholeFileBufferReference,
                              boolean reclaimed, ByteObjectHashMap<Boolean> replaceableRecords) throws Exception {
      file.getFile().open(1, false);
      ByteBuffer wholeFileBuffer = null;
      try {
         final int filesize = (int) file.getFile().size();

         if (filesize < JournalImpl.SIZE_HEADER) {
            // the file is damaged or the system crash before it was able to write
            return -1;
         }
         wholeFileBuffer = allocateDirectBufferIfNeeded(fileFactory, filesize, wholeFileBufferReference);

         final int journalFileSize = file.getFile().read(wholeFileBuffer);

         if (journalFileSize != filesize) {
            throw new RuntimeException("Invalid read! The system couldn't read the entire file into memory");
         }

         // First long is the ordering timestamp, we just jump its position
         wholeFileBuffer.position(JournalImpl.SIZE_HEADER);

         int lastDataPos = JournalImpl.SIZE_HEADER;

         while (wholeFileBuffer.hasRemaining()) {
            final int pos = wholeFileBuffer.position();

            byte recordType = wholeFileBuffer.get();

            if (recordType < JournalImpl.EVENT_RECORD || recordType > JournalImpl.ROLLBACK_RECORD) {
               // I - We scan for any valid record on the file. If a hole
               // happened on the middle of the file we keep looking until all
               // the possibilities are gone
               continue;
            }

            if (JournalImpl.isInvalidSize(journalFileSize, wholeFileBuffer.position(), DataConstants.SIZE_INT)) {
               reader.markAsDataFile(file);

               wholeFileBuffer.position(pos + 1);
               // II - Ignore this record, let's keep looking
               continue;
            }

            // III - Every record has the file-id.
            // This is what supports us from not re-filling the whole file
            int readFileId = wholeFileBuffer.getInt();

            // This record is from a previous file-usage. The file was
            // reused and we need to ignore this record
            if (readFileId != file.getRecordID() && !reclaimed) {
               wholeFileBuffer.position(pos + 1);
               continue;
            }

            short compactCount = 0;

            if (file.getJournalVersion() >= 2) {
               if (JournalImpl.isInvalidSize(journalFileSize, wholeFileBuffer.position(), DataConstants.SIZE_BYTE)) {
                  reader.markAsDataFile(file);

                  wholeFileBuffer.position(pos + 1);
                  continue;
               }

               compactCount = wholeFileBuffer.get();
            }

            long transactionID = 0;

            if (JournalImpl.isTransaction(recordType)) {
               if (JournalImpl.isInvalidSize(journalFileSize, wholeFileBuffer.position(), DataConstants.SIZE_LONG)) {
                  wholeFileBuffer.position(pos + 1);
                  reader.markAsDataFile(file);
                  continue;
               }

               transactionID = wholeFileBuffer.getLong();
            }

            long recordID = 0;

            // If prepare or commit
            if (!JournalImpl.isCompleteTransaction(recordType)) {
               if (JournalImpl.isInvalidSize(journalFileSize, wholeFileBuffer.position(), DataConstants.SIZE_LONG)) {
                  wholeFileBuffer.position(pos + 1);
                  reader.markAsDataFile(file);
                  continue;
               }

               recordID = wholeFileBuffer.getLong();
            }

            // We use the size of the record to validate the health of the
            // record.
            // (V) We verify the size of the record

            // The variable record portion used on Updates and Appends
            int variableSize = 0;

            // Used to hold extra data on transaction prepares
            int preparedTransactionExtraDataSize = 0;

            byte userRecordType = 0;

            byte[] record = null;

            if (JournalImpl.isContainsBody(recordType)) {
               if (JournalImpl.isInvalidSize(journalFileSize, wholeFileBuffer.position(), DataConstants.SIZE_INT)) {
                  wholeFileBuffer.position(pos + 1);
                  reader.markAsDataFile(file);
                  continue;
               }

               variableSize = wholeFileBuffer.getInt();

               if (recordType != JournalImpl.DELETE_RECORD_TX) {
                  if (JournalImpl.isInvalidSize(journalFileSize, wholeFileBuffer.position(), 1)) {
                     wholeFileBuffer.position(pos + 1);
                     continue;
                  }

                  userRecordType = wholeFileBuffer.get();
               }

               if (JournalImpl.isInvalidSize(journalFileSize, wholeFileBuffer.position(), variableSize)) {
                  wholeFileBuffer.position(pos + 1);
                  continue;
               }

               record = new byte[variableSize];

               wholeFileBuffer.get(record);
            }

            // Case this is a transaction, this will contain the number of pendingTransactions on a transaction, at the
            // currentFile
            int transactionCheckNumberOfRecords = 0;

            if (recordType == JournalImpl.PREPARE_RECORD || recordType == JournalImpl.COMMIT_RECORD) {
               if (JournalImpl.isInvalidSize(journalFileSize, wholeFileBuffer.position(), DataConstants.SIZE_INT)) {
                  wholeFileBuffer.position(pos + 1);
                  continue;
               }

               transactionCheckNumberOfRecords = wholeFileBuffer.getInt();

               if (recordType == JournalImpl.PREPARE_RECORD) {
                  if (JournalImpl.isInvalidSize(journalFileSize, wholeFileBuffer.position(), DataConstants.SIZE_INT)) {
                     wholeFileBuffer.position(pos + 1);
                     continue;
                  }
                  // Add the variable size required for preparedTransactions
                  preparedTransactionExtraDataSize = wholeFileBuffer.getInt();
               }
               variableSize = 0;
            }

            int recordSize = JournalImpl.getRecordSize(recordType, file.getJournalVersion());

            // VI - this is completing V, We will validate the size at the end
            // of the record,
            // But we avoid buffer overflows by damaged data
            if (JournalImpl.isInvalidSize(journalFileSize, pos, recordSize + variableSize +
               preparedTransactionExtraDataSize)) {
               // Avoid a buffer overflow caused by damaged data... continue
               // scanning for more pendingTransactions...
               if (logger.isTraceEnabled()) {
                  logger.trace("Record at position {} recordType = {} file:{} recordSize: {} variableSize: {} preparedTransactionExtraDataSize: {} is corrupted and it is being ignored (II)",
                               pos, recordType, file.getFile().getFileName(), recordSize, variableSize, preparedTransactionExtraDataSize);
               }
               // If a file has damaged pendingTransactions, we make it a dataFile, and the
               // next reclaiming will fix it
               reader.markAsDataFile(file);
               wholeFileBuffer.position(pos + 1);

               continue;
            }

            int oldPos = wholeFileBuffer.position();

            wholeFileBuffer.position(pos + variableSize +
                                        recordSize +
                                        preparedTransactionExtraDataSize - DataConstants.SIZE_INT);

            int checkSize = wholeFileBuffer.getInt();

            // VII - The checkSize at the end has to match with the size
            // informed at the beginning.
            // This is like testing a hash for the record. (We could replace the
            // checkSize by some sort of calculated hash)
            if (checkSize != variableSize + recordSize + preparedTransactionExtraDataSize) {
               if (logger.isTraceEnabled()) {
                  logger.trace("Record at position {} recordType = {} possible transactionID = {} possible recordID = {} file:{} is corrupted and it is being ignored (III)",
                               pos, recordType, transactionID, recordID, file.getFile().getFileName());
               }

               // If a file has damaged pendingTransactions, we make it a dataFile, and the
               // next reclaiming will fix it
               reader.markAsDataFile(file);

               wholeFileBuffer.position(pos + DataConstants.SIZE_BYTE);

               continue;
            }

            wholeFileBuffer.position(oldPos);

            // At this point everything is checked. So we relax and just load
            // the data now.

            if (logger.isTraceEnabled()) {
               logger.trace("reading {}, userRecordType={}, compactCount={}", recordID, userRecordType, compactCount);
            }

            boolean replaceableUpdate =  replaceableRecords != null ? replaceableRecords.containsKey(userRecordType) : false;

            switch (recordType) {
               case EVENT_RECORD: {
                  reader.onReadEventRecord(new RecordInfo(recordID, userRecordType, record, false, replaceableUpdate, compactCount));
                  break;
               }

               case ADD_RECORD: {
                  reader.onReadAddRecord(new RecordInfo(recordID, userRecordType, record, false, false, compactCount));
                  break;
               }

               case UPDATE_RECORD: {
                  reader.onReadUpdateRecord(new RecordInfo(recordID, userRecordType, record, true, replaceableUpdate, compactCount));
                  break;
               }

               case DELETE_RECORD: {
                  reader.onReadDeleteRecord(recordID);
                  break;
               }

               case ADD_RECORD_TX: {
                  reader.onReadAddRecordTX(transactionID, new RecordInfo(recordID, userRecordType, record, false, false, compactCount));
                  break;
               }

               case UPDATE_RECORD_TX: {
                  reader.onReadUpdateRecordTX(transactionID, new RecordInfo(recordID, userRecordType, record, true, replaceableUpdate, compactCount));
                  break;
               }

               case DELETE_RECORD_TX: {
                  reader.onReadDeleteRecordTX(transactionID, new RecordInfo(recordID, (byte) 0, record, true, false, compactCount));
                  break;
               }

               case PREPARE_RECORD: {

                  byte[] extraData = new byte[preparedTransactionExtraDataSize];

                  wholeFileBuffer.get(extraData);

                  reader.onReadPrepareRecord(transactionID, extraData, transactionCheckNumberOfRecords);

                  break;
               }
               case COMMIT_RECORD: {

                  reader.onReadCommitRecord(transactionID, transactionCheckNumberOfRecords);
                  break;
               }
               case ROLLBACK_RECORD: {
                  reader.onReadRollbackRecord(transactionID);
                  break;
               }
               default: {
                  throw new IllegalStateException("Journal " + file.getFile().getFileName() +
                                                     " is corrupt, invalid record type " +
                                                     recordType);
               }
            }

            checkSize = wholeFileBuffer.getInt();

            // This is a sanity check about the loading code itself.
            // If this checkSize doesn't match, it means the reading method is
            // not doing what it was supposed to do
            if (checkSize != variableSize + recordSize + preparedTransactionExtraDataSize) {
               throw new IllegalStateException("Internal error on loading file. Position doesn't match with checkSize, file = " + file.getFile() +
                                                  ", pos = " +
                                                  pos);
            }

            lastDataPos = wholeFileBuffer.position();

         }
         reader.done();
         return lastDataPos;
      } catch (Throwable e) {
         ActiveMQJournalLogger.LOGGER.errorReadingFile(e);
         throw new Exception(e.getMessage(), e);
      } finally {
         if (wholeFileBufferReference == null && wholeFileBuffer != null) {
            fileFactory.releaseDirectBuffer(wholeFileBuffer);
         }
         try {
            file.getFile().close(false, false);
         } catch (Throwable ignored) {
         }
      }
   }

   /**
    * this method is used internally only however tools may use it to maintenance.
    */
   public static int readJournalFile(final SequentialFileFactory fileFactory,
                                     final JournalFile file,
                                     final JournalReaderCallback reader) throws Exception {
      return readJournalFile(fileFactory, file, reader, null, false, null);
   }

   // Journal implementation
   // ----------------------------------------------------------------

   @Override
   public void appendAddRecord(final long id,
                               final byte recordType,
                               final Persister persister,
                               final Object record,
                               final boolean sync,
                               final IOCompletion callback) throws Exception {
      checkJournalIsLoaded();

      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendAddRecord::id={}, userRecordType={}, record = {}", id, recordType, record);
      }

      final JournalInternalRecord addRecord = new JournalAddRecord(true, id, recordType, persister, record);
      final int addRecordEncodeSize = addRecord.getEncodeSize();

      checkRecordSize(addRecordEncodeSize, record);

      lineUpContext(callback);

      final SimpleFuture<Boolean> result = newSyncAndCallbackResult(sync, callback);
      appendExecutor.execute(() -> {
         journalLock.readLock().lock();
         try {
            JournalFile usedFile = appendRecord(addRecord, false, sync, null, callback);
            records.put(id, new JournalRecord(usedFile, addRecordEncodeSize));

            if (logger.isTraceEnabled()) {
               logger.trace("appendAddRecord::id={}, userRecordType={}, record = {}, usedFile = {}",
                            id, recordType, record, usedFile);
            }
            result.set(true);
         } catch (ActiveMQShutdownException e) {
            result.fail(e);
            logger.error("Exception during appendAddRecord:", e);
         } catch (Throwable e) {
            result.fail(e);
            setErrorCondition(callback, null, e);
            logger.error("Exception during appendAddRecord:", e);
         } finally {
            journalLock.readLock().unlock();
         }
      });

      result.get();
   }


   @Override
   public void appendAddEvent(final long id,
                              final byte recordType,
                              final Persister persister,
                              final Object record,
                              final boolean sync,
                              final IOCompletion callback) throws Exception {
      checkJournalIsLoaded();

      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendAddEvent::id={}, userRecordType={}, record = {}", id, recordType, record);
      }

      final JournalInternalRecord addRecord = new JournalAddRecord(JournalImpl.EVENT_RECORD, id, recordType, persister, record);

      checkRecordSize(addRecord.getEncodeSize(), record);

      lineUpContext(callback);

      final SimpleFuture<Boolean> result = newSyncAndCallbackResult(sync, callback);
      appendExecutor.execute(() -> {
         journalLock.readLock().lock();
         try {
            JournalFile usedFile = appendRecord(addRecord, false, sync, null, callback);

            if (logger.isTraceEnabled()) {
               logger.trace("appendAddEvent:id={}, userRecordType={}, record = {}, usedFile = {}",
                            id, recordType, record, usedFile);
            }
            result.set(true);
         } catch (ActiveMQShutdownException e) {
            result.fail(e);
            logger.error("Exception during appendAddEvent:", e);
         } catch (Throwable e) {
            result.fail(e);
            setErrorCondition(callback, null, e);
            logger.error("Exception during appendAddEvent:", e);
         } finally {
            journalLock.readLock().unlock();
         }
      });

      result.get();
   }

   private void checkRecordSize(int addRecordEncodeSize, Object record) throws ActiveMQIOErrorException {
      if (addRecordEncodeSize > getWarningRecordSize()) {
         long maxRecordSize = getMaxRecordSize();
         ActiveMQJournalLogger.LOGGER.largeHeaderWarning(addRecordEncodeSize, maxRecordSize, record);

         if (addRecordEncodeSize > maxRecordSize) {
            //The record size should be larger than max record size only on the large messages case.
            throw ActiveMQJournalBundle.BUNDLE.recordLargerThanStoreMax(addRecordEncodeSize, maxRecordSize);
         }
      }
   }

   @Override
   public void appendUpdateRecord(final long id,
                                  final byte recordType,
                                  final Persister persister,
                                  final Object record,
                                  final boolean sync,
                                  final IOCompletion callback) throws Exception {
      checkJournalIsLoaded();

      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendUpdateRecord::id={}, userRecordType={}", id, recordType);
      }

      final SimpleFuture<Boolean> onFoundAddInfo;

      if (!sync && (callback == null || callback == DummyCallback.getInstance())) {
         onFoundAddInfo = null;
      } else {
         onFoundAddInfo = new SimpleFutureImpl<>();
      }

      lineUpContext(callback);

      if (onFoundAddInfo == null) {
         internalAppendUpdateRecord(id, recordType, persister, record, false, false, null, callback);
      } else {
         internalAppendUpdateRecord(id, recordType, persister, record, sync, false, (t, v) -> onFoundAddInfo.set(v), callback);
      }

      if (onFoundAddInfo != null && !onFoundAddInfo.get()) {
         throw new IllegalStateException("Cannot find add info " + id);
      }
   }


   @Override
   public void tryAppendUpdateRecord(final long id,
                                     final byte recordType,
                                     final Persister persister,
                                     final Object record,
                                     final boolean sync,
                                     final boolean replaceableUpdate,
                                     JournalUpdateCallback updateCallback,
                                     final IOCompletion callback) throws Exception {
      checkJournalIsLoaded();
      lineUpContext(callback);

      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendUpdateRecord::id={}, userRecordType={}", id, recordType);
      }

      internalAppendUpdateRecord(id, recordType, persister, record, sync, replaceableUpdate, updateCallback, callback);
   }


   private void internalAppendUpdateRecord(long id,
                                           byte recordType,
                                           Persister persister,
                                           Object record,
                                           boolean sync,
                                           boolean replaceableUpdate,
                                           JournalUpdateCallback updateCallback,
                                           IOCompletion callback) throws InterruptedException, java.util.concurrent.ExecutionException {
      appendExecutor.execute(() -> {
         journalLock.readLock().lock();
         try {
            // compactor will never change while readLock is acquired.
            // but we are doing this since compactor is volatile, to avoid some extra work from JIT
            JournalCompactor compactor = JournalImpl.this.compactor;
            JournalRecord jrnRecord = records.get(id);
            if (jrnRecord == null) {
               if (compactor == null || (!compactor.containsRecord(id))) {
                  if (updateCallback != null) {
                     updateCallback.onUpdate(id, false);
                  }
                  if (logger.isDebugEnabled()) {
                     logger.debug("Record {} had not been found", id);
                  }

                  if (callback != null) {
                     callback.done();
                  }
                  return;
               }
            }
            JournalInternalRecord updateRecord = new JournalAddRecord(false, id, recordType, persister, record);
            JournalFile usedFile = appendRecord(updateRecord, false, sync, null, callback);

            if (logger.isTraceEnabled()) {
               logger.trace("appendUpdateRecord::id={}, userRecordType={}, usedFile = {}", id, recordType, usedFile);
            }

            // record==null here could only mean there is a compactor
            // computing the delete should be done after compacting is done
            if (jrnRecord == null) {
               if (compactor != null) {
                  compactor.addCommandUpdate(id, usedFile, updateRecord.getEncodeSize(), replaceableUpdate);
               }
            } else {
               jrnRecord.addUpdateFile(usedFile, updateRecord.getEncodeSize(), replaceableUpdate);
            }

            if (updateCallback != null) {
               updateCallback.onUpdate(id, true);
            }
         } catch (ActiveMQShutdownException e) {
            if (updateCallback != null) {
               updateCallback.onUpdate(id, false);
            }
            logger.error("Exception during appendUpdateRecord:", e);
         } catch (Throwable e) {
            if (updateCallback != null) {
               updateCallback.onUpdate(id, false);
            }
            setErrorCondition(callback, null, e);
            logger.error("Exception during appendUpdateRecord:", e);
         } finally {
            journalLock.readLock().unlock();
         }
      });
   }

   @Override
   public void appendDeleteRecord(final long id, final boolean sync, final IOCompletion callback) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendDeleteRecord::id={}", id);
      }

      checkJournalIsLoaded();

      final SimpleFuture<Boolean> onFoundAddInfo;

      if (!sync && (callback == null || callback == DummyCallback.getInstance())) {
         onFoundAddInfo = null;
      } else {
         onFoundAddInfo = new SimpleFutureImpl<>();
      }

      lineUpContext(callback);

      if (onFoundAddInfo == null) {
         internalAppendDeleteRecord(id, false, null, callback);
      } else {
         internalAppendDeleteRecord(id, sync, (record, result) -> onFoundAddInfo.set(result), callback);
      }

      if (onFoundAddInfo != null && !onFoundAddInfo.get()) {
         throw new IllegalStateException("Cannot find add info " + id);
      }
   }


   @Override
   public void tryAppendDeleteRecord(final long id, final boolean sync, final JournalUpdateCallback updateCallback, final IOCompletion callback) throws Exception {

      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendDeleteRecord::id={}", id);
      }


      checkJournalIsLoaded();
      lineUpContext(callback);
      internalAppendDeleteRecord(id, sync, updateCallback, callback);
   }

   private void internalAppendDeleteRecord(long id,
                                           boolean sync,
                                           JournalUpdateCallback updateCallback,
                                           IOCompletion callback)  {

      appendExecutor.execute(() -> {
         journalLock.readLock().lock();
         try {
            // compactor will never change while readLock is acquired.
            // but we are doing this since compactor is volatile, to avoid some extra work from JIT
            JournalCompactor compactor = JournalImpl.this.compactor;
            JournalRecord record = null;
            if (compactor == null) {
               record = records.remove(id);
               if (record == null) {
                  if (updateCallback != null) {
                     updateCallback.onUpdate(id, false);
                  }

                  if (callback != null) {
                     callback.done();
                  }
                  return;
               }
            } else {
               if (!records.containsKey(id) && !compactor.containsRecord(id)) {
                  if (updateCallback != null) {
                     updateCallback.onUpdate(id, false);
                  }
                  if (callback != null) {
                     callback.done();
                  }
                  return;
               }
            }

            JournalInternalRecord deleteRecord = new JournalDeleteRecord(id);
            JournalFile usedFile = appendRecord(deleteRecord, false, sync, null, callback);

            if (logger.isTraceEnabled()) {
               logger.trace("appendDeleteRecord::id={}, usedFile = {}", id, usedFile);
            }

            // record==null here could only mean there is a compactor
            // computing the delete should be done after compacting is done
            if (record == null) {
               // JournalImplTestUni::testDoubleDelete was written to validate this condition:
               compactor.addCommandDelete(id, usedFile);
            } else {
               record.delete(usedFile);
            }
            if (updateCallback != null) {
               updateCallback.onUpdate(id, true);
            }
         } catch (ActiveMQShutdownException e) {
            if (updateCallback != null) {
               updateCallback.onUpdate(id, false);
            }
            logger.error("Exception during appendDeleteRecord:", e);
         } catch (Throwable e) {
            if (updateCallback != null) {
               updateCallback.onUpdate(id, false);
            }
            logger.error("Exception during appendDeleteRecord:", e);
            setErrorCondition(callback, null, e);
         } finally {
            journalLock.readLock().unlock();
         }
      });
   }

   private static SimpleFuture newSyncAndCallbackResult(boolean sync, IOCompletion callback) {
      return (sync && callback == null) ? new SimpleFutureImpl<>() : SimpleFuture.dumb();
   }

   @Override
   public void appendAddRecordTransactional(final long txID,
                                            final long id,
                                            final byte recordType,
                                            final Persister persister,
                                            final Object record) throws Exception {
      checkJournalIsLoaded();
      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendAddRecordTransactional:txID={}, id={}, userRecordType={}, record = {}",
                      txID, id, recordType, record);
      }

      JournalInternalRecord addRecord = new JournalAddRecordTX(true, txID, id, recordType, persister, record);
      int encodeSize = addRecord.getEncodeSize();

      checkRecordSize(encodeSize, record);

      appendExecutor.execute(() -> {
         journalLock.readLock().lock();

         final JournalTransaction tx = getTransactionInfo(txID);

         try {
            if (tx != null) {
               tx.checkErrorCondition();
            }
            // we need to calculate the encodeSize here, as it may use caches that are eliminated once the record is written
            JournalFile usedFile = appendRecord(addRecord, false, false, tx, null);

            if (logger.isTraceEnabled()) {
               logger.trace("appendAddRecordTransactional:txID={},id={}, userRecordType={}, record = {}, usedFile = {}",
                            txID, id, recordType, record, usedFile);
            }

            tx.addPositive(usedFile, id, encodeSize, false);
         } catch (Throwable e) {
            logger.error("Exception during appendAddRecordTransactional:", e);
            setErrorCondition(null, tx, e);
         } finally {
            journalLock.readLock().unlock();
         }
      });
   }
   private void checkJournalIsLoaded() throws Exception {
      if (state != JournalState.LOADED && state != JournalState.SYNCING) {
         throw new ActiveMQShutdownException("Journal must be in state=" + JournalState.LOADED + ", was [" + state + "]");
      }
   }

   private void setJournalState(JournalState newState) {
      state = newState;
   }

   @Override
   public void appendUpdateRecordTransactional(final long txID,
                                               final long id,
                                               final byte recordType,
                                               final Persister persister,
                                               final Object record) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendUpdateRecordTransactional::txID={}, id={}, userRecordType={}, record = {}",
                      txID, id, recordType, record);
      }

      checkJournalIsLoaded();


      appendExecutor.execute(() -> {
         journalLock.readLock().lock();

         final JournalTransaction tx = getTransactionInfo(txID);

         try {
            tx.checkErrorCondition();

            JournalInternalRecord updateRecordTX = new JournalAddRecordTX(false, txID, id, recordType, persister, record);
            JournalFile usedFile = appendRecord(updateRecordTX, false, false, tx, null);

            if (logger.isTraceEnabled()) {
               logger.trace("appendUpdateRecordTransactional::txID={}, id={}, userRecordType={}, record = {}, usedFile = {}",
                            txID, id, recordType, record, usedFile);
            }

            tx.addPositive(usedFile, id, updateRecordTX.getEncodeSize(), false);
         } catch (Throwable e) {
            logger.error("Exception during appendUpdateRecordTransactional:", e);
            setErrorCondition(null, tx, e);
         } finally {
            journalLock.readLock().unlock();
         }
      });
   }

   @Override
   public void appendDeleteRecordTransactional(final long txID,
                                               final long id,
                                               final EncodingSupport record) throws Exception {

      logger.trace("scheduling appendDeleteRecordTransactional::txID={}, id={}", txID, id);


      checkJournalIsLoaded();

      appendExecutor.execute(() -> {
         journalLock.readLock().lock();

         final JournalTransaction tx = getTransactionInfo(txID);

         try {
            if (tx != null) {
               tx.checkErrorCondition();
            }

            JournalInternalRecord deleteRecordTX = new JournalDeleteRecordTX(txID, id, record);
            JournalFile usedFile = appendRecord(deleteRecordTX, false, false, tx, null);

            if (logger.isTraceEnabled()) {
               logger.trace("appendDeleteRecordTransactional::txID={}, id={}, usedFile = {}", txID, id, usedFile);
            }

            tx.addNegative(usedFile, id);
         } catch (Throwable e) {
            logger.error("Exception during appendDeleteRecordTransactional:", e);
            setErrorCondition(null, tx, e);
         } finally {
            journalLock.readLock().unlock();
         }
      });
   }

   /**
    * <p>If the system crashed after a prepare was called, it should store information that is required to bring the
    * transaction
    * back to a state it could be committed. </p>
    * <p> transactionData allows you to store any other supporting user-data related to the transaction</p>
    * <p> This method also uses the same logic applied on {@link JournalImpl#appendCommitRecord(long, boolean)}
    *
    * @param transactionData extra user data for the prepare
    */
   @Override
   public void appendPrepareRecord(final long txID,
                                   final EncodingSupport transactionData,
                                   final boolean sync,
                                   final IOCompletion callback) throws Exception {

      checkJournalIsLoaded();
      lineUpContext(callback);

      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendPrepareRecord::txID={}", txID);
      }

      final SimpleFuture<JournalTransaction> result = newSyncAndCallbackResult(sync, callback);

      appendExecutor.execute(() -> {
         journalLock.readLock().lock();


         final JournalTransaction tx = getTransactionInfo(txID);

         try {
            tx.checkErrorCondition();
            JournalInternalRecord prepareRecord = new JournalCompleteRecordTX(TX_RECORD_TYPE.PREPARE, txID, transactionData);
            JournalFile usedFile = appendRecord(prepareRecord, true, sync, tx, callback);

            if (logger.isTraceEnabled()) {
               logger.trace("appendPrepareRecord::txID={}, usedFile = {}", txID, usedFile);
            }

            tx.prepare(usedFile);
         } catch (ActiveMQShutdownException e) {
            result.fail(e);
            logger.error("Exception during appendPrepareRecord:", e);
         } catch (Throwable e) {
            result.fail(e);
            logger.error("Exception during appendPrepareRecord:", e);
            setErrorCondition(callback, tx, e);
         } finally {
            journalLock.readLock().unlock();
            result.set(tx);
         }
      });

      JournalTransaction tx = result.get();
      if (tx != null) {
         tx.checkErrorCondition();
      }
   }

   @Override
   public void lineUpContext(IOCompletion callback) {
      if (callback != null) {
         callback.storeLineUp();
      }
   }

   private void setErrorCondition(IOCallback ioCallback, JournalTransaction jt, Throwable t) {
      if (jt != null) {
         jt.onError(ActiveMQExceptionType.IO_ERROR.getCode(), t.getMessage());
      }

      if (ioCallback != null) {
         ioCallback.onError(ActiveMQExceptionType.IO_ERROR.getCode(), t.getMessage());
      }
   }

   /**
    * Regarding the number of operations in a given file see {@link JournalCompleteRecordTX}.
    */
   @Override
   public void appendCommitRecord(final long txID,
                                  final boolean sync,
                                  final IOCompletion callback,
                                  final boolean lineUpContext) throws Exception {
      checkJournalIsLoaded();

      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendCommitRecord::txID={}", txID);
      }

      JournalTransaction txcheck = transactions.get(txID);
      if (txcheck != null) {
         txcheck.checkErrorCondition();
      }

      if (lineUpContext) {
         lineUpContext(callback);
      }

      final SimpleFuture<JournalTransaction> result = newSyncAndCallbackResult(sync, callback);

      appendExecutor.execute(() -> {
         journalLock.readLock().lock();
         // cannot remove otherwise compact may get lost
         final JournalTransaction tx = transactions.remove(txID);

         try {
            if (tx == null) {
               throw new IllegalStateException("Cannot find tx with id " + txID);
            }

            JournalInternalRecord commitRecord = new JournalCompleteRecordTX(TX_RECORD_TYPE.COMMIT, txID, null);
            JournalFile usedFile = appendRecord(commitRecord, true, sync, tx, callback);

            if (logger.isTraceEnabled()) {
               logger.trace("appendCommitRecord::txID={}, usedFile = {}", txID, usedFile);
            }

            tx.commit(usedFile);
         } catch (ActiveMQShutdownException e) {
            result.fail(e);
            logger.error("Exception during appendCommitRecord:", e);
         } catch (Throwable e) {
            result.fail(e);
            logger.error("Exception during appendCommitRecord:", e);
            setErrorCondition(callback, tx, e);
         } finally {
            journalLock.readLock().unlock();
            result.set(tx);
         }
      });

      JournalTransaction tx = result.get();
      if (tx != null) {
         tx.checkErrorCondition();
      }
   }

   @Override
   public void appendRollbackRecord(final long txID, final boolean sync, final IOCompletion callback) throws Exception {
      checkJournalIsLoaded();
      lineUpContext(callback);

      if (logger.isTraceEnabled()) {
         logger.trace("scheduling appendRollbackRecord::txID={}", txID);
      }

      final SimpleFuture<JournalTransaction> result = newSyncAndCallbackResult(sync, callback);
      appendExecutor.execute(() -> {
         journalLock.readLock().lock();

         final JournalTransaction tx = transactions.remove(txID);
         try {
            if (logger.isTraceEnabled()) {
               logger.trace("appendRollbackRecord::txID={}", txID);
            }

            if (tx == null) {
               throw new IllegalStateException("Cannot find tx with id " + txID);
            }


            JournalInternalRecord rollbackRecord = new JournalRollbackRecordTX(txID);
            JournalFile usedFile = appendRecord(rollbackRecord, false, sync, tx, callback);

            tx.rollback(usedFile);
         } catch (ActiveMQShutdownException e) {
            result.fail(e);
            logger.error("Exception during appendRollbackRecord:", e);
         } catch (Throwable e) {
            result.fail(e);
            logger.error("Exception during appendRollbackRecord:", e);
            setErrorCondition(callback, tx, e);
         }  finally {
            journalLock.readLock().unlock();
            result.set(tx);
         }
      });

      JournalTransaction tx = result.get();
      if (tx != null) {
         tx.checkErrorCondition();
      }
   }

   @Override
   public int getAlignment() throws Exception {
      return fileFactory.getAlignment();
   }

   private static final class DummyLoader implements LoaderCallback {

      static final LoaderCallback INSTANCE = new DummyLoader();

      @Override
      public void failedTransaction(final long transactionID,
                                    final List<RecordInfo> records,
                                    final List<RecordInfo> recordsToDelete) {
      }

      @Override
      public void updateRecord(final RecordInfo info) {
      }

      @Override
      public void deleteRecord(final long id) {
      }

      @Override
      public void addRecord(final RecordInfo info) {
      }

      @Override
      public void addPreparedTransaction(final PreparedTransactionInfo preparedTransaction) {
      }
   }

   @Override
   public synchronized JournalLoadInformation loadInternalOnly() throws Exception {
      return load(DummyLoader.INSTANCE, true, null);
   }

   @Override
   public synchronized JournalLoadInformation loadSyncOnly(JournalState syncState) throws Exception {
      assert syncState == JournalState.SYNCING || syncState == JournalState.SYNCING_UP_TO_DATE;
      return load(DummyLoader.INSTANCE, true, syncState);
   }

   @Override
   public JournalLoadInformation load(List<RecordInfo> committedRecords,
                                      List<PreparedTransactionInfo> preparedTransactions,
                                      TransactionFailureCallback transactionFailure,
                                      boolean fixBadTx) throws Exception {
      // suboptimal method: it would perform an additional copy!
      // Implementors should override this to provide their optimized version
      final SparseArrayLinkedList<RecordInfo> records = new SparseArrayLinkedList<>();
      final JournalLoadInformation info = load(records, preparedTransactions, transactionFailure, fixBadTx);
      if (committedRecords instanceof ArrayList list) {
         final long survivedRecordsCount = records.size();
         if (survivedRecordsCount <= Integer.MAX_VALUE) {
            list.ensureCapacity((int) survivedRecordsCount);
         }
      }
      records.clear(committedRecords::add);
      return info;
   }

   /**
    * @see JournalImpl#load(LoaderCallback)
    */
   @Override
   public synchronized JournalLoadInformation load(final SparseArrayLinkedList<RecordInfo> committedRecords,
                                                   final List<PreparedTransactionInfo> preparedTransactions,
                                                   final TransactionFailureCallback failureCallback,
                                                   final boolean fixBadTX) throws Exception {
      final LongHashSet recordsToDelete = new LongHashSet(1024);
      final Predicate<RecordInfo> toDeleteFilter = recordInfo -> recordsToDelete.contains(recordInfo.id);

      final int DELETE_FLUSH = 20000;

      JournalLoadInformation info = load(new LoaderCallback() {
         Runtime runtime = Runtime.getRuntime();

         private void checkDeleteSize() {
            // Flush deletes only if memory is critical
            if (recordsToDelete.size() > DELETE_FLUSH && runtime.freeMemory() < runtime.maxMemory() * 0.2) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Flushing deletes during loading, deleteCount = {}", recordsToDelete.size());
               }
               // Clean up when the list is too large, or it won't be possible to load large sets of files
               // Done as part of JBMESSAGING-1678
               final long removed = committedRecords.remove(toDeleteFilter);
               if (logger.isDebugEnabled()) {
                  logger.debug("Removed records during loading = {}", removed);
               }
               recordsToDelete.clear();

               logger.debug("flush delete done");
            }
         }

         @Override
         public void addPreparedTransaction(final PreparedTransactionInfo preparedTransaction) {
            preparedTransactions.add(preparedTransaction);
            checkDeleteSize();
         }

         @Override
         public void addRecord(final RecordInfo info) {
            committedRecords.add(info);
            checkDeleteSize();
         }

         @Override
         public void updateRecord(final RecordInfo info) {
            committedRecords.add(info);
            checkDeleteSize();
         }

         @Override
         public void deleteRecord(final long id) {
            recordsToDelete.add(id);
            checkDeleteSize();
         }

         @Override
         public void failedTransaction(final long transactionID,
                                       final List<RecordInfo> records,
                                       final List<RecordInfo> recordsToDelete) {
            if (failureCallback != null) {
               failureCallback.failedTransaction(transactionID, records, recordsToDelete);
            }
         }
      }, fixBadTX, null);

      if (!recordsToDelete.isEmpty()) {
         committedRecords.remove(toDeleteFilter);
      }
      return info;
   }

   @Override
   public void scheduleCompactAndBlock(int timeout) throws Exception {
      final AtomicInteger errors = new AtomicInteger(0);

      final CountDownLatch latch = newLatch(1);

      compactorRunning.set(true);

      // We can't use the executor for the compacting... or we would dead lock because of file open and creation
      // operations (that will use the executor)
      compactorExecutor.execute(() -> {

         try {
            JournalImpl.this.compact();
         } catch (Throwable e) {
            errors.incrementAndGet();
            ActiveMQJournalLogger.LOGGER.errorCompacting(e);
         } finally {
            latch.countDown();
         }
      });

      try {

         awaitLatch(latch, timeout);

         if (errors.get() > 0) {
            throw new RuntimeException("Error during compact, look at the logs");
         }
      } finally {
         compactorRunning.set(false);
      }
   }

   /**
    * Note: This method can't be called from the main executor, as it will invoke other methods depending on it.
    * <p>
    * Note: only synchronized methods on journal are methods responsible for the life-cycle such as stop, start records
    * will still come as this is being executed
    */
   public synchronized void compact() {

      if (compactor != null) {
         throw new IllegalStateException("There is pending compacting operation");
      }

      if (logger.isDebugEnabled()) {
         ++compactCount;  // Member used only for logging state when debug enabled
         logger.debug("JournalImpl::compact {} for its {} time", JournalImpl.this, compactCount);
      }

      compactorLock.writeLock().lock();
      try {
         List<JournalFile> dataFilesToProcess;

         boolean previousReclaimValue = isAutoReclaim();

         try {
            logger.debug("Starting compacting operation on journal {}", this);

            onCompactStart();

            dataFilesToProcess = getDataListToCompact();

            if (dataFilesToProcess == null) {
               return;
            }

            Collections.sort(dataFilesToProcess, JOURNAL_FILE_COMPARATOR);

            // This is where most of the work is done, taking most of the time of the compacting routine.
            // Notice there are no locks while this is being done.

            // Read the files, and use the JournalCompactor class to create the new outputFiles, and the new collections as
            // well
            // this AtomicReference is not used for thread-safety, but just as a reference
            final AtomicReference<ByteBuffer> wholeFileBufferRef = dataFilesToProcess.isEmpty() ? null : new AtomicReference<>();
            try {
               for (final JournalFile file : dataFilesToProcess) {
                  try {
                     JournalImpl.readJournalFile(fileFactory, file, compactor, wholeFileBufferRef, false, this.replaceableRecords);
                  } catch (Throwable e) {
                     ActiveMQJournalLogger.LOGGER.compactReadError(file);
                     throw new Exception("Error on reading compacting for " + file, e);
                  }
               }
            } finally {
               ByteBuffer wholeFileBuffer;
               if (wholeFileBufferRef != null && (wholeFileBuffer = wholeFileBufferRef.get()) != null) {
                  fileFactory.releaseDirectBuffer(wholeFileBuffer);
               }
            }

            compactor.flushUpdates();
            compactor.flush();

            // pointcut for tests
            // We need to test concurrent updates on the journal, as the compacting is being performed.
            // Usually tests will use this to hold the compacting while other structures are being updated.
            onCompactDone();

            List<JournalFile> newDatafiles = null;

            JournalCompactor localCompactor = compactor;

            SequentialFile controlFile = createControlFile(dataFilesToProcess, compactor.getNewDataFiles(), null);

            journalLock.writeLock().lock();
            try {
               // Need to clear the compactor here, or the replay commands will send commands back (infinite loop)
               compactor = null;

               onCompactLockingTheJournal();

               newDatafiles = localCompactor.getNewDataFiles();

               // Restore newRecords created during compacting
               localCompactor.getNewRecords().forEach((id, newRecord) -> {
                  records.put(id, newRecord);
               });

               // Restore compacted dataFiles
               for (int i = newDatafiles.size() - 1; i >= 0; i--) {
                  JournalFile fileToAdd = newDatafiles.get(i);
                  logger.trace("Adding file {} back as datafile", fileToAdd);
                  filesRepository.addDataFileOnTop(fileToAdd);
               }

               if (logger.isTraceEnabled()) {
                  logger.trace("There are {} datafiles Now", filesRepository.getDataFilesCount());
               }

               // Replay pending commands (including updates, deletes and commits)

               localCompactor.getNewTransactions().forEach((id, newTransaction) -> newTransaction.replaceRecordProvider(this));

               localCompactor.replayPendingCommands();

               // Merge transactions back after compacting.
               // This has to be done after the replay pending commands, as we need to delete commits
               // that happened during the compacting

               localCompactor.getNewTransactions().forEach((id, newTransaction) -> {
                  logger.trace("Merging pending transaction {} after compacting the journal", newTransaction);

                  JournalTransaction liveTransaction = transactions.get(newTransaction.getId());
                  if (liveTransaction != null) {
                     liveTransaction.merge(newTransaction);
                  } else {
                     ActiveMQJournalLogger.LOGGER.compactMergeError(newTransaction.getId());
                  }
               });
            } catch (Throwable e) {
               fileFactory.onIOError(e, e.getMessage());
               return;
            } finally {
               journalLock.writeLock().unlock();
            }

            // At this point the journal is unlocked. We keep renaming files while the journal is already operational
            renameFiles(dataFilesToProcess, newDatafiles);
            deleteControlFile(controlFile);

            logger.debug("Flushing compacting on journal {}", this);

            setAutoReclaim(previousReclaimValue);

            logger.debug("Finished compacting on journal {}", this);

         } catch (Throwable e) {
            fileFactory.onIOError(e, e.getMessage());
         }
      } finally {
         compactorLock.writeLock().unlock();
         logger.debug("JournalImpl::compact finalized");

      }

   }

   /**
    * this private method will return a list of data files that need to be cleaned up. It will get the list, and replace
    * it on the journal structure, while a separate thread would be able to read it, and append to a new list that will
    * be replaced on the journal.
    */
   private List<JournalFile> getDataListToCompact() throws Exception {
      List<JournalFile> dataFilesToProcess = new ArrayList<>(filesRepository.getDataFilesCount());
      // We need to guarantee that the journal is frozen for this short time
      // We don't freeze the journal as we compact, only for the short time where we replace records
      journalLock.writeLock().lock();
      try {
         if (state != JournalState.LOADED) {
            return null;
         }

         onCompactLockingTheJournal();

         setAutoReclaim(false);

         // We need to move to the next file, as we need a clear start for negatives and positives counts
         moveNextFile(false, true);

         // Take the snapshots and replace the structures

         dataFilesToProcess.addAll(filesRepository.getDataFiles());

         filesRepository.clearDataFiles();

         if (dataFilesToProcess.isEmpty()) {
            logger.trace("Finishing compacting, nothing to process");
            return null;
         }

         compactor = new JournalCompactor(fileFactory, this, filesRepository, records.keysLongHashSet(), dataFilesToProcess.get(0).getFileID());

         if (replaceableRecords != null) {
            replaceableRecords.forEach((k, v) -> compactor.replaceableRecord(k));
         }

         transactions.forEach((id, pendingTransaction) -> {
            compactor.addPendingTransaction(id, pendingTransaction.getPositiveArray());
            pendingTransaction.setCompacting();
         });

         // We will calculate the new records during compacting, what will take the position the records will take
         // after compacting
         records.clear();
      } finally {
         journalLock.writeLock().unlock();
      }

      processBackup();
      return dataFilesToProcess;
   }

   /**
    * <p>Load data accordingly to the record layouts</p>
    * <p>Basic record layout:</p>
    * <table border=1>
    * <caption></caption>
    * <tr><td><b>Field Name</b></td><td><b>Size</b></td></tr>
    * <tr><td>RecordType</td><td>Byte (1)</td></tr>
    * <tr><td>FileID</td><td>Integer (4 bytes)</td></tr>
    * <tr><td>Compactor Counter</td><td>1 byte</td></tr>
    * <tr><td>TransactionID <i>(if record is transactional)</i></td><td>Long (8 bytes)</td></tr>
    * <tr><td>RecordID</td><td>Long (8 bytes)</td></tr>
    * <tr><td>BodySize(Add, update and delete)</td><td>Integer (4 bytes)</td></tr>
    * <tr><td>UserDefinedRecordType (If add/update only)</td><td>Byte (1)</td></tr>
    * <tr><td>RecordBody</td><td>Byte Array (size=BodySize)</td></tr>
    * <tr><td>Check Size</td><td>Integer (4 bytes)</td></tr>
    * </table>
    * <p> The check-size is used to validate if the record is valid and complete </p>
    * <p>Commit/Prepare record layout:</p>
    * <table border=1>
    * <caption></caption>
    * <tr><td><b>Field Name</b></td><td><b>Size</b></td></tr>
    * <tr><td>RecordType</td><td>Byte (1)</td></tr>
    * <tr><td>FileID</td><td>Integer (4 bytes)</td></tr>
    * <tr><td>Compactor Counter</td><td>1 byte</td></tr>
    * <tr><td>TransactionID <i>(if record is transactional)</i></td><td>Long (8 bytes)</td></tr>
    * <tr><td>ExtraDataLength (Prepares only)</td><td>Integer (4 bytes)</td></tr>
    * <tr><td>Number Of Files (N)</td><td>Integer (4 bytes)</td></tr>
    * <tr><td>ExtraDataBytes</td><td>Bytes (sized by ExtraDataLength)</td></tr>
    * <tr><td>* FileID(n)</td><td>Integer (4 bytes)</td></tr>
    * <tr><td>* NumberOfElements(n)</td><td>Integer (4 bytes)</td></tr>
    * <tr><td>CheckSize</td><td>Integer (4 bytes)</td></tr>
    * </table>
    * <p> * FileID and NumberOfElements are the transaction summary, and they will be repeated (N)umberOfFiles times </p>
    */
   @Override
   public JournalLoadInformation load(final LoaderCallback loadManager) throws Exception {
      return load(loadManager, true, null);
   }

   /**
    * @param replicationSync {@code true} will place
    */
   private synchronized JournalLoadInformation load(final LoaderCallback loadManager,
                                                    final boolean changeData,
                                                    final JournalState replicationSync,
                                                    final AtomicReference<ByteBuffer> wholeFileBufferRef) throws Exception {
      JournalState state;
      assert (state = this.state) != JournalState.STOPPED &&
         state != JournalState.LOADED &&
         state != replicationSync;

      checkControlFile(wholeFileBufferRef);

      records.clear();

      filesRepository.clear();

      transactions.clear();
      currentFile = null;

      final Map<Long, TransactionHolder> loadTransactions = new LinkedHashMap<>();

      final List<JournalFile> orderedFiles = orderFiles();

      filesRepository.calculateNextfileID(orderedFiles);

      int lastDataPos = JournalImpl.SIZE_HEADER;

      // AtomicLong is used only as a reference, not as an Atomic value
      final AtomicLong maxID = new AtomicLong(-1);

      for (final JournalFile file : orderedFiles) {
         logger.trace("Loading file {}", file.getFile().getFileName());

         final AtomicBoolean hasData = new AtomicBoolean(false);

         int resultLastPost = JournalImpl.readJournalFile(fileFactory, file, new JournalReaderCallback() {

            private void checkID(final long id) {
               if (id > maxID.longValue()) {
                  maxID.lazySet(id);
               }
            }

            @Override
            public void onReadAddRecord(final RecordInfo info) throws Exception {
               checkID(info.id);

               hasData.lazySet(true);

               loadManager.addRecord(info);

               records.put(info.id, new JournalRecord(file, info.data.length + JournalImpl.SIZE_ADD_RECORD + 1));
            }

            @Override
            public void onReadUpdateRecord(final RecordInfo info) throws Exception {
               checkID(info.id);

               hasData.lazySet(true);

               loadManager.updateRecord(info);

               JournalRecord posFiles = records.get(info.id);

               if (posFiles != null) {
                  // It's legal for this to be null. The file(s) with the may
                  // have been deleted
                  // just leaving some updates in this file

                  posFiles.addUpdateFile(file, info.data.length + JournalImpl.SIZE_ADD_RECORD + 1, info.replaceableUpdate); // +1 = compact
                  // count
               }
            }

            @Override
            public void onReadDeleteRecord(final long recordID) throws Exception {
               hasData.lazySet(true);

               loadManager.deleteRecord(recordID);

               JournalRecord posFiles = records.remove(recordID);

               if (posFiles != null) {
                  posFiles.delete(file);
               }
            }

            @Override
            public void onReadUpdateRecordTX(final long transactionID, final RecordInfo info) throws Exception {
               onReadAddRecordTX(transactionID, info);
            }

            @Override
            public void onReadAddRecordTX(final long transactionID, final RecordInfo info) throws Exception {

               checkID(info.id);

               hasData.lazySet(true);

               TransactionHolder tx = loadTransactions.get(transactionID);

               if (tx == null) {
                  tx = new TransactionHolder(transactionID);

                  loadTransactions.put(transactionID, tx);
               }

               tx.recordInfos.add(info);

               JournalTransaction tnp = transactions.get(transactionID);

               if (tnp == null) {
                  tnp = new JournalTransaction(transactionID, JournalImpl.this);

                  transactions.put(transactionID, tnp);
               }

               tnp.addPositive(file, info.id, info.data.length + JournalImpl.SIZE_ADD_RECORD_TX + 1, info.replaceableUpdate); // +1 = compact
               // count
            }

            @Override
            public void onReadDeleteRecordTX(final long transactionID, final RecordInfo info) throws Exception {
               hasData.lazySet(true);

               TransactionHolder tx = loadTransactions.get(transactionID);

               if (tx == null) {
                  tx = new TransactionHolder(transactionID);

                  loadTransactions.put(transactionID, tx);
               }

               tx.recordsToDelete.add(info);

               JournalTransaction tnp = transactions.get(transactionID);

               if (tnp == null) {
                  tnp = new JournalTransaction(transactionID, JournalImpl.this);

                  transactions.put(transactionID, tnp);
               }

               tnp.addNegative(file, info.id);

            }

            @Override
            public void onReadPrepareRecord(final long transactionID,
                                            final byte[] extraData,
                                            final int numberOfRecords) throws Exception {
               hasData.lazySet(true);

               TransactionHolder tx = loadTransactions.get(transactionID);

               if (tx == null) {
                  // The user could choose to prepare empty transactions
                  tx = new TransactionHolder(transactionID);

                  loadTransactions.put(transactionID, tx);
               }

               tx.prepared = true;

               tx.extraData = extraData;

               JournalTransaction journalTransaction = transactions.get(transactionID);

               if (journalTransaction == null) {
                  journalTransaction = new JournalTransaction(transactionID, JournalImpl.this);

                  transactions.put(transactionID, journalTransaction);
               }

               boolean healthy = checkTransactionHealth(file, journalTransaction, orderedFiles, numberOfRecords);

               if (healthy) {
                  journalTransaction.prepare(file);
               } else {
                  ActiveMQJournalLogger.LOGGER.preparedTXIncomplete(transactionID);
                  tx.invalid = true;
               }
            }

            @Override
            public void onReadCommitRecord(final long transactionID, final int numberOfRecords) throws Exception {
               TransactionHolder tx = loadTransactions.remove(transactionID);

               // The commit could be alone on its own journal-file and the
               // whole transaction body was reclaimed but not the
               // commit-record
               // So it is completely legal to not find a transaction at this
               // point
               // If we can't find it, we assume the TX was reclaimed and we
               // ignore this
               if (tx != null) {
                  JournalTransaction journalTransaction = transactions.remove(transactionID);

                  if (journalTransaction == null) {
                     throw new IllegalStateException("Cannot find tx " + transactionID);
                  }

                  boolean healthy = checkTransactionHealth(file, journalTransaction, orderedFiles, numberOfRecords);

                  if (healthy) {
                     for (RecordInfo txRecord : tx.recordInfos) {
                        if (txRecord.isUpdate) {
                           loadManager.updateRecord(txRecord);
                        } else {
                           loadManager.addRecord(txRecord);
                        }
                     }

                     for (RecordInfo deleteValue : tx.recordsToDelete) {
                        loadManager.deleteRecord(deleteValue.id);
                     }

                     journalTransaction.commit(file);
                  } else {
                     ActiveMQJournalLogger.LOGGER.txMissingElements(transactionID);

                     journalTransaction.forget();
                  }

                  hasData.lazySet(true);
               }

            }

            @Override
            public void onReadRollbackRecord(final long transactionID) throws Exception {
               TransactionHolder tx = loadTransactions.remove(transactionID);

               // The rollback could be alone on its own journal-file and the
               // whole transaction body was reclaimed but the commit-record
               // So it is completely legal to not find a transaction at this
               // point
               if (tx != null) {
                  JournalTransaction tnp = transactions.remove(transactionID);

                  if (tnp == null) {
                     throw new IllegalStateException("Cannot find tx " + transactionID);
                  }

                  // There is no need to validate summaries/holes on
                  // Rollbacks.. We will ignore the data anyway.
                  tnp.rollback(file);

                  hasData.lazySet(true);
               }
            }

            @Override
            public void markAsDataFile(final JournalFile file) {
               hasData.lazySet(true);
            }

         }, wholeFileBufferRef, false, this.replaceableRecords);

         if (hasData.get()) {
            lastDataPos = resultLastPost;
            filesRepository.addDataFileOnBottom(file);
         } else {
            if (changeData) {
               // Empty dataFiles with no data
               filesRepository.addFreeFile(file, false, isRemoveExtraFilesOnLoad());
            }
         }
      }

      if (replicationSync == JournalState.SYNCING) {
         assert filesRepository.getDataFiles().isEmpty();
         setJournalState(JournalState.SYNCING);
         return new JournalLoadInformation(0, -1);
      }

      setUpCurrentFile(lastDataPos);

      setJournalState(JournalState.LOADED);

      for (TransactionHolder transaction : loadTransactions.values()) {
         if ((!transaction.prepared || transaction.invalid) && replicationSync != JournalState.SYNCING_UP_TO_DATE) {
            ActiveMQJournalLogger.LOGGER.uncomittedTxFound(transaction.transactionID);

            if (changeData) {
               // I append a rollback record here, because otherwise compacting will be throwing messages because of unknown transactions
               this.appendRollbackRecord(transaction.transactionID, false);
            }

            loadManager.failedTransaction(transaction.transactionID, transaction.recordInfos, transaction.recordsToDelete);
         } else {
            for (RecordInfo info : transaction.recordInfos) {
               if (info.id > maxID.get()) {
                  maxID.lazySet(info.id);
               }
            }

            PreparedTransactionInfo info = new PreparedTransactionInfo(transaction.transactionID, transaction.extraData);

            info.getRecords().addAll(transaction.recordInfos);

            info.getRecordsToDelete().addAll(transaction.recordsToDelete);

            loadManager.addPreparedTransaction(info);
         }
      }

      if (changeData) {
         checkReclaimStatus();
      }

      return new JournalLoadInformation(records.size(), maxID.longValue());
   }

   private synchronized JournalLoadInformation load(final LoaderCallback loadManager,
                                                    final boolean changeData,
                                                    final JournalState replicationSync) throws Exception {
      final JournalState state = this.state;
      if (state == JournalState.STOPPED || state == JournalState.LOADED) {
         throw new IllegalStateException("Journal " + this + " must be in " + JournalState.STARTED + " state, was " +
                                            state);
      }
      if (state == replicationSync) {
         throw new IllegalStateException("Journal cannot be in state " + JournalState.STARTED);
      }
      // AtomicReference is used only as a reference, not as an Atomic value
      final AtomicReference<ByteBuffer> wholeFileBufferRef = new AtomicReference<>();
      try {
         return load(loadManager, changeData, replicationSync, wholeFileBufferRef);
      } finally {
         final ByteBuffer wholeFileBuffer = wholeFileBufferRef.get();
         if (wholeFileBuffer != null) {
            fileFactory.releaseDirectBuffer(wholeFileBuffer);
            wholeFileBufferRef.lazySet(null);
         }
      }
   }


   @Override
   public void processBackupCleanup() {
      if (logger.isDebugEnabled()) {
         logger.debug("processBackupCleanup with maxFiles = {} and period = {}", journalRetentionMaxFiles, journalRetentionPeriod);
      }

      if (journalRetentionFolder != null && (journalRetentionMaxFiles > 0 || journalRetentionPeriod > 0)) {

         FilenameFilter fnf = (d, name) -> name.endsWith("." + filesRepository.getFileExtension());

         if (journalRetentionPeriod > 0) {
            String[] fileNames = journalRetentionFolder.list(fnf);
            Arrays.sort(fileNames);

            GregorianCalendar calendar = this.calendarThreadLocal.get();
            calendar.setTimeInMillis(System.currentTimeMillis() - journalRetentionPeriod);
            long timeCutOf = calendar.getTimeInMillis();

            for (String fileName : fileNames) {
               long timeOnFile = getDatePortionMillis(fileName);
               if (timeOnFile < timeCutOf) {
                  logger.debug("File {} is too old and should go", fileName);
                  File fileToRemove = new File(journalRetentionFolder, fileName);
                  if (!fileToRemove.delete()) {
                     logger.debug("Could not remove {}", fileToRemove);
                  }
               } else {
                  break;
               }
            }
         }

         if (journalRetentionMaxFiles > 0) {
            String[] fileNames = journalRetentionFolder.list(fnf);
            Arrays.sort(fileNames);

            if (fileNames.length > journalRetentionMaxFiles) {
               int toRemove = fileNames.length - journalRetentionMaxFiles;

               for (String file : fileNames) {
                  logger.debug("Removing {}", file);
                  File fileToRemove = new File(journalRetentionFolder, file);
                  fileToRemove.delete();
                  toRemove--;
                  if (toRemove <= 0) {
                     break;
                  }
               }
            }
         }


      }
   }

   /**
    * With the exception of initialization, this has to be always called within the compactorExecutor
    */
   @Override
   public void processBackup() {
      if (this.journalRetentionFolder == null) {
         return;
      }

      synchronized (processBackupLock) {
         List<JournalFile> filesToMove;
         filesToMove = new ArrayList<>(historyPendingFiles.size());
         filesToMove.addAll(historyPendingFiles);
         historyPendingFiles.clear();

         for (JournalFile fileToCopy : filesToMove) {
            copyFile(fileToCopy);
         }
      }

      if (compactorExecutor != null) {
         compactorExecutor.execute(this::processBackupCleanup);
      } else {
         processBackupCleanup();
      }
   }

   // This exists to avoid a race with copying the files on initial replica
   // we get the list, and check each individual file if they have the pending copy
   private void checkRetentionFile(JournalFile file) {
      if (this.journalRetentionFolder == null) {
         return;
      }

      // It is cheaper to check without a lock
      if (!file.getFile().getFileName().endsWith(BKP)) {
         return;
      }

      copyFile(file);
   }

   // you need to synchronize processBackupLock before calling this
   private void copyFile(JournalFile fileToCopy) {
      synchronized (processBackupLock) {
         if (fileToCopy == null || !fileToCopy.getFile().getFileName().endsWith(BKP)) {
            return;
         }

         long fileId = fileToCopy.getFileID();

         GregorianCalendar calendar = calendarThreadLocal.get();

         calendar.setTimeInMillis(System.currentTimeMillis());
         String fileName = getHistoryFileName(fileId, calendar);

         File copyFrom = fileToCopy.getFile().getJavaFile();

         File copyTo = new File(journalRetentionFolder, fileName);

         logger.debug("Copying journal retention from {} to {}", copyFrom, copyTo);

         try {
            Files.copy(copyFrom.toPath(), copyTo.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException e) {
            fileFactory.onIOError(e, e.getMessage(), copyFrom.getName());
         }

         try {
            fileToCopy.getFile().renameTo(removeBackupExtension(fileToCopy.getFile().getFileName()));
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            fileFactory.onIOError(e, e.getMessage(), fileToCopy.getFile().getFileName());
         }

         fileToCopy.setReclaimable(true);
      }
   }

   public String getHistoryFileName(long sequence, Calendar calendar) {

      String fileName = String.format("%s-%04d%02d%02d%02d%02d%02d-%d.%s", filesRepository.getFilePrefix(), calendar.get(Calendar.YEAR),
                                      (calendar.get(Calendar.MONTH) + 1), calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.HOUR_OF_DAY),
                                      calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND), sequence, filesRepository.getFileExtension());

      return fileName;
   }

   public String removeBackupExtension(String name) {
      int indexOfBKP = name.indexOf(BKP);
      if (indexOfBKP >= 0) {
         return name.substring(0, name.indexOf(BKP));
      } else {
         return name;
      }
   }

   public long getDatePortionMillis(String name) {
      String datePortion = getDatePortion(name);
      GregorianCalendar calendar = calendarThreadLocal.get();

      int year = Integer.parseInt(datePortion.substring(0, 4));
      int month = Integer.parseInt(datePortion.substring(4, 6));
      int day = Integer.parseInt(datePortion.substring(6, 8));
      int hour = Integer.parseInt(datePortion.substring(8, 10));
      int minutes = Integer.parseInt(datePortion.substring(10, 12));
      int seconds = Integer.parseInt(datePortion.substring(12, 14));

      calendar.clear();
      calendar.set(year, month - 1, day, hour, minutes, seconds);
      return calendar.getTimeInMillis();
   }

   public String getDatePortion(String name) {
      return name.substring(filesRepository.getFilePrefix().length() + 1, name.indexOf("-", filesRepository.getFilePrefix().length() + 1));
   }

   @Override
   public final void checkReclaimStatus() throws Exception {

      logger.trace("JournalImpl::checkReclaimStatus");
      if (compactorRunning.get()) {
         logger.trace("Giving up checkReclaimStatus as compactor is running");
         return;
      }

      // We can't start reclaim while compacting is working
      while (true) {
         logger.trace("JournalImpl::checkReclaimStatus Trying to get a read lock on reclaming");
         if (state != JournalImpl.JournalState.LOADED) {
            return;
         }
         if (!isAutoReclaim()) {
            logger.trace("JournalImpl::checkReclaimStatus has no autoReclaim, giving up loop");
            return;
         }
         if (journalLock.readLock().tryLock(250, TimeUnit.MILLISECONDS)) {
            logger.trace("JournalImpl checkReclaimStatus readLock acquired");
            break;
         }
         logger.trace("Could not acquire readLock on checkReclaimStatus, retrying loop");
      }

      logger.debug("JournalImpl::checkReclaimStatus() starting");
      try {
         scan(getDataFiles());

         for (JournalFile file : filesRepository.getDataFiles()) {
            if (file.isCanReclaim()) {
               // File can be reclaimed or deleted
               logger.trace("Reclaiming file {}", file);

               filesRepository.removeDataFile(file);

               filesRepository.addFreeFile(file, false);
            }
         }
      } finally {
         journalLock.readLock().unlock();
      }

      logger.debug("JournalImpl::checkReclaimStatus() finishing");

      return;
   }

   private boolean needsCompact() throws Exception {
      JournalFile[] dataFiles = getDataFiles();

      long totalLiveSize = 0;

      long updateCount = 0, addRecord = 0;

      for (JournalFile file : dataFiles) {
         totalLiveSize += file.getLiveSize();
         updateCount += file.getReplaceableCount();
         addRecord += file.getAddRecord();
      }


      if (dataFiles.length > compactMinFiles && addRecord > 0 && updateCount > 0) {
         double updateFactor = updateCount / addRecord;

         if (updateFactor > UPDATE_FACTOR) { // this means every add records with at least 10 records
            if (logger.isDebugEnabled()) {
               logger.debug("There are {} records, with {} towards them. UpdateCound / AddCount = {}, being greater than {} meaning we have to schedule compacting",
                            addRecord, updateCount, updateFactor, UPDATE_FACTOR);
            }
            return true;
         } else {
            if (logger.isDebugEnabled()) {
               logger.debug("There are {} records, with {} towards them. UpdateCound / AddCount = {}, which is lower than {} meaning we are ok to leave these records",
                            addRecord, updateCount, updateFactor, UPDATE_FACTOR);
            }
         }
      }


      long totalBytes = dataFiles.length * (long) fileSize;

      long compactMargin = (long) (totalBytes * compactPercentage);

      boolean needCompact = totalLiveSize < compactMargin && dataFiles.length > compactMinFiles;

      if (logger.isDebugEnabled()) {
         logger.debug("JournalImpl::needsCompact={}, totalBytes={}, dataFiles.length={}, fileSize={}, compactMargin={}, compactingPercentage={}," +
                         "compactMinFiles={}", needCompact, totalBytes, dataFiles.length, fileSize,
                      compactMargin, compactPercentage, compactMinFiles);
      }

      return needCompact;

   }

   private void checkCompact() throws Exception {
      if (compactMinFiles == 0) {
         // compacting is disabled
         return;
      }

      if (state != JournalState.LOADED) {
         return;
      }

      if (!compactorRunning.get() && needsCompact()) {
         scheduleCompact();
      }
   }

   private void scheduleCompact() {
      if (!compactorRunning.compareAndSet(false, true)) {
         return;
      }

      logger.debug("JournalImpl::scheduleCompact() starting");

      // We can't use the executor for the compacting... or we would dead lock because of file open and creation
      // operations (that will use the executor)
      compactorExecutor.execute(() -> {
         try {
            JournalImpl.this.compact();
         } catch (Throwable e) {
            ActiveMQJournalLogger.LOGGER.errorCompacting(e);
         } finally {
            compactorRunning.set(false);
            logger.debug("JournalImpl::scheduleCompact() done");
         }
      });
   }

   // TestableJournal implementation
   // --------------------------------------------------------------

   @Override
   public final void setAutoReclaim(final boolean autoReclaim) {
      this.autoReclaim = autoReclaim;
   }

   @Override
   public final boolean isAutoReclaim() {
      return autoReclaim;
   }

   // Only meant to be used in tests.
   @Override
   public String debug() throws Exception {
      scan(getDataFiles());

      StringBuilder builder = new StringBuilder();

      for (JournalFile file : filesRepository.getDataFiles()) {
         builder.append("DataFile:" + file +
                           " posCounter = " +
                           file.getPosCount() +
                           " reclaimStatus = " +
                           file.isCanReclaim() +
                           " live size = " +
                           file.getLiveSize() +
                           "\n");
         if (file instanceof JournalFileImpl journalFile) {
            builder.append(journalFile.debug());

         }
      }

      for (JournalFile file : filesRepository.getFreeFiles()) {
         builder.append("FreeFile:" + file + "\n");
      }

      if (currentFile != null) {
         builder.append("CurrentFile:" + currentFile + " posCounter = " + currentFile.getPosCount() + "\n");

         if (currentFile instanceof JournalFileImpl journalFile) {
            builder.append(journalFile.debug());
         }
      } else {
         builder.append("CurrentFile: No current file at this point!");
      }

      return builder.toString();
   }

   /**
    * Method for use on testcases. It will call waitComplete on every transaction, so any assertions on the file system
    * will be correct after this
    */
   @Override
   public void debugWait() throws InterruptedException {
      fileFactory.flush();

      flushExecutor(filesExecutor);

      flushExecutor(appendExecutor);
   }

   @Override
   public void flush() throws InterruptedException {
      fileFactory.flush();

      flushExecutor(appendExecutor);

      flushExecutor(filesExecutor);

      flushExecutor(compactorExecutor);
   }

   /**
    * The max size record that can be stored in the journal
    */
   @Override
   public long getMaxRecordSize() {
      if (fileFactory.getBufferSize() == 0) {
         return getFileSize();
      } else {
         return Math.min(getFileSize(), fileFactory.getBufferSize());
      }
   }

   @Override
   public long getWarningRecordSize() {
      return getMaxRecordSize() - 2048;
   }

   private void flushExecutor(Executor executor) throws InterruptedException {

      if (executor != null) {
         // Send something to the closingExecutor, just to make sure we went until its end
         final CountDownLatch latch = new CountDownLatch(1);

         try {
            executor.execute(latch::countDown);
            latch.await(10, TimeUnit.SECONDS);
         } catch (RejectedExecutionException ignored) {
            // this is fine
         }
      }

   }

   public boolean flushAppendExecutor(long timeout, TimeUnit unit) throws InterruptedException {
      return OrderedExecutorFactory.flushExecutor(appendExecutor, timeout, unit);
   }

   @Override
   public int getDataFilesCount() {
      return filesRepository.getDataFilesCount();
   }

   @Override
   public JournalFile[] getDataFiles() {
      JournalFile[] files = filesRepository.getDataFilesArray();
      for (JournalFile file : files) {
         checkRetentionFile(file);
      }
      return files;
   }

   @Override
   public int getFreeFilesCount() {
      return filesRepository.getFreeFilesCount();
   }

   @Override
   public int getOpenedFilesCount() {
      return filesRepository.getOpenedFilesCount();
   }

   @Override
   public int getIDMapSize() {
      return records.size();
   }

   @Override
   public int getFileSize() {
      return fileSize;
   }

   @Override
   public int getMinFiles() {
      return minFiles;
   }

   @Override
   public String getFilePrefix() {
      return filesRepository.getFilePrefix();
   }

   @Override
   public String getFileExtension() {
      return filesRepository.getFileExtension();
   }

   @Override
   public int getMaxAIO() {
      return filesRepository.getMaxAIO();
   }

   @Override
   public int getUserVersion() {
      return userVersion;
   }

   // In some tests we need to force the journal to move to a next file
   @Override
   public void forceMoveNextFile() throws Exception {
      debugWait();
      journalLock.writeLock().lock();
      try {
         moveNextFile(false, true);
      } finally {
         journalLock.writeLock().unlock();
      }
   }

   @Override
   public void forceBackup(int timeout, TimeUnit unit) throws Exception {
      journalLock.writeLock().lock();
      try {
         moveNextFile(true, true);
      } finally {
         journalLock.writeLock().unlock();
      }

      CountDownLatch latch = new CountDownLatch(1);
      compactorExecutor.execute(latch::countDown);
      latch.await(timeout, unit);
   }


   // ActiveMQComponent implementation
   // ---------------------------------------------------

   @Override
   public synchronized boolean isStarted() {
      return state != JournalState.STOPPED;
   }

   @Override
   public synchronized void start() {
      if (state != JournalState.STOPPED) {
         throw new IllegalStateException("Journal " + this + " is not stopped, state is " + state);
      }

      if (providedIOThreadPool == null) {
         ThreadFactory factory = SecurityManagerShim.doPrivileged((PrivilegedAction<ThreadFactory>) () -> new ActiveMQThreadFactory("io", true, JournalImpl.class.getClassLoader()));

         threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue(), factory);
         ioExecutorFactory = new OrderedExecutorFactory(threadPool);
      } else {
         ioExecutorFactory = providedIOThreadPool;
      }

      filesExecutor = ioExecutorFactory.getExecutor();

      compactorExecutor = ioExecutorFactory.getExecutor();

      appendExecutor = ioExecutorFactory.getExecutor();

      filesRepository.setExecutor(filesExecutor);

      fileFactory.start();

      setJournalState(JournalState.STARTED);
   }

   @Override
   public synchronized void stop() {
      if (state == JournalState.STOPPED) {
         return;
      }

      try {
         flush();
      } catch (InterruptedException e) {
         logger.debug(e.getMessage(), e);
         // passing the interrupt forward
         Thread.currentThread().interrupt();
      }

      setJournalState(JournalState.STOPPED);

      journalLock.writeLock().lock();
      try {
         try {
            for (CountDownLatch latch : latches) {
               latch.countDown();
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }

         fileFactory.deactivateBuffer();

         try {
            if (currentFile != null && currentFile.getFile().isOpen()) {
               currentFile.getFile().close(true, true);
            }
         } catch (Exception e) {
            ActiveMQJournalLogger.LOGGER.errorClosingFile(e);
         }
         filesRepository.clear();

         fileFactory.stop();

         historyPendingFiles.clear();

         currentFile = null;
      } finally {
         journalLock.writeLock().unlock();
      }

      // I have to shutdown the pool after
      // otherwise pending closes will not succeed in certain races
      if (providedIOThreadPool == null) {
         threadPool.shutdown();

         try {
            if (!threadPool.awaitTermination(120, TimeUnit.SECONDS)) {
               threadPool.shutdownNow();
            }
         } catch (InterruptedException e) {
            logger.debug(e.getMessage(), e);
            // delegating exception to further interrupt
            Thread.currentThread().interrupt();
         }
         threadPool = null;
         ioExecutorFactory = null;
      }

   }

   @Override
   public int getNumberOfRecords() {
      return records.size();
   }

   protected SequentialFile createControlFile(final List<JournalFile> files,
                                              final List<JournalFile> newFiles,
                                              final Pair<String, String> cleanupRename) throws Exception {
      List<Pair<String, String>> cleanupList;
      if (cleanupRename == null) {
         cleanupList = null;
      } else {
         cleanupList = new ArrayList<>();
         cleanupList.add(cleanupRename);
      }
      return writeControlFile(fileFactory, files, newFiles, cleanupList);
   }


   protected SequentialFile writeControlFile(final SequentialFileFactory fileFactory,
                                                 final List<JournalFile> files,
                                                 final List<JournalFile> newFiles,
                                                 final List<Pair<String, String>> renames) throws Exception {

      return AbstractJournalUpdateTask.writeControlFile(fileFactory, files, newFiles, renames);
   }



   protected void deleteControlFile(final SequentialFile controlFile) throws Exception {
      controlFile.delete();
   }

   /**
    * being protected as testcases can override this method
    */
   protected void renameFiles(final List<JournalFile> oldFiles, final List<JournalFile> newFiles) throws Exception {

      // addFreeFiles has to be called through filesExecutor, or the fileID on the orderedFiles may end up in a wrong
      // order
      // These files are already freed, and are described on the compactor file control.
      // In case of crash they will be cleared anyways

      final CountDownLatch done = newLatch(1);

      filesExecutor.execute(() -> {
         try {
            for (JournalFile file : oldFiles) {
               try {
                  filesRepository.addFreeFile(file, false);
               } catch (Throwable e) {
                  ActiveMQJournalLogger.LOGGER.errorReinitializingFile(file, e);
               }
            }
         } finally {
            done.countDown();
         }
      });

      // need to wait all old files to be freed
      // to avoid a race where the CTR file is deleted before the init for these files is already done
      // what could cause a duplicate in case of a crash after the CTR is deleted and before the file is initialized
      awaitLatch(done, -1);

      for (JournalFile file : newFiles) {
         String newName = JournalImpl.renameExtensionFile(file.getFile().getFileName(), ".cmp");
         file.getFile().renameTo(newName);
      }

   }

   protected static String renameExtensionFile(String name, final String extension) {
      name = name.substring(0, name.lastIndexOf(extension));
      return name;
   }

   /**
    * This is an interception point for testcases, when the compacted files are written, before replacing the data
    * structures
    */
   protected void onCompactStart() throws Exception {
   }

   /**
    * This is an interception point for testcases, when the compacted files are written, to be called as soon as the
    * compactor gets a writeLock
    */
   protected void onCompactLockingTheJournal() throws Exception {
   }

   /**
    * This is an interception point for testcases, when the compacted files are written, before replacing the data
    * structures
    */
   protected void onCompactDone() {
   }

   // Private
   // -----------------------------------------------------------------------------

   /**
    * Checks for holes on the transaction (a commit written but with an incomplete transaction).
    * <p>
    * This method will validate if the transaction (PREPARE/COMMIT) is complete as stated on the COMMIT-RECORD.
    * <p>
    * For details see {@link JournalCompleteRecordTX} about how the transaction-summary is recorded.
    */
   private boolean checkTransactionHealth(final JournalFile currentFile,
                                          final JournalTransaction journalTransaction,
                                          final List<JournalFile> orderedFiles,
                                          final int numberOfRecords) {
      return journalTransaction.getCounter(currentFile) == numberOfRecords;
   }

   private static boolean isTransaction(final byte recordType) {
      return recordType == JournalImpl.ADD_RECORD_TX || recordType == JournalImpl.UPDATE_RECORD_TX ||
         recordType == JournalImpl.DELETE_RECORD_TX ||
         JournalImpl.isCompleteTransaction(recordType);
   }

   private static boolean isCompleteTransaction(final byte recordType) {
      return recordType == JournalImpl.COMMIT_RECORD || recordType == JournalImpl.PREPARE_RECORD ||
         recordType == JournalImpl.ROLLBACK_RECORD;
   }

   private static boolean isContainsBody(final byte recordType) {
      return recordType >= JournalImpl.EVENT_RECORD && recordType <= JournalImpl.DELETE_RECORD_TX;
   }

   private static int getRecordSize(final byte recordType, final int journalVersion) {
      // The record size (without the variable portion)
      int recordSize = switch (recordType) {
         case ADD_RECORD, EVENT_RECORD, UPDATE_RECORD -> JournalImpl.SIZE_ADD_RECORD;
         case ADD_RECORD_TX, UPDATE_RECORD_TX -> JournalImpl.SIZE_ADD_RECORD_TX;
         case DELETE_RECORD -> JournalImpl.SIZE_DELETE_RECORD;
         case DELETE_RECORD_TX -> JournalImpl.SIZE_DELETE_RECORD_TX;
         case PREPARE_RECORD -> JournalImpl.SIZE_PREPARE_RECORD;
         case COMMIT_RECORD -> JournalImpl.SIZE_COMMIT_RECORD;
         case ROLLBACK_RECORD -> JournalImpl.SIZE_ROLLBACK_RECORD;
         default ->
            // Sanity check, this was previously tested, nothing different
            // should be on this switch
            throw new IllegalStateException("Record other than expected");
      };
      if (journalVersion >= 2) {
         return recordSize + 1;
      } else {
         return recordSize;
      }
   }

   public JournalFileImpl readFileHeader(final SequentialFile file) throws Exception {
      ByteBuffer bb = fileFactory.newBuffer(JournalImpl.SIZE_HEADER);

      file.read(bb);

      int journalVersion = bb.getInt();

      if (journalVersion != JournalImpl.FORMAT_VERSION) {
         boolean isCompatible = false;

         for (int v : JournalImpl.COMPATIBLE_VERSIONS) {
            if (v == journalVersion) {
               isCompatible = true;
            }
         }

         if (!isCompatible) {
            throw ActiveMQJournalBundle.BUNDLE.journalFileMisMatch();
         }
      }

      int readUserVersion = bb.getInt();

      if (readUserVersion != userVersion) {
         throw ActiveMQJournalBundle.BUNDLE.journalDifferentVersion();
      }

      long fileID = bb.getLong();

      fileFactory.releaseBuffer(bb);

      bb = null;

      return new JournalFileImpl(file, fileID, journalVersion);
   }

   public static int initFileHeader(final SequentialFileFactory fileFactory,
                                    final SequentialFile sequentialFile,
                                    final int userVersion,
                                    final long fileID) throws Exception {
      // We don't need to release buffers while writing.
      ByteBuffer bb = fileFactory.newBuffer(JournalImpl.SIZE_HEADER);

      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(bb);

      try {
         JournalImpl.writeHeader(buffer, userVersion, fileID);

         bb.rewind();

         int bufferSize = bb.limit();

         sequentialFile.position(0);

         sequentialFile.writeDirect(bb, true);
         return bufferSize;
      } finally {
         // release it by first unwrap the unreleasable buffer and then release it.
         buffer.byteBuf().unwrap().release();
      }
   }

   public static void writeHeader(final ActiveMQBuffer buffer, final int userVersion, final long fileID) {
      buffer.writeInt(JournalImpl.FORMAT_VERSION);

      buffer.writeInt(userVersion);

      buffer.writeLong(fileID);
   }

   /**
    * @param completeTransaction If the appendRecord is for a prepare or commit, where we should update the number of
    *                            pendingTransactions on the current file
    */
   private JournalFile appendRecord(final JournalInternalRecord encoder,
                                    final boolean completeTransaction,
                                    final boolean sync,
                                    final JournalTransaction tx,
                                    final IOCallback parameterCallback) throws Exception {

      checkJournalIsLoaded();

      final IOCallback callback;

      final int size = encoder.getEncodeSize();

      switchFileIfNecessary(size);

      if (tx != null) {
         // The callback of a transaction has to be taken inside the lock,
         // when we guarantee the currentFile will not be changed,
         // since we individualize the callback per file
         if (fileFactory.isSupportsCallbacks()) {
            // Set the delegated callback as a parameter
            tx.countUp();
            if (parameterCallback != null) {
               tx.setDelegateCompletion(parameterCallback);
            }
            callback = tx;
         } else {
            callback = parameterCallback;
         }

         // We need to add the number of records on currentFile if prepare or commit
         if (completeTransaction) {
            // Filling the number of pendingTransactions at the current file
            tx.fillNumberOfRecords(currentFile, encoder);
         }
      } else {
         callback = parameterCallback;
      }

      // Adding fileID
      encoder.setFileID(currentFile.getRecordID());

      if (callback != null) {
         currentFile.getFile().write(encoder, sync, callback);
      } else {
         currentFile.getFile().write(encoder, sync);
      }

      return currentFile;
   }

   @Override
   void scheduleReclaim() {
      if (state != JournalState.LOADED) {
         return;
      }

      if (logger.isDebugEnabled()) {
         logger.debug("JournalImpl::scheduleReclaim, autoReclaim={}, compactorRunning={}", isAutoReclaim(), compactorRunning.get());
      }

      if (isAutoReclaim() && !compactorRunning.get()) {
         logger.trace("Scheduling reclaim and compactor checks");
         compactorExecutor.execute(() -> {
            try {
               processBackup();
               checkReclaimStatus();
               checkCompact();
            } catch (Exception e) {
               ActiveMQJournalLogger.LOGGER.errorSchedulingCompacting(e);
            } finally {
               logger.debug("JournalImpl::scheduleReclaim finished");
            }
         });
      } else {
         if (logger.isDebugEnabled()) {
            logger.debug("Ignoring scheduleReclaim call because of autoReclaim={} and compactorRunning={}", isAutoReclaim(), compactorRunning.get());
         }
      }
   }

   private JournalTransaction getTransactionInfo(final long txID) {
      journalLock.readLock().lock();
      try {
         JournalTransaction tx = transactions.get(txID);

         if (tx == null) {
            tx = new JournalTransaction(txID, this);

            JournalTransaction trans = transactions.putIfAbsent(txID, tx);

            if (trans != null) {
               tx = trans;
            }
         }

         return tx;
      } finally {
         journalLock.readLock().unlock();
      }
   }

   private void checkControlFile(AtomicReference<ByteBuffer> wholeFileBufferRef) throws Exception {
      List<String> dataFiles = new ArrayList<>();
      List<String> newFiles = new ArrayList<>();
      List<Pair<String, String>> renames = new ArrayList<>();

      SequentialFile controlFile = AbstractJournalUpdateTask.readControlFile(fileFactory, dataFiles, newFiles, renames, wholeFileBufferRef);
      if (controlFile != null) {
         for (String dataFile : dataFiles) {
            SequentialFile file = fileFactory.createSequentialFile(dataFile);
            if (file.exists()) {
               file.delete();
            }
         }

         for (String newFile : newFiles) {
            SequentialFile file = fileFactory.createSequentialFile(newFile);
            if (file.exists()) {
               final String originalName = file.getFileName();
               final String newName = originalName.substring(0, originalName.lastIndexOf(".cmp"));
               file.renameTo(newName);
            }
         }

         for (Pair<String, String> rename : renames) {
            SequentialFile fileTmp = fileFactory.createSequentialFile(rename.getA());
            SequentialFile fileTo = fileFactory.createSequentialFile(rename.getB());
            // We should do the rename only if the tmp file still exist, or else we could
            // delete a valid file depending on where the crash occurred during the control file delete
            if (fileTmp.exists()) {
               fileTo.delete();
               fileTmp.renameTo(rename.getB());
            }
         }

         controlFile.delete();
      }

      cleanupTmpFiles(".cmp");

      cleanupTmpFiles(".tmp");

      return;
   }

   private void cleanupTmpFiles(final String extension) throws Exception {
      List<String> leftFiles = fileFactory.listFiles(getFileExtension() + extension);

      if (!leftFiles.isEmpty()) {
         ActiveMQJournalLogger.LOGGER.tempFilesLeftOpen();

         for (String fileToDelete : leftFiles) {
            ActiveMQJournalLogger.LOGGER.deletingOrphanedFile(fileToDelete);
            SequentialFile file = fileFactory.createSequentialFile(fileToDelete);
            file.delete();
         }
      }
   }

   private static boolean isInvalidSize(final int fileSize, final int bufferPos, final int size) {
      if (size < 0) {
         return true;
      } else {
         final int position = bufferPos + size;

         return position > fileSize || position < 0;

      }
   }

   // Used on Load
   private static final class TransactionHolder {

      private TransactionHolder(final long id) {
         transactionID = id;
      }

      public final long transactionID;

      public final List<RecordInfo> recordInfos = new ArrayList<>();

      public final List<RecordInfo> recordsToDelete = new ArrayList<>();

      public boolean prepared;

      public boolean invalid;

      public byte[] extraData;

   }

   private static final Comparator<JournalFile> JOURNAL_FILE_COMPARATOR = Comparator.comparingLong(JournalFile::getFileID);

   @Override
   public final void synchronizationLock() {
      compactorLock.writeLock().lock();
      journalLock.writeLock().lock();
   }

   @Override
   public final void synchronizationUnlock() {
      try {
         compactorLock.writeLock().unlock();
      } finally {
         journalLock.writeLock().unlock();
      }
   }

   /**
    * {@return map with the IDs and corresponding {@link JournalFile}s; these are the files needed to be sent to a
    * backup in order to synchronize it}
    */
   @Override
   public synchronized Map<Long, JournalFile> createFilesForBackupSync(long[] fileIds) throws Exception {
      synchronizationLock();
      try {
         Map<Long, JournalFile> map = new HashMap<>();
         long maxID = -1;
         for (long id : fileIds) {
            maxID = Math.max(maxID, id);
            map.put(id, filesRepository.createRemoteBackupSyncFile(id));
         }
         filesRepository.setNextFileID(maxID);
         return map;
      } finally {
         synchronizationUnlock();
      }
   }

   @Override
   public SequentialFileFactory getFileFactory() {
      return fileFactory;
   }

   protected JournalFile setUpCurrentFile(int lastDataPos) throws Exception {
      // Create any more files we need

      filesRepository.ensureMinFiles();

      // The current file is the last one that has data

      currentFile = filesRepository.pollLastDataFile();
      if (currentFile != null) {
         if (!currentFile.getFile().isOpen()) {
            currentFile.getFile().open();
         }
         currentFile.getFile().position(currentFile.getFile().calculateBlockStart(lastDataPos));
      } else {
         currentFile = filesRepository.getFreeFile();
         filesRepository.openFile(currentFile, true);
      }

      fileFactory.activateBuffer(currentFile.getFile());

      filesRepository.pushOpenedFile();
      return currentFile;
   }

   protected JournalFile switchFileIfNecessary(int size) throws Exception {

      // We take into account the fileID used on the Header
      if (size > fileSize - currentFile.getFile().calculateBlockStart(JournalImpl.SIZE_HEADER)) {
         throw new IllegalArgumentException("Record is too large to store " + size + " bytes");
      }

      try {
         if (!currentFile.getFile().fits(size)) {
            moveNextFile(true, false);

            // The same check needs to be done at the new file also
            if (!currentFile.getFile().fits(size)) {
               // The exception will be thrown by criticalIO
               Exception reportingException = ActiveMQJournalBundle.BUNDLE.unexpectedFileSize(currentFile.getFile().getFileName(), size, currentFile.getFile().size());
               fileFactory.onIOError(reportingException, reportingException.getMessage());
               return null;
            }
         }
         return currentFile;
      } catch (Throwable e) {
         criticalIO(e, null);
         return null; // this will never happen, the method will call throw
      }
   }

   private void criticalIO(Throwable e, SequentialFile file) throws Exception {
      fileFactory.onIOError(e, e.getMessage(), file);
      if (e instanceof Exception exception) {
         throw exception;
      } else if (e instanceof IllegalStateException illegalStateException) {
         throw illegalStateException;
      } else {
         IOException ioex = new IOException();
         ioex.initCause(e);
         throw ioex;
      }
   }


   private CountDownLatch newLatch(int countDown) {
      if (state == JournalState.STOPPED) {
         throw new RuntimeException("Server is not started");
      }
      CountDownLatch latch = new CountDownLatch(countDown);
      latches.add(latch);
      return latch;
   }

   private void awaitLatch(CountDownLatch latch, int timeout) throws InterruptedException {
      try {
         if (timeout < 0) {
            latch.await();
         } else {
            latch.await(timeout, TimeUnit.SECONDS);
         }

         // in case of an interrupted server, we need to make sure we don't proceed on anything
         if (state == JournalState.STOPPED) {
            throw new RuntimeException("Server is not started");
         }
      } finally {
         latches.remove(latch);
      }
   }

   /**
    * You need to guarantee lock.acquire() before calling this method!
    */
   protected void moveNextFile(final boolean scheduleReclaim, boolean blockOnClose) throws Exception {
      filesRepository.closeFile(currentFile, blockOnClose);


      if (this.journalRetentionFolder != null) {
         currentFile.setReclaimable(false);
         currentFile.getFile().renameTo(currentFile.getFile().getFileName() + BKP);
         this.historyPendingFiles.add(currentFile);
      }

      currentFile = filesRepository.openFile();

      if (scheduleReclaim) {
         scheduleReclaim();
      } else {
         logger.trace("JournalImpl::moveNextFile scheduleReclaim is false, not calling scheduleReclaim");
      }

      logger.trace("Moving next file {}", currentFile);

      fileFactory.activateBuffer(currentFile.getFile());
   }

   @Override
   public void replicationSyncPreserveOldFiles() {
      setAutoReclaim(false);
   }

   @Override
   public void replicationSyncFinished() {
      setAutoReclaim(true);
   }

   @Override
   public void testCompact() {
      try {
         scheduleCompactAndBlock(60);
      } catch (Exception e) {
         logger.warn("Error during compact", e);
         throw new RuntimeException(e);
      }
   }

   /**
    * For tests only
    */
   public int getCompactCount() {
      return compactCount;
   }

   public void markTXError(long txID, Throwable t) {
      JournalTransaction tx = transactions.get(txID);
      tx.onError(-1, t.getMessage());
   }
}
