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

package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class InitialConnectionTest extends ActiveMQTestBase {

   @Test
   public void testInitialInfinite() throws Exception {
      AtomicInteger errors = new AtomicInteger(0);

      ActiveMQServer server = createServer(false, true);

      Thread t = new Thread(() -> {
         try {
            Thread.sleep(500);
            server.start();
         } catch (Throwable e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }
      });
      t.start();
      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("(tcp://localhost:61618,tcp://localhost:61616,tcp://localhost:61610)?ha=true&retryInterval=100&retryIntervalMultiplier=1.0&reconnectAttempts=-1&initialConnectAttempts=-1&useTopologyForLoadBalancing=true");
      connectionFactory.createConnection().close();
      connectionFactory.close();


      t.join();

      assertEquals(0, errors.get());
   }


   @Test
   public void testNegativeMaxTries() throws Exception {

      long timeStart = System.currentTimeMillis();
      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("(tcp://localhost:61618,tcp://localhost:61616,tcp://localhost:61610)?ha=true&retryInterval=100&retryIntervalMultiplier=1.0&reconnectAttempts=-1&initialConnectAttempts=2&useTopologyForLoadBalancing=true");
      boolean failed = false;
      try {
         connectionFactory.createConnection();
      } catch (JMSException e) {
         // expected
         failed = true;
      }
      assertTrue(failed);
      long timeEnd = System.currentTimeMillis();
      assertTrue(timeEnd - timeStart >= 500, "3 connectors, at 100 milliseconds each try, initialConnectAttempt=2, it should have waited at least 600 (- 100 from the last try that we don't actually wait, just throw ) milliseconds");
   }

   @Test
   public void testRetryIntervalMultiplier() {
      int interval = 100;
      double multiplier = 10.0;
      int attempts = 3;
      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61610?retryInterval=" + interval + "&retryIntervalMultiplier=" + multiplier + "&initialConnectAttempts=" + attempts);
      long timeStart = System.currentTimeMillis();
      try {
         connectionFactory.createConnection();
         fail("Creating connection here should have failed");
      } catch (JMSException e) {
         // expected
      }
      long duration = System.currentTimeMillis() - timeStart;
      long toWait = 1100;
      assertTrue(duration >= toWait, "Waited only " + duration + "ms, but should have waiting " + toWait);
   }

   @Test
   public void testMaxRetryInterval() {
      int interval = 100;
      double multiplier = 50.0;
      int attempts = 3;
      int maxInterval = 1000;
      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61610?retryInterval=" + interval + "&retryIntervalMultiplier=" + multiplier + "&initialConnectAttempts=" + attempts + "&maxRetryInterval=" + maxInterval);
      long timeStart = System.currentTimeMillis();
      try {
         connectionFactory.createConnection();
         fail("Creating connection here should have failed");
      } catch (JMSException e) {
         // expected
      }
      long duration = System.currentTimeMillis() - timeStart;
      long toWait = 1100;
      assertTrue(duration >= toWait, "Waited only " + duration + "ms, but should have waited " + toWait);
      assertTrue(duration <= toWait + 500, "Waited " + duration + "ms, but should have only waited " + (toWait + 500));
   }
}
