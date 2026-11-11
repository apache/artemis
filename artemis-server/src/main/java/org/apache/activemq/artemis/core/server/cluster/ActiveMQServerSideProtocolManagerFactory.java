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

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.protocol.ServerPacketDecoder;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQClientProtocolManager;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketDecoder;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManagerFactory;

/**
 * A protocol manager that will replace the packet manager for inter-server communications
 */
public class ActiveMQServerSideProtocolManagerFactory implements ClientProtocolManagerFactory {

   ServerLocator locator;

   final StorageManager storageManager;

   private ActiveMQServerSideProtocolManagerFactory(StorageManager storageManager) {
      this.storageManager = storageManager;
   }

   @Override
   public ServerLocator getLocator() {
      return locator;
   }

   @Override
   public ClientProtocolManagerFactory setLocator(ServerLocator locator) {
      this.locator = locator;
      return this;
   }

   public static ActiveMQServerSideProtocolManagerFactory getInstance(ServerLocator locator, StorageManager storageManager) {
      ActiveMQServerSideProtocolManagerFactory instance = new ActiveMQServerSideProtocolManagerFactory(storageManager);
      instance.setLocator(locator);
      return instance;
   }

   @Override
   public TransportConfiguration adaptTransportConfiguration(TransportConfiguration tc) {
      return tc;
   }

   @Override
   public ClientProtocolManager newProtocolManager() {
      return new ActiveMQReplicationProtocolManager();
   }

   class ActiveMQReplicationProtocolManager extends ActiveMQClientProtocolManager {

      @Override
      protected PacketDecoder createPacketDecoder() {
         return new ServerPacketDecoder(storageManager);
      }
   }
}
