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
package org.apache.activemq.artemis.core.server.files;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This will keep a list of fileStores. It will make a comparison on all file stores registered. if any is over the
 * limit, all Callbacks will be called with over.
 * <p>
 * For instance: if Large Messages folder is registered on a different folder and it's over capacity, the whole system
 * will be waiting it to be released.
 */
public class FileStoreMonitor extends ActiveMQScheduledComponent {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Set<Callback> callbackList = new HashSet<>();
   private final Set<FileStore> stores = new HashSet<>();
   private Double maxUsage;
   private Long minDiskFree;
   private final Object monitorLock = new Object();
   private final IOCriticalErrorListener ioCriticalErrorListener;
   private final FileStoreMonitorType type;

   public FileStoreMonitor(ScheduledExecutorService scheduledExecutorService,
                           Executor executor,
                           long checkPeriod,
                           TimeUnit timeUnit,
                           Number referenceValue,
                           IOCriticalErrorListener ioCriticalErrorListener,
                           FileStoreMonitorType type) {
      super(scheduledExecutorService, executor, checkPeriod, timeUnit, false);
      this.type = type;
      if (type == FileStoreMonitorType.MaxDiskUsage) {
         this.maxUsage = referenceValue.doubleValue();
      } else {
         this.minDiskFree = referenceValue.longValue();
      }
      this.ioCriticalErrorListener = ioCriticalErrorListener;
   }

   public FileStoreMonitor addCallback(Callback callback) {
      synchronized (monitorLock) {
         callbackList.add(callback);
         return this;
      }
   }

   public FileStoreMonitor removeCallback(Callback callback) {
      synchronized (monitorLock) {
         callbackList.remove(callback);
      }
      return this;
   }

   public FileStoreMonitor addStore(File file) throws IOException {
      synchronized (monitorLock) {
         // JDBC storage may return this as null, and we may need to ignore it
         if (file != null && file.exists()) {
            try {
               addStore(Files.getFileStore(file.toPath()));
            } catch (IOException e) {
               logger.error("Error getting file store for {}", file.getAbsolutePath(), e);
               throw e;
            }
         }
         return this;
      }
   }

   public FileStoreMonitor addStore(FileStore store) {
      synchronized (monitorLock) {
         stores.add(store);
         return this;
      }
   }

   @Override
   public void run() {
      tick();
   }

   public void tick() {
      synchronized (monitorLock) {
         boolean ok = true;
         long usableSpace = 0;
         long totalSpace = 0;

         for (FileStore store : stores) {
            try {
               usableSpace = store.getUsableSpace();
               totalSpace = getTotalSpace(store);
               if (type == FileStoreMonitorType.MinDiskFree) {
                  ok = usableSpace > minDiskFree;
               } else {
                  ok = calculateUsage(usableSpace, totalSpace) < maxUsage;
               }
               if (!ok) {
                  break;
               }
            } catch (IOException ioe) {
               ioCriticalErrorListener.onIOException(ioe, "IO Error while calculating disk usage", null);
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
            }
         }

         for (Callback callback : callbackList) {
            callback.tick(usableSpace, totalSpace, ok, type);
         }
      }
   }

   public double getMaxUsage() {
      return maxUsage;
   }

   public long getMinDiskFree() {
      return minDiskFree;
   }

   public FileStoreMonitor setMaxUsage(double maxUsage) {
      this.maxUsage = maxUsage;
      return this;
   }

   public FileStoreMonitor setMinDiskFree(long minDiskFree) {
      this.minDiskFree = minDiskFree;
      return this;
   }

   public static double calculateUsage(long usableSpace, long totalSpace) {
      if (totalSpace == 0) {
         return 0.0;
      }
      return 1.0 - (double) usableSpace / (double) totalSpace;
   }

   private long getTotalSpace(FileStore store) throws IOException {
      long totalSpace = store.getTotalSpace();
      if (totalSpace < 0) {
         totalSpace = Long.MAX_VALUE;
      }
      return totalSpace;
   }

   public interface Callback {
      void tick(long usableSpace, long totalSpace, boolean withinLimit, FileStoreMonitorType type);
   }

   public enum FileStoreMonitorType {
      MaxDiskUsage, MinDiskFree;
   }
}
