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
package org.apache.activemq.artemis.core.deployers.impl;

import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.ArtemisConstants;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ConfigurationUtils;
import org.apache.activemq.artemis.core.config.ConnectorServiceConfiguration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationDownstreamConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationPolicySet;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationStreamConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationTransformerConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationUpstreamConfiguration;
import org.apache.activemq.artemis.core.config.ha.ColocatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.DistributedLockManagerConfiguration;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicationPrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.routing.CacheConfiguration;
import org.apache.activemq.artemis.core.config.routing.ConnectionRouterConfiguration;
import org.apache.activemq.artemis.core.config.routing.NamedPropertyConfiguration;
import org.apache.activemq.artemis.core.config.routing.PoolConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.config.storage.FileStorageConfiguration;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.SecuritySettingPlugin;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.group.impl.GroupingHandlerConfiguration;
import org.apache.activemq.artemis.core.server.metrics.ActiveMQMetricsPlugin;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.core.server.routing.policies.PolicyFactoryResolver;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.core.settings.impl.DiskFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.PageFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.ResourceLimitSettings;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit;
import org.apache.activemq.artemis.selector.impl.SelectorParser;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.XMLConfigurationUtil;
import org.apache.activemq.artemis.utils.XMLUtil;
import org.apache.activemq.artemis.utils.XmlProvider;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.apache.activemq.artemis.core.config.impl.Validators.ADDRESS_FULL_MESSAGE_POLICY_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.COMPONENT_ROUTING_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.DELETION_POLICY_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.DISK_FULL_MESSAGE_POLICY_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.GE_ZERO;
import static org.apache.activemq.artemis.core.config.impl.Validators.GT_ZERO;
import static org.apache.activemq.artemis.core.config.impl.Validators.JOURNAL_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.KEY_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.LE_ONE;
import static org.apache.activemq.artemis.core.config.impl.Validators.MAX_QUEUE_CONSUMERS;
import static org.apache.activemq.artemis.core.config.impl.Validators.MESSAGE_LOAD_BALANCING_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.MINUS_ONE_OR_GE_ZERO;
import static org.apache.activemq.artemis.core.config.impl.Validators.MINUS_ONE_OR_GT_ZERO;
import static org.apache.activemq.artemis.core.config.impl.Validators.MINUS_ONE_OR_POSITIVE_INT;
import static org.apache.activemq.artemis.core.config.impl.Validators.NOT_NULL_OR_EMPTY;
import static org.apache.activemq.artemis.core.config.impl.Validators.NO_CHECK;
import static org.apache.activemq.artemis.core.config.impl.Validators.NULL_OR_TWO_CHARACTERS;
import static org.apache.activemq.artemis.core.config.impl.Validators.PAGE_FULL_MESSAGE_POLICY_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.PERCENTAGE;
import static org.apache.activemq.artemis.core.config.impl.Validators.PERCENTAGE_OR_MINUS_ONE;
import static org.apache.activemq.artemis.core.config.impl.Validators.POSITIVE_INT;
import static org.apache.activemq.artemis.core.config.impl.Validators.POSITIVE_POWER_OF_TWO;
import static org.apache.activemq.artemis.core.config.impl.Validators.ROUTING_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.SLOW_CONSUMER_POLICY_TYPE;
import static org.apache.activemq.artemis.core.config.impl.Validators.SLOW_CONSUMER_THRESHOLD_MEASUREMENT_UNIT;
import static org.apache.activemq.artemis.core.security.Role.BROWSE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CONSUME_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CREATE_ADDRESS_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CREATE_DURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.CREATE_NONDURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.DELETE_ADDRESS_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.DELETE_DURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.DELETE_NONDURABLE_QUEUE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.EDIT_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.MANAGE_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.SEND_PERMISSION;
import static org.apache.activemq.artemis.core.security.Role.VIEW_PERMISSION;

/**
 * Parses an XML document according to the {@literal artemis-configuration.xsd} schema.
 */
public final class FileConfigurationParser extends XMLConfigurationUtil {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // Security Parsing
   public static final String SECURITY_ELEMENT_NAME = "security-setting";

   public static final String SECURITY_PLUGIN_ELEMENT_NAME = "security-setting-plugin";

   public static final String SECURITY_ROLE_MAPPING_NAME = "role-mapping";

   public static final String BROKER_PLUGINS_ELEMENT_NAME = "broker-plugins";

   public static final String BROKER_PLUGIN_ELEMENT_NAME = "broker-plugin";

   private static final String PERMISSION_ELEMENT_NAME = "permission";

   private static final String SETTING_ELEMENT_NAME = "setting";

   private static final String TYPE_ATTR_NAME = "type";

   private static final String ROLES_ATTR_NAME = "roles";

   private static final String NAME_ATTR_NAME = "name";

   private static final String VALUE_ATTR_NAME = "value";

   private static final String ROLE_FROM_ATTR_NAME = "from";

   private static final String ROLE_TO_ATTR_NAME = "to";

   // We keep supporting these attribute names for compatibility
   @Deprecated(forRemoval = true)
   private static final String CREATETEMPQUEUE_NAME = "createTempQueue";

   @Deprecated(forRemoval = true)
   private static final String DELETETEMPQUEUE_NAME = "deleteTempQueue";

   // Address parsing

   private static final String DEAD_LETTER_ADDRESS_NODE_NAME = "dead-letter-address";

   private static final String AUTO_CREATE_DEAD_LETTER_RESOURCES_NODE_NAME = "auto-create-dead-letter-resources";

   private static final String DEAD_LETTER_QUEUE_PREFIX_NODE_NAME = "dead-letter-queue-prefix";

   private static final String DEAD_LETTER_QUEUE_SUFFIX_NODE_NAME = "dead-letter-queue-suffix";

   private static final String EXPIRY_ADDRESS_NODE_NAME = "expiry-address";

   private static final String AUTO_CREATE_EXPIRY_RESOURCES_NODE_NAME = "auto-create-expiry-resources";

   private static final String EXPIRY_QUEUE_PREFIX_NODE_NAME = "expiry-queue-prefix";

   private static final String EXPIRY_QUEUE_SUFFIX_NODE_NAME = "expiry-queue-suffix";

   private static final String EXPIRY_DELAY_NODE_NAME = "expiry-delay";

   private static final String MIN_EXPIRY_DELAY_NODE_NAME = "min-expiry-delay";

   private static final String MAX_EXPIRY_DELAY_NODE_NAME = "max-expiry-delay";

   private static final String NO_EXPIRY_NODE_NAME = "no-expiry";

   private static final String REDELIVERY_DELAY_NODE_NAME = "redelivery-delay";

   private static final String REDELIVERY_DELAY_MULTIPLIER_NODE_NAME = "redelivery-delay-multiplier";

   private static final String REDELIVERY_COLLISION_AVOIDANCE_FACTOR_NODE_NAME = "redelivery-collision-avoidance-factor";

   private static final String MAX_REDELIVERY_DELAY_NODE_NAME = "max-redelivery-delay";

   private static final String MAX_DELIVERY_ATTEMPTS = "max-delivery-attempts";

   private static final String MAX_SIZE_BYTES_NODE_NAME = "max-size-bytes";

   private static final String MAX_MESSAGES_NODE_NAME = "max-size-messages";

   private static final String MAX_SIZE_BYTES_REJECT_THRESHOLD_NODE_NAME = "max-size-bytes-reject-threshold";

   private static final String ADDRESS_FULL_MESSAGE_POLICY_NODE_NAME = "address-full-policy";

   private static final String PAGE_FULL_MESSAGE_POLICY_NODE_NAME = "page-full-policy";

   private static final String DISK_FULL_MESSAGE_POLICY_NODE_NAME = "disk-full-policy";

   private static final String MAX_READ_PAGE_BYTES_NODE_NAME = "max-read-page-bytes";

   private static final String PREFETCH_PAGE_BYTES_NODE_NAME = "prefetch-page-bytes";

   private static final String MAX_READ_PAGE_MESSAGES_NODE_NAME = "max-read-page-messages";

   private static final String PREFETCH_PAGE_MESSAGES_NODE_NAME = "prefetch-page-messages";

   private static final String PAGE_SIZE_BYTES_NODE_NAME = "page-size-bytes";

   private static final String PAGE_MAX_CACHE_SIZE_NODE_NAME = "page-max-cache-size";

   private static final String PAGE_LIMIT_BYTES_NODE_NAME = "page-limit-bytes";

   private static final String PAGE_LIMIT_MESSAGES_NODE_NAME = "page-limit-messages";

   private static final String MESSAGE_COUNTER_HISTORY_DAY_LIMIT_NODE_NAME = "message-counter-history-day-limit";

   private static final String LVQ_NODE_NAME = "last-value-queue";

   private static final String DEFAULT_LVQ_NODE_NAME = "default-last-value-queue";

   private static final String DEFAULT_LVQ_KEY_NODE_NAME = "default-last-value-key";

   private static final String DEFAULT_NON_DESTRUCTIVE_NODE_NAME = "default-non-destructive";

   private static final String DEFAULT_EXCLUSIVE_NODE_NAME = "default-exclusive-queue";

   private static final String DEFAULT_GROUP_REBALANCE = "default-group-rebalance";

   private static final String DEFAULT_GROUP_REBALANCE_PAUSE_DISPATCH = "default-group-rebalance-pause-dispatch";

   private static final String DEFAULT_GROUP_BUCKETS = "default-group-buckets";

   private static final String DEFAULT_GROUP_FIRST_KEY = "default-group-first-key";

   private static final String DEFAULT_CONSUMERS_BEFORE_DISPATCH = "default-consumers-before-dispatch";

   private static final String DEFAULT_DELAY_BEFORE_DISPATCH = "default-delay-before-dispatch";

   private static final String REDISTRIBUTION_DELAY_NODE_NAME = "redistribution-delay";

   private static final String SEND_TO_DLA_ON_NO_ROUTE = "send-to-dla-on-no-route";

   private static final String SLOW_CONSUMER_THRESHOLD_NODE_NAME = "slow-consumer-threshold";

   private static final String SLOW_CONSUMER_THRESHOLD_MEASUREMENT_UNIT_NODE_NAME = "slow-consumer-threshold-measurement-unit";

   private static final String SLOW_CONSUMER_CHECK_PERIOD_NODE_NAME = "slow-consumer-check-period";

   private static final String SLOW_CONSUMER_POLICY_NODE_NAME = "slow-consumer-policy";

   private static final String AUTO_CREATE_JMS_QUEUES = "auto-create-jms-queues";

   private static final String AUTO_DELETE_JMS_QUEUES = "auto-delete-jms-queues";

   private static final String AUTO_CREATE_JMS_TOPICS = "auto-create-jms-topics";

   private static final String AUTO_DELETE_JMS_TOPICS = "auto-delete-jms-topics";

   private static final String AUTO_CREATE_QUEUES = "auto-create-queues";

   private static final String AUTO_DELETE_QUEUES = "auto-delete-queues";

   private static final String AUTO_DELETE_CREATED_QUEUES = "auto-delete-created-queues";

   private static final String AUTO_DELETE_QUEUES_DELAY = "auto-delete-queues-delay";

   private static final String AUTO_DELETE_QUEUES_MESSAGE_COUNT = "auto-delete-queues-message-count";

   private static final String AUTO_DELETE_QUEUES_SKIP_USAGE_CHECK = "auto-delete-queues-skip-usage-check";

   private static final String CONFIG_DELETE_QUEUES = "config-delete-queues";

   private static final String AUTO_CREATE_ADDRESSES = "auto-create-addresses";

   private static final String AUTO_DELETE_ADDRESSES = "auto-delete-addresses";

   private static final String AUTO_DELETE_ADDRESSES_DELAY = "auto-delete-addresses-delay";

   private static final String AUTO_DELETE_ADDRESSES_SKIP_USAGE_CHECK = "auto-delete-addresses-skip-usage-check";

   private static final String CONFIG_DELETE_ADDRESSES = "config-delete-addresses";

   private static final String CONFIG_DELETE_DIVERTS = "config-delete-diverts";

   private static final String DEFAULT_PURGE_ON_NO_CONSUMERS = "default-purge-on-no-consumers";

   private static final String DEFAULT_MAX_CONSUMERS = "default-max-consumers";

   private static final String DEFAULT_QUEUE_ROUTING_TYPE = "default-queue-routing-type";

   private static final String DEFAULT_ADDRESS_ROUTING_TYPE = "default-address-routing-type";

   private static final String MANAGEMENT_BROWSE_PAGE_SIZE = "management-browse-page-size";

   private static final String MANAGEMENT_MESSAGE_ATTRIBUTE_SIZE_LIMIT = "management-message-attribute-size-limit";

   private static final String MAX_CONNECTIONS_NODE_NAME = "max-connections";

   private static final String MAX_QUEUES_NODE_NAME = "max-queues";

   private static final String GLOBAL_MAX_SIZE = "global-max-size";

   private static final String GLOBAL_MAX_SIZE_PERCENT_JVM_MAX_MEM = "global-max-size-percent-of-jvm-max-memory";

   private static final String GLOBAL_MAX_MESSAGES = "global-max-messages";

   public static final String MAX_DISK_USAGE = "max-disk-usage";

   public static final String MIN_DISK_FREE = "min-disk-free";

   private static final String DISK_SCAN_PERIOD = "disk-scan-period";

   private static final String INTERNAL_NAMING_PREFIX = "internal-naming-prefix";

   private static final String AMQP_USE_CORE_SUBSCRIPTION_NAMING = "amqp-use-core-subscription-naming";

   private static final String DEFAULT_CONSUMER_WINDOW_SIZE = "default-consumer-window-size";

   private static final String DEFAULT_RING_SIZE = "default-ring-size";

   private static final String RETROACTIVE_MESSAGE_COUNT = "retroactive-message-count";

   private static final String ENABLE_METRICS = "enable-metrics";

   private static final String ENABLE_INGRESS_TIMESTAMP = "enable-ingress-timestamp";

   private static final String ID_CACHE_SIZE = "id-cache-size";

   private static final String MIRROR_ACK_MANAGER_QUEUE_ATTEMPTS = "mirror-ack-manager-queue-attempts";

   private static final String MIRROR_ACK_MANAGER_PAGE_ATTEMPTS = "mirror-ack-manager-page-attempts";

   private static final String MIRROR_ACK_MANAGER_RETRY_DELAY = "mirror-ack-manager-retry-delay";
   private static final String MIRROR_ACK_MANAGER_WARN_UNACKED = "mirror-ack-manager-warn-unacked";

   private static final String MIRROR_PAGE_TRANSACTION = "mirror-page-transaction";

   private static final String INITIAL_QUEUE_BUFFER_SIZE = "initial-queue-buffer-size";

   private static final String MQTT_SUBSCRIPTION_PERSISTENCE_ENABLED = "mqtt-subscription-persistence-enabled";

   private boolean validateAIO = false;

   private boolean printPageMaxSizeUsed = false;

   public boolean isValidateAIO() {
      return validateAIO;
   }

   public void setValidateAIO(final boolean validateAIO) {
      this.validateAIO = validateAIO;
   }

   public Configuration parseMainConfig(final InputStream input) throws Exception {
      Element e = XMLUtil.streamToElement(input);
      Validator validator = XmlProvider.newValidator(XMLUtil.findResource("schema/artemis-server.xsd"));
      try {
         validator.validate(new DOMSource(e));
      } catch (Exception ex) {
         logger.error(ex.getMessage(), ex);
      }
      Configuration config = new ConfigurationImpl();
      parseMainConfig(e, config);

      return config;
   }

