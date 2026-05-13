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
package org.apache.activemq.artemis.tests.smoke.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.Run;
import org.apache.activemq.artemis.cli.commands.messages.Transfer;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransferTest extends SmokeTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SERVER_NAME_0 = "transfer1";
   public static final String SERVER_NAME_1 = "transfer2";
   private static final int NUMBER_OF_MESSAGES = 200;
   private static final int PARTIAL_MESSAGES = 10;


   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);

      File server1Location = getFileServerLocation(SERVER_NAME_1);
      deleteDirectory(server1Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location);
         cliCreateServer.addArgs("--disable-persistence");
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server1Location).setPortOffset(100);
         cliCreateServer.addArgs("--disable-persistence");
         cliCreateServer.createServer();
      }
   }

   public TransferTest() {
   }

   public static Collection<String[]> mixProtocolOptions() {

      String[] protocols = new String[]{"core", "amqp"};

      List<String[]> parameters = new ArrayList<>();

      for (int i = 0; i < protocols.length; i++) {
         for (int j = 0; j < protocols.length; j++) {
            // sender and sourceOnTransfer have to be the same
            // consumer and targetOnTransfer have to be the same
            // this is because AMQP Shared Subscription will create a different queue than core
            String[] parameter = new String[]{protocols[i], protocols[j], protocols[i], protocols[j]};
            parameters.add(parameter);
         }
      }

      return parameters;
   }

   private ConnectionFactory createConsumerCF(String consumerProtocol) {
      return CFUtil.createConnectionFactory(consumerProtocol, "tcp://localhost:61716");
   }

   private ConnectionFactory createSenderCF(String senderProtocol) {
      return CFUtil.createConnectionFactory(senderProtocol, "tcp://localhost:61616");
   }

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      cleanupData(SERVER_NAME_1);
      disableCheckThread();
      startServer(SERVER_NAME_0, 0, 30000);
      startServer(SERVER_NAME_1, 100, 30000);
   }

   @Test
   public void testTryAllPermutations() throws Exception {

      Collection<String[]> options = mixProtocolOptions();
      int iteration = 0;
      for (String[] option : options) {
         logger.info("{} {} {} {} - iteration = {}", option[0], option[1], option[2], option[3], iteration);
         internalTransferSimpleQueue("queue_a" + iteration, false, option[0], option[1], option[2], option[3]);
         internalTransferSimpleQueue("queue_b" + iteration, true, option[0], option[1], option[2], option[3]);
         testDurableSharedSubscrition("topic_c" + iteration, "queue_c" + iteration, option[0], option[1], option[2], option[3]);
         testSharedSubscription("topic_d" + iteration, "queue_d" + iteration, option[0], option[1], option[2], option[3]);
         testDurableConsumer("topic_e" + iteration, "queue_e" + iteration, option[0], option[1], option[2], option[3]);
         iteration++;
      }
   }

   private static void callTransferQueue(String targetURL, String sourceQueue, String sourceTopic, String sharedDurableSubscription, String sharedSubscription, String durableConsumer, String clientID, String targetQueue, String sourceProtocol, String transferProtocol, boolean copy) throws Exception {

      Run.setEmbedded(true); // Telling the CLI to not use System.exit

      Transfer transfer = new Transfer();
      File artemisInstance = getFileServerLocation(SERVER_NAME_0);
      File etc = new File(artemisInstance, "etc");
      transfer.setHomeValues(HelperCreate.getHome(ARTEMIS_HOME_PROPERTY), getFileServerLocation(SERVER_NAME_0), etc);
      transfer.setTargetURL(targetURL);
      if (sourceQueue != null) {
         transfer.setSourceQueue(sourceQueue);
      }
      if (sourceTopic != null) {
         transfer.setSourceTopic(sourceTopic);
      }
      if (sharedDurableSubscription != null) {
         transfer.setSharedDurableSubscription(sharedDurableSubscription);
      }
      if (sharedSubscription != null) {
         transfer.setSharedSubscription(sharedSubscription);
      }
      if (durableConsumer != null) {
         transfer.setDurableConsumer(durableConsumer);
      }
      if (clientID != null) {
         transfer.setSourceClientID(clientID);
      }
      transfer.setReceiveTimeout(100);
      transfer.setTargetQueue(targetQueue);
      transfer.setSourceProtocol(sourceProtocol);
      transfer.setTargetProtocol(transferProtocol);
      transfer.setCopy(copy);
      transfer.execute(new ActionContext());

   }

   private void internalTransferSimpleQueue(String queueName, boolean copy,
                                            String senderProtocol, String consumerProtocol,
                                            String sourceTransferProtocol, String targetTransferProtocol) throws Exception {
      ConnectionFactory factory = createSenderCF(senderProtocol);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

      Queue queue = session.createQueue(queueName);
      MessageProducer producer = session.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(session.createTextMessage("hello " + i));
      }

      session.commit();
      callTransferQueue("tcp://localhost:61716", queueName, null, null, null, null, null, queueName, sourceTransferProtocol, targetTransferProtocol, copy);

      ConnectionFactory factoryTarget = createConsumerCF(consumerProtocol);
      Connection connectionTarget = factoryTarget.createConnection();
      connectionTarget.start();
      Session sessionTarget = connectionTarget.createSession(true, Session.SESSION_TRANSACTED);

      Queue queueTarget = sessionTarget.createQueue(queueName);
      MessageConsumer consumer = sessionTarget.createConsumer(queueTarget);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         TextMessage received = (TextMessage) consumer.receive(1000);
         assertNotNull(received);
         assertEquals("hello " + i, received.getText());
      }

      sessionTarget.commit();

      assertNull(consumer.receiveNoWait());

      MessageConsumer consumerSource = session.createConsumer(queue);
      connection.start();

      if (copy) {
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage received = (TextMessage) consumerSource.receive(1000);
            assertNotNull(received);
            assertEquals("hello " + i, received.getText());
         }
      }

      assertNull(consumerSource.receiveNoWait());

      connection.close();
      connectionTarget.close();
   }

   public void testDurableSharedSubscrition(String topicName, String queueName, String senderProtocol, String consumerProtocol, String sourceTransferProtocol, String targetTransferProtocol) throws Exception {
      ConnectionFactory factory = createSenderCF(senderProtocol);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

      Topic topic = session.createTopic(topicName);
      MessageConsumer subscription = session.createSharedDurableConsumer(topic, "testSubs");
      MessageProducer producer = session.createProducer(topic);
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(session.createTextMessage("hello " + i));
      }

      session.commit();
      subscription.close();

      callTransferQueue("tcp://localhost:61716", null, topicName, "testSubs", null, null, null, queueName, sourceTransferProtocol, targetTransferProtocol, false);

      ConnectionFactory factoryTarget = createConsumerCF(consumerProtocol);
      Connection connectionTarget = factoryTarget.createConnection();
      connectionTarget.start();
      Session sessionTarget = connectionTarget.createSession(true, Session.SESSION_TRANSACTED);

      Queue queueTarget = sessionTarget.createQueue(queueName);
      MessageConsumer consumer = sessionTarget.createConsumer(queueTarget);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         TextMessage received = (TextMessage) consumer.receive(1000);
         assertNotNull(received);
      }

      sessionTarget.commit();

      assertNull(consumer.receiveNoWait());

      session.unsubscribe("testSubs");
      connection.close();
      connectionTarget.close();
   }

   public void testSharedSubscription(String topicName, String queueName, String senderProtocol, String consumerProtocol, String sourceTransferProtocol, String targetTransferProtocol) throws Exception {
      ConnectionFactory factory = createSenderCF(senderProtocol);
      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

      Topic topic = session.createTopic(topicName);
      MessageConsumer subscription = session.createSharedConsumer(topic, "testSubs");
      MessageProducer producer = session.createProducer(topic);
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      // I can't just send a few messages.. I have to send more messages that would be stuck on delivering mode
      // since the transfer will share the consumer with the shared consumer
      // and the temporary queue would be removed if i closed it earlier.
      for (int i = 0; i < 2000; i++) {
         producer.send(session.createTextMessage("hello " + i));
      }

      session.commit();

      callTransferQueue("tcp://localhost:61716", null, topicName, null, "testSubs", null, null, queueName, sourceTransferProtocol, targetTransferProtocol, false);

      // this test is a bit tricky as the subscription would be removed when the consumer is gone...
      // I'm adding a test for completion only
      // and the subscription has to be closed only after the transfer,
      // which will not receive all the messages as some messages will be in delivering mode
      subscription.close();

      ConnectionFactory factoryTarget = createConsumerCF(consumerProtocol);
      Connection connectionTarget = factoryTarget.createConnection();
      connectionTarget.start();
      Session sessionTarget = connectionTarget.createSession(true, Session.SESSION_TRANSACTED);

      Queue queueTarget = sessionTarget.createQueue(queueName);
      MessageConsumer consumer = sessionTarget.createConsumer(queueTarget);

      // we are keeping a non durable subscription so the temporary queue still up
      // I'm not going to bother about being too strict about the content, just that some messages arrived
      for (int i = 0; i < PARTIAL_MESSAGES; i++) {
         TextMessage received = (TextMessage) consumer.receive(1000);
         assertNotNull(received);
      }

      sessionTarget.commit();

      connection.close();
      connectionTarget.close();
   }

   public void testDurableConsumer(String topicName, String queueName, String senderProtocol, String consumerProtocol, String sourceTransferProtocol, String targetTransferProtocol) throws Exception {
      ConnectionFactory factory = createSenderCF(senderProtocol);
      Connection connection = factory.createConnection();
      connection.setClientID("test");
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

      Topic topic = session.createTopic(topicName);
      MessageConsumer subscription = session.createDurableConsumer(topic, "testSubs");
      MessageProducer producer = session.createProducer(topic);
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         producer.send(session.createTextMessage("hello " + i));
      }

      session.commit();
      connection.close();
      subscription.close();

      callTransferQueue("tcp://localhost:61716", null, topicName, null, null, "testSubs", "test", queueName, sourceTransferProtocol, targetTransferProtocol, false);

      ConnectionFactory factoryTarget = createConsumerCF(consumerProtocol);
      Connection connectionTarget = factoryTarget.createConnection();
      connectionTarget.start();
      Session sessionTarget = connectionTarget.createSession(true, Session.SESSION_TRANSACTED);

      Queue queueTarget = sessionTarget.createQueue(queueName);
      MessageConsumer consumer = sessionTarget.createConsumer(queueTarget);

      // we are keeping a non durable subscription so the temporary queue still up
      // I'm not going to bother about being too strict about the content, just that some messages arrived
      for (int i = 0; i < PARTIAL_MESSAGES; i++) {
         TextMessage received = (TextMessage) consumer.receive(1000);
         assertNotNull(received);
      }

      sessionTarget.commit();

      connectionTarget.close();
   }

}
