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
package org.apache.activemq.artemis.tests.unit.core.paging.impl;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.collections.LinkedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PagingManagerImplTest extends ActiveMQTestBase {

   @Test
   public void testPagingManager() throws Exception {

      HierarchicalRepository<AddressSettings> addressSettings = new HierarchicalObjectRepository<>();
      addressSettings.setDefault(new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE));

      OrderedExecutorFactory orderedExecutorFactory = getOrderedExecutor();

      final StorageManager storageManager = new NullStorageManager().setContextSupplier(() -> OperationContextImpl.getContext(orderedExecutorFactory));

      ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
      runAfter(scheduledExecutorService::shutdownNow);

      PagingStoreFactoryNIO storeFactory = new PagingStoreFactoryNIO(storageManager, getPageDirFile(), 100, scheduledExecutorService, orderedExecutorFactory, true, null);

      PagingManagerImpl managerImpl = new PagingManagerImpl(storeFactory, addressSettings);

      managerImpl.start();

      runAfter(managerImpl::stop);

      PagingStore store = managerImpl.getPageStore(SimpleString.of("simple-test"));

      ICoreMessage msg = createMessage(1L, SimpleString.of("simple-test"), createRandomBuffer(10));

      final RoutingContextImpl ctx = new RoutingContextImpl(null);
      assertFalse(store.page(msg, ctx.getTransaction(), ctx.getContextListing(store.getStoreName())));

      store.startPaging();

      assertTrue(store.page(msg, ctx.getTransaction(), ctx.getContextListing(store.getStoreName())));
      syncOperationContext();

      Page page = depageOnExecutor(store);

      page.open(true);

      LinkedList<PagedMessage> msgs = page.read(new NullStorageManager());

      page.close(false, false);

      assertEquals(1, msgs.size());

      ActiveMQTestBase.assertEqualsByteArrays(msg.getBodyBuffer().writerIndex(), msg.getBodyBuffer().toByteBuffer().array(), (msgs.get(0).getMessage()).toCore().getBodyBuffer().toByteBuffer().array());

      assertTrue(store.isPaging());

      assertNull(depageOnExecutor(store));

      final RoutingContextImpl ctx2 = new RoutingContextImpl(null);
      assertFalse(store.page(msg, ctx2.getTransaction(), ctx2.getContextListing(store.getStoreName())));

   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      File fileJournalDir = new File(getJournalDir());
      fileJournalDir.mkdirs();

      File pageDirDir = new File(getPageDir());
      pageDirDir.mkdirs();
   }

   protected ICoreMessage createMessage(final long messageId,
                                        final SimpleString destination,
                                        final ByteBuffer buffer) {
      ICoreMessage msg = new CoreMessage(messageId, 200);

      msg.setAddress(destination);

      msg.getBodyBuffer().writeBytes(buffer);

      return msg;
   }

   protected ByteBuffer createRandomBuffer(final int size) {
      ByteBuffer buffer = ByteBuffer.allocate(size);

      for (int j = 0; j < buffer.limit(); j++) {
         buffer.put(RandomUtil.randomByte());
      }
      return buffer;
   }

   /**
    * depage is now done within the page's executor. This unit test needs to call the depage within that executor
    */
   protected Page depageOnExecutor(final PagingStore store) throws Exception {
      return callOnExecutor(store.getExecutor(), () -> store.depage(), 10, TimeUnit.SECONDS);
   }
}