   public void parseMainConfig(final Element e, final Configuration config) throws Exception {

      config.setName(getString(e, "name", config.getName(), NO_CHECK));

      config.setSystemPropertyPrefix(getString(e, "system-property-prefix", config.getSystemPropertyPrefix(), NOT_NULL_OR_EMPTY));

      NodeList haPolicyNodes = e.getElementsByTagName("ha-policy");

      if (haPolicyNodes.getLength() > 0) {
         parseHAPolicyConfiguration((Element) haPolicyNodes.item(0), config);
      }

      //if we aren already set then set to default
      if (config.getHAPolicyConfiguration() == null) {
         config.setHAPolicyConfiguration(new PrimaryOnlyPolicyConfiguration());
      }

      config.setResolveProtocols(getBoolean(e, "resolve-protocols", config.isResolveProtocols()));

      config.setPersistenceEnabled(getBoolean(e, "persistence-enabled", config.isPersistenceEnabled()));

      config.setPersistDeliveryCountBeforeDelivery(getBoolean(e, "persist-delivery-count-before-delivery", config.isPersistDeliveryCountBeforeDelivery()));

      config.setMaxRedeliveryRecords(getInteger(e, "max-redelivery-records", config.getMaxRedeliveryRecords(), MINUS_ONE_OR_GE_ZERO));

      config.setScheduledThreadPoolMaxSize(getInteger(e, "scheduled-thread-pool-max-size", config.getScheduledThreadPoolMaxSize(), GT_ZERO));

      config.setThreadPoolMaxSize(getInteger(e, "thread-pool-max-size", config.getThreadPoolMaxSize(), MINUS_ONE_OR_GT_ZERO));

      config.setSecurityEnabled(getBoolean(e, "security-enabled", config.isSecurityEnabled()));

      config.setGracefulShutdownEnabled(getBoolean(e, "graceful-shutdown-enabled", config.isGracefulShutdownEnabled()));

      config.setGracefulShutdownTimeout(getLong(e, "graceful-shutdown-timeout", config.getGracefulShutdownTimeout(), MINUS_ONE_OR_GE_ZERO));

      config.setJMXManagementEnabled(getBoolean(e, "jmx-management-enabled", config.isJMXManagementEnabled()));

      config.setJMXDomain(getString(e, "jmx-domain", config.getJMXDomain(), NOT_NULL_OR_EMPTY));

      config.setJMXUseBrokerName(getBoolean(e, "jmx-use-broker-name", config.isJMXUseBrokerName()));

      config.setSecurityInvalidationInterval(getLong(e, "security-invalidation-interval", config.getSecurityInvalidationInterval(), GE_ZERO));

      config.setAuthenticationCacheSize(getLong(e, "authentication-cache-size", config.getAuthenticationCacheSize(), GE_ZERO));

      config.setAuthorizationCacheSize(getLong(e, "authorization-cache-size", config.getAuthorizationCacheSize(), GE_ZERO));

      config.setConnectionTTLOverride(getLong(e, "connection-ttl-override", config.getConnectionTTLOverride(), MINUS_ONE_OR_GT_ZERO));

      config.setEnabledAsyncConnectionExecution(getBoolean(e, "async-connection-execution-enabled", config.isAsyncConnectionExecutionEnabled()));

      config.setTransactionTimeout(getLong(e, "transaction-timeout", config.getTransactionTimeout(), GT_ZERO));

      config.setTransactionTimeoutScanPeriod(getLong(e, "transaction-timeout-scan-period", config.getTransactionTimeoutScanPeriod(), GT_ZERO));

      config.setMessageExpiryScanPeriod(getLong(e, "message-expiry-scan-period", config.getMessageExpiryScanPeriod(), MINUS_ONE_OR_GT_ZERO));

      config.setAddressQueueScanPeriod(getLong(e, "address-queue-scan-period", config.getAddressQueueScanPeriod(), MINUS_ONE_OR_GT_ZERO));

      config.setIDCacheSize(getInteger(e, "id-cache-size", config.getIDCacheSize(), GE_ZERO));

      config.setPersistIDCache(getBoolean(e, "persist-id-cache", config.isPersistIDCache()));

      config.setManagementAddress(SimpleString.of(getString(e, "management-address", config.getManagementAddress().toString(), NOT_NULL_OR_EMPTY)));

      config.setManagementNotificationAddress(SimpleString.of(getString(e, "management-notification-address", config.getManagementNotificationAddress().toString(), NOT_NULL_OR_EMPTY)));

      config.setMaskPassword(getBoolean(e, "mask-password", null));

      config.setPasswordCodec(getString(e, "password-codec", DefaultSensitiveStringCodec.class.getName(), NOT_NULL_OR_EMPTY));

      config.setPopulateValidatedUser(getBoolean(e, "populate-validated-user", config.isPopulateValidatedUser()));

      config.setRejectEmptyValidatedUser(getBoolean(e, "reject-empty-validated-user", config.isRejectEmptyValidatedUser()));

      config.setConnectionTtlCheckInterval(getLong(e, "connection-ttl-check-interval", config.getConnectionTtlCheckInterval(), GT_ZERO));

      config.setConfigurationFileRefreshPeriod(getLong(e, "configuration-file-refresh-period", config.getConfigurationFileRefreshPeriod(), MINUS_ONE_OR_GT_ZERO));

      config.setTemporaryQueueNamespace(getString(e, "temporary-queue-namespace", config.getTemporaryQueueNamespace(), NOT_NULL_OR_EMPTY));

      config.setUuidNamespace(getString(e, "uuid-namespace", config.getUuidNamespace(), NOT_NULL_OR_EMPTY));

      config.setMqttSessionScanInterval(getLong(e, "mqtt-session-scan-interval", config.getMqttSessionScanInterval(), GT_ZERO));

      config.setMqttSessionStatePersistenceTimeout(getLong(e, "mqtt-session-state-persistence-timeout", config.getMqttSessionStatePersistenceTimeout(), GT_ZERO, MQTT_SUBSCRIPTION_PERSISTENCE_ENABLED));

      config.setMqttSubscriptionPersistenceEnabled(getBoolean(e, MQTT_SUBSCRIPTION_PERSISTENCE_ENABLED, config.isMqttSubscriptionPersistenceEnabled()));

      config.setGlobalMaxSizePercentOfJvmMaxMemory(getInteger(e, GLOBAL_MAX_SIZE_PERCENT_JVM_MAX_MEM, config.getGlobalMaxSizePercentOfJvmMaxMemory(), GT_ZERO));

      long globalMaxSize = getTextBytesAsLongBytes(e, GLOBAL_MAX_SIZE, -1, MINUS_ONE_OR_GT_ZERO);

      if (globalMaxSize > 0) {
         // We only set it if it's not set on the XML, otherwise getGlobalMaxSize will calculate it.
         // We do it this way because it will be valid also on the case of embedded
         config.setGlobalMaxSize(globalMaxSize);
      }

      long globalMaxMessages = getLong(e, GLOBAL_MAX_MESSAGES, -1, MINUS_ONE_OR_GT_ZERO);

      config.setGlobalMaxMessages(globalMaxMessages);

      config.setMinDiskFree(getTextBytesAsLongBytes(e, MIN_DISK_FREE, config.getMinDiskFree(), MINUS_ONE_OR_GT_ZERO));

      config.setMaxDiskUsage(getInteger(e, MAX_DISK_USAGE, config.getMaxDiskUsage(), PERCENTAGE_OR_MINUS_ONE));

      config.setDiskScanPeriod(getInteger(e, DISK_SCAN_PERIOD, config.getDiskScanPeriod(), MINUS_ONE_OR_GT_ZERO));

      config.setInternalNamingPrefix(getString(e, INTERNAL_NAMING_PREFIX, config.getInternalNamingPrefix(), NO_CHECK));

      config.setAmqpUseCoreSubscriptionNaming(getBoolean(e, AMQP_USE_CORE_SUBSCRIPTION_NAMING, config.isAmqpUseCoreSubscriptionNaming()));


      // parsing cluster password
      String passwordText = getString(e, "cluster-password", null, NO_CHECK);

      final Boolean maskText = config.isMaskPassword();

      if (passwordText != null) {
         String resolvedPassword = PasswordMaskingUtil.resolveMask(maskText, passwordText, config.getPasswordCodec());
         config.setClusterPassword(resolvedPassword);
      }

      config.setClusterUser(getString(e, "cluster-user", config.getClusterUser(), NO_CHECK));

      NodeList storeTypeNodes = e.getElementsByTagName("store");

      if (storeTypeNodes.getLength() > 0) {
         parseStoreConfiguration((Element) storeTypeNodes.item(0), config);
      }

      NodeList interceptorNodes = e.getElementsByTagName("remoting-interceptors");

      List<String> incomingInterceptorList = new ArrayList<>();

      if (interceptorNodes.getLength() > 0) {
         NodeList interceptors = interceptorNodes.item(0).getChildNodes();

         for (int i = 0; i < interceptors.getLength(); i++) {
            if ("class-name".equalsIgnoreCase(interceptors.item(i).getNodeName())) {
               String clazz = getTrimmedTextContent(interceptors.item(i));

               incomingInterceptorList.add(clazz);
            }
         }
      }

      NodeList incomingInterceptorNodes = e.getElementsByTagName("remoting-incoming-interceptors");

      if (incomingInterceptorNodes.getLength() > 0) {
         NodeList interceptors = incomingInterceptorNodes.item(0).getChildNodes();

         for (int i = 0; i < interceptors.getLength(); i++) {
            if ("class-name".equalsIgnoreCase(interceptors.item(i).getNodeName())) {
               String clazz = getTrimmedTextContent(interceptors.item(i));

               incomingInterceptorList.add(clazz);
            }
         }
      }

      config.setIncomingInterceptorClassNames(incomingInterceptorList);

      NodeList outgoingInterceptorNodes = e.getElementsByTagName("remoting-outgoing-interceptors");

      List<String> outgoingInterceptorList = new ArrayList<>();

      if (outgoingInterceptorNodes.getLength() > 0) {
         NodeList interceptors = outgoingInterceptorNodes.item(0).getChildNodes();

         for (int i = 0; i < interceptors.getLength(); i++) {
            if ("class-name".equalsIgnoreCase(interceptors.item(i).getNodeName())) {
               String clazz = interceptors.item(i).getTextContent();

               outgoingInterceptorList.add(clazz);
            }
         }
      }

      config.setOutgoingInterceptorClassNames(outgoingInterceptorList);

      NodeList connectorNodes = e.getElementsByTagName("connector");

      for (int i = 0; i < connectorNodes.getLength(); i++) {
         Element connectorNode = (Element) connectorNodes.item(i);

         TransportConfiguration connectorConfig = parseConnectorTransportConfiguration(connectorNode, config);

         if (connectorConfig.getName() == null) {
            ActiveMQServerLogger.LOGGER.connectorWithNoName();

            continue;
         }

         if (config.getConnectorConfigurations().containsKey(connectorConfig.getName())) {
            ActiveMQServerLogger.LOGGER.connectorAlreadyDeployed(connectorConfig.getName());

            continue;
         }

         config.getConnectorConfigurations().put(connectorConfig.getName(), connectorConfig);
      }

      NodeList acceptorNodes = e.getElementsByTagName("acceptor");

      for (int i = 0; i < acceptorNodes.getLength(); i++) {
         Element acceptorNode = (Element) acceptorNodes.item(i);

         TransportConfiguration acceptorConfig = parseAcceptorTransportConfiguration(acceptorNode, config);

         config.getAcceptorConfigurations().add(acceptorConfig);
      }

      NodeList bgNodes = e.getElementsByTagName("broadcast-group");

      for (int i = 0; i < bgNodes.getLength(); i++) {
         Element bgNode = (Element) bgNodes.item(i);

         parseBroadcastGroupConfiguration(bgNode, config);
      }

      NodeList dgNodes = e.getElementsByTagName("discovery-group");

      for (int i = 0; i < dgNodes.getLength(); i++) {
         Element dgNode = (Element) dgNodes.item(i);

         parseDiscoveryGroupConfiguration(dgNode, config);
      }

      // Ensure AMQP bridge configuration isn't picked up here by core bridge
      // configuration parsing.
      NodeList bridges = e.getElementsByTagName("bridges");

      for (int i = 0; i < bridges.getLength(); i++) {
         Element bridgesElement = (Element) bridges.item(i);

         for (int j = 0; j < bridgesElement.getChildNodes().getLength(); ++j) {
            Node node = bridgesElement.getChildNodes().item(j);

            if (node.getNodeName().equalsIgnoreCase("bridge")) {
               parseBridgeConfiguration((Element) node, config);
            }
         }
      }

      NodeList fedNodes = e.getElementsByTagName("federation");

      for (int i = 0; i < fedNodes.getLength(); i++) {
         Element fedNode = (Element) fedNodes.item(i);

         parseFederationConfiguration(fedNode, config);
      }

      NodeList gaNodes = e.getElementsByTagName("grouping-handler");

      for (int i = 0; i < gaNodes.getLength(); i++) {
         Element gaNode = (Element) gaNodes.item(i);

         parseGroupingHandlerConfiguration(gaNode, config);
      }

      NodeList ccNodes = e.getElementsByTagName("cluster-connection");

      for (int i = 0; i < ccNodes.getLength(); i++) {
         Element ccNode = (Element) ccNodes.item(i);

         parseClusterConnectionConfiguration(ccNode, config);
      }

      NodeList ccNodesURI = e.getElementsByTagName("cluster-connection-uri");

      for (int i = 0; i < ccNodesURI.getLength(); i++) {
         Element ccNode = (Element) ccNodesURI.item(i);

         parseClusterConnectionConfigurationURI(ccNode, config);
      }


      NodeList ccAMQPConnections = e.getElementsByTagName("broker-connections");

      if (ccAMQPConnections != null) {
         NodeList ccAMQConnectionsURI = e.getElementsByTagName("amqp-connection");

         if (ccAMQConnectionsURI != null) {
            for (int i = 0; i < ccAMQConnectionsURI.getLength(); i++) {
               Element ccNode = (Element) ccAMQConnectionsURI.item(i);

               parseAMQPBrokerConnections(ccNode, config);
            }
         }
      }

      NodeList dvNodes = e.getElementsByTagName("divert");

      for (int i = 0; i < dvNodes.getLength(); i++) {
         Element dvNode = (Element) dvNodes.item(i);

         parseDivertConfiguration(dvNode, config);
      }

      NodeList ccConnectionRouters = e.getElementsByTagName("connection-routers");

      if (ccConnectionRouters != null) {
         NodeList ccConnectionRouter = e.getElementsByTagName("connection-router");

         if (ccConnectionRouter != null) {
            for (int i = 0; i < ccConnectionRouter.getLength(); i++) {
               Element ccNode = (Element) ccConnectionRouter.item(i);

               parseConnectionRouterConfiguration(ccNode, config);
            }
         }
      }

      // Persistence config

      config.setLargeMessagesDirectory(getString(e, "large-messages-directory", config.getLargeMessagesDirectory(), NOT_NULL_OR_EMPTY));

      config.setBindingsDirectory(getString(e, "bindings-directory", config.getBindingsDirectory(), NOT_NULL_OR_EMPTY));

      config.setCreateBindingsDir(getBoolean(e, "create-bindings-dir", config.isCreateBindingsDir()));

      config.setJournalDirectory(getString(e, "journal-directory", config.getJournalDirectory(), NOT_NULL_OR_EMPTY));


      parseJournalRetention(e, config);

      config.setNodeManagerLockDirectory(getString(e, "node-manager-lock-directory", null, NO_CHECK));

      config.setPageMaxConcurrentIO(getInteger(e, "page-max-concurrent-io", config.getPageMaxConcurrentIO(), MINUS_ONE_OR_GT_ZERO));

      config.setReadWholePage(getBoolean(e, "read-whole-page", config.isReadWholePage()));

      config.setPagingDirectory(getString(e, "paging-directory", config.getPagingDirectory(), NOT_NULL_OR_EMPTY));

      config.setPurgePageFolders(getBoolean(e, "purge-page-folders", config.isPurgePageFolders()));

      config.setCreateJournalDir(getBoolean(e, "create-journal-dir", config.isCreateJournalDir()));

      String s = getString(e, "journal-type", config.getJournalType().toString(), JOURNAL_TYPE);

      config.setJournalType(JournalType.getType(s));

      if (config.getJournalType() == JournalType.ASYNCIO) {
         // We do the check here to see if AIO is supported so we can use the correct defaults and/or use correct
         // settings in xml. If we fall back later on these settings can be ignored.
         boolean supportsAIO = AIOSequentialFileFactory.isSupported();

         if (!supportsAIO) {
            if (validateAIO) {
               ActiveMQServerLogger.LOGGER.AIONotFound();
            }
            config.setJournalType(JournalType.NIO);
         }
      }

      config.setJournalDatasync(getBoolean(e, "journal-datasync", config.isJournalDatasync()));

      config.setJournalSyncTransactional(getBoolean(e, "journal-sync-transactional", config.isJournalSyncTransactional()));

      config.setJournalSyncNonTransactional(getBoolean(e, "journal-sync-non-transactional", config.isJournalSyncNonTransactional()));

      config.setJournalFileSize(getTextBytesAsIntBytes(e, "journal-file-size", config.getJournalFileSize(), POSITIVE_INT));

      config.setJournalMaxAtticFiles(getInteger(e, "journal-max-attic-files", config.getJournalMaxAtticFiles(), NO_CHECK));

      int journalBufferTimeout = getInteger(e, "journal-buffer-timeout", config.getJournalType() == JournalType.ASYNCIO ? ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_AIO : ArtemisConstants.DEFAULT_JOURNAL_BUFFER_TIMEOUT_NIO, GE_ZERO);

      int journalBufferSize = getTextBytesAsIntBytes(e, "journal-buffer-size", config.getJournalType() == JournalType.ASYNCIO ? ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_AIO : ArtemisConstants.DEFAULT_JOURNAL_BUFFER_SIZE_NIO, POSITIVE_INT);

      int journalMaxIO = getInteger(e, "journal-max-io", config.getJournalType() == JournalType.ASYNCIO ? ActiveMQDefaultConfiguration.getDefaultJournalMaxIoAio() : ActiveMQDefaultConfiguration.getDefaultJournalMaxIoNio(), GT_ZERO);

      config.setJournalDeviceBlockSize(getInteger(e, "journal-device-block-size", null, MINUS_ONE_OR_GE_ZERO));

      if (config.getJournalType() == JournalType.ASYNCIO) {
         config.setJournalBufferTimeout_AIO(journalBufferTimeout);
         config.setJournalBufferSize_AIO(journalBufferSize);
         config.setJournalMaxIO_AIO(journalMaxIO);
      } else {
         config.setJournalBufferTimeout_NIO(journalBufferTimeout);
         config.setJournalBufferSize_NIO(journalBufferSize);
         config.setJournalMaxIO_NIO(journalMaxIO);
      }

      config.setJournalFileOpenTimeout(getInteger(e, "journal-file-open-timeout", ActiveMQDefaultConfiguration.getDefaultJournalFileOpenTimeout(), GT_ZERO));

      config.setJournalMinFiles(getInteger(e, "journal-min-files", config.getJournalMinFiles(), GT_ZERO));

      config.setJournalPoolFiles(getInteger(e, "journal-pool-files", config.getJournalPoolFiles(), MINUS_ONE_OR_GT_ZERO));

      config.setJournalCompactMinFiles(getInteger(e, "journal-compact-min-files", config.getJournalCompactMinFiles(), GE_ZERO));

      config.setJournalCompactPercentage(getInteger(e, "journal-compact-percentage", config.getJournalCompactPercentage(), PERCENTAGE));

      config.setLogJournalWriteRate(getBoolean(e, "log-journal-write-rate", ActiveMQDefaultConfiguration.isDefaultJournalLogWriteRate()));

      config.setJournalLockAcquisitionTimeout(getLong(e, "journal-lock-acquisition-timeout", config.getJournalLockAcquisitionTimeout(), MINUS_ONE_OR_GT_ZERO));

      if (e.hasAttribute("wild-card-routing-enabled")) {
         config.setWildcardRoutingEnabled(getBoolean(e, "wild-card-routing-enabled", config.isWildcardRoutingEnabled()));
      }

      config.setMessageCounterEnabled(getBoolean(e, "message-counter-enabled", config.isMessageCounterEnabled()));

      config.setMessageCounterSamplePeriod(getLong(e, "message-counter-sample-period", config.getMessageCounterSamplePeriod(), GT_ZERO));

      config.setMessageCounterMaxDayHistory(getInteger(e, "message-counter-max-day-history", config.getMessageCounterMaxDayHistory(), GT_ZERO));

      config.setServerDumpInterval(getLong(e, "server-dump-interval", config.getServerDumpInterval(), MINUS_ONE_OR_GT_ZERO)); // in milliseconds

      config.setMemoryWarningThreshold(getInteger(e, "memory-warning-threshold", config.getMemoryWarningThreshold(), PERCENTAGE));

      config.setMemoryMeasureInterval(getLong(e, "memory-measure-interval", config.getMemoryMeasureInterval(), MINUS_ONE_OR_GT_ZERO));

      config.setNetworkCheckList(getString(e, "network-check-list", config.getNetworkCheckList(), NO_CHECK));

      config.setNetworkCheckURLList(getString(e, "network-check-URL-list", config.getNetworkCheckURLList(), NO_CHECK));

      config.setNetworkCheckPeriod(getLong(e, "network-check-period", config.getNetworkCheckPeriod(), GT_ZERO));

      config.setNetworkCheckTimeout(getInteger(e, "network-check-timeout", config.getNetworkCheckTimeout(), GT_ZERO));

      config.setNetworkCheckNIC(getString(e, "network-check-NIC", config.getNetworkCheckNIC(), NO_CHECK));

      config.setNetworkCheckPing6Command(getString(e, "network-check-ping6-command", config.getNetworkCheckPing6Command(), NO_CHECK));

      config.setNetworkCheckPingCommand(getString(e, "network-check-ping-command", config.getNetworkCheckPingCommand(), NO_CHECK));

      config.setCriticalAnalyzer(getBoolean(e, "critical-analyzer", config.isCriticalAnalyzer()));

      config.setCriticalAnalyzerTimeout(getLong(e, "critical-analyzer-timeout", config.getCriticalAnalyzerTimeout(), GE_ZERO));

      config.setCriticalAnalyzerCheckPeriod(getLong(e, "critical-analyzer-check-period", config.getCriticalAnalyzerCheckPeriod(), GE_ZERO));

      config.setCriticalAnalyzerPolicy(CriticalAnalyzerPolicy.valueOf(getString(e, "critical-analyzer-policy", config.getCriticalAnalyzerPolicy().name(), NOT_NULL_OR_EMPTY)));

      config.setPageSyncTimeout(getInteger(e, "page-sync-timeout", config.getJournalBufferTimeout_NIO(), GE_ZERO));

      config.setSuppressSessionNotifications(getBoolean(e, "suppress-session-notifications", config.isSuppressSessionNotifications()));

      config.setLiteralMatchMarkers(getString(e, "literal-match-markers", config.getLiteralMatchMarkers(), NULL_OR_TWO_CHARACTERS));

      config.setLargeMessageSync(getBoolean(e, "large-message-sync", config.isLargeMessageSync()));

      config.setViewPermissionMethodMatchPattern(getString(e, "view-permission-method-match-pattern", config.getViewPermissionMethodMatchPattern(), NO_CHECK));

      config.setManagementMessageRbac(getBoolean(e, "management-message-rbac", config.isManagementMessageRbac()));

      config.setManagementRbacPrefix(getString(e, "management-rbac-prefix", config.getManagementRbacPrefix(), NO_CHECK));

      config.setMirrorPageTransaction(getBoolean(e, MIRROR_PAGE_TRANSACTION, config.isMirrorPageTransaction()));

      config.setMirrorAckManagerPageAttempts(getInteger(e, MIRROR_ACK_MANAGER_PAGE_ATTEMPTS, config.getMirrorAckManagerPageAttempts(), GT_ZERO));

      config.setMirrorAckManagerQueueAttempts(getInteger(e, MIRROR_ACK_MANAGER_QUEUE_ATTEMPTS, config.getMirrorAckManagerQueueAttempts(), GT_ZERO));

      config.setMirrorAckManagerRetryDelay(getInteger(e, MIRROR_ACK_MANAGER_RETRY_DELAY, config.getMirrorAckManagerRetryDelay(), GT_ZERO));

      config.setMirrorAckManagerWarnUnacked(getBoolean(e, MIRROR_ACK_MANAGER_WARN_UNACKED, config.isMirrorAckManagerWarnUnacked()));

      parseAddressSettings(e, config);

      parseResourceLimits(e, config);

      parseQueues(e, config);

      parseAddresses(e, config);

      parseSecurity(e, config);

      parseBrokerPlugins(e, config);

      parseMetrics(e, config);

      NodeList connectorServiceConfigs = e.getElementsByTagName("connector-service");

      List<ConnectorServiceConfiguration> configs = new ArrayList<>();

      for (int i = 0; i < connectorServiceConfigs.getLength(); i++) {
         Element node = (Element) connectorServiceConfigs.item(i);

         configs.add((parseConnectorService(node)));
      }

      config.setConnectorServiceConfigurations(configs);

      NodeList wildCardConfiguration = e.getElementsByTagName("wildcard-addresses");

      if (wildCardConfiguration.getLength() > 0) {
         parseWildcardConfiguration((Element) wildCardConfiguration.item(0), config);
      }
   }


