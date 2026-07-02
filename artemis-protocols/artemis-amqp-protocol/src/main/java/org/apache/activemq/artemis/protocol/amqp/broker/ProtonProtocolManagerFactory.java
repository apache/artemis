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
package org.apache.activemq.artemis.protocol.amqp.broker;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.connect.AMQPBrokerConnectionManager;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManager;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManagerProvider;
import org.apache.activemq.artemis.spi.core.protocol.AbstractProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = ProtocolManagerFactory.class)
public class ProtonProtocolManagerFactory extends AbstractProtocolManagerFactory<AmqpInterceptor> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String AMQP_PROTOCOL_NAME = "AMQP";

   private static final String MODULE_NAME = "artemis-amqp-protocol";

   private static String[] SUPPORTED_PROTOCOLS = {AMQP_PROTOCOL_NAME};

   private AMQPBrokerConnectionManager brokerConnectionManager;

   @Override
   public Persister<Message>[] getPersister() {

      Persister[] persisters = new Persister[]{AMQPMessagePersister.getInstance(), AMQPMessagePersisterV2.getInstance(), AMQPMessagePersisterV3.getInstance(),
         AMQPMessagePersisterV4.getInstance(), AMQPLargeMessagePersister.getInstance(), AMQPLargeMessagePersisterV2.getInstance()};
      return persisters;
   }

   @Override
   public ProtocolManager createProtocolManager(ActiveMQServer server,
                                                final Map<String, Object> parameters,
                                                List<BaseInterceptor> incomingInterceptors,
                                                List<BaseInterceptor> outgoingInterceptors) throws Exception {
      BeanSupport.stripPasswords(parameters);
      return BeanSupport.setData(new ProtonProtocolManager(this, server, incomingInterceptors, outgoingInterceptors), parameters);
   }

   @Override
   public List<AmqpInterceptor> filterInterceptors(List<BaseInterceptor> interceptors) {
      return internalFilterInterceptors(AmqpInterceptor.class, interceptors);
   }

   @Override
   public String[] getProtocols() {
      return SUPPORTED_PROTOCOLS;
   }

   @Override
   public String getModuleName() {
      return MODULE_NAME;
   }

   /**
    * AMQP integration with the broker on this case needs to be soft as the broker may choose to not load the AMQP
    * Protocol module.
    */
   @Override
   public void loadProtocolServices(ActiveMQServer server, List<ActiveMQComponent> services) {
      try {
         AckManager ackManager = AckManagerProvider.getManager(server);
         services.add(ackManager);
         server.registerRecordsLoader(ackManager::reload);
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
      final List<AMQPBrokerConnectConfiguration> amqpServicesConfigurations = server.getConfiguration().getAMQPConnection();

      if (amqpServicesConfigurations != null && !amqpServicesConfigurations.isEmpty()) {
         brokerConnectionManager = new AMQPBrokerConnectionManager(this, amqpServicesConfigurations, server);
         services.add(brokerConnectionManager);
      }
   }

   /**
    * Check if broker configuration of AMQP broker connections or other broker configuration related to protocol
    * services has been updated and update the protocol services accordingly.
    */
   @Override
   public void updateProtocolServices(ActiveMQServer server, List<ActiveMQComponent> services) throws Exception {
      if (brokerConnectionManager == null) {
         checkAddNewBrokerConnectionManager(server, services);
      } else {
         updateBrokerConnectionManager(server, services);
      }
   }

   private void updateBrokerConnectionManager(ActiveMQServer server, List<ActiveMQComponent> services) throws Exception {
      final List<AMQPBrokerConnectConfiguration> amqpServicesConfigurations = server.getConfiguration().getAMQPConnection();

      brokerConnectionManager.updateConfiguration(amqpServicesConfigurations);

      if (brokerConnectionManager.getConfiguredConnectionsCount() == 0) {
         try {
            brokerConnectionManager.stop();
         } finally {
            services.remove(brokerConnectionManager);
            brokerConnectionManager = null;
         }
      }
   }

   private void checkAddNewBrokerConnectionManager(ActiveMQServer server, List<ActiveMQComponent> services) throws Exception {
      final List<AMQPBrokerConnectConfiguration> amqpServicesConfigurations = server.getConfiguration().getAMQPConnection();

      if (amqpServicesConfigurations != null && !amqpServicesConfigurations.isEmpty()) {
         brokerConnectionManager = new AMQPBrokerConnectionManager(this, amqpServicesConfigurations, server);
         services.add(brokerConnectionManager);
         brokerConnectionManager.start();
      }
   }
}
