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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Decimal128;
import org.apache.qpid.proton.amqp.Decimal32;
import org.apache.qpid.proton.amqp.Decimal64;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public class JMXManagementTest extends JMSClientTestSupport {

   @Test
   public void testListDeliveringMessages() throws Exception {
      SimpleString queue = SimpleString.of(getQueueName());

      Connection connection1 = createConnection();
      Connection connection2 = createConnection();
      Session prodSession = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Session consSession = connection2.createSession(true, Session.SESSION_TRANSACTED);

      javax.jms.Queue jmsQueue = prodSession.createQueue(queue.toString());

      QueueControl queueControl = createManagementControl(queue, queue);

      MessageProducer producer = prodSession.createProducer(jmsQueue);
      final int num = 20;

      for (int i = 0; i < num; i++) {
         TextMessage message = prodSession.createTextMessage("hello" + i);
         producer.send(message);
      }

      connection2.start();
      MessageConsumer consumer = consSession.createConsumer(jmsQueue);

      for (int i = 0; i < num; i++) {
         TextMessage msgRec = (TextMessage) consumer.receive(5000);
         assertNotNull(msgRec);
         assertEquals(msgRec.getText(), "hello" + i);
      }

      //before commit
      Wait.assertEquals(num, () -> queueControl.getDeliveringCount());

      Map<String, Map<String, Object>[]> result = null;
      Map<String, Object>[] msgMaps = null;
      // we might need some retry, and Wait.assert won't be as efficient on this case
      for (int i = 0; i < 10; i++) {
         result = queueControl.listDeliveringMessages();
         assertEquals(1, result.size());

         msgMaps = result.entrySet().iterator().next().getValue();
         if (msgMaps.length == num) {
            break;
         } else {
            Thread.sleep(100);
         }
      }

      assertEquals(num, msgMaps.length);

      consSession.commit();
      result = queueControl.listDeliveringMessages();

      assertEquals(0, result.size());

      consSession.close();
      prodSession.close();

      connection1.close();
      connection2.close();
   }

   @Test
   public void testGetFirstMessage() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         session.begin();
         AmqpMessage message = new AmqpMessage();
         message.setApplicationProperty("TEST_BINARY", new Binary("TEST".getBytes()));

         final String oneK = new String(new char[1024]).replace("\0", "$");
         message.setApplicationProperty("TEST_BIG_BINARY", new Binary(oneK.getBytes(StandardCharsets.UTF_8)));
         message.setApplicationProperty("TEST_STRING", oneK);
         message.setText("NOT_VISIBLE");
         sender.send(message);
         session.commit();

         Queue serverQueue = server.locateQueue(getQueueName());
         Wait.assertEquals(1L, serverQueue::getMessageCount, 100, 5000);

         SimpleString queue = SimpleString.of(getQueueName());
         QueueControl queueControl = createManagementControl(queue, queue);
         String firstMessageAsJSON = queueControl.getFirstMessageAsJSON();
         assertNotNull(firstMessageAsJSON);

         // Json is still bulky!
         assertTrue(firstMessageAsJSON.length() < 1500);
         assertFalse(firstMessageAsJSON.contains("NOT_VISIBLE"));

         // composite data limits
         Map<String, Object>[] result = queueControl.listMessages("");
         assertEquals(1, result.length);

         final Map<String, Object> msgMap = result[0];
         assertTrue(msgMap.get("TEST_STRING").toString().length() < 512);

      } finally {
         connection.close();
      }
   }

   @Test
   public void testGetFirstMessageWithAMQPTypes() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         UUID uuid = UUID.randomUUID();
         Character character = 'C';
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         session.begin();
         AmqpMessage message = new AmqpMessage();
         message.setApplicationProperty("TEST_UUID", uuid);
         message.setApplicationProperty("TEST_CHAR", character);
         message.setApplicationProperty("TEST_DECIMAL_32", new Decimal32(BigDecimal.ONE));
         message.setApplicationProperty("TEST_DECIMAL_64", new Decimal64(BigDecimal.ONE));
         message.setApplicationProperty("TEST_DECIMAL_128", new Decimal128(BigDecimal.ONE));

         sender.send(message);
         session.commit();

         SimpleString queue = SimpleString.of(getQueueName());

         Queue serverQueue = server.locateQueue(getQueueName());
         Wait.assertEquals(1L, serverQueue::getMessageCount, 100, 5000);

         QueueControl queueControl = createManagementControl(queue, queue);
         String firstMessageAsJSON = queueControl.getFirstMessageAsJSON();
         assertNotNull(firstMessageAsJSON);

         JsonObject firstMessageObject = JsonUtil.readJsonArray(firstMessageAsJSON).getJsonObject(0);

         assertEquals(uuid.toString(), firstMessageObject.getString("TEST_UUID"));
         assertEquals(character.toString(), firstMessageObject.getString("TEST_CHAR"));
         assertNotNull(firstMessageObject.getJsonNumber("TEST_DECIMAL_32"));
         assertNotNull(firstMessageObject.getJsonNumber("TEST_DECIMAL_64"));
         assertNotNull(firstMessageObject.getJsonNumber("TEST_DECIMAL_128"));
      } finally {
         connection.close();
      }
   }

   @Test
   public void testAddressSizeOnDelete() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         session.begin();
         AmqpMessage message = new AmqpMessage();
         message.setApplicationProperty("TEST_STRING", "TEST");
         message.setTimeToLive(100);
         message.setText("TEST");
         // send 2 so we can verify getFirstMessage and List
         sender.send(message);
         sender.send(message);
         session.commit();

         PagingStore targetPagingStore = server.getPagingManager().getPageStore(SimpleString.of(getQueueName()));
         assertNotNull(targetPagingStore);

         assertTrue(targetPagingStore.getAddressSize() > 0);

         SimpleString queue = SimpleString.of(getQueueName());
         QueueControl queueControl = createManagementControl(queue, queue);

         Wait.assertEquals(2, queueControl::getMessageCount);

         JsonArray array = JsonUtil.readJsonArray(queueControl.getFirstMessageAsJSON());
         JsonObject object = (JsonObject) array.get(0);
         queueControl.removeMessage(object.getJsonNumber("messageID").longValue());

         Wait.assertEquals(1, queueControl::getMessageCount);

         Map<String, Object>[] messages = queueControl.listMessages("");
         assertEquals(1, messages.length);
         queueControl.removeMessage((Long) messages[0].get("messageID"));

         assertEquals(0, queueControl.getMessageCount());
         Wait.assertEquals(0L, targetPagingStore::getAddressSize);

      } finally {
         connection.close();
      }
   }

   @Test
   public void testCountMessagesWithOriginalQueueFilter() throws Exception {
      final String queueA = getQueueName();
      final String queueB = getQueueName() + "_B";
      final String queueC = getQueueName() + "_C";
      final QueueControl queueAControl = createManagementControl(queueA);
      final QueueControl queueBControl = createManagementControl(queueB);
      final QueueControl queueCControl = createManagementControl(queueC);
      final int MESSAGE_COUNT = 20;

      server.createQueue(QueueConfiguration.of(queueB).setAddress(queueB).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queueC).setAddress(queueC).setRoutingType(RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpSender senderA = session.createSender(queueA);
         AmqpSender senderB = session.createSender(queueB);

         for (int i = 0; i < MESSAGE_COUNT; i++) {
            final AmqpMessage message = new AmqpMessage();
            message.setText("Message number: " + i);

            senderA.send(message);
            senderB.send(message);
         }

         Wait.assertEquals((long)MESSAGE_COUNT, () -> queueAControl.countMessages(), 5000, 100);
         Wait.assertEquals((long)MESSAGE_COUNT, () -> queueBControl.countMessages(), 5000, 100);
         assertEquals(0, queueCControl.countMessages());

         queueAControl.moveMessages(null, queueC);
         queueBControl.moveMessages(null, queueC);

         assertEquals(0, queueAControl.countMessages());
         assertEquals(0, queueBControl.countMessages());
         Wait.assertEquals(MESSAGE_COUNT * 2, () -> queueCControl.countMessages());

         final String originalGroup = queueCControl.countMessages(null, "_AMQ_ORIG_QUEUE");
         final String[] originalSplit = originalGroup.split(",");

         assertEquals(2, originalSplit.length);
         assertTrue(originalSplit[0].contains("" + MESSAGE_COUNT));
         assertTrue(originalSplit[1].contains("" + MESSAGE_COUNT));

         assertEquals("{\"" + queueA + "\":" + MESSAGE_COUNT + "}",
                      queueCControl.countMessages("_AMQ_ORIG_QUEUE = '" + queueA + "'", "_AMQ_ORIG_QUEUE"));
         assertEquals("{\"" + queueB + "\":" + MESSAGE_COUNT + "}",
                      queueCControl.countMessages("_AMQ_ORIG_QUEUE = '" + queueB + "'", "_AMQ_ORIG_QUEUE"));
      } finally {
         connection.close();
      }
   }

   protected QueueControl createManagementControl(final String queue) throws Exception {
      return createManagementControl(SimpleString.of(queue), SimpleString.of(queue));
   }

   protected QueueControl createManagementControl(final SimpleString address,
                                                  final SimpleString queue) throws Exception {
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, queue, RoutingType.ANYCAST, this.mBeanServer);

      return queueControl;
   }
}
