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
package org.apache.activemq.artemis.tests.integration.mqtt5;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTStateManager;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MQTTStateManagerTest extends MQTT5TestSupport {

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testNullClientID() throws Exception {
      testBadStateMessage(null);
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testEmptyClientID() throws Exception {
      testBadStateMessage("");
   }

   @Test
   @Timeout(DEFAULT_TIMEOUT_SEC)
   public void testEmptyStateMessage() throws Exception {
      testBadStateMessage(RandomUtil.randomUUIDString());
   }

   private void testBadStateMessage(String clientId) throws Exception {
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocator("vm://0"));
      ClientSessionFactory csf = locator.createSessionFactory();
      ClientSession s = addClientSession(csf.createSession());
      ClientProducer p = addClientProducer(s.createProducer(MQTTUtil.MQTT_SESSION_STORE));
      ClientMessage m = s.createMessage(true);
      if (clientId != null) {
         m.putStringProperty(Message.HDR_LAST_VALUE_NAME, clientId);
      }
      p.send(m);
      s.close();
      server.stop();
      server.start();
      assertTrue(server.waitForActivation(3, TimeUnit.SECONDS));
      assertNotNull(MQTTStateManager.getInstance(server));
   }

   @Test
   public void testWrongStateMessageType() throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");
      Connection c = factory.createConnection();
      Session s = c.createSession();
      MessageProducer p = s.createProducer(s.createQueue(MQTTUtil.MQTT_SESSION_STORE));
      javax.jms.Message m = s.createMessage();
      m.setStringProperty(Message.HDR_LAST_VALUE_NAME.toString(), RandomUtil.randomUUIDString());
      p.send(m);
      c.close();
      server.stop();
      server.start();
      assertTrue(server.waitForActivation(3, TimeUnit.SECONDS));
      assertNotNull(MQTTStateManager.getInstance(server));
   }
}
