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
package org.apache.activemq.artemis.tests.db.dbstorage.integration;

import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class CrossProtocolIntegrationTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int MESSAGE_SIZE = 1024;
   private static final int LARGE_MESSAGE_SIZE = 300 * 1024;
   private static final int TOTAL_MESSAGES = 10;

   @BeforeEach
   @Override
   public void setupTest() throws Exception {
      super.setupTest();
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("DLQ").addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of("DLQ").setRoutingType(RoutingType.ANYCAST)));
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("ExpiryQueue").addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of("ExpiryQueue").setRoutingType(RoutingType.ANYCAST)));
   }

   @TestTemplate
   public void testAMQPCore() throws Exception {
      testCrossProtocol("AMQP", "CORE");
   }

   @TestTemplate
   public void testCoreAMQP() throws Exception {
      testCrossProtocol("CORE", "AMQP");
   }

   @TestTemplate
   public void testCoreOpenWire() throws Exception {
      testCrossProtocol("CORE", "OPENWIRE");
   }

   @TestTemplate
   public void testOpenWireCore() throws Exception {
      testCrossProtocol("OPENWIRE", "CORE");
   }

   private void testCrossProtocol(String sendProtocol, String receiveProtocol) throws Exception {
      String queueName = "crossProtocol" + RandomUtil.randomUUIDString();

      String regularBody = "r".repeat(MESSAGE_SIZE);
      String largeBody = "L".repeat(LARGE_MESSAGE_SIZE);

      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName(queueName).addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST)));

      ActiveMQServer server = createServer(true, configuration);
      server.start();

      ConnectionFactory sendFactory = CFUtil.createConnectionFactory(sendProtocol, "tcp://localhost:61616");

      try (javax.jms.Connection connection = sendFactory.createConnection()) {
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(queueName));
            for (int i = 0; i < TOTAL_MESSAGES; i++) {
               boolean large = i % 2 == 0;
               TextMessage msg = session.createTextMessage(large ? largeBody : regularBody);
               msg.setIntProperty("index", i);
               msg.setBooleanProperty("large", large);
               producer.send(msg);
               logger.debug("Sent {} message {} via {}", large ? "large" : "regular", i, sendProtocol);
            }
         }
      }

      server.stop();
      server.start();

      ConnectionFactory receiveFactory = CFUtil.createConnectionFactory(receiveProtocol, "tcp://localhost:61616");

      try (javax.jms.Connection connection = receiveFactory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
            for (int i = 0; i < TOTAL_MESSAGES; i++) {
               TextMessage received = (TextMessage) consumer.receive(10_000);
               assertNotNull(received, "Missing message at index " + i);
               assertEquals(i, received.getIntProperty("index"));
               boolean large = received.getBooleanProperty("large");
               String expectedBody = large ? largeBody : regularBody;
               assertEquals(expectedBody, received.getText(), "Wrong body for message " + received.getIntProperty("index"));
            }
         }
      }

      checkMessageCounts(0, true);
      server.stop();
   }
}
