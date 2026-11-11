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
package org.apache.activemq.artemis.tests.integration.cluster.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.tests.util.CountDownSessionFailureListener;

public class SameProcessActiveMQServer implements TestableServer {

   private final ActiveMQServer server;

   public SameProcessActiveMQServer(ActiveMQServer server) {
      this.server = server;
   }

   @Override
   public boolean isActive() {
      return server.isActive();
   }

   @Override
   public void setIdentity(String identity) {
      server.setIdentity(identity);
   }

   @Override
   public boolean isStarted() {
      return server.isStarted();
   }

   @Override
   public void addInterceptor(Interceptor interceptor) {
      server.getRemotingService().addIncomingInterceptor(interceptor);
   }

   @Override
   public void removeInterceptor(Interceptor interceptor) {
      server.getRemotingService().removeIncomingInterceptor(interceptor);
   }

   @Override
   public void start() throws Exception {
      server.start();
   }

   @Override
   public void stop() throws Exception {
      server.stop();
   }

   @Override
   public CountDownLatch crash(ClientSession... sessions) throws Exception {
      return crash(true, sessions);
   }

   @Override
   public CountDownLatch crash(boolean waitFailure, ClientSession... sessions) throws Exception {
      return crash(true, waitFailure, sessions);
   }

   @Override
   public CountDownLatch crash(boolean failover, boolean waitFailure, ClientSession... sessions) throws Exception {
      CountDownLatch latch = new CountDownLatch(sessions.length);
      CountDownSessionFailureListener[] listeners = new CountDownSessionFailureListener[sessions.length];
      long callTimeout = 0;
      long failoverCallTimeout = 0;
      for (int i = 0; i < sessions.length; i++) {
         long timeout = sessions[i].getSessionFactory().getServerLocator().getCallTimeout();
         long failoverTimeout = sessions[i].getSessionFactory().getServerLocator().getCallFailoverTimeout();
         if (callTimeout < timeout) {
            callTimeout = timeout;
         }
         if (failoverCallTimeout < failoverTimeout) {
            failoverCallTimeout = failoverTimeout;
         }
         listeners[i] = new CountDownSessionFailureListener(latch, sessions[i]);
         sessions[i].addFailureListener(listeners[i]);
      }

      ClusterManager clusterManager = server.getClusterManager();
      clusterManager.flushExecutor();
      clusterManager.clear();
      assertTrue(server.isStarted(), "server should be running!");
      server.fail(failover);

      if (waitFailure) {
         // Wait to be informed of failure
         // In case of a failover, remote call will block and it may also wait for failover
         // so in order to give enough time we double the wait time.
         boolean ok = latch.await(callTimeout * 2 + failoverCallTimeout * 2, TimeUnit.MILLISECONDS);

         assertTrue(ok, "Failed to stop the server! Latch count is " + latch.getCount() + " out of " +
                              sessions.length);
      }

      return latch;
   }

   @Override
   public ActiveMQServer getServer() {
      return server;
   }
}
