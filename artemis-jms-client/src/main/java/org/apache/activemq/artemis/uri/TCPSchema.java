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
package org.apache.activemq.artemis.uri;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.uri.schema.connector.TCPTransportConfigurationSchema;
import org.apache.activemq.artemis.uri.schema.serverLocator.TCPServerLocatorSchema;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.SchemaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class TCPSchema extends AbstractCFSchema {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   public String getSchemaName() {
      return SchemaConstants.TCP;
   }

   @Override
   protected ActiveMQConnectionFactory internalNewObject(URI uri,
                                                         Map<String, String> query,
                                                         String name) throws Exception {
      JMSConnectionOptions options = newConectionOptions(uri, query);

      List<TransportConfiguration> configurations = TCPTransportConfigurationSchema.getTransportConfigurations(uri, query, TransportConstants.ALLOWABLE_CONNECTOR_KEYS, name, NettyConnectorFactory.class.getName());

      TransportConfiguration[] tcs = new TransportConfiguration[configurations.size()];

      configurations.toArray(tcs);

      ActiveMQConnectionFactory factory;

      if (options.isHa()) {
         factory = ActiveMQJMSClient.createConnectionFactoryWithHA(options.getFactoryTypeEnum(), tcs);
      } else {
         factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(options.getFactoryTypeEnum(), tcs);
      }

      setData(uri, query, factory);

      checkIgnoredQueryFields(factory, query);

      return factory;
   }

   @Override
   protected void internalPopulateObject(URI uri,
                                         Map<String, String> query,
                                         ActiveMQConnectionFactory bean) throws Exception {
      super.internalPopulateObject(uri, query, bean);

      checkIgnoredQueryFields(bean, query);
   }

   private void checkIgnoredQueryFields(ActiveMQConnectionFactory factory, Map<String, String> query) throws Exception {
      Properties factoryProperties = new Properties();
      BeanSupport.getProperties(factory, factoryProperties);

      for (String key: query.keySet()) {
         if (!key.equals("ha") && !key.equals("type") &&
            !TransportConstants.ALLOWABLE_CONNECTOR_KEYS.contains(key) &&
            !factoryProperties.containsKey(key)) {
            logger.debug("Connection factory parameter {} is not standard", key);
         }
      }
   }

   @Override
   protected URI internalNewURI(ActiveMQConnectionFactory bean) throws Exception {
      String query = BeanSupport.getData(null, bean);
      TransportConfiguration[] staticConnectors = bean.getStaticConnectors();
      return TCPServerLocatorSchema.getURI(query, staticConnectors);
   }
}
