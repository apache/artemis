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
package org.apache.activemq.artemis.uri.schema.connector;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.SchemaConstants;

public class TCPTransportConfigurationSchema extends AbstractTransportConfigurationSchema {

   private final Set<String> allowableProperties;

   public TCPTransportConfigurationSchema(Set<String> allowableProperties) {
      this.allowableProperties = allowableProperties;
   }

   @Override
   public String getSchemaName() {
      return SchemaConstants.TCP;
   }

   @Override
   protected List<TransportConfiguration> internalNewObject(URI uri,
                                                            Map<String, String> query,
                                                            String name) throws Exception {
      return getTransportConfigurations(uri, query, allowableProperties, name, getFactoryName(uri));
   }

   @Override
   protected URI internalNewURI(List<TransportConfiguration> bean) throws Exception {
      return null;
   }

   public static List<TransportConfiguration> getTransportConfigurations(URI uri,
                                                                         Map<String, String> query,
                                                                         Set<String> allowableProperties,
                                                                         String name,
                                                                         String factoryName) throws URISyntaxException {
      Map<String, Object> props = new HashMap<>();

      Map<String, Object> extraProps = new HashMap<>();
      BeanSupport.setData(uri, props, allowableProperties, query, extraProps);
      List<TransportConfiguration> transportConfigurations = new ArrayList<>();

      String nameFromQuery = query.getOrDefault(TransportConfiguration.NAME_PARAM, name);
      if (name != null && !name.equals(nameFromQuery)) {
         throw new IllegalArgumentException("Name doesn't match query param");
      }
      TransportConfiguration config = new TransportConfiguration(factoryName, props, nameFromQuery, extraProps);

      transportConfigurations.add(config);
      String connectors = uri.getFragment();

      if (connectors != null && !connectors.trim().isEmpty()) {
         String[] split = connectors.split(",");
         for (String s : split) {
            URI extraUri = new URI(s);
            Map<String, Object> newProps = new HashMap<>();
            extraProps = new HashMap<>();
            BeanSupport.setData(extraUri, newProps, allowableProperties, query, extraProps);
            Map<String, String> extraUriQuery = parseQuery(extraUri.getQuery(), null);
            BeanSupport.setData(extraUri, newProps, allowableProperties, extraUriQuery, extraProps);
            String extraUriNameFromQuery = extraUriQuery.getOrDefault(TransportConfiguration.NAME_PARAM, name + ":" + extraUri);
            transportConfigurations.add(new TransportConfiguration(factoryName, newProps, extraUriNameFromQuery, extraProps));
         }
      }
      return transportConfigurations;
   }

   protected String getFactoryName(URI uri) {
      //here for backwards compatibility
      if (uri.getPath() != null && uri.getPath().contains("hornetq")) {
         return "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory";
      }
      return NettyConnectorFactory.class.getName();
   }
}
