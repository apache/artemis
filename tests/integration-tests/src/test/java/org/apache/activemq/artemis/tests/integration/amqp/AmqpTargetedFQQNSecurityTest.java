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

import javax.jms.JMSSecurityException;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test that AMQP senders and receivers can send to and receive from FQQN addresses when the broker security policy is
 * configured to limit access to those resources.
 */
@Timeout(20)
public class AmqpTargetedFQQNSecurityTest extends AmqpClientTestSupport {

   private final String FQQN_SENDER_1 = "fqqnSender1";
   private final String FQQN_SENDER_2 = "fqqnSender2";
   private final String FQQN_RECEIVER_1 = "fqqnReceiver1";
   private final String FQQN_RECEIVER_2 = "fqqnReceiver2";
   private final String FQQN_SENDER1_ROLE = "fqqnSender1Role";
   private final String FQQN_SENDER2_ROLE = "fqqnSender2Role";
   private final String FQQN_RECEIVER1_ROLE = "fqqnReceiver1Role";
   private final String FQQN_RECEIVER2_ROLE = "fqqnReceiver2Role";
   private final String FQQN_ADDRESS = "fqqnAddress";
   private final String FQQN_QUEUE1 = "fqqnQueue1";
   private final String FQQN_QUEUE2 = "fqqnQueue2";
   private final String FQQN_1 = CompositeAddress.toFullyQualified(FQQN_ADDRESS, FQQN_QUEUE1);
   private final String FQQN_2 = CompositeAddress.toFullyQualified(FQQN_ADDRESS, FQQN_QUEUE2);
   private final String PASS = UUID.randomUUID().toString();

   @Override
   protected boolean isSecurityEnabled() {
      return true;
   }

   @Override
   protected void enableSecurity(ActiveMQServer server, String... securityMatches) {
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      Configuration configuration = server.getConfiguration();

      // This section create a split FQQN set under a single address where each user can only read or write
      // to their own Queue under the base FQQN address, these roles disallow auto create to ensure neither
      // can just create their way into a working configuration.

      final Role fqqnSender1Role = new Role(FQQN_SENDER1_ROLE, true, false, false, false, false, false, false, false, false, false, false, false);
      final Role fqqnSender2Role = new Role(FQQN_SENDER2_ROLE, true, false, false, false, false, false, false, false, false, false, false, false);
      final Role fqqnReceiver1Role = new Role(FQQN_RECEIVER1_ROLE, false, true, false, false, false, false, false, false, false, false, true, false);
      final Role fqqnReceiver2Role = new Role(FQQN_RECEIVER2_ROLE, false, true, false, false, false, false, false, false, false, false, true, false);

      // Senders
      securityManager.getConfiguration().addUser(FQQN_SENDER_1, PASS);
      securityManager.getConfiguration().addRole(FQQN_SENDER_1, FQQN_SENDER1_ROLE);
      securityManager.getConfiguration().addUser(FQQN_SENDER_2, PASS);
      securityManager.getConfiguration().addRole(FQQN_SENDER_2, FQQN_SENDER2_ROLE);

      // Receivers
      securityManager.getConfiguration().addUser(FQQN_RECEIVER_1, PASS);
      securityManager.getConfiguration().addRole(FQQN_RECEIVER_1, FQQN_RECEIVER1_ROLE);
      securityManager.getConfiguration().addUser(FQQN_RECEIVER_2, PASS);
      securityManager.getConfiguration().addRole(FQQN_RECEIVER_2, FQQN_RECEIVER2_ROLE);

      configuration.putSecurityRoles(FQQN_1, Set.of(fqqnSender1Role, fqqnReceiver1Role));
      configuration.putSecurityRoles(FQQN_2, Set.of(fqqnSender2Role, fqqnReceiver2Role));

      configuration.addQueueConfiguration(QueueConfiguration.of(FQQN_1).setAddress(FQQN_ADDRESS).setRoutingType(RoutingType.ANYCAST));
      configuration.addQueueConfiguration(QueueConfiguration.of(FQQN_2).setAddress(FQQN_ADDRESS).setRoutingType(RoutingType.ANYCAST));

      server.getConfiguration().setSecurityEnabled(true);
   }

   @Test
   public void testSender1CanWriteToAssignedFQQN() throws Exception {
      doTestSenderCanWriteToAssignedFQQN(FQQN_SENDER_1, FQQN_1);
   }

   @Test
   public void testSender2CanWriteToAssignedFQQN() throws Exception {
      doTestSenderCanWriteToAssignedFQQN(FQQN_SENDER_2, FQQN_2);
   }

