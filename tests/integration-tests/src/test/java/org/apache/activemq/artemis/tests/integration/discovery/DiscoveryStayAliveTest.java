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
package org.apache.activemq.artemis.tests.integration.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.BroadcastEndpoint;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.core.cluster.DiscoveryGroup;
import org.apache.activemq.artemis.core.server.cluster.impl.BroadcastGroupImpl;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This is to make sure discovery works fine even when garbled data is sent
 */
public class DiscoveryStayAliveTest extends DiscoveryBaseTest {

   ScheduledExecutorService scheduledExecutorService;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      scheduledExecutorService = new ScheduledThreadPoolExecutor(1, new ActiveMQThreadFactory("ActiveMQ-scheduled-threads", false, Thread.currentThread().getContextClassLoader()));

   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      scheduledExecutorService.shutdown();
      super.tearDown();
   }

   @Test
   public void testDiscoveryRunning() throws Throwable {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      final DiscoveryGroup dg = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), InetAddress.getByName("localhost"), groupAddress, groupPort, timeout);

      final AtomicInteger errors = new AtomicInteger(0);
      Thread t = new Thread(() -> {
         try {
            dg.internalRunning();
         } catch (Throwable e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }

      });
      t.start();

      BroadcastGroupImpl bg = null;

      try {

         bg = new BroadcastGroupImpl(new FakeNodeManager("test-nodeID"), RandomUtil.randomUUIDString(), 1, scheduledExecutorService, new UDPBroadcastEndpointFactory().setGroupAddress(address1).
            setGroupPort(groupPort));

         bg.start();

         bg.addConnector(generateTC());

         for (int i = 0; i < 10; i++) {
            BroadcastEndpointFactory factoryEndpoint = new UDPBroadcastEndpointFactory().setGroupAddress(address1).
               setGroupPort(groupPort).setLocalBindAddress("localhost");
            sendBadData(factoryEndpoint);

         }
         Thread.sleep(100);
         assertTrue(t.isAlive());
         assertEquals(0, errors.get());
      } finally {

         if (bg != null) {
            bg.stop();
         }

         if (dg != null) {
            dg.stop();
         }

         t.join(1000);

         // it will retry for a limited time only
         for (int i = 0; t.isAlive() && i < 100; i++) {
            t.interrupt();
            Thread.sleep(100);
         }
      }
   }

   private static void sendBadData(BroadcastEndpointFactory factoryEndpoint) throws Exception {
      BroadcastEndpoint endpoint = factoryEndpoint.createBroadcastEndpoint();

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(500);

      buffer.writeString("This is a test1!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
      buffer.writeString("This is a test2!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

      byte[] bytes = new byte[buffer.writerIndex()];

      buffer.readBytes(bytes);

      // messing up with the string!!!
      for (int i = bytes.length - 10; i < bytes.length; i++) {
         bytes[i] = 0;
      }

      endpoint.openBroadcaster();

      endpoint.broadcast(bytes);

      endpoint.close(true);
   }
}
