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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PredefinedQueueTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private Configuration configuration = null;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      configuration = createDefaultInVMConfig();
   }

   @Test
   public void testFailOnCreatePredefinedQueues() throws Exception {
      final String testAddress = "testAddress";

      final String queueName1 = "queue1";

      final String queueName2 = "queue2";

      final String queueName3 = "queue3";

      QueueConfiguration queue1 = QueueConfiguration.of(queueName1).setAddress(testAddress);

      QueueConfiguration queue2 = QueueConfiguration.of(queueName2).setAddress(testAddress);

      QueueConfiguration queue3 = QueueConfiguration.of(queueName3).setAddress(testAddress);

      List<QueueConfiguration> queueConfs = new ArrayList<>();

      queueConfs.add(queue1);
      queueConfs.add(queue2);
      queueConfs.add(queue3);

      configuration.setQueueConfigs(queueConfs);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true));

      try {
         session.createQueue(QueueConfiguration.of(queueName1).setAddress(testAddress).setFilterString("").setDurable(false));

         fail("Should throw exception");
      } catch (ActiveMQQueueExistsException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      try {
         session.createQueue(QueueConfiguration.of(queueName2).setAddress(testAddress).setDurable(false));

         fail("Should throw exception");
      } catch (ActiveMQQueueExistsException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      try {
         session.createQueue(QueueConfiguration.of(queueName3).setAddress(testAddress).setDurable(false));

         fail("Should throw exception");
      } catch (ActiveMQQueueExistsException se) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @Test
   public void testDeploySameNames() throws Exception {
      final String testAddress = "testAddress";

      final String queueName1 = "queue1";

      final String queueName2 = "queue2";

      QueueConfiguration queue1 = QueueConfiguration.of(queueName1).setAddress(testAddress);

      QueueConfiguration queue2 = QueueConfiguration.of(queueName2).setAddress(testAddress);

      configuration.addQueueConfiguration(queue1).addQueueConfiguration(queue2);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));

      server.start();

      Bindings bindings = server.getPostOffice().getBindingsForAddress(SimpleString.of(testAddress));

      assertEquals(2, bindings.getBindings().size());

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true));

      session.start();

      ClientProducer producer = addClientProducer(session.createProducer(SimpleString.of(testAddress)));

      ClientConsumer consumer1 = addClientConsumer(session.createConsumer(queueName1));

      ClientConsumer consumer2 = addClientConsumer(session.createConsumer(queueName2));

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(200);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message.acknowledge();

         message = consumer2.receive(200);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());
   }

   @Test
   public void testDeployPreexistingQueues() throws Exception {
      final String testAddress = "testAddress";

      final String queueName1 = "queue1";

      final String queueName2 = "queue2";

      final String queueName3 = "queue3";

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(configuration));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true));

      session.createQueue(QueueConfiguration.of(queueName1).setAddress(testAddress));

      session.createQueue(QueueConfiguration.of(queueName2).setAddress(testAddress));

      session.createQueue(QueueConfiguration.of(queueName3).setAddress(testAddress));

      session.close();

      sf.close();

      server.stop();

      QueueConfiguration queue1 = QueueConfiguration.of(queueName1).setAddress(testAddress);

      QueueConfiguration queue2 = QueueConfiguration.of(queueName2).setAddress(testAddress);

      QueueConfiguration queue3 = QueueConfiguration.of(queueName3).setAddress(testAddress);

      configuration.addQueueConfiguration(queue1).addQueueConfiguration(queue2).addQueueConfiguration(queue3);

      server.start();

      sf = createSessionFactory(locator);

      session = addClientSession(sf.createSession(false, true, true));

      session.start();

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientConsumer consumer3 = session.createConsumer(queueName3);

      final int numMessages = 10;

      final SimpleString propKey = SimpleString.of("testkey");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(200);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message.acknowledge();

         message = consumer2.receive(200);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message.acknowledge();

         message = consumer3.receive(200);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());
      assertNull(consumer3.receiveImmediate());
   }

   @Test
   public void testDurableNonDurable() throws Exception {
      final String testAddress = "testAddress";

      final String queueName1 = "queue1";

      final String queueName2 = "queue2";

      QueueConfiguration queue1 = QueueConfiguration.of(queueName1).setAddress(testAddress).setDurable(false);

      QueueConfiguration queue2 = QueueConfiguration.of(queueName2).setAddress(testAddress);

      List<QueueConfiguration> queueConfs = new ArrayList<>();

      queueConfs.add(queue1);
      queueConfs.add(queue2);

      configuration.addQueueConfiguration(queue1).addQueueConfiguration(queue2);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(configuration));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true));

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      final SimpleString propKey = SimpleString.of("testkey");

      final int numMessages = 1;

      logger.debug("sending messages");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      session.close();

      logger.debug("stopping");

      sf.close();

      server.stop();

      server.start();

      sf = createSessionFactory(locator);

      session = addClientSession(sf.createSession(false, true, true));

      session.start();

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientMessage message = consumer1.receiveImmediate();

      assertNull(message);

      for (int i = 0; i < numMessages; i++) {
         message = consumer2.receive(200);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());
   }

   @Test
   public void testDeployWithFilter() throws Exception {
      final String testAddress = "testAddress";

      final String queueName1 = "queue1";

      final String filter = "cheese='camembert'";

      QueueConfiguration queue1 = QueueConfiguration.of(queueName1).setAddress(testAddress).setFilterString(filter).setDurable(false);

      configuration.addQueueConfiguration(queue1);

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true));

      ClientProducer producer = session.createProducer(SimpleString.of(testAddress));

      final SimpleString propKey = SimpleString.of("testkey");

      final int numMessages = 1;

      logger.debug("sending messages");

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.putStringProperty(SimpleString.of("cheese"), SimpleString.of("camembert"));

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      session.start();

      ClientConsumer consumer1 = session.createConsumer(queueName1);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer1.receive(200);
         assertNotNull(message);
         assertEquals(i, message.getObjectProperty(propKey));
         message.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.putStringProperty(SimpleString.of("cheese"), SimpleString.of("roquefort"));

         message.putIntProperty(propKey, i);

         producer.send(message);
      }

      assertNull(consumer1.receiveImmediate());
   }

}
