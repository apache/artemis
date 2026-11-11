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

package org.apache.activemq.artemis.cli.commands.check;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonString;

public class ClusterNodeVerifier implements AutoCloseable {

   final String uri, user, password;

   final SimpleManagement simpleManagement;

   final long allowedVariance;

   public ClusterNodeVerifier(String uri, String user, String password) {
      this(uri, user, password, 1000);
   }

   public ClusterNodeVerifier(String uri, String user, String password, long variance) {
      this.uri = uri;
      this.user = user;
      this.password = password;
      this.allowedVariance = variance;
      this.simpleManagement = new SimpleManagement(uri, user, password);
   }

   @Override
   public void close() throws Exception {
      simpleManagement.close();
   }

   public ClusterNodeVerifier open() throws Exception {
      simpleManagement.open();
      return this;
   }

   public boolean verify(ActionContext context) throws Exception {
      String mainID = getNodeID();
      JsonArray mainToplogy = fetchMainTopology();

      AtomicBoolean verificationResult = new AtomicBoolean(true);

      Map<String, TopologyItem> mainTopology = parseTopology(mainToplogy);
      boolean supportTime = true;
      try {
         fetchTopologyTime(mainTopology);
      } catch (Exception e) {
         supportTime = false;
      }

      if (supportTime) {
         verifyTime(context, mainTopology, verificationResult, supportTime);
      } else {
         context.out.println("*******************************************************************************************************************************");
         context.out.println("Topology on " + uri + " nodeID=" + mainID + " with " + mainToplogy.size() + " nodes :");
         printTopology(context, "", mainToplogy);
         context.out.println("*******************************************************************************************************************************");
      }

      mainTopology.forEach((a, b) -> {
         try {
            context.out.println("--> Verifying Topology for NodeID " + b.nodeID + ", primary = " + b.primary + ", backup = " + b.backup);
            if (b.primary != null) {
               context.out.println("   verification on primary " + b.primary);
               if (!subVerify(context, b.primary, mainTopology)) {
                  verificationResult.set(false);
               } else {
                  context.out.println("   ok!");
               }
            }
         } catch (Exception e) {
            e.printStackTrace(context.out);
            verificationResult.set(false);
         }
      });

      return verificationResult.get();
   }

   protected void verifyTime(ActionContext context,
                             Map<String, TopologyItem> mainTopology,
                             AtomicBoolean verificationResult,
                             boolean supportTime) {

      final String FORMAT = "%-40s | %-25s | %-19s | %-25s";
      context.out.println("*******************************************************************************************************************************");

      if (supportTime) {
         Long[] times = fetchTopologyTime(mainTopology);

         context.out.printf(FORMAT, "nodeID", "primary", "primary local time", "backup");
         context.out.println();
         SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

         long initialTime = System.currentTimeMillis();

         mainTopology.forEach((id, node) -> {
            context.out.printf(FORMAT, id, node.primary, formatDate(sdf, node.primaryTime), node.backup);
            context.out.println();
         });

         // how long it took to fetch the times. I'm adding this to the allowed variance.
         long latencyTime = System.currentTimeMillis() - initialTime;

         long min = Long.MAX_VALUE, max = Long.MIN_VALUE;

         for (long l : times) {

            if (l < min) {
               min = l;
            }

            if (l > max) {
               max = l;
            }
         }

         long variance = times.length > 0 ? (max - min) : 0;

         long allowedVarianceWithLatency = allowedVariance + latencyTime;

         if (variance < allowedVarianceWithLatency) {
            context.out.println("Time variance in the cluster is " + variance + " milliseconds");
         } else {
            context.out.println("WARNING: Time variance in the cluster is greater than " + allowedVarianceWithLatency + " milliseconds: " + variance + ". Please verify your server's NTP configuration.");
            verificationResult.set(false);
         }
      } else {
         context.out.println("The current management version does not support the getCurrentTimeMillis() method. Please verify whether your server's times are in sync and whether they are using NTP.");
      }
      context.out.println("*******************************************************************************************************************************");
   }

   String formatDate(SimpleDateFormat sdf, long time) {
      if (time == 0) {
         return "";
      } else {
         return sdf.format(new Date(time));
      }
   }

