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
package org.apache.activemq.artemis.tests.integration.security;

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSSecurityException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.RoleSet;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FQQNSendSecurityTest extends ActiveMQTestBase {

   private final String ALLOWED_USER = "allowedUser";
   private final String ALLOWED_ROLE = "allowedRole";
   private final String DENIED_USER = "deniedUser";
   private final String DENIED_ROLE = "deniedRole";
   private final String PASS = RandomUtil.randomUUIDString();
   private final String ADDRESS = "myAddress";
   private final String QUEUE = "myQueue";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration configuration = createDefaultInVMConfig().setSecurityEnabled(true);
      RoleSet roles = new RoleSet();
      roles.add(new Role(ALLOWED_ROLE, true, false, false, false, false, false, false, false, false, false, false, false));
      roles.add(new Role(DENIED_ROLE, false, false, false, false, false, false, false, false, false, false, false, false));
      configuration.putSecurityRoles(CompositeAddress.toFullyQualified(ADDRESS, QUEUE), roles);

      ActiveMQServer server = createServer(false, configuration);

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName());
      SecurityConfiguration securityConfiguration = new SecurityConfiguration();
      securityConfiguration.addUser(ALLOWED_USER, PASS);
      securityConfiguration.addRole(ALLOWED_USER, ALLOWED_ROLE);
      securityConfiguration.addUser(DENIED_USER, PASS);
      securityConfiguration.addRole(DENIED_USER, DENIED_ROLE);
      securityManager.setConfiguration(securityConfiguration);
      server.setSecurityManager(securityManager);

      configuration.addQueueConfiguration(QueueConfiguration.of(QUEUE).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      server.start();
   }

   @Test
   public void sendMessageToFQQN() throws Exception {
      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");
      try (Connection connection = connectionFactory.createConnection(ALLOWED_USER, PASS)) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createQueue(CompositeAddress.toFullyQualified(ADDRESS, QUEUE));
         MessageProducer messageProducer = session.createProducer(destination);
         messageProducer.send(session.createMessage());
      }
   }

   @Test
   public void sendMessageToFQQNNegative() throws Exception {
      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");
      try (Connection connection = connectionFactory.createConnection(DENIED_USER, PASS)) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = session.createQueue(CompositeAddress.toFullyQualified(ADDRESS, QUEUE));
         MessageProducer messageProducer = session.createProducer(destination);
         try {
            messageProducer.send(session.createMessage());
            fail();
         } catch (JMSSecurityException e) {
            // expected
         }
      }
   }
}