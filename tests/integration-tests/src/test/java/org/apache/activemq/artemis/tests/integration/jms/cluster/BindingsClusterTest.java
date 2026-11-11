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

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.JMSClusteredTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class BindingsClusterTest extends JMSClusteredTestBase {

   // TODO: find a solution to this
   // the "jms." prefix is needed because the cluster connection is matching on this
   public static final String TOPIC = "jms.t1";

   private final boolean crash;

   public BindingsClusterTest(boolean crash) {
      this.crash = crash;
   }

   @Parameters(name = "crash={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      //todo fix if needed
      super.setUp();
      jmsServer1.getActiveMQServer().setIdentity("Server 1");
      jmsServer2.getActiveMQServer().setIdentity("Server 2");
   }

   @Override
   protected boolean enablePersistence() {
      return true;
   }

   @TestTemplate
   public void testSendToSingleDisconnectedBinding() throws Exception {
      Connection conn1 = cf1.createConnection();

      conn1.setClientID("someClient1");

      Connection conn2 = cf2.createConnection();

      conn2.setClientID("someClient2");

      conn1.start();

      conn2.start();

      try {

         Topic topic1 = createTopic(TOPIC, true);

         Topic topic2 = (Topic) context1.lookup("topic/" + TOPIC);

         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons2 = session2.createDurableSubscriber(topic2, "sub2");

         cons2.close();

         session2.close();

         conn2.close();

         Thread.sleep(500);

         MessageProducer prod1 = session1.createProducer(topic1);

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         prod1.send(session1.createTextMessage("m1"));

         printBindings(jmsServer1.getActiveMQServer(), TOPIC);
         printBindings(jmsServer2.getActiveMQServer(), TOPIC);

         crash();

         printBindings(jmsServer1.getActiveMQServer(), TOPIC);

         prod1.send(session1.createTextMessage("m2"));

         restart();

         Thread.sleep(2000);

         printBindings(jmsServer1.getActiveMQServer(), TOPIC);
         printBindings(jmsServer2.getActiveMQServer(), TOPIC);

         prod1.send(session1.createTextMessage("m3"));

         cf2 = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(2)));

         conn2 = cf2.createConnection();

         conn2.setClientID("someClient2");

         session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         cons2 = session2.createDurableSubscriber(topic2, "sub2");

         conn2.start();

         TextMessage received = (TextMessage) cons2.receive(5000);

         assertNotNull(received);

         assertEquals("m1", received.getText());

         received = (TextMessage) cons2.receive(5000);

         assertNotNull(received);

         assertEquals("m2", received.getText());

         received = (TextMessage) cons2.receive(5000);

         assertNotNull(received);

         assertEquals("m3", received.getText());

         cons2.close();
      } finally {
         conn1.close();
         conn2.close();
      }

      jmsServer1.destroyTopic(TOPIC);
      jmsServer2.destroyTopic(TOPIC);
   }

   @TestTemplate
   public void testSendToSingleDisconnectedBindingWhenLocalAvailable() throws Exception {
      Connection conn1 = cf1.createConnection();

      conn1.setClientID("someClient2");

      Connection conn2 = cf2.createConnection();

      conn2.setClientID("someClient2");

      conn1.start();

      conn2.start();

      try {

         Topic topic1 = createTopic(TOPIC, true);

         Topic topic2 = (Topic) context1.lookup("topic/" + TOPIC);

         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons1 = session1.createDurableSubscriber(topic1, "sub2");

         MessageConsumer cons2 = session2.createDurableSubscriber(topic2, "sub2");

         cons2.close();

         session2.close();

         conn2.close();

         Thread.sleep(500);

         MessageProducer prod1 = session1.createProducer(topic1);

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         prod1.send(session1.createTextMessage("m1"));

         printBindings(jmsServer1.getActiveMQServer(), TOPIC);
         printBindings(jmsServer2.getActiveMQServer(), TOPIC);

         crash();

         printBindings(jmsServer1.getActiveMQServer(), TOPIC);

         //send a few messages while the binding is disconnected
         prod1.send(session1.createTextMessage("m2"));
         prod1.send(session1.createTextMessage("m3"));
         prod1.send(session1.createTextMessage("m4"));

         restart();

         Thread.sleep(2000);

         printBindings(jmsServer1.getActiveMQServer(), TOPIC);
         printBindings(jmsServer2.getActiveMQServer(), TOPIC);

         prod1.send(session1.createTextMessage("m5"));
         prod1.send(session1.createTextMessage("m6"));

         cf2 = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName(), generateInVMParams(2)));

         conn2 = cf2.createConnection();

         conn2.setClientID("someClient2");

         session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         cons2 = session2.createDurableSubscriber(topic2, "sub2");

         conn2.start();

         TextMessage received = (TextMessage) cons2.receiveNoWait();

         assertNull(received);

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m1", received.getText());

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m2", received.getText());

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m3", received.getText());

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m4", received.getText());

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m5", received.getText());

         cons2.close();
      } finally {
         conn1.close();
         conn2.close();
      }

      jmsServer1.destroyTopic(TOPIC);
      jmsServer2.destroyTopic(TOPIC);
   }

   @TestTemplate
   public void testRemoteBindingRemovedOnReconnectLocalAvailable() throws Exception {
      Connection conn1 = cf1.createConnection();

      conn1.setClientID("someClient2");

      Connection conn2 = cf2.createConnection();

      conn2.setClientID("someClient2");

      conn1.start();

      conn2.start();

      try {

         Topic topic1 = createTopic(TOPIC, true);

         Topic topic2 = (Topic) context1.lookup("topic/" + TOPIC);

         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons1 = session1.createSharedConsumer(topic1, "sub2");

         MessageConsumer cons2 = session2.createSharedConsumer(topic2, "sub2");

         Thread.sleep(500);

         MessageProducer prod1 = session1.createProducer(topic1);

         prod1.setDeliveryMode(DeliveryMode.PERSISTENT);

         prod1.send(session1.createTextMessage("m1"));
         prod1.send(session1.createTextMessage("m2"));

         printBindings(jmsServer1.getActiveMQServer(), TOPIC);
         printBindings(jmsServer2.getActiveMQServer(), TOPIC);

         // verify receipt by remote binding before crash
         TextMessage received = (TextMessage) cons2.receive(5000);
         assertNotNull(received);
         assertEquals("m2", received.getText());

         crash();

         //this may or may not be closed, if the server was crashed then it would have been closed on failure.
         cons2.close();

         printBindings(jmsServer1.getActiveMQServer(), TOPIC);

         //send a few messages while the binding is disconnected
         prod1.send(session1.createTextMessage("m3"));
         prod1.send(session1.createTextMessage("m4"));
         prod1.send(session1.createTextMessage("m5"));

         restart();

         Thread.sleep(2000);

         printBindings(jmsServer1.getActiveMQServer(), TOPIC);
         printBindings(jmsServer2.getActiveMQServer(), TOPIC);

         prod1.send(session1.createTextMessage("m6"));
         prod1.send(session1.createTextMessage("m7"));

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m1", received.getText());

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m3", received.getText());

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m4", received.getText());

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m5", received.getText());

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m6", received.getText());

         received = (TextMessage) cons1.receive(5000);

         assertNotNull(received);

         assertEquals("m7", received.getText());

         cons2.close();
      } finally {
         conn1.close();
         conn2.close();
      }

      jmsServer1.destroyTopic(TOPIC);
      jmsServer2.destroyTopic(TOPIC);
   }

   private void crash() throws Exception {
      /*
       * Rather than just calling stop() on the server here we want to simulate an actual node crash or bridge failure
       * so the bridge's failure listener needs to get something other than a DISCONNECTED message.  In this case we
       * simulate a NOT_CONNECTED exception.
       */
      final CountDownLatch latch = new CountDownLatch(1);
      ClusterConnectionImpl next = (ClusterConnectionImpl) server1.getClusterManager().getClusterConnections().iterator().next();
      BridgeImpl bridge = (BridgeImpl) next.getRecords().values().iterator().next().getBridge();
      RemotingConnection forwardingConnection = getForwardingConnection(bridge);
      forwardingConnection.addFailureListener(new FailureListener() {
         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {
            latch.countDown();
         }

         @Override
         public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
            connectionFailed(me, failedOver);
         }
      });
      forwardingConnection.fail(new ActiveMQNotConnectedException());
      assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));

      if (crash) {
         jmsServer2.stop();
      }
   }

   private void restart() throws Exception {
      if (crash) {
         jmsServer2.start();
      }
   }

   private RemotingConnection getForwardingConnection(final Bridge bridge) throws Exception {
      long start = System.currentTimeMillis();

      do {
         RemotingConnection forwardingConnection = ((BridgeImpl) bridge).getForwardingConnection();

         if (forwardingConnection != null) {
            return forwardingConnection;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < 50000);

      throw new IllegalStateException("Failed to get forwarding connection");
   }
}
