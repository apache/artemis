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
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class JMSMismatchedRoutingTypeTest extends MultiprotocolJMSClientTestSupport {

   protected final String ANYCAST_ADDRESS = RandomUtil.randomUUIDString();
   protected final String MULTICAST_ADDRESS = RandomUtil.randomUUIDString();

   protected ConnectionSupplier OpenWireConnection = () -> {
      return createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), null, null, null, true, false);
   };

   @Override
   protected boolean isAutoCreateAddresses() {
      return false;
   }

   @Override
   protected boolean isAutoCreateQueues() {
      return false;
   }

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      server.addAddressInfo(new AddressInfo(SimpleString.of(ANYCAST_ADDRESS), RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(RandomUtil.randomUUIDString()).setAddress(ANYCAST_ADDRESS).setRoutingType(RoutingType.ANYCAST));

      server.addAddressInfo(new AddressInfo(SimpleString.of(MULTICAST_ADDRESS), RoutingType.MULTICAST));
      server.createQueue(QueueConfiguration.of(RandomUtil.randomUUIDString()).setAddress(MULTICAST_ADDRESS).setRoutingType(RoutingType.MULTICAST));
   }

   @Test
   public void testSendingMulticastToAnycastAMQP() throws Exception {
      internalTestSendingMulticastToAnycast(AMQPConnection);
   }

   @Test
   public void testSendingMulticastToAnycastCore() throws Exception {
      internalTestSendingMulticastToAnycast(CoreConnection);
   }

   @Test
   public void testSendingMulticastToAnycastOpenWire() throws Exception {
      internalTestSendingMulticastToAnycast(OpenWireConnection);
   }

   private void internalTestSendingMulticastToAnycast(ConnectionSupplier connectionSupplier) throws Exception {
      Connection connection = null;
      try {
         connection = connectionSupplier.createConnection();
         Session s = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Topic topic = s.createTopic(ANYCAST_ADDRESS);
         MessageProducer producer = s.createProducer(topic);
         producer.send(s.createMessage());
         fail("Sending message here should fail!");
      } catch (InvalidDestinationException e) {
         // expected
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   public void testSendingAnycastToMulticastAMQP() throws Exception {
      internalTestSendingAnycastToMulticast(AMQPConnection);
   }

   @Test
   public void testSendingAnycastToMulticastCore() throws Exception {
      internalTestSendingAnycastToMulticast(CoreConnection);
   }

   @Test
   public void testSendingAnycastToMulticastOpenWire() throws Exception {
      internalTestSendingAnycastToMulticast(OpenWireConnection);
   }

   private void internalTestSendingAnycastToMulticast(ConnectionSupplier connectionSupplier) throws Exception {
      Connection connection = null;
      try {
         connection = connectionSupplier.createConnection();
         Session s = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue q;
         try {
            // when using core this will fail because the client actually checks for the queue on the broker (which won't be there)
            q = s.createQueue(MULTICAST_ADDRESS);
         } catch (JMSException e) {
            if (connection instanceof ActiveMQConnection) {
               // expected
               return;
            } else {
               throw e;
            }
         }
         try {
            MessageProducer producer = s.createProducer(q);
            producer.send(s.createMessage());
            fail("Sending message here should fail!");
         } catch (InvalidDestinationException e) {
            // expected
         }
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }

   @Test
   public void testManagementQueueRequestorAMQP() throws Exception {
      internalTestManagementQueueRequestor(AMQPConnection);
   }

   @Test
   public void testManagementQueueRequestorCore() throws Exception {
      internalTestManagementQueueRequestor(CoreConnection);
   }

   @Test
   public void testManagementQueueRequestorOpenWire() throws Exception {
      internalTestManagementQueueRequestor(OpenWireConnection);
   }

   private void internalTestManagementQueueRequestor(ConnectionSupplier connectionSupplier) throws Exception {
      Connection connection = null;
      try {
         connection = connectionSupplier.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue managementQueue = session.createQueue(server.getConfiguration().getManagementAddress().toString());
         QueueRequestor requestor = new QueueRequestor(((QueueSession)session), managementQueue);
         connection.start();
         Message m = session.createMessage();
         Message response = requestor.request(m);
         assertNotNull(response);
      } finally {
         if (connection != null) {
            connection.close();
         }
      }
   }
}