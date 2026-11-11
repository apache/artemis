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
package org.apache.activemq.artemis.uri;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.junit.jupiter.api.Test;

public class ClusterConnectionConfigurationTest {

   @Test
   public void testClusterConnectionStatic() throws Exception {
      ClusterConnectionConfigurationParser parser = new ClusterConnectionConfigurationParser();
      ClusterConnectionConfiguration configuration = parser.newObject(new URI("static:(tcp://localhost:6556,tcp://localhost:6557)?minLargeMessageSize=132;s&messageLoadBalancingType=OFF"), null);
      assertEquals(MessageLoadBalancingType.OFF, configuration.getMessageLoadBalancingType());
      assertEquals(132, configuration.getMinLargeMessageSize());
      assertEquals("tcp://localhost:6556", configuration.getCompositeMembers().getComponents()[0].toString());
      assertEquals("tcp://localhost:6557", configuration.getCompositeMembers().getComponents()[1].toString());
   }

   @Test
   public void testClusterConnectionStaticOffWithRedistribution() throws Exception {
      ClusterConnectionConfigurationParser parser = new ClusterConnectionConfigurationParser();
      ClusterConnectionConfiguration configuration = parser.newObject(new URI("static:(tcp://localhost:6556,tcp://localhost:6557)?minLargeMessageSize=132;s&messageLoadBalancingType=OFF_WITH_REDISTRIBUTION"), null);
      assertEquals(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION, configuration.getMessageLoadBalancingType());
   }

   @Test
   public void testClusterConnectionStatic2() throws Exception {
      ClusterConnectionConfigurationParser parser = new ClusterConnectionConfigurationParser();
      ClusterConnectionConfiguration configuration = parser.newObject(new URI("static://(tcp://localhost:6556,tcp://localhost:6557)?minLargeMessageSize=132;messageLoadBalancingType=OFF"), null);
      assertEquals(132, configuration.getMinLargeMessageSize());
      assertEquals(2, configuration.getCompositeMembers().getComponents().length);
      assertEquals("tcp://localhost:6556", configuration.getCompositeMembers().getComponents()[0].toString());
      assertEquals("tcp://localhost:6557", configuration.getCompositeMembers().getComponents()[1].toString());
   }

   @Test
   public void testClusterConnectionStaticOnConstrcutor() throws Exception {
      ClusterConnectionConfiguration configuration = new ClusterConnectionConfiguration(new URI("static:(tcp://localhost:6556,tcp://localhost:6557)?minLargeMessageSize=132"));
      assertEquals(132, configuration.getMinLargeMessageSize());
      assertEquals("tcp://localhost:6556", configuration.getCompositeMembers().getComponents()[0].toString());
      assertEquals("tcp://localhost:6557", configuration.getCompositeMembers().getComponents()[1].toString());
   }

   @Test
   public void testClusterConnectionMulticast() throws Exception {
      ClusterConnectionConfigurationParser parser = new ClusterConnectionConfigurationParser();
      ClusterConnectionConfiguration configuration = parser.newObject(new URI("multicast://myGroup?minLargeMessageSize=132"), null);
      assertEquals("myGroup", configuration.getDiscoveryGroupName());
      assertEquals(132, configuration.getMinLargeMessageSize());
   }

   @Test
   public void testClusterConnectionProducerWindowSize() throws Exception {
      ClusterConnectionConfigurationParser parser = new ClusterConnectionConfigurationParser();
      ClusterConnectionConfiguration configuration = parser.newObject(new URI("static:(tcp://localhost:6556)?producerWindowSize=1234"), null);
      assertEquals(1234, configuration.getProducerWindowSize());
   }
}
