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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.ByteUtil;

/**
 * Tracks MQTT packet identifiers using a {@link DuplicateIDCache} to provide durable, duplicate-safe tracking across
 * broker restarts for QoS2 message flows.
 * <p>
 * QoS1 flows do not use this cache.
 */
public class PacketIdCache {

   private DuplicateIDCache cache;
   private MQTTSession session;
   private PostOffice postOffice;
   private final SimpleString cacheName;

   public PacketIdCache(MQTTSession session, TYPE type) {
      this.session = session;
      this.postOffice = session.getServer().getPostOffice();
      this.cacheName = getCacheName(session.getServer().getInternalNamingPrefix(), session.getState().getClientId(), type);
   }

   /*
    * "Getting" the cache from the PostOffice automatically creates it if it doesn't exist. We want to avoid this for
    * most operations.
    */
   private void check() {
      if (cache == null && durableStateExists()) {
         cache = postOffice.getDuplicateIDCache(cacheName, MQTTUtil.TWO_BYTE_INT_MAX);
      }
   }

   private boolean durableStateExists() {
      return postOffice.duplicateIDCacheExists(cacheName);
   }

   public void add(int packetId, Transaction tx) throws Exception {
      if (cache == null) {
         cache = postOffice.getDuplicateIDCache(cacheName, MQTTUtil.TWO_BYTE_INT_MAX);
      }
      cache.addToCache(ByteUtil.intToBytes(packetId), tx);
   }

   public boolean contains(int packetId) {
      check();
      return cache != null && cache.contains(ByteUtil.intToBytes(packetId));
   }

   public boolean remove(int packetId) throws Exception {
      check();
      return cache != null && cache.deleteFromCache(ByteUtil.intToBytes(packetId));
   }

   public int size() {
      check();
      return cache != null ? cache.getMap().size() : 0;
   }

   public List<Integer> getPacketIds() {
      check();
      if (cache == null) {
         return Collections.emptyList();
      }
      List<Integer> result = new ArrayList<>();
      for (Pair<byte[], Long> entry : cache.getMap()) {
         result.add(ByteUtil.bytesToInt(entry.getA()));
      }
      return result;
   }

   public void clear() throws Exception {
      postOffice.deleteDuplicateCache(cacheName);
      cache = null;
   }

   public static SimpleString getCacheName(String prefix, String clientId, TYPE type) {
      return SimpleString.of(prefix).concat("mqtt.qos.").concat(type.type).concat('.').concat(clientId);
   }

   public enum TYPE {
      PUBLISH(SimpleString.of("publish")),
      PUBREC(SimpleString.of("pubrec"));

      final SimpleString type;

      TYPE(SimpleString type) {
         this.type = type;
      }
   }
}
