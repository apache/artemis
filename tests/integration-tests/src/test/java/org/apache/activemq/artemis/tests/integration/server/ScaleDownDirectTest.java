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

package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.persistence.impl.journal.BatchingIDGenerator;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ScaleDownHandler;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * On this test we will run ScaleDown directly as an unit-test in several cases, simulating what would happen during a
 * real scale down.
 */
@ExtendWith(ParameterizedTestExtension.class)
public class ScaleDownDirectTest extends ClusterTestBase {

   @Parameters(name = "isNetty={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{false}, {true}});
   }

   private final boolean isNetty;

   public ScaleDownDirectTest(boolean isNetty) {
      this.isNetty = isNetty;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupPrimaryServer(0, isFileStorage(), isNetty, true);
      setupPrimaryServer(1, isFileStorage(), isNetty, true);
      startServers(0, 1);
      setupSessionFactory(0, isNetty);
      setupSessionFactory(1, isNetty);

   }

   @TestTemplate
   public void testSendMixedSmallMessages() throws Exception {
      internalTest(100, 100);
   }

   @TestTemplate
   public void testSendMixedLargelMessages() throws Exception {
      internalTest(2 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, 100);
   }

   protected void internalTest(int bufferSize, int numberOfMessages) throws Exception {
      ClientSessionFactory sf = sfs[0];

      ClientSession session = sf.createSession(true, true);

      session.createQueue(QueueConfiguration.of("queue1").setAddress("ad1"));

      ClientProducer producer = session.createProducer("ad1");

      byte[] buffer = new byte[bufferSize];
      for (int i = 0; i < bufferSize; i++) {
         buffer[i] = getSamplebyte(i);
      }

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("i", i);
         message.getBodyBuffer().writeBytes(buffer);
         producer.send(message);
      }

      session.createQueue(QueueConfiguration.of("queue2").setAddress("ad1"));

      for (int i = numberOfMessages; i < (numberOfMessages * 2); i++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("i", i);
         message.getBodyBuffer().writeBytes(buffer);
         producer.send(message);
      }

      assertEquals(numberOfMessages * 2, performScaledown());

      sfs[0].close();

      session.close();

      stopServers(0);

      session = sfs[1].createSession(true, true);

      ClientConsumer consumer1 = session.createConsumer("queue1");
      session.start();

      for (int i = 0; i < numberOfMessages * 2; i++) {
         ClientMessage message = consumer1.receive(5000);
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("i").intValue());
         //         message.acknowledge();

