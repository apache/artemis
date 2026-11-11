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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoutingTest extends ActiveMQTestBase {

   public final SimpleString addressA = SimpleString.of("addressA");
   public final SimpleString addressB = SimpleString.of("addressB");
   public final SimpleString queueA = SimpleString.of("queueA");
   public final SimpleString queueB = SimpleString.of("queueB");
   public final SimpleString queueC = SimpleString.of("queueC");
   public final SimpleString queueD = SimpleString.of("queueD");

   private ServerLocator locator;
   private ActiveMQServer server;
   private ClientSessionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
      server = createServer(false);

      server.start();
      cf = createSessionFactory(locator);
   }

   @Test
   public void testRouteToMultipleQueues() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      sendSession.createQueue(QueueConfiguration.of(queueB).setAddress(addressA).setDurable(false));
      sendSession.createQueue(QueueConfiguration.of(queueC).setAddress(addressA).setDurable(false));
      int numMessages = 300;
      ClientProducer p = sendSession.createProducer(addressA);
      for (int i = 0; i < numMessages; i++) {
         p.send(sendSession.createMessage(false));
      }
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumer c1 = session.createConsumer(queueA);
      ClientConsumer c2 = session.createConsumer(queueB);
      ClientConsumer c3 = session.createConsumer(queueC);
      session.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = c1.receive(5000);
         assertNotNull(m);
         m.acknowledge();
         c2.receive(5000);
         assertNotNull(m);
         m.acknowledge();
         c3.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }
      assertNull(c1.receiveImmediate());
      assertNull(c2.receiveImmediate());
      assertNull(c3.receiveImmediate());
      sendSession.close();
      session.close();
   }

   @Test
   public void testRouteToSingleNonDurableQueue() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      int numMessages = 300;
      ClientProducer p = sendSession.createProducer(addressA);
      for (int i = 0; i < numMessages; i++) {
         p.send(sendSession.createMessage(false));
      }
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumer c1 = session.createConsumer(queueA);
      session.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = c1.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }
      assertNull(c1.receiveImmediate());
      sendSession.close();
      session.close();
   }

   @Test
   public void testRouteToSingleDurableQueue() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA));
      int numMessages = 300;
      ClientProducer p = sendSession.createProducer(addressA);
      for (int i = 0; i < numMessages; i++) {
         p.send(sendSession.createMessage(false));
      }
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumer c1 = session.createConsumer(queueA);
      session.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = c1.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }
      assertNull(c1.receiveImmediate());
      sendSession.close();
      session.close();
   }

   @Test
   public void testRouteToSingleQueueWithFilter() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setFilterString("foo = 'bar'").setDurable(false));
      int numMessages = 300;
      ClientProducer p = sendSession.createProducer(addressA);
      for (int i = 0; i < numMessages; i++) {
         ClientMessage clientMessage = sendSession.createMessage(false);
         clientMessage.putStringProperty(SimpleString.of("foo"), SimpleString.of("bar"));
         p.send(clientMessage);
      }
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumer c1 = session.createConsumer(queueA);
      session.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = c1.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }
      assertNull(c1.receiveImmediate());
      sendSession.close();
      session.close();
   }

   @Test
   public void testRouteToMultipleQueueWithFilters() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setFilterString("foo = 'bar'").setDurable(false));
      sendSession.createQueue(QueueConfiguration.of(queueB).setAddress(addressA).setFilterString("x = 1").setDurable(false));
      sendSession.createQueue(QueueConfiguration.of(queueC).setAddress(addressA).setFilterString("b = false").setDurable(false));
      int numMessages = 300;
      ClientProducer p = sendSession.createProducer(addressA);
      for (int i = 0; i < numMessages; i++) {
         ClientMessage clientMessage = sendSession.createMessage(false);
         if (i % 3 == 0) {
            clientMessage.putStringProperty(SimpleString.of("foo"), SimpleString.of("bar"));
         } else if (i % 3 == 1) {
            clientMessage.putIntProperty(SimpleString.of("x"), 1);
         } else {
            clientMessage.putBooleanProperty(SimpleString.of("b"), false);
         }
         p.send(clientMessage);
      }
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumer c1 = session.createConsumer(queueA);
      ClientConsumer c2 = session.createConsumer(queueB);
      ClientConsumer c3 = session.createConsumer(queueC);
      session.start();
      for (int i = 0; i < numMessages / 3; i++) {
         ClientMessage m = c1.receive(5000);
         assertNotNull(m);
         m.acknowledge();
         m = c2.receive(5000);
         assertNotNull(m);
         m.acknowledge();
         m = c3.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }
      assertNull(c1.receiveImmediate());
      assertNull(c2.receiveImmediate());
      assertNull(c3.receiveImmediate());
      sendSession.close();
      session.close();
   }

   @Test
   public void testRouteToSingleTemporaryQueue() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false).setTemporary(true));
      int numMessages = 300;
      ClientProducer p = sendSession.createProducer(addressA);
      for (int i = 0; i < numMessages; i++) {
         p.send(sendSession.createMessage(false));
      }
      ClientSession session = cf.createSession(false, true, true);
      ClientConsumer c1 = session.createConsumer(queueA);
      session.start();
      for (int i = 0; i < numMessages; i++) {
         ClientMessage m = c1.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }
      assertNull(c1.receiveImmediate());
      sendSession.close();
      session.close();
   }

   @Test
   public void testAnycastMessageRoutingExclusivity() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      EnumSet<RoutingType> routingTypes = EnumSet.of(RoutingType.ANYCAST, RoutingType.MULTICAST);
      sendSession.createAddress(addressA, routingTypes, false);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));
      sendSession.createQueue(QueueConfiguration.of(queueB).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));
      sendSession.createQueue(QueueConfiguration.of(queueC).setAddress(addressA));
      ClientProducer p = sendSession.createProducer(addressA);
      ClientMessage message = sendSession.createMessage(false);
      message.setRoutingType(RoutingType.ANYCAST);
      p.send(message);
      sendSession.close();
      assertTrue(Wait.waitFor(() -> server.locateQueue(queueA).getMessageCount() + server.locateQueue(queueB).getMessageCount() == 1));
      assertTrue(Wait.waitFor(() -> server.locateQueue(queueC).getMessageCount() == 0));
   }

   @Test
   public void testMulticastMessageRoutingExclusivity() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      EnumSet<RoutingType> routingTypes = EnumSet.of(RoutingType.ANYCAST, RoutingType.MULTICAST);
      sendSession.createAddress(addressA, routingTypes, false);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));
      sendSession.createQueue(QueueConfiguration.of(queueB).setAddress(addressA));
      sendSession.createQueue(QueueConfiguration.of(queueC).setAddress(addressA));
      ClientProducer p = sendSession.createProducer(addressA);
      ClientMessage message = sendSession.createMessage(false);
      message.setRoutingType(RoutingType.MULTICAST);
      p.send(message);
      sendSession.close();
      assertTrue(Wait.waitFor(() -> server.locateQueue(queueA).getMessageCount() == 0));
      assertTrue(Wait.waitFor(() -> server.locateQueue(queueB).getMessageCount() + server.locateQueue(queueC).getMessageCount() == 2));
   }

   @Test
   public void testAmbiguousMessageRouting() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      EnumSet<RoutingType> routingTypes = EnumSet.of(RoutingType.ANYCAST, RoutingType.MULTICAST);
      sendSession.createAddress(addressA, routingTypes, false);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));
      sendSession.createQueue(QueueConfiguration.of(queueB).setAddress(addressA).setRoutingType(RoutingType.ANYCAST));
      sendSession.createQueue(QueueConfiguration.of(queueC).setAddress(addressA));
      sendSession.createQueue(QueueConfiguration.of(queueD).setAddress(addressA));
      ClientProducer p = sendSession.createProducer(addressA);
      ClientMessage message = sendSession.createMessage(false);
      p.send(message);
      sendSession.close();

      assertTrue(Wait.waitFor(() -> server.locateQueue(queueA).getMessageCount() + server.locateQueue(queueB).getMessageCount() == 1));
      assertTrue(Wait.waitFor(() -> server.locateQueue(queueC).getMessageCount() + server.locateQueue(queueD).getMessageCount() == 2));
   }
}
