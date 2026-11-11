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
package org.apache.activemq.artemis.tests.unit.core.server.cluster.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionBridge;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class ClusterConnectionBridgeTest extends ActiveMQTestBase {

   @Test
   public void testCreateSelectorFromAddressForNormalMatches() {
      String address = "jms.my.address";
      String expectedSelector = ManagementHelper.HDR_ADDRESS + " LIKE '" + address + "%'";
      assertEquals(expectedSelector, ClusterConnectionBridge.createSelectorFromAddress(address));
   }

   @Test
   public void testCreateSelectorFromAddressForExclusions() {
      String address = "jms.my.address";
      String expectedSelector = ManagementHelper.HDR_ADDRESS + " NOT LIKE '" + address + "%'";
      assertEquals(expectedSelector, ClusterConnectionBridge.createSelectorFromAddress("!" + address));
   }

   @Test
   public void testCreateSelectorFromListForNormalMatches() {
      String address1 = "jms.test1.address";
      String address2 = "jms.test2.address";
      String addresses = address1 + "," + address2;

      StringBuilder expectedSelector = new StringBuilder();
      expectedSelector.append("(");
      expectedSelector.append("(" + ManagementHelper.HDR_ADDRESS + " LIKE '" + address1 + "%')");
      expectedSelector.append(" OR ");
      expectedSelector.append("(" + ManagementHelper.HDR_ADDRESS + " LIKE '" + address2 + "%')");
      expectedSelector.append(")");
      assertEquals(expectedSelector.toString(), ClusterConnectionBridge.createSelectorFromAddress(addresses));
   }

   @Test
   public void testCreateSelectorFromListForExclusions() {
      String address1 = "jms.test1.address";
      String address2 = "jms.test2.address";
      String addresses = "!" + address1 + "," + "!" + address2;

      StringBuilder expectedSelector = new StringBuilder();
      expectedSelector.append("(");
      expectedSelector.append("(" + ManagementHelper.HDR_ADDRESS + " NOT LIKE '" + address1 + "%')");
      expectedSelector.append(" AND ");
      expectedSelector.append("(" + ManagementHelper.HDR_ADDRESS + " NOT LIKE '" + address2 + "%')");
      expectedSelector.append(")");
      assertEquals(expectedSelector.toString(), ClusterConnectionBridge.createSelectorFromAddress(addresses));
   }

   @Test
   public void testCreateSelectorFromListForExclusionsAndNormalMatches() {
      String address1 = "jms.test1.address";
      String address2 = "jms.test2.address";
      String address3 = "jms.test3.address";
      String address4 = "jms.test4.address";
      String addresses = address1 + ",!" + address2 + "," + address3 + ",!" + address4;

      StringBuilder expectedSelector = new StringBuilder();
      expectedSelector.append("(((" + ManagementHelper.HDR_ADDRESS + " LIKE '" + address1 + "%')");
      expectedSelector.append(" OR ");
      expectedSelector.append("(" + ManagementHelper.HDR_ADDRESS + " LIKE '" + address3 + "%'))");
      expectedSelector.append(" AND ");
      expectedSelector.append("((" + ManagementHelper.HDR_ADDRESS + " NOT LIKE '" + address2 + "%')");
      expectedSelector.append(" AND ");
      expectedSelector.append("(" + ManagementHelper.HDR_ADDRESS + " NOT LIKE '" + address4 + "%')))");

      assertEquals(expectedSelector.toString(), ClusterConnectionBridge.createSelectorFromAddress(addresses));
   }

   @Test
   public void testCreateSelectorFromListIgnoresEmptyStrings() {
      String address1 = "jms.test1.address";
      String address2 = "jms.test2.address";
      String addresses = address1 + ",!" + address2 + ",,,";

      StringBuilder expectedSelector = new StringBuilder();
      expectedSelector.append("(((" + ManagementHelper.HDR_ADDRESS + " LIKE '" + address1 + "%'))");
      expectedSelector.append(" AND ");
      expectedSelector.append("((" + ManagementHelper.HDR_ADDRESS + " NOT LIKE '" + address2 + "%')))");

      assertEquals(expectedSelector.toString(), ClusterConnectionBridge.createSelectorFromAddress(addresses));
   }
}
