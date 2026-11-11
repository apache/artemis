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

package org.apache.activemq.artemis.tests.db.paging;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class SchemaValidationTest extends ParameterDBTestBase {

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      return convertParameters(Database.selectedList());
   }

   @TestTemplate
   public void testTableNameTooLong() throws Exception {

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler(true)) {
         final Configuration config = createDefaultInVMConfig();
         final DatabaseStorageConfiguration storageConfiguration = (DatabaseStorageConfiguration) config.getStoreConfiguration();
         //set the page store table to be longer than 10 chars -> the paging manager initialization will fail
         storageConfiguration.setPageStoreTableName("PAGE_STORE_");

         final int PAGE_MAX = 20 * 1024;

         final int PAGE_SIZE = 10 * 1024;

         final ActiveMQServer server = createServer(true, config, PAGE_SIZE, PAGE_MAX);
         server.start();

         //due to a failed initialisation of the paging manager, it must be null
         assertNull(server.getPagingManager());

         server.stop();

         assertTrue(loggerHandler.findText("AMQ224000")); // Failure in initialization
      }
   }

}
