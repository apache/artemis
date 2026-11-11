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
package org.apache.activemq.artemis.tests.integration.paging;


import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DiskFullMessagePolicy;
import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DiskFullSendPagingTest extends ActiveMQTestBase {

   @Test
   public void testSendMessagesOnDiskFullWithDropPolicy() throws Exception {
      ActiveMQServerImpl server = createServerWithDiskFullPolicy(DiskFullMessagePolicy.DROP);

      server.getMonitor().setMaxUsage(0); // forcing disk full (faking it)
      server.getMonitor().tick();

      CountDownLatch done = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger();

      String address = getTestMethodName();

      Thread t = new Thread(() -> {
         try {
            trySendMessage(address);
         } catch (Exception e) {
            errors.incrementAndGet();
         } finally {
            done.countDown();
         }
      });

      t.start();

      assertTrue(done.await(200, TimeUnit.MILLISECONDS));
      assertEquals(0, errors.get());
   }

   @Test
   public void testSendMessagesOnDiskFullWithFailPolicy() throws Exception {
      ActiveMQServerImpl server = createServerWithDiskFullPolicy(DiskFullMessagePolicy.FAIL);

      server.getMonitor().setMaxUsage(0); // forcing disk full (faking it)
      server.getMonitor().tick();

      CountDownLatch done = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger();

      String address = getTestMethodName();

      Thread t = new Thread(() -> {
         try {
            trySendMessage(address);
         } catch (Exception e) {
            errors.incrementAndGet();
         } finally {
            done.countDown();
         }
      });

      t.start();

      assertTrue(done.await(200, TimeUnit.MILLISECONDS));
      assertEquals(1, errors.get());
   }

   @Test
   public void testSendMessagesOnDiskFullWithBlockPolicy() throws Exception {
      ActiveMQServerImpl server = createServerWithDiskFullPolicy(DiskFullMessagePolicy.BLOCK);

      server.getMonitor().setMaxUsage(0); // forcing disk full (faking it)
      server.getMonitor().tick();

      CountDownLatch done = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger();

      String address = getTestMethodName();

      Thread t = new Thread(() -> {
         try {
            trySendMessage(address);
         } catch (Exception e) {
            errors.incrementAndGet();
         } finally {
            done.countDown();
         }
      });

      t.start();

      assertFalse(done.await(200, TimeUnit.MILLISECONDS));
      assertEquals(0, errors.get());

      server.getMonitor().setMaxUsage(1); // release the disk
      server.getMonitor().tick();

      assertTrue(done.await(200, TimeUnit.MILLISECONDS));
      assertEquals(0, errors.get());
   }

   private ActiveMQServerImpl createServerWithDiskFullPolicy(DiskFullMessagePolicy policy) throws Exception {
      ActiveMQServerImpl server = (ActiveMQServerImpl) createServer(true);
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDiskFullMessagePolicy(policy));
      server.start();

      waitForServerToStart(server);
      return server;
   }

   private void trySendMessage(String address) throws Exception {
      try (ClientSessionFactory factory = createSessionFactory(createFactory(false))) {
         try (ClientSession session = factory.createSession()) {
            session.createQueue(QueueConfiguration.of(address));

            ClientProducer producer = session.createProducer(address);

            producer.send(createBytesMessage(session, ActiveMQBytesMessage.TYPE, new byte[1024], false));
         }
      }
   }
}
