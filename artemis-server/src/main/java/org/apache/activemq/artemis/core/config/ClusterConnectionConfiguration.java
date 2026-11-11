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
package org.apache.activemq.artemis.core.config;

import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.uri.ClusterConnectionConfigurationParser;
import org.apache.activemq.artemis.utils.uri.URISupport;

public final class ClusterConnectionConfiguration implements Serializable {

   private static final long serialVersionUID = 8948303813427795935L;

   private String name;

   private String address = ActiveMQDefaultConfiguration.getDefaultClusterAddress();

   private String connectorName;

   private long clientFailureCheckPeriod = ActiveMQDefaultConfiguration.getDefaultClusterFailureCheckPeriod();

   private long connectionTTL = ActiveMQDefaultConfiguration.getDefaultClusterConnectionTtl();

   private long retryInterval = ActiveMQDefaultConfiguration.getDefaultClusterRetryInterval();

   private double retryIntervalMultiplier = ActiveMQDefaultConfiguration.getDefaultClusterRetryIntervalMultiplier();

   private long maxRetryInterval = ActiveMQDefaultConfiguration.getDefaultClusterMaxRetryInterval();

   private int initialConnectAttempts = ActiveMQDefaultConfiguration.getDefaultClusterInitialConnectAttempts();

   private int reconnectAttempts = ActiveMQDefaultConfiguration.getDefaultClusterReconnectAttempts();

   private long callTimeout = ActiveMQDefaultConfiguration.getDefaultClusterCallTimeout();

   private long callFailoverTimeout = ActiveMQDefaultConfiguration.getDefaultClusterCallFailoverTimeout();

   private boolean duplicateDetection = ActiveMQDefaultConfiguration.isDefaultClusterDuplicateDetection();

   private MessageLoadBalancingType messageLoadBalancingType = Enum.valueOf(MessageLoadBalancingType.class, ActiveMQDefaultConfiguration.getDefaultClusterMessageLoadBalancingType());

   private URISupport.CompositeData compositeMembers;

   private List<String> staticConnectors = Collections.emptyList();

   private String discoveryGroupName = null;

   private int maxHops = ActiveMQDefaultConfiguration.getDefaultClusterMaxHops();

   private int confirmationWindowSize = ActiveMQDefaultConfiguration.getDefaultClusterConfirmationWindowSize();

   private int producerWindowSize = ActiveMQDefaultConfiguration.getDefaultBridgeProducerWindowSize();

   private boolean allowDirectConnectionsOnly = false;

   private int minLargeMessageSize = ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE;

   private long clusterNotificationInterval = ActiveMQDefaultConfiguration.getDefaultClusterNotificationInterval();

   private int clusterNotificationAttempts = ActiveMQDefaultConfiguration.getDefaultClusterNotificationAttempts();

   private String clientId;

   private int topologyScannerAttempts = ActiveMQDefaultConfiguration.getClusterTopologyScannerAttempts();

   private long scaleDownReaperPeriod = ActiveMQDefaultConfiguration.getDefaultClusterScaleDownReaperPeriod();

   private int scaleDownReaperGraceTicks = ActiveMQDefaultConfiguration.getDefaultClusterScaleDownReaperGraceTicks();

   private int scaleDownReaperMaxAttempts = ActiveMQDefaultConfiguration.getDefaultClusterScaleDownReaperMaxAttempts();

   public ClusterConnectionConfiguration() {
   }

   public ClusterConnectionConfiguration(URI uri) throws Exception {
      ClusterConnectionConfigurationParser parser = new ClusterConnectionConfigurationParser();
      parser.populateObject(uri, this);
   }

   public String getName() {
      return name;
   }

   public ClusterConnectionConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   public String getAddress() {
      return address;
   }

   public ClusterConnectionConfiguration setAddress(String address) {
      this.address = address;
      return this;
   }

   public URISupport.CompositeData getCompositeMembers() {
      return compositeMembers;
   }

   public ClusterConnectionConfiguration setCompositeMembers(URISupport.CompositeData members) {
      this.compositeMembers = members;
      return this;
   }

   public long getClientFailureCheckPeriod() {
      return clientFailureCheckPeriod;
   }

