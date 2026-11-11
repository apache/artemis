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
package org.apache.activemq.artemis.tests.stress.paging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This is an integration-tests that will take some time to run.
 */
public class PageStressTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ServerLocator locator;

   @Test
   public void testStopDuringDepage() throws Exception {
      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false).setJournalSyncTransactional(false);

      Map<String, AddressSettings> settings = new HashMap<>();

      AddressSettings setting = new AddressSettings().setMaxSizeBytes(20 * 1024 * 1024);
      settings.put("page-adr", setting);

      server = addServer(createServer(true, config, 10 * 1024 * 1024, 20 * 1024 * 1024, settings));
      server.start();

      final int NUMBER_OF_MESSAGES = 60000;
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = addClientSession(factory.createSession(null, null, false, false, true, false, 1024 * NUMBER_OF_MESSAGES));

      SimpleString address = SimpleString.of("page-adr");

      session.createQueue(QueueConfiguration.of(address));

      ClientProducer prod = session.createProducer(address);

      ClientMessage message = createBytesMessage(session, ActiveMQBytesMessage.TYPE, new byte[700], true);

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         if (i % 10000 == 0) {
            System.out.println("Sent " + i);
         }
         prod.send(message);
      }

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(address);

      int msgs = 0;
      ClientMessage msg;
      do {
         msg = consumer.receive(10000);
         if (msg != null) {
            msg.acknowledge();
            if (++msgs % 1000 == 0) {
               System.out.println("Received " + msgs);
            }
         }
      }
      while (msg != null);

      session.commit();

      session.close();

      server.stop();

      System.out.println("server stopped, nr msgs: " + msgs);

      server = addServer(createServer(true, config, 10 * 1024 * 1024, 20 * 1024 * 1024, settings));

      server.start();

      factory = createSessionFactory(locator);

      session = addClientSession(factory.createSession(false, false, false));

      consumer = session.createConsumer(address);

      session.start();

      do {
         msg = consumer.receive(10000);
         if (msg != null) {
            msg.acknowledge();
            session.commit();
            if (++msgs % 1000 == 0) {
               System.out.println("Received " + msgs);
            }
         }
      }
      while (msg != null);

      System.out.println("msgs second time: " + msgs);

      assertEquals(NUMBER_OF_MESSAGES, msgs);
   }

   @Test
   public void testPageOnMultipleDestinations() throws Exception {
      Map<String, AddressSettings> settings = new HashMap<>();

      AddressSettings setting = new AddressSettings().setMaxSizeBytes(20 * 1024 * 1024);
      settings.put("page-adr", setting);

      server = addServer(createServer(true, createDefaultInVMConfig(), 10 * 1024 * 1024, 20 * 1024 * 1024, settings));
      server.start();

      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = null;

      session = factory.createSession(false, false, false);

      SimpleString address = SimpleString.of("page-adr");
      SimpleString[] queue = new SimpleString[]{SimpleString.of("queue1"), SimpleString.of("queue2")};

      session.createQueue(QueueConfiguration.of(queue[0]).setAddress(address));
      session.createQueue(QueueConfiguration.of(queue[1]).setAddress(address));

      ClientProducer prod = session.createProducer(address);

      ClientMessage message = createBytesMessage(session, ActiveMQBytesMessage.TYPE, new byte[700], false);

      int NUMBER_OF_MESSAGES = 60000;

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         if (i % 10000 == 0) {
            System.out.println(i);
         }
         prod.send(message);
      }

      session.commit();

      session.start();

      int[] counters = new int[2];

      ClientConsumer[] consumers = new ClientConsumer[]{session.createConsumer(queue[0]), session.createConsumer(queue[1])};

      while (true) {
         int msgs1 = readMessages(session, consumers[0], queue[0]);
         int msgs2 = readMessages(session, consumers[1], queue[1]);
         counters[0] += msgs1;
         counters[1] += msgs2;

         System.out.println("msgs1 = " + msgs1 + " msgs2 = " + msgs2);

         if (msgs1 + msgs2 == 0) {
            break;
         }
      }

      consumers[0].close();
      consumers[1].close();

      assertEquals(NUMBER_OF_MESSAGES, counters[0]);
      assertEquals(NUMBER_OF_MESSAGES, counters[1]);
   }

   private int readMessages(final ClientSession session,
                            final ClientConsumer consumer,
                            final SimpleString queue) throws ActiveMQException {
      session.start();
      int msgs = 0;

      ClientMessage msg = null;
      do {
         msg = consumer.receive(1000);
         if (msg != null) {
            msg.acknowledge();
            if (++msgs % 10000 == 0) {
               System.out.println("received " + msgs);
               session.commit();

            }
         }
      }
      while (msg != null);

      session.commit();

      return msgs;
   }


   @Override
   protected Configuration createDefaultInVMConfig() throws Exception {
      Configuration config = super.createDefaultInVMConfig().setJournalFileSize(10 * 1024 * 1024).setJournalMinFiles(5);

      return config;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator().setBlockOnAcknowledge(true).setBlockOnDurableSend(false).setBlockOnNonDurableSend(false);
   }
}
