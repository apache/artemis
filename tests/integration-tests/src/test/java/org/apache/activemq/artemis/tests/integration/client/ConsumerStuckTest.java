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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerStuckTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private final SimpleString QUEUE = SimpleString.of("ConsumerTestQueue");

   protected boolean isNetty() {
      return true;
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false, isNetty());

      server.start();
   }

   @Test
   public void testClientStuckTest() throws Exception {

      ServerLocator locator = createNettyNonHALocator().setConnectionTTL(1000).setClientFailureCheckPeriod(100).setConsumerWindowSize(10 * 1024 * 1024).setCallTimeout(1000);
      ClientSessionFactory sf = locator.createSessionFactory();
      ((ClientSessionFactoryImpl) sf).stopPingingAfterOne();

      RemotingConnectionImpl remotingConnection = (RemotingConnectionImpl) sf.getConnection();
      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10000;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = createTextMessage(session, "m" + i);
         producer.send(message);
      }

      final ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();

      final NettyConnection nettyConnection = (NettyConnection) remotingConnection.getTransportConnection();

      Thread tReceive = new Thread(() -> {
         boolean first = true;
         try {
            while (!Thread.interrupted()) {
               ClientMessage received = consumer.receive(500);
               logger.debug("Received {}", received);
               if (first) {
                  first = false;
                  nettyConnection.getNettyChannel().config().setAutoRead(false);
               }
               if (received != null) {
                  received.acknowledge();
               }
            }
         } catch (Throwable e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
         }
      });

      tReceive.start();

      try {

         assertEquals(1, server.getSessions().size());

         logger.debug("sessions = {}", server.getSessions().size());

         assertEquals(1, server.getConnectionCount());

         long timeout = System.currentTimeMillis() + 20000;

         long timeStart = System.currentTimeMillis();

         while (timeout > System.currentTimeMillis() && (server.getSessions().size() != 0 || server.getConnectionCount() != 0)) {
            Thread.sleep(10);
         }

         logger.debug("Time = {} time diff = {}, connections Size = {} sessions = {}",
            System.currentTimeMillis(), (System.currentTimeMillis() - timeStart), server.getConnectionCount(), server.getSessions().size());

         if (server.getSessions().size() != 0) {
            System.out.println(threadDump("Thread dump"));
            fail("The cleanup wasn't able to finish cleaning the session. It's probably stuck, look at the thread dump generated by the test for more information");
         }

         logger.debug("Size = {}", server.getConnectionCount());

         logger.debug("sessions = {}", server.getSessions().size());

         if (server.getSessions().size() != 0) {
            System.out.println(threadDump("Thread dump"));
            fail("The cleanup wasn't able to finish cleaning the session. It's probably stuck, look at the thread dump generated by the test for more information");
         }
         assertEquals(0, server.getConnectionCount());
      } finally {
         nettyConnection.getNettyChannel().config().setAutoRead(true);
         tReceive.interrupt();
         tReceive.join();
      }
   }

   @Test
   public void testClientStuckTestWithDirectDelivery() throws Exception {

      ServerLocator locator = createNettyNonHALocator().setConnectionTTL(1000).setClientFailureCheckPeriod(100).setConsumerWindowSize(10 * 1024 * 1024).setCallTimeout(1000);
      ClientSessionFactory sf = locator.createSessionFactory();
      ((ClientSessionFactoryImpl) sf).stopPingingAfterOne();

      RemotingConnectionImpl remotingConnection = (RemotingConnectionImpl) sf.getConnection();
      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      final int numMessages = 10000;

      final ClientConsumer consumer = session.createConsumer(QUEUE);
      session.start();

      final NettyConnection nettyConnection = (NettyConnection) remotingConnection.getTransportConnection();

      Thread tReceive = new Thread(() -> {
         boolean first = true;
         try {
            while (!Thread.interrupted()) {
               ClientMessage received = consumer.receive(500);
               logger.debug("Received {}", received);
               if (first) {
                  first = false;
                  nettyConnection.getNettyChannel().config().setAutoRead(false);
               }
               if (received != null) {
                  received.acknowledge();
               }
            }
         } catch (Throwable e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
         }
      });

      tReceive.start();

      Thread sender = new Thread(() -> {
         try (
            ServerLocator locator1 = createNettyNonHALocator();
            ClientSessionFactory factory = locator1.createSessionFactory();
            ClientSession session1 = factory.createSession(false, true, true, true);
            ClientProducer producer = session1.createProducer(QUEUE);
         ) {
            for (int i = 0; i < numMessages; i++) {
               ClientMessage message = createTextMessage(session1, "m" + i);
               producer.send(message);
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      });

      sender.start();

      try {

         long timeout = System.currentTimeMillis() + 20000;

         while (System.currentTimeMillis() < timeout && server.getSessions().size() != 2) {
            Thread.sleep(10);
         }

         assertEquals(2, server.getSessions().size());

         logger.debug("sessions = {}", server.getSessions().size());

         assertEquals(2, server.getConnectionCount());

         timeout = System.currentTimeMillis() + 20000;

         while (System.currentTimeMillis() < timeout && server.getSessions().size() != 1) {
            Thread.sleep(10);
         }

         logger.debug("Size = {}", server.getConnectionCount());

         logger.debug("sessions = {}", server.getSessions().size());

         if (server.getSessions().size() != 1) {
            System.out.println(threadDump("Thread dump"));
            fail("The cleanup wasn't able to finish cleaning the session. It's probably stuck, look at the thread dump generated by the test for more information");
         }

         sender.join();

         timeout = System.currentTimeMillis() + 20000;

         while (System.currentTimeMillis() < timeout && server.getConnectionCount() != 0) {
            Thread.sleep(10);
         }
         assertEquals(0, server.getConnectionCount());
      } finally {
         nettyConnection.getNettyChannel().config().setAutoRead(true);
         tReceive.interrupt();
         tReceive.join();
      }
   }

}
