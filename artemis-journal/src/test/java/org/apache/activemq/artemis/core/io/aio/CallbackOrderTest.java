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
package org.apache.activemq.artemis.core.io.aio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * This will emulate callbacks out of order from libaio
 */
public class CallbackOrderTest {

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public File temporaryFolder;

   /**
    * This method will make sure callbacks will come back in order even when out order from libaio
    */
   @Test
   public void testCallbackOutOfOrder() throws Exception {
      AIOSequentialFileFactory factory = new AIOSequentialFileFactory(temporaryFolder, 100);
      AIOSequentialFile file = (AIOSequentialFile) factory.createSequentialFile("test.bin");

      final AtomicInteger count = new AtomicInteger(0);

      IOCallback callback = new IOCallback() {
         @Override
         public void done() {
            count.incrementAndGet();
         }

         @Override
         public void onError(int errorCode, String errorMessage) {

         }
      };

      List<AIOSequentialFileFactory.AIOSequentialCallback> list = new ArrayList<>();

      // We will repeat the test a few times, increasing N
      // to increase possibility of issues due to reuse of callbacks
      for (int n = 1; n < 100; n++) {
         int N = n;
         count.set(0);
         list.clear();
         for (int i = 0; i < N; i++) {
            list.add(file.getCallback(callback, null));
         }

         for (int i = N - 1; i >= 0; i--) {
            list.get(i).done();
         }

         assertEquals(N, count.get());
         assertEquals(0, file.pendingCallbackList.size());
         assertTrue(file.pendingCallbackList.isEmpty());
      }

      factory.stop();

   }
}
