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

import static org.apache.activemq.transport.amqp.AmqpSupport.COPY;
import static org.apache.activemq.transport.amqp.AmqpSupport.JMS_SELECTOR_NAME;
import static org.apache.activemq.transport.amqp.AmqpSupport.NO_LOCAL_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpFrameValidator;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.amqp.transport.Detach;
import org.apache.qpid.proton.engine.Receiver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Tests for broker side support of the Durable Subscription mapping for JMS.
 */
public class AmqpDurableReceiverTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String SELECTOR_STRING = "color = red";

   @Test
   @Timeout(60)
   public void testCreateDurableReceiver() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createDurableReceiver(getTopicName(), getSubscriptionName());
      receiver.flow(1);

      assertEquals(getTopicName(), lookupSubscription());

      AmqpSender sender = session.createSender(getTopicName());
      AmqpMessage message = new AmqpMessage();
      message.setMessageId("message:1");
      sender.send(message);

      message = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(message);

      connection.close();

      assertEquals(getTopicName(), lookupSubscription());
   }

   @Test
   @Timeout(60)
   public void testDetachedDurableReceiverRemainsActive() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      connection.setReceivedFrameInspector(new AmqpFrameValidator() {

         @Override
         public void inspectDetach(Detach detach, Binary encoded) {
            if (detach.getClosed()) {
               markAsInvalid("Remote should have detached but closed instead.");
            }
         }
      });

      connection.setSentFrameInspector(new AmqpFrameValidator() {

         @Override
         public void inspectDetach(Detach detach, Binary encoded) {
            if (detach.getClosed()) {
               markAsInvalid("Client should have detached but closed instead.");
            }
         }
      });

      AmqpSession session = connection.createSession();
      AmqpReceiver receiver = session.createDurableReceiver(getTopicName(), getSubscriptionName());

      assertEquals(getTopicName(), lookupSubscription());

      receiver.detach();

      assertEquals(getTopicName(), lookupSubscription());

      connection.getSentFrameInspector().assertValid();
      connection.getReceivedFrameInspector().assertValid();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testCloseDurableReceiverRemovesSubscription() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      AmqpSession session = connection.createSession();
      AmqpReceiver receiver = session.createDurableReceiver(getTopicName(), getSubscriptionName());

      assertEquals(getTopicName(), lookupSubscription());

      receiver.close();

      assertNull(lookupSubscription());

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testReattachToDurableNode() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      AmqpSession session = connection.createSession();
      AmqpReceiver receiver = session.createDurableReceiver(getTopicName(), getSubscriptionName());

      receiver.detach();

      receiver = session.createDurableReceiver(getTopicName(), getSubscriptionName());

      receiver.close();

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testLookupExistingSubscription() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      AmqpSession session = connection.createSession();
      AmqpReceiver receiver = session.createDurableReceiver(getTopicName(), getSubscriptionName());

      receiver.detach();

      receiver = session.lookupSubscription(getSubscriptionName());

      assertNotNull(receiver);

      Receiver protonReceiver = receiver.getReceiver();
      assertNotNull(protonReceiver.getRemoteSource());
      Source remoteSource = (Source) protonReceiver.getRemoteSource();

      if (remoteSource.getFilter() != null) {
         assertFalse(remoteSource.getFilter().containsKey(NO_LOCAL_NAME));
         assertFalse(remoteSource.getFilter().containsKey(JMS_SELECTOR_NAME));
      }

      assertEquals(TerminusExpiryPolicy.NEVER, remoteSource.getExpiryPolicy());
      assertEquals(TerminusDurability.UNSETTLED_STATE, remoteSource.getDurable());
      assertEquals(COPY, remoteSource.getDistributionMode());

      receiver.close();

      try {
         receiver = session.lookupSubscription(getSubscriptionName());
         fail("Should not be able to lookup the subscription");
      } catch (Exception e) {
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testLookupExistingSubscriptionWithSelector() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      AmqpSession session = connection.createSession();
      AmqpReceiver receiver = session.createDurableReceiver(getTopicName(), getSubscriptionName(), SELECTOR_STRING, false);

      receiver.detach();

      receiver = session.lookupSubscription(getSubscriptionName());

      assertNotNull(receiver);

      Receiver protonReceiver = receiver.getReceiver();
      assertNotNull(protonReceiver.getRemoteSource());
      Source remoteSource = (Source) protonReceiver.getRemoteSource();

      assertNotNull(remoteSource.getFilter());
      assertFalse(remoteSource.getFilter().containsKey(NO_LOCAL_NAME));
      assertTrue(remoteSource.getFilter().containsKey(JMS_SELECTOR_NAME));
      String selector = (String) ((DescribedType) remoteSource.getFilter().get(JMS_SELECTOR_NAME)).getDescribed();
      assertEquals(SELECTOR_STRING, selector);

      assertEquals(TerminusExpiryPolicy.NEVER, remoteSource.getExpiryPolicy());
      assertEquals(TerminusDurability.UNSETTLED_STATE, remoteSource.getDurable());
      assertEquals(COPY, remoteSource.getDistributionMode());

      receiver.close();

      try {
         receiver = session.lookupSubscription(getSubscriptionName());
         fail("Should not be able to lookup the subscription");
      } catch (Exception e) {
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testLookupExistingSubscriptionWithNoLocal() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      AmqpSession session = connection.createSession();
      AmqpReceiver receiver = session.createDurableReceiver(getTopicName(), getSubscriptionName(), null, true);

      receiver.detach();

      receiver = session.lookupSubscription(getSubscriptionName());

      assertNotNull(receiver);

      Receiver protonReceiver = receiver.getReceiver();
      assertNotNull(protonReceiver.getRemoteSource());
      Source remoteSource = (Source) protonReceiver.getRemoteSource();

      assertNotNull(remoteSource.getFilter());
      assertTrue(remoteSource.getFilter().containsKey(NO_LOCAL_NAME));
      assertFalse(remoteSource.getFilter().containsKey(JMS_SELECTOR_NAME));

      assertEquals(TerminusExpiryPolicy.NEVER, remoteSource.getExpiryPolicy());
      assertEquals(TerminusDurability.UNSETTLED_STATE, remoteSource.getDurable());
      assertEquals(COPY, remoteSource.getDistributionMode());

      receiver.close();

      try {
         receiver = session.lookupSubscription(getSubscriptionName());
         fail("Should not be able to lookup the subscription");
      } catch (Exception e) {
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testLookupExistingSubscriptionWithSelectorAndNoLocal() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      AmqpSession session = connection.createSession();
      AmqpReceiver receiver = session.createDurableReceiver(getTopicName(), getSubscriptionName(), SELECTOR_STRING, true);

      receiver.detach();

      receiver = session.lookupSubscription(getSubscriptionName());

      assertNotNull(receiver);

      Receiver protonReceiver = receiver.getReceiver();
      assertNotNull(protonReceiver.getRemoteSource());
      Source remoteSource = (Source) protonReceiver.getRemoteSource();

      assertNotNull(remoteSource.getFilter());
      assertTrue(remoteSource.getFilter().containsKey(NO_LOCAL_NAME));
      assertTrue(remoteSource.getFilter().containsKey(JMS_SELECTOR_NAME));
      String selector = (String) ((DescribedType) remoteSource.getFilter().get(JMS_SELECTOR_NAME)).getDescribed();
      assertEquals(SELECTOR_STRING, selector);

      assertEquals(TerminusExpiryPolicy.NEVER, remoteSource.getExpiryPolicy());
      assertEquals(TerminusDurability.UNSETTLED_STATE, remoteSource.getDurable());
      assertEquals(COPY, remoteSource.getDistributionMode());

      receiver.close();

      try {
         receiver = session.lookupSubscription(getSubscriptionName());
         fail("Should not be able to lookup the subscription");
      } catch (Exception e) {
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testLookupNonExistingSubscription() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      AmqpSession session = connection.createSession();

      try {
         session.lookupSubscription(getSubscriptionName());
         fail("Should throw an exception since there is not subscription");
      } catch (Exception e) {
         logger.debug("Error on lookup: {}", e.getMessage());
      }

      connection.close();
   }

   public String lookupSubscription() {
      Binding binding = server.getPostOffice().getBinding(SimpleString.of(getContainerID() + "." + getSubscriptionName()));
      if (binding != null) {
         return binding.getAddress().toString();
      }

      return null;
   }

   private String getContainerID() {
      return "myContainerID";
   }

   private String getSubscriptionName() {
      return "mySubscription";
   }
}
