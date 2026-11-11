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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ServerConsumer;

public interface ActiveMQServerConsumerPlugin extends ActiveMQServerBasePlugin {

   /**
    * Before a consumer is created
    *
    *
    * @deprecated use {@link #beforeCreateConsumer(long, QueueBinding, SimpleString, boolean, boolean)}
    */
   @Deprecated
   default void beforeCreateConsumer(long consumerID, SimpleString queueName, SimpleString filterString,
                                     boolean browseOnly, boolean supportLargeMessage) throws ActiveMQException {

   }


   /**
    * Before a consumer is created
    */
   default void beforeCreateConsumer(long consumerID, QueueBinding queueBinding, SimpleString filterString,
                                     boolean browseOnly, boolean supportLargeMessage) throws ActiveMQException {
      //by default call the old method for backwards compatibility
      this.beforeCreateConsumer(consumerID, queueBinding.getQueue().getName(), filterString, browseOnly, supportLargeMessage);
   }

   /**
    * After a consumer has been created
    *
    * @param consumer the created consumer
    */
   default void afterCreateConsumer(ServerConsumer consumer) throws ActiveMQException {

   }

   /**
    * Before a consumer is closed
    */
   default void beforeCloseConsumer(ServerConsumer consumer, boolean failed) throws ActiveMQException {

   }

   /**
    * After a consumer is closed
    */
   default void afterCloseConsumer(ServerConsumer consumer, boolean failed) throws ActiveMQException {

   }
}
