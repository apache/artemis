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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;

public final class SimpleWaitIOCallback extends SyncIOCompletion {

   private final CountDownLatch latch = new CountDownLatch(1);

   private volatile String errorMessage;

   private volatile int errorCode = 0;

   public SimpleWaitIOCallback() {
   }

   @Override
   public String toString() {
      return SimpleWaitIOCallback.class.getName();
   }

   @Override
   public void done() {
      latch.countDown();
   }

   @Override
   public void onError(final int errorCode1, final String errorMessage1) {
      this.errorCode = errorCode1;

      this.errorMessage = errorMessage1;

      ActiveMQJournalLogger.LOGGER.errorOnIOCallback(errorMessage1);

      latch.countDown();
   }

   @Override
   public void waitCompletion() throws InterruptedException, ActiveMQException {
      while (true) {
         if (latch.await(2, TimeUnit.SECONDS)) {
            break;
         }
      }

      if (errorMessage != null) {
         throw ActiveMQExceptionType.createException(errorCode, errorMessage);
      }

      return;
   }

   public boolean waitCompletion(final long timeout) throws InterruptedException, ActiveMQException {
      boolean retValue = latch.await(timeout, TimeUnit.MILLISECONDS);

      if (errorMessage != null) {
         throw ActiveMQExceptionType.createException(errorCode, errorMessage);
      }

      return retValue;
   }

   @Override
   public void storeLineUp() {
   }
}
