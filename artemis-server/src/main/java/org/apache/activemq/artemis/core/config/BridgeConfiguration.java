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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.json.JsonValue;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.utils.BufferHelper;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.JsonLoader;

public final class BridgeConfiguration implements Serializable {

   private static final long serialVersionUID = -1057244274380572226L;

   public static String NAME = "name";
   public static String QUEUE_NAME = "queue-name";
   public static String FORWARDING_ADDRESS = "forwarding-address";
   public static String FILTER_STRING = "filter-string";
   public static String STATIC_CONNECTORS = "static-connectors";
   public static String DISCOVERY_GROUP_NAME = "discovery-group-name";
   public static String HA = "ha";
   public static String TRANSFORMER_CONFIGURATION = "transformer-configuration";
   public static String RETRY_INTERVAL = "retry-interval";
   public static String RETRY_INTERVAL_MULTIPLIER = "retry-interval-multiplier";
   public static String INITIAL_CONNECT_ATTEMPTS = "initial-connect-attempts";
   public static String RECONNECT_ATTEMPTS = "reconnect-attempts";
   public static String RECONNECT_ATTEMPTS_ON_SAME_NODE = "reconnect-attempts-on-same-node";
   public static String USE_DUPLICATE_DETECTION = "use-duplicate-detection";
   public static String CONFIRMATION_WINDOW_SIZE = "confirmation-window-size";
   public static String PRODUCER_WINDOW_SIZE = "producer-window-size";
   public static String CLIENT_FAILURE_CHECK_PERIOD = "client-failure-check-period";
   public static String USER = "user";
   public static String PASSWORD = "password";
   public static String CONNECTION_TTL = "connection-ttl";
   public static String MAX_RETRY_INTERVAL = "max-retry-interval";
   public static String MIN_LARGE_MESSAGE_SIZE = "min-large-message-size";
   public static String CALL_TIMEOUT = "call-timeout";
   public static String ROUTING_TYPE = "routing-type";
   public static String CONCURRENCY = "concurrency";
   public static String CONFIGURATION_MANAGED = "configuration-managed";
   public static String PENDING_ACK_TIMEOUT = "pending-ack-timeout";
   public static String CLIENT_ID = "client-id";

   private String name = null;

   private String queueName = null;

   private String forwardingAddress = null;

   private String filterString = null;

   private List<String> staticConnectors = null;

   private String discoveryGroupName = null;

   private boolean ha = false;

   private TransformerConfiguration transformerConfiguration = null;

   private long retryInterval = ActiveMQClient.DEFAULT_RETRY_INTERVAL;

   private double retryIntervalMultiplier = ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER;

   private int initialConnectAttempts = ActiveMQDefaultConfiguration.getDefaultBridgeInitialConnectAttempts();

   private int reconnectAttempts = ActiveMQDefaultConfiguration.getDefaultBridgeReconnectAttempts();

   private int reconnectAttemptsOnSameNode = ActiveMQDefaultConfiguration.getDefaultBridgeConnectSameNode();

   private boolean useDuplicateDetection = ActiveMQDefaultConfiguration.isDefaultBridgeDuplicateDetection();

   private int confirmationWindowSize = ActiveMQDefaultConfiguration.getDefaultBridgeConfirmationWindowSize();

   // disable flow control
   private int producerWindowSize = ActiveMQDefaultConfiguration.getDefaultBridgeProducerWindowSize();

   private long clientFailureCheckPeriod = ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD;

   private String user = ActiveMQDefaultConfiguration.getDefaultClusterUser();

   private String password = ActiveMQDefaultConfiguration.getDefaultClusterPassword();

   private long connectionTTL = ActiveMQClient.DEFAULT_CONNECTION_TTL;

   private long maxRetryInterval = ActiveMQClient.DEFAULT_MAX_RETRY_INTERVAL;

   private int minLargeMessageSize = ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE;

   // At this point this is only changed on testcases
   // The bridge shouldn't be sending blocking anyways
   private long callTimeout = ActiveMQClient.DEFAULT_CALL_TIMEOUT;

   private ComponentConfigurationRoutingType routingType = ComponentConfigurationRoutingType.valueOf(ActiveMQDefaultConfiguration.getDefaultBridgeRoutingType());

