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
package org.apache.activemq.artemis.core.server.routing.pools;

import org.apache.activemq.artemis.api.core.TransportConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MockDiscoveryService extends DiscoveryService {
   private final Map<String, Entry> entries = new HashMap<>();

   private final Map<String, Entry> pendingEntries = new HashMap<>();

   private volatile boolean started;


   public Map<String, Entry> getEntries() {
      return entries;
   }

   public Map<String, Entry> getPendingEntries() {
      return pendingEntries;
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   public Entry addEntry() {
      return addEntry(new Entry(UUID.randomUUID().toString(), new TransportConfiguration()));
   }

   public Entry addEntry(Entry entry) {
      if (started) {
         entries.put(entry.getNodeID(), entry);
         fireEntryAddedEvent(entry);
      } else {
         pendingEntries.put(entry.getNodeID(), entry);
      }

      return entry;
   }

   public Entry removeEntry(String nodeID) {
      if (started) {
         Entry removedEntry = entries.remove(nodeID);
         fireEntryRemovedEvent(removedEntry);
         return removedEntry;
      } else {
         return pendingEntries.remove(nodeID);
      }
   }


   @Override
   public void start() throws Exception {
      started = true;

      pendingEntries.forEach((nodeID, entry) -> {
         entries.put(nodeID, entry);
         fireEntryAddedEvent(entry);
      });
   }

   @Override
   public void stop() throws Exception {
      started = false;
   }
}
