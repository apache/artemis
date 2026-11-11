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

package org.apache.activemq.cli.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.cli.Artemis;
import org.apache.activemq.artemis.cli.commands.Run;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.ManagementContext;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

public class RetryCLIClientIDTest extends CliTestBase {

   @Test
   public void testWrongUserAndPass() throws Exception {
      try {
         Run.setEmbedded(true);
         File instance1 = new File(temporaryFolder, "instance_user");
         System.setProperty("java.security.auth.login.config", instance1.getAbsolutePath() + "/etc/login.config");
         Artemis.main("create", instance1.getAbsolutePath(), "--silent", "--no-autotune", "--no-web", "--no-amqp-acceptor", "--no-mqtt-acceptor", "--no-stomp-acceptor", "--no-hornetq-acceptor", "--user", "dumb", "--password", "dumber", "--require-login");
         System.setProperty("artemis.instance", instance1.getAbsolutePath());
         Object result = Artemis.internalExecute("run");
         ActiveMQServer activeMQServer = ((Pair<ManagementContext, ActiveMQServer>) result).getB();
         ActiveMQServerControl activeMQServerControl = activeMQServer.getActiveMQServerControl();

         ConnectionTest test = new ConnectionTest();
         test.setSilentInput(true);
         test.setClientID("someClientID");
         ActiveMQConnectionFactory cf = test.newCF();
         assertEquals("someClientID", cf.getClientID());

      } finally {
         stopServer();
      }
   }

   private class ConnectionTest extends ConnectionAbstract {

      public ActiveMQConnectionFactory newCF() {
         return createCoreConnectionFactory();
      }
   }
}
