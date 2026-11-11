package multiVersionCluster
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

// Wait for cluster to form - should see 2 members
def timeout = System.currentTimeMillis() + 10000
def formed = false

while (System.currentTimeMillis() < timeout && !formed) {
    def clusterManager = theBroker1.getActiveMQServer().getClusterManager()
    def clusterConnection = clusterManager.getClusterConnection("my-cluster")
    if (clusterConnection != null) {
        def topology = clusterConnection.getTopology()
        if (topology != null && topology.getMembers().size() == 2) {
            formed = true
            break
        }
    }
    Thread.sleep(100)
}

if (!formed) {
    throw new RuntimeException("Broker1: Cluster topology did not form in time")
}

println("Broker1: Cluster topology formed with " +
    theBroker1.getActiveMQServer().getClusterManager().getClusterConnection("my-cluster").getTopology().getMembers().size() +
    " members")
