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
package org.apache.activemq.artemis.lockmanager.file;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;

/**
 * file-based distributed lock manager.
 * <p>
 * This implementation uses the file system to manage distributed locks
 * <p>
 * Valid configuration parameters:
 * <ul>
 *   <li><b>locks-folder</b> (required): Path to the directory where lock files will be created and managed.
 *   The directory must be created in advance before using this lock manager.</li>
 * </ul>
 */
public class FileBasedLockManager implements DistributedLockManager {

   private final File locksFolder;
   private final Map<String, FileDistributedLock> locks;
   private boolean started;

   public FileBasedLockManager(Map<String, String> args) {
      this(new File(args.get("locks-folder")));
   }

   public FileBasedLockManager(File locksFolder) {
      Objects.requireNonNull(locksFolder);
      if (!locksFolder.exists()) {
         throw new IllegalStateException(locksFolder + " is supposed to already exists");
      }
      if (!locksFolder.isDirectory()) {
         throw new IllegalStateException(locksFolder + " is supposed to be a directory");
      }
      this.locksFolder = locksFolder;
      this.locks = new HashMap<>();
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public void addUnavailableManagerListener(UnavailableManagerListener listener) {
      // noop
   }

   @Override
   public void removeUnavailableManagerListener(UnavailableManagerListener listener) {
      // noop
   }

   @Override
   public boolean start(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
      if (timeout >= 0) {
         Objects.requireNonNull(unit);
      }
      if (started) {
         return true;
      }
      started = true;
      return true;
   }

   @Override
   public void start() throws InterruptedException, ExecutionException {
      start(-1, null);
   }

   @Override
   public void stop() {
      if (!started) {
         return;
      }
      try {
         locks.forEach((lockId, lock) -> {
            try {
               lock.close(false);
            } catch (Throwable t) {
               // TODO no op for now: log would be better!
            }
         });
         locks.clear();
      } finally {
         started = false;
      }
   }

   @Override
   public DistributedLock getDistributedLock(String lockId) throws ExecutionException {
      Objects.requireNonNull(lockId);
      if (!started) {
         throw new IllegalStateException("manager should be started first");
      }
      final FileDistributedLock lock = locks.get(lockId);
      if (lock != null && !lock.isClosed()) {
         return lock;
      }
      try {
         final FileDistributedLock newLock = new FileDistributedLock(locks::remove, locksFolder, lockId);
         locks.put(lockId, newLock);
         return newLock;
      } catch (IOException ioEx) {
         throw new ExecutionException(ioEx);
      }
   }

   @Override
   public MutableLong getMutableLong(final String mutableLongId) throws ExecutionException {
      // use a lock file - but with a prefix
      final FileDistributedLock fileDistributedLock = (FileDistributedLock) getDistributedLock("ML:" + mutableLongId);
      return new MutableLong() {
         @Override
         public String getMutableLongId() {
            return mutableLongId;
         }

         @Override
         public long get() throws UnavailableStateException {
            try {
               return readLong(fileDistributedLock);
            } catch (IOException e) {
               throw new UnavailableStateException(e);
            }
         }

         @Override
         public void set(long value) throws UnavailableStateException {
            try {
               writeLong(fileDistributedLock, value);
            } catch (IOException e) {
               throw new UnavailableStateException(e);
            }
         }

         @Override
         public void close() {
            fileDistributedLock.close();
         }
      };
   }

   private void writeLong(FileDistributedLock fileDistributedLock, long value) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
      buffer.putLong(value);
      buffer.flip();
      if (fileDistributedLock.getChannel().position(0).write(buffer) == Long.BYTES) {
         fileDistributedLock.getChannel().force(false);
      }
   }

   private long readLong(FileDistributedLock fileDistributedLock) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
      if (fileDistributedLock.getChannel().position(0).read(buffer, 0) != Long.BYTES) {
         return 0;
      }
      buffer.flip();
      return buffer.getLong();
   }
}