   private int concurrency = ActiveMQDefaultConfiguration.getDefaultBridgeConcurrency();

   private long pendingAckTimeout = ActiveMQDefaultConfiguration.getDefaultBridgePendingAckTimeout();

   private String parentName = null;

   private boolean configurationManaged = true;

   private String clientId = null;

   public BridgeConfiguration() {
   }

   public BridgeConfiguration(BridgeConfiguration other) {
      name = other.name;
      parentName = other.parentName;
      queueName = other.queueName;
      forwardingAddress = other.forwardingAddress;
      filterString = other.filterString;
      staticConnectors = other.staticConnectors;
      discoveryGroupName = other.discoveryGroupName;
      ha = other.ha;
      transformerConfiguration = other.transformerConfiguration;
      retryInterval = other.retryInterval;
      retryIntervalMultiplier = other.retryIntervalMultiplier;
      initialConnectAttempts = other.initialConnectAttempts;
      reconnectAttempts = other.reconnectAttempts;
      reconnectAttemptsOnSameNode = other.reconnectAttemptsOnSameNode;
      useDuplicateDetection = other.useDuplicateDetection;
      confirmationWindowSize = other.confirmationWindowSize;
      producerWindowSize = other.producerWindowSize;
      clientFailureCheckPeriod = other.clientFailureCheckPeriod;
      user = other.user;
      password = other.password;
      connectionTTL = other.connectionTTL;
      maxRetryInterval = other.maxRetryInterval;
      minLargeMessageSize = other.minLargeMessageSize;
      callTimeout = other.callTimeout;
      routingType = other.routingType;
      concurrency = other.concurrency;
      configurationManaged = other.configurationManaged;
      pendingAckTimeout = other.pendingAckTimeout;
      clientId = other.clientId;
   }

   public BridgeConfiguration(String name) {
      setName(name);
   }

