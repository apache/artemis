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

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class EmbeddedActiveMQResourceCustomConfigurationTest {

   static final String TEST_QUEUE = "test.queue";
   static final String TEST_ADDRESS = "test.address";

   @RegisterExtension
   public static EmbeddedActiveMQExtension server = new EmbeddedActiveMQExtension(new ConfigurationImpl()
                                                                                        .setPersistenceEnabled(false)
                                                                                        .setSecurityEnabled(true)
                                                                                        .addQueueConfiguration(QueueConfiguration.of(TEST_QUEUE)
                                                                                                                  .setAddress(TEST_ADDRESS)));

   @Test
   public void testCustomConfiguration() {
      Configuration configuration = server.getServer().getActiveMQServer().getConfiguration();

      assertFalse(configuration.isPersistenceEnabled(), "Persistence should have been disabled");
      assertTrue(configuration.isSecurityEnabled(), "Security should have been enabled");

      assertNotNull(server.locateQueue(TEST_QUEUE), TEST_QUEUE + " should exist");

      List<Queue> boundQueues = server.getBoundQueues(TEST_ADDRESS);
      assertNotNull(boundQueues, "List should never be null");
      assertEquals(1, boundQueues.size(), "Should have one queue bound to address " + TEST_ADDRESS);
   }

}
