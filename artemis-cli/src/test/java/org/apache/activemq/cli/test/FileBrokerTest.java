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
package org.apache.activemq.cli.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.dto.ServerDTO;
import org.apache.activemq.artemis.integration.FileBroker;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class FileBrokerTest {

   @Test
   public void startWithoutJMS() throws Exception {
      ServerDTO serverDTO = new ServerDTO();
      serverDTO.configuration = "broker-nojms.xml";
      FileBroker broker = new FileBroker(serverDTO, new ActiveMQJAASSecurityManager(), null);
      try {
         broker.start();
         JMSServerManagerImpl jmsServerManager = (JMSServerManagerImpl) broker.getComponents().get("jms");
         assertNull(jmsServerManager);
         ActiveMQServerImpl activeMQServer = (ActiveMQServerImpl) broker.getComponents().get("core");
         assertNotNull(activeMQServer);
         assertTrue(activeMQServer.isStarted());
         assertTrue(broker.isStarted());
      } finally {
         broker.stop();
      }
   }

   @Test
   public void startTwoBrokersWithSameDataDir() throws Exception {
      ServerDTO serverDTO1 = new ServerDTO();
      ServerDTO serverDTO2 = new ServerDTO();
      serverDTO1.configuration = "FileBrokerTest-broker1.xml";
      serverDTO2.configuration = "FileBrokerTest-broker2.xml";
      FileBroker broker1 = new FileBroker(serverDTO1, new ActiveMQJAASSecurityManager(), null);
      FileBroker broker2 = new FileBroker(serverDTO2, new ActiveMQJAASSecurityManager(), null);
      try {
         broker1.start();
         assertTrue(broker1.isStarted());

         Thread thread = new Thread(() -> {
            try {
               broker2.start();
            } catch (Exception e) {
               e.printStackTrace();
            }
         });
         thread.start();

         assertFalse(broker2.isStarted());
         //only if broker1 is stopped can broker2 be fully started
         broker1.stop();
         broker1 = null;

         thread.join(5000);
         assertTrue(broker2.isStarted());
         broker2.stop();

      } finally {
         if (broker1 != null) {
            broker1.stop();
         }
         if (broker2 != null) {
            broker2.stop();
         }
      }
   }

   @Test
   public void testConfigFileReload() throws Exception {
      ServerDTO serverDTO = new ServerDTO();
      serverDTO.configuration = "broker-reload.xml";
      FileBroker broker = null;
      String path = null;
      try {
         SecurityConfiguration securityConfiguration = new SecurityConfiguration();
         securityConfiguration.addUser("myUser", "myPass");
         securityConfiguration.addRole("myUser", "guest");
         ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration);
         broker = new FileBroker(serverDTO, securityManager, null);
         broker.start();
         ActiveMQServerImpl activeMQServer = (ActiveMQServerImpl) broker.getComponents().get("core");
         assertNotNull(activeMQServer);
         assertTrue(activeMQServer.isStarted());
         assertTrue(broker.isStarted());
         File file = new File(activeMQServer.getConfiguration().getConfigurationUrl().toURI());
         path = file.getPath();
         assertNotNull(activeMQServer.getConfiguration().getConfigurationUrl());

         Thread.sleep(activeMQServer.getConfiguration().getConfigurationFileRefreshPeriod() * 2);

         ServerLocator locator = ActiveMQClient.createServerLocator("tcp://localhost:61616");
         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession("myUser", "myPass", false, true, false, false, 0);
         ClientProducer producer = session.createProducer("DLQ");
         producer.send(session.createMessage(true));

         replacePatternInFile(path, "guest", "X");

         Thread.sleep(activeMQServer.getConfiguration().getConfigurationFileRefreshPeriod() * 2);

         try {
            producer.send(session.createMessage(true));
            fail("Should throw a security exception");
         } catch (Exception e) {
         }

         locator.close();
      } finally {
         broker.stop();
         if (path != null) {
            replacePatternInFile(path, "X", "guest");
         }
      }
   }

   @Test
   public void testConfigFileReloadNegative() throws Exception {
      ServerDTO serverDTO = new ServerDTO();
      serverDTO.configuration = "broker-reload-disabled.xml";
      FileBroker broker = null;
      String path = null;
      try {
         SecurityConfiguration securityConfiguration = new SecurityConfiguration();
         securityConfiguration.addUser("myUser", "myPass");
         securityConfiguration.addRole("myUser", "guest");
         ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration);
         broker = new FileBroker(serverDTO, securityManager, null);
         broker.start();
         ActiveMQServerImpl activeMQServer = (ActiveMQServerImpl) broker.getComponents().get("core");
         assertNotNull(activeMQServer);
         assertTrue(activeMQServer.isStarted());
         assertTrue(broker.isStarted());
         File file = new File(activeMQServer.getConfiguration().getConfigurationUrl().toURI());
         path = file.getPath();
         assertNotNull(activeMQServer.getConfiguration().getConfigurationUrl());

         Thread.sleep(1000);

         ServerLocator locator = ActiveMQClient.createServerLocator("tcp://localhost:61616");
         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession("myUser", "myPass", false, true, false, false, 0);
         ClientProducer producer = session.createProducer("DLQ");
         producer.send(session.createMessage(true));

         replacePatternInFile(path, "guest", "X");

         Thread.sleep(1000);

         try {
            producer.send(session.createMessage(true));
         } catch (Exception e) {
            fail("Should not throw an exception: " + e.getMessage());
         }

         locator.close();
      } finally {
         broker.stop();
         if (path != null) {
            replacePatternInFile(path, "X", "guest");
         }
      }
   }

   private void replacePatternInFile(String file, String regex, String replacement) throws IOException {
      Path path = Paths.get(file);
      String content = Files.readString(path);
      String replaced = content.replaceAll(regex, replacement);
      Files.writeString(path, replaced);
      Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
   }
}
