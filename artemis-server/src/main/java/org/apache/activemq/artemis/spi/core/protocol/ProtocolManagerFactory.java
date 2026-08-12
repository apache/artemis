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
package org.apache.activemq.artemis.spi.core.protocol;

import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;

public interface ProtocolManagerFactory<P extends BaseInterceptor> {

   default Persister<Message>[] getPersister() {
      return new Persister[]{};
   }

   /**
    * When you create the ProtocolManager, you should filter out any interceptors that won't belong to this Protocol.
    * For example don't send any core Interceptors {@link org.apache.activemq.artemis.api.core.Interceptor} to Stomp * *
    * *
    */
   ProtocolManager createProtocolManager(ActiveMQServer server,
                                         Map<String, Object> parameters,
                                         List<BaseInterceptor> incomingInterceptors,
                                         List<BaseInterceptor> outgoingInterceptors) throws Exception;

   /**
    * This should get the entire list and only return the ones this factory can deal with
    */
   List<P> filterInterceptors(List<BaseInterceptor> interceptors);

   String[] getProtocols();

   String getModuleName();

   void loadProtocolServices(ActiveMQServer server, List<ActiveMQComponent> services) throws Exception;

   /**
    * Provides an entry point for the server to trigger the protocol manager factory to update its protocol services
    * based on updates to server configuration.
    *
    * @param server   The service instance that has triggered this update
    * @param services The protocol services that were previously registered (mutable).
    * @throws Exception can throw an exception if an error occurs while updating or adding protocol services from
    *                   configuration updates.
    */
   void updateProtocolServices(ActiveMQServer server, List<ActiveMQComponent> services) throws Exception;

}
