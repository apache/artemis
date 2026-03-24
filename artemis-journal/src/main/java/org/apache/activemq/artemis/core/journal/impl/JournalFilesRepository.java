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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.journal.ActiveMQJournalBundle;
import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;
import org.jboss.logging.Logger;

/**
 * This is a helper class for the Journal, which will control access to dataFiles, openedFiles and freeFiles
 * Guaranteeing that they will be delivered in order to the Journal
 */
public class JournalFilesRepository {

   private static final Logger logger = Logger.getLogger(JournalFilesRepository.class);

   /**
    * Used to debug the consistency of the journal ordering.
    * <br>
    * This is meant to be false as these extra checks would cause performance issues
    */
   private static final boolean CHECK_CONSISTENCE = false;

   private final SequentialFileFactory fileFactory;

   private final JournalImpl journal;

   private final BlockingDeque<JournalFile> dataFiles = new LinkedBlockingDeque<>();

   private final ConcurrentLinkedQueue<JournalFile> freeFiles = new ConcurrentLinkedQueue<>();

   private final BlockingQueue<JournalFile> openedFiles = new LinkedBlockingQueue<>();

   private final AtomicLong nextFileID = new AtomicLong(0);

   private final int maxAIO;

   private final int minFiles;

   private final int poolSize;

   private final int fileSize;

   private final String filePrefix;

   private final String fileExtension;

   private final int userVersion;

   private final AtomicInteger freeFilesCount = new AtomicInteger(0);

   private final int journalFileOpenTimeout;

   private final int maxAtticFiles;

   private Executor openFilesExecutor;

   private final Runnable pushOpenRunnable = new Runnable() {
      @Override
      public void run() {
         // if there's already an opened file there is no need to push a new one
         try {
            pushOpenedFile();
         } catch (Exception e) {
            ActiveMQJournalLogger.LOGGER.errorPushingFile(e);
            fileFactory.onIOError(e, "unable to open ", null);
         }
      }
   };

   public JournalFilesRepository(final SequentialFileFactory fileFactory,
                                 final JournalImpl journal,
                                 final String filePrefix,
                                 final String fileExtension,
                                 final int userVersion,
                                 final int maxAIO,
                                 final int fileSize,
                                 final int minFiles,
                                 final int poolSize,
                                 final int journalFileOpenTimeout,
                                 final int maxAtticFiles) {
      if (filePrefix == null) {
         throw new IllegalArgumentException("filePrefix cannot be null");
      }
      if (fileExtension == null) {
         throw new IllegalArgumentException("fileExtension cannot be null");
      }
      if (maxAIO <= 0) {
         throw new IllegalArgumentException("maxAIO must be a positive number");
      }
      this.fileFactory = fileFactory;
      this.maxAIO = maxAIO;
      this.filePrefix = filePrefix;
      this.fileExtension = fileExtension;
      this.minFiles = minFiles;
      this.fileSize = fileSize;
      this.poolSize = poolSize;
      this.userVersion = userVersion;
      this.journal = journal;
      this.journalFileOpenTimeout = journalFileOpenTimeout;
      this.maxAtticFiles = maxAtticFiles;
   }


   public int getPoolSize() {
      return poolSize;
   }

   public void setExecutor(final Executor fileExecutor) {
      this.openFilesExecutor = fileExecutor;
   }

   public void clear() throws Exception {
      for (JournalFile file : dataFiles) {
         file.getFile().waitNotPending();
      }

      dataFiles.clear();


      for (JournalFile file : freeFiles) {
         file.getFile().waitNotPending();
      }

      freeFiles.clear();

      freeFilesCount.set(0);

      for (JournalFile file : openedFiles) {
         try {
            file.getFile().close();
         } catch (Exception e) {
            ActiveMQJournalLogger.LOGGER.errorClosingFile(e);
         }
      }
      openedFiles.clear();
   }

   public int getMaxAIO() {
      return maxAIO;
   }

   public String getFileExtension() {
      return fileExtension;
   }

   public String getFilePrefix() {
      return filePrefix;
   }

   public void calculateNextfileID(final List<JournalFile> files) {

      for (JournalFile file : files) {
         final long fileIdFromFile = file.getFileID();
         final long fileIdFromName = getFileNameID(filePrefix, file.getFile().getFileName());

         // The compactor could create a fileName but use a previously assigned ID.
         // Because of that we need to take both parts into account
         setNextFileID(Math.max(fileIdFromName, fileIdFromFile));
      }
   }

