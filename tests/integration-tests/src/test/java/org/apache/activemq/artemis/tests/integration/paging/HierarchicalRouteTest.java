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
package org.apache.activemq.artemis.tests.integration.paging;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.function.Predicate;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HierarchicalRouteTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected ActiveMQServer server;
   protected ClientSession session;
   protected ClientSessionFactory sf;
   protected ServerLocator locator;

   final String addressToSend = "a.b.c.d.e.f.g";
   final String[] queueToReceive = new String[]{"a.b.c.d.e.f.g", "a.b.c.d.e.f.*", "a.b.c.d.e.*.*", "a.b.c.d.*.*.*", "a.b.c.*.*.*.*", "a.b.*.*.*.*.*", "a.*.*.*.*.*.*"};
   final String addressSettingsMatch = "a.#";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, createDefaultNettyConfig());
      server.getConfiguration().getWildcardConfiguration().setAggregateSizes(true);
      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));
   }

   public void printSizes() throws Exception {
      SimpleString[] stores = server.getPagingManager().getStoreNames();

      for (SimpleString q : stores) {
         PagingStore store = server.getPagingManager().getPageStore(q);
         logger.debug("Store {} has {} elements and {}", store.getAddress(), store.getAddressElements(), store.getAddressSize());
      }
   }

   public void printHierarchy() throws Exception {
      for (String q : queueToReceive) {
         PagingStoreImpl store = (PagingStoreImpl)server.getPagingManager().lookupPageStore(SimpleString.of(q));
         if (store == null) {
            logger.debug("no address for {}", q);
         } else {
            logger.debug("hierarchy from {}/{}:\n {}", q, store, store.debugHierarchy());
         }
      }
   }

   @Test
   public void testMultipleSends() throws Exception {
      internalMultipleSends(false, true);
   }

   @Test
   public void testMultipleSendsNoRouteOnMainQueue() throws Exception {
      internalMultipleSends(true, true);
   }

   @Test
   public void testMultipleSendsNoRouteOnMainQueueNoQueues() throws Exception {
      internalMultipleSends(true, false);
   }

   @Test
   public void testValidateSizingOneMessage() throws Exception {

      // only queue to receive messages here will be the sending address
      createHierarchicalQueues(q -> !q.equals(addressToSend), true);

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createTopic(addressToSend));
         producer.send(session.createTextMessage("hello"));
         session.commit();
      }

      Queue queue = server.locateQueue(addressToSend);
      Wait.assertEquals(1L, queue::getMessageCount);

      long size = -1;
      for (String q : queueToReceive) {
         PagingStore store = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(1, store.getAddressElements());
         if (size < 0) {
            size = store.getAddressSize();
         } else {
            assertEquals(size, store.getAddressSize());
         }
      }

      server.stop();
      server.start();

      size = -1;
      for (String q : queueToReceive) {
         PagingStore store = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(1, store.getAddressElements());
         if (size < 0) {
            size = store.getAddressSize();
         } else {
            assertEquals(size, store.getAddressSize());
         }
      }

      if (logger.isDebugEnabled()) {
         printSizes();
      }

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = session.createConsumer(session.createQueue(addressToSend + "::" + addressToSend));
         assertNotNull(consumer.receive(5000));
         session.commit();
      }

      for (String q : queueToReceive) {
         PagingStore store = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(0L, store.getAddressElements());
         assertEquals(0L, store.getAddressSize());
      }

      validateRemoveHierarchy();

   }

   private void validateRemoveHierarchy() throws Exception {
      for (String q: queueToReceive) {
         if (q.equals(addressToSend)) {
            continue;
         }
         server.destroyQueue(SimpleString.of(q));
      }

      logger.debug("Checking hierarchy.............................");
      printHierarchy();

      for (String q: queueToReceive) {
         if (q.equals(addressToSend)) {
            continue;
         }
         try {
            server.removeAddressInfo(SimpleString.of(q), null);
         } catch (Exception dontCare) {
         }

         assertNull(server.getPagingManager().lookupPageStore(SimpleString.of(q)));

         for (String q2 : queueToReceive) {
            PagingStoreImpl store = (PagingStoreImpl) server.getPagingManager().getPageStore(SimpleString.of(q2));
            for (PagingStore h : store.getHierarchy()) {
               assertNotSame(store, h);
               assertNotEquals(store, h);

            }
         }
         logger.debug("Removed hierarchy on {}", q);
         printHierarchy();
      }

      for (String q : queueToReceive) {
         PagingStoreImpl store = (PagingStoreImpl) server.getPagingManager().getPageStore(SimpleString.of(q));
         assertTrue(store.getHierarchy().isEmpty());
      }

      logger.debug("Checking hierarchy.............................");
      printHierarchy();
   }

   public void internalMultipleSends(boolean skipSendingQueue, boolean useFilterOnSkip) throws Exception {

      int numberOfMessages = 10;
      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      createHierarchicalQueues(skipSendingQueue ? q -> q.equals(addressToSend) : null, useFilterOnSkip);

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createTopic(addressToSend));
         for (int i = 0; i < numberOfMessages; i++) {
            producer.send(session.createTextMessage("message " + i));
         }
         session.commit();
      }

      printSizes();

      PagingStore rootStore = server.getPagingManager().getPageStore(SimpleString.of("a.*.*.*.*.*.*"));
      Wait.assertEquals((long)numberOfMessages, rootStore::getAddressElements);

      for (String q : queueToReceive) {
         PagingStore leaveStore = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(numberOfMessages, leaveStore.getAddressElements());
      }

      server.stop();
      server.start();

      for (String q : queueToReceive) {
         PagingStore leaveStore = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(numberOfMessages, leaveStore.getAddressElements());
      }

      printHierarchy();

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         for (String q : queueToReceive) {
            if (skipSendingQueue && q.equals(addressToSend)) {
               continue;
            }
            MessageConsumer consumer = session.createConsumer(session.createQueue(q + "::" + q));
            for (int i = 0; i < numberOfMessages; i++) {

               TextMessage message = (TextMessage) consumer.receive(1000);
               assertNotNull(message);
               logger.debug("Queue {}, message = {}", q, message.getText());
            }

            logger.debug("> > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > After receiving {}", q);
            if (logger.isDebugEnabled()) {
               printSizes();
            }
         }
      }

      if (logger.isDebugEnabled()) {
         printSizes();
      }

      PagingStore store = server.getPagingManager().getPageStore(SimpleString.of(addressToSend));
      assertEquals(0L, store.getAddressElements());
      assertEquals(0, store.getAddressSize());

      for (String q : queueToReceive) {
         store = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(0L, store.getAddressElements());
         assertEquals(0, store.getAddressSize());
      }

      validateRemoveHierarchy();
   }

   private void createHierarchicalQueues(Predicate<String> skipFilter, boolean useFilterStringOnSkipped) throws ActiveMQException {
      server.getAddressSettingsRepository().addMatch(addressSettingsMatch, new AddressSettings().setMaxSizeMessages(100_000).setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL));
      session.createAddress(SimpleString.of(addressToSend), RoutingType.MULTICAST, false);
      for (String q : queueToReceive) {
         if (skipFilter != null && skipFilter.test(q)) {
            if (useFilterStringOnSkipped) {
               session.createQueue(QueueConfiguration.of(q).setRoutingType(RoutingType.MULTICAST).setAddress(q).setFilterString("impossibleField=true"));
            } else {
               // just skip it, don't create
            }
         } else {
            session.createQueue(QueueConfiguration.of(q).setRoutingType(RoutingType.MULTICAST).setAddress(q));
         }
      }
   }
}