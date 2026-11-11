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

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class FailureDeadlockTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private ActiveMQConnectionFactory cf1;

   private ActiveMQConnectionFactory cf2;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false, createDefaultInVMConfig());
      server.start();

      cf1 = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      cf2 = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
   }

   // Test that two failures concurrently executing and calling the same exception listener don't deadlock
   @Test
   public void testDeadlock() throws Exception {
      for (int i = 0; i < 100; i++) {
         final Connection conn1 = cf1.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         RemotingConnection rc1 = ((ClientSessionInternal) ((ActiveMQSession) sess1).getCoreSession()).getConnection();

         final Connection conn2 = cf2.createConnection();

         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         RemotingConnection rc2 = ((ClientSessionInternal) ((ActiveMQSession) sess2).getCoreSession()).getConnection();

         ExceptionListener listener1 = exception -> {
            try {
               conn2.close();
            } catch (Exception e) {
               logger.error("Failed to close connection2", e);
            }
         };

         conn1.setExceptionListener(listener1);

         conn2.setExceptionListener(listener1);

         Failer f1 = new Failer(rc1);

         Failer f2 = new Failer(rc2);

         f1.start();

         f2.start();

         f1.join();

         f2.join();

         conn1.close();

         conn2.close();
      }
   }

   private class Failer extends Thread {

      RemotingConnection conn;

      Failer(final RemotingConnection conn) {
         this.conn = conn;
      }

      @Override
      public void run() {
         conn.fail(new ActiveMQNotConnectedException("blah"));
      }
   }

   // https://jira.jboss.org/jira/browse/JBMESSAGING-1703
   // Make sure that failing a connection removes it from the connection manager and can't be returned in a subsequent
   // call
   @Test
   public void testUsingDeadConnection() throws Exception {
      for (int i = 0; i < 100; i++) {
         final Connection conn1 = cf1.createConnection();

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         RemotingConnection rc1 = ((ClientSessionInternal) ((ActiveMQSession) sess1).getCoreSession()).getConnection();

         rc1.fail(new ActiveMQNotConnectedException("blah"));

         try {
            conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
            fail("should throw exception");
         } catch (JMSException e) {
            //pass
         }

         conn1.close();
      }
   }

}
