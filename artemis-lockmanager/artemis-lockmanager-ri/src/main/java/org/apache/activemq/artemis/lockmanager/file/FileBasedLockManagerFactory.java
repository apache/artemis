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
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.lockmanager.DistributedLockManager;
import org.apache.activemq.artemis.lockmanager.DistributedLockManagerFactory;

/**
 * Factory for creating file-based distributed lock managers.
 * <p>
 * This implementation uses the file system to manage distributed locks
 * <p>
 * Valid configuration parameters:
 * <ul>
 *   <li><b>locks-folder</b> (required): Path to the directory where lock files will be created and managed.
 *   The directory must be created in advance before using this lock manager.</li>
 * </ul>
 */
public class FileBasedLockManagerFactory implements DistributedLockManagerFactory {

   private static final String LOCK_FOLDER = "locks-folder";

   private static final Set<String> VALID_PARAMS = Set.of(LOCK_FOLDER);

   @Override
   public String getName() {
      return "file";
   }

   @Override
   public DistributedLockManager build(Map<String, String> config) {
      config = validateParameters(config);
      String folder = config.get(LOCK_FOLDER);
      if (folder == null) {
         throw new IllegalArgumentException("folder not passed as a parameter");
      }
      return new FileBasedLockManager(new File(folder));
   }

   @Override
   public Set<String> getValidParametersList() {
      return VALID_PARAMS;
   }

   @Override
   public String getImplName() {
      return FileBasedLockManager.class.getName();
   }
}
