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

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProducerTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private final SimpleString QUEUE = SimpleString.of("ConsumerTestQueue");

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false);

      server.start();
   }

   @Test
   public void testProducerWithSmallWindowSizeAndLargeMessage() throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);
      server.getRemotingService().addIncomingInterceptor((Interceptor) (packet, connection) -> {
         if (packet.getType() == PacketImpl.SESS_SEND) {
            latch.countDown();
         }
         return true;
      });
      ServerLocator locator = createInVMNonHALocator().setConfirmationWindowSize(100);
      ClientSessionFactory cf = locator.createSessionFactory();
      ClientSession session = cf.createSession(false, true, true);
      ClientProducer producer = session.createProducer(QUEUE);
      ClientMessage message = session.createMessage(true);
      byte[] body = new byte[1000];
      message.getBodyBuffer().writeBytes(body);
      producer.send(message);
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      session.close();
      locator.close();
   }

   @Test
   public void testProducerMultiThread() throws Exception {
      final ServerLocator locator = createInVMNonHALocator();
      AddressSettings setting = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK).setMaxSizeBytes(10 * 1024);
      server.stop();
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put(QUEUE.toString(), setting);
      server.start();

      server.createQueue(QueueConfiguration.of(QUEUE));

      for (int i = 0; i < 100; i++) {
         final CountDownLatch latch = new CountDownLatch(1);
         logger.debug("Try {}", i);
         ClientSessionFactory cf = locator.createSessionFactory();
         final ClientSession session = cf.createSession(false, true, true);

         Thread t = new Thread(() -> {
            try {
               ClientProducer producer = session.createProducer();

               for (int i1 = 0; i1 < 62; i1++) {
                  if (i1 == 30) {
                     // the point where the send would block
                     latch.countDown();
                  }
                  ClientMessage msg = session.createMessage(false);
                  msg.getBodyBuffer().writeBytes(new byte[2048]);
                  producer.send(QUEUE, msg);
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         });

         t.start();
         assertTrue(latch.await(10, TimeUnit.SECONDS));
         session.close();

         t.join(5000);

         if (!t.isAlive()) {
            t.interrupt();
         }

         assertFalse(t.isAlive());

         ClientSession sessionConsumer = cf.createSession();
         sessionConsumer.start();
         ClientConsumer cons = sessionConsumer.createConsumer(QUEUE);
         while (true) {
            ClientMessage msg = cons.receiveImmediate();
            if (msg == null) {
               break;
            }
            msg.acknowledge();
            sessionConsumer.commit();
         }

         cf.close();
      }
   }

}
