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

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.utils.RandomUtil.randomBytes;
import static org.apache.activemq.artemis.utils.RandomUtil.randomInt;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionCloseTest extends ActiveMQTestBase {


   private ActiveMQServer server;

   private ClientSessionFactory sf;




   @Test
   public void testCanNotUseAClosedSession() throws Exception {

      final ClientSession session = sf.createSession(false, true, true);

      session.close();

      assertTrue(session.isClosed());

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.createProducer());

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.createConsumer(RandomUtil.randomUUIDSimpleString()));

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.createQueue(QueueConfiguration.of(RandomUtil.randomUUIDSimpleString()).setDurable(RandomUtil.randomBoolean())));

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.createQueue(QueueConfiguration.of(RandomUtil.randomUUIDSimpleString()).setAddress(RandomUtil.randomUUIDSimpleString()).setDurable(false).setTemporary(true)));

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.start());

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.stop());

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.commit());

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.rollback());

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.queueQuery(RandomUtil.randomUUIDSimpleString()));

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> session.addressQuery(RandomUtil.randomUUIDSimpleString()));

   }

   @Test
   public void testCanNotUseXAWithClosedSession() throws Exception {

      final ClientSession session = sf.createSession(true, false, false);

      session.close();

      assertTrue(session.isXA());
      assertTrue(session.isClosed());

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, () -> session.commit(randomXid(), true));

      ActiveMQTestBase.expectXAException(XAException.XA_RETRY, () -> session.commit(randomXid(), false));

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, () -> session.end(randomXid(), XAResource.TMSUCCESS));

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, () -> session.forget(randomXid()));

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, () -> session.prepare(randomXid()));

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, () -> session.recover(XAResource.TMSTARTRSCAN));

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, () -> session.rollback(randomXid()));

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, () -> session.start(randomXid(), XAResource.TMNOFLAGS));

   }

   private static Xid randomXid() {
      return new XidImpl(randomBytes(), randomInt(), randomBytes());
   }

   @Test
   public void testCloseHierarchy() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientConsumer consumer = session.createConsumer(queue);

      session.close();

      assertTrue(session.isClosed());
      assertTrue(producer.isClosed());
      assertTrue(consumer.isClosed());

   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), false));
      server.start();
      ServerLocator locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
   }



}
