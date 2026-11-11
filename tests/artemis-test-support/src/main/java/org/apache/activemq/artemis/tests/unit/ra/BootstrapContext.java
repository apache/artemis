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
package org.apache.activemq.artemis.tests.unit.ra;

import javax.resource.spi.UnavailableException;
import javax.resource.spi.XATerminator;
import javax.resource.spi.work.ExecutionContext;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkContext;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkListener;
import javax.resource.spi.work.WorkManager;
import javax.transaction.TransactionSynchronizationRegistry;
import java.util.Timer;

public class BootstrapContext implements javax.resource.spi.BootstrapContext {

   @Override
   public Timer createTimer() throws UnavailableException {
      return null;
   }

   @Override
   public boolean isContextSupported(Class<? extends WorkContext> aClass) {
      return false;
   }

   @Override
   public TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() {
      return null;
   }

   @Override
   public WorkManager getWorkManager() {
      return new WorkManager() {
         @Override
         public void doWork(final Work work) throws WorkException {
         }

         @Override
         public void doWork(final Work work,
                            final long l,
                            final ExecutionContext executionContext,
                            final WorkListener workListener) throws WorkException {
         }

         @Override
         public long startWork(final Work work) throws WorkException {
            return 0;
         }

         @Override
         public long startWork(final Work work,
                               final long l,
                               final ExecutionContext executionContext,
                               final WorkListener workListener) throws WorkException {
            return 0;
         }

         @Override
         public void scheduleWork(final Work work) throws WorkException {
            work.run();
         }

         @Override
         public void scheduleWork(final Work work,
                                  final long l,
                                  final ExecutionContext executionContext,
                                  final WorkListener workListener) throws WorkException {
         }
      };
   }

   @Override
   public XATerminator getXATerminator() {
      return null;
   }
}