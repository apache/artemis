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
package org.apache.activemq.transport.failover;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.ServerSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TransactionRolledBackException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.AutoFailTestSupport;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.apache.activemq.transport.TransportListener;
import org.apache.activemq.util.SocketProxy;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// see https://issues.apache.org/activemq/browse/AMQ-2473

// https://issues.apache.org/activemq/browse/AMQ-2590
@RunWith(BMUnitRunner.class)
public class FailoverTransactionTest extends OpenwireArtemisBaseTest {

   private static final Logger LOG = LoggerFactory.getLogger(FailoverTransactionTest.class);
   private static final String QUEUE_NAME = "Failover.WithTx";
   private String url = newURI(0);

   private static final AtomicBoolean doByteman = new AtomicBoolean(false);
   private static CountDownLatch brokerStopLatch;

   private static SocketProxy proxy;
   private static boolean firstSend;
   private static int count;

   private static volatile EmbeddedJMS broker;

   @Before
   public void setUp() throws Exception {
      doByteman.set(false);
      brokerStopLatch = new CountDownLatch(1);
   }

   @After
   public void tearDown() throws Exception {
      doByteman.set(false);
      stopBroker();
   }

   public void stopBroker() throws Exception {
      if (broker != null) {
         broker.stop();
      }
   }

   private void startCleanBroker() throws Exception {
      startBroker();
   }

   public void startBroker() throws Exception {
      broker = createBroker();
      broker.start();
   }

   public void configureConnectionFactory(ActiveMQConnectionFactory factory) {
      // nothing to do
   }

