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
package org.apache.activemq.artemis.tests.integration.jms.connection;

import javax.jms.Connection;
import javax.jms.Session;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Attempts to reproduce a server-side InVM connection leak where {@code RemotingServiceImpl.removeConnection} is not
 * invoked even though {@code ActiveMQConnection.close()} was called.
 * <p>
 * Two independent defects can cause this and are guarded here:
 * <ol>
 *    <li>A race in {@code InVMConnection.internalClose} where a concurrent close could return before
 *    {@code connectionDestroyed} fired (fixed by making the close fully atomic).</li>
 *    <li>{@code InVMAcceptor}/{@code InVMConnector} reporting <em>every</em> close (including a graceful
 *    {@code close()}) to the server as a failure, which routed it through {@code issueFailure} where the
 *    {@code isSupportReconnect()} guard could skip removal. When the client enables a confirmation window the
 *    server-side connection reports {@code isSupportReconnect() == true}; if the session channel is still present at
 *    transport-teardown time the connection was never removed, and because the InVM connection-ttl is -1 the
 *    failure-check reaper never removes it either - a permanent leak.</li>
 * </ol>
 * The bug is timing dependent (it depends on the ordering of {@code SESS_CLOSE} processing versus transport teardown),
 * so this test floods the broker with many short-lived reconnect-capable connections to widen the race window. It may
 * not fail on every machine, but on hardware/timing where the race is hit it will leave server connections behind.
 */
public class InVMConnectionLeakStressTest extends JMSTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQConnectionFactory floodCf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      floodCf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      // A positive confirmation window makes the server-side connection report isSupportReconnect() == true, which is
      // what used to make issueFailure()/issueClose() skip removeConnection().
      floodCf.setConfirmationWindowSize(1024 * 1024);
      floodCf.setReconnectAttempts(-1);
   }

   @Test
   public void testConcurrentGracefulCloseRemovesAllConnections() throws Exception {
      final int numConnections = 20_000;

      AtomicInteger error = new AtomicInteger(0);
      CountDownLatch latch = new CountDownLatch(numConnections);
      ExecutorService executor = Executors.newFixedThreadPool(100);
      runAfter(executor::shutdownNow);

      for (int i = 0; i < numConnections; i++) {
         executor.execute(() -> {
            try {
               Connection connection = floodCf.createConnection();
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               session.createProducer(ActiveMQJMSClient.createQueue("stress-queue"));
               // Graceful close
               connection.close();
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
               error.incrementAndGet();
            } finally {
               latch.countDown();
            }
         });
      }

      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(0, error.get());

      // Every gracefully-closed connection must be removed from the server. InVM connection-ttl is -1 so the
      // failure-check reaper never removes them; if this never reaches 0 the connections have leaked.
      Wait.assertEquals(0, () -> server.getRemotingService().getConnectionCount(), 10_000);
   }
}
