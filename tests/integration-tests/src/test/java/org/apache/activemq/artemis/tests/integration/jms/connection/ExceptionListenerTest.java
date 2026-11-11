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
package org.apache.activemq.artemis.tests.integration.jms.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExceptionListenerTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ActiveMQConnectionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig(), false));
      server.start();
      cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      cf.setBlockOnDurableSend(true);
      cf.setPreAcknowledge(true);
   }

   private class MyExceptionListener implements ExceptionListener {

      volatile int numCalls;

      private final CountDownLatch latch;

      private MyExceptionListener(final CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public synchronized void onException(final JMSException arg0) {
         numCalls++;
         latch.countDown();
      }
   }

   @Test
   public void testListenerCalledForOneConnection() throws Exception {
      Connection conn = cf.createConnection();
      CountDownLatch latch = new CountDownLatch(1);
      MyExceptionListener listener = new MyExceptionListener(latch);

      conn.setExceptionListener(listener);

      ClientSessionInternal coreSession = (ClientSessionInternal) ((ActiveMQConnection) conn).getInitialSession();

      coreSession.getConnection().fail(new ActiveMQInternalErrorException("blah"));

      latch.await(5, TimeUnit.SECONDS);

      assertEquals(1, listener.numCalls);

      conn.close();
   }

   @Test
   public void testListenerCalledForOneConnectionAndSessions() throws Exception {
      Connection conn = cf.createConnection();

      CountDownLatch latch = new CountDownLatch(1);
      MyExceptionListener listener = new MyExceptionListener(latch);

      conn.setExceptionListener(listener);

      Session sess1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Session sess2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Session sess3 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSessionInternal coreSession0 = (ClientSessionInternal) ((ActiveMQConnection) conn).getInitialSession();

      ClientSessionInternal coreSession1 = (ClientSessionInternal) ((ActiveMQSession) sess1).getCoreSession();

      ClientSessionInternal coreSession2 = (ClientSessionInternal) ((ActiveMQSession) sess2).getCoreSession();

      ClientSessionInternal coreSession3 = (ClientSessionInternal) ((ActiveMQSession) sess3).getCoreSession();

      coreSession0.getConnection().fail(new ActiveMQInternalErrorException("blah"));

      coreSession1.getConnection().fail(new ActiveMQInternalErrorException("blah"));

      coreSession2.getConnection().fail(new ActiveMQInternalErrorException("blah"));

      coreSession3.getConnection().fail(new ActiveMQInternalErrorException("blah"));

      latch.await(5, TimeUnit.SECONDS);
      // Listener should only be called once even if all sessions connections die
      assertEquals(1, listener.numCalls);

      conn.close();
   }

   /**
    * The JMS Spec isn't specific about if ClientId can be set after Exception Listener or not, simply it states that
    * clientId must be set before any operation (read as remote)
    * <p>
    * QpidJMS and ActiveMQ5 both interpret that therefor you can set the exception lister first. As such we align with
    * those, allowing the exception listener to be set prior to the clientId, This to avoid causing implementation
    * nuance's, when switching code from one client to another.
    * <p>
    * This test is to test this and to ensure it doesn't get accidentally regressed.
    */
   @Test
   public void testSetClientIdAfterSetExceptionListener() throws Exception {
      Connection conn = cf.createConnection();
      conn.setExceptionListener((e) -> { });
      conn.setClientID("clientId");
      conn.close();
   }

   @Test
   public void testSetClientIdAfterGetExceptionListener() throws Exception {
      Connection conn = cf.createConnection();
      conn.getExceptionListener();
      conn.setClientID("clientId");
      conn.close();
   }

}
