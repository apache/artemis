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
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(ParameterizedTestExtension.class)
public class AmqpDurableReceiverReconnectWithMulticastPrefixTest extends JMSClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Parameters(name = "routingType={0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
         {RoutingType.ANYCAST}, {RoutingType.MULTICAST}
      });
   }

   @Parameter(index = 0)
   public RoutingType routingType;

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP";
   }

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      // Don't create anything by default since our test relies on prefixes to define routing types.
   }

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("anycastPrefix", ANYCAST_PREFIX);
      params.put("multicastPrefix", MULTICAST_PREFIX);
   }

   @Override
   protected void configureAddressPolicy(ActiveMQServer server) {
      Configuration serverConfig = server.getConfiguration();
      serverConfig.setJournalType(JournalType.NIO);
      Map<String, AddressSettings> map = serverConfig.getAddressSettings();
      if (map.isEmpty()) {
         AddressSettings as = new AddressSettings();
         map.put("#", as);
      }
      Map.Entry<String, AddressSettings> entry = map.entrySet().iterator().next();
      AddressSettings settings = entry.getValue();
      settings.setAutoCreateQueues(true);
      settings.setDefaultAddressRoutingType(routingType);
      logger.info("server config, isauto? {}", entry.getValue().isAutoCreateQueues());
      logger.info("server config, default address routing type? {}", entry.getValue().getDefaultAddressRoutingType());
   }

   @TestTemplate
   @Timeout(60)
   public void testReattachToDurableNodeAndTryAndReceiveNewlySentMessage() throws Exception {
      final String addressName = "test-address";
      final String prefixedName = MULTICAST_PREFIX + addressName;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      final AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createDurableReceiver(prefixedName, getSubscriptionName());

      receiver.detach();

      AddressInfo address = getProxyToAddress(addressName);

      assertNotNull(address);
      assertEquals(Set.of(RoutingType.MULTICAST), address.getRoutingTypes());

      assertEquals(0, lookupSubscriptionQueue().getMessageCount());

      // Recover without lookup as a non-JMS client might do
      receiver = session.createDurableReceiver(prefixedName, getSubscriptionName());
      receiver.flow(1);

      assertEquals(0, lookupSubscriptionQueue().getMessageCount());

      final AmqpSender sender = session.createSender(addressName);
      final AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg:1");
      message.setText("Test-Message");

      sender.send(message);

      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      assertEquals(1, lookupSubscriptionQueue().getDeliveringCount());

      sender.close();
      receiver.close();

      connection.close();
   }

   @TestTemplate
   @Timeout(60)
   public void testReattachToDurableNodeAndTryAndReceivePreviouslySentMessage() throws Exception {
      final String addressName = "test-address";
      final String prefixedName = MULTICAST_PREFIX + addressName;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.setContainerId(getContainerID());
      connection.connect();

      final AmqpSession session = connection.createSession();

      // Recover without lookup as a non-JMS client might do
      AmqpReceiver receiver = session.createDurableReceiver(prefixedName, getSubscriptionName());

      receiver.detach();

      AddressInfo address = getProxyToAddress(addressName);

      assertNotNull(address);
      assertEquals(Set.of(RoutingType.MULTICAST), address.getRoutingTypes());

      assertEquals(0, lookupSubscriptionQueue().getMessageCount());

      final AmqpSender sender = session.createSender(addressName);
      final AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg:1");
      message.setText("Test-Message");

      sender.send(message);

      assertEquals(1, lookupSubscriptionQueue().getMessageCount());

      receiver = session.createDurableReceiver(prefixedName, getSubscriptionName());

      receiver.flow(1);
      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      assertEquals(1, lookupSubscriptionQueue().getDeliveringCount());

      sender.close();
      receiver.close();

      connection.close();
   }

   private String getContainerID() {
      return "myContainerID";
   }

   private String getSubscriptionName() {
      return "mySubscription";
   }

   private Queue lookupSubscriptionQueue() {
      Binding binding = server.getPostOffice().getBinding(SimpleString.of(getContainerID() + "." + getSubscriptionName()));
      if (binding instanceof LocalQueueBinding localQueueBinding) {
         return localQueueBinding.getQueue();
      }

      throw new AssertionError("Should have found an existing queue binding for the durable subscription");
   }
}