   /**
    * Set the value of a parameter based on its "key" {@code String}. Valid key names and corresponding {@code static}
    * {@code final} are:
    * <ul>
    * <li>name: {@link #NAME}
    * <li>queue-name: {@link #QUEUE_NAME}
    * <li>forwarding-address: {@link #FORWARDING_ADDRESS}
    * <li>filter-string: {@link #FILTER_STRING}
    * <li>static-connectors: {@link #STATIC_CONNECTORS}
    * <li>discovery-group-name: {@link #DISCOVERY_GROUP_NAME}
    * <li>ha: {@link #HA}
    * <li>transformer-configuration: {@link #TRANSFORMER_CONFIGURATION}
    * <li>retry-interval: {@link #RETRY_INTERVAL}
    * <li>RETRY-interval-multiplier: {@link #RETRY_INTERVAL_MULTIPLIER}
    * <li>initial-connect-attempts: {@link #INITIAL_CONNECT_ATTEMPTS}
    * <li>reconnect-attempts: {@link #RECONNECT_ATTEMPTS}
    * <li>reconnect-attempts-on-same-node: {@link #RECONNECT_ATTEMPTS_ON_SAME_NODE}
    * <li>use-duplicate-detection: {@link #USE_DUPLICATE_DETECTION}
    * <li>confirmation-window-size: {@link #CONFIRMATION_WINDOW_SIZE}
    * <li>producer-window-size: {@link #PRODUCER_WINDOW_SIZE}
    * <li>client-failure-check-period: {@link #CLIENT_FAILURE_CHECK_PERIOD}
    * <li>user: {@link #USER}
    * <li>password: {@link #PASSWORD}
    * <li>connection-ttl: {@link #CONNECTION_TTL}
    * <li>max-retry-interval: {@link #MAX_RETRY_INTERVAL}
    * <li>min-large-message-size: {@link #MIN_LARGE_MESSAGE_SIZE}
    * <li>call-timeout: {@link #CALL_TIMEOUT}
    * <li>routing-type: {@link #ROUTING_TYPE}
    * <li>concurrency: {@link #CONCURRENCY}
    * <li>client-id: {@link #CLIENT_ID}
    * </ul>
    * The {@code String}-based values will be converted to the proper value types based on the underlying property. For
    * example, if you pass the value "TRUE" for the key "auto-created" the {@code String} "TRUE" will be converted to
    * the {@code Boolean} {@code true}.
    *
    * @param key   the key to set to the value
    * @param value the value to set for the key
    * @return this {@code BridgeConfiguration}
    */
   public BridgeConfiguration set(String key, String value) {
      if (key != null) {
         if (key.equals(NAME)) {
            setName(value);
         } else if (key.equals(QUEUE_NAME)) {
            setQueueName(value);
         } else if (key.equals(FORWARDING_ADDRESS)) {
            setForwardingAddress(value);
         } else if (key.equals(FILTER_STRING)) {
            setFilterString(value);
         } else if (key.equals(STATIC_CONNECTORS)) {
            // convert JSON array to string list
            List<String> stringList = new ArrayList<>();
            JsonArray staticConnectors = JsonLoader.readArray(new StringReader(value));
            for (int i = 0; i < staticConnectors.size(); i++) {
               stringList.add(staticConnectors.getString(i));
            }
            setStaticConnectors(stringList);
         } else if (key.equals(DISCOVERY_GROUP_NAME)) {
            setDiscoveryGroupName(value);
         } else if (key.equals(HA)) {
            setHA(Boolean.parseBoolean(value));
         } else if (key.equals(TRANSFORMER_CONFIGURATION)) {
            // create a transformer instance from a JSON string
            TransformerConfiguration transformerConfiguration = TransformerConfiguration.fromJSON(value);
            if (transformerConfiguration != null) {
               setTransformerConfiguration(transformerConfiguration);
            }
         } else if (key.equals(RETRY_INTERVAL)) {
            setRetryInterval(Long.parseLong(value));
         } else if (key.equals(RETRY_INTERVAL_MULTIPLIER)) {
            setRetryIntervalMultiplier(Double.parseDouble(value));
         } else if (key.equals(INITIAL_CONNECT_ATTEMPTS)) {
            setInitialConnectAttempts(Integer.parseInt(value));
         } else if (key.equals(RECONNECT_ATTEMPTS)) {
            setReconnectAttempts(Integer.parseInt(value));
         } else if (key.equals(RECONNECT_ATTEMPTS_ON_SAME_NODE)) {
            setReconnectAttemptsOnSameNode(Integer.parseInt(value));
         } else if (key.equals(USE_DUPLICATE_DETECTION)) {
            setUseDuplicateDetection(Boolean.parseBoolean(value));
         } else if (key.equals(CONFIRMATION_WINDOW_SIZE)) {
            setConfirmationWindowSize(Integer.parseInt(value));
         } else if (key.equals(PRODUCER_WINDOW_SIZE)) {
            setProducerWindowSize(Integer.parseInt(value));
         } else if (key.equals(CLIENT_FAILURE_CHECK_PERIOD)) {
            setClientFailureCheckPeriod(Long.parseLong(value));
         } else if (key.equals(USER)) {
            setUser(value);
         } else if (key.equals(PASSWORD)) {
            setPassword(value);
         } else if (key.equals(CONNECTION_TTL)) {
            setConnectionTTL(Long.parseLong(value));
         } else if (key.equals(MAX_RETRY_INTERVAL)) {
            setMaxRetryInterval(Long.parseLong(value));
         } else if (key.equals(MIN_LARGE_MESSAGE_SIZE)) {
            setMinLargeMessageSize(Integer.parseInt(value));
         } else if (key.equals(CALL_TIMEOUT)) {
            setCallTimeout(Long.parseLong(value));
         } else if (key.equals(ROUTING_TYPE)) {
            setRoutingType(ComponentConfigurationRoutingType.valueOf(value));
         } else if (key.equals(CONCURRENCY)) {
            setConcurrency(Integer.parseInt(value));
         } else if (key.equals(PENDING_ACK_TIMEOUT)) {
            setPendingAckTimeout(Long.parseLong(value));
         } else if (key.equals(CLIENT_ID)) {
            setClientId(value);
         }
      }
      return this;
   }

   public String getName() {
      return name;
   }

