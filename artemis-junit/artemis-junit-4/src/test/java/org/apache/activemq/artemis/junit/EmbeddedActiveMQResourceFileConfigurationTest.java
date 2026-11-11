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
package org.apache.activemq.artemis.junit;

import java.util.List;

import org.apache.activemq.artemis.core.server.Queue;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EmbeddedActiveMQResourceFileConfigurationTest {

   // These values must match the contents of the configuration file
   static final String TEST_QUEUE = "test.queue";
   static final String TEST_ADDRESS = "test.address";

   private EmbeddedActiveMQResource server = new EmbeddedActiveMQResource("embedded-artemis-server.xml");

   @Rule
   public RuleChain rulechain = RuleChain.outerRule(server);

   @After
   public void tear() {
      server.stop();
   }

   @Test
   public void testConfiguredQueue() throws Exception {
      assertNotNull(TEST_QUEUE + " should exist", server.locateQueue(TEST_QUEUE));

      List<Queue> boundQueues = server.getBoundQueues(TEST_ADDRESS);
      assertNotNull("List should never be null", boundQueues);
      assertEquals("Should have one queue bound to address " + TEST_ADDRESS, 1, boundQueues.size());
   }

}
