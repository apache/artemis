/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.persistence.impl.journal;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PersistentQueueBindingEncoding;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueueBindingEncodingTest {

   @Test
   public void testEncodeDecode() {
      final QueueConfiguration config = QueueConfiguration.of(RandomUtil.randomUUIDSimpleString())
         .setAddress(RandomUtil.randomUUIDSimpleString())
         .setFilterString(RandomUtil.randomUUIDSimpleString())
         .setUser(RandomUtil.randomUUIDSimpleString())
         .setAutoCreated(RandomUtil.randomBoolean())
         .setMaxConsumers(RandomUtil.randomInt())
         .setPurgeOnNoConsumers(RandomUtil.randomBoolean())
         .setEnabled(RandomUtil.randomBoolean())
         .setExclusive(RandomUtil.randomBoolean())
         .setGroupRebalance(RandomUtil.randomBoolean())
         .setGroupRebalancePauseDispatch(RandomUtil.randomBoolean())
         .setGroupBuckets(RandomUtil.randomInt())
         .setGroupFirstKey(RandomUtil.randomUUIDSimpleString())
         .setLastValue(RandomUtil.randomBoolean())
         .setLastValueKey(RandomUtil.randomUUIDSimpleString())
         .setNonDestructive(RandomUtil.randomBoolean())
         .setConsumersBeforeDispatch(RandomUtil.randomInt())
         .setDelayBeforeDispatch(RandomUtil.randomLong())
         .setAutoDelete(RandomUtil.randomBoolean())
         .setAutoDeleteDelay(RandomUtil.randomLong())
         .setAutoDeleteMessageCount(RandomUtil.randomLong())
         .setRoutingType(RoutingType.getType((byte) RandomUtil.randomInterval(0, 1)))
         .setConfigurationManaged(RandomUtil.randomBoolean())
         .setRingSize(RandomUtil.randomLong())
         .setInternal(RandomUtil.randomBoolean());

      PersistentQueueBindingEncoding encoding = new PersistentQueueBindingEncoding(config);
      int size = encoding.getEncodeSize();
      ActiveMQBuffer encodedBuffer = ActiveMQBuffers.fixedBuffer(size);
      encoding.encode(encodedBuffer);

      PersistentQueueBindingEncoding decoding = new PersistentQueueBindingEncoding();
      decoding.decode(encodedBuffer);
      QueueConfiguration decodedQueueConfig = decoding.getQueueConfiguration();

      assertEquals(config.getName(), decodedQueueConfig.getName());
      assertEquals(config.getAddress(), decodedQueueConfig.getAddress());
      assertEquals(config.getFilterString(), decodedQueueConfig.getFilterString());
      assertEquals(config.getUser(), decodedQueueConfig.getUser());
      assertEquals(config.isAutoCreated(), decodedQueueConfig.isAutoCreated());
      assertEquals(config.getMaxConsumers(), decodedQueueConfig.getMaxConsumers());
      assertEquals(config.isPurgeOnNoConsumers(), decodedQueueConfig.isPurgeOnNoConsumers());
      assertEquals(config.isEnabled(), decodedQueueConfig.isEnabled());
      assertEquals(config.isExclusive(), decodedQueueConfig.isExclusive());
      assertEquals(config.isGroupRebalance(), decodedQueueConfig.isGroupRebalance());
      assertEquals(config.getGroupBuckets(), decodedQueueConfig.getGroupBuckets());
      assertEquals(config.getGroupFirstKey(), decodedQueueConfig.getGroupFirstKey());
      assertEquals(config.isLastValue(), decodedQueueConfig.isLastValue());
      assertEquals(config.getLastValueKey(), decodedQueueConfig.getLastValueKey());
      assertEquals(config.isNonDestructive(), decodedQueueConfig.isNonDestructive());
      assertEquals(config.getConsumersBeforeDispatch(), decodedQueueConfig.getConsumersBeforeDispatch());
      assertEquals(config.getDelayBeforeDispatch(), decodedQueueConfig.getDelayBeforeDispatch());
      assertEquals(config.isAutoDelete(), decodedQueueConfig.isAutoDelete());
      assertEquals(config.getAutoDeleteDelay(), decodedQueueConfig.getAutoDeleteDelay());
      assertEquals(config.getAutoDeleteMessageCount(), decodedQueueConfig.getAutoDeleteMessageCount());
      assertEquals(config.getRoutingType(), decodedQueueConfig.getRoutingType());
      assertEquals(config.isConfigurationManaged(), decodedQueueConfig.isConfigurationManaged());
      assertEquals(config.getRingSize(), decodedQueueConfig.getRingSize());
      assertEquals(config.isGroupRebalancePauseDispatch(), decodedQueueConfig.isGroupRebalancePauseDispatch());
      assertEquals(config.isInternal(), decodedQueueConfig.isInternal());
   }
}
