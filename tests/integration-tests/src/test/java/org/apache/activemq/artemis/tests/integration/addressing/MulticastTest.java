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
package org.apache.activemq.artemis.tests.integration.addressing;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.TimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

public class MulticastTest extends ActiveMQTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private SimpleString baseAddress = SimpleString.of("multicast.address");

   private AddressInfo addressInfo;

   private ActiveMQServer server;

   private ClientSessionFactory sessionFactory;

   @BeforeEach
   public void setup() throws Exception {
      server = createServer(true);
      server.start();

      server.waitForActivation(10, TimeUnit.SECONDS);

      ServerLocator sl = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      sessionFactory = sl.createSessionFactory();

      addSessionFactory(sessionFactory);

      addressInfo = new AddressInfo(baseAddress);
      addressInfo.addRoutingType(RoutingType.MULTICAST);
      server.addOrUpdateAddressInfo(addressInfo);
   }

   @Test
   public void testTxCommitReceive() throws Exception {

      Queue q1 = server.createQueue(QueueConfiguration.of(baseAddress.concat(".1")).setAddress(baseAddress).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED));
      Queue q2 = server.createQueue(QueueConfiguration.of(baseAddress.concat(".2")).setAddress(baseAddress).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED));

      ClientSession session = sessionFactory.createSession(false, false);
      session.start();

      ClientConsumer consumer1 = session.createConsumer(q1.getName());
      ClientConsumer consumer2 = session.createConsumer(q2.getName());

      ClientProducer producer = session.createProducer(baseAddress);

      final int num = 10;

      for (int i = 0; i < num; i++) {
         ClientMessage m = session.createMessage(ClientMessage.TEXT_TYPE, true);
         m.getBodyBuffer().writeString("AnyCast" + i);
         producer.send(m);
      }
      assertNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());

      session.commit();

      Wait.assertEquals(num, q1::getMessageCount, 5000);
      Wait.assertEquals(num, q2::getMessageCount, 5000);


      ClientConsumer[] consumers = new ClientConsumer[] {consumer1, consumer2};
      for (int i = 0; i < consumers.length; i++) {

         for (int j = 0; j < num; j++) {
            ClientMessage m = consumers[i].receive(2000);
            assertNotNull(m);
            logger.debug("consumer{} received: {}", i, m.getBodyBuffer().readString());
         }

         assertNull(consumers[i].receiveImmediate());
         session.commit();

         assertNull(consumers[i].receiveImmediate());
      }

      q1.deleteQueue();
      q2.deleteQueue();
   }

   @Test
   public void testTxRollbackReceive() throws Exception {

      Queue q1 = server.createQueue(QueueConfiguration.of(baseAddress.concat(".1")).setAddress(baseAddress).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED));
      Queue q2 = server.createQueue(QueueConfiguration.of(baseAddress.concat(".2")).setAddress(baseAddress).setMaxConsumers(Queue.MAX_CONSUMERS_UNLIMITED));

      ClientSession session = sessionFactory.createSession(false, false);
      session.start();

      ClientConsumer consumer1 = session.createConsumer(q1.getName());
      ClientConsumer consumer2 = session.createConsumer(q2.getName());

      ClientProducer producer = session.createProducer(baseAddress);

      final int num = 10;

      for (int i = 0; i < num; i++) {
         ClientMessage m = session.createMessage(ClientMessage.TEXT_TYPE, true);
         m.getBodyBuffer().writeString("AnyCast" + i);
         producer.send(m);
      }
      assertNull(consumer1.receive(200));
      assertNull(consumer2.receive(200));
      session.commit();
      session.close();

      assertTrue(TimeUtils.waitOnBoolean(true, 2000, () -> num == q1.getMessageCount()));
      assertTrue(TimeUtils.waitOnBoolean(true, 2000, () -> num == q2.getMessageCount()));

      ClientSession session1 = sessionFactory.createSession(false, false);
      ClientSession session2 = sessionFactory.createSession(false, false);
      session1.start();
      session2.start();

      consumer1 = session1.createConsumer(q1.getName());
      consumer2 = session2.createConsumer(q2.getName());

      ClientConsumer[] consumers = new ClientConsumer[] {consumer1, consumer2};
      ClientSession[] sessions = new ClientSession[] {session1, session2};
      Queue[] queues = new Queue[] {q1, q2};

      for (int i = 0; i < consumers.length; i++) {

         for (int j = 0; j < num; j++) {
            ClientMessage m = consumers[i].receive(2000);
            assertNotNull(m);
            logger.debug("consumer{} received: {}", i, m.getBodyBuffer().readString());
         }

         assertNull(consumers[i].receive(200));
         sessions[i].rollback();
         sessions[i].close();

         sessions[i] = sessionFactory.createSession(false, false);
         sessions[i].start();

         //receive same after rollback
         consumers[i] = sessions[i].createConsumer(queues[i].getName());

         for (int j = 0; j < num; j++) {
            ClientMessage m = consumers[i].receive(2000);
            assertNotNull(m);
            logger.debug("consumer{} received: {}", i, m.getBodyBuffer().readString());
         }

         assertNull(consumers[i].receive(200));
         sessions[i].commit();

         assertNull(consumers[i].receive(200));
         sessions[i].close();
      }

      q1.deleteQueue();
      q2.deleteQueue();
   }

}
