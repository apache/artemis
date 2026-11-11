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
package org.apache.activemq.artemis.tests.integration.isolated.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.management.JMX;
import javax.security.auth.Subject;
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.management.ArtemisRbacMBeanServerBuilder;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JmxSecurityMultipleSubjectTest {

   public static Subject viewSubject = new Subject();
   public static Subject updateSubject = new Subject();

   static {
      System.setProperty("javax.management.builder.initial", ArtemisRbacMBeanServerBuilder.class.getCanonicalName());

      viewSubject.getPrincipals().add(new UserPrincipal("v"));
      viewSubject.getPrincipals().add(new RolePrincipal("viewers"));

      updateSubject.getPrincipals().add(new UserPrincipal("u"));
      updateSubject.getPrincipals().add(new RolePrincipal("updaters"));
   }

   ActiveMQServer server;


   @BeforeEach
   public void setUp() throws Exception {
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      Configuration configuration = new ConfigurationImpl().addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName()));
      configuration.setManagementRbacPrefix("jmx");
      server = ActiveMQServers.newActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, false);
   }

   @AfterEach
   public void tearDown() throws Exception {
      server.stop();
   }

   @Test
   public void testJmxAuthBrokerNotCached() throws Exception {

      Set<Role> roles = new HashSet<>();
      roles.add(new Role("viewers", false, false, false, false, false, false, false, false, false, false, true, false));
      roles.add(new Role("updaters", false, false, false, false, false, false, false, false, false, false, true, true));

      server.getConfiguration().putSecurityRoles("jmx.broker.addConnector", roles);
      server.getConfiguration().putSecurityRoles("jmx.broker.getActivationSequence", roles);
      server.getConfiguration().putSecurityRoles("jmx.broker.forceFailover", new HashSet<>());
      server.start();


      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), server.getConfiguration().getName(), true);
      final ActiveMQServerControl activeMQServerControl = JMX.newMBeanProxy(ManagementFactory.getPlatformMBeanServer(), objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);

      try {
         activeMQServerControl.getActivationSequence();
         fail("not logged in");
      } catch (Exception expectedOnNotLoggedIn) {
         assertTrue(expectedOnNotLoggedIn.getMessage().contains("management"));
      }

      // requiring update
      Exception e = SecurityManagerShim.callAs(updateSubject, (Callable<Exception>) () -> {
         try {
            // update first
            activeMQServerControl.addConnector("c", "tcp://localhost:89");
            // also exercise view
            activeMQServerControl.getActivationSequence();
            return null;
         } catch (Exception e1) {
            e1.printStackTrace();
            return e1;
         }
      });
      assertNull(e);

      e = SecurityManagerShim.callAs(viewSubject, (Callable<Exception>) () -> {
         try {
            activeMQServerControl.addConnector("d", "tcp://localhost:89");
            return null;
         } catch (Exception e1) {
            return e1;
         }
      });
      assertNotNull(e, "view permission is not sufficient");

      e = SecurityManagerShim.callAs(updateSubject, (Callable<Exception>) () -> {
         try {
            activeMQServerControl.forceFailover();
            return null;
         } catch (Exception e1) {
            return e1;
         }
      });
      assertNotNull(e, "no permission is sufficient");

   }
}