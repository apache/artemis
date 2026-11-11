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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerBlockedLeakTest extends AbstractLeakTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private static final String QUEUE_NAME = "TEST_BLOCKED_QUEUE";

   ActiveMQServer server;

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, createDefaultConfig(1, true));
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK).setMaxSizeMessages(10));
      server.start();
   }

   @Test
   public void testOPENWIRE() throws Exception {
      testBlocked("OPENWIRE");
   }

   @Test
   public void testCORE() throws Exception {
      testBlocked("CORE");
   }

   @Test
   public void testAMQP() throws Exception {
      testBlocked("AMQP");
   }

   private void testBlocked(String protocol) throws Exception {
      testBody(protocol);
      MemoryAssertions.basicMemoryAsserts(false);
      Queue queue = server.locateQueue(QUEUE_NAME);
      queue.deleteAllReferences();
      MemoryAssertions.basicMemoryAsserts(true);
      server.stop();
   }

   // separating the test into a sub-method just to allow removing local references
   // so they would be gone when basicMemoryAsserts is called
   private void testBody(String protocol) throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         AtomicInteger messagesSent = new AtomicInteger(0);

         server.addAddressInfo(new AddressInfo(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST));
         server.createQueue(QueueConfiguration.of(QUEUE_NAME).setAddress(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST).setDurable(true));

         // clients need to be disconnected while blocked. For that reason a new VM is being spawned
         Process process = SpawnedVMSupport.spawnVM(ProducerBlockedLeakTest.class.getName(), protocol, "10");

         // checking the logs that the destination is blocked...
         Wait.assertTrue(() -> loggerHandler.findText("AMQ222183"), 5000, 10);

         process.destroyForcibly();
         assertTrue(process.waitFor(10, TimeUnit.SECONDS));

         // Making sure there are no connections anywhere in Acceptors or RemotingService.
         // Just to speed up the test especially in OpenWire
         server.getRemotingService().getConnections().forEach(c -> c.fail(new ActiveMQException("this is it!")));
         Wait.assertEquals(0, () -> server.getRemotingService().getConnectionCount());
         server.getRemotingService().getAcceptors().forEach((a, b) -> {
            if (b instanceof NettyAcceptor acceptor) {
               acceptor.getConnections().clear();
            }
         });
      }
   }

   public static void main(String[] arg) {
      String protocol = arg[0];
      int threads = Integer.parseInt(arg[1]);
      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      ExecutorService executorService = Executors.newFixedThreadPool(threads);

      for (int i = 0; i < threads; i++) {
         executorService.execute(() -> {
            try {
               Connection connection = factory.createConnection();
               Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
               MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
               for (int send = 0; send < 100; send++) {
                  producer.send(session.createTextMessage("hello"));
                  session.commit();
               }
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
               Runtime.getRuntime().halt(-1);
            }
         });
      }
      try {
         while (true) {
            Thread.sleep(1000);
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         Runtime.getRuntime().halt(-1);
      }
   }

}