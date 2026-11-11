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

package org.apache.activemq.artemis.tests.integration.addressing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This test is simulating a dead lock that may happen while removing addresses.
 */
public class TwoWaysRemoveAddressTest extends ActiveMQTestBase {

   private static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testDeadLock() throws Throwable {
      ActiveMQServer server = addServer(createServer(false));
      server.start();

      final int retries = 10;
      CyclicBarrier barrier = new CyclicBarrier(2);

      AtomicInteger errors = new AtomicInteger(0);

      Thread createAndDestroy1 = new Thread(() -> {

         try {
            barrier.await(10, TimeUnit.SECONDS);

            for (int i = 0; i < retries; i++) {
               logger.debug("Removed queue on thread 1 ::{}", i);
               server.createQueue(QueueConfiguration.of("queueName_1_" + i).setAddress("address_1_" + i).setRoutingType(RoutingType.ANYCAST));
               server.destroyQueue(SimpleString.of("queueName_1_" + i));
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         }
      });

      Thread createAndDestroy2 = new Thread(() -> {

         try {
            barrier.await(10, TimeUnit.SECONDS);

            for (int i = 0; i < retries; i++) {
               logger.debug("Removed queue on thread 2 ::{}", i);
               server.createQueue(QueueConfiguration.of("queueName_2_" + i).setAddress("address_2_" + i).setRoutingType(RoutingType.ANYCAST));
               server.removeAddressInfo(SimpleString.of("address_2_" + i), null, true);
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         }
      });

      createAndDestroy1.start();
      createAndDestroy2.start();

      createAndDestroy1.join(10_000);
      createAndDestroy2.join(10_000);

      assertFalse(createAndDestroy1.isAlive());
      assertFalse(createAndDestroy2.isAlive());


      assertEquals(0, errors.get());

   }

}
