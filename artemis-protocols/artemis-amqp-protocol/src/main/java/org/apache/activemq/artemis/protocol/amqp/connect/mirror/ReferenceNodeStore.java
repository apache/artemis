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
package org.apache.activemq.artemis.protocol.amqp.connect.mirror;

import java.util.HashMap;
import java.util.Map;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.apache.activemq.artemis.utils.collections.NodeStore;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;

public class ReferenceNodeStore implements NodeStore<MessageReference> {

   private final ReferenceIDSupplier idSupplier;

   public ReferenceNodeStore(ReferenceIDSupplier idSupplier) {
      this.idSupplier = idSupplier;
   }

   // This is where the messages are stored by server id...
   Map<String, LongObjectHashMap<LinkedListImpl.Node<MessageReference>>> lists;

   String name;

   String lruListID;
   LongObjectHashMap<LinkedListImpl.Node<MessageReference>> lruMap;

   @Override
   public String toString() {
      return "ReferenceNodeStore{" + "name='" + name + "'}" + "@" + Integer.toHexString(System.identityHashCode(ReferenceNodeStore.this));
   }

   @Override
   public NodeStore<MessageReference> setName(String name) {
      this.name = name;
      return this;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public void storeNode(MessageReference element, LinkedListImpl.Node<MessageReference> node) {
      String list = getServerID(element);
      long id = getID(element);
      storeNode(list, id, node);
   }

   private void storeNode(String serverID, long id, LinkedListImpl.Node<MessageReference> node) {
      LongObjectHashMap<LinkedListImpl.Node<MessageReference>> nodesMap = getMap(serverID);
      if (nodesMap != null) {
         synchronized (nodesMap) {
            LinkedListImpl.Node<MessageReference> previousNode = nodesMap.put(id, node);
            if (previousNode != null) {
               ActiveMQAMQPProtocolLogger.LOGGER.duplicateNodeStoreID(name, serverID, id, new Exception("trace"));
            }
         }
      }
   }

   @Override
   public void removeNode(MessageReference element, LinkedListImpl.Node<MessageReference> node) {
      long id = getID(element);
      String serverID = getServerID(element);
      LongObjectHashMap<LinkedListImpl.Node<MessageReference>> nodeMap = getMap(serverID);
      if (nodeMap != null) {
         synchronized (nodeMap) {
            nodeMap.remove(id);
         }
      }
   }

   @Override
   public LinkedListImpl.Node<MessageReference> getNode(String serverID, long id) {
      LongObjectHashMap<LinkedListImpl.Node<MessageReference>> nodeMap = getMap(serverID);

      assert nodeMap != null;

      synchronized (nodeMap) {
         return nodeMap.get(id);
      }
   }

   /**
    * notice getMap should always return an instance. It should never return null.
    */
   private synchronized LongObjectHashMap<LinkedListImpl.Node<MessageReference>> getMap(String serverID) {
      if (serverID == null) {
         serverID = idSupplier.getDefaultNodeID();
      }

      if (lruListID != null && lruListID.equals(serverID)) {
         return lruMap;
      }

      if (lists == null) {
         lists = new HashMap<>();
      }
      LongObjectHashMap<LinkedListImpl.Node<MessageReference>> theList = lists.get(serverID);
      if (theList == null) {
         theList = new LongObjectHashMap<>();
         lists.put(serverID, theList);
      }

      lruMap = theList; // cached result
      lruListID = serverID;

      return theList;
   }

   public String getServerID(MessageReference element) {
      return idSupplier.getServerID(element);
   }

   public String getServerID(Message message) {
      return idSupplier.getServerID(message);
   }

   public long getID(MessageReference element) {
      return idSupplier.getID(element);
   }

   @Override
   public synchronized void clear() {
      lists.forEach((k, v) -> v.clear());
      lists.clear();
      lruListID = null;
      lruMap = null;
   }

   @Override
   public int size() {
      int size = 0;
      for (LongObjectHashMap mapValue : lists.values()) {
         size += mapValue.size();
      }
      return size;
   }
}