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
package org.apache.activemq.artemis.tests.smoke.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.Consumer;
import org.apache.activemq.artemis.cli.commands.messages.Producer;
import org.apache.activemq.artemis.cli.commands.queue.CreateQueue;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.smoke.console.pages.LoginPage;
import org.apache.activemq.artemis.tests.smoke.console.pages.MessagePage;
import org.apache.activemq.artemis.tests.smoke.console.pages.QueuePage;
import org.apache.activemq.artemis.tests.smoke.console.pages.QueuesPage;
import org.apache.activemq.artemis.tests.smoke.console.pages.SendMessagePage;
import org.apache.activemq.artemis.tests.smoke.console.pages.StatusPage;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.management.ObjectName;

// Parameters set in super class
@ExtendWith(ParameterizedTestExtension.class)
public class QueuesTest extends ArtemisTest {

   public QueuesTest(String browser, String serverName) {
      super(browser, serverName);
   }

   @TestTemplate
   public void testDefaultQueues() throws Exception {
      loadLandingPage();
      LoginPage loginPage = new LoginPage(driver);
      StatusPage statusPage = loginPage.loginValidUser(
         SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, DEFAULT_TIMEOUT);
      QueuesPage queuesPage = statusPage.getQueuesPage(DEFAULT_TIMEOUT);

      Wait.assertEquals(1, () -> queuesPage.countQueue("DLQ"));
      assertEquals(0, queuesPage.getMessagesCount("DLQ"));

      Wait.assertEquals(1, () -> queuesPage.countQueue("ExpiryQueue"));
      assertEquals(0, queuesPage.getMessagesCount("ExpiryQueue"));
   }

   @TestTemplate
   public void testAutoCreatedQueue() throws Exception {
      final long messages = 1;
      final String queueName = "TEST";
      final String messageText = "TEST";

      loadLandingPage();
      LoginPage loginPage = new LoginPage(driver);
      StatusPage statusPage = loginPage.loginValidUser(
         SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, DEFAULT_TIMEOUT);
      QueuesPage beforeQueuesPage = statusPage.getQueuesPage(DEFAULT_TIMEOUT);
      Wait.assertEquals(1, () -> beforeQueuesPage.countQueue("DLQ"));
      Wait.assertEquals(0, () -> beforeQueuesPage.countQueue(queueName));

      Producer producer = new Producer();
      producer.setUser(SERVER_ADMIN_USERNAME);
      producer.setPassword(SERVER_ADMIN_PASSWORD);
      producer.setDestination(queueName);
      producer.setMessageCount(messages);
      producer.setMessage(messageText);
      producer.setSilentInput(true);
      producer.execute(new ActionContext());

      Wait.assertTrue(() -> beforeQueuesPage.searchQueues(queueName));
      Wait.assertEquals(1, () -> beforeQueuesPage.countQueue("DLQ"));
      Wait.assertEquals(1, () -> beforeQueuesPage.countQueue(queueName));
      assertEquals(messages, beforeQueuesPage.getMessagesCount(queueName));

      QueuePage queuePage = beforeQueuesPage.getQueuePage(queueName, DEFAULT_TIMEOUT);
      MessagePage messagePage = queuePage.getMessagePage(0, DEFAULT_TIMEOUT);
      assertEquals(messageText, messagePage.getMessageText());

      Consumer consumer = new Consumer();
      consumer.setUser(SERVER_ADMIN_USERNAME);
      consumer.setPassword(SERVER_ADMIN_PASSWORD);
      consumer.setDestination(queueName);
      consumer.setMessageCount(messages);
      consumer.setSilentInput(true);
      consumer.setReceiveTimeout(2000);
      consumer.setBreakOnNull(true);
      long consumed = (long)consumer.execute(new ActionContext());

      assertEquals(messages, consumed);

      QueuesPage afterQueuesPage = messagePage.getQueuesPageFromMessageView(DEFAULT_TIMEOUT);
      Wait.assertEquals(1, () -> afterQueuesPage.countQueue("DLQ"));
      Wait.assertEquals(0, () -> afterQueuesPage.getMessagesCount(queueName));
   }

   @TestTemplate
   public void testDefaultExpiryQueue() throws Exception {
      testExpiryQueue("TEST", "ExpiryQueue");
   }

