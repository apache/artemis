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
import java.util.Map;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.uri.schema.serverLocator.UDPServerLocatorSchema;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.SchemaConstants;

public class UDPSchema extends AbstractCFSchema {

   @Override
   public String getSchemaName() {
      return SchemaConstants.UDP;
   }

   @Override
   public ActiveMQConnectionFactory internalNewObject(URI uri,
                                                      Map<String, String> query,
                                                      String name) throws Exception {
      JMSConnectionOptions options = newConectionOptions(uri, query);

      DiscoveryGroupConfiguration dgc = UDPServerLocatorSchema.getDiscoveryGroupConfiguration(uri, query, getHost(uri), getPort(uri), name);

      ActiveMQConnectionFactory factory;
      if (options.isHa()) {
         factory = ActiveMQJMSClient.createConnectionFactoryWithHA(dgc, options.getFactoryTypeEnum());
      } else {
         factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(dgc, options.getFactoryTypeEnum());
      }
      return setData(uri, query, factory);
   }

   @Override
   protected URI internalNewURI(ActiveMQConnectionFactory bean) throws Exception {
      DiscoveryGroupConfiguration dgc = bean.getDiscoveryGroupConfiguration();
      UDPBroadcastEndpointFactory endpoint = (UDPBroadcastEndpointFactory) dgc.getBroadcastEndpointFactory();
      String query = BeanSupport.getData(UDPServerLocatorSchema.IGNORED, bean, dgc, endpoint);
      dgc.setBroadcastEndpointFactory(endpoint);
      return new URI(SchemaConstants.UDP, null, endpoint.getGroupAddress(), endpoint.getGroupPort(), null, query, null);
   }
}