   /**
    * Set the {link #nextFileID} value to {@code targetUpdate} if the current value is less than
    * {@code targetUpdate}.
    *
    * Notice that {@code nextFileID} is incremented before being used, see
    * {@link JournalFilesRepository#generateFileID()}.
    *
    * @param targetUpdate
    */
   public void setNextFileID(final long targetUpdate) {
      while (true) {
         final long current = nextFileID.get();
         if (current >= targetUpdate)
            return;

         if (nextFileID.compareAndSet(current, targetUpdate))
            return;
      }
   }

   public void ensureMinFiles() throws Exception {
      int filesToCreate = minFiles - (dataFiles.size() + freeFilesCount.get());

      if (filesToCreate > 0) {
         for (int i = 0; i < filesToCreate; i++) {
            // Keeping all files opened can be very costly (mainly on AIO)
            freeFiles.add(createFile(false, false, true, false, -1));
            freeFilesCount.getAndIncrement();
         }
      }

   }

   public void openFile(final JournalFile file, final boolean multiAIO) throws Exception {
      if (multiAIO) {
         file.getFile().open();
      } else {
         file.getFile().open(1, false);
      }

      file.getFile().position(file.getFile().calculateBlockStart(JournalImpl.SIZE_HEADER));
   }

   // Data File Operations ==========================================

   public JournalFile[] getDataFilesArray() {
      return dataFiles.toArray(new JournalFile[dataFiles.size()]);
   }

   public JournalFile pollLastDataFile() {
      return dataFiles.pollLast();
   }

   public void removeDataFile(final JournalFile file) {
      if (!dataFiles.remove(file)) {
         ActiveMQJournalLogger.LOGGER.couldNotRemoveFile(file);
      }
   }

   public int getDataFilesCount() {
      return dataFiles.size();
   }

   public int getJournalFileOpenTimeout() {
      return journalFileOpenTimeout;
   }

   public Collection<JournalFile> getDataFiles() {
      return dataFiles;
   }

   public void clearDataFiles() {
      dataFiles.clear();
   }

   public void addDataFileOnTop(final JournalFile file) {
      dataFiles.addFirst(file);

      if (CHECK_CONSISTENCE) {
         checkDataFiles();
      }
   }

   public String debugFiles() {
      StringBuilder buffer = new StringBuilder();

      buffer.append("**********\nCurrent File = " + journal.getCurrentFile() + "\n");
      buffer.append("**********\nDataFiles:\n");
      for (JournalFile file : dataFiles) {
         buffer.append(file.toString() + "\n");
      }
      buffer.append("*********\nFreeFiles:\n");
      for (JournalFile file : freeFiles) {
         buffer.append(file.toString() + "\n");
      }
      return buffer.toString();
   }

   public synchronized void checkDataFiles() {
      long seq = -1;
      for (JournalFile file : dataFiles) {
         if (file.getFileID() <= seq) {
            ActiveMQJournalLogger.LOGGER.checkFiles();
            ActiveMQJournalLogger.LOGGER.info(debugFiles());
            ActiveMQJournalLogger.LOGGER.seqOutOfOrder();
            throw new IllegalStateException("Sequence out of order");
         }

         if (journal.getCurrentFile() != null && journal.getCurrentFile().getFileID() <= file.getFileID()) {
            ActiveMQJournalLogger.LOGGER.checkFiles();
            ActiveMQJournalLogger.LOGGER.info(debugFiles());
            ActiveMQJournalLogger.LOGGER.currentFile(file.getFileID(), journal.getCurrentFile().getFileID(), file.getFileID(), (journal.getCurrentFile() == file));

            // throw new RuntimeException ("Check failure!");
         }

         if (journal.getCurrentFile() == file) {
            throw new RuntimeException("Check failure! Current file listed as data file!");
         }

         seq = file.getFileID();
      }

      long lastFreeId = -1;
      for (JournalFile file : freeFiles) {
         if (file.getFileID() <= lastFreeId) {
            ActiveMQJournalLogger.LOGGER.checkFiles();
            ActiveMQJournalLogger.LOGGER.info(debugFiles());
            ActiveMQJournalLogger.LOGGER.fileIdOutOfOrder();

            throw new RuntimeException("Check failure!");
         }

         lastFreeId = file.getFileID();

         if (file.getFileID() < seq) {
            ActiveMQJournalLogger.LOGGER.checkFiles();
            ActiveMQJournalLogger.LOGGER.info(debugFiles());
            ActiveMQJournalLogger.LOGGER.fileTooSmall();

            // throw new RuntimeException ("Check failure!");
         }
      }
   }

