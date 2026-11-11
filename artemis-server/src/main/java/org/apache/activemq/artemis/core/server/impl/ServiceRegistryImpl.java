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
package org.apache.activemq.artemis.core.server.impl;

import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.config.ConnectorServiceConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ConnectorServiceFactory;
import org.apache.activemq.artemis.core.server.ServiceRegistry;
import org.apache.activemq.artemis.core.server.transformer.RegisteredTransformer;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

public class ServiceRegistryImpl implements ServiceRegistry {

   private ExecutorService executorService;

   private ExecutorService pageExecutorService;

   private ExecutorService ioExecutorService;

   private ScheduledExecutorService scheduledExecutorService;

   /*
    * We are using a List rather than HashMap here as Apache Artemis allows multiple instances of the same class to be added
   * to the interceptor list
   */
   private List<BaseInterceptor> incomingInterceptors;

   private List<BaseInterceptor> outgoingInterceptors;

   private Map<String, Transformer> divertTransformers;

   private Map<String, Transformer> bridgeTransformers;

   private Map<String, Transformer> federationTransformers;

   private Map<String, AcceptorFactory> acceptorFactories;

   private Map<String, Pair<ConnectorServiceFactory, ConnectorServiceConfiguration>> connectorServices;

   public ServiceRegistryImpl() {
      this.incomingInterceptors = Collections.synchronizedList(new ArrayList<>());
      this.outgoingInterceptors = Collections.synchronizedList(new ArrayList<>());
      this.connectorServices = new ConcurrentHashMap<>();
      this.divertTransformers = new ConcurrentHashMap<>();
      this.bridgeTransformers = new ConcurrentHashMap<>();
      this.federationTransformers = new ConcurrentHashMap<>();
      this.acceptorFactories = new ConcurrentHashMap<>();
   }

   @Override
   public ExecutorService getPageExecutorService() {
      return pageExecutorService;
   }

   @Override
   public void setPageExecutorService(ExecutorService executorService) {
      this.pageExecutorService = executorService;
   }

   @Override
   public ExecutorService getExecutorService() {
      return executorService;
   }

   @Override
   public void setExecutorService(ExecutorService executorService) {
      this.executorService = executorService;
   }

   @Override
   public ScheduledExecutorService getScheduledExecutorService() {
      return scheduledExecutorService;
   }

