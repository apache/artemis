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
package org.apache.activemq.artemis.core.protocol.core.impl;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.ResponseHandler;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage_V2;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashMap;
import org.apache.activemq.artemis.utils.collections.ConcurrentLongHashSet;

public class ResponseCache {

   private final AtomicLong sequence = new AtomicLong(0);

   private final ConcurrentLongHashMap<Packet> store;
   private ResponseHandler responseHandler;

   public ResponseCache() {
      this.store = new ConcurrentLongHashMap<>();
   }

   public long nextCorrelationID() {
      return sequence.incrementAndGet();
   }

   public boolean add(Packet packet) {
      this.store.put(packet.getCorrelationID(), packet);
      return true;
   }

   public Packet remove(long correlationID) {
      return store.remove(correlationID);
   }

   public void handleResponse(Packet response) {
      long correlationID = response.getCorrelationID();
      Packet packet = remove(correlationID);
      if (packet != null) {
         responseHandler.handleResponse(packet, response);
      }
   }

   public void errorAll(ActiveMQException exception) {
      ConcurrentLongHashSet keys = store.keysLongHashSet();
      keys.forEach(correlationID -> {
         handleResponse(new ActiveMQExceptionMessage_V2(correlationID, exception));
      });
   }

   public void setResponseHandler(ResponseHandler responseHandler) {
      this.responseHandler = responseHandler;
   }

   public int size() {
      return this.store.size();
   }
}
