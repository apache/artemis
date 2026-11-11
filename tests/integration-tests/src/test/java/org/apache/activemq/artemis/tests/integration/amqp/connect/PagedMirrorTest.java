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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PagedMirrorTest extends ActiveMQTestBase {

   ActiveMQServer server1;

   ActiveMQServer server2;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server1 = createServer(true, createDefaultConfig(0, true), 1024, 10 * 1024, -1, -1);
      server1.getConfiguration().getAcceptorConfigurations().clear();
      server1.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:61616");
      AMQPBrokerConnectConfiguration brokerConnectConfiguration = new AMQPBrokerConnectConfiguration("other", "tcp://localhost:61617").setReconnectAttempts(-1).setRetryInterval(1000);
      brokerConnectConfiguration.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(false));
      server1.getConfiguration().addAMQPConnection(brokerConnectConfiguration);

      server2 = createServer(true, createDefaultConfig(1, true), 1024, 10 * 1024, -1, -1);
      server2.getConfiguration().getAcceptorConfigurations().clear();
      server2.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:61617");
      brokerConnectConfiguration = new AMQPBrokerConnectConfiguration("other", "tcp://localhost:61616").setReconnectAttempts(-1).setRetryInterval(1000);
      brokerConnectConfiguration.addElement(new AMQPMirrorBrokerConnectionElement().setDurable(false));
      server2.getConfiguration().addAMQPConnection(brokerConnectConfiguration);

      server1.start();
      server2.start();
   }

   @Test
   public void testPaged() throws Throwable {
      String sendURI = "tcp://localhost:61616";
      String consumeURI = "tcp://localhost:61616";
      String secondConsumeURI = "tcp://localhost:61617";

      Wait.waitFor(() -> server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other") != null);
      Wait.waitFor(() -> server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other") != null);

      org.apache.activemq.artemis.core.server.Queue snf1 = server2.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other");
      assertNotNull(snf1);

      org.apache.activemq.artemis.core.server.Queue snf2 = server1.locateQueue("$ACTIVEMQ_ARTEMIS_MIRROR_other");
      assertNotNull(snf2);

      File countJournalLocation = server2.getConfiguration().getJournalLocation();
      assertTrue(countJournalLocation.exists() && countJournalLocation.isDirectory());
      String protocol = "amqp";

      ConnectionFactory sendCF = CFUtil.createConnectionFactory(protocol, sendURI);
      ConnectionFactory consumeCF = CFUtil.createConnectionFactory(protocol, consumeURI);
      ConnectionFactory secondConsumeCF = CFUtil.createConnectionFactory(protocol, secondConsumeURI);

      String bodyBuffer = "*".repeat(1024);

      int NUMBER_OF_MESSAGES = 200;
      int ACK_I = 77;

      try (Connection sendConnecton = sendCF.createConnection()) {
         Session sendSession = sendConnecton.createSession(true, Session.SESSION_TRANSACTED);
         Queue jmsQueue = sendSession.createQueue("someQueue");
         MessageProducer producer = sendSession.createProducer(jmsQueue);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = sendSession.createTextMessage(bodyBuffer);
            message.setIntProperty("i", i);
            producer.send(message);
         }
         sendSession.commit();
      }

      Wait.assertEquals(0, snf1::getMessageCount);
      Wait.assertEquals(0, snf2::getMessageCount);

      try (Connection consumeConnection = consumeCF.createConnection()) {
         Session consumeSession = consumeConnection.createSession(false, 101); // individual ack
         Queue jmsQueue = consumeSession.createQueue("someQueue");
         MessageConsumer consumer = consumeSession.createConsumer(jmsQueue);
         consumeConnection.start();
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(6000);
            if (message.getIntProperty("i") == ACK_I) {
               message.acknowledge();
            }
         }
         assertNull(consumer.receiveNoWait());
      }
      Wait.assertEquals(0, snf1::getMessageCount);
      Wait.assertEquals(0, snf2::getMessageCount);

      org.apache.activemq.artemis.core.server.Queue queueServer1 = server1.locateQueue("someQueue");
      org.apache.activemq.artemis.core.server.Queue queueServer2 = server1.locateQueue("someQueue");
      Wait.assertEquals(NUMBER_OF_MESSAGES - 1, queueServer2::getMessageCount, 5000);
      Wait.assertEquals(NUMBER_OF_MESSAGES - 1, queueServer1::getMessageCount, 5000);
      Wait.assertEquals(1, () -> acksCount(countJournalLocation), 5000, 1000);

      Set<Integer> receivedIDs = new HashSet<>();

      try (Connection consumeConnection = secondConsumeCF.createConnection()) {
         Session consumeSession = consumeConnection.createSession(true, Session.SESSION_TRANSACTED);
         Queue jmsQueue = consumeSession.createQueue("someQueue");
         MessageConsumer consumer = consumeSession.createConsumer(jmsQueue);
         consumeConnection.start();

         for (int i = 0; i < NUMBER_OF_MESSAGES - 1; i++) {
            TextMessage message = (TextMessage) consumer.receive(6000);
            assertNotNull(message);
            assertNotEquals(ACK_I, message.getIntProperty("i"));
            receivedIDs.add(message.getIntProperty("i"));
         }
         assertNull(consumer.receiveNoWait());


         assertEquals(NUMBER_OF_MESSAGES - 1, receivedIDs.size());

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            if (i != ACK_I) {
               assertTrue(receivedIDs.contains(i));
            }
         }
      }
   }

   private int acksCount(File countJournalLocation) throws Exception {
      Map<Integer, AtomicInteger> countJournal = countJournal(countJournalLocation, 10485760, 2, 2);
      AtomicInteger acksCount = countJournal.get((int)JournalRecordIds.ACKNOWLEDGE_CURSOR);
      return acksCount != null ? acksCount.get() : 0;
   }

}
