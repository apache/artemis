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

import static org.apache.qpid.jms.provider.amqp.message.AmqpDestinationHelper.TOPIC_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.TerminusDurability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ClientDefinedMultiConsumerTest extends AmqpClientTestSupport {

   SimpleString address = SimpleString.of("testAddress");

   @Test
   @Timeout(60)
   public void test2ConsumersOnSharedVolatileAddress() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect("myClientId"));
      AmqpSession session = connection.createSession();
      Source source = createSharedSource(TerminusDurability.NONE);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      AmqpReceiver receiver2 = session.createMulticastReceiver(source, "myReceiverID", "mySub|2");
      receiver.flow(1);
      receiver2.flow(1);
      sendMessages(address.toString(), 2);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      amqpMessage = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(2, ((QueueImpl)server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub:shared-volatile")).getBindable()).getConsumerCount());
      receiver.close();
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub:shared-volatile")));
      receiver2.close();
      //check its been deleted
      Wait.waitFor(() -> server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub:shared-volatile")) == null, 1000);
      connection.close();
   }

   @Test
   @Timeout(60)
   public void test2ConsumersOnSharedVolatileAddressBrokerDefined() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      server.createQueue(QueueConfiguration.of("myClientId.mySub:shared-volatile").setAddress(address).setAutoCreateAddress(false));
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect("myClientId"));
      AmqpSession session = connection.createSession();
      Source source = createSharedSource(TerminusDurability.NONE);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      AmqpReceiver receiver2 = session.createMulticastReceiver(source, "myReceiverID", "mySub|1");
      receiver.flow(1);
      receiver2.flow(1);
      sendMessages(address.toString(), 2);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      amqpMessage = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(2, ((QueueImpl)server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub:shared-volatile")).getBindable()).getConsumerCount());
      receiver.close();
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub:shared-volatile")));
      receiver2.close();
      //check its **Hasn't** been deleted
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub:shared-volatile")));
      connection.close();
   }

   @Test
   @Timeout(60)
   public void test2ConsumersOnSharedVolatileAddressNoReceiverClose() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect("myClientId"));
      AmqpSession session = connection.createSession();
      Source source = createSharedSource(TerminusDurability.NONE);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      AmqpReceiver receiver2 = session.createMulticastReceiver(source, "myReceiverID", "mySub|2");
      receiver.flow(1);
      receiver2.flow(1);
      sendMessages(address.toString(), 2);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      amqpMessage = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(2, ((QueueImpl)server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub:shared-volatile")).getBindable()).getConsumerCount());
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub:shared-volatile")));
      //check its been deleted
      connection.close();
      Wait.waitFor(() -> server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub:shared-volatile")) == null, 1000);
   }

   @Test
   @Timeout(60)
   public void test2ConsumersOnSharedVolatileAddressGlobal() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect(false));
      AmqpSession session = connection.createSession();
      Source source = createSharedGlobalSource(TerminusDurability.NONE);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      AmqpReceiver receiver2 = session.createMulticastReceiver(source, "myReceiverID", "mySub|2");
      receiver.flow(1);
      receiver2.flow(1);
      sendMessages(address.toString(), 2);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      amqpMessage = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(2, ((QueueImpl)server.getPostOffice().getBinding(SimpleString.of("mySub:shared-volatile:global")).getBindable()).getConsumerCount());
      receiver.close();
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("mySub:shared-volatile:global")));
      receiver2.close();
      //check its been deleted
      Wait.waitFor(() -> server.getPostOffice().getBinding(SimpleString.of("mySub:shared-volatile:global")) == null, 1000);
      connection.close();
   }

   @Test
   @Timeout(60)
   public void test2ConsumersOnSharedDurableAddress() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect("myClientId"));
      AmqpSession session = connection.createSession();
      Source source = createSharedSource(TerminusDurability.CONFIGURATION);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      AmqpReceiver receiver2 = session.createMulticastReceiver(source, "myReceiverID", "mySub|2");
      receiver.flow(1);
      receiver2.flow(1);
      sendMessages(address.toString(), 2);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      amqpMessage = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(2, ((QueueImpl)server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")).getBindable()).getConsumerCount());
      receiver.close();
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")));
      receiver2.close();
      //check its been deleted
      assertNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")));
      connection.close();
   }

   @Test
   @Timeout(60)
   public void test2ConsumersOnSharedDurableAddressReconnect() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect("myClientId"));
      AmqpSession session = connection.createSession();
      Source source = createSharedSource(TerminusDurability.CONFIGURATION);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      AmqpReceiver receiver2 = session.createMulticastReceiver(source, "myReceiverID", "mySub|2");
      receiver.flow(1);
      receiver2.flow(1);
      sendMessages(address.toString(), 2);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      amqpMessage = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(2, ((QueueImpl)server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")).getBindable()).getConsumerCount());

      connection.close();

      connection = addConnection(client.connect("myClientId"));
      session = connection.createSession();

      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")));
      receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      receiver2 = session.createMulticastReceiver(source, "myReceiverID", "mySub|2");

      receiver.close();
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")));
      receiver2.close();
      //check its been deleted
      assertNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")));
      connection.close();
   }

   @Test
   @Timeout(60)
   public void test2ConsumersOnSharedDurableAddressReconnectwithNull() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect("myClientId"));
      AmqpSession session = connection.createSession();
      Source source = createSharedSource(TerminusDurability.CONFIGURATION);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      AmqpReceiver receiver2 = session.createMulticastReceiver(source, "myReceiverID", "mySub|2");
      receiver.flow(1);
      receiver2.flow(1);
      sendMessages(address.toString(), 2);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      amqpMessage = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(2, ((QueueImpl)server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")).getBindable()).getConsumerCount());

      connection.close();

      connection = addConnection(client.connect("myClientId"));
      session = connection.createSession();

      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")));
      receiver = session.createDurableReceiver(null, "mySub");
      receiver2 = session.createDurableReceiver(null, "mySub|2");

      receiver.close();
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")));
      receiver2.close();
      //check its been deleted
      assertNull(server.getPostOffice().getBinding(SimpleString.of("myClientId.mySub")));
      connection.close();
   }

   @Test
   @Timeout(60)
   public void test2ConsumersOnSharedDurableAddressGlobal() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect(false));
      AmqpSession session = connection.createSession();
      Source source = createSharedGlobalSource(TerminusDurability.CONFIGURATION);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      AmqpReceiver receiver2 = session.createMulticastReceiver(source, "myReceiverID", "mySub|2");
      receiver.flow(1);
      receiver2.flow(1);
      sendMessages(address.toString(), 2);
      AmqpMessage amqpMessage = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      amqpMessage = receiver2.receive(5, TimeUnit.SECONDS);
      assertNotNull(amqpMessage);
      assertEquals(2, ((QueueImpl)server.getPostOffice().getBinding(SimpleString.of("mySub:global")).getBindable()).getConsumerCount());
      receiver.close();
      assertNotNull(server.getPostOffice().getBinding(SimpleString.of("mySub:global")));
      receiver2.close();
      //check its been deleted
      assertNull(server.getPostOffice().getBinding(SimpleString.of("mySub:global")));
      connection.close();
   }

   @Test
   @Timeout(60)
   public void test2ConsumersOnNonSharedDurableAddress() throws Exception {
      AddressInfo addressInfo = new AddressInfo(address);
      addressInfo.getRoutingTypes().add(RoutingType.MULTICAST);
      server.addAddressInfo(addressInfo);
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect("myClientId"));
      AmqpSession session = connection.createSession();
      Source source = createNonSharedSource(TerminusDurability.CONFIGURATION);
      Source source1 = createSharedSource(TerminusDurability.CONFIGURATION);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      try {
         session.createMulticastReceiver(source1, "myReceiverID", "mySub|2");
         fail("Exception expected");
      } catch (Exception e) {
         //expected
      } finally {
         receiver.close();
      }

      connection.close();
   }

   @Test
   @Timeout(60)
   public void testAddressDoesntExist() throws Exception {
      AmqpClient client = createAmqpClient();

      AmqpConnection connection = addConnection(client.connect("myClientId"));
      AmqpSession session = connection.createSession();
      Source source = createNonSharedSource(TerminusDurability.CONFIGURATION);
      Source source1 = createSharedSource(TerminusDurability.CONFIGURATION);
      AmqpReceiver receiver = session.createMulticastReceiver(source, "myReceiverID", "mySub");
      try {
         session.createMulticastReceiver(source1, "myReceiverID", "mySub|2");
         fail("Exception expected");
      } catch (Exception e) {
         //expected
      } finally {
         receiver.close();
      }

      connection.close();
   }

   private Source createNonSharedSource(TerminusDurability terminusDurability) {
      Source source = new Source();
      source.setAddress(address.toString());
      source.setCapabilities(TOPIC_CAPABILITY);
      source.setDurable(terminusDurability);
      return source;
   }

   private Source createSharedSource(TerminusDurability terminusDurability) {
      Source source = new Source();
      source.setAddress(address.toString());
      source.setCapabilities(TOPIC_CAPABILITY, SHARED);
      source.setDurable(terminusDurability);
      return source;
   }

   private Source createSharedGlobalSource(TerminusDurability terminusDurability) {
      Source source = new Source();
      source.setAddress(address.toString());
      source.setCapabilities(TOPIC_CAPABILITY, SHARED, GLOBAL);
      source.setDurable(terminusDurability);
      return source;
   }
}
