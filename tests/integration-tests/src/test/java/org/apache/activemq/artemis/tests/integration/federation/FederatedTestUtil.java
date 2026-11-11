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
package org.apache.activemq.artemis.tests.integration.federation;

import java.util.Collections;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationDownstreamConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationTransformerConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationUpstreamConfiguration;

public class FederatedTestUtil {

   public static FederationConfiguration createAddressFederationConfiguration(String address, int hops) {
      FederationAddressPolicyConfiguration addressPolicyConfiguration = new FederationAddressPolicyConfiguration();
      addressPolicyConfiguration.setName("AddressPolicy" + address);
      addressPolicyConfiguration.addInclude(new FederationAddressPolicyConfiguration.Matcher().setAddressMatch(address));
      addressPolicyConfiguration.setMaxHops(hops);

      FederationConfiguration federationConfiguration = new FederationConfiguration();
      federationConfiguration.setName("default");
      federationConfiguration.addFederationPolicy(addressPolicyConfiguration);

      return federationConfiguration;
   }

   public static FederationConfiguration createAddressUpstreamFederationConfiguration(String connector, String address, int hops) {
      FederationUpstreamConfiguration upstreamConfiguration = new FederationUpstreamConfiguration();
      upstreamConfiguration.setName(connector);
      upstreamConfiguration.getConnectionConfiguration().setStaticConnectors(Collections.singletonList(connector));
      upstreamConfiguration.getConnectionConfiguration().setCircuitBreakerTimeout(-1);
      upstreamConfiguration.addPolicyRef("AddressPolicy" + address);

      FederationConfiguration federationConfiguration = createAddressFederationConfiguration(address, hops);
      federationConfiguration.addUpstreamConfiguration(upstreamConfiguration);

      return federationConfiguration;
   }

   public static FederationConfiguration createAddressUpstreamFederationConfiguration(String connector, String address) {
      return createAddressUpstreamFederationConfiguration(connector, address, 1);
   }

   public static FederationConfiguration createAddressDownstreamFederationConfiguration(String connector, String address, TransportConfiguration transportConfiguration) {
      return createAddressDownstreamFederationConfiguration(connector, address, transportConfiguration, 1);
   }

   public static FederationConfiguration createAddressDownstreamFederationConfiguration(String connector, String address, TransportConfiguration transportConfiguration,
                                                                           int hops) {
      FederationDownstreamConfiguration downstreamConfiguration = new FederationDownstreamConfiguration();
      downstreamConfiguration.setName(connector);
      downstreamConfiguration.getConnectionConfiguration().setStaticConnectors(Collections.singletonList(connector));
      downstreamConfiguration.getConnectionConfiguration().setCircuitBreakerTimeout(-1);
      downstreamConfiguration.addPolicyRef("AddressPolicy" + address);
      downstreamConfiguration.setUpstreamConfiguration(transportConfiguration);

      FederationConfiguration federationConfiguration = createAddressFederationConfiguration(address, hops);
      federationConfiguration.addDownstreamConfiguration(downstreamConfiguration);

      return federationConfiguration;
   }

   public static FederationConfiguration createAddressDownstreamFederationConfiguration(String connector, String address, String transportConfigurationRef,
                                                                           int hops) {
      FederationDownstreamConfiguration downstreamConfiguration = new FederationDownstreamConfiguration();
      downstreamConfiguration.setName(connector);
      downstreamConfiguration.getConnectionConfiguration().setStaticConnectors(Collections.singletonList(connector));
      downstreamConfiguration.getConnectionConfiguration().setCircuitBreakerTimeout(-1);
      downstreamConfiguration.addPolicyRef("AddressPolicy" + address);
      downstreamConfiguration.setUpstreamConfigurationRef(transportConfigurationRef);

      FederationConfiguration federationConfiguration = createAddressFederationConfiguration(address, hops);
      federationConfiguration.addDownstreamConfiguration(downstreamConfiguration);

      return federationConfiguration;
   }

   public static FederationConfiguration createAddressDownstreamFederationConfiguration(String connector, String address, String transportConfigurationRef) {
      return createAddressDownstreamFederationConfiguration(connector, address, transportConfigurationRef, 1);
   }

