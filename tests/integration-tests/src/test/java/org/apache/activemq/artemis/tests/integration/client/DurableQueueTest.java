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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DurableQueueTest extends ActiveMQTestBase {


   private ActiveMQServer server;

   private ClientSession session;

   private ClientSessionFactory sf;

   private ServerLocator locator;



   @Test
   public void testConsumeFromDurableQueue() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(false));

      session.start();
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);
      message.acknowledge();

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testConsumeFromDurableQueueAfterServerRestart() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(true));

      session.close();

      server.stop();
      server.start();

      sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true);

      session.start();
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);
      message.acknowledge();

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testUserEncoding() throws Exception {
      final String userName = "myUser";
      session.close();
      session = sf.createSession(userName, "myPass", false, true, true, false, 0);

      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address));

      session.close();

      server.stop();
      server.start();

      assertEquals(1, ((ActiveMQServerImpl) server).getQueueCountForUser(userName));
   }

   @Test
   public void testProduceAndConsumeFromDurableQueueAfterServerRestart() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address));

      session.close();

      server.stop();
      server.start();

      sf = createSessionFactory(locator);

      session = sf.createSession(false, true, true);

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(true));

      session.start();
      ClientConsumer consumer = session.createConsumer(queue);
      ClientMessage message = consumer.receive(500);
      assertNotNull(message);
      message.acknowledge();

      consumer.close();
      session.deleteQueue(queue);
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(true);

      server.start();

      locator = createInVMNonHALocator();

      sf = createSessionFactory(locator);

      session = addClientSession(sf.createSession(false, true, true));
   }
}
