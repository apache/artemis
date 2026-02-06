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
package org.apache.activemq.artemis.tests.integration.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import java.io.File;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.AcceptorControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.DivertBinding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.cluster.impl.RemoteQueueBindingImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.reload.ReloadManager;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.tests.unit.core.postoffice.impl.fakes.FakeQueue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RedeployTest extends ActiveMQTestBase {

   /*
    * This tests that the broker doesnt fall over when it tries to delete any autocreated addresses/queues in a clustered environment
    * If the undeploy fails then bridges etc can stop working, we need to make sure if undeploy fails on anything the broker is still live
    */
   @Test
   public void testRedeployAutoCreateAddress() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-test-autocreateaddress.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-test-autocreateaddress-reload.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession();
         Queue queue = session.createQueue("autoQueue");
         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createTextMessage("text"));
         connection.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue("autoQueue"));
         assertNotNull(consumer.receive(5000), "Address wasn't autocreated accordingly");
      }

      assertNotNull(getQueue(embeddedActiveMQ, "autoQueue"));

      // this simulates a remote queue or other type being added that wouldnt get deleted, its not valid to have this happen but it can happen when addresses and queues are auto created in a clustered env
      embeddedActiveMQ.getActiveMQServer().getPostOffice().addBinding(new RemoteQueueBindingImpl(5L,
              SimpleString.of("autoQueue"),
              SimpleString.of("uniqueName"),
              SimpleString.of("routingName"),
              6L,
              null,
              new FakeQueue(SimpleString.of("foo"), 6L),
              SimpleString.of("bridge"),
              1,
              MessageLoadBalancingType.OFF));

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);
         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         assertTrue(tryConsume());

         assertNotNull(getQueue(embeddedActiveMQ, "autoQueue"));

         factory = new ActiveMQConnectionFactory();
         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession();
            Queue queue = session.createQueue("autoQueue");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("text"));
            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue("autoQueue"));
            assertNotNull(consumer.receive(5000), "autoQueue redeployed accordingly");
         }

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeploy() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-test-jms.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-test-updated-jms.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);
         assertEquals("DLQ", embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch("jms").getDeadLetterAddress().toString());
         assertEquals("ExpiryQueue", embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch("jms").getExpiryAddress().toString());
         assertFalse(tryConsume());
         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         assertTrue(tryConsume());

         assertEquals("NewQueue", embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch("jms").getDeadLetterAddress().toString());
         assertEquals("NewQueue", embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch("jms").getExpiryAddress().toString());

         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession();
            Queue queue = session.createQueue("DivertQueue");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("text"));
            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue("NewQueue"));
            assertNotNull(consumer.receive(5000), "Divert wasn't redeployed accordingly");
         }

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeployConnector() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-connector.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-connector-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);
         assertEquals("127.0.0.1", embeddedActiveMQ.getActiveMQServer().getConfiguration().getConnectorConfigurations().get("artemis").getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61616", embeddedActiveMQ.getActiveMQServer().getConfiguration().getConnectorConfigurations().get("artemis").getParams().get(TransportConstants.PORT_PROP_NAME));
         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         assertEquals("127.0.0.2", embeddedActiveMQ.getActiveMQServer().getConfiguration().getConnectorConfigurations().get("artemis").getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61617", embeddedActiveMQ.getActiveMQServer().getConfiguration().getConnectorConfigurations().get("artemis").getParams().get(TransportConstants.PORT_PROP_NAME));
         assertEquals("127.0.0.3", embeddedActiveMQ.getActiveMQServer().getConfiguration().getConnectorConfigurations().get("artemis2").getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61618", embeddedActiveMQ.getActiveMQServer().getConfiguration().getConnectorConfigurations().get("artemis2").getParams().get(TransportConstants.PORT_PROP_NAME));
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeploySecuritySettings() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-security-settings.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-security-settings-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         Set<Role> roles = embeddedActiveMQ.getActiveMQServer().getSecurityRepository().getMatch("foo");
         boolean found = false;
         for (Role role : roles) {
            if (role.getName().equals("a")) {
               found = true;
            }
         }

         assertTrue(found);

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         roles = embeddedActiveMQ.getActiveMQServer().getSecurityRepository().getMatch("foo");
         found = false;
         for (Role role : roles) {
            if (role.getName().equals("b") && CheckType.VIEW.hasRole(role)) {
               found = true;
            }
         }

         assertTrue(found);

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeploySecuritySettingsWithManagementChange() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-security-settings.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-security-settings-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         Set<Role> roles = embeddedActiveMQ.getActiveMQServer().getSecurityRepository().getMatch("foo");
         boolean found = false;
         for (Role role : roles) {
            if (role.getName().equals("a")) {
               found = true;
            }
         }

         assertTrue(found);

         embeddedActiveMQ.getActiveMQServer().getActiveMQServerControl().addSecuritySettings("bar", "c", "c", "c", "c", "c", "c", "c", "c", "c", "c", "", "");
         roles = embeddedActiveMQ.getActiveMQServer().getSecurityRepository().getMatch("bar");
         for (Role role : roles) {
            if (role.getName().equals("c")) {
               found = true;
            }
         }

         assertTrue(found);

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         roles = embeddedActiveMQ.getActiveMQServer().getSecurityRepository().getMatch("foo");
         found = false;
         for (Role role : roles) {
            if (role.getName().equals("b")) {
               found = true;
            }
         }

         assertTrue(found);

         roles = embeddedActiveMQ.getActiveMQServer().getSecurityRepository().getMatch("bar");
         found = false;
         for (Role role : roles) {
            if (role.getName().equals("c")) {
               found = true;
            }
         }

         assertTrue(found);

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeployAddressSettingsWithManagementChange() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-address-settings.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-address-settings-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         AddressSettings addressSettings = embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch("foo");
         assertEquals("a", addressSettings.getDeadLetterAddress().toString());

         AddressSettings settings = new AddressSettings();
         settings.setDeadLetterAddress(SimpleString.of("c")).setExpiryDelay(0L);

         embeddedActiveMQ.getActiveMQServer().getActiveMQServerControl().addAddressSettings("bar", settings.toJSON());
         addressSettings = embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch("bar");
         assertEquals("c", addressSettings.getDeadLetterAddress().toString());

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         addressSettings = embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch("foo");
         assertEquals("b", addressSettings.getDeadLetterAddress().toString());

         addressSettings = embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch("bar");
         assertEquals("c", addressSettings.getDeadLetterAddress().toString());

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeployDivertsWithManagementChange() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-diverts.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-diverts-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         DivertBinding divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(SimpleString.of("a"));
         assertNotNull(divertBinding);
         assertEquals("a", divertBinding.getDivert().getAddress().toString());

         embeddedActiveMQ.getActiveMQServer().getActiveMQServerControl().createDivert("c", "c", "c", "target", false, null, null);

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(SimpleString.of("b"));
         assertNotNull(divertBinding);
         assertEquals("b", divertBinding.getDivert().getAddress().toString());

         divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(SimpleString.of("c"));
         assertNotNull(divertBinding);
         assertEquals("c", divertBinding.getDivert().getAddress().toString());

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeployFilter() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-queue-filter.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-queue-filter-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
            connection.start();
            Queue queue = session.createQueue("myFilterQueue");
            MessageProducer producer = session.createProducer(queue);
            Message message = session.createMessage();
            message.setStringProperty("x", "x");
            producer.send(message);
            MessageConsumer consumer = session.createConsumer(queue);
            assertNotNull(consumer.receive(5000));
            consumer.close();
         }

         //Send a message that should remain in the queue (this ensures config change is non-destructive)
         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
            connection.start();
            Queue queue = session.createQueue("myFilterQueue");
            MessageProducer producer = session.createProducer(queue);
            Message message = session.createTextMessage("hello");
            message.setStringProperty("x", "x");
            producer.send(message);
         }

         Binding binding = embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(SimpleString.of("myFilterQueue"));

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         Binding bindingAfterChange = embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(SimpleString.of("myFilterQueue"));

         assertSame(binding, bindingAfterChange, "Instance should be the same (as should be non destructive)");
         assertEquals(binding.getID(), bindingAfterChange.getID());

         //Check that after the config change we can still consume a message that was sent before, ensuring config change was non-destructive of the queue.
         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
            connection.start();
            Queue queue = session.createQueue("myFilterQueue");
            MessageConsumer consumer = session.createConsumer(queue);
            Message message = consumer.receive(5000);
            assertNotNull(message);
            assertEquals("hello", ((TextMessage)message).getText());
            consumer.close();
         }

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
            connection.start();
            Queue queue = session.createQueue("myFilterQueue");
            MessageProducer producer = session.createProducer(queue);
            Message message = session.createMessage();
            message.setStringProperty("x", "y");
            producer.send(message);
            MessageConsumer consumer = session.createConsumer(queue);
            assertNotNull(consumer.receive(2000));
            consumer.close();
         }

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeployBridge() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-bridge.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-bridge-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
            Queue queue = session.createQueue("a-from");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createMessage());
            Wait.assertEquals(1, () -> embeddedActiveMQ.getActiveMQServer().locateQueue("a-to").getMessageCount());
            Wait.assertEquals(3, () -> embeddedActiveMQ.getActiveMQServer().locateQueue("a-from").getConsumerCount());
         }

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
            Queue queue = session.createQueue("b-from");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createMessage());
            Wait.assertEquals(1, () -> embeddedActiveMQ.getActiveMQServer().locateQueue("b-to").getMessageCount());
         }

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
            Queue queue = session.createQueue("a-from");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createMessage());
            Wait.assertEquals(1, () -> embeddedActiveMQ.getActiveMQServer().locateQueue("a-new").getMessageCount(), 2000);
            Wait.assertEquals(1, () -> embeddedActiveMQ.getActiveMQServer().locateQueue("a-to").getMessageCount(), 2000);
            Wait.assertEquals(2, () -> embeddedActiveMQ.getActiveMQServer().locateQueue("a-from").getConsumerCount(), 2000);
         }

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
            Queue queue = session.createQueue("b-from");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createMessage());
            assertFalse(Wait.waitFor(() -> embeddedActiveMQ.getActiveMQServer().locateQueue("b-to").getMessageCount() == 2, 2000, 100));
         }

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
            Queue queue = session.createQueue("c-from");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createMessage());
            Wait.assertEquals(1, () -> embeddedActiveMQ.getActiveMQServer().locateQueue("c-to").getMessageCount(), 2000);
         }

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   private void deployBrokerConfig(EmbeddedActiveMQ server, String configFileName) throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      final ReloadManager reloadManager = server.getActiveMQServer().getReloadManager();
      final boolean reloadManagerOriginallyStarted = reloadManager.isStarted();
      try {
         reloadManager.stop();
         final URL configFile = RedeployTest.class.getClassLoader().getResource(configFileName);
         assertNotNull(configFile);
         try (InputStream configStream = configFile.openStream()) {
            Files.copy(configStream, brokerXML, StandardCopyOption.REPLACE_EXISTING);
         }
         server.getActiveMQServer().reloadConfigurationFile();
      } finally {
         if (reloadManagerOriginallyStarted) {
            reloadManager.start();
         }
      }
   }

   private void doTestQueueRemoveFilter(String testConfigurationFileName) throws Exception {

      final EmbeddedActiveMQ embeddedActiveMQ = createEmbeddedActiveMQServer("reload-queue-filter.xml");

      try {

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            Queue queue = session.createQueue("myFilterQueue");

            // Test that the original filter has been set up
            LocalQueueBinding queueBinding = (LocalQueueBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice()
                    .getBinding(SimpleString.of("myFilterQueue"));
            // The "x = 'x'" value is found in "reload-queue-filter.xml"
            assertEquals("x = 'x'", queueBinding.getFilter().getFilterString().toString());

            MessageProducer producer = session.createProducer(queue);

            // Test that the original filter affects the flow
            Message passingMessage = session.createMessage();
            passingMessage.setStringProperty("x", "x");
            producer.send(passingMessage);

            Message filteredMessage = session.createMessage();
            filteredMessage.setStringProperty("x", "y");
            producer.send(filteredMessage);

            MessageConsumer consumer = session.createConsumer(queue);
            Message receivedMessage = consumer.receive(2000);
            assertNotNull(receivedMessage);
            assertEquals("x", receivedMessage.getStringProperty("x"));

            assertNull(consumer.receive(2000));

            consumer.close();
         }

         deployBrokerConfig(embeddedActiveMQ, testConfigurationFileName);

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            Queue queue = session.createQueue("myFilterQueue");

            // Test that the filter has been removed
            LocalQueueBinding queueBinding = (LocalQueueBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice()
                    .getBinding(SimpleString.of("myFilterQueue"));
            assertNull(queueBinding.getFilter());

            MessageProducer producer = session.createProducer(queue);

            // Test that the original filter no longer affects the flow
            Message message1 = session.createMessage();
            message1.setStringProperty("x", "x");
            producer.send(message1);

            Message message2 = session.createMessage();
            message2.setStringProperty("x", "y");
            producer.send(message2);

            MessageConsumer consumer = session.createConsumer(queue);
            assertNotNull(consumer.receive(2000));
            assertNotNull(consumer.receive(2000));

            consumer.close();
         }

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeployRemoveQueueFilter() throws Exception {
      doTestQueueRemoveFilter("reload-queue-filter-updated-empty.xml");
      doTestQueueRemoveFilter("reload-queue-filter-removed.xml");
   }

   /**
    * This one is here just to make sure it's possible to change queue parameters one by one without setting the others
    * to {@code null}.
    *
    * @throws Exception An exception.
    */
   @Test
   public void testQueuePartialReconfiguration() throws Exception {

      final EmbeddedActiveMQ embeddedActiveMQ = createEmbeddedActiveMQServer("reload-empty.xml");

      try {

         embeddedActiveMQ.getActiveMQServer().createQueue(QueueConfiguration.of("virtualQueue").setUser("bob"));
         embeddedActiveMQ.getActiveMQServer().updateQueue(QueueConfiguration.of("virtualQueue").setFilterString("foo"));

         LocalQueueBinding queueBinding = (LocalQueueBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice()
                 .getBinding(SimpleString.of("virtualQueue"));
         org.apache.activemq.artemis.core.server.Queue queue = queueBinding.getQueue();

         assertEquals(SimpleString.of("bob"), queue.getUser());
         assertEquals(SimpleString.of("foo"), queue.getFilter().getFilterString());

      } finally {
         embeddedActiveMQ.stop();
      }

   }

   @Test
   public void testRedeployQueueDefaults() throws Exception {

      final EmbeddedActiveMQ embeddedActiveMQ = createEmbeddedActiveMQServer("reload-queue-defaults-before.xml");

      try {
         LocalQueueBinding queueBinding = (LocalQueueBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice()
                 .getBinding(SimpleString.of("myQueue"));
         org.apache.activemq.artemis.core.server.Queue queue = queueBinding.getQueue();

         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(), queue.getMaxConsumers());
         assertNotEquals(RoutingType.MULTICAST, queue.getRoutingType());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), queue.isPurgeOnNoConsumers());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultEnabled(), queue.isEnabled());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultExclusive(), queue.isExclusive());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultGroupRebalance(), queue.isGroupRebalance());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultGroupBuckets(), queue.getGroupBuckets());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultGroupFirstKey(), queue.getGroupFirstKey());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultNonDestructive(), queue.isNonDestructive());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultConsumersBeforeDispatch(), queue.getConsumersBeforeDispatch());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultDelayBeforeDispatch(), queue.getDelayBeforeDispatch());
         assertNotNull(queue.getFilter());
         assertEquals(SimpleString.of("jdoe"), queue.getUser());
         assertNotEquals(ActiveMQDefaultConfiguration.getDefaultRingSize(), queue.getRingSize());

         deployBrokerConfig(embeddedActiveMQ, "reload-queue-defaults-after.xml");

         assertEquals(ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(), queue.getMaxConsumers());
         assertEquals(RoutingType.MULTICAST, queue.getRoutingType());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), queue.isPurgeOnNoConsumers());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultEnabled(), queue.isEnabled());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultExclusive(), queue.isExclusive());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultGroupRebalance(), queue.isGroupRebalance());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultGroupBuckets(), queue.getGroupBuckets());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultGroupFirstKey(), queue.getGroupFirstKey());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultNonDestructive(), queue.isNonDestructive());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultConsumersBeforeDispatch(), queue.getConsumersBeforeDispatch());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultDelayBeforeDispatch(), queue.getDelayBeforeDispatch());
         assertNull(queue.getFilter());
         assertNull(queue.getUser());
         assertEquals(ActiveMQDefaultConfiguration.getDefaultRingSize(), queue.getRingSize());

      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testUndeployDivert() throws Exception {

      final EmbeddedActiveMQ embeddedActiveMQ = createEmbeddedActiveMQServer("reload-divert-undeploy-before.xml");

      try {
         DivertBinding divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice()
                 .getBinding(SimpleString.of("divert"));
         assertNotNull(divertBinding);

         final Queue sourceQueue = (Queue) ActiveMQDestination.createDestination("queue://source", ActiveMQDestination.TYPE.QUEUE);
         final Queue targetQueue = (Queue) ActiveMQDestination.createDestination("queue://target", ActiveMQDestination.TYPE.QUEUE);

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
              MessageProducer sourceProducer = session.createProducer(sourceQueue);
              MessageConsumer sourceConsumer = session.createConsumer(sourceQueue);
              MessageConsumer targetConsumer = session.createConsumer(targetQueue)) {

            connection.start();
            final Message message = session.createTextMessage("Hello world");
            sourceProducer.send(message);
            assertNotNull(sourceConsumer.receive(2000));
            assertNotNull(targetConsumer.receive(2000));
         }

         deployBrokerConfig(embeddedActiveMQ, "reload-divert-undeploy-after.xml");

         Wait.waitFor(() -> embeddedActiveMQ.getActiveMQServer().getPostOffice()
                         .getBinding(SimpleString.of("divert")) == null);
         divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice()
                 .getBinding(SimpleString.of("divert"));
         assertNull(divertBinding);

         try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
              Connection connection = factory.createConnection();
              Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
              MessageProducer sourceProducer = session.createProducer(sourceQueue);
              MessageConsumer sourceConsumer = session.createConsumer(sourceQueue);
              MessageConsumer targetConsumer = session.createConsumer(targetQueue)) {

            connection.start();
            Message message = session.createTextMessage("Hello world");
            sourceProducer.send(message);
            assertNotNull(sourceConsumer.receive(2000));
            assertNull(targetConsumer.receiveNoWait());
         }
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   private void sendDivertedTestMessage(Queue queue, Queue forwardingQueue, boolean shouldReceiveFromQueue, boolean shouldReceiveFromForwardingQueue, Map<String, String> properties) throws JMSException {
      try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
           Connection connection = factory.createConnection();
           Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
           MessageProducer queueProducer = session.createProducer(queue);
           MessageConsumer queueConsumer = session.createConsumer(queue);
           MessageConsumer forwardingQueueConsumer = session.createConsumer(forwardingQueue)) {

         connection.start();
         final Message message = session.createTextMessage("Hello world");
         for (Map.Entry<String, String> entry : properties.entrySet()) {
            message.setStringProperty(entry.getKey(), entry.getValue());
         }
         queueProducer.send(message);

         final Message queueMessage = queueConsumer.receive(2000);
         final Message forwardingQueueMessage = forwardingQueueConsumer.receive(2000);
         if (shouldReceiveFromQueue) {
            assertNotNull(queueMessage, "A message should have been received from the '" + queue.getQueueName() + "' queue.");
         } else {
            assertNull(queueMessage, "No message should have been received from the '" + queue.getQueueName() + "' queue.");
         }
         if (shouldReceiveFromForwardingQueue) {
            assertNotNull(forwardingQueueMessage, "A message should have been received from the '" + forwardingQueue.getQueueName() + "' forwarding queue.");
         } else {
            assertNull(forwardingQueueMessage, "No message should have been received from the '" + forwardingQueue.getQueueName() + "' forwarding queue.");
         }
      }
   }

   private EmbeddedActiveMQ createEmbeddedActiveMQServer(String initialConfigFileName) throws Exception {
      final Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      final URL baseConfig = RedeployTest.class.getClassLoader().getResource(initialConfigFileName);
      assertNotNull(baseConfig);
      try (InputStream configStream = baseConfig.openStream()) {
         Files.copy(configStream, brokerXML, StandardCopyOption.REPLACE_EXISTING);
      }
      final EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();
      waitForServerToStart(embeddedActiveMQ.getActiveMQServer());
      return embeddedActiveMQ;
   }

   @Test
   public void testAddDivertFilter() throws Exception {

      final EmbeddedActiveMQ embeddedActiveMQ = createEmbeddedActiveMQServer("reload-divert-filter-none.xml");

      final SimpleString divertName = SimpleString.of("source-to-target");
      final Queue sourceQueue = (Queue) ActiveMQDestination.createDestination("queue://source", ActiveMQDestination.TYPE.QUEUE);
      final Queue targetQueue = (Queue) ActiveMQDestination.createDestination("queue://target", ActiveMQDestination.TYPE.QUEUE);
      final Map<String, String> emptyTestMessageProperties = Map.of();
      final Map<String, String> testMessagePropertiesXX = Map.of("x", "x");

      try {
         DivertBinding divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(divertName);
         assertNotNull(divertBinding, "Divert '" + divertName + "' binding should exist.");
         assertNull(divertBinding.getFilter(), "The divert '" + divertName + "' should have no filter applied at first.");

         assertNull(embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(SimpleString.of("foo")));

         // Message with no properties should be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, emptyTestMessageProperties);
         // Message with properties should be diverted too.
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, testMessagePropertiesXX);

         // Add filter
         deployBrokerConfig(embeddedActiveMQ, "reload-divert-filter-x-eq-x.xml");

         divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(divertName);
         assertNotNull(divertBinding, "Divert '" + divertName + "' binding should exist.");
         assertNotNull(divertBinding.getFilter(), "The divert '" + divertName + "' should have a filter applied after the new configuration is loaded.");

         // Message with no properties SHOULD NOT be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, true, false, emptyTestMessageProperties);
         // Message with property x == "x" SHOULD be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, testMessagePropertiesXX);
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRemoveDivertFilter() throws Exception {

      final EmbeddedActiveMQ embeddedActiveMQ = createEmbeddedActiveMQServer("reload-divert-filter-x-eq-x.xml");
      final SimpleString divertName = SimpleString.of("source-to-target");
      final Queue sourceQueue = (Queue) ActiveMQDestination.createDestination("queue://source", ActiveMQDestination.TYPE.QUEUE);
      final Queue targetQueue = (Queue) ActiveMQDestination.createDestination("queue://target", ActiveMQDestination.TYPE.QUEUE);
      final Map<String, String> emptyTestMessageProperties = Map.of();
      final Map<String, String> testMessagePropertiesXX = Map.of("x", "x");

      try {
         DivertBinding divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(divertName);
         assertNotNull(divertBinding, "Divert '" + divertName + "' binding should exist.");
         assertNotNull(divertBinding.getFilter(), "The divert '" + divertName + "' should have a filter applied at first.");

         // Message with no properties SHOULD NOT be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, true, false, emptyTestMessageProperties);
         // Message with property x == "x" SHOULD be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, testMessagePropertiesXX);

         // Remove filter
         deployBrokerConfig(embeddedActiveMQ, "reload-divert-filter-none.xml");

         divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(divertName);
         assertNotNull(divertBinding, "Divert '" + divertName + "' binding should exist.");
         assertNull(divertBinding.getFilter(), "The divert '" + divertName + "' should not have a filter applied after the new configuration is loaded.");

         // Message with no properties should be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, emptyTestMessageProperties);
         // Message with properties should be diverted too.
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, testMessagePropertiesXX);
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testChangeDivertFilter() throws Exception {

      final EmbeddedActiveMQ embeddedActiveMQ = createEmbeddedActiveMQServer("reload-divert-filter-x-eq-x.xml");
      final SimpleString divertName = SimpleString.of("source-to-target");
      final Queue sourceQueue = (Queue) ActiveMQDestination.createDestination("queue://source", ActiveMQDestination.TYPE.QUEUE);
      final Queue targetQueue = (Queue) ActiveMQDestination.createDestination("queue://target", ActiveMQDestination.TYPE.QUEUE);
      final Map<String, String> testMessagePropertiesXX = Map.of("x", "x");
      final Map<String, String> testMessagePropertiesXY = Map.of("x", "y");

      try {
         DivertBinding divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(divertName);
         assertNotNull(divertBinding, "Divert '" + divertName + "' binding should exist.");
         assertNotNull(divertBinding.getFilter(), "The divert '" + divertName + "' should have a filter applied after the first configuration file is loaded.");

         // Message with property x == "x" SHOULD be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, testMessagePropertiesXX);
         // Message with property x == "y" SHOULD NOT be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, true, false, testMessagePropertiesXY);

         // Update filter
         deployBrokerConfig(embeddedActiveMQ, "reload-divert-filter-x-eq-y.xml");

         divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(divertName);
         assertNotNull(divertBinding, "Divert '" + divertName + "' binding should exist.");
         assertNotNull(divertBinding.getFilter(), "The divert '" + divertName + "' should have a filter applied after the second configuration file is loaded.");

         // Message with property x == "x" SHOULD NOT be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, true, false, testMessagePropertiesXX);
         // Message with property x == "y" SHOULD be diverted.
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, testMessagePropertiesXY);
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testChangeDivertExclusivity() throws Exception {

      final EmbeddedActiveMQ embeddedActiveMQ = createEmbeddedActiveMQServer("reload-divert-exclusive.xml");
      final SimpleString divertName = SimpleString.of("source-to-target");
      final Queue sourceQueue = (Queue) ActiveMQDestination.createDestination("queue://source", ActiveMQDestination.TYPE.QUEUE);
      final Queue targetQueue = (Queue) ActiveMQDestination.createDestination("queue://target", ActiveMQDestination.TYPE.QUEUE);

      try {
         DivertBinding divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(divertName);
         assertNotNull(divertBinding, "Divert '" + divertName + "' binding should exist.");

         // Message should be routed to the forwarding queue only
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, Map.of());

         // Route to both queues
         deployBrokerConfig(embeddedActiveMQ, "reload-divert-non-exclusive.xml");
         sendDivertedTestMessage(sourceQueue, targetQueue, true, true, Map.of());

         // Route to the forwarding queue only
         deployBrokerConfig(embeddedActiveMQ, "reload-divert-exclusive.xml");
         sendDivertedTestMessage(sourceQueue, targetQueue, false, true, Map.of());
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testChangeDivertAddress() throws Exception {

      final EmbeddedActiveMQ embeddedActiveMQ = createEmbeddedActiveMQServer("reload-divert-address-source1.xml");
      final SimpleString divertName = SimpleString.of("source-to-target");
      final Queue sourceQueue1 = (Queue) ActiveMQDestination.createDestination("queue://source1", ActiveMQDestination.TYPE.QUEUE);
      final Queue sourceQueue2 = (Queue) ActiveMQDestination.createDestination("queue://source2", ActiveMQDestination.TYPE.QUEUE);
      final Queue targetQueue = (Queue) ActiveMQDestination.createDestination("queue://target", ActiveMQDestination.TYPE.QUEUE);

      try {
         DivertBinding divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(divertName);
         assertNotNull(divertBinding, "Divert '" + divertName + "' binding should exist.");
         assertEquals(sourceQueue1.getQueueName(), divertBinding.getAddress().toString(), "Divert '" + divertName + "' address should be '" + sourceQueue1.getQueueName() + "'.");
         sendDivertedTestMessage(sourceQueue1, targetQueue, false, true, Map.of());
         sendDivertedTestMessage(sourceQueue2, targetQueue, true, false, Map.of());

         deployBrokerConfig(embeddedActiveMQ, "reload-divert-address-source2.xml");

         divertBinding = (DivertBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(divertName);
         assertNotNull(divertBinding, "Divert '" + divertName + "' binding should exist.");
         assertEquals(sourceQueue2.getQueueName(), divertBinding.getAddress().toString(), "Divert '" + divertName + "' address should have been updated to '" + sourceQueue2.getQueueName() + "'.");
         sendDivertedTestMessage(sourceQueue1, targetQueue, true, false, Map.of());
         sendDivertedTestMessage(sourceQueue2, targetQueue, false, true, Map.of());
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   // TODO: Test divert transformers: add, change, remove

   @Test
   public void testRedeployWithFailover() throws Exception {
      Set<Role> original = new HashSet<>();
      original.add(new Role("a", false, true, false, false, false, false, false, false, false, false, false, false));
      Set<Role> changed = new HashSet<>();
      changed.add(new Role("b", false, true, false, false, false, false, false, false, false, false, false, false));



      EmbeddedActiveMQ primary = new EmbeddedActiveMQ();
      EmbeddedActiveMQ backup = new EmbeddedActiveMQ();

      try {
         // set these system properties to use in the relevant broker.xml files
         System.setProperty("primary-data-dir", getTestDirfile().toPath() + "/redeploy-primary-data");
         System.setProperty("backup-data-dir", getTestDirfile().toPath() + "/redeploy-backup-data");

         Path primaryBrokerXML = getTestDirfile().toPath().resolve("primary.xml");
         Path backupBrokerXML = getTestDirfile().toPath().resolve("backup.xml");
         URL url1 = RedeployTest.class.getClassLoader().getResource("reload-primary-original.xml");
         URL url2 = RedeployTest.class.getClassLoader().getResource("reload-primary-changed.xml");
         URL url3 = RedeployTest.class.getClassLoader().getResource("reload-backup-original.xml");
         URL url4 = RedeployTest.class.getClassLoader().getResource("reload-backup-changed.xml");
         Files.copy(url1.openStream(), primaryBrokerXML);
         Files.copy(url3.openStream(), backupBrokerXML);

         primary.setConfigResourcePath(primaryBrokerXML.toUri().toString());
         primary.start();

         waitForServerToStart(primary.getActiveMQServer());

         backup.setConfigResourcePath(backupBrokerXML.toUri().toString());
         backup.start();

         assertTrue(Wait.waitFor(() -> backup.getActiveMQServer().isReplicaSync(), 15000, 200));

         assertEquals(AddressFullMessagePolicy.BLOCK, primary.getActiveMQServer().getAddressSettingsRepository().getMatch("myQueue").getAddressFullMessagePolicy(), "Test address settings original - primary");
         assertEquals(AddressFullMessagePolicy.BLOCK, backup.getActiveMQServer().getAddressSettingsRepository().getMatch("myQueue").getAddressFullMessagePolicy(), "Test address settings original - backup");
         assertEquals(original, primary.getActiveMQServer().getSecurityRepository().getMatch("myQueue"), "Test security settings original - primary");
         assertEquals(original, backup.getActiveMQServer().getSecurityRepository().getMatch("myQueue"), "Test security settings original - backup");

         final ReusableLatch primaryReloadLatch = new ReusableLatch(1);
         Runnable liveTick = () -> primaryReloadLatch.countDown();
         primary.getActiveMQServer().getReloadManager().setTick(liveTick);

         final ReusableLatch backupReloadTickLatch = new ReusableLatch(1);
         Runnable backupTick = () -> backupReloadTickLatch.countDown();
         backup.getActiveMQServer().getReloadManager().setTick(backupTick);

         primaryReloadLatch.await(10, TimeUnit.SECONDS);
         Files.copy(url2.openStream(), primaryBrokerXML, StandardCopyOption.REPLACE_EXISTING);
         primaryBrokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         primaryReloadLatch.countUp();
         primary.getActiveMQServer().getReloadManager().setTick(liveTick);
         primaryReloadLatch.await(10, TimeUnit.SECONDS);

         backupReloadTickLatch.await(10, TimeUnit.SECONDS);
         Files.copy(url4.openStream(), backupBrokerXML, StandardCopyOption.REPLACE_EXISTING);
         backupBrokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         backupReloadTickLatch.countUp();
         backup.getActiveMQServer().getReloadManager().setTick(backupTick);
         backupReloadTickLatch.await(10, TimeUnit.SECONDS);

         ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://127.0.0.1:61616");
         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession();
            Queue queue = session.createQueue("myQueue2");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("text1"));
         }

         assertFalse(backup.getActiveMQServer().isActive());
         assertEquals(AddressFullMessagePolicy.PAGE, primary.getActiveMQServer().getAddressSettingsRepository().getMatch("myQueue").getAddressFullMessagePolicy(), "Test address settings redeploy - primary");
         assertEquals(changed, primary.getActiveMQServer().getSecurityRepository().getMatch("myQueue"), "Test security settings redeploy - primary");

         primary.stop();

         assertTrue(Wait.waitFor(() -> (backup.getActiveMQServer().isActive()), 5000, 100));

         factory = new ActiveMQConnectionFactory("tcp://127.0.0.1:61617");
         try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession();
            Queue queue = session.createQueue("myQueue2");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("text"));
            connection.start();
            MessageConsumer consumer = session.createConsumer(session.createQueue("myQueue2"));
            assertNotNull(consumer.receive(5000), "Queue wasn't deployed accordingly");
            assertNotNull(consumer.receive(5000));
         }
         assertEquals(changed, backup.getActiveMQServer().getSecurityRepository().getMatch("myQueue"), "Test security settings redeploy - backup");
         assertEquals(AddressFullMessagePolicy.PAGE, backup.getActiveMQServer().getAddressSettingsRepository().getMatch("myQueue").getAddressFullMessagePolicy(), "Test address settings redeploy - backup");
      } finally {
         primary.stop();
         backup.stop();
         System.clearProperty("primary-data-dir");
         System.clearProperty("backup-data-dir");
      }
   }

   private boolean tryConsume() throws JMSException {
      try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
           Connection connection = factory.createConnection();
           Session session = connection.createSession(Session.AUTO_ACKNOWLEDGE)) {
         Queue queue = session.createQueue("NewQueue");
         MessageConsumer consumer = session.createConsumer(queue);
         return true;
      } catch (JMSException e) {
         return false;
      }

   }

   @Test
   public void testRedeployAddressQueue() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-address-queues.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-address-queues-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
      try (JMSContext jmsContext = connectionFactory.createContext()) {
         jmsContext.createSharedDurableConsumer(jmsContext.createTopic("config_test_consumer_created_queues"), "mySub").receive(100);
      }

      try {
         latch.await(10, TimeUnit.SECONDS);
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_consumer_created_queues").contains("mySub"));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal_no_queue"));
         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal"));
         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_removal"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_1"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_2"));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "permanent_test_address_removal"));
         assertNotNull(getAddressInfo(embeddedActiveMQ, "permanent_test_queue_removal"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "permanent_test_queue_removal").contains("permanent_test_queue_removal_queue_1"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "permanent_test_queue_removal").contains("permanent_test_queue_removal_queue_2"));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_change"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_change").contains("config_test_queue_change_queue"));
         assertEquals(10, getQueue(embeddedActiveMQ, "config_test_queue_change_queue").getMaxConsumers());
         assertFalse(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isPurgeOnNoConsumers());

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         //Ensure queues created by clients (NOT by broker.xml are not removed when we reload).
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_consumer_created_queues").contains("mySub"));

         assertNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal_no_queue"));
         assertNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal"));
         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_removal"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_1"));
         assertFalse(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_2"));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "permanent_test_address_removal"));
         assertNotNull(getAddressInfo(embeddedActiveMQ, "permanent_test_queue_removal"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "permanent_test_queue_removal").contains("permanent_test_queue_removal_queue_1"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "permanent_test_queue_removal").contains("permanent_test_queue_removal_queue_2"));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_change"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_change").contains("config_test_queue_change_queue"));
         assertEquals(1, getQueue(embeddedActiveMQ, "config_test_queue_change_queue").getMaxConsumers());
         assertTrue(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isPurgeOnNoConsumers());

         assertNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_change_queue"));
         assertNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_removal_queue_1"));
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeployChangeQueueRoutingType() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-queue-routingtype.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-queue-routingtype-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://0.0.0.0:61616");
         try (JMSContext context = connectionFactory.createContext()) {
            context.createProducer().send(context.createQueue("myAddress"), "hello");
         }

         latch.await(10, TimeUnit.SECONDS);
         assertNotNull(getAddressInfo(embeddedActiveMQ, "myAddress"));
         assertEquals(RoutingType.ANYCAST, getQueue(embeddedActiveMQ, "myQueue").getRoutingType());

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);
         assertTrue(latch.await(10, TimeUnit.SECONDS));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "myAddress"));
         assertEquals(RoutingType.MULTICAST, getQueue(embeddedActiveMQ, "myQueue").getRoutingType());

         //Ensures the queue isnt detroyed by checking message sent before change is consumable after (e.g. no message loss)
         try (JMSContext context = connectionFactory.createContext()) {
            Message message = context.createSharedDurableConsumer(context.createTopic("myAddress"), "myQueue").receive();
            assertEquals("hello", ((TextMessage) message).getText());
         }

      } finally {
         embeddedActiveMQ.stop();
      }
   }


   @Test
   public void testRedeployChangeAddressQueueRoutingType() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-address-queue-routingtype.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-address-queue-routingtype-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);

      try {
         latch.await(10, TimeUnit.SECONDS);
         assertNotNull(getAddressInfo(embeddedActiveMQ, "TEST.QUEUE.0"));
         assertTrue(getAddressInfo(embeddedActiveMQ, "TEST.QUEUE.0").getRoutingTypes().contains(RoutingType.ANYCAST));
         assertEquals(RoutingType.ANYCAST, getQueue(embeddedActiveMQ, "TEST.QUEUE.0.1").getRoutingType());
         assertEquals(RoutingType.ANYCAST, getQueue(embeddedActiveMQ, "TEST.QUEUE.0.2").getRoutingType());
         assertEquals(RoutingType.ANYCAST, getQueue(embeddedActiveMQ, "TEST.QUEUE.0.3").getRoutingType());
         assertEquals(RoutingType.ANYCAST, getQueue(embeddedActiveMQ, "TEST.QUEUE.0.4").getRoutingType());

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(latch::countDown);
         assertTrue(latch.await(10, TimeUnit.SECONDS));

         AddressInfo testQueue0AddressInfo = getAddressInfo(embeddedActiveMQ, "TEST.QUEUE.0");
         assertNotNull(testQueue0AddressInfo);
         assertTrue(testQueue0AddressInfo.getRoutingTypes().contains(RoutingType.ANYCAST));
         assertTrue(testQueue0AddressInfo.getRoutingTypes().contains(RoutingType.MULTICAST));
         assertEquals(RoutingType.ANYCAST, getQueue(embeddedActiveMQ, "TEST.QUEUE.0.1").getRoutingType());
         assertEquals(RoutingType.MULTICAST, getQueue(embeddedActiveMQ, "TEST.QUEUE.0.2").getRoutingType());
         assertNotNull(getAddressInfo(embeddedActiveMQ, "TEST.QUEUE.0.3"));
         assertTrue(getAddressInfo(embeddedActiveMQ, "TEST.QUEUE.0.3").getRoutingTypes().contains(RoutingType.ANYCAST));
         assertEquals(RoutingType.ANYCAST, getQueue(embeddedActiveMQ, "TEST.QUEUE.0.3").getRoutingType());
         assertNotNull(getAddressInfo(embeddedActiveMQ, "TEST.QUEUE.0.4"));
         assertTrue(getAddressInfo(embeddedActiveMQ, "TEST.QUEUE.0.4").getRoutingTypes().contains(RoutingType.MULTICAST));
         assertEquals(RoutingType.MULTICAST, getQueue(embeddedActiveMQ, "TEST.QUEUE.0.4").getRoutingType());
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   /**
    * Simulates Stop and Start that occurs when network health checker stops the server when network is detected
    * unhealthy and re-starts the broker once detected that it is healthy again.
    *
    * @throws Exception for anything un-expected, test will fail.
    */
   @Test
   public void testRedeployStopAndRestart() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-original.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-changed.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);

      Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         assertEquals(1, getSecurityRoles(embeddedActiveMQ, "security_address").size());
         assertEquals("b", getSecurityRoles(embeddedActiveMQ, "security_address").iterator().next().getName());

         assertEquals(getAddressSettings(embeddedActiveMQ, "address_settings_address").getDeadLetterAddress(), SimpleString.of("OriginalDLQ"));
         assertEquals(getAddressSettings(embeddedActiveMQ, "address_settings_address").getExpiryAddress(), SimpleString.of("OriginalExpiryQueue"));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal_no_queue"));
         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal"));
         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_removal"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_1"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_2"));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_change"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_change").contains("config_test_queue_change_queue"));
         assertEquals(10, getQueue(embeddedActiveMQ, "config_test_queue_change_queue").getMaxConsumers());
         assertFalse(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isPurgeOnNoConsumers());
         assertTrue(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isEnabled());

         assertTrue(getQueue(embeddedActiveMQ, "config_test_queue_change_queue_defaults").isPurgeOnNoConsumers());
         assertFalse(getQueue(embeddedActiveMQ, "config_test_queue_change_queue_defaults").isEnabled());

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         //Assert that the security settings change applied
         assertEquals(1, getSecurityRoles(embeddedActiveMQ, "security_address").size());
         assertEquals("c", getSecurityRoles(embeddedActiveMQ, "security_address").iterator().next().getName());

         //Assert that the address settings change applied
         assertEquals(getAddressSettings(embeddedActiveMQ, "address_settings_address").getDeadLetterAddress(), SimpleString.of("NewDLQ"));
         assertEquals(getAddressSettings(embeddedActiveMQ, "address_settings_address").getExpiryAddress(), SimpleString.of("NewExpiryQueue"));

         //Assert the address and queue changes applied
         assertNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal_no_queue"));
         assertNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal"));
         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_removal"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_1"));
         assertFalse(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_2"));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_change"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_change").contains("config_test_queue_change_queue"));
         assertEquals(1, getQueue(embeddedActiveMQ, "config_test_queue_change_queue").getMaxConsumers());
         assertTrue(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isPurgeOnNoConsumers());
         assertFalse(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isEnabled());

         assertFalse(getQueue(embeddedActiveMQ, "config_test_queue_change_queue_defaults").isPurgeOnNoConsumers());
         assertTrue(getQueue(embeddedActiveMQ, "config_test_queue_change_queue_defaults").isEnabled());

      } finally {
         embeddedActiveMQ.stop();
      }


      try {
         embeddedActiveMQ.start();

         //Assert that the security settings changes persist a stop and start server (e.g. like what occurs if network health check stops the node), but JVM remains up.
         assertEquals(1, getSecurityRoles(embeddedActiveMQ, "security_address").size());
         assertEquals("c", getSecurityRoles(embeddedActiveMQ, "security_address").iterator().next().getName());

         //Assert that the address settings changes persist a stop and start server (e.g. like what occurs if network health check stops the node), but JVM remains up.
         assertEquals(getAddressSettings(embeddedActiveMQ, "address_settings_address").getDeadLetterAddress(), SimpleString.of("NewDLQ"));
         assertEquals(getAddressSettings(embeddedActiveMQ, "address_settings_address").getExpiryAddress(), SimpleString.of("NewExpiryQueue"));

         //Assert that the address and queue changes persist a stop and start server (e.g. like what occurs if network health check stops the node), but JVM remains up.
         assertNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal_no_queue"));
         assertNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal"));
         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_removal"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_1"));
         assertFalse(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_2"));

         assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_change"));
         assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_change").contains("config_test_queue_change_queue"));
         assertEquals(1, getQueue(embeddedActiveMQ, "config_test_queue_change_queue").getMaxConsumers());
         assertTrue(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isPurgeOnNoConsumers());
         assertFalse(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isEnabled());

         assertFalse(getQueue(embeddedActiveMQ, "config_test_queue_change_queue_defaults").isPurgeOnNoConsumers());
         assertTrue(getQueue(embeddedActiveMQ, "config_test_queue_change_queue_defaults").isEnabled());
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeployNewAcceptorsAndRemoveOldAcceptor() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-acceptor.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-acceptor-add-new.xml");
      Files.copy(url1.openStream(), brokerXML);

      final MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();
      runAfter(() -> MBeanServerFactory.releaseMBeanServer(mBeanServer));

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.setMbeanServer(mBeanServer);
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);
      final Runnable tick = latch::countDown;
      final ManagementService managementService = embeddedActiveMQ.getActiveMQServer().getManagementService();

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         final TransportConfiguration acceptor0 = findInConfiguration("artemis", embeddedActiveMQ.getActiveMQServer().getConfiguration());

         assertNotNull(acceptor0);
         assertEquals("127.0.0.1", acceptor0.getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61616", acceptor0.getParams().get(TransportConstants.PORT_PROP_NAME));

         final AcceptorControl acceptorControl = managementService.getAcceptorControl("artemis");

         assertNotNull(acceptorControl);
         assertTrue(acceptorControl.isStarted());

         @SuppressWarnings("resource")
         final ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://127.0.0.1:61616");
         final Connection connection = connectionFactory.createConnection();

         try (Session session = connection.createSession()) {
            final Queue queue = session.createQueue("test-queue");

            session.createConsumer(queue);
         } catch (Exception e) {
            fail("Should be able to connect and create resources");
         }

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         // Should not be present in the server any longer
         final AcceptorControl afterReload = managementService.getAcceptorControl("artemis");
         assertNull(afterReload);

         final TransportConfiguration acceptor1 = findInConfiguration("artemis1", embeddedActiveMQ.getActiveMQServer().getConfiguration());
         final TransportConfiguration acceptor2 = findInConfiguration("artemis2", embeddedActiveMQ.getActiveMQServer().getConfiguration());

         assertNotNull(acceptor1);
         assertEquals("127.0.0.2", acceptor1.getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61617", acceptor1.getParams().get(TransportConstants.PORT_PROP_NAME));

         final AcceptorControl acceptorControl1 = managementService.getAcceptorControl("artemis1");

         assertNotNull(acceptorControl1);
         assertTrue(acceptorControl1.isStarted());

         assertNotNull(acceptor2);
         assertEquals("127.0.0.3", acceptor2.getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61618", acceptor2.getParams().get(TransportConstants.PORT_PROP_NAME));

         final AcceptorControl acceptorControl2 = managementService.getAcceptorControl("artemis2");

         assertNotNull(acceptorControl2);
         assertFalse(acceptorControl2.isStarted());

         // Our original connection on the removed acceptor should have been closed.
         try (Session session = connection.createSession()) {
            final Queue queue = session.createQueue("test-queue");

            session.createConsumer(queue);
            fail("Should have been disconnected when acceptor was removed.");
         } catch (Exception e) {
         }
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testRedeploySameAcceptorChangedToNotAutoStart() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-acceptor.xml");
      URL url2 = RedeployTest.class.getClassLoader().getResource("reload-acceptor-updated.xml");
      Files.copy(url1.openStream(), brokerXML);

      final MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();
      runAfter(() -> MBeanServerFactory.releaseMBeanServer(mBeanServer));

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.setMbeanServer(mBeanServer);
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);
      final Runnable tick = latch::countDown;
      final ManagementService managementService = embeddedActiveMQ.getActiveMQServer().getManagementService();

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         final TransportConfiguration acceptor = findInConfiguration("artemis", embeddedActiveMQ.getActiveMQServer().getConfiguration());

         assertNotNull(acceptor);
         assertEquals("127.0.0.1", acceptor.getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61616", acceptor.getParams().get(TransportConstants.PORT_PROP_NAME));
         assertNull(acceptor.getParams().get(TransportConstants.AUTO_START));

         final AcceptorControl acceptorControl = (AcceptorControl) managementService.getAcceptorControl("artemis");

         assertNotNull(acceptorControl);
         assertTrue(acceptorControl.isStarted());

         @SuppressWarnings("resource")
         final ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://127.0.0.1:61616");
         final Connection connection = connectionFactory.createConnection();

         try (Session session = connection.createSession()) {
            final Queue queue = session.createQueue("test-queue");

            session.createConsumer(queue);
         } catch (Exception e) {
            fail("Should be able to connect and create resources");
         }

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);
         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         final TransportConfiguration acceptorUpdated = findInConfiguration("artemis", embeddedActiveMQ.getActiveMQServer().getConfiguration());

         assertNotNull(acceptorUpdated);
         assertEquals("127.0.0.1", acceptorUpdated.getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61616", acceptorUpdated.getParams().get(TransportConstants.PORT_PROP_NAME));
         assertEquals("false", acceptorUpdated.getParams().get(TransportConstants.AUTO_START));

         final AcceptorControl acceptorControl2 = managementService.getAcceptorControl("artemis");

         assertNotNull(acceptorControl2);

         // Re-added and not started according to new configuration
         assertFalse(acceptorControl2.isStarted());

         // Our original connection on the removed acceptor should have been closed.
         try (Session session = connection.createSession()) {
            final Queue queue = session.createQueue("test-queue");

            session.createConsumer(queue);
            fail("Should have been disconnected when acceptor was removed.");
         } catch (Exception e) {
         }

         // New connection should fail the acceptor is not auto starting now
         try (ActiveMQConnectionFactory connectionFactory1 = new ActiveMQConnectionFactory("tcp://127.0.0.1:61616")) {
            connectionFactory1.createConnection();
            fail("Should not be able to connect");
         } catch (Exception e) {
         }
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testUpdatedAcceptorStillWatchesForTLSPropertiesUpdatesAfterReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      Path brokerProperties = getTestDirfile().toPath().resolve("broker.properties");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-no-acceptor-config.xml");
      Files.copy(url1.openStream(), brokerXML);

      // reference Key Store from temporary location that we can update later
      File keyStoreToReload = new File(getTestDirfile(), "server-ks.p12");
      copyRecursive(new File(this.getClass().getClassLoader().getResource("unknown-server-keystore.p12").getFile()), keyStoreToReload);

      final Properties properties = new ConfigurationImpl.InsertionOrderedProperties();

      properties.put("acceptorConfigurations.tcp.factoryClassName", NETTY_ACCEPTOR_FACTORY);
      properties.put("acceptorConfigurations.tcp.params.host", "127.0.0.1");
      properties.put("acceptorConfigurations.tcp.params.port", "61617");
      properties.put("acceptorConfigurations.tcp.params." + TransportConstants.SSL_AUTO_RELOAD_PROP_NAME, "true");
      properties.put("acceptorConfigurations.tcp.params." + TransportConstants.SSL_ENABLED_PROP_NAME, "true");
      properties.put("acceptorConfigurations.tcp.params." + TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStoreToReload.getAbsolutePath());
      properties.put("acceptorConfigurations.tcp.params." + TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");

      Writer propertiesWriter = Files.newBufferedWriter(brokerProperties, StandardOpenOption.WRITE,
                                                                          StandardOpenOption.CREATE,
                                                                          StandardOpenOption.TRUNCATE_EXISTING);
      try {
         properties.store(propertiesWriter, null);
      } finally {
         propertiesWriter.flush();
         propertiesWriter.close();
      }

      final MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();
      runAfter(() -> MBeanServerFactory.releaseMBeanServer(mBeanServer));

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setPropertiesResourcePath(brokerProperties.toString());
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.setMbeanServer(mBeanServer);
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);
      final Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         final TransportConfiguration acceptor = findInConfiguration("tcp", embeddedActiveMQ.getActiveMQServer().getConfiguration());

         assertNotNull(acceptor);
         assertEquals("127.0.0.1", acceptor.getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61617", acceptor.getParams().get(TransportConstants.PORT_PROP_NAME));

         String url = "tcp://127.0.0.1:61616?sslEnabled=true;trustStorePath=server-ca-truststore.p12;trustStorePassword=securepass";
         ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocator(url)).setCallTimeout(3000);

         try {
            createSessionFactory(locator);
            fail("Creating session here should fail due to SSL handshake problems.");
         } catch (Exception ignored) {
         }

         // Update the broker properties file with the correct port to trigger configuration reload.
         // This should retain the ability to watch the TLS resources for updates and after the reload
         // the test will update the key store to one that allows the client to connect and we should
         // see the client succeed eventually.
         properties.put("acceptorConfigurations.tcp.params.port", "61616");

         propertiesWriter = Files.newBufferedWriter(brokerProperties, StandardOpenOption.WRITE,
                                                                      StandardOpenOption.TRUNCATE_EXISTING);
         try {
            properties.store(propertiesWriter, null);
         } finally {
            propertiesWriter.flush();
            propertiesWriter.close();
         }

         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         final TransportConfiguration updatedAcceptor = findInConfiguration("tcp", embeddedActiveMQ.getActiveMQServer().getConfiguration());

         assertNotNull(updatedAcceptor);
         assertEquals("127.0.0.1", updatedAcceptor.getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61616", updatedAcceptor.getParams().get(TransportConstants.PORT_PROP_NAME));

         // update the server side key store with one that actually works with the client trust store
         copyRecursive(new File(this.getClass().getClassLoader().getResource("server-keystore.p12").getFile()), keyStoreToReload);

         // expect success after auto reload, which we wait for
         Wait.assertTrue(() -> {
            try {
               addSessionFactory(createSessionFactory(locator));
               return true;
            } catch (Throwable ignored) {
            }
            return false;
         }, 5000, 100);
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   public void testReplacedAcceptorWatchesForTLSPropertiesUpdatesAfterReload() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      Path brokerProperties = getTestDirfile().toPath().resolve("broker.properties");
      URL url1 = RedeployTest.class.getClassLoader().getResource("reload-no-acceptor-config.xml");
      Files.copy(url1.openStream(), brokerXML);

      // reference Key Store from temporary location that we can update later
      File keyStoreToReload = new File(getTestDirfile(), "server-ks.p12");
      copyRecursive(new File(this.getClass().getClassLoader().getResource("unknown-server-keystore.p12").getFile()), keyStoreToReload);

      final Properties properties = new ConfigurationImpl.InsertionOrderedProperties();

      properties.put("acceptorConfigurations.tcp1.factoryClassName", NETTY_ACCEPTOR_FACTORY);
      properties.put("acceptorConfigurations.tcp1.params.host", "127.0.0.1");
      properties.put("acceptorConfigurations.tcp1.params.port", "61617");
      properties.put("acceptorConfigurations.tcp1.params." + TransportConstants.SSL_AUTO_RELOAD_PROP_NAME, "true");
      properties.put("acceptorConfigurations.tcp1.params." + TransportConstants.SSL_ENABLED_PROP_NAME, "true");
      properties.put("acceptorConfigurations.tcp1.params." + TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStoreToReload.getAbsolutePath());
      properties.put("acceptorConfigurations.tcp1.params." + TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");

      Writer propertiesWriter = Files.newBufferedWriter(brokerProperties, StandardOpenOption.WRITE,
                                                                          StandardOpenOption.CREATE,
                                                                          StandardOpenOption.TRUNCATE_EXISTING);
      try {
         properties.store(propertiesWriter, null);
      } finally {
         propertiesWriter.flush();
         propertiesWriter.close();
      }

      final MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();
      runAfter(() -> MBeanServerFactory.releaseMBeanServer(mBeanServer));

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setPropertiesResourcePath(brokerProperties.toString());
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.setMbeanServer(mBeanServer);
      embeddedActiveMQ.start();

      final ReusableLatch latch = new ReusableLatch(1);
      final Runnable tick = latch::countDown;

      embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);

      try {
         latch.await(10, TimeUnit.SECONDS);

         final TransportConfiguration acceptor = findInConfiguration("tcp1", embeddedActiveMQ.getActiveMQServer().getConfiguration());

         assertNotNull(acceptor);
         assertEquals("127.0.0.1", acceptor.getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61617", acceptor.getParams().get(TransportConstants.PORT_PROP_NAME));

         String url = "tcp://127.0.0.1:61616?sslEnabled=true;trustStorePath=server-ca-truststore.p12;trustStorePassword=securepass";
         ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocator(url)).setCallTimeout(3000);

         try {
            createSessionFactory(locator);
            fail("Creating session here should fail due to SSL handshake problems.");
         } catch (Exception ignored) {
         }

         // Update the broker properties file with a new acceptor on the correct port to trigger configuration
         // reload. This should retain the ability to watch the TLS resources for updates and after the reload
         // the test will update the key store to one that allows the client to connect and we should see the
         // client succeed eventually.
         properties.clear();
         properties.put("acceptorConfigurations.tcp2.factoryClassName", NETTY_ACCEPTOR_FACTORY);
         properties.put("acceptorConfigurations.tcp2.params.host", "127.0.0.1");
         properties.put("acceptorConfigurations.tcp2.params.port", "61616");
         properties.put("acceptorConfigurations.tcp2.params." + TransportConstants.SSL_AUTO_RELOAD_PROP_NAME, "true");
         properties.put("acceptorConfigurations.tcp2.params." + TransportConstants.SSL_ENABLED_PROP_NAME, "true");
         properties.put("acceptorConfigurations.tcp2.params." + TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStoreToReload.getAbsolutePath());
         properties.put("acceptorConfigurations.tcp2.params." + TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");

         propertiesWriter = Files.newBufferedWriter(brokerProperties, StandardOpenOption.WRITE,
                                                                      StandardOpenOption.TRUNCATE_EXISTING);
         try {
            properties.store(propertiesWriter, null);
         } finally {
            propertiesWriter.flush();
            propertiesWriter.close();
         }

         latch.setCount(1);
         embeddedActiveMQ.getActiveMQServer().getReloadManager().setTick(tick);
         latch.await(10, TimeUnit.SECONDS);

         // Old acceptor should be null but new one should be present and should be watching for updates
         assertNull(findInConfiguration("tcp1", embeddedActiveMQ.getActiveMQServer().getConfiguration()));
         final TransportConfiguration updatedAcceptor = findInConfiguration("tcp2", embeddedActiveMQ.getActiveMQServer().getConfiguration());

         assertNotNull(updatedAcceptor);
         assertEquals("127.0.0.1", updatedAcceptor.getParams().get(TransportConstants.HOST_PROP_NAME));
         assertEquals("61616", updatedAcceptor.getParams().get(TransportConstants.PORT_PROP_NAME));

         // update the server side key store with one that actually works with the client trust store
         copyRecursive(new File(this.getClass().getClassLoader().getResource("server-keystore.p12").getFile()), keyStoreToReload);

         // expect success after auto reload, which we wait for
         Wait.assertTrue(() -> {
            try {
               addSessionFactory(createSessionFactory(locator));
               return true;
            } catch (Throwable ignored) {
            }
            return false;
         }, 5000, 100);
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   private TransportConfiguration findInConfiguration(String acceptorName, Configuration configuration) {
      final Set<TransportConfiguration> acceptors = configuration.getAcceptorConfigurations();

      for (TransportConfiguration acceptorConfig : acceptors) {
         if (acceptorName.equals(acceptorConfig.getName())) {
            return acceptorConfig;
         }
      }

      return null;
   }

   private AddressSettings getAddressSettings(EmbeddedActiveMQ embeddedActiveMQ, String address) {
      return embeddedActiveMQ.getActiveMQServer().getAddressSettingsRepository().getMatch(address);
   }

   private Set<Role> getSecurityRoles(EmbeddedActiveMQ embeddedActiveMQ, String address) {
      return embeddedActiveMQ.getActiveMQServer().getSecurityRepository().getMatch(address);
   }

   private AddressInfo getAddressInfo(EmbeddedActiveMQ embeddedActiveMQ, String address) {
      return embeddedActiveMQ.getActiveMQServer().getPostOffice().getAddressInfo(SimpleString.of(address));
   }

   private org.apache.activemq.artemis.core.server.Queue getQueue(EmbeddedActiveMQ embeddedActiveMQ, String queueName) throws Exception {
      QueueBinding queueBinding = (QueueBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(SimpleString.of(queueName));
      return queueBinding == null ? null : queueBinding.getQueue();
   }

   private List<String> listQueuesNamesForAddress(EmbeddedActiveMQ embeddedActiveMQ, String address) throws Exception {
      return embeddedActiveMQ.getActiveMQServer().getPostOffice().listQueuesForAddress(SimpleString.of(address)).stream().map(
          org.apache.activemq.artemis.core.server.Queue::getName).map(SimpleString::toString).collect(Collectors.toList());
   }

}
