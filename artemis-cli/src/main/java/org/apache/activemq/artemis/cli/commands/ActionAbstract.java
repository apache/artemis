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
import java.net.InetAddress;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.artemis.utils.uri.SchemaConstants;
import picocli.CommandLine.Option;

public abstract class ActionAbstract implements Action, Runnable {

   public static final String DEFAULT_BROKER_URL = "tcp://localhost:61616";

   public static final String DEFAULT_BROKER_ACCEPTOR = "artemis";

   @Option(names = "--verbose", description = "Print additional information.")
   public boolean verbose;

   // this could be changed by a test accessor for testing purposes.
   String brokerConfigurationFileName = "broker.xml";

   private String brokerInstance;

   private String brokerHome;

   private String brokerEtc;

   private URI brokerInstanceURI;

   private ActionContext actionContext;

   protected ActionContext getActionContext() {
      if (actionContext == null) {
         actionContext = ActionContext.system();
      }
      return actionContext;
   }

   @Override
   public boolean isVerbose() {
      return verbose;

   }

   @Override
   public void setHomeValues(File brokerHome, File brokerInstance, File etcFolder) {
      if (brokerHome != null) {
         this.brokerHome = brokerHome.getAbsolutePath();
      }
      if (brokerInstance != null) {
         this.brokerInstance = brokerInstance.getAbsolutePath();
      }
      if (etcFolder != null) {
         this.brokerEtc = etcFolder.getAbsolutePath();
      }
   }

   @Override
   public String getBrokerInstance() {
      if (brokerInstance == null) {
         /*
          * We use File URI for locating files.  The ARTEMIS_HOME variable is used to determine file paths. For Windows
          * the ARTEMIS_HOME variable will include back slashes (An invalid file URI character path separator). For this
          * reason we overwrite the ARTEMIS_HOME variable with backslashes replaced with forward slashes.
          */
         brokerInstance = System.getProperty("artemis.instance");
         if (brokerInstance != null) {
            brokerInstance = brokerInstance.replace("\\", "/");
            System.setProperty("artemis.instance", brokerInstance);
         }
      }
      return brokerInstance;
   }

   public String getBrokerURLInstance(String name) {
      String uri = null;
      if (getBrokerInstance() != null) {
         try {
            Configuration brokerConfiguration = getBrokerConfiguration();

            if (name == null) {
               name = DEFAULT_BROKER_ACCEPTOR;
            }

            uri = findTransportURI(name, brokerConfiguration.getAcceptorConfigurations());
            if (uri == null) {
               uri = findTransportURI(name, brokerConfiguration.getConnectorConfigurations().values());
            }

            return uri;
         } catch (Exception e) {
            getActionContext().out.print("Can not get the broker url instance: " + e.getMessage());
            e.printStackTrace(getActionContext().out);
         }
      }

      if (uri == null) {
         uri = DEFAULT_BROKER_URL;
      }

      return uri;
   }

   private String findTransportURI(String name, Collection<TransportConfiguration> configurationis) throws Exception {
      for (TransportConfiguration acceptorConfiguration: configurationis) {
         if (acceptorConfiguration.getName().equals(name)) {
            Map<String, Object> acceptorParams = acceptorConfiguration.getParams();
            String scheme = ConfigurationHelper.getStringProperty(TransportConstants.SCHEME_PROP_NAME, SchemaConstants.TCP, acceptorParams);
            String host = ConfigurationHelper.getStringProperty(TransportConstants.HOST_PROP_NAME, "localhost", acceptorParams);
            int port = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, 61616, acceptorParams);

            if (InetAddress.getByName(host).isAnyLocalAddress()) {
               host = "localhost";
            }

            return new URI(scheme, null, host, port, null, null, null).toString();
         }
      }
      return null;
   }


   protected Configuration getBrokerConfiguration() throws Exception {
      FileConfiguration fileConfiguration = new FileConfiguration();

      String brokerConfiguration = new File(new File(getBrokerEtc()), this.brokerConfigurationFileName).toURI().toASCIIString();
      FileDeploymentManager fileDeploymentManager = new FileDeploymentManager(brokerConfiguration);
      fileDeploymentManager.addDeployable(fileConfiguration);
      fileDeploymentManager.readConfiguration();

      return fileConfiguration;
   }


   public String getBrokerEtc() {
      if (brokerEtc == null) {
         brokerEtc = System.getProperty("artemis.instance.etc");
         if (brokerEtc != null) {
            brokerEtc = brokerEtc.replace("\\", "/");
         } else {
            brokerEtc = getBrokerInstance() + "/etc";
         }
      }
      return brokerEtc;
   }


   public URI getBrokerURIInstance() {

      if (brokerInstanceURI == null) {
         String instanceProperty = getBrokerInstance();

         File artemisInstance = null;
         if (artemisInstance == null && instanceProperty != null) {
            artemisInstance = new File(instanceProperty);
         }

         if (artemisInstance != null) {
            brokerInstanceURI = artemisInstance.toURI();
         }
      }
      return brokerInstanceURI;
   }


   @Override
   public String getBrokerHome() {
      if (brokerHome == null) {
         /*
          * We use File URI for locating files.  The ARTEMIS_HOME variable is used to determine file paths. For Windows
          * the ARTEMIS_HOME variable will include back slashes (An invalid file URI character path separator). For this
          * reason we overwrite the ARTEMIS_HOME variable with backslashes replaced with forward slashes.
          */
         brokerHome = System.getProperty("artemis.home");
         if (brokerHome != null) {
            brokerHome = brokerHome.replace("\\", "/");
            System.setProperty("artemis.home", brokerHome);
         }

         if (brokerHome == null) {
            // if still null we will try to improvise with "."
            brokerHome = ".";
         }
      }
      return brokerHome;
   }

   @Override
   public void run() {
      try {
         // this is used only by the Shell
         // When  using the CLI outside of the shell the execute(ActionContext) will be used instead.
         execute(getActionContext());
      } catch (Throwable e) {
         e.printStackTrace();
      } finally {
         done();
      }
   }

   @Override
   public void done() {
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      this.actionContext = context;
      ActionContext.setSystem(context);

      return null;
   }

}
