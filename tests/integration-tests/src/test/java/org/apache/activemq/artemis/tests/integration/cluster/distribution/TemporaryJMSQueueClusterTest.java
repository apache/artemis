/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

public class TemporaryJMSQueueClusterTest extends ClusterTestBase {

   @Test
   public void testDuplicateCacheCleanupForTempQueues() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());

      setupClusterConnection("cluster0", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      servers[0].getConfiguration().getClusterConfigurations().get(0).setDuplicateDetection(true);
      servers[0].getAddressSettingsRepository().addMatch("#", new AddressSettings().setRedistributionDelay(0));

      setupClusterConnection("cluster1", "", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);
      servers[1].getConfiguration().getClusterConfigurations().get(0).setDuplicateDetection(true);
      servers[1].getAddressSettingsRepository().addMatch("#", new AddressSettings().setRedistributionDelay(0));

      startServers(0, 1);

      final Map<String, TextMessage> requestMap = new HashMap<>();
      ConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616");

      for (int j = 0; j < 10; j++) {
         try (Connection connection = cf.createConnection()) {
            SimpleMessageListener server = new SimpleMessageListener().start();
            Queue requestQueue = ActiveMQJMSClient.createQueue("exampleQueue");
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(requestQueue);
            TemporaryQueue replyQueue = session.createTemporaryQueue();
            MessageConsumer replyConsumer = session.createConsumer(replyQueue);

            int numMessages = 10;
            for (int i = 0; i < numMessages; i++) {

               TextMessage requestMsg = session.createTextMessage("A request message");
               requestMsg.setJMSReplyTo(replyQueue);
               producer.send(requestMsg);
               requestMap.put(requestMsg.getJMSMessageID(), requestMsg);
            }

            for (int i = 0; i < numMessages; i++) {
               TextMessage replyMessageReceived = (TextMessage) replyConsumer.receive();
               assertNotNull(requestMap.get(replyMessageReceived.getJMSCorrelationID()));
            }

            replyConsumer.close();
            replyQueue.delete();
            server.shutdown();
         }

      }

      assertTrue(((PostOfficeImpl) servers[0].getPostOffice()).getDuplicateIDCaches().size() <= 1);
      assertTrue(((PostOfficeImpl) servers[1].getPostOffice()).getDuplicateIDCaches().size() <= 1);

   }

   public boolean isNetty() {
      return true;
   }
}

class SimpleMessageListener implements MessageListener {

   private Session session;
   MessageProducer replyProducer;
   MessageConsumer requestConsumer;
   Connection connection = null;

   public SimpleMessageListener start() throws Exception {
      Queue requestQueue = ActiveMQJMSClient.createQueue("exampleQueue");
      ConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61617");
      connection = cf.createConnection("guest", "guest");
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      connection.start();
      replyProducer = session.createProducer(null);
      requestConsumer = session.createConsumer(requestQueue);
      requestConsumer.setMessageListener(this);
      return this;
   }

   @Override
   public void onMessage(final javax.jms.Message request) {
      try {
         Destination replyDestination = request.getJMSReplyTo();
         TextMessage replyMessage = session.createTextMessage("A reply message");
         replyMessage.setJMSCorrelationID(request.getJMSMessageID());
         replyProducer.send(replyDestination, replyMessage);
      } catch (JMSException e) {
         e.printStackTrace();
      }
   }

   public void shutdown() throws JMSException {
      connection.close();
   }
}
