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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class LargeMessageRedistributionTest extends MessageRedistributionTest {

   @Override
   public boolean isLargeMessage() {
      return true;
   }

   @Test
   public void testRedistributionLargeMessageDirCleanup() throws Exception {
      final long delay = 100;
      final int numMessages = 5;

      setRedistributionDelay(delay);
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 1, 0, false);
      waitForBindings(1, "queues.testaddress", 1, 0, false);

      send(0, "queues.testaddress", numMessages, true, null);
      addConsumer(0, 0, "queue0", null);

      verifyReceiveAll(numMessages, 0);
      removeConsumer(0);

      addConsumer(1, 1, "queue0", null);
      verifyReceiveAll(numMessages, 1);
      removeConsumer(1);

      Wait.assertEquals(0, () -> getServer(0).getConfiguration().getLargeMessagesLocation().listFiles().length);
      Wait.assertEquals(numMessages, () -> getServer(1).getConfiguration().getLargeMessagesLocation().listFiles().length);
   }

   @Test
   public void testRedistributionLargeMessageDirCleanup2() throws Exception {
      final long delay = 0;
      final int numMessages = 5;

      setRedistributionDelay(delay);
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(1, "queues.testaddress", "queue0", null, false);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(1, "queues.testaddress", 1, 0, true);

      waitForBindings(0, "queues.testaddress", 1, 0, false);
      waitForBindings(1, "queues.testaddress", 1, 0, false);

      send(0, "queues.testaddress", numMessages, true, null);
      addConsumer(0, 0, "queue0", null);

      verifyReceiveAll(numMessages, 0);
      removeConsumer(0);

      addConsumer(1, 1, "queue0", null);
      verifyReceiveAll(numMessages, 1);
      servers[1].stop();

      send(0, "queues.testaddress", numMessages, true, null);

      Wait.assertEquals(5, () -> getServer(0).getConfiguration().getLargeMessagesLocation().listFiles().length);
      Wait.assertEquals(numMessages, () -> getServer(0).getConfiguration().getLargeMessagesLocation().listFiles().length);
   }


   @Test
   @Disabled
   @Override
   public void testRedistributionWhenConsumerIsClosedConsumersWithFilters() {
      // this test wasn't designed for large messages
   }
}
