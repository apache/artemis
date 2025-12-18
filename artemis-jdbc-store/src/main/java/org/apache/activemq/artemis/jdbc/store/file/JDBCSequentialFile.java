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
package org.apache.activemq.artemis.jdbc.store.file;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.buffer.TimedBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.impl.SimpleWaitIOCallback;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBCSequentialFile implements SequentialFile {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String filename;

   private final String extension;

   private AtomicBoolean isOpen = new AtomicBoolean(false);

   private AtomicBoolean isLoaded = new AtomicBoolean(false);

   private volatile boolean deleted = false;

   private long id = -1;

   // used in postgres
   private long oid = -1;

   private long readPosition = 0;

   private long writePosition = 0;

   private final Executor executor;

   private final JDBCSequentialFileFactory fileFactory;

   private final JDBCSequentialFileFactoryDriver dbDriver;

   MpscUnboundedArrayQueue<ScheduledWrite> writeQueue = new MpscUnboundedArrayQueue<>(8192);

   // Allows DB Drivers to cache meta data.
   private final Map<Object, Object> metaData = new ConcurrentHashMap<>();

   final JDBCPageWriteScheduler pageWriteScheduler;

   final ScheduledExecutorService scheduledExecutorService;

   private final ReusableLatch pendingWrites = new ReusableLatch();

   final long syncDelay;

   JDBCSequentialFile(final JDBCSequentialFileFactory fileFactory,
                      final String filename,
                      final Executor executor,
                      final ScheduledExecutorService scheduledExecutorService,
                      final long syncDelay,
                      final JDBCSequentialFileFactoryDriver driver) throws SQLException {
      this.fileFactory = fileFactory;
      this.filename = filename;
      this.extension = filename.contains(".") ? filename.substring(filename.lastIndexOf(".") + 1, filename.length()) : "";
      this.executor = executor;
      this.dbDriver = driver;
      this.scheduledExecutorService = scheduledExecutorService;
      this.syncDelay = syncDelay;
      this.pageWriteScheduler = new JDBCPageWriteScheduler(scheduledExecutorService, executor, syncDelay);
   }

   void setWritePosition(long writePosition) {
      this.writePosition = writePosition;
   }

   @Override
   public boolean isOpen() {
      return isOpen.get();
   }

   @Override
   public boolean exists() {
      try {
         return dbDriver.getFileID(this) >= 0;
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         return false;
      }
   }

   @Override
   public void open() throws Exception {
      isOpen.compareAndSet(false, load());
   }

   private boolean load() {
      try {
         if (isLoaded.compareAndSet(false, true)) {
            dbDriver.openFile(this);
         }
         return true;
      } catch (SQLException e) {
         isLoaded.set(false);
         // should not throw exceptions, as we drop the table on queue.destroy.
         // storage.exists could be called for non existing pages during async cleanup and they are
         // just supposed to return false
      }
      return false;
   }

   @Override
   public void open(int maxIO, boolean useExecutor) throws Exception {
      open();
   }

   @Override
   public boolean fits(int size) {
      return writePosition + size <= dbDriver.getMaxSize();
   }

   @Override
   public int calculateBlockStart(int position) throws Exception {
      return 0;
   }

   @Override
   public String getFileName() {
      return filename;
   }

   @Override
   public void fill(int size) throws Exception {
      // Do nothing
   }

   @Override
   public ByteBuffer map(int position, long size) throws IOException {
      return null;
   }

   @Override
   public void delete() throws IOException, InterruptedException, ActiveMQException {
      try {
         synchronized (this) {
            if (deleted) {
               return;
            }
            if (load()) {
               dbDriver.deleteFile(this);
            }
            deleted = true;
         }
      } catch (SQLException e) {
         // file is already gone from a drop somewhere
         logger.debug("Expected error deleting Sequential File", e);
         return;
      }
   }

   private synchronized int jdbcWrite(byte[] data, IOCallback callback, boolean append) {
      try {
         logger.debug("Writing {} bytes into {}", data.length, filename);
         synchronized (this) {
            int noBytes = dbDriver.writeToFile(this, data, append);
            seek(append ? writePosition + noBytes : noBytes);
            if (logger.isTraceEnabled()) {
               logger.trace("Write: ID: {} FileName: {}{}", getId(), getFileName(), size());
            }
            if (callback != null)
               callback.done();
            return noBytes;
         }
      } catch (Exception e) {
         if (callback != null)
            callback.onError(ActiveMQExceptionType.IO_ERROR.getCode(), e.getClass() + " during JDBC write:" + e.getMessage());
         fileFactory.onIOError(e, "Error writing to JDBC file.", this);
      }
      return 0;
   }

   public synchronized int jdbcWrite(ActiveMQBuffer buffer, IOCallback callback) {
      return jdbcWrite(buffer, callback, true);
   }

   public synchronized int jdbcWrite(ActiveMQBuffer buffer, IOCallback callback, boolean append) {
      byte[] data = new byte[buffer.readableBytes()];
      buffer.readBytes(data);
      return jdbcWrite(data, callback, append);
   }



   private void pollWrites() {
      if (writeQueue.isEmpty()) {
         return;
      }

      logger.debug("polling {} elements on {}", writeQueue.size(), this.filename);

      List<ScheduledWrite> writeList = new ArrayList<>(writeQueue.size()); // the size here is just an estimate

      byte[] bytes = extractBytes(writeList);

      jdbcWrite(bytes, null, true);
      writeList.forEach(this::doCallback);
   }

   /*
    * Even though I would love to have a reusable byte[] for the following buffer PreparedStatement.setData takes a
    * byte[] without any sizing on the interface. Blob interface would support setBytes with an offset and size, but
    * some of the databases we are using (DB2 specifically) is not allowing us to use Blob (at least during our dev
    * time). For that reason I'm using this byte[] with the very specific size that needs to be written
    *
    * Also Notice that our PagingManager will make sure that this size wouldn't go beyond our page-size limit which we
    * also limit at the JDBC storage, which should be 100K.
    */
   private byte[] extractBytes(List<ScheduledWrite> writeList) {
      int totalSize = 0;
      ScheduledWrite write;
      while ((write = writeQueue.poll()) != null) {
         writeList.add(write);
         totalSize += write.readable();
      }
      byte[] bytes = new byte[totalSize];

      int writePosition = 0;

      for (ScheduledWrite el : writeList) {
         writePosition += el.readAt(bytes, writePosition);
         el.releaseBuffer();
      }

      return bytes;
   }

   private void doCallback(ScheduledWrite write) {
      if (write != null && write.callback != null) {
         write.callback.done();
      }
      pendingWrites.countDown();
   }

   private void scheduleWrite(final ActiveMQBuffer bytes, final IOCallback callback, boolean append) {
      scheduleWrite(new ScheduledWrite(bytes, callback, append));
   }

   private void scheduleWrite(ScheduledWrite scheduledWrite) {
      logger.debug("offering {} bytes into {}", scheduledWrite.readable(), filename);
      pendingWrites.countUp();
      writeQueue.offer(scheduledWrite);
      this.pageWriteScheduler.delay();
   }

   private void scheduleWrite(final ByteBuffer bytes, final IOCallback callback) {
      scheduleWrite(new ScheduledWrite(bytes, callback, true));
   }

   synchronized void seek(long noBytes) {
      writePosition = noBytes;
   }

   public void sendToDB(ActiveMQBuffer bytes, IOCallback callback, boolean append) throws Exception {
      SimpleWaitIOCallback waitIOCallback = null;

      if (callback == null) {
         waitIOCallback = new SimpleWaitIOCallback();
         callback = waitIOCallback;
      }

      scheduleWrite(bytes, callback, append);

      if (callback != null) {
         waitIOCallback.waitCompletion();
      }
   }

   @Override
   public void write(ActiveMQBuffer bytes, boolean sync, IOCallback callback) throws Exception {
      sendToDB(bytes, callback, true);
   }

   @Override
   public void write(ActiveMQBuffer bytes, boolean sync) throws Exception {
      write(bytes, sync, null);
   }

   @Override
   public void write(EncodingSupport bytes, boolean sync, IOCallback callback) throws Exception {
      ActiveMQBuffer data = ActiveMQBuffers.fixedBuffer(bytes.getEncodeSize());
      bytes.encode(data);
      sendToDB(data, callback, true);
   }

   @Override
   public void write(EncodingSupport bytes, boolean sync) throws Exception {
      write(bytes, sync, null);
   }

   @Override
   public void writeDirect(ByteBuffer bytes, boolean sync, IOCallback callback) {
      if (callback == null) {
         final SimpleWaitIOCallback waitIOCallback = sync ? new SimpleWaitIOCallback() : null;
         try {
            scheduleWrite(bytes, waitIOCallback);
            if (waitIOCallback != null) {
               waitIOCallback.waitCompletion();
            }
         } catch (Exception e) {
            waitIOCallback.onError(ActiveMQExceptionType.IO_ERROR.getCode(), e.getClass() + " during JDBC write direct:" + e.getMessage());
            fileFactory.onIOError(e, "Failed to write to file.", this);
         }
      } else {
         scheduleWrite(bytes, callback);
      }

   }

   @Override
   public void blockingWriteDirect(ByteBuffer bytes, boolean sync, boolean releaseBuffer) {
      writeDirect(bytes, sync, null);
   }

   @Override
   public void writeDirect(ByteBuffer bytes, boolean sync) throws Exception {
      writeDirect(bytes, sync, null);
      // Are we meant to block here?
   }

   @Override
   public synchronized int read(ByteBuffer bytes, final IOCallback callback) throws SQLException {
      synchronized (this) {
         try {
            int read = dbDriver.readFromFile(this, bytes);
            readPosition += read;
            if (callback != null)
               callback.done();
            return read;
         } catch (SQLException e) {
            if (callback != null)
               callback.onError(ActiveMQExceptionType.IO_ERROR.getCode(), e.getClass() + " during JDBC read:" + e.getMessage());
            fileFactory.onIOError(e, "Error reading from JDBC file.", this);
         }
         return 0;
      }
   }

   @Override
   public int read(ByteBuffer bytes) throws Exception {
      return read(bytes, null);
   }

   @Override
   public void position(long pos) throws IOException {
      readPosition = pos;
   }

   @Override
   public long position() {
      return readPosition;
   }


   @Override
   public void close() throws Exception {
      close(true, true);
   }

   @Override
   public void close(boolean waitOnSync, boolean block) throws Exception {
      isOpen.set(false);
      if (waitOnSync) {
         sync();
      }
      fileFactory.sequentialFileClosed(this);
   }

   public int getNetworkTimeoutMillis() {
      return dbDriver.getJdbcConnectionProvider().getNetworkTimeoutMillis();
   }

   @Override
   public void sync() throws IOException {
      try {
         int syncTimeout = getNetworkTimeoutMillis();

         if (syncTimeout >= 0) {
            if (!pendingWrites.await(syncTimeout, TimeUnit.MILLISECONDS)) {
               fileFactory.onIOError(new ActiveMQIOErrorException("Database not responding to syncs before timeout"), "Error during JDBC file sync.", this);
            }
         } else {
            // waiting forever however logger.debug while doing so
            while (!pendingWrites.await(1, TimeUnit.SECONDS)) {
               logger.debug("Awaiting syncs from database for page file {}", this.filename);
            }
         }

      } catch (Exception e) {
         fileFactory.onIOError(e, "Error during JDBC file sync.", this);
      }
   }

   @Override
   public long size() throws Exception {
      load();
      return writePosition;
   }

   @Override
   public void renameTo(String newFileName) throws Exception {
      synchronized (this) {
         try {
            dbDriver.renameFile(this, newFileName);
         } catch (SQLException e) {
            fileFactory.onIOError(e, "Error renaming JDBC file.", this);
         }
      }
   }

   @Override
   public SequentialFile cloneFile() {
      try {
         JDBCSequentialFile clone = new JDBCSequentialFile(fileFactory, filename, executor, scheduledExecutorService, syncDelay, dbDriver);
         clone.setWritePosition(this.writePosition);
         return clone;
      } catch (Exception e) {
         fileFactory.onIOError(e, "Error cloning JDBC file.", this);
      }
      return null;
   }

   @Override
   public void copyTo(SequentialFile cloneFile) throws Exception {
      JDBCSequentialFile clone = (JDBCSequentialFile) cloneFile;
      try {
         synchronized (this) {
            logger.trace("JDBC Copying File.  From: {} To: {}", this, cloneFile);
            clone.open();
            dbDriver.copyFileData(this, clone);
            clone.setWritePosition(writePosition);
         }
      } catch (Exception e) {
         fileFactory.onIOError(e, "Error copying JDBC file.", this);
      }
   }

   public long getId() {
      return id;
   }

   public long getOid() {
      return oid;
   }

   public void setOid(long oid) {
      this.oid = oid;
   }

   public void setId(long id) {
      this.id = id;
   }

   public String getFilename() {
      return filename;
   }

   public String getExtension() {
      return extension;
   }

   // Only Used by Journal, no need to implement.
   @Override
   public void setTimedBuffer(TimedBuffer buffer) {
   }

   // Only Used by replication, no need to implement.
   @Override
   public File getJavaFile() {
      return null;
   }

   public void addMetaData(Object key, Object value) {
      metaData.put(key, value);
   }

   public Object getMetaData(Object key) {
      return metaData.get(key);
   }



   private class JDBCPageWriteScheduler extends ActiveMQScheduledComponent {

      JDBCPageWriteScheduler(ScheduledExecutorService scheduledExecutorService,
                   Executor executor,
                   long checkPeriod) {
         super(scheduledExecutorService, executor, checkPeriod, TimeUnit.MILLISECONDS, true);
      }

      @Override
      public void run() {
         pollWrites();
      }
   }
}
