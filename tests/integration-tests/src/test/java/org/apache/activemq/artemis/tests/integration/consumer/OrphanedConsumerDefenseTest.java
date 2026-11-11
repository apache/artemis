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

package org.apache.activemq.artemis.tests.integration.consumer;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.protocol.ServerPacketDecoder;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnection;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPSessionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ProtonHandler;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.remoting.BaseConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * as worked through ARTEMIS-4476 we fixed the possibility of a ghost consumer (a term coined by a user), where the
 * connection is gone but the consumer still in memory.
 * <p>
 * The fix involved on calling the disconnect from the proper threads.
 * <p>
 * And as a line of defense the ServerConsumer and AMQP handler are also validating the connection states. If a
 * connection is in destroyed state tries to create a consumer, the system should throw an error.
 */
public class OrphanedConsumerDefenseTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testDefendNPESession() throws Exception {
      ActiveMQServerImpl server = Mockito.mock(ActiveMQServerImpl.class);
      SessionCallback sessionCallback = Mockito.mock(SessionCallback.class);
      ManagementService managementService = Mockito.mock(ManagementService.class);

      try {
         new ServerConsumerImpl(1, null, null, null, 1, true, false, new NullStorageManager(), sessionCallback, true, true, managementService, false, 0, server);
         fail("Exception was expected");
      } catch (NullPointerException e) {
         logger.debug("Expected exception", e);
      }
   }

   @Test
   public void testDefendDestroyedConnection() throws Exception {
      ActiveMQServerImpl server = Mockito.mock(ActiveMQServerImpl.class);

      SessionCallback sessionCallback = Mockito.mock(SessionCallback.class);

      ManagementService managementService = Mockito.mock(ManagementService.class);

      StorageManager storageManager = new NullStorageManager();

      ArtemisExecutor artemisExecutor = Mockito.mock(ArtemisExecutor.class);
      ExecutorFactory executorFactory = Mockito.mock(ExecutorFactory.class);
      Mockito.when(executorFactory.getExecutor()).thenReturn(artemisExecutor);

      Configuration configuration = new ConfigurationImpl();

      ActiveMQServer mockServer = Mockito.mock(ActiveMQServer.class);
      Mockito.when(mockServer.getExecutorFactory()).thenReturn(executorFactory);
      Mockito.when(mockServer.getConfiguration()).thenReturn(configuration);
      Mockito.when(mockServer.getManagementService()).thenReturn(mock(ManagementService.class));
      Mockito.when(mockServer.getConfiguration()).thenReturn(mock(Configuration.class));
      Mockito.when(mockServer.getResourceManager()).thenReturn(mock(ResourceManager.class));
      Mockito.when(mockServer.getSecurityStore()).thenReturn(mock(SecurityStore.class));

      BufferHandler bufferHandler = Mockito.mock(BufferHandler.class);
      InVMConnection inVMConnection = new InVMConnection(1, bufferHandler, Mockito.mock(BaseConnectionLifeCycleListener.class), artemisExecutor);

      RemotingConnectionImpl remotingConnection = new RemotingConnectionImpl(new ServerPacketDecoder(storageManager), inVMConnection, new ArrayList<>(), new ArrayList<>(), RandomUtil.randomUUIDSimpleString(), artemisExecutor);
      remotingConnection.destroy();
      ServerSessionImpl session = new ServerSessionImpl(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(),
                                                        RandomUtil.randomUUIDString(), 1000, true, true, true, true, true,
                                                        remotingConnection, mockServer, RandomUtil.randomUUIDSimpleString(), Mockito.mock(SessionCallback.class), Mockito.mock(OperationContext.class), new HashMap<>(), "securityDomain", false);

      try {
         new ServerConsumerImpl(1, session, null, null, 1, true, false, new NullStorageManager(), sessionCallback, true, true, managementService, false, 0, server);
         fail("Exception was expected");
      } catch (ActiveMQException activeMQException) {
         logger.info("Expected exception", activeMQException);
         assertTrue(activeMQException.getMessage().contains("AMQ229250"));
      }
   }

   @Test
   public void testDefendAMQPConsumerNullConnection() throws Exception {
      testDefendAMQPConsumer(true);
   }

   @Test
   public void testDefendAMQPConsumer() throws Exception {
      testDefendAMQPConsumer(false);
   }

   private void testDefendAMQPConsumer(boolean returnNullConnection) throws Exception {
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
      ProtonProtocolManagerFactory protonProtocolManagerFactory = new ProtonProtocolManagerFactory();
      ProtonProtocolManager protonProtocolManager = new ProtonProtocolManager(protonProtocolManagerFactory, server, new ArrayList<>(), new ArrayList<>());
      AMQPConnectionContext connectionContext = Mockito.mock(AMQPConnectionContext.class);

      if (returnNullConnection) {
         Mockito.when(connectionContext.getHandler()).thenReturn(null);
      } else {
         ProtonHandler protonHandler = Mockito.mock(ProtonHandler.class);
         Mockito.when(connectionContext.getHandler()).thenReturn(protonHandler);
         Connection qpidConnection = Mockito.mock(Connection.class);
         Mockito.when(qpidConnection.getRemoteState()).thenReturn(EndpointState.CLOSED);
         Mockito.when(protonHandler.getConnection()).thenReturn(qpidConnection);
      }

      Mockito.when(connectionContext.getProtocolManager()).thenReturn(protonProtocolManager);
      Sender sender = Mockito.mock(Sender.class);
      AMQPSessionContext sessionContext = Mockito.mock(AMQPSessionContext.class);
      AMQPSessionCallback sessionCallback = Mockito.mock(AMQPSessionCallback.class);

      try {
         ProtonServerSenderContext serverSenderContext = new ProtonServerSenderContext(connectionContext, sender, sessionContext, sessionCallback);
         serverSenderContext.initialize();
      } catch (ActiveMQException e) {
         assertTrue(e.getMessage().contains("AMQ119027"));
         logger.warn(e.getMessage(), e);
      }
   }
}