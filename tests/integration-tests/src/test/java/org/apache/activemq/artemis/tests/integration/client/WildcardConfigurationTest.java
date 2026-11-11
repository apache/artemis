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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WildcardConfigurationTest extends ActiveMQTestBase {

   private ActiveMQServer server;
   private ServerLocator locator;
   private ClientSession clientSession;
   private ClientSessionFactory sf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
      wildcardConfiguration.setDelimiter('/');
      Configuration configuration = createDefaultInVMConfig().setWildcardRoutingEnabled(true).setTransactionTimeoutScanPeriod(500).setWildCardConfiguration(wildcardConfiguration);
      server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));
      server.start();
      server.getManagementService().enableNotifications(false);
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      clientSession = addClientSession(sf.createSession(false, true, true));
   }

   @Test
   public void testBasicWildcardRouting() throws Exception {
      SimpleString addressAB = SimpleString.of("a/b");
      SimpleString addressAC = SimpleString.of("a/c");
      SimpleString address = SimpleString.of("a/*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

}
