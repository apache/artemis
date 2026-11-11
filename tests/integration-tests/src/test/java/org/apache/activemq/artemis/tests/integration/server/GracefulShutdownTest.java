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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQSessionCreationException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class GracefulShutdownTest extends ActiveMQTestBase {

   @Test
   public void testGracefulShutdown() throws Exception {
      Configuration config = createDefaultInVMConfig().setGracefulShutdownEnabled(true);

      final ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, true);

      Thread t = new Thread(() -> {
         try {
            server.stop();
         } catch (Exception e) {
            e.printStackTrace();
         }
      });

      t.setName("shutdown thread");
      t.start();

      // wait for the thread to actually call stop() on the server
      while (server.isStarted()) {
         Thread.sleep(100);
      }

      // confirm we can still do work on the original connection even though the server is stopping
      session.createQueue(QueueConfiguration.of("testQueue").setAddress("testAddress").setRoutingType(RoutingType.ANYCAST));
      ClientProducer producer = session.createProducer("testAddress");
      producer.send(session.createMessage(true));
      session.start();
      assertNotNull(session.createConsumer("testQueue").receive(500));

      try {
         sf.createSession();
         fail("Creating a session here should fail because the acceptors should be paused");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQSessionCreationException.class, e);
         ActiveMQSessionCreationException activeMQSessionCreationException = (ActiveMQSessionCreationException) e;
         assertEquals(ActiveMQExceptionType.SESSION_CREATION_REJECTED, activeMQSessionCreationException.getType());
      }

      // close the connection to allow broker shutdown to complete
      locator.close();

      long start = System.currentTimeMillis();

      // wait for the shutdown thread to complete, interrupt it if it takes too long
      while (t.isAlive()) {
         if (System.currentTimeMillis() - start > 3000) {
            t.interrupt();
            break;
         }
         Thread.sleep(100);
      }

      // make sure the shutdown thread is dead
      assertFalse(t.isAlive());
   }

   @Test
   public void testGracefulShutdownWithTimeout() throws Exception {
      long timeout = 10000;

      Configuration config = createDefaultInVMConfig().setGracefulShutdownEnabled(true).setGracefulShutdownTimeout(timeout);

      final ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = createSessionFactory(locator);

      Thread t = new Thread(() -> {
         try {
            server.stop();
         } catch (Exception e) {
            e.printStackTrace();
         }
      });

      t.setName("shutdown thread");
      long start = System.currentTimeMillis();
      t.start();

      // wait for the thread to actually call stop() on the server
      while (server.isStarted()) {
         Thread.sleep(100);
      }

      try {
         sf.createSession();
         fail("Creating a session here should fail because the acceptors should be paused");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQSessionCreationException.class, e);
         ActiveMQSessionCreationException activeMQSessionCreationException = (ActiveMQSessionCreationException) e;
         assertEquals(ActiveMQExceptionType.SESSION_CREATION_REJECTED, activeMQSessionCreationException.getType());
      }

      Thread.sleep(timeout / 2);

      assertTrue(t.isAlive(), "thread should still be alive here waiting for the timeout to elapse");

      while (t.isAlive()) {
         Thread.sleep(100);
      }

      assertTrue(System.currentTimeMillis() - start >= timeout, "thread terminated too soon, the graceful shutdown timeout wasn't enforced properly");
   }
}
