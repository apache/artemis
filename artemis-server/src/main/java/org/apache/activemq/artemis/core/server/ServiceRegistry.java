/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.config.ConnectorServiceConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory;

/**
 * A holder for common services leveraged by the broker.
 */
public interface ServiceRegistry {

   ExecutorService getPageExecutorService();

   /**
    * Notice that if you want to provide your own PageExecutor, you should limit the number of threads to the number of
    * parallel reads you want to perform on paging
    */
   void setPageExecutorService(ExecutorService executorService);

   ExecutorService getExecutorService();

   void setExecutorService(ExecutorService executorService);

   ExecutorService getIOExecutorService();

   void setIOExecutorService(ExecutorService ioExecutorService);

   ScheduledExecutorService getScheduledExecutorService();

   void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService);

   void addConnectorService(ConnectorServiceFactory connectorServiceFactory, ConnectorServiceConfiguration configuration);

   void removeConnectorService(ConnectorServiceConfiguration configuration);

   /**
    * Get a collection of paired org.apache.activemq.artemis.core.server.ConnectorServiceFactory and
    * org.apache.activemq.artemis.core.config.ConnectorServiceConfiguration instances.
    */
   Collection<Pair<ConnectorServiceFactory, ConnectorServiceConfiguration>> getConnectorServices(List<ConnectorServiceConfiguration> configs);

   /**
    * Get connector service for a given configuration.
    *
    * @param configuration The connector service configuration.
    * @return an instance of the connector service factory
    */
   ConnectorServiceFactory getConnectorService(ConnectorServiceConfiguration configuration);

   void addIncomingInterceptor(BaseInterceptor interceptor);

   /**
    * Get a list of org.apache.activemq.artemis.api.core.BaseInterceptor instances
    */
   List<BaseInterceptor> getIncomingInterceptors(List<String> classNames);

   void addOutgoingInterceptor(BaseInterceptor interceptor);

   /**
    * Get a list of org.apache.activemq.artemis.api.core.BaseInterceptor instances
    */
   List<BaseInterceptor> getOutgoingInterceptors(List<String> classNames);

   /**
    * Get an instance of org.apache.activemq.artemis.core.server.transformer.Transformer for a divert
    *
    * @param name                     the name of divert for which the transformer will be used
    * @param transformerConfiguration the transformer configuration
    */
   Transformer getDivertTransformer(String name, TransformerConfiguration transformerConfiguration);

   void addDivertTransformer(String name, Transformer transformer);

   void removeDivertTransformer(String name);

   /**
    * Get an instance of org.apache.activemq.artemis.core.server.transformer.Transformer for a bridge
    *
    * @param name                     the name of bridge for which the transformer will be used
    * @param transformerConfiguration the transformer configuration
    */
   Transformer getBridgeTransformer(String name, TransformerConfiguration transformerConfiguration);

   void addBridgeTransformer(String name, Transformer transformer);

   /**
    * Get an instance of org.apache.activemq.artemis.core.server.transformer.Transformer for federation
    *
    * @param name                     the name of bridge for which the transformer will be used
    * @param transformerConfiguration the transformer configuration
    */
   Transformer getFederationTransformer(String name, TransformerConfiguration transformerConfiguration);

   void addFederationTransformer(String name, Transformer transformer);

   /**
    * Get an instance of org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory
    *
    * @param name      the name of acceptor for which the factory will be used
    * @param className the fully qualified name of the factory implementation (can be null)
    */
   AcceptorFactory getAcceptorFactory(String name, String className);

   void addAcceptorFactory(String name, AcceptorFactory acceptorFactory);
}
