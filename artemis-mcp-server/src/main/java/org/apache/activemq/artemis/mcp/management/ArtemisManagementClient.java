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
package org.apache.activemq.artemis.mcp.management;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.mcp.connection.ArtemisConnectionManager;

public final class ArtemisManagementClient {

   private final ManagementInvoker invoker;

   public ArtemisManagementClient(ArtemisConnectionManager connectionManager) {
      this.invoker = new ManagementInvoker(connectionManager);
   }

   public List<String> listQueues() throws Exception {
      return toStringList(invoker.attribute(ResourceNames.BROKER, "queueNames"));
   }

   public List<String> listAddresses() throws Exception {
      return toStringList(invoker.attribute(ResourceNames.BROKER, "addressNames"));
   }

   public Map<String, Object> queueStats(String queueName) throws Exception {
      String resource = ResourceNames.QUEUE + queueName;
      Map<String, Object> stats = new LinkedHashMap<>();
      stats.put("name", queueName);
      stats.put("address", invoker.attribute(resource, "address"));
      stats.put("routingType", invoker.attribute(resource, "routingType"));
      stats.put("durable", invoker.attribute(resource, "durable"));
      stats.put("messageCount", invoker.attribute(resource, "messageCount"));
      stats.put("consumerCount", invoker.attribute(resource, "consumerCount"));
      stats.put("messagesAdded", invoker.attribute(resource, "messagesAdded"));
      stats.put("messagesAcknowledged", invoker.attribute(resource, "messagesAcknowledged"));
      stats.put("messagesExpired", invoker.attribute(resource, "messagesExpired"));
      return stats;
   }

   public Map<String, Object> brokerOverview() throws Exception {
      Map<String, Object> overview = new LinkedHashMap<>();
      overview.put("version", invoker.attribute(ResourceNames.BROKER, "version"));
      overview.put("nodeId", invoker.attribute(ResourceNames.BROKER, "nodeID"));
      overview.put("uptime", invoker.attribute(ResourceNames.BROKER, "uptime"));
      overview.put("active", invoker.attribute(ResourceNames.BROKER, "active"));
      overview.put("totalConnectionCount", invoker.attribute(ResourceNames.BROKER, "totalConnectionCount"));
      overview.put("totalConsumerCount", invoker.attribute(ResourceNames.BROKER, "totalConsumerCount"));
      overview.put("totalMessageCount", invoker.attribute(ResourceNames.BROKER, "totalMessageCount"));
      overview.put("addressMemoryUsage", invoker.attribute(ResourceNames.BROKER, "addressMemoryUsage"));
      overview.put("queueCount", listQueues().size());
      overview.put("addressCount", listAddresses().size());
      return overview;
   }

   private static List<String> toStringList(Object result) {
      List<String> names = new ArrayList<>();
      if (result instanceof Object[] array) {
         for (Object element : array) {
            if (element != null) {
               names.add(element.toString());
            }
         }
      }
      return names;
   }
}
