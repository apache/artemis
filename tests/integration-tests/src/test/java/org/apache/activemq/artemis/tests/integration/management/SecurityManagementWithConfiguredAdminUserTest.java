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

import java.util.Set;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.junit.Test;

public class SecurityManagementWithConfiguredAdminUserTest extends SecurityManagementTestBase {



   private final String validAdminUser = "validAdminUser";

   private final String validAdminPassword = "validAdminPassword";

   private final String invalidAdminUser = "invalidAdminUser";

   private final String invalidAdminPassword = "invalidAdminPassword";




   /**
    * default CLUSTER_ADMIN_USER must work even when there are other
    * configured admin users
    */
   @Test
   public void testSendManagementMessageWithClusterAdminUser() throws Exception {
      doSendManagementMessage(ActiveMQDefaultConfiguration.getDefaultClusterUser(), CLUSTER_PASSWORD, true);
   }

   @Test
   public void testSendManagementMessageWithAdminRole() throws Exception {
      doSendManagementMessage(validAdminUser, validAdminPassword, true);
   }

   @Test
   public void testSendManagementMessageWithoutAdminRole() throws Exception {
      doSendManagementMessage(invalidAdminUser, invalidAdminPassword, false);
   }

   @Test
   public void testSendManagementMessageWithoutUserCredentials() throws Exception {
      doSendManagementMessage(null, null, false);
   }



   @Override
   protected ActiveMQServer setupAndStartActiveMQServer() throws Exception {
      Configuration config = createDefaultInVMConfig().setSecurityEnabled(true);
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser(validAdminUser, validAdminPassword);
      securityManager.getConfiguration().addUser(invalidAdminUser, invalidAdminPassword);

      securityManager.getConfiguration().addRole(validAdminUser, "admin");
      securityManager.getConfiguration().addRole(validAdminUser, "guest");
      securityManager.getConfiguration().addRole(invalidAdminUser, "guest");

      Set<Role> adminRole = securityRepository.getMatch(ActiveMQDefaultConfiguration.getDefaultManagementAddress().toString());
      adminRole.add(new Role("admin", true, true, true, true, true, true, true, true, true, true));
      securityRepository.addMatch(ActiveMQDefaultConfiguration.getDefaultManagementAddress().toString(), adminRole);
      Set<Role> guestRole = securityRepository.getMatch("*");
      guestRole.add(new Role("guest", true, true, true, true, true, true, false, true, true, true));
      securityRepository.addMatch("*", guestRole);

      return server;
   }



}
