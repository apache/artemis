/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.artemis.client.cdi.factory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.jms.JMSContext;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.artemis.client.cdi.configuration.ArtemisClientConfiguration;

@ApplicationScoped
public class ConnectionFactoryProvider {

   @Produces
   @ApplicationScoped
   private ActiveMQConnectionFactory activeMQConnectionFactory;

   @Inject
   private ArtemisClientConfiguration configuration;

   @Inject
   private Configuration embeddedConfiguration;

   @PostConstruct
   public void setupConnection() {
      if (configuration.startEmbeddedBroker()) {
         try {
            ActiveMQServer activeMQServer = ActiveMQServers.newActiveMQServer(embeddedConfiguration, false);
            activeMQServer.start();
         } catch (Exception e) {
            throw new RuntimeException("Unable to start embedded JMS", e);
         }
      }

      try {
         this.activeMQConnectionFactory = createConnectionFactory();
      } catch (Exception e) {
         throw new RuntimeException("Unable to connect to remote server", e);
      }
   }

   @Produces
   @ApplicationScoped
   public JMSContext createJMSContext() {
      return this.activeMQConnectionFactory.createContext();
   }

   private ActiveMQConnectionFactory createConnectionFactory() throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "1");
      final ActiveMQConnectionFactory activeMQConnectionFactory;
      if (configuration.getUrl() != null) {
         activeMQConnectionFactory = ActiveMQJMSClient.createConnectionFactory(configuration.getUrl(), null);
      } else {
         if (configuration.getHost() != null) {
            params.put(TransportConstants.HOST_PROP_NAME, configuration.getHost());
            params.put(TransportConstants.PORT_PROP_NAME, configuration.getPort());
         }
         if (configuration.isHa()) {
            activeMQConnectionFactory = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, new TransportConfiguration(configuration.getConnectorFactory(), params));
         } else {
            activeMQConnectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(configuration.getConnectorFactory(), params));
         }
      }
      if (configuration.hasAuthentication()) {
         activeMQConnectionFactory.setUser(configuration.getUsername());
         activeMQConnectionFactory.setPassword(configuration.getPassword());
      }
      return activeMQConnectionFactory;
   }
}
