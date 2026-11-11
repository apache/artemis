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
package org.apache.activemq.artemis.core.config.impl;

import javax.management.MBeanServer;
import java.net.URL;
import java.util.Map;

import org.apache.activemq.artemis.core.deployers.Deployable;
import org.apache.activemq.artemis.core.deployers.impl.FileConfigurationParser;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.w3c.dom.Element;

/**
 * A {@code FileConfiguration} reads configuration values from a file.
 */
public final class FileConfiguration extends ConfigurationImpl implements Deployable {

   private static final long serialVersionUID = -4766689627675039596L;

   private static final String CONFIGURATION_SCHEMA_URL = "schema/artemis-configuration.xsd";

   private static final String CONFIGURATION_SCHEMA_ROOT_ELEMENT = "core";

   // For a bridge confirmations must be activated or send acknowledgments won't return
   public static final int DEFAULT_CONFIRMATION_WINDOW_SIZE = 1024 * 1024;

   private boolean parsed = false;

   @Override
   public void parse(Element config, URL url) throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();

      // We only want to validate AIO when starting the server and we don't want to do it when deploying
      // activemq-queues.xml which uses the same parser and XML format.
      parser.setValidateAIO(true);

      parser.parseMainConfig(config, this);

      setConfigurationUrl(url);

      parsed = true;
   }

   @Override
   public boolean isParsed() {
      return parsed;
   }

   @Override
   public String getRootElement() {
      return CONFIGURATION_SCHEMA_ROOT_ELEMENT;
   }

   @Override
   public void buildService(ActiveMQSecurityManager securityManager,
                            MBeanServer mBeanServer,
                            Map<String, Deployable> deployables,
                            Map<String, ActiveMQComponent> components, ActivateCallback activateCallback) {
      ActiveMQServerImpl activeMQServer = new ActiveMQServerImpl(this, mBeanServer, securityManager);
      if (activateCallback != null) {
         activeMQServer.registerActivateCallback(activateCallback);
      }
      components.put(getRootElement(), activeMQServer);
   }

   @Override
   public String getSchema() {
      return CONFIGURATION_SCHEMA_URL;
   }
}
