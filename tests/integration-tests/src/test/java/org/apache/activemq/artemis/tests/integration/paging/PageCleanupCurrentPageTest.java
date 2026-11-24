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

import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageCursorProviderTestAccessor;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManagerImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PageCleanupCurrentPageTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final SimpleString ADDRESS = SimpleString.of("pageCleanupCurrentPage");

   private static final int PAGE_MAX = 100 * 1024;

   private static final int PAGE_SIZE = 10 * 1024;

   private static final int MESSAGES = 10;

   private ActiveMQServer server;

   @Test
   public void testCleanupKeepsCurrentPageWhileItIsNotFlushed() throws Exception {
      final Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      // when set, the current writing page pretends its file is not on the storage yet
      final AtomicBoolean hideCurrentPageFile = new AtomicBoolean(false);

      server = new ActiveMQServerImpl(config, ManagementFactory.getPlatformMBeanServer(), new ActiveMQSecurityManagerImpl()) {
         @Override
         protected PagingStoreFactoryNIO getPagingStoreFactory() {
            return new PagingStoreFactoryNIO(getStorageManager(), getConfiguration().getPagingLocation(), getConfiguration().getJournalBufferTimeout_NIO(), getScheduledPool(), getExecutorFactory(), getConfiguration().isJournalSyncNonTransactional(), null) {
               @Override
               public synchronized PagingStore newStore(SimpleString address, AddressSettings settings) {
                  return new PagingStoreImpl(address, getScheduledExecutor(), config.getJournalBufferTimeout_NIO(), getPagingManager(), getStorageManager(), null, this, address, settings, getExecutorFactory().getExecutor(), isSyncNonTransactional()) {
                     @Override
                     public boolean checkPageFileExists(long pageNumber) {
                        if (hideCurrentPageFile.get() && pageNumber == getCurrentWritingPage()) {
                           logger.debug("simulating a non flushed page file on page {}", pageNumber);
                           return false;
                        }
                        return super.checkPageFileExists(pageNumber);
                     }
                  };
               }
            };
         }
      };
      addServer(server);

      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setPageSizeBytes(PAGE_SIZE).setMaxSizeBytes(PAGE_MAX).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxReadPageBytes(-1));

      server.start();

      server.addAddressInfo(new AddressInfo(ADDRESS).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      final Queue queue = server.locateQueue(ADDRESS);
      final PagingStore store = queue.getPagingStore();

      final ServerLocator locator = addServerLocator(createInVMNonHALocator()).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);
      final ClientSessionFactory sf = createSessionFactory(locator);
      final ClientSession session = addClientSession(sf.createSession(false, true, true));
      session.start();

      final ClientProducer producer = session.createProducer(ADDRESS);

      // A first complete page/consume/cleanup cycle. The cleanup leaves page mode with a brand new current page which
      // the next call to startPaging() will reuse
      pageAndConsume(store, session, producer);
      PageCursorProviderTestAccessor.cleanup(store.getCursorProvider());
      Wait.assertFalse(store::isPaging, 5000, 10);

      // Resume paging and write a single message on the page left behind by the previous cleanup
      store.startPaging();
      producer.send(session.createMessage(true));
      Wait.assertTrue(() -> store.getCurrentPage() != null && store.getCurrentPage().getNumberOfMessages() == 1, 5000, 10);

      final long pagedOn = store.getCurrentWritingPage();
      logger.debug("the message to be preserved was paged on {}", pagedOn);

      hideCurrentPageFile.set(true);
      try {
         PageCursorProviderTestAccessor.cleanup(store.getCursorProvider());
      } finally {
         hideCurrentPageFile.set(false);
      }

      // The simulation only hides the page file from the cleanup, it does not hide the message itself, so the queue
      // can still deliver it from memory even after the page is deleted. What the cleanup got wrong is therefore
      // asserted on the page store: it must stay in page mode and keep writing to the same page.
      assertTrue(store.isStorePaging(), "the cleanup left page mode while page " + pagedOn + " still held an unconsumed message");
      assertEquals(pagedOn, store.getCurrentWritingPage(), "the cleanup rolled over page " + pagedOn + " while it was still being written");
   }

   private void pageAndConsume(PagingStore store, ClientSession session, ClientProducer producer) throws Exception {
      store.startPaging();

      for (int i = 0; i < MESSAGES; i++) {
         producer.send(session.createMessage(true));
      }

      try (ClientConsumer consumer = session.createConsumer(ADDRESS)) {
         for (int i = 0; i < MESSAGES; i++) {
            ClientMessage received = consumer.receive(5000);
            assertNotNull(received, "could not receive message " + i + " during the warm up");
            received.acknowledge();
         }
         session.commit();
      }
   }
}
