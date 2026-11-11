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
package org.apache.activemq.artemis.tests.integration.openwire;

import javax.jms.ConnectionFactory;
import javax.management.MBeanServer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.unit.util.InVMNamingContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class OpenWireTestBase extends ActiveMQTestBase {

   public static final String OWHOST = "localhost";
   public static final int OWPORT = 61616;

   protected static final String urlString = "tcp://" + OWHOST + ":" + OWPORT + "?wireFormat.cacheEnabled=true";
   protected static final String urlStringLoose = "tcp://" + OWHOST + ":" + OWPORT + "?wireFormat.tightEncodingEnabled=false";

   protected ActiveMQServer server;

   protected boolean realStore = false;
   protected boolean enableSecurity = false;

   protected ConnectionFactory coreCf;
   protected InVMNamingContext namingContext;

   protected MBeanServer mbeanServer;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = this.createServer(realStore, true);

      Configuration serverConfig = server.getConfiguration();

      Map<String, AddressSettings> addressSettingsMap = serverConfig.getAddressSettings();

      configureAddressSettings(addressSettingsMap);

      serverConfig.setSecurityEnabled(enableSecurity);

      extraServerConfig(serverConfig);

      if (enableSecurity) {
         ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
         securityManager.getConfiguration().addRole("openwireSender", "sender");
         securityManager.getConfiguration().addUser("openwireSender", "SeNdEr");
         //sender cannot receive
         Role senderRole = new Role("sender", true, false, false, false, true, true, false, false, true, true, false, false);

         securityManager.getConfiguration().addRole("openwireReceiver", "receiver");
         securityManager.getConfiguration().addUser("openwireReceiver", "ReCeIvEr");
         //receiver cannot send
         Role receiverRole = new Role("receiver", false, true, false, false, true, true, false, true, false, false, false, false);

         securityManager.getConfiguration().addRole("openwireGuest", "guest");
         securityManager.getConfiguration().addUser("openwireGuest", "GuEsT");

         //guest cannot do anything
         Role guestRole = new Role("guest", false, false, false, false, false, false, false, false, false, false, false, false);

         securityManager.getConfiguration().addRole("openwireDestinationManager", "manager");
         securityManager.getConfiguration().addUser("openwireDestinationManager", "DeStInAtIoN");

         Role destRole = new Role("manager", false, false, false, false, true, true, false, false, true, false, false, false);

         Set<Role> roles = new HashSet<>();
         roles.add(senderRole);
         roles.add(receiverRole);
         roles.add(guestRole);
         roles.add(destRole);

         server.getConfiguration().putSecurityRoles("#", roles);

         // advisory addresses, anyone can create/consume
         // broker can produce
         Role advisoryReceiverRole = new Role("advisoryReceiver", false, true, false, false, true, true, false, true, true, false, false, false);

         roles = new HashSet<>();
         roles.add(advisoryReceiverRole);
         server.getConfiguration().putSecurityRoles("ActiveMQ.Advisory.#", roles);

         securityManager.getConfiguration().addRole("openwireReceiver", "advisoryReceiver");
         securityManager.getConfiguration().addRole("openwireSender", "advisoryReceiver");
         securityManager.getConfiguration().addRole("openwireGuest", "advisoryReceiver");
         securityManager.getConfiguration().addRole("openwireDestinationManager", "advisoryReceiver");
      }

      mbeanServer = createMBeanServer();
      server.setMBeanServer(mbeanServer);
      addServer(server);
      server.start();

      coreCf = ActiveMQJMSClient.createConnectionFactory("vm://0?reconnectAttempts=-1", "cf");
   }

   protected void configureAddressSettings(Map<String, AddressSettings> addressSettingsMap) {
      addressSettingsMap.put("#", new AddressSettings().setAutoCreateQueues(false).setAutoCreateAddresses(false).setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")).setAutoCreateAddresses(true));
   }

   //override this to add extra server configs
   protected void extraServerConfig(Configuration serverConfig) {
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      mbeanServer = null;
      server.stop();
      super.tearDown();
   }

}
