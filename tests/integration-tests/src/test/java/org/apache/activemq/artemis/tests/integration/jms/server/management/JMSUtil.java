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
package org.apache.activemq.artemis.tests.integration.jms.server.management;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.management.Notification;
import javax.management.NotificationListener;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.tests.integration.cluster.failover.FailoverTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;

public class JMSUtil {


   public static Connection createConnection(final String connectorFactory) throws JMSException {
      ActiveMQJMSConnectionFactory cf = (ActiveMQJMSConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(connectorFactory));

      cf.setBlockOnNonDurableSend(true);
      cf.setBlockOnDurableSend(true);
      cf.setBlockOnAcknowledge(true);

      return cf.createConnection();
   }

   public static ConnectionFactory createFactory(final String connectorFactory,
                                                 final long connectionTTL,
                                                 final long clientFailureCheckPeriod) throws JMSException {
      ActiveMQJMSConnectionFactory cf = (ActiveMQJMSConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(connectorFactory));

      cf.setBlockOnNonDurableSend(true);
      cf.setBlockOnDurableSend(true);
      cf.setBlockOnAcknowledge(true);
      cf.setConnectionTTL(connectionTTL);
      cf.setClientFailureCheckPeriod(clientFailureCheckPeriod);

      return cf;
   }

   static MessageConsumer createConsumer(final Connection connection,
                                         final Destination destination) throws JMSException {
      return createConsumer(connection, destination, Session.AUTO_ACKNOWLEDGE);
   }

   static MessageConsumer createConsumer(final Connection connection,
                                         final Destination destination,
                                         int ackMode) throws JMSException {
      Session s = connection.createSession(false, ackMode);

      return s.createConsumer(destination);
   }

   static TopicSubscriber createDurableSubscriber(final Connection connection,
                                                  final Topic topic,
                                                  final String clientID,
                                                  final String subscriptionName) throws JMSException {
      return createDurableSubscriber(connection, topic, clientID, subscriptionName, Session.AUTO_ACKNOWLEDGE);
   }

   static TopicSubscriber createDurableSubscriber(final Connection connection,
                                                  final Topic topic,
                                                  final String clientID,
                                                  final String subscriptionName,
                                                  final int ackMode) throws JMSException {
      connection.setClientID(clientID);
      Session s = connection.createSession(false, ackMode);

      return s.createDurableSubscriber(topic, subscriptionName);
   }

