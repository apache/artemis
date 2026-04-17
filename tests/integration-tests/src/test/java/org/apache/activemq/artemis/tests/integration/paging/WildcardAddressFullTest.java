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

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class WildcardAddressFullTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected ActiveMQServer server;
   protected ClientSession session;
   protected ClientSessionFactory sf;
   protected ServerLocator locator;

   final String addressToSend = "a.b.c.d.e.f.g";
   final String[] queueToReceive = new String[]{"a.b.c.d.e.f.*", "a.b.c.d.e.*.*", "a.b.c.d.*.*.*", "a.b.c.*.*.*.*", "a.b.*.*.*.*.*", "a.*.*.*.*.*.*"};
   final String addressSettingsMatch = "a.#";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, createDefaultNettyConfig());
      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));
   }

   @Test
   public void testFail() throws Exception {

      final int MAX_MESSAGES = 1;
      server.getAddressSettingsRepository().addMatch(addressSettingsMatch, new AddressSettings().setMaxSizeMessages(MAX_MESSAGES).setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL));

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      session.createAddress(SimpleString.of(addressToSend), RoutingType.MULTICAST, false);
      for (String q : queueToReceive) {
         session.createQueue(QueueConfiguration.of(q).setRoutingType(RoutingType.MULTICAST));
      }

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createTopic(addressToSend));
         for (int i = 0; i < MAX_MESSAGES; i++) {
            producer.send(session.createTextMessage("will send"));
         }
         try {
            producer.send(session.createTextMessage("should fail"));
            fail("should fail");
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         for (String q : queueToReceive) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(q + "::" + q));
            for (int i = 0; i < MAX_MESSAGES; i++) {
               assertNotNull(consumer.receive(5000));
            }
         }
      }

      PagingStore store = server.getPagingManager().getPageStore(SimpleString.of(addressToSend));
      assertEquals(0L, store.getAddressElements());
      assertEquals(0, store.getAddressSize());

      for (String q : queueToReceive) {
         store = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(0L, store.getAddressElements());
         assertEquals(0, store.getAddressSize());
      }
   }

   @Test
   public void testPaging() throws Exception {
      int producerSend = 10;
      final int MAX_MESSAGES = 1;
      server.getAddressSettingsRepository().addMatch(addressSettingsMatch, new AddressSettings().setMaxSizeMessages(MAX_MESSAGES).setMaxSizeBytes(1).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE));

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      session.createAddress(SimpleString.of(addressToSend), RoutingType.MULTICAST, false);
      for (String q : queueToReceive) {
         session.createQueue(QueueConfiguration.of(q).setRoutingType(RoutingType.MULTICAST));
      }

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createTopic(addressToSend));
         for (int i = 0; i < producerSend; i++) {
            producer.send(session.createTextMessage("will send"));
         }
      }

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         for (String q : queueToReceive) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(q + "::" + q));
            for (int i = 0; i < producerSend; i++) {
               assertNotNull(consumer.receive(5000));
            }
            assertNull(consumer.receiveNoWait());
         }
      }

      PagingStore store = server.getPagingManager().getPageStore(SimpleString.of(addressToSend));
      assertEquals(0L, store.getAddressElements());
      assertEquals(0, store.getAddressSize());

      for (String q : queueToReceive) {
         store = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(0L, store.getAddressElements());
         assertEquals(0, store.getAddressSize());
      }
   }

   @Test
   public void testDrop() throws Exception {
      int producerSend = 10;
      final int MAX_MESSAGES = 1;
      server.getAddressSettingsRepository().addMatch(addressSettingsMatch, new AddressSettings().setMaxSizeMessages(MAX_MESSAGES).setAddressFullMessagePolicy(AddressFullMessagePolicy.DROP));

      ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

      session.createAddress(SimpleString.of(addressToSend), RoutingType.MULTICAST, false);
      for (String q : queueToReceive) {
         session.createQueue(QueueConfiguration.of(q).setRoutingType(RoutingType.MULTICAST));
      }

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createTopic(addressToSend));
         for (int i = 0; i < producerSend; i++) {
            producer.send(session.createTextMessage("will send"));
         }
      }

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         for (String q : queueToReceive) {
            MessageConsumer consumer = session.createConsumer(session.createQueue(q + "::" + q));
            for (int i = 0; i < MAX_MESSAGES; i++) {
               assertNotNull(consumer.receive(5000));
            }
            assertNull(consumer.receiveNoWait());
         }
      }

      PagingStore store = server.getPagingManager().getPageStore(SimpleString.of(addressToSend));
      assertEquals(0L, store.getAddressElements());
      assertEquals(0, store.getAddressSize());

      for (String q : queueToReceive) {
         store = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(0L, store.getAddressElements());
         assertEquals(0, store.getAddressSize());
      }
   }

   @Test
   public void testBlock() throws Exception {
      String addressToSend = "a.b.c.d.e.f.g";
      String[]  queueToReceive = new String[]{"a.b.c.d.e.f.*", "a.b.c.d.e.*.*", "a.b.c.d.*.*.*", "a.b.c.*.*.*.*", "a.b.*.*.*.*.*", "a.*.*.*.*.*.*"};
      String addressSettingsMatch = "a.#";

      ExecutorService executorService = Executors.newFixedThreadPool(1 + queueToReceive.length);
      runAfter(executorService::shutdownNow);
      final int NUMBER_OF_MESSAGES = 100;
      final int MAX_MESSAGES = 1;
      server.getAddressSettingsRepository().addMatch(addressSettingsMatch, new AddressSettings().setMaxSizeMessages(MAX_MESSAGES).setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK));

      CountDownLatch doneSending = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger(0);

      executorService.execute(() -> {

         try {
            ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");

            session.createAddress(SimpleString.of(addressToSend), RoutingType.MULTICAST, false);
            for (String q : queueToReceive) {
               session.createQueue(QueueConfiguration.of(q).setRoutingType(RoutingType.MULTICAST));
            }

            try (Connection connection = factory.createConnection()) {
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               MessageProducer producer = session.createProducer(session.createTopic(addressToSend));
               for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
                  logger.info("Sending message {}", i);
                  producer.send(session.createTextMessage("a".repeat(10000)));
               }
            }
         } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            errors.incrementAndGet();
         } finally {
            doneSending.countDown();
         }
      });

      assertFalse(doneSending.await(500, TimeUnit.MILLISECONDS));

      CountDownLatch doneConsume = new CountDownLatch(queueToReceive.length);

      for (String q : queueToReceive) {
         executorService.execute(() -> {
            ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
            try (Connection connection = factory.createConnection()) {
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               MessageConsumer consumer = session.createConsumer(session.createQueue(q + "::" + q));
               connection.start();
               for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
                  assertNotNull(consumer.receive(5000));
                  logger.info("Consumed {} on queue {}", i, q);
               }
               assertNull(consumer.receiveNoWait());
            } catch (Exception e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               doneConsume.countDown();
            }
         });
      }

      assertTrue(doneSending.await(5, TimeUnit.SECONDS));
      assertTrue(doneConsume.await(5, TimeUnit.SECONDS));
      assertEquals(0, errors.get());

      PagingStore store = server.getPagingManager().getPageStore(SimpleString.of(addressToSend));
      assertEquals(0L, store.getAddressElements());
      assertEquals(0, store.getAddressSize());

      for (String q : queueToReceive) {
         store = server.getPagingManager().getPageStore(SimpleString.of(q));
         assertEquals(0L, store.getAddressElements());
         assertEquals(0, store.getAddressSize());
      }
   }

}
