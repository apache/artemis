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
package org.apache.activemq.artemis.tests.leak;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.actors.OrderedExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoreClientLeakTest extends AbstractLeakTest {

   ActiveMQServer server;

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @AfterAll
   public static void afterClass() throws Exception {
      ServerStatus.clear();
      MemoryAssertions.assertMemory(new CheckLeak(), 0, ActiveMQServerImpl.class.getName());
      MemoryAssertions.assertMemory(new CheckLeak(), 0, JournalImpl.class.getName());
      MemoryAssertions.assertMemory(new CheckLeak(), 0, OrderedExecutor.class.getName());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, createDefaultConfig(1, true));
      server.getConfiguration().setJournalPoolFiles(4).setJournalMinFiles(2);
      server.start();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();
      server.stop();
      server = null;
   }

   @Test
   public void testConsumerFiltered() throws Exception {

      ServerLocator locator = ActiveMQClient.createServerLocator("tcp://localhost:61616");
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString emptyString = SimpleString.of("");
      SimpleString dummyFilter = SimpleString.of("dummy=true");


      try (ClientSessionFactory sf = createSessionFactory(locator);
           ClientSession clientSession = sf.createSession()) {
         try {
            clientSession.start();
            clientSession.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(true));
            CheckLeak checkLeak = new CheckLeak();
            int initialSimpleString = 0;

            for (int i = 0; i < 500; i++) {
               ClientConsumer consumer;
               if (i % 2 == 0) {
                  consumer = clientSession.createConsumer(queue, emptyString);
               } else {
                  consumer = clientSession.createConsumer(queue, dummyFilter);
               }
               consumer.close();
               consumer = null; // setting it to null to release the consumer earlier before the checkLeak call bellow
               if (i == 100) {
                  // getting a stable number of strings after 100 consumers created
                  initialSimpleString = checkLeak.getAllObjects(SimpleString.class).length;
               }
            }

            int lastNumberOfSimpleStrings = checkLeak.getAllObjects(SimpleString.class).length;

            // I am allowing extra 50 strings created elsewhere. it should not happen at the time I created this test but I am allowing this just in case
            if (lastNumberOfSimpleStrings > initialSimpleString + 50) {
               fail("There are " + lastNumberOfSimpleStrings + " while there was " + initialSimpleString + " SimpleString objects initially");
            }

         } finally {
            clientSession.deleteQueue(queue);
         }
      }

      locator.close();
   }


}