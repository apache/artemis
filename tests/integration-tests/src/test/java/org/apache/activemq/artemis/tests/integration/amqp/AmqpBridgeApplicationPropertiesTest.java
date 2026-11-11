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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.TransformerConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AmqpBridgeApplicationPropertiesTest extends AmqpClientTestSupport {

   public static class DivertApplicationPropertiesTransformer implements Transformer {

      public static final String TRX_ID = "trxId";

      @Override
      public Message transform(final Message message) {

         message.putStringProperty("A", "1");
         message.putStringProperty("B", "2");
         message.reencode();

         return message;
      }
   }
   public static class BridgeApplicationPropertiesTransformer implements Transformer {

      @Override
      public Message transform(final Message message) {

         message.putStringProperty("C", "3");
         message.putStringProperty("D", "4");
         message.reencode();

         return message;
      }
   }
   private ActiveMQServer server0;
   private ActiveMQServer server1;

   private SimpleString customNotificationQueue;
   private SimpleString frameworkNotificationsQueue;
   private SimpleString bridgeNotificationsQueue;
   private SimpleString notificationsQueue;

   private String getServer0URL() {
      return "tcp://localhost:61616";
   }

   private String getServer1URL() {
      return "tcp://localhost:61617";
   }

   @Override
   public URI getBrokerAmqpConnectionURI() {
      try {
         return new URI(getServer0URL());
      } catch (URISyntaxException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server0 = createServer(false, createBasicConfig());
      server1 = createServer(false, createBasicConfig());

      server0.getConfiguration().addAcceptorConfiguration("acceptor", getServer0URL());
      server0.getConfiguration().addConnectorConfiguration("notification-broker", getServer1URL());
      server1.getConfiguration().addAcceptorConfiguration("acceptor", getServer1URL());

      DivertConfiguration customNotificationsDivert = new DivertConfiguration().setName("custom-notifications-divert").setAddress("*.Provider.*.Agent.*.CustomNotification").setForwardingAddress("FrameworkNotifications").setExclusive(true).setTransformerConfiguration(new TransformerConfiguration(DivertApplicationPropertiesTransformer.class.getName()));
      DivertConfiguration frameworkNotificationsDivert = new DivertConfiguration().setName("framework-notifications-divert").setAddress("BridgeNotifications").setForwardingAddress("Notifications").setRoutingType(ComponentConfigurationRoutingType.MULTICAST).setExclusive(true);

      server0.getConfiguration().addDivertConfiguration(customNotificationsDivert);
      server1.getConfiguration().addDivertConfiguration(frameworkNotificationsDivert);

      customNotificationQueue = SimpleString.of("*.Provider.*.Agent.*.CustomNotification");
      frameworkNotificationsQueue = SimpleString.of("FrameworkNotifications");
      bridgeNotificationsQueue = SimpleString.of("BridgeNotifications");
      notificationsQueue = SimpleString.of("Notifications");

      server0.start();
      server1.start();

      server0.createQueue(QueueConfiguration.of(customNotificationQueue).setRoutingType(RoutingType.ANYCAST));
      server0.createQueue(QueueConfiguration.of(frameworkNotificationsQueue).setRoutingType(RoutingType.ANYCAST));
      server1.createQueue(QueueConfiguration.of(bridgeNotificationsQueue).setRoutingType(RoutingType.ANYCAST));
      server1.createQueue(QueueConfiguration.of(notificationsQueue));

      server0.deployBridge(new BridgeConfiguration().setName("notifications-bridge").setQueueName(frameworkNotificationsQueue.toString()).setForwardingAddress(bridgeNotificationsQueue.toString()).setConfirmationWindowSize(10).setStaticConnectors(Arrays.asList("notification-broker")).setTransformerConfiguration(new TransformerConfiguration(BridgeApplicationPropertiesTransformer.class.getName())));
   }

   @Test
   public void testApplicationPropertiesFromTransformerForwardBridge() throws Exception {
      Map<String, Object> applicationProperties = new HashMap<>();
      applicationProperties.put(DivertApplicationPropertiesTransformer.TRX_ID, "100");

      sendMessages("uswest.Provider.AMC.Agent.f261d0fa-51bd-44bd-abe0-ce22d2a387cd.CustomNotification", 1, RoutingType.ANYCAST, true);

      try (ServerLocator locator = ActiveMQClient.createServerLocator(getServer1URL());
           ClientSessionFactory sessionFactory = locator.createSessionFactory();
           ClientSession session = sessionFactory.createSession();
           ClientConsumer consumer = session.createConsumer(notificationsQueue)) {

         session.start();

         ClientMessage message = consumer.receive(5000);
         assertNotNull(message);

         assertEquals("1", message.getStringProperty("A"));
         assertEquals("2", message.getStringProperty("B"));
         assertEquals("3", message.getStringProperty("C"));
         assertEquals("4", message.getStringProperty("D"));
      }
   }
}