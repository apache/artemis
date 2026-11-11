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
package org.apache.activemq.artemis.tests.integration.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class DeleteQueueRestartTest extends ActiveMQTestBase {



   private static final String ADDRESS = "ADDRESS";



   @Test
   public void testDeleteQueueAndRestart() throws Exception {
      // This test could eventually pass, even when the queue was being deleted in the wrong order,
      // however it failed in 90% of the runs with 5 iterations.
      for (int i = 0; i < 5; i++) {
         setUp();
         internalDeleteQueueAndRestart();
         tearDown();
      }
   }

   private void internalDeleteQueueAndRestart() throws Exception {
      ActiveMQServer server = createServer(true);

      server.start();

      ServerLocator locator = createInVMNonHALocator().setBlockOnDurableSend(true).setBlockOnNonDurableSend(true).setMinLargeMessageSize(1024 * 1024);

      ClientSessionFactory factory = createSessionFactory(locator);

      final ClientSession session = factory.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(DeleteQueueRestartTest.ADDRESS));

      ClientProducer prod = session.createProducer(DeleteQueueRestartTest.ADDRESS);

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = createBytesMessage(session, ActiveMQBytesMessage.TYPE, new byte[0], true);
         prod.send(msg);
      }

      final CountDownLatch count = new CountDownLatch(1);

      // Using another thread, as the deleteQueue is a blocked call
      new Thread(() -> {
         try {
            session.deleteQueue(DeleteQueueRestartTest.ADDRESS);
            session.close();
            count.countDown();
         } catch (ActiveMQException e) {
         }
      }).start();

      assertTrue(count.await(5, TimeUnit.SECONDS));

      server.stop();

      server.start();

      server.stop();

   }
}
