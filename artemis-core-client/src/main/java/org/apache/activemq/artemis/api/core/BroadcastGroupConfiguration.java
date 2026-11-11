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
package org.apache.activemq.artemis.api.core;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;

/**
 * The basic configuration used to determine how the server will broadcast members This is analogous to
 * {@link DiscoveryGroupConfiguration}
 */
public final class BroadcastGroupConfiguration implements Serializable {

   private static final long serialVersionUID = 2335634694112319124L;

   private String name = null;

   private long broadcastPeriod = ActiveMQDefaultConfiguration.getDefaultBroadcastPeriod();

   private BroadcastEndpointFactory endpointFactory = null;

   private List<String> connectorInfos = null;

   public BroadcastGroupConfiguration() {
   }

   public String getName() {
      return name;
   }

   public long getBroadcastPeriod() {
      return broadcastPeriod;
   }

   public List<String> getConnectorInfos() {
      return connectorInfos;
   }

   public BroadcastGroupConfiguration setName(final String name) {
      this.name = name;
      return this;
   }

   public BroadcastGroupConfiguration setBroadcastPeriod(final long broadcastPeriod) {
      this.broadcastPeriod = broadcastPeriod;
      return this;
   }

   public BroadcastGroupConfiguration setConnectorInfos(final List<String> connectorInfos) {
      this.connectorInfos = connectorInfos;
      return this;
   }

   public BroadcastEndpointFactory getEndpointFactory() {
      return endpointFactory;
   }

   public BroadcastGroupConfiguration setEndpointFactory(BroadcastEndpointFactory endpointFactory) {
      this.endpointFactory = endpointFactory;
      return this;
   }

   @Override
   public int hashCode() {
      return Objects.hash(broadcastPeriod, connectorInfos, endpointFactory, name);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof BroadcastGroupConfiguration other)) {
         return false;
      }
      return broadcastPeriod == other.broadcastPeriod &&
             Objects.equals(connectorInfos, other.connectorInfos) &&
             Objects.equals(endpointFactory, other.endpointFactory) &&
             Objects.equals(name, other.name);
   }
}
