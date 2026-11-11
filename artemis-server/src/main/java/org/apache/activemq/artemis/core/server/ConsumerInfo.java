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
package org.apache.activemq.artemis.core.server;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;

public interface ConsumerInfo {

   /**
    * an unique sequential ID for this consumer
    */
   long getSequentialID();

   /**
    * {@return name of the queue that is being consumed}
    */
   SimpleString getQueueName();

   /**
    * {@return routing type of the queue that is being consumed}
    */
   RoutingType getQueueType();

   /**
    * {@return address of the queue that is being consumed}
    */
   SimpleString getQueueAddress();

   SimpleString getFilterString();

   String getSessionName();

   String getConnectionClientID();

   /**
    * {@return the name of the protocol for this Remoting Connection}
    */
   String getConnectionProtocolName();

   /**
    * {@return a string representation of the local IP address this connection is connected to; useful when the server
    * is configured at {@code 0.0.0.0} (or multiple IPs)}
    */
   String getConnectionLocalAddress();

   /**
    * {@return a string representation of the remote address this connection is connected to}
    */
   String getConnectionRemoteAddress();

   /**
    * {@return how many messages are out for delivery but not yet acknowledged}
    */
   int getMessagesInTransit();

   /**
    * {@return the combined size of all the messages out for delivery but not yet acknowledged}
    */
   long getMessagesInTransitSize();

   /**
    * {@return the total number of messages sent to a consumer including redeliveries that have been acknowledged}
    */
   long getMessagesDelivered();

   /**
    * {@return the total size of all the messages delivered to the consumer including redelivered messages}
    */
   long getMessagesDeliveredSize();

   /**
    * {@return the number of messages acknowledged by this consumer since it was created}
    */
   long getMessagesAcknowledged();

   /**
    * {@return the number of acknowledged messages that are awaiting commit an a transaction}
    */
   int getMessagesAcknowledgedAwaitingCommit();

   /**
    * {@return the time in milliseconds that the last message was delivered to a consumer}
    */
   long getLastDeliveredTime();

   /**
    * {@return the time in milliseconds that the last message was acknowledged by a consumer}
    */
   long getLastAcknowledgedTime();
}