   private void parseJournalRetention(final Element e, final Configuration config) {
      NodeList retention = e.getElementsByTagName("journal-retention-directory");

      if (retention.getLength() != 0) {
         Element node = (Element) retention.item(0);

         String directory = node.getTextContent().trim();

         String storageLimitStr = getAttributeValue(node, "storage-limit");
         long storageLimit;

         if (storageLimitStr == null) {
            storageLimit = -1;
         } else {
            storageLimit = ByteUtil.convertTextBytes(storageLimitStr.trim());
         }
         int period = getAttributeInteger(node, "period", -1, GT_ZERO);
         String unitStr = getAttributeValue(node, "unit");

         if (unitStr == null) {
            unitStr = "DAYS";
         }

         TimeUnit unit = TimeUnit.valueOf(unitStr.toUpperCase());

         config.setJournalRetentionDirectory(directory);
         config.setJournalRetentionMaxBytes(storageLimit);
         config.setJournalRetentionPeriod(unit, period);

         if (directory == null || directory.isEmpty()) {
            throw new IllegalArgumentException("journal-retention-directory=null");
         }

         if (storageLimit == -1 && period == -1) {
            throw new IllegalArgumentException("configure either storage-limit or period on journal-retention-directory");
         }

      }
   }

   private void parseSecurity(final Element e, final Configuration config) throws Exception {
      NodeList elements = e.getElementsByTagName("security-settings");
      if (elements.getLength() != 0) {
         Element node = (Element) elements.item(0);
         NodeList list = node.getElementsByTagName(SECURITY_ROLE_MAPPING_NAME);
         for (int i = 0; i < list.getLength(); i++) {
            Map<String, Set<String>> roleMappings = parseSecurityRoleMapping(list.item(i));
            for (Map.Entry<String, Set<String>> roleMapping : roleMappings.entrySet()) {
               config.addSecurityRoleNameMapping(roleMapping.getKey(), roleMapping.getValue());
            }
         }
         list = node.getElementsByTagName(SECURITY_ELEMENT_NAME);
         for (int i = 0; i < list.getLength(); i++) {
            Pair<String, Set<Role>> securityItem = parseSecurityRoles(list.item(i), config.getSecurityRoleNameMappings());
            config.putSecurityRoles(securityItem.getA(), securityItem.getB());
         }
         list = node.getElementsByTagName(SECURITY_PLUGIN_ELEMENT_NAME);
         for (int i = 0; i < list.getLength(); i++) {
            Pair<SecuritySettingPlugin, Map<String, String>> securityItem = parseSecuritySettingPlugins(list.item(i), config.isMaskPassword(), config.getPasswordCodec());
            config.addSecuritySettingPlugin(securityItem.getA().init(securityItem.getB()));
         }
      }
   }

   private void parseBrokerPlugins(final Element e, final Configuration config) {
      NodeList brokerPlugins = e.getElementsByTagName(BROKER_PLUGINS_ELEMENT_NAME);
      if (brokerPlugins.getLength() != 0) {
         Element node = (Element) brokerPlugins.item(0);
         NodeList list = node.getElementsByTagName(BROKER_PLUGIN_ELEMENT_NAME);
         for (int i = 0; i < list.getLength(); i++) {
            ActiveMQServerPlugin plugin = parseActiveMQServerPlugin(list.item(i));
            config.registerBrokerPlugin(plugin);
         }
      }
   }

   private ActiveMQServerPlugin parseActiveMQServerPlugin(Node item) {
      final String clazz = item.getAttributes().getNamedItem("class-name").getNodeValue();

      Map<String, String> properties = getMapOfChildPropertyElements(item);

      ActiveMQServerPlugin serverPlugin = SecurityManagerShim.doPrivileged((PrivilegedAction<ActiveMQServerPlugin>) () -> (ActiveMQServerPlugin) ClassloadingUtil.newInstanceFromClassLoader(FileConfigurationParser.class, clazz, ActiveMQServerPlugin.class));

      serverPlugin.init(properties);

      return serverPlugin;
   }

