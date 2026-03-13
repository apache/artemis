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

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationAddressPolicyElement.AddressMatch;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement.QueueMatch;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromAddressPolicy;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationReceiveFromQueuePolicy;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.WritableBuffer;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.core.config.WildcardConfiguration.DEFAULT_WILDCARD_CONFIGURATION;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for basic error checking and expected outcomes of the federation policy support class.
 */
public class AMQPFederationPolicySupportTest {

   @Test
   public void testEncodeReceiveFromQueuePolicy() {
      final Set<Map.Entry<String, String>> includes = new LinkedHashSet<>();
      includes.add(new SimpleEntry<>("a", "b"));
      includes.add(new SimpleEntry<>("c", "d"));
      final Set<Map.Entry<String, String>> excludes = new LinkedHashSet<>();
      excludes.add(new SimpleEntry<>("e", "f"));
      excludes.add(new SimpleEntry<>("g", "h"));
      final Map<String, Object> properties1 = new HashMap<>();
      properties1.put("amqpCredits", "10");
      properties1.put("amqpLowCredits", "3");
      final Map<String, Object> properties2 = new HashMap<>();
      properties2.put("amqpCredits", 10);
      properties2.put("amqpLowCredits", 3);

      doTestEncodeReceiveFromQueuePolicy("test", false, 0, includes, excludes, properties1);
      doTestEncodeReceiveFromQueuePolicy("test", true, 5, includes, excludes, properties2);
      doTestEncodeReceiveFromQueuePolicy("test", false, -5, includes, excludes, null);
      doTestEncodeReceiveFromQueuePolicy("test", true, 5, null, excludes, properties2);
      doTestEncodeReceiveFromQueuePolicy("test", true, 5, includes, null, properties2);
      doTestEncodeReceiveFromQueuePolicy("test", true, 5, Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
   }

   @Test
   public void testEncodeReceiveFromQueuePolicyNoExcludes() {
      final Set<Map.Entry<String, String>> includes = new LinkedHashSet<>();
      includes.add(new SimpleEntry<>("a", "b"));
      includes.add(new SimpleEntry<>("c", "d"));

      doTestEncodeReceiveFromQueuePolicy("includes", false, 0, includes, null, null);
   }

   @Test
   public void testEncodeReceiveFromQueuePolicyNoIncludes() {
      final Set<Map.Entry<String, String>> excludes = new LinkedHashSet<>();
      excludes.add(new SimpleEntry<>("e", "f"));
      excludes.add(new SimpleEntry<>("g", "h"));

      doTestEncodeReceiveFromQueuePolicy("excludes", false, 0, null, excludes, null);
   }

   @Test
   public void testEncodeReceiveFromQueuePolicyNullsAndEmptyStrings() {
      final Set<Map.Entry<String, String>> includes = new LinkedHashSet<>();
      includes.add(new SimpleEntry<>(null, "b"));
      includes.add(new SimpleEntry<>("", "d"));
      final Set<Map.Entry<String, String>> excludes = new LinkedHashSet<>();
      excludes.add(new SimpleEntry<>("e", ""));
      excludes.add(new SimpleEntry<>("g", null));

      doTestEncodeReceiveFromQueuePolicy("excludes", false, 0, includes, excludes, null);
   }

   @SuppressWarnings("unchecked")
   private void doTestEncodeReceiveFromQueuePolicy(String name,
                                                   boolean includeFederated, int priorityAdjustment,
                                                   Collection<Map.Entry<String, String>> includes,
                                                   Collection<Map.Entry<String, String>> excludes,
                                                   Map<String, Object> policyProperties) {
      final FederationReceiveFromQueuePolicy policy = new FederationReceiveFromQueuePolicy(
         name, includeFederated, priorityAdjustment, includes, excludes, policyProperties, null, DEFAULT_WILDCARD_CONFIGURATION);

      final AMQPMessage message = AMQPFederationPolicySupport.encodeQueuePolicyControlMessage(policy);

      assertEquals(ADD_QUEUE_POLICY, message.getAnnotation(SimpleString.of(OPERATION_TYPE.toString())));

      assertNotNull(message.getBody());
      assertInstanceOf(AmqpValue.class, message.getBody());
      assertInstanceOf(Map.class, ((AmqpValue) message.getBody()).getValue());

      final Map<String, Object> policyMap = (Map<String, Object>) ((AmqpValue) message.getBody()).getValue();

      assertEquals(name, policyMap.get(POLICY_NAME));
      assertEquals(includeFederated, policyMap.get(QUEUE_INCLUDE_FEDERATED));
      assertEquals(priorityAdjustment, policyMap.get(QUEUE_PRIORITY_ADJUSTMENT));

      if (includes == null || includes.isEmpty()) {
         assertFalse(policyMap.containsKey(QUEUE_INCLUDES));
      } else {
         assertTrue(policyMap.containsKey(QUEUE_INCLUDES));
         assertInstanceOf(List.class, policyMap.get(QUEUE_INCLUDES));

         final List<String> flattenedIncludes = (List<String>) policyMap.get(QUEUE_INCLUDES);

         assertEquals(includes.size() * 2, flattenedIncludes.size());

         for (int i = 0; i < flattenedIncludes.size(); ) {
            assertTrue(includes.contains(new SimpleEntry<>(flattenedIncludes.get(i++), flattenedIncludes.get(i++))));
         }
      }

      if (excludes == null || excludes.isEmpty()) {
         assertFalse(policyMap.containsKey(QUEUE_EXCLUDES));
      } else {
         assertTrue(policyMap.containsKey(QUEUE_EXCLUDES));
         assertInstanceOf(List.class, policyMap.get(QUEUE_EXCLUDES));

         final List<String> flattenedExcludes = (List<String>) policyMap.get(QUEUE_EXCLUDES);

         assertEquals(excludes.size() * 2, flattenedExcludes.size());

         for (int i = 0; i < flattenedExcludes.size(); ) {
            assertTrue(excludes.contains(new SimpleEntry<>(flattenedExcludes.get(i++), flattenedExcludes.get(i++))));
         }
      }

      if (policyProperties == null || policyProperties.isEmpty()) {
         assertFalse(policyMap.containsKey(POLICY_PROPERTIES_MAP));
      } else {
         assertTrue(policyMap.containsKey(POLICY_PROPERTIES_MAP));
         assertInstanceOf(Map.class, policyMap.get(POLICY_PROPERTIES_MAP));

         final Map<String, String> encodedProperties = (Map<String, String>) policyMap.get(POLICY_PROPERTIES_MAP);

         assertEquals(policyProperties.size(), encodedProperties.size());

         policyProperties.forEach((k, v) -> {
            assertTrue(encodedProperties.containsKey(k));
            assertEquals(v, encodedProperties.get(k));
         });
      }
   }

   @Test
   public void testEncodeReceiveFromAddressPolicy() {
      final Set<String> includes = new LinkedHashSet<>();
      includes.add("a");
      includes.add("b");
      includes.add("c");
      includes.add("d");
      final Set<String> excludes = new LinkedHashSet<>();
      excludes.add("e");
      includes.add("f");
      includes.add("g");
      includes.add("h");
      includes.add("I");
      final Map<String, Object> properties1 = new HashMap<>();
      properties1.put("amqpCredits", "10");
      properties1.put("amqpLowCredits", "3");
      final Map<String, Object> properties2 = new HashMap<>();
      properties2.put("amqpCredits", 10);
      properties2.put("amqpLowCredits", 3);

      doTestEncodeReceiveFromAddressPolicy("test", false, 0, 1, 2, true, false, includes, excludes, properties1);
      doTestEncodeReceiveFromAddressPolicy("test", true, 1, 3, 2, false, true, includes, excludes, null);
      doTestEncodeReceiveFromAddressPolicy("test", false, 2, 4, -1, false, false, includes, excludes, properties2);
      doTestEncodeReceiveFromAddressPolicy("test", true, 7, -1, 255, true, true, includes, excludes, null);
      doTestEncodeReceiveFromAddressPolicy("test", false, 2, 4, -1, false, true, null, excludes, properties2);
      doTestEncodeReceiveFromAddressPolicy("test", true, 7, -1, 255, true, false, includes, null, null);
      doTestEncodeReceiveFromAddressPolicy("test", true, 7, -1, 255, true, true, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
   }

   @SuppressWarnings("unchecked")
   private void doTestEncodeReceiveFromAddressPolicy(String name,
                                                     boolean autoDelete,
                                                     long autoDeleteDelay,
                                                     long autoDeleteMessageCount,
                                                     int maxHops,
                                                     boolean enableDivertBindings,
                                                     boolean allowWildcardGroupings,
                                                     Collection<String> includes,
                                                     Collection<String> excludes,
                                                     Map<String, Object> policyProperties) {
      final FederationReceiveFromAddressPolicy policy = new FederationReceiveFromAddressPolicy(
         name, autoDelete, autoDeleteDelay, autoDeleteMessageCount, maxHops, enableDivertBindings,
         allowWildcardGroupings, includes, excludes, policyProperties, null, DEFAULT_WILDCARD_CONFIGURATION);

      final AMQPMessage message = AMQPFederationPolicySupport.encodeAddressPolicyControlMessage(policy);

      assertEquals(ADD_ADDRESS_POLICY, message.getAnnotation(SimpleString.of(OPERATION_TYPE.toString())));

      assertNotNull(message.getBody());
      assertInstanceOf(AmqpValue.class, message.getBody());
      assertInstanceOf(Map.class, ((AmqpValue) message.getBody()).getValue());

      final Map<String, Object> policyMap = (Map<String, Object>) ((AmqpValue) message.getBody()).getValue();

      assertEquals(name, policyMap.get(POLICY_NAME));
      assertEquals(autoDelete, policyMap.get(ADDRESS_AUTO_DELETE));
      assertEquals(autoDeleteDelay, policyMap.get(ADDRESS_AUTO_DELETE_DELAY));
      assertEquals(autoDeleteMessageCount, policyMap.get(ADDRESS_AUTO_DELETE_MSG_COUNT));
      assertEquals(maxHops, policyMap.get(ADDRESS_MAX_HOPS));
      assertEquals(enableDivertBindings, policyMap.get(ADDRESS_ENABLE_DIVERT_BINDINGS));
      assertEquals(allowWildcardGroupings, policyMap.get(ADDRESS_ALLOW_WILDCARD_GROUPINGS));

      if (includes == null || includes.isEmpty()) {
         assertFalse(policyMap.containsKey(ADDRESS_INCLUDES));
      } else {
         assertTrue(policyMap.containsKey(ADDRESS_INCLUDES));
         assertInstanceOf(List.class, policyMap.get(ADDRESS_INCLUDES));

         final List<String> encodedIncludes = (List<String>) policyMap.get(ADDRESS_INCLUDES);

         assertEquals(includes.size(), encodedIncludes.size());

         for (int i = 0; i < encodedIncludes.size(); ++i) {
            assertTrue(includes.contains(encodedIncludes.get(i)));
         }
      }

      if (excludes == null || excludes.isEmpty()) {
         assertFalse(policyMap.containsKey(ADDRESS_EXCLUDES));
      } else {
         assertTrue(policyMap.containsKey(ADDRESS_EXCLUDES));
         assertInstanceOf(List.class, policyMap.get(ADDRESS_EXCLUDES));

         final List<String> encodedExcludes = (List<String>) policyMap.get(ADDRESS_EXCLUDES);

         assertEquals(excludes.size(), encodedExcludes.size());

         for (int i = 0; i < encodedExcludes.size(); ++i) {
            assertTrue(excludes.contains(encodedExcludes.get(i)));
         }
      }

      if (policyProperties == null || policyProperties.isEmpty()) {
         assertFalse(policyMap.containsKey(POLICY_PROPERTIES_MAP));
      } else {
         assertTrue(policyMap.containsKey(POLICY_PROPERTIES_MAP));
         assertInstanceOf(Map.class, policyMap.get(POLICY_PROPERTIES_MAP));

         final Map<String, String> encodedProperties = (Map<String, String>) policyMap.get(POLICY_PROPERTIES_MAP);

         assertEquals(policyProperties.size(), encodedProperties.size());

         policyProperties.forEach((k, v) -> {
            assertTrue(encodedProperties.containsKey(k));
            assertEquals(v, encodedProperties.get(k));
         });
      }
   }

   @Test
   public void testDecodeReceiveFromQueuePolicyWithSingleIncludeAndExclude() throws ActiveMQException {
      final Set<Map.Entry<String, String>> includes = new LinkedHashSet<>();
      includes.add(new SimpleEntry<>("a", "b"));
      final Set<Map.Entry<String, String>> excludes = new LinkedHashSet<>();
      excludes.add(new SimpleEntry<>("c", "d"));

      doTestDecodeReceiveFromQueuePolicy("address", "test", false, 0, includes, excludes, null);
   }

   @Test
   public void testDecodeReceiveFromQueuePolicyWithNullMatches() throws ActiveMQException {
      final Set<Map.Entry<String, String>> includes = new LinkedHashSet<>();
      includes.add(new SimpleEntry<>("a", null));
      final Set<Map.Entry<String, String>> excludes = new LinkedHashSet<>();
      excludes.add(new SimpleEntry<>(null, "b"));

      doTestDecodeReceiveFromQueuePolicy("address", "test", false, 0, includes, excludes, null);
   }

   @Test
   public void testDecodeReceiveFromQueuePolicy() throws ActiveMQException {
      final Set<Map.Entry<String, String>> includes = new LinkedHashSet<>();
      includes.add(new SimpleEntry<>("a", "b"));
      includes.add(new SimpleEntry<>("c", "d"));
      final Set<Map.Entry<String, String>> excludes = new LinkedHashSet<>();
      excludes.add(new SimpleEntry<>("e", "f"));
      excludes.add(new SimpleEntry<>("g", "h"));
      final Map<String, String> properties = new HashMap<>();
      properties.put("amqpCredits", "10");
      properties.put("amqpLowCredits", "3");

      doTestDecodeReceiveFromQueuePolicy("address", "test", false, 0, includes, excludes, null);
      doTestDecodeReceiveFromQueuePolicy("address", "test", true, -5, includes, excludes, properties);
      doTestDecodeReceiveFromQueuePolicy("address", "test", false, 5, includes, excludes, null);
      doTestDecodeReceiveFromQueuePolicy("address", "test", true, -5, includes, null, properties);
      doTestDecodeReceiveFromQueuePolicy("address", "test", true, -5, null, excludes, properties);
      doTestDecodeReceiveFromQueuePolicy("address", "test", true, -5, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
   }

   private void doTestDecodeReceiveFromQueuePolicy(String address, String name,
                                                   boolean includeFederated,
                                                   int priorityAdjustment,
                                                   Collection<Map.Entry<String, String>> includes,
                                                   Collection<Map.Entry<String, String>> excludes,
                                                   Map<String, String> policyProperties) throws ActiveMQException {
      final Properties properties = new Properties();
      final Map<Symbol, Object> annotations = new LinkedHashMap<>();
      final MessageAnnotations messageAnnotations = new MessageAnnotations(annotations);
      final Map<String, Object> policyMap = new LinkedHashMap<>();
      final Section sectionBody = new AmqpValue(policyMap);

      properties.setTo("address");

      annotations.put(OPERATION_TYPE, ADD_QUEUE_POLICY);

      policyMap.put(POLICY_NAME, name);
      policyMap.put(QUEUE_INCLUDE_FEDERATED, includeFederated);
      policyMap.put(QUEUE_PRIORITY_ADJUSTMENT, priorityAdjustment);

      if (includes != null && !includes.isEmpty()) {
         final List<String> flattenedIncludes = new ArrayList<>(includes.size() * 2);
         includes.forEach((entry) -> {
            flattenedIncludes.add(entry.getKey());
            flattenedIncludes.add(entry.getValue());
         });

         policyMap.put(QUEUE_INCLUDES, flattenedIncludes);
      }

      if (excludes != null && !excludes.isEmpty()) {
         final List<String> flatteneExcludes = new ArrayList<>(excludes.size() * 2);
         excludes.forEach((entry) -> {
            flatteneExcludes.add(entry.getKey());
            flatteneExcludes.add(entry.getValue());
         });

         policyMap.put(QUEUE_EXCLUDES, flatteneExcludes);
      }

      if (policyProperties != null && !policyProperties.isEmpty()) {
         policyMap.put(POLICY_PROPERTIES_MAP, policyProperties);
      }

      final AMQPMessage amqpMessage = encodeFromAMQPTypes(properties, messageAnnotations, sectionBody);

      final FederationReceiveFromQueuePolicy policy =
         AMQPFederationPolicySupport.decodeReceiveFromQueuePolicy(amqpMessage, DEFAULT_WILDCARD_CONFIGURATION);

      checkPolicyMatchesExpectations(policy, name, includeFederated, priorityAdjustment, includes, excludes, policyProperties);
   }

   @Test
   public void testDecodeReceiveFromAddressPolicy() throws ActiveMQException {
      final Set<String> includes = new LinkedHashSet<>();
      includes.add("a");
      includes.add("b");
      includes.add("c");
      includes.add("d");
      final Set<String> excludes = new LinkedHashSet<>();
      excludes.add("e");
      includes.add("f");
      includes.add("g");
      includes.add("h");
      includes.add("I");
      final Map<String, String> properties = new HashMap<>();
      properties.put("amqpCredits", "10");
      properties.put("amqpLowCredits", "3");

      doTestDecodeReceiveFromAddressPolicy("address", "test", false, 0, 1, 2, true, false, includes, excludes, null);
      doTestDecodeReceiveFromAddressPolicy("address", "test", false, 0, 1, 2, true, true, includes, excludes, properties);
      doTestDecodeReceiveFromAddressPolicy("address", "test", false, 0, 1, 2, true, true, null, excludes, null);
      doTestDecodeReceiveFromAddressPolicy("address", "test", false, 0, 1, 2, true, false, includes, null, properties);
      doTestDecodeReceiveFromAddressPolicy("address", "test", false, 0, 1, 2, true, true, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
   }

   private void doTestDecodeReceiveFromAddressPolicy(String address, String name,
                                                     boolean autoDelete,
                                                     long autoDeleteDelay,
                                                     long autoDeleteMessageCount,
                                                     int maxHops,
                                                     boolean enableDivertBindings,
                                                     boolean allowWildcardGroupings,
                                                     Collection<String> includes,
                                                     Collection<String> excludes,
                                                     Map<String, String> policyProperties) throws ActiveMQException {

      final Properties properties = new Properties();
      final Map<Symbol, Object> annotations = new LinkedHashMap<>();
      final MessageAnnotations messageAnnotations = new MessageAnnotations(annotations);
      final Map<String, Object> policyMap = new LinkedHashMap<>();
      final Section sectionBody = new AmqpValue(policyMap);

      properties.setTo("address");

      annotations.put(OPERATION_TYPE, ADD_ADDRESS_POLICY);

      policyMap.put(POLICY_NAME, name);
      policyMap.put(ADDRESS_AUTO_DELETE, autoDelete);
      policyMap.put(ADDRESS_AUTO_DELETE_DELAY, autoDeleteDelay);
      policyMap.put(ADDRESS_AUTO_DELETE_MSG_COUNT, autoDeleteMessageCount);
      policyMap.put(ADDRESS_MAX_HOPS, maxHops);
      policyMap.put(ADDRESS_ENABLE_DIVERT_BINDINGS, enableDivertBindings);
      policyMap.put(ADDRESS_ALLOW_WILDCARD_GROUPINGS, allowWildcardGroupings);

      if (includes != null && !includes.isEmpty()) {
         policyMap.put(ADDRESS_INCLUDES, new ArrayList<>(includes));
      }
      if (excludes != null && !excludes.isEmpty()) {
         policyMap.put(ADDRESS_EXCLUDES, new ArrayList<>(excludes));
      }
      if (policyProperties != null && !policyProperties.isEmpty()) {
         policyMap.put(POLICY_PROPERTIES_MAP, policyProperties);
      }

      final AMQPMessage amqpMessage = encodeFromAMQPTypes(properties, messageAnnotations, sectionBody);

      final FederationReceiveFromAddressPolicy policy =
         AMQPFederationPolicySupport.decodeReceiveFromAddressPolicy(amqpMessage, DEFAULT_WILDCARD_CONFIGURATION);

      checkPolicyMatchesExpectations(policy, name, autoDelete, autoDeleteDelay, autoDeleteMessageCount, maxHops,
                                     enableDivertBindings, allowWildcardGroupings, includes, excludes, policyProperties);
   }

   @Test
   public void testDecodeOfQueuePolicyWithOddNumberOfIncludes() throws ActiveMQException {
      final Properties properties = new Properties();
      final Map<Symbol, Object> annotations = new LinkedHashMap<>();
      final MessageAnnotations messageAnnotations = new MessageAnnotations(annotations);
      final Map<String, Object> policyMap = new LinkedHashMap<>();
      final Section sectionBody = new AmqpValue(policyMap);

      properties.setTo("address");

      annotations.put(OPERATION_TYPE, ADD_ADDRESS_POLICY);

      policyMap.put(POLICY_NAME, "test");
      policyMap.put(QUEUE_INCLUDE_FEDERATED, false);
      policyMap.put(QUEUE_PRIORITY_ADJUSTMENT, 0);

      final List<String> includes = new ArrayList<>();
      includes.add("a");
      includes.add("b");
      includes.add("c");

      policyMap.put(QUEUE_INCLUDE_FEDERATED, includes);

      final AMQPMessage amqpMessage = encodeFromAMQPTypes(properties, messageAnnotations, sectionBody);

      assertThrows(ActiveMQException.class, () ->
         AMQPFederationPolicySupport.decodeReceiveFromQueuePolicy(amqpMessage, DEFAULT_WILDCARD_CONFIGURATION));
   }

   @Test
   public void testCreateQueuePolicyFromConfigurationElement() throws ActiveMQException {
      final Set<Map.Entry<String, String>> includes = new LinkedHashSet<>();
      includes.add(new SimpleEntry<>("a", "b"));
      includes.add(new SimpleEntry<>("c", "d"));
      final Set<Map.Entry<String, String>> excludes = new LinkedHashSet<>();
      excludes.add(new SimpleEntry<>("e", "f"));
      excludes.add(new SimpleEntry<>("g", "h"));
      final Map<String, Object> properties1 = new HashMap<>();
      properties1.put("amqpCredits", "10");
      properties1.put("amqpLowCredits", "3");
      final Map<String, Object> properties2 = new HashMap<>();
      properties2.put("amqpCredits", 10);
      properties2.put("amqpLowCredits", 3);

      doTestCreateQueuePolicyFromConfigurationElement("test", false, 0, includes, excludes, properties1);
      doTestCreateQueuePolicyFromConfigurationElement("test", true, 5, includes, excludes, properties2);
      doTestCreateQueuePolicyFromConfigurationElement("test", false, -5, includes, excludes, null);
      doTestCreateQueuePolicyFromConfigurationElement("test", true, 5, null, excludes, properties1);
      doTestCreateQueuePolicyFromConfigurationElement("test", true, 5, includes, null, properties2);
      doTestCreateQueuePolicyFromConfigurationElement("test", false, 5, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
   }

   private void doTestCreateQueuePolicyFromConfigurationElement(String name,
                                                                boolean includeFederated,
                                                                int priorityAdjustment,
                                                                Collection<Map.Entry<String, String>> includes,
                                                                Collection<Map.Entry<String, String>> excludes,
                                                                Map<String, Object> policyProperties) throws ActiveMQException {
      final AMQPFederationQueuePolicyElement element = new AMQPFederationQueuePolicyElement();

      element.setName(name);
      element.setPriorityAdjustment(priorityAdjustment);
      element.setIncludeFederated(includeFederated);
      element.setProperties(policyProperties);

      if (includes != null) {
         includes.forEach(inc -> element.addInclude(new QueueMatch().setAddressMatch(inc.getKey())
                                                                    .setQueueMatch(inc.getValue())
                                                                    .setName(UUID.randomUUID().toString())));
      }

      if (excludes != null) {
         excludes.forEach(ex -> element.addExclude(new QueueMatch().setAddressMatch(ex.getKey())
                                                                   .setQueueMatch(ex.getValue())
                                                                   .setName(UUID.randomUUID().toString())));
      }

      final FederationReceiveFromQueuePolicy policy = AMQPFederationPolicySupport.create(element, DEFAULT_WILDCARD_CONFIGURATION);

      checkPolicyMatchesExpectations(policy, name, includeFederated, priorityAdjustment, includes, excludes, policyProperties);
   }

   @Test
   public void testCreateAddressPolicyFromConfigurationElement() throws ActiveMQException {
      final Set<String> includes = new LinkedHashSet<>();
      includes.add("a");
      includes.add("b");
      includes.add("c");
      includes.add("d");
      final Set<String> excludes = new LinkedHashSet<>();
      excludes.add("e");
      includes.add("f");
      includes.add("g");
      includes.add("h");
      includes.add("I");
      final Map<String, Object> properties = new HashMap<>();
      properties.put("amqpCredits", "10");
      properties.put("amqpLowCredits", "3");

      doTestCreateAddressPolicyFromConfigurationElement("test", false, 0, 1, 2, true, false, includes, excludes, null);
      doTestCreateAddressPolicyFromConfigurationElement("test", true, 1, 2, 3, true, true, includes, excludes, properties);
      doTestCreateAddressPolicyFromConfigurationElement("test", false, 10, 9, 8, false, true, null, excludes, properties);
      doTestCreateAddressPolicyFromConfigurationElement("test", true, 1, 1, 1, false, false, includes, null, null);
      doTestCreateAddressPolicyFromConfigurationElement("test", false, 7, 1, 1, true, true, null, null, properties);
      doTestCreateAddressPolicyFromConfigurationElement("test", false, 7, 1, 1, true, false, Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
   }

   private void doTestCreateAddressPolicyFromConfigurationElement(String name,
                                                                  boolean autoDelete,
                                                                  long autoDeleteDelay,
                                                                  long autoDeleteMessageCount,
                                                                  int maxHops,
                                                                  boolean enableDivertBindings,
                                                                  boolean allowWildcardGroupings,
                                                                  Collection<String> includes,
                                                                  Collection<String> excludes,
                                                                  Map<String, Object> policyProperties) throws ActiveMQException {

      final AMQPFederationAddressPolicyElement element = new AMQPFederationAddressPolicyElement();

      element.setName(name);
      element.setAutoDelete(autoDelete);
      element.setAutoDeleteDelay(autoDeleteDelay);
      element.setAutoDeleteMessageCount(autoDeleteMessageCount);
      element.setMaxHops(maxHops);
      element.setEnableDivertBindings(enableDivertBindings);
      element.setAllowWildcardGroupings(allowWildcardGroupings);
      element.setProperties(policyProperties);

      if (includes != null) {
         includes.forEach(inc -> element.addInclude(new AddressMatch().setAddressMatch(inc).setName(UUID.randomUUID().toString())));
      }

      if (excludes != null) {
         excludes.forEach(ex -> element.addExclude(new AddressMatch().setAddressMatch(ex).setName(UUID.randomUUID().toString())));
      }

      final FederationReceiveFromAddressPolicy policy = AMQPFederationPolicySupport.create(element, DEFAULT_WILDCARD_CONFIGURATION);

      checkPolicyMatchesExpectations(policy, name, autoDelete, autoDeleteDelay, autoDeleteMessageCount, maxHops,
                                     enableDivertBindings, allowWildcardGroupings, includes, excludes, policyProperties);
   }

   private void checkPolicyMatchesExpectations(FederationReceiveFromAddressPolicy policy,
                                               String name, boolean autoDelete, long autoDeleteDelay,
                                               long autoDeleteMessageCount, int maxHops,
                                               boolean enableDivertBindings, boolean enableWildcardSubscriptions,
                                               Collection<?> includes, Collection<?> excludes,
                                               Map<String, ?> policyProperties) {
      assertEquals(name, policy.getPolicyName());
      assertEquals(autoDelete, policy.isAutoDelete());
      assertEquals(autoDeleteDelay, policy.getAutoDeleteDelay());
      assertEquals(autoDeleteMessageCount, policy.getAutoDeleteMessageCount());
      assertEquals(maxHops, policy.getMaxHops());
      assertEquals(enableDivertBindings, policy.isEnableDivertBindings());
      assertEquals(enableWildcardSubscriptions, policy.isAllowWildcardGroupings());

      if (includes == null || includes.isEmpty()) {
         assertTrue(policy.getIncludes().isEmpty());
      } else {
         assertEquals(includes.size(), policy.getIncludes().size());
         includes.forEach((include) -> assertTrue(policy.getIncludes().contains(include)));
      }

      if (excludes == null || excludes.isEmpty()) {
         assertTrue(policy.getExcludes().isEmpty());
      } else {
         assertEquals(excludes.size(), policy.getExcludes().size());
         excludes.forEach((exclude) -> assertTrue(policy.getExcludes().contains(exclude)));
      }

      if (policyProperties == null || policyProperties.isEmpty()) {
         assertTrue(policy.getProperties().isEmpty());
      } else {
         policyProperties.forEach((k, v) -> {
            assertTrue(policy.getProperties().containsKey(k));
            assertEquals(v, policy.getProperties().get(k));
         });
      }
   }

   private void checkPolicyMatchesExpectations(FederationReceiveFromQueuePolicy policy,
                                               String name, boolean includeFederated, int priorityAdjustment,
                                               Collection<?> includes, Collection<?> excludes,
                                               Map<String, ?> policyProperties) {

      assertEquals(name, policy.getPolicyName());
      assertEquals(includeFederated, policy.isIncludeFederated());
      assertEquals(priorityAdjustment, policy.getPriorityAjustment());

      if (includes == null || includes.isEmpty()) {
         assertTrue(policy.getIncludes().isEmpty());
      } else {
         assertEquals(includes.size(), policy.getIncludes().size());
         includes.forEach((include) -> assertTrue(policy.getIncludes().contains(include)));
      }

      if (excludes == null || excludes.isEmpty()) {
         assertTrue(policy.getExcludes().isEmpty());
      } else {
         assertEquals(excludes.size(), policy.getExcludes().size());
         excludes.forEach((exclude) -> assertTrue(policy.getExcludes().contains(exclude)));
      }

      if (policyProperties == null || policyProperties.isEmpty()) {
         assertTrue(policy.getProperties().isEmpty());
      } else {
         policyProperties.forEach((k, v) -> {
            assertTrue(policy.getProperties().containsKey(k));
            assertEquals(v, policy.getProperties().get(k));
         });
      }
   }

   private AMQPMessage encodeFromAMQPTypes(Properties properties, MessageAnnotations messageAnnotations, Section sectionBody) {
      final ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);

      try {
         final EncoderImpl encoder = TLSEncode.getEncoder();
         encoder.setByteBuffer(new NettyWritable(buffer));
         encoder.writeObject(messageAnnotations);
         encoder.writeObject(properties);
         encoder.writeObject(sectionBody);

         final byte[] data = new byte[buffer.writerIndex()];
         buffer.readBytes(data);

         final AMQPMessage amqpMessage = new AMQPStandardMessage(0, data, null);
         amqpMessage.getProperties().setTo("test");
         amqpMessage.setAddress("test");

         return amqpMessage;
      } finally {
         TLSEncode.getEncoder().setByteBuffer((WritableBuffer) null);
         buffer.release();
      }
   }
}
