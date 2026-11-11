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

import java.io.NotSerializableException;
import java.net.URI;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.uri.schema.serverLocator.JGroupsServerLocatorSchema;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.SchemaConstants;

public class JGroupsSchema extends AbstractCFSchema {

   @Override
   public String getSchemaName() {
      return SchemaConstants.JGROUPS;
   }

   @Override
   public ActiveMQConnectionFactory internalNewObject(URI uri,
                                                      Map<String, String> query,
                                                      String name) throws Exception {
      JMSConnectionOptions options = newConectionOptions(uri, query);

      DiscoveryGroupConfiguration dcConfig = JGroupsServerLocatorSchema.getDiscoveryGroupConfiguration(uri, query, name);

      ActiveMQConnectionFactory factory;
      if (options.isHa()) {
         factory = ActiveMQJMSClient.createConnectionFactoryWithHA(dcConfig, options.getFactoryTypeEnum());
      } else {
         factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(dcConfig, options.getFactoryTypeEnum());
      }
      return setData(uri, query, factory);
   }

   @Override
   protected URI internalNewURI(ActiveMQConnectionFactory bean) throws Exception {
      DiscoveryGroupConfiguration dgc = bean.getDiscoveryGroupConfiguration();
      BroadcastEndpointFactory endpoint = dgc.getBroadcastEndpointFactory();
      String auth;
      if (endpoint instanceof JGroupsFileBroadcastEndpointFactory factory) {
         auth = factory.getChannelName();
      } else {
         throw new NotSerializableException(endpoint + "not serializable");
      }
      String query = BeanSupport.getData(null, bean, dgc, endpoint);
      dgc.setBroadcastEndpointFactory(endpoint);
      return new URI(SchemaConstants.JGROUPS, null, auth, -1, null, query, null);
   }
}
