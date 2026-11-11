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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.Test;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class AuditLoggerAMQPMutualSSLTest extends AuditLoggerTestBase {

   @Override
   protected String getServerName() {
      return "audit-logging-amqp-mutual-ssl";
   }

   @Test
   public void testAuditForProducerAndConsumer() throws Exception {
      String sslhost = "amqps://localhost:5500";

      int maxInactivityDurationInitialDelay = 30000;
      int idleTimeout = 120000;
      boolean verifyHost = false;
      String keyStoreLocation = getClass().getClassLoader().getResource("client-keystore.jks").getFile();
      String keyStorePassword = "securepass";
      String trustStoreLocation = getClass().getClassLoader().getResource("server-ca-truststore.jks").getFile();
      String trustStorePassword = "securepass";

      String remoteUri = sslhost +
         "?maxInactivityDurationInitialDelay=" + maxInactivityDurationInitialDelay +
         "&amqp.idleTimeout=" + idleTimeout +
         "&transport.verifyHost=" + verifyHost +
         "&transport.keyStoreLocation=" + keyStoreLocation +
         "&transport.keyStorePassword=" + keyStorePassword +
         "&transport.trustStoreLocation=" + trustStoreLocation +
         "&transport.trustStorePassword=" + trustStorePassword;

      ConnectionFactory connectionFactory = new JmsConnectionFactory(remoteUri);
      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue("exampleQueue");
         MessageProducer sender = session.createProducer(queue);
         TextMessage stm = session.createTextMessage("Hello world ");
         stm.setStringProperty("foo", "bar");
         sender.send(stm);
         session.commit();
         MessageConsumer consumer = session.createConsumer(queue);
         connection.start();
         Message m = consumer.receive(500);
         assertNotNull(m);
         session.commit();
      }

      assertTrue(findLogRecord(getAuditLog(), "AMQ601715: User myUser(consumers,producers)@", "successfully authenticated"));
      assertTrue(findLogRecord(getAuditLog(), "AMQ601267: User myUser(consumers,producers)@", "is creating a core session"));
      assertTrue(findLogRecord(getAuditLog(), "AMQ601500: User myUser(consumers,producers)@", "sent a message AMQPStandardMessage"));
      assertTrue(findLogRecord(getAuditLog(), "AMQ601265: User myUser(consumers,producers)@", "is creating a core consumer"));
      assertTrue(findLogRecord(getAuditLog(), "AMQ601501: User myUser(consumers,producers)@", "is consuming a message from exampleQueue"));
      assertTrue(findLogRecord(getAuditLog(), "AMQ601502: User myUser(consumers,producers)@", "acknowledged message from exampleQueue: AMQPStandardMessage"));
   }
}