   @Override
   public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
      this.scheduledExecutorService = scheduledExecutorService;
   }

   @Override
   public void addConnectorService(ConnectorServiceFactory connectorServiceFactory,
                                   ConnectorServiceConfiguration configuration) {
      connectorServices.put(configuration.getConnectorName(), new Pair<>(connectorServiceFactory, configuration));
   }

   @Override
   public void removeConnectorService(ConnectorServiceConfiguration configuration) {
      connectorServices.remove(configuration.getConnectorName());
   }

   @Override
   public Collection<Pair<ConnectorServiceFactory, ConnectorServiceConfiguration>> getConnectorServices(List<ConnectorServiceConfiguration> configs) {
      if (configs != null) {
         for (final ConnectorServiceConfiguration config : configs) {
            if (connectorServices.get(config.getConnectorName()) == null) {
               ConnectorServiceFactory factory = loadClass(config.getFactoryClassName(), ConnectorServiceFactory.class);
               addConnectorService(factory, config);
            }
         }
      }

      return connectorServices.values();
   }

   @Override
   public ConnectorServiceFactory getConnectorService(ConnectorServiceConfiguration configuration) {
      return loadClass(configuration.getFactoryClassName(), ConnectorServiceFactory.class);
   }

   @Override
   public void addIncomingInterceptor(BaseInterceptor interceptor) {
      incomingInterceptors.add(interceptor);
   }

   @Override
   public List<BaseInterceptor> getIncomingInterceptors(List<String> classNames) {
      List<BaseInterceptor> interceptors = new ArrayList<>(incomingInterceptors);

      instantiateInterceptors(classNames, interceptors);

      return interceptors;
   }

   @Override
   public void addOutgoingInterceptor(BaseInterceptor interceptor) {
      outgoingInterceptors.add(interceptor);
   }

   @Override
   public List<BaseInterceptor> getOutgoingInterceptors(List<String> classNames) {
      List<BaseInterceptor> interceptors = new ArrayList<>(outgoingInterceptors);

      instantiateInterceptors(classNames, interceptors);

      return interceptors;
   }

   @Override
   public void addDivertTransformer(String name, Transformer transformer) {
      divertTransformers.put(name, transformer);
   }

   @Override
   public void removeDivertTransformer(String name) {
      divertTransformers.remove(name);
   }

   @Override
   public Transformer getDivertTransformer(String name, TransformerConfiguration transformerConfiguration) {
      Transformer transformer = divertTransformers.get(name);

      if (transformer == null && transformerConfiguration != null && transformerConfiguration.getClassName() != null) {
         transformer = instantiateTransformer(transformerConfiguration);
         addDivertTransformer(name, transformer);
      }

      return transformer;
   }

   @Override
   public ExecutorService getIOExecutorService() {
      return ioExecutorService;
   }

   @Override
   public void setIOExecutorService(ExecutorService ioExecutorService) {
      this.ioExecutorService = ioExecutorService;
   }

   @Override
   public void addBridgeTransformer(String name, Transformer transformer) {
      bridgeTransformers.put(name, transformer);
   }

   @Override
   public Transformer getBridgeTransformer(String name, TransformerConfiguration transformerConfiguration) {
      Transformer transformer = bridgeTransformers.get(name);

      if (transformer == null && transformerConfiguration != null && transformerConfiguration.getClassName() != null) {
         transformer = instantiateTransformer(transformerConfiguration);
         addBridgeTransformer(name, transformer);
      }

      return transformer;
   }

   @Override
   public void addFederationTransformer(String name, Transformer transformer) {
      federationTransformers.put(name, transformer);
   }

   @Override
   public Transformer getFederationTransformer(String name, TransformerConfiguration transformerConfiguration) {
      Transformer transformer = federationTransformers.get(name);

      if (transformer == null && transformerConfiguration != null && transformerConfiguration.getClassName() != null) {
         transformer = instantiateTransformer(transformerConfiguration);
         addFederationTransformer(name, transformer);
      }

      return transformer;
   }

   @Override
   public AcceptorFactory getAcceptorFactory(String name, final String className) {
      AcceptorFactory factory = acceptorFactories.get(name);

      if (factory == null && className != null) {
         factory = loadClass(className, AcceptorFactory.class);
         addAcceptorFactory(name, factory);
      }

      return factory;
   }

   @Override
   public void addAcceptorFactory(String name, AcceptorFactory acceptorFactory) {
      acceptorFactories.put(name, acceptorFactory);
   }

   @SuppressWarnings("TypeParameterUnusedInFormals")
   public <T> T loadClass(final String className, Class expectedType) {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<T>) () -> (T) ClassloadingUtil.newInstanceFromClassLoader(className, expectedType));
   }

   private Transformer instantiateTransformer(final TransformerConfiguration transformerConfiguration) {
      Transformer transformer = null;

      if (transformerConfiguration != null && transformerConfiguration.getClassName() != null) {
         try {
            transformer = new RegisteredTransformer(loadClass(transformerConfiguration.getClassName(), Transformer.class));
            transformer.init(Collections.unmodifiableMap(transformerConfiguration.getProperties()));
         } catch (Exception e) {
            throw ActiveMQMessageBundle.BUNDLE.errorCreatingTransformerClass(transformerConfiguration.getClassName(), e);
         }
      }
      return transformer;
   }

   private void instantiateInterceptors(List<String> classNames, List<BaseInterceptor> interceptors) {
      if (classNames != null) {
         for (final String className : classNames) {
            BaseInterceptor interceptor = loadClass(className, BaseInterceptor.class);
            interceptors.add(interceptor);
         }
      }
   }
}
