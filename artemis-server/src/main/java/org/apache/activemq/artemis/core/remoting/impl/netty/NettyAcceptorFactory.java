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
package org.apache.activemq.artemis.core.remoting.impl.netty;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.metrics.MetricsManager;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener;
import org.apache.activemq.artemis.utils.actors.OrderedExecutor;

public class NettyAcceptorFactory implements AcceptorFactory {

   @Override
   public Acceptor createAcceptor(final String name,
                                  final ClusterConnection connection,
                                  final Map<String, Object> configuration,
                                  final BufferHandler handler,
                                  final ServerConnectionLifeCycleListener listener,
                                  final Executor threadPool,
                                  final ScheduledExecutorService scheduledThreadPool,
                                  final Map<String, ProtocolManager> protocolMap,
                                  final String threadFactoryGroupName,
                                  MetricsManager metricsManager) {
      Executor failureExecutor = new OrderedExecutor(threadPool);
      return new NettyAcceptor(name, connection, configuration, handler, listener, scheduledThreadPool, failureExecutor, protocolMap, threadFactoryGroupName, metricsManager);
   }
}
