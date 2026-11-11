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
package org.apache.activemq.artemis.tests.integration.replication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.tests.integration.cluster.failover.FailoverTestBase;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class ReplicationOrderTest extends FailoverTestBase {

   public static final int NUM = 300;

   @Test
   public void testMixedPersistentAndNonPersistentMessagesOrderWithReplicatedBackup() throws Exception {
      doTestMixedPersistentAndNonPersistentMessagesOrderWithReplicatedBackup(false);
   }

   @Test
   public void testTxMixedPersistentAndNonPersistentMessagesOrderWithReplicatedBackup() throws Exception {
      doTestMixedPersistentAndNonPersistentMessagesOrderWithReplicatedBackup(true);
   }

   private void doTestMixedPersistentAndNonPersistentMessagesOrderWithReplicatedBackup(final boolean transactional) throws Exception {
      String address = RandomUtil.randomUUIDString();
      String queue = RandomUtil.randomUUIDString();
      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(getConnectorTransportConfiguration(true));
      addServerLocator(locator);
      locator.setBlockOnNonDurableSend(false).setBlockOnDurableSend(false);
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = null;
      if (transactional) {
         session = csf.createSession(false, false);
      } else {
         session = csf.createSession(true, true);
      }
      addClientSession(session);
      session.createQueue(QueueConfiguration.of(queue).setAddress(address));
      ClientProducer producer = session.createProducer(address);
      boolean durable = false;
      for (int i = 0; i < ReplicationOrderTest.NUM; i++) {
         ClientMessage msg = session.createMessage(durable);
         msg.putIntProperty("counter", i);
         producer.send(msg);
         if (transactional) {
            if (i % 10 == 0) {
               session.commit();
               durable = !durable;
            }
         } else {
            durable = !durable;
         }
      }
      if (transactional) {
         session.commit();
      }
      session.close();

      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(getConnectorTransportConfiguration(true)));
      csf = createSessionFactory(locator);
      session = csf.createSession(true, true);
      session.start();
      ClientConsumer consumer = session.createConsumer(queue);
      for (int i = 0; i < ReplicationOrderTest.NUM; i++) {
         ClientMessage message = consumer.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getIntProperty("counter").intValue());
      }

      consumer.close();
      session.deleteQueue(queue);

      session.close();
   }

   @Override
   protected void createConfigs() throws Exception {
      createReplicatedConfigs();
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMConnector(live);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMAcceptor(live);
   }
}
