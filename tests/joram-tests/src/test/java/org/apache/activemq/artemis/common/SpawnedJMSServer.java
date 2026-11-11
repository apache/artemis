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
package org.apache.activemq.artemis.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.utils.FileUtil;

public class SpawnedJMSServer {

   public static ActiveMQServer server;

   // Using files may be useful for debugging (through print-data for instance)
   private static final boolean useFiles = false;

   public static void main(final String[] args) throws Exception {
      try {
         startServer();

         System.out.println("Server started, ready to start client test");

         // create the reader before printing OK so that if the test is quick
         // we will still capture the STOP message sent by the client
         InputStreamReader isr = new InputStreamReader(System.in);
         BufferedReader br = new BufferedReader(isr);

         System.out.println("OK");

         String line = br.readLine();
         if (line != null && "STOP".equals(line.trim())) {
            stopServer();
            System.out.println("Server stopped");
            System.exit(0);
         } else {
            // stop anyway but with an error status
            System.exit(1);
         }
      } catch (Throwable t) {
         t.printStackTrace();
         String allStack = t.getCause().getMessage() + "|";
         StackTraceElement[] stackTrace = t.getCause().getStackTrace();
         for (StackTraceElement stackTraceElement : stackTrace) {
            allStack += stackTraceElement.toString() + "|";
         }
         System.out.println(allStack);
         System.out.println("KO");
         System.exit(1);
      }
   }

   public static ActiveMQServer startServer() throws Exception {
      if (server == null) {
         Configuration config = new ConfigurationImpl().addAcceptorConfiguration("netty", "tcp://localhost:61616").setSecurityEnabled(false).addConnectorConfiguration("netty", "tcp://localhost:61616");
         File dataPlace = new File("./target/dataJoram");

         FileUtil.deleteDirectory(dataPlace);

         config.setJournalDirectory(new File(dataPlace, "./journal").getAbsolutePath()).
            setPagingDirectory(new File(dataPlace, "./paging").getAbsolutePath()).
            setLargeMessagesDirectory(new File(dataPlace, "./largemessages").getAbsolutePath()).
            setBindingsDirectory(new File(dataPlace, "./bindings").getAbsolutePath()).setPersistenceEnabled(true);

         // disable server persistence since JORAM tests do not restart server
         server = ActiveMQServers.newActiveMQServer(config, useFiles);
         // set DLA and expiry to avoid spamming the log with warnings
         server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("DLA")).setExpiryAddress(SimpleString.of("Expiry")));

         server.start();
      }
      return server;
   }

   public static void stopServer() throws Exception {
      if (server != null) {
         server.stop();
      }
      server = null;
   }




}
