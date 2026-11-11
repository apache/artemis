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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class AmqpPagingTest extends AmqpClientTestSupport {

   @Parameters(name = "durability={0}")
   public static Collection getParams() {
      return Arrays.asList(new Object[][]{
         {Boolean.TRUE}, {Boolean.FALSE}});
   }

   private final Boolean durable;

   public AmqpPagingTest(Boolean durable) {
      this.durable = durable;
   }

   @Override
   protected void addConfiguration(ActiveMQServer server) {
      super.addConfiguration(server);
      final Map<String, AddressSettings> addressesSettings = server.getConfiguration()
         .getAddressSettings();
      addressesSettings.get("#")
         .setMaxSizeBytes(100000)
         .setPageSizeBytes(10000);
   }

   @TestTemplate
   @Timeout(60)
   public void testPaging() throws Exception {
      final String data = "0".repeat(1000);
      final int MSG_COUNT = 1_000;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName(), true);

      AmqpReceiver receiver = session.createReceiver(getQueueName());
      receiver.setPresettle(true);
      receiver.flow(10);
      assertNull(receiver.receiveNoWait(), "somehow the queue had messages from a previous test");
      receiver.flow(0);
      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage message = new AmqpMessage();
         message.setText(data);
         if (durable != null) {
            message.setDurable(durable);
         }
         sender.send(message);
      }
      sender.close();
      final Queue queueView = getProxyToQueue(getQueueName());
      Wait.assertEquals(MSG_COUNT, queueView::getMessageCount);
      PagingStore pagingStore = server.getPagingManager().getPageStore(SimpleString.of(getQueueName()));
      assertTrue(pagingStore.isPaging());
      final long pageCacheMaxSize = server.getConfiguration().getAddressSettings().get("#").getPageCacheMaxSize();
      assertThat("the size of the messages or the number of messages isn't enough",
                        pagingStore.getNumberOfPages(), Matchers.greaterThan(pageCacheMaxSize));
      receiver.flow(MSG_COUNT);
      for (int i = 0; i < MSG_COUNT; i++) {
         AmqpMessage receive = receiver.receive(10, TimeUnit.SECONDS);
         assertNotNull(receive, "Not received anything after " + i + " receive");
         assertEquals(durable == null ? false : durable.booleanValue(), receive.isDurable());
         receive.accept();
      }
      receiver.close();
      connection.close();
   }


   @TestTemplate
   @Timeout(60)
   public void testSizeCalculationsForApplicationProperties() throws Exception {
      final String data = "0".repeat(1000);
      final int MSG_COUNT = 1;

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpSender sender = session.createSender(getQueueName(), true);

      // use selector expression that references a property to force decode of application properties
      AmqpReceiver receiver = session.createReceiver(getQueueName(), "myData IS NOT NULL");
      receiver.setPresettle(true);
      receiver.flow(10);
      assertNull(receiver.receiveNoWait(), "somehow the queue had messages from a previous test");
      receiver.flow(0);

      AmqpMessage message = new AmqpMessage();
      message.setText(data);

      // large message property also
      message.setApplicationProperty("myData", data);

      if (durable != null) {
         message.setDurable(durable);
      }
      sender.send(message);

      PagingStore pagingStore = server.getPagingManager().getPageStore(SimpleString.of(getQueueName()));

      // verify page usage reflects data + 2*application properties (encoded and decoded)
      assertTrue(Wait.waitFor(() -> {
         return pagingStore.getAddressSize() > 3000;
      }));

      receiver.flow(MSG_COUNT);
      AmqpMessage receive = receiver.receive(10, TimeUnit.MINUTES);
      assertNotNull(receive, "Not received anything after receive");
      receive.accept();

      assertTrue(Wait.waitFor(() -> {
         return pagingStore.getAddressSize() == 0;
      }));

      // send another with duplicate id property, to force early decode
      message = new AmqpMessage();
      message.setText(data);

      // ensures application properties are referenced
      message.setApplicationProperty("_AMQ_DUPL_ID", "1");

      // large message property also
      message.setApplicationProperty("myData", data);

      if (durable != null) {
         message.setDurable(durable);
      }
      sender.send(message);

      sender.close();

      // verify page usage reflects data + 2*application properties (encoded and decoded)
      assertTrue(Wait.waitFor(() -> {
         return pagingStore.getAddressSize() > 3000;
      }));

      receiver.flow(MSG_COUNT);
      receive = receiver.receive(10, TimeUnit.MINUTES);
      assertNotNull(receive, "Not received anything after receive");
      receive.accept();

      receiver.close();
      connection.close();
   }

}
