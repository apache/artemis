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
import org.jboss.logging.Logger;

public class QueueManagerImpl extends ReferenceCounterUtil implements QueueManager {

   private static final Logger logger = Logger.getLogger(QueueManagerImpl.class);

   private final SimpleString queueName;

   private final ActiveMQServer server;

   private void doIt() {
      Queue queue = server.locateQueue(queueName);
      //the queue may already have been deleted and this is a result of that
      if (queue == null) {
         if (logger.isDebugEnabled()) {
            logger.debug("no queue to delete \"" + queueName + "\".");
         }
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
         logger.debug("purging queue \"" + queue.getName() + "\": consumerCount = " + consumerCount + "; messageCount = " + messageCount);
      }
      try {
         queue.deleteMatchingReferences(QueueImpl.DEFAULT_FLUSH_LIMIT, null, AckReason.KILLED);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.failedToPurgeQueue(e, queue.getName());
      }
   }

   public static void performAutoDeleteQueue(ActiveMQServer server, Queue queue) {
      SimpleString queueName = queue.getName();
      AddressSettings settings = server.getAddressSettingsRepository().getMatch(queue.getAddress().toString());
      if (logger.isDebugEnabled()) {
         logger.debug("deleting auto-created queue \"" + queueName + "\": consumerCount = " + queue.getConsumerCount() + "; messageCount = " + queue.getMessageCount() + "; isAutoDelete = " + queue.isAutoDelete());
      }

      ActiveMQServerLogger.LOGGER.autoRemoveQueue("" + queue.getName(), queue.getID(), "" + queue.getAddress());

      try {
         server.destroyQueue(queueName, null, true, false, false, true);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorRemovingAutoCreatedDestination(e, queueName, "queue");
      }
   }

   public static boolean isAutoDelete(Queue queue) {
      return queue.isAutoDelete();
   }

   public static boolean messageCountCheck(Queue queue) {
      return queue.getAutoDeleteMessageCount() == -1 || queue.getMessageCount() <= queue.getAutoDeleteMessageCount();
   }

   public static boolean delayCheck(Queue queue) {
      return System.currentTimeMillis() - queue.getConsumerRemovedTimestamp() >= queue.getAutoDeleteDelay();
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
