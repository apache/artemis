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
package org.apache.activemq.artemis.protocol.amqp.federation;

import org.apache.activemq.artemis.api.core.RoutingType;

/**
 * Information and identification interface for Federation consumers that will be created on the remote broker as demand
 * on the local broker is detected.  The behavior and meaning of some APIs in this interface may vary slightly depending
 * on the role of the consumer (Address or Queue).
 */
public interface FederationConsumerInfo {

   enum Role {
      /**
       * Consumer created from a match on a configured address federation policy.
       */
      ADDRESS_CONSUMER,

      /**
       * Consumer created from a match on a configured queue federation policy.
       */
      QUEUE_CONSUMER
   }

   /**
    * {@return a unique Id for the consumer being represented}
    */
   String getId();

   /**
    * {@return the type of federation consumer being represented}
    */
   Role getRole();

   /**
    * Gets the queue name that will be used for this federation consumer instance.
    * <p>
    * For Queue federation this will be the name of the queue whose messages are being federated to this server
    * instance. For an Address federation this will be an automatically generated name that should be unique to a given
    * federation instance
    *
    * @return the queue name associated with the federation consumer
    */
   String getQueueName();

   /**
    * Gets the address that will be the source of messages for the federation consumer instance.
    * <p>
    * For Queue federation this is the address under which the matching queue must reside. For Address federation this
    * is the actual address whose messages are being federated from on the remote broker..
    *
    * @return the source address associated with this federation consumer
    */
   String getSourceAddress();

   /**
    * Gets the address that will be the target of messages for the federation consumer instance.
    * <p>
    * For Queue federation this is the address under which the matching queue must reside. For Address federation this
    * is the actual address whose messages are being federated to on the local broker.
    *
    * @return the target address associated with this federation consumer
    */
   String getTargetAddress();

   /**
    * Gets the FQQN that comprises the address and queue where the remote consumer will be attached.
    *
    * @return provides the FQQN that can be used to address the remote consumer queue directly
    */
   String getSourceFqqn();

   /**
    * Gets the FQQN that comprises the address and queue where the local sender will be attached..
    *
    * @return provides the FQQN that can be used to address the local sender queue directly
    */
   String getTargetFqqn();

   /**
    * Gets the routing type that will be requested when creating a consumer on the remote server.
    *
    * @return the routing type of the remote consumer
    */
   RoutingType getRoutingType();

   /**
    * Gets the filter string that will be used when creating the remote consumer.
    * <p>
    * For Queue federation this will be the filter that exists on the local queue that is requesting federation of
    * messages from the remote. For address federation this filter will be used to restrict some movement of messages
    * amongst federated server addresses.
    *
    * @return the filter string in use for the federation consumer
    */
   String getFilterString();

   /**
    * Gets the priority value that will be requested for the remote consumer that is created.
    *
    * @return the assigned consumer priority for the federation consumer
    */
   int getPriority();

}
