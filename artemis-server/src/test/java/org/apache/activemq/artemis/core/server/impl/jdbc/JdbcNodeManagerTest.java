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
package org.apache.activemq.artemis.core.server.impl.jdbc;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class JdbcNodeManagerTest extends ServerTestBase {

   @Parameter(index = 0)
   public boolean useAuthentication;
   private DatabaseStorageConfiguration dbConf;
   private ScheduledExecutorService leaseLockExecutor;

   @Parameters(name = "authentication = {0}")
   public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][]{{false}, {true}});
   }

   @BeforeEach
   public void configure() {
      dbConf = createDefaultDatabaseStorageConfiguration();
      dbConf.setJdbcUser(getJDBCUser());
      dbConf.setJdbcPassword(getJDBCPassword());
      leaseLockExecutor = Executors.newSingleThreadScheduledExecutor();
      runAfter(leaseLockExecutor::shutdownNow);
   }


   @Override
   protected String getJDBCUser() {
      if (useAuthentication) {
         return System.getProperty("jdbc.user", "testuser");
      } else {
         return null;
      }
   }

   @Override
   protected String getJDBCPassword() {
      if (useAuthentication) {
         return System.getProperty("jdbc.password", "testpassword");
      } else {
         return null;
      }
   }

   @TestTemplate
   public void shouldStartAndStopGracefullyTest() throws Exception {
      final JdbcNodeManager nodeManager = JdbcNodeManager.with(dbConf, leaseLockExecutor, null);
      nodeManager.start();
      nodeManager.stop();
   }

}