   private Map<String, String> getMapOfChildPropertyElements(Node item) {
      Map<String, String> properties = new HashMap<>();
      NodeList children = item.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);
         if (child.getNodeName().equals("property")) {
            String key = getAttributeValue(child, "key");
            String value = getAttributeValue(child, "value");
            properties.put(key, value);
         }
      }
      return properties;
   }

   private void parseMetrics(final Element e, final Configuration config) {
      NodeList metrics = e.getElementsByTagName("metrics");
      NodeList metricsPlugin = e.getElementsByTagName("metrics-plugin");
      MetricsConfiguration metricsConfiguration = new MetricsConfiguration();

      if (metrics.getLength() != 0) {
         Element node = (Element) metrics.item(0);
         NodeList children = node.getChildNodes();
         for (int j = 0; j < children.getLength(); j++) {
            Node child = children.item(j);
            if (child.getNodeName().equals("jvm-gc")) {
               metricsConfiguration.setJvmGc(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("jvm-memory")) {
               metricsConfiguration.setJvmMemory(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("jvm-threads")) {
               metricsConfiguration.setJvmThread(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("netty-pool")) {
               metricsConfiguration.setNettyPool(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("file-descriptors")) {
               metricsConfiguration.setFileDescriptors(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("processor")) {
               metricsConfiguration.setProcessor(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("uptime")) {
               metricsConfiguration.setUptime(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("logging")) {
               metricsConfiguration.setLogging(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("security-caches")) {
               metricsConfiguration.setSecurityCaches(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("executor-services")) {
               metricsConfiguration.setExecutorServices(XMLUtil.parseBoolean(child));
            } else if (child.getNodeName().equals("plugin")) {
               metricsConfiguration.setPlugin(parseMetricsPlugin(child, config));
            }
         }

         if (metricsPlugin.getLength() != 0) {
            ActiveMQServerLogger.LOGGER.metricsPluginElementIgnored();
         }
      } else { // for backwards compatibility
         if (metricsPlugin.getLength() != 0) {
            ActiveMQServerLogger.LOGGER.metricsPluginElementDeprecated();
            metricsConfiguration.setPlugin(parseMetricsPlugin(metricsPlugin.item(0), config));
         }
      }

      config.setMetricsConfiguration(metricsConfiguration);
   }

   private ActiveMQMetricsPlugin parseMetricsPlugin(final Node item, final Configuration config) {
      final String clazz = item.getAttributes().getNamedItem("class-name").getNodeValue();

      Map<String, String> properties = getMapOfChildPropertyElements(item);

      ActiveMQMetricsPlugin metricsPlugin = SecurityManagerShim.doPrivileged((PrivilegedAction<ActiveMQMetricsPlugin>) () -> (ActiveMQMetricsPlugin) ClassloadingUtil.newInstanceFromClassLoader(FileConfigurationParser.class, clazz, ActiveMQMetricsPlugin.class));

      ActiveMQServerLogger.LOGGER.initializingMetricsPlugin(clazz, properties.toString());

      // leaving this as-is for backwards compatibility
      config.setMetricsPlugin(metricsPlugin.init(properties));

      return metricsPlugin;
   }

   private void parseQueues(final Element e, final Configuration config) {
      NodeList elements = e.getElementsByTagName("queues");
      if (elements.getLength() != 0) {
         ActiveMQServerLogger.LOGGER.queuesElementDeprecated();
         Element node = (Element) elements.item(0);
         config.setQueueConfigs(parseQueueConfigurations(node, ActiveMQDefaultConfiguration.DEFAULT_ROUTING_TYPE));
      }
   }

   private List<QueueConfiguration> parseQueueConfigurations(final Element node, RoutingType routingType) {
      List<QueueConfiguration> queueConfigurations = new ArrayList<>();
      NodeList list = node.getElementsByTagName("queue");
      for (int i = 0; i < list.getLength(); i++) {
         QueueConfiguration queueConfig = parseQueueConfiguration(list.item(i));
         queueConfig.setRoutingType(routingType);
         queueConfigurations.add(queueConfig);
      }
      return queueConfigurations;
   }

   private void parseAddresses(final Element e, final Configuration config) {
      NodeList elements = e.getElementsByTagName("addresses");

      if (elements.getLength() != 0) {
         Element node = (Element) elements.item(0);
         NodeList list = node.getElementsByTagName("address");
         for (int i = 0; i < list.getLength(); i++) {
            config.addAddressConfiguration(parseAddressConfiguration(list.item(i)));
         }
      }
   }

   private void parseAddressSettings(final Element e, final Configuration config) {
      NodeList elements = e.getElementsByTagName("address-settings");

      if (elements.getLength() != 0) {
         Element node = (Element) elements.item(0);
         NodeList list = node.getElementsByTagName("address-setting");
         for (int i = 0; i < list.getLength(); i++) {
            Pair<String, AddressSettings> newAddressSettings = parseAddressSettings(list.item(i));
            Map<String, AddressSettings> addressSettings = config.getAddressSettings();
            if (addressSettings.containsKey(newAddressSettings.getA())) {
               ActiveMQServerLogger.LOGGER.duplicateAddressSettingMatch(newAddressSettings.getA());
            } else {
               config.getAddressSettings().put(newAddressSettings.getA(), newAddressSettings.getB());
            }
         }
      }
   }

   private void parseResourceLimits(final Element e, final Configuration config) {
      NodeList elements = e.getElementsByTagName("resource-limit-settings");

      if (elements.getLength() != 0) {
         Element node = (Element) elements.item(0);
         NodeList list = node.getElementsByTagName("resource-limit-setting");
         for (int i = 0; i < list.getLength(); i++) {
            config.addResourceLimitSettings(parseResourceLimitSettings(list.item(i)));
         }
      }
   }

   protected Pair<String, Set<Role>> parseSecurityRoles(final Node node, final Map<String, Set<String>> roleMappings) {
      final String match = node.getAttributes().getNamedItem("match").getNodeValue();

      Set<Role> securityRoles = new HashSet<>();

      Pair<String, Set<Role>> securityMatch = new Pair<>(match, securityRoles);

      List<String> send = new ArrayList<>();
      List<String> consume = new ArrayList<>();
      List<String> createDurableQueue = new ArrayList<>();
      List<String> deleteDurableQueue = new ArrayList<>();
      List<String> createNonDurableQueue = new ArrayList<>();
      List<String> deleteNonDurableQueue = new ArrayList<>();
      List<String> manageRoles = new ArrayList<>();
      List<String> browseRoles = new ArrayList<>();
      List<String> createAddressRoles = new ArrayList<>();
      List<String> deleteAddressRoles = new ArrayList<>();
      List<String> viewRoles = new ArrayList<>();
      List<String> editRoles = new ArrayList<>();
      List<String> allRoles = new ArrayList<>();
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);
         final String name = child.getNodeName();
         if (PERMISSION_ELEMENT_NAME.equalsIgnoreCase(name)) {
            final String type = getAttributeValue(child, TYPE_ATTR_NAME);
            final String roleString = getAttributeValue(child, ROLES_ATTR_NAME);
            String[] roles = roleString.split(",");
            String[] mappedRoles = getMappedRoleNames(roles, roleMappings);

            for (String role : mappedRoles) {
               if (SEND_PERMISSION.equals(type)) {
                  send.add(role.trim());
               } else if (CONSUME_PERMISSION.equals(type)) {
                  consume.add(role.trim());
               } else if (CREATE_DURABLE_QUEUE_PERMISSION.equals(type)) {
                  createDurableQueue.add(role.trim());
               } else if (DELETE_DURABLE_QUEUE_PERMISSION.equals(type)) {
                  deleteDurableQueue.add(role.trim());
               } else if (CREATE_NONDURABLE_QUEUE_PERMISSION.equals(type)) {
                  createNonDurableQueue.add(role.trim());
               } else if (DELETE_NONDURABLE_QUEUE_PERMISSION.equals(type)) {
                  deleteNonDurableQueue.add(role.trim());
               } else if (CREATETEMPQUEUE_NAME.equals(type)) {
                  createNonDurableQueue.add(role.trim());
               } else if (DELETETEMPQUEUE_NAME.equals(type)) {
                  deleteNonDurableQueue.add(role.trim());
               } else if (MANAGE_PERMISSION.equals(type)) {
                  manageRoles.add(role.trim());
               } else if (BROWSE_PERMISSION.equals(type)) {
                  browseRoles.add(role.trim());
               } else if (CREATE_ADDRESS_PERMISSION.equals(type)) {
                  createAddressRoles.add(role.trim());
               } else if (DELETE_ADDRESS_PERMISSION.equals(type)) {
                  deleteAddressRoles.add(role.trim());
               } else if (VIEW_PERMISSION.equals(type)) {
                  viewRoles.add(role.trim());
               } else if (EDIT_PERMISSION.equals(type)) {
                  editRoles.add(role.trim());
               } else {
                  ActiveMQServerLogger.LOGGER.rolePermissionConfigurationError(type);
               }
               if (!allRoles.contains(role.trim())) {
                  allRoles.add(role.trim());
               }
            }
         }
      }

      for (String role : allRoles) {
         securityRoles.add(new Role(role, send.contains(role), consume.contains(role), createDurableQueue.contains(role), deleteDurableQueue.contains(role), createNonDurableQueue.contains(role), deleteNonDurableQueue.contains(role), manageRoles.contains(role), browseRoles.contains(role), createAddressRoles.contains(role), deleteAddressRoles.contains(role), viewRoles.contains(role), editRoles.contains(role)));
      }

      return securityMatch;
   }

   /**
    * Translate and expand a set of role names to a set of mapped role names, also includes the original role names
    *
    * @param roles        the original set of role names
    * @param roleMappings a one-to-many mapping of original role names to mapped role names
    * @return the final set of mapped role names
    */
   private String[] getMappedRoleNames(String[] roles, Map<String, Set<String>> roleMappings) {
      Set<String> mappedRoles = new HashSet<>();
      for (String role : roles) {
         if (roleMappings.containsKey(role)) {
            mappedRoles.addAll(roleMappings.get(role));
         }
         mappedRoles.add(role);
      }
      return mappedRoles.toArray(new String[mappedRoles.size()]);
   }

   private Pair<SecuritySettingPlugin, Map<String, String>> parseSecuritySettingPlugins(Node item, Boolean maskPassword, String passwordCodec) throws Exception {
      final String clazz = item.getAttributes().getNamedItem("class-name").getNodeValue();
      final Map<String, String> settings = new HashMap<>();
      NodeList children = item.getChildNodes();
      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);
         final String nodeName = child.getNodeName();
         if (SETTING_ELEMENT_NAME.equalsIgnoreCase(nodeName)) {
            final String settingName = getAttributeValue(child, NAME_ATTR_NAME);
            String settingValue = getAttributeValue(child, VALUE_ATTR_NAME);
            if (settingValue != null && PasswordMaskingUtil.isEncMasked(settingValue)) {
               settingValue = PasswordMaskingUtil.resolveMask(maskPassword, settingValue, passwordCodec);
            }
            settings.put(settingName, settingValue);
         }
      }

      SecuritySettingPlugin securitySettingPlugin = SecurityManagerShim.doPrivileged((PrivilegedAction<SecuritySettingPlugin>) () -> (SecuritySettingPlugin) ClassloadingUtil.newInstanceFromClassLoader(FileConfigurationParser.class, clazz, SecuritySettingPlugin.class));

      return new Pair<>(securitySettingPlugin, settings);
   }

   /**
    * Computes the map of internal ActiveMQ role names to sets of external (e.g. LDAP) role names.  For example, given a
    * role "myrole" with a DN of "cn=myrole,dc=local,dc=com":
    * <pre>{@code
    *      from="cn=myrole,dc=local,dc=com", to="amq,admin,guest"
    *      from="cn=myOtherRole,dc=local,dc=com", to="amq"
    * }</pre>
    * The resulting map will consist of:
    * <pre>{@code
    *      amq => {"cn=myrole,dc=local,dc=com","cn=myOtherRole",dc=local,dc=com"}
    *      admin => {"cn=myrole,dc=local,dc=com"}
    *      guest => {"cn=myrole,dc=local,dc=com"}
    * }</pre>
    *
    * @param item the role-mapping node
    * @return the map of local ActiveMQ role names to the set of mapped role names
    */
   private Map<String, Set<String>> parseSecurityRoleMapping(Node item) {
      Map<String, Set<String>> mappedRoleNames = new HashMap<>();
      String externalRoleName = getAttributeValue(item, ROLE_FROM_ATTR_NAME).trim();
      Set<String> internalRoleNames = new HashSet<>();
      Collections.addAll(internalRoleNames, getAttributeValue(item, ROLE_TO_ATTR_NAME).split(","));
      for (String internalRoleName : internalRoleNames) {
         internalRoleName = internalRoleName.trim();
         if (mappedRoleNames.containsKey(internalRoleName)) {
            mappedRoleNames.get(internalRoleName).add(externalRoleName);
         } else {
            Set<String> externalRoleNames = new HashSet<>();
            externalRoleNames.add(externalRoleName);
            if ((!internalRoleName.isEmpty()) && (!externalRoleName.isEmpty())) {
               mappedRoleNames.put(internalRoleName, externalRoleNames);
            }
         }
      }
      return mappedRoleNames;
   }

   protected Pair<String, AddressSettings> parseAddressSettings(final Node node) {
      String match = getAttributeValue(node, "match");

      NodeList children = node.getChildNodes();

      AddressSettings addressSettings = new AddressSettings();

      Pair<String, AddressSettings> setting = new Pair<>(match, addressSettings);

      for (int i = 0; i < children.getLength(); i++) {
         final Node child = children.item(i);
         final String name = child.getNodeName();
         if (DEAD_LETTER_ADDRESS_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setDeadLetterAddress(SimpleString.of(getTrimmedTextContent(child)));
         } else if (EXPIRY_ADDRESS_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setExpiryAddress(SimpleString.of(getTrimmedTextContent(child)));
         } else if (EXPIRY_DELAY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setExpiryDelay(XMLUtil.parseLong(child));
         } else if (MIN_EXPIRY_DELAY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setMinExpiryDelay(XMLUtil.parseLong(child));
         } else if (MAX_EXPIRY_DELAY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setMaxExpiryDelay(XMLUtil.parseLong(child));
         } else if (NO_EXPIRY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setNoExpiry(XMLUtil.parseBoolean(child));
         } else if (REDELIVERY_DELAY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setRedeliveryDelay(XMLUtil.parseLong(child));
         } else if (REDELIVERY_DELAY_MULTIPLIER_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setRedeliveryMultiplier(XMLUtil.parseDouble(child));
         } else if (REDELIVERY_COLLISION_AVOIDANCE_FACTOR_NODE_NAME.equalsIgnoreCase(name)) {
            double redeliveryCollisionAvoidanceFactor = XMLUtil.parseDouble(child);
            GE_ZERO.validate(REDELIVERY_COLLISION_AVOIDANCE_FACTOR_NODE_NAME, redeliveryCollisionAvoidanceFactor);
            LE_ONE.validate(REDELIVERY_COLLISION_AVOIDANCE_FACTOR_NODE_NAME, redeliveryCollisionAvoidanceFactor);
            addressSettings.setRedeliveryCollisionAvoidanceFactor(redeliveryCollisionAvoidanceFactor);
         } else if (MAX_REDELIVERY_DELAY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setMaxRedeliveryDelay(XMLUtil.parseLong(child));
         } else if (MAX_SIZE_BYTES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setMaxSizeBytes(ByteUtil.convertTextBytes(getTrimmedTextContent(child)));
         } else if (MAX_MESSAGES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setMaxSizeMessages(XMLUtil.parseInt(child));
         } else if (MAX_SIZE_BYTES_REJECT_THRESHOLD_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setMaxSizeBytesRejectThreshold(ByteUtil.convertTextBytes(getTrimmedTextContent(child)));
         } else if (PAGE_SIZE_BYTES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setPageSizeBytes(POSITIVE_INT.validate(PAGE_SIZE_BYTES_NODE_NAME, ByteUtil.convertTextBytes(getTrimmedTextContent(child))).intValue());
         } else if (MAX_READ_PAGE_MESSAGES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setMaxReadPageMessages(MINUS_ONE_OR_POSITIVE_INT.validate(MAX_READ_PAGE_MESSAGES_NODE_NAME, Long.parseLong(getTrimmedTextContent(child))).intValue());
         } else if (MAX_READ_PAGE_BYTES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setMaxReadPageBytes(MINUS_ONE_OR_POSITIVE_INT.validate(MAX_READ_PAGE_BYTES_NODE_NAME, ByteUtil.convertTextBytes(getTrimmedTextContent(child))).intValue());
         } else if (PREFETCH_PAGE_MESSAGES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setPrefetchPageMessages(MINUS_ONE_OR_POSITIVE_INT.validate(PREFETCH_PAGE_MESSAGES_NODE_NAME, Long.parseLong(getTrimmedTextContent(child))).intValue());
         }  else if (PREFETCH_PAGE_BYTES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setPrefetchPageBytes(MINUS_ONE_OR_POSITIVE_INT.validate(PREFETCH_PAGE_BYTES_NODE_NAME, ByteUtil.convertTextBytes(getTrimmedTextContent(child))).intValue());
         } else if (PAGE_MAX_CACHE_SIZE_NODE_NAME.equalsIgnoreCase(name)) {
            if (!printPageMaxSizeUsed) {
               printPageMaxSizeUsed = true;
               ActiveMQServerLogger.LOGGER.pageMaxSizeUsed();
            }
            addressSettings.setPageCacheMaxSize(XMLUtil.parseInt(child));
         } else if (PAGE_LIMIT_BYTES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setPageLimitBytes(MINUS_ONE_OR_GT_ZERO.validate(PAGE_LIMIT_BYTES_NODE_NAME, ByteUtil.convertTextBytes(getTrimmedTextContent(child))).longValue());
         } else if (PAGE_LIMIT_MESSAGES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setPageLimitMessages(MINUS_ONE_OR_GT_ZERO.validate(PAGE_LIMIT_MESSAGES_NODE_NAME, XMLUtil.parseLong(child)).longValue());
         } else if (MESSAGE_COUNTER_HISTORY_DAY_LIMIT_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setMessageCounterHistoryDayLimit(XMLUtil.parseInt(child));
         } else if (ADDRESS_FULL_MESSAGE_POLICY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setAddressFullMessagePolicy(Enum.valueOf(AddressFullMessagePolicy.class, ADDRESS_FULL_MESSAGE_POLICY_TYPE.validate(ADDRESS_FULL_MESSAGE_POLICY_NODE_NAME, getTrimmedTextContent(child))));
         } else if (DISK_FULL_MESSAGE_POLICY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setDiskFullMessagePolicy(Enum.valueOf(DiskFullMessagePolicy.class, DISK_FULL_MESSAGE_POLICY_TYPE.validate(DISK_FULL_MESSAGE_POLICY_NODE_NAME, getTrimmedTextContent(child))));
         } else if (PAGE_FULL_MESSAGE_POLICY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setPageFullMessagePolicy(Enum.valueOf(PageFullMessagePolicy.class, PAGE_FULL_MESSAGE_POLICY_TYPE.validate(PAGE_FULL_MESSAGE_POLICY_NODE_NAME, getTrimmedTextContent(child))));
         } else if (LVQ_NODE_NAME.equalsIgnoreCase(name) || DEFAULT_LVQ_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setDefaultLastValueQueue(XMLUtil.parseBoolean(child));
         } else if (DEFAULT_LVQ_KEY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setDefaultLastValueKey(SimpleString.of(getTrimmedTextContent(child)));
         } else if (DEFAULT_NON_DESTRUCTIVE_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setDefaultNonDestructive(XMLUtil.parseBoolean(child));
         } else if (DEFAULT_EXCLUSIVE_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setDefaultExclusiveQueue(XMLUtil.parseBoolean(child));
         } else if (DEFAULT_GROUP_REBALANCE.equalsIgnoreCase(name)) {
            addressSettings.setDefaultGroupRebalance(XMLUtil.parseBoolean(child));
         } else if (DEFAULT_GROUP_REBALANCE_PAUSE_DISPATCH.equalsIgnoreCase(name)) {
            addressSettings.setDefaultGroupRebalancePauseDispatch(XMLUtil.parseBoolean(child));
         } else if (DEFAULT_GROUP_BUCKETS.equalsIgnoreCase(name)) {
            addressSettings.setDefaultGroupBuckets(XMLUtil.parseInt(child));
         } else if (DEFAULT_GROUP_FIRST_KEY.equalsIgnoreCase(name)) {
            addressSettings.setDefaultGroupFirstKey(SimpleString.of(getTrimmedTextContent(child)));
         } else if (MAX_DELIVERY_ATTEMPTS.equalsIgnoreCase(name)) {
            addressSettings.setMaxDeliveryAttempts(XMLUtil.parseInt(child));
         } else if (REDISTRIBUTION_DELAY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setRedistributionDelay(XMLUtil.parseLong(child));
         } else if (SEND_TO_DLA_ON_NO_ROUTE.equalsIgnoreCase(name)) {
            addressSettings.setSendToDLAOnNoRoute(XMLUtil.parseBoolean(child));
         } else if (SLOW_CONSUMER_THRESHOLD_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setSlowConsumerThreshold(MINUS_ONE_OR_GT_ZERO.validate(SLOW_CONSUMER_THRESHOLD_NODE_NAME, XMLUtil.parseLong(child)).longValue());
         } else if (SLOW_CONSUMER_THRESHOLD_MEASUREMENT_UNIT_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setSlowConsumerThresholdMeasurementUnit(SlowConsumerThresholdMeasurementUnit.valueOf(SLOW_CONSUMER_THRESHOLD_MEASUREMENT_UNIT.validate(SLOW_CONSUMER_THRESHOLD_MEASUREMENT_UNIT_NODE_NAME, getTrimmedTextContent(child))));
         } else if (SLOW_CONSUMER_CHECK_PERIOD_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setSlowConsumerCheckPeriod(GT_ZERO.validate(SLOW_CONSUMER_CHECK_PERIOD_NODE_NAME, XMLUtil.parseLong(child)).longValue());
         } else if (SLOW_CONSUMER_POLICY_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setSlowConsumerPolicy(Enum.valueOf(SlowConsumerPolicy.class, SLOW_CONSUMER_POLICY_TYPE.validate(SLOW_CONSUMER_POLICY_NODE_NAME, getTrimmedTextContent(child))));
         } else if (AUTO_CREATE_JMS_QUEUES.equalsIgnoreCase(name)) {
            addressSettings.setAutoCreateJmsQueues(XMLUtil.parseBoolean(child));
         } else if (AUTO_DELETE_JMS_QUEUES.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteJmsQueues(XMLUtil.parseBoolean(child));
         } else if (AUTO_CREATE_JMS_TOPICS.equalsIgnoreCase(name)) {
            addressSettings.setAutoCreateJmsTopics(XMLUtil.parseBoolean(child));
         } else if (AUTO_DELETE_JMS_TOPICS.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteJmsTopics(XMLUtil.parseBoolean(child));
         } else if (AUTO_CREATE_QUEUES.equalsIgnoreCase(name)) {
            addressSettings.setAutoCreateQueues(XMLUtil.parseBoolean(child));
         } else if (AUTO_DELETE_QUEUES.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteQueues(XMLUtil.parseBoolean(child));
         } else if (AUTO_DELETE_CREATED_QUEUES.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteCreatedQueues(XMLUtil.parseBoolean(child));
         } else if (AUTO_DELETE_QUEUES_DELAY.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteQueuesDelay(GE_ZERO.validate(AUTO_DELETE_QUEUES_DELAY, XMLUtil.parseLong(child)).longValue());
         } else if (AUTO_DELETE_QUEUES_MESSAGE_COUNT.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteQueuesMessageCount((Long) MINUS_ONE_OR_GE_ZERO.validate(AUTO_DELETE_QUEUES_MESSAGE_COUNT, XMLUtil.parseLong(child)));
         }  else if (AUTO_DELETE_QUEUES_SKIP_USAGE_CHECK.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteQueuesSkipUsageCheck(XMLUtil.parseBoolean(child));
         } else if (CONFIG_DELETE_QUEUES.equalsIgnoreCase(name)) {
            addressSettings.setConfigDeleteQueues(Enum.valueOf(DeletionPolicy.class, DELETION_POLICY_TYPE.validate(CONFIG_DELETE_QUEUES, getTrimmedTextContent(child))));
         } else if (AUTO_CREATE_ADDRESSES.equalsIgnoreCase(name)) {
            addressSettings.setAutoCreateAddresses(XMLUtil.parseBoolean(child));
         } else if (AUTO_DELETE_ADDRESSES.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteAddresses(XMLUtil.parseBoolean(child));
         } else if (AUTO_DELETE_ADDRESSES_DELAY.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteAddressesDelay(GE_ZERO.validate(AUTO_DELETE_ADDRESSES_DELAY, XMLUtil.parseLong(child)).longValue());
         } else if (AUTO_DELETE_ADDRESSES_SKIP_USAGE_CHECK.equalsIgnoreCase(name)) {
            addressSettings.setAutoDeleteAddressesSkipUsageCheck(XMLUtil.parseBoolean(child));
         } else if (CONFIG_DELETE_ADDRESSES.equalsIgnoreCase(name)) {
            addressSettings.setConfigDeleteAddresses(Enum.valueOf(DeletionPolicy.class, DELETION_POLICY_TYPE.validate(CONFIG_DELETE_ADDRESSES, getTrimmedTextContent(child))));
         } else if (CONFIG_DELETE_DIVERTS.equalsIgnoreCase(name)) {
            addressSettings.setConfigDeleteDiverts(Enum.valueOf(DeletionPolicy.class, DELETION_POLICY_TYPE.validate(CONFIG_DELETE_DIVERTS, getTrimmedTextContent(child))));
         } else if (MANAGEMENT_BROWSE_PAGE_SIZE.equalsIgnoreCase(name)) {
            addressSettings.setManagementBrowsePageSize(XMLUtil.parseInt(child));
         } else if (MANAGEMENT_MESSAGE_ATTRIBUTE_SIZE_LIMIT.equalsIgnoreCase(name)) {
            addressSettings.setManagementMessageAttributeSizeLimit(XMLUtil.parseInt(child));
         } else if (DEFAULT_PURGE_ON_NO_CONSUMERS.equalsIgnoreCase(name)) {
            addressSettings.setDefaultPurgeOnNoConsumers(XMLUtil.parseBoolean(child));
         } else if (DEFAULT_MAX_CONSUMERS.equalsIgnoreCase(name)) {
            addressSettings.setDefaultMaxConsumers(XMLUtil.parseInt(child));
         } else if (DEFAULT_CONSUMERS_BEFORE_DISPATCH.equalsIgnoreCase(name)) {
            addressSettings.setDefaultConsumersBeforeDispatch(XMLUtil.parseInt(child));
         } else if (DEFAULT_DELAY_BEFORE_DISPATCH.equalsIgnoreCase(name)) {
            addressSettings.setDefaultDelayBeforeDispatch(XMLUtil.parseLong(child));
         } else if (DEFAULT_QUEUE_ROUTING_TYPE.equalsIgnoreCase(name)) {
            addressSettings.setDefaultQueueRoutingType(RoutingType.valueOf(ROUTING_TYPE.validate(DEFAULT_QUEUE_ROUTING_TYPE, getTrimmedTextContent(child))));
         } else if (DEFAULT_ADDRESS_ROUTING_TYPE.equalsIgnoreCase(name)) {
            addressSettings.setDefaultAddressRoutingType(RoutingType.valueOf(ROUTING_TYPE.validate(DEFAULT_ADDRESS_ROUTING_TYPE, getTrimmedTextContent(child))));
         } else if (DEFAULT_CONSUMER_WINDOW_SIZE.equalsIgnoreCase(name)) {
            addressSettings.setDefaultConsumerWindowSize(XMLUtil.parseInt(child));
         } else if (DEFAULT_RING_SIZE.equalsIgnoreCase(name)) {
            addressSettings.setDefaultRingSize(XMLUtil.parseLong(child));
         } else if (RETROACTIVE_MESSAGE_COUNT.equalsIgnoreCase(name)) {
            addressSettings.setRetroactiveMessageCount(GE_ZERO.validate(RETROACTIVE_MESSAGE_COUNT, XMLUtil.parseLong(child)).longValue());
         } else if (AUTO_CREATE_DEAD_LETTER_RESOURCES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setAutoCreateDeadLetterResources(XMLUtil.parseBoolean(child));
         } else if (DEAD_LETTER_QUEUE_PREFIX_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setDeadLetterQueuePrefix(SimpleString.of(getTrimmedTextContent(child)));
         } else if (DEAD_LETTER_QUEUE_SUFFIX_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setDeadLetterQueueSuffix(SimpleString.of(getTrimmedTextContent(child)));
         } else if (AUTO_CREATE_EXPIRY_RESOURCES_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setAutoCreateExpiryResources(XMLUtil.parseBoolean(child));
         } else if (EXPIRY_QUEUE_PREFIX_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setExpiryQueuePrefix(SimpleString.of(getTrimmedTextContent(child)));
         } else if (EXPIRY_QUEUE_SUFFIX_NODE_NAME.equalsIgnoreCase(name)) {
            addressSettings.setExpiryQueueSuffix(SimpleString.of(getTrimmedTextContent(child)));
         } else if (ENABLE_METRICS.equalsIgnoreCase(name)) {
            addressSettings.setEnableMetrics(XMLUtil.parseBoolean(child));
         } else if (ENABLE_INGRESS_TIMESTAMP.equalsIgnoreCase(name)) {
            addressSettings.setEnableIngressTimestamp(XMLUtil.parseBoolean(child));
         } else if (ID_CACHE_SIZE.equalsIgnoreCase(name)) {
            addressSettings.setIDCacheSize(GE_ZERO.validate(ID_CACHE_SIZE, XMLUtil.parseInt(child)).intValue());
         } else if (INITIAL_QUEUE_BUFFER_SIZE.equalsIgnoreCase(name)) {
            addressSettings.setInitialQueueBufferSize(POSITIVE_POWER_OF_TWO.validate(INITIAL_QUEUE_BUFFER_SIZE, XMLUtil.parseInt(child)).intValue());
         }
      }
      return setting;
   }

   protected ResourceLimitSettings parseResourceLimitSettings(final Node node) {
      ResourceLimitSettings resourceLimitSettings = new ResourceLimitSettings();

      resourceLimitSettings.setMatch(SimpleString.of(getAttributeValue(node, "match")));

      NodeList children = node.getChildNodes();

      for (int i = 0; i < children.getLength(); i++) {
         final Node child = children.item(i);
         final String name = child.getNodeName();
         if (MAX_CONNECTIONS_NODE_NAME.equalsIgnoreCase(name)) {
            resourceLimitSettings.setMaxConnections(XMLUtil.parseInt(child));
         } else if (MAX_QUEUES_NODE_NAME.equalsIgnoreCase(name)) {
            resourceLimitSettings.setMaxQueues(XMLUtil.parseInt(child));
         }
      }
      return resourceLimitSettings;
   }

   protected QueueConfiguration parseQueueConfiguration(final Node node) {
      String name = getAttributeValue(node, "name");
      String address = null;
      String filterString = null;
      boolean durable = true;
      Integer maxConsumers = null;
      boolean purgeOnNoConsumers = ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers();
      String user = null;
      Boolean exclusive = null;
      Boolean groupRebalance = null;
      Boolean groupRebalancePauseDispatch = null;
      Integer groupBuckets = null;
      String groupFirstKey = null;
      Boolean lastValue = null;
      String lastValueKey = null;
      Boolean nonDestructive = null;
      Integer consumersBeforeDispatch = null;
      Long delayBeforeDispatch = null;
      Boolean enabled = null;
      Long ringSize = ActiveMQDefaultConfiguration.getDefaultRingSize();

      NamedNodeMap attributes = node.getAttributes();
      for (int i = 0; i < attributes.getLength(); i++) {
         Node item = attributes.item(i);
         if (item.getNodeName().equals("max-consumers")) {
            maxConsumers = MAX_QUEUE_CONSUMERS.validate(name, Integer.parseInt(item.getNodeValue()));
         } else if (item.getNodeName().equals("purge-on-no-consumers")) {
            purgeOnNoConsumers = Boolean.parseBoolean(item.getNodeValue());
         } else if (item.getNodeName().equals("exclusive")) {
            exclusive = Boolean.parseBoolean(item.getNodeValue());
         } else if (item.getNodeName().equals("group-rebalance")) {
            groupRebalance = Boolean.parseBoolean(item.getNodeValue());
         } else if (item.getNodeName().equals("group-rebalance-pause-dispatch")) {
            groupRebalancePauseDispatch = Boolean.parseBoolean(item.getNodeValue());
         } else if (item.getNodeName().equals("group-buckets")) {
            groupBuckets = Integer.parseInt(item.getNodeValue());
         } else if (item.getNodeName().equals("group-first-key")) {
            groupFirstKey = item.getNodeValue();
         } else if (item.getNodeName().equals("last-value")) {
            lastValue = Boolean.parseBoolean(item.getNodeValue());
         } else if (item.getNodeName().equals("last-value-key")) {
            lastValueKey = item.getNodeValue();
         } else if (item.getNodeName().equals("non-destructive")) {
            nonDestructive = Boolean.parseBoolean(item.getNodeValue());
         } else if (item.getNodeName().equals("consumers-before-dispatch")) {
            consumersBeforeDispatch = Integer.parseInt(item.getNodeValue());
         } else if (item.getNodeName().equals("delay-before-dispatch")) {
            delayBeforeDispatch = Long.parseLong(item.getNodeValue());
         } else if (item.getNodeName().equals("enabled")) {
            enabled = Boolean.parseBoolean(item.getNodeValue());
         } else if (item.getNodeName().equals("ring-size")) {
            ringSize = Long.parseLong(item.getNodeValue());
         }
      }

      NodeList children = node.getChildNodes();
      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("address")) {
            address = getTrimmedTextContent(child);
         } else if (child.getNodeName().equals("filter")) {
            filterString = getAttributeValue(child, "string");
         } else if (child.getNodeName().equals("durable")) {
            durable = XMLUtil.parseBoolean(child);
         } else if (child.getNodeName().equals("user")) {
            user = getTrimmedTextContent(child);
         }
      }

      return QueueConfiguration.of(name)
              .setAddress(address)
              .setFilterString(filterString)
              .setDurable(durable)
              .setMaxConsumers(maxConsumers)
              .setPurgeOnNoConsumers(purgeOnNoConsumers)
              .setUser(user)
              .setExclusive(exclusive)
              .setGroupRebalance(groupRebalance)
              .setGroupRebalancePauseDispatch(groupRebalancePauseDispatch)
              .setGroupBuckets(groupBuckets)
              .setGroupFirstKey(groupFirstKey)
              .setLastValue(lastValue)
              .setLastValueKey(lastValueKey)
              .setNonDestructive(nonDestructive)
              .setConsumersBeforeDispatch(consumersBeforeDispatch)
              .setDelayBeforeDispatch(delayBeforeDispatch)
              .setEnabled(enabled)
              .setRingSize(ringSize);
   }

   protected CoreAddressConfiguration parseAddressConfiguration(final Node node) {
      CoreAddressConfiguration addressConfiguration = new CoreAddressConfiguration();

      String name = getAttributeValue(node, "name");
      addressConfiguration.setName(name);

      List<QueueConfiguration> queueConfigurations = new ArrayList<>();
      NodeList children = node.getChildNodes();
      if (children.getLength() == 0) {
         throw ActiveMQMessageBundle.BUNDLE.addressWithNoRoutingType(name);
      }
      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);
         if (child.getNodeName().equals("multicast")) {
            addressConfiguration.addRoutingType(RoutingType.MULTICAST);
            queueConfigurations.addAll(parseQueueConfigurations((Element) child, RoutingType.MULTICAST));
         } else if (child.getNodeName().equals("anycast")) {
            addressConfiguration.addRoutingType(RoutingType.ANYCAST);
            queueConfigurations.addAll(parseQueueConfigurations((Element) child, RoutingType.ANYCAST));
         }
      }

      for (QueueConfiguration queueConfiguration : queueConfigurations) {
         queueConfiguration.setAddress(name);
      }

      addressConfiguration.setQueueConfigs(queueConfigurations);
      return addressConfiguration;
   }

   private TransportConfiguration parseAcceptorTransportConfiguration(final Element e,
                                                                      final Configuration mainConfig) throws Exception {
      Node nameNode = e.getAttributes().getNamedItem("name");

      String name = nameNode != null ? nameNode.getNodeValue() : null;

      String uri = e.getChildNodes().item(0).getNodeValue();

      List<TransportConfiguration> configurations = ConfigurationUtils.parseAcceptorURI(name, uri);

      Map<String, Object> params = configurations.get(0).getParams();

      if (mainConfig.isMaskPassword() != null) {
         params.put(ActiveMQDefaultConfiguration.getPropMaskPassword(), mainConfig.isMaskPassword());

         if (mainConfig.isMaskPassword() && mainConfig.getPasswordCodec() != null) {
            params.put(ActiveMQDefaultConfiguration.getPropPasswordCodec(), mainConfig.getPasswordCodec());
         }
      }

      return configurations.get(0);
   }

   private TransportConfiguration parseConnectorTransportConfiguration(final Element e,
                                                                       final Configuration mainConfig) throws Exception {
      Node nameNode = e.getAttributes().getNamedItem("name");

      String name = nameNode != null ? nameNode.getNodeValue() : null;

      String uri = e.getChildNodes().item(0).getNodeValue();

      List<TransportConfiguration> configurations = ConfigurationUtils.parseConnectorURI(name, uri);

      Map<String, Object> params = configurations.get(0).getParams();

      if (mainConfig.isMaskPassword() != null) {
         params.put(ActiveMQDefaultConfiguration.getPropMaskPassword(), mainConfig.isMaskPassword());

         if (mainConfig.isMaskPassword() && mainConfig.getPasswordCodec() != null) {
            params.put(ActiveMQDefaultConfiguration.getPropPasswordCodec(), mainConfig.getPasswordCodec());
         }
      }

      return configurations.get(0);
   }

   private static final List<String> HA_LIST = new ArrayList<>();

   static {
      HA_LIST.add("live-only");
      HA_LIST.add("primary-only");
      HA_LIST.add("shared-store");
      HA_LIST.add("replication");
   }

   private static final List<String> STORE_TYPE_LIST = new ArrayList<>();

   static {
      STORE_TYPE_LIST.add("database-store");
      STORE_TYPE_LIST.add("file-store");
   }

   private void parseStoreConfiguration(final Element e, final Configuration mainConfig) throws Exception {
      for (String storeType : STORE_TYPE_LIST) {
         NodeList storeNodeList = e.getElementsByTagName(storeType);
         if (storeNodeList.getLength() > 0) {
            Element storeNode = (Element) storeNodeList.item(0);
            if (storeNode.getTagName().equals("database-store")) {
               mainConfig.setStoreConfiguration(createDatabaseStoreConfig(storeNode, mainConfig));
            } else if (storeNode.getTagName().equals("file-store")) {
               mainConfig.setStoreConfiguration(createFileStoreConfig(storeNode));
            }
         }
      }
   }

   private void parseHAPolicyConfiguration(final Element e, final Configuration mainConfig) {
      for (String haType : HA_LIST) {
         NodeList haNodeList = e.getElementsByTagName(haType);
         if (haNodeList.getLength() > 0) {
            Element haNode = (Element) haNodeList.item(0);
            if (haNode.getTagName().equals("replication")) {
               // check for "colocated" first because it will contain primary/master & backup/slave elements of its own
               NodeList colocatedNodeList = e.getElementsByTagName("colocated");
               if (colocatedNodeList.getLength() > 0) {
                  Element colocatedNode = (Element) colocatedNodeList.item(0);
                  mainConfig.setHAPolicyConfiguration(createColocatedHaPolicy(colocatedNode, true));
                  return;
               }
               NodeList primaryNodeList = e.getElementsByTagName("master");
               if (primaryNodeList.getLength() > 0) {
                  ActiveMQServerLogger.LOGGER.deprecatedConfigurationOption("master", "primary");
                  mainConfig.setHAPolicyConfiguration(createReplicationPrimaryHaPolicy((Element) primaryNodeList.item(0)));
                  return;
               }
               NodeList backupNodeList = e.getElementsByTagName("slave");
               if (backupNodeList.getLength() > 0) {
                  ActiveMQServerLogger.LOGGER.deprecatedConfigurationOption("slave", "backup");
                  mainConfig.setHAPolicyConfiguration(createReplicationBackupHaPolicy((Element) backupNodeList.item(0)));
                  return;
               }
               primaryNodeList = e.getElementsByTagName("primary");
               if (primaryNodeList.getLength() > 0) {
                  Element primaryNode = (Element) primaryNodeList.item(0);
                  if (primaryNode.getElementsByTagName("manager").item(0) != null) {
                     mainConfig.setHAPolicyConfiguration(createPluggableReplicationPrimaryHaPolicy(primaryNode));
                     return;
                  } else {
                     mainConfig.setHAPolicyConfiguration(createReplicationPrimaryHaPolicy(primaryNode));
                     return;
                  }
               }
               backupNodeList = e.getElementsByTagName("backup");
               if (backupNodeList.getLength() > 0) {
                  Element backupNode = (Element) backupNodeList.item(0);
                  if (backupNode.getElementsByTagName("manager").item(0) != null) {
                     mainConfig.setHAPolicyConfiguration(createPluggableReplicationBackupHaPolicy(backupNode));
                     return;
                  } else {
                     mainConfig.setHAPolicyConfiguration(createReplicationBackupHaPolicy(backupNode));
                     return;
                  }
               }
            } else if (haNode.getTagName().equals("shared-store")) {
               // check for "colocated" first because it will contain primary/master & backup/slave elements of its own
               NodeList colocatedNodeList = e.getElementsByTagName("colocated");
               if (colocatedNodeList.getLength() > 0) {
                  Element colocatedNode = (Element) colocatedNodeList.item(0);
                  mainConfig.setHAPolicyConfiguration(createColocatedHaPolicy(colocatedNode, false));
                  return;
               }
               NodeList primaryNodeList = e.getElementsByTagName("master");
               if (primaryNodeList.getLength() > 0) {
                  ActiveMQServerLogger.LOGGER.deprecatedConfigurationOption("master", "primary");
                  mainConfig.setHAPolicyConfiguration(createSharedStorePrimaryHaPolicy((Element) primaryNodeList.item(0)));
                  return;
               }
               NodeList backupNodeList = e.getElementsByTagName("slave");
               if (backupNodeList.getLength() > 0) {
                  ActiveMQServerLogger.LOGGER.deprecatedConfigurationOption("slave", "backup");
                  mainConfig.setHAPolicyConfiguration(createSharedStoreBackupHaPolicy((Element) backupNodeList.item(0)));
                  return;
               }
               primaryNodeList = e.getElementsByTagName("primary");
               if (primaryNodeList.getLength() > 0) {
                  mainConfig.setHAPolicyConfiguration(createSharedStorePrimaryHaPolicy((Element) primaryNodeList.item(0)));
                  return;
               }
               backupNodeList = e.getElementsByTagName("backup");
               if (backupNodeList.getLength() > 0) {
                  mainConfig.setHAPolicyConfiguration(createSharedStoreBackupHaPolicy((Element) backupNodeList.item(0)));
                  return;
               }
            } else if (haNode.getTagName().equals("live-only")) {
               NodeList noneNodeList = e.getElementsByTagName("live-only");
               Element noneNode = (Element) noneNodeList.item(0);
               mainConfig.setHAPolicyConfiguration(createPrimaryOnlyHaPolicy(noneNode));
            } else if (haNode.getTagName().equals("primary-only")) {
               NodeList noneNodeList = e.getElementsByTagName("primary-only");
               Element noneNode = (Element) noneNodeList.item(0);
               mainConfig.setHAPolicyConfiguration(createPrimaryOnlyHaPolicy(noneNode));
            }
         }
      }
   }

   private PrimaryOnlyPolicyConfiguration createPrimaryOnlyHaPolicy(Element policyNode) {
      PrimaryOnlyPolicyConfiguration configuration = new PrimaryOnlyPolicyConfiguration();

      configuration.setScaleDownConfiguration(parseScaleDownConfig(policyNode));

      return configuration;
   }

   private ReplicatedPolicyConfiguration createReplicationPrimaryHaPolicy(Element policyNode) {
      ReplicatedPolicyConfiguration configuration = new ReplicatedPolicyConfiguration();
      configuration.setQuorumVoteWait(getInteger(policyNode, "quorum-vote-wait", ActiveMQDefaultConfiguration.getDefaultQuorumVoteWait(), GT_ZERO));
      final String checkForLiveServer = "check-for-live-server";
      final String checkForActiveServer = "check-for-active-server";
      if (policyNode.getElementsByTagName(checkForLiveServer).getLength() > 0) {
         ActiveMQServerLogger.LOGGER.deprecatedConfigurationOption(checkForLiveServer, checkForActiveServer);
         configuration.setCheckForActiveServer(getBoolean(policyNode, checkForLiveServer, configuration.isCheckForActiveServer()));
      } else {
         configuration.setCheckForActiveServer(getBoolean(policyNode, checkForActiveServer, configuration.isCheckForActiveServer()));
      }
      configuration.setGroupName(getString(policyNode, "group-name", configuration.getGroupName(), NO_CHECK));
      configuration.setClusterName(getString(policyNode, "cluster-name", configuration.getClusterName(), NO_CHECK));
      configuration.setMaxSavedReplicatedJournalsSize(getInteger(policyNode, "max-saved-replicated-journals-size", configuration.getMaxSavedReplicatedJournalsSize(), MINUS_ONE_OR_GE_ZERO));
      configuration.setInitialReplicationSyncTimeout(getLong(policyNode, "initial-replication-sync-timeout", configuration.getInitialReplicationSyncTimeout(), GT_ZERO));
      configuration.setVoteOnReplicationFailure(getBoolean(policyNode, "vote-on-replication-failure", configuration.getVoteOnReplicationFailure()));
      configuration.setVoteRetries(getInteger(policyNode, "vote-retries", configuration.getVoteRetries(), MINUS_ONE_OR_GE_ZERO));
      configuration.setVoteRetryWait(getLong(policyNode, "vote-retry-wait", configuration.getVoteRetryWait(), GT_ZERO));
      configuration.setRetryReplicationWait(getLong(policyNode, "retry-replication-wait", configuration.getVoteRetryWait(), GT_ZERO));
      configuration.setQuorumSize(getInteger(policyNode, "quorum-size", configuration.getQuorumSize(), MINUS_ONE_OR_GT_ZERO));
      return configuration;
   }

   private ReplicaPolicyConfiguration createReplicationBackupHaPolicy(Element policyNode) {
      ReplicaPolicyConfiguration configuration = new ReplicaPolicyConfiguration();
      configuration.setQuorumVoteWait(getInteger(policyNode, "quorum-vote-wait", ActiveMQDefaultConfiguration.getDefaultQuorumVoteWait(), GT_ZERO));
      configuration.setRestartBackup(getBoolean(policyNode, "restart-backup", configuration.isRestartBackup()));
      configuration.setGroupName(getString(policyNode, "group-name", configuration.getGroupName(), NO_CHECK));
      configuration.setAllowFailBack(getBoolean(policyNode, "allow-failback", configuration.isAllowFailBack()));
      configuration.setInitialReplicationSyncTimeout(getLong(policyNode, "initial-replication-sync-timeout", configuration.getInitialReplicationSyncTimeout(), GT_ZERO));
      configuration.setClusterName(getString(policyNode, "cluster-name", configuration.getClusterName(), NO_CHECK));
      configuration.setMaxSavedReplicatedJournalsSize(getInteger(policyNode, "max-saved-replicated-journals-size", configuration.getMaxSavedReplicatedJournalsSize(), MINUS_ONE_OR_GE_ZERO));
      configuration.setScaleDownConfiguration(parseScaleDownConfig(policyNode));
      configuration.setVoteOnReplicationFailure(getBoolean(policyNode, "vote-on-replication-failure", configuration.getVoteOnReplicationFailure()));
      configuration.setVoteRetries(getInteger(policyNode, "vote-retries", configuration.getVoteRetries(), MINUS_ONE_OR_GE_ZERO));
      configuration.setVoteRetryWait(getLong(policyNode, "vote-retry-wait", configuration.getVoteRetryWait(), GT_ZERO));
      configuration.setRetryReplicationWait(getLong(policyNode, "retry-replication-wait", configuration.getVoteRetryWait(), GT_ZERO));
      configuration.setQuorumSize(getInteger(policyNode, "quorum-size", configuration.getQuorumSize(), MINUS_ONE_OR_GT_ZERO));
      return configuration;
   }

   private ReplicationPrimaryPolicyConfiguration createPluggableReplicationPrimaryHaPolicy(Element policyNode) {
      ReplicationPrimaryPolicyConfiguration configuration = ReplicationPrimaryPolicyConfiguration.withDefault();
      configuration.setGroupName(getString(policyNode, "group-name", configuration.getGroupName(), NO_CHECK));
      configuration.setClusterName(getString(policyNode, "cluster-name", configuration.getClusterName(), NO_CHECK));
      configuration.setInitialReplicationSyncTimeout(getLong(policyNode, "initial-replication-sync-timeout", configuration.getInitialReplicationSyncTimeout(), GT_ZERO));
      configuration.setRetryReplicationWait(getLong(policyNode, "retry-replication-wait", configuration.getRetryReplicationWait(), GT_ZERO));
      configuration.setDistributedManagerConfiguration(createDistributedLockManagerConfiguration(policyNode));
      configuration.setCoordinationId(getString(policyNode, "coordination-id", configuration.getCoordinationId(), NOT_NULL_OR_EMPTY));
      configuration.setMaxSavedReplicatedJournalsSize(getInteger(policyNode, "max-saved-replicated-journals-size", configuration.getMaxSavedReplicatedJournalsSize(), MINUS_ONE_OR_GE_ZERO));
      return configuration;
   }

   private ReplicationBackupPolicyConfiguration createPluggableReplicationBackupHaPolicy(Element policyNode) {
      ReplicationBackupPolicyConfiguration configuration = ReplicationBackupPolicyConfiguration.withDefault();
      configuration.setGroupName(getString(policyNode, "group-name", configuration.getGroupName(), NO_CHECK));
      configuration.setAllowFailBack(getBoolean(policyNode, "allow-failback", configuration.isAllowFailBack()));
      configuration.setInitialReplicationSyncTimeout(getLong(policyNode, "initial-replication-sync-timeout", configuration.getInitialReplicationSyncTimeout(), GT_ZERO));
      configuration.setClusterName(getString(policyNode, "cluster-name", configuration.getClusterName(), NO_CHECK));
      configuration.setMaxSavedReplicatedJournalsSize(getInteger(policyNode, "max-saved-replicated-journals-size", configuration.getMaxSavedReplicatedJournalsSize(), MINUS_ONE_OR_GE_ZERO));
      configuration.setRetryReplicationWait(getLong(policyNode, "retry-replication-wait", configuration.getRetryReplicationWait(), GT_ZERO));
      configuration.setDistributedManagerConfiguration(createDistributedLockManagerConfiguration(policyNode));
      return configuration;
   }

   private DistributedLockManagerConfiguration createDistributedLockManagerConfiguration(Element policyNode) {
      final Element managerNode = (Element) policyNode.getElementsByTagName("manager").item(0);
      final String className = getString(managerNode, "class-name",
                                         ActiveMQDefaultConfiguration.getDefaultDistributedLockManagerClassName(),
                                         NO_CHECK);
      final Map<String, String> properties;
      if (parameterExists(managerNode, "properties")) {
         final NodeList propertyNodeList = managerNode.getElementsByTagName("property");
         final int propertiesCount = propertyNodeList.getLength();
         properties = new HashMap<>(propertiesCount);
         for (int i = 0; i < propertiesCount; i++) {
            final Element propertyNode = (Element) propertyNodeList.item(i);
            final String propertyName = propertyNode.getAttributeNode("key").getValue();
            final String propertyValue = propertyNode.getAttributeNode("value").getValue();
            properties.put(propertyName, propertyValue);
         }
      } else {
         properties = new HashMap<>(1);
      }
      return new DistributedLockManagerConfiguration(className, properties);
   }

   private SharedStorePrimaryPolicyConfiguration createSharedStorePrimaryHaPolicy(Element policyNode) {
      SharedStorePrimaryPolicyConfiguration configuration = new SharedStorePrimaryPolicyConfiguration();

      configuration.setFailoverOnServerShutdown(getBoolean(policyNode, "failover-on-shutdown", configuration.isFailoverOnServerShutdown()));
      configuration.setWaitForActivation(getBoolean(policyNode, "wait-for-activation", configuration.isWaitForActivation()));

      return configuration;
   }

   private SharedStoreBackupPolicyConfiguration createSharedStoreBackupHaPolicy(Element policyNode) {
      SharedStoreBackupPolicyConfiguration configuration = new SharedStoreBackupPolicyConfiguration();

      configuration.setAllowFailBack(getBoolean(policyNode, "allow-failback", configuration.isAllowFailBack()));

      configuration.setFailoverOnServerShutdown(getBoolean(policyNode, "failover-on-shutdown", configuration.isFailoverOnServerShutdown()));

      configuration.setRestartBackup(getBoolean(policyNode, "restart-backup", configuration.isRestartBackup()));

      configuration.setScaleDownConfiguration(parseScaleDownConfig(policyNode));

      return configuration;
   }

   private ColocatedPolicyConfiguration createColocatedHaPolicy(Element policyNode, boolean replicated) {
      ColocatedPolicyConfiguration configuration = new ColocatedPolicyConfiguration();

      boolean requestBackup = getBoolean(policyNode, "request-backup", configuration.isRequestBackup());

      configuration.setRequestBackup(requestBackup);

      int backupRequestRetries = getInteger(policyNode, "backup-request-retries", configuration.getBackupRequestRetries(), MINUS_ONE_OR_GE_ZERO);

      configuration.setBackupRequestRetries(backupRequestRetries);

      long backupRequestRetryInterval = getLong(policyNode, "backup-request-retry-interval", configuration.getBackupRequestRetryInterval(), GT_ZERO);

      configuration.setBackupRequestRetryInterval(backupRequestRetryInterval);

      int maxBackups = getInteger(policyNode, "max-backups", configuration.getMaxBackups(), GE_ZERO);

      configuration.setMaxBackups(maxBackups);

      int backupPortOffset = getInteger(policyNode, "backup-port-offset", configuration.getBackupPortOffset(), GT_ZERO);

      configuration.setBackupPortOffset(backupPortOffset);

      NodeList remoteConnectorNode = policyNode.getElementsByTagName("excludes");

      if (remoteConnectorNode != null && remoteConnectorNode.getLength() > 0) {
         NodeList remoteConnectors = remoteConnectorNode.item(0).getChildNodes();
         for (int i = 0; i < remoteConnectors.getLength(); i++) {
            Node child = remoteConnectors.item(i);
            if (child.getNodeName().equals("connector-ref")) {
               String connectorName = getTrimmedTextContent(child);
               configuration.getExcludedConnectors().add(connectorName);
            }
         }
      }

      NodeList primaryNodeList = policyNode.getElementsByTagName("master");
      if (primaryNodeList.getLength() > 0) {
         ActiveMQServerLogger.LOGGER.deprecatedConfigurationOption("master", "primary");
         Element primaryNode = (Element) primaryNodeList.item(0);
         configuration.setPrimaryConfig(replicated ? createReplicationPrimaryHaPolicy(primaryNode) : createSharedStorePrimaryHaPolicy(primaryNode));
      }
      NodeList backupNodeList = policyNode.getElementsByTagName("slave");
      if (backupNodeList.getLength() > 0) {
         ActiveMQServerLogger.LOGGER.deprecatedConfigurationOption("slave", "backup");
         Element backupNode = (Element) backupNodeList.item(0);
         configuration.setBackupConfig(replicated ? createReplicationBackupHaPolicy(backupNode) : createSharedStoreBackupHaPolicy(backupNode));
      }

      primaryNodeList = policyNode.getElementsByTagName("primary");
      if (primaryNodeList.getLength() > 0) {
         Element primaryNode = (Element) primaryNodeList.item(0);
         configuration.setPrimaryConfig(replicated ? createReplicationPrimaryHaPolicy(primaryNode) : createSharedStorePrimaryHaPolicy(primaryNode));
      }
      backupNodeList = policyNode.getElementsByTagName("backup");
      if (backupNodeList.getLength() > 0) {
         Element backupNode = (Element) backupNodeList.item(0);
         configuration.setBackupConfig(replicated ? createReplicationBackupHaPolicy(backupNode) : createSharedStoreBackupHaPolicy(backupNode));
      }

      return configuration;
   }

   private ScaleDownConfiguration parseScaleDownConfig(Element policyNode) {
      NodeList scaleDownNode = policyNode.getElementsByTagName("scale-down");

      if (scaleDownNode.getLength() > 0) {
         ScaleDownConfiguration scaleDownConfiguration = new ScaleDownConfiguration();

         Element scaleDownElement = (Element) scaleDownNode.item(0);

         scaleDownConfiguration.setEnabled(getBoolean(scaleDownElement, "enabled", scaleDownConfiguration.isEnabled()));

         scaleDownConfiguration.setCommitInterval(getInteger(scaleDownElement, "commit-interval", scaleDownConfiguration.getCommitInterval(), MINUS_ONE_OR_GT_ZERO));

         NodeList discoveryGroupRef = scaleDownElement.getElementsByTagName("discovery-group-ref");

         if (discoveryGroupRef.item(0) != null) {
            scaleDownConfiguration.setDiscoveryGroup(discoveryGroupRef.item(0).getAttributes().getNamedItem("discovery-group-name").getNodeValue());
         }

         String scaleDownGroupName = getString(scaleDownElement, "group-name", scaleDownConfiguration.getGroupName(), NO_CHECK);

         scaleDownConfiguration.setGroupName(scaleDownGroupName);

         NodeList scaleDownConnectorNode = scaleDownElement.getElementsByTagName("connectors");

         if (scaleDownConnectorNode != null && scaleDownConnectorNode.getLength() > 0) {
            NodeList scaleDownConnectors = scaleDownConnectorNode.item(0).getChildNodes();
            for (int i = 0; i < scaleDownConnectors.getLength(); i++) {
               Node child = scaleDownConnectors.item(i);
               if (child.getNodeName().equals("connector-ref")) {
                  String connectorName = getTrimmedTextContent(child);

                  scaleDownConfiguration.getConnectors().add(connectorName);
               }
            }
         }
         return scaleDownConfiguration;
      }
      return null;
   }

   private DatabaseStorageConfiguration createDatabaseStoreConfig(Element storeNode, Configuration mainConfig) throws Exception {
      DatabaseStorageConfiguration conf = new DatabaseStorageConfiguration();
      conf.setBindingsTableName(getString(storeNode, "bindings-table-name", conf.getBindingsTableName(), NO_CHECK));
      conf.setMessageTableName(getString(storeNode, "message-table-name", conf.getMessageTableName(), NO_CHECK));
      conf.setLargeMessageTableName(getString(storeNode, "large-message-table-name", conf.getLargeMessageTableName(), NO_CHECK));
      conf.setPageStoreTableName(getString(storeNode, "page-store-table-name", conf.getPageStoreTableName(), NO_CHECK));
      conf.setNodeManagerStoreTableName(getString(storeNode, "node-manager-store-table-name", conf.getNodeManagerStoreTableName(), NO_CHECK));
      String jdbcConnectionUrl = getString(storeNode, "jdbc-connection-url", conf.getJdbcConnectionUrl(), NO_CHECK);
      if (jdbcConnectionUrl != null && !jdbcConnectionUrl.startsWith("jdbc:")) {
         jdbcConnectionUrl = PasswordMaskingUtil.resolveMask(jdbcConnectionUrl, mainConfig.getPasswordCodec());
      }
      conf.setJdbcConnectionUrl(jdbcConnectionUrl);
      conf.setJdbcDriverClassName(getString(storeNode, "jdbc-driver-class-name", conf.getJdbcDriverClassName(), NO_CHECK));
      conf.setJdbcNetworkTimeout(getInteger(storeNode, "jdbc-network-timeout", conf.getJdbcNetworkTimeout(), NO_CHECK));
      conf.setJdbcLockRenewPeriodMillis(getLong(storeNode, "jdbc-lock-renew-period", conf.getJdbcLockRenewPeriodMillis(), NO_CHECK));
      conf.setJdbcLockExpirationMillis(getLong(storeNode, "jdbc-lock-expiration", conf.getJdbcLockExpirationMillis(), NO_CHECK));
      conf.setJdbcJournalSyncPeriodMillis(getLong(storeNode, "jdbc-journal-sync-period", conf.getJdbcJournalSyncPeriodMillis(), NO_CHECK));
      conf.setJdbcAllowedTimeDiff(getLong(storeNode, "jdbc-allowed-time-diff", conf.getJdbcAllowedTimeDiff(), NO_CHECK));
      conf.setMaxPageSizeBytes(getTextBytesAsIntBytes(storeNode, "jdbc-max-page-size-bytes", conf.getMaxPageSizeBytes(), NO_CHECK));
      String jdbcUser = getString(storeNode, "jdbc-user", conf.getJdbcUser(), NO_CHECK);
      if (jdbcUser != null) {
         jdbcUser = PasswordMaskingUtil.resolveMask(mainConfig.isMaskPassword(), jdbcUser, mainConfig.getPasswordCodec());
      }
      conf.setJdbcUser(jdbcUser);
      String password = getString(storeNode, "jdbc-password", conf.getJdbcPassword(), NO_CHECK);
      if (password != null) {
         password = PasswordMaskingUtil.resolveMask(mainConfig.isMaskPassword(), password, mainConfig.getPasswordCodec());
      }
      conf.setJdbcPassword(password);
      conf.setDataSourceClassName(getString(storeNode, "data-source-class-name", conf.getDataSourceClassName(), NO_CHECK));
      if (parameterExists(storeNode, "data-source-properties")) {
         NodeList propertyNodeList = storeNode.getElementsByTagName("data-source-property");
         for (int i = 0; i < propertyNodeList.getLength(); i++) {
            Element propertyNode = (Element) propertyNodeList.item(i);
            final String propertyName = propertyNode.getAttributeNode("key").getValue();
            String propertyValue = propertyNode.getAttributeNode("value").getValue();
            if (propertyValue != null && PasswordMaskingUtil.isEncMasked(propertyValue)) {
               propertyValue = PasswordMaskingUtil.resolveMask(mainConfig.isMaskPassword(), propertyValue, mainConfig.getPasswordCodec());
            }
            conf.addDataSourceProperty(propertyName, propertyValue);
         }
      }

      return conf;
   }

   private FileStorageConfiguration createFileStoreConfig(Element storeNode) {
      return new FileStorageConfiguration();
   }

   private void parseBroadcastGroupConfiguration(final Element e, final Configuration mainConfig) {
      String name = e.getAttribute("name");

      List<String> connectorNames = new ArrayList<>();

      NodeList children = e.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("connector-ref")) {
            String connectorName = getString(e, "connector-ref", null, NOT_NULL_OR_EMPTY);

            connectorNames.add(connectorName);
         }
      }

      long broadcastPeriod = getLong(e, "broadcast-period", ActiveMQDefaultConfiguration.getDefaultBroadcastPeriod(), GT_ZERO);

      String localAddress = getString(e, "local-bind-address", null, NO_CHECK);

      int localBindPort = getInteger(e, "local-bind-port", -1, MINUS_ONE_OR_GT_ZERO);

      String groupAddress = getString(e, "group-address", null, NO_CHECK);

      int groupPort = getInteger(e, "group-port", -1, MINUS_ONE_OR_GT_ZERO);

      String jgroupsFile = getString(e, "jgroups-file", null, NO_CHECK);

      String jgroupsChannel = getString(e, "jgroups-channel", null, NO_CHECK);

      // TODO: validate if either jgroups or UDP is being filled

      BroadcastEndpointFactory endpointFactory;

      if (jgroupsFile != null) {
         endpointFactory = new JGroupsFileBroadcastEndpointFactory().setFile(jgroupsFile).setChannelName(jgroupsChannel);
      } else {
         endpointFactory = new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(groupPort).setLocalBindAddress(localAddress).setLocalBindPort(localBindPort);
      }

      BroadcastGroupConfiguration config = new BroadcastGroupConfiguration().setName(name).setBroadcastPeriod(broadcastPeriod).setConnectorInfos(connectorNames).setEndpointFactory(endpointFactory);

      mainConfig.getBroadcastGroupConfigurations().add(config);
   }

   private void parseDiscoveryGroupConfiguration(final Element e, final Configuration mainConfig) {
      String name = e.getAttribute("name");

      long discoveryInitialWaitTimeout = getLong(e, "initial-wait-timeout", ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT, GT_ZERO);

      long refreshTimeout = getLong(e, "refresh-timeout", ActiveMQDefaultConfiguration.getDefaultBroadcastRefreshTimeout(), GT_ZERO);

      String localBindAddress = getString(e, "local-bind-address", null, NO_CHECK);

      int localBindPort = getInteger(e, "local-bind-port", -1, MINUS_ONE_OR_GT_ZERO);

      String groupAddress = getString(e, "group-address", null, NO_CHECK);

      int groupPort = getInteger(e, "group-port", -1, MINUS_ONE_OR_GT_ZERO);

      String jgroupsFile = getString(e, "jgroups-file", null, NO_CHECK);

      String jgroupsChannel = getString(e, "jgroups-channel", null, NO_CHECK);

      // TODO: validate if either jgroups or UDP is being filled
      BroadcastEndpointFactory endpointFactory;
      if (jgroupsFile != null) {
         endpointFactory = new JGroupsFileBroadcastEndpointFactory().setFile(jgroupsFile).setChannelName(jgroupsChannel);
      } else {
         endpointFactory = new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(groupPort).setLocalBindAddress(localBindAddress).setLocalBindPort(localBindPort);
      }

      DiscoveryGroupConfiguration config = new DiscoveryGroupConfiguration().setName(name).setRefreshTimeout(refreshTimeout).setDiscoveryInitialWaitTimeout(discoveryInitialWaitTimeout).setBroadcastEndpointFactory(endpointFactory);

      if (mainConfig.getDiscoveryGroupConfigurations().containsKey(name)) {
         ActiveMQServerLogger.LOGGER.discoveryGroupAlreadyDeployed(name);

         return;
      } else {
         mainConfig.getDiscoveryGroupConfigurations().put(name, config);
      }
   }

   private void parseClusterConnectionConfigurationURI(final Element e,
                                                       final Configuration mainConfig) throws Exception {
      String name = e.getAttribute("name");

      String uri = e.getAttribute("address");

      ClusterConnectionConfiguration config = mainConfig.addClusterConfiguration(name, uri);

      logger.debug("Adding cluster connection :: {}", config);
   }

   private void parseAMQPBrokerConnections(final Element e,
                                           final Configuration mainConfig) throws Exception {
      String name = e.getAttribute("name");

      String uri = e.getAttribute("uri");

      int retryInterval = getAttributeInteger(e, "retry-interval", 5000, GT_ZERO);
      int reconnectAttempts = getAttributeInteger(e, "reconnect-attempts", -1, MINUS_ONE_OR_GT_ZERO);
      String user = getAttributeValue(e, "user");
      if (user != null && PasswordMaskingUtil.isEncMasked(user)) {
         user = PasswordMaskingUtil.resolveMask(mainConfig.isMaskPassword(), user, mainConfig.getPasswordCodec());
      }
      String password = getAttributeValue(e, "password");
      if (password != null && PasswordMaskingUtil.isEncMasked(password)) {
         password = PasswordMaskingUtil.resolveMask(mainConfig.isMaskPassword(), password, mainConfig.getPasswordCodec());
      }
      boolean autoStart = getBooleanAttribute(e, "auto-start", true);

      getInteger(e, "local-bind-port", -1, MINUS_ONE_OR_GT_ZERO);

      AMQPBrokerConnectConfiguration config = new AMQPBrokerConnectConfiguration(name, uri);
      config.parseURI();
      config.setRetryInterval(retryInterval).setReconnectAttempts(reconnectAttempts).setUser(user).setPassword(password).setAutostart(autoStart);

      mainConfig.addAMQPConnection(config);

      NodeList senderList = e.getChildNodes();
      for (int i = 0; i < senderList.getLength(); i++) {
         if (senderList.item(i).getNodeType() == Node.ELEMENT_NODE) {
            Element e2 = (Element)senderList.item(i);
            AMQPBrokerConnectionAddressType nodeType = AMQPBrokerConnectionAddressType.valueOf(e2.getTagName().toUpperCase());
            AMQPBrokerConnectionElement connectionElement;

            if (nodeType == AMQPBrokerConnectionAddressType.MIRROR) {
               boolean messageAcks = getBooleanAttribute(e2, "message-acknowledgements", true);
               boolean queueCreation = getBooleanAttribute(e2, "queue-creation", true);
               boolean durable = getBooleanAttribute(e2, "durable", true);
               boolean queueRemoval = getBooleanAttribute(e2, "queue-removal", true);
               boolean sync = getBooleanAttribute(e2, "sync", false);
               String addressFilter = getAttributeValue(e2, "address-filter");

               AMQPMirrorBrokerConnectionElement amqpMirrorConnectionElement = new AMQPMirrorBrokerConnectionElement();
               amqpMirrorConnectionElement.setMessageAcknowledgements(messageAcks).setQueueCreation(queueCreation).setQueueRemoval(queueRemoval).setDurable(durable).setAddressFilter(addressFilter).setSync(sync);
               connectionElement = amqpMirrorConnectionElement;
               connectionElement.setType(AMQPBrokerConnectionAddressType.MIRROR);

               final NodeList federationAttrs = e2.getChildNodes();

               for (int j = 0; j < federationAttrs.getLength(); j++) {
                  final Node mirrorElement = federationAttrs.item(j);

                  if (mirrorElement.getNodeName().equals("property")) {
                     amqpMirrorConnectionElement.addProperty(getAttributeValue(mirrorElement, "key"), getAttributeValue(mirrorElement, "value"));
                  }
               }

            } else if (nodeType == AMQPBrokerConnectionAddressType.FEDERATION) {
               final AMQPFederatedBrokerConnectionElement amqpFederationConnectionElement = new AMQPFederatedBrokerConnectionElement(name);
               final NodeList federationAttrs = e2.getChildNodes();

               for (int j = 0; j < federationAttrs.getLength(); j++) {
                  final Node federationPolicy = federationAttrs.item(j);

                  if (federationPolicy.getNodeName().equals("remote-queue-policy")) {
                     amqpFederationConnectionElement.addRemoteQueuePolicy(parseAMQPFederatedFromQueuePolicy((Element) federationPolicy, mainConfig));
                  } else if (federationPolicy.getNodeName().equals("local-queue-policy")) {
                     amqpFederationConnectionElement.addLocalQueuePolicy(parseAMQPFederatedFromQueuePolicy((Element)federationPolicy, mainConfig));
                  } else if (federationPolicy.getNodeName().equals("remote-address-policy")) {
                     amqpFederationConnectionElement.addRemoteAddressPolicy(parseAMQPFederatedFromAddressPolicy((Element)federationPolicy, mainConfig));
                  } else if (federationPolicy.getNodeName().equals("local-address-policy")) {
                     amqpFederationConnectionElement.addLocalAddressPolicy(parseAMQPFederatedFromAddressPolicy((Element)federationPolicy, mainConfig));
                  } else if (federationPolicy.getNodeName().equals("property")) {
                     amqpFederationConnectionElement.addProperty(getAttributeValue(federationPolicy, "key"), getAttributeValue(federationPolicy, "value"));
                  }
               }

               connectionElement = amqpFederationConnectionElement;
               connectionElement.setType(AMQPBrokerConnectionAddressType.FEDERATION);
            } else if (nodeType == AMQPBrokerConnectionAddressType.BRIDGE) {
               final NodeList bridgeAttrs = e2.getChildNodes();
               final String bridgeName = e2.hasAttribute("name") ? e2.getAttribute("name") : name;
               final AMQPBridgeBrokerConnectionElement amqpBridgeConnectionElement = new AMQPBridgeBrokerConnectionElement(bridgeName);

               for (int j = 0; j < bridgeAttrs.getLength(); j++) {
                  final Node bridgePolicy = bridgeAttrs.item(j);

                  if (bridgePolicy.getNodeName().equals("bridge-from-address")) {
                     amqpBridgeConnectionElement.addBridgeFromAddressPolicy(parseAMQPBridgeAddressPolicy((Element) bridgePolicy, mainConfig));
                  } else if (bridgePolicy.getNodeName().equals("bridge-to-address")) {
                     amqpBridgeConnectionElement.addBridgeToAddressPolicy(parseAMQPBridgeAddressPolicy((Element)bridgePolicy, mainConfig));
                  } else if (bridgePolicy.getNodeName().equals("bridge-from-queue")) {
                     amqpBridgeConnectionElement.addBridgeFromQueuePolicy(parseAMQPBridgeQueuePolicy((Element)bridgePolicy, mainConfig));
                  } else if (bridgePolicy.getNodeName().equals("bridge-to-queue")) {
                     amqpBridgeConnectionElement.addBridgeToQueuePolicy(parseAMQPBridgeQueuePolicy((Element)bridgePolicy, mainConfig));
                  } else if (bridgePolicy.getNodeName().equals("property")) {
                     amqpBridgeConnectionElement.addProperty(getAttributeValue(bridgePolicy, "key"), getAttributeValue(bridgePolicy, "value"));
                  }
               }

               connectionElement = amqpBridgeConnectionElement;
               connectionElement.setType(AMQPBrokerConnectionAddressType.BRIDGE);
            } else {
               String match = getAttributeValue(e2, "address-match");
               String queue = getAttributeValue(e2, "queue-name");
               connectionElement = new AMQPBrokerConnectionElement();
               connectionElement.setMatchAddress(SimpleString.of(match)).setType(nodeType);
               connectionElement.setQueueName(SimpleString.of(queue));
            }

            config.addElement(connectionElement);
         }
      }

      logger.debug("Adding AMQP connection :: {}", config);
   }

   private AMQPFederationAddressPolicyElement parseAMQPFederatedFromAddressPolicy(Element policyNod, final Configuration mainConfig) throws Exception {
      AMQPFederationAddressPolicyElement config = new AMQPFederationAddressPolicyElement();
      config.setName(policyNod.getAttribute("name"));

      NamedNodeMap attributes = policyNod.getAttributes();
      for (int i = 0; i < attributes.getLength(); i++) {
         Node item = attributes.item(i);
         if (item.getNodeName().equals("max-hops")) {
            config.setMaxHops(MINUS_ONE_OR_GE_ZERO.validate(item.getNodeName(), Integer.parseInt(item.getNodeValue())).intValue());
         } else if (item.getNodeName().equals("auto-delete")) {
            config.setAutoDelete(Boolean.parseBoolean(item.getNodeValue()));
         } else if (item.getNodeName().equals("auto-delete-delay")) {
            config.setAutoDeleteDelay(GE_ZERO.validate("auto-delete-delay", Long.parseLong(item.getNodeValue())).longValue());
         } else if (item.getNodeName().equals("auto-delete-message-count")) {
            config.setAutoDeleteMessageCount(MINUS_ONE_OR_GE_ZERO.validate("auto-delete-message-count", Long.parseLong(item.getNodeValue())).longValue());
         } else if (item.getNodeName().equals("enable-divert-bindings")) {
            config.setEnableDivertBindings(Boolean.parseBoolean(item.getNodeValue()));
         }
      }

      final NodeList children = policyNod.getChildNodes();

      final String transformerClassName = getString(policyNod, "transformer-class-name", null, NO_CHECK);
      if (transformerClassName != null && !transformerClassName.isEmpty()) {
         config.setTransformerConfiguration(getTransformerConfiguration(transformerClassName));
      }

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("include")) {
            config.addToIncludes(((Element) child).getAttribute("address-match"));
         } else if (child.getNodeName().equals("exclude")) {
            config.addToExcludes(((Element) child).getAttribute("address-match"));
         } else if (child.getNodeName().equals("transformer")) {
            config.setTransformerConfiguration(getTransformerConfiguration(child));
         } else if (child.getNodeName().equals("property")) {
            config.addProperty(getAttributeValue(child, "key"), getAttributeValue(child, "value"));
         }
      }

      return config;
   }

   private AMQPFederationQueuePolicyElement parseAMQPFederatedFromQueuePolicy(Element policyNod, final Configuration mainConfig) throws Exception {
      AMQPFederationQueuePolicyElement config = new AMQPFederationQueuePolicyElement();
      config.setName(policyNod.getAttribute("name"));

      NamedNodeMap attributes = policyNod.getAttributes();
      for (int i = 0; i < attributes.getLength(); i++) {
         Node item = attributes.item(i);
         if (item.getNodeName().equals("include-federated")) {
            config.setIncludeFederated(Boolean.parseBoolean(item.getNodeValue()));
         } else if (item.getNodeName().equals("priority-adjustment")) {
            config.setPriorityAdjustment(Integer.parseInt(item.getNodeValue()));
         }
      }

      final NodeList children = policyNod.getChildNodes();

      final String transformerClassName = getString(policyNod, "transformer-class-name", null, NO_CHECK);
      if (transformerClassName != null && !transformerClassName.isEmpty()) {
         config.setTransformerConfiguration(getTransformerConfiguration(transformerClassName));
      }

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("include")) {
            config.addToIncludes(((Element) child).getAttribute("address-match"), ((Element) child).getAttribute("queue-match"));
         } else if (child.getNodeName().equals("exclude")) {
            config.addToExcludes(((Element) child).getAttribute("address-match"), ((Element) child).getAttribute("queue-match"));
         } else if (child.getNodeName().equals("transformer")) {
            config.setTransformerConfiguration(getTransformerConfiguration(child));
         } else if (child.getNodeName().equals("property")) {
            config.addProperty(getAttributeValue(child, "key"), getAttributeValue(child, "value"));
         }
      }

      return config;
   }

   private AMQPBridgeAddressPolicyElement parseAMQPBridgeAddressPolicy(Element policyNod, final Configuration mainConfig) throws Exception {
      final AMQPBridgeAddressPolicyElement config = new AMQPBridgeAddressPolicyElement();
      config.setName(policyNod.getAttribute("name"));
      final NamedNodeMap attributes = policyNod.getAttributes();

      for (int i = 0; i < attributes.getLength(); i++) {
         final Node item = attributes.item(i);

         if (item.getNodeName().equals("filter")) {
            final String filterString = item.getNodeValue();
            try {
               SelectorParser.parse(filterString);
            } catch (Exception ex) {
               logger.debug("Filter expression in bridge address policy {} has invalid syntax: {}", config.getName(), filterString, ex);
               throw ex;
            }
            config.setFilter(filterString);
         } else if (item.getNodeName().equals("priority")) {
            config.setPriority(Integer.parseInt(item.getNodeValue()));
         } else if (item.getNodeName().equals("enable-divert-bindings")) {
            config.setIncludeDivertBindings(Boolean.parseBoolean(item.getNodeValue()));
         } else if (item.getNodeName().equals("use-durable-subscriptions")) {
            config.setUseDurableSubscriptions(Boolean.parseBoolean(item.getNodeValue()));
         } else if (item.getNodeName().equals("remote-address")) {
            config.setRemoteAddress(item.getNodeValue());
         } else if (item.getNodeName().equals("remote-address-prefix")) {
            config.setRemoteAddressPrefix(item.getNodeValue());
         } else if (item.getNodeName().equals("remote-address-suffix")) {
            config.setRemoteAddressSuffix(item.getNodeValue());
         } else if (item.getNodeName().equals("remote-terminus-capabilities")) {
            final String capabilities = item.getNodeValue();
            if (capabilities != null && !capabilities.isBlank()) {
               config.setRemoteTerminusCapabilities(capabilities.split(","));
            }
         }
      }

      final NodeList children = policyNod.getChildNodes();

      @SuppressWarnings("unchecked")
      final String transformerClassName = getString(policyNod, "transformer-class-name", null, NO_CHECK);
      if (transformerClassName != null && !transformerClassName.isEmpty()) {
         config.setTransformerConfiguration(getTransformerConfiguration(transformerClassName));
      }

      for (int j = 0; j < children.getLength(); j++) {
         final Node child = children.item(j);

         if (child.getNodeName().equals("include")) {
            config.addToIncludes(((Element) child).getAttribute("address-match"));
         } else if (child.getNodeName().equals("exclude")) {
            config.addToExcludes(((Element) child).getAttribute("address-match"));
         } else if (child.getNodeName().equals("transformer")) {
            config.setTransformerConfiguration(getTransformerConfiguration(child));
         } else if (child.getNodeName().equals("property")) {
            config.addProperty(getAttributeValue(child, "key"), getAttributeValue(child, "value"));
         }
      }

      return config;
   }

   private AMQPBridgeQueuePolicyElement parseAMQPBridgeQueuePolicy(Element policyNod, final Configuration mainConfig) throws Exception {
      final AMQPBridgeQueuePolicyElement config = new AMQPBridgeQueuePolicyElement();
      config.setName(policyNod.getAttribute("name"));
      final NamedNodeMap attributes = policyNod.getAttributes();

      for (int i = 0; i < attributes.getLength(); i++) {
         final Node item = attributes.item(i);

         if (item.getNodeName().equals("filter")) {
            final String filterString = item.getNodeValue();
            try {
               SelectorParser.parse(filterString);
            } catch (Exception ex) {
               logger.debug("Filter expression in bridge queue policy {} has invalid syntax: {}", config.getName(), filterString, ex);
               throw ex;
            }
            config.setFilter(filterString);
         } else if (item.getNodeName().equals("priority")) {
            config.setPriority(Integer.parseInt(item.getNodeValue()));
         } else if (item.getNodeName().equals("priority-adjustment")) {
            config.setPriorityAdjustment(Integer.parseInt(item.getNodeValue()));
         } else if (item.getNodeName().equals("remote-address")) {
            config.setRemoteAddress(item.getNodeValue());
         } else if (item.getNodeName().equals("remote-address-prefix")) {
            config.setRemoteAddressPrefix(item.getNodeValue());
         } else if (item.getNodeName().equals("remote-address-suffix")) {
            config.setRemoteAddressSuffix(item.getNodeValue());
         } else if (item.getNodeName().equals("remote-terminus-capabilities")) {
            final String capabilities = item.getNodeValue();
            if (capabilities != null && !capabilities.isBlank()) {
               config.setRemoteTerminusCapabilities(capabilities.split(","));
            }
         }
      }

      final NodeList children = policyNod.getChildNodes();

      @SuppressWarnings("unchecked")
      final String transformerClassName = getString(policyNod, "transformer-class-name", null, NO_CHECK);
      if (transformerClassName != null && !transformerClassName.isEmpty()) {
         config.setTransformerConfiguration(getTransformerConfiguration(transformerClassName));
      }

      for (int j = 0; j < children.getLength(); j++) {
         final Node child = children.item(j);

         if (child.getNodeName().equals("include")) {
            config.addToIncludes(((Element) child).getAttribute("address-match"), ((Element) child).getAttribute("queue-match"));
         } else if (child.getNodeName().equals("exclude")) {
            config.addToExcludes(((Element) child).getAttribute("address-match"), ((Element) child).getAttribute("queue-match"));
         } else if (child.getNodeName().equals("transformer")) {
            config.setTransformerConfiguration(getTransformerConfiguration(child));
         } else if (child.getNodeName().equals("property")) {
            config.addProperty(getAttributeValue(child, "key"), getAttributeValue(child, "value"));
         }
      }

      return config;
   }

   private void parseClusterConnectionConfiguration(final Element e, final Configuration mainConfig) throws Exception {
      String name = e.getAttribute("name");

      String address = getString(e, "address", "", NO_CHECK);

      String connectorName = getString(e, "connector-ref", null, NOT_NULL_OR_EMPTY);

      if (!mainConfig.getConnectorConfigurations().containsKey(connectorName)) {
         ActiveMQServerLogger.LOGGER.connectorRefNotFound(connectorName, name);
         return;
      }

      boolean duplicateDetection = getBoolean(e, "use-duplicate-detection", ActiveMQDefaultConfiguration.isDefaultClusterDuplicateDetection());

      MessageLoadBalancingType messageLoadBalancingType;

      if (parameterExists(e, "forward-when-no-consumers")) {
         boolean forwardWhenNoConsumers = getBoolean(e, "forward-when-no-consumers", ActiveMQDefaultConfiguration.isDefaultClusterForwardWhenNoConsumers());
         if (forwardWhenNoConsumers) {
            messageLoadBalancingType = MessageLoadBalancingType.STRICT;
         } else {
            messageLoadBalancingType = MessageLoadBalancingType.ON_DEMAND;
         }
      } else {

         messageLoadBalancingType = Enum.valueOf(MessageLoadBalancingType.class, getString(e, "message-load-balancing", ActiveMQDefaultConfiguration.getDefaultClusterMessageLoadBalancingType(), MESSAGE_LOAD_BALANCING_TYPE));
      }

      int maxHops = getInteger(e, "max-hops", ActiveMQDefaultConfiguration.getDefaultClusterMaxHops(), GE_ZERO);

      long clientFailureCheckPeriod = getLong(e, "check-period", ActiveMQDefaultConfiguration.getDefaultClusterFailureCheckPeriod(), GT_ZERO);

      long connectionTTL = getLong(e, "connection-ttl", ActiveMQDefaultConfiguration.getDefaultClusterConnectionTtl(), GT_ZERO);

      long retryInterval = getLong(e, "retry-interval", ActiveMQDefaultConfiguration.getDefaultClusterRetryInterval(), GT_ZERO);

      long callTimeout = getLong(e, "call-timeout", ActiveMQDefaultConfiguration.getDefaultClusterCallTimeout(), GT_ZERO);

      long callFailoverTimeout = getLong(e, "call-failover-timeout", ActiveMQDefaultConfiguration.getDefaultClusterCallFailoverTimeout(), MINUS_ONE_OR_GT_ZERO);

      double retryIntervalMultiplier = getDouble(e, "retry-interval-multiplier", ActiveMQDefaultConfiguration.getDefaultClusterRetryIntervalMultiplier(), GT_ZERO);

      int minLargeMessageSize = getTextBytesAsIntBytes(e, "min-large-message-size", ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, POSITIVE_INT);

      long maxRetryInterval = getLong(e, "max-retry-interval", ActiveMQDefaultConfiguration.getDefaultClusterMaxRetryInterval(), GT_ZERO);

      int initialConnectAttempts = getInteger(e, "initial-connect-attempts", ActiveMQDefaultConfiguration.getDefaultClusterInitialConnectAttempts(), MINUS_ONE_OR_GE_ZERO);

      int reconnectAttempts = getInteger(e, "reconnect-attempts", ActiveMQDefaultConfiguration.getDefaultClusterReconnectAttempts(), MINUS_ONE_OR_GE_ZERO);

      int confirmationWindowSize = getTextBytesAsIntBytes(e, "confirmation-window-size", ActiveMQDefaultConfiguration.getDefaultClusterConfirmationWindowSize(), POSITIVE_INT);

      int producerWindowSize = getTextBytesAsIntBytes(e, "producer-window-size", ActiveMQDefaultConfiguration.getDefaultBridgeProducerWindowSize(), MINUS_ONE_OR_POSITIVE_INT);

      long clusterNotificationInterval = getLong(e, "notification-interval", ActiveMQDefaultConfiguration.getDefaultClusterNotificationInterval(), GT_ZERO);

      int clusterNotificationAttempts = getInteger(e, "notification-attempts", ActiveMQDefaultConfiguration.getDefaultClusterNotificationAttempts(), GT_ZERO);

      int topologyScannerAttempts = getInteger(e, "topology-scanner-attempts", ActiveMQDefaultConfiguration.getClusterTopologyScannerAttempts(), MINUS_ONE_OR_GE_ZERO);

      String discoveryGroupName = null;

      List<String> staticConnectorNames = new ArrayList<>();

      boolean allowDirectConnectionsOnly = false;

      NodeList children = e.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("discovery-group-ref")) {
            discoveryGroupName = child.getAttributes().getNamedItem("discovery-group-name").getNodeValue();
         } else if (child.getNodeName().equals("static-connectors")) {
            Node attr = child.getAttributes().getNamedItem("allow-direct-connections-only");
            if (attr != null) {
               allowDirectConnectionsOnly = "true".equalsIgnoreCase(attr.getNodeValue()) || allowDirectConnectionsOnly;
            }
            getStaticConnectors(staticConnectorNames, child);
         }
      }

      String clientId = getString(e, "client-id", null, NO_CHECK);

      ClusterConnectionConfiguration config = new ClusterConnectionConfiguration().setName(name).setAddress(address).setConnectorName(connectorName).setMinLargeMessageSize(minLargeMessageSize).setClientFailureCheckPeriod(clientFailureCheckPeriod).setConnectionTTL(connectionTTL).setRetryInterval(retryInterval).setRetryIntervalMultiplier(retryIntervalMultiplier).setMaxRetryInterval(maxRetryInterval).setInitialConnectAttempts(initialConnectAttempts).setReconnectAttempts(reconnectAttempts).setCallTimeout(callTimeout).setCallFailoverTimeout(callFailoverTimeout).setDuplicateDetection(duplicateDetection).setMessageLoadBalancingType(messageLoadBalancingType).setMaxHops(maxHops).setConfirmationWindowSize(confirmationWindowSize).setProducerWindowSize(producerWindowSize).setAllowDirectConnectionsOnly(allowDirectConnectionsOnly).setClusterNotificationInterval(clusterNotificationInterval).setClusterNotificationAttempts(clusterNotificationAttempts).setClientId(clientId).setTopologyScannerAttempts(topologyScannerAttempts);

      if (discoveryGroupName == null) {
         config.setStaticConnectors(staticConnectorNames);
      } else {
         config.setDiscoveryGroupName(discoveryGroupName);
      }

      mainConfig.getClusterConfigurations().add(config);
   }

   private void parseGroupingHandlerConfiguration(final Element node, final Configuration mainConfiguration) {
      String name = node.getAttribute("name");
      String type = getString(node, "type", null, NOT_NULL_OR_EMPTY);
      String address = getString(node, "address", "", NO_CHECK);
      Integer timeout = getInteger(node, "timeout", ActiveMQDefaultConfiguration.getDefaultGroupingHandlerTimeout(), GT_ZERO);
      Long groupTimeout = getLong(node, "group-timeout", ActiveMQDefaultConfiguration.getDefaultGroupingHandlerGroupTimeout(), MINUS_ONE_OR_GT_ZERO);
      Long reaperPeriod = getLong(node, "reaper-period", ActiveMQDefaultConfiguration.getDefaultGroupingHandlerReaperPeriod(), GT_ZERO);
      mainConfiguration.setGroupingHandlerConfiguration(new GroupingHandlerConfiguration().setName(SimpleString.of(name)).setType(type.equals(GroupingHandlerConfiguration.TYPE.LOCAL.getType()) ? GroupingHandlerConfiguration.TYPE.LOCAL : GroupingHandlerConfiguration.TYPE.REMOTE).setAddress(SimpleString.of(address)).setTimeout(timeout).setGroupTimeout(groupTimeout).setReaperPeriod(reaperPeriod));
   }

   private TransformerConfiguration getTransformerConfiguration(final Node node) {
      Element element = (Element) node;
      String className = getString(element, "class-name", null, NO_CHECK);

      Map<String, String> properties = getMapOfChildPropertyElements(element);
      return new TransformerConfiguration(className).setProperties(properties);
   }

   private TransformerConfiguration getTransformerConfiguration(final String transformerClassName) {
      return new TransformerConfiguration(transformerClassName).setProperties(Collections.emptyMap());
   }

   private void parseBridgeConfiguration(final Element brNode, final Configuration mainConfig) throws Exception {
      String name = brNode.getAttribute("name");

      String queueName = getString(brNode, "queue-name", null, NOT_NULL_OR_EMPTY);

      String forwardingAddress = getString(brNode, "forwarding-address", null, NO_CHECK);

      String transformerClassName = getString(brNode, "transformer-class-name", null, NO_CHECK);

      // Default bridge conf
      int confirmationWindowSize = getTextBytesAsIntBytes(brNode, "confirmation-window-size", ActiveMQDefaultConfiguration.getDefaultBridgeConfirmationWindowSize(), MINUS_ONE_OR_POSITIVE_INT);

      int producerWindowSize = getTextBytesAsIntBytes(brNode, "producer-window-size", ActiveMQDefaultConfiguration.getDefaultBridgeProducerWindowSize(), MINUS_ONE_OR_POSITIVE_INT);

      long retryInterval = getLong(brNode, "retry-interval", ActiveMQClient.DEFAULT_RETRY_INTERVAL, GT_ZERO);

      long clientFailureCheckPeriod = getLong(brNode, "check-period", ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, GT_ZERO);

      long connectionTTL = getLong(brNode, "connection-ttl", ActiveMQClient.DEFAULT_CONNECTION_TTL, GT_ZERO);

      int minLargeMessageSize = getTextBytesAsIntBytes(brNode, "min-large-message-size", ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, POSITIVE_INT);

      long maxRetryInterval = getLong(brNode, "max-retry-interval", ActiveMQClient.DEFAULT_MAX_RETRY_INTERVAL, GT_ZERO);

      double retryIntervalMultiplier = getDouble(brNode, "retry-interval-multiplier", ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, GT_ZERO);

      int initialConnectAttempts = getInteger(brNode, "initial-connect-attempts", ActiveMQDefaultConfiguration.getDefaultBridgeInitialConnectAttempts(), MINUS_ONE_OR_GE_ZERO);

      int reconnectAttempts = getInteger(brNode, "reconnect-attempts", ActiveMQDefaultConfiguration.getDefaultBridgeReconnectAttempts(), MINUS_ONE_OR_GE_ZERO);

      int reconnectAttemptsSameNode = getInteger(brNode, "reconnect-attempts-same-node", ActiveMQDefaultConfiguration.getDefaultBridgeConnectSameNode(), MINUS_ONE_OR_GE_ZERO);

      boolean useDuplicateDetection = getBoolean(brNode, "use-duplicate-detection", ActiveMQDefaultConfiguration.isDefaultBridgeDuplicateDetection());

      String user = getString(brNode, "user", ActiveMQDefaultConfiguration.getDefaultClusterUser(), NO_CHECK);

      ComponentConfigurationRoutingType routingType = ComponentConfigurationRoutingType.valueOf(getString(brNode, "routing-type", ActiveMQDefaultConfiguration.getDefaultBridgeRoutingType(), COMPONENT_ROUTING_TYPE));

      int concurrency = getInteger(brNode, "concurrency", ActiveMQDefaultConfiguration.getDefaultBridgeConcurrency(), GT_ZERO);

      long pendingAckTimeout = getLong(brNode, "pending-ack-timeout", ActiveMQDefaultConfiguration.getDefaultBridgePendingAckTimeout(), GT_ZERO);

      String clientId = getString(brNode, "client-id", null, NO_CHECK);

      NodeList clusterPassNodes = brNode.getElementsByTagName("password");
      String password = null;

      if (clusterPassNodes.getLength() > 0) {
         Node passNode = clusterPassNodes.item(0);
         password = passNode.getTextContent();
      }

      if (password != null) {
         password = PasswordMaskingUtil.resolveMask(mainConfig.isMaskPassword(), password, mainConfig.getPasswordCodec());
      } else {
         password = ActiveMQDefaultConfiguration.getDefaultClusterPassword();
      }

      boolean ha = getBoolean(brNode, "ha", false);

      TransformerConfiguration transformerConfiguration = null;

      String filterString = null;

      List<String> staticConnectorNames = new ArrayList<>();

      String discoveryGroupName = null;

      NodeList children = brNode.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("filter")) {
            filterString = child.getAttributes().getNamedItem("string").getNodeValue();
         } else if (child.getNodeName().equals("discovery-group-ref")) {
            discoveryGroupName = child.getAttributes().getNamedItem("discovery-group-name").getNodeValue();
         } else if (child.getNodeName().equals("static-connectors")) {
            getStaticConnectors(staticConnectorNames, child);
         } else if (child.getNodeName().equals("transformer")) {
            transformerConfiguration = getTransformerConfiguration(child);
         }
      }

      if (transformerConfiguration == null && transformerClassName != null) {
         transformerConfiguration = getTransformerConfiguration(transformerClassName);
      }

      BridgeConfiguration config = new BridgeConfiguration()
         .setName(name)
         .setQueueName(queueName)
         .setForwardingAddress(forwardingAddress)
         .setFilterString(filterString)
         .setTransformerConfiguration(transformerConfiguration)
         .setMinLargeMessageSize(minLargeMessageSize)
         .setClientFailureCheckPeriod(clientFailureCheckPeriod)
         .setConnectionTTL(connectionTTL)
         .setRetryInterval(retryInterval)
         .setMaxRetryInterval(maxRetryInterval)
         .setRetryIntervalMultiplier(retryIntervalMultiplier)
         .setInitialConnectAttempts(initialConnectAttempts)
         .setReconnectAttempts(reconnectAttempts)
         .setReconnectAttemptsOnSameNode(reconnectAttemptsSameNode)
         .setUseDuplicateDetection(useDuplicateDetection)
         .setConfirmationWindowSize(confirmationWindowSize)
         .setProducerWindowSize(producerWindowSize)
         .setHA(ha)
         .setUser(user)
         .setPassword(password)
         .setRoutingType(routingType)
         .setConcurrency(concurrency)
         .setPendingAckTimeout(pendingAckTimeout)
         .setClientId(clientId);

      if (!staticConnectorNames.isEmpty()) {
         config.setStaticConnectors(staticConnectorNames);
      } else {
         config.setDiscoveryGroupName(discoveryGroupName);
      }

      mainConfig.getBridgeConfigurations().add(config);
   }

   private void parseFederationConfiguration(final Element fedNode, final Configuration mainConfig) throws Exception {
      FederationConfiguration config = new FederationConfiguration();

      String name = fedNode.getAttribute("name");
      config.setName(name);

      FederationConfiguration.Credentials credentials = new FederationConfiguration.Credentials();

      // parsing federation password
      String passwordTextFederation = fedNode.getAttribute("password");

      if (passwordTextFederation != null && !passwordTextFederation.isEmpty()) {
         String resolvedPassword = PasswordMaskingUtil.resolveMask(mainConfig.isMaskPassword(), passwordTextFederation, mainConfig.getPasswordCodec());
         credentials.setPassword(resolvedPassword);
      }

      credentials.setUser(fedNode.getAttribute("user"));
      config.setCredentials(credentials);

      NodeList children = fedNode.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("upstream")) {
            config.addUpstreamConfiguration(getUpstream((Element) child, mainConfig));
         } else if (child.getNodeName().equals("downstream")) {
            config.addDownstreamConfiguration(getDownstream((Element) child, mainConfig));
         } else if (child.getNodeName().equals("policy-set")) {
            config.addFederationPolicy(getPolicySet((Element)child, mainConfig));
         } else if (child.getNodeName().equals("queue-policy")) {
            config.addFederationPolicy(getQueuePolicy((Element)child, mainConfig));
         } else if (child.getNodeName().equals("address-policy")) {
            config.addFederationPolicy(getAddressPolicy((Element)child, mainConfig));
         } else if (child.getNodeName().equals("transformer")) {
            TransformerConfiguration transformerConfiguration = getTransformerConfiguration(child);
            config.addTransformerConfiguration(new FederationTransformerConfiguration(
               ((Element)child).getAttribute("name"), transformerConfiguration));
         }
      }

      mainConfig.getFederationConfigurations().add(config);

   }

   private FederationQueuePolicyConfiguration getQueuePolicy(Element policyNod, final Configuration mainConfig) throws Exception {
      FederationQueuePolicyConfiguration config = new FederationQueuePolicyConfiguration();
      config.setName(policyNod.getAttribute("name"));

      NamedNodeMap attributes = policyNod.getAttributes();
      for (int i = 0; i < attributes.getLength(); i++) {
         Node item = attributes.item(i);
         if (item.getNodeName().equals("include-federated")) {
            config.setIncludeFederated(Boolean.parseBoolean(item.getNodeValue()));
         } else if (item.getNodeName().equals("priority-adjustment")) {
            int priorityAdjustment = Integer.parseInt(item.getNodeValue());
            config.setPriorityAdjustment(priorityAdjustment);
         } else if (item.getNodeName().equals("transformer-ref")) {
            String transformerRef = item.getNodeValue();
            config.setTransformerRef(transformerRef);
         }
      }

      NodeList children = policyNod.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("include")) {
            config.addInclude(getQueueMatcher((Element) child));
         } else if (child.getNodeName().equals("exclude")) {
            config.addExclude(getQueueMatcher((Element) child));
         }
      }


      return config;
   }

   private FederationQueuePolicyConfiguration.Matcher getQueueMatcher(Element child) {
      FederationQueuePolicyConfiguration.Matcher matcher = new FederationQueuePolicyConfiguration.Matcher();
      matcher.setQueueMatch(child.getAttribute("queue-match"));
      matcher.setAddressMatch(child.getAttribute("address-match"));
      return matcher;
   }


   private FederationAddressPolicyConfiguration getAddressPolicy(Element policyNod, final Configuration mainConfig) throws Exception {
      FederationAddressPolicyConfiguration config = new FederationAddressPolicyConfiguration();
      config.setName(policyNod.getAttribute("name"));

      NamedNodeMap attributes = policyNod.getAttributes();
      for (int i = 0; i < attributes.getLength(); i++) {
         Node item = attributes.item(i);
         if (item.getNodeName().equals("max-consumers")) {
            config.setMaxHops(MINUS_ONE_OR_GE_ZERO.validate(item.getNodeName(), Integer.parseInt(item.getNodeValue())).intValue());
         } else if (item.getNodeName().equals("auto-delete")) {
            config.setAutoDelete(Boolean.parseBoolean(item.getNodeValue()));
         } else if (item.getNodeName().equals("auto-delete-delay")) {
            config.setAutoDeleteDelay((GE_ZERO.validate("auto-delete-delay", Long.parseLong(item.getNodeValue())).longValue()));
         } else if (item.getNodeName().equals("auto-delete-message-count")) {
            config.setAutoDeleteMessageCount(MINUS_ONE_OR_GE_ZERO.validate("auto-delete-message-count", Long.parseLong(item.getNodeValue())).longValue());
         } else if (item.getNodeName().equals("transformer-ref")) {
            config.setTransformerRef(item.getNodeValue());
         } else if (item.getNodeName().equals("enable-divert-bindings")) {
            config.setEnableDivertBindings(Boolean.parseBoolean(item.getNodeValue()));
         }
      }

      NodeList children = policyNod.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("include")) {
            config.addInclude(getAddressMatcher((Element) child));
         } else if (child.getNodeName().equals("exclude")) {
            config.addExclude(getAddressMatcher((Element) child));
         }
      }


      return config;
   }

   private FederationAddressPolicyConfiguration.Matcher getAddressMatcher(Element child) {
      FederationAddressPolicyConfiguration.Matcher matcher = new FederationAddressPolicyConfiguration.Matcher();
      matcher.setAddressMatch(child.getAttribute("address-match"));
      return matcher;
   }

   private FederationPolicySet getPolicySet(Element policySetNode, final Configuration mainConfig) throws Exception {
      FederationPolicySet config = new FederationPolicySet();
      config.setName(policySetNode.getAttribute("name"));

      NodeList children = policySetNode.getChildNodes();

      List<String> policyRefs = new ArrayList<>();


      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("policy")) {
            policyRefs.add(((Element)child).getAttribute("ref"));
         }
      }
      config.addPolicyRefs(policyRefs);

      return config;
   }

   private <T extends FederationStreamConfiguration> T getFederationStream(final T config, final Element upstreamNode,
       final Configuration mainConfig) throws Exception {

      String name = upstreamNode.getAttribute("name");
      config.setName(name);

      // parsing federation password
      String passwordTextFederation = upstreamNode.getAttribute("password");

      if (passwordTextFederation != null && !passwordTextFederation.isEmpty()) {
         String resolvedPassword = PasswordMaskingUtil.resolveMask(mainConfig.isMaskPassword(), passwordTextFederation, mainConfig.getPasswordCodec());
         config.getConnectionConfiguration().setPassword(resolvedPassword);
      }

      config.getConnectionConfiguration().setUsername(upstreamNode.getAttribute("user"));

      NamedNodeMap attributes = upstreamNode.getAttributes();
      for (int i = 0; i < attributes.getLength(); i++) {
         Node item = attributes.item(i);
         if (item.getNodeName().equals("priority-adjustment")) {
            config.getConnectionConfiguration().setPriorityAdjustment(Integer.parseInt(item.getNodeValue()));
         }
      }

      boolean ha = getBoolean(upstreamNode, "ha", false);

      long circuitBreakerTimeout = getLong(upstreamNode, "circuit-breaker-timeout", config.getConnectionConfiguration().getCircuitBreakerTimeout(), MINUS_ONE_OR_GE_ZERO);

      long clientFailureCheckPeriod = getLong(upstreamNode, "check-period", ActiveMQDefaultConfiguration.getDefaultFederationFailureCheckPeriod(), GT_ZERO);
      long connectionTTL = getLong(upstreamNode, "connection-ttl", ActiveMQDefaultConfiguration.getDefaultFederationConnectionTtl(), GT_ZERO);
      long retryInterval = getLong(upstreamNode, "retry-interval", ActiveMQDefaultConfiguration.getDefaultFederationRetryInterval(), GT_ZERO);
      long callTimeout = getLong(upstreamNode, "call-timeout", ActiveMQClient.DEFAULT_CALL_TIMEOUT, GT_ZERO);
      long callFailoverTimeout = getLong(upstreamNode, "call-failover-timeout", ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, MINUS_ONE_OR_GT_ZERO);
      double retryIntervalMultiplier = getDouble(upstreamNode, "retry-interval-multiplier", ActiveMQDefaultConfiguration.getDefaultFederationRetryIntervalMultiplier(), GT_ZERO);
      long maxRetryInterval = getLong(upstreamNode, "max-retry-interval", ActiveMQDefaultConfiguration.getDefaultFederationMaxRetryInterval(), GT_ZERO);
      int initialConnectAttempts = getInteger(upstreamNode, "initial-connect-attempts", ActiveMQDefaultConfiguration.getDefaultFederationInitialConnectAttempts(), MINUS_ONE_OR_GE_ZERO);
      int reconnectAttempts = getInteger(upstreamNode, "reconnect-attempts", ActiveMQDefaultConfiguration.getDefaultFederationReconnectAttempts(), MINUS_ONE_OR_GE_ZERO);

      List<String> staticConnectorNames = new ArrayList<>();

      String discoveryGroupName = null;

      NodeList children = upstreamNode.getChildNodes();

      List<String> policyRefs = new ArrayList<>();


      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("discovery-group-ref")) {
            discoveryGroupName = child.getAttributes().getNamedItem("discovery-group-name").getNodeValue();
         } else if (child.getNodeName().equals("static-connectors")) {
            getStaticConnectors(staticConnectorNames, child);
         } else if (child.getNodeName().equals("policy")) {
            policyRefs.add(((Element)child).getAttribute("ref"));
         }
      }
      config.addPolicyRefs(policyRefs);

      config.getConnectionConfiguration()
          .setCircuitBreakerTimeout(circuitBreakerTimeout)
          .setHA(ha)
          .setClientFailureCheckPeriod(clientFailureCheckPeriod)
          .setConnectionTTL(connectionTTL)
          .setRetryInterval(retryInterval)
          .setRetryIntervalMultiplier(retryIntervalMultiplier)
          .setMaxRetryInterval(maxRetryInterval)
          .setInitialConnectAttempts(initialConnectAttempts)
          .setReconnectAttempts(reconnectAttempts)
          .setCallTimeout(callTimeout)
          .setCallFailoverTimeout(callFailoverTimeout);

      if (!staticConnectorNames.isEmpty()) {
         config.getConnectionConfiguration().setStaticConnectors(staticConnectorNames);
      } else {
         config.getConnectionConfiguration().setDiscoveryGroupName(discoveryGroupName);
      }
      return config;
   }

   private FederationUpstreamConfiguration getUpstream(final Element upstreamNode, final Configuration mainConfig) throws Exception {
      return getFederationStream(new FederationUpstreamConfiguration(), upstreamNode, mainConfig);
   }

   private FederationDownstreamConfiguration getDownstream(final Element downstreamNode, final Configuration mainConfig) throws Exception {
      final FederationDownstreamConfiguration downstreamConfiguration =
          getFederationStream(new FederationDownstreamConfiguration(), downstreamNode, mainConfig);

      final String upstreamRef = getString(downstreamNode, "upstream-connector-ref", null, NOT_NULL_OR_EMPTY);
      downstreamConfiguration.setUpstreamConfigurationRef(upstreamRef);

      return downstreamConfiguration;
   }

   private FederationUpstreamConfiguration getBrokerConnections(final Element upstreamNode, final Configuration mainConfig) throws Exception {
      return getFederationStream(new FederationUpstreamConfiguration(), upstreamNode, mainConfig);
   }

   private void getStaticConnectors(List<String> staticConnectorNames, Node child) {
      NodeList children2 = ((Element) child).getElementsByTagName("connector-ref");

      for (int k = 0; k < children2.getLength(); k++) {
         Element child2 = (Element) children2.item(k);

         String connectorName = child2.getChildNodes().item(0).getNodeValue();

         staticConnectorNames.add(connectorName);
      }
   }

   private void parseDivertConfiguration(final Element e, final Configuration mainConfig) {
      String name = e.getAttribute("name");

      String routingName = getString(e, "routing-name", null, NO_CHECK);

      String address = getString(e, "address", null, NOT_NULL_OR_EMPTY);

      String forwardingAddress = getString(e, "forwarding-address", null, NOT_NULL_OR_EMPTY);

      boolean exclusive = getBoolean(e, "exclusive", ActiveMQDefaultConfiguration.isDefaultDivertExclusive());

      String transformerClassName = getString(e, "transformer-class-name", null, NO_CHECK);

      ComponentConfigurationRoutingType routingType = ComponentConfigurationRoutingType.valueOf(getString(e, "routing-type", ActiveMQDefaultConfiguration.getDefaultDivertRoutingType(), COMPONENT_ROUTING_TYPE));

      TransformerConfiguration transformerConfiguration = null;

      String filterString = null;

      NodeList children = e.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("filter")) {
            filterString = getAttributeValue(child, "string");
         } else if (child.getNodeName().equals("transformer")) {
            transformerConfiguration = getTransformerConfiguration(child);
         }
      }

      if (transformerConfiguration == null && transformerClassName != null) {
         transformerConfiguration = getTransformerConfiguration(transformerClassName);
      }

      DivertConfiguration config = new DivertConfiguration().setName(name).setRoutingName(routingName).setAddress(address).setForwardingAddress(forwardingAddress).setExclusive(exclusive).setFilterString(filterString).setTransformerConfiguration(transformerConfiguration).setRoutingType(routingType);

      mainConfig.getDivertConfigurations().add(config);
   }

   private void parseConnectionRouterConfiguration(final Element e, final Configuration config) throws Exception {
      ConnectionRouterConfiguration connectionRouterConfiguration = new ConnectionRouterConfiguration();

      connectionRouterConfiguration.setName(e.getAttribute("name"));

      connectionRouterConfiguration.setKeyType(KeyType.valueOf(getString(e, "key-type", connectionRouterConfiguration.getKeyType().name(), KEY_TYPE)));

      connectionRouterConfiguration.setKeyFilter(getString(e, "key-filter", connectionRouterConfiguration.getKeyFilter(), NO_CHECK));

      connectionRouterConfiguration.setLocalTargetFilter(getString(e, "local-target-filter", connectionRouterConfiguration.getLocalTargetFilter(), NO_CHECK));

      NamedPropertyConfiguration policyConfiguration = null;
      PoolConfiguration poolConfiguration = null;
      NodeList children = e.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
         Node child = children.item(j);

         if (child.getNodeName().equals("cache")) {
            CacheConfiguration cacheConfiguration = new CacheConfiguration();
            parseCacheConfiguration((Element) child, cacheConfiguration);
            connectionRouterConfiguration.setCacheConfiguration(cacheConfiguration);
         } else if (child.getNodeName().equals("policy")) {
            policyConfiguration = new NamedPropertyConfiguration();
            parsePolicyConfiguration((Element) child, policyConfiguration);
            connectionRouterConfiguration.setPolicyConfiguration(policyConfiguration);
         } else if (child.getNodeName().equals("pool")) {
            poolConfiguration = new PoolConfiguration();
            parsePoolConfiguration((Element) child, config, poolConfiguration);
            connectionRouterConfiguration.setPoolConfiguration(poolConfiguration);
         }
      }

      config.getConnectionRouters().add(connectionRouterConfiguration);
   }

   private void parseCacheConfiguration(final Element e, final CacheConfiguration cacheConfiguration) throws ClassNotFoundException {
      cacheConfiguration.setPersisted(getBoolean(e, "persisted",
         cacheConfiguration.isPersisted()));

      cacheConfiguration.setTimeout(getInteger(e, "timeout",
         cacheConfiguration.getTimeout(), GE_ZERO));
   }

   private void parsePolicyConfiguration(final Element e, final NamedPropertyConfiguration policyConfiguration) throws ClassNotFoundException {
      String name = e.getAttribute("name");

      PolicyFactoryResolver.getInstance().resolve(name);

      policyConfiguration.setName(name);

      policyConfiguration.setProperties(getMapOfChildPropertyElements(e));
   }

   private void parsePoolConfiguration(final Element e, final Configuration config, final PoolConfiguration poolConfiguration) throws Exception {
      poolConfiguration.setUsername(getString(e, "username", null, NO_CHECK));

      String password = getString(e, "password", null, NO_CHECK);
      poolConfiguration.setPassword(password != null ? PasswordMaskingUtil.resolveMask(
         config.isMaskPassword(), password, config.getPasswordCodec()) : null);

      poolConfiguration.setCheckPeriod(getInteger(e, "check-period",
         poolConfiguration.getCheckPeriod(), GT_ZERO));

      poolConfiguration.setQuorumSize(getInteger(e, "quorum-size",
         poolConfiguration.getQuorumSize(), GT_ZERO));

      poolConfiguration.setQuorumTimeout(getInteger(e, "quorum-timeout",
         poolConfiguration.getQuorumTimeout(), GE_ZERO));

      poolConfiguration.setLocalTargetEnabled(getBoolean(e, "local-target-enabled", poolConfiguration.isLocalTargetEnabled()));

      poolConfiguration.setClusterConnection(getString(e, "cluster-connection", null, NO_CHECK));

      NodeList children = e.getChildNodes();

      for (int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if (child.getNodeName().equals("discovery-group-ref")) {
            poolConfiguration.setDiscoveryGroupName(child.getAttributes().getNamedItem("discovery-group-name").getNodeValue());
         } else if (child.getNodeName().equals("static-connectors")) {
            List<String> staticConnectorNames = new ArrayList<>();
            getStaticConnectors(staticConnectorNames, child);
            poolConfiguration.setStaticConnectors(staticConnectorNames);
         }
      }
   }

   protected void parseWildcardConfiguration(final Element e, final Configuration mainConfig) {
      WildcardConfiguration conf = mainConfig.getWildcardConfiguration();

      conf.setDelimiter(getString(e, "delimiter", Character.toString(conf.getDelimiter()), NO_CHECK).charAt(0));
      conf.setAnyWords(getString(e, "any-words", Character.toString(conf.getAnyWords()), NO_CHECK).charAt(0));
      conf.setSingleWord(getString(e, "single-word", Character.toString(conf.getSingleWord()), NO_CHECK).charAt(0));
      conf.setRoutingEnabled(getBoolean(e, "enabled", conf.isRoutingEnabled()));
      conf.setRoutingEnabled(getBoolean(e, "routing-enabled", conf.isRoutingEnabled()));
   }

   private ConnectorServiceConfiguration parseConnectorService(final Element e) {
      Node nameNode = e.getAttributes().getNamedItem("name");

      String name = nameNode != null ? nameNode.getNodeValue() : null;

      String clazz = getString(e, "factory-class", null, NOT_NULL_OR_EMPTY);

      Map<String, Object> params = new HashMap<>();

      NodeList paramsNodes = e.getElementsByTagName("param");

      for (int i = 0; i < paramsNodes.getLength(); i++) {
         Node paramNode = paramsNodes.item(i);

         NamedNodeMap attributes = paramNode.getAttributes();

         Node nkey = attributes.getNamedItem("key");

         String key = nkey.getTextContent();

         Node nValue = attributes.getNamedItem("value");

         params.put(key, nValue.getTextContent());
      }

      return new ConnectorServiceConfiguration().setFactoryClassName(clazz).setParams(params).setName(name);
   }
}
