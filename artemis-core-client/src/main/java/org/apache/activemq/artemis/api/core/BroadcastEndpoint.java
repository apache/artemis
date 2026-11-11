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
package org.apache.activemq.artemis.api.core;

import java.util.concurrent.TimeUnit;

/**
 * BroadcastEndpint is used in BroadcastGroups and DiscoveryGroups for topology updates.
 * <p>
 * A BroadcastEndpoint can perform one of the two following tasks:
 * <ul>
 * <li>when being used in BroadcastGroups, it broadcasts connector informations</li>
 * <li>when being used in DiscoveryGroups, it receives broadcasts</li>
 * </ul>
 * <p>
 * The two tasks are mutual exclusive, meaning a BroadcastEndpoint can either be a broadcaster
 * or a receiver, but not both.
 * <p>
 * It is an abstraction of various concrete broadcasting mechanisms. Different implementations
 * of this interface may use different broadcasting techniques like UDP multicasting or
 * JGroups channels.
 *
 * @see JGroupsBroadcastEndpoint
 */
public interface BroadcastEndpoint {

   /**
    * This method initializes a BroadcastEndpoint as a receiving end for broadcasts. After that data can be received
    * using one of its receiveBroadcast() methods.
    */
   void openClient() throws Exception;

   /**
    * This method initializes a BroadcastEndpint as a broadcaster. After that data can be sent via its broadcast()
    * method.
    */
   void openBroadcaster() throws Exception;

   /**
    * Close the endpoint. Any related resources should be cleaned up in this method.
    *
    * @param isBroadcast : indicates whether this endpoint serves as a broadcast or not.
    */
   void close(boolean isBroadcast) throws Exception;

   /**
    * Broadcasting data to the cluster.
    *
    * @param data : a byte array containing the data.
    */
   void broadcast(byte[] data) throws Exception;

   /**
    * Receives the broadcast data. It blocks until data is
    * available.
    *
    * @return the received data as byte array
    */
   byte[] receiveBroadcast() throws Exception;

   /**
    * Receives the broadcast data with a timeout. It blocks until either the data is available or the timeout is
    * reached, whichever comes first.
    *
    * @param time : how long the method should wait for the data to arrive.
    * @param unit : unit of the time.
    * @return a byte array if data is arrived within the timeout, or null if no data is available after the timeout.
    */
   byte[] receiveBroadcast(long time, TimeUnit unit) throws Exception;
}
