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
package org.apache.activemq.artemis.api.core.management;

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;

import org.apache.activemq.artemis.api.core.JsonUtil;

/**
 * Helper class to create Java Objects from the JSON serialization returned by
 * {@link ActiveMQServerControl#listNetworkTopology()}.
 */
public class NodeInfo {
   private final String id;
   private final String primary;
   private final String backup;

   public String getId() {
      return id;
   }

   public String getPrimary() {
      return primary;
   }

   public String getBackup() {
      return backup;
   }

   /**
    * {@return an array of NodeInfo corresponding to the JSON serialization returned by {@link
    * ActiveMQServerControl#listNetworkTopology()}}
    */
   public static NodeInfo[] from(final String jsonString) throws Exception {
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      NodeInfo[] nodes = new NodeInfo[array.size()];
      for (int i = 0; i < array.size(); i++) {
         JsonObject nodeObject = array.getJsonObject(i);
         NodeInfo role = new NodeInfo(nodeObject.getString("nodeID"), nodeObject.getString("primary", null), nodeObject.getString("backup", null));
         nodes[i] = role;
      }
      return nodes;
   }

   public NodeInfo(String id, String primary, String backup) {
      this.id = id;
      this.primary = primary;
      this.backup = backup;
   }
}
