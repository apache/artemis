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
package org.apache.activemq.artemis.core.io;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.core.io.buffer.TimedBuffer;
import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;

/**
 * An abstract SequentialFileFactory containing basic functionality for both AIO and NIO SequentialFactories
 */
public abstract class AbstractSequentialFileFactory implements SequentialFileFactory {

   // Timeout used to wait executors to shutdown
   protected static final int EXECUTOR_TIMEOUT = 60;

   protected final File journalDir;

   protected final TimedBuffer timedBuffer;

   protected final int bufferSize;

   protected final long bufferTimeout;

   protected final int maxIO;

   protected boolean dataSync = true;

   protected volatile int alignment = -1;

   protected IOCriticalErrorListener critialErrorListener;

   protected final CriticalAnalyzer criticalAnalyzer;

   protected AbstractSequentialFileFactory(final File journalDir,
                                           final boolean buffered,
                                           final int bufferSize,
                                           final int bufferTimeout,
                                           final int maxIO,
                                           final boolean logRates,
                                           final IOCriticalErrorListener criticalErrorListener,
                                           CriticalAnalyzer criticalAnalyzer) {
      this.journalDir = journalDir;

      if (criticalAnalyzer == null) {
         criticalAnalyzer = EmptyCriticalAnalyzer.getInstance();
      }

      this.criticalAnalyzer = criticalAnalyzer;

      if (buffered && bufferTimeout > 0) {
         timedBuffer = new TimedBuffer(criticalAnalyzer, bufferSize, bufferTimeout, logRates);
         criticalAnalyzer.add(timedBuffer);
      } else {
         timedBuffer = null;
      }
      this.bufferSize = bufferSize;
      this.bufferTimeout = bufferTimeout;
      this.critialErrorListener = criticalErrorListener;
      this.maxIO = maxIO;
   }

   @Override
   public IOCriticalErrorListener getCriticalErrorListener() {
      return critialErrorListener;
   }

   @Override
   public void setCriticalErrorListener(IOCriticalErrorListener listener) {
      this.critialErrorListener = listener;
   }

   @Override
   public CriticalAnalyzer getCriticalAnalyzer() {
      return criticalAnalyzer;
   }

   @Override
   public long getBufferSize() {
      return bufferSize;
   }

   @Override
   public int getAlignment() {
      if (alignment < 0) {
         alignment = 1;
      }
      return alignment;
   }

   @Override
   public AbstractSequentialFileFactory setAlignment(int alignment) {
      this.alignment = alignment;
      return this;
   }


   @Override
   public SequentialFileFactory setDatasync(boolean enabled) {
      this.dataSync = enabled;
      return this;
   }

   @Override
   public boolean isDatasync() {
      return dataSync;
   }


   @Override
   public void stop() {
      if (timedBuffer != null) {
         timedBuffer.stop();
      }
   }

   @Override
   public File getDirectory() {
      return journalDir;
   }

   @Override
   public void start() {
      if (timedBuffer != null) {
         timedBuffer.start();
      }
   }

   @Override
   public int getMaxIO() {
      return maxIO;
   }

   @Override
   public void onIOError(Throwable exception, String message, String file) {
      if (file != null) {
         ActiveMQJournalLogger.LOGGER.criticalIOFile(message, file, exception);
      } else {
         ActiveMQJournalLogger.LOGGER.criticalIO(message, exception);
      }

      if (critialErrorListener != null) {
         critialErrorListener.onIOException(exception, message, file);
      }
   }

   @Override
   public void activateBuffer(final SequentialFile file) {
      if (timedBuffer != null) {
         file.setTimedBuffer(timedBuffer);
      }
   }

   @Override
   public void flush() {
      if (timedBuffer != null) {
         timedBuffer.flush();
      }
   }

   @Override
   public void deactivateBuffer() {
      if (timedBuffer != null) {
         // When moving to a new file, we need to make sure any pending buffer will be transferred to the buffer
         timedBuffer.flush();
         timedBuffer.setObserver(null);
      }
   }

   @Override
   public void releaseBuffer(final ByteBuffer buffer) {
   }

   /**
    * Create the directory if it doesn't exist yet
    */
   @Override
   public void createDirs() throws Exception {
      boolean ok = journalDir.mkdirs();
      if (!ok && !journalDir.exists()) {
         IOException e = new IOException("Unable to create directory: " + journalDir);
         onIOError(e, e.getMessage());
         throw e;
      }
   }

   @Override
   public List<String> listFiles(final String extension) throws Exception {
      FilenameFilter fnf;

      if (extension != null) {
         fnf = (file, name) -> name.endsWith("." + extension);
      } else {
         fnf = null;
      }

      String[] fileNames = journalDir.list(fnf);

      if (fileNames == null) {
         return Collections.emptyList();
      }

      return Arrays.asList(fileNames);
   }

   @Override
   public boolean deleteFolder() {
      return this.journalDir.delete();
   }
}
