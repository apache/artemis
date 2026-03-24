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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PageCleanupWhileReplicaCatchupTest extends FailoverTestBase {

   private static final Logger logger = Logger.getLogger(PageCleanupWhileReplicaCatchupTest.class);
   volatile boolean running = true;

   @Override
   @Before
   public void setUp() throws Exception {
      startBackupServer = false;
      super.setUp();
   }

   @Override
   protected void createConfigs() throws Exception {
      createReplicatedConfigs();
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return getNettyAcceptorTransportConfiguration(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return getNettyConnectorTransportConfiguration(live);
   }

   @Override
   protected ActiveMQServer createInVMFailoverServer(final boolean realFiles,
                                                     final Configuration configuration,
                                                     final NodeManager nodeManager,
                                                     int id) {
      Map<String, AddressSettings> conf = new HashMap<>();
      AddressSettings as = new AddressSettings().setMaxSizeBytes(PAGE_MAX).setPageSizeBytes(PAGE_SIZE).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      conf.put(ADDRESS.toString(), as);
      return createInVMFailoverServer(realFiles, configuration, PAGE_SIZE, PAGE_MAX, conf, nodeManager, id);
   }

   @Test(timeout = 160_000)
   public void testPageCleanup() throws Throwable {
      int numberOfWorkers = 20;

      Worker[] workers = new Worker[numberOfWorkers];

      for (int i = 0; i < 20; i++) {
         liveServer.getServer().addAddressInfo(new AddressInfo("WORKER_" + i).setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
         liveServer.getServer().createQueue(new QueueConfiguration("WORKER_" + i).setRoutingType(RoutingType.ANYCAST).setDurable(true));
         workers[i] = new Worker("WORKER_" + i);
         workers[i].start();
      }

      for (int i = 0; i < 25; i++) {
         logger.debug("Starting replica " + i);
         backupServer.start();
         Wait.assertTrue(backupServer.getServer()::isReplicaSync);
         backupServer.stop();
      }

      running = false;

      for (Worker worker : workers) {
         worker.join();
      }

      Throwable toThrow = null;
      for (Worker worker : workers) {
         if (worker.throwable != null) {
            worker.queue.getPagingStore().getCursorProvider().scheduleCleanup();
            Thread.sleep(2000);
            worker.queue.getPagingStore().getCursorProvider().cleanup();

            // This is more a debug statement in case there is an issue with the test
            System.out.println("PagingStore(" + worker.queueName + ")::isPaging() = " + worker.queue.getPagingStore().isPaging() + " after test failure " + worker.throwable.getMessage());
            toThrow = worker.throwable;
         }
      }

      if (toThrow != null) {
         throw toThrow;
      }

      for (Worker worker : workers) {
         PagingStoreImpl storeImpl = (PagingStoreImpl)worker.queue.getPagingStore();
         Assert.assertTrue("Store impl " + worker.queueName + " had more files than expected on " + storeImpl.getFolder(), storeImpl.getNumberOfFiles() <= 1);
      }
   }

   class Worker extends Thread {

      final String queueName;
      final Queue queue;
      volatile Throwable throwable;

      Worker(String queue) {
         super("Worker on queue " + queue + " for test on PageCleanupWhileReplicaCatchupTest");
         this.queueName = queue;
         this.queue = liveServer.getServer().locateQueue(queueName);
      }

      @Override
      public void run() {
         try {
            ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
            try (Connection connection = factory.createConnection()) {
               Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
               connection.start();
               javax.jms.Queue jmsQueue = session.createQueue(queueName);
               MessageConsumer consumer = session.createConsumer(jmsQueue);
               MessageProducer producer = session.createProducer(jmsQueue);
               while (running) {
                  queue.getPagingStore().startPaging();
                  for (int i = 0; i < 10; i++) {
                     producer.send(session.createTextMessage("hello " + i));
                  }
                  Wait.assertTrue(queue.getPagingStore()::isPaging);
                  for (int i = 0; i < 10; i++) {
                     Assert.assertNotNull(consumer.receive(5000));
                  }
                  Wait.assertFalse("Waiting for !Paging on " + queueName + " with folder " + queue.getPagingStore().getFolder(), queue.getPagingStore()::isPaging);
               }
            }
         } catch (Throwable e) {
            e.printStackTrace(System.out);
            this.throwable = e;
         }

      }
   }

}
