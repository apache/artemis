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

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
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
import org.apache.activemq.artemis.core.config.ha.SharedStoreMasterPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreSlavePolicyConfiguration;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A JMSFailoverTest
 * <br>
 * A simple test to test setFailoverListener when using the JMS API.
 */
public class JMSFailoverListenerTest extends ActiveMQTestBase {


   protected Configuration backupConf;

   protected Configuration liveConf;

   protected ActiveMQServer liveServer;

   protected ActiveMQServer backupServer;

   protected Map<String, Object> backupParams = new HashMap<>();

   private TransportConfiguration backuptc;

   private TransportConfiguration livetc;

   private TransportConfiguration liveAcceptortc;

   private TransportConfiguration backupAcceptortc;



   @Test
   public void testAutomaticFailover() throws Exception {
      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, livetc);
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

      SimpleString jmsQueueName = new SimpleString("myqueue");

      coreSession.createQueue(new QueueConfiguration(jmsQueueName).setRoutingType(RoutingType.ANYCAST));

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

      Wait.assertEquals(numMessages, liveServer.locateQueue(jmsQueueName)::getMessageCount);

      conn.start();

      instanceLog.debug("sent messages and started connection");

      JMSUtil.crash(liveServer, ((ActiveMQSession) sess).getCoreSession());

      Wait.assertTrue(() -> FailoverEventType.FAILURE_DETECTED == listener.get(0));
      for (int i = 0; i < numMessages; i++) {
         instanceLog.debug("got message " + i);

         BytesMessage bm = (BytesMessage) consumer.receive(1000);

         Assert.assertNotNull(bm);

         Assert.assertEquals(body.length, bm.getBodyLength());
      }

      TextMessage tm = (TextMessage) consumer.receiveNoWait();

      Assert.assertNull(tm);
      Assert.assertEquals(FailoverEventType.FAILOVER_COMPLETED, listener.get(1));

      conn.close();
      Assert.assertEquals("Expected 2 FailoverEvents to be triggered", 2, listener.size());
   }

   @Test
   public void testManualFailover() throws Exception {
      ActiveMQConnectionFactory jbcfLive = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      jbcfLive.setBlockOnNonDurableSend(true);
      jbcfLive.setBlockOnDurableSend(true);
      jbcfLive.setCallTimeout(1000);

      ActiveMQConnectionFactory jbcfBackup = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY, backupParams));
      jbcfBackup.setBlockOnNonDurableSend(true);
      jbcfBackup.setBlockOnDurableSend(true);
      jbcfBackup.setInitialConnectAttempts(-1);
      jbcfBackup.setReconnectAttempts(-1);
      jbcfBackup.setRetryInterval(100);

      ActiveMQConnection connLive = (ActiveMQConnection) jbcfLive.createConnection();

      MyFailoverListener listener = new MyFailoverListener();

      connLive.setFailoverListener(listener);

      Session sessLive = connLive.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSessionLive = ((ActiveMQSession) sessLive).getCoreSession();

      SimpleString jmsQueueName = new SimpleString("myqueue");

      coreSessionLive.createQueue(new QueueConfiguration(jmsQueueName).setRoutingType(RoutingType.ANYCAST));

      Queue queue = sessLive.createQueue("myqueue");

      final int numMessages = 1000;

      MessageProducer producerLive = sessLive.createProducer(queue);

      for (int i = 0; i < numMessages; i++) {
         TextMessage tm = sessLive.createTextMessage("message" + i);

         producerLive.send(tm);
      }

      // Note we block on P send to make sure all messages get to server before failover

      JMSUtil.crash(liveServer, coreSessionLive);
      Assert.assertEquals(FailoverEventType.FAILURE_DETECTED, listener.get(0));
      connLive.close();

      // Now recreate on backup

      Connection connBackup = jbcfBackup.createConnection();

      Session sessBackup = connBackup.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer consumerBackup = sessBackup.createConsumer(queue);

      connBackup.start();

      for (int i = 0; i < numMessages; i++) {
         TextMessage tm = (TextMessage) consumerBackup.receive(1000);

         Assert.assertNotNull(tm);

         Assert.assertEquals("message" + i, tm.getText());
      }

      TextMessage tm = (TextMessage) consumerBackup.receiveNoWait();
      Assert.assertEquals(FailoverEventType.FAILOVER_FAILED, listener.get(1));
      Assert.assertEquals("Expected 2 FailoverEvents to be triggered", 2, listener.size());
      Assert.assertNull(tm);

      connBackup.close();
   }



   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      startServers();
   }

   /**
    * @throws Exception
    */
   protected void startServers() throws Exception {
      NodeManager nodeManager = new InVMNodeManager(false);
      backuptc = new TransportConfiguration(INVM_CONNECTOR_FACTORY, backupParams);
      livetc = new TransportConfiguration(INVM_CONNECTOR_FACTORY);

      liveAcceptortc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY);
      backupAcceptortc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY, backupParams);

      backupParams.put(TransportConstants.SERVER_ID_PROP_NAME, 1);

      backupConf = createBasicConfig().addAcceptorConfiguration(backupAcceptortc).addConnectorConfiguration(livetc.getName(), livetc).addConnectorConfiguration(backuptc.getName(), backuptc).setJournalType(getDefaultJournalType()).addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, backupParams)).setBindingsDirectory(getBindingsDir()).setJournalMinFiles(2).setJournalDirectory(getJournalDir()).setPagingDirectory(getPageDir()).setLargeMessagesDirectory(getLargeMessagesDir()).setPersistenceEnabled(true).setHAPolicyConfiguration(new SharedStoreSlavePolicyConfiguration()).addClusterConfiguration(basicClusterConnectionConfig(backuptc.getName(), livetc.getName()));
      backupConf.setConnectionTtlCheckInterval(100);

      backupServer = addServer(new InVMNodeManagerServer(backupConf, nodeManager));

      backupServer.setIdentity("JMSBackup");
      instanceLog.debug("Starting backup");
      backupServer.start();

      liveConf = createBasicConfig().setJournalDirectory(getJournalDir()).setBindingsDirectory(getBindingsDir()).addAcceptorConfiguration(liveAcceptortc).setJournalType(getDefaultJournalType()).setBindingsDirectory(getBindingsDir()).setJournalMinFiles(2).setJournalDirectory(getJournalDir()).setPagingDirectory(getPageDir()).setLargeMessagesDirectory(getLargeMessagesDir()).addConnectorConfiguration(livetc.getName(), livetc).setPersistenceEnabled(true).setHAPolicyConfiguration(new SharedStoreMasterPolicyConfiguration()).addClusterConfiguration(basicClusterConnectionConfig(livetc.getName()));
      liveConf.setConnectionTtlCheckInterval(100);

      liveServer = addServer(new InVMNodeManagerServer(liveConf, nodeManager));

      liveServer.setIdentity("JMSLive");
      instanceLog.debug("Starting life");

      liveServer.start();

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

         Assert.assertTrue(eventTypeList.size() >= elements);
      }

      @Override
      public void failoverEvent(FailoverEventType eventType) {
         eventTypeList.add(eventType);
      }
   }
}
