/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.smoke.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.remote.JMXConnector;
import java.util.UUID;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Base64;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuditLoggerTest extends AuditLoggerTestBase {

   protected ClientSession session;
   private ServerLocator locator;
   private ClientSessionFactory sf;

   @BeforeEach
   @Override
   public void before() throws Exception {
      super.before();
      locator = createNonHALocator(true).setBlockOnNonDurableSend(true);
      sf = createSessionFactory(locator);
      session = sf.createSession("guest", "guest", false, true, false, false, 100);
      session.start();
      addClientSession(session);
   }

   @Override
   protected String getServerName() {
      return "audit-logging2";
   }

   @Test
   public void testAuditLog() throws Exception {
      JMXConnector jmxConnector = getJmxConnector();

      MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
      String brokerName = "0.0.0.0";  // configured e.g. in broker.xml <broker-name> element
      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), brokerName, true);
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      session.createAddress(address, RoutingType.ANYCAST, false);

      final AddressControl addressControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getAddressObjectName(address), AddressControl.class, false);

      assertEquals(0, addressControl.getQueueNames().length);
      session.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST));
      assertEquals(1, addressControl.getQueueNames().length);
      String uniqueStr = Base64.encodeBytes(UUID.randomUUID().toString().getBytes());
      addressControl.sendMessage(null, Message.BYTES_TYPE, uniqueStr, false, null, null);

      Wait.waitFor(() -> addressControl.getMessageCount() == 1);
      assertEquals(1, addressControl.getMessageCount());

      assertTrue(findLogRecord(getAuditLog(), "sending a message", uniqueStr));

      //failure log
      address = RandomUtil.randomUUIDSimpleString();
      session.createAddress(address, RoutingType.ANYCAST, false);

      final AddressControl addressControl2 = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getAddressObjectName(address), AddressControl.class, false);

      assertEquals(1, addressControl.getQueueNames().length);

      session.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      Wait.waitFor(() -> addressControl2.getQueueNames().length == 1);

      ClientProducer producer = session.createProducer(address);
      producer.send(session.createMessage(true));
      Wait.waitFor(() -> addressControl.getMessageCount() == 1);
      try {
         session.deleteQueue(address);
         fail("Deleting queue should get exception");
      } catch (Exception e) {
         //ignore
      }

      assertTrue(findLogRecord(getAuditLog(), "AMQ601264: User guest", "gets security check failure, reason = AMQ229213: User: guest does not have permission='DELETE_NON_DURABLE_QUEUE'"));
      //hot patch not in log
      assertTrue(findLogRecord(getAuditLog(), "is sending a message"));
   }

   @Test
   public void testAuditHotLogCore() throws Exception {
      internalSend("CORE", 64);
   }

   @Test
   public void testAuditHotLogAMQP() throws Exception {
      internalSend("AMQP", 64);
   }

   @Test
   public void testAuditHotLogCoreLarge() throws Exception {
      internalSend("CORE", 1024 * 1024);
   }

   @Test
   public void testAuditHotLogAMQPLarge() throws Exception {
      internalSend("AMQP", 1024 * 1024);
   }

   public void internalSend(String protocol, int messageSize) throws Exception {
      JMXConnector jmxConnector = getJmxConnector();
      MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
      String brokerName = "0.0.0.0";  // configured e.g. in broker.xml <broker-name> element
      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), brokerName, true);
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      session.createAddress(address, RoutingType.ANYCAST, false);

      final AddressControl addressControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getAddressObjectName(address), AddressControl.class, false);

      assertEquals(0, addressControl.getQueueNames().length);
      session.createQueue(QueueConfiguration.of(address).setRoutingType(RoutingType.ANYCAST));
      assertEquals(1, addressControl.getQueueNames().length);
      String uniqueStr = RandomUtil.randomUUIDString();

      session.close();

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      Connection connection = factory.createConnection();
      try {
         Session session = connection.createSession();
         MessageProducer producer = session.createProducer(session.createQueue(address.toString()));

         byte[] msgs = new byte[messageSize];
         for (int i = 0; i < msgs.length; i++) {
            msgs[i] = RandomUtil.randomByte();
         }

         TextMessage message = session.createTextMessage(new String(msgs));
         message.setStringProperty("str", uniqueStr);
         producer.send(message);

         message = session.createTextMessage("msg2");
         message.setStringProperty("str", "Hello2");
         producer.send(message);
        // addressControl.sendMessage(null, Message.BYTES_TYPE, uniqueStr, false, null, null);

         Wait.waitFor(() -> addressControl.getMessageCount() == 2);
         assertEquals(2, addressControl.getMessageCount());

         assertFalse(findLogRecord(getAuditLog(), "messageID=0"));
         assertTrue(findLogRecord(getAuditLog(), "sent a message"));
         assertTrue(findLogRecord(getAuditLog(), uniqueStr));
         assertTrue(findLogRecord(getAuditLog(), "Hello2"));

         connection.start();
         MessageConsumer consumer = session.createConsumer(session.createQueue(address.toString()));
         javax.jms.Message clientMessage = consumer.receive(5000);
         assertNotNull(clientMessage);
         clientMessage = consumer.receive(5000);
         assertNotNull(clientMessage);
      } finally {
         connection.close();
      }
      Wait.assertTrue(() -> findLogRecord(getAuditLog(), "is consuming a message from"), 5000);
      Wait.assertTrue(() -> findLogRecord(getAuditLog(), "acknowledged message from"), 5000);
   }

   @Test
   public void testExpiration() throws Exception {
      final int EXPIRATION = 1000;
      final SimpleString queue = RandomUtil.randomUUIDSimpleString();
      final SimpleString expiryQueue = RandomUtil.randomUUIDSimpleString();

      JMXConnector jmxConnector = getJmxConnector();
      MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
      String brokerName = "0.0.0.0";  // configured e.g. in broker.xml <broker-name> element
      ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), brokerName, true);

      final ActiveMQServerControl serverControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);

      serverControl.addAddressSettings(queue.toString(), new AddressSettings().setExpiryAddress(expiryQueue).toJSON());

      session.createQueue(QueueConfiguration.of(queue).setAddress(queue).setDurable(false));
      session.createQueue(QueueConfiguration.of(expiryQueue).setAddress(expiryQueue).setDurable(false));

      ClientProducer producer = session.createProducer(queue);
      ClientMessage message = session.createMessage(false);
      message.setExpiration(System.currentTimeMillis() + EXPIRATION);
      producer.send(message);

      Thread.sleep(EXPIRATION);

      Wait.assertTrue(() -> findLogRecord(getAuditLog(), "User system@internal acknowledged message from"), EXPIRATION * 2, 100);
   }
}
