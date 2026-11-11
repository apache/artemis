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

package org.apache.activemq.artemis.tests.integration.amqp.largemessages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.SharedNothingBackupActivation;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpReplicatedLargeMessageTest extends AmqpReplicatedTestSupport {

   private String smallFrameLive = new String("tcp://localhost:" + (AMQP_PORT + 10));
   private String smallFrameBackup = new String("tcp://localhost:" + (AMQP_PORT + 10));

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMAcceptor(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMConnector(live);
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      createReplicatedConfigs();
      primaryConfig.setResolveProtocols(true).addAcceptorConfiguration("amqp", smallFrameLive + "?protocols=AMQP;useEpoll=false;maxFrameSize=512");
      backupConfig.setResolveProtocols(true).addAcceptorConfiguration("amqp", smallFrameBackup + "?protocols=AMQP;useEpoll=false;maxFrameSize=512");
      primaryServer.start();
      backupServer.start();

      primaryServer.getServer().addAddressInfo(new AddressInfo(getQueueName(), RoutingType.ANYCAST));
      primaryServer.getServer().createQueue(QueueConfiguration.of(getQueueName()).setRoutingType(RoutingType.ANYCAST));


      waitForRemoteBackupSynchronization(backupServer.getServer());
   }

   public SimpleString getQueueName() {
      return SimpleString.of("replicatedTest");
   }


   @Test
   @Timeout(60)
   public void testSimpleSend() throws Exception {
      try {

         ActiveMQServer server = primaryServer.getServer();

         int size = 100 * 1024;
         AmqpClient client = createAmqpClient(new URI(smallFrameLive));
         AmqpConnection connection = client.createConnection();
         addConnection(connection);
         connection.setMaxFrameSize(2 * 1024);
         connection.connect();

         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createSender(getQueueName().toString());

         Queue queueView = server.locateQueue(getQueueName());
         assertNotNull(queueView);
         assertEquals(0, queueView.getMessageCount());

         session.begin();
         for (int m = 0; m < 100; m++) {
            AmqpMessage message = new AmqpMessage();
            message.setDurable(true);
            message.setApplicationProperty("i", "m " + m);
            byte[] bytes = new byte[size];
            for (int i = 0; i < bytes.length; i++) {
               bytes[i] = (byte) 'z';
            }

            message.setBytes(bytes);
            sender.send(message);
         }
         session.commit();

         AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server);

         connection.close();
         primaryServer.crash();

         Wait.assertTrue(backupServer::isActive);

         server = backupServer.getServer();

         client = createAmqpClient(new URI(smallFrameBackup));
         connection = client.createConnection();
         addConnection(connection);
         connection.setMaxFrameSize(2 * 1024);
         connection.connect();
         session = connection.createSession();

         queueView = server.locateQueue(getQueueName());
         Wait.assertEquals(100, queueView::getMessageCount);

         AmqpReceiver receiver = session.createReceiver(getQueueName().toString());
         receiver.flow(100);
         for (int i = 0; i < 100; i++) {
            AmqpMessage msgReceived = receiver.receive(10, TimeUnit.SECONDS);
            assertNotNull(msgReceived);
            Data body = (Data)msgReceived.getWrappedMessage().getBody();
            byte[] bodyArray = body.getValue().getArray();
            for (int bI = 0; bI < size; bI++) {
               assertEquals((byte)'z', bodyArray[bI]);
            }
            msgReceived.accept(true);
         }

         receiver.flow(1);
         assertNull(receiver.receiveNoWait());


         receiver.close();

         connection.close();

         Wait.assertEquals(0, queueView::getMessageCount);
         validateNoFilesOnLargeDir(getLargeMessagesDir(0, true), 0);
      } catch (Exception e) {
         e.printStackTrace();
         throw e;
      }
   }

   @Test
   @Timeout(60)
   public void testCloseFilesOnTarget() throws Exception {
      try {

         ActiveMQServer server = primaryServer.getServer();

         int size = 100 * 1024;
         AmqpClient client = createAmqpClient(new URI(smallFrameLive));
         AmqpConnection connection = client.createConnection();
         addConnection(connection);
         connection.setMaxFrameSize(2 * 1024);
         connection.connect();

         AmqpSession session = connection.createSession();

         AmqpSender sender = session.createSender(getQueueName().toString());

         Queue queueView = server.locateQueue(getQueueName());
         assertNotNull(queueView);
         assertEquals(0, queueView.getMessageCount());

         session.begin();
         for (int m = 0; m < 100; m++) {
            AmqpMessage message = new AmqpMessage();
            message.setDurable(true);
            message.setApplicationProperty("i", "m " + m);
            byte[] bytes = new byte[size];
            for (int i = 0; i < bytes.length; i++) {
               bytes[i] = (byte) 'z';
            }

            message.setBytes(bytes);
            sender.send(message);
         }
         session.commit();

         AMQPLargeMessagesTestUtil.validateAllTemporaryBuffers(server);

         queueView = server.locateQueue(getQueueName());
         Wait.assertEquals(100, queueView::getMessageCount);

         SharedNothingBackupActivation activation = (SharedNothingBackupActivation) backupServer.getServer().getActivation();
         Wait.assertEquals(0, () -> activation.getReplicationEndpoint().getLargeMessages().size(), 5000);

         AmqpReceiver receiver = session.createReceiver(getQueueName().toString());
         receiver.flow(100);
         for (int i = 0; i < 100; i++) {
            AmqpMessage msgReceived = receiver.receive(10, TimeUnit.SECONDS);
            assertNotNull(msgReceived);
            Data body = (Data)msgReceived.getWrappedMessage().getBody();
            byte[] bodyArray = body.getValue().getArray();
            for (int bI = 0; bI < size; bI++) {
               assertEquals((byte)'z', bodyArray[bI]);
            }
            msgReceived.accept(true);
         }

         receiver.flow(1);
         assertNull(receiver.receiveNoWait());

         receiver.close();

         connection.close();

         Wait.assertEquals(0, queueView::getMessageCount);
         validateNoFilesOnLargeDir(getLargeMessagesDir(0, false), 0);
         validateNoFilesOnLargeDir(getLargeMessagesDir(0, true), 0);
      } catch (Exception e) {
         e.printStackTrace();
         throw e;
      }
   }

}
