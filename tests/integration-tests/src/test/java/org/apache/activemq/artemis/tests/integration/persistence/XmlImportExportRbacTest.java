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
package org.apache.activemq.artemis.tests.integration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataExporter;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataImporter;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.integration.security.SecurityTest;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlImportExportRbacTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = SecurityTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }


   public static final int CONSUMER_TIMEOUT = 5000;
   private static final String QUEUE_NAME = "A1";
   private ServerLocator locator;
   private ActiveMQServer server;
   private ClientSessionFactory factory;

   Set<Role> permissionsOnManagementAddress = new HashSet<>();

   private ClientSession basicSetUp() throws Exception {

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      Configuration configuration = createDefaultInVMConfig();
      configuration.setSecurityEnabled(true);
      configuration.setManagementMessageRbac(true);

      server = addServer(ActiveMQServers.newActiveMQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager, true));

      // our 'first' user is in the 'programmers' role, grant minimal necessary permissions
      Set<Role> permissionToProduceAnyMessage = new HashSet<>();
      permissionToProduceAnyMessage.add(new Role("programmers", true, true, true, false, false, false, false, false, true, false, false, false));
      server.getSecurityRepository().addMatch("A1", permissionToProduceAnyMessage);

      permissionsOnManagementAddress.add(new Role("programmers", true, true, true, false, true, true, true, false, true, true, true, false));

      server.getSecurityRepository().addMatch(ActiveMQDefaultConfiguration.getDefaultManagementAddress().toString() + ".*", permissionsOnManagementAddress); // for create reply queue
      server.getSecurityRepository().addMatch(ActiveMQDefaultConfiguration.getDefaultManagementAddress().toString(), permissionsOnManagementAddress); // for send to manage address


      server.start();
      locator = createInVMNonHALocator();
      factory = createSessionFactory(locator);
      return addClientSession(factory.createSession("first", "secret", false, true, true, false, 100));
   }


   @Test
   public void testExportWithOutAndWithQueueControlPerms() throws Exception {
      ClientSession session = basicSetUp();

      session.createQueue(QueueConfiguration.of(QUEUE_NAME));

      ClientProducer producer = session.createProducer(QUEUE_NAME);

      StringBuilder international = new StringBuilder();
      for (char x = 800; x < 1200; x++) {
         international.append(x);
      }

      for (int i = 0; i < 5; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.getBodyBuffer().writeString("Bob the giant pig " + i);
         producer.send(msg);
      }

      session.close();
      locator.close();
      server.stop();

      ByteArrayOutputStream xmlOutputStream = new ByteArrayOutputStream();
      XmlDataExporter xmlDataExporter = new XmlDataExporter();
      xmlDataExporter.process(xmlOutputStream, server.getConfiguration().getBindingsDirectory(), server.getConfiguration().getJournalDirectory(), server.getConfiguration().getPagingDirectory(), server.getConfiguration().getLargeMessagesDirectory());
      if (logger.isDebugEnabled()) {
         logger.debug(new String(xmlOutputStream.toByteArray()));
      }

      clearDataRecreateServerDirs();
      server.start();
      locator = createInVMNonHALocator();
      factory = createSessionFactory(locator);
      session = factory.createSession("first", "secret", false, true, true, false, 100);


      ByteArrayInputStream xmlInputStream = new ByteArrayInputStream(xmlOutputStream.toByteArray());
      XmlDataImporter xmlDataImporter = new XmlDataImporter();
      xmlDataImporter.validate(xmlInputStream);
      xmlInputStream.reset();
      xmlDataImporter.process(xmlInputStream, session);

      // assert messages not present due to no permission
      Queue queue = server.locateQueue(QUEUE_NAME);
      assertEquals(0L, queue.getMessageCount());

      // try again with permission
      server.getSecurityRepository().addMatch(SimpleString.of(server.getConfiguration().getManagementRbacPrefix()).concat(".queue." + QUEUE_NAME + ".getID").toString(), permissionsOnManagementAddress); // for send to manage address

      xmlInputStream = new ByteArrayInputStream(xmlOutputStream.toByteArray());
      xmlDataImporter = new XmlDataImporter();
      xmlDataImporter.validate(xmlInputStream);
      xmlInputStream.reset();
      xmlDataImporter.process(xmlInputStream, session);


      // should be able to consume and verify with the queue control getAttribute view permission
      ClientConsumer consumer = session.createConsumer(QUEUE_NAME);
      session.start();

      for (int i = 0; i < 5; i++) {
         ClientMessage msg = consumer.receive(CONSUMER_TIMEOUT);
         byte[] body = new byte[msg.getBodySize()];
         msg.getBodyBuffer().readBytes(body);
         assertTrue(new String(body).contains("Bob the giant pig " + i));
      }
   }
}
