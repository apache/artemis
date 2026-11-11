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

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.DeleteOnClose;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.transport.amqp.AmqpSupport.LIFETIME_POLICY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for temporary destination handling over AMQP
 */
public class AmqpTempDestinationTest extends AmqpClientTestSupport {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(60)
   public void testCreateDynamicSenderToTopic() throws Exception {
      doTestCreateDynamicSender(true);
   }

   @Test
   @Timeout(60)
   public void testCreateDynamicSenderToQueue() throws Exception {
      doTestCreateDynamicSender(false);
   }

   @SuppressWarnings("unchecked")
   protected void doTestCreateDynamicSender(boolean topic) throws Exception {
      Target target = createDynamicTarget(topic);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(target);
      assertNotNull(sender);

      Target remoteTarget = (Target) sender.getEndpoint().getRemoteTarget();
      assertTrue(remoteTarget.getDynamic());
      assertEquals(TerminusDurability.NONE, remoteTarget.getDurable());
      assertEquals(TerminusExpiryPolicy.LINK_DETACH, remoteTarget.getExpiryPolicy());

      // Check the dynamic node lifetime-policy
      Map<Symbol, Object> dynamicNodeProperties = remoteTarget.getDynamicNodeProperties();
      assertTrue(dynamicNodeProperties.containsKey(LIFETIME_POLICY));
      assertEquals(DeleteOnClose.getInstance(), dynamicNodeProperties.get(LIFETIME_POLICY));

      assertNotNull(getProxyToAddress(remoteTarget.getAddress()));
      if (!topic) {
         assertNotNull(getProxyToQueue(remoteTarget.getAddress()));
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testDynamicSenderLifetimeBoundToLinkTopic() throws Exception {
      doTestDynamicSenderLifetimeBoundToLinkQueue(true);
   }

   @Test
   @Timeout(60)
   public void testDynamicSenderLifetimeBoundToLinkQueue() throws Exception {
      doTestDynamicSenderLifetimeBoundToLinkQueue(false);
   }

   protected void doTestDynamicSenderLifetimeBoundToLinkQueue(boolean topic) throws Exception {
      Target target = createDynamicTarget(topic);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(target);
      assertNotNull(sender);

      Target remoteTarget = (Target) sender.getEndpoint().getRemoteTarget();
      assertNotNull(getProxyToAddress(remoteTarget.getAddress()));
      if (!topic) {
         assertNotNull(getProxyToQueue(remoteTarget.getAddress()));
      }

      sender.close();

      assertNull(getProxyToAddress(remoteTarget.getAddress()));
      if (!topic) {
         assertNull(getProxyToQueue(remoteTarget.getAddress()));
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testCreateDynamicReceiverToTopic() throws Exception {
      doTestCreateDynamicSender(true);
   }

   @Test
   @Timeout(60)
   public void testCreateDynamicReceiverToQueue() throws Exception {
      doTestCreateDynamicSender(false);
   }

   @SuppressWarnings("unchecked")
   protected void doTestCreateDynamicReceiver(boolean topic) throws Exception {
      Source source = createDynamicSource(topic);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(source);
      assertNotNull(receiver);

      Source remoteSource = (Source) receiver.getEndpoint().getRemoteSource();
      assertTrue(remoteSource.getDynamic());
      assertEquals(TerminusDurability.NONE, remoteSource.getDurable());
      assertEquals(TerminusExpiryPolicy.LINK_DETACH, remoteSource.getExpiryPolicy());

      // Check the dynamic node lifetime-policy
      Map<Symbol, Object> dynamicNodeProperties = remoteSource.getDynamicNodeProperties();
      assertTrue(dynamicNodeProperties.containsKey(LIFETIME_POLICY));
      assertEquals(DeleteOnClose.getInstance(), dynamicNodeProperties.get(LIFETIME_POLICY));

      Queue queueView = getProxyToQueue(remoteSource.getAddress());
      assertNotNull(queueView);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testDynamicReceiverLifetimeBoundToLinkTopic() throws Exception {
      doTestDynamicReceiverLifetimeBoundToLinkQueue(true);
   }

   @Test
   @Timeout(60)
   public void testDynamicReceiverLifetimeBoundToLinkQueue() throws Exception {
      doTestDynamicReceiverLifetimeBoundToLinkQueue(false);
   }

   protected void doTestDynamicReceiverLifetimeBoundToLinkQueue(boolean topic) throws Exception {
      Source source = createDynamicSource(topic);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(source);
      assertNotNull(receiver);

      Source remoteSource = (Source) receiver.getEndpoint().getRemoteSource();
      Queue queueView = getProxyToQueue(remoteSource.getAddress());
      assertNotNull(queueView);

      receiver.close();

      queueView = getProxyToQueue(remoteSource.getAddress());
      assertNull(queueView);

      connection.close();
   }

   @Test
   @Timeout(60)
   public void TestCreateDynamicQueueSenderAndPublish() throws Exception {
      doTestCreateDynamicSenderAndPublish(false);
   }

   @Test
   @Timeout(60)
   public void TestCreateDynamicTopicSenderAndPublish() throws Exception {
      doTestCreateDynamicSenderAndPublish(true);
   }

   protected void doTestCreateDynamicSenderAndPublish(boolean topic) throws Exception {
      Target target = createDynamicTarget(topic);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(target);
      assertNotNull(sender);

      Target remoteTarget = (Target) sender.getEndpoint().getRemoteTarget();
      assertNotNull(getProxyToAddress(remoteTarget.getAddress()));
      if (!topic) {
         assertNotNull(getProxyToQueue(remoteTarget.getAddress()));
      }

      // Get the new address
      String address = sender.getSender().getRemoteTarget().getAddress();
      logger.debug("New dynamic sender address -> {}", address);

      // Create a message and send to a receive that is listening on the newly
      // created dynamic link address.
      AmqpMessage message = new AmqpMessage();
      message.setMessageId("msg-1");
      message.setText("Test-Message");

      AmqpReceiver receiver = session.createReceiver(address);
      receiver.flow(1);

      sender.send(message);

      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received, "Should have read a message");
      received.accept();

      receiver.close();
      sender.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testCreateDynamicReceiverToTopicAndSend() throws Exception {
      doTestCreateDynamicSender(true);
   }

   @Test
   @Timeout(60)
   public void testCreateDynamicReceiverToQueueAndSend() throws Exception {
      doTestCreateDynamicSender(false);
   }

   protected void doTestCreateDynamicReceiverAndSend(boolean topic) throws Exception {
      Source source = createDynamicSource(topic);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(source);
      assertNotNull(receiver);

      Source remoteSource = (Source) receiver.getEndpoint().getRemoteSource();
      Queue queueView = getProxyToQueue(remoteSource.getAddress());
      assertNotNull(queueView);

      // Get the new address
      String address = receiver.getReceiver().getRemoteSource().getAddress();
      logger.debug("New dynamic receiver address -> {}", address);

      // Create a message and send to a receive that is listening on the newly
      // created dynamic link address.
      AmqpMessage message = new AmqpMessage();
      message.setMessageId("msg-1");
      message.setText("Test-Message");

      AmqpSender sender = session.createSender(address);
      sender.send(message);

      receiver.flow(1);
      AmqpMessage received = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(received, "Should have read a message");
      received.accept();

      sender.close();
      receiver.close();

      connection.close();
   }
}
