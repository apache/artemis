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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;

import org.apache.activemq.artemis.lockmanager.DistributedLockTest;
import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileDistributedLockTest extends DistributedLockTest {

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public File tmpFolder;

   private File locksFolder;

   @BeforeEach
   @Override
   public void setupEnv() throws Throwable {
      locksFolder = newFolder(tmpFolder, "locks-folder");
      super.setupEnv();
   }

   @Override
   protected void configureManager(Map<String, String> config) {
      config.put("locks-folder", locksFolder.toString());
   }

   @Override
   protected String managerClassName() {
      return FileBasedLockManager.class.getName();
   }

   @Test
   public void reflectiveManagerCreation() throws Exception {
      DistributedLockManager.newInstanceOf(managerClassName(), Collections.singletonMap("locks-folder", locksFolder.toString()));
   }

   @Test
   public void reflectiveManagerCreationFailWithoutLocksFolder() throws Exception {
      assertThrows(InvocationTargetException.class, () -> {
         DistributedLockManager.newInstanceOf(managerClassName(), Collections.emptyMap());
      });
   }

   @Test
   public void reflectiveManagerCreationFailIfLocksFolderIsNotFolder() throws Exception {
      assertThrows(InvocationTargetException.class, () -> {
         DistributedLockManager.newInstanceOf(managerClassName(), Collections.singletonMap("locks-folder", File.createTempFile("junit", null, tmpFolder).toString()));
      });
   }

   private static File newFolder(File root, String subFolder) throws IOException {
      File result = new File(root, subFolder);
      if (!result.mkdirs()) {
         throw new IOException("Couldn't create folders " + root);
      }
      return result;
   }

}
