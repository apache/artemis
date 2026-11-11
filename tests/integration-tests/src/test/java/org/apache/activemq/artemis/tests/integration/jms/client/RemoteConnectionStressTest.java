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
package org.apache.activemq.artemis.tests.integration.jms.client;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RemoteConnectionStressTest extends ActiveMQTestBase {

   ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultNettyConfig(), false));
      server.start();
   }

   @Test
   public void testSimpleRemoteConnections() throws Exception {
      for (int i = 0; i < 1000; i++) {

         TransportConfiguration config = new TransportConfiguration(NETTY_CONNECTOR_FACTORY);
         ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);
         cf.setInitialConnectAttempts(10);
         cf.setRetryInterval(100);

         Connection conn = cf.createConnection();

         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Queue queue = session.createQueue("SomeQueue");

         MessageProducer producer = session.createProducer(queue);

         TextMessage msg = session.createTextMessage();
         msg.setText("Message " + i);

         producer.send(msg);

         producer.close();
         session.close();
         conn.close();

         cf.close();

      }
   }

}
