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
package org.apache.activemq.artemis.core.config.routing;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class PoolConfiguration implements Serializable {

   private String username;

   private String password;

   private boolean localTargetEnabled;

   private String clusterConnection;

   private List<String> staticConnectors;

   private String discoveryGroupName;

   private int checkPeriod = 5000;

   private int quorumSize = 1;

   private int quorumTimeout = 3000;

   public String getUsername() {
      return username;
   }

   public PoolConfiguration setUsername(String username) {
      this.username = username;
      return this;
   }

   public String getPassword() {
      return password;
   }

   public PoolConfiguration setPassword(String password) {
      this.password = password;
      return this;
   }

   public int getCheckPeriod() {
      return checkPeriod;
   }

   public PoolConfiguration setCheckPeriod(int checkPeriod) {
      this.checkPeriod = checkPeriod;
      return this;
   }

   public int getQuorumSize() {
      return quorumSize;
   }

   public PoolConfiguration setQuorumSize(int quorumSize) {
      this.quorumSize = quorumSize;
      return this;
   }

   public int getQuorumTimeout() {
      return quorumTimeout;
   }

   public PoolConfiguration setQuorumTimeout(int quorumTimeout) {
      this.quorumTimeout = quorumTimeout;
      return this;
   }

   public boolean isLocalTargetEnabled() {
      return localTargetEnabled;
   }

   public PoolConfiguration setLocalTargetEnabled(boolean localTargetEnabled) {
      this.localTargetEnabled = localTargetEnabled;
      return this;
   }

   public String getClusterConnection() {
      return clusterConnection;
   }

   public PoolConfiguration setClusterConnection(String clusterConnection) {
      this.clusterConnection = clusterConnection;
      return this;
   }

   public List<String> getStaticConnectors() {
      return staticConnectors;
   }

   public PoolConfiguration setStaticConnectors(List<String> staticConnectors) {
      this.staticConnectors = staticConnectors;
      return this;
   }

   public String getDiscoveryGroupName() {
      return discoveryGroupName;
   }

   public PoolConfiguration setDiscoveryGroupName(String discoveryGroupName) {
      this.discoveryGroupName = discoveryGroupName;
      return this;
   }

   @Override
   public int hashCode() {
      return Objects.hash(checkPeriod, clusterConnection, discoveryGroupName, localTargetEnabled, password, quorumSize, quorumTimeout, staticConnectors,
         username);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }

      if (obj instanceof PoolConfiguration other) {
         return checkPeriod == other.checkPeriod &&
                Objects.equals(clusterConnection, other.clusterConnection) &&
                Objects.equals(discoveryGroupName, other.discoveryGroupName) &&
                localTargetEnabled == other.localTargetEnabled &&
                Objects.equals(password, other.password) &&
                quorumSize == other.quorumSize &&
                quorumTimeout == other.quorumTimeout &&
                Objects.equals(staticConnectors, other.staticConnectors) &&
                Objects.equals(username, other.username);
      }

      return false;
   }
}
