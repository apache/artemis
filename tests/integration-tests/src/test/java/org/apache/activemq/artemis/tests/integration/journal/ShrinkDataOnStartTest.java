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
package org.apache.activemq.artemis.tests.integration.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

public class ShrinkDataOnStartTest extends ActiveMQTestBase {

   @Test
   public void shrinkDataOnStart() throws Exception {

      ActiveMQServer server = addServer(createServer(true));
      server.getConfiguration().setJournalMinFiles(10);
      server.getConfiguration().setJournalPoolFiles(2);
      server.start();
      Wait.waitFor(server::isActive);
      assertEquals(10, server.getStorageManager().getMessageJournal().getFileFactory().listFiles("amq").size());
      server.stop();
      server.getConfiguration().setJournalMinFiles(2);
      server.getConfiguration().setJournalPoolFiles(2);
      server.start();
      assertEquals(2, server.getStorageManager().getMessageJournal().getFileFactory().listFiles("amq").size());
   }

}
