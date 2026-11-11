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
package org.apache.activemq.artemis.tests.integration.federation;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.federation.FederationDownstreamConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationPolicy;
import org.apache.activemq.artemis.core.config.federation.FederationPolicySet;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.FederationDownstreamConnectMessage;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.apache.activemq.artemis.core.protocol.core.impl.wireformat.FederationDownstreamConnectMessage.UPSTREAM_SUFFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FederationDownstreamDirectTest extends ActiveMQTestBase {

   protected ActiveMQServer server;

   private static final String noAuth = "noAuth";

   private static final String authorizedUser = "authorizedUser";
   private static final String authorizedPass = "authorizedPass";
   private static final String authorizedRole = "authorizedRole";

   private static final String unauthorizedUser = "unauthorizedUser";
   private static final String unauthorizedPass = "unauthorizedPass";
   private static final String unauthorizedRole = "unauthorizedRole";

   @BeforeEach
   public void setUp(TestInfo testInfo) throws Exception {
      super.setUp();
      Configuration config = createDefaultNettyConfig().setSecurityEnabled(true);
      if (!testInfo.getTags().contains(noAuth)) {
         config.addFederationDownstreamAuthorization(authorizedRole);
      }
      server = createServer(false, config);
      server.start();

      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      securityManager.getConfiguration().addUser(authorizedUser, authorizedPass);
      securityManager.getConfiguration().addRole(authorizedUser, authorizedRole);

      securityManager.getConfiguration().addUser(unauthorizedUser, unauthorizedPass);
      securityManager.getConfiguration().addRole(unauthorizedUser, unauthorizedRole);
   }

   @Test
   @Tag(noAuth)
   public void testNoAuthConfigured() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         sendFederationDownstreamConnectMessage(authorizedUser, authorizedPass, false);
         assertFalse(loggerHandler.findText("AMQ224158"));
         assertTrue(loggerHandler.findText("AMQ224159"));
         assertFalse(loggerHandler.findText("AMQ224160"));
      }
   }

   @Test
   public void testUnauthenticatedDeployment() throws Exception {
      // send the federation message without authenticating with a session first
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         sendFederationDownstreamConnectMessage(null, null, false);
         assertTrue(loggerHandler.findText("AMQ224158"));
         assertFalse(loggerHandler.findText("AMQ224159"));
         assertFalse(loggerHandler.findText("AMQ224160"));
      }
   }

   @Test
   public void testUnauthorizedDeployment() throws Exception {
      // send the federation message after authenticating with a user who isn't authorized for downstream deployment
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         sendFederationDownstreamConnectMessage(unauthorizedUser, unauthorizedPass, false);
         assertFalse(loggerHandler.findText("AMQ224158"));
         assertTrue(loggerHandler.findText("AMQ224159"));
         assertFalse(loggerHandler.findText("AMQ224160"));
      }
   }

   @Test
   public void testSuccessfulDeployment() throws Exception {
      // send the federation message after authenticating with a user who is authorized for downstream deployment
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         sendFederationDownstreamConnectMessage(authorizedUser, authorizedPass, true);
         assertFalse(loggerHandler.findText("AMQ224158"));
         assertFalse(loggerHandler.findText("AMQ224159"));
         assertTrue(loggerHandler.findText("AMQ224160"));
         Wait.assertTrue(() -> loggerHandler.findText("AMQ224161"));
      }
   }

   private void sendFederationDownstreamConnectMessage(String user, String password, boolean succeed) throws Exception {
      try (ServerLocator locator = ActiveMQClient.createServerLocator("tcp://localhost:61616")) {
         ClientSessionFactory factory = locator.createSessionFactory();
         ClientSession session;
         if (user != null) {
            session = factory.createSession(user, password, true, true, true, true, -1);
         }
         CoreRemotingConnection coreConn = (CoreRemotingConnection) factory.getConnection();
         Wait.assertEquals(1, server.getActiveMQServerControl()::getConnectionCount);
         Channel federationChannel = coreConn.getChannel(ChannelImpl.CHANNEL_ID.FEDERATION.id, -1);
         federationChannel.send(getFederationDownstreamConnectMessage(getName()));
         if (succeed) {
            Wait.assertNotNull(() -> server.getFederationManager().get(getName() + UPSTREAM_SUFFIX), 1000, 20);
         } else {
            assertFalse(Wait.waitFor(() -> server.getFederationManager().get(getName() + UPSTREAM_SUFFIX) != null, 1000, 20));
            assertEquals(0, server.getActiveMQServerControl().getConnectionCount());
         }
      }
   }

   private FederationDownstreamConnectMessage getFederationDownstreamConnectMessage(String name) {
      final String policySetName = "fake-policy-set";
      final String policyConfigName = "fake-policy-config";
      FederationDownstreamConnectMessage msg = new FederationDownstreamConnectMessage();
      msg.setName(name);

      Map<String, FederationPolicy<?>> policyMap = new HashMap<>();
      policyMap.put(policyConfigName, new FederationQueuePolicyConfiguration().setName(policyConfigName).addInclude(new FederationQueuePolicyConfiguration.Matcher().setQueueMatch("#").setAddressMatch("#")));
      policyMap.put(policySetName, new FederationPolicySet().setName(policySetName).addPolicyRef(policyConfigName));
      msg.setFederationPolicyMap(policyMap);

      FederationDownstreamConfiguration downstreamConfig = new FederationDownstreamConfiguration()
         .setName("fake")
         .addPolicyRef(policySetName);
      downstreamConfig.setUpstreamConfiguration(new TransportConfiguration(NettyConnectorFactory.class.getName(), new HashMap<>(), "fake"));
      msg.setStreamConfiguration(downstreamConfig);
      return msg;
   }
}
