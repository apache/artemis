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

package org.apache.activemq.cli.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.check.ClusterNodeVerifier;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;

public class ClusterVerifyTest {

   private static JsonArray read(String jsonString) throws Exception {
      return JsonUtil.readJsonArray(jsonString);
   }

   @Test
   public void testClusterVerifyError() throws Exception {
      internalVerify(true);
      internalVerify(false);
   }

   private void internalVerify(boolean fail) throws Exception {

      // This is returning the following string:
      // for main topology: [{"nodeID":"A1","live":"A:1", "backup":"B:1"}, {"nodeID":"A2","live":"A:2", "backup":"B:2"}, {"nodeID":"A3","live":"A:3", "backup":"B:3"}]
      // A:1 topology:[{"nodeID":"A1","live":"A:1", "backup":"B:1"}, {"nodeID":"A2","live":"A:2", "backup":"B:2"}, {"nodeID":"A3","live":"A:3", "backup":"B:3"}]
      // A:2 topology:[{"nodeID":"A1","live":"A:1"}, {"nodeID":"A2","live":"A:2"}, {"nodeID":"A3","live":"A:3"}]
      // A:3 topology:[{"nodeID":"A1","live":"A:1", "backup":"B:1"}, {"nodeID":"A2","live":"A:2", "backup":"B:2"}] [
      ClusterNodeVerifier fakeVerifier = new ClusterNodeVerifier("fake", "a", "b") {
         @Override
         protected void verifyTime(ActionContext context,
                                   Map<String, ClusterNodeVerifier.TopologyItem> mainTopology,
                                   AtomicBoolean verificationResult,
                                   boolean supportTime) {
            // not doing it
         }

         @Override
         protected Long[] fetchTopologyTime(Map<String, TopologyItem> topologyItemMap) {
            throw new NotImplementedException("not doing it");
         }

         @Override
         protected long fetchMainTime() throws Exception {
            throw new NotImplementedException("not doing it");
         }

         @Override
         protected long fetchTime(String uri) throws Exception {
            return super.fetchTime(uri);
         }

         @Override
         protected String getNodeID() {
            return "AA";
         }

         @Override
         protected JsonArray fetchMainTopology() throws Exception {
            return read("[{\"nodeID\":\"A1\",\"primary\":\"A:1\", \"backup\":\"B:1\"}, {\"nodeID\":\"A2\",\"primary\":\"A:2\", \"backup\":\"B:2\"}, {\"nodeID\":\"A3\",\"primary\":\"A:3\", \"backup\":\"B:3\"}]");
         }

         @Override
         protected JsonArray fetchTopology(String uri) throws Exception {
            if (fail) {
               return switch (uri) {
                  case "tcp://A:1", "tcp://B:1" ->
                     read("[{\"nodeID\":\"A1\",\"primary\":\"A:1\", \"backup\":\"B:1\"}, {\"nodeID\":\"A2\",\"primary\":\"A:2\", \"backup\":\"B:2\"}, {\"nodeID\":\"A3\",\"primary\":\"A:3\", \"backup\":\"B:3\"}]");
                  case "tcp://A:2", "tcp://B:2" ->
                     read("[{\"nodeID\":\"A1\",\"primary\":\"A:1\"}, {\"nodeID\":\"A2\",\"primary\":\"A:2\"}, {\"nodeID\":\"A3\",\"primary\":\"A:3\"}]");
                  default ->
                     read("[{\"nodeID\":\"A1\",\"primary\":\"A:1\", \"backup\":\"B:1\"}, {\"nodeID\":\"A2\",\"primary\":\"A:2\", \"backup\":\"B:2\"}]");
               };
            } else {
               return fetchMainTopology();
            }
         }
      };

      if (fail) {
         assertFalse(fakeVerifier.verify(new ActionContext()));
      } else {
         assertTrue(fakeVerifier.verify(new ActionContext()));
      }
   }

   // The server is not clustered, and listTopology will return []
   @Test
   public void testReadEmpty() throws Exception {
      ClusterNodeVerifier fakeVerifier = new ClusterNodeVerifier("fake", "a", "b") {
         @Override
         protected void verifyTime(ActionContext context,
                                   Map<String, TopologyItem> mainTopology,
                                   AtomicBoolean verificationResult,
                                   boolean supportTime) {
            // not doing it
         }

         @Override
         protected Long[] fetchTopologyTime(Map<String, TopologyItem> topologyItemMap) {
            throw new NotImplementedException("not doing it");
         }

         @Override
         protected long fetchMainTime() throws Exception {
            throw new NotImplementedException("not doing it");
         }

         @Override
         protected long fetchTime(String uri) throws Exception {
            return super.fetchTime(uri);
         }

         @Override
         protected String getNodeID() {
            return "AA";
         }

         @Override
         protected JsonArray fetchMainTopology() throws Exception {
            return read("[]");
         }

         @Override
         protected JsonArray fetchTopology(String uri) throws Exception {
            return read("[]");
         }
      };

      assertTrue(fakeVerifier.verify(new ActionContext()));

   }

}
