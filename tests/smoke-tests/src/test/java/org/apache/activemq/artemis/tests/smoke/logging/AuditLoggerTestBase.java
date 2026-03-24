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

import javax.jms.ConnectionFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Before;

public abstract class AuditLoggerTestBase extends SmokeTestBase {

   private File auditLog = null;

   @Before
   public void before() throws Exception {
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

   //check the audit log has a line that contains all the values
   protected void checkAuditLogRecord(boolean exist, String... values) throws Exception {
      checkLogRecord(getAuditLog(), exist, values);
   }

   public static ConnectionFactory createConnectionFactory(String protocol, String uri) {
      if (protocol.toUpperCase().equals("OPENWIRE")) {
         return new org.apache.activemq.ActiveMQConnectionFactory(uri);
      } else if (protocol.toUpperCase().equals("AMQP")) {

         if (uri.startsWith("tcp://")) {
            // replacing tcp:// by amqp://
            uri = "amqp" + uri.substring(3);
         }
         return new JmsConnectionFactory(uri);
      } else if (protocol.toUpperCase().equals("CORE") || protocol.toUpperCase().equals("ARTEMIS")) {
         return new org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory(uri);
      } else {
         throw new IllegalStateException("Unkown:" + protocol);
      }
   }
}
