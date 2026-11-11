/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.network.NetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test will simulate a failure where the network card is gone. On that case the server should fail (as in stop)
 * and not hung. If you don't have sudoer access to ifutil, this test will skip. You should add sudoer on your
 * environment to run the test.
 */
public class NetworkFailureFailoverTest extends FailoverTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @BeforeAll
   public static void start() {
      NetUtil.skipIfNotSudo();
   }

   // 192.0.2.0 is reserved for documentation (and testing on this case).
   private static final String LIVE_IP = "192.0.2.0";

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      NetUtil.netUp(LIVE_IP);
      super.setUp();
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      try {
         super.tearDown();
      } finally {
         NetUtil.cleanup();
      }
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return getNettyAcceptorTransportConfiguration(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return getNettyConnectorTransportConfiguration(live);
   }

   @Override
   protected TransportConfiguration getNettyAcceptorTransportConfiguration(final boolean live) {
      Map<String, Object> server1Params = new HashMap<>();

      if (live) {
         server1Params.put(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT);
         server1Params.put(TransportConstants.HOST_PROP_NAME, LIVE_IP);
      } else {
         server1Params.put(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT);
         server1Params.put(TransportConstants.HOST_PROP_NAME, "localhost");
      }

      return new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, server1Params);
   }

   @Override
   protected TransportConfiguration getNettyConnectorTransportConfiguration(final boolean live) {
      Map<String, Object> server1Params = new HashMap<>();

      if (live) {
         server1Params.put(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT);
         server1Params.put(TransportConstants.HOST_PROP_NAME, LIVE_IP);
      } else {
         server1Params.put(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT);
         server1Params.put(TransportConstants.HOST_PROP_NAME, "localhost");
      }

      return new TransportConfiguration(NETTY_CONNECTOR_FACTORY, server1Params);
   }

   @Test
   public void testFailoverAfterNetFailure() throws Exception {
      final AtomicInteger sentMessages = new AtomicInteger(0);
      final AtomicInteger blockedAt = new AtomicInteger(0);

      assertTrue(NetUtil.checkIP(LIVE_IP));
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, LIVE_IP);
      TransportConfiguration tc = createTransportConfiguration(true, false, params);

      final AtomicInteger countSent = new AtomicInteger(0);

      primaryServer.addInterceptor((packet, connection) -> {
         //logger.debug("Received {}", packet);
         if (packet instanceof SessionSendMessage) {

            if (countSent.incrementAndGet() == 500) {
               try {
                  NetUtil.netDown(LIVE_IP);
                  logger.debug("Blocking traffic");
                  // Thread.sleep(3000); // this is important to let stuff to block
                  primaryServer.crash(true, false);
               } catch (Exception e) {
                  e.printStackTrace();
               }
               new Thread(() -> {
                  try {
                     System.err.println("Stopping server");
                  } catch (Exception e) {
                     e.printStackTrace();
                  }
               }).start();
            }
         }
         return true;
      });

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithHA(tc));

      locator.setBlockOnNonDurableSend(false);
      locator.setBlockOnDurableSend(true);
      locator.setBlockOnAcknowledge(false);
      locator.setReconnectAttempts(-1);
      locator.setConfirmationWindowSize(-1);
      locator.setProducerWindowSize(-1);
      locator.setConnectionTTL(1000);
      locator.setClientFailureCheckPeriod(100);
      ClientSessionFactoryInternal sfProducer = createSessionFactoryAndWaitForTopology(locator, 2);
      sfProducer.addFailureListener(new SessionFailureListener() {
         @Override
         public void beforeReconnect(ActiveMQException exception) {
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {

         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {

         }
      });

      ClientSession sessionProducer = createSession(sfProducer, true, true, 0);

      sessionProducer.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = sessionProducer.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 2001;
      final CountDownLatch latchReceived = new CountDownLatch(numMessages);

      ClientSessionFactoryInternal sfConsumer = createSessionFactoryAndWaitForTopology(locator, 2);

      final ClientSession sessionConsumer = createSession(sfConsumer, true, true, 0);
      final ClientConsumer consumer = sessionConsumer.createConsumer(FailoverTestBase.ADDRESS);

      sessionConsumer.start();

      final AtomicBoolean running = new AtomicBoolean(true);

      final Thread t = new Thread(() -> {
         int received = 0;
         int errors = 0;
         while (running.get() && received < numMessages) {
            try {
               ClientMessage msgReceived = consumer.receive(500);
               if (msgReceived != null) {
                  latchReceived.countDown();
                  msgReceived.acknowledge();
                  if (received++ % 100 == 0) {
                     logger.debug("Received {}", received);
                     sessionConsumer.commit();
                  }
               } else {
                  logger.debug("Null");
               }
            } catch (Throwable e) {
               errors++;
               if (errors > 10) {
                  break;
               }
               e.printStackTrace();
            }
         }
      });

      t.start();

      for (sentMessages.set(0); sentMessages.get() < numMessages; sentMessages.incrementAndGet()) {
         do {
            try {
               if (sentMessages.get() % 100 == 0) {
                  logger.debug("Sent {}", sentMessages.get());
               }
               producer.send(createMessage(sessionProducer, sentMessages.get(), true));
               break;
            } catch (Exception e) {
               sentMessages.decrementAndGet();
               new Exception("Exception on ending", e).printStackTrace();
            }
         }
         while (true);
      }

      // these may never be received. doing the count down where we blocked.
      for (int i = 0; i < blockedAt.get(); i++) {
         latchReceived.countDown();
      }

      assertTrue(latchReceived.await(1, TimeUnit.MINUTES));

      running.set(false);

      t.join();
   }


   private int countTopologyMembers(Topology topology) {
      int count = 0;
      for (TopologyMember m : topology.getMembers()) {
         count++;
         if (m.getBackup() != null) {
            count++;
         }
      }

      return count;
   }

   @Test
   public void testNetFailureConsume() throws Exception {
      final AtomicInteger sentMessages = new AtomicInteger(0);
      final AtomicInteger blockedAt = new AtomicInteger(0);

      assertTrue(NetUtil.checkIP(LIVE_IP));
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, LIVE_IP);
      TransportConfiguration tc = createTransportConfiguration(true, false, params);

      final AtomicInteger countSent = new AtomicInteger(0);

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithHA(tc));

      locator.setBlockOnNonDurableSend(false);
      locator.setBlockOnDurableSend(false);
      locator.setBlockOnAcknowledge(false);
      locator.setReconnectAttempts(-1);
      locator.setConfirmationWindowSize(-1);
      locator.setProducerWindowSize(-1);
      locator.setClientFailureCheckPeriod(100);
      locator.setConnectionTTL(1000);
      ClientSessionFactoryInternal sfProducer = createSessionFactoryAndWaitForTopology(locator, 2);

      Wait.assertEquals(2, () -> countTopologyMembers(locator.getTopology()));

      sfProducer.addFailureListener(new SessionFailureListener() {
         @Override
         public void beforeReconnect(ActiveMQException exception) {
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {

         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {

         }
      });

      ClientSession sessionProducer = createSession(sfProducer, true, true, 0);

      sessionProducer.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      ClientProducer producer = sessionProducer.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 2001;
      final CountDownLatch latchReceived = new CountDownLatch(numMessages);

      ClientSessionFactoryInternal sfConsumer = createSessionFactoryAndWaitForTopology(locator, 2);

      final ClientSession sessionConsumer = createSession(sfConsumer, true, true, 0);
      final ClientConsumer consumer = sessionConsumer.createConsumer(FailoverTestBase.ADDRESS);

      sessionConsumer.start();

      final AtomicBoolean running = new AtomicBoolean(true);

      final Thread t = new Thread(() -> {
         int received = 0;
         int errors = 0;
         while (running.get() && received < numMessages) {
            try {
               ClientMessage msgReceived = consumer.receive(500);
               if (msgReceived != null) {
                  latchReceived.countDown();
                  msgReceived.acknowledge();
                  if (++received % 100 == 0) {

                     if (received == 300) {
                        logger.debug("Shutting down IP");
                        NetUtil.netDown(LIVE_IP);
                        primaryServer.crash(true, false);
                     }
                     logger.debug("Received {}", received);
                     sessionConsumer.commit();
                  }
               } else {
                  logger.debug("Null");
               }
            } catch (Throwable e) {
               errors++;
               if (errors > 10) {
                  break;
               }
               e.printStackTrace();
            }
         }
      });


      for (sentMessages.set(0); sentMessages.get() < numMessages; sentMessages.incrementAndGet()) {
         do {
            try {
               if (sentMessages.get() % 100 == 0) {
                  logger.debug("Sent {}", sentMessages.get());
               }
               producer.send(createMessage(sessionProducer, sentMessages.get(), true));
               break;
            } catch (Exception e) {
               sentMessages.decrementAndGet();
               new Exception("Exception on ending", e).printStackTrace();
            }
         }
         while (true);
      }

      sessionProducer.close();


      t.start();

      // these may never be received. doing the count down where we blocked.
      for (int i = 0; i < blockedAt.get(); i++) {
         latchReceived.countDown();
      }

      assertTrue(latchReceived.await(1, TimeUnit.MINUTES));

      running.set(false);

      t.join();
   }

   @Test
   public void testFailoverCreateSessionOnFailure() throws Exception {
      final AtomicInteger sentMessages = new AtomicInteger(0);
      final AtomicInteger blockedAt = new AtomicInteger(0);

      assertTrue(NetUtil.checkIP(LIVE_IP));
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, LIVE_IP);
      TransportConfiguration tc = createTransportConfiguration(true, false, params);

      final AtomicInteger countSent = new AtomicInteger(0);

      final CountDownLatch latchDown = new CountDownLatch(1);

      primaryServer.addInterceptor((packet, connection) -> {
         //logger.debug("Received {}", packet);
         if (packet instanceof CreateSessionMessage) {

            if (countSent.incrementAndGet() == 50) {
               try {
                  NetUtil.netDown(LIVE_IP);
                  logger.debug("Blocking traffic");
                  blockedAt.set(sentMessages.get());
                  latchDown.countDown();
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }
         }
         return true;
      });

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithHA(tc));
      //locator.setDebugReconnects("CF_retry");

      locator.setBlockOnNonDurableSend(false);
      locator.setBlockOnDurableSend(false);
      locator.setBlockOnAcknowledge(false);
      locator.setReconnectAttempts(-1);
      locator.setConfirmationWindowSize(-1);
      locator.setProducerWindowSize(-1);
      locator.setClientFailureCheckPeriod(100);
      locator.setConnectionTTL(1000);
      final ClientSessionFactoryInternal sessionFactory = createSessionFactoryAndWaitForTopology(locator, 2);
      final AtomicInteger failed = new AtomicInteger(0);
      sessionFactory.addFailureListener(new SessionFailureListener() {
         @Override
         public void beforeReconnect(ActiveMQException exception) {
            if (failed.incrementAndGet() == 1) {
               Thread.currentThread().interrupt();
            }
            new Exception("producer before reconnect", exception).printStackTrace();
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {

         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {

         }
      });
      final int numSessions = 100;
      final CountDownLatch latchCreated = new CountDownLatch(numSessions);

      final AtomicBoolean running = new AtomicBoolean(true);

      final Thread t = new Thread("session-creator") {
         @Override
         public void run() {
            int received = 0;
            int errors = 0;
            while (running.get() && received < numSessions) {
               try {
                  ClientSession session = sessionFactory.createSession();
                  logger.debug("Creating session, currentLatch = {}", latchCreated.getCount());
                  session.close();
                  latchCreated.countDown();
               } catch (Throwable e) {
                  e.printStackTrace();
                  errors++;
               }
            }
         }
      };

      t.start();

      assertTrue(latchDown.await(1, TimeUnit.MINUTES));

      Thread.sleep(1000);

      logger.debug("Server crashed now!!!");

      primaryServer.crash(true, false);

      try {
         assertTrue(latchCreated.await(5, TimeUnit.MINUTES));

      } finally {
         running.set(false);

         t.join(TimeUnit.SECONDS.toMillis(30));
      }
   }

   @Test
   public void testInterruptFailingThread() throws Exception {
      final AtomicInteger sentMessages = new AtomicInteger(0);
      final AtomicInteger blockedAt = new AtomicInteger(0);

      assertTrue(NetUtil.checkIP(LIVE_IP));
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.HOST_PROP_NAME, LIVE_IP);
      TransportConfiguration tc = createTransportConfiguration(true, false, params);

      final AtomicInteger countSent = new AtomicInteger(0);

      final CountDownLatch latchBlocked = new CountDownLatch(1);

      primaryServer.addInterceptor((packet, connection) -> {
         //logger.debug("Received {}", packet);
         if (packet instanceof SessionSendMessage) {

            if (countSent.incrementAndGet() == 50) {
               try {
                  NetUtil.netDown(LIVE_IP);
                  logger.debug("Blocking traffic");
                  Thread.sleep(3000); // this is important to let stuff to block
                  blockedAt.set(sentMessages.get());
                  latchBlocked.countDown();
               } catch (Exception e) {
                  e.printStackTrace();
               }
               //                  new Thread()
               //                  {
               //                     public void run()
               //                     {
               //                        try
               //                        {
               //                           System.err.println("Stopping server");
               //                           // liveServer.stop();
               //                           liveServer.crash(true, false);
               //                        }
               //                        catch (Exception e)
               //                        {
               //                           e.printStackTrace();
               //                        }
               //                     }
               //                  }.start();
            }
         }
         return true;
      });

      final CountDownLatch failing = new CountDownLatch(1);
      final HashSet<Thread> setThread = new HashSet<>();

      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithHA(tc));

      locator.setBlockOnNonDurableSend(false);
      locator.setBlockOnDurableSend(false);
      locator.setBlockOnAcknowledge(false);
      locator.setReconnectAttempts(-1);
      locator.setConfirmationWindowSize(-1);
      locator.setProducerWindowSize(-1);
      locator.setClientFailureCheckPeriod(100);
      locator.setConnectionTTL(1000);
      ClientSessionFactoryInternal sfProducer = createSessionFactoryAndWaitForTopology(locator, 2);
      sfProducer.addFailureListener(new SessionFailureListener() {
         @Override
         public void beforeReconnect(ActiveMQException exception) {
            setThread.add(Thread.currentThread());
            failing.countDown();
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {

         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {

         }
      });

      final ClientSession sessionProducer = createSession(sfProducer, true, true, 0);

      sessionProducer.createQueue(QueueConfiguration.of(FailoverTestBase.ADDRESS));

      final ClientProducer producer = sessionProducer.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 10000;

      final AtomicBoolean running = new AtomicBoolean(true);
      final CountDownLatch messagesSentlatch = new CountDownLatch(numMessages);

      Thread t = new Thread("sendingThread") {
         @Override
         public void run() {

            while (sentMessages.get() < numMessages && running.get()) {
               try {
                  if (sentMessages.get() % 10 == 0) {
                     logger.debug("Sent {}", sentMessages.get());
                  }
                  producer.send(createMessage(sessionProducer, sentMessages.get(), true));
                  sentMessages.incrementAndGet();
                  messagesSentlatch.countDown();
               } catch (Throwable e) {
                  e.printStackTrace();
               }
            }

         }
      };

      t.start();

      assertTrue(latchBlocked.await(1, TimeUnit.MINUTES));

      assertTrue(failing.await(1, TimeUnit.MINUTES));

      for (int i = 0; i < 5; i++) {
         for (Thread tint : setThread) {
            tint.interrupt();
         }
         Thread.sleep(500);
      }

      primaryServer.crash(true, false);

      assertTrue(messagesSentlatch.await(3, TimeUnit.MINUTES));

      running.set(false);

      t.join();
   }



   @Override
   protected ClusterConnectionConfiguration createBasicClusterConfig(String connectorName,
                                                                         String... connectors) {
      List<String> connectors0 = new ArrayList<>();
      for (String c : connectors) {
         connectors0.add(c);
      }
      ClusterConnectionConfiguration clusterConnectionConfiguration = new ClusterConnectionConfiguration().
         setName("cluster1").setAddress("jms").setConnectorName(connectorName).
         setRetryInterval(1000).setDuplicateDetection(false).setMaxHops(1).setClientFailureCheckPeriod(100).setConnectionTTL(1000).
         setConfirmationWindowSize(1).setMessageLoadBalancingType(MessageLoadBalancingType.STRICT).
         setStaticConnectors(connectors0);

      return clusterConnectionConfiguration;
   }

}
