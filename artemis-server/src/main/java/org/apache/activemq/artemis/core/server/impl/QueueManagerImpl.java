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

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueManager;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.ReferenceCounterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class QueueManagerImpl extends ReferenceCounterUtil implements QueueManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final SimpleString queueName;

   private final ActiveMQServer server;

   private void doIt() {
      Queue queue = server.locateQueue(queueName);
      //the queue may already have been deleted and this is a result of that
      if (queue == null) {
         logger.debug("no queue to delete \"{}\".", queueName);
         return;
      }

      if (queue.isPurgeOnNoConsumers()) {
         purge(queue);
      }
   }

   private static void purge(Queue queue) {
      long consumerCount = queue.getConsumerCount();
      long messageCount = queue.getMessageCount();

      if (logger.isDebugEnabled()) {
         logger.debug("purging queue \"{}\": consumerCount = {}; messageCount = {}", queue.getName(), consumerCount, messageCount);
      }

      try {
         queue.deleteMatchingReferences(QueueImpl.DEFAULT_FLUSH_LIMIT, null, AckReason.KILLED);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToPurgeQueue(queue.getName(), e);
      }
   }

   public static void performAutoDeleteQueue(ActiveMQServer server, Queue queue) {
      SimpleString queueName = queue.getName();

      if (logger.isDebugEnabled()) {
         logger.debug("deleting auto-created queue \"{}\": consumerCount = {}; messageCount = {}; isAutoDelete = {}", queueName, queue.getConsumerCount(), queue.getMessageCount(), queue.isAutoDelete());
      }

      ActiveMQServerLogger.LOGGER.autoRemoveQueue(String.valueOf(queue.getName()), queue.getID(), String.valueOf(queue.getAddress()));

      try {
         server.destroyQueue(queueName, null, true, false, false, true);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorRemovingAutoCreatedDestination("queue", queueName, e);
      }
   }

   public static boolean messageCountCheck(Queue queue) {
      return queue.getAutoDeleteMessageCount() == -1 || queue.getMessageCount() <= queue.getAutoDeleteMessageCount();
   }

   public static boolean delayCheck(Queue queue, AddressSettings settings) {
      return (!settings.getAutoDeleteQueuesSkipUsageCheck() && System.currentTimeMillis() - queue.getConsumerRemovedTimestamp() >= queue.getAutoDeleteDelay()) || (settings.getAutoDeleteQueuesSkipUsageCheck() && System.currentTimeMillis() - queue.getCreatedTimestamp() >= queue.getAutoDeleteDelay());
   }

   public static boolean consumerCountCheck(Queue queue) {
      return queue.getConsumerCount() == 0;
   }

   public QueueManagerImpl(ActiveMQServer server, SimpleString queueName) {
      // This needs to be an executor
      // otherwise we may have dead-locks in certain cases such as failure,
      // where consumers are closed after callbacks
      super(server.getExecutorFactory().getExecutor());
      this.server = server;
      this.queueName = queueName;
      this.setTask(this::doIt);
   }

   @Override
   public SimpleString getQueueName() {
      return queueName;
   }
}
