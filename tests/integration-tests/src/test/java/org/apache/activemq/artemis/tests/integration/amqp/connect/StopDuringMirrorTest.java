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
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StopDuringMirrorTest extends ActiveMQTestBase {


   private static final int EXIT_AS_EXPECTED = 7;
   private static final int ERROR = 11;

   private static String QUEUE_NAME = "StopDuringMirror";

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int NUMBER_OF_MESSAGES = 5;

   ActiveMQServer server;

   ExecutorService executorService;

   public static void main(String[] arg) {
      try {
         StopDuringMirrorTest stopDuringMirrorTest = new StopDuringMirrorTest();
         stopDuringMirrorTest.spawnedRun(arg[0]);
         for (;;) {
            Thread.sleep(1000);
         }
      } catch (Throwable e) {
         e.printStackTrace();
         System.exit(ERROR);
      }
   }


   public void spawnedRun(String temporaryFolder) throws Exception {
      this.temporaryFolder = new File(temporaryFolder);

      ActiveMQServer server = createServer();
      server.start();
      MirrorController controller = server.getPostOffice().getMirrorControlSource();
      assertNotNull(controller);
      server.getPostOffice().setMirrorControlSource(new KillServerController(controller));
      this.server = server;
   }

   @Test
   public void testStopDuringRoute() throws Exception {
      Process process = SpawnedVMSupport.spawnVM(StopDuringMirrorTest.class.getName(), temporaryFolder.getAbsolutePath());
      runAfter(process::destroyForcibly);
      ServerUtil.waitForServerToStart(0, (int)TimeUnit.SECONDS.toMillis(20));

      executorService = Executors.newSingleThreadExecutor();
      runAfter(executorService::shutdown);

      ConnectionFactory cf = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
         producer.send(session.createTextMessage("hello"));
      } catch (Throwable expected) {
         logger.info(expected.getMessage(), expected);
      }

      assertTrue(process.waitFor(5, TimeUnit.MINUTES));

      assertEquals(EXIT_AS_EXPECTED, process.exitValue());

      server = createServer();
      server.start();

      Queue queue = server.locateQueue(QUEUE_NAME);

      assertEquals(0, queue.getMessageCount());

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         connection.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
         assertNull(consumer.receiveNoWait());
      } catch (Exception expected) {
         logger.info(expected.getMessage(), expected);
      }

      assertEquals(0, queue.getMessageCount());
   }

   private ActiveMQServer createServer() throws Exception {
      Configuration configuration = createDefaultConfig(0, false);
      configuration.getAddressConfigurations().clear();
      configuration.setResolveProtocols(true);
      configuration.setMirrorAckManagerRetryDelay(100).setMirrorAckManagerPageAttempts(5).setMirrorAckManagerQueueAttempts(5);
      configuration.addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST));
      configuration.addAcceptorConfiguration("clients", "tcp://localhost:61616");
      AMQPBrokerConnectConfiguration brokerConnectConfiguration = new AMQPBrokerConnectConfiguration("willNeverConnect", "tcp://localhost:61617").setRetryInterval(60_000).setReconnectAttempts(-1);
      AMQPMirrorBrokerConnectionElement mirror = new AMQPMirrorBrokerConnectionElement().setDurable(true);
      brokerConnectConfiguration.addMirror(mirror);
      configuration.addAMQPConnection(brokerConnectConfiguration);
      org.apache.activemq.artemis.core.server.ActiveMQServer server = createServer(true, configuration);
      server.setIdentity("server1");
      return server;
   }

   class KillServerController implements MirrorController {
      MirrorController target;

      KillServerController(MirrorController target) {
         this.target = target;
      }

      @Override
      public boolean isRetryACK() {
         return target.isRetryACK();
      }

      @Override
      public void addAddress(AddressInfo addressInfo) throws Exception {
         target.addAddress(addressInfo);
      }

      @Override
      public void deleteAddress(AddressInfo addressInfo) throws Exception {
         target.deleteAddress(addressInfo);
      }

      @Override
      public void createQueue(QueueConfiguration queueConfiguration) throws Exception {
         target.createQueue(queueConfiguration);
      }

      @Override
      public void deleteQueue(SimpleString addressName, SimpleString queueName, QueueConfiguration configuration) throws Exception {
         target.deleteQueue(addressName, queueName, configuration);
      }

      @Override
      public void sendMessage(Transaction tx, Message message, RoutingContext context) {
         try {
            System.out.println("..... send message" + message);
            server.getStorageManager().getContext().waitCompletion(5000);
            // to make the client to exit earlier
            server.getRemotingService().stop(false);
            // this is exiting a spawned server
            System.exit(EXIT_AS_EXPECTED);
         } catch (Exception e) {
         }
      }

      @Override
      public void postAcknowledge(MessageReference ref, AckReason reason) throws Exception {
         target.postAcknowledge(ref, reason);
      }

      @Override
      public void preAcknowledge(Transaction tx, MessageReference ref, AckReason reason) throws Exception {
         target.preAcknowledge(tx, ref, reason);
      }

      @Override
      public String getRemoteMirrorId() {
         return target.getRemoteMirrorId();
      }
   }
}