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
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JMSReconnectTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   //In this test we re-attach to the same node without restarting the server
   @Test
   public void testReattachSameNode() throws Exception {
      testReconnectOrReattachSameNode(true);
   }

   //In this test, we reconnect to the same node without restarting the server
   @Test
   public void testReconnectSameNode() throws Exception {
      testReconnectOrReattachSameNode(false);
   }

   private void testReconnectOrReattachSameNode(boolean reattach) throws Exception {
      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      jbcf.setBlockOnDurableSend(true);
      jbcf.setBlockOnNonDurableSend(true);

      jbcf.setReconnectAttempts(-1);

      if (reattach) {
         jbcf.setConfirmationWindowSize(1024 * 1024);
      }

      // Note we set consumer window size to a value so we can verify that consumer credit re-sending
      // works properly on failover
      // The value is small enough that credits will have to be resent several time

      final int numMessages = 10;

      final int bodySize = 1000;

      jbcf.setConsumerWindowSize(numMessages * bodySize / 10);

      Connection conn = jbcf.createConnection();

      MyExceptionListener listener = new MyExceptionListener();

      conn.setExceptionListener(listener);

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSession = ((ActiveMQSession) sess).getCoreSession();

      RemotingConnection coreConn = ((ClientSessionInternal) coreSession).getConnection();

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

      conn.start();

      Thread.sleep(2000);

      ActiveMQException me = new ActiveMQNotConnectedException();

      coreConn.fail(me);

      //It should reconnect to the same node

      for (int i = 0; i < numMessages; i++) {
         BytesMessage bm = (BytesMessage) consumer.receive(1000);

         Assert.assertNotNull(bm);

         Assert.assertEquals(body.length, bm.getBodyLength());
      }

      TextMessage tm = (TextMessage) consumer.receiveNoWait();

      Assert.assertNull(tm);

      conn.close();

      Assert.assertNotNull(listener.e);

      Assert.assertTrue(me == listener.e.getCause());
   }

   @Test
   public void testReconnectSameNodeServerRestartedWithNonDurableSub() throws Exception {
      testReconnectSameNodeServerRestartedWithNonDurableSubOrTempQueue(true);
   }

   @Test
   public void testReconnectSameNodeServerRestartedWithTempQueue() throws Exception {
      testReconnectSameNodeServerRestartedWithNonDurableSubOrTempQueue(false);
   }

   //Test that non durable JMS sub gets recreated in auto reconnect
   private void testReconnectSameNodeServerRestartedWithNonDurableSubOrTempQueue(final boolean nonDurableSub) throws Exception {
      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      jbcf.setReconnectAttempts(-1);

      Connection conn = jbcf.createConnection();

      MyExceptionListener listener = new MyExceptionListener();

      conn.setExceptionListener(listener);

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSession = ((ActiveMQSession) sess).getCoreSession();

      Destination dest;

      if (nonDurableSub) {
         coreSession.createQueue(new QueueConfiguration("blahblah").setAddress("mytopic").setDurable(false));

         dest = ActiveMQJMSClient.createTopic("mytopic");
      } else {
         dest = sess.createTemporaryQueue();
      }

      MessageProducer producer = sess.createProducer(dest);

      //Create a non durable subscriber
      MessageConsumer consumer = sess.createConsumer(dest);

      this.server.stop();

      this.server.start();

      //Allow client some time to reconnect
      Thread.sleep(3000);

      final int numMessages = 100;

      byte[] body = RandomUtil.randomBytes(1000);

      for (int i = 0; i < numMessages; i++) {
         BytesMessage bm = sess.createBytesMessage();

         bm.writeBytes(body);

         producer.send(bm);
      }

      conn.start();

      for (int i = 0; i < numMessages; i++) {
         BytesMessage bm = (BytesMessage) consumer.receive(1000);

         Assert.assertNotNull(bm);

         Assert.assertEquals(body.length, bm.getBodyLength());
      }

      TextMessage tm = (TextMessage) consumer.receiveNoWait();

      Assert.assertNull(tm);

      conn.close();

      Assert.assertNotNull(listener.e);
   }

   //If the server is shutdown after a non durable sub is created, then close on the connection should proceed normally
   @Test
   public void testNoReconnectCloseAfterFailToReconnectWithTopicConsumer() throws Exception {
      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      jbcf.setReconnectAttempts(0);

      Connection conn = jbcf.createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSession = ((ActiveMQSession) sess).getCoreSession();

      coreSession.createQueue(new QueueConfiguration("blahblah").setAddress("mytopic").setDurable(false));

      Topic topic = ActiveMQJMSClient.createTopic("mytopic");

      //Create a non durable subscriber
      sess.createConsumer(topic);

      Thread.sleep(2000);

      this.server.stop();

      this.server.start();

      sess.close();

      conn.close();
   }

   //If server is shutdown, and then connection is closed, after a temp queue has been created, the close should complete normally
   @Test
   public void testNoReconnectCloseAfterFailToReconnectWithTempQueue() throws Exception {
      ActiveMQConnectionFactory jbcf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      jbcf.setReconnectAttempts(0);

      Connection conn = jbcf.createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      sess.createTemporaryQueue();

      Thread.sleep(2000);

      this.server.stop();

      this.server.start();

      sess.close();

      conn.close();
   }



   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), true));
      server.start();
   }



   private static class MyExceptionListener implements ExceptionListener {

      volatile JMSException e;

      @Override
      public void onException(final JMSException e) {
         this.e = e;
      }
   }

}
