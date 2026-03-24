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
package org.apache.activemq.artemis.tests.timing.jms.bridge.impl;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.MBeanServerInvocationHandler;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.bridge.ConnectionFactoryFactory;
import org.apache.activemq.artemis.jms.bridge.DestinationFactory;
import org.apache.activemq.artemis.jms.bridge.QualityOfServiceMode;
import org.apache.activemq.artemis.jms.bridge.impl.JMSBridgeImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class JMSBridgeImplTest extends ActiveMQTestBase {

   private static Logger log = Logger.getLogger(JMSBridgeImplTest.class);


   private static final String SOURCE = RandomUtil.randomString();

   private static final String TARGET = RandomUtil.randomString();

   private ActiveMQServer server;

   private static final AtomicBoolean tcclClassFound = new AtomicBoolean(false);

   @Rule
   public ExpectedException thrown = ExpectedException.none();


   protected static TransactionManager newTransactionManager() {
      return new TransactionManager() {
         @Override
         public Transaction suspend() throws SystemException {
            return null;
         }

         @Override
         public void setTransactionTimeout(final int arg0) throws SystemException {
         }

         @Override
         public void setRollbackOnly() throws IllegalStateException, SystemException {
         }

         @Override
         public void rollback() throws IllegalStateException, SecurityException, SystemException {
         }

         @Override
         public void resume(final Transaction arg0) throws InvalidTransactionException, IllegalStateException, SystemException {
         }

         @Override
         public Transaction getTransaction() throws SystemException {
            return null;
         }

         @Override
         public int getStatus() throws SystemException {
            return 0;
         }

         @Override
         public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
         }

         @Override
         public void begin() throws NotSupportedException, SystemException {
         }
      };
   }

   private static DestinationFactory newDestinationFactory(final Destination dest) {
      return new DestinationFactory() {
         @Override
         public Destination createDestination() throws Exception {
            return dest;
         }
      };
   }

   private static ConnectionFactoryFactory newConnectionFactoryFactory(final ConnectionFactory cf) {
      return new ConnectionFactoryFactory() {
         @Override
         public ConnectionFactory createConnectionFactory() throws Exception {
            return cf;
         }
      };
   }

   private static ConnectionFactory createConnectionFactory() {

      ActiveMQJMSConnectionFactory cf = (ActiveMQJMSConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName()));
      // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
      cf.setReconnectAttempts(0);
      cf.setBlockOnNonDurableSend(true);
      cf.setBlockOnDurableSend(true);
      return cf;
   }

   private static ConnectionFactoryFactory newTCCLAwareConnectionFactoryFactory(final ConnectionFactory cf) {
      return new ConnectionFactoryFactory() {
         @Override
         public ConnectionFactory createConnectionFactory() throws Exception {
            loadATCCLClass();
            return cf;
         }

         private void loadATCCLClass() {
            ClassLoader tcclClassLoader = Thread.currentThread().getContextClassLoader();
            try {
               tcclClassLoader.loadClass("com.class.only.visible.to.tccl.SomeClass");
               tcclClassFound.set(true);
            } catch (ClassNotFoundException e) {
               e.printStackTrace();
            }
         }
      };
   }



   @Test
   public void testStartWithRepeatedFailure() throws Exception {
      ActiveMQJMSConnectionFactory failingSourceCF = new ActiveMQJMSConnectionFactory(false, new TransportConfiguration(InVMConnectorFactory.class.getName())) {
         private static final long serialVersionUID = 2834937512213001068L;

         @Override
         public Connection createConnection() throws JMSException {
            throw new JMSException("unable to create a conn");
         }
      };

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(failingSourceCF);
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));
      TransactionManager tm = JMSBridgeImplTest.newTransactionManager();

      JMSBridgeImpl bridge = new JMSBridgeImpl();

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      // retry after 10 ms
      bridge.setFailureRetryInterval(10);
      // retry only once
      bridge.setMaxRetries(1);
      bridge.setMaxBatchSize(1);
      bridge.setMaxBatchTime(-1);
      bridge.setTransactionManager(tm);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

      Assert.assertFalse(bridge.isStarted());
      bridge.start();

      Thread.sleep(50);
      Assert.assertFalse(bridge.isStarted());
      Assert.assertTrue(bridge.isFailed());

      bridge.stop();

   }

   @Test
   public void testStartWithFailureThenSuccess() throws Exception {
      ActiveMQJMSConnectionFactory failingSourceCF = new ActiveMQJMSConnectionFactory(false, new TransportConfiguration(InVMConnectorFactory.class.getName())) {
         private static final long serialVersionUID = 4657153922210359725L;
         boolean firstTime = true;

         @Override
         public Connection createConnection() throws JMSException {
            if (firstTime) {
               firstTime = false;
               throw new JMSException("unable to create a conn");
            } else {
               return super.createConnection();
            }
         }
      };
      // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
      failingSourceCF.setReconnectAttempts(0);
      failingSourceCF.setBlockOnNonDurableSend(true);
      failingSourceCF.setBlockOnDurableSend(true);

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(failingSourceCF);
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));
      TransactionManager tm = JMSBridgeImplTest.newTransactionManager();

      JMSBridgeImpl bridge = new JMSBridgeImpl();

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      // retry after 10 ms
      bridge.setFailureRetryInterval(10);
      // retry only once
      bridge.setMaxRetries(1);
      bridge.setMaxBatchSize(1);
      bridge.setMaxBatchTime(-1);
      bridge.setTransactionManager(tm);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

      Assert.assertFalse(bridge.isStarted());
      bridge.start();

      Thread.sleep(500);
      Assert.assertTrue(bridge.isStarted());
      Assert.assertFalse(bridge.isFailed());

      bridge.stop();
   }

   /*
   * we receive only 1 message. The message is sent when the maxBatchTime
   * expires even if the maxBatchSize is not reached
   */
   @Test
   public void testBridgeWithMaskPasswords() throws Exception {

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));
      TransactionManager tm = JMSBridgeImplTest.newTransactionManager();

      JMSBridgeImpl bridge = new JMSBridgeImpl();
      Assert.assertNotNull(bridge);

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      bridge.setFailureRetryInterval(10);
      bridge.setMaxRetries(1);
      bridge.setMaxBatchSize(1);
      bridge.setMaxBatchTime(-1);
      bridge.setTransactionManager(tm);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

      bridge.setSourceUsername("sourceuser");
      bridge.setSourcePassword("ENC(5493dd76567ee5ec269d11823973462f)");
      bridge.setTargetUsername("targetuser");
      bridge.setTargetPassword("ENC(56a0db3b71043054269d11823973462f)");

      Assert.assertFalse(bridge.isStarted());
      bridge.start();
      Assert.assertTrue(bridge.isStarted());

      assertEquals("sourcepassword", bridge.getSourcePassword());
      assertEquals("targetpassword", bridge.getTargetPassword());

      bridge.stop();
      Assert.assertFalse(bridge.isStarted());
   }

   @Test
   public void testSendMessagesWhenMaxBatchTimeExpires() throws Exception {
      int maxBatchSize = 2;
      long maxBatchTime = 500;

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));
      TransactionManager tm = JMSBridgeImplTest.newTransactionManager();

      JMSBridgeImpl bridge = new JMSBridgeImpl();
      Assert.assertNotNull(bridge);

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      bridge.setFailureRetryInterval(10);
      bridge.setMaxRetries(-1);
      bridge.setMaxBatchSize(maxBatchSize);
      bridge.setMaxBatchTime(maxBatchTime);
      bridge.setTransactionManager(tm);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

      Assert.assertFalse(bridge.isStarted());
      bridge.start();
      Assert.assertTrue(bridge.isStarted());

      Connection targetConn = JMSBridgeImplTest.createConnectionFactory().createConnection();
      Session targetSess = targetConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = targetSess.createConsumer(targetDF.createDestination());
      final List<Message> messages = new LinkedList<>();
      MessageListener listener = new MessageListener() {

         @Override
         public void onMessage(final Message message) {
            messages.add(message);
         }
      };
      consumer.setMessageListener(listener);
      targetConn.start();

      Connection sourceConn = JMSBridgeImplTest.createConnectionFactory().createConnection();
      Session sourceSess = sourceConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = sourceSess.createProducer(sourceDF.createDestination());
      producer.send(sourceSess.createTextMessage());
      sourceConn.close();

      Assert.assertEquals(0, messages.size());
      Thread.sleep(3 * maxBatchTime);

      Assert.assertEquals(1, messages.size());

      bridge.stop();
      Assert.assertFalse(bridge.isStarted());

      targetConn.close();
   }

   @Test
   public void testSendMessagesWithMaxBatchSize() throws Exception {
      final int numMessages = 10;

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));
      TransactionManager tm = JMSBridgeImplTest.newTransactionManager();

      JMSBridgeImpl bridge = new JMSBridgeImpl();
      Assert.assertNotNull(bridge);

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      bridge.setFailureRetryInterval(10);
      bridge.setMaxRetries(-1);
      bridge.setMaxBatchSize(numMessages);
      bridge.setMaxBatchTime(-1);
      bridge.setTransactionManager(tm);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

      Assert.assertFalse(bridge.isStarted());
      bridge.start();
      Assert.assertTrue(bridge.isStarted());

      Connection targetConn = JMSBridgeImplTest.createConnectionFactory().createConnection();
      Session targetSess = targetConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = targetSess.createConsumer(targetDF.createDestination());
      final List<Message> messages = new LinkedList<>();
      final CountDownLatch latch = new CountDownLatch(numMessages);
      MessageListener listener = new MessageListener() {
         @Override
         public void onMessage(final Message message) {
            messages.add(message);
            latch.countDown();
         }
      };
      consumer.setMessageListener(listener);
      targetConn.start();

      Connection sourceConn = JMSBridgeImplTest.createConnectionFactory().createConnection();
      Session sourceSess = sourceConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer producer = sourceSess.createProducer(sourceDF.createDestination());

      for (int i = 0; i < numMessages - 1; i++) {
         TextMessage msg = sourceSess.createTextMessage();
         producer.send(msg);
         JMSBridgeImplTest.log.info("sent message " + i);
      }

      Thread.sleep(1000);

      Assert.assertEquals(0, messages.size());

      TextMessage msg = sourceSess.createTextMessage();

      producer.send(msg);

      Assert.assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));

      sourceConn.close();

      Assert.assertEquals(numMessages, messages.size());

      bridge.stop();
      Assert.assertFalse(bridge.isStarted());

      targetConn.close();
   }

   @Test
   public void testAutoAckOnSourceBatchOfOne() throws Exception {
      doTestAutoAckOnSource(1);
   }

   @Test
   public void testAutoAckOnSourceBatchOfTen() throws Exception {
      doTestAutoAckOnSource(10);
   }

   public void doTestAutoAckOnSource(int maxBatchSize) throws Exception {
      final int numMessages = maxBatchSize;

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));
      TransactionManager tm = JMSBridgeImplTest.newTransactionManager();

      JMSBridgeImpl bridge = new JMSBridgeImpl();
      Assert.assertNotNull(bridge);

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      bridge.setFailureRetryInterval(10);
      bridge.setMaxRetries(1);
      bridge.setMaxBatchSize(maxBatchSize);
      bridge.setMaxBatchTime(-1);
      bridge.setTransactionManager(tm);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

      Assert.assertFalse(bridge.isStarted());
      bridge.start();
      Assert.assertTrue(bridge.isStarted());

      Connection sourceConn = JMSBridgeImplTest.createConnectionFactory().createConnection();
      Session sourceSess = sourceConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer producer = sourceSess.createProducer(sourceDF.createDestination());

      for (int i = 0; i < numMessages; i++) {
         TextMessage msg = sourceSess.createTextMessage();
         producer.send(msg);
         JMSBridgeImplTest.log.info("sent message " + i);
      }

      sourceConn.close();
      SimpleString add = new SimpleString(JMSBridgeImplTest.SOURCE);
      QueueControl jmsQueueControl = MBeanServerInvocationHandler.newProxyInstance(ManagementFactory.getPlatformMBeanServer(), ObjectNameBuilder.DEFAULT.getQueueObjectName(add, add, RoutingType.ANYCAST), QueueControl.class, false);
      assertNotEquals(jmsQueueControl.getDeliveringCount(), numMessages);

      bridge.stop();
      Assert.assertFalse(bridge.isStarted());
   }

   @Test
   public void testExceptionOnSourceAndRetrySucceeds() throws Exception {
      final AtomicReference<Connection> sourceConn = new AtomicReference<>();
      ActiveMQJMSConnectionFactory failingSourceCF = new ActiveMQJMSConnectionFactory(false, new TransportConfiguration(InVMConnectorFactory.class.getName())) {
         private static final long serialVersionUID = -8866390811966688830L;

         @Override
         public Connection createConnection() throws JMSException {
            sourceConn.set(super.createConnection());
            return sourceConn.get();
         }
      };
      // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
      failingSourceCF.setReconnectAttempts(0);
      failingSourceCF.setBlockOnNonDurableSend(true);
      failingSourceCF.setBlockOnDurableSend(true);

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(failingSourceCF);
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));
      TransactionManager tm = JMSBridgeImplTest.newTransactionManager();

      JMSBridgeImpl bridge = new JMSBridgeImpl();
      Assert.assertNotNull(bridge);

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      bridge.setFailureRetryInterval(10);
      bridge.setMaxRetries(2);
      bridge.setMaxBatchSize(1);
      bridge.setMaxBatchTime(-1);
      bridge.setTransactionManager(tm);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

      Assert.assertFalse(bridge.isStarted());
      bridge.start();
      Assert.assertTrue(bridge.isStarted());

      sourceConn.get().getExceptionListener().onException(new JMSException("exception on the source"));
      Thread.sleep(4 * bridge.getFailureRetryInterval());
      // reconnection must have succeeded
      Assert.assertTrue(bridge.isStarted());

      bridge.stop();
      Assert.assertFalse(bridge.isStarted());
   }

   @Test
   public void testExceptionOnSourceAndRetryFails() throws Exception {
      final AtomicReference<Connection> sourceConn = new AtomicReference<>();
      ActiveMQJMSConnectionFactory failingSourceCF = new ActiveMQJMSConnectionFactory(false, new TransportConfiguration(INVM_CONNECTOR_FACTORY)) {
         private static final long serialVersionUID = 8216804886099984645L;
         boolean firstTime = true;

         @Override
         public Connection createConnection() throws JMSException {
            if (firstTime) {
               firstTime = false;
               sourceConn.set(super.createConnection());
               return sourceConn.get();
            } else {
               throw new JMSException("exception while retrying to connect");
            }
         }
      };
      // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
      failingSourceCF.setReconnectAttempts(0);
      failingSourceCF.setBlockOnNonDurableSend(true);
      failingSourceCF.setBlockOnDurableSend(true);

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(failingSourceCF);
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));
      TransactionManager tm = JMSBridgeImplTest.newTransactionManager();

      JMSBridgeImpl bridge = new JMSBridgeImpl();
      Assert.assertNotNull(bridge);

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      bridge.setFailureRetryInterval(100);
      bridge.setMaxRetries(1);
      bridge.setMaxBatchSize(1);
      bridge.setMaxBatchTime(-1);
      bridge.setTransactionManager(tm);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

      Assert.assertFalse(bridge.isStarted());
      bridge.start();
      Assert.assertTrue(bridge.isStarted());

      sourceConn.get().getExceptionListener().onException(new JMSException("exception on the source"));
      Thread.sleep(4 * bridge.getFailureRetryInterval());
      // reconnection must have failed
      Assert.assertFalse(bridge.isStarted());

   }

   @Test
   public void testStartWithSpecificTCCL() throws Exception {
      MockContextClassLoader mockTccl = setMockTCCL();
      try {
         final AtomicReference<Connection> sourceConn = new AtomicReference<>();
         ActiveMQJMSConnectionFactory failingSourceCF = new ActiveMQJMSConnectionFactory(false, new TransportConfiguration(InVMConnectorFactory.class.getName())) {
            private static final long serialVersionUID = -8866390811966688830L;

            @Override
            public Connection createConnection() throws JMSException {
               sourceConn.set(super.createConnection());
               return sourceConn.get();
            }
         };
         // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
         failingSourceCF.setReconnectAttempts(0);
         failingSourceCF.setBlockOnNonDurableSend(true);
         failingSourceCF.setBlockOnDurableSend(true);

         ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newTCCLAwareConnectionFactoryFactory(failingSourceCF);
         ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
         DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
         DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));
         TransactionManager tm = JMSBridgeImplTest.newTransactionManager();

         JMSBridgeImpl bridge = new JMSBridgeImpl();
         Assert.assertNotNull(bridge);

         bridge.setSourceConnectionFactoryFactory(sourceCFF);
         bridge.setSourceDestinationFactory(sourceDF);
         bridge.setTargetConnectionFactoryFactory(targetCFF);
         bridge.setTargetDestinationFactory(targetDF);
         bridge.setFailureRetryInterval(10);
         bridge.setMaxRetries(2);
         bridge.setMaxBatchSize(1);
         bridge.setMaxBatchTime(-1);
         bridge.setTransactionManager(tm);
         bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

         Assert.assertFalse(bridge.isStarted());
         bridge.start();
         Assert.assertTrue(bridge.isStarted());

         unsetMockTCCL(mockTccl);
         tcclClassFound.set(false);

         sourceConn.get().getExceptionListener().onException(new JMSException("exception on the source"));
         Thread.sleep(4 * bridge.getFailureRetryInterval());
         // reconnection must have succeeded
         Assert.assertTrue(bridge.isStarted());

         bridge.stop();
         Assert.assertFalse(bridge.isStarted());
         assertTrue(tcclClassFound.get());
      } finally {
         if (mockTccl != null)
            unsetMockTCCL(mockTccl);
      }
   }


   private static MockContextClassLoader setMockTCCL() {
      ClassLoader parent = JMSBridgeImpl.class.getClassLoader();
      MockContextClassLoader tccl = new MockContextClassLoader(parent);
      Thread.currentThread().setContextClassLoader(tccl);
      return tccl;
   }

   private static void unsetMockTCCL(MockContextClassLoader mockTccl) {
      Thread.currentThread().setContextClassLoader(mockTccl.getOriginal());
   }

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
      server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      server.createQueue(new QueueConfiguration(JMSBridgeImplTest.SOURCE).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(new QueueConfiguration(JMSBridgeImplTest.TARGET).setRoutingType(RoutingType.ANYCAST));
   }

   @Test
   public void testTransactionManagerNotSetForDuplicatesOK() throws Exception {

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));

      JMSBridgeImpl bridge = new JMSBridgeImpl();
      Assert.assertNotNull(bridge);

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      bridge.setFailureRetryInterval(10);
      bridge.setMaxRetries(1);
      bridge.setMaxBatchTime(-1);
      bridge.setMaxBatchSize(10);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.DUPLICATES_OK);

      Assert.assertFalse(bridge.isStarted());
      bridge.start();

      Field field = JMSBridgeImpl.class.getDeclaredField("tm");
      field.setAccessible(true);
      assertNull(field.get(bridge));

      bridge.stop();
      Assert.assertFalse(bridge.isStarted());
   }

   @Test
   public void testThrowErrorWhenTMNotSetForOnceOnly() throws Exception {
      thrown.expect(RuntimeException.class);

      ConnectionFactoryFactory sourceCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      ConnectionFactoryFactory targetCFF = JMSBridgeImplTest.newConnectionFactoryFactory(JMSBridgeImplTest.createConnectionFactory());
      DestinationFactory sourceDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE));
      DestinationFactory targetDF = JMSBridgeImplTest.newDestinationFactory(ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET));

      JMSBridgeImpl bridge = new JMSBridgeImpl();
      Assert.assertNotNull(bridge);

      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      bridge.setFailureRetryInterval(10);
      bridge.setMaxRetries(1);
      bridge.setMaxBatchTime(-1);
      bridge.setMaxBatchSize(10);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.ONCE_AND_ONLY_ONCE);

      Assert.assertFalse(bridge.isStarted());
      bridge.start();

      Field field = JMSBridgeImpl.class.getDeclaredField("tm");
      field.setAccessible(true);
      assertNotNull(field.get(bridge));

      bridge.stop();
      Assert.assertFalse(bridge.isStarted());
   }

   private static class MockContextClassLoader extends ClassLoader {

      private final ClassLoader original;
      private final String knownClass = "com.class.only.visible.to.tccl.SomeClass";

      private MockContextClassLoader(ClassLoader parent) {
         super(parent);
         original = Thread.currentThread().getContextClassLoader();
      }

      public ClassLoader getOriginal() {
         return original;
      }

      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
         if (knownClass.equals(name)) {
            return null;
         }
         return super.findClass(name);
      }
   }
}
