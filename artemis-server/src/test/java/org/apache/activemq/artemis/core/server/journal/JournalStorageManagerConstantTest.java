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

package org.apache.activemq.artemis.core.server.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.Test;

public class JournalStorageManagerConstantTest {

   @Test
   public void testConstant() {
      Configuration configuration = new ConfigurationImpl();
      configuration.setJournalType(JournalType.NIO);
      configuration.setJournalPoolFiles(11);
      configuration.setJournalCompactMinFiles(22);
      configuration.setJournalCompactPercentage(33);
      JournalStorageManager journalStorageManager = new JournalStorageManager(configuration, null, dumbExecutor, dumbScheduler, dumbExecutor);

      JournalImpl journal = (JournalImpl)journalStorageManager.getBindingsJournal();
      assertJournalConstants(journal);
      journal = (JournalImpl)journalStorageManager.getMessageJournal();
      assertJournalConstants(journal);

   }

   private void assertJournalConstants(JournalImpl journal) {
      assertEquals("0.33", "" + journal.getCompactPercentage());
      assertEquals(22, journal.getCompactMinFiles());
      assertEquals(11, journal.getFilesRepository().getPoolSize());
   }

   ExecutorFactory dumbExecutor = () -> (ArtemisExecutor) command -> command.run();

   ScheduledExecutorService dumbScheduler = new ScheduledExecutorService() {
      @Override
      public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
         return null;
      }

      @Override
      public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
         return null;
      }

      @Override
      public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
         return null;
      }

      @Override
      public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
         return null;
      }

      @Override
      public void shutdown() {

      }

      @Override
      public List<Runnable> shutdownNow() {
         return null;
      }

      @Override
      public boolean isShutdown() {
         return false;
      }

      @Override
      public boolean isTerminated() {
         return false;
      }

      @Override
      public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
         return false;
      }

      @Override
      public <T> Future<T> submit(Callable<T> task) {
         return null;
      }

      @Override
      public <T> Future<T> submit(Runnable task, T result) {
         return null;
      }

      @Override
      public Future<?> submit(Runnable task) {
         return null;
      }

      @Override
      public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
         return null;
      }

      @Override
      public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                           long timeout,
                                           TimeUnit unit) throws InterruptedException {
         return null;
      }

      @Override
      public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
         return null;
      }

      @Override
      public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                             long timeout,
                             TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
         return null;
      }

      @Override
      public void execute(Runnable command) {

      }
   };

}
