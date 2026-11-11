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
package org.apache.activemq.artemis.tests.integration.jms.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.tests.integration.jms.server.management.JMSUtil;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.InVMNodeManagerServer;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JMSFailoverTest
 * <p>
 * A simple test to test setFailoverListener when using the JMS API.
 */
public class JMSFailoverListenerTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected Configuration backupConf;

   protected Configuration primaryConf;

   protected ActiveMQServer primaryServer;

   protected ActiveMQServer backupServer;

   protected Map<String, Object> backupParams = new HashMap<>();

   private TransportConfiguration backuptc;

   private TransportConfiguration primarytc;

   private TransportConfiguration primaryAcceptortc;

   private TransportConfiguration backupAcceptortc;



   @Test
   public void testAutomaticFailover() throws Exception {
      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, primarytc);
      jbcf.setReconnectAttempts(-1);
      jbcf.setRetryInterval(100);
      jbcf.setConnectionTTL(500);
      jbcf.setClientFailureCheckPeriod(100);
      jbcf.setBlockOnDurableSend(true);
      jbcf.setBlockOnNonDurableSend(true);
      jbcf.setCallTimeout(1000);

      // Note we set consumer window size to a value so we can verify that consumer credit re-sending
      // works properly on failover
      // The value is small enough that credits will have to be resent several time

      final int numMessages = 10;

      final int bodySize = 1000;

      jbcf.setConsumerWindowSize(numMessages * bodySize / 10);

      ActiveMQConnection conn = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);

      MyFailoverListener listener = new MyFailoverListener();

      conn.setFailoverListener(listener);

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSession = ((ActiveMQSession) sess).getCoreSession();

      SimpleString jmsQueueName = SimpleString.of("myqueue");

      coreSession.createQueue(QueueConfiguration.of(jmsQueueName).setRoutingType(RoutingType.ANYCAST));

      Queue queue = sess.createQueue("myqueue");

      MessageProducer producer = sess.createProducer(queue);

      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      MessageConsumer consumer = sess.createConsumer(queue);

      byte[] body = RandomUtil.randomBytes(bodySize);

      for (int i = 0; i < numMessages; i++) {
         BytesMessage bm = sess.createBytesMessage();

         bm.writeBytes(body);

         producer.send(bm);
      }

      Wait.assertEquals(numMessages, primaryServer.locateQueue(jmsQueueName)::getMessageCount);

      conn.start();

      logger.debug("sent messages and started connection");

      JMSUtil.crash(primaryServer, ((ActiveMQSession) sess).getCoreSession());

      Wait.assertTrue(() -> FailoverEventType.FAILURE_DETECTED == listener.get(0));
      for (int i = 0; i < numMessages; i++) {
         logger.debug("got message {}", i);

         BytesMessage bm = (BytesMessage) consumer.receive(1000);

         assertNotNull(bm);

         assertEquals(body.length, bm.getBodyLength());
      }

      TextMessage tm = (TextMessage) consumer.receiveNoWait();

      assertNull(tm);
      assertEquals(FailoverEventType.FAILOVER_COMPLETED, listener.get(1));

      conn.close();
      assertEquals(2, listener.size(), "Expected 2 FailoverEvents to be triggered");
   }

   @Test
   public void testManualFailover() throws Exception {
      ActiveMQConnectionFactory jbcfPrimary = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      jbcfPrimary.setBlockOnNonDurableSend(true);
      jbcfPrimary.setBlockOnDurableSend(true);
      jbcfPrimary.setCallTimeout(1000);

      ActiveMQConnectionFactory jbcfBackup = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY, backupParams));
      jbcfBackup.setBlockOnNonDurableSend(true);
      jbcfBackup.setBlockOnDurableSend(true);
      jbcfBackup.setInitialConnectAttempts(-1);
      jbcfBackup.setReconnectAttempts(-1);
      jbcfBackup.setRetryInterval(100);

      ActiveMQConnection connPrimary = (ActiveMQConnection) jbcfPrimary.createConnection();

      MyFailoverListener listener = new MyFailoverListener();

      connPrimary.setFailoverListener(listener);

      Session sessPrimary = connPrimary.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSessionPrimary = ((ActiveMQSession) sessPrimary).getCoreSession();

      SimpleString jmsQueueName = SimpleString.of("myqueue");

      coreSessionPrimary.createQueue(QueueConfiguration.of(jmsQueueName).setRoutingType(RoutingType.ANYCAST));

      Queue queue = sessPrimary.createQueue("myqueue");

      final int numMessages = 1000;

      MessageProducer producerPrimary = sessPrimary.createProducer(queue);

      for (int i = 0; i < numMessages; i++) {
         TextMessage tm = sessPrimary.createTextMessage("message" + i);

         producerPrimary.send(tm);
      }

      // Note we block on P send to make sure all messages get to server before failover

      JMSUtil.crash(primaryServer, coreSessionPrimary);
      assertEquals(FailoverEventType.FAILURE_DETECTED, listener.get(0));
      connPrimary.close();

      // Now recreate on backup

      Connection connBackup = jbcfBackup.createConnection();

      Session sessBackup = connBackup.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer consumerBackup = sessBackup.createConsumer(queue);

      connBackup.start();

      for (int i = 0; i < numMessages; i++) {
         TextMessage tm = (TextMessage) consumerBackup.receive(1000);

         assertNotNull(tm);

         assertEquals("message" + i, tm.getText());
      }

      TextMessage tm = (TextMessage) consumerBackup.receiveNoWait();
      assertEquals(FailoverEventType.FAILOVER_FAILED, listener.get(1));
      assertEquals(2, listener.size(), "Expected 2 FailoverEvents to be triggered");
      assertNull(tm);

      connBackup.close();
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      startServers();
   }

   protected void startServers() throws Exception {
      NodeManager nodeManager = new InVMNodeManager(false);
      backuptc = new TransportConfiguration(INVM_CONNECTOR_FACTORY, backupParams);
      primarytc = new TransportConfiguration(INVM_CONNECTOR_FACTORY);

      primaryAcceptortc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY);
      backupAcceptortc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY, backupParams);

      backupParams.put(TransportConstants.SERVER_ID_PROP_NAME, 1);

      backupConf = createBasicConfig().addAcceptorConfiguration(backupAcceptortc).addConnectorConfiguration(primarytc.getName(), primarytc).addConnectorConfiguration(backuptc.getName(), backuptc).setJournalType(getDefaultJournalType()).addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, backupParams)).setBindingsDirectory(getBindingsDir()).setJournalMinFiles(2).setJournalDirectory(getJournalDir()).setPagingDirectory(getPageDir()).setLargeMessagesDirectory(getLargeMessagesDir()).setPersistenceEnabled(true).setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration()).addClusterConfiguration(basicClusterConnectionConfig(primarytc.getName(), backuptc.getName()));
      backupConf.setConnectionTtlCheckInterval(100);

      backupServer = addServer(new InVMNodeManagerServer(backupConf, nodeManager));

      backupServer.setIdentity("JMSBackup");
      logger.debug("Starting backup");
      backupServer.start();

      primaryConf = createBasicConfig().setJournalDirectory(getJournalDir()).setBindingsDirectory(getBindingsDir()).addAcceptorConfiguration(primaryAcceptortc).setJournalType(getDefaultJournalType()).setBindingsDirectory(getBindingsDir()).setJournalMinFiles(2).setJournalDirectory(getJournalDir()).setPagingDirectory(getPageDir()).setLargeMessagesDirectory(getLargeMessagesDir()).addConnectorConfiguration(primarytc.getName(), primarytc).setPersistenceEnabled(true).setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration()).addClusterConfiguration(basicClusterConnectionConfig(primarytc.getName()));
      primaryConf.setConnectionTtlCheckInterval(100);

      primaryServer = addServer(new InVMNodeManagerServer(primaryConf, nodeManager));

      primaryServer.setIdentity("JMSPrimary");
      logger.debug("Starting life");

      primaryServer.start();

      JMSUtil.waitForServer(backupServer);
   }



   private static class MyFailoverListener implements FailoverEventListener {

      private List<FailoverEventType> eventTypeList = new ArrayList<>();

      public FailoverEventType get(int element) {
         waitForElements(element + 1);
         return eventTypeList.get(element);
      }

      public int size() {
         return eventTypeList.size();
      }

      private void waitForElements(int elements) {
         long timeout = System.currentTimeMillis() + 5000;
         while (timeout > System.currentTimeMillis() && eventTypeList.size() < elements) {
            try {
               Thread.sleep(1);
            } catch (Throwable e) {
               fail(e.getMessage());
            }
         }

         assertTrue(eventTypeList.size() >= elements);
      }

      @Override
      public void failoverEvent(FailoverEventType eventType) {
         eventTypeList.add(eventType);
      }
   }
}