   public ClusterConnectionConfiguration setClientFailureCheckPeriod(long clientFailureCheckPeriod) {
      this.clientFailureCheckPeriod = clientFailureCheckPeriod;
      return this;
   }

   public long getConnectionTTL() {
      return connectionTTL;
   }

   public ClusterConnectionConfiguration setConnectionTTL(long connectionTTL) {
      this.connectionTTL = connectionTTL;
      return this;
   }

   public double getRetryIntervalMultiplier() {
      return retryIntervalMultiplier;
   }

   public ClusterConnectionConfiguration setRetryIntervalMultiplier(double retryIntervalMultiplier) {
      this.retryIntervalMultiplier = retryIntervalMultiplier;
      return this;
   }

   public long getMaxRetryInterval() {
      return maxRetryInterval;
   }

   public ClusterConnectionConfiguration setMaxRetryInterval(long maxRetryInterval) {
      this.maxRetryInterval = maxRetryInterval;
      return this;
   }

   public int getInitialConnectAttempts() {
      return initialConnectAttempts;
   }

   public ClusterConnectionConfiguration setInitialConnectAttempts(int initialConnectAttempts) {
      this.initialConnectAttempts = initialConnectAttempts;
      return this;
   }

   public int getReconnectAttempts() {
      return reconnectAttempts;
   }

   public ClusterConnectionConfiguration setReconnectAttempts(int reconnectAttempts) {
      this.reconnectAttempts = reconnectAttempts;
      return this;
   }

   public long getCallTimeout() {
      return callTimeout;
   }

   public ClusterConnectionConfiguration setCallTimeout(long callTimeout) {
      this.callTimeout = callTimeout;
      return this;
   }

   public long getCallFailoverTimeout() {
      return callFailoverTimeout;
   }

   public ClusterConnectionConfiguration setCallFailoverTimeout(long callFailoverTimeout) {
      this.callFailoverTimeout = callFailoverTimeout;
      return this;
   }

   public String getConnectorName() {
      return connectorName;
   }

   public ClusterConnectionConfiguration setConnectorName(String connectorName) {
      this.connectorName = connectorName;
      return this;
   }

   public boolean isDuplicateDetection() {
      return duplicateDetection;
   }

   public ClusterConnectionConfiguration setDuplicateDetection(boolean duplicateDetection) {
      this.duplicateDetection = duplicateDetection;
      return this;
   }

   public MessageLoadBalancingType getMessageLoadBalancingType() {
      return messageLoadBalancingType;
   }

   public ClusterConnectionConfiguration setMessageLoadBalancingType(MessageLoadBalancingType messageLoadBalancingType) {
      this.messageLoadBalancingType = messageLoadBalancingType;
      return this;
   }

   public int getMaxHops() {
      return maxHops;
   }

   public ClusterConnectionConfiguration setMaxHops(int maxHops) {
      this.maxHops = maxHops;
      return this;
   }

   public int getConfirmationWindowSize() {
      return confirmationWindowSize;
   }

   public ClusterConnectionConfiguration setConfirmationWindowSize(int confirmationWindowSize) {
      this.confirmationWindowSize = confirmationWindowSize;
      return this;
   }

   public int getProducerWindowSize() {
      return producerWindowSize;
   }

   public ClusterConnectionConfiguration setProducerWindowSize(int producerWindowSize) {
      this.producerWindowSize = producerWindowSize;
      return this;
   }

   public List<String> getStaticConnectors() {
      return staticConnectors;
   }

   public ClusterConnectionConfiguration setStaticConnectors(List<String> staticConnectors) {
      this.staticConnectors = staticConnectors;
      return this;
   }

   public String getDiscoveryGroupName() {
      return discoveryGroupName;
   }

   public ClusterConnectionConfiguration setDiscoveryGroupName(String discoveryGroupName) {
      this.discoveryGroupName = discoveryGroupName;
      return this;
   }

   public long getRetryInterval() {
      return retryInterval;
   }

   public ClusterConnectionConfiguration setRetryInterval(long retryInterval) {
      this.retryInterval = retryInterval;
      return this;
   }

   public boolean isAllowDirectConnectionsOnly() {
      return allowDirectConnectionsOnly;
   }

   public ClusterConnectionConfiguration setAllowDirectConnectionsOnly(boolean allowDirectConnectionsOnly) {
      this.allowDirectConnectionsOnly = allowDirectConnectionsOnly;
      return this;
   }

