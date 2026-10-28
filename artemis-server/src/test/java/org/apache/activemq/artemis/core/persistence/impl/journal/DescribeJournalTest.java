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
package org.apache.activemq.artemis.core.persistence.impl.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.junit.jupiter.api.Test;

public class DescribeJournalTest {

   @Test
   public void testDecorateRouteToMultipleQueues() {
      CoreMessage message = new CoreMessage(1, 512);
      ByteBuffer buffer = ByteBuffer.allocate(16);
      buffer.putLong(10);
      buffer.putLong(20);
      message.putBytesProperty(Message.HDR_ROUTE_TO_IDS, buffer.array());

      Map<Long, String> queues = new HashMap<>();
      DescribeJournal.BrokerInfo brokerInfo = new DescribeJournal.BrokerInfo(100, Map.of(), queues);

      String result = DescribeJournal.decorateRouteToProperties(message);

      assertTrue(result.contains("10"));
      assertTrue(result.contains("20"));
   }

   @Test
   public void testDecorateRouteToUnknownQueue() {
      CoreMessage message = new CoreMessage(1, 512);
      ByteBuffer buffer = ByteBuffer.allocate(8);
      buffer.putLong(99);
      message.putBytesProperty(Message.HDR_ROUTE_TO_IDS, buffer.array());

      String result = DescribeJournal.decorateRouteToProperties(message);

      assertTrue(result.contains("99"));
   }


   @Test
   public void testDecorateRouteToNoProperties() {
      CoreMessage message = new CoreMessage(1, 512);

      String result = DescribeJournal.decorateRouteToProperties(message);

      assertEquals("", result);
   }

   @Test
   public void testDecorateQueueName() {
      Map<Long, String> queues = new HashMap<>();
      queues.put(15L, "myQueue");
      DescribeJournal.BrokerInfo brokerInfo = new DescribeJournal.BrokerInfo(100, Map.of(), queues);

      assertEquals("(myQueue)", DescribeJournal.decorateQueueName(15, brokerInfo));
      assertEquals("", DescribeJournal.decorateQueueName(999, brokerInfo));
   }
}
