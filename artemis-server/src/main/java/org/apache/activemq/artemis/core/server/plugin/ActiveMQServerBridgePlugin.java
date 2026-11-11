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
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.server.HandleStatus;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.cluster.Bridge;

public interface ActiveMQServerBridgePlugin extends ActiveMQServerBasePlugin {

   /**
    * Before a bridge is deployed
    *
    * @param config The bridge configuration
    */
   default void beforeDeployBridge(BridgeConfiguration config) throws ActiveMQException {

   }

   /**
    * After a bridge has been deployed
    *
    * @param bridge The newly deployed bridge
    */
   default void afterDeployBridge(Bridge bridge) throws ActiveMQException {

   }

   /**
    * Called immediately before a bridge delivers a message
    */
   default void beforeDeliverBridge(Bridge bridge, MessageReference ref) throws ActiveMQException {

   }

   /**
    * Called immediately after a bridge delivers a message but before the message is acknowledged
    */
   default void afterDeliverBridge(Bridge bridge, MessageReference ref, HandleStatus status) throws ActiveMQException {

   }

   /**
    * Called after delivered message over this bridge has been acknowledged by the remote broker
    */
   default void afterAcknowledgeBridge(Bridge bridge, MessageReference ref) throws ActiveMQException {

   }
}
