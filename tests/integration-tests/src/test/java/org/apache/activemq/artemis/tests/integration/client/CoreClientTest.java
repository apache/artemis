/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreClientTest extends ActiveMQTestBase {

   @Test
   public void testCoreClientNetty() throws Exception {
      testCoreClient(true, null);
   }

   @Test
   public void testCoreClientInVM() throws Exception {
      testCoreClient(false, null);
   }

   @Test
   public void testCoreClientWithInjectedThreadPools() throws Exception {

      ExecutorService threadPool = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      ScheduledThreadPoolExecutor scheduledThreadPool = new ScheduledThreadPoolExecutor(10);
      ExecutorService flowControlThreadPool = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));

      ServerLocator locator = createNonHALocator(false);
      boolean setThreadPools = locator.setThreadPools(threadPool, scheduledThreadPool, flowControlThreadPool);

      assertTrue(setThreadPools);
      testCoreClient(true, locator);

      threadPool.shutdown();
      scheduledThreadPool.shutdown();
      flowControlThreadPool.shutdown();

      threadPool.awaitTermination(60, TimeUnit.SECONDS);
      scheduledThreadPool.awaitTermination(60, TimeUnit.SECONDS);
      flowControlThreadPool.awaitTermination(60, TimeUnit.SECONDS);
   }

   @Test
   public void testCoreClientWithGlobalThreadPoolParamtersChanged() throws Exception {

      int originalScheduled = ActiveMQClient.getGlobalScheduledThreadPoolSize();
      int originalGlobal = ActiveMQClient.getGlobalThreadPoolSize();
      int originalFlowControl = ActiveMQClient.getGlobalFlowControlThreadPoolSize();

      try {
         ActiveMQClient.setGlobalThreadPoolProperties(2, 1, 3);
         ActiveMQClient.clearThreadPools();
         ServerLocator locator = createNonHALocator(false);
         testCoreClient(true, locator);
      } finally {
         // restoring original value otherwise future tests would be screwed up
         ActiveMQClient.setGlobalThreadPoolProperties(originalGlobal, originalScheduled, originalFlowControl);
         ActiveMQClient.clearThreadPools();
      }
   }

   private void testCoreClient(final boolean netty, ServerLocator serverLocator) throws Exception {
      final SimpleString QUEUE = SimpleString.of("CoreClientTestQueue");

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultConfig(netty), false));

      server.start();

      ClientSessionFactory sf = createSessionFactory(Objects.requireNonNullElseGet(serverLocator, () -> createNonHALocator(netty)));

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 1000;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);

         message.putStringProperty("foo", "bar");

         // One way around the setting destination problem is as follows -
         // Remove destination as an attribute from client producer.
         // The destination always has to be set explicitly before sending a message

         message.setAddress(QUEUE);

         message.getBodyBuffer().writeString("testINVMCoreClient");

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receive();

         ActiveMQBuffer buffer = message2.getBodyBuffer();

         assertEquals("testINVMCoreClient", buffer.readString());

         message2.acknowledge();
      }

      sf.close();
   }

   @Test
   public void testCoreClientPrefixes() throws Exception {
      internalTestCoreClientPrefixes(false);
   }

   @Test
   public void testCoreClientPrefixesWithSecurity() throws Exception {
      internalTestCoreClientPrefixes(true);
   }

   public void internalTestCoreClientPrefixes(boolean security) throws Exception {

      Configuration configuration = createBasicConfig();
      configuration.clearAcceptorConfigurations();
      configuration.addAddressSetting("#", new AddressSettings().setMaxSizeBytes(10 * 1024 * 1024).setPageSizeBytes(1024 * 1024));

      String baseAddress = "foo";

      List<String> anycastPrefixes = new ArrayList<>();
      anycastPrefixes.add("anycast://");
      anycastPrefixes.add("queue://");
      anycastPrefixes.add("jms.queue.");

      List<String> multicastPrefixes = new ArrayList<>();
      multicastPrefixes.add("multicast://");
      multicastPrefixes.add("topic://");
      multicastPrefixes.add("jms.topic.");

      String locatorString = "tcp://localhost:5445";
      StringBuilder acceptor = new StringBuilder(locatorString + "?PROTOCOLS=CORE;anycastPrefix=");
      for (String prefix : anycastPrefixes) {
         acceptor.append(prefix + ",");
      }
      acceptor.append(";multicastPrefix=");
      for (String prefix : multicastPrefixes) {
         acceptor.append(prefix + ",");
      }

      configuration.addAcceptorConfiguration("prefix", acceptor.toString());

      ActiveMQJAASSecurityManager securityManager = null;

      if (security) {
         configuration.setSecurityEnabled(true);

         SecurityConfiguration securityConfiguration = new SecurityConfiguration();
         securityConfiguration.addUser("myUser", "myPass");
         securityConfiguration.addRole("myUser", "myrole");
         securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration);
      }

      ActiveMQServer server = addServer(new ActiveMQServerImpl(configuration, securityManager));

      server.start();

      Role myRole = new Role("myrole", true, true, true, true, true, true, true, true, true, true, false, false);
      Set<Role> anySet = new HashSet<>();
      anySet.add(myRole);
      server.getSecurityRepository().addMatch(baseAddress, anySet);

      ServerLocator locator = addServerLocator(ServerLocatorImpl.newLocator(locatorString));

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession("myUser", "myPass", false, true, true, false, 0);

      Map<String, ClientConsumer> consumerMap = new HashMap<>();

      for (String prefix : anycastPrefixes) {
         String queueName = UUIDGenerator.getInstance().generateSimpleStringUUID().toString();
         String address = prefix + baseAddress;

         session.createQueue(QueueConfiguration.of(queueName).setAddress(prefix + baseAddress).setDurable(false));
         consumerMap.put(address, session.createConsumer(queueName));
      }

      for (String prefix : multicastPrefixes) {
         String queueName = UUIDGenerator.getInstance().generateSimpleStringUUID().toString();
         String address = prefix + baseAddress;

         session.createQueue(QueueConfiguration.of(queueName).setAddress(prefix + baseAddress).setDurable(false));
         consumerMap.put(address, session.createConsumer(queueName));
      }

      session.start();

      final int numMessages = 3;

      for (String prefix : anycastPrefixes) {
         ClientProducer producer = session.createProducer(prefix + baseAddress);
         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
            message.getBodyBuffer().writeString("testINVMCoreClient");
            producer.send(message);
         }

         // Ensure that messages are load balanced across all queues

         for (String queuePrefix : anycastPrefixes) {
            ClientConsumer consumer = consumerMap.get(queuePrefix + baseAddress);
            for (int i = 0; i < numMessages / anycastPrefixes.size(); i++) {
               ClientMessage message = consumer.receive(1000);
               assertNotNull(message);
               // this seems to be the only assert of this non requirement
               assertFalse(message.getAddress().contains(queuePrefix));
               message.acknowledge();
            }
            assertNull(consumer.receiveImmediate());
         }

         for (String multicastPrefix : multicastPrefixes) {
            ClientConsumer consumer = consumerMap.get(multicastPrefix + baseAddress);
            assertNull(consumer.receiveImmediate());
         }
      }

      sf.close();
   }

   @Test
   public void testRemoteAddress() throws Exception {
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultConfig(true), false));
      server.start();
      ServerLocator locator = createNonHALocator(true);
      ClientSessionFactory sf = createSessionFactory(locator);
      addClientSession(sf.createSession(false, true, true));
      assertFalse(server.getRemotingService().getConnections().iterator().next().getTransportConnection().getRemoteAddress().startsWith("/"));
   }
}
