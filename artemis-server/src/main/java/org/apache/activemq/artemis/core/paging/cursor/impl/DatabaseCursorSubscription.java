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
package org.apache.activemq.artemis.core.paging.cursor.impl;

import java.util.function.Consumer;

import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.ConsumedPage;
import org.apache.activemq.artemis.core.paging.cursor.PageIterator;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscriptionCounter;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.transaction.Transaction;

public class DatabaseCursorSubscription implements PageSubscription {

   @Override
   public PagingStore getPagingStore() {
      return null;
   }

   @Override
   public void stop() {
   }

   @Override
   public void counterSnapshot() {
   }

   @Override
   public void deleteCursorInfo() {
   }

   @Override
   public void notEmpty() {
   }

   @Override
   public void bookmark(PagePosition position) throws Exception {
   }

   @Override
   public PageSubscriptionCounter getCounter() {
      return null;
   }

   @Override
   public long getMessageCount() {
      return 0;
   }

   @Override
   public boolean isCounterPending() {
      return false;
   }

   @Override
   public long getPersistentSize() {
      return 0;
   }

   @Override
   public long getId() {
      return 0;
   }

   @Override
   public boolean isPersistent() {
      return false;
   }

   @Override
   public boolean isPaging() {
      return false;
   }

   @Override
   public boolean isStorePaging() {
      return false;
   }

   @Override
   public PageIterator iterator() {
      return null;
   }

   @Override
   public PageIterator iterator(boolean browsing) {
      return null;
   }

   @Override
   public void destroy() throws Exception {
   }

   @Override
   public void scheduleCleanupCheck() {
   }

   @Override
   public void cleanupEntries(boolean completeDelete) throws Exception {
   }

   @Override
   public void onPageModeCleared(Transaction tx) throws Exception {
   }

   @Override
   public void disableAutoCleanup() {
   }

   @Override
   public void enableAutoCleanup() {
   }

   @Override
   public void ack(PagedReference ref) throws Exception {
   }

   @Override
   public boolean contains(PagedReference ref) throws Exception {
      return false;
   }

   @Override
   public boolean isAcked(PagedMessage pagedMessage) {
      return false;
   }

   @Override
   public void confirmPosition(PagePosition ref) throws Exception {
   }

   @Override
   public void ackTx(Transaction tx, PagedReference position, boolean fromDelivery) throws Exception {
   }

   @Override
   public void confirmPosition(Transaction tx, PagePosition position, boolean fromDelivery) throws Exception {
   }

   @Override
   public long getFirstPage() {
      return Long.MAX_VALUE;
   }

   @Override
   public void reloadACK(PagePosition position) {
   }

   @Override
   public boolean reloadPageCompletion(PagePosition position) throws Exception {
      return false;
   }

   @Override
   public void reloadPageInfo(long pageNr) {
   }

   @Override
   public void positionIgnored(PagePosition position) {
   }

   @Override
   public void lateDeliveryRollback(PagePosition position) {
   }

   @Override
   public void reloadPreparedACK(Transaction tx, PagePosition position) {
   }

   @Override
   public void processReload() throws Exception {
   }

   @Override
   public void addPendingDelivery(PagedMessage pagedMessage) {
   }

   @Override
   public void redeliver(PageIterator iterator, PagedReference reference) {
   }

   @Override
   public void printDebug() {
   }

   @Override
   public boolean isComplete(long page) {
      return false;
   }

   @Override
   public void forEachConsumedPage(Consumer<ConsumedPage> pageCleaner) {
   }

   @Override
   public PagedMessage queryMessage(PagePosition pos) {
      return null;
   }

   @Override
   public void setQueue(Queue queue) {
   }

   @Override
   public Queue getQueue() {
      return null;
   }

   @Override
   public void onDeletePage(Page deletedPage) throws Exception {
   }

   @Override
   public void removePendingDelivery(PagedMessage pagedMessage) {
   }

   @Override
   public ConsumedPage locatePageInfo(long pageNr) {
      return null;
   }

}
