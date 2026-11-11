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
 * Management interface that is backed by an active bridge sender
 * that was created when demand was applied to a matching address or queue
 * on the remote broker that is bringing messages to itself via bridge.
 *
 * The bridge sender sends messages for the given address or queue to
 * a remote bridge sender.
 */
public interface AMQPBridgeSenderControl {

   /**
    * {@return the number of messages this bridge sender has sent to the remote}
    */
   @Attribute(desc = "returns the number of messages this bridge sender has sent to the remote")
   long getMessagesSent();

   /**
    * {@return the type of bridge sender being represented}
    */
   @Attribute(desc = "AMQP bridge sender type (address or queue) that backs this instance.")
   String getRole();

   /**
    * Gets the queue name that will be used for this bridge sender instance.
    *
    * For Queue bridge this will be the name of the queue whose messages are
    * being bridged from this server instance to the remote. For an Address bridge
    * this will be an automatically generated name that should be unique to a given
    * bridge sender instance.
    *
    * @return the queue name associated with the bridge sender
    */
   @Attribute(desc = "the queue name associated with the bridge sender.")
   String getQueueName();

   /**
    * Gets the address that will be used for this bridge sender instance.
    *
    * For Queue bridge this is the address under which the matching queue must
    * reside. For Address bridge this is the actual subscription address whose
    * messages are being bridged from the local address.
    *
    * @return the address associated with this bridge sender.
    */
   @Attribute(desc = "the address name associated with the bridge sender.")
   String getAddress();

   /**
    * Gets the local FQQN that comprises the address and queue where the remote sender
    * will be attached.
    *
    * @return provides the FQQN that can be used to address the sender queue directly.
    */
   @Attribute(desc = "the FQQN associated with the bridge sender.")
   String getFqqn();

   /**
    * Gets the routing type that will be requested when creating a sender on the
    * local server.
    *
    * @return the routing type of the bridge sender.
    */
   @Attribute(desc = "the Routing Type associated with the bridge sender.")
   String getRoutingType();

   /**
    * Gets the filter string that will be used when creating the bridge sender.
    *
    * This is the filter string that is applied to the local subscription used to filter
    * which message are actually sent to the remote broker.
    *
    * @return the filter string in use for the bridge sender.
    */
   @Attribute(desc = "the filter string associated with the bridge sender.")
   String getFilterString();

   /**
    * Gets the priority value that will be requested for the local subscription that feeds
    * this bridge sender with messages to send to the remote.
    *
    * @return the assigned sender priority for the bridge sender.
    */
   @Attribute(desc = "the assigned priority of the bridge sender.")
   int getPriority();

}
