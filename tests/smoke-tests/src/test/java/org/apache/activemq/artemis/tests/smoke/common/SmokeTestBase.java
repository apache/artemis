/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.tests.smoke.common;

import javax.management.MBeanServerInvocationHandler;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.cli.commands.Stop;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.junit.After;
import org.junit.Assert;

public class SmokeTestBase extends ActiveMQTestBase {
   Set<Process> processes = new HashSet<>();
   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final int JMX_SERVER_PORT = 10099;

   public static final String basedir = System.getProperty("basedir");

   @After
   public void after() throws Exception {
      for (Process process : processes) {
         try {
            ServerUtil.killServer(process, true);
         } catch (Throwable e) {
            e.printStackTrace();
         }
      }
      processes.clear();
   }

   public void killServer(Process process) {
      processes.remove(process);
      try {
         ServerUtil.killServer(process);
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   protected static void stopServerWithFile(String serverLocation) throws IOException {
      File serverPlace = new File(serverLocation);
      File etcPlace = new File(serverPlace, "etc");
      File stopMe = new File(etcPlace, Stop.STOP_FILE_NAME);
      Assert.assertTrue(stopMe.createNewFile());
   }

   public static String getServerLocation(String serverName) {
      return basedir + "/target/" + serverName;
   }

   public static void cleanupData(String serverName) {
      String location = getServerLocation(serverName);
      deleteDirectory(new File(location, "data"));
      deleteDirectory(new File(location, "log"));
   }

   public void addProcess(Process process) {
      processes.add(process);
   }

   public Process startServer(String serverName, int portID, int timeout) throws Exception {
      Process process = ServerUtil.startServer(getServerLocation(serverName), serverName, portID, timeout);
      addProcess(process);
      return process;
   }

   public Process startServer(String serverName, String uri, int timeout) throws Exception {
      Process process = ServerUtil.startServer(getServerLocation(serverName), serverName, uri, timeout);
      addProcess(process);
      return process;
   }

   protected JMXConnector getJmxConnector() throws MalformedURLException {
      return getJmxConnector(JMX_SERVER_HOSTNAME, JMX_SERVER_PORT);
   }

   protected static JMXConnector newJMXFactory(String uri) throws Throwable {
      return JMXConnectorFactory.connect(new JMXServiceURL(uri));
   }

   protected static ActiveMQServerControl getServerControl(String uri,
                                                         ObjectNameBuilder builder,
                                                         long timeout) throws Throwable {
      long expireLoop = System.currentTimeMillis() + timeout;
      Throwable lastException = null;
      do {
         try {
            JMXConnector connector = newJMXFactory(uri);

            ActiveMQServerControl serverControl = MBeanServerInvocationHandler.newProxyInstance(connector.getMBeanServerConnection(), builder.getActiveMQServerObjectName(), ActiveMQServerControl.class, false);
            serverControl.isActive(); // making one call to make sure it's working
            return serverControl;
         } catch (Throwable e) {
            lastException = e;
            Thread.sleep(500);
         }
      }
      while (expireLoop > System.currentTimeMillis());

      throw lastException;
   }

   protected static JMXConnector getJmxConnector(String hostname, int port) throws MalformedURLException {
      // Without this, the RMI server would bind to the default interface IP (the user's local IP mostly)
      System.setProperty("java.rmi.server.hostname", hostname);

      // I don't specify both ports here manually on purpose. See actual RMI registry connection port extraction below.
      String urlString = "service:jmx:rmi:///jndi/rmi://" + hostname + ":" + port + "/jmxrmi";

      JMXServiceURL url = new JMXServiceURL(urlString);
      JMXConnector jmxConnector = null;

      try {
         jmxConnector = JMXConnectorFactory.connect(url);
         System.out.println("Successfully connected to: " + urlString);
      } catch (Exception e) {
         jmxConnector = null;
         e.printStackTrace();
         Assert.fail(e.getMessage());
      }
      return jmxConnector;
   }

   protected static final void recreateBrokerDirectory(final String homeInstance) {
      recreateDirectory(homeInstance + "/data");
      recreateDirectory(homeInstance + "/log");
   }


   public boolean waitForServerToStart(String uri, String username, String password, long timeout) throws InterruptedException {
      long realTimeout = System.currentTimeMillis() + timeout;
      while (System.currentTimeMillis() < realTimeout) {
         try (ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactory(uri, null)) {
            cf.createConnection(username, password).close();
            System.out.println("server " + uri + " started");
         } catch (Exception e) {
            System.out.println("awaiting server " + uri + " start at ");
            Thread.sleep(500);
            continue;
         }
         return true;
      }

      return false;
   }

   protected void checkLogRecord(File logFile, boolean exist, String... values) throws Exception {
      Assert.assertTrue(logFile.exists());
      boolean hasRecord = false;
      try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
         String line = reader.readLine();
         while (line != null) {
            if (line.contains(values[0])) {
               boolean hasAll = true;
               for (int i = 1; i < values.length; i++) {
                  if (!line.contains(values[i])) {
                     hasAll = false;
                     break;
                  }
               }
               if (hasAll) {
                  hasRecord = true;
                  System.out.println("audit has it: " + line);
                  break;
               }
            }
            line = reader.readLine();
         }
         if (exist) {
            Assert.assertTrue(hasRecord);
         } else {
            Assert.assertFalse(hasRecord);
         }
      }
   }

}
