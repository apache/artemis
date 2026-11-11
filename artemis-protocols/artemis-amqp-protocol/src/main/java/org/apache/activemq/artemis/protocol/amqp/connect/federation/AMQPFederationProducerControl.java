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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import org.apache.activemq.artemis.api.core.management.Attribute;

/**
 * Management interface that is backed by an active federation producer that was created when demand was applied to a
 * matching address or queue on the remote broker that is bringing messages to itself via federation.
 * <p>
 * The federation producer sends messages for the given address or queue to a remote federation consumer.
 */
public interface AMQPFederationProducerControl {

   /**
    * {@return the number of messages this federation producer has sent to the remote}
    */
   @Attribute(desc = "returns the number of messages this federation producer has sent to the remote")
   long getMessagesSent();

   /**
    * {@return the type of federation producer being represented}
    */
   @Attribute(desc = "AMQP federation producer type (address or queue) that backs this instance.")
   String getRole();

   /**
    * Gets the queue name that will be used for this federation producer instance.
    * <p>
    * For Queue federation this will be the name of the queue whose messages are being federated from this server
    * instance to the remote. For an Address federation this will be an automatically generated name that should be
    * unique to a given federation producer instance.
    *
    * @return the queue name associated with the federation producer
    */
   @Attribute(desc = "the queue name associated with the federation producer.")
   String getQueueName();

   /**
    * Gets the address that will be used for this federation producer instance.
    * <p>
    * For Queue federation this is the address under which the matching queue must reside. For Address federation this
    * is the actual subscription address whose messages are being federated from the local address.
    *
    * @return the address associated with this federation producer
    */
   @Attribute(desc = "the address name associated with the federation producer.")
   String getAddress();

   /**
    * Gets the local FQQN that comprises the address and queue where the remote producer will be attached.
    *
    * @return provides the FQQN that can be used to address the producer queue directly
    */
   @Attribute(desc = "the FQQN associated with the federation producer.")
   String getFqqn();

   /**
    * Gets the routing type that will be requested when creating a producer on the local server.
    *
    * @return the routing type of the federation producer
    */
   @Attribute(desc = "the Routing Type associated with the federation producer.")
   String getRoutingType();

   /**
    * Gets the filter string that will be used when creating the federation producer.
    * <p>
    * This is the filter string that is applied to the local subscription used to filter which message are actually sent
    * to the remote broker.
    *
    * @return the filter string that will be used when creating the federation producer
    */
   @Attribute(desc = "the filter string associated with the federation producer.")
   String getFilterString();

   /**
    * Gets the priority value that will be requested for the local subscription that feeds this federation producer with
    * messages to send to the remote.
    *
    * @return the assigned producer priority for the federation producer
    */
   @Attribute(desc = "the assigned priority of the the federation producer.")
   int getPriority();

}
