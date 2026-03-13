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

package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_DELAY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_AUTO_DELETE_MSG_COUNT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_ENABLE_DIVERT_BINDINGS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_ALLOW_WILDCARD_GROUPINGS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_EXCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_INCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADDRESS_MAX_HOPS;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADD_ADDRESS_POLICY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.ADD_QUEUE_POLICY;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.OPERATION_TYPE;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.POLICY_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.POLICY_PROPERTIES_MAP;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_EXCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_INCLUDES;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_INCLUDE_FEDERATED;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.QUEUE_PRIORITY_ADJUSTMENT;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.TRANSFORMER_CLASS_NAME;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.TRANSFORMER_PROPERTIES_MAP;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromAddressPolicy;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromQueuePolicy;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolMessageBundle;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.WritableBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

/**
 * Tools used when loading AMQP Broker connections configuration that includes Federation configuration.
 */
public final class AMQPFederationPolicySupport {

   /**
    * Default priority adjustment used for a federation queue match policy if nothing was configured in the broker
    * configuration file.
    */
   public static final int DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT = -1;

   /**
    * Annotation added to received messages from address consumers that indicates how many times the message has
    * traversed a federation link.
    */
   public static final Symbol MESSAGE_HOPS_ANNOTATION = Symbol.valueOf("x-opt-amq-fed-hops");

   /**
    * Property value placed on Core messages to indicate number of hops that a message has made when crossing Federation
    * links. This value is used when Core messages are tunneled via an AMQP custom message and then recreated again on
    * the other side.
    */
   public static final String MESSAGE_HOPS_PROPERTY = "_AMQ_Fed_Hops";

   /**
    * Property name used to embed a nested map of properties meant to be applied if the address indicated in an
    * federation address receiver auto creates the federated address.
    */
   public static final Symbol FEDERATED_ADDRESS_SOURCE_PROPERTIES = Symbol.valueOf("federated-address-source-properties");

   /**
    * Constructs an address filter for a federated address receiver link that deals with both AMQP messages and
    * unwrapped Core messages which can carry different hops markers. If the max is less than or equal to zero no filter
    * is created as these values are used to indicate no max hops for federated messages on an address.
    *
    * @param maxHops The max allowed number of hops before a message should stop crossing federation links.
    *
    * @return the address filter string that should be applied (or null)
    */
   public static String generateAddressFilter(int maxHops) {
      if (maxHops <= 0) {
         return null;
      }

      return "((\"m." + MESSAGE_HOPS_ANNOTATION +
             "\" IS NULL OR \"m." + MESSAGE_HOPS_ANNOTATION +
             "\"<" + maxHops + ")" +
             " AND " +
             "(" + MESSAGE_HOPS_PROPERTY + " IS NULL OR " +
             MESSAGE_HOPS_PROPERTY + "<" + maxHops + "))";
   }

   /**
    * Create an AMQP Message used to instruct the remote peer that it should perform Federation operations on the given
    * {@link FederationReceiveFromQueuePolicy}.
    *
    * @param policy The policy to encode into an AMQP message.
    *
    * @return an AMQP Message with the encoded policy
    */
   public static AMQPMessage encodeQueuePolicyControlMessage(FederationReceiveFromQueuePolicy policy) {
      final Map<Symbol, Object> annotations = new LinkedHashMap<>();
      final MessageAnnotations messageAnnotations = new MessageAnnotations(annotations);
      final Map<String, Object> policyMap = new LinkedHashMap<>();
      final Section sectionBody = new AmqpValue(policyMap);
      final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);

      annotations.put(OPERATION_TYPE, ADD_QUEUE_POLICY);

      policyMap.put(POLICY_NAME, policy.getPolicyName());
      policyMap.put(QUEUE_INCLUDE_FEDERATED, policy.isIncludeFederated());
      policyMap.put(QUEUE_PRIORITY_ADJUSTMENT, policy.getPriorityAjustment());

      if (!policy.getIncludes().isEmpty()) {
         final List<String> flattenedIncludes = new ArrayList<>(policy.getIncludes().size() * 2);
         policy.getIncludes().forEach((entry) -> {
            flattenedIncludes.add(entry.getKey());
            flattenedIncludes.add(entry.getValue());
         });

         policyMap.put(QUEUE_INCLUDES, flattenedIncludes);
      }