   private void doTestSenderCanWriteToAssignedFQQN(String username, String fqqn) throws Exception {
      final AmqpClient client = createAmqpClient(username, PASS);
      final AmqpConnection connection = addConnection(client.connect());
      final AmqpSession session = connection.createSession();
      final AmqpSender sender = session.createSender(fqqn);

      final AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");

      sender.send(message);

      final Queue queue = getProxyToQueue(fqqn);
      assertNotNull(queue);

      Wait.assertEquals(1, queue::getMessageCount);
   }

   @Test
   public void testReceiver1CanReadFromAssignedFQQN() throws Exception {
      doTestReceiverCanReadFromAssignedFQQN(FQQN_SENDER_1, FQQN_RECEIVER_1, FQQN_1);
   }

   @Test
   public void testReceiver2CanReadFromAssignedFQQN() throws Exception {
      doTestReceiverCanReadFromAssignedFQQN(FQQN_SENDER_2, FQQN_RECEIVER_2, FQQN_2);
   }

   private void doTestReceiverCanReadFromAssignedFQQN(String senderUser, String receiverUser, String fqqn) throws Exception {
      final AmqpClient sendClient = createAmqpClient(senderUser, PASS);
      final AmqpConnection sendConnection = addConnection(sendClient.connect());
      final AmqpSession sendSession = sendConnection.createSession();
      final AmqpSender sender = sendSession.createSender(fqqn);

      final AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");

      sender.send(message);

      final Queue queue = getProxyToQueue(fqqn);
      assertNotNull(queue);

      Wait.assertEquals(1, queue::getMessageCount);

      final AmqpClient receiveClient = createAmqpClient(receiverUser, PASS);
      final AmqpConnection receiveConnection = addConnection(receiveClient.connect());
      final AmqpSession receiveSession = receiveConnection.createSession();
      final AmqpReceiver receiver = receiveSession.createReceiver(fqqn);

      receiver.flow(1);
      final AmqpMessage received = receiver.receive();
      assertNotNull(received);
      assertEquals("Test-Message", received.getText());
      received.accept();

      Wait.assertEquals(0, queue::getMessageCount);
   }

   @Test
   public void testReceiver1CannotReadFromFQQNAssignedToReceiver2() throws Exception {
      doTestReceiverCannotReadFromFQQNAssignedToAnotherReceiver(FQQN_SENDER_2, FQQN_RECEIVER_1, FQQN_2);
   }

   @Test
   public void testReceiver2CannotReadFromFQQNAssignedToReceiver1() throws Exception {
      doTestReceiverCannotReadFromFQQNAssignedToAnotherReceiver(FQQN_SENDER_1, FQQN_RECEIVER_2, FQQN_1);
   }

   private void doTestReceiverCannotReadFromFQQNAssignedToAnotherReceiver(String senderUser, String receiverUser, String fqqn) throws Exception {
      final AmqpClient sendClient = createAmqpClient(senderUser, PASS);
      final AmqpConnection sendConnection = addConnection(sendClient.connect());
      final AmqpSession sendSession = sendConnection.createSession();
      final AmqpSender sender = sendSession.createSender(fqqn);

      final AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");

      sender.send(message);

      final Queue queue = getProxyToQueue(fqqn);
      assertNotNull(queue);

      Wait.assertEquals(1, queue::getMessageCount);

      final AmqpClient receiveClient = createAmqpClient(receiverUser, PASS);
      final AmqpConnection receiveConnection = addConnection(receiveClient.connect());
      final AmqpSession receiveSession = receiveConnection.createSession();

      try {
         receiveSession.createReceiver(fqqn);
         fail("Should not be able to attach to FQQN assigned to another user.");
      } catch (IOException e) {
         assertNotNull(e.getCause());
         assertInstanceOf(JMSSecurityException.class, e.getCause());
      }

      Wait.assertEquals(1, queue::getMessageCount);
   }

   @Test
   public void testAnonymousSender1CanWriteToAssignedFQQN() throws Exception {
      doTestAnonymousSendersCanWriteToAssignedFQQN(FQQN_SENDER_1, FQQN_1);
   }

   @Test
   public void testAnonymousSenders2CanWriteToAssignedFQQN() throws Exception {
      doTestAnonymousSendersCanWriteToAssignedFQQN(FQQN_SENDER_2, FQQN_2);
   }

   private void doTestAnonymousSendersCanWriteToAssignedFQQN(String username, String fqqn) throws Exception {
      final AmqpClient client = createAmqpClient(username, PASS);
      final AmqpConnection connection = addConnection(client.connect());
      final AmqpSession session = connection.createSession();
      final AmqpSender sender = session.createSender();

      final AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      message.setAddress(fqqn);

      sender.send(message);

      final Queue queue = getProxyToQueue(fqqn);
      assertNotNull(queue);

      Wait.assertEquals(1, queue::getMessageCount);
   }

