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
package org.apache.activemq.artemis.tests.integration.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.security.auth.Subject;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManager;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTSessionState;
import org.apache.activemq.artemis.core.protocol.mqtt.exceptions.InvalidClientIdException;
import org.apache.activemq.artemis.core.remoting.impl.AbstractAcceptor;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager5;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.MQTTException;
import org.fusesource.mqtt.codec.CONNACK;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MQTTSecurityManagerTest extends MQTTTestSupport {

   private String clientID = "new-" + RandomUtil.randomUUIDString();
   private boolean rejectClientId = false;

   @Override
   public boolean isSecurityEnabled() {
      return true;
   }

   @Override
   public void configureBroker() throws Exception {
      super.configureBroker();
      server.setSecurityManager(new ActiveMQSecurityManager5() {
         @Override
         public Subject authenticate(String user,
                                     String password,
                                     RemotingConnection remotingConnection,
                                     String securityDomain) {
            if (rejectClientId) {
               throw new InvalidClientIdException();
            }
            remotingConnection.setClientID(clientID);
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            return subject;
         }

         @Override
         public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            return true;
         }

         @Override
         public boolean validateUser(String user, String password) {
            return true;
         }

         @Override
         public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return true;
         }
      });
      server.getConfiguration().setAuthenticationCacheSize(0);
      server.getConfiguration().setAuthorizationCacheSize(0);
   }

   @Test
   @Timeout(30)
   public void testSecurityManagerModifyClientID() throws Exception {
      BlockingConnection connection = null;
      try {
         MQTT mqtt = createMQTTConnection(RandomUtil.randomUUIDString(), true);
         mqtt.setUserName(fullUser);
         mqtt.setPassword(fullPass);
         mqtt.setConnectAttemptsMax(1);
         connection = mqtt.blockingConnection();
         connection.connect();
         BlockingConnection finalConnection = connection;
         assertTrue(Wait.waitFor(() -> finalConnection.isConnected(), 5000, 100), "Should be connected");
         Map<String, MQTTSessionState> sessionStates = null;
         Acceptor acceptor = server.getRemotingService().getAcceptor("MQTT");
         if (acceptor instanceof AbstractAcceptor abstractAcceptor) {
            ProtocolManager protocolManager = abstractAcceptor.getProtocolMap().get("MQTT");
            if (protocolManager instanceof MQTTProtocolManager mqttProtocolManager) {
               sessionStates = mqttProtocolManager.getStateManager().getSessionStates();
            }
         }
         assertEquals(1, sessionStates.size());
         assertTrue(sessionStates.containsKey(clientID));
         for (MQTTSessionState state : sessionStates.values()) {
            assertEquals(clientID, state.getClientId());
         }
      } finally {
         if (connection != null && connection.isConnected()) {
            connection.disconnect();
         }
      }
   }

   @Test
   @Timeout(30)
   public void testSecurityManagerModifyClientIDAndStealConnection() throws Exception {
      BlockingConnection connection1 = null;
      BlockingConnection connection2 = null;
      final String CLIENT_ID = "old-" + RandomUtil.randomUUIDString();
      try {
         MQTT mqtt = createMQTTConnection(CLIENT_ID, true);
         mqtt.setUserName(fullUser);
         mqtt.setPassword(fullPass);
         mqtt.setConnectAttemptsMax(1);
         connection1 = mqtt.blockingConnection();
         connection1.connect();
         final BlockingConnection finalConnection = connection1;
         assertTrue(Wait.waitFor(() -> finalConnection.isConnected(), 5000, 100), "Should be connected");
         Map<String, MQTTSessionState> sessionStates = null;
         Acceptor acceptor = server.getRemotingService().getAcceptor("MQTT");
         if (acceptor instanceof AbstractAcceptor abstractAcceptor) {
            ProtocolManager protocolManager = abstractAcceptor.getProtocolMap().get("MQTT");
            if (protocolManager instanceof MQTTProtocolManager manager) {
               sessionStates = manager.getStateManager().getSessionStates();
            }
         }
         assertEquals(1, sessionStates.size());
         assertTrue(sessionStates.containsKey(clientID));
         for (MQTTSessionState state : sessionStates.values()) {
            assertEquals(clientID, state.getClientId());
         }

         connection2 = mqtt.blockingConnection();
         connection2.connect();
         final BlockingConnection finalConnection2 = connection2;
         assertTrue(Wait.waitFor(() -> finalConnection2.isConnected(), 5000, 100), "Should be connected");
         Wait.assertFalse(() -> finalConnection.isConnected(), 5000, 100);
         assertEquals(1, sessionStates.size());
         assertTrue(sessionStates.containsKey(clientID));
         for (MQTTSessionState state : sessionStates.values()) {
            assertEquals(clientID, state.getClientId());
         }
      } finally {
         if (connection1 != null && connection1.isConnected()) {
            connection1.disconnect();
         }
      }
   }

   @Test
   @Timeout(30)
   public void testSecurityManagerRejectClientID() throws Exception {
      rejectClientId = true;
      BlockingConnection connection = null;
      try {
         MQTT mqtt = createMQTTConnection(RandomUtil.randomUUIDString(), true);
         mqtt.setUserName(fullUser);
         mqtt.setPassword(fullPass);
         mqtt.setConnectAttemptsMax(1);
         connection = mqtt.blockingConnection();
         try {
            connection.connect();
            fail("Should have thrown exception");
         } catch (MQTTException e) {
            assertEquals(CONNACK.Code.CONNECTION_REFUSED_IDENTIFIER_REJECTED, e.connack.code());
         }
      } finally {
         if (connection != null && connection.isConnected()) {
            connection.disconnect();
         }
      }
   }
}