   @Test
   public void testFailoverProducerCloseBeforeTransaction() throws Exception {
      LOG.info(this + " running test testFailoverProducerCloseBeforeTransaction");
      startCleanBroker();
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      Queue destination = session.createQueue(QUEUE_NAME);

      MessageConsumer consumer = session.createConsumer(destination);
      produceMessage(session, destination);

      // restart to force failover and connection state recovery before the commit
      broker.stop();
      startBroker();

      session.commit();
      Assert.assertNotNull("we got the message", consumer.receive(20000));
      session.commit();
      connection.close();
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response and stop the broker",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processCommitTransactionOnePhase",
         targetLocation = "EXIT",
         action = "org.apache.activemq.transport.failover.FailoverTransactionTest.holdResponseAndStopBroker($0)")})
   public void testFailoverCommitReplyLost() throws Exception {
      LOG.info(this + " running test testFailoverCommitReplyLost");

      broker = createBroker();
      startBrokerWithDurableQueue();
      doByteman.set(true);

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      connection.start();
      final Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      Queue destination = session.createQueue(QUEUE_NAME);

      MessageConsumer consumer = session.createConsumer(destination);
      produceMessage(session, destination);

      final CountDownLatch commitDoneLatch = new CountDownLatch(1);
      // broker will die on commit reply so this will hang till restart
      new Thread(() -> {
         LOG.info("doing async commit...");
         try {
            session.commit();
         } catch (JMSException e) {
            Assert.assertTrue(e instanceof TransactionRolledBackException);
            LOG.info("got commit exception: ", e);
         }
         commitDoneLatch.countDown();
         LOG.info("done async commit");
      }).start();

      // will be stopped by the plugin
      brokerStopLatch.await(60, TimeUnit.SECONDS);
      doByteman.set(false);
      broker = createBroker();
      broker.start();

      Assert.assertTrue("tx committed through failover", commitDoneLatch.await(30, TimeUnit.SECONDS));

      // new transaction
      Message msg = consumer.receive(20000);
      LOG.info("Received: " + msg);
      Assert.assertNotNull("we got the message", msg);
      Assert.assertNull("we got just one message", consumer.receive(2000));
      session.commit();
      consumer.close();
      connection.close();

      // ensure no dangling messages with fresh broker etc
      broker.stop();

      LOG.info("Checking for remaining/hung messages..");
      broker = createBroker();
      broker.start();

      // after restart, ensure no dangling messages
      cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      connection = cf.createConnection();
      connection.start();
      Session session2 = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      consumer = session2.createConsumer(destination);
      msg = consumer.receive(1000);
      if (msg == null) {
         msg = consumer.receive(5000);
      }
      LOG.info("Received: " + msg);
      Assert.assertNull("no messges left dangling but got: " + msg, msg);
      connection.close();
   }

   @Test
   public void testFailoverCommitReplyLostWithDestinationPathSeparator() throws Exception {
      //the original test validates destinations using forward slash (/) as
      //separators instead of dot (.). The broker internally uses a plugin
      //called DestinationPathSeparatorBroker to convert every occurrence of
      // "/" into "." inside the server.
      //Artemis doesn't support "/" so far and this test doesn't make sense therefore.
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response and stop the broker",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processMessage",
         targetLocation = "EXIT",
         action = "org.apache.activemq.transport.failover.FailoverTransactionTest.holdResponseAndStopBroker($0)")})
   public void testFailoverSendReplyLost() throws Exception {
      LOG.info(this + " running test testFailoverSendReplyLost");
      broker = createBroker();
      startBrokerWithDurableQueue();
      doByteman.set(true);

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")?jms.watchTopicAdvisories=false");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      connection.start();
      final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final Queue destination = session.createQueue(QUEUE_NAME);

      MessageConsumer consumer = session.createConsumer(destination);
      final CountDownLatch sendDoneLatch = new CountDownLatch(1);
      // broker will die on send reply so this will hang till restart
      new Thread(() -> {
         LOG.info("doing async send...");
         try {
            produceMessage(session, destination);
         } catch (JMSException e) {
            //assertTrue(e instanceof TransactionRolledBackException);
            LOG.error("got send exception: ", e);
            Assert.fail("got unexpected send exception" + e);
         }
         sendDoneLatch.countDown();
         LOG.info("done async send");
      }).start();

      // will be stopped by the plugin
      brokerStopLatch.await(60, TimeUnit.SECONDS);
      doByteman.set(false);
      broker = createBroker();
      LOG.info("restarting....");
      broker.start();

      Assert.assertTrue("message sent through failover", sendDoneLatch.await(30, TimeUnit.SECONDS));

      // new transaction
      Message msg = consumer.receive(20000);
      LOG.info("Received: " + msg);
      Assert.assertNotNull("we got the message", msg);
      Assert.assertNull("we got just one message", consumer.receive(2000));
      consumer.close();
      connection.close();

      // ensure no dangling messages with fresh broker etc
      broker.stop();

      LOG.info("Checking for remaining/hung messages with second restart..");
      broker = createBroker();
      broker.start();

      // after restart, ensure no dangling messages
      cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      connection = cf.createConnection();
      connection.start();
      Session session2 = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      consumer = session2.createConsumer(destination);
      msg = consumer.receive(1000);
      if (msg == null) {
         msg = consumer.receive(5000);
      }
      LOG.info("Received: " + msg);
      Assert.assertNull("no messges left dangling but got: " + msg, msg);
      connection.close();
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response and stop the broker",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processMessage",
         targetLocation = "EXIT",
         action = "org.apache.activemq.transport.failover.FailoverTransactionTest.holdResponseAndStopProxyOnFirstSend($0)")})
   public void testFailoverConnectionSendReplyLost() throws Exception {
      LOG.info(this + " running test testFailoverConnectionSendReplyLost");
      broker = createBroker();
      proxy = new SocketProxy();
      firstSend = true;
      startBrokerWithDurableQueue();

      proxy.setTarget(new URI(url));
      proxy.open();
      doByteman.set(true);

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + proxy.getUrl().toASCIIString() + ")?jms.watchTopicAdvisories=false");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      connection.start();
      final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final Queue destination = session.createQueue(QUEUE_NAME);

      MessageConsumer consumer = session.createConsumer(destination);
      final CountDownLatch sendDoneLatch = new CountDownLatch(1);
      // proxy connection will die on send reply so this will hang on failover reconnect till open
      new Thread(() -> {
         LOG.info("doing async send...");
         try {
            produceMessage(session, destination);
         } catch (JMSException e) {
            //assertTrue(e instanceof TransactionRolledBackException);
            LOG.info("got send exception: ", e);
         }
         sendDoneLatch.countDown();
         LOG.info("done async send");
      }).start();

      // will be closed by the plugin
      Assert.assertTrue("proxy was closed", proxy.waitUntilClosed(30));
      LOG.info("restarting proxy");
      proxy.open();

      Assert.assertTrue("message sent through failover", sendDoneLatch.await(30, TimeUnit.SECONDS));

      Message msg = consumer.receive(20000);
      LOG.info("Received: " + msg);
      Assert.assertNotNull("we got the message", msg);
      Assert.assertNull("we got just one message", consumer.receive(2000));
      consumer.close();
      connection.close();

      // ensure no dangling messages with fresh broker etc
      broker.stop();

      LOG.info("Checking for remaining/hung messages with restart..");
      broker = createBroker();
      broker.start();

      // after restart, ensure no dangling messages
      cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      connection = cf.createConnection();
      connection.start();
      Session session2 = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      consumer = session2.createConsumer(destination);
      msg = consumer.receive(1000);
      if (msg == null) {
         msg = consumer.receive(5000);
      }
      LOG.info("Received: " + msg);
      Assert.assertNull("no messges left dangling but got: " + msg, msg);
      connection.close();
      proxy.close();
   }

   @Test
   public void testFailoverProducerCloseBeforeTransactionFailWhenDisabled() throws Exception {
      LOG.info(this + " running test testFailoverProducerCloseBeforeTransactionFailWhenDisabled");
      startCleanBroker();
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")?trackTransactionProducers=false");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      Queue destination = session.createQueue(QUEUE_NAME);

      MessageConsumer consumer = session.createConsumer(destination);
      produceMessage(session, destination);

      // restart to force failover and connection state recovery before the commit
      broker.stop();
      startBroker();

      session.commit();

      // without tracking producers, message will not be replayed on recovery
      Assert.assertNull("we got the message", consumer.receive(5000));
      session.commit();
      connection.close();
   }

   @Test
   public void testFailoverMultipleProducerCloseBeforeTransaction() throws Exception {
      LOG.info(this + " running test testFailoverMultipleProducerCloseBeforeTransaction");
      startCleanBroker();
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      connection.start();
      Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      Queue destination = session.createQueue(QUEUE_NAME);

      MessageConsumer consumer = session.createConsumer(destination);
      MessageProducer producer;
      TextMessage message;
      final int count = 10;
      for (int i = 0; i < count; i++) {
         producer = session.createProducer(destination);
         message = session.createTextMessage("Test message: " + count);
         producer.send(message);
         producer.close();
      }

      // restart to force failover and connection state recovery before the commit
      broker.stop();
      startBroker();

      session.commit();
      for (int i = 0; i < count; i++) {
         Assert.assertNotNull("we got all the message: " + count, consumer.receive(20000));
      }
      session.commit();
      connection.close();
   }

   // https://issues.apache.org/activemq/browse/AMQ-2772
   @Test
   public void testFailoverWithConnectionConsumer() throws Exception {
      LOG.info(this + " running test testFailoverWithConnectionConsumer");
      startCleanBroker();
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      connection.start();
      final CountDownLatch connectionConsumerGotOne = new CountDownLatch(1);

      try {
         Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
         Queue destination = session.createQueue(QUEUE_NAME);

         final Session poolSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         connection.createConnectionConsumer(destination, null, () -> new ServerSession() {
            @Override
            public Session getSession() throws JMSException {
               return poolSession;
            }

            @Override
            public void start() throws JMSException {
               connectionConsumerGotOne.countDown();
               poolSession.run();
            }
         }, 1);

         MessageConsumer consumer = session.createConsumer(destination);
         MessageProducer producer;
         TextMessage message;
         final int count = 10;
         for (int i = 0; i < count; i++) {
            producer = session.createProducer(destination);
            message = session.createTextMessage("Test message: " + count);
            producer.send(message);
            producer.close();
         }

         // restart to force failover and connection state recovery before the commit
         broker.stop();
         startBroker();

         session.commit();
         for (int i = 0; i < count - 1; i++) {
            Message received = consumer.receive(20000);
            Assert.assertNotNull("Failed to get message: " + count, received);
         }
         session.commit();
      } finally {
         connection.close();
      }

      Assert.assertTrue("connectionconsumer did not get a message", connectionConsumerGotOne.await(10, TimeUnit.SECONDS));
   }

   //   @Test
   //   @BMRules(
   //           rules = {
   //                   @BMRule(
   //                           name = "set no return response and stop the broker",
   //                           targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
   //                           targetMethod = "processMessageAck",
   //                           targetLocation = "ENTRY",
   //                           action = "org.apache.activemq.transport.failover.FailoverTransactionTest.holdResponseAndStopBroker($0)")
   //           }
   //   )
   //   public void testFailoverConsumerAckLost() throws Exception {
   //      LOG.info(this + " running test testFailoverConsumerAckLost");
   //      // as failure depends on hash order of state tracker recovery, do a few times
   //      for (int i = 0; i < 3; i++) {
   //         try {
   //            LOG.info("Iteration: " + i);
   //            doTestFailoverConsumerAckLost(i);
   //         }
   //         finally {
   //            stopBroker();
   //         }
   //      }
   //   }
   //
   public void doTestFailoverConsumerAckLost(final int pauseSeconds) throws Exception {
      broker = createBroker();
      broker.start();
      brokerStopLatch = new CountDownLatch(1);
      doByteman.set(true);

      Vector<Connection> connections = new Vector<>();
      Connection connection = null;
      Message msg = null;
      Queue destination = null;
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      try {
         configureConnectionFactory(cf);
         connection = cf.createConnection();
         connection.start();
         connections.add(connection);
         final Session producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         destination = producerSession.createQueue(QUEUE_NAME + "?consumer.prefetchSize=1");

         connection = cf.createConnection();
         connection.start();
         connections.add(connection);
         final Session consumerSession1 = connection.createSession(true, Session.SESSION_TRANSACTED);

         connection = cf.createConnection();
         connection.start();
         connections.add(connection);
         final Session consumerSession2 = connection.createSession(true, Session.SESSION_TRANSACTED);

         final MessageConsumer consumer1 = consumerSession1.createConsumer(destination);
         final MessageConsumer consumer2 = consumerSession2.createConsumer(destination);

         produceMessage(producerSession, destination);
         produceMessage(producerSession, destination);

         final Vector<Message> receivedMessages = new Vector<>();
         final CountDownLatch commitDoneLatch = new CountDownLatch(1);
         final AtomicBoolean gotTransactionRolledBackException = new AtomicBoolean(false);
         Thread t = new Thread("doTestFailoverConsumerAckLost(" + pauseSeconds + ")") {
            @Override
            public void run() {
               LOG.info("doing async commit after consume...");
               try {
                  Message msg = consumer1.receive(20000);
                  LOG.info("consumer1 first attempt got message: " + msg);
                  receivedMessages.add(msg);

                  // give some variance to the runs
                  TimeUnit.SECONDS.sleep(pauseSeconds * 2);

                  // should not get a second message as there are two messages and two consumers
                  // and prefetch=1, but with failover and unordered connection restore it can get the second
                  // message.

                  // For the transaction to complete it needs to get the same one or two messages
                  // again so that the acks line up.
                  // If redelivery order is different, the commit should fail with an ex
                  //
                  msg = consumer1.receive(5000);
                  LOG.info("consumer1 second attempt got message: " + msg);
                  if (msg != null) {
                     receivedMessages.add(msg);
                  }

                  LOG.info("committing consumer1 session: " + receivedMessages.size() + " messsage(s)");
                  try {
                     consumerSession1.commit();
                  } catch (JMSException expectedSometimes) {
                     LOG.info("got exception ex on commit", expectedSometimes);
                     if (expectedSometimes instanceof TransactionRolledBackException) {
                        gotTransactionRolledBackException.set(true);
                        // ok, message one was not replayed so we expect the rollback
                     } else {
                        throw expectedSometimes;
                     }

                  }
                  commitDoneLatch.countDown();
                  LOG.info("done async commit");
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }
         };
         t.start();

         // will be stopped by the plugin
         brokerStopLatch.await(60, TimeUnit.SECONDS);
         t.join(30000);
         if (t.isAlive()) {
            t.interrupt();
            Assert.fail("Thread " + t.getName() + " is still alive");
         }
         broker = createBroker();
         broker.start();
         doByteman.set(false);

         Assert.assertTrue("tx committed through failover", commitDoneLatch.await(30, TimeUnit.SECONDS));

         LOG.info("received message count: " + receivedMessages.size());

         // new transaction
         msg = consumer1.receive(gotTransactionRolledBackException.get() ? 5000 : 20000);
         LOG.info("post: from consumer1 received: " + msg);
         if (gotTransactionRolledBackException.get()) {
            Assert.assertNotNull("should be available again after commit rollback ex", msg);
         } else {
            Assert.assertNull("should be nothing left for consumer as receive should have committed", msg);
         }
         consumerSession1.commit();

         if (gotTransactionRolledBackException.get() || !gotTransactionRolledBackException.get() && receivedMessages.size() == 1) {
            // just one message successfully consumed or none consumed
            // consumer2 should get other message
            msg = consumer2.receive(10000);
            LOG.info("post: from consumer2 received: " + msg);
            Assert.assertNotNull("got second message on consumer2", msg);
            consumerSession2.commit();
         }
      } finally {
         for (Connection c : connections) {
            c.close();
         }

         // ensure no dangling messages with fresh broker etc
         if (broker != null) {
            broker.stop();
         }
      }

      LOG.info("Checking for remaining/hung messages..");
      broker = createBroker();
      broker.start();

      // after restart, ensure no dangling messages
      cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      connection = cf.createConnection();
      try {
         connection.start();
         Session sweeperSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer sweeper = sweeperSession.createConsumer(destination);
         msg = sweeper.receive(1000);
         if (msg == null) {
            msg = sweeper.receive(5000);
         }
         LOG.info("Sweep received: " + msg);
         Assert.assertNull("no messges left dangling but got: " + msg, msg);
      } finally {
         connection.close();
         broker.stop();
      }
   }

   @Test
   @BMRules(
      rules = {@BMRule(
         name = "set no return response and stop the broker",
         targetClass = "org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection$CommandProcessor",
         targetMethod = "processRemoveConsumer",
         targetLocation = "ENTRY",
         action = "org.apache.activemq.transport.failover.FailoverTransactionTest.stopBrokerOnCounter()")})
   public void testPoolingNConsumesAfterReconnect() throws Exception {
      LOG.info(this + " running test testPoolingNConsumesAfterReconnect");
      count = 0;
      broker = createBroker();
      startBrokerWithDurableQueue();

      doByteman.set(true);

      Vector<Connection> connections = new Vector<>();
      final ExecutorService executorService = Executors.newCachedThreadPool();

      try {
         ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
         configureConnectionFactory(cf);
         Connection connection = cf.createConnection();
         connection.start();
         Session producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Queue destination = producerSession.createQueue(QUEUE_NAME + "?consumer.prefetchSize=1");

         produceMessage(producerSession, destination);
         connection.close();

         connection = cf.createConnection();
         connection.start();
         connections.add(connection);
         final Session consumerSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         final int sessionCount = 10;
         final Stack<Session> sessions = new Stack<>();
         for (int i = 0; i < sessionCount; i++) {
            sessions.push(connection.createSession(false, Session.AUTO_ACKNOWLEDGE));
         }

         final int consumerCount = 1000;
         final Deque<MessageConsumer> consumers = new ArrayDeque<>();
         for (int i = 0; i < consumerCount; i++) {
            consumers.push(consumerSession.createConsumer(destination));
         }

         final FailoverTransport failoverTransport = ((ActiveMQConnection) connection).getTransport().narrow(FailoverTransport.class);
         final TransportListener delegate = failoverTransport.getTransportListener();
         failoverTransport.setTransportListener(new TransportListener() {
            @Override
            public void onCommand(Object command) {
               delegate.onCommand(command);
            }

            @Override
            public void onException(IOException error) {
               delegate.onException(error);
            }

            @Override
            public void transportInterupted() {

               LOG.error("Transport interrupted: " + failoverTransport, new RuntimeException("HERE"));
               for (int i = 0; i < consumerCount && !consumers.isEmpty(); i++) {

                  executorService.execute(() -> {
                     MessageConsumer localConsumer = null;
                     try {
                        synchronized (delegate) {
                           localConsumer = consumers.pop();
                        }
                        localConsumer.receive(1);

                        LOG.info("calling close() " + ((ActiveMQMessageConsumer) localConsumer).getConsumerId());
                        localConsumer.close();
                     } catch (NoSuchElementException nse) {
                     } catch (Exception ignored) {
                        LOG.error("Ex on: " + ((ActiveMQMessageConsumer) localConsumer).getConsumerId(), ignored);
                     }
                  });
               }

               delegate.transportInterupted();
            }

            @Override
            public void transportResumed() {
               delegate.transportResumed();
            }
         });

         MessageConsumer consumer = null;
         synchronized (delegate) {
            consumer = consumers.pop();
         }
         LOG.info("calling close to trigger broker stop " + ((ActiveMQMessageConsumer) consumer).getConsumerId());
         consumer.close();

         LOG.info("waiting latch: " + brokerStopLatch.getCount());
         // will be stopped by the plugin
         Assert.assertTrue(brokerStopLatch.await(60, TimeUnit.SECONDS));

         doByteman.set(false);
         broker = createBroker();
         broker.start();

         consumer = consumerSession.createConsumer(destination);
         LOG.info("finally consuming message: " + ((ActiveMQMessageConsumer) consumer).getConsumerId());

         Message msg = null;
         for (int i = 0; i < 4 && msg == null; i++) {
            msg = consumer.receive(1000);
         }

         LOG.info("post: from consumer1 received: " + msg);
         Assert.assertNotNull("got message after failover", msg);
         msg.acknowledge();
      } finally {
         executorService.shutdown();
         for (Connection c : connections) {
            c.close();
         }
      }
   }

   private void startBrokerWithDurableQueue() throws Exception {
      broker.start();
      //auto created queue can't survive a restart, so we need this
      broker.getJMSServerManager().createQueue(false, QUEUE_NAME, null, true, QUEUE_NAME);
   }

   @Test
   public void testAutoRollbackWithMissingRedeliveries() throws Exception {
      LOG.info(this + " running test testAutoRollbackWithMissingRedeliveries");
      broker = createBroker();
      broker.start();
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      try {
         connection.start();
         final Session producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Queue destination = producerSession.createQueue(QUEUE_NAME + "?consumer.prefetchSize=1");
         final Session consumerSession = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = consumerSession.createConsumer(destination);

         produceMessage(producerSession, destination);

         Message msg = consumer.receive(20000);
         Assert.assertNotNull(msg);

         broker.stop();
         broker = createBroker();
         // use empty jdbc store so that default wait(0) for redeliveries will timeout after failover
         broker.start();

         try {
            consumerSession.commit();
            Assert.fail("expected transaction rolledback ex");
         } catch (TransactionRolledBackException expected) {
         }

         broker.stop();
         broker = createBroker();
         broker.start();

         Assert.assertNotNull("should get rolledback message from original restarted broker", consumer.receive(20000));
      } finally {
         connection.close();
      }
   }

   @Test
   public void testWaitForMissingRedeliveries() throws Exception {
      LOG.info(this + " running test testWaitForMissingRedeliveries");

      broker = createBroker();
      broker.start();
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")?jms.consumerFailoverRedeliveryWaitPeriod=30000");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      try {
         connection.start();
         final Session producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Queue destination = producerSession.createQueue(QUEUE_NAME);
         final Session consumerSession = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = consumerSession.createConsumer(destination);

         produceMessage(producerSession, destination);
         Message msg = consumer.receive(20000);
         if (msg == null) {
            AutoFailTestSupport.dumpAllThreads("missing-");
         }
         Assert.assertNotNull("got message just produced", msg);

         broker.stop();
         broker = createBroker();
         broker.start();

         final CountDownLatch commitDone = new CountDownLatch(1);
         final CountDownLatch gotException = new CountDownLatch(1);
         // will block pending re-deliveries
         new Thread(() -> {
            LOG.info("doing async commit...");
            try {
               consumerSession.commit();
               commitDone.countDown();
            }
            catch (JMSException ignored) {
               System.out.println("--->err: got exfeption:");
               ignored.printStackTrace();
               gotException.countDown();
            }
            finally {
               commitDone.countDown();
            }
         }).start();

         broker.stop();
         broker = createBroker();
         broker.start();

         Assert.assertTrue("commit was successful", commitDone.await(30, TimeUnit.SECONDS));
         Assert.assertTrue("got exception on commit", gotException.await(30, TimeUnit.SECONDS));

         Assert.assertNotNull("should get failed committed message", consumer.receive(5000));
      } finally {
         connection.close();
      }
   }

   @Test
   public void testReDeliveryWhilePending() throws Exception {
      LOG.info(this + " running test testReDeliveryWhilePending");
      broker = createBroker();
      broker.start();
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("failover:(" + url + ")?jms.consumerFailoverRedeliveryWaitPeriod=10000");
      configureConnectionFactory(cf);
      Connection connection = cf.createConnection();
      connection.start();
      final Session producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      final Queue destination = producerSession.createQueue(QUEUE_NAME + "?consumer.prefetchSize=0");
      final Session consumerSession = connection.createSession(true, Session.SESSION_TRANSACTED);
      MessageConsumer consumer = consumerSession.createConsumer(destination);

      produceMessage(producerSession, destination);
      Message msg = consumer.receive(20000);
      if (msg == null) {
         AutoFailTestSupport.dumpAllThreads("missing-");
      }
      Assert.assertNotNull("got message just produced", msg);

      // add another consumer into the mix that may get the message after restart
      MessageConsumer consumer2 = consumerSession.createConsumer(consumerSession.createQueue(QUEUE_NAME + "?consumer.prefetchSize=1"));

      broker.stop();
      broker = createBroker();
      broker.start();

      final CountDownLatch commitDone = new CountDownLatch(1);

      final Vector<Exception> exceptions = new Vector<>();

      // commit may fail if other consumer gets the message on restart
      new Thread(() -> {
         LOG.info("doing async commit...");
         try {
            consumerSession.commit();
         } catch (JMSException ex) {
            exceptions.add(ex);
         } finally {
            commitDone.countDown();
         }
      }).start();

      Assert.assertTrue("commit completed ", commitDone.await(15, TimeUnit.SECONDS));

      // either message redelivered in existing tx or consumed by consumer2
      // should not be available again in any event
      Assert.assertNull("consumer should not get rolled back on non redelivered message or duplicate", consumer.receive(5000));

      // consumer replay is hashmap order dependent on a failover connection state recover so need to deal with both cases
      if (exceptions.isEmpty()) {
         LOG.info("commit succeeded, message was redelivered to the correct consumer after restart so commit was fine");
         Assert.assertNull("consumer2 not get a second message consumed by 1", consumer2.receive(2000));
      } else {
         LOG.info("commit failed, consumer2 should get it", exceptions.get(0));
         Assert.assertNotNull("consumer2 got message", consumer2.receive(2000));
         consumerSession.commit();
         // no message should be in dlq
         MessageConsumer dlqConsumer = consumerSession.createConsumer(consumerSession.createQueue("ActiveMQ.DLQ"));
         Assert.assertNull("nothing in the dlq", dlqConsumer.receive(5000));
      }
      connection.close();
   }

   private void produceMessage(final Session producerSession, Queue destination) throws JMSException {
      MessageProducer producer = producerSession.createProducer(destination);
      TextMessage message = producerSession.createTextMessage("Test message");
      producer.send(message);
      producer.close();
   }

   public static void holdResponseAndStopBroker(final OpenWireConnection.CommandProcessor context) {
      if (doByteman.get()) {
         context.getContext().setDontSendReponse(true);
         new Thread(() -> {
            LOG.info("Stopping broker post commit...");
            try {
               broker.stop();
               broker = null;
            } catch (Exception e) {
               e.printStackTrace();
            } finally {
               brokerStopLatch.countDown();
            }
         }).start();
      }
   }

   public static void holdResponseAndStopProxyOnFirstSend(final OpenWireConnection.CommandProcessor context) {
      if (doByteman.get()) {
         if (firstSend) {
            firstSend = false;
            context.getContext().setDontSendReponse(true);
            new Thread(() -> {
               LOG.info("Stopping connection post send...");
               try {
                  proxy.close();
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }).start();
         }
      }
   }

   public static void stopBrokerOnCounter() {
      LOG.info("in stopBrokerOnCounter, byteman " + doByteman.get() + " count " + count);
      if (doByteman.get()) {
         if (count++ == 1) {
            LOG.info("ok stop broker...");
            new Thread(() -> {
               try {
                  if (broker != null) {
                     broker.stop();
                     broker = null;
                  }
                  LOG.info("broker stopped.");
               } catch (Exception e) {
                  e.printStackTrace();
               } finally {
                  brokerStopLatch.countDown();
               }
            }).start();
         }
      }
   }
}
