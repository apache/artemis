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
package org.apache.activemq.artemis.tests.smoke.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeEach;

public abstract class AuditLoggerTestBase extends SmokeTestBase {

   private File auditLog = null;

   @BeforeEach
   public void before() throws Exception {

      File server0Location = getFileServerLocation(getServerName());
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location).
            setConfiguration("./src/main/resources/servers/" + getServerName()).setArgs("--java-options", "-Djava.rmi.server.hostname=localhost");
         cliCreateServer.createServer();
      }

      cleanupData(getServerName());
      disableCheckThread();
      startServer(getServerName(), 0, 30000);
      emptyLogFile();
   }

   private void emptyLogFile() throws Exception {
      if (getAuditLog().exists()) {
         try (PrintWriter writer = new PrintWriter(new FileWriter(getAuditLog()))) {
            writer.print("");
         }
      }
   }

   protected File getAuditLog() {
      if (auditLog == null) {
         auditLog = new File("target/" + getServerName() + "/log/audit.log");
      }
      return auditLog;
   }

   abstract String getServerName();

}
