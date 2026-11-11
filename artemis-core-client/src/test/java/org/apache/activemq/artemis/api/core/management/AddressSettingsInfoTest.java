/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.activemq.artemis.api.core.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.junit.jupiter.api.Test;

public class AddressSettingsInfoTest {

   @Test
   public void shouldLoadFromJSON() {
      String json = """
         {
         "addressFullMessagePolicy":"fullPolicy",
         "maxSizeBytes":500,
         "pageSizeBytes":200,
         "pageCacheMaxSize":3,
         "maxDeliveryAttempts":3,
         "redeliveryDelay":70000,
         "redeliveryMultiplier":1.5,
         "maxRedeliveryDelay":100000,
         "deadLetterAddress":"deadLettersGoHere",
         "expiryAddress":"",
         "defaultLastValueQueue":true,
         "redistributionDelay":10004,
         "sendToDLAOnNoRoute":true,
         "slowConsumerThreshold":200,
         "slowConsumerCheckPeriod":300,
         "slowConsumerPolicy":"retire",
         "autoCreateJmsQueues":true,
         "autoDeleteJmsQueues":false,
         "autoCreateJmsTopics":true,
         "autoDeleteJmsTopics":false,
         "autoCreateQueues":false,
         "autoDeleteQueues":false,
         "autoCreateAddresses":false,
         "autoDeleteAddresses":false,
         "configDeleteQueues":"OFF",
         "configDeleteAddresses":"FORCE",
         "maxSizeBytesRejectThreshold":1023,
         "defaultLastValueKey":"yyy",
         "defaultNonDestructive":false,
         "defaultExclusiveQueue":false,
         "defaultGroupRebalance":false,
         "defaultGroupBuckets":1026,
         "defaultGroupFirstKey":"xxx",
         "defaultMaxConsumers":1001,
         "defaultPurgeOnNoConsumers":false,
         "defaultConsumersBeforeDispatch":1005,
         "defaultDelayBeforeDispatch":1003,
         "defaultQueueRoutingType":"MULTICAST",
         "defaultAddressRoutingType":"ANYCAST",
         "defaultConsumerWindowSize":2001,
         "defaultRingSize":999,
         "autoDeleteCreatedQueues":false,
         "autoDeleteQueuesDelay":4,
         "autoDeleteQueuesMessageCount":8,
         "autoDeleteAddressesDelay":3003,
         "redeliveryCollisionAvoidanceFactor":1.1,
         "retroactiveMessageCount":101,
         "autoCreateDeadLetterResources":true,
         "deadLetterQueuePrefix":"FOO.",
         "deadLetterQueueSuffix":".FOO",
         "autoCreateExpiryResources":true,
         "expiryQueuePrefix":"BAR.",
         "expiryQueueSuffix":".BAR",
         "expiryDelay":404,
         "minExpiryDelay":40,
         "maxExpiryDelay":4004,
         "enableMetrics":false
         }""";
      AddressSettingsInfo addressSettingsInfo = AddressSettingsInfo.fromJSON(json);
      assertEquals("fullPolicy", addressSettingsInfo.getAddressFullMessagePolicy());
      assertEquals(500L, addressSettingsInfo.getMaxSizeBytes());
      assertEquals(200L, addressSettingsInfo.getPageSizeBytes());
      assertEquals(3, addressSettingsInfo.getPageCacheMaxSize());
      assertEquals(3, addressSettingsInfo.getMaxDeliveryAttempts());
      assertEquals(70000, addressSettingsInfo.getRedeliveryDelay());
      assertEquals(1.5, addressSettingsInfo.getRedeliveryMultiplier(), 0);
      assertEquals(100000, addressSettingsInfo.getMaxRedeliveryDelay());
      assertEquals("deadLettersGoHere", addressSettingsInfo.getDeadLetterAddress());
      assertEquals("", addressSettingsInfo.getExpiryAddress());
      assertTrue(addressSettingsInfo.isDefaultLastValueQueue());
      assertEquals(10004L, addressSettingsInfo.getRedistributionDelay());
      assertTrue(addressSettingsInfo.isSendToDLAOnNoRoute());
      assertEquals(200L, addressSettingsInfo.getSlowConsumerThreshold());
      assertEquals(300L, addressSettingsInfo.getSlowConsumerCheckPeriod());
      assertEquals("retire", addressSettingsInfo.getSlowConsumerPolicy());
      assertTrue(addressSettingsInfo.isAutoCreateJmsQueues());
      assertTrue(addressSettingsInfo.isAutoCreateJmsTopics());
      assertFalse(addressSettingsInfo.isAutoDeleteJmsQueues());
      assertFalse(addressSettingsInfo.isAutoDeleteJmsTopics());
      assertFalse(addressSettingsInfo.isAutoCreateQueues());
      assertFalse(addressSettingsInfo.isAutoDeleteQueues());
      assertFalse(addressSettingsInfo.isAutoCreateAddresses());
      assertFalse(addressSettingsInfo.isAutoDeleteAddresses());
      assertEquals("OFF", addressSettingsInfo.getConfigDeleteQueues());
      assertEquals("FORCE", addressSettingsInfo.getConfigDeleteAddresses());
      assertEquals(1023, addressSettingsInfo.getMaxSizeBytesRejectThreshold());
      assertEquals("yyy", addressSettingsInfo.getDefaultLastValueKey());
      assertFalse(addressSettingsInfo.isDefaultNonDestructive());
      assertFalse(addressSettingsInfo.isDefaultExclusiveQueue());
      assertFalse(addressSettingsInfo.isDefaultGroupRebalance());
      assertEquals(1026, addressSettingsInfo.getDefaultGroupBuckets());
      assertEquals("xxx", addressSettingsInfo.getDefaultGroupFirstKey());
      assertEquals(1001, addressSettingsInfo.getDefaultMaxConsumers());
      assertFalse(addressSettingsInfo.isDefaultPurgeOnNoConsumers());
      assertEquals(1005, addressSettingsInfo.getDefaultConsumersBeforeDispatch());
      assertEquals(1003, addressSettingsInfo.getDefaultDelayBeforeDispatch());
      assertEquals(RoutingType.MULTICAST.toString(), addressSettingsInfo.getDefaultQueueRoutingType());
      assertEquals(RoutingType.ANYCAST.toString(), addressSettingsInfo.getDefaultAddressRoutingType());
      assertEquals(2001, addressSettingsInfo.getDefaultConsumerWindowSize());
      assertEquals(999, addressSettingsInfo.getDefaultRingSize());
      assertFalse(addressSettingsInfo.isAutoDeleteCreatedQueues());
      assertEquals(4, addressSettingsInfo.getAutoDeleteQueuesDelay());
      assertEquals(8, addressSettingsInfo.getAutoDeleteQueuesMessageCount());
      assertEquals(3003, addressSettingsInfo.getAutoDeleteAddressesDelay());
      assertEquals(1.1, addressSettingsInfo.getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(101, addressSettingsInfo.getRetroactiveMessageCount());
      assertTrue(addressSettingsInfo.isAutoCreateDeadLetterResources());
      assertEquals("FOO.", addressSettingsInfo.getDeadLetterQueuePrefix());
      assertEquals(".FOO", addressSettingsInfo.getDeadLetterQueueSuffix());
      assertTrue(addressSettingsInfo.isAutoCreateExpiryResources());
      assertEquals("BAR.", addressSettingsInfo.getExpiryQueuePrefix());
      assertEquals(".BAR", addressSettingsInfo.getExpiryQueueSuffix());
      assertEquals(404, addressSettingsInfo.getExpiryDelay());
      assertEquals(40, addressSettingsInfo.getMinExpiryDelay());
      assertEquals(4004, addressSettingsInfo.getMaxExpiryDelay());
      assertFalse(addressSettingsInfo.isEnableMetrics());
   }

}
