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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_44_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(ParameterizedTestExtension.class)
public class ValidateReattachCompatibilityTest extends ClasspathBase {

   ClassLoader senderClassloader;

   ActiveMQServer server;

   @BeforeEach
   protected void setup() throws Exception {

      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.setJournalType(JournalType.NIO);
      configuration.addAcceptorConfiguration("artemis", "tcp://0.0.0.0:61616");
      configuration.setSecurityEnabled(true);
      configuration.setPersistenceEnabled(false);
      server = ActiveMQServers.newActiveMQServer(configuration, false);


      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      // Add users
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().addRole("guest", "limited");
      securityManager.getConfiguration().addUser("admin", "admin");
      securityManager.getConfiguration().addRole("admin", "full");

      // Configure roles
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      Set<Role> roles = new HashSet<>();

      // "limited" role: can only send and consume, cannot create queues
      roles.add(new Role("limited", true, true, false, false, false, false, false, false, false, false, false, false));

      // "full" role: can do everything
      roles.add(new Role("full", true, true, true, true, true, true, true, true, true, true, false, false));

      securityRepository.addMatch("#", roles);

      server.getConfiguration().setSecurityEnabled(true);

      server.start();
   }

   @AfterEach
   protected void teardown() throws Exception {
      server.stop();
   }

   @Parameters(name = "client={0}")
   public static Collection getParameters() {
      List<Object[]> combinations = new ArrayList<>();
      combinations.add(new Object[]{ARTEMIS_2_44_0});
      combinations.add(new Object[]{SNAPSHOT});
      return combinations;
   }

   public ValidateReattachCompatibilityTest(String sender) throws Exception {
      this.senderClassloader = getClasspath(sender, false);
      clearGroovy(senderClassloader);
   }

   @BeforeEach
   public void setUp() throws Throwable {
      FileUtil.deleteDirectory(serverFolder);
   }

   @TestTemplate
   public void testValidateExceptionThrown() throws Throwable {
      Boolean testOkay = (Boolean) evaluate(senderClassloader, "reattachCompatibility/testValidReattach.groovy");

      assertTrue(testOkay);
      assertNull(server.locateQueue("iDontExist"));
   }
}
