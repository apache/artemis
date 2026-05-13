/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.apache.qpid.jms.provider.amqp.message.AmqpDestinationHelper.QUEUE_CAPABILITY;
import static org.apache.qpid.jms.provider.amqp.message.AmqpDestinationHelper.TOPIC_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class BrokerDefinedAnycastConsumerTest extends AmqpClientTestSupport {

   SimpleString address = SimpleString.of("testAddress");
   SimpleString queue1 = SimpleString.of("queue1");
   SimpleString queue2 = SimpleString.of("queue2");

   @Override
   protected boolean isAutoCreateQueues() {
      return false;
   }

   @Test
   @Timeout(60)
   public void testConsumeFromSingleQueueOnAddressSameName() throws Exception {
      server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST));

      sendMessages(address.toString(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(address.toString());
      receiver.flow(1);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(1, ((QueueImpl)server.getPostOffice().getBinding(address).getBindable()).getConsumerCount());

      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testConsumeFromSingleQueueOnAddressSameNameMultipleQueues() throws Exception {
      server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queue1).setAddress(address).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST));

      sendMessages(address.toString(), 2);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(address.toString());
      receiver.flow(1);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(1, ((QueueImpl)server.getPostOffice().getBinding(address).getBindable()).getConsumerCount());
      assertEquals(0, ((QueueImpl)server.getPostOffice().getBinding(queue1).getBindable()).getConsumerCount());
      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testConsumeFromSingleQueueOnAddressDifferentName() throws Exception {
      server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queue1).setAddress(address).setRoutingType(RoutingType.ANYCAST));

      sendMessages(address.toString(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(address.toString());
      receiver.flow(1);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(1, ((QueueImpl)server.getPostOffice().getBinding(queue1).getBindable()).getConsumerCount());

      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testConsumeFromSingleQueueOnAddressDifferentNameMultipleQueues() throws Exception {
      server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queue1).setAddress(address).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queue2).setAddress(address).setRoutingType(RoutingType.ANYCAST));

      sendMessages(address.toString(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(address.toString());
      receiver.flow(1);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(1, ((QueueImpl)server.getPostOffice().getBinding(queue1).getBindable()).getConsumerCount());
      assertEquals(0, ((QueueImpl)server.getPostOffice().getBinding(queue2).getBindable()).getConsumerCount());
      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testConsumeFromSingleQualifiedQueueOnAddressSameName() throws Exception {
      server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(queue1).setAddress(address).setRoutingType(RoutingType.ANYCAST));

      sendMessages(address.toString(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(address.toString() + "::" + queue1.toString());
      receiver.flow(1);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(1, ((QueueImpl)server.getPostOffice().getBinding(queue1).getBindable()).getConsumerCount());

      receiver.close();
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testConsumeWhenOnlyMulticast() throws Exception {
      server.addAddressInfo(new AddressInfo(address, RoutingType.MULTICAST));

      sendMessages(address.toString(), 1);

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      AmqpSession session = connection.createSession();
      Source jmsSource = createJmsSource(false);
      jmsSource.setAddress(address.toString());
      try {
         session.createReceiver(jmsSource);
         fail("should throw exception");
      } catch (Exception e) {
         //ignore
      }
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testConsumeWhenNoAddressCreatedNoAutoCreate() throws Exception {
      AddressSettings settings = new AddressSettings();
      settings.setAutoCreateAddresses(false);
      server.getAddressSettingsRepository().addMatch(address.toString(), settings);
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();
      try {
         session.createReceiver(address.toString());
         fail("should throw exception");
      } catch (Exception e) {
         //ignore
      }
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testConsumeWhenNoAddressCreatedAutoCreate() throws Exception {
      // This test needs auto-create.. for that just clear the settings and use defaults
      server.getAddressSettingsRepository().clear();
      AddressSettings settings = new AddressSettings();
      settings.setAutoCreateAddresses(true);
      settings.setDefaultAddressRoutingType(RoutingType.ANYCAST);
      server.getAddressSettingsRepository().addMatch(address.toString(), settings);
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();
      AmqpReceiver receiver = session.createReceiver(address.toString());
      sendMessages(address.toString(), 1);
      receiver.flow(1);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(1, ((QueueImpl)server.getPostOffice().getBinding(address).getBindable()).getConsumerCount());
      connection.close();
   }

   @Test
   @Timeout(60)
   public void testConsumeWhenNoAddressHasBothRoutingTypesButDefaultQueueIsMultiCast() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.ANYCAST);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      server.createQueue(QueueConfiguration.of(address));

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();
      try {
         session.createReceiver(address.toString());
         fail("expected exception");
      } catch (Exception e) {
         //ignore
      }
      connection.close();
   }

   protected Source createJmsSource(boolean topic) {

      Source source = new Source();
      // Set the capability to indicate the node type being created
      if (!topic) {
         source.setCapabilities(QUEUE_CAPABILITY);
      } else {
         source.setCapabilities(TOPIC_CAPABILITY);
      }

      return source;
   }
}
