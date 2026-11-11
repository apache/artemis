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
package org.apache.activemq.artemis.tests.smoke.jmxfailback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.management.MBeanServerInvocationHandler;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.File;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JmxFailbackTest extends SmokeTestBase {

   // This test will use a smoke created by the pom on this project (smoke-tsts)
   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final int JMX_SERVER_PORT_0 = 10099;
   private static final int JMX_SERVER_PORT_1 = 10199;

   public static final String SERVER_NAME_0 = "jmx-failback1";
   public static final String SERVER_NAME_1 = "jmx-failback2";

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);
      File server1Location = getFileServerLocation(SERVER_NAME_1);
      deleteDirectory(server1Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location).
            setConfiguration("./src/main/resources/servers/jmx-failback1").setArgs("--java-options", "-Djava.rmi.server.hostname=localhost");
         cliCreateServer.createServer();
      }

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server1Location).
            setConfiguration("./src/main/resources/servers/jmx-failback2").setArgs("--java-options", "-Djava.rmi.server.hostname=localhost");
         cliCreateServer.createServer();
      }
   }

   String urlString_1 = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT_0 + "/jmxrmi";
   String urlString_2 = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT_1 + "/jmxrmi";

   ObjectNameBuilder objectNameBuilder1 = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), "live", true);
   ObjectNameBuilder objectNameBuilder2 = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), "backup", true);

   JMXServiceURL url1 = null;
   JMXServiceURL url2 = null;

   Process server1;
   Process server2;

   @BeforeEach
   public void before() throws Exception {
      url1 = new JMXServiceURL(urlString_1);
      url2 = new JMXServiceURL(urlString_2);
      deleteDirectory(new File("shared-jmxfailback"));
      disableCheckThread();
      server1 = startServer(SERVER_NAME_0, 0, 30000);
      Wait.assertTrue(() -> testConnection(url1, objectNameBuilder1));
      server2 = startServer(SERVER_NAME_1, 0, 0);
      Wait.assertTrue(() -> testConnection(url2, objectNameBuilder2));
   }

   boolean isBackup(JMXServiceURL serviceURI, ObjectNameBuilder builder) throws Exception {
      JMXConnector jmx = null;
      try {
         jmx = JMXConnectorFactory.connect(serviceURI);
         builder.getActiveMQServerObjectName();

         ActiveMQServerControl control = MBeanServerInvocationHandler.newProxyInstance(jmx.getMBeanServerConnection(), builder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);
         return control.isBackup(); // performing any operation to make sure JMX is bound already
      } finally {
         try {
            jmx.close();
         } catch (Exception e) {
         }
      }
   }

   boolean testConnection(JMXServiceURL serviceURI, ObjectNameBuilder builder) {
      try {
         isBackup(serviceURI, builder);
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   @Test
   public void testFailbackOnJMX() throws Exception {
      assertFalse(isBackup(url1, objectNameBuilder1));
      assertTrue(isBackup(url2, objectNameBuilder2));

      server1.destroyForcibly();
      Wait.assertFalse(() -> isBackup(url2, objectNameBuilder2));

      server1 = startServer(SERVER_NAME_0, 0, 30000);
      Wait.assertTrue(() -> testConnection(url1, objectNameBuilder1), 5_000, 100);
      Wait.assertTrue(() -> testConnection(url2, objectNameBuilder2), 5_000, 100);

      Wait.assertFalse(() -> isBackup(url1, objectNameBuilder1), 5_000, 100);
      Wait.assertTrue(() -> isBackup(url2, objectNameBuilder2), 5_000, 100);
   }
}
