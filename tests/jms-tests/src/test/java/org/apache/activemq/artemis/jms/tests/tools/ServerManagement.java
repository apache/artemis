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
package org.apache.activemq.artemis.jms.tests.tools;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.activemq.artemis.jms.tests.tools.container.InVMInitialContextFactory;
import org.apache.activemq.artemis.jms.tests.tools.container.LocalTestServer;
import org.apache.activemq.artemis.jms.tests.tools.container.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collection of static methods to use to start/stop and interact with the in-memory JMS server. It is also use to
 * start/stop a remote server.
 */
public class ServerManagement {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   // logging levels used by the remote client to forward log output on a remote server
   public static int FATAL = 0;

   public static int ERROR = 1;

   public static int WARN = 2;

   public static int INFO = 3;

   public static int DEBUG = 4;

   public static int TRACE = 5;

   public static final String DEFAULT_QUEUE_CONTEXT = "/queue";

   public static final String DEFAULT_TOPIC_CONTEXT = "/topic";


   private static List<Server> servers = new ArrayList<>();

   /**
    * Makes sure that a "hollow" TestServer (either local or remote, depending on the nature of the test), exists and
    * it's ready to be started.
    */
   public static synchronized Server create() throws Exception {
      return new LocalTestServer();
   }

   public static void start(final int i, final String config, final boolean clearDatabase) throws Exception {
      ServerManagement.start(i, config, clearDatabase, true);
   }

   /**
    * When this method correctly completes, the server (local or remote) is started and fully operational (the server
    * container and the server peer are created and started).
    */
   public static void start(final int i,
                            final String config,
                            final boolean clearDatabase,
                            final boolean startActiveMQServer) throws Exception {
      throw new IllegalStateException("Method to start a server is not implemented");
   }

   public static synchronized void kill(final int i) throws Exception {
      if (i == 0) {
         // Cannot kill server 0 if there are any other servers since it has the rmi registry in it
         for (int j = 1; j < ServerManagement.servers.size(); j++) {
            if (ServerManagement.servers.get(j) != null) {
               throw new IllegalStateException("Cannot kill server 0, since server[" + j + "] still exists");
            }
         }
      }

      if (i > ServerManagement.servers.size()) {
         logger.error("server {} has not been created or has already been killed, so it cannot be killed", i);
      } else {
         Server server = ServerManagement.servers.get(i);
         logger.debug("invoking kill() on server {}", i);
         try {
            server.kill();
         } catch (Throwable t) {
            // This is likely to throw an exception since the server dies before the response is received
         }

         logger.debug("Waiting for server to die");

         try {
            while (true) {
               server.ping();
               logger.debug("server {} still alive ...", i);
               Thread.sleep(100);
            }
         } catch (Throwable e) {
            // Ok
         }

         Thread.sleep(300);

         logger.debug("server {} killed and dead", i);
      }

   }

   public static Hashtable<String, String> getJNDIEnvironment(final int serverIndex) {
      return InVMInitialContextFactory.getJNDIEnvironment(serverIndex);
   }
}
