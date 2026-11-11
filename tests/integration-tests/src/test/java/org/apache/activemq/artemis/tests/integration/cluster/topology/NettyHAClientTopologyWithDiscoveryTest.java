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
package org.apache.activemq.artemis.tests.integration.cluster.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class NettyHAClientTopologyWithDiscoveryTest extends HAClientTopologyWithDiscoveryTest {

   @Override
   protected boolean isNetty() {
      return true;
   }



   @Test
   public void testRecoveryBadUDPWithRetry() throws Exception {
      startServers(0);
      ServerLocatorImpl serverLocator = (ServerLocatorImpl) createHAServerLocator();
      serverLocator.setInitialConnectAttempts(10);
      serverLocator.initialize();
      serverLocator.getDiscoveryGroup().stop();


      ClientSessionFactory factory = serverLocator.createSessionFactory();
      ClientSession session = factory.createSession();
      session.close();
   }

   @Test
   public void testRecoveryBadUDPWithoutRetry() throws Exception {
      startServers(0);
      ServerLocatorImpl serverLocator = (ServerLocatorImpl) createHAServerLocator();
      serverLocator.setInitialConnectAttempts(0);
      serverLocator.initialize();
      serverLocator.getDiscoveryGroup().stop();


      boolean failure = false;
      try {
         ClientSessionFactory factory = serverLocator.createSessionFactory();
         ClientSession session = factory.createSession();
         session.close();
         factory.close();
      } catch (Exception e) {
         e.printStackTrace();
         failure = true;
      }

      assertTrue(failure);

      ClientSessionFactory factory = serverLocator.createSessionFactory();
      ClientSession session = factory.createSession();
      session.close();
      factory.close();

   }

   @Test
   public void testNoServer() {
      final ServerLocatorImpl serverLocator = (ServerLocatorImpl)ActiveMQClient.createServerLocatorWithHA(new DiscoveryGroupConfiguration().
              setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).
                      setGroupPort(groupPort)).setDiscoveryInitialWaitTimeout(10)).setInitialConnectAttempts(0);
      addServerLocator(serverLocator);
      serverLocator.setInitialConnectAttempts(3);

      try {
         serverLocator.createSessionFactory();
         fail("Exception was expected");
      } catch (Exception e) {
      }
   }


   @Test
   public void testConnectWithMultiThread() throws Exception {
      final AtomicInteger errors = new AtomicInteger(0);
      int NUMBER_OF_THREADS = 100;
      final CyclicBarrier barrier = new CyclicBarrier(NUMBER_OF_THREADS);
      final ServerLocatorImpl serverLocator = (ServerLocatorImpl)ActiveMQClient.createServerLocatorWithHA(new DiscoveryGroupConfiguration().
              setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).
                      setGroupPort(groupPort)).setDiscoveryInitialWaitTimeout(1000)).setInitialConnectAttempts(0);
      serverLocator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      addServerLocator(serverLocator);

      startServers(0);

      try {

         serverLocator.setInitialConnectAttempts(0);

         Runnable runnable = () -> {
            try {
               barrier.await();

               ClientSessionFactory factory = serverLocator.createSessionFactory();
               ClientSession session = factory.createSession();
               session.close();
               factory.close();

            } catch (Exception e) {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         };


         Thread[] threads = new Thread[NUMBER_OF_THREADS];

         for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(runnable);
            threads[i].start();
         }

         for (Thread t : threads) {
            t.join();
         }


         assertEquals(0, errors.get());

         serverLocator.close();

         serverLocator.getDiscoveryGroup().stop();
      } finally {
         stopServers(0);
      }
   }





}
