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
package org.apache.activemq.artemis.ra.inflow;

import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.resource.spi.InvalidPropertyException;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.ra.ActiveMQRALogger;

public final class ActiveMQActivationValidationUtils {

   private ActiveMQActivationValidationUtils() {
   }

   public static int validateAcknowledgeMode(final String value) {
      if ("DUPS_OK_ACKNOWLEDGE".equalsIgnoreCase(value) || "Dups-ok-acknowledge".equalsIgnoreCase(value)) {
         return Session.DUPS_OK_ACKNOWLEDGE;
      } else if ("AUTO_ACKNOWLEDGE".equalsIgnoreCase(value) || "Auto-acknowledge".equalsIgnoreCase(value)) {
         return Session.AUTO_ACKNOWLEDGE;
      } else {
         throw new IllegalArgumentException(value);
      }
   }

   public static void validate(String destination, String destinationType, Boolean subscriptionDurability,
         String subscriptionName) throws InvalidPropertyException {
      List<String> errorMessages = new ArrayList<>();
      List<PropertyDescriptor> propsNotSet = new ArrayList<>();

      try {
         if (destination == null || destination.trim().isEmpty()) {
            propsNotSet.add(new PropertyDescriptor("destination", ActiveMQActivationSpec.class));
            errorMessages.add("Destination is mandatory.");
         }

         if (destinationType != null && !Topic.class.getName().equals(destinationType)
               && !Queue.class.getName().equals(destinationType)) {
            propsNotSet.add(new PropertyDescriptor("destinationType", ActiveMQActivationSpec.class));
            errorMessages.add("If set, the destinationType must be either 'javax.jms.Topic' or 'javax.jms.Queue'.");
         }

         if ((destinationType == null || destinationType.isEmpty() || Topic.class.getName().equals(destinationType))
               && subscriptionDurability && (subscriptionName == null || subscriptionName.isEmpty())) {
            propsNotSet.add(new PropertyDescriptor("subscriptionName", ActiveMQActivationSpec.class));
            errorMessages.add("If subscription is durable then subscription name must be specified.");
         }
      } catch (IntrospectionException e) {
         ActiveMQRALogger.LOGGER.unableToValidateProperties(e);
      }

      ActiveMQActivationValidationUtils.buildAndThrowExceptionIfNeeded(propsNotSet, errorMessages);
   }

   private static void buildAndThrowExceptionIfNeeded(List<PropertyDescriptor> propsNotSet, List<String> errorMessages)
         throws InvalidPropertyException {
      if (!propsNotSet.isEmpty()) {
         StringBuilder b = new StringBuilder();
         b.append("Invalid settings:");
         for (String errorMessage : errorMessages) {
            b.append(" ");
            b.append(errorMessage);
         }
         InvalidPropertyException e = new InvalidPropertyException(b.toString());
         final PropertyDescriptor[] descriptors = propsNotSet.toArray(new PropertyDescriptor[propsNotSet.size()]);
         e.setInvalidPropertyDescriptors(descriptors);
         throw e;
      }
   }
}
