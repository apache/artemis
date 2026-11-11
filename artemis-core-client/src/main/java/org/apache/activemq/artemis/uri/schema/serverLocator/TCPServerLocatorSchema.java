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
package org.apache.activemq.artemis.uri.schema.serverLocator;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.uri.schema.connector.TCPTransportConfigurationSchema;
import org.apache.activemq.artemis.utils.IPV6Util;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.SchemaConstants;

public class TCPServerLocatorSchema extends AbstractServerLocatorSchema {

   @Override
   public String getSchemaName() {
      return SchemaConstants.TCP;
   }

   @Override
   protected ServerLocator internalNewObject(URI uri, Map<String, String> query, String name) throws Exception {
      List<TransportConfiguration> configurations = TCPTransportConfigurationSchema.getTransportConfigurations(uri, query, TransportConstants.ALLOWABLE_CONNECTOR_KEYS, name, NettyConnectorFactory.class.getName());
      TransportConfiguration[] tcs = new TransportConfiguration[configurations.size()];
      configurations.toArray(tcs);

      BeanSupport.stripPasswords(query);
      ConnectionOptions options = newConnectionOptions(uri, query);
      if (options.isHa()) {
         return BeanSupport.setData(uri, ActiveMQClient.createServerLocatorWithHA(tcs), query);
      } else {
         return BeanSupport.setData(uri, ActiveMQClient.createServerLocatorWithoutHA(tcs), query);
      }
   }

   @Override
   protected URI internalNewURI(ServerLocator bean) throws Exception {
      String query = BeanSupport.getData(null, bean);
      TransportConfiguration[] staticConnectors = bean.getStaticTransportConfigurations();
      return getURI(query, staticConnectors);
   }

   public static URI getURI(String query, TransportConfiguration[] staticConnectors) throws Exception {
      if (staticConnectors == null || staticConnectors.length < 1) {
         throw new Exception();
      }
      StringBuilder fragment = new StringBuilder();
      for (int i = 1; i < staticConnectors.length; i++) {
         TransportConfiguration connector = staticConnectors[i];
         Map<String, Object> params = escapeIPv6Host(connector.getCombinedParams());
         URI extraUri = new URI(SchemaConstants.TCP, null, getHost(params), getPort(params), null, createQuery(params, null), null);
         if (i > 1) {
            fragment.append(",");
         }
         fragment.append(extraUri.toASCIIString());

      }
      Map<String, Object> params = escapeIPv6Host(staticConnectors[0].getCombinedParams());
      return new URI(SchemaConstants.TCP, null, getHost(params), getPort(params), null, createQuery(params, query), fragment.toString());
   }

   @SuppressWarnings("StringEquality")
   private static Map<String, Object> escapeIPv6Host(Map<String, Object> params) {
      String host = (String) params.get("host");
      String newHost = IPV6Util.encloseHost(host);

      // We really want to check the objects here
      // Some bug finders may report this as an error, hence the SuppressWarnings on this method
      if (host != newHost) {
         params.put("host", "[" + host + "]");
      }

      return params;
   }

   private static int getPort(Map<String, Object> params) {
      Object port = params.get("port");
      if (port instanceof String string) {
         return Integer.parseInt(string);
      }
      return port != null ? (int) port : 61616;
   }

   private static String getHost(Map<String, Object> params) {
      return params.get("host") != null ? (String) params.get("host") : "localhost";
   }

   private static String createQuery(Map<String, Object> params, String query) throws Exception {
      StringBuilder cb;
      boolean empty;
      if (query == null) {
         cb = new StringBuilder();
         empty = true;
      } else {
         cb = new StringBuilder(query);
         empty = false;
      }

      for (Map.Entry<String, Object> entry : params.entrySet()) {
         if (entry.getValue() != null) {
            if (!empty) {
               cb.append("&");
            } else {
               empty = false;
            }
            cb.append(BeanSupport.encodeURI(entry.getKey()));
            cb.append("=");
            cb.append(BeanSupport.encodeURI(entry.getValue().toString()));
         }
      }
      return cb.toString();
   }
}
