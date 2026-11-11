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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.Match;
import org.apache.qpid.proton.amqp.Symbol;

/**
 * Policy used for bridging addresses between peers.
 */
public final class AMQPBridgeAddressPolicy extends AMQPBridgePolicy implements BiPredicate<String, RoutingType> {

   private final Set<AddressMatcher> includesMatchers = new LinkedHashSet<>();
   private final Set<AddressMatcher> excludesMatchers = new LinkedHashSet<>();

   private final Collection<String> includes;
   private final Collection<String> excludes;

   private final boolean includeDivertBindings;
   private final boolean useDurableSubscriptions;

   public AMQPBridgeAddressPolicy(String policyName,
                                  boolean includeDivertBindings, boolean useDurableSubscriptions,
                                  Integer priority, String filter, String remoteAddress,
                                  String remoteAddressPrefix, String remoteAddressSuffix,
                                  Collection<Symbol> remoteTerminusCapabilities,
                                  Collection<String> includeAddresses,
                                  Collection<String> excludeAddresses,
                                  Map<String, Object> properties,
                                  TransformerConfiguration transformerConfig,
                                  WildcardConfiguration wildcardConfig) {
      super(policyName, priority, filter, remoteAddress, remoteAddressPrefix, remoteAddressSuffix,
            remoteTerminusCapabilities, properties, transformerConfig, wildcardConfig);

      this.includeDivertBindings = includeDivertBindings;
      this.useDurableSubscriptions = useDurableSubscriptions;

      this.includes = Collections.unmodifiableCollection(Objects.requireNonNullElse(includeAddresses, Collections.emptyList()));
      this.excludes = Collections.unmodifiableCollection(Objects.requireNonNullElse(excludeAddresses, Collections.emptyList()));

      // Create Matchers from configured includes and excludes for use when matching broker resources
      includes.forEach((address) -> includesMatchers.add(new AddressMatcher(address, wildcardConfig)));
      excludes.forEach((address) -> excludesMatchers.add(new AddressMatcher(address, wildcardConfig)));
   }

   public boolean isIncludeDivertBindings() {
      return includeDivertBindings;
   }

   public boolean isUseDurableSubscriptions() {
      return useDurableSubscriptions;
   }

   /**
    * Convenience test method for those who have an {@link AddressInfo} object
    * but don't want to deal with the {@link SimpleString} object or any null
    * checks.
    *
    * @param addressInfo
    *    The address info to check which if null will result in a negative result.
    *
    * @return <code>true</code> if the address value matches this configured policy.
    */
   public boolean test(AddressInfo addressInfo) {
      if (addressInfo != null) {
         return test(addressInfo.getName().toString(), addressInfo.getRoutingType());
      } else {
         return false;
      }
   }

   @Override
   public boolean test(String address, RoutingType type) {
      if (RoutingType.MULTICAST.equals(type)) {
         for (AddressMatcher matcher : excludesMatchers) {
            if (matcher.test(address)) {
               return false;
            }
         }

         for (AddressMatcher matcher : includesMatchers) {
            if (matcher.test(address)) {
               return true;
            }
         }
      }

      return false;
   }

   private static class AddressMatcher implements Predicate<String> {

      private final Predicate<String> matcher;

      AddressMatcher(String address, WildcardConfiguration wildcardConfig) {
         if (address == null || address.isEmpty()) {
            matcher = (target) -> true;
         } else {
            matcher = new Match<>(address, null, wildcardConfig).getPattern().asPredicate();
         }
      }

      @Override
      public boolean test(String address) {
         return matcher.test(address);
      }
   }
}
