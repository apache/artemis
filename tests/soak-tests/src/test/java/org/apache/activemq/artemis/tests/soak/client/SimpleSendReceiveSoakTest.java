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
package org.apache.activemq.artemis.tests.soak.client;

import java.util.HashMap;

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
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SimpleSendReceiveSoakTest extends ActiveMQTestBase {


   private static final SimpleString ADDRESS = SimpleString.of("ADD");

   private static final boolean IS_JOURNAL = false;

   public static final int MIN_MESSAGES_ON_QUEUE = 1000;

   protected boolean isNetty() {
      return false;
   }



   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      clearDataRecreateServerDirs();

      Configuration config = createDefaultConfig(isNetty()).setJournalFileSize(10 * 1024 * 1024);

      server = createServer(IS_JOURNAL, config, -1, -1, new HashMap<>());

      server.start();

      ServerLocator locator = createFactory(isNetty());

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession();

      session.createQueue(QueueConfiguration.of(SimpleSendReceiveSoakTest.ADDRESS));

      session.close();
   }

   @Test
   public void testSoakClientTransactions() throws Exception {
      final ServerLocator locator = createFactory(isNetty());

      final ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, true);

      ClientProducer producer = session.createProducer(ADDRESS);

      long msgId = 0;

      long msgReceivedID = 0;

      for (int i = 0; i < MIN_MESSAGES_ON_QUEUE; i++) {
         ClientMessage msg = session.createMessage(IS_JOURNAL);
         msg.putLongProperty("count", msgId++);
         msg.getBodyBuffer().writeBytes(new byte[10 * 1024]);
         producer.send(msg);
      }

      ClientSession sessionConsumer = sf.createSession(true, true, 0);
      ClientConsumer consumer = sessionConsumer.createConsumer(ADDRESS);
      sessionConsumer.start();

      for (int loopNumber = 0; loopNumber < 1000; loopNumber++) {
         System.out.println("Loop " + loopNumber);
         for (int i = 0; i < MIN_MESSAGES_ON_QUEUE; i++) {
            ClientMessage msg = session.createMessage(IS_JOURNAL);
            msg.putLongProperty("count", msgId++);
            msg.getBodyBuffer().writeBytes(new byte[10 * 1024]);
            producer.send(msg);
         }

         for (int i = 0; i < MIN_MESSAGES_ON_QUEUE; i++) {
            ClientMessage msg = consumer.receive(5000);
            assertNotNull(msg);
            msg.acknowledge();
            assertEquals(msgReceivedID++, msg.getLongProperty("count").longValue());
         }
      }

      sessionConsumer.close();
      session.close();
   }
}
