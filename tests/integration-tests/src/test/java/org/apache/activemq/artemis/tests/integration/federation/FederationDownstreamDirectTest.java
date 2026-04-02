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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.activemq.artemis.core.protocol.core.impl.wireformat.FederationDownstreamConnectMessage.UPSTREAM_SUFFIX;

public class FederationDownstreamDirectTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private static final String AUTHORIZED_USER = "authorizedUser";
   private static final String AUTHORIZED_PASS = "authorizedPass";
   private static final String AUTHORIZED_ROLE = "authorizedRole";

   private static final String UNAUTHORIZED_USER = "unauthorizedUser";
   private static final String UNAUTHORIZED_PASS = "unauthorizedPass";
   private static final String UNAUTHORIZED_ROLE = "unauthorizedRole";

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
   }

   @Override
   @After
   public void tearDown() throws Exception {
      try {
         if (server != null) {
            server.stop();
         }
      } finally {
         server = null;
         super.tearDown();
      }
   }

   private void startServer(boolean configureDownstreamAuth) throws Exception {
      Configuration config = createDefaultNettyConfig().setSecurityEnabled(true);
      if (configureDownstreamAuth) {
         config.addFederationDownstreamAuthorization(AUTHORIZED_ROLE);
      }

      server = createServer(false, config);
      server.start();

      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser(AUTHORIZED_USER, AUTHORIZED_PASS);
      securityManager.getConfiguration().addRole(AUTHORIZED_USER, AUTHORIZED_ROLE);

      securityManager.getConfiguration().addUser(UNAUTHORIZED_USER, UNAUTHORIZED_PASS);
      securityManager.getConfiguration().addRole(UNAUTHORIZED_USER, UNAUTHORIZED_ROLE);
   }

   @Test
   public void testNoAuthConfigured() throws Exception {
      AssertionLoggerHandler.startCapture(true);
      try {
         startServer(false);

         sendFederationDownstreamConnectMessage(AUTHORIZED_USER, AUTHORIZED_PASS, false);
         Assert.assertFalse(AssertionLoggerHandler.findText("AMQ224158"));
         Assert.assertTrue(AssertionLoggerHandler.findText("AMQ224159"));
         Assert.assertFalse(AssertionLoggerHandler.findText("AMQ224160"));
      } finally {
         AssertionLoggerHandler.stopCapture();
      }
   }

   @Test
   public void testUnauthenticatedDeployment() throws Exception {
      AssertionLoggerHandler.startCapture(true);
      try {
         startServer(true);
         sendFederationDownstreamConnectMessage(null, null, false);
         Assert.assertTrue(AssertionLoggerHandler.findText("AMQ224158"));
         Assert.assertFalse(AssertionLoggerHandler.findText("AMQ224159"));
         Assert.assertFalse(AssertionLoggerHandler.findText("AMQ224160"));
      } finally {
         AssertionLoggerHandler.stopCapture();
      }
   }

   @Test
   public void testUnauthorizedDeployment() throws Exception {
      AssertionLoggerHandler.startCapture(true);
      try {
         startServer(true);
         sendFederationDownstreamConnectMessage(UNAUTHORIZED_USER, UNAUTHORIZED_PASS, false);
         Assert.assertFalse(AssertionLoggerHandler.findText("AMQ224158"));
         Assert.assertTrue(AssertionLoggerHandler.findText("AMQ224159"));
         Assert.assertFalse(AssertionLoggerHandler.findText("AMQ224160"));
      } finally {
         AssertionLoggerHandler.stopCapture();
      }
   }

   @Test
   public void testSuccessfulDeployment() throws Exception {
      AssertionLoggerHandler.startCapture(true);
      try {
         startServer(true);
         sendFederationDownstreamConnectMessage(AUTHORIZED_USER, AUTHORIZED_PASS, true);
         Assert.assertFalse(AssertionLoggerHandler.findText("AMQ224158"));
         Assert.assertFalse(AssertionLoggerHandler.findText("AMQ224159"));
         Assert.assertTrue(AssertionLoggerHandler.findText("AMQ224160"));

         // AMQ224161 is logged when the connection closes and the federation is undeployed
         Wait.assertTrue(() -> AssertionLoggerHandler.findText("AMQ224161"));
      } finally {
         AssertionLoggerHandler.stopCapture();
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
            assertTrue(Wait.waitFor(() -> server.getFederationManager().get(getName() + UPSTREAM_SUFFIX) != null, 1000, 20));
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

      Map<String, FederationPolicy> policyMap = new HashMap<>();
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
