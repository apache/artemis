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

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import java.net.URI;
import java.util.HashMap;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.junit.jupiter.api.Test;

public class AuditLoggerResourceTest extends AuditLoggerTestBase {

   @Override
   protected String getServerName() {
      return "audit-logging";
   }

   @Test
   public void testAuditResourceLog() throws Exception {
      JMXConnector jmxConnector = getJmxConnector();

      try {
         MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
         String brokerName = "0.0.0.0";  // configured e.g. in broker.xml <broker-name> element
         ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), brokerName, true);
         ActiveMQServerControl serverControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);

         serverControl.createAddress("auditAddress", "ANYCAST,MULTICAST");
         assertTrue(findLogRecord(getAuditLog(), "successfully created address:"));
         serverControl.updateAddress("auditAddress", "ANYCAST");
         assertTrue(findLogRecord(getAuditLog(), "successfully updated address:"));
         serverControl.deleteAddress("auditAddress");
         assertTrue(findLogRecord(getAuditLog(), "successfully deleted address:"));
         serverControl.createQueue("auditAddress", "auditQueue", "ANYCAST");
         assertTrue(findLogRecord(getAuditLog(), "successfully created queue:"));
         serverControl.updateQueue("auditQueue", "ANYCAST", -1, false);
         final QueueControl queueControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection,
               objectNameBuilder.getQueueObjectName(SimpleString.of("auditAddress"), SimpleString.of("auditQueue"), RoutingType.ANYCAST),
               QueueControl.class,
               false);
         assertTrue(findLogRecord(getAuditLog(), "successfully updated queue:"));
         queueControl.removeAllMessages();
         assertTrue(findLogRecord(getAuditLog(), "has removed 0 messages"));
         queueControl.sendMessage(new HashMap<>(), 0, "foo", true, "admin", "admin");
         assertTrue(findLogRecord(getAuditLog(), "sent message to"));
         CompositeData[] browse = queueControl.browse();
         assertTrue(findLogRecord(getAuditLog(), "browsed " + browse.length + " messages"));
         serverControl.destroyQueue("auditQueue");
         assertTrue(findLogRecord(getAuditLog(), "successfully deleted queue:"));

         ServerLocator locator = createNettyNonHALocator();
         ClientSessionFactory sessionFactory = locator.createSessionFactory();
         ClientSession session = sessionFactory.createSession("admin", "admin", false, false, true, false, 0);
         ClientProducer producer = session.createProducer("myQ");
         producer.send(session.createMessage(true));
         locator.close();

      } finally {
         jmxConnector.close();
      }
   }

   @Test
   public void testCoreConnectionAuditLog() throws Exception {
      testConnectionAuditLog("CORE", "tcp://localhost:61616");
   }

   @Test
   public void testAMQPConnectionAuditLog() throws Exception {
      testConnectionAuditLog("AMQP", "amqp://localhost:61616");
   }

   @Test
   public void testAMQPNoSaslConnectionAuditLog() throws Exception {
      testConnectionAuditLog("AMQP", "amqp://localhost:61616?amqp.saslLayer=false");
   }

   @Test
   public void testOpenWireConnectionAuditLog() throws Exception {
      testConnectionAuditLog("OPENWIRE", "tcp://localhost:61616");
   }

   private void testConnectionAuditLog(String protocol, String url) throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, url);
      Connection connection = factory.createConnection();
      Session s = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Wait.assertTrue(() -> findLogRecord(getAuditLog(), "AMQ601767: " + protocol + " connection"), 5000, 100);
      s.close();
      connection.close();
      Wait.assertTrue(() -> findLogRecord(getAuditLog(), "AMQ601768: " + protocol + " connection"), 5000, 100);
   }

   @Test
   public void testMQTTConnectionAuditLog() throws Exception {
      MQTT mqtt = new MQTT();
      mqtt.setConnectAttemptsMax(1);
      mqtt.setReconnectAttemptsMax(0);
      mqtt.setVersion("3.1.1");
      mqtt.setClientId(RandomUtil.randomUUIDString());
      mqtt.setCleanSession(true);
      mqtt.setHost("localhost", 1883);
      final BlockingConnection connection = mqtt.blockingConnection();
      connection.connect();
      connection.disconnect();
      Wait.assertTrue(() -> findLogRecord(getAuditLog(), "AMQ601767: MQTT connection"), 5000, 100);
      Wait.assertTrue(() -> findLogRecord(getAuditLog(), "AMQ601768: MQTT connection"), 5000, 100);
   }

   @Test
   public void testStompConnectionAuditLog() throws Exception {
      StompClientConnection connection = StompClientConnectionFactory.createClientConnection(new URI("tcp://localhost:61613"));
      connection.connect();
      connection.disconnect();
      Wait.assertTrue(() -> findLogRecord(getAuditLog(), "AMQ601767: STOMP connection"), 5000, 100);
      Wait.assertTrue(() -> findLogRecord(getAuditLog(), "AMQ601768: STOMP connection"), 5000, 100);
   }
}
