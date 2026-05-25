/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.routing;

import javax.jms.ConnectionFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.routing.ConnectionRouterConfiguration;
import org.apache.activemq.artemis.core.config.routing.CacheConfiguration;
import org.apache.activemq.artemis.core.config.routing.NamedPropertyConfiguration;
import org.apache.activemq.artemis.core.config.routing.PoolConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;

public class RoutingTestBase extends ClusterTestBase {
   protected static final String AMQP_PROTOCOL = "AMQP";
   protected static final String CORE_PROTOCOL = "CORE";
   protected static final String MQTT_PROTOCOL = "MQTT";
   protected static final String OPENWIRE_PROTOCOL = "OPENWIRE";

   protected static final String CLUSTER_POOL = "CLUSTER";
   protected static final String DISCOVERY_POOL = "DISCOVERY";
   protected static final String STATIC_POOL = "STATIC";

   protected static final String CONNECTION_ROUTER_NAME = "bb1";

   protected static final String DEFAULT_CONNECTOR_NAME = "DEFAULT";

   protected static final String GROUP_ADDRESS = ActiveMQTestBase.getUDPDiscoveryAddress();

   protected static final int GROUP_PORT = ActiveMQTestBase.getUDPDiscoveryPort();


   protected TransportConfiguration getDefaultServerAcceptor(final int node) {
      return getServer(node).getConfiguration().getAcceptorConfigurations().stream().findFirst().get();
   }

   protected TransportConfiguration getDefaultServerConnector(final int node) {
      Map<String, TransportConfiguration> connectorConfigurations = getServer(node).getConfiguration().getConnectorConfigurations();
      TransportConfiguration connector = connectorConfigurations.get(DEFAULT_CONNECTOR_NAME);
      return Objects.requireNonNullElseGet(connector, () -> connectorConfigurations.values().stream().findFirst().get());
   }

   protected TransportConfiguration setupDefaultServerConnector(final int node) {
      TransportConfiguration defaultServerConnector = getDefaultServerConnector(node);

      if (!defaultServerConnector.getName().equals(DEFAULT_CONNECTOR_NAME)) {
         defaultServerConnector = new TransportConfiguration(defaultServerConnector.getFactoryClassName(),
            defaultServerConnector.getParams(), DEFAULT_CONNECTOR_NAME, defaultServerConnector.getExtraParams());

         getServer(node).getConfiguration().getConnectorConfigurations().put(DEFAULT_CONNECTOR_NAME, defaultServerConnector);
      }

      return defaultServerConnector;
   }

   protected void setupRouterServerWithCluster(final int node, final KeyType keyType, final String policyName, final Map<String, String> properties, final boolean localTargetEnabled, final String localTargetFilter, final int quorumSize, String clusterConnection) {
      Configuration configuration = getServer(node).getConfiguration();
      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration().setName(CONNECTION_ROUTER_NAME);

      setupDefaultServerConnector(node);

      connectionRouterConfiguration.setKeyType(keyType).setLocalTargetFilter(localTargetFilter)
         .setPoolConfiguration(new PoolConfiguration().setCheckPeriod(1000).setQuorumSize(quorumSize)
            .setLocalTargetEnabled(localTargetEnabled).setClusterConnection(clusterConnection))
         .setPolicyConfiguration(new NamedPropertyConfiguration().setName(policyName).setProperties(properties));

      configuration.setConnectionRouters(Collections.singletonList(connectionRouterConfiguration));

      TransportConfiguration acceptor = getDefaultServerAcceptor(node);
      acceptor.getParams().put("router", CONNECTION_ROUTER_NAME);
   }