   public static String[] sendMessages(final Destination destination, final int messagesToSend) throws Exception {
      ActiveMQJMSConnectionFactory cf = (ActiveMQJMSConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName()));
      return JMSUtil.sendMessages(cf, destination, messagesToSend);
   }

   public static String[] sendMessages(final ConnectionFactory cf,
                                       final Destination destination,
                                       final int messagesToSend) throws Exception {
      String[] messageIDs = new String[messagesToSend];

      Connection conn = cf.createConnection();

      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer producer = s.createProducer(destination);

      for (int i = 0; i < messagesToSend; i++) {
         Message m = s.createTextMessage(RandomUtil.randomUUIDString());
         producer.send(m);
         messageIDs[i] = m.getJMSMessageID();
      }

      conn.close();

      return messageIDs;
   }

   public static Message sendMessageWithProperty(final Session session,
                                                 final Destination destination,
                                                 final String key,
                                                 final long value) throws JMSException {
      MessageProducer producer = session.createProducer(destination);
      Message message = session.createMessage();
      message.setLongProperty(key, value);
      producer.send(message);
      return message;
   }

   public static BytesMessage sendByteMessage(final Session session,
                                              final Destination destination,
                                              final byte[] bytes) throws JMSException {
      MessageProducer producer = session.createProducer(destination);
      BytesMessage message = session.createBytesMessage();
      message.writeBytes(bytes);
      producer.send(message);
      return message;
   }

   public static Message sendMessageWithProperty(final Session session,
                                                 final Destination destination,
                                                 final String key,
                                                 final int value) throws JMSException {
      MessageProducer producer = session.createProducer(destination);
      Message message = session.createMessage();
      message.setIntProperty(key, value);
      producer.send(message);
      return message;
   }

   public static Message sendMessageWithProperty(final Session session,
                                                 final Destination destination,
                                                 final String key,
                                                 final String value) throws JMSException {
      MessageProducer producer = session.createProducer(destination);
      Message message = session.createMessage();
      message.setStringProperty(key, value);
      producer.send(message);
      return message;
   }

   public static Message sendMessageWithReplyTo(final Session session,
                                                final Destination destination,
                                                final String replyTo) throws JMSException {
      MessageProducer producer = session.createProducer(destination);
      Message message = session.createMessage();
      message.setJMSReplyTo(ActiveMQJMSClient.createQueue(replyTo));
      producer.send(message);
      return message;
   }

   public static void consumeMessages(final int expected, final Destination dest) throws JMSException {
      Connection connection = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(dest);

         connection.start();

         Message m = null;
         for (int i = 0; i < expected; i++) {
            m = consumer.receive(500);
            assertNotNull(m, "expected to received " + expected + " messages, got only " + (i + 1));
         }
         m = consumer.receiveNoWait();
         assertNull(m, "received one more message than expected (" + expected + ")");
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   public static void waitForServer(ActiveMQServer server) throws InterruptedException {
      long timetowait = System.currentTimeMillis() + 5000;
      while (!server.isStarted()) {
         Thread.sleep(100);
         if (server.isStarted()) {
            break;
         } else if (System.currentTimeMillis() > timetowait) {
            throw new IllegalStateException("server didn't start");
         }
      }
   }

   public static void crash(ActiveMQServer server, ClientSession... sessions) throws Exception {
      final CountDownLatch latch = new CountDownLatch(sessions.length);

      class MyListener implements SessionFailureListener {

         @Override
         public void connectionFailed(final ActiveMQException me, boolean failedOver) {
            latch.countDown();
         }

         @Override
         public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
            connectionFailed(me, failedOver);
         }

         @Override
         public void beforeReconnect(ActiveMQException exception) {
         }
      }
      for (ClientSession session : sessions) {
         session.addFailureListener(new MyListener());
      }

      ClusterManager clusterManager = server.getClusterManager();
      clusterManager.clear();
      server.fail(true);

      // Wait to be informed of failure
      boolean ok = latch.await(10000, TimeUnit.MILLISECONDS);

      assertTrue(ok);
   }



   public static ActiveMQConnection createConnectionAndWaitForTopology(ActiveMQConnectionFactory factory,
                                                                       int topologyMembers,
                                                                       int timeout) throws Exception {
      ActiveMQConnection conn;
      CountDownLatch countDownLatch = new CountDownLatch(topologyMembers);

      ServerLocator locator = factory.getServerLocator();

      locator.addClusterTopologyListener(new FailoverTestBase.LatchClusterTopologyListener(countDownLatch));

      conn = (ActiveMQConnection) factory.createConnection();

      boolean ok = countDownLatch.await(timeout, TimeUnit.SECONDS);
      if (!ok) {
         throw new IllegalStateException("timed out waiting for topology");
      }
      return conn;
   }

   public static void waitForFailoverTopology(final int timeToWait,
                                              final ActiveMQServer backupServer,
                                              final ActiveMQServer... liveServers) throws Exception {
      long start = System.currentTimeMillis();

      final int waitMillis = 2000;
      final int sleepTime = 50;
      int nWaits = 0;
      while ((backupServer.getClusterManager() == null || backupServer.getClusterManager().getClusterConnections().size() != 1) && nWaits++ < waitMillis / sleepTime) {
         Thread.sleep(sleepTime);
      }
      Set<ClusterConnection> ccs = backupServer.getClusterManager().getClusterConnections();

      if (ccs.size() != 1) {
         throw new IllegalStateException("You need a single cluster connection on this version of waitForTopology on ServiceTestBase");
      }

      boolean exists = false;

      for (ActiveMQServer liveServer : liveServers) {
         ClusterConnectionImpl clusterConnection = (ClusterConnectionImpl) ccs.iterator().next();
         Topology topology = clusterConnection.getTopology();
         TransportConfiguration nodeConnector = liveServer.getClusterManager().getClusterConnections().iterator().next().getConnector();
         do {
            Collection<TopologyMemberImpl> members = topology.getMembers();
            for (TopologyMemberImpl member : members) {
               if (member.getConnector().getA() != null && member.getConnector().getA().equals(nodeConnector)) {
                  exists = true;
                  break;
               }
            }
            if (exists) {
               break;
            }
            Thread.sleep(10);
         }
         while (System.currentTimeMillis() - start < timeToWait);
         if (!exists) {
            String msg = "Timed out waiting for cluster topology of " + backupServer +
               " (received " +
               topology.getMembers().size() +
               ") topology = " +
               topology +
               ")";

            //logTopologyDiagram();

            throw new Exception(msg);
         }
      }
   }

   public static class JMXListener implements NotificationListener {

      private Notification notif;

      @Override
      public void handleNotification(Notification notification, Object handback) {
         notif = notification;
      }

      public Notification getNotification() {
         return notif;
      }
   }
}
