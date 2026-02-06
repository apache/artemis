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
package org.apache.activemq.artemis.tests.integration.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonNumber;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonValue;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This class contains tests for core management functionalities that are affected by a server in paging mode.
 */
public class ManagementWithPagingServerTest extends ManagementTestBase {

   private ActiveMQServer server;
   private ClientSession session1;
   private ClientSession session2;
   private ServerLocator locator;

   @Test
   public void testListMessagesAsJSON() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session1.createQueue(QueueConfiguration.of(queue).setAddress(address));

      QueueControl queueControl = createManagementControl(address, queue);

      int num = 1000;
      SenderThread sender = new SenderThread(address, num, 0);

      ReceiverThread receiver = new ReceiverThread(queue, num, 0);

      //kick off sender
      sender.start();

      //wait for all messages sent
      sender.join();
      assertNull(sender.getError());

      long count = queueControl.countMessages(null);

      assertEquals(num, count);

      String result = queueControl.listMessagesAsJSON(null);

      JsonArray array = JsonUtil.readJsonArray(result);
      List<Long> longs = new ArrayList<>();
      for (JsonValue jsonValue : array) {
         JsonValue val = ((JsonObject) jsonValue).get("messageID");
         Long l = ((JsonNumber) val).longValue();
         longs.add(l);
      }
      assertEquals(num, array.size());

      //kick off receiver
      receiver.start();
      receiver.join();
      assertNull(receiver.getError());

      result = queueControl.listMessagesAsJSON(null);

      array = JsonUtil.readJsonArray(result);

