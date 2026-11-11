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
package org.apache.activemq.artemis.core.server.cluster;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeMetrics;
import org.apache.activemq.artemis.core.server.management.NotificationService;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * A Core Bridge
 */
public interface Bridge extends Consumer, ActiveMQComponent {

   SimpleString getName();

   Queue getQueue();

   SimpleString getForwardingAddress();

   void flushExecutor();

   void setNotificationService(NotificationService notificationService);

   RemotingConnection getForwardingConnection();

   void pause() throws Exception;

   void resume() throws Exception;

   /**
    * To be called when the server sent a disconnect to the client. Basically this is for cluster bridges being
    * disconnected
    */
   @Override
   void disconnect();

   boolean isConnected();

   BridgeMetrics getMetrics();

   BridgeConfiguration getConfiguration();
}
