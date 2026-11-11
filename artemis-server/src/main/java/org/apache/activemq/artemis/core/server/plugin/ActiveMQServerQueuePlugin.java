/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.plugin;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueConfig;

public interface ActiveMQServerQueuePlugin extends ActiveMQServerBasePlugin {

   /**
    * Before a queue is created
    */
   default void beforeCreateQueue(QueueConfig queueConfig) throws ActiveMQException {

   }

   /**
    * Before a queue is created
    */
   default void beforeCreateQueue(QueueConfiguration queueConfig) throws ActiveMQException {
      //by default call the old method for backwards compatibility
      beforeCreateQueue(QueueConfig.fromQueueConfiguration(queueConfig));
   }

   /**
    * After a queue has been created
    *
    * @param queue The newly created queue
    */
   default void afterCreateQueue(Queue queue) throws ActiveMQException {

   }

   /**
    * Before a queue is destroyed
    *
    *
    * @deprecated use {@link #beforeDestroyQueue(Queue, SecurityAuth, boolean, boolean, boolean)}
    */
   @Deprecated
   default void beforeDestroyQueue(SimpleString queueName, final SecurityAuth session, boolean checkConsumerCount,
                                   boolean removeConsumers, boolean autoDeleteAddress) throws ActiveMQException {

   }

   /**
    * Before a queue is destroyed
    */
   default void beforeDestroyQueue(Queue queue, final SecurityAuth session, boolean checkConsumerCount,
                                   boolean removeConsumers, boolean autoDeleteAddress) throws ActiveMQException {
      //by default call the old method for backwards compatibility
      beforeDestroyQueue(queue.getName(), session, checkConsumerCount, removeConsumers, autoDeleteAddress);
   }

   /**
    * After a queue has been destroyed
    */
   default void afterDestroyQueue(Queue queue, SimpleString address, final SecurityAuth session, boolean checkConsumerCount,
                                  boolean removeConsumers, boolean autoDeleteAddress) throws ActiveMQException {

   }

   /**
    * To be called before starting expiry scan on the queue
    */
   default void beforeExpiryScan(Queue queue) {
   }

   /**
    * To be called before starting expiry scan on the queue
    */
   default void afterExpiryScan(Queue queue) {
   }
}
