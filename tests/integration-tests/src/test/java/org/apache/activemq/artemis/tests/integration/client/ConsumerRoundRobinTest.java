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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ConsumerRoundRobinTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public final SimpleString addressA = SimpleString.of("addressA");

   public final SimpleString queueA = SimpleString.of("queueA");

   @Test
   public void testConsumersRoundRobinCorrectly() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession session = addClientSession(cf.createSession(false, true, true));
      session.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));

      ClientConsumer[] consumers = new ClientConsumer[5];
      // start the session before we create the consumers, this is because start is non blocking and we have to
      // guarantee
      // all consumers have been started before sending messages
      session.start();
      consumers[0] = session.createConsumer(queueA);
      consumers[1] = session.createConsumer(queueA);
      consumers[2] = session.createConsumer(queueA);
      consumers[3] = session.createConsumer(queueA);
      consumers[4] = session.createConsumer(queueA);

      ClientProducer cp = session.createProducer(addressA);
      int numMessage = 10;
      for (int i = 0; i < numMessage; i++) {
         ClientMessage cm = session.createMessage(false);
         cm.getBodyBuffer().writeInt(i);
         cp.send(cm);
      }
      int currMessage = 0;
      for (int i = 0; i < numMessage / 5; i++) {
         logger.debug("i is {}", i);
         for (int j = 0; j < 5; j++) {
            logger.debug("j is {}", j);
            ClientMessage cm = consumers[j].receive(5000);
            assertNotNull(cm);
            assertEquals(currMessage++, cm.getBodyBuffer().readInt());
            cm.acknowledge();
         }
      }
   }

}
