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
package org.apache.activemq.artemis.tests.unit.ra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.jms.Session;
import javax.resource.spi.InvalidPropertyException;

import org.apache.activemq.artemis.ra.inflow.ActiveMQActivationValidationUtils;
import org.junit.jupiter.api.Test;

public class ActiveMQActivationsSpecTest {

   @Test
   public void nullDestinationName() throws InvalidPropertyException {
      assertThrows(InvalidPropertyException.class, () -> {
         ActiveMQActivationValidationUtils.validate(null, "destinationType", false, "subscriptionName");
      });
   }

   @Test
   public void emptyDestinationName() throws InvalidPropertyException {
      assertThrows(InvalidPropertyException.class, () -> {
         ActiveMQActivationValidationUtils.validate(null, "destinationType", false, "subscriptionName");
      });
   }

   public void nullDestinationType() throws InvalidPropertyException {
      ActiveMQActivationValidationUtils.validate("destinationName", null, false, "subscriptionName");
   }

   @Test
   public void emptyDestinationType() throws InvalidPropertyException {
      assertThrows(InvalidPropertyException.class, () -> {
         ActiveMQActivationValidationUtils.validate("destinationName", "", false, "subscriptionName");
      });
   }

   @Test
   public void subscriptionDurableButNoName() throws InvalidPropertyException {
      assertThrows(InvalidPropertyException.class, () -> {
         ActiveMQActivationValidationUtils.validate("", "", true, "subscriptionName");
      });
   }

   @Test
   public void validateAcknowledgeMode() {
      assertThrows(IllegalArgumentException.class, () -> {
         assertEquals(Session.DUPS_OK_ACKNOWLEDGE, ActiveMQActivationValidationUtils.validateAcknowledgeMode("DUPS_OK_ACKNOWLEDGE"));
         assertEquals(Session.DUPS_OK_ACKNOWLEDGE, ActiveMQActivationValidationUtils.validateAcknowledgeMode("Dups-ok-acknowledge"));
         assertEquals(Session.AUTO_ACKNOWLEDGE, ActiveMQActivationValidationUtils.validateAcknowledgeMode("AUTO_ACKNOWLEDGE"));
         assertEquals(Session.AUTO_ACKNOWLEDGE, ActiveMQActivationValidationUtils.validateAcknowledgeMode("Auto-acknowledge"));
         ActiveMQActivationValidationUtils.validateAcknowledgeMode("Invalid Acknowledge Mode");
      });
   }
}
