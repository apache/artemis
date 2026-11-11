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

package org.apache.activemq.artemis.tests.integration.stomp;

import javax.security.auth.Subject;
import java.lang.management.ManagementFactory;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.spi.core.security.jaas.NoCacheLoginException;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StompWithClientIdValidationTest extends StompTestBase {

   public StompWithClientIdValidationTest() {
      super("tcp+v10.stomp");
   }

   @Override
   public boolean isSecurityEnabled() {
      return true;
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      Configuration config = createBasicConfig()
         .setSecurityEnabled(isSecurityEnabled())
         .setPersistenceEnabled(isPersistenceEnabled())
         .addAcceptorConfiguration("stomp", "tcp://localhost:61613?enabledProtocols=STOMP")
         .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()))
         .setSecurityInvalidationInterval(0); // disable caching

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration()) {
         @Override
         public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) throws NoCacheLoginException {

            Subject validatedUser = super.authenticate(user, password, remotingConnection, securityDomain);
            if (validatedUser == null) {
               return null;
            }

            if ("STOMP".equals(remotingConnection.getProtocolName())) {
               final String clientId = remotingConnection.getClientID();
               // perform some kind of clientId validation, e.g. check presence or format
               if (clientId == null || clientId.isEmpty()) {
                  System.err.println("ClientID not set!");
                  return null;
               }
            }
            return validatedUser;
         }
      };

      securityManager.getConfiguration().addUser(defUser, defPass);

      server = addServer(ActiveMQServers.newActiveMQServer(config, ManagementFactory.getPlatformMBeanServer(), securityManager));
      return server;
   }

   @Test
   public void testStompConnectWithClientId() throws Exception {
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);

      try {
         assertEquals("CONNECTED", conn.connect(defUser, defPass, "MyClientID").getCommand());
      } finally {
         conn.closeTransport();
      }
   }

   @Test
   public void testStompConnectWithoutClientId() throws Exception {
      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      try {
         assertEquals("ERROR", conn.connect(defUser, defPass).getCommand());
      } finally {
         conn.closeTransport();
      }
   }
}
