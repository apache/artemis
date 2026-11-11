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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientConnectorFailoverTest extends StaticClusterWithBackupFailoverTest {

   private static final String TEST_PARAM = "TEST";

   @Override
   protected boolean isNetty() {
      return true;
   }

   @Test
   public void testConsumerAfterFailover() throws Exception {
      setupCluster();
      startServers(getPrimaryServerIDs());
      startServers(getBackupServerIDs());

      for (int i : getPrimaryServerIDs()) {
         waitForTopology(servers[i], 3, 3);
      }

      for (int i : getBackupServerIDs()) {
         waitForFailoverTopology(i, 0, 1, 2);
      }

      for (int i : getPrimaryServerIDs()) {
         setupSessionFactory(i, i + 3, isNetty(), false);
         createQueue(i, QUEUES_TESTADDRESS, QUEUE_NAME, null, true);
      }

      List<TransportConfiguration> transportConfigList = new ArrayList<>();
      for (int i : getServerIDs()) {
         Map<String, Object> params = generateParams(i, isNetty());
         TransportConfiguration serverToTC = createTransportConfiguration("node" + i, isNetty(), false, params);
         serverToTC.getExtraParams().put(TEST_PARAM, TEST_PARAM);
         transportConfigList.add(serverToTC);
      }
      TransportConfiguration[] transportConfigs = transportConfigList.toArray(new TransportConfiguration[transportConfigList.size()]);

      try (ServerLocator serverLocator = new ServerLocatorImpl(true, transportConfigs)) {
         serverLocator.setReconnectAttempts(-1);
         try (ClientSessionFactory sessionFactory = serverLocator.createSessionFactory()) {
            try (ClientSession clientSession = sessionFactory.createSession()) {
               clientSession.start();

               TransportConfiguration backupConnector = (TransportConfiguration)
                  ((ClientSessionFactoryImpl)sessionFactory).getBackupConnector();
               assertNotEquals(backupConnector.getName(), sessionFactory.getConnectorConfiguration().getName());

               int serverIdBeforeCrash = Integer.parseInt(sessionFactory.
                  getConnectorConfiguration().getName().substring(4));

               try (ClientProducer clientProducer = clientSession.createProducer(QUEUES_TESTADDRESS)) {
                  clientProducer.send(clientSession.createMessage(true));
               }

               crashAndWaitForFailure(getServer(serverIdBeforeCrash), clientSession);

               assertEquals(backupConnector.getName(), sessionFactory.getConnectorConfiguration().getName());
               assertEquals(TEST_PARAM, sessionFactory.getConnectorConfiguration().getExtraParams().get(TEST_PARAM));

               int serverIdAfterCrash = Integer.parseInt(sessionFactory.
                  getConnectorConfiguration().getName().substring(4));
               assertNotEquals(serverIdBeforeCrash, serverIdAfterCrash);

               try (ClientConsumer clientConsumer = clientSession.createConsumer(QUEUE_NAME)) {
                  assertNotNull(clientConsumer.receive(3000));
               }

               QueueControl testQueueControlAfterCrash = getServer(serverIdAfterCrash).
                  getManagementService().getQueueControl(QUEUE_NAME);
               Wait.waitFor(() -> testQueueControlAfterCrash.getMessageCount() == 0, 3000);

               clientSession.stop();
            }
         }
      }
   }

   @Test
   public void testConsumerAfterFailoverWithRedistribution() throws Exception {
      setupCluster();

      AddressSettings testAddressSettings = new AddressSettings().setRedistributionDelay(0);
      for (int i : getServerIDs()) {
         getServer(i).getAddressSettingsRepository().addMatch(QUEUES_TESTADDRESS, testAddressSettings);
      }

      startServers(getPrimaryServerIDs());
      startServers(getBackupServerIDs());

      for (int i : getPrimaryServerIDs()) {
         waitForTopology(servers[i], 3, 3);
      }

      for (int i : getBackupServerIDs()) {
         waitForFailoverTopology(i, 0, 1, 2);
      }

      for (int i : getPrimaryServerIDs()) {
         setupSessionFactory(i, i + 3, isNetty(), false);
         createQueue(i, QUEUES_TESTADDRESS, QUEUE_NAME, null, true);
      }

      List<TransportConfiguration> transportConfigList = new ArrayList<>();
      for (int i : getPrimaryServerIDs()) {
         Map<String, Object> params = generateParams(i, isNetty());
         TransportConfiguration serverToTC = createTransportConfiguration("node" + i, isNetty(), false, params);
         serverToTC.getExtraParams().put(TEST_PARAM, TEST_PARAM);
         transportConfigList.add(serverToTC);
      }
      TransportConfiguration[] transportConfigs = transportConfigList.toArray(new TransportConfiguration[transportConfigList.size()]);

      try (ServerLocator serverLocator = new ServerLocatorImpl(false, transportConfigs)) {
         serverLocator.setFailoverAttempts(3);
         serverLocator.setReconnectAttempts(0);
         serverLocator.setUseTopologyForLoadBalancing(false);

         try (ClientSessionFactory sessionFactory = serverLocator.createSessionFactory()) {
            try (ClientSession clientSession = sessionFactory.createSession()) {
               clientSession.start();

               int serverIdBeforeCrash = Integer.parseInt(sessionFactory.
                  getConnectorConfiguration().getName().substring(4));

               QueueControl testQueueControlBeforeCrash = getServer(serverIdBeforeCrash).
                  getManagementService().getQueueControl(QUEUE_NAME);

               assertEquals(0, testQueueControlBeforeCrash.getMessageCount());

               try (ClientProducer clientProducer = clientSession.createProducer(QUEUES_TESTADDRESS)) {
                  clientProducer.send(clientSession.createMessage(true));
                  clientProducer.send(clientSession.createMessage(true));
               }

               assertEquals(2, testQueueControlBeforeCrash.getMessageCount());

               try (ClientConsumer clientConsumer = clientSession.createConsumer(QUEUE_NAME)) {
                  ClientMessage messageBeforeCrash = clientConsumer.receive(3000);
                  assertNotNull(messageBeforeCrash);
                  messageBeforeCrash.acknowledge();
                  clientSession.commit();

                  assertEquals(1, testQueueControlBeforeCrash.getMessageCount());

                  crashAndWaitForFailure(getServer(serverIdBeforeCrash), clientSession);

                  assertEquals(TEST_PARAM, sessionFactory.getConnectorConfiguration().getExtraParams().get(TEST_PARAM));

                  int serverIdAfterCrash = Integer.parseInt(sessionFactory.
                     getConnectorConfiguration().getName().substring(4));
                  assertNotEquals(serverIdBeforeCrash, serverIdAfterCrash);

                  assertTrue(isPrimaryServerID(serverIdAfterCrash));

                  QueueControl testQueueControlAfterCrash = getServer(serverIdAfterCrash).
                     getManagementService().getQueueControl(QUEUE_NAME);

                  Wait.waitFor(() -> testQueueControlAfterCrash.getMessageCount() == 1, 3000);

                  assertNotNull(clientConsumer.receive());
               }

               clientSession.stop();
            }
         }
      }
   }

   @Test
   public void testAutoCreatedQueueAfterFailoverWithoutHA() throws Exception {
      setupCluster();

      startServers(getPrimaryServerIDs());

      for (int i : getPrimaryServerIDs()) {
         waitForTopology(servers[i], 3, 0);
      }

      for (int i : getPrimaryServerIDs()) {
         setupSessionFactory(i, i + 3, isNetty(), false);
      }

      List<TransportConfiguration> transportConfigList = new ArrayList<>();
      for (int i : getPrimaryServerIDs()) {
         Map<String, Object> params = generateParams(i, isNetty());
         TransportConfiguration serverToTC = createTransportConfiguration("node" + i, isNetty(), false, params);
         serverToTC.getExtraParams().put(TEST_PARAM, TEST_PARAM);
         transportConfigList.add(serverToTC);
      }
      TransportConfiguration[] transportConfigs = transportConfigList.toArray(new TransportConfiguration[transportConfigList.size()]);

      try (ServerLocator serverLocator = new ServerLocatorImpl(false, transportConfigs)) {
         serverLocator.setFailoverAttempts(3);
         serverLocator.setReconnectAttempts(0);
         serverLocator.setUseTopologyForLoadBalancing(false);

         try (ClientSessionFactory sessionFactory = serverLocator.createSessionFactory()) {
            try (ClientSession clientSession = sessionFactory.createSession()) {
               clientSession.start();

               TransportConfiguration backupConnector = (TransportConfiguration) ((ClientSessionFactoryImpl) sessionFactory).getBackupConnector();
               assertNull(backupConnector);

               int serverIdBeforeCrash = Integer.parseInt(sessionFactory.getConnectorConfiguration().getName().substring(4));

               createQueue(serverIdBeforeCrash, QUEUES_TESTADDRESS, QUEUE_NAME, null, false);

               QueueControl testQueueControlBeforeCrash = getServer(serverIdBeforeCrash).getManagementService().getQueueControl(QUEUE_NAME);
               assertEquals(0, testQueueControlBeforeCrash.getMessageCount());

               for (int i : getPrimaryServerIDs()) {
                  if (i != serverIdBeforeCrash) {
                     assertNull(getServer(i).getManagementService().getQueueControl(QUEUE_NAME));
                  }
               }

               try (ClientConsumer clientConsumer = clientSession.createConsumer(QUEUE_NAME)) {
                  try (ClientProducer clientProducer = clientSession.createProducer(QUEUES_TESTADDRESS)) {
                     clientProducer.send(clientSession.createMessage(true));
                  }

                  Wait.waitFor(() -> testQueueControlBeforeCrash.getMessageCount() == 1, 3000);

                  assertNotNull(clientConsumer.receive(3000));

                  crashAndWaitForFailure(getServer(serverIdBeforeCrash), clientSession);

                  assertEquals(TEST_PARAM, sessionFactory.getConnectorConfiguration().getExtraParams().get(TEST_PARAM));

                  int serverIdAfterCrash = Integer.parseInt(sessionFactory.getConnectorConfiguration().getName().substring(4));
                  assertNotEquals(serverIdBeforeCrash, serverIdAfterCrash);

                  boolean serverIdAfterCrashFound = false;
                  for (int i : getPrimaryServerIDs()) {
                     if (i == serverIdAfterCrash) {
                        serverIdAfterCrashFound = true;
                     }
                  }
                  assertTrue(serverIdAfterCrashFound);

                  QueueControl testQueueControlAfterCrash = getServer(serverIdAfterCrash).getManagementService().getQueueControl(QUEUE_NAME);
                  assertNotNull(testQueueControlAfterCrash);
                  assertEquals(0, testQueueControlAfterCrash.getMessageCount());

                  try (ClientProducer clientProducer = clientSession.createProducer(QUEUES_TESTADDRESS)) {
                     clientProducer.send(clientSession.createMessage(true));

                     Wait.waitFor(() -> testQueueControlAfterCrash.getMessageCount() == 1, 3000);
                     assertEquals(1, testQueueControlAfterCrash.getMessageCount());

                     assertNotNull(clientConsumer.receive(3000));
                  }

                  clientSession.stop();
               }
            }
         }
      }
   }

   @Test
   public void testJMSConsumerAfterFailover() throws Exception {

      setupCluster();
      startServers(getPrimaryServerIDs());
      startServers(getBackupServerIDs());

      for (int i : getPrimaryServerIDs()) {
         waitForTopology(servers[i], 3, 3);
      }

      for (int i : getBackupServerIDs()) {
         waitForFailoverTopology(i, 0, 1, 2);
      }

      StringBuilder connectionURL = new StringBuilder();
      connectionURL.append("(");
      for (int i : getServerIDs()) {
         connectionURL.append("tcp://localhost:");
         connectionURL.append(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + i);
         connectionURL.append("?name=node");
         connectionURL.append(i);
         connectionURL.append("&");
         connectionURL.append(TEST_PARAM);
         connectionURL.append("=");
         connectionURL.append(TEST_PARAM);
         connectionURL.append(",");
      }
      connectionURL.replace(connectionURL.length() - 1, connectionURL.length(), ")");
      connectionURL.append("?ha=true&reconnectAttempts=-1");

      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(connectionURL.toString());

      try (Connection connection = connectionFactory.createConnection()) {
         connection.start();
         try (Session session = connection.createSession()) {
            ClientSessionFactory sessionFactory = ((ActiveMQConnection)connection).getSessionFactory();
            TransportConfiguration backupConnector = (TransportConfiguration)
               ((ClientSessionFactoryImpl)sessionFactory).getBackupConnector();
            assertNotEquals(backupConnector.getName(), sessionFactory.getConnectorConfiguration().getName());

            int serverIdBeforeCrash = Integer.parseInt(sessionFactory.
               getConnectorConfiguration().getName().substring(4));

            Queue testQueue = session.createQueue(QUEUE_NAME);

            try (MessageProducer producer = session.createProducer(testQueue)) {
               producer.send(session.createTextMessage(TEST_PARAM));
            }

            ClientSession clientSession = ((ActiveMQSession)session).getCoreSession();
            crashAndWaitForFailure(getServer(serverIdBeforeCrash), clientSession);
            assertEquals(backupConnector.getName(), sessionFactory.getConnectorConfiguration().getName());
            assertEquals(TEST_PARAM, sessionFactory.getConnectorConfiguration().getExtraParams().get(TEST_PARAM));

            int serverIdAfterCrash = Integer.parseInt(sessionFactory.
               getConnectorConfiguration().getName().substring(4));
            assertNotEquals(serverIdBeforeCrash, serverIdAfterCrash);

            try (MessageConsumer messageConsumer = session.createConsumer(testQueue)) {
               assertNotNull(messageConsumer.receive(3000));
            }

            QueueControl testQueueControlAfterCrash = getServer(serverIdAfterCrash).
               getManagementService().getQueueControl(QUEUE_NAME);
            Wait.waitFor(() -> testQueueControlAfterCrash.getMessageCount() == 0, 3000);
         }
         connection.stop();
      }
   }
}
