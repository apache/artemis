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
package org.apache.activemq.artemis.tests.integration.ra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ClientSession.QueueQuery;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivationSpec;
import org.junit.jupiter.api.Test;

/**
 * Tests for single connection mode exception handling in ActiveMQActivation.
 * Related to ARTEMIS-5987: Properly handling exceptions with single connection.
 */
public class ActiveMQMessageHandlerSingleConnectionTest extends ActiveMQRATestBase {

   @Override
   public boolean useSecurity() {
      return false;
   }

   /**
    * Test that when using single connection mode and session creation fails because the server is
    * down, the activation completes without hanging and leaves no active consumers behind.
    *
    * Without the fix, the loop would continue after the shared ClientSessionFactory entered a broken
    * state, causing unnecessary retries against a dead factory instead of breaking early.
    */
   @Test
   public void testSingleConnectionFailsCleanlyWhenServerIsDown() throws Exception {
      server.stop();

      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setMaxSession(5);
      spec.setSingleConnection(true);
      spec.setSetupAttempts(1);
      spec.setSetupInterval(0L);

      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);

      // endpointActivation must complete without hanging even though the server is down.
      // Setup failures are handled asynchronously and do not propagate through endpointActivation.
      qResourceAdapter.endpointActivation(endpointFactory, spec);

      // Restart the server so we can send messages and verify no consumers are active.
      server.start();

      try (ClientSessionFactory sf = locator.createSessionFactory();
           ClientSession session = sf.createSession()) {
         session.start();
         ClientProducer producer = session.createProducer(MDBQUEUEPREFIXED);
         for (int i = 0; i < 3; i++) {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeString("test-message-" + i);
            producer.send(message);
         }
      }

      // No handlers should be active: the activation failed when the server was down and all
      // reconnect attempts (setupAttempts=1) have been exhausted before the server came back.
      assertFalse(latch.await(2, TimeUnit.SECONDS),
                  "Messages should not be consumed — activation failed before server came back up");

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   /**
    * Test that when using single connection mode and a mid-loop failure occurs, the loop breaks
    * immediately instead of continuing to create sessions on a shared factory that is now broken.
    *
    * A queue with maxConsumers=2 triggers a genuine server-enforced failure inside handler.setup()
    * when the 3rd session tries to subscribe. The fix ensures the loop breaks at that point,
    * producing exactly maxConsumers+1 CreateSession packets (2 successful + 1 failing). Without
    * the fix the loop runs all maxSession iterations, producing 5 packets and masking the original
    * error with secondary "factory closed" failures in the logs.
    *
    * setupAttempts=0 is used so setup() runs exactly once, keeping the packet count precise.
    */
   @Test
   public void testSingleConnectionMidLoopFailureBreaksEarly() throws Exception {
      AtomicInteger sessionCreateCount = new AtomicInteger(0);
      Interceptor countingInterceptor = (packet, connection) -> {
         if (packet instanceof CreateSessionMessage) {
            sessionCreateCount.incrementAndGet();
         }
         return true;
      };
      server.getRemotingService().addIncomingInterceptor(countingInterceptor);

      String limitedQueue = "limited-consumers-queue";
      server.createQueue(QueueConfiguration.of(limitedQueue)
                            .setAddress(limitedQueue)
                            .setRoutingType(RoutingType.ANYCAST)
                            .setMaxConsumers(2)
                            .setAutoCreated(false)
                            .setAutoDelete(false));

      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(limitedQueue);
      spec.setMaxSession(5);
      spec.setSingleConnection(true);
      spec.setSetupAttempts(0);  // no retries — exactly one setup() call, keeping the count exact
      spec.setSetupInterval(0L);

      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);

      qResourceAdapter.endpointActivation(endpointFactory, spec);

      server.getRemotingService().removeIncomingInterceptor(countingInterceptor);

      // With the fix the loop breaks after session index 2 (the first failure):
      //   session 0 → CreateSession #1, consumer created OK
      //   session 1 → CreateSession #2, consumer created OK
      //   session 2 → CreateSession #3, consumer rejected (maxConsumers=2) → cf.close() + break
      // Without the fix the loop continues through all 5 iterations, sending 5 CreateSession
      // packets even though sessions 2-4 will all fail at consumer creation.
      assertEquals(3, sessionCreateCount.get(),
                   "Expected 3 CreateSession packets (maxConsumers+1); without the fix all 5 are sent");

      // Verify all-or-nothing teardown: handlers 0 and 1 must also be torn down.
      Thread.sleep(500);
      try (ClientSessionFactory sf = locator.createSessionFactory();
           ClientSession verifySession = sf.createSession()) {
         QueueQuery queueQuery = verifySession.queueQuery(SimpleString.of(limitedQueue));
         assertEquals(0, queueQuery.getConsumerCount(),
                      "Queue should have 0 active consumers — all handlers torn down after mid-loop failure");
      }

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
      server.destroyQueue(SimpleString.of(limitedQueue));
   }

   /**
    * Test that single connection mode works correctly under normal operation — all sessions
    * share one underlying connection and messages are delivered.
    */
   @Test
   public void testSingleConnectionNormalOperation() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setMaxSession(3);
      spec.setSingleConnection(true);

      CountDownLatch latch = new CountDownLatch(3);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);

      qResourceAdapter.endpointActivation(endpointFactory, spec);

      try (ClientSessionFactory sf = locator.createSessionFactory();
           ClientSession session = sf.createSession()) {
         ClientProducer producer = session.createProducer(MDBQUEUEPREFIXED);
         for (int i = 0; i < 3; i++) {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeString("test-message-" + i);
            producer.send(message);
         }
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS), "All 3 messages should be received within 5s");
      assertNotNull(endpoint.lastMessage, "At least one message should have been received");

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

}
