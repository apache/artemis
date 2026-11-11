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

import org.apache.activemq.artemis.api.core.TransportConfigurationHelper;

/**
 * A ConnectorFactory is used by the client for creating connectors.
 * <p>
 * A Connector is used to connect to an org.apache.activemq.artemis.spi.core.remoting.Acceptor.
 */
public interface ConnectorFactory extends TransportConfigurationHelper {

   /**
    * creates a new instance of a connector.
    *
    * @param configuration       the configuration
    * @param handler             the handler
    * @param listener            the listener
    * @param closeExecutor       the close executor
    * @param threadPool          the thread pool
    * @param scheduledThreadPool the scheduled thread pool
    * @return a new connector
    */
   Connector createConnector(Map<String, Object> configuration,
                             BufferHandler handler,
                             ClientConnectionLifeCycleListener listener,
                             Executor closeExecutor,
                             Executor threadPool,
                             ScheduledExecutorService scheduledThreadPool,
                             ClientProtocolManager protocolManager);

   /**
    * Indicates if connectors from this factory are reliable or not. If a connector is reliable then connection
    * monitoring (i.e. pings/pongs) will be disabled.
    *
    * @return whether connectors from this factory are reliable
    */
   boolean isReliable();
}
