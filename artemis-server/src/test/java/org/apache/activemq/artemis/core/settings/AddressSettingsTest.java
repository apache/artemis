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
package org.apache.activemq.artemis.core.settings;

import java.io.StringReader;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddressSettingsTest extends ServerTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testDefaults() {
      AddressSettings addressSettings = new AddressSettings();
      assertNull(addressSettings.getDeadLetterAddress());
      assertNull(addressSettings.getExpiryAddress());
      assertEquals(AddressSettings.DEFAULT_MAX_DELIVERY_ATTEMPTS, addressSettings.getMaxDeliveryAttempts());
      assertEquals(AddressSettings.DEFAULT_MAX_SIZE_BYTES, addressSettings.getMaxSizeBytes());
      assertEquals(AddressSettings.DEFAULT_PAGE_SIZE, addressSettings.getPageSizeBytes());
      assertEquals(AddressSettings.DEFAULT_MESSAGE_COUNTER_HISTORY_DAY_LIMIT, addressSettings.getMessageCounterHistoryDayLimit());
      assertEquals(AddressSettings.DEFAULT_REDELIVER_DELAY, addressSettings.getRedeliveryDelay());
      assertEquals(AddressSettings.DEFAULT_REDELIVER_MULTIPLIER, addressSettings.getRedeliveryMultiplier(), 0.000001);
      assertEquals(AddressSettings.DEFAULT_SLOW_CONSUMER_THRESHOLD, addressSettings.getSlowConsumerThreshold());
      assertEquals(AddressSettings.DEFAULT_SLOW_CONSUMER_THRESHOLD_MEASUREMENT_UNIT, addressSettings.getSlowConsumerThresholdMeasurementUnit());
      assertEquals(AddressSettings.DEFAULT_SLOW_CONSUMER_CHECK_PERIOD, addressSettings.getSlowConsumerCheckPeriod());
      assertEquals(AddressSettings.DEFAULT_SLOW_CONSUMER_POLICY, addressSettings.getSlowConsumerPolicy());
      assertEquals(AddressSettings.DEFAULT_AUTO_CREATE_JMS_QUEUES, addressSettings.isAutoCreateJmsQueues());
      assertEquals(AddressSettings.DEFAULT_AUTO_DELETE_JMS_QUEUES, addressSettings.isAutoDeleteJmsQueues());
      assertEquals(AddressSettings.DEFAULT_AUTO_CREATE_TOPICS, addressSettings.isAutoCreateJmsTopics());
      assertEquals(AddressSettings.DEFAULT_AUTO_DELETE_TOPICS, addressSettings.isAutoDeleteJmsTopics());
      assertEquals(AddressSettings.DEFAULT_AUTO_CREATE_QUEUES, addressSettings.isAutoCreateQueues());
      assertEquals(AddressSettings.DEFAULT_AUTO_DELETE_QUEUES, addressSettings.isAutoDeleteQueues());
      assertEquals(AddressSettings.DEFAULT_AUTO_CREATE_ADDRESSES, addressSettings.isAutoCreateAddresses());
      assertEquals(AddressSettings.DEFAULT_AUTO_DELETE_ADDRESSES, addressSettings.isAutoDeleteAddresses());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), addressSettings.isDefaultPurgeOnNoConsumers());
      assertEquals(Integer.valueOf(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers()), addressSettings.getDefaultMaxConsumers());
      assertEquals(AddressSettings.DEFAULT_NO_EXPIRY, addressSettings.isNoExpiry());
   }

   @Test
   public void testSizeNegative() {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setPageSizeBytes(-1);
      assertEquals(AddressSettings.DEFAULT_PAGE_SIZE, addressSettings.getPageSizeBytes());
   }

   @Test
   public void testSingleMerge() {
      testSingleMerge(false);
   }

   @Test
   public void testSingleMergeCopy() {
      testSingleMerge(true);
   }

   private void testSingleMerge(boolean copy) {
      AddressSettings addressSettings = new AddressSettings();
      AddressSettings addressSettingsToMerge = new AddressSettings();
      SimpleString DLQ = SimpleString.of("testDLQ");
      SimpleString exp = SimpleString.of("testExpiryQueue");
      addressSettingsToMerge.setDeadLetterAddress(DLQ);
      addressSettingsToMerge.setExpiryAddress(exp);
      addressSettingsToMerge.setMaxDeliveryAttempts(1000);
      addressSettingsToMerge.setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP);
      addressSettingsToMerge.setMaxSizeBytes(1001);
      addressSettingsToMerge.setMaxSizeMessages(101);
      addressSettingsToMerge.setMessageCounterHistoryDayLimit(1002);
      addressSettingsToMerge.setRedeliveryDelay(1003);
      addressSettingsToMerge.setPageSizeBytes(1004);
      addressSettingsToMerge.setMaxSizeBytesRejectThreshold(10 * 1024);
      addressSettingsToMerge.setConfigDeleteDiverts(DeletionPolicy.FORCE);
      addressSettingsToMerge.setExpiryDelay(999L);
      addressSettingsToMerge.setMinExpiryDelay(888L);
      addressSettingsToMerge.setMaxExpiryDelay(777L);
      addressSettingsToMerge.setIDCacheSize(5);
      addressSettingsToMerge.setInitialQueueBufferSize(256);
      addressSettingsToMerge.setNoExpiry(true);

      if (copy) {
         addressSettings = addressSettings.mergeCopy(addressSettingsToMerge);
      } else {
         addressSettings.merge(addressSettingsToMerge);
      }
      assertEquals(addressSettings.getDeadLetterAddress(), DLQ);
      assertEquals(addressSettings.getExpiryAddress(), exp);
      assertEquals(1000, addressSettings.getMaxDeliveryAttempts());
      assertEquals(1001, addressSettings.getMaxSizeBytes());
      assertEquals(101, addressSettings.getMaxSizeMessages());
      assertEquals(1002, addressSettings.getMessageCounterHistoryDayLimit());
      assertEquals(1003, addressSettings.getRedeliveryDelay());
      assertEquals(1004, addressSettings.getPageSizeBytes());
      assertEquals(AddressFullMessagePolicy.DROP, addressSettings.getAddressFullMessagePolicy());
      assertEquals(10 * 1024, addressSettings.getMaxSizeBytesRejectThreshold());
      assertEquals(DeletionPolicy.FORCE, addressSettings.getConfigDeleteDiverts());
      assertEquals(Long.valueOf(999), addressSettings.getExpiryDelay());
      assertEquals(Long.valueOf(888), addressSettings.getMinExpiryDelay());
      assertEquals(Long.valueOf(777), addressSettings.getMaxExpiryDelay());
      assertEquals(Integer.valueOf(5), addressSettings.getIDCacheSize());
      assertEquals(Integer.valueOf(256), addressSettings.getInitialQueueBufferSize());
      assertTrue(addressSettings.isNoExpiry());
   }

   @Test
   public void testMultipleMerge() {
      testMultipleMerge(false);
   }

   @Test
   public void testMultipleMergeCopy() {
      testSingleMerge(true);
   }

   private void testMultipleMerge(boolean copy) {
      AddressSettings addressSettings = new AddressSettings();
      AddressSettings addressSettingsToMerge = new AddressSettings();
      SimpleString DLQ = SimpleString.of("testDLQ");
      SimpleString exp = SimpleString.of("testExpiryQueue");
      addressSettingsToMerge.setDeadLetterAddress(DLQ);
      addressSettingsToMerge.setExpiryAddress(exp);
      addressSettingsToMerge.setMaxDeliveryAttempts(1000);
      addressSettingsToMerge.setMaxSizeBytes(1001);
      addressSettingsToMerge.setMessageCounterHistoryDayLimit(1002);
      addressSettingsToMerge.setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP);
      addressSettingsToMerge.setMaxSizeBytesRejectThreshold(10 * 1024);
      addressSettingsToMerge.setNoExpiry(true);
      if (copy) {
         addressSettings = addressSettings.mergeCopy(addressSettingsToMerge);
      } else {
         addressSettings.merge(addressSettingsToMerge);
      }

      AddressSettings addressSettingsToMerge2 = new AddressSettings();
      SimpleString exp2 = SimpleString.of("testExpiryQueue2");
      addressSettingsToMerge2.setExpiryAddress(exp2);
      addressSettingsToMerge2.setMaxSizeBytes(2001);
      addressSettingsToMerge2.setRedeliveryDelay(2003);
      addressSettingsToMerge2.setRedeliveryMultiplier(2.5);
      if (copy) {
         addressSettings = addressSettings.mergeCopy(addressSettingsToMerge2);
      } else {
         addressSettings.merge(addressSettingsToMerge2);
      }

      assertEquals(addressSettings.getDeadLetterAddress(), DLQ);
      assertEquals(addressSettings.getExpiryAddress(), exp);
      assertEquals(1000, addressSettings.getMaxDeliveryAttempts());
      assertEquals(1001, addressSettings.getMaxSizeBytes());
      assertEquals(1002, addressSettings.getMessageCounterHistoryDayLimit());
      assertEquals(2003, addressSettings.getRedeliveryDelay());
      assertEquals(2.5, addressSettings.getRedeliveryMultiplier(), 0.000001);
      assertEquals(AddressFullMessagePolicy.DROP, addressSettings.getAddressFullMessagePolicy());
      assertEquals(10 * 1024, addressSettings.getMaxSizeBytesRejectThreshold());
      assertTrue(addressSettings.isNoExpiry());
   }

   @Test
   public void testMultipleMergeAll() {
      testMultipleMergeAll(false);
   }

   @Test
   public void testMultipleMergeAllCopy() {
      testMultipleMergeAll(true);
   }

   private void testMultipleMergeAll(boolean copy) {
      AddressSettings addressSettings = new AddressSettings();
      AddressSettings addressSettingsToMerge = new AddressSettings();
      SimpleString DLQ = SimpleString.of("testDLQ");
      SimpleString exp = SimpleString.of("testExpiryQueue");
      addressSettingsToMerge.setDeadLetterAddress(DLQ);
      addressSettingsToMerge.setExpiryAddress(exp);
      addressSettingsToMerge.setMaxSizeBytes(1001);
      addressSettingsToMerge.setRedeliveryDelay(1003);
      addressSettingsToMerge.setRedeliveryMultiplier(1.0);
      addressSettingsToMerge.setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP);
      if (copy) {
         addressSettings = addressSettings.mergeCopy(addressSettingsToMerge);
      } else {
         addressSettings.merge(addressSettingsToMerge);
      }

      AddressSettings addressSettingsToMerge2 = new AddressSettings();
      SimpleString exp2 = SimpleString.of("testExpiryQueue2");
      SimpleString DLQ2 = SimpleString.of("testDlq2");
      addressSettingsToMerge2.setExpiryAddress(exp2);
      addressSettingsToMerge2.setDeadLetterAddress(DLQ2);
      addressSettingsToMerge2.setMaxDeliveryAttempts(2000);
      addressSettingsToMerge2.setMaxSizeBytes(2001);
      addressSettingsToMerge2.setMessageCounterHistoryDayLimit(2002);
      addressSettingsToMerge2.setRedeliveryDelay(2003);
      addressSettingsToMerge2.setRedeliveryMultiplier(2.0);
      addressSettingsToMerge2.setMaxRedeliveryDelay(5000);
      addressSettingsToMerge.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      if (copy) {
         addressSettings = addressSettings.mergeCopy(addressSettingsToMerge2);
      } else {
         addressSettings.merge(addressSettingsToMerge2);
      }

      assertEquals(addressSettings.getDeadLetterAddress(), DLQ);
      assertEquals(addressSettings.getExpiryAddress(), exp);
      assertEquals(2000, addressSettings.getMaxDeliveryAttempts());
      assertEquals(1001, addressSettings.getMaxSizeBytes());
      assertEquals(2002, addressSettings.getMessageCounterHistoryDayLimit());
      assertEquals(1003, addressSettings.getRedeliveryDelay());
      assertEquals(1.0, addressSettings.getRedeliveryMultiplier(), 0.000001);
      assertEquals(5000, addressSettings.getMaxRedeliveryDelay());
      assertEquals(AddressFullMessagePolicy.DROP, addressSettings.getAddressFullMessagePolicy());
   }

   @Test
   public void testToJSON() {
      AddressSettings addressSettings = new AddressSettings();
      SimpleString DLQ = SimpleString.of("testDLQ");
      SimpleString exp = SimpleString.of("testExpiryQueue");
      addressSettings.setDeadLetterAddress(DLQ);
      addressSettings.setExpiryAddress(exp);
      addressSettings.setMaxSizeBytes(1001);
      addressSettings.setPrefetchPageMessages(1000);
      addressSettings.setRedeliveryDelay(1003);
      addressSettings.setRedeliveryMultiplier(1.0);
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP);
      addressSettings.setNoExpiry(true);

      String json = addressSettings.toJSON();
      logger.info("Json:: {}", json);

      AddressSettings jsonClone = AddressSettings.fromJSON(json);
      assertEquals(1001, jsonClone.getMaxSizeBytes());
      System.err.println("AddressSettings::" + addressSettings);
      System.err.println("clonedSettings ::" + jsonClone);
      assertEquals(addressSettings.getAddressFullMessagePolicy(), jsonClone.getAddressFullMessagePolicy());

      assertEquals(addressSettings, jsonClone);
   }

   @Test
   public void testToJSONEmpty() {
      assertEquals(0, JsonLoader.readObject(new StringReader(new AddressSettings().toJSON())).size());
   }
}
