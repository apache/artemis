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

package org.apache.activemq.artemis.tests.soak.stomp;

import java.io.File;

import java.net.URI;

import java.lang.invoke.MethodHandles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class StompSoakTest extends SoakTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_0 = "stomp/stompServer";

   private static final int THREADS = 10;
   private static final int NUMBER_OF_MESSAGES = 1000;

   Process serverProcess;

   @BeforeAll
   public static void createServer() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(false)
           .setArtemisInstance(serverLocation);
         // some limited memory to make it more likely to fail
         cliCreateServer.setArgs("--java-memory", "512M");
         cliCreateServer.createServer();
      }
   }

   @Test
   public void testStomp() throws Exception {
      serverProcess = startServer(SERVER_NAME_0, 0, 60_000);

      ExecutorService executorService = Executors.newFixedThreadPool(THREADS * 2);
      runAfter(executorService::shutdownNow);

      CountDownLatch done = new CountDownLatch(THREADS * 2);
      AtomicInteger errors = new AtomicInteger(0);

      StompClientConnection cc = null;
      try {
         cc = StompClientConnectionFactory.createClientConnection(new URI("tcp://127.0.0.1:61613"));
         cc.connect("admin", "admin");

         final StompClientConnection clientConnection = cc;
         for (int i = 0; i < THREADS; i++) {
            String destination = "CLIENT_" + i;
            String subId = "SUB_" + i;
            executorService.execute(() -> {
               try {
                  ClientStompFrame subscribeFrame = clientConnection.createFrame(Stomp.Commands.SUBSCRIBE).
                     addHeader(Stomp.Headers.Subscribe.ID, subId).
                     addHeader(Stomp.Headers.Subscribe.DESTINATION, destination);

                  clientConnection.sendFrame(subscribeFrame);

                  logger.debug("Subscribed to destination {} with id {}", destination, subId);

                  int receivedCount = 0;

                  while (clientConnection.isConnected() && receivedCount < NUMBER_OF_MESSAGES) {
                     ClientStompFrame clientStompFrame = clientConnection.receiveFrame();
                     if (clientStompFrame != null) {
                        receivedCount++;
                     } else {
                        Thread.yield();
                     }
                  }
                  logger.debug("Received count for destination {} is {}", destination, receivedCount);
               } catch (Throwable e) {
                  logger.warn(e.getMessage(), e);
                  errors.incrementAndGet();
               } finally {
                  done.countDown();
               }
            });
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      }

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616");
      cf.setUser("admin");
      cf.setPassword("admin");
      cf.setBlockOnNonDurableSend(false);
      Connection c = null;
      try {
         c = cf.createConnection();
         final Connection connection = c;
         for (int i = 0; i < THREADS; i++) {
            String destination = "CLIENT_" + i;
            executorService.execute(() -> {
               logger.debug("Creating session for destination {}", destination);
               try (Session session = connection.createSession(false, Session.DUPS_OK_ACKNOWLEDGE)) {
                  MessageProducer producer = session.createProducer(ActiveMQDestination.createDestination(RoutingType.MULTICAST,
                                                                                                          SimpleString.of(destination)));

                  logger.debug("Sending {} messages to destination {}", NUMBER_OF_MESSAGES, destination);

                  for (int messageCount = 0; messageCount < NUMBER_OF_MESSAGES; messageCount++) {
                     TextMessage message = session.createTextMessage("message-" + messageCount);
                     producer.send(message);
                  }
               } catch (Throwable e) {
                  logger.warn(e.getMessage(), e);
                  errors.incrementAndGet();
               } finally {
                  done.countDown();
               }
            });
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
         errors.incrementAndGet();
      }

      Assertions.assertTrue(done.await(10, TimeUnit.MINUTES));

      try {
         if (c != null) {
            c.close();
         }
         if (cc != null) {
            cc.closeTransport();
            cc.disconnect();
         }
      } catch (Throwable ignored) {
      }

      Assertions.assertEquals(0, errors.get());

      File artemisLog = new File("target/" + SERVER_NAME_0 + "/log/artemis.log");
      Assertions.assertFalse(findLogRecord(artemisLog, "AMQ222151"));
      Assertions.assertFalse(findLogRecord(artemisLog, "ConcurrentModificationException"));
   }
}
