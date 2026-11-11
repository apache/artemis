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
package org.apache.activemq.artemis.core.protocol.mqtt;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.remoting.FailureListener;

/**
 * Registered with the server and called during connection failure.  This class informs the ConnectionManager when a
 * connection failure has occurred, which subsequently cleans up any connection data.
 */
public class MQTTFailureListener implements FailureListener {

   private MQTTConnectionManager connectionManager;

   public MQTTFailureListener(MQTTConnectionManager connectionManager) {
      this.connectionManager = connectionManager;
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver) {
      connectionFailed(exception, failedOver, null);
   }

   @Override
   public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
      connectionManager.disconnect(true);
   }
}
