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

import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class OrderTest extends ActiveMQTestBase {

   private boolean persistent;

   private ActiveMQServer server;

   private ServerLocator locator;

   public OrderTest(boolean persistent) {
      this.persistent = persistent;
   }

   @Parameters(name = "persistent={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createNettyNonHALocator();
   }



   @TestTemplate
   public void testSimpleStorage() throws Exception {
      server = createServer(persistent, true);
      server.start();

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(true);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, true, 0);

      session.createQueue(QueueConfiguration.of("queue"));

      ClientProducer prod = session.createProducer("queue");

      for (int i = 0; i < 100; i++) {
         ClientMessage msg = session.createMessage(i % 2 == 0);
         msg.putIntProperty("id", i);
         prod.send(msg);
      }

      session.close();

      boolean started = false;

      for (int start = 0; start < 2; start++) {

         if (persistent && start == 1) {
            started = true;
            server.stop();
            server.start();
            sf = createSessionFactory(locator);
         }

         session = sf.createSession(true, true);

         session.start();

         ClientConsumer cons = session.createConsumer("queue");

         for (int i = 0; i < 100; i++) {
            if (!started || started && i % 2 == 0) {
               ClientMessage msg = cons.receive(10000);

               assertEquals(i, msg.getIntProperty("id").intValue());
            }
         }

         cons.close();

         cons = session.createConsumer("queue");

         for (int i = 0; i < 100; i++) {
            if (!started || started && i % 2 == 0) {
               ClientMessage msg = cons.receive(10000);

               assertEquals(i, msg.getIntProperty("id").intValue());
            }
         }

         session.close();
      }
   }

   @TestTemplate
   public void testOrderOverSessionClose() throws Exception {
      server = createServer(persistent, true);

      server.start();

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false);

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(true, true, 0);

      int numberOfMessages = 500;
      session.createQueue(QueueConfiguration.of("queue"));

      ClientProducer prod = session.createProducer("queue");

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = session.createMessage(i % 2 == 0);
         msg.putIntProperty("id", i);
         prod.send(msg);
      }

      session.close();

      for (int i = 0; i < numberOfMessages; ) {
         session = sf.createSession();

         session.start();

         ClientConsumer consumer = session.createConsumer("queue");

         int max = i + 10;

         for (; i < max; i++) {
            ClientMessage msg = consumer.receive(1000);

            msg.acknowledge();

            assertEquals(i, msg.getIntProperty("id").intValue());
         }

         // Receive a few more messages but don't consume them
         for (int j = 0; j < 10 && i < numberOfMessages; j++) {
            ClientMessage msg = consumer.receiveImmediate();
            if (msg == null) {
               break;
            }
         }
         session.close();

      }
   }

   @TestTemplate
   public void testOrderOverSessionCloseWithRedeliveryDelay() throws Exception {
      server = createServer(persistent, true);

      server.getAddressSettingsRepository().clear();
      AddressSettings setting = new AddressSettings().setRedeliveryDelay(500);
      server.getAddressSettingsRepository().addMatch("#", setting);

      server.start();

      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false).setBlockOnAcknowledge(false);

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(true, true, 0);

      int numberOfMessages = 500;

      session.createQueue(QueueConfiguration.of("queue"));

      ClientProducer prod = session.createProducer("queue");

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = session.createMessage(i % 2 == 0);
         msg.putIntProperty("id", i);
         prod.send(msg);
      }

      session.close();

      session = sf.createSession(false, false);

      session.start();

      ClientConsumer cons = session.createConsumer("queue");

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = cons.receive(5000);
         msg.acknowledge();
         assertEquals(i, msg.getIntProperty("id").intValue());
      }
      session.close();

      session = sf.createSession(false, false);

      session.start();

      cons = session.createConsumer("queue");

      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage msg = cons.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         assertEquals(i, msg.getIntProperty("id").intValue());
      }

      // receive again
      session.commit();
      session.close();
   }

}
