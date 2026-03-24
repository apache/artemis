/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.paging.cursor.impl;

import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.cursor.NonExistentPage;
import org.apache.activemq.artemis.core.paging.cursor.PageCache;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.jboss.logging.Logger;

import static org.apache.activemq.artemis.utils.Preconditions.checkNotNull;

public class PageReader implements PageCache {
   private static final Logger logger = Logger.getLogger(PageReader.class);

   private final Page page;
   private final int numberOfMessages;

   public PageReader(Page page, int numberOfMessages) {
      checkNotNull(page);

      this.page = page;
      this.numberOfMessages = numberOfMessages;
   }

   @Override
   public long getPageId() {
      return page.getPageId();
   }

   @Override
   public int getNumberOfMessages() {
      return numberOfMessages;
   }

   /**
    * Used just for testing purposes.
    */
   protected synchronized PagedMessage[] readMessages() {
      try {
         openPage();
         return page.read().toArray(new PagedMessage[numberOfMessages]);
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      } finally {
         close();
      }
   }

   @Override
   public boolean isLive() {
      return false;
   }

   /**
    * @param pagePosition   page position
    * @param throwException if {@code true} exception will be thrown when message number is beyond the page
    * @param keepOpen       if {@code true} page file would keep open after reading message
    * @return the paged message
    */
   public synchronized PagedMessage getMessage(PagePosition pagePosition, boolean throwException, boolean keepOpen) {
      if (pagePosition.getMessageNr() >= getNumberOfMessages()) {
         if (throwException) {
            throw new NonExistentPage("Invalid messageNumber passed = " + pagePosition + " on " + this);
         }
         return null;
      }

      boolean previouslyClosed = true;
      try {
         previouslyClosed = openPage();
         PagedMessage msg;
         if (pagePosition.getFileOffset() != -1) {
            msg = page.readMessage(pagePosition.getFileOffset(), pagePosition.getMessageNr(), pagePosition.getMessageNr());
         } else {
            if (logger.isTraceEnabled()) {
               logger.trace("get message from pos " + pagePosition, new Exception("trace get message without file offset"));
            }
            msg = page.readMessage(0, 0, pagePosition.getMessageNr());
         }
         return msg;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      } finally {
         if (!keepOpen && previouslyClosed) {
            close();
         }
      }
   }

   @Override
   public synchronized PagedMessage getMessage(PagePosition pagePosition) {
      return getMessage(pagePosition, false, false);
   }

   /**
    * @return true if file was previously closed
    * @throws Exception
    */
   boolean openPage() throws Exception {
      if (!page.getFile().isOpen()) {
         page.getFile().open();
         return true;
      }
      return false;
   }

   @Override
   public synchronized void close() {
      try {
         page.close(false, false);
      } catch (Exception e) {
         logger.warn("Closing page " + page.getPageId() + " occurs exception:", e);
      }
   }

   @Override
   public String toString() {
      return "PageReader::page=" + getPageId() + " numberOfMessages = " + numberOfMessages;
   }
}
