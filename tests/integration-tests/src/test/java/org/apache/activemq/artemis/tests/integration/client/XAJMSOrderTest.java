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

import static org.apache.activemq.artemis.tests.util.CFUtil.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class XAJMSOrderTest extends JMSTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   String protocol;
   boolean exclusive;
   ConnectionFactory protocolCF;

   @Override
   protected boolean usePersistence() {
      return true;
   }

   public XAJMSOrderTest(String protocol, boolean exclusive) {
      this.protocol = protocol;
      this.exclusive = exclusive;
   }

   @BeforeEach
   public void setupCF() {
      protocolCF = createConnectionFactory(protocol, "tcp://localhost:61616");
   }

   @Parameters(name = "protocol={0}&exclusive={1}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{"CORE", true}, {"CORE", false}});
   }

   @Override
   protected void extraServerConfig(ActiveMQServer server) {
      if (exclusive) {
         server.getConfiguration().getAddressSettings().put("#", new AddressSettings().setAutoCreateQueues(true).setAutoCreateAddresses(true).setDeadLetterAddress(SimpleString.of("ActiveMQ.DLQ")).setDefaultExclusiveQueue(true));
      }
   }

   @TestTemplate
   public void testPreparedRollbackACKWithRestart() throws Exception {
      org.apache.activemq.artemis.core.server.Queue serverQueue = server.createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      final int NUMBER_OF_MESSAGES = 30;

      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         Queue queue = session.createQueue(getName());
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            Message message = session.createTextMessage("hello " + i);
            message.setIntProperty("i", i);
            producer.send(message);
         }
      }

      Wait.assertEquals(NUMBER_OF_MESSAGES, serverQueue::getMessageCount, 2000);

      Xid xid = newXID(); // prepared TX with ACK

      try (XAConnection connection = ((XAConnectionFactory) cf).createXAConnection(); XASession session = connection.createXASession()) {
         Queue queue = session.createQueue(getName());
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         session.getXAResource().start(xid, XAResource.TMNOFLAGS);

         for (int i = 0; i < 5; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            logger.debug("message {} received", message.getText());
            assertEquals("hello " + i, message.getText());
            assertEquals(i, message.getIntProperty("i"));
         }

         session.getXAResource().end(xid, XAResource.TMSUCCESS);
         session.getXAResource().prepare(xid);
      }

      server.stop();

      server.start();

      serverQueue = server.locateQueue(getName());

      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
         Queue queue = session.createQueue(getName());
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();

         for (int i = 5; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            logger.debug("message {} received", message.getText());
            assertEquals("hello " + i, message.getText());
            assertEquals(i, message.getIntProperty("i"));
         }
         session.rollback();
      }

      try (XAConnection connection = ((XAConnectionFactory) cf).createXAConnection(); XASession session = connection.createXASession()) {
         session.getXAResource().rollback(xid);
      }

      try (Connection connection = cf.createConnection();
           Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
         Queue queue = session.createQueue(getName());
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            logger.debug("message {} received", message.getText());
            assertEquals("hello " + i, message.getText());
            assertEquals(i, message.getIntProperty("i"));
         }
         session.commit();
      }

      Wait.assertEquals(0, serverQueue::getMessageCount, 2000);
   }
}
