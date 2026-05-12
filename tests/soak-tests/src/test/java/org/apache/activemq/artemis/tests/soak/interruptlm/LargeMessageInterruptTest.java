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

package org.apache.activemq.artemis.tests.soak.interruptlm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This is used to kill a server and make sure the server will remove any pending files.
public class LargeMessageInterruptTest extends SoakTestBase {

   public static final String SERVER_NAME_0 = "interruptlm";

   @BeforeAll
   public static void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("artemis").setPassword("artemis").setAllowAnonymous(true).setArtemisInstance(serverLocation).
            setConfiguration("./src/main/resources/servers/interruptlm");
         cliCreateServer.setArgs("--java-options", "-Djava.rmi.server.hostname=localhost", "--clustered", "--static-cluster", "tcp://localhost:61716", "--queues", "ClusteredLargeMessageInterruptTest", "--name", "lmbroker1", "--cluster-user", "my-cluster-user", "--cluster-password", "my-cluster-password");
         cliCreateServer.createServer();
      }
   }

   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final int JMX_SERVER_PORT_0 = 1099;
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   static String liveURI = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT_0 + "/jmxrmi";
   static ObjectNameBuilder nameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), "lminterrupt", true);
   Process serverProcess;

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      serverProcess = startServer(SERVER_NAME_0, 0, 30000);
      disableCheckThread();
   }

   private void killProcess(Process process) throws Exception {
      Runtime.getRuntime().exec("kill -SIGINT " + process.pid());
   }

   @Test
   public void testInterrupt() throws Throwable {
      final int BODY_SIZE = 500 * 1024;
      final int NUMBER_OF_MESSAGES = 10; // this is per producer

      // SENDING_THREADS must have the number of tasks submitted on executorService as we will use a CyclicBarrier to align
      // start and other controls. If you change the logic on the loop please update this number here.
      final int SENDING_THREADS = 12;

      CyclicBarrier startFlag = new CyclicBarrier(SENDING_THREADS);
      final CountDownLatch done = new CountDownLatch(SENDING_THREADS);
      final AtomicInteger produced = new AtomicInteger(0);
      final AtomicInteger errors = new AtomicInteger(0); // I don't expect many errors since this test is disconnecting and reconnecting the server
      final CountDownLatch killAt = new CountDownLatch(10);

      ExecutorService executorService = Executors.newFixedThreadPool(SENDING_THREADS);
      runAfter(executorService::shutdownNow);

      String queueName = "LargeMessageInterruptTest";
      String pagedQueueName = "LargeMessageInterruptTestPaged";

      String largebody = RandomUtil.randomAlphaNumericString(BODY_SIZE);

      {
         ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(session.createQueue(pagedQueueName));
            for (int i = 0; i < 1000; i++) {
               producer.send(session.createTextMessage("forcePage"));
            }
            session.commit();
         }
      }

      String[] protocols = new String[]{"CORE", "AMQP", "OPENWIRE"};
      String[] destinations = new String[] {queueName, pagedQueueName};

      for (String queueUsed : destinations) {
         for (String protocolUsed : protocols) {
            for (int i = 0; i <= 1; i++) {
               boolean tx = i > 0;
               logger.info("sending protocol {}, destination {}, tx={}", protocolUsed, queueUsed, tx);
               executorService.execute(() -> {
                  int numberOfMessages = 0;
                  try {
                     final ConnectionFactory factory = CFUtil.createConnectionFactory(protocolUsed, "tcp://localhost:61616");
                     Connection connection = factory.createConnection();
                     Session session = connection.createSession(tx, tx ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
                     MessageProducer producer = session.createProducer(session.createQueue(queueUsed));

                     startFlag.await(10, TimeUnit.SECONDS);
                     while (numberOfMessages < NUMBER_OF_MESSAGES) {
                        try {
                           producer.send(session.createTextMessage(largebody));
                           if (tx) {
                              session.commit();
                           }
                           produced.incrementAndGet();
                           killAt.countDown();
                           if (numberOfMessages++ % 10 == 0) {
                              logger.info("Sent {}", numberOfMessages);
                           }
                        } catch (Exception e) {
                           logger.warn(e.getMessage(), e);
                           try {
                              connection.close();
                           } catch (Throwable ignored) {
                           }

                           for (int retryNumber = 0; retryNumber < 100; retryNumber++) {
                              try {
                                 connection = factory.createConnection();
                              } catch (Throwable retry) {
                                 connection = null;
                                 Thread.sleep(500);
                              }
                           }

                           assertNotNull(connection, "retry did not work on createConnection");
                           session = connection.createSession(tx, tx ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
                           producer = session.createProducer(session.createQueue(queueName));
                           connection.start();

                        }
                     }
                  } catch (Throwable e) {
                     logger.warn("Error while sending protocol {}, destination {}, tx={}", protocolUsed, queueUsed, tx, e);
                     errors.incrementAndGet();
                  }

                  logger.info("Done sending");
                  done.countDown();
               });
            }
         }
      }

      assertTrue(killAt.await(60, TimeUnit.SECONDS));
      killProcess(serverProcess);
      assertTrue(serverProcess.waitFor(1, TimeUnit.MINUTES));
      serverProcess = startServer(SERVER_NAME_0, 0, 0);

      assertTrue(done.await(60, TimeUnit.SECONDS));
      assertEquals(0, errors.get());

      ServerUtil.waitForServerToStart(0, 60_000);

      verifyQueue(queueName, largebody);
      verifyQueue(pagedQueueName, largebody);

      File lmFolder = new File(getServerLocation(SERVER_NAME_0) + "/data/large-messages");
      assertTrue(lmFolder.exists());
      Wait.assertEquals(0, () -> lmFolder.listFiles().length);
   }

   private static void verifyQueue(String queueName, String largebody) throws Throwable {
      QueueControl queueControl = getQueueControl(liveURI, nameBuilder, queueName, queueName, RoutingType.ANYCAST, 5000);

      long numberOfMessages = queueControl.getMessageCount();
      logger.info("there are {} messages", numberOfMessages);

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
         connection.start();
         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertTrue(message.getText().equals("forcePage") || message.getText().equals(largebody));
         }
      }
   }

}