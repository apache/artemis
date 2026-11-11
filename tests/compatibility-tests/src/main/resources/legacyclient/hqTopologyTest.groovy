package legacyclient

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

import org.hornetq.api.core.TransportConfiguration
import org.hornetq.api.core.client.ClientSession
import org.hornetq.api.core.client.HornetQClient
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory
import org.hornetq.core.remoting.impl.netty.TransportConstants

import java.lang.reflect.Field

def printTopology(locator, String label, boolean failIfPreAuth) {
   String preAuthError = null
   try {
      def topology = locator.getTopology()
      def members = topology.getMembers()
      println(label + " topology members: " + members.size())
      for (def member : members) {
         println("  nodeID=" + member.getNodeId() + " live=" + member.getLive() + " backup=" + member.getBackup())
         if (failIfPreAuth) {
            if (member.getLive() != null && "PRE_AUTH_CONNECTOR".equals(member.getLive().getName())) {
               preAuthError = "PRE_AUTH_CONNECTOR found in live for nodeID=" + member.getNodeId()
            }
            if (member.getBackup() != null && "PRE_AUTH_CONNECTOR".equals(member.getBackup().getName())) {
               preAuthError = "PRE_AUTH_CONNECTOR found in backup for nodeID=" + member.getNodeId()
            }
         }
      }
      if (preAuthError != null) {
         throw new Exception(preAuthError)
      }
      return members.size()
   } catch (Exception e) {
      if (preAuthError != null) {
         throw e
      }
      println(label + " Could not inspect topology: " + e.getMessage())
      return 0
   }
}

Map<String, Object> params = new HashMap<String, Object>()
params.put(TransportConstants.HOST_PROP_NAME, "localhost")
params.put(TransportConstants.PORT_PROP_NAME, "61616")
def tc = new TransportConfiguration(NettyConnectorFactory.class.getName(), params)

def locator = HornetQClient.createServerLocatorWithHA(tc)

printTopology(locator, "Before first createSessionFactory:", false)

println("=== Attempting createSessionFactory (subscribes to topology before auth) ===")

def sf = locator.createSessionFactory()
println("createSessionFactory succeeded")

def count = printTopology(locator, "After first createSessionFactory:", false)
if (count > 1) {
   throw new Exception("Topology after first createSessionFactory has " + count1 + " elements, expected at most 1")
}

println("=== Attempting second createSessionFactory (should use topologyArray) ===")
def sf2 = locator.createSessionFactory()
println("second createSessionFactory succeeded")

count = printTopology(locator, "After second createSessionFactory:", false)
if (count > 1) {
   throw new Exception("Topology after second createSessionFactory has " + count2 + " elements, expected at most 1")
}

ClientSession session = sf.createSession("guest", "guest", false, true, true, false, 0)


Thread.sleep(1000);

count = printTopology(locator, "After authorization:", true)
if (count != 2) {
   throw new Exception("Cluster topology is not correctly informed to the client")
}
session.close();
System.out.println("count : " + count)
sf2.close()
sf.close()
locator.close()
return true
