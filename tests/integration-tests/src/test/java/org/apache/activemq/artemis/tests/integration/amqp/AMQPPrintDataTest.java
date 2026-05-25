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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.activemq.artemis.cli.commands.tools.PrintData;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.server.config.impl.FileJMSConfiguration;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class AMQPPrintDataTest extends ActiveMQTestBase {

   @Test
   public void testPrintDataWithAMQP() throws Exception {
      String random = RandomUtil.randomUUIDString();
      ActiveMQServer server = addServer(getActiveMQServer("dataprint/etc/broker.xml"));
      try {
         server.getConfiguration().setPersistenceEnabled(true);
         server.start();
         ConnectionFactory factory = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");
         Connection connection = factory.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue("q1"));
         TextMessage message = session.createTextMessage("hello");
         message.setStringProperty("hello", "world:" + random);
         producer.send(message);
         connection.close();
         server.stop();
         System.setProperty("artemis.instance",
                            this.getClass().getClassLoader().getResource("dataprint").getFile());

         ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         PrintStream printStream = new PrintStream(byteArrayOutputStream, true, StandardCharsets.UTF_8.name());
         PrintData.printData(server.getConfiguration().getBindingsLocation().getAbsoluteFile(), server.getConfiguration().getJournalLocation().getAbsoluteFile(), server.getConfiguration().getPagingLocation().getAbsoluteFile(),
                             printStream, false, false);

         assertTrue(byteArrayOutputStream.toString().contains(random));


      } finally {
         try {
            server.stop();
         } catch (Exception e) {
         }
      }
   }

   protected ActiveMQServer getActiveMQServer(String brokerConfig) throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileJMSConfiguration fileConfiguration = new FileJMSConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(brokerConfig);
      deploymentManager.addDeployable(fc);
      deploymentManager.addDeployable(fileConfiguration);
      deploymentManager.readConfiguration();

      ActiveMQJAASSecurityManager sm = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());

      recreateDirectory(fc.getBindingsDirectory());
      recreateDirectory(fc.getJournalDirectory());
      recreateDirectory(fc.getPagingDirectory());
      recreateDirectory(fc.getLargeMessagesDirectory());

      return addServer(new ActiveMQServerImpl(fc, sm));
   }

}
