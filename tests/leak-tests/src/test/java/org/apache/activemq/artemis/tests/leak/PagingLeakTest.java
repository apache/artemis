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

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.paging.cursor.impl.PagePositionImpl;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageSubscriptionImpl;
import org.apache.activemq.artemis.core.paging.impl.PageTransactionInfoImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.assertMemory;
import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.basicMemoryAsserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/*
 * At the time this test was written JournalFileImpl was leaking through JournalFileImpl::negative creating a linked
 * list (or leaked-list, pun intended)
 */
public class PagingLeakTest extends AbstractLeakTest {

   ActiveMQServer server;

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @AfterEach
   public void validateServer() throws Exception {
      CheckLeak checkLeak = new CheckLeak();

      // I am doing this check here because the test method might hold a client connection
      // so this check has to be done after the test, and before the server is stopped
      assertMemory(checkLeak, 0, RemotingConnectionImpl.class.getName());

      server.stop();

      server = null;

      clearServers();
      ServerStatus.clear();

      assertMemory(checkLeak, 0, ActiveMQServerImpl.class.getName());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      server = createServer(true, createDefaultConfig(1, true));
      server.getConfiguration().setJournalPoolFiles(4).setJournalMinFiles(2);
      server.start();
   }

   @Test
   public void testAMQP() throws Exception {
      doTest("AMQP");
   }

   @Test
   public void testCore() throws Exception {
      doTest("CORE");
   }

   @Test
   public void testOpenWire() throws Exception {
      doTest("OPENWIRE");
   }

   private void doTest(String protocol) throws Exception {
      int MESSAGES = 100;
      int MESSAGE_SIZE = 104;
      int COMMIT_INTERVAL = 10;
      basicMemoryAsserts();

      ExecutorService executorService = Executors.newFixedThreadPool(2);
      runAfter(executorService::shutdownNow);

      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      server.addAddressInfo(new AddressInfo(getName()).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(getName()).setAddress(getName()).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      Queue serverQueue = server.locateQueue(getName());
      serverQueue.getPagingStore().startPaging();

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(getName()));
         connection.start();

         for (int i = 0; i < MESSAGES; i++) {
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(new byte[MESSAGE_SIZE]);
            message.setIntProperty("i", i);
            producer.send(message);
            if (i > 0 && i % COMMIT_INTERVAL == 0) {
               session.commit();
               serverQueue.getPagingStore().forceAnotherPage(true);
            }
         }
         session.commit();
      }

      Wait.assertEquals(MESSAGES, serverQueue::getMessageCount, 5000);

      CheckLeak checkLeak = new CheckLeak();

      // no acks done, no PagePosition recorded
      assertEquals(0, checkLeak.getAllObjects(PagePositionImpl.class).length);

      serverQueue.getPagingStore().disableCleanup();

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = session.createConsumer(session.createQueue(getName()));
         connection.start();

         for (int i = 0; i < MESSAGES; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
            if (i > 0 && i % COMMIT_INTERVAL == 0) {
               session.commit();
            }
         }
         session.commit();
      }

      // We forced paged, there should be one PageCursorInfo per page since they are all acked
      Wait.assertEquals(MESSAGES / COMMIT_INTERVAL, () -> checkLeak.getAllObjects(PageSubscriptionImpl.PageCursorInfo.class).length, 5_0000, 500);

      serverQueue.getPagingStore().enableCleanup();

      // There should be only one holding the next page in place
      Wait.assertEquals(1, () -> checkLeak.getAllObjects(PagePositionImpl.class).length, 5_000, 500);

      // Everything is acked, we should not have any pageTransactions
      Wait.assertEquals(0, () -> checkLeak.getAllObjects(PageTransactionInfoImpl.class).length, 5_0000, 500);

      serverQueue.getPagingStore().startPaging();

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(getName()));
         connection.start();

         for (int i = 0; i < MESSAGES; i++) {
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(new byte[MESSAGE_SIZE]);
            message.setIntProperty("i", i);
            producer.send(message);
            if (i > 0 && i % COMMIT_INTERVAL == 0) {
               session.commit();
               serverQueue.getPagingStore().forceAnotherPage(true);
            }
         }
         session.commit();
      }

      // there should be MESSAGES / COMMIT_INTERVAL in the page TX
      Wait.assertEquals(MESSAGES / COMMIT_INTERVAL, () -> checkLeak.getAllObjects(PageTransactionInfoImpl.class).length, 5_0000, 500);

      serverQueue.deleteQueue();

      // setting it to null to make sure checkLeak will not count anything else
      serverQueue = null;

      // Nothing else being held after queue is removed
      Wait.assertEquals(0, () -> checkLeak.getAllObjects(PageSubscriptionImpl.PageCursorInfo.class).length);
      Wait.assertEquals(0, () -> checkLeak.getAllObjects(PagePositionImpl.class).length, 5_000, 500);
      Wait.assertEquals(0, () -> checkLeak.getAllObjects(PageTransactionInfoImpl.class).length, 5_0000, 500);
   }
}