   public int getMinLargeMessageSize() {
      return minLargeMessageSize;
   }

   public ClusterConnectionConfiguration setMinLargeMessageSize(final int minLargeMessageSize) {
      this.minLargeMessageSize = minLargeMessageSize;
      return this;
   }

   public long getClusterNotificationInterval() {
      return clusterNotificationInterval;
   }

   public ClusterConnectionConfiguration setClusterNotificationInterval(long clusterNotificationInterval) {
      this.clusterNotificationInterval = clusterNotificationInterval;
      return this;
   }

   public int getClusterNotificationAttempts() {
      return clusterNotificationAttempts;
   }

   public ClusterConnectionConfiguration setClusterNotificationAttempts(int clusterNotificationAttempts) {
      this.clusterNotificationAttempts = clusterNotificationAttempts;
      return this;
   }

   public String getClientId() {
      return clientId;
   }

   public ClusterConnectionConfiguration setClientId(String clientId) {
      this.clientId = clientId;
      return this;
   }

   public int getTopologyScannerAttempts() {
      return topologyScannerAttempts;
   }

   public ClusterConnectionConfiguration setTopologyScannerAttempts(int topologyScannerAttempts) {
      this.topologyScannerAttempts = topologyScannerAttempts;
      return this;
   }

   public long getScaleDownReaperPeriod() {
      return scaleDownReaperPeriod;
   }

   public ClusterConnectionConfiguration setScaleDownReaperPeriod(long scaleDownReaperPeriod) {
      this.scaleDownReaperPeriod = scaleDownReaperPeriod;
      return this;
   }

   public int getScaleDownReaperGraceTicks() {
      return scaleDownReaperGraceTicks;
   }

   public ClusterConnectionConfiguration setScaleDownReaperGraceTicks(int scaleDownReaperGraceTicks) {
      this.scaleDownReaperGraceTicks = scaleDownReaperGraceTicks;
      return this;
   }

   public int getScaleDownReaperMaxAttempts() {
      return scaleDownReaperMaxAttempts;
   }

   public ClusterConnectionConfiguration setScaleDownReaperMaxAttempts(int scaleDownReaperMaxAttempts) {
      this.scaleDownReaperMaxAttempts = scaleDownReaperMaxAttempts;
      return this;
   }

   /**
    * This method will match the configuration and return the proper TransportConfiguration for the Configuration
    */
   public TransportConfiguration[] getTransportConfigurations(Configuration configuration) throws Exception {

      if (getCompositeMembers() != null) {
         URI[] members = getCompositeMembers().getComponents();

         List<TransportConfiguration> list = new LinkedList<>();

         for (URI member : members) {
            list.addAll(ConfigurationUtils.parseConnectorURI(null, member));
         }

         return list.toArray(new TransportConfiguration[list.size()]);
      } else {
         return staticConnectors != null ? configuration.getTransportConfigurations(staticConnectors) : null;
      }
   }

   /**
    * This method will return the proper discovery configuration from the main configuration
    */
   public DiscoveryGroupConfiguration getDiscoveryGroupConfiguration(Configuration configuration) {
      if (discoveryGroupName != null) {
         DiscoveryGroupConfiguration dg = configuration.getDiscoveryGroupConfigurations().get(discoveryGroupName);

         if (dg == null) {
            ActiveMQServerLogger.LOGGER.clusterConnectionNoDiscoveryGroup(discoveryGroupName);
            return null;
         }
         return dg;
      } else {
         return null;
      }
   }

   public TransportConfiguration getTransportConfiguration(Configuration configuration) {
      TransportConfiguration connector = configuration.getConnectorConfigurations().get(getConnectorName());

      if (connector == null) {
         ActiveMQServerLogger.LOGGER.clusterConnectionNoConnector(connectorName);
         return null;
      }
      return connector;
   }

   public boolean validateConfiguration() {
      if (getName() == null) {
         ActiveMQServerLogger.LOGGER.clusterConnectionNotUnique();
         return false;
      }
      return true;
   }

