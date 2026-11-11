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
package org.apache.activemq.artemis.tests.integration.jms.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import javax.jms.Connection;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.tests.util.JMSClusteredTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultipleThreadsOpeningTest extends JMSClusteredTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /**
    * created for https://issues.apache.org/jira/browse/ARTEMIS-385
    */
   @Test
   public void testRepetitions() throws Exception {
      // This test was eventually failing with way over more iterations.
      // you might increase it for debugging
      final int ITERATIONS = 50;

      for (int i = 0; i < ITERATIONS; i++) {
         logger.info("#test {}", i);
         internalMultipleOpen(200, 1);
         tearDown();
         setUp();
      }
   }

   @Test
   public void testMultipleOpen() throws Exception {
      internalMultipleOpen(20, 500);
   }

   protected void internalMultipleOpen(final int numberOfThreads, final int numberOfOpens) throws Exception {

      cf1 = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(1)));
      // I want all the threads aligned, just ready to start creating connections like in a car race
      final CountDownLatch flagAlignSemaphore = new CountDownLatch(numberOfThreads);
      final CountDownLatch flagStartRace = new CountDownLatch(1);

      class ThreadOpen extends Thread {

         ThreadOpen(int i) {
            super("MultipleThreadsOpeningTest/ThreadOpen::" + i);
         }

         int errors = 0;

         @Override
         public void run() {

            try {
               flagAlignSemaphore.countDown();
               flagStartRace.await();

               for (int i = 0; i < numberOfOpens; i++) {
                  if (i > 0 && i % 100 == 0) {
                     logger.debug("connections created on Thread {} {}", Thread.currentThread(), i);
                  }
                  Connection conn = cf1.createConnection();
                  Session sess = conn.createSession(true, Session.AUTO_ACKNOWLEDGE);
                  sess.close();
                  conn.close();
               }
            } catch (Exception e) {
               e.printStackTrace();
               errors++;
            }
         }
      }

      ThreadOpen[] threads = new ThreadOpen[numberOfThreads];

      for (int i = 0; i < numberOfThreads; i++) {
         threads[i] = new ThreadOpen(i);
         threads[i].start();
      }

      flagAlignSemaphore.await();
      flagStartRace.countDown();

      try {
         for (ThreadOpen t : threads) {
            t.join(60000);
            assertFalse(t.isAlive());
            assertEquals(0, t.errors, "There are Errors on the test thread");
         }
      } finally {
         for (ThreadOpen t : threads) {
            if (t.isAlive()) {
               t.interrupt();
            }
            t.join(1000);
         }
      }
   }
}
