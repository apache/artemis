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
package org.apache.activemq.artemis.tests.stress.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class SendStressTest extends ActiveMQTestBase {



   // Remove this method to re-enable those tests
   @Test
   public void testStressSendNetty() throws Exception {
      doTestStressSend(true);
   }

   @Test
   public void testStressSendInVM() throws Exception {
      doTestStressSend(false);
   }

   public void doTestStressSend(final boolean netty) throws Exception {
      ActiveMQServer server = createServer(false, netty);
      server.start();
      ServerLocator locator = createNonHALocator(netty);
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = null;

      final int batchSize = 2000;

      final int numberOfMessages = 100000;

      try {
         server.start();

         session = sf.createSession(false, false);

         session.createQueue(QueueConfiguration.of("queue").setAddress("address"));

         ClientProducer producer = session.createProducer("address");

         ClientMessage message = session.createMessage(false);

         message.getBodyBuffer().writeBytes(new byte[1024]);

         for (int i = 0; i < numberOfMessages; i++) {
            producer.send(message);
            if (i % batchSize == 0) {
               System.out.println("Sent " + i);
               session.commit();
            }
         }

         session.commit();

         session.close();

         session = sf.createSession(false, false);

         ClientConsumer consumer = session.createConsumer("queue");

         session.start();

         for (int i = 0; i < numberOfMessages; i++) {
            ClientMessage msg = consumer.receive(5000);
            assertNotNull(msg);
            msg.acknowledge();

            if (i % batchSize == 0) {
               System.out.println("Consumed " + i);
               session.commit();
            }
         }

         session.commit();
      } finally {
         if (session != null) {
            try {
               sf.close();
               session.close();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
         locator.close();
         server.stop();
      }

   }

}
