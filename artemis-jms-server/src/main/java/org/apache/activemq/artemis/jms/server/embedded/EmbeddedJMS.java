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
package org.apache.activemq.artemis.jms.server.embedded;

import javax.naming.Context;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.registry.JndiBindingRegistry;
import org.apache.activemq.artemis.core.registry.MapBindingRegistry;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.server.JMSServerManager;
import org.apache.activemq.artemis.jms.server.config.JMSConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.FileJMSConfiguration;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq.artemis.spi.core.naming.BindingRegistry;

/**
 * Deprecated in favor of org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ. Since Artemis 2.0 all JMS
 * specific broker management classes, interfaces, and methods have been deprecated in favor of their more general
 * counter-parts.
 * <p>
 * Simple bootstrap class that parses activemq config files (server and jms and security) and starts an ActiveMQServer
 * instance and populates it with configured JMS endpoints.
 * <p>
 * JMS Endpoints are registered with a simple MapBindingRegistry.  If you want to use a different registry you must set
 * the registry property of this class or call the setRegistry() method if you want to use JNDI
 */
@Deprecated
public class EmbeddedJMS extends EmbeddedActiveMQ {

   protected JMSServerManagerImpl serverManager;
   protected BindingRegistry registry;
   protected JMSConfiguration jmsConfiguration;
   protected Context context;

   public BindingRegistry getRegistry() {
      return registry;
   }

   public JMSServerManager getJMSServerManager() {
      return serverManager;
   }

   /**
    * Only set this property if you are using a custom BindingRegistry
    */
   public EmbeddedJMS setRegistry(BindingRegistry registry) {
      this.registry = registry;
      return this;
   }

   /**
    * By default, this class uses file-based configuration.  Set this property to override it.
    */
   public EmbeddedJMS setJmsConfiguration(JMSConfiguration jmsConfiguration) {
      this.jmsConfiguration = jmsConfiguration;
      return this;
   }

   /**
    * If you want to use JNDI instead of an internal map, set this property
    */
   public EmbeddedJMS setContext(Context context) {
      this.context = context;
      return this;
   }

   @Override
   public EmbeddedJMS setConfiguration(Configuration configuration) {
      super.setConfiguration(configuration);
      return this;
   }

   /**
    * Lookup in the registry for registered object, i.e. a ConnectionFactory.
    * <p>
    * This is a convenience method.
    */
   public Object lookup(String name) {
      return serverManager.getRegistry().lookup(name);
   }

   @Override
   public EmbeddedJMS start() throws Exception {
      super.createActiveMQServer();
      if (jmsConfiguration != null) {
         serverManager = new JMSServerManagerImpl(activeMQServer, jmsConfiguration);
      } else {
         FileJMSConfiguration fileConfiguration = new FileJMSConfiguration();
         FileDeploymentManager deploymentManager;
         if (configResourcePath != null) {
            deploymentManager = new FileDeploymentManager(configResourcePath);
         } else {
            deploymentManager = new FileDeploymentManager();
         }
         deploymentManager.addDeployable(fileConfiguration);
         deploymentManager.readConfiguration();
         serverManager = new JMSServerManagerImpl(activeMQServer, fileConfiguration);
      }

      if (registry == null) {
         if (context != null) {
            registry = new JndiBindingRegistry(context);
         } else {
            registry = new MapBindingRegistry();
         }
      }
      serverManager.setRegistry(registry);
      serverManager.start();

      return this;
   }

   @Override
   public EmbeddedJMS stop() throws Exception {
      serverManager.stop();
      return this;
   }

}
