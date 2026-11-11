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
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
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
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.qpid.jms.provider.amqp.message.AmqpDestinationHelper.QUEUE_CAPABILITY;
import static org.apache.qpid.jms.provider.amqp.message.AmqpDestinationHelper.TOPIC_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(ParameterizedTestExtension.class)
public class AutoCreateWithDefaultRoutingTypesTest extends JMSClientTestSupport {

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
      // Don't create anything by default since we are testing auto create
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
   @Timeout(30)
   public void testCreateSender() throws Exception {
      final String addressName = getTestName();

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(addressName);

      AddressInfo address = getProxyToAddress(addressName);

      assertNotNull(address);
      assertEquals(Set.of(routingType), address.getRoutingTypes());

      final AmqpReceiver receiver = session.createReceiver(addressName);
      receiver.flow(1);

      final AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg:1");
      message.setText("Test-Message");

      sender.send(message);

      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      sender.close();
      connection.close();
   }

   @TestTemplate
   @Timeout(30)
   public void testCreateReceiver() throws Exception {
      final String addressName = getTestName();

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final AmqpReceiver receiver = session.createReceiver(addressName);
      receiver.flow(1);

      AddressInfo address = getProxyToAddress(addressName);

      assertNotNull(address);
      assertEquals(Set.of(routingType), address.getRoutingTypes());

      final AmqpSender sender = session.createSender(addressName);
      final AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg:1");
      message.setText("Test-Message");

      sender.send(message);

      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      sender.close();
      receiver.close();
      connection.close();
   }

   @TestTemplate
   @Timeout(30)
   public void testCreateSenderThatRequestsMultiCast() throws Exception {
      dotestCreateSenderThatRequestsSpecificRoutingType(RoutingType.MULTICAST);
   }

   @TestTemplate
   @Timeout(30)
   public void testCreateSenderThatRequestsAnyCast() throws Exception {
      dotestCreateSenderThatRequestsSpecificRoutingType(RoutingType.ANYCAST);
   }

   private void dotestCreateSenderThatRequestsSpecificRoutingType(RoutingType routingType) throws Exception {
      final String addressName = getTestName();

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      Target target = new Target();
      target.setAddress(addressName);
      if (routingType == RoutingType.ANYCAST) {
         target.setCapabilities(QUEUE_CAPABILITY);
      } else {
         target.setCapabilities(TOPIC_CAPABILITY);
      }

      AmqpSender sender = session.createSender(target);

      AddressInfo address = getProxyToAddress(addressName);

      assertNotNull(address);
      assertEquals(Set.of(routingType), address.getRoutingTypes());

      final AmqpReceiver receiver = session.createReceiver(addressName);
      receiver.flow(1);

      final AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg:1");
      message.setText("Test-Message");

      sender.send(message);

      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      sender.close();
      connection.close();
   }

   @TestTemplate
   @Timeout(30)
   public void testCreateReceiverThatRequestsMultiCast() throws Exception {
      dotestCreateReceiverThatRequestsSpecificRoutingType(RoutingType.MULTICAST);
   }

   @TestTemplate
   @Timeout(30)
   public void testCreateReceiverThatRequestsAnyCast() throws Exception {
      dotestCreateReceiverThatRequestsSpecificRoutingType(RoutingType.ANYCAST);
   }

   private void dotestCreateReceiverThatRequestsSpecificRoutingType(RoutingType routingType) throws Exception {
      final String addressName = getTestName();

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      Source source = new Source();
      source.setAddress(addressName);
      if (routingType == RoutingType.ANYCAST) {
         source.setCapabilities(QUEUE_CAPABILITY);
      } else {
         source.setCapabilities(TOPIC_CAPABILITY);
      }

      final AmqpReceiver receiver = session.createReceiver(source);
      receiver.flow(1);

      AddressInfo address = getProxyToAddress(addressName);

      assertNotNull(address);
      assertEquals(Set.of(routingType), address.getRoutingTypes());

      final AmqpSender sender = session.createSender(addressName);
      final AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg:1");
      message.setText("Test-Message");

      sender.send(message);

      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      sender.close();
      receiver.close();
      connection.close();
   }

   @TestTemplate
   @Timeout(30)
   public void testCreateSenderThatRequestsMultiCastViaPrefix() throws Exception {
      dotestCreateSenderThatRequestsSpecificRoutingTypeViaPrefix(RoutingType.MULTICAST);
   }

   @TestTemplate
   @Timeout(30)
   public void testCreateSenderThatRequestsAnyCastViaPrefix() throws Exception {
      dotestCreateSenderThatRequestsSpecificRoutingTypeViaPrefix(RoutingType.ANYCAST);
   }

   private void dotestCreateSenderThatRequestsSpecificRoutingTypeViaPrefix(RoutingType routingType) throws Exception {
      final String addressName = getTestName();

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final String prefixedName;
      if (routingType == RoutingType.ANYCAST) {
         prefixedName = ANYCAST_PREFIX + addressName;
      } else {
         prefixedName = MULTICAST_PREFIX + addressName;
      }

      AmqpSender sender = session.createSender(prefixedName);

      AddressInfo address = getProxyToAddress(addressName);

      assertNotNull(address);
      assertEquals(Set.of(routingType), address.getRoutingTypes());

      final AmqpReceiver receiver = session.createReceiver(addressName);
      receiver.flow(1);

      final AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg:1");
      message.setText("Test-Message");

      sender.send(message);

      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      sender.close();
      receiver.close();
      connection.close();
   }

   @TestTemplate
   @Timeout(30)
   public void testCreateReceiverThatRequestsMultiCastViaPrefix() throws Exception {
      dotestCreateReceiverThatRequestsSpecificRoutingTypeViaPrefix(RoutingType.MULTICAST);
   }

   @TestTemplate
   @Timeout(30)
   public void testCreateReceiverThatRequestsAnyCastViaPrefix() throws Exception {
      dotestCreateReceiverThatRequestsSpecificRoutingTypeViaPrefix(RoutingType.ANYCAST);
   }

   private void dotestCreateReceiverThatRequestsSpecificRoutingTypeViaPrefix(RoutingType routingType) throws Exception {
      final String addressName = getTestName();

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      final String prefixedName;
      if (routingType == RoutingType.ANYCAST) {
         prefixedName = ANYCAST_PREFIX + addressName;
      } else {
         prefixedName = MULTICAST_PREFIX + addressName;
      }

      final AmqpReceiver receiver = session.createReceiver(prefixedName);
      receiver.flow(1);

      AddressInfo address = getProxyToAddress(addressName);

      assertNotNull(address);
      assertEquals(Set.of(routingType), address.getRoutingTypes());

      final AmqpSender sender = session.createSender(addressName);
      final AmqpMessage message = new AmqpMessage();

      message.setMessageId("msg:1");
      message.setText("Test-Message");

      sender.send(message);

      assertNotNull(receiver.receive(5, TimeUnit.SECONDS));

      sender.close();
      receiver.close();
      connection.close();
   }
}
