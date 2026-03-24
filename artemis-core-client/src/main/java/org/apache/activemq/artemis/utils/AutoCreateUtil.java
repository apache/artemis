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
package org.apache.activemq.artemis.utils;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import static org.apache.activemq.artemis.api.core.ActiveMQExceptionType.QUEUE_DOES_NOT_EXIST;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSession.AddressQuery;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

/**
 * Utility class to create queues 'automatically'.
 */
public class AutoCreateUtil {

   public static  void autoCreateQueue(ClientSession session, SimpleString destAddress, SimpleString selectorString) throws ActiveMQException {
      AddressQuery response = session.addressQuery(destAddress);
      /* The address query will send back exists=true even if the node only has a REMOTE binding for the destination.
       * Therefore, we must check if the queue names list contains the exact name of the address to know whether or
       * not a LOCAL binding for the address exists. If no LOCAL binding exists then it should be created here.
       */
      SimpleString queueName = getCoreQueueName(session, destAddress);
      if (!response.isExists() || !response.getQueueNames().contains(queueName)) {
         if (response.isAutoCreateQueues()) {
            try {
               QueueConfiguration queueConfiguration = new QueueConfiguration(queueName)
                     .setAutoCreated(true)
                     .setAddress(destAddress);
               setRequiredQueueConfigurationIfNotSet(queueConfiguration,response, RoutingType.ANYCAST, selectorString, true);
               session.createQueue(queueConfiguration);
               ActiveMQClientLogger.LOGGER.debug("The queue " + destAddress + " was created automatically");
            } catch (ActiveMQQueueExistsException e) {
               // The queue was created by another client/admin between the query check and send create queue packet
            }
         } else {
            throw new ActiveMQException("Destination " + destAddress + " does not exist", QUEUE_DOES_NOT_EXIST);
         }
      }
   }

   /**
    * Set the non nullable (CreateQueueMessage_V2) queue attributes (all others have static defaults or get defaulted if null by address settings server side).
    *
    * @param queueConfiguration the provided queue configuration the client wants to set
    * @param addressQuery the address settings query information (this could be removed if max consumers and purge on no consumers were null-able in CreateQueueMessage_V2)
    * @param routingType of the queue (multicast or anycast)
    * @param filter to apply on the queue
    * @param durable if queue is durable
    */
   public static void setRequiredQueueConfigurationIfNotSet(QueueConfiguration queueConfiguration, ClientSession.AddressQuery addressQuery, RoutingType routingType, SimpleString filter, boolean durable) {
      if (queueConfiguration.getRoutingType() == null) {
         queueConfiguration.setRoutingType(routingType);
      }
      if (queueConfiguration.getFilterString() == null) {
         queueConfiguration.setFilterString(filter);
      }
      if (queueConfiguration.getMaxConsumers() == null) {
         queueConfiguration.setMaxConsumers(addressQuery.getDefaultMaxConsumers());
      }
      if (queueConfiguration.isPurgeOnNoConsumers() == null) {
         queueConfiguration.setPurgeOnNoConsumers(addressQuery.isDefaultPurgeOnNoConsumers());
      }
      queueConfiguration.setDurable(durable);
   }

   public static SimpleString getCoreQueueName(ClientSession session, SimpleString destAddress) {
      if (session.getVersion() < PacketImpl.FQQN_CHANGE_VERSION) {
         return destAddress;
      }
      return CompositeAddress.extractQueueName(destAddress);
   }
}
