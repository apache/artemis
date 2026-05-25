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
package org.apache.activemq.artemis.core.config.impl;

import java.util.EnumSet;

import org.apache.activemq.artemis.core.server.routing.KeyType;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.core.settings.impl.DiskFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.PageFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerThresholdMeasurementUnit;

public final class Validators {

   public interface Validator<T> {
      T validate(String name, T value);
   }

   public static final Validator NO_CHECK = (name, value) -> value;

   public static final Validator<String> NOT_NULL_OR_EMPTY = (name, value) -> {
      if (value == null || value.isEmpty()) {
         throw ActiveMQMessageBundle.BUNDLE.emptyOrNull(name);
      }
      return value;
   };

   public static final Validator<Number> GT_ZERO = (name, value) -> {
      if (value.doubleValue() > 0) {
         return value;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.greaterThanZero(name, value);
      }
   };

   public static final Validator<Number> PERCENTAGE = (name, value) -> {
      if (value == null || (value.intValue() < 0 || value.intValue() > 100)) {
         throw ActiveMQMessageBundle.BUNDLE.notPercent(name, value);
      }
      return value;
   };

   public static final Validator<Number> PERCENTAGE_OR_MINUS_ONE = (name, value) -> {
      if (value == null || ((value.intValue() < 0 || value.intValue() > 100) && value.intValue() != -1)) {
         throw ActiveMQMessageBundle.BUNDLE.notPercentOrMinusOne(name, value);
      }
      return value;
   };

   public static final Validator<Number> GE_ZERO = (name, value) -> {
      if (value.doubleValue() >= 0) {
         return value;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.greaterThanZero(name, value);
      }
   };

   public static final Validator<Number> LE_ONE = (name, value) -> {
      if (value.doubleValue() <= 1) {
         return value;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.lessThanOrEqualToOne(name, value);
      }
   };

   public static final Validator<Number> MINUS_ONE_OR_GT_ZERO = (name, value) -> {
      if (value.doubleValue() == -1 || value.doubleValue() > 0) {
         return value;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.greaterThanMinusOne(name, value);
      }
   };

   public static final Validator<Number> MINUS_ONE_OR_GE_ZERO = (name, value) -> {
      if (value.doubleValue() == -1 || value.doubleValue() >= 0) {
         return value;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.greaterThanZeroOrMinusOne(name, value);
      }
   };

   public static final Validator<Number> POSITIVE_INT = (name, value) -> {
      if (value.longValue() > 0 && value.longValue() <= Integer.MAX_VALUE) {
         return value;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.inRangeOfPositiveInt(name, value);
      }
   };

   public static final Validator<Number> POSITIVE_POWER_OF_TWO = (name, value) -> {
      if ((value.longValue() & (value.longValue() - 1)) == 0 && value.longValue() > 0) {
         return value;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.positivePowerOfTwo(name, value);
      }
   };

   public static final Validator<Number> MINUS_ONE_OR_POSITIVE_INT = (name, value) -> {
      if (value.longValue() == -1 || (value.longValue() > 0 && value.longValue() <= Integer.MAX_VALUE)) {
         return value;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.inRangeOfPositiveIntThanMinusOne(name, value);
      }
   };

   public static final Validator<Number> THREAD_PRIORITY_RANGE = (name, value) -> {
      if (value.intValue() >= Thread.MIN_PRIORITY && value.intValue() <= Thread.MAX_PRIORITY) {
         return value;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.mustbeBetween(name, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY, value);
      }
   };

   public static final Validator<String> JOURNAL_TYPE = (name, value) -> {
      if (value == null || !EnumSet.allOf(JournalType.class).contains(JournalType.valueOf(value))) {
         throw ActiveMQMessageBundle.BUNDLE.invalidJournalType(value);
      }
      return value;
   };

   public static final Validator<String> ADDRESS_FULL_MESSAGE_POLICY_TYPE = (name, value) -> {
      if (value == null || !value.equals(AddressFullMessagePolicy.PAGE.toString()) &&
         !value.equals(AddressFullMessagePolicy.DROP.toString()) &&
         !value.equals(AddressFullMessagePolicy.BLOCK.toString()) &&
         !value.equals(AddressFullMessagePolicy.FAIL.toString())) {
         throw ActiveMQMessageBundle.BUNDLE.invalidAddressFullPolicyType(value);
      }
      return value;
   };

