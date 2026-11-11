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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ResourceAllocationException;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpFlowControlTest extends JMSClientTestSupport {

   private static final long MAX_SIZE_BYTES = 1 * 1024 * 1024;
   private static final long MAX_SIZE_BYTES_REJECT_THRESHOLD = 2 * 1024 * 1024;

   private String singleCreditAcceptorURI = new String("tcp://localhost:" + (AMQP_PORT + 8));
   private int messagesSent;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      messagesSent = 0;
   }

   @Override
   protected void addAdditionalAcceptors(ActiveMQServer server) throws Exception {
      server.getConfiguration().addAcceptorConfiguration("flow", singleCreditAcceptorURI + "?protocols=AMQP;useEpoll=false;amqpCredits=1;amqpLowCredits=1");
   }

   @Override
   protected void configureAddressPolicy(ActiveMQServer server) {
      // For BLOCK tests
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch("#");
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
      addressSettings.setMaxSizeBytes(MAX_SIZE_BYTES);
      addressSettings.setMaxSizeBytesRejectThreshold(MAX_SIZE_BYTES_REJECT_THRESHOLD);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);
   }

   @Test
   @Timeout(60)
   public void testCreditsAreAllocatedOnceOnLinkCreated() throws Exception {
      AmqpClient client = createAmqpClient(new URI(singleCreditAcceptorURI));
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());
         assertEquals(1, sender.getSender().getCredit(), "Should only be issued one credit");
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testCreditIsNotGivenOnLinkCreationWhileBlockedAndIsGivenOnceThenUnblocked() throws Exception {
      AmqpClient client = createAmqpClient(new URI(singleCreditAcceptorURI));
      AmqpConnection connection = addConnection(client.connect());

      try {
         AddressControl addressControl = ManagementControlHelper.createAddressControl(SimpleString.of(getQueueName()), mBeanServer);
         addressControl.block();
         AmqpSession session = connection.createSession();
         final AmqpSender sender = session.createSender(getQueueName());
         assertTrue(Wait.waitFor(() -> 0 == sender.getSender().getCredit(), 5000, 20), "Should get 0 credit");

         addressControl.unblock();
         assertTrue(Wait.waitFor(() -> 1 == sender.getSender().getCredit(), 5000, 20), "Should now get issued one credit");
         sender.close();

         AmqpSender sender2 = session.createSender(getQueueName());
         assertEquals(1, sender2.getSender().getCredit(), "Should only be issued one credit");
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testCreditsAreNotAllocatedWhenAddressIsFull() throws Exception {
      AmqpClient client = createAmqpClient(new URI(singleCreditAcceptorURI));
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         // Use blocking send to ensure buffered messages do not interfere with credit.
         sender.setSendTimeout(-1);
         sendUntilFull(sender);

         // This should be -1. A single message is buffered in the client, and 0 credit has been allocated.
         assertEquals(-1, sender.getSender().getCredit());

         long addressSize = server.getPagingManager().getPageStore(SimpleString.of(getQueueName())).getAddressSize();
         assertTrue(addressSize >= MAX_SIZE_BYTES && addressSize <= MAX_SIZE_BYTES_REJECT_THRESHOLD);
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testAddressIsBlockedForOtherProducersWhenFull() throws Exception {
      Connection connection = createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination d = session.createQueue(getQueueName());
      MessageProducer p = session.createProducer(d);

      fillAddress(getQueueName());

      Exception e = null;
      try {
         p.send(session.createBytesMessage());
      } catch (ResourceAllocationException rae) {
         e = rae;
      }
      assertInstanceOf(ResourceAllocationException.class, e);
      assertTrue(e.getMessage().contains("resource-limit-exceeded"));

      long addressSize = server.getPagingManager().getPageStore(SimpleString.of(getQueueName())).getAddressSize();
      assertTrue(addressSize >= MAX_SIZE_BYTES_REJECT_THRESHOLD);
   }

   @Test
   @Timeout(60)
   public void testSendBlocksWhenAddressBlockedAndCompletesAfterUnblocked() throws Exception {
      Connection connection = createConnection(new URI(singleCreditAcceptorURI.replace("tcp", "amqp")), null, null, null, true);
      final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination d = session.createQueue(getQueueName());
      final MessageProducer p = session.createProducer(d);

      final CountDownLatch running = new CountDownLatch(1);
      final CountDownLatch done = new CountDownLatch(1);

      AddressControl addressControl = ManagementControlHelper.createAddressControl(SimpleString.of(getQueueName()), mBeanServer);

      assertTrue(addressControl.block(), "blocked ok");

      // one credit
      p.send(session.createBytesMessage());

      // this send will block, no credit
      new Thread(() -> {
         try {
            running.countDown();
            p.send(session.createBytesMessage());
         } catch (JMSException ignored) {
         } finally {
            done.countDown();
         }
      }).start();

      assertTrue(running.await(5, TimeUnit.SECONDS));

      assertFalse(done.await(200, TimeUnit.MILLISECONDS));

      addressControl.unblock();

      assertTrue(done.await(5, TimeUnit.SECONDS));

      // good to go again
      p.send(session.createBytesMessage());

      assertEquals(3, addressControl.getMessageCount());
   }

   @Test
   @Timeout(60)
   public void testCreditsAreRefreshedWhenAddressIsUnblocked() throws Exception {
      fillAddress(getQueueName());

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         // Wait for a potential flow frame.
         Thread.sleep(500);
         assertEquals(0, sender.getSender().getCredit());

         // Empty Address except for 1 message used later.
         AmqpReceiver receiver = session.createReceiver(getQueueName());
         receiver.flow(100);

         AmqpMessage m;
         for (int i = 0; i < messagesSent - 1; i++) {
            m = receiver.receive(5000, TimeUnit.MILLISECONDS);
            m.accept();
         }

         // Wait for address to unblock and flow frame to arrive
         Thread.sleep(500);

         assertTrue(sender.getSender().getCredit() >= 0);
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testNewLinkAttachAreNotAllocatedCreditsWhenAddressIsBlocked() throws Exception {
      fillAddress(getQueueName());

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());

         // Wait for a potential flow frame.
         Thread.sleep(1000);
         assertEquals(0, sender.getSender().getCredit());
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testTxIsRolledBackOnRejectedPreSettledMessage() throws Throwable {

      // Create the link attach before filling the address to ensure the link is allocated credit.
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      AmqpSession session = connection.createSession();
      AmqpSender sender = session.createSender(getQueueName());
      sender.setPresettle(true);

      fillAddress(getQueueName());

      final AmqpMessage message = new AmqpMessage();
      byte[] payload = new byte[50 * 1024];
      message.setBytes(payload);

      Exception expectedException = null;
      try {
         session.begin();
         sender.send(message);
         session.commit();
      } catch (Exception e) {
         expectedException = e;
      } finally {
         connection.close();
      }

      assertNotNull(expectedException);
      assertTrue(expectedException.getMessage().contains("resource-limit-exceeded"));
      assertTrue(expectedException.getMessage().contains("Address is full: " + getQueueName()));
   }

   // Fills an address.  Careful when using this method.  Only use when rejected messages are switched on.
   private void fillAddress(String address) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      Exception exception = null;
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(address);
         sendUntilFull(sender);
      } catch (Exception e) {
         exception = e;
      } finally {
         connection.close();
      }

      // Should receive a rejected error
      assertNotNull(exception);
      assertTrue(exception.getMessage().contains("amqp:resource-limit-exceeded"));
   }

   private void sendUntilFull(final AmqpSender sender) throws Exception {
      final AmqpMessage message = new AmqpMessage();
      byte[] payload = new byte[50 * 1024];
      message.setBytes(payload);

      final int maxMessages = 50;
      final AtomicInteger sentMessages = new AtomicInteger(0);
      final Exception[] errors = new Exception[1];
      final CountDownLatch timeout = new CountDownLatch(1);

      Runnable sendMessages = () -> {
         try {
            for (int i = 0; i < maxMessages; i++) {
               sender.send(message);
               sentMessages.getAndIncrement();
            }
            timeout.countDown();
         } catch (IOException e) {
            errors[0] = e;
         }
      };

      Thread t = new Thread(sendMessages);

      try {
         t.start();

         timeout.await(1, TimeUnit.SECONDS);

         messagesSent = sentMessages.get();
         if (errors[0] != null) {
            throw errors[0];
         }
      } finally {
         t.interrupt();
         t.join(1000);
         assertFalse(t.isAlive());
      }
   }
}
