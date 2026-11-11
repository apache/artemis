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
package org.apache.activemq.artemis.cli.commands;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.cli.Artemis;
import org.apache.activemq.artemis.cli.commands.tools.LockAbstract;
import org.apache.activemq.artemis.cli.factory.BrokerFactory;
import org.apache.activemq.artemis.cli.factory.jmx.ManagementFactory;
import org.apache.activemq.artemis.cli.factory.security.SecurityManagerFactory;
import org.apache.activemq.artemis.components.ExternalComponent;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.management.ManagementContext;
import org.apache.activemq.artemis.dto.BrokerDTO;
import org.apache.activemq.artemis.dto.ComponentDTO;
import org.apache.activemq.artemis.dto.ManagementContextDTO;
import org.apache.activemq.artemis.integration.Broker;
import org.apache.activemq.artemis.integration.bootstrap.ActiveMQBootstrapLogger;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "run", description = "Run the broker.")
public class Run extends LockAbstract {

   @Option(names = "--allow-kill", description = "This will allow the server to kill itself. Useful for tests (e.g. failover tests).")
   boolean allowKill;

   @Option(names = "--properties", description = "URL to a properties file that is applied to the server's configuration.")
   String properties;

   private static boolean embedded = false;

   public static final ReusableLatch latchRunning = new ReusableLatch(0);

   private ManagementContext managementContext;

   private Timer shutdownTimer;

   /**
    * This will disable the System.exit at the end of the server.stop, as that means there are other things happening on
    * the same VM.
    */
   public static void setEmbedded(boolean embedded) {
      Run.embedded = true;
   }

   public static boolean getEmbedded() {
      return Run.embedded;
   }

   private Broker server;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      verifyOlderLogging(new File(getBrokerEtc()));

      AtomicReference<Throwable> serverActivationFailed = new AtomicReference<>();
      try {
         BrokerDTO broker = getBrokerDTO();
         ActiveMQSecurityManager securityManager = SecurityManagerFactory.create(broker.security);
         ManagementContextDTO managementDTO = getManagementDTO();
         managementContext = ManagementFactory.create(managementDTO, securityManager);

         Artemis.printBanner(getActionContext().out);

         addShutdownHook(broker.server.getConfigurationFile().getParentFile());

         ActivateCallback activateCallback = new ActivateCallback() {
            @Override
            public void preActivate() {
               try {
                  managementContext.start();
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.unableStartManagementContext(e);
                  return;
               }
               try {
                  server.getServer().getManagementService().registerHawtioSecurity(managementContext.getArtemisMBeanServerGuard());
               } catch (Throwable e) {
                  ActiveMQServerLogger.LOGGER.unableToDeployHawtioMBean(e);
               }
            }

            @Override
            public void deActivate() {
               try {
                  server.getServer().getManagementService().unregisterHawtioSecurity();
               } catch (Exception e) {
                  //ok to ignore
               }
            }
         };

         server = BrokerFactory.createServer(broker.server, securityManager, activateCallback);

         server.createComponents();
         server.getServer().registerActivationFailureListener(exception -> serverActivationFailed.set(exception));

         if (properties == null) {
            File propertiesFileFromEtc = new File(getBrokerEtc(), ActiveMQDefaultConfiguration.BROKER_PROPERTIES_SYSTEM_PROPERTY_NAME);
            if (propertiesFileFromEtc.exists()) {
               properties = propertiesFileFromEtc.getAbsolutePath();
            }
         }
         server.getServer().setProperties(properties);
         server.start();
         server.getServer().addExternalComponent(managementContext, false);

         if (broker.web != null) {
            broker.components.add(broker.web);
         }

         String systemWebPropertyPrefix = ActiveMQDefaultConfiguration.getDefaultSystemWebPropertyPrefix();
         server.getServer().getConfiguration().parsePrefixedProperties(broker.web,
            "system-" + systemWebPropertyPrefix, System.getProperties(), systemWebPropertyPrefix);

         for (ComponentDTO componentDTO : broker.components) {
            ExternalComponent component = (ExternalComponent) ClassloadingUtil.getInstanceWithTypeCheck(componentDTO.componentClassName, ExternalComponent.class, this.getClass().getClassLoader());
            component.configure(componentDTO, getBrokerInstance(), getBrokerHome());
            server.getServer().addExternalComponent(component, true);
            assert component.isStarted();
         }
      } catch (Throwable t) {
         t.printStackTrace();
         serverActivationFailed.set(t);
         latchRunning.countDown();
      }

      if (serverActivationFailed.get() != null) {
         stop();
         return serverActivationFailed.get();
      }

      return new Pair<>(managementContext, server.getServer());
   }

   /**
    * Add a simple shutdown hook to stop the server.
    */
   private void addShutdownHook(File configurationDir) {

      latchRunning.countUp();
      final File file = new File(configurationDir, "STOP_ME");
      if (file.exists()) {
         if (!file.delete()) {
            ActiveMQBootstrapLogger.LOGGER.errorDeletingFile(file.getAbsolutePath());
         }
      }
      final File fileKill = new File(configurationDir, "KILL_ME");
      if (fileKill.exists()) {
         if (!fileKill.delete()) {
            ActiveMQBootstrapLogger.LOGGER.errorDeletingFile(fileKill.getAbsolutePath());
         }
      }

      shutdownTimer = new Timer("activemq-shutdown-timer", true);
      shutdownTimer.scheduleAtFixedRate(new TimerTask() {
         @Override
         public void run() {
            if (allowKill && fileKill.exists()) {
               try {
                  getActionContext().err.println("Halting by user request");
                  fileKill.delete();
               } catch (Throwable ignored) {
               }
               Runtime.getRuntime().halt(0);
            }
            if (file.exists()) {
               try {
                  stop();
                  shutdownTimer.cancel();
               } finally {
                  getActionContext().out.println("Server stopped!");
                  getActionContext().out.flush();
                  latchRunning.countDown();
                  if (!embedded) {
                     Runtime.getRuntime().exit(0);
                  }
               }
            }
         }
      }, 500, 500);

      Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook") {
         @Override
         public void run() {
            Run.this.stop();
         }
      });

   }

   protected void stop() {
      try {
         if (server != null) {
            server.stop(true);
         }
         if (managementContext != null) {
            managementContext.stop();
         }
         if (shutdownTimer != null) {
            shutdownTimer.cancel();
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }


   public void verifyOlderLogging(File etc) {
      File newLogging = new File(etc, Create.ETC_LOG4J2_PROPERTIES);
      File oldLogging = new File(etc, Upgrade.OLD_LOGGING_PROPERTIES);

      if (oldLogging.exists() && !newLogging.exists()) {
         getActionContext().out.println("******************************************************************************************************************************************************************************");
         getActionContext().out.println("Your system has the older logging file " + Upgrade.OLD_LOGGING_PROPERTIES + ", but not the new " + Create.ETC_LOG4J2_PROPERTIES);
         getActionContext().out.println("It appears you did not complete the migration on this artemis instance properly. Please check all the settings or run the './artemis upgrade' command from the new artemis home");
         getActionContext().out.println("******************************************************************************************************************************************************************************");
      }
   }

}
