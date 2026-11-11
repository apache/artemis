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
package org.apache.activemq.artemis.cli.commands.tools;

import java.io.File;

import picocli.CommandLine.Option;

/**
 * This is for commands where --f on ignoring lock could be valid.
 */
public class OptionalLocking extends LockAbstract {

   @Option(names = "--f", description = "This will allow certain tools like print-data to be performed ignoring any running servers. WARNING: Changing data concurrently with a running broker may damage your data. Be careful with this option.")
   boolean ignoreLock;

   public boolean isIgnoreLock() {
      return ignoreLock;
   }

   public OptionalLocking setIgnoreLock(boolean ignoreLock) {
      this.ignoreLock = ignoreLock;
      return this;
   }

   @Override
   protected void lockCLI(File lockPlace) throws Exception {
      if (!ignoreLock) {
         super.lockCLI(lockPlace);
      }
   }

}
