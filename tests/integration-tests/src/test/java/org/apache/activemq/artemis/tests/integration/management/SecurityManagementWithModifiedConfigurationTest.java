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
package org.apache.activemq.artemis.tests.integration.management;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

//Parameters set in parent class
@ExtendWith(ParameterizedTestExtension.class)
public class SecurityManagementWithModifiedConfigurationTest extends SecurityManagementTestBase {

   private final String configuredClusterPassword = "this is not the default password";

   @TestTemplate
   public void testSendManagementMessageWithModifiedClusterAdminUser() throws Exception {
      doSendBrokerManagementMessage(ActiveMQDefaultConfiguration.getDefaultClusterUser(), configuredClusterPassword, true);
   }

   @TestTemplate
   public void testSendManagementMessageWithDefaultClusterAdminUser() throws Exception {
      doSendBrokerManagementMessage(ActiveMQDefaultConfiguration.getDefaultClusterUser(), ActiveMQDefaultConfiguration.getDefaultClusterPassword(), false);
   }

   @TestTemplate
   public void testSendManagementMessageWithGuest() throws Exception {
      doSendBrokerManagementMessage("guest", "guest", false);
   }

   @TestTemplate
   public void testSendManagementMessageWithoutUserCredentials() throws Exception {
      doSendBrokerManagementMessage(null, null, false);
   }



   @Override
   protected ActiveMQServer setupAndStartActiveMQServer() throws Exception {
      Configuration conf = createDefaultInVMConfig().setSecurityEnabled(true).setClusterPassword(configuredClusterPassword);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(conf, false));
      server.start();

      return server;
   }



}
