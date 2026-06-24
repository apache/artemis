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
package org.apache.activemq.artemis.tests.integration.routing;

import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.util.Map;

import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.routing.policies.FirstElementPolicy;
import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpValidator;
import org.apache.qpid.proton.amqp.transport.ConnectionError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Note: The primary routing tests for AMQP clients are in e.g {@link RedirectTest} along with those for other protocols.
 *
 * This class only adds some additional validations that are AMQP-specific.
 */
public class AmqpRedirectTest extends RoutingTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      enableDiscoveryForTest();
      super.setUp();
   }

   @Test
   public void testRouterRejectionDueToOfflineTargetPool() throws Exception {
      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      setupPrimaryServerWithDiscovery(1, GROUP_ADDRESS, GROUP_PORT, true, true, false);

      // Zero quorum size to avoid the quorum delay, given it will never be satisfied
      setupRouterServerWithStaticConnectors(0, KeyType.USER_NAME, FirstElementPolicy.NAME, null, false, null, 0, 1);

      // Only start the broker with the router, so it can never become ready to redirect.
      startServers(0);

      URI uri = new URI("tcp://localhost:" + TransportConstants.DEFAULT_PORT);
      AmqpClient client = new AmqpClient(uri, "admin", "admin");

      AmqpConnection connection = client.createConnection();
      connection.setContainerId(getName());

      connection.setStateInspector(new AmqpValidator() {

         @Override
         public void inspectOpenedResource(Connection connection) {
            if (!connection.getRemoteProperties().containsKey(AmqpSupport.CONNECTION_OPEN_FAILED)) {
               markAsInvalid("Broker did not set connection establishment failed hint");
            }
         }

         @Override
         public void inspectClosedResource(Connection connection) {
            ErrorCondition remoteError = connection.getRemoteCondition();
            if (remoteError == null || remoteError.getCondition() == null) {
               markAsInvalid("Broker did not add error condition for connection");
               return;
            }

            if (!remoteError.getCondition().equals(ConnectionError.CONNECTION_FORCED)) {
               markAsInvalid("Broker did not set condition to " + ConnectionError.CONNECTION_FORCED);
               return;
            }

            String expectedDescription = "Connection router " + CONNECTION_ROUTER_NAME + " is not ready";
            String actualDescription = remoteError.getDescription();
            if (!expectedDescription.equals(actualDescription)) {
               markAsInvalid("Broker did not set description as expected, was: " + actualDescription);
               return;
            }
         }
      });

      try {
         connection.connect();
         fail("Expected connection to fail, without redirect");
      } catch (Exception e) {
         // Expected
      }

      connection.getStateInspector().assertValid();
      connection.close();

      stopServers(0);
   }

   @Test
   public void testRouterRedirectDetails() throws Exception {
      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);
      setupPrimaryServerWithDiscovery(1, GROUP_ADDRESS, GROUP_PORT, true, true, false);

      setupRouterServerWithStaticConnectors(0, KeyType.USER_NAME, FirstElementPolicy.NAME, null, false, null, 1, 1);

      startServers(0, 1);

      URI uri = new URI("tcp://localhost:" + TransportConstants.DEFAULT_PORT);
      AmqpClient client = new AmqpClient(uri, "admin", "admin");

      AmqpConnection connection = client.createConnection();
      connection.setContainerId(getName());

      connection.setStateInspector(new AmqpValidator() {

         @Override
         public void inspectOpenedResource(Connection connection) {
            if (!connection.getRemoteProperties().containsKey(AmqpSupport.CONNECTION_OPEN_FAILED)) {
               markAsInvalid("Broker did not set connection establishment failed hint");
            }
         }

         @Override
         public void inspectClosedResource(Connection connection) {
            ErrorCondition remoteError = connection.getRemoteCondition();
            if (remoteError == null || remoteError.getCondition() == null) {
               markAsInvalid("Broker did not add error condition for connection");
               return;
            }

            if (!remoteError.getCondition().equals(ConnectionError.REDIRECT)) {
               markAsInvalid("Broker did not set condition to " + ConnectionError.REDIRECT);
               return;
            }

            Integer redirectPort = TransportConstants.DEFAULT_PORT + 1;

            String expectedDescription = "Connection router " + CONNECTION_ROUTER_NAME + " redirected this connection to localhost:" + redirectPort;
            String actualDescription = remoteError.getDescription();
            if (!expectedDescription.equals(actualDescription)) {
               markAsInvalid("Broker did not set description as expected, was: " + actualDescription);
               return;
            }

            // Validate the info map contains expected redirect info
            Map<?, ?> infoMap = remoteError.getInfo();
            if (infoMap == null) {
               markAsInvalid("Broker did not set an info map on condition with redirect details");
               return;
            }

            if (!infoMap.containsKey(AmqpSupport.NETWORK_HOST)) {
               markAsInvalid("Info map does not contain key " + AmqpSupport.NETWORK_HOST);
               return;
            } else {
               Object value = infoMap.get(AmqpSupport.NETWORK_HOST);
               if (!"localhost".equals(value)) {
                  markAsInvalid("Info map does not contain expected network-host value, was: " + value);
                  return;
               }
            }

            if (!infoMap.containsKey(AmqpSupport.PORT)) {
               markAsInvalid("Info map does not contain key " + AmqpSupport.PORT);
               return;
            } else {
               Object value = infoMap.get(AmqpSupport.PORT);
               if (value == null || !redirectPort.equals(value)) {
                  markAsInvalid("Info map does not contain expected port value, was: " + value);
                  return;
               }
            }
         }
      });

      try {
         connection.connect();
         fail("Expected connection to fail, with redirect");
      } catch (Exception e) {
         // Expected
      }

      connection.getStateInspector().assertValid();
      connection.close();

      stopServers(0, 1);
   }

   @Test
   public void testRouterRejectionUseAnother() throws Exception {
      setupPrimaryServerWithDiscovery(0, GROUP_ADDRESS, GROUP_PORT, true, true, false);

      // only accepts users with RoleName==B so will reject
      setupRouterServerWithLocalTarget(0, KeyType.ROLE_NAME, "B", null);

      startServers(0);

      URI uri = new URI("tcp://localhost:" + TransportConstants.DEFAULT_PORT);
      AmqpClient client = new AmqpClient(uri, "admin", "admin");

      AmqpConnection connection = client.createConnection();
      connection.setContainerId(getName());

      connection.setStateInspector(new AmqpValidator() {

         @Override
         public void inspectOpenedResource(Connection connection) {
            if (!connection.getRemoteProperties().containsKey(AmqpSupport.CONNECTION_OPEN_FAILED)) {
               markAsInvalid("Broker did not set connection establishment failed hint");
            }
         }

         @Override
         public void inspectClosedResource(Connection connection) {
            ErrorCondition remoteError = connection.getRemoteCondition();
            if (remoteError == null || remoteError.getCondition() == null) {
               markAsInvalid("Broker did not add error condition for connection");
               return;
            }

            if (!remoteError.getCondition().equals(ConnectionError.CONNECTION_FORCED)) {
               markAsInvalid("Broker did not set condition to " + ConnectionError.CONNECTION_FORCED);
               return;
            }
            String expectedDescription = "Connection router " + CONNECTION_ROUTER_NAME + " rejected this connection";
            String actualDescription = remoteError.getDescription();
            if (!expectedDescription.equals(actualDescription)) {
               markAsInvalid("Broker did not set description as expected, was: " + actualDescription);
               return;
            }
         }
      });

      try {
         connection.connect();
         fail("Expected connection to fail, without redirect");
      } catch (Exception e) {
         // Expected
      }

      connection.getStateInspector().assertValid();
      connection.close();

      stopServers(0);
   }
}
