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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SessionFactoryCloseTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false);

      server.start();
   }

   @Test
   public void testCloseSessionFactory() throws Exception {
      ServerLocator locator = createInVMNonHALocator().setReconnectAttempts(-1).setConnectionTTL(1000).setClientFailureCheckPeriod(100).setConsumerWindowSize(10 * 1024 * 1024).setCallTimeout(1000);
      ClientSessionFactory sf = locator.createSessionFactory();

      final CountDownLatch latch = new CountDownLatch(1);
      sf.addFailoverListener(eventType -> {
         if (eventType == FailoverEventType.FAILURE_DETECTED) {
            try {
               /*
                * We close client session factory during this period and expect reconnection stopped without exception
                * which notifies FAILOVER_FAILED event. See ARTEMIS-1949.
                */
               Thread.sleep(1000L);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
         } else if (eventType == FailoverEventType.FAILOVER_FAILED) {
            latch.countDown();
         }
      });
      server.stop();

      Thread.sleep(600);
      sf.close();
      assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
   }
}