   protected Long[] fetchTopologyTime(Map<String, TopologyItem> topologyItemMap) {
      List<Long> times = new ArrayList<>(topologyItemMap.size() * 2);
      topologyItemMap.forEach((id, node) -> {
         if (node.primary != null) {
            try {
               node.primaryTime = fetchTime(node.primary);
               times.add(node.primaryTime);
            } catch (Exception e) {
               ActionContext.system().err.println("Cannot fetch liveTime for nodeID=" + id + ", url=" + node.primary + " -> " + e.getMessage());
               node.primaryTime = 0;
            }
         }
      });

      return times.toArray(new Long[times.size()]);
   }

   private boolean subVerify(ActionContext context,
                             String uri,
                             Map<String, TopologyItem> mainTopology) throws Exception {
      JsonArray verifyTopology = fetchTopology(uri);
      Map<String, TopologyItem> verifyTopologyMap = parseTopology(verifyTopology);
      String result = compareTopology(mainTopology, verifyTopologyMap);
      if (result != null) {
         context.out.println(result);
         context.out.println("    Topology detailing for " + uri);
         printTopology(context, "    ", verifyTopology);
         return false;
      } else {
         return true;
      }
   }

   public String compareTopology(Map<String, TopologyItem> mainTopology, Map<String, TopologyItem> compareTopology) {
      if (mainTopology.size() != compareTopology.size()) {
         return "main topology size " + mainTopology.size() + "!= compareTopology size " + compareTopology.size();
      }

      int matchElements = 0;

      for (Map.Entry<String, TopologyItem> entry : mainTopology.entrySet()) {
         TopologyItem item = compareTopology.get(entry.getKey());
         if (!item.equals(entry.getValue())) {
            return "Topology mistmatch on " + item;
         } else {
            matchElements++;
         }
      }

      if (matchElements != mainTopology.size()) {
         return "Not all elements match!";
      }

      return null;

   }

   Map<String, TopologyItem> parseTopology(JsonArray topology) {
      Map<String, TopologyItem> map = new LinkedHashMap<>();
      navigateTopology(topology, t -> map.put(t.nodeID, t));
      return map;
   }

   private void printTopology(ActionContext context, String prefix, JsonArray topology) {
      context.out.printf(prefix + "%-40s | %-25s | %-25s", "nodeID", "live", "backup");
      context.out.println();
      navigateTopology(topology, t -> {
         context.out.printf(prefix + "%-40s | %-25s | %-25s", t.nodeID, t.primary, t.backup);
         context.out.println();
      });
   }

   private void navigateTopology(JsonArray topology, Consumer<TopologyItem> consumer) {
      for (int i = 0; i < topology.size(); i++) {
         JsonObject node = topology.getJsonObject(i);
         JsonString primary = node.getJsonString("primary");
         JsonString backup = node.getJsonString("backup");
         String nodeID = node.getString("nodeID");
         TopologyItem item = new TopologyItem(nodeID, primary != null ? primary.getString() : null, backup != null ? backup.getString() : null);
         consumer.accept(item);
      }
   }

   protected String getNodeID() throws Exception {
      return simpleManagement.getNodeID();
   }

   protected long fetchMainTime() throws Exception {
      return simpleManagement.getCurrentTimeMillis();
   }

   protected long fetchTime(String uri) throws Exception {
      SimpleManagement management = new SimpleManagement(uri, user, password);
      return management.getCurrentTimeMillis();
   }

   protected JsonArray fetchMainTopology() throws Exception {
      return simpleManagement.listNetworkTopology();
   }

   protected JsonArray fetchTopology(String uri) throws Exception {
      SimpleManagement management = new SimpleManagement(uri, user, password);
      return management.listNetworkTopology();
   }

   public static class TopologyItem {

      final String nodeID, primary, backup;

      long primaryTime, backupTime;

      TopologyItem(String nodeID, String primary, String backup) {
         this.nodeID = nodeID;
         if (primary != null) {
            this.primary = "tcp://" + primary;
         } else {
            this.primary = null;
         }
         if (backup != null) {
            this.backup = "tcp://" + backup;
         } else {
            this.backup = null;
         }
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof TopologyItem other)) {
            return false;
         }

         return Objects.equals(nodeID, other.nodeID) &&
                Objects.equals(primary, other.primary) &&
                Objects.equals(backup, other.backup);
      }

      @Override
      public int hashCode() {
         return Objects.hash(nodeID, primary, backup);
      }

      @Override
      public String toString() {
         return "TopologyItem{" + "nodeID='" + nodeID + '\'' + ", primary='" + primary + '\'' + ", backup='" + backup + '\'' + '}';
      }
   }
}
