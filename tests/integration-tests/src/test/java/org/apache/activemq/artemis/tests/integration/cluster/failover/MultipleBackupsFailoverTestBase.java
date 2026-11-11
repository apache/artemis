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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MultipleBackupsFailoverTestBase extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected abstract boolean isNetty();

   protected int waitForNewPrimary(long seconds,
                                   boolean waitForNewBackup,
                                   Map<Integer, TestableServer> servers,
                                   int... nodes) {
      long time = System.currentTimeMillis();
      long toWait = seconds * 1000;
      int newPrimary = -1;
      while (true) {
         for (int node : nodes) {
            TestableServer backupServer = servers.get(node);
            if (newPrimary == -1 && backupServer.isActive()) {
               newPrimary = node;
            } else if (newPrimary != -1) {
               if (waitForNewBackup) {
                  if (node != newPrimary && servers.get(node).isStarted()) {
                     return newPrimary;
                  }
               } else {
                  return newPrimary;
               }
            }
         }

         try {
            Thread.sleep(100);
         } catch (InterruptedException e) {
            // ignore
         }
         if (System.currentTimeMillis() > (time + toWait)) {
            fail("backup server never started");
         }
      }
   }

   protected ClientSession sendAndConsume(final ClientSessionFactory sf, final boolean createQueue) throws Exception {
      ClientSession session = sf.createSession(false, true, true);

      if (createQueue) {
         session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS).setDurable(false));
      }

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 1000;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.putIntProperty(SimpleString.of("count"), i);
         message.getBodyBuffer().writeString("aardvarks");
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receive(10000);

         assertNotNull(message2);

         assertEquals("aardvarks", message2.getBodyBuffer().readString());

         assertEquals(i, message2.getObjectProperty(SimpleString.of("count")));

         message2.acknowledge();
      }

      ClientMessage message3 = consumer.receiveImmediate();

      assertNull(message3);

      return session;
   }

   protected ClientSessionFactoryInternal createSessionFactoryAndWaitForTopology(ServerLocator locator,
                                                                                 int topologyMembers) throws Exception {
      return createSessionFactoryAndWaitForTopology(locator, topologyMembers, null);
   }

   protected ClientSessionFactoryInternal createSessionFactoryAndWaitForTopology(ServerLocator locator,
                                                                                 int topologyMembers,
                                                                                 ActiveMQServer server) throws Exception {
      ClientSessionFactoryInternal sf;
      CountDownLatch countDownLatch = new CountDownLatch(topologyMembers);

      FailoverTestBase.LatchClusterTopologyListener topListener = new FailoverTestBase.LatchClusterTopologyListener(countDownLatch);
      locator.addClusterTopologyListener(topListener);

      sf = (ClientSessionFactoryInternal) locator.createSessionFactory();
      addSessionFactory(sf);

      boolean ok = countDownLatch.await(5, TimeUnit.SECONDS);
      locator.removeClusterTopologyListener(topListener);
      if (!ok) {
         if (server != null) {
            logger.warn("failed topology, Topology on server = {}", server.getClusterManager().describe());
         }
      }
      assertTrue(ok, "expected " + topologyMembers + " members");
      return sf;
   }

   public ServerLocator getServerLocator(int... nodes) {
      TransportConfiguration[] configs = new TransportConfiguration[nodes.length];
      for (int i = 0, configsLength = configs.length; i < configsLength; i++) {
         configs[i] = createTransportConfiguration(isNetty(), false, generateParams(nodes[i], isNetty()));
      }
      return addServerLocator(new ServerLocatorImpl(true, configs));
   }
}
