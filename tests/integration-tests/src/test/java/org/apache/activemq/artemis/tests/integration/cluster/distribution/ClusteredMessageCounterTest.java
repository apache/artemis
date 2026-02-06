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
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.MessageCounterInfo;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClusteredMessageCounterTest extends ClusterTestBase {
   private AtomicInteger total = new AtomicInteger();
   private AtomicBoolean stopFlag = new AtomicBoolean();
   private Timer timer1 = new Timer();
   private Timer timer2 = new Timer();
   private int numMsg = 1000;
   private List<MessageCounterInfo> results = new ArrayList<>();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      setupServers();
      setupClusters();
      total.set(0);
      stopFlag.set(false);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      timer1.cancel();
      timer2.cancel();
      super.tearDown();
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
   }

   protected void setupClusters() {
      setupClusterConnection("cluster0", 0, 1, "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), false);
      setupClusterConnection("cluster1", 1, 0, "queues", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), false);
   }

   protected boolean isNetty() {
      return true;
   }

   @Override
   protected ConfigurationImpl createBasicConfig(final int serverID) {
      ConfigurationImpl config = super.createBasicConfig(serverID);
      Map<String, AddressSettings> addrSettingsMap = config.getAddressSettings();
      AddressSettings addrSettings = new AddressSettings();
      addrSettings.setMaxSizeBytes(10 * 1024);
      addrSettings.setPageSizeBytes(5 * 1024);
      addrSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      addrSettingsMap.put("queues", addrSettings);
      if (serverID == 1) {
         config.setMessageCounterEnabled(true);
      }
      return config;
   }

   @Test
   public void testNonDurableMessageAddedWithPaging() throws Exception {
      testMessageAddedWithPaging(false);
   }

   @Test
   public void testDurableMessageAddedWithPaging() throws Exception {
      testMessageAddedWithPaging(true);
   }

   //messages flow from one node to another, in paging mode
   //check the messageAdded is correct.
   private void testMessageAddedWithPaging(boolean durable) throws Exception {
      startServers(0, 1);
      numMsg = 100;

      try {
         setupSessionFactory(0, isNetty());
         setupSessionFactory(1, isNetty());

         createQueue(0, "queues", "queue0", null, false);
         createQueue(1, "queues", "queue0", null, false);

         waitForBindings(1, "queues", 1, 0, true);
         waitForBindings(0, "queues", 1, 0, false);

         addConsumer(1, 1, "queue0", null);

         waitForBindings(0, "queues", 1, 1, false);

         send(0, "queues", numMsg, durable, null);

         verifyReceiveAllOnSingleConsumer(true, 0, numMsg, 1);

         QueueControl control = servers[1].getManagementService().getQueueControl("queue0");

         //wait up to 30sec to allow the counter get updated
         long timeout = 30000;
         while (timeout > 0 && (numMsg != control.getMessagesAdded())) {
            Thread.sleep(1000);
            timeout -= 1000;
         }
         assertEquals(numMsg, control.getMessagesAdded());
      } finally {
         stopServers(0, 1);
      }
   }

   @Test
   public void testMessageCounterWithPaging() throws Exception {
      startServers(0, 1);

      try {
         setupSessionFactory(0, isNetty());
         setupSessionFactory(1, isNetty());

         createQueue(0, "queues", "queue0", null, false);
         createQueue(1, "queues", "queue0", null, false);

         waitForBindings(1, "queues", 1, 0, true);
         waitForBindings(0, "queues", 1, 0, false);

         Thread sendThread = new Thread(() -> {
            try {
               send(0, "queues", numMsg, true, null);
            } catch (Exception e) {
               e.printStackTrace();
            }
         });

         QueueControl control = servers[1].getManagementService().getQueueControl("queue0");
         ActiveMQServerControl serverControl = (ActiveMQServerControl) servers[1].getManagementService().getServerControl();
         serverControl.setMessageCounterSamplePeriod(300);

         CountDownLatch resultLatch = new CountDownLatch(40);

         MessageCounterCollector collector = new MessageCounterCollector(control, resultLatch);
         timer1.schedule(collector, 0);

         PeriodicalReceiver receiver = new PeriodicalReceiver(50, 1, 100);
         timer2.schedule(receiver, 0);

         sendThread.start();

         try {
            resultLatch.await(120, TimeUnit.SECONDS);
         } finally {
            stopFlag.set(true);
         }
         sendThread.join();
         //checking
         for (MessageCounterInfo info : results) {
            assertTrue(info.getCountDelta() >= 0, "countDelta should be positive " + info.getCountDelta() + dumpResults(results));
         }
      } finally {
         timer1.cancel();
         timer2.cancel();
         stopServers(0, 1);
      }
   }

   private String dumpResults(List<MessageCounterInfo> results) {
      StringBuilder sb = new StringBuilder("\n");
      for (int i = 0; i < results.size(); i++) {
         sb.append("result[" + i + "]: " + results.get(i).getCountDelta() + " " + results.get(i).getCount() + "\n");
      }
      return sb.toString();
   }

   //Periodically read the counter
   private class MessageCounterCollector extends TimerTask {
      private QueueControl queueControl;
      private CountDownLatch resultLatch;

      MessageCounterCollector(QueueControl queueControl, CountDownLatch resultLatch) {
         this.queueControl = queueControl;
         this.resultLatch = resultLatch;
      }

      @Override
      public void run() {
         if (stopFlag.get()) {
            return;
         }
         try {
            String result = queueControl.listMessageCounter();
            MessageCounterInfo info = MessageCounterInfo.fromJSON(result);
            results.add(info);
            resultLatch.countDown();
            if (info.getCountDelta() < 0) {
               //stop and make the test finish quick
               stopFlag.set(true);
               while (resultLatch.getCount() > 0) {
                  resultLatch.countDown();
               }
            }
         } catch (Exception e) {
            e.printStackTrace();
         } finally {
            if (!stopFlag.get()) {
               timer1.schedule(new MessageCounterCollector(this.queueControl, resultLatch), 200);
            }
         }
      }
   }

   //Periodically receive a number of messages
   private class PeriodicalReceiver extends TimerTask {
      private int batchSize;
      private int serverID;
      private long period;

      PeriodicalReceiver(int batchSize, int serverID, long period) {
         this.batchSize = batchSize;
         this.serverID = serverID;
         this.period = period;
      }

      @Override
      public void run() {
         if (stopFlag.get()) {
            return;
         }
         int num = 0;
         ClientSessionFactory sf = sfs[serverID];
         ClientSession session = null;
         ClientConsumer consumer = null;
         try {
            session = sf.createSession(false, true, false);
            consumer = session.createConsumer("queue0", null);
            session.start();
            for (; num < batchSize || stopFlag.get(); num++) {
               ClientMessage message = consumer.receive(2000);
               if (message == null) {
                  break;
               }
               message.acknowledge();
            }
            session.commit();
         } catch (ActiveMQException e) {
            e.printStackTrace();
         } finally {
            if (consumer != null) {
               try {
                  consumer.close();
               } catch (ActiveMQException e) {
                  e.printStackTrace();
               }
            }
            if (session != null) {
               try {
                  session.close();
               } catch (ActiveMQException e) {
                  e.printStackTrace();
               }
            }

            //we only receive (numMsg - 200) to avoid the paging being cleaned up
            //when all paged messages are consumed.
            if (!stopFlag.get() && total.addAndGet(num) < numMsg - 200) {
               timer2.schedule(new PeriodicalReceiver(this.batchSize, this.serverID, this.period), period);
            }
         }
      }
   }
}
