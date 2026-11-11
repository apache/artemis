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

import static org.apache.activemq.transport.amqp.AmqpSupport.JMS_SELECTOR_FILTER_IDS;
import static org.apache.activemq.transport.amqp.AmqpSupport.NO_LOCAL_FILTER_IDS;
import static org.apache.activemq.transport.amqp.AmqpSupport.findFilter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.JMSException;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.activemq.transport.amqp.client.AmqpUnknownFilterType;
import org.apache.activemq.transport.amqp.client.AmqpValidator;
import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Test various behaviors of AMQP receivers with the broker.
 */
public class AmqpReceiverTest extends AmqpClientTestSupport {

   @Test
   @Timeout(60)
   public void testCreateQueueReceiver() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getQueueName());

      Queue queue = getProxyToQueue(getQueueName());
      assertNotNull(queue);

      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testCreateTopicReceiver() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(getTopicName());

      Queue queue = getProxyToQueue(getQueueName());
      assertNotNull(queue);

      receiver.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testCreateQueueReceiverWithNoLocalSet() throws Exception {
      AmqpClient client = createAmqpClient();

      client.setValidator(new AmqpValidator() {

         @SuppressWarnings("unchecked")
         @Override
         public void inspectOpenedResource(Receiver receiver) {

            if (receiver.getRemoteSource() == null) {
               markAsInvalid("Link opened with null source.");
            }

            Source source = (Source) receiver.getRemoteSource();
            Map<Symbol, Object> filters = source.getFilter();

            // Currently don't support noLocal on a Queue
            if (findFilter(filters, NO_LOCAL_FILTER_IDS) != null) {
               markAsInvalid("Broker did not return the NoLocal Filter on Attach");
            }
         }
      });

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      session.createReceiver(getQueueName(), null, true);

      connection.getStateInspector().assertValid();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testCreateQueueReceiverWithJMSSelector() throws Exception {
      AmqpClient client = createAmqpClient();

      client.setValidator(new AmqpValidator() {

         @SuppressWarnings("unchecked")
         @Override
         public void inspectOpenedResource(Receiver receiver) {

            if (receiver.getRemoteSource() == null) {
               markAsInvalid("Link opened with null source.");
            }

            Source source = (Source) receiver.getRemoteSource();
            Map<Symbol, Object> filters = source.getFilter();

            if (findFilter(filters, JMS_SELECTOR_FILTER_IDS) == null) {
               markAsInvalid("Broker did not return the JMS Filter on Attach");
            }
         }
      });

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      session.createReceiver(getQueueName(), "JMSPriority > 8");

      connection.getStateInspector().assertValid();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testInvalidFilter() throws Exception {
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      try {
         session.createReceiver(getQueueName(), "null = 'f''", true);
         fail("should throw exception");
      } catch (Exception e) {
         assertInstanceOf(JMSException.class, e.getCause());
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testSenderSettlementModeSettledIsHonored() throws Exception {
      doTestSenderSettlementModeIsHonored(SenderSettleMode.SETTLED);
   }

   @Test
   @Timeout(60)
   public void testSenderSettlementModeUnsettledIsHonored() throws Exception {
      doTestSenderSettlementModeIsHonored(SenderSettleMode.UNSETTLED);
   }

   @Test
   @Timeout(60)
   public void testSenderSettlementModeMixedIsHonored() throws Exception {
      doTestSenderSettlementModeIsHonored(SenderSettleMode.MIXED);
   }

   public void doTestSenderSettlementModeIsHonored(SenderSettleMode settleMode) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver("queue://" + getTestName(), settleMode, ReceiverSettleMode.FIRST);

      Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);
      assertEquals(0, queueView.getMessageCount());
      assertEquals(1, server.getTotalConsumerCount());

      assertEquals(settleMode, receiver.getEndpoint().getRemoteSenderSettleMode());

      receiver.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiverSettlementModeSetToFirst() throws Exception {
      doTestReceiverSettlementModeForcedToFirst(ReceiverSettleMode.FIRST);
   }

   @Test
   @Timeout(60)
   public void testReceiverSettlementModeSetToSecond() throws Exception {
      doTestReceiverSettlementModeForcedToFirst(ReceiverSettleMode.SECOND);
   }

   /*
    * The Broker does not currently support ReceiverSettleMode of SECOND so we ensure that it
    * always drops that back to FIRST to let the client know. The client will need to check and
    * react accordingly.
    */
   private void doTestReceiverSettlementModeForcedToFirst(ReceiverSettleMode modeToUse) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver("queue://" + getTestName(), SenderSettleMode.MIXED, modeToUse);

      Queue queueView = getProxyToQueue(getQueueName());
      assertNotNull(queueView);
      assertEquals(0, queueView.getMessageCount());
      assertEquals(1, server.getTotalConsumerCount());

      assertEquals(ReceiverSettleMode.FIRST, receiver.getEndpoint().getRemoteReceiverSettleMode());

      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testClientIdIsSetInSubscriptionList() throws Exception {
      server.addAddressInfo(new AddressInfo(SimpleString.of("mytopic"), RoutingType.ANYCAST));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      connection.setContainerId("testClient");
      connection.connect();

      try {
         AmqpSession session = connection.createSession();

         Source source = new Source();
         source.setDurable(TerminusDurability.UNSETTLED_STATE);
         source.setCapabilities(Symbol.getSymbol("topic"));
         source.setAddress("mytopic");
         session.createReceiver(source, "testSub");

         SimpleString fo = SimpleString.of("testClient.testSub:mytopic");
         assertNotNull(server.locateQueue(fo));

      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testLinkDetachSentWhenQueueDeleted() throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpReceiver receiver = session.createReceiver(getQueueName());

         server.destroyQueue(SimpleString.of(getQueueName()), null, false, true);

         Wait.assertTrue("Receiver should have closed", receiver::isClosed);
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testLinkDetatchErrorIsCorrectWhenQueueDoesNotExists() throws Exception {
      AddressSettings value = new AddressSettings();
      value.setAutoCreateQueues(false);
      value.setAutoCreateAddresses(false);
      server.getAddressSettingsRepository().addMatch("AnAddressThatDoesNotExist", value);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();

         Exception expectedException = null;
         try {
            session.createSender("AnAddressThatDoesNotExist");
            fail("Creating a sender here on an address that doesn't exist should fail");
         } catch (Exception e) {
            expectedException = e;
         }

         assertNotNull(expectedException);
         assertTrue(expectedException.getMessage().contains("amqp:not-found"));
         assertTrue(expectedException.getMessage().contains("target address AnAddressThatDoesNotExist does not exist"));
      } finally {
         connection.close();
      }
   }

   @Test
   @Timeout(60)
   public void testUnsupportedFiltersAreNotListedAsSupported() throws Exception {
      AmqpClient client = createAmqpClient();

      client.setValidator(new AmqpValidator() {

         @SuppressWarnings("unchecked")
         @Override
         public void inspectOpenedResource(Receiver receiver) {

            if (receiver.getRemoteSource() == null) {
               markAsInvalid("Link opened with null source.");
            }

            Source source = (Source) receiver.getRemoteSource();
            Map<Symbol, Object> filters = source.getFilter();

            if (findFilter(filters, AmqpUnknownFilterType.UNKNOWN_FILTER_IDS) != null) {
               markAsInvalid("Broker should not return unsupported filter on attach.");
            }
         }
      });

      Map<Symbol, DescribedType> filters = new HashMap<>();
      filters.put(AmqpUnknownFilterType.UNKNOWN_FILTER_NAME, AmqpUnknownFilterType.UNKNOWN_FILTER);

      Source source = new Source();
      source.setAddress(getQueueName());
      source.setFilter(filters);
      source.setDurable(TerminusDurability.NONE);
      source.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);

      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      assertEquals(0, server.getTotalConsumerCount());

      session.createReceiver(source);

      assertEquals(1, server.getTotalConsumerCount());

      connection.getStateInspector().assertValid();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReceiverCloseSendsRemoteClose() throws Exception {
      AmqpClient client = createAmqpClient();
      assertNotNull(client);

      final AtomicBoolean closed = new AtomicBoolean();

      client.setValidator(new AmqpValidator() {

         @Override
         public void inspectClosedResource(Session session) {
         }

         @Override
         public void inspectDetachedResource(Receiver receiver) {
            markAsInvalid("Broker should not detach receiver linked to closed session.");
         }

         @Override
         public void inspectClosedResource(Receiver receiver) {
            closed.set(true);
         }
      });

      AmqpConnection connection = addConnection(client.connect());
      assertNotNull(connection);
      AmqpSession session = connection.createSession();
      assertNotNull(session);
      AmqpReceiver receiver = session.createReceiver(getQueueName());
      assertNotNull(receiver);

      receiver.close();

      assertTrue(closed.get(), "Did not process remote close as expected");
      connection.getStateInspector().assertValid();

      connection.close();
   }
}