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
package org.apache.activemq.artemis.tests.integration.jms.bridge;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.transaction.TransactionManager;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.bridge.ConnectionFactoryFactory;
import org.apache.activemq.artemis.jms.bridge.DestinationFactory;
import org.apache.activemq.artemis.jms.bridge.QualityOfServiceMode;
import org.apache.activemq.artemis.jms.bridge.impl.JMSBridgeImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class JMSBridgeImplTest extends ActiveMQTestBase {

   private static final String SOURCE = RandomUtil.randomUUIDString();

   private static final String TARGET = RandomUtil.randomUUIDString();

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false, createDefaultInVMConfig());
      server.start();

      server.createQueue(QueueConfiguration.of(SOURCE).setRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(TARGET).setRoutingType(RoutingType.ANYCAST));
   }

   private static ConnectionFactory createConnectionFactory() {
      ActiveMQJMSConnectionFactory cf = (ActiveMQJMSConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName()));
      cf.setReconnectAttempts(0);
      cf.setBlockOnNonDurableSend(true);
      cf.setBlockOnDurableSend(true);
      return cf;
   }

   @Test
   public void testExceptionOnSourceAndManualRestartSucceeds() throws Exception {
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

      ConnectionFactoryFactory sourceCFF = () -> failingSourceCF;
      ConnectionFactoryFactory targetCFF = () -> createConnectionFactory();
      DestinationFactory sourceDF = () -> ActiveMQJMSClient.createQueue(JMSBridgeImplTest.SOURCE);
      DestinationFactory targetDF = () -> ActiveMQJMSClient.createQueue(JMSBridgeImplTest.TARGET);
      TransactionManager tm = Mockito.mock(TransactionManager.class);

      JMSBridgeImpl bridge = new JMSBridgeImpl();
      bridge.setSourceConnectionFactoryFactory(sourceCFF);
      bridge.setSourceDestinationFactory(sourceDF);
      bridge.setTargetConnectionFactoryFactory(targetCFF);
      bridge.setTargetDestinationFactory(targetDF);
      bridge.setFailureRetryInterval(1);
      bridge.setMaxRetries(0);
      bridge.setMaxBatchSize(1);
      bridge.setMaxBatchTime(-1);
      bridge.setTransactionManager(tm);
      bridge.setQualityOfServiceMode(QualityOfServiceMode.AT_MOST_ONCE);

      Assertions.assertFalse(bridge.isStarted());
      bridge.start();
      Assertions.assertTrue(bridge.isStarted());

      // make sure the bridge is actually working first
      Connection targetConn = createConnectionFactory().createConnection();
      Session targetSess = targetConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer = targetSess.createConsumer(targetDF.createDestination());
      final List<Message> messages = new LinkedList<>();
      MessageListener listener = message -> messages.add(message);
      consumer.setMessageListener(listener);
      targetConn.start();

      Session sourceSess = sourceConn.get().createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = sourceSess.createProducer(sourceDF.createDestination());
      producer.send(sourceSess.createTextMessage());

      Wait.assertEquals(1, () -> messages.size(), 2000, 100);

      sourceConn.get().getExceptionListener().onException(new JMSException("exception on the source"));
      Wait.assertTrue(() -> bridge.isFailed(), 2000, 50);
      targetConn.close();
      bridge.stop();
      Assertions.assertFalse(bridge.isStarted());
      bridge.start();
      Assertions.assertTrue(bridge.isStarted());

      // test the bridge again after it's been restarted to ensure it's working
      targetConn = JMSBridgeImplTest.createConnectionFactory().createConnection();
      targetSess = targetConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      consumer = targetSess.createConsumer(targetDF.createDestination());
      messages.clear();
      consumer.setMessageListener(listener);
      targetConn.start();

      sourceSess = sourceConn.get().createSession(false, Session.AUTO_ACKNOWLEDGE);
      producer = sourceSess.createProducer(sourceDF.createDestination());
      producer.send(sourceSess.createTextMessage());

      Wait.assertEquals(1, () -> messages.size(), 2000, 100);
      targetConn.close();
      sourceConn.get().close();
      bridge.stop();
      Assertions.assertFalse(bridge.isStarted());
   }
}
