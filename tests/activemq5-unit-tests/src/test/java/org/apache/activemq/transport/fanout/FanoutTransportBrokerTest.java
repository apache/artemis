/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.fanout;

import javax.jms.DeliveryMode;
import javax.jms.MessageNotWriteableException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.StubConnection;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.ConnectionId;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.MessageId;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.command.SessionInfo;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.TransportFactory;
import org.apache.activemq.transport.TransportFilter;
import org.apache.activemq.transport.mock.MockTransport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class FanoutTransportBrokerTest extends OpenwireArtemisBaseTest {

   public static final boolean FAST_NO_MESSAGE_LEFT_ASSERT = System.getProperty("FAST_NO_MESSAGE_LEFT_ASSERT", "true").equals("true");

   protected ArrayList<StubConnection> connections = new ArrayList<>();
   protected long idGenerator;
   protected int msgIdGenerator;
   protected int maxWait = 10000;

   private static final Logger LOG = LoggerFactory.getLogger(FanoutTransportBrokerTest.class);

   private EmbeddedJMS server;
   private EmbeddedJMS remoteServer;

   private ActiveMQDestination destination;
   private int deliveryMode;

   @Parameterized.Parameters(name = "test-{index}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{Integer.valueOf(DeliveryMode.NON_PERSISTENT), new ActiveMQQueue("TEST")}, {Integer.valueOf(DeliveryMode.NON_PERSISTENT), new ActiveMQTopic("TEST")}, {Integer.valueOf(DeliveryMode.PERSISTENT), new ActiveMQQueue("TEST")}, {Integer.valueOf(DeliveryMode.PERSISTENT), new ActiveMQTopic("TEST")}});
   }

   public FanoutTransportBrokerTest(int deliveryMode, ActiveMQDestination destination) {
      this.deliveryMode = deliveryMode;
      this.destination = destination;
   }

   @Before
   public void setUp() throws Exception {
      Configuration config0 = createConfig(0);
      server = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      Configuration config1 = createConfig(1);
      remoteServer = new EmbeddedJMS().setConfiguration(config1).setJmsConfiguration(new JMSConfigurationImpl());
      server.start();
      remoteServer.start();

   }

   @After
   public void tearDown() throws Exception {
      for (StubConnection conn : connections) {
         try {
            conn.stop();
         } catch (Exception e) {
         }
      }
      try {
         remoteServer.stop();
      } catch (Exception e) {
      }
      try {
         server.stop();
      } catch (Exception e) {
      }
   }

   @Test
   public void testPublisherFansout() throws Exception {
      // Start a normal consumer on the local broker
      StubConnection connection1 = createConnection();
      ConnectionInfo connectionInfo1 = createConnectionInfo();
      SessionInfo sessionInfo1 = createSessionInfo(connectionInfo1);
      ConsumerInfo consumerInfo1 = createConsumerInfo(sessionInfo1, destination);
      connection1.send(connectionInfo1);
      connection1.send(sessionInfo1);
      connection1.request(consumerInfo1);

      // Start a normal consumer on a remote broker
      StubConnection connection2 = createRemoteConnection();
      ConnectionInfo connectionInfo2 = createConnectionInfo();
      SessionInfo sessionInfo2 = createSessionInfo(connectionInfo2);
      ConsumerInfo consumerInfo2 = createConsumerInfo(sessionInfo2, destination);
      connection2.send(connectionInfo2);
      connection2.send(sessionInfo2);
      connection2.request(consumerInfo2);

      // Start a fanout publisher.
      LOG.info("Starting the fanout connection.");
      StubConnection connection3 = createFanoutConnection();
      ConnectionInfo connectionInfo3 = createConnectionInfo();
      SessionInfo sessionInfo3 = createSessionInfo(connectionInfo3);
      ProducerInfo producerInfo3 = createProducerInfo(sessionInfo3);
      connection3.send(connectionInfo3);
      connection3.send(sessionInfo3);
      connection3.send(producerInfo3);

      // Send the message using the fail over publisher.
      connection3.request(createMessage(producerInfo3, destination, deliveryMode));

      Assert.assertNotNull(receiveMessage(connection1));
      assertNoMessagesLeft(connection1);

      Assert.assertNotNull(receiveMessage(connection2));
      assertNoMessagesLeft(connection2);

   }

   /*
   public void initCombosForTestPublisherWaitsForServerToBeUp() {
      addCombinationValues("deliveryMode", new Object[]{Integer.valueOf(DeliveryMode.NON_PERSISTENT), Integer.valueOf(DeliveryMode.PERSISTENT)});
      addCombinationValues("destination", new Object[]{new ActiveMQTopic("TEST")});
   }
*/

   @Test
   public void testPublisherWaitsForServerToBeUp() throws Exception {

      if (name.getMethodName().contains("test-0") || name.getMethodName().contains("test-2")) {
         System.out.println("Discarding invalid test: " + name.getMethodName());
         return;
      }
      // Start a normal consumer on the local broker
      StubConnection connection1 = createConnection();
      ConnectionInfo connectionInfo1 = createConnectionInfo();
      SessionInfo sessionInfo1 = createSessionInfo(connectionInfo1);
      ConsumerInfo consumerInfo1 = createConsumerInfo(sessionInfo1, destination);
      connection1.send(connectionInfo1);
      connection1.send(sessionInfo1);
      connection1.request(consumerInfo1);

      // Start a normal consumer on a remote broker
      StubConnection connection2 = createRemoteConnection();
      ConnectionInfo connectionInfo2 = createConnectionInfo();
      SessionInfo sessionInfo2 = createSessionInfo(connectionInfo2);
      ConsumerInfo consumerInfo2 = createConsumerInfo(sessionInfo2, destination);
      connection2.send(connectionInfo2);
      connection2.send(sessionInfo2);
      connection2.request(consumerInfo2);

      // Start a fanout publisher.
      LOG.info("Starting the fanout connection.");
      final StubConnection connection3 = createFanoutConnection();
      ConnectionInfo connectionInfo3 = createConnectionInfo();
      SessionInfo sessionInfo3 = createSessionInfo(connectionInfo3);
      final ProducerInfo producerInfo3 = createProducerInfo(sessionInfo3);
      connection3.send(connectionInfo3);
      connection3.send(sessionInfo3);
      connection3.send(producerInfo3);

      // Send the message using the fail over publisher.
      connection3.request(createMessage(producerInfo3, destination, deliveryMode));

      Assert.assertNotNull(receiveMessage(connection1));
      assertNoMessagesLeft(connection1);

      Assert.assertNotNull(receiveMessage(connection2));
      assertNoMessagesLeft(connection2);

      final CountDownLatch publishDone = new CountDownLatch(1);

      // The MockTransport is on the remote connection.
      // Slip in a new transport filter after the MockTransport
      MockTransport mt = connection3.getTransport().narrow(MockTransport.class);
      mt.install(new TransportFilter(mt.getNext()) {
         @Override
         public void oneway(Object command) throws IOException {
            LOG.info("Dropping: " + command);
            // just eat it! to simulate a recent failure.
         }
      });

      // Send a message (async) as this will block
      new Thread(() -> {
         // Send the message using the fail over publisher.
         try {
            connection3.request(createMessage(producerInfo3, destination, deliveryMode));
         } catch (Throwable e) {
            e.printStackTrace();
         }
         publishDone.countDown();
      }).start();

      // Assert that we block:
      Assert.assertFalse(publishDone.await(3, TimeUnit.SECONDS));

      // Restart the remote server. State should be re-played and the publish
      // should continue.
      LOG.info("Restarting Broker");
      restartRemoteBroker();
      LOG.info("Broker Restarted");

      // This should reconnect, and resend
      Assert.assertTrue(publishDone.await(20, TimeUnit.SECONDS));

   }

   protected String getLocalURI() {
      return "tcp://localhost:61616";
   }

   protected String getRemoteURI() {
      return "tcp://localhost:61617";
   }

   protected StubConnection createFanoutConnection() throws Exception {
      URI fanoutURI = new URI("fanout://(static://(" + newURI(0) + "," + "mock://" + newURI(1) + "))?fanOutQueues=true");
      Transport transport = TransportFactory.connect(fanoutURI);
      StubConnection connection = new StubConnection(transport);
      connections.add(connection);
      return connection;
   }

   protected StubConnection createConnection() throws Exception {
      Transport transport = TransportFactory.connect(new URI(newURI(0)));
      StubConnection connection = new StubConnection(transport);
      connections.add(connection);
      return connection;
   }

   protected StubConnection createRemoteConnection() throws Exception {
      Transport transport = TransportFactory.connect(new URI(newURI(1)));
      StubConnection connection = new StubConnection(transport);
      connections.add(connection);
      return connection;
   }

   protected ConnectionInfo createConnectionInfo() throws Exception {
      ConnectionInfo info = new ConnectionInfo();
      info.setConnectionId(new ConnectionId("connection:" + (++idGenerator)));
      info.setClientId(info.getConnectionId().getValue());
      return info;
   }

   protected SessionInfo createSessionInfo(ConnectionInfo connectionInfo) throws Exception {
      SessionInfo info = new SessionInfo(connectionInfo, ++idGenerator);
      return info;
   }

   protected ConsumerInfo createConsumerInfo(SessionInfo sessionInfo,
                                             ActiveMQDestination destination) throws Exception {
      ConsumerInfo info = new ConsumerInfo(sessionInfo, ++idGenerator);
      info.setBrowser(false);
      info.setDestination(destination);
      info.setPrefetchSize(1000);
      info.setDispatchAsync(false);
      return info;
   }

   protected ProducerInfo createProducerInfo(SessionInfo sessionInfo) throws Exception {
      ProducerInfo info = new ProducerInfo(sessionInfo, ++idGenerator);
      return info;
   }

   protected Message createMessage(ProducerInfo producerInfo, ActiveMQDestination destination, int deliveryMode) {
      Message message = createMessage(producerInfo, destination);
      message.setPersistent(deliveryMode == DeliveryMode.PERSISTENT);
      return message;
   }

   protected Message createMessage(ProducerInfo producerInfo, ActiveMQDestination destination) {
      ActiveMQTextMessage message = new ActiveMQTextMessage();
      message.setMessageId(new MessageId(producerInfo, ++msgIdGenerator));
      message.setDestination(destination);
      message.setPersistent(false);
      try {
         message.setText("Test Message Payload.");
      } catch (MessageNotWriteableException e) {
      }
      return message;
   }

   public Message receiveMessage(StubConnection connection) throws InterruptedException {
      return receiveMessage(connection, maxWait);
   }

   public Message receiveMessage(StubConnection connection, long timeout) throws InterruptedException {
      while (true) {
         Object o = connection.getDispatchQueue().poll(timeout, TimeUnit.MILLISECONDS);

         if (o == null) {
            return null;
         }
         if (o instanceof MessageDispatch) {

            MessageDispatch dispatch = (MessageDispatch) o;
            if (dispatch.getMessage() == null) {
               return null;
            }
            dispatch.setMessage(dispatch.getMessage().copy());
            dispatch.getMessage().setRedeliveryCounter(dispatch.getRedeliveryCounter());
            return dispatch.getMessage();
         }
      }
   }

   protected void assertNoMessagesLeft(StubConnection connection) throws InterruptedException {
      long wait = FAST_NO_MESSAGE_LEFT_ASSERT ? 0 : maxWait;
      while (true) {
         Object o = connection.getDispatchQueue().poll(wait, TimeUnit.MILLISECONDS);
         if (o == null) {
            return;
         }
         if (o instanceof MessageDispatch && ((MessageDispatch) o).getMessage() != null) {
            Assert.fail("Received a message: " + ((MessageDispatch) o).getMessage().getMessageId());
         }
      }
   }

   protected void restartRemoteBroker() throws Exception {
      remoteServer.stop();
      Thread.sleep(2000);
      remoteServer.start();
   }

}
