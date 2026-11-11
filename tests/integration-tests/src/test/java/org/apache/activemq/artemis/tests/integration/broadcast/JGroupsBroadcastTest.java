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
package org.apache.activemq.artemis.tests.integration.broadcast;

import org.apache.activemq.artemis.api.core.BroadcastEndpoint;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.ChannelBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.JGroupsBroadcastEndpoint;
import org.apache.activemq.artemis.api.core.jgroups.JChannelManager;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.jgroups.JChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class JGroupsBroadcastTest extends ArtemisTestCase {

   @AfterEach
   public void cleanupJChannel() {
      JChannelManager.getInstance().clear();
   }

   @BeforeEach
   public void prepareJChannel() {
      JChannelManager.getInstance().setLoopbackMessages(true);
   }

   @Test
   public void testRefCount() throws Exception {
      JChannel channel = null;
      JChannel newChannel = null;

      try {

         channel = new JChannel("udp.xml");

         String channelName1 = "channel1";

         BroadcastEndpointFactory jgroupsBroadcastCfg1 = new ChannelBroadcastEndpointFactory(channel, channelName1);

         BroadcastEndpoint channelEndpoint1 = jgroupsBroadcastCfg1.createBroadcastEndpoint();

         BroadcastEndpoint channelEndpoint2 = jgroupsBroadcastCfg1.createBroadcastEndpoint();

         BroadcastEndpoint channelEndpoint3 = jgroupsBroadcastCfg1.createBroadcastEndpoint();

         channelEndpoint1.close(true);

         assertTrue(channel.isOpen());

         channelEndpoint2.close(true);

         assertTrue(channel.isOpen());

         channelEndpoint3.close(true);

         assertTrue(channel.isOpen());

         channel.close();

         //after we close the last endpoint reference counting will close the channel so once we create a new one the
         // channel wrapper is recreated
         try {
            channelEndpoint2.openClient();
            fail("this should be closed");
         } catch (Exception e) {
         }

         newChannel = new JChannel("udp.xml");

         jgroupsBroadcastCfg1 = new ChannelBroadcastEndpointFactory(newChannel, channelName1);

         channelEndpoint1 = jgroupsBroadcastCfg1.createBroadcastEndpoint();

         channelEndpoint1.openClient();

      } catch (Exception e) {
         e.printStackTrace();
         throw e;
      } finally {
         try {
            channel.close();
         } catch (Throwable ignored) {

         }
         try {
            newChannel.close();
         } catch (Throwable ignored) {

         }
      }
   }


   @Test
   public void testConcurrentAccess() throws Exception {

      final CountDownLatch inClose = new CountDownLatch(1);
      final CountDownLatch doClose = new CountDownLatch(1);

      final Deque<Throwable> errors = new ConcurrentLinkedDeque<>();

      class InstrumentedJChannel extends JChannel {
         final AtomicBoolean closed = new AtomicBoolean(false);

         InstrumentedJChannel() throws Exception {
         }

         @Override
         public synchronized void close() {
            closed.set(true);
            inClose.countDown();
            try {
               doClose.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
               throw new RuntimeException(e);
            }
         }

         @Override
         public synchronized JChannel connect(String cluster_name) throws Exception {
            if (closed.get()) {
               throw new IllegalStateException("closed");
            }
            return this;
         }

         @Override
         public boolean isConnected() {
            return !closed.get();
         }
      }

      AtomicInteger numChannels = new AtomicInteger(0);
      class JGroupsBroadcastEndpointWithChannel extends JGroupsBroadcastEndpoint {
         JGroupsBroadcastEndpointWithChannel() {
            super(JChannelManager.getInstance(), "shared");
         }

         @Override
         public JChannel createChannel() throws Exception {
            numChannels.incrementAndGet();
            return new InstrumentedJChannel();
         }
      }

      ExecutorService executor = Executors.newFixedThreadPool(2);
      runAfter(executor::shutdownNow);

      try {
         executor.execute(() -> {
            try {
               JGroupsBroadcastEndpoint endpointWithSharedChannel = new JGroupsBroadcastEndpointWithChannel().initChannel();
               endpointWithSharedChannel.openClient();
               endpointWithSharedChannel.close(false);

            } catch (Exception e) {
               errors.add(e);
               throw new RuntimeException(e);
            }
         });

         executor.execute(() -> {

            try {

               // try and share while closing
               inClose.await(5, TimeUnit.SECONDS);

               JGroupsBroadcastEndpoint endpointWithSharedChannel = new JGroupsBroadcastEndpointWithChannel().initChannel();

               doClose.countDown();

               endpointWithSharedChannel.openClient();
               endpointWithSharedChannel.close(false);

            } catch (Exception e) {
               errors.add(e);
               throw new RuntimeException(e);
            } finally {
               doClose.countDown();
            }
         });

      } finally {
         executor.shutdown();
      }
      executor.awaitTermination(10, TimeUnit.SECONDS);
      executor.shutdownNow();
      assertTrue(errors.isEmpty());
      assertEquals(2, numChannels.get());
   }
}