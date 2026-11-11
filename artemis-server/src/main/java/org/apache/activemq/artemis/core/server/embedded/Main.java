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
package org.apache.activemq.artemis.core.server.embedded;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.LegacyJMSConfiguration;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static String workDir = "/app";
   private static volatile EmbeddedActiveMQ embeddedServer;

   public static void main(String[] args) throws Exception {

      if (args.length == 1) {
         workDir = args[0];
         logger.debug("User supplied work dir {}", workDir);
      }

      String propertiesConfigPath = "/config/," + workDir + "/etc/";
      if (args.length == 2) {
         propertiesConfigPath = args[1];
         logger.debug("User supplied properties config path {}", propertiesConfigPath);
      }

      FileConfiguration configuration = new FileConfiguration();

      String dataDir = workDir + "/data";
      configureDataDirectory(configuration, dataDir);

      File bringYourOwnXml = new File(workDir + "/etc/broker.xml");
      if (bringYourOwnXml.exists()) {
         logger.debug("byo config found {}", bringYourOwnXml);
         configuration = loadFromXmlFile(bringYourOwnXml, configuration);
      }

      embeddedServer = new EmbeddedActiveMQ();
      // look for properties files to augment configuration
      embeddedServer.setPropertiesResourcePath(propertiesConfigPath);
      embeddedServer.setConfiguration(configuration);
      embeddedServer.createActiveMQServer();

      final ActiveMQServer activeMQServer = embeddedServer.getActiveMQServer();
      final CountDownLatch serverStopped = new CountDownLatch(1);
      registerCallbackToTriggerLatchOnStopped(activeMQServer, serverStopped);
      exitWithErrorOnStartFailure(activeMQServer);
      addShutdownHookForServerStop(embeddedServer);

      logger.debug("starting server");
      embeddedServer.start();

      logger.debug("await server stop");
      serverStopped.await();
      embeddedServer = null;
   }

   private static void exitWithErrorOnStartFailure(ActiveMQServer activeMQServer) {
      activeMQServer.registerActivationFailureListener(exception -> {
         logger.error("server failed to start {}, exit(1) in thread", exception);
         new Thread("exit(1)-on-start-failure") {
            @Override
            public void run() {
               logger.error("exit(1)");
               Runtime.getRuntime().exit(1);
            }
         }.start();
      });
   }

   private static void registerCallbackToTriggerLatchOnStopped(ActiveMQServer activeMQServer, CountDownLatch serverStopped) {
      activeMQServer.registerActivateCallback(new ActivateCallback() {

         @Override
         public void stop(ActiveMQServer server) {
            logger.trace("server stop, state {}", server.getState());
            serverStopped.countDown();
         }

         @Override
         public void shutdown(ActiveMQServer server) {
            logger.trace("server shutdown, state {}", server.getState());
            serverStopped.countDown();
         }
      });
   }

   private static void addShutdownHookForServerStop(final EmbeddedActiveMQ server) {
      Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook") {
         @Override
         public void run() {
            try {
               logger.trace("stop via shutdown hook");
               server.stop();
            } catch (Exception ignored) {
               // we want to exit fast and silently
               logger.trace("Error on stop {}", ignored);
            }
         }
      });
   }

   public static FileConfiguration loadFromXmlFile(File bringYourOwnXml, FileConfiguration base) throws Exception {
      FileDeploymentManager deploymentManager = new FileDeploymentManager(bringYourOwnXml.toURI().toASCIIString());
      LegacyJMSConfiguration legacyJMSConfiguration = new LegacyJMSConfiguration(base);
      deploymentManager.addDeployable(base).addDeployable(legacyJMSConfiguration);
      deploymentManager.readConfiguration();
      return base;
   }

   public static void configureDataDirectory(ConfigurationImpl configuration, String dataDir) {
      // any provided value via xml config or properties will override
      configuration.setJournalDirectory(dataDir);
      // setting these gives a better log message, not necessary otherwise
      configuration.setBindingsDirectory(dataDir + "/bindings");
      configuration.setLargeMessagesDirectory(dataDir + "/largemessages");
      configuration.setPagingDirectory(dataDir + "/paging");
   }

   public static EmbeddedActiveMQ getEmbeddedServer() {
      return embeddedServer;
   }
}
