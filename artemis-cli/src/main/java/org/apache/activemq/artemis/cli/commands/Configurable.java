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
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.cli.factory.BrokerFactory;
import org.apache.activemq.artemis.cli.factory.jmx.ManagementFactory;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.dto.BrokerDTO;
import org.apache.activemq.artemis.dto.ManagementContextDTO;
import org.apache.activemq.artemis.jms.server.config.impl.FileJMSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Abstract class where we can replace the configuration in various places
 */
public abstract class Configurable extends ActionAbstract {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Parameters(description = "Broker Configuration URI. Default: xml:${ARTEMIS_INSTANCE}/etc/bootstrap.xml.", defaultValue = "")
   String configuration;

   @Option(names = "--broker", description = "Override the broker configuration from the bootstrap.xml.")
   String brokerConfig;

   private BrokerDTO brokerDTO = null;

   private FileConfiguration fileConfiguration;

   protected void treatError(Exception e, String group, String command) {
      logger.debug(e.getMessage(), e);
      getActionContext().err.println();
      getActionContext().err.println("Error:" + e.getMessage());
      getActionContext().err.println();

      if (!(e instanceof ActiveMQException)) {
         e.printStackTrace();
      }
   }

   protected FileConfiguration getFileConfiguration() throws Exception {
      if (fileConfiguration == null) {
         fileConfiguration = readConfiguration();
      }

      return fileConfiguration;
   }

   protected FileConfiguration readConfiguration() throws Exception {
      FileConfiguration fileConfiguration = new FileConfiguration();
      if (getBrokerInstance() == null) {
         final String defaultLocation = "./data";
         fileConfiguration = new FileConfiguration();
         // These will be the default places in case the file can't be loaded
         fileConfiguration.setBindingsDirectory(defaultLocation + "/bindings");
         fileConfiguration.setJournalDirectory(defaultLocation + "/journal");
         fileConfiguration.setLargeMessagesDirectory(defaultLocation + "/largemessages");
         fileConfiguration.setPagingDirectory(defaultLocation + "/paging");
         fileConfiguration.setBrokerInstance(new File("."));
      } else {
         FileJMSConfiguration jmsConfiguration = new FileJMSConfiguration();

         String serverConfiguration = getBrokerDTO().server.getConfigurationURI().toASCIIString();
         FileDeploymentManager fileDeploymentManager = new FileDeploymentManager(serverConfiguration);
         fileDeploymentManager.addDeployable(fileConfiguration).addDeployable(jmsConfiguration);
         fileDeploymentManager.readConfiguration();
         fileConfiguration.setBrokerInstance(new File(getBrokerInstance()));
      }

      return fileConfiguration;
   }

   protected BrokerDTO getBrokerDTO() throws Exception {
      if (brokerDTO == null) {
         getConfiguration();

         brokerDTO = BrokerFactory.createBrokerConfiguration(configuration, getBrokerHome(), getBrokerInstance(), getBrokerURIInstance());

         if (brokerConfig != null) {
            if (!brokerConfig.startsWith("file:")) {
               brokerConfig = "file:" + brokerConfig;
            }

            brokerDTO.server.configuration = brokerConfig;
         }
      }

      return brokerDTO;
   }

   protected ManagementContextDTO getManagementDTO() throws Exception {
      String configuration = getManagementConfiguration();
      return ManagementFactory.createJmxAclConfiguration(configuration, getBrokerHome(), getBrokerInstance(), getBrokerURIInstance());
   }

   protected String getConfiguration() {
      if (configuration == null || configuration.isEmpty()) {
         File xmlFile = new File(new File(getBrokerEtc()), "bootstrap.xml");
         configuration = "xml:" + xmlFile.toURI().toString().substring("file:".length());

         // To support Windows paths as explained above.
         configuration = configuration.replace("\\", "/");

         logger.debug("Using broker configuration: {}", configuration);
      }

      return configuration;
   }

   protected String getManagementConfiguration() {
      File xmlFile = new File(new File(getBrokerEtc()), "management.xml");
      String configuration = "xml:" + xmlFile.toURI().toString().substring("file:".length());

      // To support Windows paths as explained above.
      configuration = configuration.replace("\\", "/");

      return configuration;
   }

}