   @Override
   public int hashCode() {
      return Objects.hash(address, allowDirectConnectionsOnly, callFailoverTimeout, callTimeout,
                          clientFailureCheckPeriod, clusterNotificationAttempts, clusterNotificationInterval,
                          confirmationWindowSize, connectionTTL, connectorName, discoveryGroupName, duplicateDetection,
                          messageLoadBalancingType, maxHops, maxRetryInterval, minLargeMessageSize, name,
                          initialConnectAttempts, reconnectAttempts, retryInterval, retryIntervalMultiplier,
                          staticConnectors, clientId, topologyScannerAttempts, scaleDownReaperPeriod,
                          scaleDownReaperGraceTicks, scaleDownReaperMaxAttempts);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof ClusterConnectionConfiguration other)) {
         return false;
      }
      return Objects.equals(address, other.address) &&
             allowDirectConnectionsOnly == other.allowDirectConnectionsOnly &&
             callFailoverTimeout == other.callFailoverTimeout &&
             callTimeout == other.callTimeout &&
             clientFailureCheckPeriod == other.clientFailureCheckPeriod &&
             clusterNotificationAttempts == other.clusterNotificationAttempts &&
             clusterNotificationInterval == other.clusterNotificationInterval &&
             confirmationWindowSize == other.confirmationWindowSize &&
             connectionTTL == other.connectionTTL &&
             Objects.equals(connectorName, other.connectorName) &&
             Objects.equals(discoveryGroupName, other.discoveryGroupName) &&
             duplicateDetection == other.duplicateDetection &&
             messageLoadBalancingType == other.messageLoadBalancingType &&
             maxHops == other.maxHops &&
             maxRetryInterval == other.maxRetryInterval &&
             minLargeMessageSize == other.minLargeMessageSize &&
             Objects.equals(name, other.name) &&
             initialConnectAttempts == other.initialConnectAttempts &&
             reconnectAttempts == other.reconnectAttempts &&
             retryInterval == other.retryInterval &&
             Double.doubleToLongBits(retryIntervalMultiplier) == Double.doubleToLongBits(other.retryIntervalMultiplier) &&
             Objects.equals(staticConnectors, other.staticConnectors) &&
             Objects.equals(clientId, other.clientId) &&
             topologyScannerAttempts == other.topologyScannerAttempts &&
             scaleDownReaperPeriod == other.scaleDownReaperPeriod &&
             scaleDownReaperGraceTicks == other.scaleDownReaperGraceTicks &&
             scaleDownReaperMaxAttempts == other.scaleDownReaperMaxAttempts;
   }

   @Override
   public String toString() {
      return "ClusterConnectionConfiguration{" +
         "name='" + name + '\'' +
         ", address='" + address + '\'' +
         ", connectorName='" + connectorName + '\'' +
         ", clientFailureCheckPeriod=" + clientFailureCheckPeriod +
         ", connectionTTL=" + connectionTTL +
         ", retryInterval=" + retryInterval +
         ", retryIntervalMultiplier=" + retryIntervalMultiplier +
         ", maxRetryInterval=" + maxRetryInterval +
         ", initialConnectAttempts=" + initialConnectAttempts +
         ", reconnectAttempts=" + reconnectAttempts +
         ", callTimeout=" + callTimeout +
         ", callFailoverTimeout=" + callFailoverTimeout +
         ", duplicateDetection=" + duplicateDetection +
         ", messageLoadBalancingType=" + messageLoadBalancingType +
         ", compositeMembers=" + compositeMembers +
         ", staticConnectors=" + staticConnectors +
         ", discoveryGroupName='" + discoveryGroupName + '\'' +
         ", maxHops=" + maxHops +
         ", confirmationWindowSize=" + confirmationWindowSize +
         ", allowDirectConnectionsOnly=" + allowDirectConnectionsOnly +
         ", minLargeMessageSize=" + minLargeMessageSize +
         ", clusterNotificationInterval=" + clusterNotificationInterval +
         ", clusterNotificationAttempts=" + clusterNotificationAttempts +
         ", clientId=" + clientId +
         ", topologyScannerInterval=" + topologyScannerAttempts +
         ", scaleDownReaperPeriod=" + scaleDownReaperPeriod +
         ", scaleDownReaperGraceTicks=" + scaleDownReaperGraceTicks +
         ", scaleDownReaperMaxAttempts=" + scaleDownReaperMaxAttempts +
         '}';
   }
}
