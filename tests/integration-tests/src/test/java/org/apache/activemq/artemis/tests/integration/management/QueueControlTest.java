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
package org.apache.activemq.artemis.tests.integration.management;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.management.Notification;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularDataSupport;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.DayCounterInfo;
import org.apache.activemq.artemis.api.core.management.MessageCounterInfo;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.management.impl.QueueControlImpl;
import org.apache.activemq.artemis.core.management.impl.view.ConsumerField;
import org.apache.activemq.artemis.core.messagecounter.impl.MessageCounterManagerImpl;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImplAccessor;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueImplTestAccessor;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.jms.server.management.JMSUtil;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.Base64;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.message.openmbean.CompositeDataConstants.BODY;
import static org.apache.activemq.artemis.core.message.openmbean.CompositeDataConstants.STRING_PROPERTIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(ParameterizedTestExtension.class)
public class QueueControlTest extends ManagementTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String NULL_DATE = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(0));

   private ActiveMQServer server;
   private ClientSession session;
   private ServerLocator locator;
   private final boolean durable;

   @Parameters(name = "durable={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][] {{true}, {false}});
   }


   public QueueControlTest(boolean durable) {
      super();
      this.durable = durable;
   }

   @TestTemplate
   public void testMoveMessagesInPagingMode() throws Exception {
      final int TOTAL_MESSAGES = 100;
      final String DLA = "DLA";
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, false);

      SimpleString queueAddr = SimpleString.of("testQueue");
      session.createQueue(QueueConfiguration.of(queueAddr).setDurable(durable));
      SimpleString dlq = SimpleString.of(DLA);
      session.createQueue(QueueConfiguration.of(dlq));

      // Set up paging on the queue address
      AddressSettings addressSettings = new AddressSettings().setPageSizeBytes(1024 * 1024).setMaxSizeBytes(1).setDeadLetterAddress(dlq);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      sendMessageBatch(TOTAL_MESSAGES, session, queueAddr);

      Queue queue = server.locateQueue(queueAddr);

      // Give time Queue.deliverAsync to deliver messages
      assertTrue(waitForMessages(queue, TOTAL_MESSAGES, 5000));

      PagingStore queuePagingStore = queue.getPagingStore();
      assertTrue(queuePagingStore != null && queuePagingStore.isPaging());

      //invoke moveMessages op
      String queueControlResourceName = ResourceNames.QUEUE + "testQueue";
      Object resource = server.getManagementService().getResource(queueControlResourceName);
      QueueControl queueControl = (QueueControl) resource;
      assertEquals(TOTAL_MESSAGES, queueControl.getMessageCount());

      // move messages to DLQ
      int count = queueControl.moveMessages(5, "", DLA, false, 10);
      assertEquals(10, count);

      //messages shouldn't move on to the same queue
      try {
         queueControl.moveMessages(1000, "", "testQueue", false, 9000);
         fail("messages cannot be moved on to the queue itself");
      } catch (IllegalArgumentException ok) {
         //ok
      }

      // 90 left, moving 7 messages
      count = queueControl.moveMessages(1000, "", DLA, false, 7);
      assertEquals(7, count);

      // 83 left, trying to move 1000
      count = queueControl.moveMessages(100, "", DLA, false, 1000);
      assertEquals(83, count);

      // zero left, try move again
      count = queueControl.moveMessages(100, "", DLA, false, 1000);
      assertEquals(0, count);
   }

   @TestTemplate
   public void testGetPreparedTransactionMessageCount() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      ClientProducer producer = session.createProducer(address);

      for (int i = 0; i < 10; i++) {
         producer.send(session.createMessage(true));
      }

      producer.close();

      ClientSession xaSession = locator.createSessionFactory().createXASession();

      ClientConsumer consumer = xaSession.createConsumer(queue);

      Xid xid = newXID();

      xaSession.start(xid, XAResource.TMNOFLAGS);

      xaSession.start();

      for (int i = 0; i < 10; i++) {
         ClientMessage receive = consumer.receive();
         receive.acknowledge();
      }

      xaSession.end(xid, XAResource.TMSUCCESS);

      xaSession.prepare(xid);

      QueueControl queueControl = createManagementControl(address, queue);

      int count = queueControl.getPreparedTransactionMessageCount();

      assertEquals(10, count);

      consumer.close();

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetPreparedTransactionMessageCountDifferentQueues() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString address2 = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString queue2 = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      session.createQueue(QueueConfiguration.of(queue2).setAddress(address2).setDurable(durable));

      ClientProducer producer = session.createProducer(address);
      ClientProducer producer2 = session.createProducer(address2);

      for (int i = 0; i < 10; i++) {
         producer.send(session.createMessage(true));
         producer2.send(session.createMessage(true));
      }

      producer.close();
      producer2.close();

      ClientSession xaSession = locator.createSessionFactory().createXASession();

      ClientConsumer consumer = xaSession.createConsumer(queue);

      ClientConsumer consumer2 = xaSession.createConsumer(queue2);

      Xid xid = newXID();

      xaSession.start(xid, XAResource.TMNOFLAGS);

      xaSession.start();

      for (int i = 0; i < 10; i++) {
         ClientMessage receive = consumer.receive();
         receive.acknowledge();
         receive = consumer2.receive();
         receive.acknowledge();
      }

      xaSession.end(xid, XAResource.TMSUCCESS);

      xaSession.prepare(xid);

      QueueControl queueControl = createManagementControl(address, queue);

      int count = queueControl.getPreparedTransactionMessageCount();

      assertEquals(10, count);

      consumer.close();

      consumer2.close();

      session.deleteQueue(queue);

      session.deleteQueue(queue2);
   }

   @TestTemplate
   public void testGetPreparedTransactionMessageCountNoTX() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      int count = queueControl.getPreparedTransactionMessageCount();

      assertEquals(0, count);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testAttributes() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString filter = SimpleString.of("color = 'blue'");

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setFilterString(filter).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(queue.toString(), queueControl.getName());
      assertEquals(address.toString(), queueControl.getAddress());
      assertEquals(filter.toString(), queueControl.getFilter());
      assertEquals(durable, queueControl.isDurable());
      assertFalse(queueControl.isTemporary());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testRegisterInternalQueues() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      server.createQueue(QueueConfiguration.of(queue).setDurable(durable).setInternal(true));

      QueueControl queueControl = createManagementControl(queue, queue);
      assertNotNull(queueControl);
      assertTrue(server.locateQueue(queue).isInternalQueue());
      assertEquals(queue.toString(), queueControl.getName());
      assertEquals(durable, queueControl.isDurable());

      //check that internal queue can be managed
      queueControl.pause();
      assertTrue(queueControl.isPaused());

      queueControl.resume();
      assertFalse(queueControl.isPaused());
   }

   @TestTemplate
   public void testAutoDeleteAttribute() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address));

      QueueControl queueControl = createManagementControl(address, queue);
      assertFalse(queueControl.isAutoDelete());

      session.deleteQueue(queue);

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setAutoDelete(true));

      queueControl = createManagementControl(address, queue);
      assertTrue(queueControl.isAutoDelete());

      session.deleteQueue(queue);

      server.getAddressSettingsRepository().addMatch(address.toString(), new AddressSettings().setAutoDeleteQueues(true));

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setAutoCreated(true));

      queueControl = createManagementControl(address, queue);
      assertTrue(queueControl.isAutoDelete());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGroupAttributes() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(address, RoutingType.MULTICAST, queue);

      QueueControl queueControl = createManagementControl(address, queue);
      assertFalse(queueControl.isGroupRebalance());
      assertEquals(-1, queueControl.getGroupBuckets());
      assertNull(queueControl.getGroupFirstKey());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testRetroactiveResourceAttribute() throws Exception {
      SimpleString baseAddress = RandomUtil.randomUUIDSimpleString();
      String internalNamingPrefix = server.getInternalNamingPrefix();
      String delimiter = server.getConfiguration().getWildcardConfiguration().getDelimiterString();
      SimpleString address = ResourceNames.getRetroactiveResourceAddressName(internalNamingPrefix, delimiter, baseAddress);
      SimpleString multicastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, baseAddress, RoutingType.MULTICAST);
      SimpleString anycastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, baseAddress, RoutingType.ANYCAST);

      session.createQueue(QueueConfiguration.of(multicastQueue).setAddress(address).setDurable(durable));
      session.createQueue(QueueConfiguration.of(anycastQueue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, multicastQueue);
      assertTrue(queueControl.isRetroactiveResource());
      queueControl = createManagementControl(address, anycastQueue);
      assertTrue(queueControl.isRetroactiveResource());

      session.deleteQueue(multicastQueue);
      session.deleteQueue(anycastQueue);
   }

   @TestTemplate
   public void testGetNullFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(queue.toString(), queueControl.getName());
      assertNull(queueControl.getFilter());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetDeadLetterAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      final SimpleString deadLetterAddress = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertNull(queueControl.getDeadLetterAddress());

      server.getAddressSettingsRepository().addMatch(address.toString(), new AddressSettings().setDeadLetterAddress(deadLetterAddress));
      assertEquals(deadLetterAddress.toString(), queueControl.getDeadLetterAddress());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testSetDeadLetterAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      String deadLetterAddress = RandomUtil.randomUUIDString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      AddressSettings addressSettings = new AddressSettings().setDeadLetterAddress(SimpleString.of(deadLetterAddress));
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      assertEquals(deadLetterAddress, queueControl.getDeadLetterAddress());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetExpiryAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      final SimpleString expiryAddress = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertNull(queueControl.getExpiryAddress());

      server.getAddressSettingsRepository().addMatch(address.toString(), new AddressSettings().setExpiryAddress(expiryAddress));

      assertEquals(expiryAddress.toString(), queueControl.getExpiryAddress());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testSetExpiryAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      String expiryAddress = RandomUtil.randomUUIDString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(SimpleString.of(expiryAddress));
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      assertEquals(expiryAddress, queueControl.getExpiryAddress());

      Queue serverqueue = server.locateQueue(queue);
      assertEquals(expiryAddress, serverqueue.getExpiryAddress().toString());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetConsumerCount() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      assertEquals(0, queueControl.getConsumerCount());

      ClientConsumer consumer = session.createConsumer(queue);
      Wait.assertEquals(1, () -> queueControl.getConsumerCount());

      consumer.close();
      Wait.assertEquals(0, () -> queueControl.getConsumerCount());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetConsumerJSON() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      Wait.assertEquals(0, () -> queueControl.getConsumerCount());

      ClientConsumer consumer = session.createConsumer(queue);
      Wait.assertEquals(1, () -> queueControl.getConsumerCount());

      JsonArray obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

      assertEquals(1, obj.size());

      assertEquals(0, obj.get(0).asJsonObject().getInt(ConsumerField.LAST_DELIVERED_TIME.getName()));

      assertEquals(0, obj.get(0).asJsonObject().getInt(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName()));

      consumer.close();
      assertEquals(0, queueControl.getConsumerCount());

      obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

      assertEquals(0, obj.size());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetConsumerWithMessagesJSON() throws Exception {
      long currentTime = System.currentTimeMillis();
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);

      for (int i = 0; i < 10; i++) {
         producer.send(session.createMessage(true));
      }

      Wait.assertEquals(0, () -> queueControl.getConsumerCount());

      ClientConsumer consumer = session.createConsumer(queue);
      Wait.assertEquals(1, () -> queueControl.getConsumerCount());

      session.start();

      ClientMessage clientMessage = null;

      int size = 0;
      for (int i = 0; i < 5; i++) {
         clientMessage = consumer.receiveImmediate();
         size += clientMessage.getEncodeSize();
      }

      JsonArray obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

      assertEquals(1, obj.size());

      Wait.assertEquals(5, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

      obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

      JsonObject jsonObject = obj.get(0).asJsonObject();

      assertEquals(5, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

      assertEquals(size, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()));

      assertEquals(5, jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED.getName()));

      assertEquals(size, jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()));

      assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()));

      long lastDelivered = jsonObject.getJsonNumber(ConsumerField.LAST_DELIVERED_TIME.getName()).longValue();

      assertTrue(lastDelivered > currentTime);

      assertEquals(0, jsonObject.getInt(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName()));

      clientMessage.acknowledge();

      session.commit();

      Wait.assertEquals(5, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()));

      obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

      jsonObject = obj.get(0).asJsonObject();

      assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

      long lastAcked = jsonObject.getJsonNumber(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName()).longValue();

      assertTrue(lastAcked >= lastDelivered, "lastAcked = " + lastAcked + " lastDelivered = " + lastDelivered);

      currentTime = System.currentTimeMillis();

      //now make sure they fall between the test time window
      assertTrue(currentTime >= lastAcked, "currentTime = " + currentTime + " lastAcked = " + lastAcked);

      consumer.close();

      assertEquals(0, queueControl.getConsumerCount());

      obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

      assertEquals(0, obj.size());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetConsumerMessageCountsAutoAckCore() throws Exception {
      ActiveMQConnectionFactory factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      factory.setConsumerWindowSize(1);
      testGetConsumerMessageCountsAutoAck(factory, false);
   }

   @TestTemplate
   public void testGetConsumerMessageCountsAutoAckAMQP() throws Exception {
      testGetConsumerMessageCountsAutoAck(new JmsConnectionFactory("amqp://localhost:61616"), false);
   }

   @TestTemplate
   public void testGetConsumerMessageCountsAutoAckOpenWire() throws Exception {
      testGetConsumerMessageCountsAutoAck(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), false);
   }

   @TestTemplate
   public void testGetConsumerMessageCountsAutoAckCoreIndividualAck() throws Exception {
      ActiveMQConnectionFactory factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      testGetConsumerMessageCountsAutoAck(factory, true);
   }

   public void testGetConsumerMessageCountsAutoAck(ConnectionFactory factory, boolean usePriority) throws Exception {
      SimpleString queueName = RandomUtil.randomUUIDSimpleString();
      this.session.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setDurable(durable).setRoutingType(RoutingType.ANYCAST));

      QueueControl queueControl = createManagementControl(queueName, queueName, RoutingType.ANYCAST);
      Connection connection = factory.createConnection();
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         javax.jms.Queue queue = session.createQueue(queueName.toString());

         MessageProducer producer = session.createProducer(queue);

         for (int i = 0; i < 100; i++) {
            javax.jms.Message message = session.createMessage();
            if (usePriority) {
               producer.send(message, DeliveryMode.PERSISTENT, 1, javax.jms.Message.DEFAULT_TIME_TO_LIVE);
            } else {
               producer.send(message);
            }
         }

         MessageConsumer consumer = session.createConsumer(queue);

         connection.start();

         javax.jms.Message message = null;

         for (int i = 0; i < 100; i++) {
            message = consumer.receive(5000);
            assertNotNull(message, "message " + i + " not received");
         }

         JsonArray obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         assertEquals(1, obj.size());

         Wait.assertEquals(0, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));
         Wait.assertEquals(0, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));


         obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         JsonObject jsonObject = obj.get(0).asJsonObject();

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()));

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED.getName()));

         assertTrue(jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()) > 0);

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()));

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));

         consumer.close();
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   public void testGetConsumerMessageCountsClientAckCore() throws Exception {
      ActiveMQConnectionFactory factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      factory.setConsumerWindowSize(1);
      testGetConsumerMessageCountsClientAck(factory, false);
   }

   @TestTemplate
   public void testGetConsumerMessageCountsClientAckAMQP() throws Exception {
      testGetConsumerMessageCountsClientAck(new JmsConnectionFactory("amqp://localhost:61616"), false);
   }

   @TestTemplate
   public void testGetConsumerMessageCountsClientAckOpenWire() throws Exception {
      testGetConsumerMessageCountsClientAck(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), false);
   }

   public void testGetConsumerMessageCountsClientAck(ConnectionFactory factory, boolean usePriority) throws Exception {
      SimpleString queueName = RandomUtil.randomUUIDSimpleString();
      this.session.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setDurable(durable).setRoutingType(RoutingType.ANYCAST));

      QueueControl queueControl = createManagementControl(queueName, queueName, RoutingType.ANYCAST);
      Connection connection = factory.createConnection();
      try {
         Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         javax.jms.Queue queue = session.createQueue(queueName.toString());

         MessageProducer producer = session.createProducer(queue);

         for (int i = 0; i < 100; i++) {
            javax.jms.Message message = session.createMessage();
            if (usePriority) {
               producer.send(message, DeliveryMode.PERSISTENT, 1, javax.jms.Message.DEFAULT_TIME_TO_LIVE);
            } else {
               producer.send(message);
            }
         }

         MessageConsumer consumer = session.createConsumer(queue);

         connection.start();

         javax.jms.Message message = null;

         for (int i = 0; i < 100; i++) {
            message = consumer.receive(5000);
            assertNotNull(message, "message " + i + " not received");
            message.acknowledge();
         }

         JsonArray obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         assertEquals(1, obj.size());

         Wait.assertEquals(0, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));
         Wait.assertEquals(0, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));
         Wait.assertEquals(0, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()));

         obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         JsonObject jsonObject = obj.get(0).asJsonObject();

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()));

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED.getName()));

         assertTrue(jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()) > 0);

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()));

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));

         consumer.close();
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   public void testGetConsumerMessageCountsTransactedCore() throws Exception {
      ActiveMQConnectionFactory factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      factory.setConsumerWindowSize(1);
      testGetConsumerMessageCountsTransacted(factory, false);
   }

   @TestTemplate
   public void testGetConsumerMessageCountsTransactedAMQP() throws Exception {
      testGetConsumerMessageCountsTransacted(new JmsConnectionFactory("amqp://localhost:61616"), false);
   }

   @TestTemplate
   public void testGetConsumerMessageCountsTransactedOpenWire() throws Exception {
      testGetConsumerMessageCountsTransacted(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), false);
   }

   public void testGetConsumerMessageCountsTransacted(ConnectionFactory factory, boolean usePriority) throws Exception {
      SimpleString queueName = RandomUtil.randomUUIDSimpleString();
      this.session.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setDurable(durable).setRoutingType(RoutingType.ANYCAST));

      QueueControl queueControl = createManagementControl(queueName, queueName, RoutingType.ANYCAST);
      Connection connection = factory.createConnection();
      try {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

         javax.jms.Queue queue = session.createQueue(queueName.toString());

         MessageProducer producer = session.createProducer(queue);

         for (int i = 0; i < 100; i++) {
            javax.jms.Message message = session.createMessage();
            if (usePriority) {
               producer.send(message, DeliveryMode.PERSISTENT, 1, javax.jms.Message.DEFAULT_TIME_TO_LIVE);
            } else {
               producer.send(message);
            }
         }
         session.commit();
         MessageConsumer consumer = session.createConsumer(queue);

         connection.start();

         javax.jms.Message message = null;

         for (int i = 0; i < 100; i++) {
            message = consumer.receive(5000);
            assertNotNull(message, "message " + i + " not received");
         }

         session.commit();

         JsonArray obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         assertEquals(1, obj.size());

         Wait.assertEquals(0, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));

         obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         JsonObject jsonObject = obj.get(0).asJsonObject();

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()));

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED.getName()));

         assertTrue(jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()) > 0);

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()));

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));

         consumer.close();
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   public void testGetConsumerMessageCountsTransactedXACore() throws Exception {
      ActiveMQConnectionFactory factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      factory.setConsumerWindowSize(1);
      testGetConsumerMessageCountsTransactedXA(factory, false);
   }

   @TestTemplate
   public void testGetConsumerMessageCountsTransactedXAOpenWire() throws Exception {
      testGetConsumerMessageCountsTransactedXA(new org.apache.activemq.ActiveMQXAConnectionFactory("tcp://localhost:61616"), false);
   }
   public void testGetConsumerMessageCountsTransactedXA(XAConnectionFactory factory, boolean usePriority) throws Exception {
      SimpleString queueName = RandomUtil.randomUUIDSimpleString();
      this.session.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setDurable(durable).setRoutingType(RoutingType.ANYCAST));

      QueueControl queueControl = createManagementControl(queueName, queueName, RoutingType.ANYCAST);
      XAConnection connection = factory.createXAConnection();
      try {
         XASession session = connection.createXASession();

         XidImpl xid = newXID();
         javax.jms.Queue queue = session.createQueue(queueName.toString());

         MessageProducer producer = session.createProducer(queue);
         session.getXAResource().start(xid, XAResource.TMNOFLAGS);
         for (int i = 0; i < 100; i++) {
            javax.jms.Message message = session.createMessage();
            if (usePriority) {
               producer.send(message, DeliveryMode.PERSISTENT, 1, javax.jms.Message.DEFAULT_TIME_TO_LIVE);
            } else {
               producer.send(message);
            }
         }
         session.getXAResource().end(xid, XAResource.TMSUCCESS);
         session.getXAResource().commit(xid, true);

         MessageConsumer consumer = session.createConsumer(queue);

         connection.start();
         xid = newXID();
         javax.jms.Message message = null;
         session.getXAResource().start(xid, XAResource.TMNOFLAGS);
         for (int i = 0; i < 100; i++) {
            message = consumer.receive(5000);
            assertNotNull(message, "message " + i + " not received");
         }
         session.getXAResource().end(xid, XAResource.TMSUCCESS);

         JsonArray obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         assertEquals(1, obj.size());

         Wait.assertEquals(100, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));

         obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         JsonObject jsonObject = obj.get(0).asJsonObject();

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()));

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED.getName()));

         assertTrue(jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()) > 0);

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()));

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));

         session.getXAResource().commit(xid, true);

         obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         assertEquals(1, obj.size());

         Wait.assertEquals(0, () -> JsonUtil.readJsonArray(queueControl.listConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));

         obj = JsonUtil.readJsonArray(queueControl.listConsumersAsJSON());

         jsonObject = obj.get(0).asJsonObject();

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()));

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED.getName()));

         assertTrue(jsonObject.getInt(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()) > 0);

         assertEquals(100, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()));

         assertEquals(0, jsonObject.getInt(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()));

         consumer.close();
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   public void testGetMessageCount() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, getMessageCount(queueControl));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));
      assertMessageMetrics(queueControl, 1, durable);

      consumeMessages(1, session, queue);

      assertMessageMetrics(queueControl, 0, durable);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetFirstMessage() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, getMessageCount(queueControl));

      // It's empty, so it's supposed to be like this
      assertEquals("[{}]", queueControl.getFirstMessageAsJSON());

      long beforeSend = System.currentTimeMillis();
      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(false).putStringProperty("x", "valueX").putStringProperty("y", "valueY"));
      Wait.assertEquals(1, queueControl::getMessageCount);

      long firstMessageTimestamp = queueControl.getFirstMessageTimestamp();
      assertTrue(beforeSend <= firstMessageTimestamp);
      assertTrue(firstMessageTimestamp <= System.currentTimeMillis());

      long firstMessageAge = queueControl.getFirstMessageAge();
      assertTrue(firstMessageAge <= (System.currentTimeMillis() - firstMessageTimestamp));

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testPeekFirstMessage() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, getMessageCount(queueControl));

      assertEquals("null", queueControl.peekFirstMessageAsJSON());

      String fooValue = RandomUtil.randomUUIDString();
      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(false).putStringProperty("foo", fooValue));
      Wait.assertEquals(1, queueControl::getMessageCount);

      JsonObject messageAsJson = JsonUtil.readJsonObject(queueControl.peekFirstMessageAsJSON());
      assertEquals(fooValue, messageAsJson.getString("foo"));

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testPeekFirstScheduledMessage() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, getMessageCount(queueControl));

      // It's empty, so it's supposed to be like this
      assertEquals("null", queueControl.peekFirstScheduledMessageAsJSON());

      long timestampBeforeSend = System.currentTimeMillis();

      ClientProducer producer = addClientProducer(session.createProducer(address));
      ClientMessage message = session.createMessage(durable)
              .putStringProperty("x", "valueX")
              .putStringProperty("y", "valueY")
              .putBooleanProperty("durable", durable)
              .putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, timestampBeforeSend + 5000);
      producer.send(message);

      consumeMessages(0, session, queue);
      assertScheduledMetrics(queueControl, 1, durable);

      long timestampAfterSend = System.currentTimeMillis();

      JsonObject messageAsJson = JsonUtil.readJsonObject(queueControl.peekFirstScheduledMessageAsJSON());
      assertEquals("valueX", messageAsJson.getString("x"));
      assertEquals("valueY", messageAsJson.getString("y"));
      assertEquals(durable, messageAsJson.getBoolean("durable"));

      long messageTimestamp = messageAsJson.getJsonNumber("timestamp").longValue();
      assertTrue(messageTimestamp >= timestampBeforeSend);
      assertTrue(messageTimestamp <= timestampAfterSend);

      // Make sure that the message is no longer available the "not scheduled" way
      assertEquals("[{}]", queueControl.getFirstMessageAsJSON());

      queueControl.deliverScheduledMessage(messageAsJson.getInt("messageID"));
      queueControl.flushExecutor();
      assertScheduledMetrics(queueControl, 0, durable);

      consumeMessages(1, session, queue);
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testMessageAttributeLimits() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      AddressSettings addressSettings = new AddressSettings().setManagementMessageAttributeSizeLimit(100);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      byte[] twoKBytes = new byte[2048];
      for (int i = 0; i < 2048; i++) {
         twoKBytes[i] = '*';
      }

      String twoKString = new String(twoKBytes);

      ClientMessage clientMessage = session.createMessage(false);

      clientMessage.putStringProperty("y", "valueY");
      clientMessage.putStringProperty("bigString", twoKString);
      clientMessage.putBytesProperty("bigBytes", twoKBytes);
      clientMessage.putObjectProperty("bigObject", twoKString);

      clientMessage.getBodyBuffer().writeBytes(twoKBytes);

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, getMessageCount(queueControl));

      ClientProducer producer = session.createProducer(address);
      producer.send(clientMessage);

      Wait.assertEquals(1, () -> getMessageCount(queueControl));

      assertTrue(server.getPagingManager().getPageStore(address).getAddressSize() > 2048);

      Map<String, Object>[] messages = queueControl.listMessages("");
      assertEquals(1, messages.length);
      for (String key : messages[0].keySet()) {
         Object value = messages[0].get(key);
         System.err.println(key + " " + value);
         assertTrue(value.toString().length() <= 150);

         if (value instanceof byte[] bytes) {
            assertTrue(bytes.length <= 150);
         }
      }

      String all = queueControl.listMessagesAsJSON("");
      assertTrue(all.length() < 1024);

      String first = queueControl.getFirstMessageAsJSON();
      assertTrue(first.length() < 1024);

      CompositeData[] browseResult = queueControl.browse(1, 100);
      for (CompositeData compositeData : browseResult) {
         for (String key : compositeData.getCompositeType().keySet()) {
            Object value = compositeData.get(key);
            System.err.println("" + key + ", " + value);

            if (value != null) {

               if (key.equals("StringProperties")) {
                  // these are very verbose composite data structures
                  assertTrue(value.toString().length() <= 3000, value.toString().length() + " truncated? " + key);
               } else {
                  assertTrue(value.toString().length() <= 512, value.toString().length() + " truncated? " + key);
               }

               if (value instanceof byte[] bytes) {
                  assertTrue(bytes.length <= 150, "truncated? " + key);
               }
            }
         }
      }

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testBytesMessageBodyWithoutLimits() throws Exception {
      final int BYTE_COUNT = 2048;
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      AddressSettings addressSettings = new AddressSettings().setManagementMessageAttributeSizeLimit(-1);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      byte[] randomBytes = RandomUtil.randomBytes(BYTE_COUNT);

      ClientMessage clientMessage = session.createMessage(false);
      clientMessage.getBodyBuffer().writeBytes(randomBytes);

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, getMessageCount(queueControl));

      ClientProducer producer = session.createProducer(address);
      producer.send(clientMessage);

      Wait.assertEquals(1, () -> getMessageCount(queueControl));

      CompositeData[] browseResult = queueControl.browse(1, 1);
      boolean tested = false;
      for (CompositeData compositeData : browseResult) {
         for (String key : compositeData.getCompositeType().keySet()) {
            Object value = compositeData.get(key);
            if (value != null) {
               if (value instanceof byte[] bytes) {
                  assertEqualsByteArrays(randomBytes, bytes);
                  tested = true;
               }
            }
         }
      }

      assertTrue(tested, "Nothing tested!");
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testTextMessageAttributeLimits() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      AddressSettings addressSettings = new AddressSettings().setManagementMessageAttributeSizeLimit(10);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      final String twentyBytes = new String(new char[20]).replace("\0", "#");

      ClientMessage clientMessage = session.createMessage(Message.TEXT_TYPE, durable);
      clientMessage.getBodyBuffer().writeNullableSimpleString(SimpleString.of(twentyBytes));
      clientMessage.putStringProperty("x", twentyBytes);

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, getMessageCount(queueControl));

      ClientProducer producer = session.createProducer(address);
      producer.send(clientMessage);

      Wait.assertEquals(1, () -> getMessageCount(queueControl));

      Map<String, Object>[] messages = queueControl.listMessages("");
      assertEquals(1, messages.length);
      assertTrue(((String)messages[0].get("x")).contains("more"), "truncated? ");

      CompositeData[] browseResult = queueControl.browse(1, 100);
      for (CompositeData compositeData : browseResult) {
         for (String key : new String[] {"text", "PropertiesText", "StringProperties"}) {
            assertTrue(compositeData.get(key).toString().contains("more"), "truncated? : " + key);
         }
      }

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetMessagesAdded() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, getMessagesAdded(queueControl));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));
      Wait.assertEquals(1, () -> getMessagesAdded(queueControl));
      producer.send(session.createMessage(durable));
      Wait.assertEquals(2, () -> getMessagesAdded(queueControl));

      consumeMessages(2, session, queue);

      assertEquals(2, getMessagesAdded(queueControl));

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetMessagesAcknowledged() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.getMessagesAcknowledged());

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(false));
      consumeMessages(1, session, queue);
      Wait.assertEquals(1, () -> queueControl.getMessagesAcknowledged());
      producer.send(session.createMessage(false));
      consumeMessages(1, session, queue);
      Wait.assertEquals(2, () -> queueControl.getMessagesAcknowledged());

      //      ManagementTestBase.consumeMessages(2, session, queue);

      //      assertEquals(2, getMessagesAdded(queueControl));

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetMessagesAcknowledgedOnXARollback() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.getMessagesAcknowledged());

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));

      ClientSessionFactory xaFactory = createSessionFactory(locator);
      ClientSession xaSession = addClientSession(xaFactory.createSession(true, false, false));
      xaSession.start();

      ClientConsumer consumer = xaSession.createConsumer(queue);

      int tries = 10;
      for (int i = 0; i < tries; i++) {
         XidImpl xid = newXID();
         xaSession.start(xid, XAResource.TMNOFLAGS);
         ClientMessage message = consumer.receive(1000);
         assertNotNull(message);
         message.acknowledge();
         assertEquals(0, queueControl.getMessagesAcknowledged());
         xaSession.end(xid, XAResource.TMSUCCESS);
         assertEquals(0, queueControl.getMessagesAcknowledged());
         xaSession.prepare(xid);
         assertEquals(0, queueControl.getMessagesAcknowledged());
         if (i + 1 == tries) {
            xaSession.commit(xid, false);
         } else {
            xaSession.rollback(xid);
         }
      }

      Wait.assertEquals(1, queueControl::getMessagesAcknowledged);
      Wait.assertEquals(10, queueControl::getAcknowledgeAttempts);

      consumer.close();

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetMessagesAcknowledgedOnRegularRollback() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.getMessagesAcknowledged());

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));

      ClientSessionFactory xaFactory = createSessionFactory(locator);
      ClientSession txSession = addClientSession(xaFactory.createSession(false, false, false));
      txSession.start();

      ClientConsumer consumer = txSession.createConsumer(queue);

      int tries = 10;
      for (int i = 0; i < tries; i++) {
         ClientMessage message = consumer.receive(1000);
         assertNotNull(message);
         message.acknowledge();
         assertEquals(0, queueControl.getMessagesAcknowledged());
         if (i + 1 == tries) {
            txSession.commit();
         } else {
            txSession.rollback();
         }
      }

      Wait.assertEquals(1, queueControl::getMessagesAcknowledged);
      Wait.assertEquals(10, queueControl::getAcknowledgeAttempts);

      consumer.close();

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetScheduledCount() throws Exception {
      long delay = 500;
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.getScheduledCount());

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(durable);
      message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + delay);
      producer.send(message);

      long timeout = System.currentTimeMillis() + 5000;
      while (timeout > System.currentTimeMillis() && queueControl.getScheduledCount() != 1) {
         Thread.sleep(100);
      }

      assertScheduledMetrics(queueControl, 1, durable);
      assertMessageMetrics(queueControl, 1, durable);

      consumeMessages(0, session, queue);

      Thread.sleep(delay * 2);

      assertEquals(0, queueControl.getScheduledCount());
      consumeMessages(1, session, queue);
      assertMessageMetrics(queueControl, 0, durable);
      assertScheduledMetrics(queueControl, 0, durable);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListDeliveringMessagesWithRASession() throws Exception {
      ServerLocator locator1 = createInVMNonHALocator().setBlockOnNonDurableSend(true).setConsumerWindowSize(10240).setAckBatchSize(0);
      ClientSessionFactory sf = locator1.createSessionFactory();
      final ClientSession transSession = sf.createSession(false, true, false);
      ClientConsumer consumer = null;
      SimpleString queue = null;
      int numMsg = 10;

      try {
         // a session from RA does this
         transSession.addMetaData("resource-adapter", "inbound");
         transSession.addMetaData("jms-session", "");

         SimpleString address = RandomUtil.randomUUIDSimpleString();
         queue = RandomUtil.randomUUIDSimpleString();

         transSession.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

         final QueueControl queueControl = createManagementControl(address, queue);

         ClientProducer producer = transSession.createProducer(address);

         for (int i = 0; i < numMsg; i++) {
            ClientMessage message = transSession.createMessage(durable);
            message.putIntProperty(SimpleString.of("seqno"), i);
            producer.send(message);
         }

         consumer = transSession.createConsumer(queue);
         transSession.start();

         /*
          * the following latches are used to make sure that
          *
          * 1. the first call on queueControl happens after the first message arrived at the message handler.
          * 2. the message handler wait on the first message until the queueControl returns the right/wrong result.
          * 3. the test exits after all messages are received.
          */
         final CountDownLatch latch1 = new CountDownLatch(1);
         final CountDownLatch latch2 = new CountDownLatch(1);
         final CountDownLatch latch3 = new CountDownLatch(10);

         consumer.setMessageHandler(message -> {
            try {
               message.acknowledge();
            } catch (ActiveMQException e1) {
               e1.printStackTrace();
            }
            latch1.countDown();
            try {
               latch2.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
            latch3.countDown();
         });

         latch1.await(10, TimeUnit.SECONDS);
         //now we know the ack of the message is sent but to make sure
         //the server has received it, we try 5 times
         int n = 0;
         for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);
            String jsonStr = queueControl.listDeliveringMessagesAsJSON();

            n = countOccurrencesOf(jsonStr, "seqno");

            if (n == numMsg) {
               break;
            }
         }

         assertEquals(numMsg, n);

         latch2.countDown();

         latch3.await(10, TimeUnit.SECONDS);

         transSession.commit();
      } finally {
         consumer.close();
         transSession.deleteQueue(queue);
         transSession.close();
         locator1.close();
      }
   }

   @TestTemplate
   public void testListDeliveringMessages() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      int intValue = RandomUtil.randomInt();
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      Queue srvqueue = server.locateQueue(queue);

      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(durable);
      message.putIntProperty(SimpleString.of("key"), intValue);
      producer.send(message);
      producer.send(session.createMessage(durable));

      ClientConsumer consumer = session.createConsumer(queue);
      session.start();
      ClientMessage msgRec = consumer.receive(5000);
      assertNotNull(msgRec);
      assertEquals(msgRec.getIntProperty("key").intValue(), intValue);

      ClientSessionFactory sf2 = createSessionFactory(locator);
      ClientSession session2 = sf2.createSession(false, true, false);
      ClientConsumer consumer2 = session2.createConsumer(queue);
      session2.start();
      ClientMessage msgRec2 = consumer2.receive(5000);
      assertNotNull(msgRec2);

      assertEquals(2, srvqueue.getDeliveringCount());
      assertEquals(2, srvqueue.getConsumerCount());

      Map<String, Map<String, Object>[]> deliveringMap = queueControl.listDeliveringMessages();
      assertEquals(2, deliveringMap.size());

      consumer.close();
      consumer2.close();

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListDeliveringMessagesOnClosedConsumer() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      int intValue = RandomUtil.randomInt();
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      Queue srvqueue = server.locateQueue(queue);

      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(durable);
      message.putIntProperty(SimpleString.of("key"), intValue);
      producer.send(message);
      producer.send(session.createMessage(durable));

      ClientConsumer consumer = session.createConsumer(queue);
      session.start();
      ClientMessage msgRec = consumer.receive(5000);
      assertNotNull(msgRec);
      assertEquals(msgRec.getIntProperty("key").intValue(), intValue);
      assertEquals(1, srvqueue.getDeliveringCount());
      assertEquals(1, queueControl.listDeliveringMessages().size());

      msgRec.acknowledge();
      consumer.close();
      assertEquals(1, srvqueue.getDeliveringCount());

      Map<String, Map<String, Object>[]> deliveringMap = queueControl.listDeliveringMessages();
      assertEquals(1, deliveringMap.size());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListScheduledMessages() throws Exception {
      long delay = 2000;
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      int intValue = RandomUtil.randomInt();
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(durable);
      message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + delay);
      message.putIntProperty(SimpleString.of("key"), intValue);
      producer.send(message);
      // unscheduled message
      producer.send(session.createMessage(durable));

      Map<String, Object>[] messages = queueControl.listScheduledMessages();
      assertEquals(1, messages.length);
      assertScheduledMetrics(queueControl, 1, durable);

      assertEquals(intValue, Integer.parseInt((messages[0].get("key")).toString()));

      Thread.sleep(delay + 500);

      messages = queueControl.listScheduledMessages();
      assertEquals(0, messages.length);

      consumeMessages(2, session, queue);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListScheduledMessagesAsJSON() throws Exception {
      long delay = 2000;
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      int intValue = RandomUtil.randomInt();
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);
      message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + delay);
      message.putIntProperty(SimpleString.of("key"), intValue);
      producer.send(message);
      // unscheduled message
      producer.send(session.createMessage(durable));

      String jsonString = queueControl.listScheduledMessagesAsJSON();
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(1, array.size());
      int i = Integer.parseInt(array.getJsonObject(0).get("key").toString().replaceAll("\"", ""));
      assertEquals(intValue, i);

      Thread.sleep(delay + 500);

      jsonString = queueControl.listScheduledMessagesAsJSON();
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      assertEquals(0, array.size());

      consumeMessages(2, session, queue);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetDeliveringCount() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.getDeliveringCount());

      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);
      assertDeliveringMetrics(queueControl, 1, durable);

      message.acknowledge();
      session.commit();
      assertDeliveringMetrics(queueControl, 0, durable);

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testMessagesAddedAndMessagesAcknowledged() throws Exception {
      final int THREAD_COUNT = 5;
      final int MSG_COUNT = 1000;

      CountDownLatch producerCountDown = new CountDownLatch(THREAD_COUNT);
      CountDownLatch consumerCountDown = new CountDownLatch(THREAD_COUNT);

      ExecutorService producerExecutor = Executors.newFixedThreadPool(THREAD_COUNT);
      ExecutorService consumerExecutor = Executors.newFixedThreadPool(THREAD_COUNT);

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      try {
         session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

         for (int i = 0; i < THREAD_COUNT; i++) {
            producerExecutor.submit(() -> {
               try (ClientSessionFactory sf = locator.createSessionFactory();
                    ClientSession session = sf.createSession(false, true, false);
                    ClientProducer producer = session.createProducer(address)) {
                  for (int j = 0; j < MSG_COUNT; j++) {
                     producer.send(session.createMessage(false));
                     Thread.sleep(5);
                  }
                  producerCountDown.countDown();
               } catch (Exception e) {
                  e.printStackTrace();
               }
            });
         }

         for (int i = 0; i < THREAD_COUNT; i++) {
            consumerExecutor.submit(() -> {
               try (ClientSessionFactory sf = locator.createSessionFactory();
                    ClientSession session = sf.createSession(false, true, false);
                    ClientConsumer consumer = session.createConsumer(queue)) {
                  session.start();
                  for (int j = 0; j < MSG_COUNT; j++) {
                     ClientMessage message = consumer.receive(500);
                     assertNotNull(message);
                     message.acknowledge();
                  }
                  session.commit();
                  consumerCountDown.countDown();
               } catch (Exception e) {
                  e.printStackTrace();
               }
            });
         }

         producerCountDown.await(30, TimeUnit.SECONDS);
         consumerCountDown.await(30, TimeUnit.SECONDS);

         QueueControl queueControl = createManagementControl(address, queue, RoutingType.MULTICAST);
         Thread.sleep(200);
         assertEquals(0, queueControl.getMessageCount());
         assertEquals(0, queueControl.getConsumerCount());
         assertEquals(0, queueControl.getDeliveringCount());
         assertEquals(THREAD_COUNT * MSG_COUNT, queueControl.getMessagesAdded());
         assertEquals(THREAD_COUNT * MSG_COUNT, queueControl.getMessagesAcknowledged());

         session.deleteQueue(queue);
      } finally {
         shutdownExecutor(producerExecutor);
         shutdownExecutor(consumerExecutor);
      }
   }

   private void shutdownExecutor(ExecutorService executor) {
      try {
         executor.shutdown();
         executor.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
      } finally {
         executor.shutdownNow();
      }
   }

   @TestTemplate
   public void testListMessagesAsJSONWithNullFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      int intValue = RandomUtil.randomInt();
      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(durable);
      message.putIntProperty(SimpleString.of("key"), intValue);
      producer.send(message);

      String jsonString = queueControl.listMessagesAsJSON(null);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(1, array.size());

      long l = Long.parseLong(array.getJsonObject(0).get("key").toString().replaceAll("\"", ""));
      assertEquals(intValue, l);

      consumeMessages(1, session, queue);

      jsonString = queueControl.listMessagesAsJSON(null);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      assertEquals(0, array.size());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListMessagesWithFilter() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;
      String filter = key + " =" + matchingValue;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);
      ClientMessage matchingMessage = session.createMessage(durable);
      matchingMessage.putLongProperty(key, matchingValue);
      producer.send(matchingMessage);
      ClientMessage unmatchingMessage = session.createMessage(durable);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(unmatchingMessage);

      assertMessageMetrics(queueControl, 2, durable);
      Map<String, Object>[] messages = queueControl.listMessages(filter);
      assertEquals(1, messages.length);
      assertEquals(matchingValue, Long.parseLong(messages[0].get("key").toString()));

      consumeMessages(2, session, queue);

      messages = queueControl.listMessages(filter);
      assertEquals(0, messages.length);
      assertMessageMetrics(queueControl, 0, durable);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListMessagesWithNullFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable).putBytesProperty("bytes", new byte[]{'%'}));
      producer.send(session.createMessage(durable));

      Wait.assertEquals(2, () -> queueControl.listMessages(null).length);

      consumeMessages(2, session, queue);

      Wait.assertEquals(0, () -> queueControl.listMessages(null).length);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListMessagesWithEmptyFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));
      producer.send(session.createMessage(durable));

      Wait.assertEquals(2, () -> queueControl.listMessages("").length);

      consumeMessages(2, session, queue);

      Wait.assertEquals(0, () -> queueControl.listMessages("").length);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListMessagesAsJSONWithFilter() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;
      String filter = key + " =" + matchingValue;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ClientProducer producer = session.createProducer(address);
      ClientMessage matchingMessage = session.createMessage(durable);
      matchingMessage.putLongProperty(key, matchingValue);
      producer.send(matchingMessage);
      ClientMessage unmatchingMessage = session.createMessage(durable);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(unmatchingMessage);

      String jsonString = queueControl.listMessagesAsJSON(filter);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(1, array.size());

      long l = Long.parseLong(array.getJsonObject(0).get("key").toString().replaceAll("\"", ""));
      assertEquals(matchingValue, l);

      consumeMessages(2, session, queue);

      jsonString = queueControl.listMessagesAsJSON(filter);
      assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      assertEquals(0, array.size());

      session.deleteQueue(queue);
   }

   /**
    * Test retry - get a message from DLQ and put on original queue.
    */
   @TestTemplate
   public void testRetryMessage() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString qName = SimpleString.of("q1");
      final SimpleString adName = SimpleString.of("ad1");
      final SimpleString dlq = SimpleString.of("DLQ1");
      final String sampleText = "Put me on DLQ";

      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(adName.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(durable));
      session.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(durable));

      // Send message to queue.
      ClientProducer producer = session.createProducer(adName);
      producer.send(createTextMessage(session, sampleText));
      session.start();

      ClientConsumer clientConsumer = session.createConsumer(qName);
      ClientMessage clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      // force a rollback to DLQ
      session.rollback();
      clientMessage = clientConsumer.receiveImmediate();
      assertNull(clientMessage);

      QueueControl queueControl = createManagementControl(dla, dlq);
      assertMessageMetrics(queueControl, 1, durable);
      final long messageID = getFirstMessageId(queueControl);

      // Retry the message - i.e. it should go from DLQ to original Queue.
      assertTrue(queueControl.retryMessage(messageID));

      // Assert DLQ is empty...
      assertEquals(0, getMessageCount(queueControl));
      assertMessageMetrics(queueControl, 0, durable);

      // .. and that the message is now on the original queue once more.
      clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      clientConsumer.close();
   }

   /**
    * Test retry - get a message from auto-created DLA/DLQ and put on original queue.
    */
   @TestTemplate
   public void testRetryMessageWithAutoCreatedResources() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString qName = SimpleString.of("q1");
      final SimpleString adName = SimpleString.of("ad1");
      final String sampleText = "Put me on DLQ";

      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(adName.toString());
      final SimpleString dlq = addressSettings.getDeadLetterQueuePrefix().concat(adName).concat(addressSettings.getDeadLetterQueueSuffix());

      server.getAddressSettingsRepository().addMatch(adName.toString(), new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla).setAutoCreateDeadLetterResources(true));

      session.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(durable));

      // Send message to queue.
      ClientProducer producer = session.createProducer(adName);
      producer.send(createTextMessage(session, sampleText));
      session.start();

      ClientConsumer clientConsumer = session.createConsumer(qName);
      ClientMessage clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      // force a rollback to DLQ
      session.rollback();
      clientMessage = clientConsumer.receiveImmediate();
      assertNull(clientMessage);

      QueueControl queueControl = createManagementControl(dla, dlq);
      assertMessageMetrics(queueControl, 1, true);
      final long messageID = getFirstMessageId(queueControl);

      // Retry the message - i.e. it should go from DLQ to original Queue.
      assertTrue(queueControl.retryMessage(messageID));

      // Assert DLQ is empty...
      assertEquals(0, getMessageCount(queueControl));
      assertMessageMetrics(queueControl, 0, durable);

      // .. and that the message is now on the original queue once more.
      clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      clientConsumer.close();
   }

   @TestTemplate
   public void testRetryMessageWithoutDLQ() throws Exception {
      final SimpleString qName = SimpleString.of("q1");
      final SimpleString qName2 = SimpleString.of("q2");
      final SimpleString adName = SimpleString.of("ad1");
      final SimpleString adName2 = SimpleString.of("ad2");
      final String sampleText = "Put me on DLQ";

      session.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(durable));
      session.createQueue(QueueConfiguration.of(qName2).setAddress(adName2).setDurable(durable));

      // Send message to queue.
      ClientProducer producer = session.createProducer(adName);
      producer.send(createTextMessage(session, sampleText));
      ClientMessage m = createTextMessage(session, sampleText);
      m.putStringProperty(Message.HDR_ORIGINAL_ADDRESS, adName2);
      m.putStringProperty(Message.HDR_ORIGINAL_QUEUE, qName2);
      producer.send(m);
      session.start();

      QueueControl queueControl = createManagementControl(adName, qName);
      assertMessageMetrics(queueControl, 2, durable);

      QueueControl queueControl2 = createManagementControl(adName2, qName2);
      assertMessageMetrics(queueControl2, 0, durable);

      queueControl.retryMessages();

      Wait.assertTrue(() -> getMessageCount(queueControl) == 1, 2000, 100);
      assertMessageMetrics(queueControl, 1, durable);

      Wait.assertTrue(() -> getMessageCount(queueControl2) == 1, 2000, 100);
      assertMessageMetrics(queueControl2, 1, durable);

      ClientConsumer clientConsumer = session.createConsumer(qName);
      ClientMessage clientMessage = clientConsumer.receive(500);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      clientConsumer = session.createConsumer(qName2);
      clientMessage = clientConsumer.receive(500);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      clientConsumer.close();
   }

   /**
    * Test retry - get a diverted message from DLQ and put on original queue.
    */
   @TestTemplate
   public void testRetryDivertedMessage() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString dlq = SimpleString.of("DLQ");
      final SimpleString forwardingQueue = SimpleString.of("forwardingQueue");
      final SimpleString forwardingAddress = SimpleString.of("forwardingAddress");
      final SimpleString myTopic = SimpleString.of("myTopic");
      final String sampleText = "Put me on DLQ";

      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(forwardingAddress.toString(), addressSettings);

      // create target queue, DLQ and source topic
      session.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(durable));
      session.createQueue(QueueConfiguration.of(forwardingQueue).setAddress(forwardingAddress).setDurable(durable));
      session.createAddress(myTopic, RoutingType.MULTICAST, false);

      DivertConfiguration divert = new DivertConfiguration().setName("local-divert")
            .setRoutingName("some-name").setAddress(myTopic.toString())
            .setForwardingAddress(forwardingAddress.toString()).setExclusive(false);
      server.deployDivert(divert);

      // Send message to topic.
      ClientProducer producer = session.createProducer(myTopic);
      producer.send(createTextMessage(session, sampleText));
      session.start();

      ClientConsumer clientConsumer = session.createConsumer(forwardingQueue);
      ClientMessage clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      // force a rollback to DLQ
      session.rollback();
      clientMessage = clientConsumer.receiveImmediate();
      assertNull(clientMessage);

      QueueControl queueControl = createManagementControl(dla, dlq, RoutingType.MULTICAST);
      assertMessageMetrics(queueControl, 1, durable);

      final long messageID = getFirstMessageId(queueControl);

      // Retry the message - i.e. it should go from DLQ to original Queue.
      assertTrue(queueControl.retryMessage(messageID));

      // Assert DLQ is empty...
      assertMessageMetrics(queueControl, 0, durable);

      // .. and that the message is now on the original queue once more.
      clientMessage = clientConsumer.receive(500);
      assertNotNull(clientMessage); // fails because of AMQ222196 !!!
      clientMessage.acknowledge();

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      clientConsumer.close();
   }

   /**
    * Test retry multiple messages from  DLQ to original queue.
    */
   @TestTemplate
   public void testRetryMultipleMessages() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString qName = SimpleString.of("q1");
      final SimpleString adName = SimpleString.of("ad1");
      final SimpleString dlq = SimpleString.of("DLQ1");
      final String sampleText = "Put me on DLQ";
      final int numMessagesToTest = 10;

      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(adName.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(durable));
      session.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(durable));

      // Send message to queue.
      ClientProducer producer = session.createProducer(adName);
      for (int i = 0; i < numMessagesToTest; i++) {
         producer.send(createTextMessage(session, sampleText));
      }

      session.start();

      final LocalQueueBinding binding = (LocalQueueBinding) server.getPostOffice().getBinding(qName);
      Queue q = binding.getQueue();
      final LocalQueueBinding binding2 = (LocalQueueBinding) server.getPostOffice().getBinding(dlq);
      Queue q2 = binding2.getQueue();

      //Verify that original queue has a memory size greater than 0 and DLQ is 0
      assertTrue(QueueImplTestAccessor.getQueueMemorySize(q) > 0);
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q2));

      // Read and rollback all messages to DLQ
      ClientConsumer clientConsumer = session.createConsumer(qName);
      for (int i = 0; i < numMessagesToTest; i++) {
         ClientMessage clientMessage = clientConsumer.receive(500);
         clientMessage.acknowledge();
         assertNotNull(clientMessage);
         assertEquals(sampleText, clientMessage.getBodyBuffer().readString());
         session.rollback();
      }

      assertNull(clientConsumer.receiveImmediate());

      //Verify that original queue has a memory size of 0 and DLQ is greater than 0 after rollback
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q));
      assertTrue(QueueImplTestAccessor.getQueueMemorySize(q2) > 0);

      QueueControl dlqQueueControl = createManagementControl(dla, dlq);
      assertMessageMetrics(dlqQueueControl, numMessagesToTest, durable);

      // Retry all messages - i.e. they should go from DLQ to original Queue.
      assertEquals(numMessagesToTest, dlqQueueControl.retryMessages());

      // Assert DLQ is empty...
      assertMessageMetrics(dlqQueueControl, 0, durable);

      //Verify that original queue has a memory size of greater than 0 and DLQ is 0 after move
      assertTrue(QueueImplTestAccessor.getQueueMemorySize(q) > 0);
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q2));

      // .. and that the messages is now on the original queue once more.
      for (int i = 0; i < numMessagesToTest; i++) {
         ClientMessage clientMessage = clientConsumer.receive(500);
         clientMessage.acknowledge();
         assertNotNull(clientMessage);
         assertEquals(sampleText, clientMessage.getBodyBuffer().readString());
      }

      clientConsumer.close();

      //Verify that original queue and DLQ have a memory size of 0
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q));
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q2));
   }

   /**
    * Test retry messages matching a filter from DLQ to original queue.
    */
   @TestTemplate
   public void testRetryMatchingMessages() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString qName = SimpleString.of("q1");
      final SimpleString adName = SimpleString.of("ad1");
      final SimpleString dlq = SimpleString.of("DLQ1");
      final String sampleText = "Put me on DLQ";

      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla);
      server.getAddressSettingsRepository().addMatch(adName.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(durable));
      session.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(durable));

      // Send message to queue.
      ClientProducer producer = session.createProducer(adName);

      producer.send(createTextMessage(session, sampleText + ":red").putStringProperty("color", "red"));
      producer.send(createTextMessage(session, sampleText + ":green").putStringProperty("color", "green"));
      producer.send(createTextMessage(session, sampleText + ":blue").putStringProperty("color", "blue"));

      session.start();

      final LocalQueueBinding binding = (LocalQueueBinding) server.getPostOffice().getBinding(qName);
      Queue q = binding.getQueue();
      final LocalQueueBinding binding2 = (LocalQueueBinding) server.getPostOffice().getBinding(dlq);
      Queue q2 = binding2.getQueue();

      //Verify that original queue has a memory size greater than 0 and DLQ is 0
      assertTrue(QueueImplTestAccessor.getQueueMemorySize(q) > 0);
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q2));

      // Read and rollback all messages to DLQ
      ClientConsumer clientConsumer = session.createConsumer(qName);

      ClientMessage clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);
      assertEquals(sampleText + ":red", clientMessage.getBodyBuffer().readString());
      session.rollback();
      clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);
      assertEquals(sampleText + ":green", clientMessage.getBodyBuffer().readString());
      session.rollback();
      clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);
      assertEquals(sampleText + ":blue", clientMessage.getBodyBuffer().readString());
      session.rollback();

      assertNull(clientConsumer.receiveImmediate());

      //Verify that original queue has a memory size of 0 and DLQ is greater than 0 after rollback
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q));
      assertTrue(QueueImplTestAccessor.getQueueMemorySize(q2) > 0);

      QueueControl dlqQueueControl = createManagementControl(dla, dlq);
      assertMessageMetrics(dlqQueueControl, 3, durable);

      // Retry matching messages - i.e. they should go from DLQ to original Queue.
      assertEquals(1, dlqQueueControl.retryMessages("color = 'green'"));

      // Assert DLQ is not empty...
      assertMessageMetrics(dlqQueueControl, 2, durable);

      //Verify that original queue has a memory size of greater than 0 and DLQ still has data since the filter only matched one
      assertTrue(QueueImplTestAccessor.getQueueMemorySize(q) > 0);
      assertNotEquals(0, QueueImplTestAccessor.getQueueMemorySize(q2));

      // .. and that the message is now on the original queue once more.
      {
         ClientMessage retriedMessage = clientConsumer.receive(500);
         clientMessage.acknowledge();
         assertNotNull(retriedMessage);
         assertEquals(sampleText + ":green", retriedMessage.getBodyBuffer().readString());
      }

      // Retry moving messages without a filter - i.e. all remaining should go from DLQ to original Queue.
      assertEquals(2, dlqQueueControl.retryMessages(null));

      // .. and that the messages are now on the original queue once more.
      {
         ClientMessage retriedMessage = clientConsumer.receive(500);
         clientMessage.acknowledge();
         assertNotNull(retriedMessage);
         assertEquals(sampleText + ":red", retriedMessage.getBodyBuffer().readString());
         retriedMessage = clientConsumer.receive(500);
         clientMessage.acknowledge();
         assertNotNull(retriedMessage);
         assertEquals(sampleText + ":blue", retriedMessage.getBodyBuffer().readString());
      }

      clientConsumer.close();

      //Verify that original queue and DLQ have a memory size of 0
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q));
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q2));
   }

   /**
    * Test send to DLA while paging includes paged messages
    */
   @TestTemplate
   public void testSendToDLAIncludesPagedMessages() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString qName = SimpleString.of("q1");
      final SimpleString adName = SimpleString.of("ad1");
      final SimpleString dlq = SimpleString.of("DLQ1");
      final String sampleText = "Put me on DLQ";
      final int messageCount = 10;

      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla).setMaxSizeBytes(200L);
      server.getAddressSettingsRepository().addMatch(adName.toString(), addressSettings);
      server.getAddressSettingsRepository().addMatch(dla.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(durable));
      session.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(durable));

      // Send message to queue, make sure address enters paging.
      ClientProducer producer = session.createProducer(adName);
      for (int i = 0; i < messageCount; i++) {
         producer.send(createTextMessage(session, sampleText));
      }

      Wait.assertTrue(server.locateQueue(qName).getPagingStore()::isPaging);

      //Send all messages to DLA, make sure all are sent
      QueueControl queueControl = createManagementControl(adName, qName);
      assertEquals(messageCount, queueControl.sendMessagesToDeadLetterAddress(null));
      assertEquals(0, getMessageCount(queueControl));

      //Make sure all shows up on DLA
      queueControl = createManagementControl(dla, dlq);
      assertEquals(messageCount, getMessageCount(queueControl));

      queueControl.removeAllMessages();

   }

   /**
    * Test send single message to DLA while paging includes paged message
    */
   @TestTemplate
   public void testSendMessageToDLAIncludesPagedMessage() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString qName = SimpleString.of("q1");
      final SimpleString adName = SimpleString.of("ad1");
      final SimpleString dlq = SimpleString.of("DLQ1");
      final String sampleText = "Message Content";
      final int messageCount = 10;

      AddressSettings addressSettings = new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla).setMaxSizeBytes(200L);
      server.getAddressSettingsRepository().addMatch(adName.toString(), addressSettings);
      server.getAddressSettingsRepository().addMatch(dla.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(durable));
      session.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(durable));

      // Send message to queue, make sure address enters paging.
      ClientProducer producer = session.createProducer(adName);
      for (int i = 0; i < messageCount; i++) {
         producer.send(createTextMessage(session, sampleText));
      }

      Wait.assertTrue(server.locateQueue(qName).getPagingStore()::isPaging);

      //Send identifiable message to DLA
      producer.send(createTextMessage(session, sampleText).putStringProperty("myID", "unique"));
      QueueControl queueControl = createManagementControl(adName, qName);
      Map<String, Object>[] messages = queueControl.listMessages(null);
      long messageID = (Long) messages[messageCount].get("messageID");

      assertTrue(queueControl.sendMessageToDeadLetterAddress(messageID));
      queueControl.removeAllMessages();

      //Make sure it shows up on DLA
      queueControl = createManagementControl(dla, dlq);
      messages = queueControl.listMessages(null);
      assertEquals(1, messages.length);
      assertEquals("unique", (String) messages[0].get("myID"));

      queueControl.removeAllMessages();

   }

   /**
    * <ol>
    * <li>send a message to queue</li>
    * <li>move all messages from queue to otherQueue using management method</li>
    * <li>check there is no message to consume from queue</li>
    * <li>consume the message from otherQueue</li>
    * </ol>
    */
   @TestTemplate
   public void testMoveMessages() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString otherAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString otherQueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue).setAddress(otherAddress).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      ClientMessage message = session.createMessage(durable);
      SimpleString key = RandomUtil.randomUUIDSimpleString();
      long value = RandomUtil.randomLong();
      message.putLongProperty(key, value);
      producer.send(message);

      final LocalQueueBinding binding = (LocalQueueBinding) server.getPostOffice().getBinding(queue);
      Queue q = binding.getQueue();

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 1, durable);

      //verify memory usage is greater than 0
      assertTrue(QueueImplTestAccessor.getQueueMemorySize(q) > 0);

      // moved all messages to otherQueue
      int movedMessagesCount = queueControl.moveMessages(null, otherQueue.toString());
      assertEquals(1, movedMessagesCount);
      assertMessageMetrics(queueControl, 0, durable);

      //verify memory usage is 0 after move
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q));

      // check there is no message to consume from queue
      consumeMessages(0, session, queue);

      // consume the message from otherQueue
      ClientConsumer otherConsumer = session.createConsumer(otherQueue);
      ClientMessage m = otherConsumer.receive(500);
      assertEquals(value, m.getObjectProperty(key));

      m.acknowledge();

      session.deleteQueue(queue);
      otherConsumer.close();
      session.deleteQueue(otherQueue);
   }

   @TestTemplate
   public void testMoveMessages2() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queueA = SimpleString.of("A");
      SimpleString queueB = SimpleString.of("B");
      SimpleString queueC = SimpleString.of("C");

      server.createQueue(QueueConfiguration.of(queueA).setAddress(address).setDurable(durable));
      server.createQueue(QueueConfiguration.of(queueB).setAddress(address).setDurable(durable));
      server.createQueue(QueueConfiguration.of(queueC).setAddress(address).setDurable(durable));


      QueueControl queueControlA = createManagementControl(address, queueA);
      QueueControl queueControlB = createManagementControl(address, queueB);
      QueueControl queueControlC = createManagementControl(address, queueC);

      // send two messages on queueA

      queueControlA.sendMessage(new HashMap<>(), Message.BYTES_TYPE, Base64.encodeBytes("theBody".getBytes()), true, "myUser", "myPassword");
      queueControlA.sendMessage(new HashMap<>(), Message.BYTES_TYPE, Base64.encodeBytes("theBody2".getBytes()), true, "myUser", "myPassword");

      Wait.assertEquals(2, () -> getMessageCount(queueControlA));
      Wait.assertEquals(0, () -> getMessageCount(queueControlB));
      Wait.assertEquals(0, () -> getMessageCount(queueControlC));

      // move 2 messages from queueA to queueB
      queueControlA.moveMessages(null, queueB.toString());
      Thread.sleep(500);
      Wait.assertEquals(0, () -> getMessageCount(queueControlA));
      Wait.assertEquals(2, () -> getMessageCount(queueControlB));

      // move 1 message to queueC
      queueControlA.sendMessage(new HashMap<>(), Message.BYTES_TYPE, Base64.encodeBytes("theBody3".getBytes()), true, "myUser", "myPassword");
      Wait.assertEquals(1, () -> getMessageCount(queueControlA));
      queueControlA.moveMessages(null, queueC.toString());
      Wait.assertEquals(1, () -> getMessageCount(queueControlC));
      Wait.assertEquals(0, () -> getMessageCount(queueControlA));

      //move all messages back to A
      queueControlB.moveMessages(null, queueA.toString());
      Wait.assertEquals(2, () -> getMessageCount(queueControlA));
      Wait.assertEquals(0, () -> getMessageCount(queueControlB));

      queueControlC.moveMessages(null, queueA.toString());
      Wait.assertEquals(3, () -> getMessageCount(queueControlA));
      Wait.assertEquals(0, () -> getMessageCount(queueControlC));

      // consume the message from queueA
      ClientConsumer consumer = session.createConsumer(queueA);
      ClientMessage m1 = consumer.receive(500);
      ClientMessage m2 = consumer.receive(500);
      ClientMessage m3 = consumer.receive(500);

      m1.acknowledge();
      m2.acknowledge();
      m3.acknowledge();

      consumer.close();
      session.deleteQueue(queueA);
      session.deleteQueue(queueB);
      session.deleteQueue(queueC);

   }

   @TestTemplate
   public void testMoveMessagesToUnknownQueue() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString unknownQueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      ClientMessage message = session.createMessage(durable);
      SimpleString key = RandomUtil.randomUUIDSimpleString();
      long value = RandomUtil.randomLong();
      message.putLongProperty(key, value);
      producer.send(message);

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 1, durable);

      // moved all messages to unknown queue
      try {
         queueControl.moveMessages(null, unknownQueue.toString());
         fail("operation must fail if the other queue does not exist");
      } catch (Exception e) {
      }
      assertEquals(1, getMessageCount(queueControl));
      assertMessageMetrics(queueControl, 1, durable);

      consumeMessages(1, session, queue);

      session.deleteQueue(queue);
   }

   /**
    * <ol>
    * <li>send 2 message to queue</li>
    * <li>move messages from queue to otherQueue using management method <em>with filter</em></li>
    * <li>consume the message which <strong>did not</strong> matches the filter from queue</li>
    * <li>consume the message which <strong>did</strong> matches the filter from otherQueue</li>
    * </ol>
    */
   @TestTemplate
   public void testMoveMessagesWithFilter() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString otherAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString otherQueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue).setAddress(otherAddress).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      ClientMessage matchingMessage = session.createMessage(durable);
      matchingMessage.putLongProperty(key, matchingValue);
      producer.send(matchingMessage);
      ClientMessage unmatchingMessage = session.createMessage(durable);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(unmatchingMessage);

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 2, durable);

      // moved matching messages to otherQueue
      int movedMatchedMessagesCount = queueControl.moveMessages(key + " =" + matchingValue, otherQueue.toString());
      assertEquals(1, movedMatchedMessagesCount);
      assertEquals(1, getMessageCount(queueControl));

      // consume the unmatched message from queue
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage m = consumer.receive(500);
      assertNotNull(m);
      assertEquals(unmatchingValue, m.getObjectProperty(key));

      // consume the matched message from otherQueue
      ClientConsumer otherConsumer = session.createConsumer(otherQueue);
      m = otherConsumer.receive(500);
      assertNotNull(m);
      assertEquals(matchingValue, m.getObjectProperty(key));

      m.acknowledge();

      consumer.close();
      session.deleteQueue(queue);
      otherConsumer.close();
      session.deleteQueue(otherQueue);
   }

   @TestTemplate
   public void testMoveMessagesWithMessageCount() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString otherAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString otherQueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue).setAddress(otherAddress).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      for (int i = 0; i < 10; i++) {
         ClientMessage message = session.createMessage(durable);
         SimpleString key = RandomUtil.randomUUIDSimpleString();
         long value = RandomUtil.randomLong();
         message.putLongProperty(key, value);
         producer.send(message);
      }

      final LocalQueueBinding binding = (LocalQueueBinding) server.getPostOffice().getBinding(queue);
      Wait.assertEquals(10, () -> binding.getQueue().getMessageCount());

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(10, queueControl.getMessageCount());

      // moved all messages to otherQueue
      int movedMessagesCount = queueControl.moveMessages(QueueControlImpl.FLUSH_LIMIT, null, otherQueue.toString(), false, 5);
      assertEquals(5, movedMessagesCount);
      assertEquals(5, queueControl.getMessageCount());

      consumeMessages(5, session, queue);

      consumeMessages(5, session, otherQueue);

      session.deleteQueue(queue);
      session.deleteQueue(otherQueue);
   }

   @TestTemplate
   public void testMoveMessage() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString otherAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString otherQueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue).setAddress(otherAddress).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send 2 messages on queue
      producer.send(session.createMessage(durable));
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      QueueControl otherQueueControl = createManagementControl(otherAddress, otherQueue);
      assertMessageMetrics(queueControl, 2, durable);
      assertMessageMetrics(otherQueueControl, 0, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(2, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      boolean moved = queueControl.moveMessage(messageID, otherQueue.toString());
      assertTrue(moved);
      assertMessageMetrics(queueControl, 1, durable);
      assertMessageMetrics(otherQueueControl, 1, durable);

      consumeMessages(1, session, queue);
      consumeMessages(1, session, otherQueue);

      session.deleteQueue(queue);
      session.deleteQueue(otherQueue);
   }

   @TestTemplate
   public void testCopyMessage() throws Exception {
      SimpleString address = SimpleString.of("address");//RandomUtil.randomSimpleString();
      SimpleString queue = SimpleString.of("queue");//RandomUtil.randomSimpleString();
      SimpleString otherAddress = SimpleString.of("otherAddress");//RandomUtil.randomSimpleString();
      SimpleString otherQueue = SimpleString.of("otherQueue");//RandomUtil.randomSimpleString();
      SimpleString otherQueue2 = SimpleString.of("otherQueue2");//RandomUtil.randomSimpleString();


      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue).setAddress(otherAddress).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue2).setAddress(otherAddress).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send 2 messages on queue
      producer.send(session.createMessage(durable));
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      QueueControl otherQueueControl = createManagementControl(otherAddress, otherQueue);
      QueueControl otherQueueControl2 = createManagementControl(otherAddress, otherQueue2);
      assertMessageMetrics(queueControl, 2, durable);
      assertMessageMetrics(otherQueueControl, 0, durable);
      assertMessageMetrics(otherQueueControl2, 0, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(2, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      boolean copied = queueControl.copyMessage(messageID, otherQueue.toString());
      assertTrue(copied);

      assertMessageMetrics(queueControl, 2, durable);
      assertMessageMetrics(otherQueueControl, 1, durable);
      assertMessageMetrics(otherQueueControl2, 0, durable);

      messageID = (Long) messages[1].get("messageID");
      copied = queueControl.copyMessage(messageID, otherQueue.toString());
      assertTrue(copied);

      assertMessageMetrics(queueControl, 2, durable);
      assertMessageMetrics(otherQueueControl, 2, durable);
      assertMessageMetrics(otherQueueControl2, 0, durable);

      consumeMessages(2, session, queue);
      consumeMessages(2, session, otherQueue);
      consumeMessages(0, session, otherQueue2);

      session.deleteQueue(queue);
      session.deleteQueue(otherQueue);
      session.deleteQueue(otherQueue2);
   }

   @TestTemplate
   public void testCopyLargeMessage() throws Exception {
      SimpleString address = SimpleString.of("address");//RandomUtil.randomSimpleString();
      SimpleString queue = SimpleString.of("queue");//RandomUtil.randomSimpleString();
      SimpleString otherAddress = SimpleString.of("otherAddress");//RandomUtil.randomSimpleString();
      SimpleString otherQueue = SimpleString.of("otherQueue");//RandomUtil.randomSimpleString();
      SimpleString otherQueue2 = SimpleString.of("otherQueue2");//RandomUtil.randomSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue).setAddress(otherAddress).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue2).setAddress(otherAddress).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      final byte[] payload1 = new byte[ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE + 1000];
      final Random random1 = new Random(System.currentTimeMillis());
      random1.nextBytes(payload1);
      final ClientMessage sentMessage1 = session.createMessage(durable).writeBodyBufferBytes(payload1);

      final byte[] payload2 = new byte[ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE + 1000];
      final Random random2 = new Random(System.currentTimeMillis());
      random2.nextBytes(payload2);
      final ClientMessage sentMessage2 = session.createMessage(durable).writeBodyBufferBytes(payload2);

      // send 2 messages on queue
      producer.send(sentMessage1);
      producer.send(sentMessage2);

      QueueControl queueControl = createManagementControl(address, queue);
      QueueControl otherQueueControl = createManagementControl(otherAddress, otherQueue);
      QueueControl otherQueueControl2 = createManagementControl(otherAddress, otherQueue2);
      assertMessageMetrics(queueControl, 2, durable);
      assertMessageMetrics(otherQueueControl, 0, durable);
      assertMessageMetrics(otherQueueControl2, 0, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(2, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      boolean copied = queueControl.copyMessage(messageID, otherQueue.toString());
      assertTrue(copied);

      assertMessageMetrics(queueControl, 2, durable);
      assertMessageMetrics(otherQueueControl, 1, durable);
      assertMessageMetrics(otherQueueControl2, 0, durable);

      messageID = (Long) messages[1].get("messageID");
      copied = queueControl.copyMessage(messageID, otherQueue.toString());
      assertTrue(copied);

      assertMessageMetrics(queueControl, 2, durable);
      assertMessageMetrics(otherQueueControl, 2, durable);
      assertMessageMetrics(otherQueueControl2, 0, durable);

      ClientConsumer consumer1 = session.createConsumer(queue);
      ClientMessage clientMessage = consumer1.receiveImmediate();
      assertNotNull(clientMessage);
      byte[] returnedPayload = new byte[clientMessage.getBodySize()];
      clientMessage.getBodyBuffer().readBytes(returnedPayload);
      assertEqualsByteArrays(payload1, returnedPayload);

      clientMessage = consumer1.receiveImmediate();
      assertNotNull(clientMessage);
      returnedPayload = new byte[clientMessage.getBodySize()];
      clientMessage.getBodyBuffer().readBytes(returnedPayload);
      assertEqualsByteArrays(payload2, returnedPayload);

      ClientConsumer consumer2 = session.createConsumer(otherQueue);
      clientMessage = consumer2.receiveImmediate();
      assertNotNull(clientMessage);
      returnedPayload = new byte[clientMessage.getBodySize()];
      clientMessage.getBodyBuffer().readBytes(returnedPayload);
      assertEqualsByteArrays(payload1, returnedPayload);

      clientMessage = consumer2.receiveImmediate();
      assertNotNull(clientMessage);
      returnedPayload = new byte[clientMessage.getBodySize()];
      clientMessage.getBodyBuffer().readBytes(returnedPayload);
      assertEqualsByteArrays(payload2, returnedPayload);

      consumeMessages(0, session, otherQueue2);

      consumer1.close();
      consumer2.close();

      session.deleteQueue(queue);
      session.deleteQueue(otherQueue);
      session.deleteQueue(otherQueue2);
   }

   @TestTemplate
   public void testCoreCopyMessage() throws Exception {
      testCopyMessage("CORE", false);
   }

   @TestTemplate
   public void testCoreCopyLargeMessage() throws Exception {
      testCopyMessage("CORE", true);
   }

   @TestTemplate
   public void testAMQPCopyMessage() throws Exception {
      testCopyMessage("AMQP", false);
   }

   @TestTemplate
   public void testAMQPCopyLargeMessage() throws Exception {
      testCopyMessage("AMQP", true);
   }

   @TestTemplate
   public void testOpenwireCopyMessage() throws Exception {
      testCopyMessage("OPENWIRE", false);
   }


   @TestTemplate
   public void testOpenwireCopyLargeMessage() throws Exception {
      testCopyMessage("OPENWIRE", true);
   }

   public void testCopyMessage(String protocol, boolean isLarge) throws Exception {
      SimpleString address = SimpleString.of("queue1");//RandomUtil.randomSimpleString();
      SimpleString queue = SimpleString.of("queue1");//RandomUtil.randomSimpleString();
      SimpleString otherAddress = SimpleString.of("queue2");//RandomUtil.randomSimpleString();
      SimpleString otherQueue = SimpleString.of("queue2");//RandomUtil.randomSimpleString();


      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable).setRoutingType(RoutingType.ANYCAST));
      session.createQueue(QueueConfiguration.of(otherQueue).setAddress(otherAddress).setDurable(durable).setRoutingType(RoutingType.ANYCAST));

      ConnectionFactory connectionFactory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      try (Connection connection = connectionFactory.createConnection()) {
         Session jmsProducerSession = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         Session jmsConsumerSession = connection.createSession(Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue1 = jmsProducerSession.createQueue("queue1");
         javax.jms.Queue queue2 = jmsConsumerSession.createQueue("queue2");

         MessageProducer producer = jmsProducerSession.createProducer(queue1);

         final String payload1;
         final String payload2;
         if (isLarge) {
            final Random random1 = new Random(System.currentTimeMillis());
            byte[] bytePayload1 = new byte[ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE + 1000];
            random1.nextBytes(bytePayload1);
            payload1 = new String(bytePayload1);
            final Random random2 = new Random(System.currentTimeMillis());
            byte[] bytePayload2 = new byte[ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE + 1000];
            random2.nextBytes(bytePayload2);
            payload2 = new String(bytePayload2);
         } else {
            payload1 = "message1";
            payload2 = "message2";
         }

         // send 2 messages on queue
         producer.send(jmsProducerSession.createTextMessage(payload1));
         producer.send(jmsProducerSession.createTextMessage(payload2));

         QueueControl queueControl = createManagementControl(address, queue, RoutingType.ANYCAST);
         QueueControl otherQueueControl = createManagementControl(otherAddress, otherQueue, RoutingType.ANYCAST);
         assertMessageMetrics(queueControl, 2, durable);
         assertMessageMetrics(otherQueueControl, 0, durable);

         // the message IDs are set on the server
         Map<String, Object>[] messages = queueControl.listMessages(null);
         assertEquals(2, messages.length);
         long messageID = (Long) messages[0].get("messageID");

         boolean copied = queueControl.copyMessage(messageID, otherQueue.toString());
         assertTrue(copied);

         assertMessageMetrics(queueControl, 2, durable);
         assertMessageMetrics(otherQueueControl, 1, durable);

         messageID = (Long) messages[1].get("messageID");
         copied = queueControl.copyMessage(messageID, otherQueue.toString());
         assertTrue(copied);

         assertMessageMetrics(queueControl, 2, durable);
         assertMessageMetrics(otherQueueControl, 2, durable);

         MessageConsumer consumer1 = jmsConsumerSession.createConsumer(queue1);
         MessageConsumer consumer2 = jmsConsumerSession.createConsumer(queue2);
         connection.start();

         javax.jms.TextMessage message = (TextMessage) consumer1.receive(500);
         assertNotNull(message);
         assertEquals(payload1, message.getText());
         message = (TextMessage) consumer1.receive(500);
         assertNotNull(message);
         assertEquals(payload2, message.getText());

         message = (TextMessage) consumer2.receive(500);
         assertNotNull(message);
         assertEquals(payload1, message.getText());
         message = (TextMessage) consumer2.receive(500);
         assertNotNull(message);
         assertEquals(payload2, message.getText());

         connection.close();
      }
      session.deleteQueue(queue);
      session.deleteQueue(otherQueue);
   }

   /**
    * Moving message from another address to a single "child" queue of a multicast address
    * <pre>{@code
    *    <address name="ErrorQueue">
    *       <anycast>
    *          <queue name="ErrorQueue" />
    *       </anycast>
    *    </address>
    *    <address name="parent.addr.1">
    *       <multicast>
    *          <queue name="child.queue.1" />
    *          <queue name="child.queue.2" />
    *       </multicast>
    *    </address>
    * }</pre>
    */
   @TestTemplate
   public void testMoveMessageToFQQN() throws Exception {
      SimpleString address = SimpleString.of("ErrorQueue");
      SimpleString queue = SimpleString.of("ErrorQueue");
      SimpleString otherAddress = SimpleString.of("parent.addr.1");
      SimpleString otherQueue1 = SimpleString.of("child.queue.1");
      SimpleString otherQueue2 = SimpleString.of("child.queue.2");

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue1).setAddress(otherAddress).setDurable(durable));
      session.createQueue(QueueConfiguration.of(otherQueue2).setAddress(otherAddress).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      producer.send(session.createMessage(durable));
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue, RoutingType.ANYCAST);
      QueueControl otherQueue1Control = createManagementControl(otherAddress, otherQueue1);
      QueueControl otherQueue2Control = createManagementControl(otherAddress, otherQueue2);
      assertMessageMetrics(queueControl, 2, durable);
      assertMessageMetrics(otherQueue1Control, 0, durable);
      assertMessageMetrics(otherQueue2Control, 0, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(2, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      boolean moved = queueControl.moveMessage(messageID, otherQueue1.toString());
      assertTrue(moved);
      assertMessageMetrics(queueControl, 1, durable);
      assertMessageMetrics(otherQueue1Control, 1, durable);
      assertMessageMetrics(otherQueue2Control, 0, durable);

      consumeMessages(1, session, queue);
      consumeMessages(1, session, otherQueue1);

      session.deleteQueue(queue);
      session.deleteQueue(otherQueue1);
      session.deleteQueue(otherQueue2);
   }

   @TestTemplate
   public void testMoveMessageToUnknownQueue() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString unknownQueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send 2 messages on queue
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 1, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(1, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      // moved all messages to unknown queue
      try {
         queueControl.moveMessage(messageID, unknownQueue.toString());
         fail("operation must fail if the other queue does not exist");
      } catch (Exception e) {
      }
      assertEquals(1, getMessageCount(queueControl));

      consumeMessages(1, session, queue);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCopiedMessageProperties() throws Exception {
      final String testAddress = "testAddress";
      final SimpleString queue = SimpleString.of("queue");
      final int COUNT = 5;

      for (int i = 0; i < COUNT; i++) {
         server.createQueue(QueueConfiguration.of(queue.concat(Integer.toString(i))).setAddress(testAddress + i).setRoutingType(RoutingType.ANYCAST));
      }

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress + "0"));
      ClientMessage message = session.createMessage(durable);
      producer.send(message);
      producer.close();

      for (int i = 0; i < COUNT - 1; i++) {
         QueueControl queueControl = createManagementControl(SimpleString.of(testAddress + i), queue.concat(Integer.toString(i)), RoutingType.ANYCAST);
         QueueControl otherQueueControl = createManagementControl(SimpleString.of(testAddress + (i + 1)), queue.concat(Integer.toString(i + 1)), RoutingType.ANYCAST);
         assertMessageMetrics(queueControl, 1, durable);
         assertMessageMetrics(otherQueueControl, 0, durable);

         int moved = queueControl.moveMessages(null, queue.concat(Integer.toString(i + 1)).toString());
         assertEquals(1, moved);
         assertMessageMetrics(queueControl, 0, durable);
         assertMessageMetrics(otherQueueControl, 1, durable);
      }

      ClientConsumer consumer1 = session.createConsumer(queue.concat(Integer.toString(COUNT - 1)));
      message = consumer1.receive(1000);
      assertNotNull(message);
      message.acknowledge();
      System.out.println(message);
      assertEquals(testAddress + (COUNT - 1), message.getAddress());
      assertEquals(testAddress + (COUNT - 2), message.getStringProperty(Message.HDR_ORIGINAL_ADDRESS));
   }

   /**
    * <ol>
    * <li>send 2 message to queue</li>
    * <li>remove messages from queue using management method <em>with filter</em></li>
    * <li>check there is only one message to consume from queue</li>
    * </ol>
    */
   @TestTemplate
   public void testRemoveMessages() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      ClientMessage matchingMessage = session.createMessage(durable);
      matchingMessage.putLongProperty(key, matchingValue);
      producer.send(matchingMessage);
      ClientMessage unmatchingMessage = session.createMessage(durable);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(unmatchingMessage);

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 2, durable);

      // removed matching messages to otherQueue
      int removedMatchedMessagesCount = queueControl.removeMessages(key + " =" + matchingValue);
      assertEquals(1, removedMatchedMessagesCount);
      assertEquals(1, getMessageCount(queueControl));

      // consume the unmatched message from queue
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage m = consumer.receive(500);
      assertNotNull(m);
      assertEquals(unmatchingValue, m.getObjectProperty(key));

      m.acknowledge();

      // check there is no other message to consume:
      m = consumer.receiveImmediate();
      assertNull(m);

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testRemoveMessagesWithLimit() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      ClientMessage matchingMessage = session.createMessage(durable);
      matchingMessage.putLongProperty(key, matchingValue);
      producer.send(matchingMessage);
      ClientMessage unmatchingMessage = session.createMessage(durable);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(unmatchingMessage);

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 2, durable);

      // removed matching messages to otherQueue
      int removedMatchedMessagesCount = queueControl.removeMessages(5, key + " =" + matchingValue);
      assertEquals(1, removedMatchedMessagesCount);
      assertMessageMetrics(queueControl, 1, durable);

      // consume the unmatched message from queue
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage m = consumer.receive(500);
      assertNotNull(m);
      assertEquals(unmatchingValue, m.getObjectProperty(key));

      m.acknowledge();

      // check there is no other message to consume:
      m = consumer.receiveImmediate();
      assertNull(m);

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testRemoveMessagesWithNullFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      producer.send(session.createMessage(durable));
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 2, durable);

      // removed matching messages to otherQueue
      int removedMatchedMessagesCount = queueControl.removeMessages(null);
      assertEquals(2, removedMatchedMessagesCount);
      assertMessageMetrics(queueControl, 0, durable);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testRemoveAllMessages() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      producer.send(session.createMessage(durable));
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 2, durable);

      // removed matching messages to otherQueue
      int removedMatchedMessagesCount = queueControl.removeAllMessages();
      assertEquals(2, removedMatchedMessagesCount);
      assertMessageMetrics(queueControl, 0, durable);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testRemoveAllWithPagingMode() throws Exception {

      final int MESSAGE_SIZE = 1024 * 3; // 3k

      PagingManagerImplAccessor.resetMaxSize(server.getPagingManager(), 10240, 0);
      clearDataRecreateServerDirs();

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queueName = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(durable));

      Queue queue = server.locateQueue(queueName);
      assertFalse(queue.getPageSubscription().isPaging());

      ClientProducer producer = session.createProducer(address);

      byte[] body = new byte[MESSAGE_SIZE];

      ByteBuffer bb = ByteBuffer.wrap(body);

      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      final int numberOfMessages = 100;
      ClientMessage message;
      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);

         ActiveMQBuffer bodyLocal = message.getBodyBuffer();

         bodyLocal.writeBytes(body);

         producer.send(message);
      }

      assertTrue(queue.getPageSubscription().isPaging());

      QueueControl queueControl = createManagementControl(address, queueName);
      assertMessageMetrics(queueControl, numberOfMessages, durable);
      int removedMatchedMessagesCount = queueControl.removeAllMessages();
      assertEquals(numberOfMessages, removedMatchedMessagesCount);
      assertMessageMetrics(queueControl, 0, durable);

      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(queue));

      session.deleteQueue(queueName);
   }

   @TestTemplate
   public void testRemoveMessagesWithEmptyFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      producer.send(session.createMessage(durable));
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 2, durable);

      // removed matching messages to otherQueue
      int removedMatchedMessagesCount = queueControl.removeMessages("");
      assertEquals(2, removedMatchedMessagesCount);
      assertMessageMetrics(queueControl, 0, durable);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testRemoveMessage() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send 2 messages on queue
      producer.send(session.createMessage(durable));
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 2, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(2, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      // delete 1st message
      boolean deleted = queueControl.removeMessage(messageID);
      assertTrue(deleted);
      assertMessageMetrics(queueControl, 1, durable);

      // check there is a single message to consume from queue
      consumeMessages(1, session, queue);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testRemoveScheduledMessage() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send 2 messages on queue, both scheduled
      long timeout = System.currentTimeMillis() + 5000;
      ClientMessage m1 = session.createMessage(durable);
      m1.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, timeout);
      producer.send(m1);
      ClientMessage m2 = session.createMessage(durable);
      m2.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, timeout);
      producer.send(m2);

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(2, queueControl.getScheduledCount());

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listScheduledMessages();
      assertEquals(2, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      // delete 1st message
      boolean deleted = queueControl.removeMessage(messageID);
      assertTrue(deleted);
      assertScheduledMetrics(queueControl, 1, durable);

      // check there is a single message to consume from queue
      while (timeout > System.currentTimeMillis() && queueControl.getScheduledCount() == 1) {
         Thread.sleep(100);
      }

      consumeMessages(1, session, queue);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testRemoveScheduledMessageRestart() throws Exception {
      assumeTrue(durable);
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send 2 messages on queue, both scheduled
      long timeout = System.currentTimeMillis() + 5000;
      ClientMessage m1 = session.createMessage(durable);
      m1.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, timeout);
      producer.send(m1);
      ClientMessage m2 = session.createMessage(durable);
      m2.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, timeout);
      producer.send(m2);

      QueueControl queueControl = createManagementControl(address, queue);
      assertScheduledMetrics(queueControl, 2, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listScheduledMessages();
      assertEquals(2, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      // delete 1st message
      boolean deleted = queueControl.removeMessage(messageID);
      assertTrue(deleted);
      assertScheduledMetrics(queueControl, 1, durable);

      locator.close();
      server.stop();
      server.start();

      assertScheduledMetrics(queueControl, 1, durable);
   }

   @TestTemplate
   public void testRemoveMessage2() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send messages on queue

      for (int i = 0; i < 100; i++) {

         ClientMessage msg = session.createMessage(durable);
         msg.putIntProperty("count", i);
         producer.send(msg);
      }

      ClientConsumer cons = session.createConsumer(queue);
      session.start();
      LinkedList<ClientMessage> msgs = new LinkedList<>();
      for (int i = 0; i < 50; i++) {
         ClientMessage msg = cons.receive(1000);
         msgs.add(msg);
      }

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 100, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(50, messages.length);
      int i = Integer.parseInt((messages[0].get("count")).toString());
      assertEquals(50, i);
      long messageID = (Long) messages[0].get("messageID");

      // delete 1st message
      boolean deleted = queueControl.removeMessage(messageID);
      assertTrue(deleted);
      assertMessageMetrics(queueControl, 99, durable);

      cons.close();

      // check there is a single message to consume from queue
      consumeMessages(99, session, queue);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCountDeliveringMessageCountWithFilter() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomPositiveLong();
      long unmatchingValue = matchingValue + 1;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      ClientProducer producer = session.createProducer(address);

      ClientMessage matchingMessage = session.createMessage(false);
      matchingMessage.putLongProperty(key, matchingValue);
      ClientMessage unmatchingMessage = session.createMessage(false);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(matchingMessage);
      producer.send(unmatchingMessage);
      producer.send(matchingMessage);

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.countDeliveringMessages(null));
      assertEquals(0, queueControl.countDeliveringMessages(key + " =" + matchingValue));
      assertEquals(0, queueControl.countDeliveringMessages(key + " =" + unmatchingValue));

      ClientConsumer consumer = session.createConsumer(queue, null, 1024 * 1024, 1, false);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);
      assertEquals(3, queueControl.countDeliveringMessages(null));
      assertEquals(2, queueControl.countDeliveringMessages(key + " =" + matchingValue));
      assertEquals(1, queueControl.countDeliveringMessages(key + " =" + unmatchingValue));

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCountDeliveringMessageCountNoFilter() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      ClientProducer producer = session.createProducer(address);

      ClientMessage matchingMessage = session.createMessage(false);
      matchingMessage.putLongProperty(key, matchingValue);
      ClientMessage unmatchingMessage = session.createMessage(false);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(matchingMessage);
      producer.send(unmatchingMessage);
      producer.send(matchingMessage);

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.countDeliveringMessages(null));

      ClientConsumer consumer = session.createConsumer(queue, null, 1024 * 1024, 1, false);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);
      assertEquals(3, queueControl.countDeliveringMessages(null));

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCountDeliveringMessageCountNoGroupNoFilter() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      ClientProducer producer = session.createProducer(address);

      ClientMessage matchingMessage = session.createMessage(false);
      matchingMessage.putLongProperty(key, matchingValue);
      ClientMessage unmatchingMessage = session.createMessage(false);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(matchingMessage);
      producer.send(unmatchingMessage);
      producer.send(matchingMessage);
      session.commit();

      QueueControl queueControl = createManagementControl(address, queue);
      String result = queueControl.countDeliveringMessages(null, null);
      JsonObject jsonObject = JsonUtil.readJsonObject(result);
      assertEquals(0, jsonObject.getInt("null"));

      ClientConsumer consumer = session.createConsumer(queue, null, 1024 * 1024, 1, false);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);

      result = queueControl.countDeliveringMessages(null, null);
      jsonObject = JsonUtil.readJsonObject(result);
      assertEquals(3, jsonObject.getInt("null"));

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCountDeliveringMessageCountGroupNoFilter() throws Exception {
      String key = new String("key_group");
      String valueGroup1 = "group_1";
      String valueGroup2 = "group_2";

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      ClientProducer producer = session.createProducer(address);

      ClientMessage message1 = session.createMessage(false);
      message1.putStringProperty(key, valueGroup1);
      ClientMessage message2 = session.createMessage(false);
      message2.putStringProperty(key, valueGroup2);
      producer.send(message1);
      producer.send(message2);
      producer.send(message1);

      QueueControl queueControl = createManagementControl(address, queue);
      String result = queueControl.countDeliveringMessages(null, key);
      JsonObject jsonObject = JsonUtil.readJsonObject(result);
      assertTrue(jsonObject.isEmpty());

      ClientConsumer consumer = session.createConsumer(queue, null, 1024 * 1024, 1, false);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);

      result = queueControl.countDeliveringMessages(null, key);
      jsonObject = JsonUtil.readJsonObject(result);
      assertEquals(2, jsonObject.getInt(valueGroup1));
      assertEquals(1, jsonObject.getInt(valueGroup2));
      assertFalse(jsonObject.containsKey(null));

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCountDeliveringMessageCountGroupFilter() throws Exception {
      String key = new String("key_group");
      long valueGroup1 = RandomUtil.randomLong();
      long valueGroup2 = valueGroup1 + 1;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      ClientProducer producer = session.createProducer(address);

      ClientMessage message1 = session.createMessage(false);
      message1.putLongProperty(key, valueGroup1);
      ClientMessage message2 = session.createMessage(false);
      message2.putLongProperty(key, valueGroup2);
      producer.send(message1);
      producer.send(message2);
      producer.send(message1);
      session.commit();

      QueueControl queueControl = createManagementControl(address, queue);
      String result = queueControl.countDeliveringMessages(key + " =" + valueGroup1, key);
      JsonObject jsonObject = JsonUtil.readJsonObject(result);
      assertTrue(jsonObject.isEmpty());

      ClientConsumer consumer = session.createConsumer(queue, null, 1024 * 1024, 1, false);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);

      result = queueControl.countDeliveringMessages(key + " =" + valueGroup1, key);
      jsonObject = JsonUtil.readJsonObject(result);
      assertEquals(2, jsonObject.getInt(String.valueOf(valueGroup1)));
      assertFalse(jsonObject.containsKey(String.valueOf(valueGroup2)));
      assertFalse(jsonObject.containsKey(null));

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCountMessagesWithFilter() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      ClientMessage matchingMessage = session.createMessage(durable);
      matchingMessage.putLongProperty(key, matchingValue);
      ClientMessage unmatchingMessage = session.createMessage(durable);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(matchingMessage);
      producer.send(unmatchingMessage);
      producer.send(matchingMessage);

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(3, getMessageCount(queueControl));
      assertMessageMetrics(queueControl, 3, durable);

      assertEquals(2, queueControl.countMessages(key + " =" + matchingValue));
      assertEquals(1, queueControl.countMessages(key + " =" + unmatchingValue));

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCountMessagesWithInvalidFilter() throws Exception {
      SimpleString key = SimpleString.of("key");
      String matchingValue = "MATCH";
      String nonMatchingValue = "DIFFERENT";

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      for (int i = 0; i < 100; i++) {
         ClientMessage msg = session.createMessage(durable);
         msg.putStringProperty(key, SimpleString.of(matchingValue));
         producer.send(msg);
      }

      for (int i = 0; i < 10; i++) {
         ClientMessage msg = session.createMessage(durable);
         msg.putStringProperty(key, SimpleString.of(nonMatchingValue));
         producer.send(msg);
      }

      // this is just to guarantee a round trip and avoid in transit messages, so they are all accounted for
      session.commit();

      ClientConsumer consumer = session.createConsumer(queue, SimpleString.of("nonExistentProperty like \'%Temp/88\'"));

      session.start();

      assertNull(consumer.receiveImmediate());

      QueueControl queueControl = createManagementControl(address, queue);
      assertMessageMetrics(queueControl, 110, durable);

      assertEquals(0, queueControl.countMessages("nonExistentProperty like \'%Temp/88\'"));

      assertEquals(100, queueControl.countMessages(key + "=\'" + matchingValue + "\'"));
      assertEquals(10, queueControl.countMessages(key + " = \'" + nonMatchingValue + "\'"));

      consumer.close();

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCountMessagesPropertyExist() throws Exception {
      String key = new String("key_group");
      String valueGroup1 = "group_1";
      String valueGroup2 = "group_2";

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      ClientProducer producer = session.createProducer(address);

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = session.createMessage(false);
         if (i % 3 == 0) {
            msg.putStringProperty(key, valueGroup1);
         } else {
            msg.putStringProperty(key, valueGroup2);
         }
         producer.send(msg);
      }

      for (int i = 0; i < 20; i++) {
         ClientMessage msg = session.createMessage(false);
         producer.send(msg);
      }

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(120, getMessageCount(queueControl));
      String result = queueControl.countMessages(null, key);
      JsonObject jsonObject = JsonUtil.readJsonObject(result);
      assertEquals(34, jsonObject.getInt(valueGroup1));
      assertEquals(66, jsonObject.getInt(valueGroup2));
      assertEquals(20, jsonObject.getInt("null"));
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testCountMessagesPropertyWithNullFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      ClientProducer producer = session.createProducer(address);

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = session.createMessage(false);
         msg.putStringProperty(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString());
         producer.send(msg);
      }

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(100, getMessageCount(queueControl));
      String result = queueControl.countMessages(null, null);
      JsonObject jsonObject = JsonUtil.readJsonObject(result);
      assertEquals(100, jsonObject.getInt("null"));
      session.deleteQueue(queue);
   }


   @TestTemplate
   public void testExpireMessagesWithFilter() throws Exception {
      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      ClientMessage matchingMessage = session.createMessage(durable);
      matchingMessage.putLongProperty(key, matchingValue);
      producer.send(matchingMessage);
      ClientMessage unmatchingMessage = session.createMessage(durable);
      unmatchingMessage.putLongProperty(key, unmatchingValue);
      producer.send(unmatchingMessage);

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(2, getMessageCount(queueControl));

      int expiredMessagesCount = queueControl.expireMessages(key + " =" + matchingValue);
      assertEquals(1, expiredMessagesCount);
      assertMessageMetrics(queueControl, 1, durable);

      // consume the unmatched message from queue
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage m = consumer.receive(500);
      assertNotNull(m);
      assertEquals(unmatchingValue, m.getObjectProperty(key));

      m.acknowledge();

      // check there is no other message to consume:
      m = consumer.receiveImmediate();
      assertNull(m);

      consumer.close();
      session.deleteQueue(queue);
      session.close();
   }

   @TestTemplate
   public void testExpireMessage() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString expiryAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString expiryQueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      session.createQueue(QueueConfiguration.of(expiryQueue).setAddress(expiryAddress).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send on queue
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      QueueControl expiryQueueControl = createManagementControl(expiryAddress, expiryQueue);
      assertMessageMetrics(queueControl, 1, durable);
      assertMessageMetrics(expiryQueueControl, 0, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(1, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      AddressSettings addressSettings = new AddressSettings().setExpiryAddress(expiryAddress);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      boolean expired = queueControl.expireMessage(messageID);
      assertTrue(expired);
      Thread.sleep(200);
      assertMessageMetrics(queueControl, 0, durable);
      assertMessageMetrics(expiryQueueControl, 1, durable);

      consumeMessages(0, session, queue);
      consumeMessages(1, session, expiryQueue);

      session.deleteQueue(queue);
      session.deleteQueue(expiryQueue);
      session.close();
   }

   @TestTemplate
   public void testSendMessageToDeadLetterAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString deadLetterAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString deadLetterQueue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      session.createQueue(QueueConfiguration.of(deadLetterQueue).setAddress(deadLetterAddress).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      // send 2 messages on queue
      producer.send(session.createMessage(durable));
      producer.send(session.createMessage(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      QueueControl deadLetterQueueControl = createManagementControl(deadLetterAddress, deadLetterQueue);
      assertMessageMetrics(queueControl, 2, durable);

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(2, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      AddressSettings addressSettings = new AddressSettings().setDeadLetterAddress(deadLetterAddress);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      assertEquals(0, getMessageCount(deadLetterQueueControl));
      boolean movedToDeadLetterAddress = queueControl.sendMessageToDeadLetterAddress(messageID);
      assertTrue(movedToDeadLetterAddress);
      assertMessageMetrics(queueControl, 1, durable);
      Thread.sleep(200);
      assertMessageMetrics(deadLetterQueueControl, 1, durable);

      // check there is a single message to consume from queue
      consumeMessages(1, session, queue);

      // check there is a single message to consume from deadletter queue
      consumeMessages(1, session, deadLetterQueue);

      session.deleteQueue(queue);
      session.deleteQueue(deadLetterQueue);
   }

   @TestTemplate
   public void testChangeMessagePriority() throws Exception {
      byte originalPriority = (byte) 1;
      byte newPriority = (byte) 8;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      ClientMessage message = session.createMessage(durable);
      message.setPriority(originalPriority);
      producer.send(message);

      QueueControl queueControl = createManagementControl(address, queue);
      Wait.assertEquals(1, () -> getMessageCount(queueControl));

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(1, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      boolean priorityChanged = queueControl.changeMessagePriority(messageID, newPriority);
      assertTrue(priorityChanged);

      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage m = consumer.receive(500);
      assertNotNull(m);
      assertEquals(newPriority, m.getPriority());

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testChangeMessagePriorityWithInvalidValue() throws Exception {
      byte invalidPriority = (byte) 23;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = session.createProducer(address);

      ClientMessage message = session.createMessage(durable);
      producer.send(message);

      QueueControl queueControl = createManagementControl(address, queue);
      Wait.assertEquals(1, () -> getMessageCount(queueControl));

      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(1, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      try {
         queueControl.changeMessagePriority(messageID, invalidPriority);
         fail("operation fails when priority value is < 0 or > 9");
      } catch (Exception e) {
      }

      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage m = consumer.receive(500);
      assertNotNull(m);
      assertTrue(invalidPriority != m.getPriority());

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListMessageCounter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ActiveMQServerControl serverControl = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      serverControl.enableMessageCounters();
      serverControl.setMessageCounterSamplePeriod(99999);

      String jsonString = queueControl.listMessageCounter();
      MessageCounterInfo info = MessageCounterInfo.fromJSON(jsonString);

      assertEquals(0, info.getDepth());
      assertEquals(0, info.getCount());

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));
      Wait.assertTrue(() -> server.locateQueue(queue).getMessageCount() == 1);

      ((MessageCounterManagerImpl)server.getManagementService().getMessageCounterManager()).getMessageCounter(queue.toString()).onTimer();
      Thread.sleep(50);
      jsonString = queueControl.listMessageCounter();
      info = MessageCounterInfo.fromJSON(jsonString);
      assertEquals(1, info.getDepth());
      assertEquals(1, info.getDepthDelta());
      assertEquals(1, info.getCount());
      assertEquals(1, info.getCountDelta());
      assertEquals(info.getUpdateTimestamp(), info.getLastAddTimestamp());
      assertEquals(NULL_DATE, info.getLastAckTimestamp()); // no acks received yet

      producer.send(session.createMessage(durable));
      Wait.assertTrue(() -> server.locateQueue(queue).getMessageCount() == 2);

      ((MessageCounterManagerImpl)server.getManagementService().getMessageCounterManager()).getMessageCounter(queue.toString()).onTimer();
      Thread.sleep(50);
      jsonString = queueControl.listMessageCounter();
      info = MessageCounterInfo.fromJSON(jsonString);
      assertEquals(2, info.getDepth());
      assertEquals(1, info.getDepthDelta());
      assertEquals(2, info.getCount());
      assertEquals(1, info.getCountDelta());
      assertEquals(info.getUpdateTimestamp(), info.getLastAddTimestamp());
      assertEquals(NULL_DATE, info.getLastAckTimestamp()); // no acks received yet

      consumeMessages(2, session, queue);

      ((MessageCounterManagerImpl)server.getManagementService().getMessageCounterManager()).getMessageCounter(queue.toString()).onTimer();
      Thread.sleep(50);
      jsonString = queueControl.listMessageCounter();
      info = MessageCounterInfo.fromJSON(jsonString);
      assertEquals(0, info.getDepth());
      assertEquals(-2, info.getDepthDelta());
      assertEquals(2, info.getCount());
      assertEquals(0, info.getCountDelta());
      assertEquals(info.getUpdateTimestamp(), info.getLastAckTimestamp());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testResetMessageCounter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ActiveMQServerControl serverControl = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      serverControl.enableMessageCounters();
      serverControl.setMessageCounterSamplePeriod(MessageCounterManagerImpl.MIN_SAMPLE_PERIOD);

      String jsonString = queueControl.listMessageCounter();
      MessageCounterInfo info = MessageCounterInfo.fromJSON(jsonString);

      assertEquals(0, info.getDepth());
      assertEquals(0, info.getCount());

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));

      Thread.sleep(MessageCounterManagerImpl.MIN_SAMPLE_PERIOD * 2);
      jsonString = queueControl.listMessageCounter();
      info = MessageCounterInfo.fromJSON(jsonString);
      assertEquals(1, info.getDepth());
      assertEquals(1, info.getDepthDelta());
      assertEquals(1, info.getCount());
      assertEquals(1, info.getCountDelta());

      consumeMessages(1, session, queue);

      Thread.sleep(MessageCounterManagerImpl.MIN_SAMPLE_PERIOD * 2);
      jsonString = queueControl.listMessageCounter();
      info = MessageCounterInfo.fromJSON(jsonString);
      assertEquals(0, info.getDepth());
      assertEquals(-1, info.getDepthDelta());
      assertEquals(1, info.getCount());
      assertEquals(0, info.getCountDelta());

      queueControl.resetMessageCounter();

      Thread.sleep(MessageCounterManagerImpl.MIN_SAMPLE_PERIOD * 2);
      jsonString = queueControl.listMessageCounter();
      info = MessageCounterInfo.fromJSON(jsonString);
      assertEquals(0, info.getDepth());
      assertEquals(0, info.getDepthDelta());
      assertEquals(0, info.getCount());
      assertEquals(0, info.getCountDelta());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListMessageCounterAsHTML() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      String history = queueControl.listMessageCounterAsHTML();
      assertNotNull(history);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListMessageCounterHistory() throws Exception {
      long counterPeriod = 1000;
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ActiveMQServerControl serverControl = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      serverControl.enableMessageCounters();
      serverControl.setMessageCounterSamplePeriod(counterPeriod);

      String jsonString = queueControl.listMessageCounterHistory();
      DayCounterInfo[] infos = DayCounterInfo.fromJSON(jsonString);
      assertEquals(1, infos.length);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testListMessageCounterHistoryAsHTML() throws Exception {
      long counterPeriod = 1000;
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ActiveMQServerControl serverControl = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      serverControl.enableMessageCounters();
      serverControl.setMessageCounterSamplePeriod(counterPeriod);

      String history = queueControl.listMessageCounterHistoryAsHTML();
      assertNotNull(history);

      session.deleteQueue(queue);

   }

   @TestTemplate
   public void testMoveMessagesBack() throws Exception {
      server.createQueue(QueueConfiguration.of("q1").setRoutingType(RoutingType.MULTICAST).setDurable(durable));
      server.createQueue(QueueConfiguration.of("q2").setRoutingType(RoutingType.MULTICAST).setDurable(durable));

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, true);

      ClientProducer prod1 = session.createProducer("q1");

      for (int i = 0; i < 10; i++) {
         ClientMessage msg = session.createMessage(durable);

         msg.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, SimpleString.of("dupl-" + i));

         prod1.send(msg);
      }

      session.commit();

      ClientConsumer consumer = session.createConsumer("q1", true);
      session.start();

      assertNotNull(consumer.receive(5000));
      consumer.close();

      QueueControl q1Control = ManagementControlHelper.createQueueControl(SimpleString.of("q1"), SimpleString.of("q1"), mbeanServer);

      QueueControl q2Control = ManagementControlHelper.createQueueControl(SimpleString.of("q2"), SimpleString.of("q2"), mbeanServer);

      assertEquals(10, q1Control.moveMessages(null, "q2"));

      consumer = session.createConsumer("q2", true);

      assertNotNull(consumer.receive(500));

      consumer.close();

      q2Control.moveMessages(null, "q1", false);

      session.start();
      consumer = session.createConsumer("q1");

      for (int i = 0; i < 10; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      consumer.close();

      session.deleteQueue("q1");

      session.deleteQueue("q2");

      session.close();

      locator.close();

   }

   @TestTemplate
   public void testMoveMessagesBack2() throws Exception {
      server.createQueue(QueueConfiguration.of("q1").setRoutingType(RoutingType.MULTICAST).setDurable(durable));
      server.createQueue(QueueConfiguration.of("q2").setRoutingType(RoutingType.MULTICAST).setDurable(durable));

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, true);

      ClientProducer prod1 = session.createProducer("q1");

      int NUMBER_OF_MSGS = 10;

      for (int i = 0; i < NUMBER_OF_MSGS; i++) {
         ClientMessage msg = session.createMessage(durable);

         msg.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, SimpleString.of("dupl-" + i));

         prod1.send(msg);
      }

      session.commit();

      ClientConsumer consumer = session.createConsumer("q1", true);
      session.start();

      assertNotNull(consumer.receive(5000));
      consumer.close();

      QueueControl q1Control = ManagementControlHelper.createQueueControl(SimpleString.of("q1"), SimpleString.of("q1"), mbeanServer);

      QueueControl q2Control = ManagementControlHelper.createQueueControl(SimpleString.of("q2"), SimpleString.of("q2"), mbeanServer);

      assertEquals(NUMBER_OF_MSGS, q1Control.moveMessages(null, "q2"));

      long[] messageIDs = new long[NUMBER_OF_MSGS];

      consumer = session.createConsumer("q2", true);

      for (int i = 0; i < NUMBER_OF_MSGS; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         messageIDs[i] = msg.getMessageID();
      }

      assertNull(consumer.receiveImmediate());

      consumer.close();

      for (int i = 0; i < NUMBER_OF_MSGS; i++) {
         q2Control.moveMessage(messageIDs[i], "q1");
      }

      session.start();
      consumer = session.createConsumer("q1");

      for (int i = 0; i < NUMBER_OF_MSGS; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      consumer.close();

      session.deleteQueue("q1");

      session.deleteQueue("q2");

      session.close();
   }

   @TestTemplate
   public void testPauseAndResume() throws Exception {
      long counterPeriod = 1000;
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ActiveMQServerControl serverControl = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      serverControl.enableMessageCounters();
      serverControl.setMessageCounterSamplePeriod(counterPeriod);
      assertFalse(queueControl.isPaused());
      assertFalse(queueControl.isPersistedPause());
      queueControl.pause();
      assertTrue(queueControl.isPaused());
      assertFalse(queueControl.isPersistedPause());
      queueControl.resume();
      assertFalse(queueControl.isPaused());
      assertFalse(queueControl.isPersistedPause());
   }

   @TestTemplate
   public void testPersistedPauseAndResume() throws Exception {
      assumeTrue(durable);
      long counterPeriod = 1000;
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));
      QueueControl queueControl = createManagementControl(address, queue);

      ActiveMQServerControl serverControl = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      serverControl.enableMessageCounters();
      serverControl.setMessageCounterSamplePeriod(counterPeriod);
      assertFalse(queueControl.isPaused());
      queueControl.pause(true);
      assertTrue(queueControl.isPaused());
      assertTrue(queueControl.isPersistedPause());
      server.stop();
      server.start();
      queueControl = createManagementControl(address, queue);
      assertTrue(queueControl.isPaused());
      assertTrue(queueControl.isPersistedPause());
      queueControl.resume();
      assertFalse(queueControl.isPaused());
      assertFalse(queueControl.isPersistedPause());
   }

   @TestTemplate
   public void testResetMessagesAdded() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, getMessagesAdded(queueControl));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));
      Wait.assertEquals(1, () -> getMessagesAdded(queueControl));
      producer.send(session.createMessage(durable));
      Wait.assertEquals(2, () -> getMessagesAdded(queueControl));

      consumeMessages(2, session, queue);

      Wait.assertEquals(2, () -> getMessagesAdded(queueControl));

      queueControl.resetMessagesAdded();

      Wait.assertEquals(0, () -> getMessagesAdded(queueControl));

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testResetMessagesAcknowledged() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.getMessagesAcknowledged());

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));
      consumeMessages(1, session, queue);
      assertEquals(1, queueControl.getMessagesAcknowledged());
      producer.send(session.createMessage(durable));
      consumeMessages(1, session, queue);
      assertEquals(2, queueControl.getMessagesAcknowledged());

      queueControl.resetMessagesAcknowledged();

      assertEquals(0, queueControl.getMessagesAcknowledged());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testResetMessagesExpired() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.getMessagesExpired());

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(durable);
      producer.send(message);

      // the message IDs are set on the server
      Map<String, Object>[] messages;
      Wait.assertEquals(1, () -> queueControl.listMessages(null).length);
      messages = queueControl.listMessages(null);
      long messageID = (Long) messages[0].get("messageID");

      queueControl.expireMessage(messageID);
      assertEquals(1, queueControl.getMessagesExpired());

      message = session.createMessage(durable);
      producer.send(message);

      Queue serverqueue = server.locateQueue(queue);

      Wait.assertEquals(1, serverqueue::getMessageCount);

      // the message IDs are set on the server
      messages = queueControl.listMessages(null);
      assertEquals(1, messages.length);
      messageID = (Long) messages[0].get("messageID");

      queueControl.expireMessage(messageID);
      assertEquals(2, queueControl.getMessagesExpired());

      queueControl.resetMessagesExpired();

      assertEquals(0, queueControl.getMessagesExpired());

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testResetMessagesKilled() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.getMessagesExpired());

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(durable);
      producer.send(message);

      Wait.assertEquals(1, queueControl::getMessageCount);
      // the message IDs are set on the server
      Map<String, Object>[] messages = queueControl.listMessages(null);
      assertEquals(1, messages.length);
      long messageID = (Long) messages[0].get("messageID");

      queueControl.sendMessageToDeadLetterAddress(messageID);
      assertEquals(1, queueControl.getMessagesKilled());
      assertMessageMetrics(queueControl, 0, durable);

      message = session.createMessage(false);
      producer.send(message);

      // send to DLA the old-fashioned way
      ClientConsumer consumer = session.createConsumer(queue);
      for (int i = 0; i < server.getAddressSettingsRepository().getMatch(queue.toString()).getMaxDeliveryAttempts(); i++) {
         message = consumer.receive(500);
         assertNotNull(message);
         message.acknowledge();
         session.rollback();
      }

      consumer.close();

      assertEquals(2, queueControl.getMessagesKilled());

      queueControl.resetMessagesKilled();

      assertEquals(0, queueControl.getMessagesKilled());

      session.deleteQueue(queue);
   }

   //make sure notifications are always received no matter whether
   //a Queue is created via QueueControl or by JMSServerManager directly.
   @TestTemplate
   public void testCreateQueueNotification() throws Exception {
      JMSUtil.JMXListener listener = new JMSUtil.JMXListener();
      this.mbeanServer.addNotificationListener(ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName(), listener, null, null);

      SimpleString testQueueName = SimpleString.of("newQueue");
      String testQueueName2 = "newQueue2";
      this.server.createQueue(QueueConfiguration.of(testQueueName).setDurable(durable));

      Notification notif = listener.getNotification();

      assertEquals(CoreNotificationType.BINDING_ADDED.toString(), notif.getType());

      this.server.destroyQueue(testQueueName);

      notif = listener.getNotification();
      assertEquals(CoreNotificationType.BINDING_REMOVED.toString(), notif.getType());

      ActiveMQServerControl control = ManagementControlHelper.createActiveMQServerControl(mbeanServer);

      control.createQueue(QueueConfiguration.of(testQueueName2).setRoutingType(RoutingType.MULTICAST).toJSON());

      notif = listener.getNotification();
      assertEquals(CoreNotificationType.BINDING_ADDED.toString(), notif.getType());

      control.destroyQueue(testQueueName2);

      notif = listener.getNotification();
      assertEquals(CoreNotificationType.BINDING_REMOVED.toString(), notif.getType());
   }

   @TestTemplate
   public void testSendMessage() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      queueControl.sendMessage(new HashMap<>(), Message.BYTES_TYPE, Base64.encodeBytes("theBody".getBytes()), true, "myUser", "myPassword");
      queueControl.sendMessage(null, Message.BYTES_TYPE, Base64.encodeBytes("theBody".getBytes()), true, "myUser", "myPassword");

      Wait.assertEquals(2, () -> getMessageCount(queueControl));

      // the message IDs are set on the server
      CompositeData[] browse = queueControl.browse(null);

      assertEquals(2, browse.length);

      byte[] body = (byte[]) browse[0].get(BODY);

      assertNotNull(body);

      assertEquals("theBody", new String(body));

      body = (byte[]) browse[1].get(BODY);

      assertNotNull(body);

      assertEquals("theBody", new String(body));
   }


   @TestTemplate
   public void testSendMessageWithAMQP() throws Exception {
      SimpleString address = SimpleString.of("address_testSendMessageWithAMQP");
      SimpleString queue = SimpleString.of("queue_testSendMessageWithAMQP");

      server.addAddressInfo(new AddressInfo(address).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server.locateQueue(queue) != null && server.getAddressInfo(address) != null);

      QueueControl queueControl = createManagementControl(address, queue, RoutingType.ANYCAST);

      { // a namespace
         ConnectionFactory factory = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
         try (Connection connection = factory.createConnection("myUser", "myPassword")) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer =  session.createProducer(session.createQueue(address.toString()));
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            TextMessage message = session.createTextMessage("theAMQPBody");
            message.setStringProperty("protocolUsed", "amqp");
            producer.send(message);
         }
      }

      { // a namespace
         ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
         try (Connection connection = factory.createConnection("myUser", "myPassword")) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer =  session.createProducer(session.createQueue(address.toString()));
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            TextMessage message = session.createTextMessage("theCoreBody");
            message.setStringProperty("protocolUsed", "core");
            producer.send(message);
         }
      }

      Wait.assertEquals(2L, () -> getMessageCount(queueControl), 2000, 100);

      // the message IDs are set on the server
      CompositeData[] browse = queueControl.browse(null);

      assertEquals(2, browse.length);

      String userID = (String) browse[0].get("userID");

      assertTrue(userID.startsWith("ID:"));

      assertFalse(userID.startsWith("ID:ID:"));

      String body = (String) browse[0].get("text");

      assertNotNull(body);

      assertEquals("theAMQPBody", body);

      userID = (String) browse[1].get("userID");

      assertTrue(userID.startsWith("ID:"));

      assertFalse(userID.startsWith("ID:ID:"));

      body = (String) browse[1].get("text");

      assertNotNull(body);

      assertEquals("theCoreBody", body);

   }


   @TestTemplate
   public void testSendMessageWithAMQPLarge() throws Exception {
      SimpleString address = SimpleString.of("address_testSendMessageWithAMQP");
      SimpleString queue = SimpleString.of("queue_testSendMessageWithAMQP");

      server.addAddressInfo(new AddressInfo(address).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable).setRoutingType(RoutingType.ANYCAST));

      Wait.assertTrue(() -> server.locateQueue(queue) != null && server.getAddressInfo(address) != null);

      QueueControl queueControl = createManagementControl(address, queue, RoutingType.ANYCAST);

      final String text = "*-".repeat(100 * 1024);

      { // a namespace
         ConnectionFactory factory = CFUtil.createConnectionFactory("amqp", "tcp://localhost:61616");
         try (Connection connection = factory.createConnection("myUser", "myPassword")) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer =  session.createProducer(session.createQueue(address.toString()));
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            TextMessage message = session.createTextMessage(text);
            message.setStringProperty("protocolUsed", "amqp");
            producer.send(message);
         }
      }

      { // a namespace
         ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
         try (Connection connection = factory.createConnection("myUser", "myPassword")) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer =  session.createProducer(session.createQueue(address.toString()));
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            TextMessage message = session.createTextMessage(text);
            message.setStringProperty("protocolUsed", "core");
            producer.send(message);
         }
      }

      Wait.assertEquals(2L, () -> getMessageCount(queueControl), 2000, 100);

      // the message IDs are set on the server
      CompositeData[] browse = queueControl.browse(null);

      assertEquals(2, browse.length);

      String body = (String) browse[0].get("text");

      assertNotNull(body);

      body = (String) browse[1].get("text");

      assertNotNull(body);

   }

   @TestTemplate
   public void testSendMessageWithMessageId() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      queueControl.sendMessage(new HashMap<>(), Message.BYTES_TYPE, Base64.encodeBytes("theBody".getBytes()), true, "myUser", "myPassword");
      queueControl.sendMessage(null, Message.BYTES_TYPE, Base64.encodeBytes("theBody".getBytes()), true, "myUser", "myPassword", true);

      Wait.assertEquals(2, () -> getMessageCount(queueControl));

      // the message IDs are set on the server
      CompositeData[] browse = queueControl.browse(null);

      assertEquals(2, browse.length);

      byte[] body = (byte[]) browse[0].get(BODY);

      String messageID = (String) browse[0].get("userID");

      assertEquals(0, messageID.length());

      assertNotNull(body);

      assertEquals("theBody", new String(body));

      body = (byte[]) browse[1].get(BODY);

      messageID = (String) browse[1].get("userID");

      assertFalse(messageID.isEmpty());

      assertNotNull(body);

      assertEquals("theBody", new String(body));
   }

   @TestTemplate
   public void testSendMessageToQueueWithFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable).setFilterString("foo = 'foo'"));

      QueueControl queueControl = createManagementControl(address, queue);

      queueControl.sendMessage(new HashMap<>(), Message.BYTES_TYPE, Base64.encodeBytes("theBody".getBytes()), true, "myUser", "myPassword");
      queueControl.sendMessage(Map.of("foo", "foo"), Message.BYTES_TYPE, Base64.encodeBytes("theBody".getBytes()), true, "myUser", "myPassword");

      Wait.assertEquals(1, () -> getMessageCount(queueControl));
   }

   @TestTemplate
   public void testSendMessageWithProperties() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      Map<String, String> headers = new HashMap<>();
      headers.put("myProp1", "myValue1");
      headers.put("myProp2", "myValue2");
      queueControl.sendMessage(headers, Message.BYTES_TYPE, Base64.encodeBytes("theBody".getBytes()), true, "myUser", "myPassword");
      queueControl.sendMessage(null, Message.BYTES_TYPE, Base64.encodeBytes("theBody".getBytes()), true, "myUser", "myPassword");

      Wait.assertEquals(2, () -> getMessageCount(queueControl));

      // the message IDs are set on the server
      CompositeData[] browse = queueControl.browse(null);

      assertEquals(2, browse.length);

      byte[] body = (byte[]) browse[0].get(BODY);

      for (Object prop : ((TabularDataSupport)browse[0].get(STRING_PROPERTIES)).values()) {
         CompositeDataSupport cds = (CompositeDataSupport) prop;
         assertTrue(headers.containsKey(cds.get("key")));
         assertTrue(headers.containsValue(cds.get("value")));
      }

      assertNotNull(body);

      assertEquals("theBody", new String(body));

      body = (byte[]) browse[1].get(BODY);

      assertNotNull(body);

      assertEquals("theBody", new String(body));
   }

   @TestTemplate
   public void testBrowseLimitOnListBrowseAndFilteredCount() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      AddressSettings addressSettings = new AddressSettings().setManagementBrowsePageSize(5);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      ClientProducer producer = session.createProducer(address);
      for (int i = 0; i < 10; i++) {
         producer.send(session.createMessage(true));
      }
      producer.close();

      QueueControl queueControl = createManagementControl(address, queue);


      // no filter, delegates to count metric
      Wait.assertEquals(10, queueControl::getMessageCount);

      assertEquals(5, queueControl.browse().length);
      assertEquals(5, queueControl.listMessages("").length);

      JsonArray array = JsonUtil.readJsonArray(queueControl.listMessagesAsJSON(""));
      assertEquals(5, array.size());

      // filer could match all
      assertEquals(10, queueControl.countMessages("AMQSize > 0"));

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testBrowseWithNullPropertyValue() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      ClientProducer producer = session.createProducer(address);
      ClientMessage m = session.createMessage(true);
      m.putStringProperty(RandomUtil.randomUUIDString(), null);
      producer.send(m);
      producer.close();

      QueueControl queueControl = createManagementControl(address, queue);

      assertEquals(1, queueControl.browse().length);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testBrowseWithNullPropertyValueWithAMQP() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable).setRoutingType(RoutingType.ANYCAST));

      JmsConnectionFactory cf = new JmsConnectionFactory("amqp://localhost:61616");
      Connection c = cf.createConnection();
      Session s = c.createSession();
      MessageProducer p = s.createProducer(s.createQueue(address.toString()));
      javax.jms.Message m = s.createMessage();
      m.setStringProperty("foo", null);
      p.send(m);
      c.close();

      QueueControl queueControl = createManagementControl(address, queue, RoutingType.ANYCAST);

      assertEquals(1, queueControl.browse().length);

      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testResetGroups() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);

      ClientConsumer consumer = session.createConsumer(queue);
      assertEquals(1, queueControl.getConsumerCount());
      consumer.setMessageHandler(message -> logger.debug("{}", message));
      session.start();

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable).putStringProperty(Message.HDR_GROUP_ID, "group1"));
      producer.send(session.createMessage(durable).putStringProperty(Message.HDR_GROUP_ID, "group2"));
      producer.send(session.createMessage(durable).putStringProperty(Message.HDR_GROUP_ID, "group3"));

      Wait.assertEquals(3, () -> getGroupCount(queueControl));

      queueControl.resetGroup("group1");

      Wait.assertEquals(2, () -> getGroupCount(queueControl));

      producer.send(session.createMessage(durable).putStringProperty(Message.HDR_GROUP_ID, "group1"));

      Wait.assertEquals(3, () -> getGroupCount(queueControl));

      queueControl.resetAllGroups();

      Wait.assertEquals(0, () -> getGroupCount(queueControl));

      consumer.close();
      session.deleteQueue(queue);
   }

   @TestTemplate
   public void testGetScheduledCountOnRemove() throws Exception {
      long delay = Integer.MAX_VALUE;
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      QueueControl queueControl = createManagementControl(address, queue);
      assertEquals(0, queueControl.getScheduledCount());

      final LocalQueueBinding binding = (LocalQueueBinding) server.getPostOffice().getBinding(queue);
      Queue q = binding.getQueue();
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q));

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(durable);
      message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + delay);
      producer.send(message);

      queueControl.removeAllMessages();

      assertEquals(0, queueControl.getMessageCount());

      //Verify that original queue has a memory size of 0
      assertEquals(0, QueueImplTestAccessor.getQueueMemorySize(q));

      session.deleteQueue(queue);
   }

   /**
    * Test retry - get a message from auto-created DLA/DLQ with HDR_ORIG_RoutingType set and put on original queue.
    */
   @TestTemplate
   public void testRetryMessageWithAutoCreatedResourcesAndOrigRoutingType() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString qName = SimpleString.of("q1");
      final SimpleString adName = SimpleString.of("ad1");
      final String sampleText = "Put me on DLQ";

      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(adName.toString());
      final SimpleString dlq = addressSettings.getDeadLetterQueuePrefix().concat(adName).concat(addressSettings.getDeadLetterQueueSuffix());

      server.getAddressSettingsRepository().addMatch(adName.toString(), new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla).setAutoCreateDeadLetterResources(true));
      session.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(durable).setRoutingType(RoutingType.ANYCAST));

      // Send message to queue.
      ClientProducer producer = session.createProducer(adName);
      ClientMessage m = createTextMessage(session, sampleText);

      // Set ORIG RoutingType header
      m.putByteProperty(Message.HDR_ORIG_ROUTING_TYPE, (byte) 1);
      producer.send(m);
      session.start();

      ClientConsumer clientConsumer = session.createConsumer(qName);
      ClientMessage clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      // force a rollback to DLQ
      session.rollback();
      clientMessage = clientConsumer.receiveImmediate();
      assertNull(clientMessage);

      QueueControl queueControl = createManagementControl(dla, dlq);
      assertMessageMetrics(queueControl, 1, true);
      final long messageID = getFirstMessageId(queueControl);

      // Retry the message - i.e. it should go from DLQ to original Queue.
      assertTrue(queueControl.retryMessage(messageID));

      // Assert DLQ is empty...
      assertEquals(0, getMessageCount(queueControl));
      assertMessageMetrics(queueControl, 0, durable);

      // .. and that the message is now on the original queue with ORIG RoutingType set as RoutingType
      clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertEquals(RoutingType.ANYCAST, clientMessage.getRoutingType());
      assertNotNull(clientMessage);

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      clientConsumer.close();
   }

   /**
    * Test retry - get a message from auto-created DLA/DLQ and put on original queue.
    */
   @TestTemplate
   public void testRetryMessageReturnedWhenNoOrigQueue() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString qName = SimpleString.of("q1");
      final SimpleString adName = SimpleString.of("ad1");
      final String sampleText = "Put me on DLQ";

      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch(adName.toString());
      final SimpleString dlq = addressSettings.getDeadLetterQueuePrefix().concat(adName).concat(addressSettings.getDeadLetterQueueSuffix());

      server.getAddressSettingsRepository().addMatch(adName.toString(), new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla).setAutoCreateDeadLetterResources(true));

      session.createQueue(QueueConfiguration.of(qName).setAddress(adName).setDurable(durable));

      // Send message to queue.
      ClientProducer producer = session.createProducer(adName);
      producer.send(createTextMessage(session, sampleText));
      session.start();

      ClientConsumer clientConsumer = session.createConsumer(qName);
      ClientMessage clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      // force a rollback to DLQ
      session.rollback();
      clientMessage = clientConsumer.receiveImmediate();
      assertNull(clientMessage);
      clientConsumer.close();

      QueueControl queueControl = createManagementControl(dla, dlq);
      assertMessageMetrics(queueControl, 1, true);
      final long messageID = getFirstMessageId(queueControl);

      //Delete original queue
      session.deleteQueue(qName);
      // Retry the message
      queueControl.retryMessage(messageID);
      Thread.sleep(100);

      // Assert DLQ is not empty...
      assertEquals(1, getMessageCount(queueControl));

      // .. and that the message is still intact on DLQ
      clientConsumer = session.createConsumer(dlq);
      clientMessage = clientConsumer.receive(500);
      clientMessage.acknowledge();
      assertNotNull(clientMessage);

      assertEquals(sampleText, clientMessage.getBodyBuffer().readString());

      clientConsumer.close();
   }

   @TestTemplate
   public void testChangeMessagesPriorityIncludesPagedMessage() throws Exception {
      final SimpleString queueName = SimpleString.of("queue");
      final String sampleText = "Message Content";
      final int messageCount = 10;

      AddressSettings addressSettings = new AddressSettings().setMaxSizeBytes(200L);
      server.getAddressSettingsRepository().addMatch(queueName.toString(), addressSettings);
      session.createQueue(QueueConfiguration.of(queueName).setDurable(durable));

      // Send message to queue, make sure it enters paging.
      ClientProducer producer = session.createProducer(queueName);
      for (int i = 0; i < messageCount; i++) {
         producer.send(createTextMessage(session, sampleText).setPriority((byte) 2));
      }

      Wait.assertTrue(server.locateQueue(queueName).getPagingStore()::isPaging);

      //Send identifiable message to paging queue
      producer.send(createTextMessage(session, sampleText).setPriority((byte) 2));

      QueueControl queueControl = createManagementControl(queueName, queueName);

      for (Map<String, Object> messageMap : queueControl.listMessages(null)) {
         assertEquals(2, getPriorityStraight(messageMap.get("priority")));
      }

      queueControl.changeMessagesPriority(null, 5);

      //Make sure priority is changed on all messages
      for (Map<String, Object> messageMap : queueControl.listMessages(null)) {
         assertEquals(5, getPriorityStraight(messageMap.get("priority")));
      }

      queueControl.removeAllMessages();

   }

   @TestTemplate
   public void testChangeMessagePriorityIncludesPagedMessage() throws Exception {
      final SimpleString queueName = SimpleString.of("queue");
      final String sampleText = "Message Content";
      final int messageCount = 10;

      AddressSettings addressSettings = new AddressSettings().setMaxSizeBytes(200L);
      server.getAddressSettingsRepository().addMatch(queueName.toString(), addressSettings);
      session.createQueue(QueueConfiguration.of(queueName).setDurable(durable));

      // Send message to queue, make sure it enters paging.
      ClientProducer producer = session.createProducer(queueName);
      for (int i = 0; i < messageCount; i++) {
         producer.send(createTextMessage(session, sampleText));
      }

      Wait.assertTrue(server.locateQueue(queueName).getPagingStore()::isPaging);

      //Send identifiable message to paging queue
      producer.send(createTextMessage(session, sampleText).putStringProperty("myID", "unique").setPriority((byte) 2));

      QueueControl queueControl = createManagementControl(queueName, queueName);
      Map<String, Object>[] messages = queueControl.listMessages(null);
      long messageID = (Long) messages[messageCount].get("messageID");

      assertEquals(2, getPriorityStraight(messages[messageCount].get("priority")));

      queueControl.changeMessagePriority(messageID, 5);

      //Make sure priority is changed on the message
      for (Map<String, Object> messageMap : queueControl.listMessages(null)) {
         if (messageMap.get("messageID").equals(messageID)) {
            assertEquals(5, getPriorityStraight(messageMap.get("priority")));
         }
      }

      queueControl.removeAllMessages();

   }

   @TestTemplate
   public void testMoveMessageIncludesPagedMessage() throws Exception {
      final String queueNameMatch = "queue.#";
      final SimpleString queueName1 = SimpleString.of("queue.1");
      final SimpleString queueName2 = SimpleString.of("queue.2");
      final String sampleText = "Message Content";
      final int messageCount = 10;

      AddressSettings addressSettings = new AddressSettings().setMaxSizeBytes(200L);
      server.getAddressSettingsRepository().addMatch(queueNameMatch, addressSettings);
      session.createQueue(QueueConfiguration.of(queueName1).setDurable(durable));
      session.createQueue(QueueConfiguration.of(queueName2).setDurable(durable));

      // Send message to queue, make sure it enters paging.
      ClientProducer producer = session.createProducer(queueName1);
      for (int i = 0; i < messageCount; i++) {
         producer.send(createTextMessage(session, sampleText));
      }

      Wait.assertTrue(server.locateQueue(queueName1).getPagingStore()::isPaging);

      //Send identifiable message to paging queue
      producer.send(createTextMessage(session, sampleText).putStringProperty("myID", "unique").setPriority((byte) 2));

      QueueControl queueControl = createManagementControl(queueName1, queueName1);
      Map<String, Object>[] messages = queueControl.listMessages(null);
      long messageID = (Long) messages[messageCount].get("messageID");

      assertEquals(messageCount + 1, server.locateQueue(queueName1).getMessageCount());

      queueControl.moveMessage(messageID, queueName2.toString());

      assertEquals(messageCount, server.locateQueue(queueName1).getMessageCount());
      assertEquals(1, server.locateQueue(queueName2).getMessageCount());

   }

   @TestTemplate
   public void testCopyMessageIncludesPagedMessage() throws Exception {
      final String queueNameMatch = "queue.#";
      final SimpleString queueName1 = SimpleString.of("queue.1");
      final SimpleString queueName2 = SimpleString.of("queue.2");
      final String sampleText = "Message Content";
      final int messageCount = 10;

      AddressSettings addressSettings = new AddressSettings().setMaxSizeBytes(200L);
      server.getAddressSettingsRepository().addMatch(queueNameMatch, addressSettings);
      session.createQueue(QueueConfiguration.of(queueName1).setDurable(durable));
      session.createQueue(QueueConfiguration.of(queueName2).setDurable(durable));

      // Send message to queue, make sure it enters paging.
      ClientProducer producer = session.createProducer(queueName1);
      for (int i = 0; i < messageCount; i++) {
         producer.send(createTextMessage(session, sampleText));
      }

      Wait.assertTrue(server.locateQueue(queueName1).getPagingStore()::isPaging);

      //Send identifiable message to paging queue
      producer.send(createTextMessage(session, sampleText).putStringProperty("myID", "unique").setPriority((byte) 2));

      QueueControl queueControl = createManagementControl(queueName1, queueName1);
      Map<String, Object>[] messages = queueControl.listMessages(null);
      long messageID = (Long) messages[messageCount].get("messageID");

      assertEquals(messageCount + 1, server.locateQueue(queueName1).getMessageCount());

      queueControl.copyMessage(messageID, queueName2.toString());

      assertEquals(messageCount + 1, server.locateQueue(queueName1).getMessageCount());
      assertEquals(1, server.locateQueue(queueName2).getMessageCount());

   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration conf = createDefaultConfig(true).setJMXManagementEnabled(true);
      server = addServer(ActiveMQServers.newActiveMQServer(conf, mbeanServer, true));

      server.start();

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(true).setConsumerWindowSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      session = sf.createSession(false, true, false);
      session.start();
   }

   protected int getPriorityStraight(Object priority) {
      if (priority instanceof Byte) {
         return ((Byte) priority).intValue();

      } else if (priority instanceof Long) {
         return ((Long) priority).intValue();
      }

      return (Integer) priority;
   }

   protected long getFirstMessageId(final QueueControl queueControl) throws Exception {
      JsonArray array = JsonUtil.readJsonArray(queueControl.getFirstMessageAsJSON());
      JsonObject object = (JsonObject) array.get(0);
      return object.getJsonNumber("messageID").longValue();
   }

   protected void assertMessageMetrics(final QueueControl queueControl, long messageCount, boolean durable) throws Exception {
      assertMetrics(queueControl, messageCount, durable, queueControl::getMessageCount,
            queueControl::getPersistentSize, queueControl::getDurableMessageCount, queueControl::getDurablePersistentSize);
   }

   protected void assertScheduledMetrics(final QueueControl queueControl, long messageCount, boolean durable) throws Exception {
      assertMetrics(queueControl, messageCount, durable, queueControl::getScheduledCount,
            queueControl::getScheduledSize, queueControl::getDurableScheduledCount, queueControl::getDurableScheduledSize);
   }

   protected void assertDeliveringMetrics(final QueueControl queueControl, long messageCount, boolean durable) throws Exception {
      assertMetrics(queueControl, messageCount, durable, queueControl::getDeliveringCount,
            queueControl::getDeliveringSize, queueControl::getDurableDeliveringCount, queueControl::getDurableDeliveringSize);
   }

   protected void assertMetrics(final QueueControl queueControl, long messageCount, boolean durable,
                                Supplier<Number> count, Supplier<Number> size,
                                Supplier<Number> durableCount, Supplier<Number> durableSize) throws Exception {

      //make sure count stat equals message count
      assertTrue(Wait.waitFor(() -> count.get().longValue() == messageCount, 3 * 1000, 100));

      if (messageCount > 0) {
         //verify size stat greater than 0
         assertTrue(Wait.waitFor(() -> size.get().longValue() > 0, 3 * 1000, 100));

         //If durable then make sure durable count and size are correct
         if (durable) {
            Wait.assertEquals(messageCount, () -> durableCount.get().longValue(), 3 * 1000, 100);
            assertTrue(Wait.waitFor(() -> durableSize.get().longValue() > 0, 3 * 1000, 100));
         } else {
            Wait.assertEquals(0L, () -> durableCount.get().longValue(), 3 * 1000, 100);
            Wait.assertEquals(0L, () -> durableSize.get().longValue(), 3 * 1000, 100);
         }
      } else {
         Wait.assertEquals(0L, () -> count.get().longValue(), 3 * 1000, 100);
         Wait.assertEquals(0L, () -> durableCount.get().longValue(), 3 * 1000, 100);
         Wait.assertEquals(0L, () -> size.get().longValue(), 3 * 1000, 100);
         Wait.assertEquals(0L, () -> durableSize.get().longValue(), 3 * 1000, 100);
      }
   }
}
