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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.InvalidClientIDException;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class JMSConnectionTest extends JMSClientTestSupport {

   @Test
   @Timeout(60)
   public void testConnection() throws Exception {
      Connection connection = createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         session.createConsumer(queue);

         Queue queueView = getProxyToQueue(getQueueName());

         Wait.assertEquals(1, server::getConnectionCount);
         Wait.assertEquals(1, server::getTotalConsumerCount);

         assertEquals(1, queueView.getConsumerCount());

         connection.close();

         Wait.assertEquals(0, server::getConnectionCount);
         Wait.assertEquals(0, server::getTotalConsumerCount);
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testClientIDsAreExclusive() throws Exception {
      Connection testConn1 = createConnection(false);
      Connection testConn2 = createConnection(false);

      try {
         testConn1.setClientID("client-id1");
         try {
            testConn1.setClientID("client-id2");
            fail("didn't get expected exception");
         } catch (javax.jms.IllegalStateException e) {
            // expected
         }

         try {
            testConn2.setClientID("client-id1");
            fail("didn't get expected exception");
         } catch (InvalidClientIDException e) {
            // expected
         }
      } finally {
         testConn1.close();
         testConn2.close();
      }

      try {
         testConn1 = createConnection(false);
         testConn2 = createConnection(false);
         testConn1.setClientID("client-id1");
         testConn2.setClientID("client-id2");
      } finally {
         testConn1.close();
         testConn2.close();
      }
   }

   @Test
   @Timeout(60)
   public void testParallelConnections() throws Exception {
      final int numThreads = 40;
      ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
      for (int i = 0; i < numThreads; i++) {
         executorService.execute(() -> {
            try {
               Connection connection = createConnection(fullPass, fullUser);
               connection.start();
               connection.close();
            } catch (JMSException e) {
               e.printStackTrace();
            }
         });
      }

      executorService.shutdown();
      assertTrue(executorService.awaitTermination(30, TimeUnit.SECONDS), "executor done on time");
   }
}
