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
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.LockCoordinatorConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.metrics.MetricsManager;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.actors.OrderedExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LockCoordinatorStartOrderTest extends AmqpClientTestSupport {

   protected static final int AMQP_PORT_2 = 5673;
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   ActiveMQServer server_2;
   private AssertionLoggerHandler loggerHandler;

   @AfterEach
   public void stopServer1() throws Exception {
      if (server != null) {
         server.stop();
      }
   }

   @AfterEach
   public void stopServer2() throws Exception {
      if (server_2 != null) {
         server_2.stop();
      }
   }

   @BeforeEach
   public void startLogging() {
      loggerHandler = new AssertionLoggerHandler();

   }

   @AfterEach
   public void stopLogging() throws Exception {
      try {
         assertFalse(loggerHandler.findText("AMQ222214"));
      } finally {
         loggerHandler.close();
      }
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, false);
   }

   @Test
   public void testValidateAcceptorStartOrder() throws Exception {
      String queueName = getQueueName() + RandomUtil.randomUUIDString();

      server.setIdentity("Server1");
      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer2", "tcp://localhost:" + AMQP_PORT_2).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.setLockCoordinator("theLock");
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true).setQueueCreation(true));
         server.getConfiguration().addAMQPConnection(amqpConnection);

         HashMap<String, Object> params = new HashMap();
         params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, 60_000);
         TransportConfiguration transportConfiguration = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params);
         transportConfiguration.setLockCoordinator("theLock");
         transportConfiguration.setName("locked");
         server.getConfiguration().addAcceptorConfiguration(transportConfiguration);
         HashMap<String, String> properties = new HashMap<>();

         properties.put("locks-folder", getTemporaryDir());
         LockCoordinatorConfiguration lockCoordinatorConfiguration = new LockCoordinatorConfiguration(properties);
         lockCoordinatorConfiguration.setName("theLock").setClassName("org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager").setCheckPeriod(100).setLockId("theLock");
         server.getConfiguration().addLockCoordinatorConfiguration(lockCoordinatorConfiguration);
         server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }
      server.start();

      server_2 = createServer(AMQP_PORT_2, false);
      server_2.setIdentity("Server2");

      {
         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("connectTowardsServer1", "tcp://localhost:" + AMQP_PORT).setReconnectAttempts(300).setRetryInterval(100);
         amqpConnection.setLockCoordinator("theLock");
         amqpConnection.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
         server_2.getConfiguration().addAMQPConnection(amqpConnection);

         HashMap<String, Object> params = new HashMap();
         params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, 60_000);
         TransportConfiguration transportConfiguration = new TransportConfiguration(LockCoordinatorNettyFactory.class.getName(), params);
         transportConfiguration.setLockCoordinator("theLock");
         transportConfiguration.setName("locked");
         server_2.getConfiguration().addAcceptorConfiguration(transportConfiguration);

         HashMap<String, String> properties = new HashMap<>();
         properties.put("locks-folder", getTemporaryDir());
         LockCoordinatorConfiguration lockCoordinatorConfiguration = new LockCoordinatorConfiguration(properties);
         lockCoordinatorConfiguration.setName("theLock").setClassName("org.apache.activemq.artemis.lockmanager.file.FileBasedLockManager").setCheckPeriod(100).setLockId("theLock");
         server_2.getConfiguration().addLockCoordinatorConfiguration(lockCoordinatorConfiguration);
         server_2.getConfiguration().addQueueConfiguration(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }

      server_2.start();

      Wait.assertNotNull(() -> server_2.locateQueue(queueName), 5000, 100);

      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch waitSend = new CountDownLatch(1);

      ReplacedNettyAcceptor replacedNettyAcceptor = (ReplacedNettyAcceptor) server_2.getRemotingService().getAcceptor("locked");
      assertNotNull(replacedNettyAcceptor);
      replacedNettyAcceptor.setAfterStartCallback(() -> {
         try {
            started.countDown();
            waitSend.await(10, TimeUnit.SECONDS);
         } catch (Throwable ignored) {
         }
      });


      server.stop();

      assertTrue(started.await(10, TimeUnit.SECONDS));

      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:60000");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));
         producer.send(session.createTextMessage("RacedMessage"));
         session.commit();
      }

      waitSend.countDown();

      server.start();

      Queue queueOnServer1 = server.locateQueue(queueName);

      assertNotNull(queueOnServer1);

      Wait.assertEquals(1L, queueOnServer1::getMessageCount, 5000, 100);

      server_2.stop();

   }


   public static class LockCoordinatorNettyFactory implements AcceptorFactory {

      @Override
      public Acceptor createAcceptor(String name,
                                     ClusterConnection connection,
                                     Map<String, Object> configuration,
                                     BufferHandler handler,
                                     ServerConnectionLifeCycleListener listener,
                                     Executor threadPool,
                                     ScheduledExecutorService scheduledThreadPool,
                                     Map<String, ProtocolManager> protocolMap,
                                     String threadFactoryGroupName,
                                     MetricsManager metricsManager) {
         Executor failureExecutor = new OrderedExecutor(threadPool);
         return new ReplacedNettyAcceptor(name, connection, configuration, handler, listener, scheduledThreadPool, failureExecutor, protocolMap, threadFactoryGroupName, metricsManager);
      }
   }


   public static class ReplacedNettyAcceptor extends NettyAcceptor {

      private Runnable afterStartCallback;

      public ReplacedNettyAcceptor(String name,
                                   ClusterConnection clusterConnection,
                                   Map<String, Object> configuration,
                                   BufferHandler handler,
                                   ServerConnectionLifeCycleListener listener,
                                   ScheduledExecutorService scheduledThreadPool,
                                   Executor failureExecutor,
                                   Map<String, ProtocolManager> protocolMap,
                                   String threadFactoryGroupName,
                                   MetricsManager metricsManager) {
         super(name, clusterConnection, configuration, handler, listener, scheduledThreadPool, failureExecutor, protocolMap, threadFactoryGroupName, metricsManager);
      }


      @Override
      protected void internalStart() throws Exception {
         super.internalStart();
         if (afterStartCallback != null) {
            afterStartCallback.run();
         }
      }

      public Runnable getAfterStartCallback() {
         return afterStartCallback;
      }

      public ReplacedNettyAcceptor setAfterStartCallback(Runnable afterStartCallback) {
         this.afterStartCallback = afterStartCallback;
         return this;
      }
   }


}