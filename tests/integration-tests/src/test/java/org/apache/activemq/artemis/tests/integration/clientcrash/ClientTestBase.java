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
package org.apache.activemq.artemis.tests.integration.clientcrash;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.apache.activemq.artemis.tests.util.SpawnedTestBase;

public abstract class ClientTestBase extends SpawnedTestBase {

   protected ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultNettyConfig();
      server = createServer(false, config);
      server.start();
   }

   protected void assertActiveConnections(final int expectedActiveConnections) throws Exception {
      assertActiveConnections(expectedActiveConnections, 0);
   }

   protected void assertActiveConnections(final int expectedActiveConnections, long timeout) throws Exception {
      Wait.assertEquals(expectedActiveConnections, server.getActiveMQServerControl()::getConnectionCount, timeout);
   }

   protected void assertActiveSession(final int expectedActiveSession) throws Exception {
      assertActiveSession(expectedActiveSession, 0);
   }

   protected void assertActiveSession(final int expectedActiveSession, long timeout) throws Exception {
      Wait.assertEquals(expectedActiveSession, () -> server.getSessions().size(), timeout);
   }

}
