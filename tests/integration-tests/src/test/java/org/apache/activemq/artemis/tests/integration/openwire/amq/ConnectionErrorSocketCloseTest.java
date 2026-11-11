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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;

import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ConnectionErrorSocketCloseTest extends BasicOpenWireTest {

   @Override
   protected void createFactories() {
      super.createFactories();
      factory.setClientID("id");
   }

   //We want to make sure that the socket will be closed if there as an error on broker.addConnection
   //even if the client doesn't close the connection to prevent dangling open sockets
   @Test
   @Timeout(60)
   public void testDuplicateClientIdCloseConnection() throws Exception {
      connection.start();
      Wait.waitFor(() -> server.getRemotingService().getConnections().size() == 1, 10000, 500);

      try (Connection con = factory.createConnection()) {
         // Try and create second connection the second should fail because of a
         // duplicate clientId
         try {
            // Should fail because of previously started connection with same
            // client Id
            con.start();
            fail("Should have exception");
         } catch (Exception e) {
            e.printStackTrace();
         }

         // after 2 seconds the second connection should be terminated by the
         // broker because of the exception
         assertTrue(Wait.waitFor(() -> server.getRemotingService().getConnections().size() == 1, 10000, 500));
      }
   }
}
