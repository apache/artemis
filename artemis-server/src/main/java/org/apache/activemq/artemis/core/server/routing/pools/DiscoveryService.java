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
import org.apache.activemq.artemis.core.server.ActiveMQComponent;

public abstract class DiscoveryService implements ActiveMQComponent {

   private Listener listener;

   public Listener getListener() {
      return listener;
   }

   public void setListener(Listener listener) {
      this.listener = listener;
   }

   protected void fireEntryAddedEvent(Entry entry) {
      if (listener != null) {
         this.listener.entryAdded(entry);
      }
   }

   protected void fireEntryRemovedEvent(Entry entry) {
      if (listener != null) {
         this.listener.entryRemoved(entry);
      }
   }

   protected void fireEntryUpdatedEvent(Entry oldEntry, Entry newEntry) {
      if (listener != null) {
         this.listener.entryUpdated(oldEntry, newEntry);
      }
   }


   public interface Listener {
      void entryAdded(Entry entry);

      void entryRemoved(Entry entry);

      void entryUpdated(Entry oldEntry, Entry newEntry);
   }

   public class Entry {
      private final String nodeID;
      private final TransportConfiguration connector;

      public String getNodeID() {
         return nodeID;
      }

      public TransportConfiguration getConnector() {
         return connector;
      }

      public Entry(String nodeID, TransportConfiguration connector) {
         this.nodeID = nodeID;
         this.connector = connector;
      }

      @Override
      public String toString() {
         StringBuilder stringBuilder = new StringBuilder(Entry.class.getSimpleName());
         stringBuilder.append("(nodeID=" + nodeID);
         stringBuilder.append(", connector=" + connector);
         stringBuilder.append(") ");
         return stringBuilder.toString();
      }
   }
}
