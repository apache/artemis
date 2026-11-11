/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class RetroactiveAddressTest extends ActiveMQTestBase {

   protected ActiveMQServer server;

   protected ClientSession session;

   protected ClientSessionFactory sf;

   protected ServerLocator locator;

   String internalNamingPrefix;

   char delimiterChar;

   String delimiter;

   @Parameters(name = "delimiterChar={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][] {{'/'}, {'.'}});
   }

   public RetroactiveAddressTest(char delimiterChar) {
      super();
      this.delimiterChar = delimiterChar;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, createDefaultInVMConfig(), AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES, -1, -1, new HashMap<>());
      server.getConfiguration().setInternalNamingPrefix(ActiveMQDefaultConfiguration.DEFAULT_INTERNAL_NAMING_PREFIX.replace('.', delimiterChar));
      server.getConfiguration().getWildcardConfiguration().setDelimiter(delimiterChar);
      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));
      internalNamingPrefix = server.getConfiguration().getInternalNamingPrefix();
      delimiter = server.getConfiguration().getWildcardConfiguration().getDelimiterString();
   }

   @TestTemplate
   public void testRetroactiveResourceCreationWithExactMatch() throws Exception {
      internalTestRetroactiveResourceCreation("myAddress", "myAddress");
   }

   @TestTemplate
   public void testRetroactiveResourceCreationWithWildcardMatch() throws Exception {
      internalTestRetroactiveResourceCreation("myAddress", "#");
   }

   private void internalTestRetroactiveResourceCreation(String address, String match) throws Exception {
      final SimpleString addressName = SimpleString.of(address);
      final SimpleString divertAddress = ResourceNames.getRetroactiveResourceAddressName(internalNamingPrefix, delimiter, addressName);
      final SimpleString divertMulticastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      final SimpleString divertAnycastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.ANYCAST);
      final SimpleString divert = ResourceNames.getRetroactiveResourceDivertName(internalNamingPrefix, delimiter, addressName);
      server.getAddressSettingsRepository().addMatch(match, new AddressSettings().setRetroactiveMessageCount(10));
      server.addAddressInfo(new AddressInfo(addressName));
      assertNotNull(server.getAddressInfo(divertAddress));
      assertNotNull(server.locateQueue(divertMulticastQueue));
      assertEquals(RoutingType.MULTICAST, server.locateQueue(divertMulticastQueue).getRoutingType());
      assertNotNull(server.locateQueue(divertAnycastQueue));
      assertEquals(RoutingType.ANYCAST, server.locateQueue(divertAnycastQueue).getRoutingType());
      assertNotNull(server.getPostOffice().getBinding(divert));
   }

   @TestTemplate
   public void testRetroactiveResourceRemoval() throws Exception {
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertAddress = ResourceNames.getRetroactiveResourceAddressName(internalNamingPrefix, delimiter, addressName);
      final SimpleString divertMulticastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      final SimpleString divertAnycastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.ANYCAST);
      final SimpleString divert = ResourceNames.getRetroactiveResourceDivertName(internalNamingPrefix, delimiter, addressName);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(10));

      server.addAddressInfo(new AddressInfo(addressName));
      assertNotNull(server.getAddressInfo(divertAddress));
      assertNotNull(server.locateQueue(divertMulticastQueue));
      assertNotNull(server.locateQueue(divertAnycastQueue));
      assertNotNull(server.getPostOffice().getBinding(divert));

      server.removeAddressInfo(addressName, null, true);
      assertNull(server.getAddressInfo(divertAddress));
      assertNull(server.locateQueue(divertAnycastQueue));
      assertNull(server.locateQueue(divertMulticastQueue));
      assertNull(server.getPostOffice().getBinding(divert));
   }

   @TestTemplate
   public void testRetroactiveAddress() throws Exception {
      final int COUNT = 15;
      final int LOOPS = 25;
      final SimpleString queueName = SimpleString.of("simpleQueue");
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(COUNT));
      server.addAddressInfo(new AddressInfo(addressName));

      for (int i = 0; i < LOOPS; i++) {
         ClientProducer producer = session.createProducer(addressName);
         for (int j = 0; j < COUNT; j++) {
            ClientMessage message = session.createMessage(false);
            message.putIntProperty("xxx", (i * COUNT) + j);
            producer.send(message);
         }
         producer.close();

         final int finalI = i;
         Wait.assertTrue(() -> server.locateQueue(divertQueue).getMessagesReplaced() == (COUNT * finalI));
         Wait.assertTrue(() -> server.locateQueue(divertQueue).getMessageCount() == COUNT);

         session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
         Wait.assertTrue(() -> server.locateQueue(queueName) != null);
         Wait.assertTrue(() -> server.locateQueue(queueName).getMessageCount() == COUNT);
         ClientConsumer consumer = session.createConsumer(queueName);
         for (int j = 0; j < COUNT; j++) {
            session.start();
            ClientMessage message = consumer.receive(1000);
            assertNotNull(message);
            message.acknowledge();
            assertEquals(addressName.toString(), message.getAddress());
            assertEquals((i * COUNT) + j, (int) message.getIntProperty("xxx"));
         }
         consumer.close();
         session.deleteQueue(queueName);
      }
   }

   @TestTemplate
   public void testRestart() throws Exception {
      final String data = "Simple Text " + UUID.randomUUID().toString();
      final SimpleString queueName1 = SimpleString.of("simpleQueue1");
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertMulticastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(10));
      server.addAddressInfo(new AddressInfo(addressName));

      ClientProducer producer = session.createProducer(addressName);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString(data + "1");
      producer.send(message);
      producer.close();
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getMessageCount() == 1);

      server.stop();
      server.start();
      assertNotNull(server.locateQueue(divertMulticastQueue));
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getMessageCount() == 1);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(10));
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));

      producer = session.createProducer(addressName);
      message = session.createMessage(true);
      message.getBodyBuffer().writeString(data + "2");
      producer.send(message);
      producer.close();

      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getMessageCount() == 2);

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
      Wait.assertTrue(() -> server.locateQueue(queueName1) != null);
      Wait.assertTrue(() -> server.locateQueue(queueName1).getMessageCount() == 2);

      ClientConsumer consumer = session.createConsumer(queueName1);
      session.start();
      message = consumer.receive(1000);
      assertNotNull(message);
      message.acknowledge();
      assertEquals(data + "1", message.getBodyBuffer().readString());
      message = consumer.receive(1000);
      assertNotNull(message);
      message.acknowledge();
      assertEquals(data + "2", message.getBodyBuffer().readString());
      consumer.close();
      Wait.assertTrue(() -> server.locateQueue(queueName1).getMessageCount() == 0);

      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getMessageCount() == 2);
   }

   @TestTemplate
   public void testUpdateAfterRestart() throws Exception {
      final int COUNT = 10;
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertAnycastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.ANYCAST);
      final SimpleString divertMulticastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(COUNT));
      server.addAddressInfo(new AddressInfo(addressName));
      Wait.assertTrue(() -> server.locateQueue(divertAnycastQueue).getRingSize() == COUNT);
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getRingSize() == COUNT);
      server.stop();
      server.start();
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(COUNT * 2));
      Wait.assertTrue(() -> server.locateQueue(divertAnycastQueue).getRingSize() == COUNT * 2);
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getRingSize() == COUNT * 2);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(COUNT));
      Wait.assertTrue(() -> server.locateQueue(divertAnycastQueue).getRingSize() == COUNT);
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getRingSize() == COUNT);
   }

   @TestTemplate
   public void testMulticast() throws Exception {
      final String data = "Simple Text " + UUID.randomUUID().toString();
      final SimpleString queueName1 = SimpleString.of("simpleQueue1");
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(10));
      server.addAddressInfo(new AddressInfo(addressName));

      ClientProducer producer = session.createProducer(addressName);
      ClientMessage message = session.createMessage(false);
      message.getBodyBuffer().writeString(data);
      message.setRoutingType(RoutingType.MULTICAST);
      producer.send(message);
      producer.close();
      Wait.assertTrue(() -> server.locateQueue(divertQueue).getMessageCount() == 1);

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName));
      Wait.assertTrue(() -> server.locateQueue(queueName1) != null);
      Wait.assertTrue(() -> server.locateQueue(queueName1).getMessageCount() == 1);

      ClientConsumer consumer = session.createConsumer(queueName1);
      session.start();
      message = consumer.receive(1000);
      assertNotNull(message);
      message.acknowledge();
      assertEquals(data, message.getBodyBuffer().readString());
      consumer.close();
      Wait.assertTrue(() -> server.locateQueue(queueName1).getMessageCount() == 0);

      Wait.assertTrue(() -> server.locateQueue(divertQueue).getMessageCount() == 1);
   }

   @TestTemplate
   public void testJMSTopicSubscribers() throws Exception {
      final SimpleString addressName = SimpleString.of("myAddress");
      final int COUNT = 10;
      final SimpleString divertQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(COUNT));
      server.addAddressInfo(new AddressInfo(addressName).addRoutingType(RoutingType.MULTICAST));

      ConnectionFactory cf = new ActiveMQConnectionFactory("vm://0");
      Connection c = cf.createConnection();
      Session s = c.createSession();
      Topic t = s.createTopic(addressName.toString());

      MessageProducer producer = s.createProducer(t);
      for (int i = 0; i < COUNT * 2; i++) {
         Message m = s.createMessage();
         m.setIntProperty("test", i);
         producer.send(m);
      }
      producer.close();
      Wait.assertTrue(() -> server.locateQueue(divertQueue).getMessageCount() == COUNT);

      MessageConsumer consumer = s.createConsumer(t);
      c.start();
      for (int i = 0; i < COUNT; i++) {
         Message m = consumer.receive(500);
         assertNotNull(m);
         assertEquals(i + COUNT, m.getIntProperty("test"));
      }
      assertNull(consumer.receiveNoWait());
   }

   @TestTemplate
   public void testUpdateAddressSettings() throws Exception {
      final int COUNT = 10;
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertAnycastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.ANYCAST);
      final SimpleString divertMulticastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(COUNT));
      server.addAddressInfo(new AddressInfo(addressName));
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(COUNT * 2));
      Wait.assertTrue(() -> server.locateQueue(divertAnycastQueue).getRingSize() == COUNT * 2);
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getRingSize() == COUNT * 2);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(COUNT));
      Wait.assertTrue(() -> server.locateQueue(divertAnycastQueue).getRingSize() == COUNT);
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getRingSize() == COUNT);
   }

   @TestTemplate
   public void testRoutingTypes() throws Exception {
      final String data = "Simple Text " + UUID.randomUUID().toString();
      final SimpleString multicastQueue = SimpleString.of("multicastQueue");
      final SimpleString anycastQueue = SimpleString.of("anycastQueue");
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertMulticastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      final SimpleString divertAnycastQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.ANYCAST);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(10));
      server.addAddressInfo(new AddressInfo(addressName));

      ClientProducer producer = session.createProducer(addressName);
      ClientMessage message = session.createMessage(false);
      message.getBodyBuffer().writeString(data + RoutingType.MULTICAST.toString());
      message.setRoutingType(RoutingType.MULTICAST);
      producer.send(message);
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getMessageCount() == 1);
      Wait.assertTrue(() -> server.locateQueue(divertAnycastQueue).getMessageCount() == 0);

      message = session.createMessage(false);
      message.getBodyBuffer().writeString(data + RoutingType.ANYCAST.toString());
      message.setRoutingType(RoutingType.ANYCAST);
      producer.send(message);
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getMessageCount() == 1);
      Wait.assertTrue(() -> server.locateQueue(divertAnycastQueue).getMessageCount() == 1);

      producer.close();

      session.createQueue(QueueConfiguration.of(multicastQueue).setAddress(addressName));
      Wait.assertTrue(() -> server.locateQueue(multicastQueue) != null);
      Wait.assertTrue(() -> server.locateQueue(multicastQueue).getMessageCount() == 1);

      session.createQueue(QueueConfiguration.of(anycastQueue).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
      Wait.assertTrue(() -> server.locateQueue(anycastQueue) != null);
      Wait.assertTrue(() -> server.locateQueue(anycastQueue).getMessageCount() == 1);

      ClientConsumer consumer = session.createConsumer(multicastQueue);
      session.start();
      message = consumer.receive(1000);
      assertNotNull(message);
      message.acknowledge();
      assertEquals(data + RoutingType.MULTICAST.toString(), message.getBodyBuffer().readString());
      consumer.close();
      Wait.assertTrue(() -> server.locateQueue(multicastQueue).getMessageCount() == 0);
      Wait.assertTrue(() -> server.locateQueue(divertMulticastQueue).getMessageCount() == 1);

      consumer.close();

      consumer = session.createConsumer(anycastQueue);
      session.start();
      message = consumer.receive(1000);
      assertNotNull(message);
      message.acknowledge();
      assertEquals(data + RoutingType.ANYCAST.toString(), message.getBodyBuffer().readString());
      consumer.close();
      Wait.assertTrue(() -> server.locateQueue(anycastQueue).getMessageCount() == 0);
      Wait.assertTrue(() -> server.locateQueue(divertAnycastQueue).getMessageCount() == 1);
   }

   @TestTemplate
   public void testFilter() throws Exception {
      final SimpleString queueName1 = SimpleString.of("simpleQueue1");
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(10));
      server.addAddressInfo(new AddressInfo(addressName));

      ClientProducer producer = session.createProducer(addressName);
      ClientMessage message = session.createMessage(false);
      message.putLongProperty("xxx", 5);
      producer.send(message);
      message = session.createMessage(false);
      message.putLongProperty("xxx", 15);
      producer.send(message);
      producer.close();
      Wait.assertTrue(() -> server.locateQueue(divertQueue).getMessageCount() == 2);

      server.createQueue(QueueConfiguration.of(queueName1).setAddress(addressName).setFilterString("xxx > 10").setDurable(false));
      Wait.assertTrue(() -> server.locateQueue(queueName1) != null);
      Wait.assertTrue(() -> server.locateQueue(queueName1).getMessageCount() == 1);

      ClientConsumer consumer = session.createConsumer(queueName1);
      session.start();
      message = consumer.receive(1000);
      assertNotNull(message);
      message.acknowledge();
      assertEquals(15, (long) message.getLongProperty("xxx"));
      consumer.close();
      Wait.assertTrue(() -> server.locateQueue(queueName1).getMessageCount() == 0);
      Wait.assertTrue(() -> server.locateQueue(divertQueue).getMessageCount() == 2);
   }

   @TestTemplate
   public void testAddressSettingOnRetroactiveResource() throws Exception {
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertAddress = ResourceNames.getRetroactiveResourceAddressName(internalNamingPrefix, delimiter, addressName);
      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(10));
      server.addAddressInfo(new AddressInfo(addressName));
      assertEquals(-1, server.getAddressSettingsRepository().getMatch(divertAddress.toString()).getMaxSizeBytes());
      server.getAddressSettingsRepository().addMatch("*" + delimiter + "*" + delimiter + "*" + delimiter + addressName + delimiter + "*" + delimiter + ResourceNames.RETROACTIVE_SUFFIX, new AddressSettings().setMaxSizeBytes(13));
      assertEquals(13, server.getAddressSettingsRepository().getMatch(divertAddress.toString()).getMaxSizeBytes());
   }

   @TestTemplate
   public void testPaging() throws Exception {
      final SimpleString queueName = SimpleString.of("simpleQueue");
      final SimpleString randomQueueName = SimpleString.of(UUID.randomUUID().toString());
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      final int MESSAGE_COUNT = 20;
      final int MESSAGE_SIZE = 1024;

      server.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(MESSAGE_COUNT).setMaxSizeBytes(1024 * 20).setPageSizeBytes(1024 * 10).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE));
      server.addAddressInfo(new AddressInfo(addressName));
      server.createQueue(QueueConfiguration.of(randomQueueName).setAddress(addressName));

      ClientProducer producer = session.createProducer(addressName);

      byte[] body = new byte[MESSAGE_SIZE];
      ByteBuffer bb = ByteBuffer.wrap(body);
      for (int j = 1; j <= MESSAGE_SIZE; j++) {
         bb.put(getSamplebyte(j));
      }

      for (int i = 0; i < MESSAGE_COUNT * 2; i++) {
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(body);
         producer.send(message);
      }
      producer.close();
      Wait.assertTrue(() -> server.locateQueue(randomQueueName).getMessageCount() == MESSAGE_COUNT * 2);

      Wait.assertTrue(() -> server.locateQueue(divertQueue).getMessageCount() == MESSAGE_COUNT);

      session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName));
      Wait.assertTrue(() -> server.locateQueue(queueName) != null);
      Wait.assertTrue(() -> server.locateQueue(queueName).getMessageCount() == MESSAGE_COUNT);

      ClientConsumer consumer = session.createConsumer(queueName);
      session.start();

      for (int i = 0; i < MESSAGE_COUNT; i++) {
         ClientMessage message = consumer.receive(1000);
         assertNotNull(message);
         message.acknowledge();
      }
      consumer.close();
      Wait.assertTrue(() -> server.locateQueue(queueName).getMessageCount() == 0);
      Wait.assertTrue(() -> server.locateQueue(divertQueue).getMessageCount() == MESSAGE_COUNT);
   }
}
