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
package org.apache.activemq.artemis.tests.integration.cluster.reattach;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionImpl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionImplAccessor;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQSessionContext;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReattachDisabledTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final SimpleString ADDRESS = SimpleString.of(getTestClassName());
   private ActiveMQServer server;
   private ServerLocator locator;
   private ServerLocator locator2;

   @Test
   public void testValidateExceptionThrown() throws Exception {
      locator.setRetryInterval(100).setRetryIntervalMultiplier(1).setReconnectAttempts(-1).setConfirmationWindowSize(1024 * 1024);
      locator2.setRetryInterval(100).setRetryIntervalMultiplier(1).setReconnectAttempts(-1).setConfirmationWindowSize(1024 * 1024);
      ClientSessionImpl targetSessionImpl;
      {

         ClientSessionFactoryInternal sfTarget = (ClientSessionFactoryInternal) createSessionFactory(locator);
         runAfter(sfTarget::close);

         targetSessionImpl = (ClientSessionImpl) sfTarget.createSession("admin", "admin", false, true, true, false, 0);

         targetSessionImpl.createQueue(QueueConfiguration.of(ReattachDisabledTest.ADDRESS).setDurable(false));

      }

      ClientSessionFactoryInternal sfReattaching = (ClientSessionFactoryInternal) createSessionFactory(locator2);
      runAfter(sfReattaching::close);
      RemotingConnectionImpl connection = (RemotingConnectionImpl) sfReattaching.getConnection();
      int version = targetSessionImpl.getSessionContext().getServerVersion();
      ActiveMQSessionContext targetSessionContext = (ActiveMQSessionContext) targetSessionImpl.getSessionContext();

      ActiveMQSessionContext context = new ActiveMQSessionContext(targetSessionImpl.getName(), sfReattaching.getConnection(), connection.getChannel(targetSessionContext.getSessionChannel().getID(), 1024 * 1024), version, 1024 * 1024);
      ClientSessionInternal reattachSession = ClientSessionImplAccessor.createSession(sfReattaching, targetSessionImpl.getName(), "guest", "guest", false, false, false, false, false, false, 1, 1024, -1, -1, -1, -1, false, false, false, 100 * 1024, false, 1, 1024, null, 60000, context, ClientSessionImplAccessor.getExecutor(targetSessionImpl), ClientSessionImplAccessor.getConfirmationExecutor(targetSessionImpl), ClientSessionImplAccessor.getFlowControlExecutor(targetSessionImpl), ClientSessionImplAccessor.getCloseExecutor(targetSessionImpl));

      RemotingConnection conn = reattachSession.getConnection();
      conn.fail(new ActiveMQNotConnectedException());

      Thread.sleep(500);

      String nonExistingQueue = "IDontExist_" + RandomUtil.randomUUIDString();

      // The user should not have permissions to create the queue after the reconnect
      assertThrows(ActiveMQSecurityException.class, () -> {
         reattachSession.createAddress(SimpleString.of(nonExistingQueue), RoutingType.ANYCAST, true);
         reattachSession.createQueue(QueueConfiguration.of(nonExistingQueue).setAddress(nonExistingQueue).setRoutingType(RoutingType.ANYCAST).setDurable(true));
      });

      assertNull(server.locateQueue(nonExistingQueue));

      ClientProducer producer = reattachSession.createProducer(ReattachDisabledTest.ADDRESS);
      runAfter(reattachSession::close);
      producer.send(reattachSession.createMessage(false));
      reattachSession.commit();

      ClientConsumer consumer = reattachSession.createConsumer(ReattachDisabledTest.ADDRESS);
      reattachSession.start();
      assertNotNull(consumer.receive(5000));
      reattachSession.commit();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false, true);

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

      locator = createNettyNonHALocator();
      locator2 = createNettyNonHALocator();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();
   }

}