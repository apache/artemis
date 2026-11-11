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
package org.apache.activemq.artemis.tests.leak;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.assertMemory;
import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.basicMemoryAsserts;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ConnectionDroppedLeakTest extends AbstractLeakTest {

   private ConnectionFactory createConnectionFactory(String protocol) {
      if (protocol.equals("AMQP")) {
         return CFUtil.createConnectionFactory("AMQP", "amqp://localhost:61616?amqp.idleTimeout=120000&failover.maxReconnectAttempts=1&jms.prefetchPolicy.all=10&jms.forceAsyncAcks=true");
      } else {
         return CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      }
   }

   private static final String QUEUE_NAME = "QUEUE_DROP";

   ActiveMQServer server;

   Queue serverQueue;

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @AfterEach
   public void validateServer() throws Exception {
      CheckLeak checkLeak = new CheckLeak();

      // I am doing this check here because the test method might hold a client connection
      // so this check has to be done after the test, and before the server is stopped
      assertMemory(checkLeak, 0, RemotingConnectionImpl.class.getName());

      server.stop();

      server = null;
      serverQueue = null;

      clearServers();
      ServerStatus.clear();

      assertMemory(checkLeak, 0, ActiveMQServerImpl.class.getName());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      server = createServer(true, createDefaultConfig(1, true));
      server.getConfiguration().setJournalPoolFiles(4).setJournalMinFiles(2);
      server.start();
      server.addAddressInfo(new AddressInfo(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST));

      serverQueue = server.createQueue(QueueConfiguration.of(QUEUE_NAME).setAddress(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST).setDurable(true));
   }

   @Test
   public void testDropConnectionsAMQP() throws Exception {
      doDropConnections("AMQP");
   }

   @Test
   public void testDropConnectionsCORE() throws Exception {
      doDropConnections("CORE");
   }

   @Test
   public void testDropConnectionsOPENWIRE() throws Exception {
      doDropConnections("OPENWIRE");
   }

   private void doDropConnections(String protocol) throws Exception {
      basicMemoryAsserts();

      CountDownLatch latchDone = new CountDownLatch(2);
      CountDownLatch latchReceived = new CountDownLatch(50);
      AtomicInteger errors = new AtomicInteger(2);
      AtomicBoolean running = new AtomicBoolean(true);

      ExecutorService executorService = Executors.newFixedThreadPool(2);
      runAfter(executorService::shutdownNow);
      runAfter(() -> running.set(false));

      executorService.execute(() -> {
         ConnectionFactory cf = createConnectionFactory(protocol);
         Connection connection = null;
         try {
            connection = cf.createConnection(); // I will leave this open on purpose
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            while (running.get()) {
               Message message = consumer.receive(100);
               if (message != null) {
                  latchReceived.countDown();
                  session.commit();
               }
            }
         } catch (Exception e) {
            errors.incrementAndGet();
         } finally {
            if (protocol.equals("OPENWIRE")) {
               try {
                  connection.close();// only closing the openwire as it would leave a hanging thread
               } catch (Throwable ignored) {
               }
            }
            latchDone.countDown();
         }
      });

      executorService.execute(() -> {
         ConnectionFactory cf = createConnectionFactory(protocol);
         Connection connection = null;
         try {
            connection = cf.createConnection(); // I will leave this open on purpose
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
            while (running.get()) {
               producer.send(session.createTextMessage("hello"));
               session.commit();
            }
         } catch (Exception e) {
            errors.incrementAndGet();
         } finally {
            if (protocol.equals("OPENWIRE")) {
               try {
                  connection.close();// only closing the openwire as it may leave a hanging thread
               } catch (Throwable ignored) {
               }
            }
            latchDone.countDown();
         }
      });

      assertTrue(latchReceived.await(10, TimeUnit.SECONDS));

      server.getRemotingService().getConnections().forEach(r -> {
         r.fail(new ActiveMQException("it's a simulation"));
      });

      assertTrue(latchDone.await(30, TimeUnit.SECONDS));
      running.set(false);

      serverQueue.deleteAllReferences();

      basicMemoryAsserts();
   }
}