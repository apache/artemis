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
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPReceiverBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPSenderBrokerConnectionElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AMQPRoutingTypeMismatchTest extends AmqpTestSupport {

   private static final int AMQP_PORT_2 = 5673;

   @Test
   public void testReceiverTargetingQueueWithRoutingTypeNotMatchingOriginalMessageTarget() throws Exception {
      final String DLQ_NAME = "topic.DLQ";

      final ActiveMQServer server = createServer(AMQP_PORT, false);
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of(DLQ_NAME)).setRedeliveryDelay(0).setMaxDeliveryAttempts(1));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(DLQ_NAME).setRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(getName()).addRoutingType(RoutingType.MULTICAST));
      server.setIdentity("Server1");
      server.start();

      final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

      try (Connection connection = factory.createConnection()) {
         connection.setClientID("myID");

         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = session.createDurableConsumer(session.createTopic(getName()), "myTopic");
         MessageProducer producer = session.createProducer(session.createTopic(getName()));

         producer.send(session.createTextMessage("hello"));
         session.commit();
         connection.start();
         assertNotNull(consumer.receive(5_000));
         session.rollback();
         assertNull(consumer.receiveNoWait());
      }

      Wait.assertTrue(() -> server.queueQuery(SimpleString.of(DLQ_NAME)).isExists(), 5_000, 100);
      final Queue dlq = server.locateQueue(DLQ_NAME);
      Wait.assertEquals(1L, dlq::getMessageCount, 5_000, 100);

      final AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addReceiver((AMQPReceiverBrokerConnectionElement) new AMQPReceiverBrokerConnectionElement().setMatchAddress(DLQ_NAME));

      final ActiveMQServer server2 = createServer(AMQP_PORT_2, false);
      server2.getConfiguration().getAddressSettings().clear();
      server2.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of(DLQ_NAME)).setRedeliveryDelay(0).setMaxDeliveryAttempts(1));
      server2.getConfiguration().addQueueConfiguration(QueueConfiguration.of(DLQ_NAME).setRoutingType(RoutingType.ANYCAST));
      server2.setIdentity("Server2");
      server2.getConfiguration().addAMQPConnection(amqpConnection);
      server2.start();

      Wait.assertTrue(() -> server2.queueQuery(SimpleString.of(DLQ_NAME)).isExists(), 5_000, 100);
      final Queue dlqServer2 = server2.locateQueue(DLQ_NAME);
      Wait.assertEquals(1L, dlqServer2::getMessageCount, 5_000, 100);
   }

   @Test
   public void testSenderTargetingQueueWithRoutingTypeNotMatchingOriginalMessageTarget() throws Exception {
      final String DLQ_NAME = "topic.DLQ";

      final ActiveMQServer server2 = createServer(AMQP_PORT_2, false);
      server2.getConfiguration().getAddressSettings().clear();
      server2.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of(DLQ_NAME)).setRedeliveryDelay(0).setMaxDeliveryAttempts(1));
      server2.getConfiguration().addQueueConfiguration(QueueConfiguration.of(DLQ_NAME).setRoutingType(RoutingType.ANYCAST));
      server2.setIdentity("Server2");
      server2.start();

      final AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT_2);
      amqpConnection.addSender((AMQPSenderBrokerConnectionElement) new AMQPSenderBrokerConnectionElement().setMatchAddress(DLQ_NAME));

      final ActiveMQServer server = createServer(AMQP_PORT, false);
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().addAddressSetting("#", new AddressSettings().setDeadLetterAddress(SimpleString.of(DLQ_NAME)).setRedeliveryDelay(0).setMaxDeliveryAttempts(1));
      server.getConfiguration().addQueueConfiguration(QueueConfiguration.of(DLQ_NAME).setRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(getName()).addRoutingType(RoutingType.MULTICAST));
      server.getConfiguration().addAMQPConnection(amqpConnection);
      server.setIdentity("Server1");
      server.start();

      final ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);

      try (Connection connection = factory.createConnection()) {
         connection.setClientID("myID");

         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = session.createDurableConsumer(session.createTopic(getName()), "myTopic");
         MessageProducer producer = session.createProducer(session.createTopic(getName()));

         producer.send(session.createTextMessage("hello"));
         session.commit();
         connection.start();
         assertNotNull(consumer.receive(5_000));
         session.rollback();
         assertNull(consumer.receiveNoWait());
      }

      Wait.assertTrue(() -> server.queueQuery(SimpleString.of(DLQ_NAME)).isExists(), 5_000, 100);
      Wait.assertTrue(() -> server2.queueQuery(SimpleString.of(DLQ_NAME)).isExists(), 5_000, 100);

      final Queue dlq = server.locateQueue(DLQ_NAME);
      Wait.assertEquals(1, dlq::getConsumerCount, 5_000, 100); // SENDER listening on local DLQ for forwards
      Wait.assertEquals(0L, dlq::getMessageCount, 5_000, 100);
      Wait.assertEquals(1L, dlq::getMessagesAcknowledged, 5_000, 100);

      final Queue dlqServer2 = server2.locateQueue(DLQ_NAME);
      Wait.assertEquals(1L, dlqServer2::getMessageCount, 5_000, 100);
   }
}