   public static void addAddressTransformerConfiguration(final FederationConfiguration federationConfiguration, final String address) {
      federationConfiguration.addTransformerConfiguration(
         new FederationTransformerConfiguration("transformer", new TransformerConfiguration(FederatedAddressTest.TestTransformer.class.getName())));
      FederationAddressPolicyConfiguration policy = (FederationAddressPolicyConfiguration) federationConfiguration.getFederationPolicyMap().get("AddressPolicy" + address);
      policy.setTransformerRef("transformer");
   }

   public static FederationConfiguration createDownstreamFederationConfiguration(String connector, String queueName, Boolean includeFederated,
                                                                           String transportConfigurationRef) {
      return createQueueDownstreamFederationConfiguration(null, connector, queueName, includeFederated, false, transportConfigurationRef);
   }

   public static FederationConfiguration createQueueDownstreamFederationConfiguration(String name, String connector, String queueName, Boolean includeFederated,
                                                                           boolean shareConnection, String transportConfigurationRef) {
      FederationDownstreamConfiguration downstreamConfiguration = new FederationDownstreamConfiguration();
      downstreamConfiguration.setName(name != null ? name : connector);
      downstreamConfiguration.getConnectionConfiguration().setStaticConnectors(Collections.singletonList(connector));
      downstreamConfiguration.getConnectionConfiguration().setCircuitBreakerTimeout(-1);
      downstreamConfiguration.getConnectionConfiguration().setShareConnection(shareConnection);
      downstreamConfiguration.addPolicyRef("QueuePolicy" + queueName);
      downstreamConfiguration.setUpstreamConfigurationRef(transportConfigurationRef);

      FederationConfiguration federationConfiguration = createQueueFederationConfiguration(connector, queueName, includeFederated);
      federationConfiguration.addDownstreamConfiguration(downstreamConfiguration);

      return federationConfiguration;
   }

   public static FederationConfiguration createQueueDownstreamFederationConfiguration(String connector, String queueName, String transportConfigurationRef) {
      return createQueueDownstreamFederationConfiguration(null, connector, queueName, null, false, transportConfigurationRef);
   }

   public static FederationConfiguration createQueueUpstreamFederationConfiguration(String connector, String queueName, Boolean includeFederated) {
      FederationUpstreamConfiguration upstreamConfiguration = createQueueFederationUpstream(connector, queueName);

      FederationConfiguration federationConfiguration = createQueueFederationConfiguration(connector, queueName, includeFederated);
      federationConfiguration.addUpstreamConfiguration(upstreamConfiguration);

      return federationConfiguration;
   }

   public static FederationUpstreamConfiguration createQueueFederationUpstream(String connector, String queueName) {

      FederationUpstreamConfiguration upstreamConfiguration = new FederationUpstreamConfiguration();
      upstreamConfiguration.setName("server1-upstream");
      upstreamConfiguration.getConnectionConfiguration().setStaticConnectors(Collections.singletonList(connector));
      upstreamConfiguration.getConnectionConfiguration().setCircuitBreakerTimeout(-1);
      upstreamConfiguration.addPolicyRef("QueuePolicy" + queueName);

      return upstreamConfiguration;
   }

   public static FederationConfiguration createQueueUpstreamFederationConfiguration(String connector, String queueName) {
      return createQueueUpstreamFederationConfiguration(connector, queueName, null);
   }

   public static FederationConfiguration createQueueFederationConfiguration(String connector, String queueName, Boolean includeFederated) {

      FederationQueuePolicyConfiguration queuePolicyConfiguration = new FederationQueuePolicyConfiguration();
      queuePolicyConfiguration.setName("QueuePolicy" + queueName);
      queuePolicyConfiguration.addInclude(new FederationQueuePolicyConfiguration.Matcher()
                                             .setQueueMatch(queueName).setAddressMatch("#"));
      if (includeFederated != null) {
         queuePolicyConfiguration.setIncludeFederated(includeFederated);
      }

      FederationConfiguration federationConfiguration = new FederationConfiguration();
      federationConfiguration.setName("default");
      federationConfiguration.addFederationPolicy(queuePolicyConfiguration);

      return federationConfiguration;
   }

   public static void addQueueTransformerConfiguration(final FederationConfiguration federationConfiguration, final String queueName) {
      federationConfiguration.addTransformerConfiguration(
         new FederationTransformerConfiguration("transformer", new TransformerConfiguration(FederatedQueueTest.TestTransformer.class.getName())));
      FederationQueuePolicyConfiguration policy = (FederationQueuePolicyConfiguration) federationConfiguration.getFederationPolicyMap().get("QueuePolicy" + queueName);
      policy.setTransformerRef("transformer");
   }

}
