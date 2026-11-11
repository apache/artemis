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
package org.apache.activemq.artemis.tests.unit.core.remoting.server.impl.fake;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.core.security.ActiveMQPrincipal;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.management.NotificationService;
import org.apache.activemq.artemis.core.server.metrics.MetricsManager;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener;

public class FakeAcceptorFactory implements AcceptorFactory {

   private boolean started = false;

   @Override
   public Acceptor createAcceptor(String name,
                                  ClusterConnection clusterConnection,
                                  Map<String, Object> configuration,
                                  BufferHandler handler,
                                  ServerConnectionLifeCycleListener listener,
                                  Executor threadPool,
                                  ScheduledExecutorService scheduledThreadPool,
                                  Map<String, ProtocolManager> protocolMap,
                                  String threadFactoryGroupName,
                                  MetricsManager metricsManager) {
      return new FakeAcceptor();
   }

   private final class FakeAcceptor implements Acceptor {

      @Override
      public String getName() {
         return "fake";
      }

      @Override
      public void pause() {

      }

      @Override
      public void notifyStop() {

      }

      @Override
      public void updateInterceptors(List<BaseInterceptor> incomingInterceptors,
                                     List<BaseInterceptor> outgoingInterceptors) {
      }

      @Override
      public ClusterConnection getClusterConnection() {
         return null;
      }

      @Override
      public Map<String, Object> getConfiguration() {
         return null;
      }

      @Override
      public void setNotificationService(NotificationService notificationService) {

      }

      @Override
      public void setDefaultActiveMQPrincipal(ActiveMQPrincipal defaultActiveMQPrincipal) {

      }

      @Override
      public boolean isUnsecurable() {
         return false;
      }

      @Override
      public void reload() {

      }

      @Override
      public void start() throws Exception {
         started = true;
      }

      @Override
      public void stop() throws Exception {
         started = false;
      }

      @Override
      public boolean isStarted() {
         return started;
      }
   }
}