   protected void setupRouterServerWithDiscovery(final int node, final KeyType keyType, final String policyName, final Map<String, String> properties, final boolean localTargetEnabled, final String localTargetFilter, final int quorumSize) {
      Configuration configuration = getServer(node).getConfiguration();
      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration().setName(CONNECTION_ROUTER_NAME);

      setupDefaultServerConnector(node);

      connectionRouterConfiguration.setKeyType(keyType).setLocalTargetFilter(localTargetFilter)
         .setPoolConfiguration(new PoolConfiguration().setCheckPeriod(1000).setQuorumSize(quorumSize)
            .setLocalTargetEnabled(localTargetEnabled).setDiscoveryGroupName("dg1"))
         .setPolicyConfiguration(new NamedPropertyConfiguration().setName(policyName).setProperties(properties));

      configuration.setConnectionRouters(Collections.singletonList(connectionRouterConfiguration));

      TransportConfiguration acceptor = getDefaultServerAcceptor(node);
      acceptor.getParams().put("router", CONNECTION_ROUTER_NAME);
   }

   protected void setupRouterServerWithStaticConnectors(final int node, final KeyType keyType, final String policyName, final Map<String, String> properties, final boolean localTargetEnabled, final String localTargetFilter, final int quorumSize, final int... targetNodes) {
      Configuration configuration = getServer(node).getConfiguration();
      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration().setName(CONNECTION_ROUTER_NAME);

      setupDefaultServerConnector(node);

      List<String> staticConnectors = new ArrayList<>();
      for (int targetNode : targetNodes) {
         TransportConfiguration connector = getDefaultServerConnector(targetNode);
         configuration.getConnectorConfigurations().put(connector.getName(), connector);
         staticConnectors.add(connector.getName());
      }

      connectionRouterConfiguration.setKeyType(keyType).setLocalTargetFilter(localTargetFilter)
         .setPoolConfiguration(new PoolConfiguration().setCheckPeriod(1000).setQuorumSize(quorumSize)
            .setLocalTargetEnabled(localTargetEnabled).setStaticConnectors(staticConnectors))
         .setPolicyConfiguration(new NamedPropertyConfiguration().setName(policyName).setProperties(properties));

      configuration.setConnectionRouters(Collections.singletonList(connectionRouterConfiguration));

      TransportConfiguration acceptor = getDefaultServerAcceptor(node);
      acceptor.getParams().put("router", CONNECTION_ROUTER_NAME);
   }


   protected void setupRouterServerWithLocalTarget(final int node, final KeyType keyType, final String targetKeyFilter, final String localTargetFilter) {

      Configuration configuration = getServer(node).getConfiguration();
      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration().setName(CONNECTION_ROUTER_NAME);
      connectionRouterConfiguration.setKeyType(keyType).setLocalTargetFilter(localTargetFilter).setKeyFilter(targetKeyFilter);

      configuration.setConnectionRouters(Collections.singletonList(connectionRouterConfiguration));

      TransportConfiguration acceptor = getDefaultServerAcceptor(node);
      acceptor.getParams().put("router", CONNECTION_ROUTER_NAME);

   }

   protected void setupRouterLocalCache(final int node, boolean persisted, int timeout) {

      Configuration configuration = getServer(node).getConfiguration();
      ConnectionRouterConfiguration connectionRouterConfiguration = configuration.getConnectionRouters().stream()
         .filter(config -> config.getName().equals(CONNECTION_ROUTER_NAME)).findFirst().get();

      connectionRouterConfiguration.setCacheConfiguration(
         new CacheConfiguration().setPersisted(persisted).setTimeout(timeout));
   }

   protected ConnectionFactory createFactory(String protocol, boolean sslEnabled, String host, int port, String clientID, String user, String password) throws Exception {
      return createFactory(protocol, sslEnabled, host, port, clientID, user, password, false, -1);
   }

