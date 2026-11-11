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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.junit.jupiter.api.Test;

public class VerifyUpdateFactorSystemPropertyTest {

   public static void main(String[] arg) {

      try {
         assertEquals(33.0, JournalImpl.UPDATE_FACTOR, 0);
         System.exit(0);
      } catch (Throwable e) {
         e.printStackTrace();
         System.exit(100);
      }
   }

   @Test
   public void testValidateUpdateRecordProperty() throws Exception {
      Process process = SpawnedVMSupport.spawnVM(VerifyUpdateFactorSystemPropertyTest.class.getName(), new String[]{"-D" + JournalImpl.class.getName() + ".UPDATE_FACTOR=33.0"}, new String[]{});
      assertEquals(0, process.waitFor());
   }

}
