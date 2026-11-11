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
package org.apache.activemq.artemis.tests.integration.cluster.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeTestAccessor;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NettyBridgeReconnectTest extends BridgeTestBase {

   final String bridgeName = "bridge1";
   final String testAddress = "testAddress";
   final int confirmationWindowSize = 100 * 1024;

   Map<String, Object> server0Params;
   Map<String, Object> server1Params;

   ActiveMQServer server0;
   ActiveMQServer server1;

   private TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params, "server0tc");
   private TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params, "server1tc");

   private Map<String, TransportConfiguration> connectors;
   private List<String> staticConnectors;

   @AfterEach
   public void destroyServer() throws Exception {
      if (server1 != null) {
         server1.stop();
      }

      if (server0 != null) {
         server0.stop();
      }
   }

   private void server1Start() throws Exception {
      server1.start();
   }

   public void server0Start() throws Exception {
      server0 = createActiveMQServer(0, server0Params, isNetty(), null);
      server0.getConfiguration().setConnectorConfigurations(connectors);
      server0.start();
   }

   @BeforeEach
   public void setServer() throws Exception {
      server0Params = new HashMap<>();
      server1Params = new HashMap<>();
      connectors = new HashMap<>();

      server1 = createActiveMQServer(1, isNetty(), server1Params);
      connectors.put(server1tc.getName(), server1tc);

      connectors.put(server0tc.getName(), server0tc);
      connectors.put(server1tc.getName(), server1tc);

      staticConnectors = new ArrayList<>();
      staticConnectors.add(server1tc.getName());
   }

   protected boolean isNetty() {
      return true;
   }

   private String getConnector() {
      return NETTY_CONNECTOR_FACTORY;
   }

   @Test
   public void testFailoverWhileSending() throws Exception {
      internalFailoverWhileSending(false);
   }

   @Test
   public void testFailoverWhileSendingInternalQueue() throws Exception {
      internalFailoverWhileSending(true);
   }

   private void internalFailoverWhileSending(boolean forceInternal) throws Exception {

      connectors.put(server0tc.getName(), server0tc);
      connectors.put(server1tc.getName(), server1tc);

      server1.getConfiguration().setConnectorConfigurations(connectors);

      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName(bridgeName).setQueueName(testAddress).setForwardingAddress(testAddress).setRetryInterval(10).setReconnectAttempts(-1).setReconnectAttemptsOnSameNode(-1).setConfirmationWindowSize(confirmationWindowSize).setPassword(CLUSTER_PASSWORD);
      List<String> bridgeConnectors = new ArrayList<>();
      bridgeConnectors.add(server0tc.getName());
      bridgeConfiguration.setStaticConnectors(bridgeConnectors);

      bridgeConfiguration.setQueueName(testAddress);
      List<BridgeConfiguration> bridgeConfigs = new ArrayList<>();
      bridgeConfigs.add(bridgeConfiguration);
      server1.getConfiguration().setBridgeConfigurations(bridgeConfigs);

      server0Start();
      server1Start();
      org.apache.activemq.artemis.core.server.Queue serverQueue1 = server1.locateQueue(testAddress);
      if (forceInternal) {
         // pretending this is an internal queue
         // as the check acks will play it differently
         serverQueue1.setInternalQueue(true);
      }

      int TRANSACTIONS = 10;
      int NUM_MESSAGES = 1000;

      try (ActiveMQConnectionFactory cf = (ActiveMQConnectionFactory) CFUtil.createConnectionFactory("core", "tcp://localhost:61617");
           Connection connection = cf.createConnection();
           Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
         Queue queue = session.createQueue(testAddress);
         MessageProducer producer = session.createProducer(queue);
         int sequenceID = 0;
         for (int j = 0; j < TRANSACTIONS; j++) {
            for (int i = 0; i < NUM_MESSAGES; i++) {
               producer.send(session.createTextMessage("" + (sequenceID++)));
            }
            session.commit();
         }
      }

      //remoteServer0 = SpawnedVMSupport.spawnVM(NettyBridgeReconnectTest.class.getName(), getTestDir());
      Wait.waitFor(() -> serverQueue1.getDeliveringCount() > 10, 300_000);

      BridgeImpl bridge = null;
      for (Consumer c : serverQueue1.getConsumers()) {
         System.out.println("Consumer " + c);
         if (c instanceof BridgeImpl bridgeConsumer) {
            bridge = bridgeConsumer;
         }
      }
      assertNotNull(bridge);

      Executor executorFail = server1.getExecutorFactory().getExecutor();

      {
         BridgeImpl finalBridge = bridge;
         Wait.assertTrue(() -> BridgeTestAccessor.withinRefs(finalBridge, (refs) -> {
            synchronized (refs) {
               if (refs.size() > 100) {
                  executorFail.execute(() -> {
                     finalBridge.connectionFailed(new ActiveMQException("bye"), false);
                  });
                  return true;
               } else {
                  return false;
               }
            }
         }));
      }

      AtomicInteger queuesTested = new AtomicInteger(0);
      // Everything is transferred, so everything should be zeroed on the addressses
      server1.getPostOffice().getAllBindings().filter(b -> b instanceof LocalQueueBinding).forEach((b) -> {
         queuesTested.incrementAndGet();
         try {
            Wait.assertEquals(0, ((LocalQueueBinding) b).getQueue().getPagingStore()::getAddressSize);
         } catch (Exception e) {
            fail(e.getMessage());
         }
      });
      assertTrue(queuesTested.get() > 0);

      Wait.assertEquals(0, serverQueue1::getDeliveringCount);
      Wait.assertEquals(0, serverQueue1::getMessageCount);

      try (ActiveMQConnectionFactory cf = (ActiveMQConnectionFactory) CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
           Connection connection = cf.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         Queue queue = session.createQueue(testAddress);
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         Set<String> received = new HashSet<>();
         for (int j = 0; j < TRANSACTIONS * NUM_MESSAGES; j++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            received.add(message.getText());
            //Assert.assertEquals("" + j, message.getText());
         }
         assertEquals(TRANSACTIONS * NUM_MESSAGES, received.size());
         assertNull(consumer.receiveNoWait());
      }


      queuesTested.set(0);
      // After everything is consumed.. we will check the sizes being 0
      server0.getPostOffice().getAllBindings().filter(b -> b instanceof LocalQueueBinding).forEach((b) -> {
         queuesTested.incrementAndGet();
         try {
            Wait.assertEquals(0, ((LocalQueueBinding) b).getQueue().getPagingStore()::getAddressSize);
         } catch (Exception e) {
            fail(e.getMessage());
         }
      });
      assertTrue(queuesTested.get() > 0);


   }

   @Override
   protected ActiveMQServer createActiveMQServer(final int id,
                                                 final Map<String, Object> params,
                                                 final boolean netty,
                                                 final NodeManager nodeManager) throws Exception {
      ActiveMQServer server = super.createActiveMQServer(id, params, netty, nodeManager);

      QueueConfiguration queueConfig0 = QueueConfiguration.of(testAddress).setAddress(testAddress).setRoutingType(RoutingType.ANYCAST);
      List<QueueConfiguration> queueConfigs0 = new ArrayList<>();
      queueConfigs0.add(queueConfig0);

      CoreAddressConfiguration addressConfiguration = new CoreAddressConfiguration();
      addressConfiguration.setName(testAddress).addRoutingType(RoutingType.ANYCAST);
      addressConfiguration.addQueueConfiguration(QueueConfiguration.of(testAddress).setAddress(testAddress).setRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().addAddressConfiguration(addressConfiguration);

      server.getConfiguration().setPersistIDCache(true);
      return server;
   }

}
