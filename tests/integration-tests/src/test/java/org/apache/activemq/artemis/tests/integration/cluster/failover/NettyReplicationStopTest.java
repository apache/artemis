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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.replication.ReplicationEndpoint;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.tests.integration.cluster.util.SameProcessActiveMQServer;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.junit.jupiter.api.Test;

public class NettyReplicationStopTest extends FailoverTestBase {

   @Override
   protected TestableServer createTestableServer(Configuration config) {
      return new SameProcessActiveMQServer(createServer(true, config));
   }

   @Override
   protected void createConfigs() throws Exception {
      createReplicatedConfigs();
   }

   @Override
   protected NodeManager createNodeManager() throws Exception {
      return new InVMNodeManager(false);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return getNettyAcceptorTransportConfiguration(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return getNettyConnectorTransportConfiguration(live);
   }

   @Override
   protected final void crash(boolean waitFailure, ClientSession... sessions) throws Exception {
      if (sessions.length > 0) {
         for (ClientSession session : sessions) {
            waitForRemoteBackup(session.getSessionFactory(), 5, true, backupServer.getServer());
         }
      } else {
         waitForRemoteBackup(null, 5, true, backupServer.getServer());
      }
      super.crash(waitFailure, sessions);
   }

   @Override
   protected final void crash(ClientSession... sessions) throws Exception {
      crash(true, sessions);
   }

   @Test
   public void testReplicaStop() throws Exception {

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, "127.0.0.1");
      TransportConfiguration tc = createTransportConfiguration(true, false, params);

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithHA(tc)).setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true).setReconnectAttempts(15);

      final ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(true, true);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      final int numMessages = 10;

      ReplicationEndpoint endpoint = getReplicationEndpoint(backupServer.getServer());

      endpoint.pause();

      List<Thread> threads = new ArrayList<>();
      final ArrayList<Integer> codesSent = new ArrayList<>();

      CountDownLatch alignedOnSend = new CountDownLatch(10);

      for (int i = 0; i < numMessages; i++) {
         final int code = i;
         Thread t = new Thread("WillSend " + code) {
            @Override
            public void run() {
               try {
                  ClientSession session = sf.createSession(true, true);

                  ClientProducer producer = session.createProducer(ADDRESS);

                  ClientMessage message = session.createMessage(true).putIntProperty("i", code);
                  alignedOnSend.countDown();
                  producer.send(message);
                  codesSent.add(code);
               } catch (Exception e) {
                  // that's ok;
                  e.printStackTrace(); // logging just for debug & reference
               }
            }
         };

         t.start();

         threads.add(t);
      }

      assertTrue(alignedOnSend.await(10, TimeUnit.SECONDS));
      primaryServer.stop();

      assertEquals(0, codesSent.size());

   }
}
