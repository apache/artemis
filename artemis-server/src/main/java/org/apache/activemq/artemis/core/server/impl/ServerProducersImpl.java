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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerProducersImpl implements ServerProducers {
   protected final Map<String, ServerProducer> producers = new ConcurrentHashMap<>();

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
      if (senderName != null) {
         return producers.get(senderName);
      }
      return null;
   }

   @Override
   public void put(String id, ServerProducer producer) {
      producers.put(id, producer);
   }

   @Override
   public void remove(String id) {
      producers.remove(id);
   }

   @Override
   public void clear() {
      producers.clear();
   }
}
