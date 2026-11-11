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
package org.apache.activemq.artemis.core.server.federation;

import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationPolicy;
import org.apache.activemq.artemis.core.config.federation.FederationPolicySet;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationUpstreamConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.federation.address.FederatedAddress;
import org.apache.activemq.artemis.core.server.federation.queue.FederatedQueue;

public class FederationUpstream extends AbstractFederationStream {
   private FederationUpstreamConfiguration config;

   public FederationUpstream(ActiveMQServer server, Federation federation, String name, FederationUpstreamConfiguration config) {
      super(server, federation, name, config);
      this.config = config;
   }

   @Override
   public synchronized void start() {
      super.start();

      for (FederatedQueue federatedQueue : federatedQueueMap.values()) {
         federatedQueue.start();
      }
      for (FederatedAddress federatedAddress : federatedAddressMap.values()) {
         federatedAddress.start();
      }

      callFederationStreamStartedPlugins();
   }

   @Override
   public synchronized void stop() {
      for (FederatedAddress federatedAddress : federatedAddressMap.values()) {
         federatedAddress.stop();
      }
      federatedAddressMap.clear();

      for (FederatedQueue federatedQueue : federatedQueueMap.values()) {
         federatedQueue.stop();
      }
      federatedQueueMap.clear();

      super.stop();

      callFederationStreamStoppedPlugins();
   }

   public void deploy(Set<String> policyRefsToDeploy, Map<String, FederationPolicy> policyMap) throws ActiveMQException {
      deployPolicyRefs(policyRefsToDeploy, policyMap, 0);
   }

   private void deployPolicyRefs(Set<String> policyRefsToDeploy, Map<String, FederationPolicy> policyMap, int recursionDepth) throws ActiveMQException {
      for (String policyRef : policyRefsToDeploy) {
         FederationPolicy policy = policyMap.get(policyRef);
         if (policy != null) {
            if (policy instanceof FederationPolicySet federationPolicySet) {
               if (recursionDepth < 10) {
                  deployPolicyRefs(federationPolicySet.getPolicyRefs(), policyMap, ++recursionDepth);
               } else {
                  ActiveMQServerLogger.LOGGER.federationAvoidStackOverflowPolicyRef(name.toString(), policyRef);
               }
            } else if (policy instanceof FederationQueuePolicyConfiguration configuration) {
               deploy(configuration);
            } else if (policy instanceof FederationAddressPolicyConfiguration configuration) {
               deploy(configuration);
            } else {
               ActiveMQServerLogger.LOGGER.federationUnknownPolicyType(name.toString(), policyRef);
            }
         } else {
            ActiveMQServerLogger.LOGGER.federationCantFindPolicyRef(name.toString(), policyRef);
         }
      }
   }

   public synchronized boolean deploy(FederationQueuePolicyConfiguration federatedQueueConfig) throws ActiveMQException {
      String name = federatedQueueConfig.getName();
      FederatedQueue existing = federatedQueueMap.get(name);
      if (existing == null || !existing.getConfig().equals(federatedQueueConfig)) {
         undeployQueue(name);

         FederatedQueue federatedQueue = new FederatedQueue(federation, federatedQueueConfig, server, this);
         federatedQueueMap.put(name, federatedQueue);
         federation.register(federatedQueue);
         if (connection.isStarted()) {
            federatedQueue.start();
         }
         return true;
      }
      return false;

   }

   public synchronized boolean deploy(FederationAddressPolicyConfiguration federatedAddressConfig) throws ActiveMQException {
      String name = federatedAddressConfig.getName();
      FederatedAddress existing = federatedAddressMap.get(name);
      if (existing == null || !existing.getConfig().equals(federatedAddressConfig)) {
         undeployAddress(name);

         FederatedAddress federatedAddress = new FederatedAddress(federation, federatedAddressConfig, server, this);
         federatedAddressMap.put(name, federatedAddress);
         federation.register(federatedAddress);
         if (connection.isStarted()) {
            federatedAddress.start();
         }
         return true;
      }
      return false;
   }

   private void undeployAddress(String name) {
      FederatedAddress federatedAddress = federatedAddressMap.remove(name);
      if (federatedAddress != null) {
         federatedAddress.stop();
         federation.unregister(federatedAddress);
      }
   }

   private void undeployQueue(String name) {
      FederatedQueue federatedQueue = federatedQueueMap.remove(name);
      if (federatedQueue != null) {
         federatedQueue.stop();
         federation.unregister(federatedQueue);
      }
   }

   @Override
   public FederationUpstreamConfiguration getConfig() {
      return config;
   }
}
