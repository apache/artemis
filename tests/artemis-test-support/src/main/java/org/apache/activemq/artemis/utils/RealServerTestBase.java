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

package org.apache.activemq.artemis.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Base class for tests that will start a real server
public class RealServerTestBase extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   public static final String STOP_FILE_NAME = "STOP_ME";

   Set<Process> processes = new HashSet<>();
   private static final String JMX_SERVER_HOSTNAME = "localhost";
   private static final int JMX_SERVER_PORT = 10099;

   public static final String basedir = System.getProperty("basedir");

   public static final String ARTEMIS_HOME_PROPERTY = "artemis.distribution.output";

   public static HelperCreate helperCreate() {
      return new HelperCreate(ARTEMIS_HOME_PROPERTY);
   }

   @AfterEach
   public void after() throws Exception {
      // close ServerLocators before killing the server otherwise they'll hang and delay test termination
      closeAllServerLocatorsFactories();

      for (Process process : processes) {
         try {
            ServerUtil.killServer(process, true);
         } catch (Throwable e) {
            e.printStackTrace();
         }
      }
      processes.clear();
   }


   protected void jstack(long pid, String serverName) throws Exception {
      try {
         System.out.println("*******************************************************************************************************************************");
         System.out.println("jstack on " + serverName);
         ExecuteUtil.runCommand(true, 1, TimeUnit.MINUTES, "jstack", "" + pid);
         System.out.println("*******************************************************************************************************************************");
      } catch (Throwable e) {
         logger.warn("Error executing jstack on {}", serverName, e);
      }
   }



   public void killServer(Process process) {
      killServer(process, false);
   }

   public void killServer(Process process, boolean forcibly) {
      processes.remove(process);
      try {
         ServerUtil.killServer(process, forcibly);
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   protected static void stopServerWithFile(String serverLocation) throws IOException {
      File serverPlace = new File(serverLocation);
      File etcPlace = new File(serverPlace, "etc");
      File stopMe = new File(etcPlace, STOP_FILE_NAME);
      assertTrue(stopMe.createNewFile());
   }

   protected static void stopServerWithFile(String serverLocation, Process process, int timeout, TimeUnit unit) throws Exception {
      stopServerWithFile(serverLocation);
      process.waitFor(timeout, unit);
   }

   public static String getServerLocation(String serverName) {
      return basedir + "/target/" + serverName;
   }

   public static File getFileServerLocation(String serverName) {
      return new File(getServerLocation(serverName));
   }

   public static boolean cleanupData(String serverName) {
      String location = getServerLocation(serverName);
      boolean result = deleteDirectory(new File(location, "data"));
      deleteDirectory(new File(location, "log"));
      return result;
   }

   public void addProcess(Process process) {
      processes.add(process);
   }

   public void removeProcess(Process process) {
      processes.remove(process);
   }

   public Process startServer(String serverName, int portID, int timeout) throws Exception {
      return startServer(serverName, portID, timeout, null);
   }

   public Process startServer(String serverName, int portID, int timeout, File brokerProperties) throws Exception {
      return startServer(serverName, portID, timeout, brokerProperties, null);
   }

   public Process startServer(String serverName, int portID, int timeout, File brokerProperties, Consumer<String> logCallback) throws Exception {
      Process process = ServerUtil.startServer(getServerLocation(serverName), serverName, portID, timeout, brokerProperties, logCallback);
      addProcess(process);
      return process;
   }

   public Process startServer(String serverName, String uri, int timeout) throws Exception {
      return startServer(serverName, uri, timeout, null);
   }

   public Process startServer(String serverName, String uri, int timeout, Consumer<String> logCallback) throws Exception {
      Process process = ServerUtil.startServer(getServerLocation(serverName), serverName, uri, timeout, null, logCallback);
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
         fail(e.getMessage());
      }
      return jmxConnector;
   }

   protected static final void recreateBrokerDirectory(final String homeInstance) {
      recreateDirectory(homeInstance + "/data");
      recreateDirectory(homeInstance + "/log");
   }

   protected boolean findLogRecord(File logFile, String... values) throws Exception {
      assertTrue(logFile.exists());
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
                  logger.debug("audit found: {}", line);
                  break;
               }
            }
            line = reader.readLine();
         }
         return hasRecord;
      }
   }

   protected static QueueControl getQueueControl(String uri,
                                                 ObjectNameBuilder builder,
                                                 String address,
                                                 String queueName,
                                                 RoutingType routingType,
                                                 long timeout) throws Throwable {
      long expireLoop = System.currentTimeMillis() + timeout;
      Throwable lastException = null;
      do {
         try {
            JMXConnector connector = newJMXFactory(uri);

            ObjectName objectQueueName = builder.getQueueObjectName(SimpleString.of(address), SimpleString.of(queueName), routingType);

            QueueControl queueControl = MBeanServerInvocationHandler.newProxyInstance(connector.getMBeanServerConnection(), objectQueueName, QueueControl.class, false);
            queueControl.getMessagesAcknowledged(); // making one call
            return queueControl;
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
            lastException = e;
            Thread.sleep(500);
         }
      }
      while (expireLoop > System.currentTimeMillis());

      throw lastException;
   }


   protected static void unzip(File zipFile, File serverFolder) throws IOException, ClassNotFoundException, InterruptedException {
      ProcessBuilder zipBuilder = new ProcessBuilder("unzip", zipFile.getAbsolutePath()).directory(serverFolder);

      Process process = zipBuilder.start();
      SpawnedVMSupport.startLogger("zip", process);
      logger.info("Zip finished with {}", process.waitFor());
   }


   protected static void zip(File zipFile, File serverFolder) throws IOException, ClassNotFoundException, InterruptedException {
      logger.info("Zipping data folder for {}", zipFile);
      ProcessBuilder zipBuilder = new ProcessBuilder("zip", "-r", zipFile.getAbsolutePath(), "data").directory(serverFolder);
      Process process = zipBuilder.start();
      SpawnedVMSupport.startLogger("zip", process);
      logger.info("Zip finished with {}", process.waitFor());
   }

   protected static void saveProperties(Properties properties, File propertiesFile) throws Exception {
      try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(propertiesFile))) {
         properties.store(outputStream, "# Broker properties");
      }
   }


   protected long getMessageCount(String uri, String queueName) throws Exception {
      SimpleManagement management = new SimpleManagement(uri, null, null);
      return getMessageCount(management, queueName);
   }

   protected long getMessageCount(SimpleManagement simpleManagement, String queue) throws Exception {
      try {
         long value = simpleManagement.getMessageCountOnQueue(queue);
         logger.info("count on queue {} is {}", queue, value);
         return value;
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
         return -1;
      }
   }


}
