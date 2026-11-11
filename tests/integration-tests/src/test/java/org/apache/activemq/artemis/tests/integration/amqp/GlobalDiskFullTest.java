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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.files.FileStoreMonitor;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class GlobalDiskFullTest extends AmqpClientTestSupport {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Parameter(index = 0)
   public AddressFullMessagePolicy addressFullPolicy;

   @Parameters(name = "addressFullPolicy={0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
         {AddressFullMessagePolicy.FAIL}, {AddressFullMessagePolicy.DROP}, {AddressFullMessagePolicy.PAGE}
      });
   }

   @Override
   protected void configureAddressPolicy(ActiveMQServer server) {
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setAddressFullMessagePolicy(addressFullPolicy);
      server.getConfiguration().addAddressSetting(getQueueName(), addressSettings);
   }

   @Override
   protected void addConfiguration(ActiveMQServer server) {
      Configuration serverConfig = server.getConfiguration();
      serverConfig.setDiskScanPeriod(100);
   }

   protected void waitMonitor(FileStoreMonitor monitor) throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      FileStoreMonitor.Callback callback = (a, b, c, d) -> {
         latch.countDown();
      };

      monitor.addCallback(callback);

      assertTrue(latch.await(10, TimeUnit.SECONDS));

      monitor.removeCallback(callback);
   }


   @TestTemplate
   public void testProducerOnDiskFull() throws Exception {

      FileStoreMonitor monitor = ((ActiveMQServerImpl)server).getMonitor();

      waitMonitor(monitor);

      //make it full
      monitor.setMaxUsage(0.0);

      waitMonitor(monitor);

      AmqpClient client = createAmqpClient(new URI("tcp://localhost:" + AMQP_PORT));
      AmqpConnection connection = addConnection(client.connect());

      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(getQueueName());
         byte[] payload = new byte[1000];

         AmqpSender anonSender = session.createSender();

         CountDownLatch sentWithName = new CountDownLatch(1);
         CountDownLatch sentAnon = new CountDownLatch(1);

         ExecutorService pool = Executors.newCachedThreadPool();
         runAfter(pool::shutdownNow);

         pool.execute(() -> {
            try {
               final AmqpMessage message = new AmqpMessage();
               message.setBytes(payload);
               sender.setSendTimeout(-1);
               sender.send(message);
            } catch (Exception e) {
               logger.warn("Caught exception while sending", e);
            } finally {
               sentWithName.countDown();
            }
         });
         pool.execute(()-> {
            try {
               final AmqpMessage message = new AmqpMessage();
               message.setBytes(payload);
               anonSender.setSendTimeout(-1);
               message.setAddress(getQueueName());
               anonSender.send(message);
               sentAnon.countDown();
            } catch (Exception e) {
               logger.warn("Caught exception while sending", e);
            }
         });

         PagingManagerImpl pagingManager = (PagingManagerImpl) server.getPagingManager();
         Wait.assertTrue(() -> pagingManager.getBlockedSet().size() > 0, 5000);

         assertFalse(sentWithName.await(100, TimeUnit.MILLISECONDS), "Thread sender should be blocked");
         assertFalse(sentAnon.await(100, TimeUnit.MILLISECONDS), "Thread sender anonymous should be blocked");

         // unblock
         monitor.setMaxUsage(100.0);

         waitMonitor(monitor);

         assertTrue(sentWithName.await(30, TimeUnit.SECONDS), "Thread sender should be released");
         assertTrue(sentAnon.await(30, TimeUnit.SECONDS), "Thread sender anonymous should be released");

         Wait.assertEquals(0, () -> pagingManager.getBlockedSet().size(), 5000);
      } finally {
         connection.close();
      }
   }

}
