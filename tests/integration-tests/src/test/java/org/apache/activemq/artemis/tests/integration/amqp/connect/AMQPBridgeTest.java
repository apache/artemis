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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

public class AMQPBridgeTest extends AmqpClientTestSupport {

   protected static final int AMQP_PORT_2 = 5673;

   ActiveMQServer server_2;

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, false);
   }

   @Test
   public void testsSimpleConnect() throws Exception {
      server.start();
      server_2 = createServer(AMQP_PORT_2, false);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      server_2.getConfiguration().addAMQPConnection(amqpConnection);

      server_2.start();
   }

   @Test
   public void testSimpleTransferPush() throws Exception {
      internalTransferPush("TEST", false, false);
   }

   @Test
   public void testSimpleTransferPushRestartBC() throws Exception {
      internalTransferPush("TEST", false, true);
   }

   @Test
   public void testSimpleTransferPushDeferredCreation() throws Exception {
      internalTransferPush("TEST", true, false);
   }

   @Test
   public void testSimpleTransferPushDeferredCreationRestartBC() throws Exception {
      internalTransferPush("TEST", true, true);
   }

   public void internalTransferPush(String queueName, boolean deferCreation, boolean restartBC) throws Exception {
      server.setIdentity("targetServer");
      server.start();
      server.addAddressInfo(new AddressInfo(SimpleString.of(queueName), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));

      server_2 = createServer(AMQP_PORT_2, false);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT);
      amqpConnection.addElement(new AMQPBrokerConnectionElement().setMatchAddress(queueName).setType(AMQPBrokerConnectionAddressType.SENDER));
      server_2.getConfiguration().addAMQPConnection(amqpConnection);
      if (!deferCreation) {
         server_2.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(queueName).addRoutingType(RoutingType.ANYCAST));
         server_2.getConfiguration().addQueueConfiguration(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }
      server_2.setIdentity("serverWithBridge");

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      if (deferCreation) {
         server_2.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST));
         server_2.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }

      if (restartBC) {
         server_2.stopBrokerConnection("test");
         Thread.sleep(1000);
         server_2.startBrokerConnection("test");
      }

      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue(queueName));
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      String largeMessageBody = null;
      for (int i = 0; i < 30; i++) {
         if (i == 0) {
            largeMessageBody = "*******************************************************************************************************************************".repeat(10024);
            TextMessage message = session.createTextMessage(largeMessageBody);
            producer.send(message);
         } else {
            producer.send(session.createMessage());
         }
      }

      Queue testQueueOnServer2 = server_2.locateQueue(queueName);
      assertNotNull(testQueueOnServer2);
      Wait.assertEquals(0, testQueueOnServer2::getMessageCount);

      ConnectionFactory factory2 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      Connection connection2 = factory2.createConnection();
      Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      connection2.start();

      MessageConsumer consumer = session2.createConsumer(session2.createQueue(queueName));
      for (int i = 0; i < 30; i++) {
         Message message = consumer.receive(5000);
         if (message instanceof TextMessage) {
            if (message instanceof TextMessage textMessage) {
               assertEquals(largeMessageBody, textMessage.getText());
            } else {
               System.out.println("i = " + i);
            }
         }
      }
      assertNull(consumer.receiveNoWait());
   }

   @Test
   public void testSimpleTransferPull() throws Exception {
      internaltestSimpleTransferPull(false);
   }

   @Test
   public void testSimpleTransferPullSecurity() throws Exception {
      internaltestSimpleTransferPull(true);
   }

   public void internaltestSimpleTransferPull(boolean security) throws Exception {
      server.setIdentity("targetServer");

      if (security) {
         enableSecurity(server, "#");
      }

      server.start();

      server.addAddressInfo(new AddressInfo(SimpleString.of("TEST"), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of("TEST").setRoutingType(RoutingType.ANYCAST));

      server_2 = createServer(AMQP_PORT_2, false);

      AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration("test", "tcp://localhost:" + AMQP_PORT).setRetryInterval(10);

      if (security) {
         // we first do it with a wrong password. retries in place should be in place until we make it right
         amqpConnection.setUser(fullUser).setPassword("wrongPassword");
      }

      amqpConnection.addElement(new AMQPBrokerConnectionElement().setMatchAddress("TEST").setType(AMQPBrokerConnectionAddressType.RECEIVER));
      server_2.getConfiguration().addAMQPConnection(amqpConnection);
      server_2.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName("TEST").addRoutingType(RoutingType.ANYCAST));
      server_2.getConfiguration().addQueueConfiguration(QueueConfiguration.of("TEST").setRoutingType(RoutingType.ANYCAST));
      server_2.setIdentity("serverWithBridge");

      server_2.start();
      Wait.assertTrue(server_2::isStarted);

      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT);
      Connection connection = factory.createConnection(fullUser, fullPass);
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = session.createProducer(session.createQueue("TEST"));
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      String largeMessageBody = null;
      for (int i = 0; i < 30; i++) {
         if (i == 0) {
            largeMessageBody = "*******************************************************************************************************************************".repeat(10024);
            TextMessage message = session.createTextMessage(largeMessageBody);
            producer.send(message);
         } else {
            producer.send(session.createMessage());
         }
      }

      ConnectionFactory factory2 = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:" + AMQP_PORT_2);
      Connection connection2 = factory2.createConnection();
      Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      connection2.start();

      MessageConsumer consumer = session2.createConsumer(session2.createQueue("TEST"));

      if (security) {
         Thread.sleep(500); // on this case we need to wait some time to make sure retries are kicking in.
         // since the password is wrong, this should return null.
         assertNull(consumer.receiveNoWait());
         // we are fixing the password, hoping the connection will fix itself.
         amqpConnection.setUser(fullUser).setPassword(fullPass);
      }

      for (int i = 0; i < 30; i++) {
         Message message = consumer.receive(5000);
         if (message instanceof TextMessage) {
            if (message instanceof TextMessage textMessage) {
               assertEquals(largeMessageBody, textMessage.getText());
            } else {
               System.out.println("i = " + i);
            }
         }
      }
      assertNull(consumer.receiveNoWait());
   }

}
