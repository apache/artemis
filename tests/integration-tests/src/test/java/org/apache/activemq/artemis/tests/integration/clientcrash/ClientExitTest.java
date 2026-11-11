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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A test that makes sure that an Apache Artemis client gracefully exists after the last session is closed.
 * <p>
 * This is not technically a crash test, but it uses the same type of topology as the crash tests (local server, remote
 * VM client).
 */
public class ClientExitTest extends ClientTestBase {


   private static final String MESSAGE_TEXT = RandomUtil.randomUUIDString();

   private static final SimpleString QUEUE = SimpleString.of("ClientExitTestQueue");

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ClientSession session;

   private ClientConsumer consumer;

   @Test
   public void testGracefulClientExit() throws Exception {
      // spawn a JVM that creates a JMS client, which sends a test message
      Process p = SpawnedVMSupport.spawnVM(GracefulClient.class.getName(), ClientExitTest.QUEUE.toString(), ClientExitTest.MESSAGE_TEXT);

      // read the message from the queue

      ClientMessage message = consumer.receive(15000);

      assertNotNull(message);
      assertEquals(ClientExitTest.MESSAGE_TEXT, message.getBodyBuffer().readString());

      // the client VM should exit by itself. If it doesn't, that means we have a problem
      // and the test will timeout
      ClientExitTest.logger.debug("waiting for the client VM to exit ...");
      p.waitFor();

      assertEquals(0, p.exitValue());

      // FIXME
      // Thread.sleep(1000);
      //
      // // the local session
      // assertActiveConnections(1);
      // // assertActiveSession(1);

      session.close();

      // FIXME
      // Thread.sleep(1000);
      // assertActiveConnections(0);
      // // assertActiveSession(0);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      ServerLocator locator = createNettyNonHALocator();
      addServerLocator(locator);
      ClientSessionFactory sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true);
      session.createQueue(QueueConfiguration.of(ClientExitTest.QUEUE).setDurable(false));
      consumer = session.createConsumer(ClientExitTest.QUEUE);
      session.start();
   }
}
