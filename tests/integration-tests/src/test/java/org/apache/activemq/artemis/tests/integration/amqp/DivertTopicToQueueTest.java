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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;

public class DivertTopicToQueueTest extends JMSClientTestSupport {

   @Test
   public void divertTopicToQueueWithSelectorTest() throws Exception {
      final String address1 = "bss.order.workorderchanges.v1.topic";
      final String address2 = "bss.order.Consumer.cbma.workorderchanges.v1.queue";
      final String address3 = "bss.order.Consumer.pinpoint.workorderchanges.v1.queue";

      DivertConfiguration dc1 = new DivertConfiguration().setName("WorkOrderChangesCBMA-Divert").setRoutingName("WorkOrderChangesCBMA-Divert").setAddress(address1).setForwardingAddress(address2).setExclusive(false).setRoutingType(ComponentConfigurationRoutingType.ANYCAST);
      DivertConfiguration dc2 = new DivertConfiguration().setName("WorkOrderChangesPinpoint-Divert").setRoutingName("WorkOrderChangesPinpoint-Divert").setAddress(address1).setForwardingAddress(address3).setExclusive(false).setRoutingType(ComponentConfigurationRoutingType.ANYCAST);

      server.deployDivert(dc1);
      server.deployDivert(dc2);

      JmsConnectionFactory factory = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());

      Connection connection = factory.createConnection(null, null);
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      javax.jms.Topic topicSource = session.createTopic(address1);
      javax.jms.Queue queueTarget = session.createQueue(address2);
      javax.jms.Queue queueTarget2 = session.createQueue(address3);

      final MessageProducer producer = session.createProducer(topicSource);
      final TextMessage message = session.createTextMessage("Hello");
      message.setStringProperty("filename", "BILHANDLE");

      connection.start();

      String selector = "filename='BILHANDLE'";

      final MessageConsumer consumer = session.createConsumer(queueTarget, selector);
      final MessageConsumer consumer2 = session.createConsumer(queueTarget2, selector);
      producer.send(message);

      TextMessage receivedMessage = (TextMessage) consumer.receive(1000);
      TextMessage receivedMessage2 = (TextMessage) consumer2.receive(1000);

      assertNotNull(receivedMessage);
      assertNotNull(receivedMessage2);

      connection.close();
   }

   @Override
   protected void createAddressAndQueues(ActiveMQServer server) throws Exception {
      // do not create unnecessary queues
   }
}
