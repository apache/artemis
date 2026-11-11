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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SessionClosedOnRemotingConnectionFailureTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ClientSessionFactory sf;

   @Test
   public void testSessionClosedOnRemotingConnectionFailure() throws Exception {
      ClientSession session = addClientSession(sf.createSession());

      session.createQueue(QueueConfiguration.of("fooqueue").setAddress("fooaddress").setRoutingType(RoutingType.ANYCAST));

      ClientProducer prod = session.createProducer("fooaddress");

      ClientConsumer cons = session.createConsumer("fooqueue");

      session.start();

      prod.send(session.createMessage(false));

      assertNotNull(cons.receive());

      // Now fail the underlying connection

      RemotingConnection connection = ((ClientSessionInternal) session).getConnection();

      connection.fail(new ActiveMQNotConnectedException());

      assertTrue(session.isClosed());

      assertTrue(prod.isClosed());

      assertTrue(cons.isClosed());

      // Now try and use the producer

      try {
         prod.send(session.createMessage(false));

         fail("Should throw exception");
      } catch (ActiveMQObjectClosedException oce) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      try {
         cons.receive();

         fail("Should throw exception");
      } catch (ActiveMQObjectClosedException oce) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }

      session.close();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration config = createDefaultInVMConfig();
      server = createServer(false, config);
      server.start();
      ServerLocator locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
   }
}
