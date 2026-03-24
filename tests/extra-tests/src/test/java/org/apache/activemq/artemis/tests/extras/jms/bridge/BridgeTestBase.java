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
package org.apache.activemq.artemis.tests.extras.jms.bridge;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.XAConnectionFactory;
import javax.transaction.TransactionManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.arjuna.ats.arjuna.coordinator.TransactionReaper;
import com.arjuna.ats.arjuna.coordinator.TxControl;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.jms.bridge.ConnectionFactoryFactory;
import org.apache.activemq.artemis.jms.bridge.DestinationFactory;
import org.apache.activemq.artemis.jms.bridge.QualityOfServiceMode;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQXAConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public abstract class BridgeTestBase extends ActiveMQTestBase {

   protected ConnectionFactoryFactory cff0, cff1;

   protected ConnectionFactoryFactory cff0xa, cff1xa;

   protected ConnectionFactory cf0, cf1;

   protected XAConnectionFactory cf0xa, cf1xa;

   protected DestinationFactory sourceQueueFactory;
   protected DestinationFactory targetQueueFactory;
   protected DestinationFactory localTargetQueueFactory;
   protected DestinationFactory sourceTopicFactory;

   protected Queue sourceQueue, targetQueue, localTargetQueue;

   protected Topic sourceTopic;

   protected ActiveMQServer server0;

   protected ActiveMQServer server1;

   protected HashMap<String, Object> params1;

   protected ConnectionFactoryFactory cff0LowProducerWindow;

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      // Start the servers
      Configuration conf0 = createBasicConfig().setJournalDirectory(getJournalDir(0, false)).setBindingsDirectory(getBindingsDir(0, false)).addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY));

      server0 = addServer(ActiveMQServers.newActiveMQServer(conf0, false));
      server0.start();

      params1 = new HashMap<>();
      params1.put(TransportConstants.SERVER_ID_PROP_NAME, 1);

      Configuration conf1 = createBasicConfig().setJournalDirectory(getJournalDir(1, false)).setBindingsDirectory(getBindingsDir(1, false)).addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, params1));

      server1 = addServer(ActiveMQServers.newActiveMQServer(conf1, false));
      server1.start();

      setUpAdministeredObjects();
      TxControl.enable();
      // We need a local transaction and recovery manager
      // We must start this after the remote servers have been created or it won't
      // have deleted the database and the recovery manager may attempt to recover transactions

   }

   protected void createQueue(final String queueName, final int index) throws Exception {
      ActiveMQServer server = server0;
      if (index == 1) {
         server = server1;
      }
      assertTrue("queue '/queue/" + queueName + "' created", server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST)) != null);
   }

   @Override
   @After
   public void tearDown() throws Exception {
      checkEmpty(sourceQueue, 0);
      checkEmpty(localTargetQueue, 0);
      checkEmpty(targetQueue, 1);

      // Check no subscriptions left lying around

      checkNoSubscriptions(sourceTopic, 0);
      if (cff0 instanceof ActiveMQConnectionFactory) {
         ((ActiveMQConnectionFactory) cff0).close();
      }
      if (cff1 instanceof ActiveMQConnectionFactory) {
         ((ActiveMQConnectionFactory) cff1).close();
      }
      stopComponent(server0);
      stopComponent(server1);
      cff0 = cff1 = null;
      cff0xa = cff1xa = null;

      cf0 = cf1 = null;

      cf0xa = cf1xa = null;

      sourceQueueFactory = targetQueueFactory = localTargetQueueFactory = sourceTopicFactory = null;

      sourceQueue = targetQueue = localTargetQueue = null;

      sourceTopic = null;

      server0 = null;

      server1 = null;

      // Shutting down Arjuna threads
      TxControl.disable(true);

      TransactionReaper.terminate(false);
      super.tearDown();
   }

   protected void setUpAdministeredObjects() throws Exception {
      cff0LowProducerWindow = new ConnectionFactoryFactory() {
         @Override
         public ConnectionFactory createConnectionFactory() throws Exception {
            ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

            // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
            cf.setReconnectAttempts(0);
            cf.setBlockOnNonDurableSend(true);
            cf.setBlockOnDurableSend(true);
            cf.setCacheLargeMessagesClient(true);
            cf.setProducerWindowSize(100);

            return cf;
         }

      };

      cff0 = new ConnectionFactoryFactory() {
         @Override
         public ConnectionFactory createConnectionFactory() throws Exception {
            ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

            // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
            cf.setReconnectAttempts(0);
            cf.setBlockOnNonDurableSend(true);
            cf.setBlockOnDurableSend(true);
            cf.setCacheLargeMessagesClient(true);

            return cf;
         }

      };

      cff0xa = new ConnectionFactoryFactory() {
         @Override
         public Object createConnectionFactory() throws Exception {
            ActiveMQXAConnectionFactory cf = (ActiveMQXAConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.XA_CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

            // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
            cf.setReconnectAttempts(0);
            cf.setBlockOnNonDurableSend(true);
            cf.setBlockOnDurableSend(true);
            cf.setCacheLargeMessagesClient(true);

            return cf;
         }

      };

      cf0 = (ConnectionFactory) cff0.createConnectionFactory();
      cf0xa = (XAConnectionFactory) cff0xa.createConnectionFactory();

      cff1 = new ConnectionFactoryFactory() {

         @Override
         public ConnectionFactory createConnectionFactory() throws Exception {
            ActiveMQJMSConnectionFactory cf = (ActiveMQJMSConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY, params1));

            // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
            cf.setReconnectAttempts(0);
            cf.setBlockOnNonDurableSend(true);
            cf.setBlockOnDurableSend(true);
            cf.setCacheLargeMessagesClient(true);

            return cf;
         }
      };

      cff1xa = new ConnectionFactoryFactory() {

         @Override
         public XAConnectionFactory createConnectionFactory() throws Exception {
            ActiveMQXAConnectionFactory cf = (ActiveMQXAConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.XA_CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY, params1));

            // Note! We disable automatic reconnection on the session factory. The bridge needs to do the reconnection
            cf.setReconnectAttempts(0);
            cf.setBlockOnNonDurableSend(true);
            cf.setBlockOnDurableSend(true);
            cf.setCacheLargeMessagesClient(true);

            return cf;
         }
      };

      cf1 = (ConnectionFactory) cff1.createConnectionFactory();
      cf1xa = (XAConnectionFactory) cff1xa.createConnectionFactory();

      sourceQueueFactory = new DestinationFactory() {
         @Override
         public Destination createDestination() throws Exception {
            return ActiveMQDestination.createDestination("/queue/sourceQueue", ActiveMQDestination.TYPE.QUEUE);
         }
      };

      sourceQueue = (Queue) sourceQueueFactory.createDestination();

      targetQueueFactory = new DestinationFactory() {
         @Override
         public Destination createDestination() throws Exception {
            return ActiveMQDestination.createDestination("/queue/targetQueue", ActiveMQDestination.TYPE.QUEUE);
         }
      };

      targetQueue = (Queue) targetQueueFactory.createDestination();

      sourceTopicFactory = new DestinationFactory() {
         @Override
         public Destination createDestination() throws Exception {
            return ActiveMQDestination.createDestination("/topic/sourceTopic", ActiveMQDestination.TYPE.TOPIC);
         }
      };

      sourceTopic = (Topic) sourceTopicFactory.createDestination();

      localTargetQueueFactory = new DestinationFactory() {
         @Override
         public Destination createDestination() throws Exception {
            return ActiveMQDestination.createDestination("/queue/localTargetQueue", ActiveMQDestination.TYPE.QUEUE);
         }
      };

      localTargetQueue = (Queue) localTargetQueueFactory.createDestination();
   }

   protected void sendMessages(final ConnectionFactory cf,
                               final Destination dest,
                               final int start,
                               final int numMessages,
                               final boolean persistent,
                               final boolean largeMessage) throws Exception {
      Connection conn = null;

      try {
         conn = cf.createConnection();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sess.createProducer(dest);

         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         for (int i = start; i < start + numMessages; i++) {
            if (largeMessage) {
               BytesMessage msg = sess.createBytesMessage();
               ((ActiveMQMessage) msg).setInputStream(ActiveMQTestBase.createFakeLargeStream(1024L * 1024L));
               msg.setStringProperty("msg", "message" + i);
               prod.send(msg);
            } else {
               TextMessage tm = sess.createTextMessage("message" + i);
               prod.send(tm);
            }

         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   protected void checkMessagesReceived(final ConnectionFactory cf,
                                        final Destination dest,
                                        final QualityOfServiceMode qosMode,
                                        final int numMessages,
                                        final boolean longWaitForFirst,
                                        final boolean largeMessage) throws Exception {
      Connection conn = null;

      try {
         conn = cf.createConnection();

         conn.start();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sess.createConsumer(dest);

         // Consume the messages

         Set<String> msgs = new HashSet<>();

         int count = 0;

         // We always wait longer for the first one - it may take some time to arrive especially if we are
         // waiting for recovery to kick in
         while (true) {
            Message tm = cons.receive(count == 0 ? (longWaitForFirst ? 60000 : 10000) : 5000);

            if (tm == null) {
               break;
            }

            // log.info("Got message " + tm.getText());

            if (largeMessage) {
               BytesMessage bmsg = (BytesMessage) tm;
               msgs.add(tm.getStringProperty("msg"));
               byte[] buffRead = new byte[1024];
               for (int i = 0; i < 1024; i++) {
                  Assert.assertEquals(1024, bmsg.readBytes(buffRead));
               }
            } else {
               msgs.add(((TextMessage) tm).getText());
            }

            count++;

         }

         if (qosMode == QualityOfServiceMode.ONCE_AND_ONLY_ONCE || qosMode == QualityOfServiceMode.DUPLICATES_OK) {
            // All the messages should be received

            for (int i = 0; i < numMessages; i++) {
               Assert.assertTrue("quality=" + qosMode + ", #=" + i + ", message=" + msgs, msgs.contains("message" + i));
            }

            // Should be no more
            if (qosMode == QualityOfServiceMode.ONCE_AND_ONLY_ONCE) {
               Assert.assertEquals(numMessages, msgs.size());
            }
         } else if (qosMode == QualityOfServiceMode.AT_MOST_ONCE) {
            // No *guarantee* that any messages will be received
            // but you still might get some depending on how/where the crash occurred
         }

      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   protected void checkAllMessageReceivedInOrder(final ConnectionFactory cf,
                                                 final Destination dest,
                                                 final int start,
                                                 final int numMessages,
                                                 final boolean largeMessage) throws Exception {
      Connection conn = null;
      try {
         conn = cf.createConnection();

         conn.start();

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sess.createConsumer(dest);

         // Consume the messages

         for (int i = 0; i < numMessages; i++) {
            Message tm = cons.receive(3000);

            Assert.assertNotNull(tm);

            if (largeMessage) {
               BytesMessage bmsg = (BytesMessage) tm;
               Assert.assertEquals("message" + (i + start), tm.getStringProperty("msg"));
               byte[] buffRead = new byte[1024];
               for (int j = 0; j < 1024; j++) {
                  Assert.assertEquals(1024, bmsg.readBytes(buffRead));
               }
            } else {
               Assert.assertEquals("message" + (i + start), ((TextMessage) tm).getText());
            }
         }
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   public boolean checkEmpty(final Queue queue, final int index) throws Exception {
      ManagementService managementService = server0.getManagementService();
      if (index == 1) {
         managementService = server1.getManagementService();
      }
      QueueControl queueControl = (QueueControl) managementService.getResource(ResourceNames.QUEUE + queue.getQueueName());

      //server may be closed
      if (queueControl != null) {
         queueControl.flushExecutor();
         Long messageCount = queueControl.getMessageCount();

         if (messageCount > 0) {
            queueControl.removeMessages(null);
         }
      }
      return true;
   }

   protected void checkNoSubscriptions(final Topic topic, final int index) throws Exception {
      ManagementService managementService = server0.getManagementService();
      if (index == 1) {
         managementService = server1.getManagementService();
      }
      AddressControl topicControl = (AddressControl) managementService.getResource(ResourceNames.ADDRESS + topic.getTopicName());
      if (topicControl != null) {
         Assert.assertEquals(0, topicControl.getQueueNames().length);
      }
   }

   protected void removeAllMessages(final String queueName, final int index) throws Exception {
      ManagementService managementService = server0.getManagementService();
      if (index == 1) {
         managementService = server1.getManagementService();
      }
      QueueControl queueControl = (QueueControl) managementService.getResource("queue." + queueName);
      if (queueControl != null) {
         queueControl.removeMessages(null);
      }
   }

   protected TransactionManager newTransactionManager() {
      return new TransactionManagerImple();
   }

}