   @Test
   public void testSender1CannotAttachToUnassignedFQQN() throws Exception {
      doTestSendersCannotAttachToUnassignedFQQN(FQQN_SENDER_1, FQQN_2);
   }

   @Test
   public void testSender2CannotAttachToUnassignedFQQN() throws Exception {
      doTestSendersCannotAttachToUnassignedFQQN(FQQN_SENDER_2, FQQN_1);
   }

   private void doTestSendersCannotAttachToUnassignedFQQN(String username, String fqqn) throws Exception {
      final AmqpClient client = createAmqpClient(username, PASS);
      final AmqpConnection connection = addConnection(client.connect());
      final AmqpSession session = connection.createSession();

      try {
         session.createSender(fqqn);
         fail("Should not be able to attach to FQQN assigned to another user.");
      } catch (IOException e) {
         assertNotNull(e.getCause());
         assertInstanceOf(JMSSecurityException.class, e.getCause());
      }
   }

   @Test
   public void testAnonymousSender1CannotWriteToUnassignedFQQN() throws Exception {
      doTestAnonymousSendersCannotWriteToUnassignedFQQN(FQQN_SENDER_1, FQQN_2);
   }

   @Test
   public void testAnonymousSender2CannotWriteToUnassignedFQQN() throws Exception {
      doTestAnonymousSendersCannotWriteToUnassignedFQQN(FQQN_SENDER_2, FQQN_1);
   }

   private void doTestAnonymousSendersCannotWriteToUnassignedFQQN(String username, String fqqn) throws Exception {
      final AmqpClient client = createAmqpClient(username, PASS);
      final AmqpConnection connection = addConnection(client.connect());
      final AmqpSession session = connection.createSession();
      final AmqpSender sender = session.createSender();

      final AmqpMessage message = new AmqpMessage();
      message.setText("Test-Message");
      message.setAddress(fqqn);

      try {
         sender.send(message);
         fail("Should not be able to send to FQQN assigned to another user.");
      } catch (IOException e) {
         assertNotNull(e.getCause());
         assertInstanceOf(JMSSecurityException.class, e.getCause());
      }
   }

   @Test
   public void testReceiver1CannotAttachAsSenderToEitherFQQN() throws Exception {
      doTestReceiverCannotAttachAsSenderToEitherFQQN(FQQN_RECEIVER_1);
   }

   @Test
   public void testReceiver2CannotAttachAsSenderToEitherFQQN() throws Exception {
      doTestReceiverCannotAttachAsSenderToEitherFQQN(FQQN_RECEIVER_2);
   }

   private void doTestReceiverCannotAttachAsSenderToEitherFQQN(String receiverUser) throws Exception {
      final AmqpClient client = createAmqpClient(receiverUser, PASS);
      final AmqpConnection connection = addConnection(client.connect());
      final AmqpSession session = connection.createSession();

      try {
         session.createSender(FQQN_1);
         fail("Should not be able to attach to FQQN as sender from read only user.");
      } catch (IOException e) {
         assertNotNull(e.getCause());
         assertInstanceOf(JMSSecurityException.class, e.getCause());
      }

      try {
         session.createSender(FQQN_2);
         fail("Should not be able to attach to FQQN as sender from read only user.");
      } catch (IOException e) {
         assertNotNull(e.getCause());
         assertInstanceOf(JMSSecurityException.class, e.getCause());
      }
   }

   @Test
   public void testSender1CannotAttachAsReceiverToEitherFQQN() throws Exception {
      doTestSenderCannotAttachAsReceiverToEitherFQQN(FQQN_SENDER_1);
   }

   @Test
   public void testSender2CannotAttachAsReceiverToEitherFQQN() throws Exception {
      doTestSenderCannotAttachAsReceiverToEitherFQQN(FQQN_SENDER_2);
   }

   private void doTestSenderCannotAttachAsReceiverToEitherFQQN(String senderUser) throws Exception {
      final AmqpClient client = createAmqpClient(senderUser, PASS);
      final AmqpConnection connection = addConnection(client.connect());
      final AmqpSession session = connection.createSession();

      try {
         session.createReceiver(FQQN_1);
         fail("Should not be able to attach to FQQN as receiver from write only user.");
      } catch (IOException e) {
         assertNotNull(e.getCause());
         assertInstanceOf(JMSSecurityException.class, e.getCause());
      }

      try {
         session.createReceiver(FQQN_2);
         fail("Should not be able to attach to FQQN as receiver from write only user.");
      } catch (IOException e) {
         assertNotNull(e.getCause());
         assertInstanceOf(JMSSecurityException.class, e.getCause());
      }
   }
}