   /**
    * Sets the name of this {@code BridgeConfiguration}. If the {@code parentName} is {@code null} then it is also set.
    *
    * @param name the name to use
    * @return this {@code BridgeConfiguration}
    */
   public BridgeConfiguration setName(final String name) {
      this.name = name;
      if (this.parentName == null) {
         this.parentName = name;
      }
      return this;
   }

   public String getParentName() {
      return parentName;
   }

   public BridgeConfiguration setParentName(final String parentName) {
      this.parentName = parentName;
      return this;
   }

   public String getQueueName() {
      return queueName;
   }

   public BridgeConfiguration setQueueName(final String queueName) {
      this.queueName = queueName;
      return this;
   }

   public long getConnectionTTL() {
      return connectionTTL;
   }

   public BridgeConfiguration setConnectionTTL(long connectionTTL) {
      this.connectionTTL = connectionTTL;
      return this;
   }

   public long getMaxRetryInterval() {
      return maxRetryInterval;
   }

   public BridgeConfiguration setMaxRetryInterval(long maxRetryInterval) {
      this.maxRetryInterval = maxRetryInterval;
      return this;
   }

   public String getForwardingAddress() {
      return forwardingAddress;
   }

   public BridgeConfiguration setForwardingAddress(final String forwardingAddress) {
      this.forwardingAddress = forwardingAddress;
      return this;
   }

   public String getFilterString() {
      return filterString;
   }

   public BridgeConfiguration setFilterString(final String filterString) {
      this.filterString = filterString;
      return this;
   }

   public TransformerConfiguration getTransformerConfiguration() {
      return transformerConfiguration;
   }

   public BridgeConfiguration setTransformerConfiguration(final TransformerConfiguration transformerConfiguration) {
      this.transformerConfiguration = transformerConfiguration;
      return this;
   }

   public List<String> getStaticConnectors() {
      return staticConnectors;
   }

   public BridgeConfiguration setStaticConnectors(final List<String> staticConnectors) {
      this.staticConnectors = staticConnectors;
      return this;
   }

   public String getDiscoveryGroupName() {
      return discoveryGroupName;
   }

   public BridgeConfiguration setDiscoveryGroupName(final String discoveryGroupName) {
      this.discoveryGroupName = discoveryGroupName;
      return this;
   }

   public boolean isHA() {
      return ha;
   }

   public BridgeConfiguration setHA(final boolean ha) {
      this.ha = ha;
      return this;
   }

   public long getRetryInterval() {
      return retryInterval;
   }

   public BridgeConfiguration setRetryInterval(final long retryInterval) {
      this.retryInterval = retryInterval;
      return this;
   }

   public double getRetryIntervalMultiplier() {
      return retryIntervalMultiplier;
   }

   public BridgeConfiguration setRetryIntervalMultiplier(final double retryIntervalMultiplier) {
      this.retryIntervalMultiplier = retryIntervalMultiplier;
      return this;
   }

   public int getInitialConnectAttempts() {
      return initialConnectAttempts;
   }

   public BridgeConfiguration setInitialConnectAttempts(final int initialConnectAttempts) {
      this.initialConnectAttempts = initialConnectAttempts;
      return this;
   }

   public int getReconnectAttempts() {
      return reconnectAttempts;
   }

   public BridgeConfiguration setReconnectAttempts(final int reconnectAttempts) {
      this.reconnectAttempts = reconnectAttempts;
      return this;
   }

   public boolean isUseDuplicateDetection() {
      return useDuplicateDetection;
   }

   public BridgeConfiguration setUseDuplicateDetection(final boolean useDuplicateDetection) {
      this.useDuplicateDetection = useDuplicateDetection;
      return this;
   }

   public int getConfirmationWindowSize() {
      return confirmationWindowSize;
   }

   public BridgeConfiguration setConfirmationWindowSize(final int confirmationWindowSize) {
      this.confirmationWindowSize = confirmationWindowSize;
      return this;
   }

   public int getProducerWindowSize() {
      return producerWindowSize;
   }

   public BridgeConfiguration setProducerWindowSize(final int producerWindowSize) {
      this.producerWindowSize = producerWindowSize;
      return this;
   }

   public long getClientFailureCheckPeriod() {
      return clientFailureCheckPeriod;
   }

