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
package org.apache.activemq.artemis.tests.stress.journal;

import java.util.ArrayList;
import java.util.Random;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

/**
 * Simulates the journal being updated, compacted cleared up, and having multiple restarts, To make sure the journal
 * would survive at multiple restarts of the server
 */
public class JournalRestartStressTest extends ActiveMQTestBase {




   @Test
   public void testLoad() throws Throwable {
      ActiveMQServer server2 = createServer(true, false);

      server2.getConfiguration().setJournalFileSize(10 * 1024 * 1024);
      server2.getConfiguration().setJournalMinFiles(10);
      server2.getConfiguration().setJournalCompactMinFiles(3);
      server2.getConfiguration().setJournalCompactPercentage(50);

      for (int i = 0; i < 10; i++) {
         server2.start();

         ServerLocator locator = createInVMNonHALocator().setMinLargeMessageSize(1024 * 1024).setBlockOnDurableSend(false);

         ClientSessionFactory sf = createSessionFactory(locator);

         ClientSession session = sf.createSession(true, true);

         try {
            session.createQueue(QueueConfiguration.of("slow-queue"));
         } catch (Exception ignored) {
         }

         session.start();
         ClientConsumer consumer = session.createConsumer("slow-queue");

         while (true) {
            System.out.println("Received message from previous");
            ClientMessage msg = consumer.receiveImmediate();
            if (msg == null) {
               break;
            }
            msg.acknowledge();
         }

         session.close();

         produceMessages(sf, 30000);

         server2.stop();
      }

   }

   private void produceMessages(final ClientSessionFactory sf, final int NMSGS) throws Throwable {

      final int TIMEOUT = 5000;

      System.out.println("sending " + NMSGS + " messages");

      final ClientSession sessionSend = sf.createSession(true, true);

      ClientProducer prod2 = sessionSend.createProducer("slow-queue");

      try {
         sessionSend.createQueue(QueueConfiguration.of("Queue"));
      } catch (Exception ignored) {
      }

      final ClientSession sessionReceive = sf.createSession(true, true);
      sessionReceive.start();

      final ArrayList<Throwable> errors = new ArrayList<>();

      Thread tReceive = new Thread(() -> {
         try {
            ClientConsumer consumer = sessionReceive.createConsumer("Queue");

            for (int i = 0; i < NMSGS; i++) {
               if (i % 500 == 0) {
                  double percent = (double) i / (double) NMSGS;
                  System.out.println("msgs " + i + " of " + NMSGS + ", " + (int) (percent * 100) + "%");
                  Thread.sleep(100);
               }

               ClientMessage msg = consumer.receive(TIMEOUT);
               if (msg == null) {
                  errors.add(new Exception("Didn't receive msgs"));
                  break;
               }
               msg.acknowledge();
            }
         } catch (Exception e) {
            errors.add(e);
         }
      });

      tReceive.start();

      ClientProducer prod = sessionSend.createProducer("Queue");

      Random random = new Random();

      for (int i = 0; i < NMSGS; i++) {
         ClientMessage msg = sessionSend.createMessage(true);

         int size = RandomUtil.randomPositiveInt() % 10024;

         if (size == 0) {
            size = 10 * 1024;
         }

         byte[] buffer = new byte[size];

         random.nextBytes(buffer);

         msg.getBodyBuffer().writeBytes(buffer);

         prod.send(msg);

         if (i % 5000 == 0) {
            prod2.send(msg);
            System.out.println("Sending slow message");
         }
      }

      tReceive.join();

      sessionReceive.close();
      sessionSend.close();
      sf.close();

      for (Throwable e : errors) {
         throw e;
      }
   }

}
