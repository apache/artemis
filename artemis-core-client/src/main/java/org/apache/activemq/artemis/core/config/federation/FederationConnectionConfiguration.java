/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.config.federation;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

@Deprecated(forRemoval = true)
public class FederationConnectionConfiguration implements Serializable {

   @Deprecated(forRemoval = true)
   public static long DEFAULT_CIRCUIT_BREAKER_TIMEOUT = 30000;

   private boolean isHA;
   private String discoveryGroupName;
   private List<String> staticConnectors;
   private int priorityAdjustment;

   private long circuitBreakerTimeout = DEFAULT_CIRCUIT_BREAKER_TIMEOUT;
   private String username;
   private String password;
   private boolean shareConnection;

   private long clientFailureCheckPeriod = ActiveMQDefaultConfiguration.getDefaultFederationFailureCheckPeriod();
   private long connectionTTL = ActiveMQDefaultConfiguration.getDefaultFederationConnectionTtl();
   private long retryInterval = ActiveMQDefaultConfiguration.getDefaultFederationRetryInterval();
   private double retryIntervalMultiplier = ActiveMQDefaultConfiguration.getDefaultFederationRetryIntervalMultiplier();
   private long maxRetryInterval = ActiveMQDefaultConfiguration.getDefaultFederationMaxRetryInterval();
   private int initialConnectAttempts = ActiveMQDefaultConfiguration.getDefaultFederationInitialConnectAttempts();
   private int reconnectAttempts = ActiveMQDefaultConfiguration.getDefaultFederationReconnectAttempts();
   private long callTimeout = ActiveMQDefaultConfiguration.getDefaultFederationCallTimeout();
   private long callFailoverTimeout = ActiveMQDefaultConfiguration.getDefaultFederationCallFailoverTimeout();

