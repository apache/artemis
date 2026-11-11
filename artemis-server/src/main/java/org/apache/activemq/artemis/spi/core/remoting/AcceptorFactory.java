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
package org.apache.activemq.artemis.spi.core.remoting;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.metrics.MetricsManager;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;

/**
 * A factory for creating acceptors.
 * <p>
 * An Acceptor is an endpoint that a {@link org.apache.activemq.artemis.spi.core.remoting.Connector} will connect to and
 * is used by the remoting service.
 */
public interface AcceptorFactory {

   /**
    * Create a new instance of an Acceptor.
    *
    * @param name                the name of the acceptor
    * @param configuration       the configuration
    * @param handler             the handler
    * @param listener            the listener
    * @param threadPool          the threadpool
    * @param scheduledThreadPool a scheduled thread pool
    * @return an acceptor
    */
   Acceptor createAcceptor(String name,
                           ClusterConnection clusterConnection,
                           Map<String, Object> configuration,
                           BufferHandler handler,
                           ServerConnectionLifeCycleListener listener,
                           Executor threadPool,
                           ScheduledExecutorService scheduledThreadPool,
                           Map<String, ProtocolManager> protocolMap,
                           String threadFactoryGroupName,
                           MetricsManager metricsManager);

   default boolean supportsRemote() {
      return true;
   }

}
