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

import javax.transaction.xa.Xid;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscriptionCounter;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageSubscriptionCounterImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PagingCounterTest extends ActiveMQTestBase {



   private ActiveMQServer server;

   private ServerLocator sl;



   @Before
   public void checkLoggerStart() throws Exception {
      AssertionLoggerHandler.startCapture();
   }

   @After
   public void checkLoggerEnd() throws Exception {
      try {
         // These are the message errors for the negative size address size
         Assert.assertFalse(AssertionLoggerHandler.findText("222214"));
         Assert.assertFalse(AssertionLoggerHandler.findText("222215"));
      } finally {
         AssertionLoggerHandler.stopCapture();
      }
   }


   @Test
   public void testCounter() throws Exception {
      ClientSessionFactory sf = createSessionFactory(sl);
      ClientSession session = sf.createSession();

      try {
         server.addAddressInfo(new AddressInfo(new SimpleString("A1"), RoutingType.ANYCAST));
         Queue queue = server.createQueue(new QueueConfiguration(new SimpleString("A1")).setRoutingType(RoutingType.ANYCAST));

         PageSubscriptionCounter counter = locateCounter(queue);

         StorageManager storage = server.getStorageManager();

         Transaction tx = new TransactionImpl(server.getStorageManager());

         counter.increment(tx, 1, 1000);

         assertEquals(0, counter.getValue());
         assertEquals(0, counter.getPersistentSize());

         tx.commit();

         storage.waitOnOperations();

         assertEquals(1, counter.getValue());
         assertEquals(1000, counter.getPersistentSize());
      } finally {
         sf.close();
         session.close();
      }
   }

   @Test
   public void testCleanupCounter() throws Exception {
      ClientSessionFactory sf = createSessionFactory(sl);
      ClientSession session = sf.createSession();

      try {
         server.addAddressInfo(new AddressInfo(new SimpleString("A1"), RoutingType.ANYCAST));
         Queue queue = server.createQueue(new QueueConfiguration(new SimpleString("A1")).setRoutingType(RoutingType.ANYCAST));

         PageSubscriptionCounter counter = locateCounter(queue);

         StorageManager storage = server.getStorageManager();

         Transaction tx = new TransactionImpl(server.getStorageManager());

         for (int i = 0; i < 2100; i++) {

            counter.increment(tx, 1, 1000);

            if (i % 200 == 0) {
               tx.commit();

               storage.waitOnOperations();

               assertEquals(i + 1, counter.getValue());
               assertEquals((i + 1) * 1000, counter.getPersistentSize());

               tx = new TransactionImpl(server.getStorageManager());
            }
         }

         tx.commit();

         storage.waitOnOperations();

         assertEquals(2100, counter.getValue());
         assertEquals(2100 * 1000, counter.getPersistentSize());

         server.stop();

         server = newActiveMQServer();

         server.start();

         queue = server.locateQueue(new SimpleString("A1"));

         assertNotNull(queue);

         counter = locateCounter(queue);

         assertEquals(2100, counter.getValue());
         assertEquals(2100 * 1000, counter.getPersistentSize());

      } finally {
         sf.close();
         session.close();
      }
   }

   @Test
   public void testCleanupCounterNonPersistent() throws Exception {
      ClientSessionFactory sf = createSessionFactory(sl);
      ClientSession session = sf.createSession();

      try {

         server.addAddressInfo(new AddressInfo(new SimpleString("A1"), RoutingType.ANYCAST));
         Queue queue = server.createQueue(new QueueConfiguration(new SimpleString("A1")).setRoutingType(RoutingType.ANYCAST));

         PageSubscriptionCounter counter = locateCounter(queue);

         ((PageSubscriptionCounterImpl) counter).setPersistent(false);

         StorageManager storage = server.getStorageManager();

         Transaction tx = new TransactionImpl(server.getStorageManager());

         for (int i = 0; i < 2100; i++) {

            counter.increment(tx, 1, 1000);

            if (i % 200 == 0) {
               tx.commit();

               storage.waitOnOperations();

               assertEquals(i + 1, counter.getValue());
               assertEquals((i + 1) * 1000, counter.getPersistentSize());

               tx = new TransactionImpl(server.getStorageManager());
            }
         }

         tx.commit();

         storage.waitOnOperations();

         assertEquals(2100, counter.getValue());
         assertEquals(2100 * 1000, counter.getPersistentSize());

         server.stop();

         server = newActiveMQServer();

         server.start();

         queue = server.locateQueue(new SimpleString("A1"));

         assertNotNull(queue);

         counter = locateCounter(queue);

         assertEquals(0, counter.getValue());
         assertEquals(0, counter.getPersistentSize());

      } finally {
         sf.close();
         session.close();
      }
   }

   @Test
   public void testRestartCounter() throws Exception {
      server.addAddressInfo(new AddressInfo(new SimpleString("A1"), RoutingType.ANYCAST));
      Queue queue = server.createQueue(new QueueConfiguration(new SimpleString("A1")).setRoutingType(RoutingType.ANYCAST));

      PageSubscriptionCounter counter = locateCounter(queue);

      StorageManager storage = server.getStorageManager();

      Transaction tx = new TransactionImpl(server.getStorageManager());

      counter.increment(tx, 1, 1000);

      assertEquals(0, counter.getValue());
      assertEquals(0, counter.getPersistentSize());

      tx.commit();

      storage.waitOnOperations();

      assertEquals(1, counter.getValue());
      assertEquals(1000, counter.getPersistentSize());

      sl.close();

      server.stop();

      server = newActiveMQServer();

      server.start();

      queue = server.locateQueue(new SimpleString("A1"));

      assertNotNull(queue);

      counter = locateCounter(queue);

      assertEquals(1, counter.getValue());
      assertEquals(1000, counter.getPersistentSize());

   }

   /**
    * @param queue
    * @return
    * @throws Exception
    */
   private PageSubscriptionCounter locateCounter(Queue queue) throws Exception {
      PageSubscription subscription = server.getPagingManager().getPageStore(new SimpleString("A1")).getCursorProvider().getSubscription(queue.getID());

      PageSubscriptionCounter counter = subscription.getCounter();
      return counter;
   }

   @Test
   public void testPrepareCounter() throws Exception {
      Xid xid = newXID();

      Queue queue = server.createQueue(new QueueConfiguration(new SimpleString("A1")).setRoutingType(RoutingType.ANYCAST));

      PageSubscriptionCounter counter = locateCounter(queue);

      StorageManager storage = server.getStorageManager();

      Transaction tx = new TransactionImpl(xid, server.getStorageManager(), 300);

      for (int i = 0; i < 2000; i++) {
         counter.increment(tx, 1, 1000);
      }

      assertEquals(0, counter.getValue());

      tx.prepare();

      storage.waitOnOperations();

      assertEquals(0, counter.getValue());

      server.stop();

      server = newActiveMQServer();

      server.start();

      storage = server.getStorageManager();

      queue = server.locateQueue(new SimpleString("A1"));

      assertNotNull(queue);

      counter = locateCounter(queue);

      tx = server.getResourceManager().removeTransaction(xid, null);

      assertNotNull(tx);

      assertEquals(0, counter.getValue());

      tx.commit(false);

      storage.waitOnOperations();

      assertEquals(2000, counter.getValue());

   }

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      server = newActiveMQServer();
      server.start();
      sl = createInVMNonHALocator();
   }

   private ActiveMQServer newActiveMQServer() throws Exception {

      OperationContextImpl.clearContext();

      ActiveMQServer server = super.createServer(true, false);

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(10 * 1024).setMaxSizeBytes(20 * 1024);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }


}
