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
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource;
import org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorMessageFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MirrorControllerBasicTest extends ActiveMQTestBase {

   ActiveMQServer server;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      Configuration configuration = createDefaultNettyConfig();
      server = addServer(ActiveMQServers.newActiveMQServer(configuration, true));
      // start the server
      server.start();
   }


   @Test
   public void testSend() throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");
      Connection connection = factory.createConnection();
      Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue("myQueue");
      MessageConsumer consumer = session.createConsumer(queue);

      connection.start();

      MessageProducer producer = session.createProducer(queue);
      for (int i = 0; i < 10; i++) {
         producer.send(session.createTextMessage("hello"));
      }

      for (int i = 0; i < 10; i++) {
         assertNotNull(consumer.receive(1000));
      }

      connection.close();
   }

   /**
    * this test will take the Message generated from mirror controller and send it through PostOffice to validate the
    * format of the message and its delivery
    */
   @Test
   public void testDirectSend() throws Exception {
      server.addAddressInfo(new AddressInfo("test").addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of("test").setAddress("test").setRoutingType(RoutingType.ANYCAST));

      Message message = AMQPMirrorMessageFactory.createMessage("test", SimpleString.of("ad1"), SimpleString.of("qu1"), "test", "someUID", "body-test", AckReason.KILLED);
      AMQPMirrorControllerSource.routeMirrorCommand(server, message);

      AmqpClient client = new AmqpClient(new URI("tcp://localhost:61616"), null, null);
      AmqpConnection connection = client.connect();
      AmqpSession session = connection.createSession();
      AmqpReceiver receiver = session.createReceiver("test");
      receiver.flow(1);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);

      AmqpValue value = (AmqpValue)amqpMessage.getWrappedMessage().getBody();
      assertEquals("body-test", (String)value.getValue());
      assertEquals("ad1", amqpMessage.getMessageAnnotation(AMQPMirrorControllerSource.ADDRESS.toString()));
      assertEquals("qu1", amqpMessage.getMessageAnnotation(AMQPMirrorControllerSource.QUEUE.toString()));
      assertEquals("someUID", amqpMessage.getMessageAnnotation(AMQPMirrorControllerSource.BROKER_ID.toString()));
      assertEquals("test", amqpMessage.getMessageAnnotation(AMQPMirrorControllerSource.EVENT_TYPE.toString()));
      Number ackReason = (Number)amqpMessage.getMessageAnnotation("x-opt-amq-mr-ack-reason");
      assertEquals(AckReason.KILLED.getVal(), ackReason.byteValue());
      assertEquals(AckReason.KILLED, AckReason.fromValue(ackReason.byteValue()));

      connection.close();


   }

}