   protected ConnectionFactory createFactory(String protocol, boolean sslEnabled, String host, int port, String clientID, String user, String password, boolean needClientAuth, int retries) throws Exception {
      switch (protocol) {
         case CORE_PROTOCOL: {
            StringBuilder urlBuilder = new StringBuilder();

            urlBuilder.append("tcp://");
            urlBuilder.append(host);
            urlBuilder.append(":");
            urlBuilder.append(port);
            urlBuilder.append("?ha=true&reconnectAttempts=10&retryInterval=250&initialConnectAttempts=" + retries);

            urlBuilder.append("&sniHost=");
            urlBuilder.append(host);

            if (clientID != null) {
               urlBuilder.append("&clientID=");
               urlBuilder.append(clientID);
            }

            if (sslEnabled) {
               urlBuilder.append("&");
               urlBuilder.append(TransportConstants.SSL_ENABLED_PROP_NAME);
               urlBuilder.append("=");
               urlBuilder.append(true);

               urlBuilder.append("&");
               urlBuilder.append(TransportConstants.TRUSTSTORE_PATH_PROP_NAME);
               urlBuilder.append("=");
               urlBuilder.append("server-ca-truststore.jks");

               urlBuilder.append("&");
               urlBuilder.append(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME);
               urlBuilder.append("=");
               urlBuilder.append("securepass");

               if (needClientAuth) {
                  urlBuilder.append("&");
                  urlBuilder.append(TransportConstants.KEYSTORE_PATH_PROP_NAME);
                  urlBuilder.append("=");
                  urlBuilder.append("client-keystore.jks");

                  urlBuilder.append("&");
                  urlBuilder.append(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME);
                  urlBuilder.append("=");
                  urlBuilder.append("securepass");
               }
            }

            return new ActiveMQConnectionFactory(urlBuilder.toString(), user, password);
         }
         case AMQP_PROTOCOL: {
            StringBuilder urlBuilder = new StringBuilder();

            urlBuilder.append("failover:(");

            if (sslEnabled) {
               urlBuilder.append("amqps://");
               urlBuilder.append(host);
               urlBuilder.append(":");
               urlBuilder.append(port);

               urlBuilder.append("?transport.trustStoreLocation=");
               urlBuilder.append(getClass().getClassLoader().getResource("server-ca-truststore.jks").getFile());
               urlBuilder.append("&transport.trustStorePassword=securepass");

               if (needClientAuth) {
                  urlBuilder.append("&transport.keyStoreLocation=");
                  urlBuilder.append(getClass().getClassLoader().getResource("client-keystore.jks").getFile());
                  urlBuilder.append("&transport.keyStorePassword=securepass");
               }

               urlBuilder.append(")");
            } else {
               urlBuilder.append("amqp://");
               urlBuilder.append(host);
               urlBuilder.append(":");
               urlBuilder.append(port);
               urlBuilder.append(")");
            }

            urlBuilder.append("?failover.startupMaxReconnectAttempts=" + retries);

            if (clientID != null) {
               urlBuilder.append("&jms.clientID=");
               urlBuilder.append(clientID);
            }

            return new JmsConnectionFactory(user, password, urlBuilder.toString());
         }
         case OPENWIRE_PROTOCOL: {
            StringBuilder urlBuilder = new StringBuilder();

            urlBuilder.append("failover:(");

            if (sslEnabled) {
               urlBuilder.append("ssl://");
               urlBuilder.append(host);
               urlBuilder.append(":");
               urlBuilder.append(port);
               urlBuilder.append(")");
            } else {
               urlBuilder.append("tcp://");
               urlBuilder.append(host);
               urlBuilder.append(":");
               urlBuilder.append(port);
               urlBuilder.append(")");
            }

            urlBuilder.append("?randomize=false&startupMaxReconnectAttempts=" + retries + "&maxReconnectAttempts=" + retries);

            if (clientID != null) {
               urlBuilder.append("&jms.clientID=");
               urlBuilder.append(clientID);
            }

            if (sslEnabled) {
               org.apache.activemq.ActiveMQSslConnectionFactory sslConnectionFactory = new org.apache.activemq.ActiveMQSslConnectionFactory(urlBuilder.toString());
               sslConnectionFactory.setUserName(user);
               sslConnectionFactory.setPassword(password);
               sslConnectionFactory.setTrustStore("server-ca-truststore.jks");
               sslConnectionFactory.setTrustStorePassword("securepass");

               if (needClientAuth) {
                  sslConnectionFactory.setKeyStore("client-keystore.jks");
                  sslConnectionFactory.setKeyStorePassword("securepass");
               }

               return sslConnectionFactory;
            } else {
               return new org.apache.activemq.ActiveMQConnectionFactory(user, password, urlBuilder.toString());
            }
         }
         default:
            throw new IllegalStateException("Unexpected value: " + protocol);
      }
   }
}
