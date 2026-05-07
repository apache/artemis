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
package org.apache.activemq.artemis.util;

import javax.jms.Connection;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

/**
 * A tool to let clients start, stop and kill Artemis servers
 */
public class ServerUtil {

   public static Process startServer(String artemisInstance, String serverName) throws Exception {
      return startServer(artemisInstance, serverName, 0, 0);
   }

   /**
    * Start the server.
    *
    * @param serverName it will be used on logs
    * @param id         it will be used to add on the port
    */
   public static Process startServer(String artemisInstance, String serverName, int id, int timeout) throws Exception {
      return startServer(artemisInstance, serverName, id, timeout, null);
   }

   public static Process startServer(String artemisInstance, String serverName, int id, int timeout, File brokerProperties) throws Exception {
      return startServer(artemisInstance, serverName, id, timeout, brokerProperties, null);
   }

   public static Process startServer(String artemisInstance, String serverName, int id, int timeout, File brokerProperties, Consumer<String> logCallback) throws Exception {
      final Process process = internalStartServer(artemisInstance, serverName, brokerProperties, logCallback);

      // wait for start
      if (timeout > 0) {
         waitForServerToStart(id, timeout);
      }

      return process;
   }

   public static Process startServer(String artemisInstance, String serverName, String uri, int timeout) throws Exception {
      return  startServer(artemisInstance, serverName, uri, timeout, null);
   }

   public static Process startServer(String artemisInstance, String serverName, String uri, int timeout, File propertiesFile) throws Exception {
      return startServer(artemisInstance, serverName, uri, timeout, propertiesFile, null);
   }

   public static Process startServer(String artemisInstance, String serverName, String uri, int timeout, File propertiesFile, Consumer<String> logCallback) throws Exception {
      final Process process = internalStartServer(artemisInstance, serverName, propertiesFile, logCallback);

      // wait for start
      if (timeout != 0) {
         waitForServerToStart(uri, timeout);
      }

      return process;
   }

   private static Process internalStartServer(String artemisInstance,
                                              String serverName) throws IOException, ClassNotFoundException {
      return internalStartServer(artemisInstance, serverName, null, null);
   }
   private static Process internalStartServer(String artemisInstance,
                                              String serverName,
                                              File propertiesFile) throws IOException, ClassNotFoundException {
      return internalStartServer(artemisInstance, serverName, propertiesFile, null);
   }
   private static Process internalStartServer(String artemisInstance,
                                              String serverName,
                                              File propertiesFile,
                                              Consumer<String> logCallback) throws IOException, ClassNotFoundException {

      if (propertiesFile != null) {
         return execute(artemisInstance, serverName, logCallback, "run", "--properties", propertiesFile.getAbsolutePath());
      } else {
         return execute(artemisInstance, serverName, logCallback, "run");
      }
   }

   public static Process execute(String artemisInstance, String jobName, String...args) throws IOException, ClassNotFoundException {
      return execute(artemisInstance, jobName, null, args);
   }

   public static Process execute(String artemisInstance, String jobName, Consumer<String> logCallback, String...args) throws IOException, ClassNotFoundException {
      try {
         boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().trim().startsWith("win");

         List<String> command = new ArrayList<>();

         ProcessBuilder builder = null;
         if (IS_WINDOWS) {
            command.add("cmd");
            command.add("/c");
            command.add("artemis.cmd");
         } else {
            command.add("./artemis");
         }

         for (String arg: args) {
            command.add(arg);
         }

         builder = new ProcessBuilder(command);

         builder.directory(new File(artemisInstance + "/bin"));

         final Process process = builder.start();
         Runtime.getRuntime().addShutdownHook(new Thread(() -> process.destroy()));

         ProcessLogger outputLogger = new ProcessLogger(logCallback == null, process.getInputStream(), jobName, false, logCallback);
         outputLogger.start();

         // Adding a reader to System.err, so the VM won't hang on a System.err.println
         ProcessLogger errorLogger = new ProcessLogger(logCallback == null, process.getErrorStream(), jobName, true, logCallback);
         errorLogger.start();
         return process;
      } catch (IOException e) {
         throw new IOException("Cannot start server at " + artemisInstance, e);
      }
   }

   public static boolean waitForServerToStart(int id, int timeout) throws InterruptedException {
      return waitForServerToStart(id, null, null, timeout);
   }

   public static boolean waitForServerToStart(int id, String username, String password, int timeout) throws InterruptedException {
      return waitForServerToStartOnPort(61616 + id, username, password, timeout);
   }

   public static boolean waitForServerToStartOnPort(int port, String username, String password, int timeout) throws InterruptedException {
      return waitForServerToStart("tcp://localhost:" + port, username, password, timeout);
   }

   public static boolean waitForServerToStart(String uri, long timeout) throws InterruptedException {
      return waitForServerToStart(uri, null, null, timeout);
   }

   public static boolean waitForServerToStart(String uri, String username, String password, long timeout) throws InterruptedException {
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

   public static void killServer(final Process server) throws Exception {
      killServer(server, false);
   }

   public static void killServer(final Process server, boolean forcibly) throws Exception {
      if (server != null) {
         System.out.println("**********************************");
         System.out.println("Killing server " + server);
         System.out.println("**********************************");
         if (forcibly) {
            server.destroyForcibly();
         } else {
            server.destroy();
         }
         server.waitFor();
         if (!forcibly) {
            Thread.sleep(1000);
         }
      }
   }

   public static int getServer(Connection connection) {
      ClientSession session = ((ActiveMQConnection) connection).getInitialSession();
      TransportConfiguration transportConfiguration = session.getSessionFactory().getConnectorConfiguration();
      String port = (String) transportConfiguration.getParams().get("port");
      return Integer.parseInt(port) - 61616;
   }

   public static Connection getServerConnection(int server, Connection... connections) {
      for (Connection connection : connections) {
         ClientSession session = ((ActiveMQConnection) connection).getInitialSession();
         TransportConfiguration transportConfiguration = session.getSessionFactory().getConnectorConfiguration();
         String port = (String) transportConfiguration.getParams().get("port");
         if (Integer.parseInt(port) == server + 61616) {
            return connection;
         }
      }
      return null;
   }

   /**
    * Redirect the input stream to a logger (as debug logs)
    */
   static class ProcessLogger extends Thread {

      private final InputStream is;

      private final String logName;

      private final boolean print;

      private final boolean sendToErr;

      private final Consumer<String> logCallback;

      ProcessLogger(final boolean print,
                    final InputStream is,
                    final String logName,
                    final boolean sendToErr,
                    final Consumer<String> logCallback) throws ClassNotFoundException {
         this.is = is;
         this.print = print;
         this.logName = logName;
         this.sendToErr = sendToErr;
         this.logCallback = logCallback;
         setDaemon(false);
      }

      @Override
      public void run() {
         try {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
               if (print) {
                  if (sendToErr) {
                     System.err.println(logName + "-err:" + line);
                  } else {
                     System.out.println(logName + "-out:" + line);
                  }
               }
               if (logCallback != null) {
                  logCallback.accept((sendToErr ? logName + "-err:" : logName + "-out:") + line);
               }
            }
         } catch (IOException e) {
            // ok, stream closed
         }
      }
   }
}
