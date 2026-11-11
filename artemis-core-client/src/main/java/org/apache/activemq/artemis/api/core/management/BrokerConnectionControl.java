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
package org.apache.activemq.artemis.api.core.management;

/**
 * An API for a BrokerConnectionControl object that is used to view and manage BrokerConnection state.
 */
public interface BrokerConnectionControl extends ActiveMQComponentControl {

   /**
    * {@return if this broker connection is currently connected to the remote}
    */
   @Attribute(desc = "whether this broker connection is currently connected to the remote")
   boolean isConnected();

   /**
    * {@return the name of this broker connection}
    */
   @Attribute(desc = "name of this broker connection")
   String getName();

   /**
    * {@return the connection URI for this broker connection}
    */
   @Attribute(desc = "connection URI for this broker connection")
   String getUri();

   /**
    * {@return the user this broker connection is using}
    */
   @Attribute(desc = "the user this broker connection is using")
   String getUser();

   /**
    * {@return the wire protocol this broker connection is using}
    */
   @Attribute(desc = "the wire protocol this broker connection is using")
   String getProtocol();

   /**
    * {@return the retry interval configured for this broker connection}
    */
   @Attribute(desc = "Configured retry interval of this broker connection")
   long getRetryInterval();

   /**
    * {@return the number of reconnection attempts configured for this broker connection}
    */
   @Attribute(desc = "Configured number of reconnection attempts of this broker connection")
   int getReconnectAttempts();

}