   public void addDataFileOnBottom(final JournalFile file) {
      dataFiles.add(file);

      if (CHECK_CONSISTENCE) {
         checkDataFiles();
      }
   }

   // Free File Operations ==========================================

   public int getFreeFilesCount() {
      return freeFilesCount.get();
   }

   /**
    * @param file
    * @throws Exception
    */
   public synchronized void addFreeFile(final JournalFile file, final boolean renameTmp) throws Exception {
      addFreeFile(file, renameTmp, true);
   }

   /**
    * @param file
    * @param renameTmp   - should rename the file as it's being added to free files
    * @param checkDelete - should delete the file if max condition has been met
    * @throws Exception
    */
   public synchronized void addFreeFile(final JournalFile file,
                                        final boolean renameTmp,
                                        final boolean checkDelete) throws Exception {
      if (logger.isDebugEnabled()) {
         logger.debug("Adding free file " + file + ", renameTMP=" + renameTmp + ", checkDelete=" + checkDelete);
      }
      long calculatedSize = 0;
      try {
         calculatedSize = file.getFile().size();
      } catch (Exception e) {
         throw new IllegalStateException(e.getMessage() + " file: " + file);
      }
      if (calculatedSize != fileSize) {
         damagedFile(file);
      } else if (!checkDelete || (freeFilesCount.get() + dataFiles.size() + 1 + openedFiles.size() < poolSize) || (poolSize < 0)) {
         // Re-initialise it

         if (logger.isTraceEnabled()) {
            logger.trace("Re-initializing file " + file + " as checkDelete=" + checkDelete +
                            ", freeFilesCount=" + freeFilesCount + ", dataFiles.size=" + dataFiles.size() +
                            ", openedFiles=" + openedFiles + ", poolSize=" + poolSize);
         }

         JournalFile jf = reinitializeFile(file);

         if (renameTmp) {
            jf.getFile().renameTo(JournalImpl.renameExtensionFile(jf.getFile().getFileName(), ".tmp"));
         }

         freeFiles.add(jf);
         freeFilesCount.getAndIncrement();
      } else {
         if (logger.isDebugEnabled()) {
            logger.debug("Deleting file " + file.getFile());
         }
         if (logger.isTraceEnabled()) {
            logger.trace("DataFiles.size() = " + dataFiles.size());
            logger.trace("openedFiles.size() = " + openedFiles.size());
            logger.trace("minfiles = " + minFiles + ", poolSize = " + poolSize);
            logger.trace("Free Files = " + freeFilesCount.get());
            logger.trace("File " + file + " being deleted as freeFiles.size() + dataFiles.size() + 1 + openedFiles.size() (" +
                            (freeFilesCount.get() + dataFiles.size() + 1 + openedFiles.size()) +
                            ") < minFiles (" + minFiles + ")");
         }
         file.getFile().delete();
      }

      if (CHECK_CONSISTENCE) {
         checkDataFiles();
      }
   }

