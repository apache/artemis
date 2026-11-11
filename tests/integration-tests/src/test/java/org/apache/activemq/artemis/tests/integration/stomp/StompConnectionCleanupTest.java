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
package org.apache.activemq.artemis.tests.integration.stomp;

import javax.jms.Message;
import javax.jms.MessageConsumer;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class StompConnectionCleanupTest extends StompTest {

   private static final long CONNECTION_TTL = 2000;

   // ARTEMIS-231
   @Test
   public void testConnectionCleanupWithTopicSubscription() throws Exception {
      conn.connect(defUser, defPass);

      subscribeTopic(conn, null, "auto", null);

      // Now we wait until the connection is cleared on the server, which will happen some time after ttl, since no data
      // is being sent

      long start = System.currentTimeMillis();

      while (true) {
         int connCount = server.getRemotingService().getConnections().size();

         int sessionCount = server.getSessions().size();

         // All connections and sessions should be timed out including STOMP + JMS connection

         if (connCount == 0 && sessionCount == 0) {
            break;
         }

         Thread.sleep(10);

         if (System.currentTimeMillis() - start > 10000) {
            fail("Timed out waiting for connection to be cleared up");
         }
      }
   }

   @Test
   public void testConnectionCleanup() throws Exception {
      conn.connect(defUser, defPass);

      subscribe(conn, null, "auto", null);

      send(conn, getQueuePrefix() + getQueueName(), null, "Hello World");

      ClientStompFrame frame = conn.receiveFrame(10000);

      assertEquals("MESSAGE", frame.getCommand());
      assertEquals(getQueuePrefix() + getQueueName(), frame.getHeader("destination"));

      // Now we wait until the connection is cleared on the server, which will happen some time after ttl, since no data
      // is being sent

      long start = System.currentTimeMillis();

      while (true) {
         int connCount = server.getRemotingService().getConnections().size();

         int sessionCount = server.getSessions().size();

         // All connections and sessions should be timed out including STOMP + JMS connection

         if (connCount == 0 && sessionCount == 0) {
            break;
         }

         Thread.sleep(10);

         if (System.currentTimeMillis() - start > 10000) {
            fail("Timed out waiting for connection to be cleared up");
         }
      }
   }

   @Test
   public void testConnectionNotCleanedUp() throws Exception {
      conn.connect(defUser, defPass);

      MessageConsumer consumer = session.createConsumer(queue);

      long time = CONNECTION_TTL * 3;

      long start = System.currentTimeMillis();

      //Send msgs for an amount of time > connection_ttl make sure connection is not closed
      while (true) {
         //Send and receive a msg

         send(conn, getQueuePrefix() + getQueueName(), null, "Hello World");

         Message msg = consumer.receive(1000);
         assertNotNull(msg);

         Thread.sleep(100);

         if (System.currentTimeMillis() - start > time) {
            break;
         }
      }

   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      ActiveMQServer s = super.createServer();

      s.getConfiguration().setConnectionTTLOverride(CONNECTION_TTL);

      return s;
   }
}
