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
package org.apache.activemq.artemis.tests.integration.crossprotocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class OpenwireAmqpResenderTest extends ActiveMQTestBase {

   private static final String OPENWIRE_URL = "tcp://localhost:61616";
   private static final String AMQP_URL = "amqp://localhost:61616";
   private static final String QUEUE_ZERO_NAME = "queue.zero";
   private static final String QUEUE_ONE_NAME = "queue.one";

   private ActiveMQServer server;
   private ActiveMQConnectionFactory factory;
   private JmsConnectionFactory qpidFactory;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, true);

      factory = new ActiveMQConnectionFactory(OPENWIRE_URL);
      qpidFactory = new JmsConnectionFactory(AMQP_URL);

      Configuration serverConfig = server.getConfiguration();
      serverConfig.getAddressSettings().put("#", new AddressSettings().setAutoCreateQueues(true)
            .setAutoCreateAddresses(true).setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")));
      serverConfig.setSecurityEnabled(false);
      server.start();

      server.createQueue(QueueConfiguration.of(QUEUE_ZERO_NAME).setRoutingType(RoutingType.ANYCAST).setDurable(false));

      server.createQueue(QueueConfiguration.of(QUEUE_ONE_NAME).setRoutingType(RoutingType.ANYCAST).setDurable(false));
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      if (server != null) {
         server.stop();
         server = null;
      }
   }

   @Test
   @Timeout(5)
   public void internalOpenwireBinaryPropShouldBeConvertedAsByteArrays() throws Exception {
      openwireSender(factory);
      amqpResender(qpidFactory);
      openwireReceiver(factory);
   }

   private void openwireSender(ConnectionFactory cf) throws Exception {
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queueZero = session.createQueue(QUEUE_ZERO_NAME);

      MessageProducer producer = session.createProducer(queueZero);
      Message testMessage = session.createTextMessage("test");
      producer.send(testMessage);

      connection.close();
   }

   private void amqpResender(ConnectionFactory cf) throws Exception {
      Connection connection = cf.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queueZero = session.createQueue(QUEUE_ZERO_NAME);
      Queue queueOne = session.createQueue(QUEUE_ONE_NAME);

      MessageConsumer consumer = session.createConsumer(queueZero);
      connection.start();
      Message message = consumer.receive();
      assertNotNull(message);

      MessageProducer producer = session.createProducer(queueOne);
      producer.send(message);

      connection.close();
   }

   private void openwireReceiver(ConnectionFactory cf) throws Exception {
      Connection connection = cf.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queueOne = session.createQueue(QUEUE_ONE_NAME);

      MessageConsumer consumer = session.createConsumer(queueOne);
      connection.start();
      Message receivedMessage = consumer.receive();
      assertNotNull(receivedMessage);

      connection.close();
   }

}