      assertEquals(0, array.size());
   }

   @Test
   public void testListMessagesAsJSONWithFilter() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session1.createQueue(QueueConfiguration.of(queue).setAddress(address));

      QueueControl queueControl = createManagementControl(address, queue);

      int num = 1000;

      SimpleString key = SimpleString.of("key");
      long matchingValue = RandomUtil.randomLong();
      long unmatchingValue = matchingValue + 1;
      String filter = key + " =" + matchingValue;

      byte[] body = new byte[64];
      ByteBuffer bb = ByteBuffer.wrap(body);
      for (int j = 1; j <= 64; j++) {
         bb.put(getSamplebyte(j));
      }

      ClientProducer producer = session1.createProducer(address);
      for (int i = 0; i < num; i++) {
         ClientMessage message = session1.createMessage(true);
         if (i % 2 == 0) {
            message.putLongProperty(key, matchingValue);
         } else {
            message.putLongProperty(key, unmatchingValue);
         }
         producer.send(message);
      }

      String jsonString = queueControl.listMessagesAsJSON(filter);
      assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      assertEquals(num / 2, array.size());

      long l = Long.parseLong(array.getJsonObject(0).get("key").toString().replaceAll("\"", ""));
      assertEquals(matchingValue, l);

      long n = queueControl.countMessages(filter);
      assertEquals(num / 2, n);

      //drain out messages
      ReceiverThread receiver = new ReceiverThread(queue, num, 1);
      receiver.start();
      receiver.join();
   }

   //In this test, the management api listMessageAsJSon is called while
   //paging/depaging is going on. It makes sure that the implementation
   //of the api doesn't cause any exceptions during internal queue
   //message iteration.
   @Test
   public void testListMessagesAsJSONWhilePagingOnGoing() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session1.createQueue(QueueConfiguration.of(queue).setAddress(address));

      QueueControl queueControl = createManagementControl(address, queue);

      int num = 1000;
      SenderThread sender = new SenderThread(address, num, 1);

      ReceiverThread receiver = new ReceiverThread(queue, num, 2);

      ManagementThread console = new ManagementThread(queueControl);

      //kick off sender
      sender.start();

      //kick off jmx client
      console.start();

      //wait for all messages sent
      sender.join();
      assertNull(sender.getError());

      //kick off receiver
      receiver.start();

      receiver.join();
      assertNull(receiver.getError());

      console.exit();
      console.join();

      assertNull(console.getError());
   }

   @Test
   public void testCopyMessageWhilstPaging() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      SimpleString otherAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString otherQueue = RandomUtil.randomUUIDSimpleString();

      session1.createQueue(QueueConfiguration.of(queue).setAddress(address));
      session1.createQueue(QueueConfiguration.of(otherQueue).setAddress(otherAddress));

      QueueControl queueControl = createManagementControl(address, queue);

      QueueControl otherQueueControl = createManagementControl(otherAddress, otherQueue);

      int num = 100;

      ClientProducer producer = session1.createProducer(address);
      for (int i = 0; i < num; i++) {
         ClientMessage message = session1.createMessage(true).writeBodyBufferString("Message" + i);
         producer.send(message);
      }

      Map<String, Object>[] messages = queueControl.listMessages(null);

      long messageID = (Long) messages[99].get("messageID");

      assertTrue(queueControl.copyMessage(messageID, otherQueue.toString()));

      messageID = (Long) messages[0].get("messageID");

      assertTrue(queueControl.copyMessage(messageID, otherQueue.toString()));

      Map<String, Object>[] copiedMessages = otherQueueControl.listMessages(null);

      //this validates copying of a paged message
      assertEquals(2, copiedMessages.length);
   }

   @Test
   public void testCopyMessageWhilstPagingSameAddress() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      SimpleString otherQueue = RandomUtil.randomUUIDSimpleString();

      session1.createQueue(QueueConfiguration.of(queue).setAddress(address).setRoutingType(RoutingType.ANYCAST));
      session1.createQueue(QueueConfiguration.of(otherQueue).setAddress(address).setRoutingType(RoutingType.ANYCAST));

      QueueControl queueControl = createManagementControl(address, queue, RoutingType.ANYCAST);

      QueueControl otherQueueControl = createManagementControl(address, otherQueue, RoutingType.ANYCAST);

      int num = 200;

      ClientProducer producer = session1.createProducer(address);
      for (int i = 0; i < num; i++) {
         ClientMessage message = session1.createMessage(true).writeBodyBufferString("Message" + i);
         producer.send(message);
      }

      Map<String, Object>[] messages = queueControl.listMessages(null);

      assertEquals(100, messages.length);

      Map<String, Object>[] otherMessages = otherQueueControl.listMessages(null);

      assertEquals(100, otherMessages.length);

      long messageID = (Long) messages[0].get("messageID");

      assertTrue(queueControl.copyMessage(messageID, otherQueue.toString()));

      otherMessages = otherQueueControl.listMessages(null);

      assertEquals(101, otherMessages.length);

      messageID = (Long) otherMessages[100].get("messageID");

      //this validates copying of a paged message
      assertTrue(otherQueueControl.copyMessage(messageID, queue.toString()));
   }

   @Test
   public void testMoveMessageWhilstPagingAndConsuming() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      SimpleString otherAddress = RandomUtil.randomUUIDSimpleString();
      SimpleString otherQueue = RandomUtil.randomUUIDSimpleString();

      session1.createQueue(QueueConfiguration.of(queue).setAddress(address));
      session1.createQueue(QueueConfiguration.of(otherQueue).setAddress(otherAddress));

      QueueControl queueControl = createManagementControl(address, queue);

      QueueControl otherQueueControl = createManagementControl(otherAddress, otherQueue);

      int num = 1000;

      ClientProducer producer = session1.createProducer(address);
      for (int i = 0; i < num; i++) {
         ClientMessage message = session1.createMessage(true).writeBodyBufferString("Message" + i);
         producer.send(message);
      }

      ManagementCopyThread console = new ManagementCopyThread(queueControl, otherQueue.toString());
      ReceiverThread receiver = new ReceiverThread(queue, num, 0);
      console.start();
      receiver.start();

      receiver.join();
      console.stop = true;
      console.join();

      Map<String, Object>[] messages = otherQueueControl.listMessages(null);

      assertEquals(messages.length, console.copiedMessages);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultInVMConfig().setJMXManagementEnabled(true);

      server = addServer(ActiveMQServers.newActiveMQServer(config, mbeanServer, true));

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(5120).setMaxSizeBytes(10240).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setManagementBrowsePageSize(1000);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      server.start();

      locator = createInVMNonHALocator().setBlockOnNonDurableSend(false).setConsumerWindowSize(0);
      ClientSessionFactory sf = createSessionFactory(locator);
      session1 = sf.createSession(false, true, false);
      session1.start();
      session2 = sf.createSession(false, true, false);
      session2.start();
   }

   private class SenderThread extends Thread {

      private SimpleString address;
      private int num;
      private long delay;
      private volatile Exception error = null;

      private SenderThread(SimpleString address, int num, long delay) {
         this.address = address;
         this.num = num;
         this.delay = delay;
      }

      @Override
      public void run() {
         ClientProducer producer;

         byte[] body = new byte[128];
         ByteBuffer bb = ByteBuffer.wrap(body);
         for (int j = 1; j <= 128; j++) {
            bb.put(getSamplebyte(j));
         }

         try {
            producer = session1.createProducer(address);

            for (int i = 0; i < num; i++) {
               ClientMessage message = session1.createMessage(true);
               message.setPriority((byte) 1);
               ActiveMQBuffer buffer = message.getBodyBuffer();
               buffer.writeBytes(body);
               producer.send(message);
               try {
                  Thread.sleep(delay);
               } catch (InterruptedException e) {
                  //ignore
               }
            }
         } catch (Exception e) {
            error = e;
         }
      }

      public Exception getError() {
         return this.error;
      }
   }

   private class ReceiverThread extends Thread {

      private SimpleString queue;
      private int num;
      private long delay;
      private volatile Exception error = null;

      private ReceiverThread(SimpleString queue, int num, long delay) {
         this.queue = queue;
         this.num = num;
         this.delay = delay;
      }

      @Override
      public void run() {
         ClientConsumer consumer;
         try {
            consumer = session2.createConsumer(queue);

            for (int i = 0; i < num; i++) {
               ClientMessage message = consumer.receive(5000);
               message.acknowledge();
               session2.commit();
               try {
                  Thread.sleep(delay);
               } catch (InterruptedException e) {
                  //ignore
               }
            }
         } catch (Exception e) {
            error = e;
         }
      }

      public Exception getError() {
         return this.error;
      }
   }

   private class ManagementThread extends Thread {

      private QueueControl queueControl;
      private volatile boolean stop = false;
      private Exception error = null;

      private ManagementThread(QueueControl queueControl) {
         this.queueControl = queueControl;
      }

      @Override
      public void run() {
         try {
            while (!stop) {
               queueControl.countMessages(null);
               queueControl.listMessagesAsJSON(null);
               try {
                  Thread.sleep(1000);
               } catch (InterruptedException e) {
                  //ignore
               }
            }
         } catch (Exception e) {
            error = e;
         }
      }

      public Exception getError() {
         return error;
      }

      public void exit() {
         stop = true;
      }
   }

   private class ManagementCopyThread extends Thread {

      private QueueControl queueControl;
      private String queue;
      private volatile boolean stop = false;

      int copiedMessages = 0;
      private Exception error = null;

      private ManagementCopyThread(QueueControl queueControl, String queue) {
         this.queueControl = queueControl;
         this.queue = queue;
      }

      @Override
      public void run() {
         try {
            Random random = new Random(System.currentTimeMillis());
            while (!stop) {
               long messageID = random.nextInt(1000);
               boolean copied = queueControl.copyMessage(messageID, queue);
               System.out.println("messageID = " + messageID);
               if (copied) {
                  copiedMessages++;
               }
            }
         } catch (Exception e) {
            error = e;
         }
      }

      public Exception getError() {
         return error;
      }

      public void exit() {
         stop = true;
      }
   }
}
