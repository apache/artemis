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
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveContinuationMessage;
import org.apache.activemq.artemis.core.registry.JndiBindingRegistry;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.jms.server.JMSServerManager;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.integration.jms.server.management.JMSUtil;
import org.apache.activemq.artemis.tests.unit.util.InVMNamingContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.InVMNodeManagerServer;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A JMSFailoverTest
 * <p>
 * A simple test to test failover when using the JMS API. Most of the failover tests are done on the Core API.
 */
public class JMSFailoverTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());



   protected InVMNamingContext ctx1 = new InVMNamingContext();

   protected InVMNamingContext ctx2 = new InVMNamingContext();

   protected Configuration backupConf;

   protected Configuration primaryConf;

   protected JMSServerManager primaryJMSServer;

   protected ActiveMQServer primaryServer;

   protected JMSServerManager backupJMSServer;

   protected ActiveMQServer backupServer;

   protected Map<String, Object> backupParams = new HashMap<>();

   private TransportConfiguration backuptc;

   private TransportConfiguration primarytc;

   private TransportConfiguration primaryAcceptortc;

   private TransportConfiguration backupAcceptortc;


   @Test
   public void testCreateQueue() throws Exception {
      primaryJMSServer.createQueue(true, "queue1", null, true, "/queue/queue1");
      assertNotNull(ctx1.lookup("/queue/queue1"));

      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, primarytc);

      jbcf.setReconnectAttempts(-1);

      Connection conn = null;

      try {
         conn = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         ClientSession coreSession = ((ActiveMQSession) sess).getCoreSession();

         JMSUtil.crash(primaryServer, coreSession);

         assertNotNull(ctx2.lookup("/queue/queue1"));
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testCreateTopic() throws Exception {
      primaryJMSServer.createTopic(true, "topic", "/topic/t1");
      assertNotNull(ctx1.lookup("//topic/t1"));

      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, primarytc);

      jbcf.setReconnectAttempts(-1);

      Connection conn = null;

      try {
         conn = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         ClientSession coreSession = ((ActiveMQSession) sess).getCoreSession();

         JMSUtil.crash(primaryServer, coreSession);

         assertNotNull(ctx2.lookup("/topic/t1"));
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testAutomaticFailover() throws Exception {
      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, primarytc);
      jbcf.setReconnectAttempts(-1);
      jbcf.setBlockOnDurableSend(true);
      jbcf.setBlockOnNonDurableSend(true);

      // Note we set consumer window size to a value so we can verify that consumer credit re-sending
      // works properly on failover
      // The value is small enough that credits will have to be resent several time

      final int numMessages = 10;

      final int bodySize = 1000;

      jbcf.setConsumerWindowSize(numMessages * bodySize / 10);

      Connection conn = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);

      MyExceptionListener listener = new MyExceptionListener();

      conn.setExceptionListener(listener);

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

      conn.start();

      logger.debug("sent messages and started connection");

      Thread.sleep(2000);

      JMSUtil.crash(primaryServer, ((ActiveMQSession) sess).getCoreSession());

      for (int i = 0; i < numMessages; i++) {
         logger.debug("got message {}", i);

         BytesMessage bm = (BytesMessage) consumer.receive(1000);

         assertNotNull(bm);

         assertEquals(body.length, bm.getBodyLength());
      }

      TextMessage tm = (TextMessage) consumer.receiveNoWait();

      assertNull(tm);

      conn.close();

   }

   @Test
   public void testManualFailover() throws Exception {
      ActiveMQConnectionFactory jbcfPrimary = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      jbcfPrimary.setBlockOnNonDurableSend(true);
      jbcfPrimary.setBlockOnDurableSend(true);

      ActiveMQConnectionFactory jbcfBackup = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY, backupParams));

      jbcfBackup.setBlockOnNonDurableSend(true);
      jbcfBackup.setBlockOnDurableSend(true);
      jbcfBackup.setInitialConnectAttempts(-1);
      jbcfBackup.setReconnectAttempts(-1);

      Connection connPrimary = jbcfPrimary.createConnection();

      MyExceptionListener listener = new MyExceptionListener();

      connPrimary.setExceptionListener(listener);

      Session sessPrimary = connPrimary.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSessionPrimary = ((ActiveMQSession) sessPrimary).getCoreSession();

      RemotingConnection coreConnPrimary = ((ClientSessionInternal) coreSessionPrimary).getConnection();

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

      assertNull(tm);

      connBackup.close();
   }

   @Test
   public void testSendReceiveLargeMessages() throws Exception {
      SimpleString QUEUE = SimpleString.of("somequeue");

      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, primarytc, backuptc);
      jbcf.setReconnectAttempts(-1);
      jbcf.setBlockOnDurableSend(true);
      jbcf.setBlockOnNonDurableSend(true);
      jbcf.setMinLargeMessageSize(1024);
      //jbcf.setConsumerWindowSize(0);
      //jbcf.setMinLargeMessageSize(1024);

      final CountDownLatch flagAlign = new CountDownLatch(1);

      final CountDownLatch waitToKill = new CountDownLatch(1);

      final AtomicBoolean killed = new AtomicBoolean(false);

      jbcf.getServerLocator().addIncomingInterceptor(new Interceptor() {
         int count = 0;

         @Override
         public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {

            if (packet instanceof SessionReceiveContinuationMessage) {
               if (count++ == 300 && !killed.get()) {
                  killed.set(true);
                  waitToKill.countDown();
               }
            }
            return true;
         }
      });

      Connection conn = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);
      Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
      final ClientSession coreSession = ((ActiveMQSession) sess).getCoreSession();

      // The thread that will fail the server
      Thread spoilerThread = new Thread(() -> {
         flagAlign.countDown();
         // a large timeout just to help in case of debugging
         try {
            waitToKill.await(120, TimeUnit.SECONDS);
         } catch (Exception e) {
            e.printStackTrace();
         }

         try {
            JMSUtil.crash(primaryServer, coreSession);
         } catch (Exception e) {
            e.printStackTrace();
         }
      });

      coreSession.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));

      Queue queue = sess.createQueue("somequeue");

      MessageProducer producer = sess.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      for (int i = 0; i < 100; i++) {
         TextMessage message = sess.createTextMessage(new String(new byte[10 * 1024]));
         producer.send(message);

         if (i % 10 == 0) {
            sess.commit();
         }
      }

      sess.commit();

      conn.start();

      spoilerThread.start();

      assertTrue(flagAlign.await(10, TimeUnit.SECONDS));

      MessageConsumer consumer = sess.createConsumer(queue);

      // We won't receive the whole thing here.. we just want to validate if message will arrive or not...
      // this test is not meant to validate transactionality during Failover as that would require XA and recovery
      for (int i = 0; i < 90; i++) {
         TextMessage message = null;

         int retryNrs = 0;
         do {
            retryNrs++;
            try {
               message = (TextMessage) consumer.receive(5000);
               assertNotNull(message);
               break;
            } catch (JMSException e) {
               new Exception("Exception on receive message", e).printStackTrace();
            }
         }
         while (retryNrs < 10);

         assertNotNull(message);

         try {
            sess.commit();
         } catch (Exception e) {
            logger.debug("Exception during commit", e);
            sess.rollback();
         }

      }

      conn.close();

      spoilerThread.join();

   }

   @Test
   public void testCreateNewConnectionAfterFailover() throws Exception {
      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, primarytc);
      jbcf.setInitialConnectAttempts(5);
      jbcf.setRetryInterval(1000);
      jbcf.setReconnectAttempts(-1);

      Connection conn1 = null, conn2 = null, conn3 = null;

      try {
         conn1 = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);

         conn2 = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         ClientSession coreSession1 = ((ActiveMQSession)sess1).getCoreSession();
         ClientSession coreSession2 = ((ActiveMQSession)sess2).getCoreSession();

         Topology fullTopology = jbcf.getServerLocator().getTopology();
         Collection<TopologyMemberImpl> members = fullTopology.getMembers();
         assertEquals(1, members.size());
         TopologyMemberImpl member = members.iterator().next();
         TransportConfiguration tcPrimary = member.getPrimary();
         TransportConfiguration tcBackup = member.getBackup();

         System.out.println("primary tc: " + tcPrimary);
         System.out.println("Backup tc: " + tcBackup);

         JMSUtil.crash(primaryServer, coreSession1, coreSession2);

         waitForServerToStart(backupServer);

         //now pretending that the primary down event hasn't been propagated to client
         simulatePrimaryDownHasNotReachClient((ServerLocatorImpl) jbcf.getServerLocator(), tcPrimary, tcBackup);

         //now create a new connection after primary is down
         try {
            conn3 = jbcf.createConnection();
         } catch (Exception e) {
            fail("The new connection should be established successfully after failover");
         }
      } finally {
         if (conn1 != null) {
            conn1.close();
         }
         if (conn2 != null) {
            conn2.close();
         }
         if (conn3 != null) {
            conn3.close();
         }
      }
   }

   private void simulatePrimaryDownHasNotReachClient(ServerLocatorImpl locator, TransportConfiguration tcPrimary, TransportConfiguration tcBackup) throws NoSuchFieldException, IllegalAccessException {
      Field f = locator.getClass().getDeclaredField("topologyArray");
      f.setAccessible(true);

      Pair<TransportConfiguration, TransportConfiguration>[] value = (Pair<TransportConfiguration, TransportConfiguration>[]) f.get(locator);
      assertEquals(1, value.length);
      Pair<TransportConfiguration, TransportConfiguration> member = value[0];
      member.setA(tcPrimary);
      member.setB(tcBackup);
      f.set(locator, value);
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      startServers();
   }

   protected void startServers() throws Exception {
      final boolean sharedStore = true;
      NodeManager nodeManager = new InVMNodeManager(!sharedStore);
      backuptc = new TransportConfiguration(INVM_CONNECTOR_FACTORY, backupParams);
      primarytc = new TransportConfiguration(INVM_CONNECTOR_FACTORY);

      primaryAcceptortc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY);

      backupAcceptortc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY, backupParams);

      backupParams.put(TransportConstants.SERVER_ID_PROP_NAME, 1);

      backuptc.getParams().put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      backupAcceptortc.getParams().put(TransportConstants.SERVER_ID_PROP_NAME, 1);

      backupConf = createBasicConfig().addConnectorConfiguration(primarytc.getName(), primarytc).addConnectorConfiguration(backuptc.getName(), backuptc).setSecurityEnabled(false).setJournalType(getDefaultJournalType()).addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, backupParams)).setBindingsDirectory(getBindingsDir()).setJournalMinFiles(2).setJournalDirectory(getJournalDir()).setPagingDirectory(getPageDir()).setLargeMessagesDirectory(getLargeMessagesDir()).setPersistenceEnabled(true).setHAPolicyConfiguration(sharedStore ? new SharedStoreBackupPolicyConfiguration() : new ReplicaPolicyConfiguration()).addClusterConfiguration(basicClusterConnectionConfig(backuptc.getName(), primarytc.getName()));

      backupServer = addServer(new InVMNodeManagerServer(backupConf, nodeManager));

      backupJMSServer = new JMSServerManagerImpl(backupServer);

      backupJMSServer.setRegistry(new JndiBindingRegistry(ctx2));

      backupJMSServer.getActiveMQServer().setIdentity("JMSBackup");
      logger.debug("Starting backup");
      backupJMSServer.start();

      primaryConf = createBasicConfig().setJournalDirectory(getJournalDir()).setBindingsDirectory(getBindingsDir()).setSecurityEnabled(false).addAcceptorConfiguration(primaryAcceptortc).setJournalType(getDefaultJournalType()).setBindingsDirectory(getBindingsDir()).setJournalMinFiles(2).setJournalDirectory(getJournalDir()).setPagingDirectory(getPageDir()).setLargeMessagesDirectory(getLargeMessagesDir()).addConnectorConfiguration(primarytc.getName(), primarytc).setPersistenceEnabled(true).setHAPolicyConfiguration(sharedStore ? new SharedStorePrimaryPolicyConfiguration() : new ReplicatedPolicyConfiguration()).addClusterConfiguration(basicClusterConnectionConfig(primarytc.getName()));

      primaryServer = addServer(new InVMNodeManagerServer(primaryConf, nodeManager));

      primaryJMSServer = new JMSServerManagerImpl(primaryServer);

      primaryJMSServer.setRegistry(new JndiBindingRegistry(ctx1));

      primaryJMSServer.getActiveMQServer().setIdentity("JMSPrimary");
      logger.debug("Starting primary");

      primaryJMSServer.start();

      JMSUtil.waitForServer(backupServer);
   }



   private static class MyExceptionListener implements ExceptionListener {

      volatile JMSException e;

      @Override
      public void onException(final JMSException e) {
         this.e = e;
      }
   }
}
