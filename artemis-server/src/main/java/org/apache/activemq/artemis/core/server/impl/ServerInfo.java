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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Date;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.utils.SizeFormatterUtil;

public class ServerInfo {

   private final ActiveMQServer server;

   private final PagingManager pagingManager;

   public ServerInfo(final ActiveMQServer server, final PagingManager pagingManager) {
      this.server = server;
      this.pagingManager = pagingManager;
   }


   public String dump() {
      long maxMemory = Runtime.getRuntime().maxMemory();
      long totalMemory = Runtime.getRuntime().totalMemory();
      long freeMemory = Runtime.getRuntime().freeMemory();
      long availableMemory = freeMemory + maxMemory - totalMemory;
      double availableMemoryPercent = 100.0 * availableMemory / maxMemory;
      ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

      StringBuilder info = new StringBuilder("\n**** Server Dump ****\n");
      info.append(String.format("date:            %s%n", new Date()));
      info.append(String.format("free memory:      %s%n", SizeFormatterUtil.sizeof(freeMemory)));
      info.append(String.format("max memory:       %s%n", SizeFormatterUtil.sizeof(maxMemory)));
      info.append(String.format("total memory:     %s%n", SizeFormatterUtil.sizeof(totalMemory)));
      info.append(String.format("available memory: %.2f%%%n", availableMemoryPercent));
      info.append(appendPagingInfos());
      info.append(String.format("# of thread:     %d%n", threadMXBean.getThreadCount()));
      info.append(String.format("# of conns:      %d%n", server.getConnectionCount()));
      info.append("********************\n");
      return info.toString();
   }

   private String appendPagingInfos() {
      StringBuilder info = new StringBuilder();

      for (SimpleString storeName : pagingManager.getStoreNames()) {
         PagingStore pageStore;
         try {
            pageStore = pagingManager.getPageStore(storeName);
            info.append(String.format("\t%s: %s%n", storeName, SizeFormatterUtil.sizeof(pageStore.getPageSizeBytes() * pageStore.getNumberOfPages())));
         } catch (Exception e) {
            info.append(String.format("\t%s: %s%n", storeName, e.getMessage()));
         }
      }
      return info.toString();
   }
}
