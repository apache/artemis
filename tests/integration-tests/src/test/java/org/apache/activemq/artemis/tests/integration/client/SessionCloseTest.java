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

import org.apache.activemq.artemis.api.core.ActiveMQException;
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
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.activemq.artemis.tests.util.RandomUtil.randomXid;

public class SessionCloseTest extends ActiveMQTestBase {


   private ActiveMQServer server;

   private ClientSessionFactory sf;




   @Test
   public void testCanNotUseAClosedSession() throws Exception {

      final ClientSession session = sf.createSession(false, true, true);

      session.close();

      Assert.assertTrue(session.isClosed());

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.createProducer();
         }
      });

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.createConsumer(RandomUtil.randomSimpleString());
         }
      });

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.createQueue(new QueueConfiguration(RandomUtil.randomSimpleString()).setDurable(RandomUtil.randomBoolean()));
         }
      });

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.createQueue(new QueueConfiguration(RandomUtil.randomSimpleString()).setAddress(RandomUtil.randomSimpleString()).setDurable(false).setTemporary(true));
         }
      });

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.start();
         }
      });

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.stop();
         }
      });

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.commit();
         }
      });

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.rollback();
         }
      });

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.queueQuery(RandomUtil.randomSimpleString());
         }
      });

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, new ActiveMQAction() {
         @Override
         public void run() throws ActiveMQException {
            session.addressQuery(RandomUtil.randomSimpleString());
         }
      });

   }

   @Test
   public void testCanNotUseXAWithClosedSession() throws Exception {

      final ClientSession session = sf.createSession(true, false, false);

      session.close();

      Assert.assertTrue(session.isXA());
      Assert.assertTrue(session.isClosed());

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, new ActiveMQAction() {
         @Override
         public void run() throws XAException {
            session.commit(randomXid(), true);
         }
      });

      ActiveMQTestBase.expectXAException(XAException.XA_RETRY, new ActiveMQAction() {
         @Override
         public void run() throws XAException {
            session.commit(randomXid(), false);
         }
      });

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, new ActiveMQAction() {
         @Override
         public void run() throws XAException {
            session.end(randomXid(), XAResource.TMSUCCESS);
         }
      });

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, new ActiveMQAction() {
         @Override
         public void run() throws XAException {
            session.forget(randomXid());
         }
      });

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, new ActiveMQAction() {
         @Override
         public void run() throws XAException {
            session.prepare(randomXid());
         }
      });

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, new ActiveMQAction() {
         @Override
         public void run() throws XAException {
            session.recover(XAResource.TMSTARTRSCAN);
         }
      });

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, new ActiveMQAction() {
         @Override
         public void run() throws XAException {
            session.rollback(randomXid());
         }
      });

      ActiveMQTestBase.expectXAException(XAException.XAER_RMFAIL, new ActiveMQAction() {
         @Override
         public void run() throws XAException {
            session.start(randomXid(), XAResource.TMNOFLAGS);
         }
      });

   }

   @Test
   public void testCloseHierarchy() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString queue = RandomUtil.randomSimpleString();

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientConsumer consumer = session.createConsumer(queue);

      session.close();

      Assert.assertTrue(session.isClosed());
      Assert.assertTrue(producer.isClosed());
      Assert.assertTrue(consumer.isClosed());

   }



   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), false));
      server.start();
      ServerLocator locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
   }



}