      if (!policy.getExcludes().isEmpty()) {
         final List<String> flatteneExcludes = new ArrayList<>(policy.getExcludes().size() * 2);
         policy.getExcludes().forEach((entry) -> {
            flatteneExcludes.add(entry.getKey());
            flatteneExcludes.add(entry.getValue());
         });

         policyMap.put(QUEUE_EXCLUDES, flatteneExcludes);
      }

      if (!policy.getProperties().isEmpty()) {
         policyMap.put(POLICY_PROPERTIES_MAP, policy.getProperties());
      }

      if (policy.getTransformerConfiguration() != null) {
         final TransformerConfiguration config = policy.getTransformerConfiguration();

         policyMap.put(TRANSFORMER_CLASS_NAME, config.getClassName());
         if (!config.getProperties().isEmpty()) {
            policyMap.put(TRANSFORMER_PROPERTIES_MAP, config.getProperties());
         }
      }

      try {
         final EncoderImpl encoder = TLSEncode.getEncoder();
         encoder.setByteBuffer(new NettyWritable(buffer));
         encoder.writeObject(messageAnnotations);
         encoder.writeObject(sectionBody);

         final byte[] data = new byte[buffer.writerIndex()];
         buffer.readBytes(data);

         return new AMQPStandardMessage(0, data, null);
      } finally {
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
         buffer.release();
      }
   }

   /**
    * Create an AMQP Message used to instruct the remote peer that it should perform Federation operations on the given
    * {@link FederationReceiveFromAddressPolicy}.
    *
    * @param policy The policy to encode into an AMQP message.
    *
    * @return an AMQP Message with the encoded policy
    */
   public static AMQPMessage encodeAddressPolicyControlMessage(FederationReceiveFromAddressPolicy policy) {
      final Map<Symbol, Object> annotations = new LinkedHashMap<>();
      final MessageAnnotations messageAnnotations = new MessageAnnotations(annotations);
      final Map<String, Object> policyMap = new LinkedHashMap<>();
      final Section sectionBody = new AmqpValue(policyMap);
      final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);

      annotations.put(OPERATION_TYPE, ADD_ADDRESS_POLICY);

      policyMap.put(POLICY_NAME, policy.getPolicyName());
      policyMap.put(ADDRESS_AUTO_DELETE, policy.isAutoDelete());
      policyMap.put(ADDRESS_AUTO_DELETE_DELAY, policy.getAutoDeleteDelay());
      policyMap.put(ADDRESS_AUTO_DELETE_MSG_COUNT, policy.getAutoDeleteMessageCount());
      policyMap.put(ADDRESS_MAX_HOPS, policy.getMaxHops());
      policyMap.put(ADDRESS_ENABLE_DIVERT_BINDINGS, policy.isEnableDivertBindings());
      policyMap.put(ADDRESS_ALLOW_WILDCARD_GROUPINGS, policy.isAllowWildcardGroupings());
      if (!policy.getIncludes().isEmpty()) {
         policyMap.put(ADDRESS_INCLUDES, new ArrayList<>(policy.getIncludes()));
      }
      if (!policy.getExcludes().isEmpty()) {
         policyMap.put(ADDRESS_EXCLUDES, new ArrayList<>(policy.getExcludes()));
      }

      if (!policy.getProperties().isEmpty()) {
         policyMap.put(POLICY_PROPERTIES_MAP, policy.getProperties());
      }

      if (policy.getTransformerConfiguration() != null) {
         final TransformerConfiguration config = policy.getTransformerConfiguration();

         policyMap.put(TRANSFORMER_CLASS_NAME, config.getClassName());
         if (!config.getProperties().isEmpty()) {
            policyMap.put(TRANSFORMER_PROPERTIES_MAP, config.getProperties());
         }
      }

      try {
         final EncoderImpl encoder = TLSEncode.getEncoder();
         encoder.setByteBuffer(new NettyWritable(buffer));
         encoder.writeObject(messageAnnotations);
         encoder.writeObject(sectionBody);

         final byte[] data = new byte[buffer.writerIndex()];
         buffer.readBytes(data);

         return new AMQPStandardMessage(0, data, null);
      } finally {
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
         buffer.release();
      }
   }

   /**
    * Given an AMQP Message decode an {@link FederationReceiveFromQueuePolicy} from it and return the decoded value. The
    * message should have already been inspected and determined to be an control message of the add to policy type.
    * The decoder ignores elements in the message that it does not know about so that future configuration additions do
    * not break previous broker instances.
    *
    * @param message
    *    The {@link AMQPMessage} that should carry an encoded {@link FederationReceiveFromQueuePolicy}
    * @param wildcardConfig
    *    The {@link WildcardConfiguration} to use in the decoded policy.
    *
    * @return a decoded {@link FederationReceiveFromQueuePolicy} instance
    *
    * @throws ActiveMQException if an error occurs while decoding the policy.
    */
   @SuppressWarnings("unchecked")
   public static FederationReceiveFromQueuePolicy decodeReceiveFromQueuePolicy(AMQPMessage message, WildcardConfiguration wildcardConfig) throws ActiveMQException {
      final Section body = message.getBody();

      if (!(body instanceof AmqpValue bodyValue)) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Message body was not an AmqpValue type");
      }

      if (bodyValue.getValue() == null || !(bodyValue.getValue() instanceof Map)) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Message body AmqpValue did not carry an encoded Map");
      }

      try {
         final Map<String, Object> policyMap = (Map<String, Object>) bodyValue.getValue();

         if (!policyMap.containsKey(POLICY_NAME)) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
               "Message body did not carry the required policy name");
         }

         final String policyName = (String) policyMap.get(POLICY_NAME);
         final boolean includeFederated = (boolean) policyMap.getOrDefault(QUEUE_INCLUDE_FEDERATED, false);
         final int priorityAdjustment = ((Number) policyMap.getOrDefault(QUEUE_PRIORITY_ADJUSTMENT, DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT)).intValue();
         final Set<Map.Entry<String, String>> includes = decodeFlattenedFilterSet(policyMap, QUEUE_INCLUDES);
         final Set<Map.Entry<String, String>> excludes = decodeFlattenedFilterSet(policyMap, QUEUE_EXCLUDES);
         final TransformerConfiguration transformerConfig;

         if (policyMap.containsKey(TRANSFORMER_CLASS_NAME)) {
            transformerConfig = new TransformerConfiguration();
            transformerConfig.setClassName((String) policyMap.get(TRANSFORMER_CLASS_NAME));
            transformerConfig.setProperties((Map<String, String>) policyMap.get(TRANSFORMER_PROPERTIES_MAP));
         } else {
            transformerConfig = null;
         }

         final Map<String, Object> properties;

         if (policyMap.containsKey(POLICY_PROPERTIES_MAP)) {
            properties = (Map<String, Object>) policyMap.get(POLICY_PROPERTIES_MAP);
         } else {
            properties = null;
         }

         return new FederationReceiveFromQueuePolicy(policyName, includeFederated, priorityAdjustment,
                                                     includes, excludes, properties, transformerConfig,
                                                     wildcardConfig);
      } catch (ActiveMQException amqEx) {
         throw amqEx;
      } catch (Exception e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Invalid encoded queue policy entry: " + e.getMessage());
      }
   }

   @SuppressWarnings("unchecked")
   private static Set<Map.Entry<String, String>> decodeFlattenedFilterSet(Map<String, Object> policyMap, String target) throws ActiveMQException {
      final Object encodedObject = policyMap.get(target);

      if (encodedObject == null) {
         return Collections.emptySet();
      }

      if (!(encodedObject instanceof List)) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Encoded queue policy entry was not the expected List type : " + target);
      }

      final Set<Map.Entry<String, String>> policyEntrySet;

      try {
         final List<String> flattenedEntrySet = (List<String>) encodedObject;

         if (flattenedEntrySet.isEmpty()) {
            return Collections.emptySet();
         }

         if ((flattenedEntrySet.size() & 1) != 0) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
               "Encoded queue policy entry was must contain an even number of elements : " + target);
         }

         policyEntrySet = new HashSet<>(Math.max(2, flattenedEntrySet.size() / 2));

         for (int i = 0; i < flattenedEntrySet.size(); ) {
            policyEntrySet.add(new SimpleEntry<>(flattenedEntrySet.get(i++), flattenedEntrySet.get(i++)));
         }

      } catch (ActiveMQException amqEx) {
         throw amqEx;
      } catch (Exception e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Invalid encoded queue policy entry: " + e.getMessage());
      }

      return policyEntrySet;
   }

   /**
    * Given an AMQP Message decode an {@link FederationReceiveFromAddressPolicy} from it and return the decoded value.
    * The message should have already been inspected and determined to be an control message of the add to policy type.
    * The decoder ignores elements in the message that it does not know about so that future configuration additions do
    * not break previous broker instances.
    *
    * @param message
    *    The {@link AMQPMessage} that should carry an encoded {@link FederationReceiveFromQueuePolicy}
    * @param wildcardConfig
    *    The {@link WildcardConfiguration} to use in the decoded policy.
    *
    * @return a decoded {@link FederationReceiveFromAddressPolicy} instance
    *
    * @throws ActiveMQException if an error occurs during the policy decode.
    */
   @SuppressWarnings("unchecked")
   public static FederationReceiveFromAddressPolicy decodeReceiveFromAddressPolicy(AMQPMessage message, WildcardConfiguration wildcardConfig) throws ActiveMQException {
      final Section body = message.getBody();

      if (!(body instanceof AmqpValue bodyValue)) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Message body was not an AmqpValue type");
      }

      if (bodyValue.getValue() == null || !(bodyValue.getValue() instanceof Map)) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Message body AmqpValue did not carry an encoded Map");
      }

      try {
         final Map<String, Object> policyMap = (Map<String, Object>) bodyValue.getValue();

         if (!policyMap.containsKey(POLICY_NAME)) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
               "Message body did not carry the required policy name");
         }

         if (!policyMap.containsKey(ADDRESS_MAX_HOPS)) {
            throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
               "Message body did not carry the required max hops configuration");
         }

         final String policyName = (String) policyMap.get(POLICY_NAME);
         final boolean autoDelete = (Boolean) policyMap.getOrDefault(ADDRESS_AUTO_DELETE, false);
         final long autoDeleteDelay = ((Number) policyMap.getOrDefault(ADDRESS_AUTO_DELETE_DELAY, 0L)).longValue();
         final long autoDeleteMsgCount = ((Number) policyMap.getOrDefault(ADDRESS_AUTO_DELETE_MSG_COUNT, 0L)).longValue();
         final int maxHops = ((Number) policyMap.get(ADDRESS_MAX_HOPS)).intValue();
         final boolean enableDiverts = (Boolean) policyMap.getOrDefault(ADDRESS_ENABLE_DIVERT_BINDINGS, false);
         final boolean allowWildcardGroupings = (Boolean) policyMap.getOrDefault(ADDRESS_ALLOW_WILDCARD_GROUPINGS, false);

         final Set<String> includes;
         final Set<String> excludes;

         if (policyMap.containsKey(ADDRESS_INCLUDES)) {
            includes = new HashSet<>((List<String>)policyMap.get(ADDRESS_INCLUDES));
         } else {
            includes = Collections.emptySet();
         }

         if (policyMap.containsKey(ADDRESS_EXCLUDES)) {
            excludes = new HashSet<>((List<String>)policyMap.get(ADDRESS_EXCLUDES));
         } else {
            excludes = Collections.emptySet();
         }

         final TransformerConfiguration transformerConfig;

         if (policyMap.containsKey(TRANSFORMER_CLASS_NAME)) {
            transformerConfig = new TransformerConfiguration();
            transformerConfig.setClassName((String) policyMap.get(TRANSFORMER_CLASS_NAME));
            transformerConfig.setProperties((Map<String, String>) policyMap.get(TRANSFORMER_PROPERTIES_MAP));
         } else {
            transformerConfig = null;
         }

         final Map<String, Object> properties;

         if (policyMap.containsKey(POLICY_PROPERTIES_MAP)) {
            properties = (Map<String, Object>) policyMap.get(POLICY_PROPERTIES_MAP);
         } else {
            properties = null;
         }

         return new FederationReceiveFromAddressPolicy(policyName, autoDelete, autoDeleteDelay,
                                                       autoDeleteMsgCount, maxHops, enableDiverts,
                                                       allowWildcardGroupings, includes, excludes,
                                                       properties, transformerConfig, wildcardConfig);
      } catch (Exception e) {
         throw ActiveMQAMQPProtocolMessageBundle.BUNDLE.malformedFederationControlMessage(
            "Invalid encoded address policy entry: " + e.getMessage());
      }
   }

   /**
    * From the broker AMQP broker connection configuration element and the configured wild-card settings create an
    * address match policy.
    *
    * @param element
    *    The broker connections configuration that creates this federation address policy.
    * @param wildcards
    *    The configured wild-card settings for the broker or defaults.
    *
    * @return a new address match and handling policy for use in the broker connection
    */
   public static FederationReceiveFromAddressPolicy create(AMQPFederationAddressPolicyElement element, WildcardConfiguration wildcards) {
      final Set<String> includes;
      final Set<String> excludes;

      if (element.getIncludes() != null && !element.getIncludes().isEmpty()) {
         includes = new HashSet<>(element.getIncludes().size());

         element.getIncludes().forEach(addressMatch -> includes.add(addressMatch.getAddressMatch()));
      } else {
         includes = Collections.emptySet();
      }

      if (element.getExcludes() != null && !element.getExcludes().isEmpty()) {
         excludes = new HashSet<>(element.getExcludes().size());

         element.getExcludes().forEach(addressMatch -> excludes.add(addressMatch.getAddressMatch()));
      } else {
         excludes = Collections.emptySet();
      }

      // We translate from broker configuration to actual implementation to avoid any coupling here
      // as broker configuration could change and or be updated.

      final FederationReceiveFromAddressPolicy policy = new FederationReceiveFromAddressPolicy(
         element.getName(),
         Objects.requireNonNullElse(element.getAutoDelete(), false),
         Objects.requireNonNullElse(element.getAutoDeleteDelay(), 0L),
         Objects.requireNonNullElse(element.getAutoDeleteMessageCount(), 0L),
         element.getMaxHops(),
         Objects.requireNonNullElse(element.isEnableDivertBindings(), false),
         Objects.requireNonNullElse(element.isAllowWildcardGroupings(), false),
         includes,
         excludes,
         element.getProperties(),
         element.getTransformerConfiguration(),
         wildcards);

      return policy;
   }

   /**
    * From the broker AMQP broker connection configuration element and the configured wild-card settings create an queue
    * match policy. If not configured otherwise the consumer priority value is always defaulted to a value of {@code -1}
    * in order to attempt to prevent federation consumers from consuming messages on the remote when a local consumer is
    * present.
    *
    * @param element
    *    The broker connections configuration that creates this federation queue policy.
    * @param wildcards
    *    The configured wild-card settings for the broker or defaults.
    *
    * @return a new queue match and handling policy for use in the broker connection
    */
   public static FederationReceiveFromQueuePolicy create(AMQPFederationQueuePolicyElement element, WildcardConfiguration wildcards) {
      final Set<Map.Entry<String, String>> includes;
      final Set<Map.Entry<String, String>> excludes;

      if (element.getIncludes() != null && !element.getIncludes().isEmpty()) {
         includes = new HashSet<>(element.getIncludes().size());

         element.getIncludes().forEach(queueMatch ->
            includes.add(new AbstractMap.SimpleImmutableEntry<String, String>(queueMatch.getAddressMatch(), queueMatch.getQueueMatch())));
      } else {
         includes = Collections.emptySet();
      }

      if (element.getExcludes() != null && !element.getExcludes().isEmpty()) {
         excludes = new HashSet<>(element.getExcludes().size());

         element.getExcludes().forEach(queueMatch ->
            excludes.add(new AbstractMap.SimpleImmutableEntry<String, String>(queueMatch.getAddressMatch(), queueMatch.getQueueMatch())));
      } else {
         excludes = Collections.emptySet();
      }

      // We translate from broker configuration to actual implementation to avoid any coupling here
      // as broker configuration could change and or be updated.

      final FederationReceiveFromQueuePolicy policy = new FederationReceiveFromQueuePolicy(
         element.getName(),
         element.isIncludeFederated(),
         Objects.requireNonNullElse(element.getPriorityAdjustment(), DEFAULT_QUEUE_RECEIVER_PRIORITY_ADJUSTMENT),
         includes,
         excludes,
         element.getProperties(),
         element.getTransformerConfiguration(),
         wildcards);

      return policy;
   }
}