   public static final Validator<String> DISK_FULL_MESSAGE_POLICY_TYPE = (name, value) -> {
      if (value == null || !value.equals(DiskFullMessagePolicy.DROP.toString()) &&
              !value.equals(DiskFullMessagePolicy.BLOCK.toString()) &&
              !value.equals(DiskFullMessagePolicy.FAIL.toString())) {
         throw ActiveMQMessageBundle.BUNDLE.invalidDiskFullPolicyType(value);
      }
      return value;
   };

   public static final Validator<String> PAGE_FULL_MESSAGE_POLICY_TYPE = (name, value) -> {
      if (value == null ||
         !value.equals(PageFullMessagePolicy.DROP.toString()) &&
         !value.equals(PageFullMessagePolicy.FAIL.toString())) {
         throw ActiveMQMessageBundle.BUNDLE.invalidAddressFullPolicyType(value);
      }
      return value;
   };

   public static final Validator<String> SLOW_CONSUMER_THRESHOLD_MEASUREMENT_UNIT = (name, value) -> {
      if (value == null ||
         !value.equals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_SECOND.toString()) &&
         !value.equals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_MINUTE.toString()) &&
         !value.equals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_HOUR.toString()) &&
         !value.equals(SlowConsumerThresholdMeasurementUnit.MESSAGES_PER_DAY.toString())) {
         throw ActiveMQMessageBundle.BUNDLE.invalidSlowConsumerThresholdMeasurementUnit(value);
      }
      return value;
   };

   public static final Validator<String> SLOW_CONSUMER_POLICY_TYPE = (name, value) -> {
      if (value == null || !value.equals(SlowConsumerPolicy.KILL.toString()) && !value.equals(SlowConsumerPolicy.NOTIFY.toString())) {
         throw ActiveMQMessageBundle.BUNDLE.invalidSlowConsumerPolicyType(value);
      }
      return value;
   };

   public static final Validator<String> DELETION_POLICY_TYPE = (name, value) -> {
      if (value == null || !value.equals(DeletionPolicy.OFF.toString()) && !value.equals(DeletionPolicy.FORCE.toString())) {
         throw ActiveMQMessageBundle.BUNDLE.invalidDeletionPolicyType(value);
      }
      return value;
   };

   public static final Validator<String> MESSAGE_LOAD_BALANCING_TYPE = (name, value) -> {
      if (value == null || !value.equals(MessageLoadBalancingType.OFF.toString()) &&
         !value.equals(MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION.toString()) &&
         !value.equals(MessageLoadBalancingType.STRICT.toString()) &&
         !value.equals(MessageLoadBalancingType.ON_DEMAND.toString())) {
         throw ActiveMQMessageBundle.BUNDLE.invalidMessageLoadBalancingType(value);
      }
      return value;
   };

   public static final Validator<String> ROUTING_TYPE = (name, value) -> {
      if (value == null || !value.equals(RoutingType.ANYCAST.toString()) &&
         !value.equals(RoutingType.MULTICAST.toString())) {
         throw ActiveMQMessageBundle.BUNDLE.invalidRoutingType(value);
      }
      return value;
   };

   public static final Validator<String> COMPONENT_ROUTING_TYPE = (name, value) -> {
      if (value == null || !value.equals(ComponentConfigurationRoutingType.ANYCAST.toString()) &&
         !value.equals(ComponentConfigurationRoutingType.MULTICAST.toString()) &&
         !value.equals(ComponentConfigurationRoutingType.PASS.toString()) &&
         !value.equals(ComponentConfigurationRoutingType.STRIP.toString())) {
         throw ActiveMQMessageBundle.BUNDLE.invalidRoutingType(value);
      }
      return value;
   };

   public static final Validator<Integer> MAX_QUEUE_CONSUMERS = (name, value) -> {
      if (value.intValue() < -1) {
         throw ActiveMQMessageBundle.BUNDLE.invalidMaxConsumers(name, value);
      }
      return value;
   };

   public static final Validator<String> KEY_TYPE = (name, value) -> {
      if (value == null || !EnumSet.allOf(KeyType.class).contains(KeyType.valueOf(value))) {
         throw ActiveMQMessageBundle.BUNDLE.invalidConnectionRouterKey(value);
      }
      return value;
   };

   public static final Validator<String> NULL_OR_TWO_CHARACTERS = (name, value) -> {
      if (value != null && value.length() != 2) {
         throw ActiveMQMessageBundle.BUNDLE.wrongLength(name, value, value.length(), 2);
      }
      return value;
   };
}
