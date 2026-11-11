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

import java.io.File;
import java.util.HashMap;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelativePathTest extends ActiveMQTestBase {

   @Test
   public void testRelativePathOnDefaultConfig() throws Exception {
      Configuration configuration = createDefaultConfig(false);
      ActiveMQServer server = createServer(true, configuration, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES, new HashMap<>());

      server.start();
      server.stop();

      checkData(new File(configuration.getJournalDirectory()), ".amq");
      checkData(new File(configuration.getBindingsDirectory()), ".bindings");
   }

   @Test
   public void testDataOutsideHome() throws Exception {
      Configuration configuration = createDefaultConfig(false);

      File instanceHome = new File(getTemporaryDir(), "artemisHome");

      configuration.setBrokerInstance(instanceHome);

      // the journal should be outside of the artemisInstance on this case
      File journalOutside = new File(getTemporaryDir(), "./journalOut").getAbsoluteFile();
      configuration.setJournalDirectory(journalOutside.getAbsolutePath());

      // Somewhere inside artemis.instance
      configuration.setBindingsDirectory("./bind");

      File bindingsInside = new File(instanceHome, "bind");

      //      configuration.setJournal

      ActiveMQServer server = createServer(true, configuration, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES, new HashMap<>());

      server.start();
      server.stop();

      checkData(journalOutside, ".amq");
      // Checking if the journal created the lock as well
      checkData(journalOutside, "server.lock");
      checkData(bindingsInside, ".bindings");
   }

   @Test
   public void testRelativePath() throws Exception {
      Configuration configuration = createDefaultConfig(false);

      File instanceHome = new File(getTemporaryDir(), "artemisHome");
      File dataHome = new File(instanceHome, "data");
      // One folder up for testing
      File bindingsHome = new File(instanceHome, "../binx");

      instanceHome.mkdirs();
      configuration.setBrokerInstance(instanceHome);

      configuration.setJournalDirectory("./data");
      configuration.setPagingDirectory("./paging");
      configuration.setBindingsDirectory("../binx");
      // one folder up from instance home
      configuration.setLargeMessagesDirectory("./large");

      ActiveMQServer server = createServer(true, configuration, AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES, new HashMap<>());

      server.start();
      server.stop();

      checkData(dataHome, ".amq");
      checkData(bindingsHome, ".bindings");
   }

   public void checkData(File dataHome, final String extension) {
      assertTrue(dataHome.exists(), "Folder " + dataHome + " doesn't exist");

      File[] files = dataHome.listFiles(pathname -> (extension == null || pathname.toString().endsWith(extension)));

      assertNotNull(files);

      assertTrue(files.length > 0);
   }
}
