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
package org.apache.activemq.artemis.lockmanager;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.activemq.artemis.utils.ClassloadingUtil;

public interface DistributedLockManager extends AutoCloseable {

   static DistributedLockManager newInstanceOf(String className, Map<String, String> properties) throws Exception {
      return (DistributedLockManager) ClassloadingUtil.getInstanceForParamsWithTypeCheck(className,
                                                                                         DistributedLockManager.class,
                                                                                         DistributedLockManager.class.getClassLoader(),
                                                                                         new Class[]{Map.class},
                                                                                         properties);
   }

   @FunctionalInterface
   interface UnavailableManagerListener {

      void onUnavailableManagerEvent();
   }

   void addUnavailableManagerListener(UnavailableManagerListener listener);

   void removeUnavailableManagerListener(UnavailableManagerListener listener);

   boolean start(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException;

   void start() throws InterruptedException, ExecutionException;

   boolean isStarted();

   void stop();

   DistributedLock getDistributedLock(String lockId) throws InterruptedException, ExecutionException, TimeoutException;

   MutableLong getMutableLong(String mutableLongId) throws InterruptedException, ExecutionException, TimeoutException;

   @Override
   default void close() {
      stop();
   }
}