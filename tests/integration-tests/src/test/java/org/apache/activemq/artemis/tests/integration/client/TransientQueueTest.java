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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.ActiveMQInvalidTransientQueueUseException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.SingleServerTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

public class TransientQueueTest extends SingleServerTestBase {

   @Override
   protected ActiveMQServer createServer() throws Exception {
      ActiveMQServer server = super.createServer();
      server.getConfiguration().setAddressQueueScanPeriod(100);
      return server;
   }

   @Test
   public void testSimpleTransientQueue() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      session.createSharedQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      assertEquals(1, server.getConnectionCount());

      // we create a second session. the temp queue must be present
      // even after we closed the session which created it
      ClientSession session2 = sf.createSession(false, true, true);

      session.close();

      session2.start();

      session2.createConsumer(queue);

      session2.close();
   }

   @Test
   public void testMultipleConsumers() throws Exception {
      SimpleString queue = SimpleString.of("queue");
      SimpleString address = SimpleString.of("address");

      session.createSharedQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      assertEquals(1, server.getConnectionCount());

      assertNotNull(server.locateQueue(queue));

      ServerLocator locator2 = createLocator();
      ClientSessionFactory sf2 = locator2.createSessionFactory();
      ClientSession session2 = sf2.createSession(true, true);

      // At this point this has no effect, other than making sure the queue exists...
      // the JMS implementation will certainly create the queue again when dealing with
      // non durable shared subscriptions
      session2.createSharedQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientConsumer consumer1 = session.createConsumer(queue);
      ClientConsumer consumer2 = session2.createConsumer(queue);

      session.start();
      session2.start();

      ClientProducer producer = session.createProducer(address);

      for (int i = 0; i < 1000; i++) {
         ClientMessage msg = session.createMessage(false);
         producer.send(msg);
      }

      ClientMessage msg;
      for (int i = 0; i < 500; i++) {
         msg = consumer1.receive(1000);
         assertNotNull(msg);

         msg.acknowledge();
         msg = consumer2.receive(1000);
         assertNotNull(msg);
         msg.acknowledge();
      }

      assertNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());

      for (int i = 0; i < 1000; i++) {
         msg = session.createMessage(false);
         producer.send(msg);
      }

      msg = consumer1.receiveImmediate();
      assertNotNull(msg);
      msg.acknowledge();

      msg = consumer2.receiveImmediate();
      assertNotNull(msg);
      msg.acknowledge();

      consumer1.close();

      consumer2.close();

      // validate if the queue was deleted after the consumer was closed
      Wait.assertTrue(() -> server.locateQueue(queue) == null && server.getAddressInfo(address) == null, 2000, 100);

      session.createSharedQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      Wait.assertTrue(() -> server.locateQueue(queue) != null && server.getAddressInfo(address) != null, 2000, 100);

      consumer1 = session.createConsumer(queue);

      Wait.assertTrue(() -> server.locateQueue(queue) != null, 2000, 100);

      assertNull(consumer1.receiveImmediate());

      consumer1.close();

      Wait.assertTrue(() -> server.locateQueue(queue) == null, 2000, 100);

   }

   @Test
   public void testQueueDifferentConfigs() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      server.locateQueue(queue);
      SimpleString address2 = RandomUtil.randomUUIDSimpleString();

      session.createSharedQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      assertEquals(1, server.getConnectionCount());

      ServerLocator locator2 = createLocator();
      ClientSessionFactory sf2 = locator2.createSessionFactory();
      ClientSession session2 = sf2.createSession(false, false);
      addClientSession(session2);

      boolean exHappened = false;

      try {
         // There's already a queue with that name, we are supposed to throw an exception
         session2.createSharedQueue(QueueConfiguration.of(queue).setAddress(address2).setDurable(false));
      } catch (ActiveMQInvalidTransientQueueUseException e) {
         exHappened = true;
      }

      assertTrue(exHappened);

      exHappened = false;

      try {
         // There's already a queue with that name, we are supposed to throw an exception
         session2.createSharedQueue(QueueConfiguration.of(queue).setAddress(address).setFilterString("a=1").setDurable(false));
      } catch (ActiveMQInvalidTransientQueueUseException e) {
         exHappened = true;
      }

      assertTrue(exHappened);

      // forcing a consumer close to make the queue go away
      session.createConsumer(queue).close();

      Wait.assertTrue(() -> server.locateQueue(queue) == null, 2000, 100);
      Wait.assertTrue(() -> server.getAddressInfo(queue) == null, 2000, 100);

      session.createSharedQueue(QueueConfiguration.of(queue).setAddress(address).setFilterString("q=1").setDurable(false));

      exHappened = false;

      try {
         // There's already a queue with that name, we are supposed to throw an exception
         session2.createSharedQueue(QueueConfiguration.of(queue).setAddress(address).setFilterString("q=2").setDurable(false));
      } catch (ActiveMQInvalidTransientQueueUseException e) {
         exHappened = true;
      }

      assertTrue(exHappened);

      exHappened = false;
      try {
         // There's already a queue with that name, we are supposed to throw an exception
         session2.createSharedQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));
      } catch (ActiveMQInvalidTransientQueueUseException e) {
         exHappened = true;
      }

      assertTrue(exHappened);
   }

   @Override
   protected ServerLocator createLocator() {
      return super.createLocator().setConsumerWindowSize(0).setBlockOnAcknowledge(true).setBlockOnDurableSend(false).setBlockOnNonDurableSend(false);
   }
}