   @TestTemplate
   public void testCustomExpiryQueue() throws Exception {
      final ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(null, "0.0.0.0", true);
      final ObjectName activeMQServerObjectName = objectNameBuilder.getActiveMQServerObjectName();

      loadLandingPage();
      LoginPage loginPage = new LoginPage(driver);
      StatusPage statusPage = loginPage.loginValidUser(
         SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, DEFAULT_TIMEOUT);

      assertTrue(statusPage.postJolokiaExecRequest(activeMQServerObjectName.getCanonicalName(),
         "createQueue(java.lang.String,java.lang.String,java.lang.String)",
         "\"foo\",\"foo\",\"ANYCAST\"").toString().contains("\"status\":200"));
      assertTrue(statusPage.postJolokiaExecRequest(activeMQServerObjectName.getCanonicalName(),
         "addAddressSettings(java.lang.String,java.lang.String,java.lang.String,long,boolean,int,long,int,int,long,double,long,long,boolean,java.lang.String,long,long,java.lang.String,boolean,boolean,boolean,boolean)",
         "\"bar\",\"DLA\",\"foo\",-1,false,0,0,0,0,0,\"0\",0,0,false,\"\",-1,0,\"\",false,false,false,false").toString().contains("\"status\":200"));

      statusPage.logout(DEFAULT_TIMEOUT);

      testExpiryQueue("bar", "foo");
   }

   private void testExpiryQueue(String queueName, String expiryQueueName) throws Exception {
      final String messageText = "TEST";
      final ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(null, "0.0.0.0", true);
      final ObjectName expiryQueueObjectName = objectNameBuilder.getQueueObjectName(
         SimpleString.of(expiryQueueName),
         SimpleString.of(expiryQueueName),
         RoutingType.ANYCAST);
      final ObjectName testQueueObjectName = objectNameBuilder.getQueueObjectName(
         SimpleString.of(queueName),
         SimpleString.of(queueName),
         RoutingType.ANYCAST);

      loadLandingPage();
      LoginPage loginPage = new LoginPage(driver);
      StatusPage statusPage = loginPage.loginValidUser(
         SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, DEFAULT_TIMEOUT);
      assertTrue(statusPage.postJolokiaExecRequest(expiryQueueObjectName.getCanonicalName(),
         "removeAllMessages()", "").toString().contains("\"status\":200"));

      QueuesPage beforeQueuesPage = statusPage.getQueuesPage(DEFAULT_TIMEOUT);
      Wait.assertEquals(1, () -> beforeQueuesPage.countQueue(expiryQueueName));
      Wait.assertEquals(0, () -> beforeQueuesPage.countQueue(queueName));

      Producer producer = new Producer();
      producer.setUser(SERVER_ADMIN_USERNAME);
      producer.setPassword(SERVER_ADMIN_PASSWORD);
      producer.setDestination(queueName);
      producer.setMessageCount(1);
      producer.setMessage(messageText);
      producer.setSilentInput(true);
      producer.setProtocol("amqp");
      producer.execute(new ActionContext());

      Wait.assertTrue(() -> beforeQueuesPage.searchQueues(queueName));
      Wait.assertEquals(1, () -> beforeQueuesPage.countQueue(expiryQueueName));
      assertEquals(0, beforeQueuesPage.getMessagesCount(expiryQueueName));
      Wait.assertEquals(1, () -> beforeQueuesPage.countQueue(queueName));
      assertEquals(1, beforeQueuesPage.getMessagesCount(queueName));

      SendMessagePage sendMessagePage = beforeQueuesPage.getAddressSendMessagePage(queueName, DEFAULT_TIMEOUT);
      sendMessagePage.appendMessageText("xxx");
      sendMessagePage.sendMessage();

      beforeQueuesPage.refresh(DEFAULT_TIMEOUT);
      QueuePage queuePage = statusPage.getQueuesPage(DEFAULT_TIMEOUT).getQueuePage(queueName, DEFAULT_TIMEOUT);
      assertTrue(queuePage.postJolokiaExecRequest(testQueueObjectName.getCanonicalName(), "expireMessage(long)",
         String.valueOf(queuePage.getMessageId(0))).toString().contains("\"status\":200"));
      assertTrue(queuePage.postJolokiaExecRequest(testQueueObjectName.getCanonicalName(), "expireMessage(long)",
         String.valueOf(queuePage.getMessageId(1))).toString().contains("\"status\":200"));

      QueuesPage afterQueuesPage = queuePage.getQueuesPageFromMessagesView(DEFAULT_TIMEOUT);
      Wait.assertEquals(1, () -> afterQueuesPage.countQueue(expiryQueueName));
      assertEquals(2, afterQueuesPage.getMessagesCount(expiryQueueName));
      Wait.assertEquals(1, () -> afterQueuesPage.countQueue(queueName));
      assertEquals(0, afterQueuesPage.getMessagesCount(queueName));

      QueuePage expiryPage = afterQueuesPage.getQueuePage(expiryQueueName, DEFAULT_TIMEOUT);
      expiryPage.enableColumn("originalQueue");
      assertEquals(queueName, expiryPage.getMessageOriginalQueue(0));
      assertEquals(queueName, expiryPage.getMessageOriginalQueue(1));
   }

