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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.TransientQueueManager;
import org.apache.activemq.artemis.utils.ReferenceCounterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class TransientQueueManagerImpl extends ReferenceCounterUtil implements TransientQueueManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final SimpleString queueName;

   private final ActiveMQServer server;

   private void doIt() {
      try {
         logger.debug("deleting temporary queue {}", queueName);

         try {
            server.destroyQueue(queueName, null, false);
         } catch (ActiveMQNonExistentQueueException e) {
            // ignore
         } catch (ActiveMQException e) {
            ActiveMQServerLogger.LOGGER.errorOnDeletingQueue(queueName.toString(), e);
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorRemovingTempResource(queueName, e);
      }
   }

   public TransientQueueManagerImpl(ActiveMQServer server, SimpleString queueName) {
      // We have to use the same executor between here and ServerSessionImpl::TempResourceCleanerUpper
      super(server.getTransientQueueExecutor());

      this.server = server;

      this.queueName = queueName;

      this.setTask(this::doIt);
   }

   @Override
   public SimpleString getQueueName() {
      return queueName;
   }
}
