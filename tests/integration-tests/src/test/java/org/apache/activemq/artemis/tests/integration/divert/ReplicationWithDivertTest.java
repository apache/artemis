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
package org.apache.activemq.artemis.tests.integration.divert;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.ReplicatedBackupUtils;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReplicationWithDivertTest extends ActiveMQTestBase {

   public static final String JMS_SOURCE_QUEUE = "Queue";
   public static final String SOURCE_QUEUE = JMS_SOURCE_QUEUE;
   public static final String JMS_TARGET_QUEUE = "DestQueue";
   public static final String TARGET_QUEUE = JMS_TARGET_QUEUE;
   public static int messageChunkCount = 0;

   private static ActiveMQServer backupServer;
   private static ActiveMQServer primaryServer;

   ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616?minLargeMessageSize=10000&HA=true&retryInterval=100&reconnectAttempts=-1&producerWindowSize=10000");
   ActiveMQConnection connection;
   Session session;
   Queue queue;
   Queue targetQueue;
   MessageProducer producer;

   Configuration backupConfig;

   Configuration primaryConfig;

   // To inform the main thread the condition is met
   static final ReusableLatch flagChunkEntered = new ReusableLatch(1);
   // To wait while the condition is worked out
   static final ReusableLatch flagChunkWait = new ReusableLatch(1);

   // To inform the main thread the condition is met
   static final ReusableLatch flagSyncEntered = new ReusableLatch(1);
   // To wait while the condition is worked out
   static final ReusableLatch flagSyncWait = new ReusableLatch(1);

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      flagChunkEntered.setCount(1);
      flagChunkWait.setCount(1);

      flagSyncEntered.setCount(1);
      flagSyncWait.setCount(1);

      messageChunkCount = 0;

      TransportConfiguration primaryConnector = TransportConfigurationUtils.getNettyConnector(true, 0);
      TransportConfiguration primaryAcceptor = TransportConfigurationUtils.getNettyAcceptor(true, 0);
      TransportConfiguration backupConnector = TransportConfigurationUtils.getNettyConnector(false, 0);
      TransportConfiguration backupAcceptor = TransportConfigurationUtils.getNettyAcceptor(false, 0);

      backupConfig = createDefaultInVMConfig().setBindingsDirectory(getBindingsDir(0, true)).
         setJournalDirectory(getJournalDir(0, true)).setPagingDirectory(getPageDir(0, true)).
         setLargeMessagesDirectory(getLargeMessagesDir(0, true));
      backupConfig.addQueueConfiguration(QueueConfiguration.of(SOURCE_QUEUE).setRoutingType(RoutingType.ANYCAST));
      backupConfig.addQueueConfiguration(QueueConfiguration.of(TARGET_QUEUE).setRoutingType(RoutingType.ANYCAST));

      DivertConfiguration divertConfiguration = new DivertConfiguration().setName("Test").setAddress(SOURCE_QUEUE).setForwardingAddress(TARGET_QUEUE).setRoutingName("Test");

      primaryConfig = createDefaultInVMConfig();
      primaryConfig.addQueueConfiguration(QueueConfiguration.of(SOURCE_QUEUE).setRoutingType(RoutingType.ANYCAST));
      primaryConfig.addQueueConfiguration(QueueConfiguration.of(TARGET_QUEUE).setRoutingType(RoutingType.ANYCAST));
      primaryConfig.addDivertConfiguration(divertConfiguration);

      backupConfig.addDivertConfiguration(divertConfiguration);

      ReplicatedBackupUtils.configureReplicationPair(backupConfig, backupConnector, backupAcceptor, primaryConfig, primaryConnector, primaryAcceptor);

      primaryServer = createServer(primaryConfig);
      primaryServer.start();

      startBackup();

      waitForServerToStart(primaryServer);

      // Just to make sure the expression worked
      assertEquals(10000, factory.getMinLargeMessageSize());
      assertEquals(10000, factory.getProducerWindowSize());
      assertEquals(100, factory.getRetryInterval());
      assertEquals(-1, factory.getReconnectAttempts());
      assertTrue(factory.isHA());

      connection = (ActiveMQConnection) factory.createConnection();
      session = connection.createSession(true, Session.SESSION_TRANSACTED);
      queue = session.createQueue(JMS_SOURCE_QUEUE);
      targetQueue = session.createQueue(JMS_TARGET_QUEUE);

      producer = session.createProducer(queue);

   }

   private void startBackup() throws Exception {
      backupServer = createServer(backupConfig);
      backupServer.start();

      waitForServerToStart(backupServer);
   }

   @AfterEach
   public void stopServers() throws Exception {
      if (connection != null) {
         try {
            connection.close();
         } catch (Exception e) {
         }
      }
      if (backupServer != null) {
         backupServer.stop();
         backupServer = null;
      }

      if (primaryServer != null) {
         primaryServer.stop();
         primaryServer = null;
      }

      backupServer = primaryServer = null;
   }

   @Test
   public void testSendLargeMessage() throws Exception {

      final CountDownLatch failedOver = new CountDownLatch(1);
      connection.setFailoverListener(eventType -> failedOver.countDown());
      Thread t;

      final int numberOfMessage = 5;
      {
         final MapMessage message = createLargeMessage();

         t = new Thread(() -> {
            try {
               for (int i = 0; i < numberOfMessage; i++) {
                  producer.send(message);
                  session.commit();
               }
            } catch (JMSException expected) {
               expected.printStackTrace();
            }
         });
      }

      t.start();

      t.join(10000);

      {
         MessageConsumer consumer = session.createConsumer(queue);

         connection.start();

         for (int msgi = 0; msgi < numberOfMessage; msgi++) {
            MapMessage message = (MapMessage) consumer.receive(5000);

            assertNotNull(message);

            for (int i = 0; i < 10; i++) {
               assertEquals(200 * 1024, message.getBytes("test" + i).length);
            }

            session.commit();
         }
         consumer.close();
      }

      assertFalse(t.isAlive());
      primaryServer.fail(true);
      assertTrue(failedOver.await(10, TimeUnit.SECONDS));

      {
         MessageConsumer consumer = session.createConsumer(targetQueue);

         connection.start();

         for (int msgi = 0; msgi < numberOfMessage; msgi++) {
            MapMessage message = (MapMessage) consumer.receive(5000);

            assertNotNull(message);

            for (int i = 0; i < 10; i++) {
               assertEquals(200 * 1024, message.getBytes("test" + i).length);
            }

            session.commit();
         }

         consumer.close();
      }
   }

   private MapMessage createLargeMessage() throws JMSException {
      MapMessage message = session.createMapMessage();

      for (int i = 0; i < 10; i++) {
         message.setBytes("test" + i, new byte[200 * 1024]);
      }
      return message;
   }

}
