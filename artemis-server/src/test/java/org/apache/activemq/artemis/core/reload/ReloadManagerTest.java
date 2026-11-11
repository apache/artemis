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
package org.apache.activemq.artemis.core.reload;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.reload.ReloadCallback;
import org.apache.activemq.artemis.core.server.reload.ReloadManagerImpl;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReloadManagerTest extends ServerTestBase {

   private ScheduledExecutorService scheduledExecutorService;

   private ExecutorService executorService;

   private ReloadManagerImpl manager;

   @BeforeEach
   public void startScheduled() {
      scheduledExecutorService = new ScheduledThreadPoolExecutor(5);
      executorService = Executors.newSingleThreadExecutor();
      manager = new ReloadManagerImpl(scheduledExecutorService, executorService, 100);
   }

   @AfterEach
   public void stopScheduled() {
      manager.stop();
      scheduledExecutorService.shutdown();
      executorService.shutdown();
      scheduledExecutorService = null;
   }

   @Test
   public void testUpdate() throws Exception {

      File file = new File(getTemporaryDir(), "checkFile.tst");
      internalTest(manager, file);

   }

   @Test
   public void testUpdateWithSpace() throws Exception {
      File spaceDir = new File(getTemporaryDir(), "./with %25space");
      spaceDir.mkdirs();
      File file = new File(spaceDir, "checkFile.tst");
      internalTest(manager, file);
   }

   @Test
   public void testUpdateOnDirectory() throws Exception {
      File nested = new File(getTemporaryDir(), "./sub/nested.txt");
      nested.mkdirs();
      nested.createNewFile();

      File parentDir = nested.getParentFile();

      assertTrue(parentDir.isDirectory());

      final ReusableLatch latch = new ReusableLatch(1);

      ReloadCallback reloadCallback = uri -> latch.countDown();
      manager.addCallback(parentDir.toURI().toURL(), reloadCallback);

      assertFalse(latch.await(1, TimeUnit.SECONDS));

      parentDir.setLastModified(System.currentTimeMillis());

      assertTrue(latch.await(1, TimeUnit.SECONDS));

   }

   @Test
   public void testUpdateOnNewNotExistingDirectory() throws Exception {
      final ReusableLatch latch = new ReusableLatch(1);

      ReloadCallback reloadCallback = uri -> latch.countDown();

      // verify not existing dir is not a problem
      File notExistFile = new File(getTemporaryDir(), "./sub2/not-there");
      File notExistDir = notExistFile.getParentFile();

      assertFalse(notExistDir.exists());

      manager.addCallback(notExistDir.toURI().toURL(), reloadCallback);

      assertFalse(latch.await(1, TimeUnit.SECONDS));

      // create that non-existent file now
      notExistFile.mkdirs();
      notExistFile.createNewFile();

      assertTrue(latch.await(1, TimeUnit.SECONDS));
   }

   private void internalTest(ReloadManagerImpl manager, File file) throws IOException, InterruptedException {
      file.createNewFile();

      final ReusableLatch latch = new ReusableLatch(1);

      manager.addCallback(file.toURL(), uri -> latch.countDown());

      assertFalse(latch.await(1, TimeUnit.SECONDS));

      file.setLastModified(System.currentTimeMillis());

      assertTrue(latch.await(1, TimeUnit.SECONDS));
   }
}
