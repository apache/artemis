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

package org.apache.activemq.artemis.tests.smoke.jdbccli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.apache.activemq.artemis.cli.commands.tools.DBOption;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.Test;

public class JDBCExportWrongUserTest extends SmokeTestBase {

   @Test
   public void testUserNameAndPasswordCaptured() throws Exception {

      String serverConfigName = "JDBCExportWrongUserTest";

      File server0Location = getFileServerLocation(serverConfigName);
      deleteDirectory(server0Location);

      runAfter(() -> deleteDirectory(server0Location));

      HelperCreate cliCreateServer = helperCreate();
      cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location).addArgs("--jdbc", "--jdbc-connection-url", "fakeOne");
      cliCreateServer.createServer();

      File artemisInstance = getFileServerLocation(serverConfigName);

      String user = RandomUtil.randomUUIDString();
      String password = RandomUtil.randomUUIDString();

      assertTrue(FileUtil.findReplace(new File(artemisInstance, "/etc/broker.xml"), "</database-store>", "   <jdbc-user>" + user + "</jdbc-user>\n" + "            <jdbc-password>" + password + "</jdbc-password>\n" + "         </database-store>"));

      {
         DBOption dbOption = new DBOption();
         dbOption.setHomeValues(cliCreateServer.getArtemisHome(), artemisInstance, new File(artemisInstance, "/etc"));

         Configuration configuration = dbOption.getParameterConfiguration();

         assertEquals(user, ((DatabaseStorageConfiguration) configuration.getStoreConfiguration()).getJdbcUser());
         assertEquals(password, ((DatabaseStorageConfiguration) configuration.getStoreConfiguration()).getJdbcPassword());
         assertEquals(user, dbOption.getJdbcUser());
         assertEquals(password, dbOption.getJdbcPassword());
      }

      {
         DBOption dbOption = new DBOption();
         dbOption.setHomeValues(cliCreateServer.getArtemisHome(), artemisInstance, new File(artemisInstance, "/etc"));
         dbOption.setJdbcUser("myNewUser").setJdbcPassword("myNewPassword").setJdbcClassName("myClass").setJdbcURL("myURL");
         Configuration config = dbOption.getParameterConfiguration();
         assertEquals("myNewUser", ((DatabaseStorageConfiguration) config.getStoreConfiguration()).getJdbcUser());
         assertEquals("myNewPassword", ((DatabaseStorageConfiguration) config.getStoreConfiguration()).getJdbcPassword());
         assertEquals("myURL", ((DatabaseStorageConfiguration) config.getStoreConfiguration()).getJdbcConnectionUrl());
         assertEquals("myClass", ((DatabaseStorageConfiguration) config.getStoreConfiguration()).getJdbcDriverClassName());
      }
   }

}
