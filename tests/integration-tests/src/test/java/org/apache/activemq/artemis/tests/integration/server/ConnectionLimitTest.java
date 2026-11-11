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
package org.apache.activemq.artemis.tests.integration.server;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class ConnectionLimitTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Map<String, Object> nettyParams = new HashMap<>();
      nettyParams.put(TransportConstants.CONNECTIONS_ALLOWED, 1);

      Map<String, Object> invmParams = new HashMap<>();
      invmParams.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.CONNECTIONS_ALLOWED, 1);

      Configuration configuration = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, nettyParams)).addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, invmParams));

      server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));
      server.start();
   }

   @Test
   public void testInVMConnectionLimit() throws Exception {
      ServerLocator locator = addServerLocator(createNonHALocator(false));
      ClientSessionFactory clientSessionFactory = locator.createSessionFactory();

      try {
         ClientSessionFactory extraClientSessionFactory = locator.createSessionFactory();
         fail("creating a session factory here should fail");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQNotConnectedException.class, e);
      }
   }

   @Test
   public void testNettyConnectionLimit() throws Exception {
      ServerLocator locator = createNonHALocator(true).setCallTimeout(3000);
      ClientSessionFactory clientSessionFactory = locator.createSessionFactory();
      ClientSession clientSession = addClientSession(clientSessionFactory.createSession());

      try {
         ClientSessionFactory extraClientSessionFactory = locator.createSessionFactory();
         ClientSession extraClientSession = addClientSession(extraClientSessionFactory.createSession());
         fail("creating a session here should fail");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQConnectionTimedOutException.class, e);
      }
   }
}
