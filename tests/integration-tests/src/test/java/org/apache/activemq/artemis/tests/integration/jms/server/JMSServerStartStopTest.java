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
package org.apache.activemq.artemis.tests.integration.jms.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSServerStartStopTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private Connection conn;

   private ActiveMQConnectionFactory jbcf;
   private final Set<ActiveMQConnectionFactory> connectionFactories = new HashSet<>();

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("server-start-stop-config1.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();


      ActiveMQJAASSecurityManager sm = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());

      server = addServer(new ActiveMQServerImpl(fc, sm));
   }

   @Test
   public void testStopStart1() throws Exception {
      final int numMessages = 5;

      for (int j = 0; j < numMessages; j++) {
         logger.debug("Iteration {}", j);

         server.start();

         ActiveMQConnectionFactory jbcf = createConnectionFactory();

         jbcf.setBlockOnDurableSend(true);
         jbcf.setBlockOnNonDurableSend(true);

         Connection conn = jbcf.createConnection();
         try {
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue queue = sess.createQueue("myJMSQueue");

            MessageProducer producer = sess.createProducer(queue);

            TextMessage tm = sess.createTextMessage("message" + j);

            producer.send(tm);
         } finally {
            conn.close();

            jbcf.close();

            server.stop();
         }
      }

      server.start();

      jbcf = createConnectionFactory();

      jbcf.setBlockOnDurableSend(true);
      jbcf.setBlockOnNonDurableSend(true);

      conn = jbcf.createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Queue queue = sess.createQueue("myJMSQueue");

      MessageConsumer consumer = sess.createConsumer(queue);

      conn.start();

      for (int i = 0; i < numMessages; i++) {
         TextMessage tm = (TextMessage) consumer.receive(10000);

         assertNotNull(tm, "not null");

         assertEquals("message" + i, tm.getText());
      }

      conn.close();

      jbcf.close();
   }

   @Test
   public void testCloseConnectionAfterServerIsShutdown() throws Exception {
      server.start();

      jbcf = createConnectionFactory();

      jbcf.setBlockOnDurableSend(true);
      jbcf.setBlockOnNonDurableSend(true);
      jbcf.setReconnectAttempts(-1);

      conn = jbcf.createConnection();

      server.stop();
      conn.close();
   }

   private ActiveMQConnectionFactory createConnectionFactory() {
      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(NETTY_CONNECTOR_FACTORY));

      connectionFactories.add(cf);
      return cf;
   }
}