   private void damagedFile(JournalFile file) throws Exception {
      if (file.getFile().isOpen()) {
         file.getFile().close(false, false);
      }
      if (file.getFile().exists()) {
         final Path journalPath = file.getFile().getJavaFile().toPath();
         final Path atticPath = journalPath.getParent().resolve("attic");
         Files.createDirectories(atticPath);
         if (listFiles(atticPath) < maxAtticFiles) {
            ActiveMQJournalLogger.LOGGER.movingFileToAttic(file.getFile().getFileName());
            Files.move(journalPath, atticPath.resolve(journalPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
         } else {
            ActiveMQJournalLogger.LOGGER.deletingFile(file);
            Files.delete(journalPath);
         }
      }
   }

   private int listFiles(Path path) throws IOException {
      try (Stream<Path> files = Files.list(path)) {
         return files.mapToInt(e -> 1).sum();
      }
   }

   public Collection<JournalFile> getFreeFiles() {
      return freeFiles;
   }

   public JournalFile getFreeFile() {
      JournalFile file = freeFiles.remove();
      freeFilesCount.getAndDecrement();
      return file;
   }

   // Opened files operations =======================================

   public int getOpenedFilesCount() {
      return openedFiles.size();
   }

   public JournalFile openFileCMP() throws Exception {
      JournalFile file = openFile();

      SequentialFile sequentialFile = file.getFile();
      sequentialFile.close();
      sequentialFile.renameTo(sequentialFile.getFileName() + ".cmp");

      return file;
   }

   /**
    * <p>This method will instantly return the opened file, and schedule opening and reclaiming.</p>
    * <p>In case there are no cached opened files, this method will block until the file was opened,
    * what would happen only if the system is under heavy load by another system (like a backup system, or a DB sharing the same box as ActiveMQ).</p>
    *
    * @throws ActiveMQIOErrorException In case the file could not be opened
    */
   public JournalFile openFile() throws InterruptedException, ActiveMQIOErrorException {
      if (logger.isTraceEnabled()) {
         logger.trace("enqueueOpenFile with openedFiles.size=" + openedFiles.size());
      }

      // First try to get an open file, that's prepared and already open
      JournalFile nextFile = openedFiles.poll();

      if (nextFile == null) {
         // if there's none, push to open

         pushOpen();

         nextFile = openedFiles.poll(journalFileOpenTimeout, TimeUnit.SECONDS);
      } else {
         if (openedFiles.isEmpty()) {
            // if empty, push to open one.
            pushOpen();
         }
      }

      if (nextFile == null) {
         ActiveMQJournalLogger.LOGGER.cantOpenFileTimeout(journalFileOpenTimeout);
         try {
            nextFile = takeFile(true, true, true, false);
         } catch (Exception e) {
            fileFactory.onIOError(e, "unable to open ", null);
            // We need to reconnect the current file with the timed buffer as we were not able to roll the file forward
            // If you don't do this you will get a NPE in TimedBuffer::checkSize where it uses the bufferobserver
            fileFactory.activateBuffer(journal.getCurrentFile().getFile());
            throw ActiveMQJournalBundle.BUNDLE.fileNotOpened();
         }
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Returning file " + nextFile);
      }

      return nextFile;
   }

   private void pushOpen() {
      if (openFilesExecutor == null) {
         pushOpenRunnable.run();
      } else {
         openFilesExecutor.execute(pushOpenRunnable);
      }
   }

   /**
    * Open a file and place it into the openedFiles queue
    */
   public synchronized void pushOpenedFile() throws Exception {
      JournalFile nextOpenedFile = takeFile(true, true, true, false);

      if (logger.isTraceEnabled()) {
         logger.trace("pushing openFile " + nextOpenedFile);
      }

      if (!openedFiles.offer(nextOpenedFile)) {
         ActiveMQJournalLogger.LOGGER.failedToAddFile(nextOpenedFile);
      }
   }

   public void closeFile(final JournalFile file, boolean block) throws Exception {
      fileFactory.deactivateBuffer();
      file.getFile().close(true, block);
      if (!dataFiles.contains(file)) {
         // This is not a retry from openFile
         // If you don't check this then retries keep adding the same file into
         // dataFiles list and the compactor then re-adds multiple copies of the
         // same file into freeFiles.
         // The consequence of that is that you can end up with the same file
         // twice in a row in the list of openedFiles
         // The consequence of that is that JournalImpl::switchFileIfNecessary
         // will throw throw new IllegalStateException("Invalid logic on buffer allocation")
         // because the file will be checked effectively twice and the buffer will
         // not fit in it
         dataFiles.add(file);
      }
   }

   /**
    * This will get a File from freeFile without initializing it
    *
    * @return uninitialized JournalFile
    * @throws Exception
    * @see JournalImpl#initFileHeader(SequentialFileFactory, SequentialFile, int, long)
    */
   private JournalFile takeFile(final boolean keepOpened,
                               final boolean multiAIO,
                               final boolean initFile,
                               final boolean tmpCompactExtension) throws Exception {
      JournalFile nextFile = null;

      nextFile = freeFiles.poll();

      if (nextFile != null) {
         freeFilesCount.getAndDecrement();
      }

      if (nextFile == null) {
         nextFile = createFile(keepOpened, multiAIO, initFile, tmpCompactExtension, -1);
      } else {
         if (tmpCompactExtension) {
            SequentialFile sequentialFile = nextFile.getFile();
            sequentialFile.renameTo(sequentialFile.getFileName() + ".cmp");
         }

         if (keepOpened) {
            openFile(nextFile, multiAIO);
         }
      }
      return nextFile;
   }

   /**
    * Creates files for journal synchronization of a replicated backup.
    *
    * In order to simplify synchronization, the file IDs in the backup match those in the live
    * server.
    *
    * @param fileID the fileID to use when creating the file.
    */
   public JournalFile createRemoteBackupSyncFile(long fileID) throws Exception {
      return createFile(false, false, true, false, fileID);
   }

   /**
    * This method will create a new file on the file system, pre-fill it with FILL_CHARACTER
    *
    * @param keepOpened
    * @return an initialized journal file
    * @throws Exception
    */
   private JournalFile createFile(final boolean keepOpened,
                                  final boolean multiAIO,
                                  final boolean init,
                                  final boolean tmpCompact,
                                  final long fileIdPreSet) throws Exception {
      if (System.getSecurityManager() == null) {
         return createFile0(keepOpened, multiAIO, init, tmpCompact, fileIdPreSet);
      } else {
         try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<JournalFile>() {
               @Override
               public JournalFile run() throws Exception {
                  return createFile0(keepOpened, multiAIO, init, tmpCompact, fileIdPreSet);
               }
            });
         } catch (PrivilegedActionException e) {
            throw unwrapException(e);
         }
      }
   }