         checkBody(message, bufferSize);

      }

      ClientMessage messageCheckNull = consumer1.receiveImmediate();

      assertNull(messageCheckNull);

      ClientConsumer consumer2 = session.createConsumer("queue2");
      for (int i = numberOfMessages; i < numberOfMessages * 2; i++) {
         ClientMessage message = consumer2.receive(5000);
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("i").intValue());
         //         message.acknowledge();
         checkBody(message, bufferSize);
      }

      messageCheckNull = consumer2.receiveImmediate();

      assertNull(messageCheckNull);
   }

   @TestTemplate
   public void testPaging() throws Throwable {
      final int messageCount = 50;
      final String addressName = "testAddress";
      final String queueName = "testQueue";

      createQueue(0, addressName, queueName, null, true);
      createQueue(1, addressName, queueName, null, true);

      ClientSessionFactory sf = sfs[0];
      ClientSession session = addClientSession(sf.createSession(false, false));
      ClientProducer producer = addClientProducer(session.createProducer(addressName));

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(10 * 1024).setMaxSizeBytes(1024);
      servers[0].getAddressSettingsRepository().addMatch("#", defaultSetting);
      Queue queue0 = servers[0].locateQueue(queueName);
      queue0.getPagingStore().startPaging();
      assertTrue(queue0.getPagingStore().isPaging());

      for (int i = 0; i < messageCount; i++) {
         Message message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(new byte[1024]);
         // The only purpose of this count here is for eventually debug messages on print-data / print-pages
         //            message.putIntProperty("count", messageCount);
         producer.send(message);
      }
      session.commit();

      assertTrue(queue0.getPagingStore().isPaging());

      try {
         Wait.assertEquals(messageCount, queue0::getMessageCount);
         queue0.flushExecutor();
         queue0.getPagingStore().getExecutor().flush(10, TimeUnit.SECONDS);

         long scaledDown = performScaledown();

         servers[0].stop();

         addConsumer(0, 1, queueName, null);
         for (int i = 0; i < scaledDown; i++) {
            ClientMessage message = consumers[0].getConsumer().receive(500);
            assertNotNull(message);
         }

         assertNull(consumers[0].getConsumer().receiveImmediate());
         removeConsumer(0);
      } catch (Throwable ig) {
         ig.printStackTrace();
         throw ig;
      }
   }

   @TestTemplate
   public void testBasicScaleDown() throws Exception {
      final int TEST_SIZE = 2;
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";
      final String queueName2 = "testQueue2";

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, true);
      createQueue(0, addressName, queueName2, null, true);
      createQueue(1, addressName, queueName1, null, true);
      createQueue(1, addressName, queueName2, null, true);

      // send messages to node 0
      send(0, addressName, TEST_SIZE, true, null);

      // consume a message from queue 2
      addConsumer(1, 0, queueName2, null, false);
      ClientMessage clientMessage = consumers[1].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      consumers[1].getSession().commit();
      removeConsumer(1);

      // at this point on node 0 there should be 2 messages in testQueue1 and 1 message in testQueue2
      Wait.assertEquals(TEST_SIZE, () -> getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue()));
      Wait.assertEquals(TEST_SIZE - 1, () -> getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName2))).getQueue()));

      assertEquals(TEST_SIZE, performScaledown());
      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      // get the 2 messages from queue 1
      addConsumer(0, 1, queueName1, null);
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNull(clientMessage);
      removeConsumer(0);

      // get the 1 message from queue 2
      addConsumer(0, 1, queueName2, null);
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNull(clientMessage);
      removeConsumer(0);
   }

   @TestTemplate
   public void testTemporaryQueues() throws Exception {
      final String addressName1 = "testAddress1";
      final String addressName2 = "testAddress2";
      final String queueName1 = "testQueue1";
      final String queueName2 = "testQueue2";
      final String queueName3 = "testQueue3";

      ClientSessionFactory sf = sfs[0];

      ClientSession session = sf.createSession(true, true);

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName1).setDurable(false).setTemporary(true));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(addressName2));
      session.createQueue(QueueConfiguration.of(queueName3).setAddress(addressName2).setDurable(false).setTemporary(true));

      ClientProducer producer1 = session.createProducer(addressName1);
      producer1.send(session.createMessage(true));

      ClientProducer producer2 = session.createProducer(addressName2);
      producer2.send(session.createMessage(true));

      Wait.assertEquals(1, () -> getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue()));
      Wait.assertEquals(1, () -> getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName2))).getQueue()));
      Wait.assertEquals(1, () -> getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName3))).getQueue()));

      assertEquals(1, performScaledown());

      sfs[0].close();

      session.close();

      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      assertNull(servers[1].getPostOffice().getBinding(SimpleString.of(queueName1)));
      assertEquals(1, getMessageCount(((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName2))).getQueue()));
      assertNull(servers[1].getPostOffice().getBinding(SimpleString.of(queueName3)));
   }

   @TestTemplate
   public void testScaleDownWithBigQueueID() throws Exception {
      final int TEST_SIZE = 2;
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";

      JournalStorageManager manager = (JournalStorageManager) servers[0].getStorageManager();
      BatchingIDGenerator idGenerator = (BatchingIDGenerator) manager.getIDGenerator();
      idGenerator.forceNextID((Integer.MAX_VALUE) + 100L);

      long nextId = idGenerator.generateID();
      assertTrue(nextId > Integer.MAX_VALUE);

      manager = (JournalStorageManager) servers[1].getStorageManager();
      idGenerator = (BatchingIDGenerator) manager.getIDGenerator();
      idGenerator.forceNextID((Integer.MAX_VALUE) + 200L);

      nextId = idGenerator.generateID();
      assertTrue(nextId > Integer.MAX_VALUE);

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, true);
      createQueue(1, addressName, queueName1, null, true);

      // send messages to node 0
      send(0, addressName, TEST_SIZE, true, null);

      // at this point on node 0 there should be 2 messages in testQueue1
      Wait.assertEquals(TEST_SIZE, () -> getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue()));

      assertEquals(TEST_SIZE, performScaledown());
      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      // get the 2 messages from queue 1
      addConsumer(0, 1, queueName1, null);
      ClientMessage clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNull(clientMessage);
      removeConsumer(0);
   }

   private void checkBody(ClientMessage message, int bufferSize) {
      assertEquals(bufferSize, message.getBodySize());
      byte[] body = new byte[message.getBodySize()];
      message.getBodyBuffer().readBytes(body);
      for (int bpos = 0; bpos < bufferSize; bpos++) {
         if (getSamplebyte(bpos) != body[bpos]) {
            fail("body comparison failure at " + message);
         }
      }
   }

   private long performScaledown() throws Exception {
      ScaleDownHandler handler = new ScaleDownHandler(servers[0].getPagingManager(), servers[0].getPostOffice(), servers[0].getNodeManager(), servers[0].getClusterManager().getClusterController(), servers[0].getStorageManager(), -1);

      return handler.scaleDownMessages(sfs[1], servers[1].getNodeID().toString(), servers[0].getConfiguration().getClusterUser(), servers[0].getConfiguration().getClusterPassword());
   }

}
