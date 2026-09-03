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

package org.apache.activemq.artemis.protocol.amqp.federation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.settings.impl.Match;

/**
 * Policy used to provide federation of remote to local broker queues, once created the policy configuration is
 * immutable.
 */
public class FederationReceiveFromQueuePolicy implements FederationReceiveFromResourcePolicy, BiPredicate<String, String> {

   private final Set<QueueMatcher> includeMatchers = new LinkedHashSet<>();
   private final Set<QueueMatcher> excludeMatchers = new LinkedHashSet<>();

   private final Collection<Map.Entry<String, String>> includes;
   private final Collection<Map.Entry<String, String>> excludes;

   private final String policyName;
   private final boolean autoCreate;
   private final boolean autoDelete;
   private final long autoDeleteDelay;
   private final long autoDeleteMessageCount;
   private final boolean includeFederated;
   private final int priorityAdjustment;
   private final Map<String, Object> properties;
   private final TransformerConfiguration transformerConfig;

   public FederationReceiveFromQueuePolicy(String name, boolean autoCreate, boolean autoDelete,
                                           long autoDeleteDelay, long autoDeleteMessageCount,
                                           boolean includeFederated, int priorotyAdjustment,
                                           Collection<Map.Entry<String, String>> includeQueues,
                                           Collection<Map.Entry<String, String>> excludeQueues,
                                           Map<String, Object> properties, TransformerConfiguration transformerConfig,
                                           WildcardConfiguration wildcardConfig) {
      Objects.requireNonNull(name, "The provided policy name cannot be null");
      Objects.requireNonNull(wildcardConfig, "The provided wild card configuration cannot be null");

      this.policyName = name;
      this.autoCreate = autoCreate;
      this.autoDelete = autoDelete;
      this.autoDeleteDelay = autoDeleteDelay;
      this.autoDeleteMessageCount = autoDeleteMessageCount;
      this.includeFederated = includeFederated;
      this.priorityAdjustment = priorotyAdjustment;
      this.transformerConfig = transformerConfig;
      this.includes = Collections.unmodifiableCollection(Objects.requireNonNullElse(includeQueues, Collections.emptyList()));
      this.excludes = Collections.unmodifiableCollection(Objects.requireNonNullElse(excludeQueues, Collections.emptyList()));

      if (properties == null || properties.isEmpty()) {
         this.properties = Collections.emptyMap();
      } else {
         this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
      }

      // Create Matchers from configured includes and excludes for use when matching broker resources
      includes.forEach((entry) -> includeMatchers.add(new QueueMatcher(entry.getKey(), entry.getValue(), wildcardConfig)));
      excludes.forEach((entry) -> excludeMatchers.add(new QueueMatcher(entry.getKey(), entry.getValue(), wildcardConfig)));
   }

   @Override
   public FederationType getPolicyType() {
      return FederationType.QUEUE_FEDERATION;
   }

   @Override
   public String getPolicyName() {
      return policyName;
   }

   public boolean isAutoCreate() {
      return autoCreate;
   }

   public boolean isAutoDelete() {
      return autoDelete;
   }

   public long getAutoDeleteDelay() {
      return autoDeleteDelay;
   }

   public long getAutoDeleteMessageCount() {
      return autoDeleteMessageCount;
   }

   public boolean isIncludeFederated() {
      return includeFederated;
   }

   public int getPriorityAjustment() {
      return priorityAdjustment;
   }

   public Collection<Map.Entry<String, String>> getIncludes() {
      return includes;
   }

   public Collection<Map.Entry<String, String>> getExcludes() {
      return excludes;
   }

   @Override
   public Map<String, Object> getProperties() {
      return properties;
   }

   @Override
   public TransformerConfiguration getTransformerConfiguration() {
      return transformerConfig;
   }

   public boolean testQueue(String queue) {
      for (QueueMatcher matcher : excludeMatchers) {
         if (matcher.testQueue(queue)) {
            return false;
         }
      }

      for (QueueMatcher matcher : includeMatchers) {
         if (matcher.testQueue(queue)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean test(String address, String queue) {
      for (QueueMatcher matcher : excludeMatchers) {
         if (matcher.test(address, queue)) {
            return false;
         }
      }

      for (QueueMatcher matcher : includeMatchers) {
         if (matcher.test(address, queue)) {
            return true;
         }
      }

      return false;
   }

   private class QueueMatcher implements BiPredicate<String, String> {

      private final Predicate<String> addressMatch;
      private final Predicate<String> queueMatch;

      QueueMatcher(String address, String queue, WildcardConfiguration wildcardConfig) {
         if (address == null || address.isEmpty()) {
            addressMatch = (target) -> true;
         } else {
            addressMatch = new Match<>(address, null, wildcardConfig).getPattern().asPredicate();
         }

         if (queue == null || queue.isEmpty()) {
            queueMatch = (target) -> true;
         } else {
            queueMatch = new Match<>(queue, null, wildcardConfig).getPattern().asPredicate();
         }
      }

      @Override
      public boolean test(String address, String queue) {
         return testAddress(address) && testQueue(queue);
      }

      public boolean testAddress(String address) {
         return addressMatch.test(address);
      }

      public boolean testQueue(String queue) {
         return queueMatch.test(queue);
      }
   }
}