   public BridgeConfiguration setClientFailureCheckPeriod(long clientFailureCheckPeriod) {
      this.clientFailureCheckPeriod = clientFailureCheckPeriod;
      return this;
   }

   public int getMinLargeMessageSize() {
      return minLargeMessageSize;
   }

   public BridgeConfiguration setMinLargeMessageSize(int minLargeMessageSize) {
      this.minLargeMessageSize = minLargeMessageSize;
      return this;
   }

   public String getUser() {
      return user;
   }

   public BridgeConfiguration setUser(String user) {
      this.user = user;
      return this;
   }

   public String getPassword() {
      return password;
   }

   public BridgeConfiguration setPassword(String password) {
      this.password = password;
      return this;
   }

   public long getCallTimeout() {
      return callTimeout;
   }

   public int getReconnectAttemptsOnSameNode() {
      return reconnectAttemptsOnSameNode;
   }

   public BridgeConfiguration setReconnectAttemptsOnSameNode(int reconnectAttemptsOnSameNode) {
      this.reconnectAttemptsOnSameNode = reconnectAttemptsOnSameNode;
      return this;
   }

   public boolean isConfigurationManaged() {
      return configurationManaged;
   }

   public BridgeConfiguration setConfigurationManaged(boolean configurationManaged) {
      this.configurationManaged = configurationManaged;
      return this;
   }

   public ComponentConfigurationRoutingType getRoutingType() {
      return routingType;
   }

   public BridgeConfiguration setRoutingType(ComponentConfigurationRoutingType routingType) {
      this.routingType = routingType;
      return this;
   }

   public int getConcurrency() {
      return concurrency;
   }

   public BridgeConfiguration setConcurrency(int concurrency) {
      this.concurrency = concurrency;
      return this;
   }

   public long getPendingAckTimeout() {
      return pendingAckTimeout;
   }

   public BridgeConfiguration setPendingAckTimeout(long pendingAckTimeout) {
      this.pendingAckTimeout = pendingAckTimeout;
      return this;
   }

   public String getClientId() {
      return clientId;
   }

   public BridgeConfiguration setClientId(String clientId) {
      this.clientId = clientId;
      return this;
   }

   /**
    * At this point this is only changed on testcases The bridge shouldn't be sending blocking anyways
    *
    * @param callTimeout the callTimeout to set
    */
   public BridgeConfiguration setCallTimeout(long callTimeout) {
      this.callTimeout = callTimeout;
      return this;
   }

   /**
    * This method returns a JSON-formatted {@code String} representation of this {@code BridgeConfiguration}. It is a
    * simple collection of key/value pairs. The keys used are referenced in {@link #set(String, String)}.
    *
    * @return a JSON-formatted {@code String} representation of this {@code BridgeConfiguration}
    */
   public String toJSON() {
      JsonObjectBuilder builder = JsonLoader.createObjectBuilder();

      // string fields which default to null (only serialize if value is not null)

      if (getName() != null) {
         builder.add(NAME, getName());
      }
      if (getQueueName() != null) {
         builder.add(QUEUE_NAME, getQueueName());
      }
      if (getForwardingAddress() != null) {
         builder.add(FORWARDING_ADDRESS, getForwardingAddress());
      }
      if (getFilterString() != null) {
         builder.add(FILTER_STRING, getFilterString());
      }
      if (getDiscoveryGroupName() != null) {
         builder.add(DISCOVERY_GROUP_NAME, getDiscoveryGroupName());
      }

      // string fields which default to non-null values (always serialize)

      addNullable(builder, USER, getUser());
      addNullable(builder, PASSWORD, getPassword());

      // primitive data type fields (always serialize)

      builder.add(HA, isHA());
      builder.add(RETRY_INTERVAL, getRetryInterval());
      builder.add(RETRY_INTERVAL_MULTIPLIER, getRetryIntervalMultiplier());
      builder.add(INITIAL_CONNECT_ATTEMPTS, getInitialConnectAttempts());
      builder.add(RECONNECT_ATTEMPTS, getReconnectAttempts());
      builder.add(RECONNECT_ATTEMPTS_ON_SAME_NODE, getReconnectAttemptsOnSameNode());
      builder.add(USE_DUPLICATE_DETECTION, isUseDuplicateDetection());
      builder.add(CONFIRMATION_WINDOW_SIZE, getConfirmationWindowSize());
      builder.add(PRODUCER_WINDOW_SIZE, getProducerWindowSize());
      builder.add(CLIENT_FAILURE_CHECK_PERIOD, getClientFailureCheckPeriod());
      builder.add(CONNECTION_TTL, getConnectionTTL());
      builder.add(MAX_RETRY_INTERVAL, getMaxRetryInterval());
      builder.add(MIN_LARGE_MESSAGE_SIZE, getMinLargeMessageSize());
      builder.add(CALL_TIMEOUT, getCallTimeout());
      builder.add(CONCURRENCY, getConcurrency());
      builder.add(CONFIGURATION_MANAGED, isConfigurationManaged());
      builder.add(PENDING_ACK_TIMEOUT, getPendingAckTimeout());
      if (getClientId() != null) {
         builder.add(CLIENT_ID, getClientId());
      }

      // complex fields (only serialize if value is not null)

      if (getRoutingType() != null) {
         builder.add(ROUTING_TYPE, getRoutingType().name());
      }

      final List<String> staticConnectors = getStaticConnectors();
      if (staticConnectors != null) {
         JsonArrayBuilder arrayBuilder = JsonLoader.createArrayBuilder();
         staticConnectors.forEach(arrayBuilder::add);
         builder.add(STATIC_CONNECTORS, arrayBuilder);
      }

      TransformerConfiguration tc = getTransformerConfiguration();
      if (tc != null) {
         builder.add(TRANSFORMER_CONFIGURATION, tc.createJsonObjectBuilder());
      }

      return builder.build().toString();
   }

