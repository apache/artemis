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
package org.apache.activemq.cli.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.cli.commands.messages.Producer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.List;

public class CliProducerTest extends CliTestBase {
   private Connection connection;
   private ActiveMQConnectionFactory cf;
   private static final int TEST_MESSAGE_COUNT = 10;

   @BeforeEach
   @Override
   public void setup() throws Exception {
      setupAuth();
      super.setup();
      startServer();
      cf = getConnectionFactory(61616);
      connection = cf.createConnection("admin", "admin");
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      closeConnection(cf, connection);
      super.tearDown();
   }

   private void produceMessages(String address, String message, int msgCount) throws Exception {
      produceMessages(address, message, msgCount, null);
   }

   private void produceMessages(String address, String message, int msgCount, String properties) throws Exception {
      new Producer()
         .setMessage(message)
         .setProperties(properties)
         .setMessageCount(msgCount)
         .setDestination(address)
         .setUser("admin")
         .setPassword("admin")
         .execute(new TestActionContext());
   }

   private void produceMessages(String address, int msgCount) throws Exception {
      produceMessages(address, null, msgCount);
   }

   private void checkSentMessages(Session session, String address, String messageBody) throws Exception {
      final boolean isCustomMessageBody = messageBody != null;

      List<Message> received = consumeMessages(session, address, TEST_MESSAGE_COUNT, CompositeAddress.isFullyQualified(address));
      for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
         if (!isCustomMessageBody) {
            messageBody = "test message: " + String.valueOf(i);
         }
         assertEquals(messageBody, ((TextMessage) received.get(i)).getText());
      }
   }

   @Test
   public void testSendMessage() throws Exception {
      String address = "test";
      Session session = createSession(connection);

      produceMessages(address, TEST_MESSAGE_COUNT);

      checkSentMessages(session, address, null);
   }

   @Test
   public void testSendMessageWithProperties() throws Exception {
      String address = "test";
      Session session = createSession(connection);

      String myBooleanKey = "myBooleanKey";
      String myStringKey = "myStringKey";
      String myStringValue = RandomUtil.randomUUIDString();
      String propertiesJson = ("[{'type':'boolean','key':'" + myBooleanKey + "','value':'true'},{'type':'string','key':'" + myStringKey + "','value':'" + myStringValue + "'}]").replaceAll("'", "\"");

      produceMessages(address, null, 1, propertiesJson);

      List<Message> consumedMessages = consumeMessages(session, address, 1, false);
      assertEquals(1, consumedMessages.size());

      Message msg = consumedMessages.get(0);
      assertTrue(msg.propertyExists(myBooleanKey));
      assertTrue(msg.getBooleanProperty(myBooleanKey));
      assertTrue(msg.propertyExists(myStringKey));
      assertEquals(myStringValue, msg.getStringProperty(myStringKey));
   }

   @Test
   public void testSendMessageFQQN() throws Exception {
      String address = "test";
      String queue = "queue";
      String fqqn = address + "::" + queue;

      createQueue(RoutingType.MULTICAST, address, queue);
      Session session = createSession(connection);

      produceMessages("topic://" + address, TEST_MESSAGE_COUNT);

      checkSentMessages(session, fqqn, null);
   }

   @Test
   public void testSendMessageCustomBodyFQQN() throws Exception {
      String address = "test";
      String queue = "queue";
      String fqqn = address + "::" + queue;
      String messageBody = RandomUtil.randomAlphaNumericString(20);

      createQueue(RoutingType.MULTICAST, address, queue);
      Session session = createSession(connection);

      produceMessages("topic://" + address, messageBody, TEST_MESSAGE_COUNT);

      checkSentMessages(session, fqqn, messageBody);
   }

   @Test
   public void testSendMessageWithCustomBody() throws Exception {
      String address = "test";
      String messageBody = RandomUtil.randomAlphaNumericString(20);

      Session session = createSession(connection);

      produceMessages(address, messageBody, TEST_MESSAGE_COUNT);

      checkSentMessages(session, address, messageBody);
   }

   @Test
   public void testSendMessageWithCustomBodyLongString() throws Exception {
      String address = "test";
      String messageBody = RandomUtil.randomAlphaNumericString(500_000);

      Session session = createSession(connection);

      produceMessages(address, messageBody, TEST_MESSAGE_COUNT);

      checkSentMessages(session, address, messageBody);
   }
}
