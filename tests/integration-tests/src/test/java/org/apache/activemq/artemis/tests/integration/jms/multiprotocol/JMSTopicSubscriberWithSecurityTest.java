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
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(ParameterizedTestExtension.class)
public class JMSTopicSubscriberWithSecurityTest extends MultiprotocolJMSClientTestSupport {

   private SecureConnectionSupplier connectionSupplier;
   private static final String USER = "USER";
   private static final String PASS = "PASS";
   private static final String ROLE = "ROLE";

   @Parameters(name = "protocol={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{
         {Protocol.AMQP},
         {Protocol.CORE},
         {Protocol.OPENWIRE}
      });
   }

   public JMSTopicSubscriberWithSecurityTest(Protocol protocol) {
      switch (protocol) {
         case AMQP -> this.connectionSupplier = (username, password) -> createConnection(username, password);
         case CORE -> this.connectionSupplier = (username, password) -> createCoreConnection(username, password);
         case OPENWIRE -> this.connectionSupplier = (username, password) -> createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), username, password, null, true, false);
      }
   }

   @Override
   protected boolean isAutoCreateAddresses() {
      return false;
   }

   @Override
   protected boolean isSecurityEnabled() {
      return true;
   }

   @Override
   protected void enableSecurity(ActiveMQServer server, String... securityMatches) {
      super.enableSecurity(server);

      // add a new user/role who can only create non-durable queues (i.e., non-durable JMS subscriptions) and consume from them
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser(USER, PASS);
      securityManager.getConfiguration().addRole(USER, ROLE);
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      Set<Role> value = securityRepository.getMatch(getTopicName());
      value.add(new Role(ROLE, false, true, false, false, true, false, false, false, false, false));
   }

   @TestTemplate
   public void testCreateConsumer() throws Throwable {
      Connection connection = connectionSupplier.createConnection(USER, PASS);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      assertThrows(JMSException.class, () -> session.createConsumer(session.createTopic(getTopicName())));
      assertNull(server.getAddressInfo(SimpleString.of(getTopicName())));
   }
}
