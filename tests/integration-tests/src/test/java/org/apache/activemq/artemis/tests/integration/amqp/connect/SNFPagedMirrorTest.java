/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AckManagerProvider;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SNFPagedMirrorTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   ActiveMQServer server1;

   ActiveMQServer server2;

   private static final String SNF_NAME = "$ACTIVEMQ_ARTEMIS_MIRROR_other";

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server1 = createServer(true, createDefaultConfig(0, true), 1024, -1, -1, -1);
      server1.getConfiguration().addAddressSetting(SNF_NAME, new AddressSettings().setMaxSizeBytes(-1).setMaxSizeMessages(-1));
      server1.getConfiguration().getAcceptorConfigurations().clear();
      server1.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:61616");
      AMQPBrokerConnectConfiguration brokerConnectConfiguration = new AMQPBrokerConnectConfiguration("other", "tcp://localhost:61617").setReconnectAttempts(-1).setRetryInterval(1000);
      brokerConnectConfiguration.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
      server1.getConfiguration().addAMQPConnection(brokerConnectConfiguration);
      server1.getConfiguration().addAddressSetting(SNF_NAME, new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL));

      server2 = createServer(true, createDefaultConfig(1, true), 1024, -1, -1, -1);
      server2.getConfiguration().addAddressSetting(SNF_NAME, new AddressSettings().setMaxSizeBytes(-1).setMaxSizeMessages(-1));
      server2.getConfiguration().getAcceptorConfigurations().clear();
      server2.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:61617");
      brokerConnectConfiguration = new AMQPBrokerConnectConfiguration("other", "tcp://localhost:61616").setReconnectAttempts(-1).setRetryInterval(1000);
      brokerConnectConfiguration.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(true));
      server2.getConfiguration().addAddressSetting(SNF_NAME, new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL));
      server2.getConfiguration().addAMQPConnection(brokerConnectConfiguration);

      server1.start();
      server2.start();
   }

   @Test
   public void testPagedEverything() throws Throwable {
      testSNFPaged("CORE", true, true, false, 1024);
   }

   @Test
   public void testPageQueueOnly() throws Throwable {
      testSNFPaged("CORE", false, true, false, 1024);
   }

   @Test
   public void testPageSNF() throws Throwable {
      testSNFPaged("CORE", true, false, false, 1024);
   }

   @Test
   public void testNothingPaged() throws Throwable {
      testSNFPaged("CORE", false, true, false, 1024);
   }

   @Test
   public void testPageTargetQueue() throws Throwable {
      testSNFPaged("CORE", false, false, true, 1024);
   }

   @Test
   public void testPageTargetQueueAMQPLarge() throws Throwable {
      testSNFPaged("AMQP", false, false, true, 250 * 1024);
   }

   @Test
   public void testTargetPaged() throws Throwable {

      server1.setIdentity("server1");
      server2.setIdentity("server2");

      String QUEUE_NAME = "q" + RandomUtil.randomUUIDString();
      String server2URI = "tcp://localhost:61617";

      Wait.waitFor(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other") != null);
      Wait.waitFor(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other") != null);

      org.apache.activemq.artemis.core.server.Queue snf1 = server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other");
      assertNotNull(snf1);

      assertEquals(2, AckManagerProvider.getSize());

      server1.addAddressInfo(new AddressInfo(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST));
      org.apache.activemq.artemis.core.server.Queue queueOnServer1 = server1.createQueue(QueueConfiguration.of(QUEUE_NAME).setAddress(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      Wait.assertTrue(() -> server2.locateQueue(QUEUE_NAME) != null);

      org.apache.activemq.artemis.core.server.Queue queueServer2 = server2.locateQueue(QUEUE_NAME);
      snf1.getPagingStore().startPaging();

      Wait.assertEquals(0, snf1::getMessageCount);

      queueServer2.getPagingStore().stopPaging();

      int NUMBER_OF_MESSAGES = 5000;

      ConnectionFactory factory1 = CFUtil.createConnectionFactory("CORE", server2URI);
      logger.info("Starting producer");
      try (Connection connection = factory1.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.send(session.createTextMessage("hello " + i));
         }
         session.commit();
      }
      logger.info("Consumed messages");

      Wait.assertEquals(0, snf1::getMessageCount, 5000);
      Wait.assertEquals(NUMBER_OF_MESSAGES, queueServer2::getMessageCount);

   }

   public void testSNFPaged(String protocol, boolean pageSNF, boolean pageQueue, boolean pageTarget, int messageSize) throws Throwable {

      server1.setIdentity("server1");
      server2.setIdentity("server2");

      String QUEUE_NAME = "q" + RandomUtil.randomUUIDString();
      String sendURI = "tcp://localhost:61616";
      String consumerURI = "tcp://localhost:61617";

      Wait.waitFor(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other") != null);
      Wait.waitFor(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other") != null);

      org.apache.activemq.artemis.core.server.Queue snf1 = server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other");
      assertNotNull(snf1);

      logger.info("I currently have {} ACk Managers", AckManagerProvider.getSize());

      server1.addAddressInfo(new AddressInfo(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST));
      org.apache.activemq.artemis.core.server.Queue queueOnServer1 = server1.createQueue(QueueConfiguration.of(QUEUE_NAME).setAddress(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      Wait.assertTrue(() -> server2.locateQueue(QUEUE_NAME) != null);

      Wait.assertEquals(0, snf1::getMessageCount);

      JournalStorageManager journalStorageManager = (JournalStorageManager) server1.getStorageManager();

      server2.stop();

      logger.info("I currently have {} ACk Managers", AckManagerProvider.getSize());

      File countJournalLocation = server1.getConfiguration().getJournalLocation();
      assertTrue(countJournalLocation.exists() && countJournalLocation.isDirectory());

      ConnectionFactory server1CF = CFUtil.createConnectionFactory(protocol, sendURI);
      ConnectionFactory server2CF = CFUtil.createConnectionFactory(protocol, consumerURI);

      String bodyBuffer = "*".repeat(messageSize);

      int NUMBER_OF_MESSAGES = 200;

      if (pageQueue) {
         queueOnServer1.getPagingStore().startPaging();
      }

      if (pageSNF) {
         snf1.getPagingStore().startPaging();
         Wait.assertTrue(() -> snf1.getPagingStore().isPaging(), 5000, 100);
      }

      try (Connection sendConnecton = server1CF.createConnection()) {
         Session sendSession = sendConnecton.createSession(true, Session.SESSION_TRANSACTED);
         Queue jmsQueue = sendSession.createQueue(QUEUE_NAME);
         MessageProducer producer = sendSession.createProducer(jmsQueue);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = sendSession.createTextMessage(bodyBuffer);
            message.setIntProperty("i", i);
            message.setStringProperty("inPage", "this is paged");
            producer.send(message);
         }
         sendSession.commit();
      }

      if (pageQueue) {
         assertTrue(queueOnServer1.getPagingStore().isPaging());
      } else {
         assertFalse(queueOnServer1.getPagingStore().isPaging());
      }

      if (pageSNF) {
         assertTrue(snf1.getPagingStore().isPaging());
      } else {
         assertFalse(snf1.getPagingStore().isPaging());
      }

      if (pageSNF && pageQueue) {
         journalStorageManager.getMessageJournal().scheduleCompactAndBlock(60_000); // to remove previous sent records during the startup
         // verifying if everything is actually paged, nothing should be routed on the journal
         Map<Integer, AtomicInteger> counters = countJournal(server1.getConfiguration());
         assertEquals(0, getCounter(JournalRecordIds.ADD_REF, counters), "There are routed messages on the journal");
         assertEquals(0, getCounter(JournalRecordIds.ADD_MESSAGE, counters), "There are routed messages on the journal");
         assertEquals(0, getCounter(JournalRecordIds.ADD_MESSAGE_PROTOCOL, counters), "There are routed messages on the journal");
      }

      server2.start();

      assertEquals(2, AckManagerProvider.getSize());

      if (pageTarget) {
         org.apache.activemq.artemis.core.server.Queue queue2 = server2.locateQueue(QUEUE_NAME);
         assertNotNull(queue2);
         queue2.getPagingStore().startPaging();
      }

      org.apache.activemq.artemis.core.server.Queue snf2 = server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other");
      assertNotNull(snf2);

      Wait.assertEquals(0, snf1::getMessageCount);
      Wait.assertEquals(0, snf2::getMessageCount);

      org.apache.activemq.artemis.core.server.Queue queueOnServer2 = server2.locateQueue(QUEUE_NAME);
      Wait.assertEquals((long) NUMBER_OF_MESSAGES, queueOnServer1::getMessageCount, 5000);
      Wait.assertEquals((long) NUMBER_OF_MESSAGES, queueOnServer2::getMessageCount, 5000);

      assertEquals(0, queueOnServer1.getConsumerCount());
      assertEquals(0, queueOnServer1.getDeliveringCount());

      try (Connection connection = server2CF.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue jmsQueue = session.createQueue(QUEUE_NAME);
         MessageConsumer consumer = session.createConsumer(jmsQueue);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals(i, message.getIntProperty("i"));
            System.out.println("Message " + message.getIntProperty("i"));
         }
         session.commit();
      }

      assertEquals(2, AckManagerProvider.getSize());

      Wait.assertEquals(0, snf2::getMessageCount, 15_000);
      Wait.assertEquals(0, queueOnServer2::getMessageCount, 15_000);
      Wait.assertEquals(0, queueOnServer1::getMessageCount, 15_000);
      Wait.assertEquals(0, queueOnServer1::getMessageCount, 15_000);

      Wait.assertEquals(0, () -> server1.getConfiguration().getLargeMessagesLocation().listFiles().length);
      Wait.assertEquals(0, () -> server2.getConfiguration().getLargeMessagesLocation().listFiles().length);

   }

   private int getCounter(byte typeRecord, Map<Integer, AtomicInteger> values) {
      AtomicInteger value = values.get((int) typeRecord);
      if (value == null) {
         return 0;
      } else {
         return value.get();
      }
   }

}
