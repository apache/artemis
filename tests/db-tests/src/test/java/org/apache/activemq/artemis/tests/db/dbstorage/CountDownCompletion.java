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

package org.apache.activemq.artemis.tests.db.dbstorage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.journal.IOCompletion;

public class CountDownCompletion implements IOCompletion {

   public AtomicInteger errors = new AtomicInteger();
   public CountDownLatch latch;

   public CountDownCompletion(int count) {
      latch = new CountDownLatch(count);
   }

   @Override
   public void done() {
      latch.countDown();
   }

   @Override
   public void onError(int errorCode, String errorMessage) {
      errors.incrementAndGet();
      latch.countDown();
   }

   @Override
   public void storeLineUp() {
   }

   public boolean await(long time, TimeUnit unit) throws Exception {
      return latch.await(time, unit);
   }

}
