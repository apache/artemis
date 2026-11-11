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

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.junit.jupiter.api.Test;

// This subclass is NOT parameterised, sets its config in setupAndStartActiveMQServer method
public class SecurityManagementMessageRbacTest extends SecurityManagementTestBase {

   private final String password = "bla";

   private final String guest = "guest";
   private final String view = "view";
   private final String updater = "updaterUser";
   private final String admin = "validAdminUser";

   @Test
   public void testSendManagementMessageWithAdminRole() throws Exception {
      doSendBrokerManagementMessage(admin, password, true);
   }

   @Test
   public void testSendManagementMessageAsGuest() throws Exception {
      doSendBrokerManagementMessage(guest, password, false);
   }

   @Test
   public void testSendManagementMessageAsView() throws Exception {
      doSendBrokerManagementMessage(view, password, true);
   }

   @Test
   public void testSendManagementOpWithView() throws Exception {
      doSendBrokerManagementMessageFor(false, view, password, false);
   }

   @Test
   public void testSendManagementOpWithGuest() throws Exception {
      doSendBrokerManagementMessageFor(false, guest, password, false);
   }

   @Test
   public void testSendManagementOpWithUpdateRole() throws Exception {
      doSendBrokerManagementMessageFor(false, updater, password, true);
   }

   @Override
   protected ActiveMQServer setupAndStartActiveMQServer() throws Exception {
      Configuration config = createDefaultInVMConfig().setSecurityEnabled(true);
      config.setManagementMessageRbac(true); // enable rbac view/update perms check
      config.setManagementRbacPrefix("mm");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser(admin, password);
      securityManager.getConfiguration().addUser(guest, password);
      securityManager.getConfiguration().addUser(updater, password);
      securityManager.getConfiguration().addUser(view, password);

      securityManager.getConfiguration().addRole(admin, "manageRole");
      securityManager.getConfiguration().addRole(admin, "updateRole");
      securityManager.getConfiguration().addRole(guest, "guestRole");
      securityManager.getConfiguration().addRole(view, "viewRole");
      securityManager.getConfiguration().addRole(view, "manageRole");
      securityManager.getConfiguration().addRole(updater, "updateRole");
      securityManager.getConfiguration().addRole(updater, "manageRole");

      Set<Role> permissionsOnManagementAddress = new HashSet<>();
      permissionsOnManagementAddress.add(new Role("manageRole", true, true, false, false, true, true, true, true, true, true, false, false));
      securityRepository.addMatch(ActiveMQDefaultConfiguration.getDefaultManagementAddress().toString() + ".*", permissionsOnManagementAddress); // for create reply queue
      securityRepository.addMatch(ActiveMQDefaultConfiguration.getDefaultManagementAddress().toString(), permissionsOnManagementAddress); // for send to manage address


      Set<Role> catchAllPermissions = new HashSet<>();
      catchAllPermissions.add(new Role("guestRole", false, false, false, false, false, false, false, false, false, false, false, false));
      securityRepository.addMatch("#", catchAllPermissions);

      final String brokerControlRbacAttributeKey = "mm.broker.isStarted";
      Set<Role> brokerControlRoles = new HashSet<>();
      brokerControlRoles.add(new Role("viewRole", true, true, true, true, true, true, true, true, true, true, true, false));
      brokerControlRoles.add(new Role("updateRole", true, true, true, true, true, true, true, true, true, true, true, true));

      securityRepository.addMatch(brokerControlRbacAttributeKey, brokerControlRoles);

      final String brokerControlRbacOpKey = "mm.broker.enableMessageCounters";
      securityRepository.addMatch(brokerControlRbacOpKey, brokerControlRoles);

      return server;
   }
}
