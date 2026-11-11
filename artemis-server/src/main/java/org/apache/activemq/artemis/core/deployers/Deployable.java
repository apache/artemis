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
package org.apache.activemq.artemis.core.deployers;

import javax.management.MBeanServer;
import java.net.URL;
import java.util.Map;

import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.w3c.dom.Element;

/**
 * A Deployable is an object that can be configured via an xml configuration element in the main configuration file
 * "broker.xml" It holds all the information needed by the FileDeploymentManager to parse the configuration and build
 * the component
 */
public interface Deployable {

   /**
    * parse the element from the xml configuration
    */
   void parse(Element config, URL url) throws Exception;

   /**
    * has this Deployable been parsed
    */
   boolean isParsed();

   /**
    * The name of the root xml element for this Deployable, i.e. core or jms
    */
   String getRootElement();

   /**
    * The schema that should be used to validate the xml
    */
   String getSchema();

   /**
    * builds the service. The implementation should add a component to the components map passed in if it needs to.
    */
   void buildService(ActiveMQSecurityManager securityManager,
                     MBeanServer mBeanServer,
                     Map<String, Deployable> deployables,
                     Map<String, ActiveMQComponent> components, ActivateCallback activateCallback) throws Exception;

}
