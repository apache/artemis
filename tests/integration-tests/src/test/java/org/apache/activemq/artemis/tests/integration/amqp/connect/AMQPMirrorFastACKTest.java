/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AMQPMirrorFastACKTest extends AmqpClientTestSupport {

   private static final String SLOW_SERVER_NAME = "slow";
   private static final int SLOW_SERVER_PORT = AMQP_PORT + 1;

   private static final int ENCODE_DELAY = 10;

   private ActiveMQServer slowServer;

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,CORE,OPENWIRE";
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      slowServer = createSlowServer();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         if (slowServer != null) {
            slowServer.stop();
         }
      } finally {
         super.tearDown();
      }
   }

   @Test
   public void testMirrorTargetFastACKCore() throws Exception {
      doTestMirrorTargetFastACK("CORE");
   }

   @Test
   public void testMirrorTargetFastACKAMQP() throws Exception {
      doTestMirrorTargetFastACK("AMQP");
   }

   private void doTestMirrorTargetFastACK(String protocol) throws Exception {

      final int NUMBER_OF_MESSAGES = 10;
      CountDownLatch done = new CountDownLatch(NUMBER_OF_MESSAGES);

      AMQPMirrorBrokerConnectionElement replication = configureMirrorTowardsSlow(server);

      slowServer.start();
      server.start();

      waitForServerToStart(slowServer);
      waitForServerToStart(server);

      server.addAddressInfo(new AddressInfo(getQueueName()).addRoutingType(RoutingType.ANYCAST).setAutoCreated(false));
      server.createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST).setAddress(getQueueName()).setAutoCreated(false));

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:" + AMQP_PORT);

      try (Connection connection = factory.createConnection()) {
         Session consumerSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSession.createConsumer(consumerSession.createQueue(getQueueName()));
         Session producerSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageProducer producer = producerSession.createProducer(producerSession.createQueue(getQueueName()));

         connection.start();

         consumer.setMessageListener(message -> {
            try {
               message.acknowledge();
               done.countDown();
            } catch (Exception ignore) {
               // Ignore
            }
         });

         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.send(producerSession.createTextMessage("i=" + i));
         }

         assertTrue(done.await(5000, TimeUnit.MILLISECONDS));
      }

      Queue snf = server.locateQueue(replication.getMirrorSNF());
      Queue queue = slowServer.locateQueue(getQueueName());

      Wait.waitFor(() -> snf.getMessageCount() == 0 && snf.getMessagesAdded() > NUMBER_OF_MESSAGES);
      Wait.assertTrue("Expected mirrored target queue " + getQueueName() + " to be empty", () -> queue.getMessageCount() == 0 && queue.getMessagesAdded() == NUMBER_OF_MESSAGES);
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, false);
   }

   private AMQPMirrorBrokerConnectionElement configureMirrorTowardsSlow(ActiveMQServer source) {
      AMQPBrokerConnectConfiguration connection = new AMQPBrokerConnectConfiguration("mirror", "tcp://localhost:" + SLOW_SERVER_PORT).setReconnectAttempts(-1).setRetryInterval(100);
      AMQPMirrorBrokerConnectionElement replication = new AMQPMirrorBrokerConnectionElement().setDurable(true);
      connection.addElement(replication);

      source.getConfiguration().addAMQPConnection(connection);
      return replication;
   }

   private ActiveMQServer createSlowServer() throws Exception {
      ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());
      ActiveMQServer server = new ActiveMQServerImpl(createBasicConfig(SLOW_SERVER_PORT), mBeanServer, securityManager) {
         @Override
         protected StorageManager createStorageManager() {
            return AMQPMirrorFastACKTest.this.createStorageManager(getConfiguration(), getCriticalAnalyzer(), executorFactory, scheduledPool, ioExecutorFactory, ioCriticalErrorListener);
         }
      };

      server.getConfiguration().setName(SLOW_SERVER_NAME);
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().getAcceptorConfigurations().add(addAcceptorConfiguration(slowServer, SLOW_SERVER_PORT));

      server.getConfiguration().setJMXManagementEnabled(true);
      server.getConfiguration().setMessageExpiryScanPeriod(100);

      configureAddressPolicy(server);
      configureBrokerSecurity(server);

      return server;
   }

   private StorageManager createStorageManager(Configuration configuration,
                                               CriticalAnalyzer criticalAnalyzer,
                                               ExecutorFactory executorFactory,
                                               ScheduledExecutorService scheduledPool,
                                               ExecutorFactory ioExecutorFactory,
                                               IOCriticalErrorListener ioCriticalErrorListener) {
      return new JournalStorageManager(configuration, criticalAnalyzer, executorFactory, scheduledPool, ioExecutorFactory, ioCriticalErrorListener) {
         @Override
         protected Journal createMessageJournal(Configuration config,
                                                IOCriticalErrorListener criticalErrorListener,
                                                int fileSize) {
            return new JournalImpl(ioExecutorFactory, fileSize, config.getJournalMinFiles(), config.getJournalPoolFiles(), config.getJournalCompactMinFiles(), config.getJournalCompactPercentage(), config.getJournalFileOpenTimeout(), journalFF, "activemq-data", "amq", journalFF.getMaxIO(), 0, criticalErrorListener, config.getJournalMaxAtticFiles()) {
               @Override
               public void appendAddRecordTransactional(long txID,
                                                        long id,
                                                        byte recordType,
                                                        Persister persister,
                                                        Object record) throws Exception {
                  super.appendAddRecordTransactional(txID, id, recordType, record instanceof AMQPStandardMessage ? new SlowMessagePersister<>(persister) : persister, record);
               }
            };
         }
      };
   }

   static class SlowMessagePersister<T> implements Persister<T> {

      private final Persister<T> delegate;

      SlowMessagePersister(Persister<T> delegate) {
         this.delegate = delegate;
      }

      @Override
      public byte getID() {
         return delegate.getID();
      }

      @Override
      public int getEncodeSize(T record) {
         return delegate.getEncodeSize(record);
      }

      @Override
      public void encode(ActiveMQBuffer buffer, T record) {
         try {
            // This will slow down IO completion for transactional message write
            Thread.sleep(ENCODE_DELAY);
         } catch (Exception ignore) {
            // ignore
         }
         delegate.encode(buffer, record);
      }

      @Override
      public T decode(ActiveMQBuffer buffer, T record, CoreMessageObjectPools pool) {
         return delegate.decode(buffer, record, pool);
      }
   }
}
