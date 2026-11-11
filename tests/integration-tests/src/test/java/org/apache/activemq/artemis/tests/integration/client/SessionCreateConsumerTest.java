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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInvalidFilterExpressionException;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SessionCreateConsumerTest extends ActiveMQTestBase {

   private final String queueName = "ClientSessionCreateConsumerTestQ";

   private ServerLocator locator;
   private ActiveMQServer service;
   private ClientSessionInternal clientSession;
   private ClientSessionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      locator = createInVMNonHALocator();
      super.setUp();

      service = createServer(false);
      service.start();
      locator.setProducerMaxRate(99).setBlockOnNonDurableSend(true).setBlockOnNonDurableSend(true);
      cf = createSessionFactory(locator);
      clientSession = (ClientSessionInternal) addClientSession(cf.createSession(false, true, true));
   }

   @Test
   public void testCreateConsumer() throws Exception {
      clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      ClientConsumer consumer = clientSession.createConsumer(queueName);
      assertNotNull(consumer);
   }

   @Test
   public void testCreateConsumerNoQ() throws Exception {
      try {
         clientSession.createConsumer(queueName);
         fail("should throw exception");
      } catch (ActiveMQNonExistentQueueException neqe) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @Test
   public void testCreateConsumerWithFilter() throws Exception {
      clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      ClientConsumer consumer = clientSession.createConsumer(queueName, "foo=bar");
      assertNotNull(consumer);
   }

   @Test
   public void testCreateConsumerWithInvalidFilter() throws Exception {
      clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      try {
         clientSession.createConsumer(queueName, "this is not valid filter");
         fail("should throw exception");
      } catch (ActiveMQInvalidFilterExpressionException ifee) {
         //ok
      } catch (ActiveMQException e) {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @Test
   public void testCreateConsumerWithBrowseOnly() throws Exception {
      clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      ClientConsumer consumer = clientSession.createConsumer(queueName, null, true);
      assertNotNull(consumer);
   }

   @Test
   public void testCreateConsumerWithOverrides() throws Exception {
      clientSession.createQueue(QueueConfiguration.of(queueName).setDurable(false));
      ClientConsumer consumer = clientSession.createConsumer(queueName, null, 100, 100, false);
      assertNotNull(consumer);
   }

}
