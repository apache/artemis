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
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonNumber;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonValue;

import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains tests for core management functionalities that are affected by a server in paging mode.
 */
public class ManagementWithPagingServerTest extends ManagementTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;
   private ClientSession session1;
   private ClientSession session2;
   private ServerLocator locator;
   private ClientSessionFactory sf;

   @Test
   public void testListMessagesAsJSON() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session1.createQueue(QueueConfiguration.of(queue).setAddress(address));

      QueueControl queueControl = createManagementControl(address, queue);

      int num = 1000;
      SenderRunnable sender = new SenderRunnable(address, num, 0);
      ReceiverRunnable receiver = new ReceiverRunnable(queue, num, 0);

      ExecutorService executorService = Executors.newFixedThreadPool(1);
      runAfter(executorService::shutdownNow);
      runAfter(sender::stop);
      runAfter(receiver::stop);

      executorService.execute(sender);
      sender.waitDone();
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

      executorService.execute(receiver);
      receiver.waitDone();
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

      ReceiverRunnable receiver = new ReceiverRunnable(queue, num, 1);
      ExecutorService executorService = Executors.newFixedThreadPool(1);
      runAfter(executorService::shutdownNow);
      runAfter(receiver::stop);
      executorService.execute(receiver);
      receiver.waitDone();
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
      SenderRunnable sender = new SenderRunnable(address, num, 1);
      ReceiverRunnable receiver = new ReceiverRunnable(queue, num, 2);
      ManagementRunnable console = new ManagementRunnable(queueControl);

      ExecutorService executorService = Executors.newFixedThreadPool(2);
      runAfter(executorService::shutdownNow);
      runAfter(sender::stop);
      runAfter(receiver::stop);
      runAfter(console::stop);

      executorService.execute(sender);
      executorService.execute(console);

      sender.waitDone();
      assertNull(sender.getError());

      executorService.execute(receiver);

      receiver.waitDone();
      assertNull(receiver.getError());

      console.stop();
      console.waitDone();

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

      int num = 10;

      ClientProducer producer = session1.createProducer(address);
      for (int i = 0; i < num; i++) {
         ClientMessage message = session1.createMessage(true).writeBodyBufferString("Message" + i);
         producer.send(message);
      }

      ExecutorService executorService = Executors.newFixedThreadPool(2);
      runAfter(executorService::shutdownNow);

      ManagementCopyRunnable console = new ManagementCopyRunnable(queue.toString(), queueControl, otherQueue.toString());
      runAfter(console::stop);

      ReceiverRunnable receiver = new ReceiverRunnable(queue, num, 0);
      runAfter(receiver::stop);
      executorService.execute(receiver);
      executorService.execute(console);

      receiver.waitDone();

      console.stop();
      console.waitDone();

      Map<String, Object>[] messages = otherQueueControl.listMessages(null);

      assertEquals(messages.length, console.copiedMessages);
   }

   /**
    * Reproduction for ARTEMIS-6179: QueueImpl#deleteReference() is still {@code synchronized} and calls
    * iterQueue(), which locks depageLock -- while QueueImpl#depage() locks depageLock first and then enters a
    * synchronized(this) block. Racing QueueControl#removeMessage() (-> deleteReference()) against depaging
    * (triggered here by the ReceiverThread acking messages) can deadlock the two threads against each other.
    * This mirrors testMoveMessageWhilstPagingAndConsuming(), which caught the equivalent bug for copyReference()
    * (ARTEMIS-5376), replacing copyMessage with removeMessage. Detection uses the JVM's own deadlock detector
    * (ThreadMXBean) instead of a fixed timeout, since a hang here is the failure itself.
    */
   @Test
   public void testRemoveMessageWhilstPagingAndConsuming() throws Exception {
      final int messagesPerIteration = 100;
      final int maxIterations = 2;

      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session1.createQueue(QueueConfiguration.of(queue).setAddress(address));

      QueueControl queueControl = createManagementControl(address, queue);

      ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

      // A single remover is sufficient: QueueControlImpl#removeMessage() is serialized broker-wide through
      // ActiveMQServerImpl's managementLock, so extra concurrent removers would just contend on that lock
      // without increasing the odds of racing depage().
      ManagementRemoveThread remover = new ManagementRemoveThread(queueControl);

      ExecutorService executorService = Executors.newFixedThreadPool(3);
      runAfter(executorService::shutdownNow);
      runAfter(remover::stop);

      executorService.execute(remover);

      long[] deadlockedIds = null;

      for (int iteration = 0; iteration < maxIterations; iteration++) {
         logger.info("iteration : {}", iteration);
         SenderRunnable sender = new SenderRunnable(address, messagesPerIteration, 0);
         ReceiverRunnable receiver = new ReceiverRunnable(queue, messagesPerIteration, 0);

         executorService.execute(sender);
         executorService.execute(receiver);

         sender.waitDone();
         receiver.waitDone();

         deadlockedIds = threadMXBean.findDeadlockedThreads();

         if (deadlockedIds != null) {
            StringBuilder sb = new StringBuilder("Deadlock detected between removeMessage() and depage():\n");
            for (long id : deadlockedIds) {
               ThreadInfo info = threadMXBean.getThreadInfo(id, Integer.MAX_VALUE);
               sb.append(info).append('\n');
            }
            fail(sb.toString());
         }
      }

      remover.stop();
      remover.waitDone();
      assertNull(remover.getError());

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
      sf = createSessionFactory(locator);
      session1 = sf.createSession(false, true, false);
      session1.start();
      session2 = sf.createSession(false, true, false);
      session2.start();
   }

   protected abstract class AbstractRunnable implements Runnable {
      protected volatile boolean stop = false;
      protected volatile Exception error = null;
      protected final CountDownLatch done = new CountDownLatch(1);

      public boolean waitDone() throws Exception {
         return done.await(10, TimeUnit.SECONDS);
      }

      protected void done() {
         done.countDown();
      }

      public void stop() {
         stop = true;
      }

      public Exception getError() {
         return this.error;
      }
   }

   private class SenderRunnable extends AbstractRunnable {

      private SimpleString address;
      private int num;
      private long delay;
      private volatile Exception error = null;
      ClientSession sessionSender;

      private SenderRunnable(SimpleString address, int num, long delay) throws Exception {
         this.address = address;
         this.num = num;
         this.delay = delay;
         sessionSender = sf.createSession(false, false, false);
      }

      @Override
      public void run() {
         try {
            ClientProducer producer;

            byte[] body = new byte[128];
            ByteBuffer bb = ByteBuffer.wrap(body);
            for (int j = 1; j <= 128; j++) {
               bb.put(getSamplebyte(j));
            }

            try {
               producer = sessionSender.createProducer(address);

               for (int i = 0; i < num && !stop; i++) {
                  ClientMessage message = sessionSender.createMessage(true);
                  message.setPriority((byte) 1);
                  ActiveMQBuffer buffer = message.getBodyBuffer();
                  buffer.writeBytes(body);
                  producer.send(message);
                  if ((i + 1) % 100 == 0) {
                     sessionSender.commit();
                  }
                  try {
                     Thread.sleep(delay);
                  } catch (InterruptedException e) {
                     //ignore
                  }
               }
               sessionSender.commit();
            } catch (Exception e) {
               error = e;
            }
         } finally {
            done();
         }
      }

   }

   private class ReceiverRunnable extends AbstractRunnable {

      private SimpleString queue;
      private int num;
      private long delay;
      private ClientSession sessionConsumer;

      private ReceiverRunnable(SimpleString queue, int num, long delay) throws Exception {
         this.queue = queue;
         this.num = num;
         this.delay = delay;
         this.sessionConsumer = sf.createSession(false, true, false);
      }

      @Override
      public void run() {
         ClientConsumer consumer;
         try {
            consumer = sessionConsumer.createConsumer(queue);
            sessionConsumer.start();

            for (int i = 0; i < num && !stop; i++) {
               ClientMessage message = consumer.receive(5000);
               message.acknowledge();
               sessionConsumer.commit();
               try {
                  Thread.sleep(delay);
               } catch (InterruptedException e) {
                  //ignore
               }
            }
         } catch (Exception e) {
            error = e;
         } finally {
            done();
         }
      }
   }

   private class ManagementRunnable extends AbstractRunnable {

      private QueueControl queueControl;

      private ManagementRunnable(QueueControl queueControl) {
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
         } finally {
            done();
         }
      }
   }

   private class ManagementCopyRunnable extends AbstractRunnable {

      private QueueControl queueControl;
      private String queue;
      private String originalQueue;
      Queue targetQueue;

      int copiedMessages = 0;

      private ManagementCopyRunnable(String originalQueue, QueueControl queueControl, String queue) {
         this.queueControl = queueControl;
         this.queue = queue;
         this.originalQueue = originalQueue;
         targetQueue = server.locateQueue(queue);
      }

      @Override
      public void run() {
         try {
            Random random = new Random(System.currentTimeMillis());
            while (!stop && copiedMessages < 100) {
               long messageID = random.nextInt(1000);
               boolean copied = queueControl.copyMessage(messageID, queue);
               if (copied) {
                  copiedMessages++;
                  logger.info("Copied message ID {}, totalCopied so far = {}", messageID, copiedMessages);
                  targetQueue.forEach(r -> {
                     logger.info("containing {}", r.getMessage());
                  });
               }
            }
         } catch (Exception e) {
            error = e;
         } finally {
            done();
         }
      }
   }

   private class ManagementRemoveThread extends AbstractRunnable {

      private QueueControl queueControl;

      private ManagementRemoveThread(QueueControl queueControl) {
         this.queueControl = queueControl;
      }

      @Override
      public void run() {
         try {
            while (!stop) {
               queueControl.removeMessage(0);
            }
         } catch (Exception e) {
            error = e;
         } finally {
            done();
         }
      }
   }
}
