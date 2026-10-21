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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.junit.jupiter.api.Test;

public class DescribeJournalTest {

   @Test
   public void testDecorateRouteToSingleQueue() {
      CoreMessage message = new CoreMessage(1, 512);
      ByteBuffer buffer = ByteBuffer.allocate(8);
      buffer.putLong(46);
      message.putBytesProperty(Message.HDR_ROUTE_TO_IDS, buffer.array());

      Map<Long, String> queues = new HashMap<>();
      queues.put(46L, "myQueue");
      DescribeJournal.BrokerInfo brokerInfo = new DescribeJournal.BrokerInfo(100, Map.of(), queues);

      String result = DescribeJournal.decorateRouteToProperties(message, brokerInfo);

      assertTrue(result.startsWith("\n"), "Should start with line break");
      assertTrue(result.contains(Message.HDR_ROUTE_TO_IDS.toString()));
      assertTrue(result.contains("46"));
      assertTrue(result.contains("(myQueue)"));
   }

   @Test
   public void testDecorateRouteToMultipleQueues() {
      CoreMessage message = new CoreMessage(1, 512);
      ByteBuffer buffer = ByteBuffer.allocate(16);
      buffer.putLong(10);
      buffer.putLong(20);
      message.putBytesProperty(Message.HDR_ROUTE_TO_IDS, buffer.array());

      Map<Long, String> queues = new HashMap<>();
      queues.put(10L, "queue1");
      queues.put(20L, "queue2");
      DescribeJournal.BrokerInfo brokerInfo = new DescribeJournal.BrokerInfo(100, Map.of(), queues);

      String result = DescribeJournal.decorateRouteToProperties(message, brokerInfo);

      assertTrue(result.contains("10"));
      assertTrue(result.contains("(queue1)"));
      assertTrue(result.contains("20"));
      assertTrue(result.contains("(queue2)"));
   }

   @Test
   public void testDecorateRouteToUnknownQueue() {
      CoreMessage message = new CoreMessage(1, 512);
      ByteBuffer buffer = ByteBuffer.allocate(8);
      buffer.putLong(99);
      message.putBytesProperty(Message.HDR_ROUTE_TO_IDS, buffer.array());

      DescribeJournal.BrokerInfo brokerInfo = new DescribeJournal.BrokerInfo(100, Map.of(), Map.of());

      String result = DescribeJournal.decorateRouteToProperties(message, brokerInfo);

      assertTrue(result.contains("99"));
      assertFalse(result.contains("("), "No queue name decoration for unknown queue");
   }

   @Test
   public void testDecorateRouteToClusterProperty() {
      CoreMessage message = new CoreMessage(1, 512);
      String clusterPropertyName = Message.HDR_ROUTE_TO_IDS + "$.artemis.internal.sf.my-cluster.some-uuid";
      ByteBuffer buffer = ByteBuffer.allocate(8);
      buffer.putLong(46);
      message.putBytesProperty(clusterPropertyName, buffer.array());

      Map<Long, String> queues = new HashMap<>();
      queues.put(46L, "$.artemis.internal.sf.my-cluster.some-uuid");
      DescribeJournal.BrokerInfo brokerInfo = new DescribeJournal.BrokerInfo(100, Map.of(), queues);

      String result = DescribeJournal.decorateRouteToProperties(message, brokerInfo);

      assertTrue(result.contains(clusterPropertyName));
      assertTrue(result.contains("46"));
      assertTrue(result.contains("($.artemis.internal.sf.my-cluster.some-uuid)"));
   }

   @Test
   public void testDecorateRouteToNoProperties() {
      CoreMessage message = new CoreMessage(1, 512);

      DescribeJournal.BrokerInfo brokerInfo = new DescribeJournal.BrokerInfo(100, Map.of(), Map.of());

      String result = DescribeJournal.decorateRouteToProperties(message, brokerInfo);

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
