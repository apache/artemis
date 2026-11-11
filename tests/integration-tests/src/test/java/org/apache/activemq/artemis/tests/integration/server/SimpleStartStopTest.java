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

package org.apache.activemq.artemis.tests.integration.server;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler.LogLevel;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleStartStopTest extends ActiveMQTestBase {

   /**
    * Start / stopping the server shouldn't generate any errors. Also it shouldn't bloat the journal with lots of IDs
    * (it should do some cleanup when possible)
    * <p>
    * This is also validating that the same server could be restarted after stopped
    */
   @Test
   public void testStartStopAndCleanupIDs() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         ActiveMQServer server = null;

         for (int i = 0; i < 50; i++) {
            server = createServer(true, false);
            server.start();
            server.fail(false);
         }

         // There shouldn't be any error from starting / stopping the server
         assertFalse(loggerHandler.hasLevel(LogLevel.ERROR), "There shouldn't be any error for just starting / stopping the server");
         assertFalse(loggerHandler.findText("AMQ224008"));

         Map<Integer, AtomicInteger> records = this.internalCountJournalLivingRecords(server.getConfiguration(), false);

         AtomicInteger recordCount = records.get((int) JournalRecordIds.ID_COUNTER_RECORD);

         assertNotNull(recordCount);

         // The server should remove old IDs from the journal
         assertTrue(recordCount.intValue() < 5, "The server should cleanup after IDs on the bindings record. It left " + recordCount +
                       " ids on the journal");

         server.start();

         records = this.internalCountJournalLivingRecords(server.getConfiguration(), false);

         recordCount = records.get((int) JournalRecordIds.ID_COUNTER_RECORD);

         assertNotNull(recordCount);

         assertTrue(recordCount.intValue() != 0, "If this is zero it means we are removing too many records");

         server.stop();

      }
   }
}
