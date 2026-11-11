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
package org.apache.activemq.artemis.core.config;

import javax.management.MBeanServer;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.activemq.artemis.core.deployers.Deployable;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.utils.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * ised to build a set of ActiveMQComponents from a set of Deployables pulled out of the configuration file
 */
public class FileDeploymentManager {

   private static final String DEFAULT_CONFIGURATION_URL = "broker.xml";

   private final String configurationUrl;

   Map<String, Deployable> deployables = new LinkedHashMap<>();

   public FileDeploymentManager() {
      this.configurationUrl = DEFAULT_CONFIGURATION_URL;
   }

   public FileDeploymentManager(String configurationUrl) {
      this.configurationUrl = configurationUrl;
   }

   /**
    * Parse a set of configuration with the {@link Deployable}s that were given.
    */
   public void readConfiguration() throws Exception {
      URL url;

      url = Thread.currentThread().getContextClassLoader().getResource(configurationUrl);

      if (url == null) {
         // trying a different classloader now
         url = getClass().getClassLoader().getResource(configurationUrl);
      }

      if (url == null) {
         // The URL is outside of the classloader. Trying a pure url now
         url = new URL(configurationUrl);
      }

      Element e = XMLUtil.urlToElement(url);

      //iterate around all the deployables
      for (Deployable deployable : deployables.values()) {
         String root = deployable.getRootElement();
         NodeList children = e.getElementsByTagName(root);
         //if the root element exists then parse it
         if (root != null && children.getLength() > 0) {
            Node item = children.item(0);
            XMLUtil.validate(item, deployable.getSchema());
            deployable.parse((Element) item, url);
         }
      }
   }

   /**
    * Build a set of {@link ActiveMQComponent}s from the {@link Deployable}s configured
    */
   public Map<String, ActiveMQComponent> buildService(ActiveMQSecurityManager securityManager,
                                                      MBeanServer mBeanServer, ActivateCallback activateCallback) throws Exception {
      Map<String, ActiveMQComponent> components = new HashMap<>();
      for (Deployable deployable : deployables.values()) {
         // if the deployable was parsed then build the service
         if (deployable.isParsed()) {
            deployable.buildService(securityManager, mBeanServer, deployables, components, activateCallback);
         }
      }
      return components;
   }

   /**
    * Add a {@link Deployable} to be configured
    */
   public FileDeploymentManager addDeployable(Deployable deployable) {
      deployables.put(deployable.getRootElement(), deployable);
      return this;
   }
}