   private RuntimeException unwrapException(PrivilegedActionException e) throws Exception {
      Throwable c = e.getCause();
      if (c instanceof RuntimeException) {
         throw (RuntimeException) c;
      } else if (c instanceof Error) {
         throw (Error) c;
      } else {
         throw new RuntimeException(c);
      }
   }

   private JournalFile createFile0(final boolean keepOpened,
                                   final boolean multiAIO,
                                   final boolean init,
                                   final boolean tmpCompact,
                                   final long fileIdPreSet) throws Exception {
      long fileID = fileIdPreSet != -1 ? fileIdPreSet : generateFileID();

      final String fileName = createFileName(tmpCompact, fileID);

      if (logger.isTraceEnabled()) {
         logger.trace("Creating file " + fileName);
      }

      String tmpFileName = fileName + ".tmp";

      SequentialFile sequentialFile = fileFactory.createSequentialFile(tmpFileName);

      sequentialFile.open(1, false);

      if (init) {
         sequentialFile.fill(fileSize);

         JournalImpl.initFileHeader(fileFactory, sequentialFile, userVersion, fileID);
      }

      long position = sequentialFile.position();

      sequentialFile.close(false, false);

      if (logger.isTraceEnabled()) {
         logger.trace("Renaming file " + tmpFileName + " as " + fileName);
      }

      sequentialFile.renameTo(fileName);

      if (keepOpened) {
         if (multiAIO) {
            sequentialFile.open();
         } else {
            sequentialFile.open(1, false);
         }
         sequentialFile.position(position);
      }

      return new JournalFileImpl(sequentialFile, fileID, JournalImpl.FORMAT_VERSION);
   }

   /**
    * @param tmpCompact
    * @param fileID
    * @return
    */
   private String createFileName(final boolean tmpCompact, final long fileID) {
      String fileName;
      if (tmpCompact) {
         fileName = filePrefix + "-" + fileID + "." + fileExtension + ".cmp";
      } else {
         fileName = filePrefix + "-" + fileID + "." + fileExtension;
      }
      return fileName;
   }

   private long generateFileID() {
      return nextFileID.incrementAndGet();
   }

   /**
    * Get the ID part of the name
    */
   public static long getFileNameID(String filePrefix, final String fileName) {
      try {
         return Long.parseLong(fileName.substring(filePrefix.length() + 1, fileName.indexOf('.')));
      } catch (Throwable e) {
         try {
            return Long.parseLong(fileName.substring(fileName.lastIndexOf("-") + 1, fileName.indexOf('.')));
         } catch (Throwable e2) {
            ActiveMQJournalLogger.LOGGER.errorRetrievingID(e, fileName);
         }
         return 0;
      }
   }

   // Discard the old JournalFile and set it with a new ID
   private JournalFile reinitializeFile(final JournalFile file) throws Exception {
      long newFileID = generateFileID();

      SequentialFile sf = file.getFile();

      sf.open(1, false);

      int position = JournalImpl.initFileHeader(fileFactory, sf, userVersion, newFileID);

      JournalFile jf = new JournalFileImpl(sf, newFileID, JournalImpl.FORMAT_VERSION);

      sf.position(position);

      sf.close(false, false);

      return jf;
   }

   @Override
   public String toString() {
      return "JournalFilesRepository(dataFiles=" + dataFiles + ", freeFiles=" + freeFiles + ", openedFiles=" +
         openedFiles + ")";
   }
}
