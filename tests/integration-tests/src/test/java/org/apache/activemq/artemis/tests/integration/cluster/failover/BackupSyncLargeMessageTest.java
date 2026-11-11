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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class BackupSyncLargeMessageTest extends BackupSyncJournalTest {

   @Override
   protected void assertMessageBody(final int i, final ClientMessage message) {
      assertLargeMessageBody(i, message);
   }

   @Override
   protected ServerLocatorInternal getServerLocator() throws Exception {
      return (ServerLocatorInternal) super.getServerLocator().setMinLargeMessageSize(MIN_LARGE_MESSAGE);
   }

   @Override
   protected boolean supportsRetention() {
      return false;
   }

   @Override
   protected void setBody(final int i, final ClientMessage message) {
      setLargeMessageBody(i, message);
   }

   // ------------------------

   @Test
   public void testDeleteLargeMessages() throws Exception {
      // 200 will increase the odds of a failure
      setNumberOfMessages(200);
      File dir = new File(backupServer.getServer().getConfiguration().getLargeMessagesDirectory());
      assertEquals(0, getAllMessageFileIds(dir).size(), "Should not have any large messages... previous test failed to clean up?");
      createProducerSendSomeMessages();
      startBackupFinishSyncing();
      receiveMsgsInRange(0, getNumberOfMessages() / 2);
      finishSyncAndFailover();
      final int target = getNumberOfMessages() / 2;
      long timeout = System.currentTimeMillis() + 5000;
      while (getAllMessageFileIds(dir).size() != target && System.currentTimeMillis() < timeout) {
         Thread.sleep(50);
      }
      assertEquals(target, getAllMessageFileIds(dir).size(), "we really ought to delete these after delivery");
   }

   @Test
   public void testDeleteLargeMessagesDuringSync() throws Exception {
      setNumberOfMessages(200);
      File backupLMdir = new File(backupServer.getServer().getConfiguration().getLargeMessagesDirectory());
      File primaryLMDir = new File(primaryServer.getServer().getConfiguration().getLargeMessagesDirectory());
      assertEquals(0, getAllMessageFileIds(backupLMdir).size(), "Should not have any large messages... previous test failed to clean up?");
      createProducerSendSomeMessages();

      backupServer.start();
      waitForComponent(backupServer.getServer(), 5);
      receiveMsgsInRange(0, getNumberOfMessages() / 2);

      startBackupFinishSyncing();
      Thread.sleep(500);
      primaryServer.getServer().stop();
      backupServer.getServer().waitForActivation(10, TimeUnit.SECONDS);
      backupServer.stop();

      Set<Long> backupLM = getAllMessageFileIds(backupLMdir);
      Set<Long> primaryLM = getAllMessageFileIds(primaryLMDir);
      assertEquals(primaryLM, backupLM, "primary and backup should have the same files ");
      assertEquals(getNumberOfMessages() / 2, backupLM.size(), "we really ought to delete these after delivery: " + backupLM);
      assertEquals(getNumberOfMessages() / 2, getAllMessageFileIds(backupLMdir).size(), "we really ought to delete these after delivery");
   }

   /**
    * LargeMessages are passed from the client to the server in chunks. Here we test the backup starting the data
    * synchronization with the primary in the middle of a multiple chunks large message upload from the client to the
    * primary server.
    */
   @Test
   public void testBackupStartsWhenPrimaryIsReceivingLargeMessage() throws Exception {
      final ClientSession session = addClientSession(sessionFactory.createSession(true, true));
      session.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));
      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);
      final ClientMessage message = session.createMessage(true);

      String largeBody;
      {
         String randomString = "large >>> " + RandomUtil.randomUUIDString() + " <<< ";
         StringBuilder builder = new StringBuilder(randomString.length() * 1000);
         for (int i = 0; i < 1000; i++) {
            builder.append(randomString);
         }
         largeBody = builder.toString();
      }
      message.getBodyBuffer().writeString(largeBody);


      final AtomicBoolean caughtException = new AtomicBoolean(false);
      final CountDownLatch latch = new CountDownLatch(1);
      final CountDownLatch latch2 = new CountDownLatch(1);

      Runnable r = () -> {
         try {
            latch.countDown();
            producer.send(message);
            sendMessages(session, producer, 20);
            session.commit();
         } catch (ActiveMQException e) {
            e.printStackTrace();
            caughtException.set(true);
         } finally {
            latch2.countDown();
         }
      };
      Executors.defaultThreadFactory().newThread(r).start();
      waitForLatch(latch);
      startBackupFinishSyncing();
      ActiveMQTestBase.waitForLatch(latch2);
      crash(session);
      assertFalse(caughtException.get(), "no exceptions while sending message");

      session.start();
      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);
      ClientMessage largeMessageReceived = consumer.receive(2000);

      ActiveMQBuffer readBuffer = largeMessageReceived.getBodyBuffer();
      String readString = readBuffer.readString();
      assertEquals(largeBody, readString);

      receiveMessages(consumer, 0, 20, true);
      assertNull(consumer.receiveImmediate(), "there should be no more messages!");
      consumer.close();
      session.commit();
   }

   private Set<Long> getAllMessageFileIds(File dir) {
      Set<Long> idsOnBkp = new TreeSet<>();
      String[] fileList = dir.list();
      if (fileList != null) {
         for (String filename : fileList) {
            if (filename.endsWith(".msg")) {
               idsOnBkp.add(Long.valueOf(filename.split("\\.")[0]));
            }
         }
      }
      return idsOnBkp;
   }
}
