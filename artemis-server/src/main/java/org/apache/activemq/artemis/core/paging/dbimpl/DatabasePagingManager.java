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
package org.apache.activemq.artemis.core.paging.dbimpl;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.impl.AbstracPagingManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;

public class DatabasePagingManager extends AbstracPagingManager {

   public DatabasePagingManager(final PagingStoreFactory pagingStoreFactory,
                                final HierarchicalRepository<AddressSettings> addressSettingsRepository,
                                final long maxSize,
                                final long maxMessages,
                                final SimpleString managementAddress,
                                final ActiveMQServer server) {
      super(pagingStoreFactory, addressSettingsRepository, maxSize, maxMessages, managementAddress, server);
   }

   @Override
   public boolean requireRebuildCounters() {
      return false;
   }

   @Override
   public void start() throws Exception {
      started = true;
   }

   @Override
   public void stop() throws Exception {
      started = false;
   }
}
