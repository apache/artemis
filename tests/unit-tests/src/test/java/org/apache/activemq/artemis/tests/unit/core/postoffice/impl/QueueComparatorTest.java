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
package org.apache.activemq.artemis.tests.unit.core.postoffice.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ScaleDownHandler;
import org.apache.activemq.artemis.tests.unit.core.postoffice.impl.fakes.FakeQueue;
import org.junit.jupiter.api.Test;

public class QueueComparatorTest {

   @Test
   public void testQueueSorting() {
      FakeQueue queue1 = new FakeQueue(SimpleString.of("1"));
      queue1.setMessageCount(1);
      FakeQueue queue2 = new FakeQueue(SimpleString.of("2"));
      queue2.setMessageCount(2);
      FakeQueue queue3 = new FakeQueue(SimpleString.of("3"));
      queue3.setMessageCount(3);

      List<Queue> queues = new ArrayList<>();
      queues.add(queue1);
      queues.add(queue2);
      queues.add(queue3);

      assertEquals(1, queues.get(0).getMessageCount());
      assertEquals(2, queues.get(1).getMessageCount());
      assertEquals(3, queues.get(2).getMessageCount());

      Collections.sort(queues, new ScaleDownHandler.OrderQueueByNumberOfReferencesComparator());

      assertEquals(3, queues.get(0).getMessageCount());
      assertEquals(2, queues.get(1).getMessageCount());
      assertEquals(1, queues.get(2).getMessageCount());
   }
}
