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
package org.apache.activemq.artemis.tests.e2e.brokerConnection;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.tests.e2e.common.E2ETestBase;
import org.apache.activemq.artemis.tests.e2e.common.ContainerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * You need to build the Artemis Docker image with 'mvn install -De2e-tests.skipImageBuild=false' before this test is
 * executed.
 */
public class QpidDispatchPeerTest extends E2ETestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static Object network;
   static Object qpidServer;
   static Object artemisServer;

   static ContainerService service = ContainerService.getService();

   @BeforeEach
   public void disableThreadcheck() {
      disableCheckThread();
   }

   private static final String QDR_HOME = basedir + "/target/brokerConnect/qdr";

   @BeforeAll
   public static void startServers() throws Exception {
      ValidateContainer.assumeArtemisContainer();

      assertNotNull(basedir);

      network = service.newNetwork();

      artemisServer = service.newBrokerImage();
      service.setNetwork(artemisServer, network);
      service.exposePorts(artemisServer, 61616);
      service.prepareInstance(QDR_HOME);
      service.exposeBrokerHome(artemisServer, QDR_HOME);
      service.startLogging(artemisServer, "ArtemisServer:");

      qpidServer = service.newInterconnectImage();
      service.setNetwork(qpidServer, network);
      service.exposePorts(qpidServer, 5672);
      service.exposeHosts(qpidServer, "qdr");
      service.exposeFile(qpidServer, basedir + "/src/main/resources/servers/brokerConnect/qdr/qdrouterd.conf", "/tmp/qdrouterd.conf");
      service.exposeFolder(qpidServer, basedir + "/target/brokerConnect/qdr", "/routerlog");
      service.startLogging(qpidServer, "qpid-dispatch:");
      service.start(qpidServer);

      recreateBrokerDirectory(QDR_HOME);

      service.start(artemisServer);

   }

   @AfterAll
   public static void stopServer() {
      service.stop(artemisServer);
      service.stop(qpidServer);
   }

   @Test
   public void testSendReceive() throws Exception {

      int numberOfMessages = 100;

      for (int dest = 0; dest < 5; dest++) {
         {
            ConnectionFactory factoryProducer = service.createCF(qpidServer, "amqp", 5672, "?amqpIdleTimeout=1000");
            Connection connection = null;

            connection = createConnectionDumbRetry(factoryProducer);

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("queue.test" + dest);
            MessageProducer producer = session.createProducer(queue);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            for (int i = 0; i < numberOfMessages; i++) {
               logger.debug("Sending {}", i);
               producer.send(session.createTextMessage("hello " + i));
            }
            connection.close();
         }

         {
            ConnectionFactory factoryConsumer = service.createCF(artemisServer, "amqp", 61616, "?amqpIdleTimeout=1000");
            Connection connectionConsumer = factoryConsumer.createConnection("artemis", "artemis");
            Session sessionConsumer = connectionConsumer.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queueConsumer = sessionConsumer.createQueue("queue.test" + dest);
            MessageConsumer consumer = sessionConsumer.createConsumer(queueConsumer);
            connectionConsumer.start();

            for (int i = 0; i < numberOfMessages; i++) {
               Message message = consumer.receive(5000);
               assertNotNull(message);
            }

            connectionConsumer.close();
         }

      }

   }

   @Test
   public void testSendReceiveDistinct() throws Exception {

      int numberOfMessages = 100;

      {
         ConnectionFactory factoryProducer = service.createCF(qpidServer, "amqp", 5672, "?amqpIdleTimeout=1000");
         Connection connection = null;

         connection = createConnectionDumbRetry(factoryProducer);

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue("queue.dist");
         MessageProducer producer = session.createProducer(queue);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int i = 0; i < numberOfMessages; i++) {
            logger.debug("Sending {}", i);
            producer.send(session.createTextMessage("hello " + i));
         }
         connection.close();
      }

      {
         ConnectionFactory factoryConsumer = service.createCF(artemisServer, "amqp", 61616, "?amqpIdleTimeout=1000");
         Connection connectionConsumer = factoryConsumer.createConnection("artemis", "artemis");
         Session sessionConsumer = connectionConsumer.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queueConsumer = sessionConsumer.createQueue("queue.dist::distinct.dist");
         MessageConsumer consumer = sessionConsumer.createConsumer(queueConsumer);
         connectionConsumer.start();

         for (int i = 0; i < numberOfMessages; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
         }

         connectionConsumer.close();
      }


   }

   private Connection createConnectionDumbRetry(ConnectionFactory factoryProducer) throws InterruptedException {
      for (int i = 0; i < 100; i++) {
         try {
            // Some retry
            return factoryProducer.createConnection();
         } catch (Exception e) {
            Thread.sleep(10);
         }
      }
      return null;
   }

}
