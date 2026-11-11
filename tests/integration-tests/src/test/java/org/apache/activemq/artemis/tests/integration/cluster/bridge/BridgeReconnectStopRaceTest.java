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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeImpl;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BridgeReconnectStopRaceTest extends BridgeTestBase {

   private static class TestableBridge extends BridgeImpl {

      private final AtomicInteger connectAttempts = new AtomicInteger(0);
      private final CountDownLatch enteredSecondConnect = new CountDownLatch(1);
      private final CountDownLatch proceedWithSecondConnect = new CountDownLatch(1);

      TestableBridge(ServerLocatorInternal serverLocator,
                     BridgeConfiguration configuration,
                     UUID nodeUUID,
                     Queue queue,
                     Executor executor,
                     ScheduledExecutorService scheduledExecutor,
                     ActiveMQServer server) throws ActiveMQException {
         super(serverLocator, configuration, nodeUUID, queue, executor, scheduledExecutor, server);
      }

      @Override
      protected ClientSessionFactoryInternal createSessionFactory() throws Exception {
         if (connectAttempts.incrementAndGet() == 2) {
            enteredSecondConnect.countDown();
            if (!proceedWithSecondConnect.await(30, TimeUnit.SECONDS)) {
               throw new IllegalStateException("test did not release the second connect attempt in time");
            }
         }
         return super.createSessionFactory();
      }
   }

   @Test
   public void testStopDuringReconnectDoesNotReRegisterAsConsumer() throws Exception {
      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      Map<String, Object> server1Params = new HashMap<>();
      ActiveMQServer server0 = createActiveMQServer(0, false, new HashMap<>());
      ActiveMQServer server1 = createActiveMQServer(1, false, server1Params);

      server0.start();
      server1.start();
      waitForServerStart(server0);
      waitForServerStart(server1);

      Queue sourceQueue = server0.createQueue(QueueConfiguration.of(queueName0).setAddress(testAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      server1.createQueue(QueueConfiguration.of(queueName1).setAddress(forwardAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));

      TransportConfiguration server1tc = new TransportConfiguration(INVM_CONNECTOR_FACTORY, server1Params);

      ServerLocatorInternal serverLocator = (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithoutHA(server1tc);
      serverLocator.setReconnectAttempts(0);
      serverLocator.setInitialConnectAttempts(0);
      serverLocator.setConfirmationWindowSize(1024);

      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName("raceBridge").setQueueName(queueName0).setForwardingAddress(forwardAddress).setRetryInterval(10).setReconnectAttemptsOnSameNode(-1).setReconnectAttempts(-1).setConfirmationWindowSize(1024);

      TestableBridge bridge = new TestableBridge(serverLocator, bridgeConfiguration, server0.getNodeManager().getUUID(), sourceQueue, server0.getExecutorFactory().getExecutor(), server0.getScheduledPool(), server0);

      bridge.start();

      Wait.assertTrue("bridge must connect initially", bridge::isConnected, 10_000, 50);

      // simulate a connection failure to force a reconnect attempt, without actually killing server1
      bridge.connectionFailed(new ActiveMQException(ActiveMQExceptionType.DISCONNECTED, "simulated failure"), false);

      assertTrue(bridge.enteredSecondConnect.await(10, TimeUnit.SECONDS), "bridge must attempt to reconnect");

      // the reconnect attempt is now blocked inside createSessionFactory(); request a stop while it's in flight
      bridge.stop();

      AtomicBoolean reregisteredBridgeConsumer = new AtomicBoolean(false);
      Thread poller = new Thread(() -> {
         while (bridge.getState() != BridgeImpl.State.STOPPED) {
            if (sourceQueue.getConsumers().contains(bridge)) {
               reregisteredBridgeConsumer.set(true);
            }
            Thread.onSpinWait();
         }
      }, "consumer-registration-poller");
      poller.start();

      bridge.proceedWithSecondConnect.countDown();

      poller.join(10_000);
      assertFalse(poller.isAlive(), "poller did not observe the bridge reaching STOPPED in time");

      assertEquals(BridgeImpl.State.STOPPED, bridge.getState());
      assertFalse(reregisteredBridgeConsumer.get(), "bridge must not re-register as a queue consumer once stop() has been requested");
      assertFalse(sourceQueue.getConsumers().contains(bridge), "bridge must not remain a queue consumer after stopping");
   }
}
