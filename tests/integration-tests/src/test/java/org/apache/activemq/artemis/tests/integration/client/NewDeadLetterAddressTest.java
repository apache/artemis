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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A NewDeadLetterAddressTest
 */
public class NewDeadLetterAddressTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ClientSession clientSession;
   private ServerLocator locator;

   @Test
   public void testSendToDLAWhenNoRoute() throws Exception {
      SimpleString dla = SimpleString.of("DLA");
      SimpleString address = SimpleString.of("empty_address");
      AddressSettings addressSettings = new AddressSettings().setDeadLetterAddress(dla).setSendToDLAOnNoRoute(true);
      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);
      SimpleString dlq = SimpleString.of("DLQ1");
      clientSession.createQueue(QueueConfiguration.of(dlq).setAddress(dla).setDurable(false));
      ClientProducer producer = clientSession.createProducer(address);
      producer.send(createTextMessage(clientSession, "heyho!"));
      clientSession.start();
      ClientConsumer clientConsumer = clientSession.createConsumer(dlq);
      ClientMessage m = clientConsumer.receive(500);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("heyho!", m.getBodyBuffer().readString());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), false));
      server.start();
      locator = createInVMNonHALocator();
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      clientSession = sessionFactory.createSession(false, true, false);
   }
}
