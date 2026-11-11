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
package org.apache.activemq.artemis.tests.integration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class ConfigChangeTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   @Test
   public void testChangeQueueRoutingTypeOnRestart() throws Exception {
      Configuration configuration = createDefaultInVMConfig();
      configuration.addAddressSetting("#", new AddressSettings());

      List addressConfigurations = new ArrayList();
      CoreAddressConfiguration addressConfiguration = new CoreAddressConfiguration()
         .setName("myAddress")
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of("myQueue")
                                   .setAddress("myAddress")
                                   .setRoutingType(RoutingType.ANYCAST));
      addressConfigurations.add(addressConfiguration);
      configuration.setAddressConfigurations(addressConfigurations);
      server = createServer(true, configuration);
      server.start();


      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");
      try (JMSContext context = connectionFactory.createContext()) {
         context.createProducer().send(context.createQueue("myAddress"), "hello");
      }


      server.stop();

      addressConfiguration = new CoreAddressConfiguration()
         .setName("myAddress")
         .addRoutingType(RoutingType.MULTICAST)
         .addQueueConfiguration(QueueConfiguration.of("myQueue")
                                   .setAddress("myAddress")
                                   .setRoutingType(RoutingType.MULTICAST));
      addressConfigurations.clear();
      addressConfigurations.add(addressConfiguration);
      configuration.setAddressConfigurations(addressConfigurations);
      server.start();
      assertEquals(RoutingType.MULTICAST, server.getAddressInfo(SimpleString.of("myAddress")).getRoutingType());
      assertEquals(RoutingType.MULTICAST, server.locateQueue(SimpleString.of("myQueue")).getRoutingType());

      //Ensures the queue isnt detroyed by checking message sent before change is consumable after (e.g. no message loss)
      try (JMSContext context = connectionFactory.createContext()) {
         Message message = context.createSharedDurableConsumer(context.createTopic("myAddress"), "myQueue").receive();
         assertEquals("hello", ((TextMessage) message).getText());
      }

      server.stop();
   }

   @Test
   public void testChangeQueueFilterOnRestart() throws Exception {
      final String filter1 = "x = 'x'";
      final String filter2 = "x = 'y'";

      Configuration configuration = createDefaultInVMConfig();
      configuration.addAddressSetting("#", new AddressSettings());

      List addressConfigurations = new ArrayList();
      CoreAddressConfiguration addressConfiguration = new CoreAddressConfiguration()
         .setName("myAddress")
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of("myQueue")
                                   .setAddress("myAddress")
                                   .setFilterString(filter1)
                                   .setRoutingType(RoutingType.ANYCAST));
      addressConfigurations.add(addressConfiguration);
      configuration.setAddressConfigurations(addressConfigurations);
      server = createServer(true, configuration);
      server.start();

      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");
      try (JMSContext context = connectionFactory.createContext()) {
         context.createProducer().setProperty("x", "x").send(context.createQueue("myAddress"), "hello");
      }

      long originalBindingId = server.getPostOffice().getBinding(SimpleString.of("myQueue")).getID();

      server.stop();

      addressConfiguration = new CoreAddressConfiguration()
         .setName("myAddress")
         .addRoutingType(RoutingType.ANYCAST)
         .addQueueConfiguration(QueueConfiguration.of("myQueue")
                                   .setAddress("myAddress")
                                   .setFilterString(filter2)
                                   .setRoutingType(RoutingType.ANYCAST));
      addressConfigurations.clear();
      addressConfigurations.add(addressConfiguration);
      configuration.setAddressConfigurations(addressConfigurations);

      server.start();
      assertEquals(filter2, server.locateQueue(SimpleString.of("myQueue")).getFilter().getFilterString().toString());

      //Ensures the queue is not destroyed by checking message sent before change is consumable after (e.g. no message loss)
      try (JMSContext context = connectionFactory.createContext()) {
         Message message = context.createConsumer(context.createQueue("myAddress::myQueue")).receive();
         assertEquals("hello", ((TextMessage) message).getText());
      }

      long bindingId = server.getPostOffice().getBinding(SimpleString.of("myQueue")).getID();
      assertEquals(originalBindingId, bindingId, "Ensure the original queue is not destroyed by checking the binding id is the same");

      server.stop();

   }

   @Test
   public void bridgeConfigChangesPersist() throws Exception {
      server = createServer(true);
      server.start();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = addClientSession(sf.createSession(false, true, true));

      String bridgeName = "bridgeName";
      String queue = "Q1";
      String forward = "Q2";

      session.createQueue(QueueConfiguration.of(queue).setAddress(queue).setRoutingType(RoutingType.ANYCAST).setAutoDelete(false));
      session.createQueue(QueueConfiguration.of(forward).setAddress(forward).setRoutingType(RoutingType.ANYCAST).setAutoDelete(false));
      session.close();

      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration().setName(bridgeName)
         .setQueueName(queue)
         .setConcurrency(2)
         .setForwardingAddress(forward)
         .setProducerWindowSize(1234)
         .setConfirmationWindowSize(1234)
         .setStaticConnectors(Arrays.asList("connector1", "connector2"));

      server.getActiveMQServerControl().addConnector("connector1", "tcp://localhost:61616");
      server.getActiveMQServerControl().addConnector("connector2", "tcp://localhost:61616");
      server.getActiveMQServerControl().createBridge(bridgeConfiguration.toJSON());

      assertEquals(2, server.getConfiguration().getConnectorConfigurations().size());
      assertEquals(2, server.getActiveMQServerControl().getBridgeNames().length);
      server.stop();

      // clear the in-memory connector configurations to force a reload from disk
      server.getConfiguration().getConnectorConfigurations().clear();

      server.start();
      assertEquals(2, server.getConfiguration().getConnectorConfigurations().size());
      assertEquals(2, server.getActiveMQServerControl().getBridgeNames().length);

      server.getActiveMQServerControl().destroyBridge(bridgeName);
      server.getActiveMQServerControl().removeConnector("connector1");
      server.getActiveMQServerControl().removeConnector("connector2");
      assertEquals(0, server.getActiveMQServerControl().getBridgeNames().length);
      assertEquals(0, server.getConfiguration().getConnectorConfigurations().size());
      server.stop();
      server.start();
      assertEquals(0, server.getActiveMQServerControl().getBridgeNames().length);
      assertEquals(0, server.getConfiguration().getConnectorConfigurations().size());
      server.stop();

   }

}