   private static void addNullable(JsonObjectBuilder builder, String key, String value) {
      if (value == null) {
         builder.addNull(key);
      } else {
         builder.add(key, value);
      }
   }

   /**
    * This method returns a {@code BridgeConfiguration} created from the JSON-formatted input {@code String}. The input
    * should be a simple object of key/value pairs. Valid keys are referenced in {@link #set(String, String)}.
    *
    * @param jsonString json string
    * @return the {@code BridgeConfiguration} created from the JSON-formatted input {@code String}
    */
   public static BridgeConfiguration fromJSON(String jsonString) {
      JsonObject json = JsonLoader.readObject(new StringReader(jsonString));

      // name is the only required value
      if (!json.containsKey(NAME)) {
         return null;
      }
      BridgeConfiguration result = new BridgeConfiguration(json.getString(NAME));

      for (Map.Entry<String, JsonValue> entry : json.entrySet()) {
         if (entry.getValue().getValueType() == JsonValue.ValueType.STRING) {
            result.set(entry.getKey(), ((JsonString) entry.getValue()).getString());
         } else if (entry.getValue().getValueType() == JsonValue.ValueType.NULL) {
            result.set(entry.getKey(), null);
         } else {
            result.set(entry.getKey(), entry.getValue().toString());
         }
      }

      return result;
   }