   @Deprecated(forRemoval = true)
   public String getDiscoveryGroupName() {
      return discoveryGroupName;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setDiscoveryGroupName(String discoveryGroupName) {
      this.discoveryGroupName = discoveryGroupName;
      return this;
   }

   @Deprecated(forRemoval = true)
   public List<String> getStaticConnectors() {
      return staticConnectors;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setStaticConnectors(List<String> staticConnectors) {
      this.staticConnectors = staticConnectors;
      return this;
   }

   @Deprecated(forRemoval = true)
   public boolean isHA() {
      return isHA;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setHA(boolean HA) {
      isHA = HA;
      return this;
   }

   @Deprecated(forRemoval = true)
   public long getCircuitBreakerTimeout() {
      return circuitBreakerTimeout;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setCircuitBreakerTimeout(long circuitBreakerTimeout) {
      this.circuitBreakerTimeout = circuitBreakerTimeout;
      return this;
   }

   @Deprecated(forRemoval = true)
   public String getUsername() {
      return username;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setUsername(String username) {
      this.username = username;
      return this;
   }

   @Deprecated(forRemoval = true)
   public String getPassword() {
      return password;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setPassword(String password) {
      this.password = password;
      return this;
   }

   @Deprecated(forRemoval = true)
   public int getPriorityAdjustment() {
      return priorityAdjustment;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setPriorityAdjustment(int priorityAdjustment) {
      this.priorityAdjustment = priorityAdjustment;
      return this;
   }

   @Deprecated(forRemoval = true)
   public boolean isShareConnection() {
      return shareConnection;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setShareConnection(boolean shareConnection) {
      this.shareConnection = shareConnection;
      return this;
   }

   @Deprecated(forRemoval = true)
   public long getClientFailureCheckPeriod() {
      return clientFailureCheckPeriod;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setClientFailureCheckPeriod(long clientFailureCheckPeriod) {
      this.clientFailureCheckPeriod = clientFailureCheckPeriod;
      return this;
   }

   @Deprecated(forRemoval = true)
   public long getConnectionTTL() {
      return connectionTTL;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setConnectionTTL(long connectionTTL) {
      this.connectionTTL = connectionTTL;
      return this;
   }

   @Deprecated(forRemoval = true)
   public long getRetryInterval() {
      return retryInterval;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setRetryInterval(long retryInterval) {
      this.retryInterval = retryInterval;
      return this;
   }

   @Deprecated(forRemoval = true)
   public double getRetryIntervalMultiplier() {
      return retryIntervalMultiplier;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setRetryIntervalMultiplier(double retryIntervalMultiplier) {
      this.retryIntervalMultiplier = retryIntervalMultiplier;
      return this;
   }

   @Deprecated(forRemoval = true)
   public long getMaxRetryInterval() {
      return maxRetryInterval;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setMaxRetryInterval(long maxRetryInterval) {
      this.maxRetryInterval = maxRetryInterval;
      return this;
   }

   @Deprecated(forRemoval = true)
   public int getInitialConnectAttempts() {
      return initialConnectAttempts;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setInitialConnectAttempts(int initialConnectAttempts) {
      this.initialConnectAttempts = initialConnectAttempts;
      return this;
   }

   @Deprecated(forRemoval = true)
   public int getReconnectAttempts() {
      return reconnectAttempts;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setReconnectAttempts(int reconnectAttempts) {
      this.reconnectAttempts = reconnectAttempts;
      return this;
   }

   @Deprecated(forRemoval = true)
   public long getCallTimeout() {
      return callTimeout;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setCallTimeout(long callTimeout) {
      this.callTimeout = callTimeout;
      return this;
   }

   @Deprecated(forRemoval = true)
   public long getCallFailoverTimeout() {
      return callFailoverTimeout;
   }

   @Deprecated(forRemoval = true)
   public FederationConnectionConfiguration setCallFailoverTimeout(long callFailoverTimeout) {
      this.callFailoverTimeout = callFailoverTimeout;
      return this;
   }

   @Deprecated(forRemoval = true)
   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof FederationConnectionConfiguration other)) {
         return false;
      }

      return clientFailureCheckPeriod == other.clientFailureCheckPeriod &&
             connectionTTL == other.connectionTTL &&
             retryInterval == other.retryInterval &&
             Double.compare(other.retryIntervalMultiplier, retryIntervalMultiplier) == 0 &&
             maxRetryInterval == other.maxRetryInterval &&
             initialConnectAttempts == other.initialConnectAttempts &&
             reconnectAttempts == other.reconnectAttempts &&
             callTimeout == other.callTimeout &&
             callFailoverTimeout == other.callFailoverTimeout &&
             isHA == other.isHA &&
             priorityAdjustment == other.priorityAdjustment &&
             circuitBreakerTimeout == other.circuitBreakerTimeout &&
             shareConnection == other.shareConnection &&
             Objects.equals(discoveryGroupName, other.discoveryGroupName) &&
             Objects.equals(staticConnectors, other.staticConnectors) &&
             Objects.equals(username, other.username) &&
             Objects.equals(password, other.password);
   }

   @Deprecated(forRemoval = true)
   @Override
   public int hashCode() {
      return Objects.hash(clientFailureCheckPeriod, connectionTTL, retryInterval, retryIntervalMultiplier,
              maxRetryInterval, initialConnectAttempts, reconnectAttempts, callTimeout,
              callFailoverTimeout, isHA, discoveryGroupName, staticConnectors, priorityAdjustment,
              circuitBreakerTimeout, username, password, shareConnection);
   }

   @Deprecated(forRemoval = true)
   public void encode(ActiveMQBuffer buffer) {

      buffer.writeNullableString(username);
      buffer.writeNullableString(password);
      buffer.writeBoolean(shareConnection);
      buffer.writeInt(priorityAdjustment);

      buffer.writeLong(clientFailureCheckPeriod);
      buffer.writeLong(connectionTTL);
      buffer.writeLong(retryInterval);
      buffer.writeDouble(retryIntervalMultiplier);
      buffer.writeLong(maxRetryInterval);
      buffer.writeInt(initialConnectAttempts);
      buffer.writeInt(reconnectAttempts);
      buffer.writeLong(callTimeout);
      buffer.writeLong(callFailoverTimeout);
   }

   @Deprecated(forRemoval = true)
   public void decode(ActiveMQBuffer buffer) {
      username = buffer.readNullableString();
      password = buffer.readNullableString();
      shareConnection = buffer.readBoolean();
      priorityAdjustment = buffer.readInt();

      clientFailureCheckPeriod = buffer.readLong();
      connectionTTL = buffer.readLong();
      retryInterval = buffer.readLong();
      retryIntervalMultiplier = buffer.readDouble();
      maxRetryInterval = buffer.readLong();
      initialConnectAttempts = buffer.readInt();
      reconnectAttempts = buffer.readInt();
      callTimeout = buffer.readLong();
      callFailoverTimeout = buffer.readLong();

   }
}
