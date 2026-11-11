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
package org.apache.activemq.artemis.tests.integration.amqp.paging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class AmqpMaxReadPagingTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public AmqpMaxReadPagingTest() {
   }

   @Override
   protected void addConfiguration(ActiveMQServer server) {
      super.addConfiguration(server);
      final Map<String, AddressSettings> addressesSettings = server.getConfiguration()
         .getAddressSettings();
      addressesSettings.get("#").setMaxSizeMessages(1)
         .setMaxSizeBytes(100000)
         .setPageSizeBytes(10000).setMaxReadPageMessages(10).setMaxReadPageBytes(10 * 1024 * 1024);

      server.getConfiguration().setMessageExpiryScanPeriod(-1);
   }

   @Test
   @Timeout(60)
   public void testMaxReadPage() throws Exception {
      final String data = "0".repeat(1000);
      final int MSG_COUNT = 100;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      runAfter(connection::close);
      AmqpSession session = connection.createSession();

      Queue queue = server.locateQueue(getQueueName());
      assertNotNull(queue);
      queue.getPagingStore().startPaging();

      AmqpSender sender = session.createSender(getQueueName(), true);

      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.setPresettle(true);
      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setText(data);
         message.setDurable(true);
         sender.send(message);
      }
      sender.close();
      Wait.assertEquals(MSG_COUNT, queue::getMessageCount);
      receiver.flow(MSG_COUNT);
      assertNotNull(receiver.receive(10, TimeUnit.SECONDS)); // wait some time so we have some data
      if (receiver.getPrefetchSize() > 10) {
         logger.warn("Receiver has an unexpected size of {} elements on the client buffer", receiver.getPrefetchSize());
      }
      PagingStore pagingStore = server.getPagingManager().getPageStore(SimpleString.of(getQueueName()));
      assertTrue(pagingStore.isPaging());
      assertTrue(receiver.getPrefetchSize() <= 10); // we should not have more than page-read messages

      Thread.sleep(500); // it is important to have some quiet period to make sure all previous tasks were emptied
      for (int i = 0; i < MSG_COUNT - 1; i++) {
         AmqpMessage message = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(message);
         System.out.println("Received " + i);
         message.accept();
      }

      receiver.close();
      connection.close();
   }

}
