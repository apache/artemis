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
package org.apache.activemq.artemis.core.server.impl;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueFactoryImpl implements QueueFactory {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected final HierarchicalRepository<AddressSettings> addressSettingsRepository;

   protected final ScheduledExecutorService scheduledExecutor;

   /**
    * This is required for delete-all-reference to work correctly with paging, and controlling global-size
    */
   protected PostOffice postOffice;

   protected final StorageManager storageManager;

   protected final ExecutorFactory executorFactory;

   protected final ActiveMQServer server;

   public QueueFactoryImpl(final ExecutorFactory executorFactory,
                           final ScheduledExecutorService scheduledExecutor,
                           final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                           final StorageManager storageManager,
                           final ActiveMQServer server) {

      this.addressSettingsRepository = addressSettingsRepository;
      this.scheduledExecutor = scheduledExecutor;
      this.storageManager = storageManager;
      this.executorFactory = executorFactory;
      this.server = server;
   }

   @Override
   public void setPostOffice(final PostOffice postOffice) {
      this.postOffice = postOffice;
   }

   @Override
   public Queue createQueueWith(final QueueConfiguration config, PagingManager pagingManager, Filter filter) {
      validateState(config);
      final Queue queue;
      PageSubscription pageSubscription = getPageSubscription(config, pagingManager, filter);
      if (lastValueKey(config) != null) {
         queue = new LastValueQueue(config.setLastValueKey(lastValueKey(config)), filter, pageSubscription != null ? pageSubscription.getPagingStore() : null, pageSubscription, scheduledExecutor, postOffice, storageManager, addressSettingsRepository, executorFactory.getExecutor(), server, this);
      } else {
         queue = new QueueImpl(config, filter, pageSubscription != null ? pageSubscription.getPagingStore() : null, pageSubscription, scheduledExecutor, postOffice, storageManager, addressSettingsRepository, executorFactory.getExecutor(), server, this);
      }
      server.getCriticalAnalyzer().add(queue);
      return queue;
   }

   @Override
   public void queueRemoved(Queue queue) {
      server.getCriticalAnalyzer().remove(queue);
   }

   public static PageSubscription getPageSubscription(QueueConfiguration queueConfiguration, PagingManager pagingManager, Filter filter) {
      PageSubscription pageSubscription;

      try {
         PagingStore pageStore = pagingManager.getPageStore(queueConfiguration.getAddress());
         if (pageStore != null) {
            pageSubscription = pageStore.getCursorProvider().createSubscription(queueConfiguration.getId(), filter, queueConfiguration.isDurable());
         } else {
            pageSubscription = null;
         }
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }

      return pageSubscription;
   }

   private static SimpleString lastValueKey(final QueueConfiguration config) {
      if (config.getLastValueKey() != null && !config.getLastValueKey().isEmpty()) {
         return config.getLastValueKey();
      } else if (config.isLastValue()) {
         return Message.HDR_LAST_VALUE_NAME;
      } else {
         return null;
      }
   }

   private void validateState(QueueConfiguration config) {
      if (isEmptyOrNull(config.getName())) {
         throw new IllegalStateException("name can't be null or empty!");
      }
      if (isEmptyOrNull(config.getAddress())) {
         throw new IllegalStateException("address can't be null or empty!");
      }
   }

   private static boolean isEmptyOrNull(SimpleString value) {
      return (value == null || value.isEmpty());
   }
}