   @Override
   public int hashCode() {
      return Objects.hash(callTimeout, clientFailureCheckPeriod, confirmationWindowSize, producerWindowSize,
                          connectionTTL, discoveryGroupName, filterString, forwardingAddress, ha, maxRetryInterval,
                          minLargeMessageSize, name, password, queueName, initialConnectAttempts, reconnectAttempts,
                          retryInterval, retryIntervalMultiplier, staticConnectors, transformerConfiguration,
                          useDuplicateDetection, user, concurrency, pendingAckTimeout, configurationManaged, clientId);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof BridgeConfiguration other)) {
         return false;
      }
      return callTimeout == other.callTimeout &&
             clientFailureCheckPeriod == other.clientFailureCheckPeriod &&
             confirmationWindowSize == other.confirmationWindowSize &&
             producerWindowSize == other.producerWindowSize &&
             connectionTTL == other.connectionTTL &&
             ha == other.ha &&
             maxRetryInterval == other.maxRetryInterval &&
             minLargeMessageSize == other.minLargeMessageSize &&
             initialConnectAttempts == other.initialConnectAttempts &&
             reconnectAttempts == other.reconnectAttempts &&
             retryInterval == other.retryInterval &&
             Double.doubleToLongBits(retryIntervalMultiplier) == Double.doubleToLongBits(other.retryIntervalMultiplier) &&
             useDuplicateDetection == other.useDuplicateDetection &&
             concurrency == other.concurrency &&
             pendingAckTimeout == other.pendingAckTimeout &&
             configurationManaged == other.configurationManaged &&
             Objects.equals(discoveryGroupName, other.discoveryGroupName) &&
             Objects.equals(filterString, other.filterString) &&
             Objects.equals(forwardingAddress, other.forwardingAddress) &&
             Objects.equals(name, other.name) &&
             Objects.equals(parentName, other.parentName) &&
             Objects.equals(password, other.password) &&
             Objects.equals(queueName, other.queueName) &&
             Objects.equals(staticConnectors, other.staticConnectors) &&
             Objects.equals(transformerConfiguration, other.transformerConfiguration) &&
             Objects.equals(user, other.user) &&
             Objects.equals(clientId, other.clientId);
   }

   public int getEncodeSize() {
      int transformerSize;
      if (transformerConfiguration != null) {
         transformerSize = BufferHelper.sizeOfNullableString(transformerConfiguration.getClassName());
         transformerSize += DataConstants.SIZE_INT;
         for (Map.Entry<String, String> entry : transformerConfiguration.getProperties().entrySet()) {
            transformerSize += BufferHelper.sizeOfNullableString(entry.getKey());
            transformerSize += BufferHelper.sizeOfNullableString(entry.getValue());
         }
      } else {
         transformerSize = DataConstants.SIZE_NULL;
      }
      int staticConnectorSize = DataConstants.SIZE_INT;
      if (staticConnectors != null) {
         for (String connector : staticConnectors) {
            staticConnectorSize += BufferHelper.sizeOfNullableString(connector);
         }
      }
      int size = BufferHelper.sizeOfNullableString(name) +
         BufferHelper.sizeOfNullableString(parentName) +
         BufferHelper.sizeOfNullableString(queueName) +
         BufferHelper.sizeOfNullableString(forwardingAddress) +
         BufferHelper.sizeOfNullableString(filterString) +
         BufferHelper.sizeOfNullableString(discoveryGroupName) +
         BufferHelper.sizeOfNullableBoolean(ha) +
         BufferHelper.sizeOfNullableLong(retryInterval) +
         BufferHelper.sizeOfNullableDouble(retryIntervalMultiplier) +
         BufferHelper.sizeOfNullableInteger(initialConnectAttempts) +
         BufferHelper.sizeOfNullableInteger(reconnectAttempts) +
         BufferHelper.sizeOfNullableInteger(reconnectAttemptsOnSameNode) +
         BufferHelper.sizeOfNullableBoolean(useDuplicateDetection) +
         BufferHelper.sizeOfNullableInteger(confirmationWindowSize) +
         BufferHelper.sizeOfNullableInteger(producerWindowSize) +
         BufferHelper.sizeOfNullableLong(clientFailureCheckPeriod) +
         BufferHelper.sizeOfNullableString(user) +
         BufferHelper.sizeOfNullableString(password) +
         BufferHelper.sizeOfNullableLong(connectionTTL) +
         BufferHelper.sizeOfNullableLong(maxRetryInterval) +
         BufferHelper.sizeOfNullableInteger(minLargeMessageSize) +
         BufferHelper.sizeOfNullableLong(callTimeout) +
         BufferHelper.sizeOfNullableInteger(concurrency) +
         BufferHelper.sizeOfNullableBoolean(configurationManaged) +
         DataConstants.SIZE_BYTE + // routingType
         transformerSize +
         staticConnectorSize +
         BufferHelper.sizeOfNullableLong(pendingAckTimeout) +
         BufferHelper.sizeOfNullableString(clientId);
      return size;
   }

   public void encode(ActiveMQBuffer buffer) {
      buffer.writeNullableString(name);
      buffer.writeNullableString(parentName);
      buffer.writeNullableString(queueName);
      buffer.writeNullableString(forwardingAddress);
      buffer.writeNullableString(filterString);
      buffer.writeNullableString(discoveryGroupName);
      buffer.writeNullableBoolean(ha);
      buffer.writeNullableLong(retryInterval);
      BufferHelper.writeNullableDouble(buffer, retryIntervalMultiplier);
      buffer.writeNullableInt(initialConnectAttempts);
      buffer.writeNullableInt(reconnectAttempts);
      buffer.writeNullableInt(reconnectAttemptsOnSameNode);
      buffer.writeNullableBoolean(useDuplicateDetection);
      buffer.writeNullableInt(confirmationWindowSize);
      buffer.writeNullableInt(producerWindowSize);
      buffer.writeNullableLong(clientFailureCheckPeriod);
      buffer.writeNullableString(user);
      buffer.writeNullableString(password);
      buffer.writeNullableLong(connectionTTL);
      buffer.writeNullableLong(maxRetryInterval);
      buffer.writeNullableInt(minLargeMessageSize);
      buffer.writeNullableLong(callTimeout);
      buffer.writeNullableInt(concurrency);
      buffer.writeNullableBoolean(configurationManaged);
      buffer.writeByte(routingType != null ? routingType.getType() : ComponentConfigurationRoutingType.valueOf(ActiveMQDefaultConfiguration.getDefaultDivertRoutingType()).getType());
      if (transformerConfiguration != null) {
         buffer.writeNullableString(transformerConfiguration.getClassName());
         Map<String, String> properties = transformerConfiguration.getProperties();
         buffer.writeInt(properties.size());
         for (Map.Entry<String, String> entry : properties.entrySet()) {
            buffer.writeNullableString(entry.getKey());
            buffer.writeNullableString(entry.getValue());
         }
      } else {
         buffer.writeNullableString(null);
      }
      if (staticConnectors != null) {
         buffer.writeInt(staticConnectors.size());
         for (String connector : staticConnectors) {
            buffer.writeNullableString(connector);
         }
      } else {
         buffer.writeInt(0);
      }
      buffer.writeNullableLong(pendingAckTimeout);
      buffer.writeNullableString(clientId);
   }

   public void decode(ActiveMQBuffer buffer) {
      name = buffer.readNullableString();
      parentName = buffer.readNullableString();
      queueName = buffer.readNullableString();
      forwardingAddress = buffer.readNullableString();
      filterString = buffer.readNullableString();
      discoveryGroupName = buffer.readNullableString();
      ha = buffer.readNullableBoolean();
      retryInterval = buffer.readNullableLong();
      retryIntervalMultiplier = BufferHelper.readNullableDouble(buffer);
      initialConnectAttempts = buffer.readNullableInt();
      reconnectAttempts = buffer.readNullableInt();
      reconnectAttemptsOnSameNode = buffer.readNullableInt();
      useDuplicateDetection = buffer.readNullableBoolean();
      confirmationWindowSize = buffer.readNullableInt();
      producerWindowSize = buffer.readNullableInt();
      clientFailureCheckPeriod = buffer.readNullableLong();
      user = buffer.readNullableString();
      password = buffer.readNullableString();
      connectionTTL = buffer.readNullableLong();
      maxRetryInterval = buffer.readNullableLong();
      minLargeMessageSize = buffer.readNullableInt();
      callTimeout = buffer.readNullableLong();
      concurrency = buffer.readNullableInt();
      configurationManaged = buffer.readNullableBoolean();
      routingType = ComponentConfigurationRoutingType.getType(buffer.readByte());
      String transformerClassName = buffer.readNullableString();
      if (transformerClassName != null) {
         transformerConfiguration = new TransformerConfiguration(transformerClassName);
         int propsSize = buffer.readInt();
         for (int i = 0; i < propsSize; i++) {
            transformerConfiguration.getProperties().put(buffer.readNullableString(), buffer.readNullableString());
         }
      }
      int numStaticConnectors = buffer.readInt();
      if (numStaticConnectors > 0) {
         staticConnectors = new ArrayList<>();
         for (int i = 0; i < numStaticConnectors; i++) {
            staticConnectors.add(buffer.readNullableString());
         }
      }
      if (buffer.readable()) {
         pendingAckTimeout = buffer.readNullableLong();
      }
      if (buffer.readable()) {
         clientId = buffer.readNullableString();
      }
   }
}
