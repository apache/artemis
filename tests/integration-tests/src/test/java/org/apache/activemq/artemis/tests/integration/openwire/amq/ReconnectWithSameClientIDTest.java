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

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.InvalidClientIDException;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.ReconnectWithSameClientIDTest
 */
public class ReconnectWithSameClientIDTest extends BasicOpenWireTest {

   protected ActiveMQConnection sameIdConnection;
   protected boolean transacted;
   protected int authMode = Session.AUTO_ACKNOWLEDGE;

   @Test
   public void testReconnectMultipleTimesWithSameClientID() throws Exception {
      try {
         sameIdConnection = (ActiveMQConnection) this.factory.createConnection();
         useConnection(sameIdConnection);

         // now lets create another which should fail
         for (int i = 1; i < 11; i++) {
            Connection connection2 = this.factory.createConnection();
            try {
               useConnection(connection2);
               fail("Should have thrown InvalidClientIDException on attempt" + i);
            } catch (InvalidClientIDException e) {
               System.err.println("Caught expected: " + e);
            } finally {
               connection2.close();
            }
         }

         // now lets try closing the original connection and creating a new
         // connection with the same ID
         sameIdConnection.close();
         sameIdConnection = (ActiveMQConnection) factory.createConnection();
         useConnection(connection);
      } finally {
         if (sameIdConnection != null) {
            sameIdConnection.close();
         }
      }
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      if (sameIdConnection != null) {
         sameIdConnection.close();
         sameIdConnection = null;
      }
      super.tearDown();
   }

   protected void useConnection(Connection connection) throws JMSException {
      connection.setClientID("foo");
      connection.start();
   }

}
