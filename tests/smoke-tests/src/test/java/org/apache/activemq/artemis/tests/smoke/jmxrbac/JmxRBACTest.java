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
package org.apache.activemq.artemis.tests.smoke.jmxrbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import java.io.File;
import java.util.Collections;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JmxRBACTest extends SmokeTestBase {
   // This test will use a smoke created by the pom on this project (smoke-tsts)

   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final int JMX_SERVER_PORT = 10099;

   public static final String BROKER_NAME = "0.0.0.0";

   public static final String SERVER_NAME_0 = "jmx-rbac";

   public static final String SERVER_ADMIN = "admin";
   public static final String SERVER_USER = "user";

   public static final String ADDRESS_TEST = "TEST";

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("admin").setPassword("admin").setAllowAnonymous(false).setNoWeb(true).setArtemisInstance(server0Location).
            setConfiguration("./src/main/resources/servers/jmx-rbac").setArgs("--java-options", "-Djava.rmi.server.hostname=localhost");
         cliCreateServer.createServer();
      }
   }


   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      disableCheckThread();
      startServer(SERVER_NAME_0, 0, 0);
      ServerUtil.waitForServerToStart(0, SERVER_ADMIN, SERVER_ADMIN, 30000);
   }

   @Test
   public void testManagementRoleAccess() throws Exception {

      // I don't specify both ports here manually on purpose. See actual RMI registry connection port extraction below.
      String urlString = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT + "/jmxrmi";

      JMXServiceURL url = new JMXServiceURL(urlString);
      JMXConnector jmxConnector;

      try {
         //Connect using the admin.
         jmxConnector = JMXConnectorFactory.connect(url, Collections.singletonMap(
            "jmx.remote.credentials", new String[] {SERVER_ADMIN, SERVER_ADMIN}));
         System.out.println("Successfully connected to: " + urlString);
      } catch (Exception e) {
         jmxConnector = null;
         e.printStackTrace();
         fail(e.getMessage());
      }

      try {
         //Create an user.
         MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
         ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), BROKER_NAME, true);
         ActiveMQServerControl activeMQServerControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);
         ObjectName memoryObjectName = new ObjectName("java.lang:type=Memory");

         try {
            activeMQServerControl.removeUser(SERVER_USER);
         } catch (Exception ignore) {
         }
         activeMQServerControl.addUser(SERVER_USER, SERVER_USER, "amq-user", true);

         activeMQServerControl.getVersion();

         try {
            mBeanServerConnection.invoke(memoryObjectName, "gc", null, null);
            fail(SERVER_ADMIN + " should not access to " + memoryObjectName);
         } catch (Exception e) {
            assertEquals(SecurityException.class, e.getClass());
         }
      } finally {
         jmxConnector.close();
      }

      try {
         //Connect using an user.
         jmxConnector = JMXConnectorFactory.connect(url, Collections.singletonMap(
            "jmx.remote.credentials", new String[] {SERVER_USER, SERVER_USER}));
         System.out.println("Successfully connected to: " + urlString);
      } catch (Exception e) {
         jmxConnector = null;
         e.printStackTrace();
         fail(e.getMessage());
      }


      try {
         MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
         ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), BROKER_NAME, true);
         ActiveMQServerControl activeMQServerControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);
         ObjectName memoryObjectName = new ObjectName("java.lang:type=Memory");

         mBeanServerConnection.invoke(memoryObjectName, "gc", null, null);

         try {
            activeMQServerControl.getVersion();
            fail(SERVER_USER + " should not access to " + objectNameBuilder.getActiveMQServerObjectName());
         } catch (Exception e) {
            assertEquals(SecurityException.class, e.getClass());
         }
      } finally {
         jmxConnector.close();
      }
   }

   @Test
   public void testSendMessageWithoutUserAndPassword() throws Exception {

      // I don't specify both ports here manually on purpose. See actual RMI registry connection port extraction below.
      String urlString = "service:jmx:rmi:///jndi/rmi://" + JMX_SERVER_HOSTNAME + ":" + JMX_SERVER_PORT + "/jmxrmi";

      JMXServiceURL url = new JMXServiceURL(urlString);
      JMXConnector jmxConnector;

      try {
         //Connect using the admin.
         jmxConnector = JMXConnectorFactory.connect(url, Collections.singletonMap(
            "jmx.remote.credentials", new String[] {SERVER_ADMIN, SERVER_ADMIN}));
         System.out.println("Successfully connected to: " + urlString);
      } catch (Exception e) {
         jmxConnector = null;
         e.printStackTrace();
         fail(e.getMessage());
      }

      try {
         MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
         ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), BROKER_NAME, true);
         ActiveMQServerControl activeMQServerControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);

         activeMQServerControl.createAddress(ADDRESS_TEST, RoutingType.MULTICAST.name());
         AddressControl testAddressControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getAddressObjectName(SimpleString.of(ADDRESS_TEST)), AddressControl.class, false);

         testAddressControl.sendMessage(null, Message.TEXT_TYPE, ADDRESS_TEST, true, null, null);


         try {
            activeMQServerControl.removeUser(SERVER_USER);
         } catch (Exception ignore) {
         }
         activeMQServerControl.addUser(SERVER_USER, SERVER_USER, "amq-user", true);
      } finally {
         jmxConnector.close();
      }

      try {
         //Connect using an user.
         jmxConnector = JMXConnectorFactory.connect(url, Collections.singletonMap(
            "jmx.remote.credentials", new String[] {SERVER_USER, SERVER_USER}));
         System.out.println("Successfully connected to: " + urlString);
      } catch (Exception e) {
         jmxConnector = null;
         e.printStackTrace();
         fail(e.getMessage());
      }

      try {
         MBeanServerConnection mBeanServerConnection = jmxConnector.getMBeanServerConnection();
         ObjectNameBuilder objectNameBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), BROKER_NAME, true);
         AddressControl testAddressControl = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, objectNameBuilder.getAddressObjectName(SimpleString.of("TEST")), AddressControl.class, false);

         try {
            testAddressControl.sendMessage(null, Message.TEXT_TYPE, ADDRESS_TEST, true, null, null);
            fail(SERVER_USER + " should not have permissions to send a message to the address " + ADDRESS_TEST);
         } catch (Exception e) {
            assertEquals(SecurityException.class, e.getClass());
         }
      } finally {
         jmxConnector.close();
      }
   }
}
