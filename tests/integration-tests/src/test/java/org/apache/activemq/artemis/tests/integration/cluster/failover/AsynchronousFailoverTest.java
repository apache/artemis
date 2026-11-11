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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQUnBlockedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.CountDownSessionFailureListener;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A MultiThreadFailoverTest
 * <p>
 * Test Failover where failure is prompted by another thread
 */
public class AsynchronousFailoverTest extends FailoverTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private volatile CountDownSessionFailureListener listener;

   private volatile ClientSessionFactoryInternal sf;

   private final Object lockFail = new Object();

   @Test
   public void testNonTransactional() throws Throwable {
      runTest(new TestRunner() {
         @Override
         public void run() {
            try {
               doTestNonTransactional(this);
            } catch (Throwable e) {
               logger.error("Test failed", e);
               addException(e);
            }
         }
      });
   }

   abstract class TestRunner implements Runnable {

      volatile boolean failed;

      List<Throwable> errors = new ArrayList<>();

      boolean isFailed() {
         return failed;
      }

      void setFailed() {
         failed = true;
      }

      void reset() {
         failed = false;
      }

      synchronized void addException(Throwable e) {
         errors.add(e);
      }

      void checkForExceptions() throws Throwable {
         if (!errors.isEmpty()) {
            logger.warn("Exceptions on test:");
            for (Throwable e : errors) {
               logger.warn(e.getMessage(), e);
            }
            // throwing the first error that happened on the Runnable
            throw errors.get(0);
         }

      }

   }

   private void runTest(final TestRunner runnable) throws Throwable {
      final int numIts = 1;

      try {
         for (int i = 0; i < numIts; i++) {
            logger.debug("Iteration {}", i);
            //set block timeout to 10 sec to reduce test time.
            ServerLocator locator = getServerLocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(30).setRetryInterval(100).setConfirmationWindowSize(10 * 1024 * 1024).setCallTimeout(10000).setCallFailoverTimeout(10000);

            sf = createSessionFactoryAndWaitForTopology(locator, 2);
            try {

               ClientSession createSession = sf.createSession(true, true);

               createSession.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS).setAddress(FailoverTestBase.ADDRESS));

               RemotingConnection conn = ((ClientSessionInternal) createSession).getConnection();

               Thread t = new Thread(runnable);

               t.setName("MainTEST");

               t.start();

               long randomDelay = (long) (2000 * Math.random());

               logger.debug("Sleeping {}", randomDelay);

               Thread.sleep(randomDelay);

               logger.debug("Failing asynchronously");

               // Simulate failure on connection
               synchronized (lockFail) {
                  logger.debug("#test crashing test");
                  crash(createSession);
               }

               /*if (listener != null)
               {
                  boolean ok = listener.latch.await(10000, TimeUnit.MILLISECONDS);

                  Assert.assertTrue(ok);
               }*/

               runnable.setFailed();

               logger.debug("Fail complete");

               t.join(TimeUnit.SECONDS.toMillis(120));
               if (t.isAlive()) {
                  System.out.println(threadDump("Thread still running from the test"));
                  t.interrupt();
                  fail("Test didn't complete successful, thread still running");
               }

               runnable.checkForExceptions();

               createSession.close();

               assertEquals(0, sf.numSessions());

               locator.close();
            } finally {
               locator.close();

               assertEquals(0, sf.numConnections());
            }

            if (i != numIts - 1) {
               tearDown();
               runnable.checkForExceptions();
               runnable.reset();
               setUp();
            }
         }
      } finally {
      }
   }

   protected void addPayload(ClientMessage msg) {
   }

   private void doTestNonTransactional(final TestRunner runner) throws Exception {
      while (!runner.isFailed()) {
         logger.debug("looping");

         ClientSession session = sf.createSession(true, true, 0);

         listener = new CountDownSessionFailureListener(session);

         session.addFailureListener(listener);

         ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

         final int numMessages = 1000;

         for (int i = 0; i < numMessages; i++) {
            boolean retry = false;
            do {
               try {
                  ClientMessage message = session.createMessage(true);

                  message.getBodyBuffer().writeString("message" + i);

                  message.putIntProperty("counter", i);

                  addPayload(message);

                  producer.send(message);

                  retry = false;
               } catch (ActiveMQUnBlockedException ube) {
                  logger.debug("exception when sending message with counter {}", i);

                  ube.printStackTrace();

                  retry = true;

               } catch (ActiveMQException e) {
                  fail("Invalid Exception type:" + e.getType());
               }
            }
            while (retry);
         }

         // create the consumer with retry if failover occurs during createConsumer call
         ClientConsumer consumer = null;
         boolean retry = false;
         do {
            try {
               consumer = session.createConsumer(FailoverTestBase.ADDRESS);

               retry = false;
            } catch (ActiveMQUnBlockedException ube) {
               logger.debug("exception when creating consumer");

               retry = true;

            } catch (ActiveMQException e) {
               fail("Invalid Exception type:" + e.getType());
            }
         }
         while (retry);

         session.start();

         List<Integer> counts = new ArrayList<>(1000);
         int lastCount = -1;
         boolean counterGap = false;
         while (true) {
            ClientMessage message = consumer.receive(500);

            if (message == null) {
               break;
            }

            // messages must remain ordered but there could be a "jump" if messages
            // are missing or duplicated
            int count = message.getIntProperty("counter");
            counts.add(count);
            if (count != lastCount + 1) {
               if (counterGap) {
                  fail("got another counter gap at " + count + ": " + counts);
               } else {
                  if (lastCount != -1) {
                     logger.debug("got first counter gap at {}", count);
                     counterGap = true;
                  }
               }
            }

            lastCount = count;

            message.acknowledge();
         }

         session.close();

         this.listener = null;
      }
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMAcceptor(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMConnector(live);
   }

}
