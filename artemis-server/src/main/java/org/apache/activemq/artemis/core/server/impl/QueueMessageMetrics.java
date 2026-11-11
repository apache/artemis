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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueMessageMetrics {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final AtomicIntegerFieldUpdater<QueueMessageMetrics> COUNT_UPDATER =
         AtomicIntegerFieldUpdater.newUpdater(QueueMessageMetrics.class, "messageCount");

   private static final AtomicIntegerFieldUpdater<QueueMessageMetrics> COUNT_UPDATER_PAGED =
      AtomicIntegerFieldUpdater.newUpdater(QueueMessageMetrics.class, "messageCountPaged");

   private static final AtomicIntegerFieldUpdater<QueueMessageMetrics> DURABLE_COUNT_UPDATER =
         AtomicIntegerFieldUpdater.newUpdater(QueueMessageMetrics.class, "durableMessageCount");

   private static final AtomicIntegerFieldUpdater<QueueMessageMetrics> DURABLE_COUNT_UPDATER_PAGED =
      AtomicIntegerFieldUpdater.newUpdater(QueueMessageMetrics.class, "durableMessageCountPaged");

   private static final AtomicLongFieldUpdater<QueueMessageMetrics> SIZE_UPDATER =
         AtomicLongFieldUpdater.newUpdater(QueueMessageMetrics.class, "persistentSize");

   private static final AtomicLongFieldUpdater<QueueMessageMetrics> SIZE_UPDATER_PAGED =
      AtomicLongFieldUpdater.newUpdater(QueueMessageMetrics.class, "persistentSizePaged");

   private static final AtomicLongFieldUpdater<QueueMessageMetrics> DURABLE_SIZE_UPDATER =
         AtomicLongFieldUpdater.newUpdater(QueueMessageMetrics.class, "durablePersistentSize");

   private static final AtomicLongFieldUpdater<QueueMessageMetrics> DURABLE_SIZE_UPDATER_PAGED =
      AtomicLongFieldUpdater.newUpdater(QueueMessageMetrics.class, "durablePersistentSizePaged");

   private volatile int messageCount;

   private volatile int messageCountPaged;

   private volatile long persistentSize;

   private volatile long persistentSizePaged;

   private volatile int durableMessageCount;

   private volatile int durableMessageCountPaged;

   private volatile long durablePersistentSize;

   private volatile long durablePersistentSizePaged;

   private final Queue queue;

   private final String name;

   public QueueMessageMetrics(final Queue queue, final String name) {
      this.queue = Objects.requireNonNull(queue);
      this.name = name;
   }

   public void incrementMetrics(final MessageReference reference) {
      long size = getPersistentSize(reference);
      if (reference.isPaged()) {
         COUNT_UPDATER_PAGED.incrementAndGet(this);
         if (logger.isDebugEnabled()) {
            logger.debug("{} paged messageCountPaged to {}: {}", this, messageCountPaged, reference);
         }
         SIZE_UPDATER_PAGED.addAndGet(this, size);
         if (queue.isDurable() && reference.isDurable()) {
            DURABLE_COUNT_UPDATER_PAGED.incrementAndGet(this);
            DURABLE_SIZE_UPDATER_PAGED.addAndGet(this, size);
         }
      } else {
         COUNT_UPDATER.incrementAndGet(this);
         if (logger.isDebugEnabled()) {
            logger.debug("{} increment messageCount to {}: {}", this, messageCount, reference);
         }
         SIZE_UPDATER.addAndGet(this, size);
         if (queue.isDurable() && reference.isDurable()) {
            DURABLE_COUNT_UPDATER.incrementAndGet(this);
            DURABLE_SIZE_UPDATER.addAndGet(this, size);
         }
      }
   }

   public void decrementMetrics(final MessageReference reference) {
      long size = -getPersistentSize(reference);
      if (reference.isPaged()) {
         COUNT_UPDATER_PAGED.decrementAndGet(this);
         if (logger.isDebugEnabled()) {
            logger.debug("{} decrement messageCount to {}: {}", this, messageCountPaged, reference);
         }
         SIZE_UPDATER_PAGED.addAndGet(this, size);
         if (queue.isDurable() && reference.isDurable()) {
            DURABLE_COUNT_UPDATER_PAGED.decrementAndGet(this);
            DURABLE_SIZE_UPDATER_PAGED.addAndGet(this, size);
         }
      } else {
         COUNT_UPDATER.decrementAndGet(this);
         if (logger.isDebugEnabled()) {
            logger.debug("{} decrement messageCount to {}: {}", this, messageCount, reference);
         }
         SIZE_UPDATER.addAndGet(this, size);
         if (queue.isDurable() && reference.isDurable()) {
            DURABLE_COUNT_UPDATER.decrementAndGet(this);
            DURABLE_SIZE_UPDATER.addAndGet(this, size);
         }
      }
   }


   public int getNonPagedMessageCount() {
      return messageCount;
   }

   public int getMessageCount() {
      return messageCount + messageCountPaged;
   }

   public long getPersistentSize() {
      return persistentSize + persistentSizePaged;
   }

   public long getNonPagedPersistentSize() {
      return persistentSize;
   }

   public long getNonPagedDurablePersistentSize() {
      return durablePersistentSize;
   }

   public int getDurableMessageCount() {
      return durableMessageCount + durableMessageCountPaged;
   }

   public int getNonPagedDurableMessageCount() {
      return durableMessageCount;
   }

   public long getDurablePersistentSize() {
      return durablePersistentSize + durablePersistentSizePaged;
   }

   private static long getPersistentSize(final MessageReference reference) {
      long size = 0;

      try {
         size = reference.getPersistentSize() > 0 ? reference.getPersistentSize() : 0;
      } catch (Throwable e) {
         ActiveMQServerLogger.LOGGER.errorCalculatePersistentSize(e);
      }

      return size;
   }
}
