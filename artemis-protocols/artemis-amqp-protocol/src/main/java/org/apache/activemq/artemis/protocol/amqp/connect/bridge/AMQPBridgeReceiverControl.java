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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import org.apache.activemq.artemis.api.core.management.Attribute;

/**
 * Management interface that is backed by an active bridge receiver
 * that was created when demand was applied to a matching address or queue.
 */
public interface AMQPBridgeReceiverControl {

   /**
    * {@return the number of messages this bridge receiver has received from the remote}
    */
   @Attribute(desc = "returns the number of messages this bridge receiver has received from the remote")
   long getMessagesReceived();

   /**
    * {@return the type of bridge receiver being represented}
    */
   @Attribute(desc = "AMQP bridge receiver type (address or queue) that backs this instance.")
   String getRole();

   /**
    * Gets the queue name that will be used for this bridge receiver instance.
    *
    * For Queue bridge this will be the name of the queue whose messages are
    * being bridged to this server instance. For an Address bridge this will
    * be an automatically generated name that should be unique to a given bridge
    * instance
    *
    * @return the queue name associated with the bridge receiver
    */
   @Attribute(desc = "the queue name associated with the bridge receiver.")
   String getQueueName();

   /**
    * Gets the address that will be used for this bridge receiver instance.
    *
    * For Queue bridge this is the address under which the matching queue must
    * reside. For Address bridge this is the actual address whose messages are
    * being bridged.
    *
    * @return the address associated with this bridge receiver.
    */
   @Attribute(desc = "the address name associated with the bridge receiver.")
   String getAddress();

   /**
    * Gets the FQQN that comprises the address and queue where the remote receiver
    * will be attached.
    *
    * @return provides the FQQN that can be used to address the receiver queue directly.
    */
   @Attribute(desc = "the FQQN associated with the bridge receiver.")
   String getFqqn();

   /**
    * Gets the routing type that will be requested when creating a receiver on the
    * remote server.
    *
    * @return the routing type of the remote receiver.
    */
   @Attribute(desc = "the Routing Type associated with the bridge receiver.")
   String getRoutingType();

   /**
    * Gets the filter string that will be used when creating the remote receiver.
    * <p>
    * For a queue bridge the filter is selected from configuration if one was set or
    * if the consumer that generated to demand that creates the bridge carries a filter
    * that value is chosen, otherwise any filter that is set on the bridged queue is
    * chosen.
    * <p>
    * For an address bridge the filter only exists if one was set in the bridge from address
    * policy configuration.
    *
    * @return the filter string in use for the bridge receiver.
    */
   @Attribute(desc = "the filter string associated with the bridge receiver.")
   String getFilterString();

   /**
    * Gets the priority value that will be requested for the remote receiver that is
    * created.
    *
    * @return the assigned receiver priority for the bridge receiver.
    */
   @Attribute(desc = "the assigned priority of the bridge receiver.")
   int getPriority();

}
