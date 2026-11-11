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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageDurabilityTest extends ActiveMQTestBase {


   private ActiveMQServer server;

   private ClientSession session;

   private ClientSessionFactory sf;

   private ServerLocator locator;



   @Test
   public void testNonDurableMessageOnNonDurableQueue() throws Exception {
      boolean durable = true;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(!durable));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(!durable));

      restart();

      session.start();
      try {
         session.createConsumer(queue);
      } catch (ActiveMQNonExistentQueueException neqe) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @Test
   public void testNonDurableMessageOnDurableQueue() throws Exception {
      boolean durable = true;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(!durable));

      restart();

      session.start();
      ClientConsumer consumer = session.createConsumer(queue);
      assertNull(consumer.receiveImmediate());

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testDurableMessageOnDurableQueue() throws Exception {
      boolean durable = true;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(durable));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));

      restart();

      session.start();
      ClientConsumer consumer = session.createConsumer(queue);
      assertNotNull(consumer.receive(500));

      consumer.close();
      session.deleteQueue(queue);
   }

   /**
    * we can send a durable msg to a non durable queue but the msg won't be persisted
    */
   @Test
   public void testDurableMessageOnNonDurableQueue() throws Exception {
      boolean durable = true;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      final SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(!durable));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));

      restart();

      session.start();

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.QUEUE_DOES_NOT_EXIST, () -> session.createConsumer(queue));
   }

   /**
    * we can send a durable msg to a temp queue but the msg won't be persisted
    */
   @Test
   public void testDurableMessageOnTemporaryQueue() throws Exception {
      boolean durable = true;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      final SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(durable));

      restart();

      session.start();
      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.QUEUE_DOES_NOT_EXIST, () -> session.createConsumer(queue));
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


   private void restart() throws Exception {
      session.close();

      server.stop();
      server.start();

      sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true);
   }

}
