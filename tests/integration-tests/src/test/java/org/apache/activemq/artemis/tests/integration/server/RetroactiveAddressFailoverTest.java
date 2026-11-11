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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.cluster.failover.FailoverTestBase;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RetroactiveAddressFailoverTest extends FailoverTestBase {

   protected ServerLocator locator;

   protected ClientSessionFactoryInternal sf;

   String internalNamingPrefix = ActiveMQDefaultConfiguration.DEFAULT_INTERNAL_NAMING_PREFIX;

   String delimiter = ".";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = getServerLocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(300).setRetryInterval(100);
      sf = createSessionFactoryAndWaitForTopology(locator, 2);
   }

   @Test
   public void testFailover() throws Exception {
      final int MESSAGE_COUNT = 10;
      final int OFFSET = 5;
      ActiveMQServer primary = primaryServer.getServer();
      ActiveMQServer backup = backupServer.getServer();
      ClientSession session = addClientSession(sf.createSession(true, true));
      final SimpleString queueName = SimpleString.of("simpleQueue");
      final SimpleString addressName = SimpleString.of("myAddress");
      final SimpleString divertQueue = ResourceNames.getRetroactiveResourceQueueName(internalNamingPrefix, delimiter, addressName, RoutingType.MULTICAST);
      primary.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(MESSAGE_COUNT));
      backup.getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setRetroactiveMessageCount(MESSAGE_COUNT));
      primary.addAddressInfo(new AddressInfo(addressName));

      ClientProducer producer = addClientProducer(session.createProducer(addressName));
      for (int j = 0; j < OFFSET; j++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("xxx", j);
         producer.send(message);
      }

      org.apache.activemq.artemis.tests.util.Wait.assertTrue(() -> primary.locateQueue(divertQueue).getMessageCount() == OFFSET);

      crash(session);

      org.apache.activemq.artemis.tests.util.Wait.assertTrue(() -> backup.locateQueue(divertQueue).getMessageCount() == OFFSET);

      for (int j = OFFSET; j < MESSAGE_COUNT + OFFSET; j++) {
         ClientMessage message = session.createMessage(true);
         message.putIntProperty("xxx", j);
         producer.send(message);
      }

      org.apache.activemq.artemis.tests.util.Wait.assertTrue(() -> backup.locateQueue(divertQueue).getMessageCount() == MESSAGE_COUNT);

      session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
      org.apache.activemq.artemis.tests.util.Wait.assertTrue(() -> backup.locateQueue(queueName) != null);
      org.apache.activemq.artemis.tests.util.Wait.assertTrue(() -> backup.locateQueue(queueName).getMessageCount() == MESSAGE_COUNT);

      ClientConsumer consumer = session.createConsumer(queueName);
      for (int j = OFFSET; j < MESSAGE_COUNT + OFFSET; j++) {
         session.start();
         ClientMessage message = consumer.receive(1000);
         assertNotNull(message);
         message.acknowledge();
         assertEquals(j, (int) message.getIntProperty("xxx"));
      }
      consumer.close();
      session.deleteQueue(queueName);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMAcceptor(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMConnector(live);
   }
}
