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
 * An API for a RemoteBrokerConnectionControl object that is used to view information about active remote broker
 * connections.
 */
public interface RemoteBrokerConnectionControl {

   /**
    * {@return the name of the remote broker connection}
    */
   @Attribute(desc = "name of the remote broker connection")
   String getName();

   /**
    * {@return the Node ID of the remote broker connection}
    */
   @Attribute(desc = "Node ID of the remote broker connection")
   String getNodeId();

   /**
    * {@return the wire protocol this broker connection is using}
    */
   @Attribute(desc = "the wire protocol this broker connection is using")
   String getProtocol();

}
