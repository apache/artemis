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
package org.apache.activemq.artemis.tests.integration.paging;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestDeadlockOnPurgePagingTest extends ActiveMQTestBase {

   protected ServerLocator locator;
   protected ActiveMQServer server;
   protected ClientSessionFactory sf;

   protected static final int PAGE_MAX = 100 * 1024;

   protected static final int PAGE_SIZE = 10 * 1024;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
   }

   @Test
   public void testDeadlockOnPurge() throws Exception {

      int NUMBER_OF_MESSAGES = 5000;
      clearDataRecreateServerDirs();

      Configuration config = createDefaultNettyConfig().setJournalSyncNonTransactional(false);

      server = createServer(true, config, TestDeadlockOnPurgePagingTest.PAGE_SIZE, TestDeadlockOnPurgePagingTest.PAGE_MAX, -1, -1);

      server.start();

      String queue = "purgeQueue";
      SimpleString ssQueue = SimpleString.of(queue);
      server.addAddressInfo(new AddressInfo(ssQueue, RoutingType.ANYCAST));
      QueueImpl purgeQueue = (QueueImpl) server.createQueue(QueueConfiguration.of(ssQueue).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1).setPurgeOnNoConsumers(true).setAutoCreateAddress(false));

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory();
      Connection connection = cf.createConnection();

      connection.start();

      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      javax.jms.Queue jmsQueue = session.createQueue(queue);

      MessageProducer producer = session.createProducer(jmsQueue);

      for (int i = 0; i < 100; i++) {
         producer.send(session.createTextMessage("hello" + i));
      }
      session.commit();

      Wait.assertEquals(0, purgeQueue::getMessageCount);

      Wait.assertEquals(0, purgeQueue.getPageSubscription().getPagingStore()::getAddressSize);

      MessageConsumer consumer = session.createConsumer(jmsQueue);

      for (int i = 0; i < NUMBER_OF_MESSAGES / 5; i++) {
         producer.send(session.createTextMessage("hello" + i));
         if (i == 10) {
            purgeQueue.getPageSubscription().getPagingStore().startPaging();
         }

         if (i > 10 && i % 10 == 0) {
            purgeQueue.getPageSubscription().getPagingStore().forceAnotherPage(true);
         }
      }
      session.commit();


      for (int i = 0; i < 100; i++) {
         Message message = consumer.receive(5000);
         assertNotNull(message);
      }
      session.commit();

      consumer.close();

      Wait.assertEquals(0L, purgeQueue::getMessageCount, 5000L, 10L);

      Wait.assertFalse(purgeQueue.getPageSubscription()::isPaging, 5000L, 10L);

      Wait.assertEquals(0L, purgeQueue.getPageSubscription().getPagingStore()::getAddressSize, 5000L, 10L);
   }


}