   @TestTemplate
   public void testSendMessageUsingCurrentLogonUser() throws Exception {
      final String queueName = "TEST";
      final String messageText = "TEST";

      loadLandingPage();
      LoginPage loginPage = new LoginPage(driver);
      StatusPage statusPage = loginPage.loginValidUser(
         SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, DEFAULT_TIMEOUT);
      QueuesPage beforeSendingQueuesPage = statusPage.getQueuesPage(DEFAULT_TIMEOUT);
      Wait.assertEquals(1, () -> beforeSendingQueuesPage.countQueue("DLQ"));
      Wait.assertEquals(0, () -> beforeSendingQueuesPage.countQueue(queueName));

      CreateQueue createQueueCommand = new CreateQueue();
      createQueueCommand.setUser(SERVER_ADMIN_USERNAME);
      createQueueCommand.setPassword(SERVER_ADMIN_PASSWORD);
      createQueueCommand.setName(queueName);
      createQueueCommand.setMulticast(false);
      createQueueCommand.setAnycast(true);
      createQueueCommand.setAutoCreateAddress(true);
      createQueueCommand.execute(new ActionContext());

      final long messages = 1;
      beforeSendingQueuesPage.searchQueues("foo");
      Wait.assertEquals(1, () -> beforeSendingQueuesPage.countQueue("DLQ"));
      Wait.assertEquals(1, () -> beforeSendingQueuesPage.countQueue(queueName));
      assertEquals(0, beforeSendingQueuesPage.getMessagesCount(queueName));

      SendMessagePage sendMessagePage = beforeSendingQueuesPage.getQueueSendMessagePage(queueName, DEFAULT_TIMEOUT);
      for (int i = 0; i < messages; i++) {
         sendMessagePage.clearMessageText();
         sendMessagePage.selectUseCurrentLogonUser();
         sendMessagePage.appendMessageText(messageText);
         sendMessagePage.sendMessage();
      }
      sendMessagePage.refresh(DEFAULT_TIMEOUT);
      QueuesPage afterSendingQueuesPage = sendMessagePage.getQueuesPage(DEFAULT_TIMEOUT);
      Wait.assertEquals(1, () -> afterSendingQueuesPage.countQueue("DLQ"));
      Wait.assertEquals(messages, () -> afterSendingQueuesPage.getMessagesCount(queueName));

      Consumer consumer = new Consumer();
      consumer.setUser(SERVER_ADMIN_USERNAME);
      consumer.setPassword(SERVER_ADMIN_PASSWORD);
      consumer.setDestination(queueName);
      consumer.setMessageCount(messages);
      consumer.setSilentInput(true);
      consumer.setReceiveTimeout(2000);
      consumer.setBreakOnNull(true);
      long consumed = (long)consumer.execute(new ActionContext());

      assertEquals(messages, consumed);

      afterSendingQueuesPage.searchQueues("foo");
      Wait.assertEquals(1, () -> afterSendingQueuesPage.countQueue("DLQ"));
      Wait.assertEquals(0, () -> afterSendingQueuesPage.getMessagesCount(queueName));
   }
}
