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
package org.apache.activemq.artemis.core.paging.impl;

import java.util.function.Consumer;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * This is a simple cache where we keep Page objects only while they are being used.
 */
public class PageCache {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final PagingStore owner;

   public PageCache(PagingStore owner) {
      this.owner = owner;
   }

   private final LongObjectHashMap<Page> usedPages = new LongObjectHashMap<>();

   public synchronized Page get(long pageID) {
      return usedPages.get(pageID);
   }

   public synchronized void forEachUsedPage(Consumer<Page> consumerPage) {
      usedPages.values().forEach(consumerPage);
   }

   public int size() {
      return usedPages.size();
   }

   public synchronized void injectPage(Page page) {
      if (logger.isDebugEnabled()) {
         logger.debug("+++ Injecting page {} on UsedPages for destination {}", page.getPageId(), owner.getAddress());
      }
      page.releaseTask(this::removePage);
      usedPages.put(page.getPageId(), page);
   }


   public synchronized void removePage(Page page) {
      if (usedPages.remove(page.getPageId()) != null) {
         if (logger.isDebugEnabled()) {
            logger.debug("--- Releasing page {} on UsedPages for destination {}", page.getPageId(), owner.getAddress());
         }
      }
   }

   public synchronized void clear() {
      usedPages.clear();
   }


}
