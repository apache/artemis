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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ReceiveTest extends ActiveMQTestBase {

   SimpleString addressA;

   SimpleString addressB;

   SimpleString queueA;

   SimpleString queueB;

   private ServerLocator locator;

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      addressA = RandomUtil.randomUUIDSimpleString();
      queueA = RandomUtil.randomUUIDSimpleString();
      addressB = RandomUtil.randomUUIDSimpleString();
      queueB = RandomUtil.randomUUIDSimpleString();

      locator = createInVMNonHALocator();
      server = createServer(false);
      server.start();
   }

   @Test
   public void testBasicReceive() throws Exception {
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      ClientProducer cp = sendSession.createProducer(addressA);
      ClientSession session = cf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientConsumer cc = session.createConsumer(queueA);
      session.start();
      cp.send(sendSession.createMessage(false));
      assertNotNull(cc.receive());
      session.close();
      sendSession.close();
   }

   @Test
   public void testReceiveTimesoutCorrectly() throws Exception {

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientConsumer cc = session.createConsumer(queueA);
      session.start();
      long time = System.currentTimeMillis();
      cc.receive(1000);
      assertTrue(System.currentTimeMillis() - time >= 1000);
      session.close();
   }

   @Test
   public void testReceiveOnClosedException() throws Exception {

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientConsumer cc = session.createConsumer(queueA);
      session.start();
      session.close();
      try {
         cc.receive();
         fail("should throw exception");
      } catch (ActiveMQObjectClosedException oce) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      session.close();
   }

   @Test
   public void testReceiveThrowsExceptionWhenHandlerSet() throws Exception {

      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = cf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientConsumer cc = session.createConsumer(queueA);
      session.start();
      cc.setMessageHandler(message -> {
      });
      try {
         cc.receive();
         fail("should throw exception");
      } catch (ActiveMQIllegalStateException ise) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
      session.close();
   }

   @Test
   public void testReceiveImmediate() throws Exception {
      // forces perfect round robin
      locator.setConsumerWindowSize(0);
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      ClientProducer cp = sendSession.createProducer(addressA);
      ClientSession session = cf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      ClientConsumer cc = session.createConsumer(queueA);
      ClientConsumer cc2 = session.createConsumer(queueA);
      session.start();
      cp.send(sendSession.createMessage(false));
      cp.send(sendSession.createMessage(false));
      cp.send(sendSession.createMessage(false));
      sendSession.commit();

      final Queue queue = server.locateQueue(queueA);

      Wait.waitFor(() -> queue.getMessageCount() == 3, 500, 100);

      assertNotNull(cc2.receiveImmediate());
      assertNotNull(cc.receiveImmediate());
      if (cc.receiveImmediate() == null) {
         assertNotNull(cc2.receiveImmediate());
      }
      session.close();
      sendSession.close();
   }

   @Test
   public void testMultiConsumersOnSession() throws Exception {
      ClientSessionFactory cf = createSessionFactory(locator.setCallTimeout(10000000));
      ClientSession sendSession = cf.createSession(false, true, true);
      ClientProducer cp1 = sendSession.createProducer(addressA);
      ClientProducer cp2 = sendSession.createProducer(addressB);

      ClientSession session = cf.createSession(false, true, false);
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      session.createQueue(QueueConfiguration.of(queueB).setAddress(addressB).setDurable(false));

      ClientConsumer cc1 = session.createConsumer(queueA);
      ClientConsumer cc2 = session.createConsumer(queueB);
      session.start();

      cp1.send(sendSession.createMessage(false));
      cp2.send(sendSession.createMessage(false));
      assertNotNull(cc1.receive().acknowledge());
      assertNotNull(cc2.receive().acknowledge());
      session.commit();

      final Queue queue1 = server.locateQueue(queueA);
      final Queue queue2 = server.locateQueue(queueB);

      Wait.assertTrue(() -> queue1.getMessageCount() == 0, 500, 100);
      Wait.assertTrue(() -> queue1.getMessagesAcknowledged() == 1, 500, 100);
      Wait.assertTrue(() -> queue2.getMessageCount() == 0, 500, 100);
      Wait.assertTrue(() -> queue2.getMessagesAcknowledged() == 1, 500, 100);

      session.close();
      sendSession.close();
   }
}
