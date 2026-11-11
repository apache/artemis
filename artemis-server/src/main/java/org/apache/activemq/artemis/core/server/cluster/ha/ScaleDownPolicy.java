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
package org.apache.activemq.artemis.core.server.cluster.ha;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServer;

public class ScaleDownPolicy {

   private List<String> connectors = new ArrayList<>();

   private String discoveryGroup = null;

   private String groupName = null;

   private String clusterName;

   private boolean enabled;

   private int commitInterval;

   public ScaleDownPolicy() {
   }

   public ScaleDownPolicy(List<String> connectors, String groupName, String clusterName, boolean enabled, int commitInterval) {
      this.connectors = connectors;
      this.groupName = groupName;
      this.clusterName = clusterName;
      this.enabled = enabled;
      this.commitInterval = commitInterval;
   }

   public ScaleDownPolicy(String discoveryGroup, String groupName, String clusterName, boolean enabled, int commitInterval) {
      this.discoveryGroup = discoveryGroup;
      this.groupName = groupName;
      this.clusterName = clusterName;
      this.enabled = enabled;
      this.commitInterval = commitInterval;
   }

   public List<String> getConnectors() {
      return connectors;
   }

   public void setConnectors(List<String> connectors) {
      this.connectors = connectors;
   }

   public String getDiscoveryGroup() {
      return discoveryGroup;
   }

   public void setDiscoveryGroup(String discoveryGroup) {
      this.discoveryGroup = discoveryGroup;
   }

   public String getGroupName() {
      return groupName;
   }

   public void setGroupName(String groupName) {
      this.groupName = groupName;
   }

   public String getClusterName() {
      return clusterName;
   }

   public void setClusterName(String clusterName) {
      this.clusterName = clusterName;
   }

   public boolean isEnabled() {
      return enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public int getCommitInterval() {
      return commitInterval;
   }

   public void setCommitInterval(int commitInterval) {
      this.commitInterval = commitInterval;
   }

   public static ServerLocatorInternal getScaleDownConnector(ScaleDownPolicy scaleDownPolicy,
                                                             ActiveMQServer activeMQServer) throws ActiveMQException {
      if (!scaleDownPolicy.getConnectors().isEmpty()) {
         return (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithHA(connectorNameListToArray(scaleDownPolicy.getConnectors(), activeMQServer));
      } else if (scaleDownPolicy.getDiscoveryGroup() != null) {
         DiscoveryGroupConfiguration dg = activeMQServer.getConfiguration().getDiscoveryGroupConfigurations().get(scaleDownPolicy.getDiscoveryGroup());

         if (dg == null) {
            throw ActiveMQMessageBundle.BUNDLE.noDiscoveryGroupFound(dg);
         }
         return (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithHA(dg);
      } else {
         Map<String, TransportConfiguration> connectorConfigurations = activeMQServer.getConfiguration().getConnectorConfigurations();
         for (TransportConfiguration transportConfiguration : connectorConfigurations.values()) {
            if (transportConfiguration.getFactoryClassName().equals(InVMConnectorFactory.class.getName())) {
               return (ServerLocatorInternal) ActiveMQClient.createServerLocatorWithHA(transportConfiguration);
            }
         }
      }
      throw ActiveMQMessageBundle.BUNDLE.noConfigurationFoundForScaleDown();
   }

   private static TransportConfiguration[] connectorNameListToArray(final List<String> connectorNames,
                                                                    ActiveMQServer activeMQServer) {
      return activeMQServer.getConfiguration().getTransportConfigurations(connectorNames);
   }
}
