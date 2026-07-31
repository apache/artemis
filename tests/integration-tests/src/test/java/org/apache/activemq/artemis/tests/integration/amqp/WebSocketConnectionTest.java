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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;

/**
 * Test connections can be established to remote peers via WebSockets
 */
public class WebSocketConnectionTest extends JMSClientTestSupport {

   @Override
   public boolean isUseWebSockets() {
      return true;
   }

   @Test
   public void testSingleKeepAliveIsReleasedWhenWebSocketUpgradeHappens() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());

      produceAndConsumeInNewConnection(factory);

      assertKeepAliveCounterIsZero();
   }

   @Test
   public void testMultipleKeepAliveAreReleasedWhenWebSocketUpgradeHappens() throws Exception {
      JmsConnectionFactory factory = new JmsConnectionFactory(getBrokerQpidJMSConnectionURI());

      produceAndConsumeInNewConnection(factory);
      produceAndConsumeInNewConnection(factory);
      produceAndConsumeInNewConnection(factory);
      produceAndConsumeInNewConnection(factory);
      produceAndConsumeInNewConnection(factory);

      assertKeepAliveCounterIsZero();
   }

   private void produceAndConsumeInNewConnection(JmsConnectionFactory factory) throws JMSException {
      JmsConnection connection = (JmsConnection) factory.createConnection();

      try {
         Session session = connection.createSession();
         Queue queue = session.createQueue(getQueueName());

         MessageProducer producer = session.createProducer(queue);
         producer.send(session.createMessage());
         producer.close();

         connection.start();

         MessageConsumer consumer = session.createConsumer(queue);
         Message message = consumer.receive(1000);

         assertNotNull(message);
      } finally {
         connection.close();
      }
   }

   private void assertKeepAliveCounterIsZero() {
      NettyAcceptor nettyAcceptor = (NettyAcceptor) server.getRemotingService().getAcceptor(NETTY_ACCEPTOR);

      int httpAcceptorHandlerCount = nettyAcceptor.getProtocolHandler().getHttpKeepAliveRunnable().getHandlers().size();

      assertEquals(0, httpAcceptorHandlerCount);
   }
}
