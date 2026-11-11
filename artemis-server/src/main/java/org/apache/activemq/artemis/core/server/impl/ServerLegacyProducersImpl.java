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
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.utils.collections.MaxSizeMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ServerLegacyProducersImpl implements ServerProducers {

   private static final int MAX_PRODUCER_METRIC_SIZE = 100;

   protected final Map<String, ServerProducer> producers = Collections.synchronizedMap(new MaxSizeMap<>(MAX_PRODUCER_METRIC_SIZE));
   private final String sessionName;

   private final String connectionID;

   public ServerLegacyProducersImpl(ServerSession session) {
      this.sessionName = session.getName();
      this.connectionID = Objects.toString(session.getConnectionID(), null);
   }

   @Override
   public Map<String, ServerProducer> cloneProducers() {
      return new HashMap<>(producers);
   }

   @Override
   public Collection<ServerProducer> getServerProducers() {
      return new ArrayList<>(producers.values());
   }

   @Override
   public ServerProducer getServerProducer(String senderName, Message msg, ServerSessionImpl serverSession) {
      String address = msg.getAddress();
      String name = sessionName + ":" + address;
      ServerProducer producer = producers.get(name);
      if (producer == null) {
         producer = new ServerProducerImpl(name, "CORE", address);
         producer.setSessionID(sessionName);
         producer.setConnectionID(connectionID);
         producers.put(name, producer);
      }
      return producer;
   }

   @Override
   public void put(String id, ServerProducer producer) {
      //never called as we track by address the old way
   }

   @Override
   public void remove(String id) {
      //never called as we track by address the old way
   }

   @Override
   public void clear() {
      producers.clear();
   }

}
