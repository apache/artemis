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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpPurgeOnNoConsumersTest extends AmqpClientTestSupport {

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,OPENWIRE,CORE";
   }

   @Test
   @Timeout(60)
   public void testQueueReceiverReadMessage() throws Exception {
      AmqpConnection connection = null;
      String queue = "purgeQueue";
      SimpleString ssQueue = SimpleString.of(queue);

      server.addAddressInfo(new AddressInfo(ssQueue, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(ssQueue).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1).setPurgeOnNoConsumers(true).setAutoCreateAddress(false));

      AmqpClient client = createAmqpClient();
      connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final AmqpReceiver receiver = session.createReceiver(queue);

      QueueImpl queueView = (QueueImpl)getProxyToQueue(queue);
      assertEquals(0L, queueView.getPageSubscription().getPagingStore().getAddressSize());
      assertEquals(0, queueView.getMessageCount());

      sendMessages(queue, 5, null, true);

      Wait.assertEquals(5, queueView::getMessageCount);

      receiver.flow(5);

      for (int i = 0; i < 4; i++) {
         try {
            AmqpMessage receive = receiver.receive(5, TimeUnit.SECONDS);
            receive.accept();
            assertNotNull(receive);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
      try {
         receiver.close();
      } catch (IOException e) {
         e.printStackTrace();
      }

      Wait.assertEquals(0, queueView::getMessageCount);
      assertEquals(0L, queueView.getPageSubscription().getPagingStore().getAddressSize());

      connection.close();

      server.stop();

      server.start();

      queueView = (QueueImpl)getProxyToQueue(queue);

      assertEquals(0, queueView.getMessageCount());
      assertEquals(0L, queueView.getPageSubscription().getPagingStore().getAddressSize());
   }


   // I'm adding the core test here to compare semantics between AMQP and core on this test.
   @Test
   @Timeout(60)
   public void testPurgeQueueCoreRollback() throws Exception {
      String queue = "purgeQueue";
      SimpleString ssQueue = SimpleString.of(queue);
      server.addAddressInfo(new AddressInfo(ssQueue, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(ssQueue).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1).setPurgeOnNoConsumers(true).setAutoCreateAddress(false));

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:5672");
      Connection connection = cf.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer producer = session.createProducer(session.createQueue("purgeQueue"));

      javax.jms.Queue jmsQueue = session.createQueue(queue);
      MessageConsumer consumer = session.createConsumer(jmsQueue);

      for (int i = 0; i < 10; i++) {
         Message message = session.createTextMessage("hello " + i);
         producer.send(message);
      }
      session.commit();

      QueueImpl queueView = (QueueImpl)getProxyToQueue(queue);

      Wait.assertEquals(10, queueView::getMessageCount);

      connection.start();


      for (int i = 0; i < 10; i++) {
         TextMessage txt = (TextMessage)consumer.receive(1000);
         assertNotNull(txt);
         assertEquals("hello " + i, txt.getText());
      }
      consumer.close();
      session.rollback();
      connection.close();

      Wait.assertEquals(0, queueView::getMessageCount);

      server.stop();

      server.start();

      queueView = (QueueImpl)getProxyToQueue(queue);

      assertEquals(0, queueView.getMessageCount());
      assertEquals(0L, queueView.getPageSubscription().getPagingStore().getAddressSize());
   }
}
