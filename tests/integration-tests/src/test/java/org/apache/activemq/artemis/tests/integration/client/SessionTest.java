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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSession.QueueQuery;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CountDownSessionFailureListener;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * This test covers the API for ClientSession although XA tests are tested separately.
 */
@ExtendWith(ParameterizedTestExtension.class)
public class SessionTest extends ActiveMQTestBase {

   private boolean legacyCreateQueue;

   private final String queueName = "ClientSessionTestQ";

   private ServerLocator locator;
   private ActiveMQServer server;
   private ClientSessionFactory cf;

   @Parameters(name = "legacyCreateQueue={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   public SessionTest(boolean legacyCreateQueue) {
      this.legacyCreateQueue = legacyCreateQueue;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      locator = createInVMNonHALocator();
      server = createServer(false);
      server.start();
      waitForServerToStart(server);
   }

   @TestTemplate
   public void testFailureListener() throws Exception {

      cf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(cf.createSession(false, true, true));
      CountDownSessionFailureListener listener = new CountDownSessionFailureListener(clientSession);
      clientSession.addFailureListener(listener);
      // Make sure failure listener is called if server is stopped without session being closed first
      server.stop();
      assertTrue(listener.getLatch().await(5, TimeUnit.SECONDS));
   }

   @TestTemplate
   public void testFailureListenerRemoved() throws Exception {
      cf = createSessionFactory(locator);
      try {
         ClientSession clientSession = cf.createSession(false, true, true);
         class MyFailureListener implements SessionFailureListener {

            boolean called = false;

            @Override
            public void connectionFailed(final ActiveMQException me, boolean failedOver) {
               called = true;
            }

            @Override
            public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
               connectionFailed(me, failedOver);
            }

            @Override
            public void beforeReconnect(final ActiveMQException me) {
            }
         }

         MyFailureListener listener = new MyFailureListener();
         clientSession.addFailureListener(listener);

         assertTrue(clientSession.removeFailureListener(listener));
         clientSession.close();
         server.stop();
         assertFalse(listener.called);
      } finally {
         ((ClientSessionFactoryInternal) cf).causeExit();
         cf.close();
      }
   }

   // Closing a session if the underlying remoting connection is dead should cleanly
   // release all resources
   @TestTemplate
   public void testCloseSessionOnDestroyedConnection() throws Exception {
      // Make sure we have a short connection TTL so sessions will be quickly closed on the server
      server.stop();
      long ttl = 500;
      server.getConfiguration().setConnectionTTLOverride(ttl);
      server.start();
      cf = createSessionFactory(locator);
      ClientSessionInternal clientSession = (ClientSessionInternal) cf.createSession(false, true, true);
      if (legacyCreateQueue) {
         clientSession.createQueue(queueName, queueName, false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      }
      // keep unused variables in order to maintain references to both objects
      @SuppressWarnings("unused")
      ClientProducer producer = clientSession.createProducer();
      @SuppressWarnings("unused")
      ClientConsumer consumer = clientSession.createConsumer(queueName);

      assertEquals(1, server.getRemotingService().getConnections().size());

      RemotingConnection rc = clientSession.getConnection();

      rc.fail(new ActiveMQInternalErrorException());

      clientSession.close();

      long start = System.currentTimeMillis();

      while (true) {
         int cons = server.getRemotingService().getConnections().size();

         if (cons == 0) {
            break;
         }

         long now = System.currentTimeMillis();

         if (now - start > 10000) {
            throw new Exception("Timed out waiting for connections to close");
         }

         Thread.sleep(50);
      }
   }

   @TestTemplate
   public void testBindingQuery() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      if (legacyCreateQueue) {
         clientSession.createQueue("a1", "q1", false);
         clientSession.createQueue("a1", "q2", false);
         clientSession.createQueue("a2", "q3", false);
         clientSession.createQueue("a2", "q4", false);
         clientSession.createQueue("a2", "q5", false);
      } else {
         clientSession.createQueue(QueueConfiguration.of("q1").setAddress("a1").setDurable(false));
         clientSession.createQueue(QueueConfiguration.of("q2").setAddress("a1").setDurable(false));
         clientSession.createQueue(QueueConfiguration.of("q3").setAddress("a2").setDurable(false));
         clientSession.createQueue(QueueConfiguration.of("q4").setAddress("a2").setDurable(false));
         clientSession.createQueue(QueueConfiguration.of("q5").setAddress("a2").setDurable(false));
      }
      ClientSession.AddressQuery resp = clientSession.addressQuery(SimpleString.of("a"));
      List<SimpleString> queues = resp.getQueueNames();
      assertTrue(queues.isEmpty());
      resp = clientSession.addressQuery(SimpleString.of("a1"));
      queues = resp.getQueueNames();
      assertEquals(2, queues.size());
      assertTrue(queues.contains(SimpleString.of("q1")));
      assertTrue(queues.contains(SimpleString.of("q2")));
      resp = clientSession.addressQuery(SimpleString.of("a2"));
      queues = resp.getQueueNames();
      assertEquals(3, queues.size());
      assertTrue(queues.contains(SimpleString.of("q3")));
      assertTrue(queues.contains(SimpleString.of("q4")));
      assertTrue(queues.contains(SimpleString.of("q5")));
      clientSession.close();
   }

   @TestTemplate
   public void testQueueQuery() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      if (legacyCreateQueue) {
         clientSession.createQueue("a1", queueName, false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setAddress("a1").setDurable(false));
      }
      // I'm holding a reference here on purpose to avoid GC and unexpected closes.. etc
      ClientConsumer consumer1 = clientSession.createConsumer(queueName);
      ClientConsumer consumer2 = clientSession.createConsumer(queueName);
      ClientProducer cp = clientSession.createProducer("a1");
      cp.send(clientSession.createMessage(true));
      cp.send(clientSession.createMessage(true));

      Queue queue = flushQueue();
      Wait.assertEquals(2, queue::getMessageCount);
      Wait.assertEquals(2, queue::getConsumerCount);

      QueueQuery resp = clientSession.queueQuery(SimpleString.of(queueName));
      assertEquals(SimpleString.of("a1"), resp.getAddress());
      assertEquals(2, resp.getConsumerCount());
      assertEquals(2, resp.getMessageCount());
      assertNull(resp.getFilterString());
      consumer1.close();
      consumer2.close();
      clientSession.close();
   }

   private Queue flushQueue() throws Exception {
      Queue queue = server.locateQueue(SimpleString.of(queueName));
      assertNotNull(queue);
      queue.flushExecutor();
      return queue;
   }

   @TestTemplate
   public void testQueueQueryWithFilter() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      if (legacyCreateQueue) {
         clientSession.createQueue("a1", queueName, "foo=bar", false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setAddress("a1").setFilterString("foo=bar").setDurable(false));
      }
      clientSession.createConsumer(queueName);
      clientSession.createConsumer(queueName);

      QueueQuery resp = clientSession.queueQuery(SimpleString.of(queueName));
      assertEquals(SimpleString.of("a1"), resp.getAddress());
      assertEquals(2, resp.getConsumerCount());
      assertEquals(0, resp.getMessageCount());
      assertEquals(SimpleString.of("foo=bar"), resp.getFilterString());
      clientSession.close();
   }

   @TestTemplate
   public void testQueueQueryNoQ() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateQueues(false));
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      QueueQuery resp = clientSession.queueQuery(SimpleString.of(queueName));
      assertFalse(resp.isExists());
      assertFalse(resp.isAutoCreateQueues());
      assertEquals(queueName, resp.getAddress().toString());
      clientSession.close();
   }

   @TestTemplate
   public void testClose() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      if (legacyCreateQueue) {
         clientSession.createQueue(queueName, queueName, false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      }
      ClientProducer p = clientSession.createProducer();
      ClientProducer p1 = clientSession.createProducer(queueName);
      ClientConsumer c = clientSession.createConsumer(queueName);
      ClientConsumer c1 = clientSession.createConsumer(queueName);
      clientSession.close();
      assertTrue(clientSession.isClosed());
      assertTrue(p.isClosed());
      assertTrue(p1.isClosed());
      assertTrue(c.isClosed());
      assertTrue(c1.isClosed());
   }

   @TestTemplate
   public void testCreateMessageNonDurable() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      ClientMessage clientMessage = clientSession.createMessage(false);
      assertFalse(clientMessage.isDurable());
      clientSession.close();
   }

   @TestTemplate
   public void testCreateMessageDurable() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      ClientMessage clientMessage = clientSession.createMessage(true);
      assertTrue(clientMessage.isDurable());
      clientSession.close();
   }

   @TestTemplate
   public void testCreateMessageType() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      ClientMessage clientMessage = clientSession.createMessage((byte) 99, false);
      assertEquals((byte) 99, clientMessage.getType());
      clientSession.close();
   }

   @TestTemplate
   public void testCreateMessageOverrides() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      ClientMessage clientMessage = clientSession.createMessage((byte) 88, false, 100L, 300L, (byte) 33);
      assertEquals((byte) 88, clientMessage.getType());
      assertEquals(100L, clientMessage.getExpiration());
      assertEquals(300L, clientMessage.getTimestamp());
      assertEquals((byte) 33, clientMessage.getPriority());
      clientSession.close();
   }

   @TestTemplate
   public void testGetVersion() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      assertEquals(server.getVersion().getIncrementingVersion(), clientSession.getVersion());
      clientSession.close();
   }

   @TestTemplate
   public void testStart() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      if (legacyCreateQueue) {
         clientSession.createQueue(queueName, queueName, false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      }
      clientSession.start();
      clientSession.close();
   }

   @TestTemplate
   public void testStop() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, true, true);
      if (legacyCreateQueue) {
         clientSession.createQueue(queueName, queueName, false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      }
      clientSession.start();
      clientSession.stop();
      clientSession.close();
   }

   @TestTemplate
   public void testCommitWithSend() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, false, true);
      if (legacyCreateQueue) {
         clientSession.createQueue(queueName, queueName, false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      }
      ClientProducer cp = clientSession.createProducer(queueName);
      for (int i = 0; i < 10; i++) {
         cp.send(clientSession.createMessage(false));
      }
      Queue q = (Queue) server.getPostOffice().getBinding(SimpleString.of(queueName)).getBindable();
      Wait.assertEquals(0, () -> getMessageCount(q));
      clientSession.commit();
      assertTrue(Wait.waitFor(() -> getMessageCount(q) == 10, 2000, 100));
      clientSession.close();
   }

   @TestTemplate
   public void testRollbackWithSend() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = cf.createSession(false, false, true);
      if (legacyCreateQueue) {
         clientSession.createQueue(queueName, queueName, false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      }
      ClientProducer cp = clientSession.createProducer(queueName);
      for (int i = 0; i < 10; i++) {
         cp.send(clientSession.createMessage(false));
      }
      Queue q = (Queue) server.getPostOffice().getBinding(SimpleString.of(queueName)).getBindable();
      Wait.assertEquals(0, () -> getMessageCount(q));
      clientSession.rollback();
      cp.send(clientSession.createMessage(false));
      cp.send(clientSession.createMessage(false));
      clientSession.commit();
      Wait.assertEquals(2, () -> getMessageCount(q));
      clientSession.close();
   }

   @TestTemplate
   public void testCommitWithReceive() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      ClientProducer cp = sendSession.createProducer(queueName);
      ClientSession clientSession = cf.createSession(false, true, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(queueName, queueName, false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      }
      for (int i = 0; i < 10; i++) {
         cp.send(clientSession.createMessage(false));
      }
      Queue q = (Queue) server.getPostOffice().getBinding(SimpleString.of(queueName)).getBindable();
      Wait.assertEquals(10, () -> getMessageCount(q));
      ClientConsumer cc = clientSession.createConsumer(queueName);
      clientSession.start();

      for (int i = 0; i < 10; i++) {
         ClientMessage m = cc.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }
      clientSession.commit();
      assertNull(cc.receiveImmediate());
      Wait.assertEquals(0, () -> getMessageCount(q));
      clientSession.close();
      sendSession.close();
   }

   @TestTemplate
   public void testRollbackWithReceive() throws Exception {
      locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true);
      cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      ClientProducer cp = sendSession.createProducer(queueName);
      ClientSession clientSession = cf.createSession(false, true, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(queueName, queueName, false);
      } else {
         clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      }
      for (int i = 0; i < 10; i++) {
         cp.send(clientSession.createMessage(false));
      }
      Queue q = (Queue) server.getPostOffice().getBinding(SimpleString.of(queueName)).getBindable();
      Wait.assertEquals(10, () -> getMessageCount(q));
      ClientConsumer cc = clientSession.createConsumer(queueName);
      clientSession.start();
      for (int i = 0; i < 10; i++) {
         ClientMessage m = cc.receive(5000);
         assertNotNull(m);
         m.acknowledge();
      }
      Wait.assertEquals(10, () -> getMessageCount(q));
      clientSession.close();
      sendSession.close();
   }

   @TestTemplate
   public void testGetNodeId() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(cf.createSession(false, true, true));
      String nodeId = ((ClientSessionInternal) clientSession).getNodeId();
      assertNotNull(nodeId);
   }

   @TestTemplate
   public void testCreateQueue() throws Exception {
      cf = createSessionFactory(locator);
      ClientSession clientSession = addClientSession(cf.createSession(false, true, true));
      SimpleString queueName = SimpleString.of(UUID.randomUUID().toString());
      SimpleString addressName = SimpleString.of(UUID.randomUUID().toString());
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName, RoutingType.ANYCAST, queueName);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName.toString(), RoutingType.ANYCAST, queueName.toString());
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName, RoutingType.ANYCAST, queueName, true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertTrue(result.isDurable());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName.toString(), RoutingType.ANYCAST, queueName.toString(), true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertTrue(result.isDurable());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName, RoutingType.ANYCAST, queueName, SimpleString.of("filter"), true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString("filter"));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertEquals("filter", result.getFilter().getFilterString().toString());
         assertTrue(result.isDurable());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName.toString(), RoutingType.ANYCAST, queueName.toString(), "filter", true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString("filter"));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertEquals("filter", result.getFilter().getFilterString().toString());
         assertTrue(result.isDurable());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName, RoutingType.ANYCAST, queueName, SimpleString.of("filter"), true, true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString("filter").setAutoCreated(true));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertEquals("filter", result.getFilter().getFilterString().toString());
         assertTrue(result.isDurable());
         assertTrue(result.isAutoCreated());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName.toString(), RoutingType.ANYCAST, queueName.toString(), "filter", true, true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString("filter").setAutoCreated(true));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertEquals("filter", result.getFilter().getFilterString().toString());
         assertTrue(result.isDurable());
         assertTrue(result.isAutoCreated());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName, RoutingType.ANYCAST, queueName, SimpleString.of("filter"), true, true, 0, true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString("filter").setAutoCreated(true).setMaxConsumers(0).setPurgeOnNoConsumers(true));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertEquals("filter", result.getFilter().getFilterString().toString());
         assertTrue(result.isDurable());
         assertTrue(result.isAutoCreated());
         assertEquals(0, result.getMaxConsumers());
         assertTrue(result.isPurgeOnNoConsumers());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName.toString(), RoutingType.ANYCAST, queueName.toString(), "filter", true, true, 0, true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString("filter").setAutoCreated(true).setMaxConsumers(0).setPurgeOnNoConsumers(true));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertEquals("filter", result.getFilter().getFilterString().toString());
         assertTrue(result.isDurable());
         assertTrue(result.isAutoCreated());
         assertEquals(0, result.getMaxConsumers());
         assertTrue(result.isPurgeOnNoConsumers());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName, RoutingType.ANYCAST, queueName, SimpleString.of("filter"), true, true, 0, true, true, true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString("filter").setAutoCreated(true).setMaxConsumers(0).setPurgeOnNoConsumers(true).setExclusive(true).setLastValue(true));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertEquals("filter", result.getFilter().getFilterString().toString());
         assertTrue(result.isDurable());
         assertTrue(result.isAutoCreated());
         assertEquals(0, result.getMaxConsumers());
         assertTrue(result.isPurgeOnNoConsumers());
         assertTrue(result.isExclusive());
         assertTrue(result.isLastValue());
         server.destroyQueue(queueName);
      }
      {
         if (legacyCreateQueue) {
            clientSession.createQueue(addressName.toString(), RoutingType.ANYCAST, queueName.toString(), "filter", true, true, 0, true, true, true);
         } else {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString("filter").setAutoCreated(true).setMaxConsumers(0).setPurgeOnNoConsumers(true).setExclusive(true).setLastValue(true));
         }
         Queue result = server.locateQueue(queueName);
         assertEquals(addressName, result.getAddress());
         assertEquals(queueName, result.getName());
         assertEquals(RoutingType.ANYCAST, result.getRoutingType());
         assertEquals("filter", result.getFilter().getFilterString().toString());
         assertTrue(result.isDurable());
         assertTrue(result.isAutoCreated());
         assertEquals(0, result.getMaxConsumers());
         assertTrue(result.isPurgeOnNoConsumers());
         assertTrue(result.isExclusive());
         assertTrue(result.isLastValue());
         assertTrue(result.isEnabled());
         server.destroyQueue(queueName);
      }
      {
         if (!legacyCreateQueue) {
            clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setFilterString("filter").setAutoCreated(true).setMaxConsumers(0).setPurgeOnNoConsumers(true).setExclusive(true).setLastValue(true).setEnabled(false));
            Queue result = server.locateQueue(queueName);
            assertEquals(addressName, result.getAddress());
            assertEquals(queueName, result.getName());
            assertEquals(RoutingType.ANYCAST, result.getRoutingType());
            assertEquals("filter", result.getFilter().getFilterString().toString());
            assertTrue(result.isDurable());
            assertTrue(result.isAutoCreated());
            assertEquals(0, result.getMaxConsumers());
            assertTrue(result.isPurgeOnNoConsumers());
            assertTrue(result.isExclusive());
            assertTrue(result.isLastValue());
            assertFalse(result.isEnabled());
            server.destroyQueue(queueName);
         }

      }
   }
}
