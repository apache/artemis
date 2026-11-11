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
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.deployers.Deployable;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.utils.XMLConfigurationUtil;
import org.apache.activemq.artemis.utils.XMLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LegacyJMSConfiguration implements Deployable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String CONFIGURATION_SCHEMA_URL = "schema/artemis-jms.xsd";

   private static final String CONFIGURATION_SCHEMA_ROOT_ELEMENT = "jms";

   private static final String NAME_ATTR = "name";

   private static final String QUEUE_NODE_NAME = "queue";

   private static final String QUEUE_SELECTOR_NODE_NAME = "selector";

   private static final String TOPIC_NODE_NAME = "topic";

   private static final boolean DEFAULT_QUEUE_DURABILITY = true;

   final Configuration configuration;


   public LegacyJMSConfiguration(Configuration configuration) {
      this.configuration = configuration;
   }


   @Override
   public void parse(Element config, URL url) throws Exception {
      parseConfiguration(config);
   }

   public Configuration getConfiguration() {
      return configuration;
   }

   @Override
   public boolean isParsed() {
      // always return false here so that the FileDeploymentManager will not invoke buildService()
      return false;
   }

   @Override
   public String getRootElement() {
      return CONFIGURATION_SCHEMA_ROOT_ELEMENT;
   }

   @Override
   public void buildService(ActiveMQSecurityManager securityManager,
                            MBeanServer mBeanServer,
                            Map<String, Deployable> deployables,
                            Map<String, ActiveMQComponent> components, ActivateCallback activateCallback) throws Exception {
   }

   @Override
   public String getSchema() {
      return CONFIGURATION_SCHEMA_URL;
   }


   public void parseConfiguration(final InputStream input) throws Exception {
      Element e = XMLUtil.streamToElement(input);
      // only parse elements from <jms>
      NodeList children = e.getElementsByTagName(CONFIGURATION_SCHEMA_ROOT_ELEMENT);
      if (children.getLength() > 0) {
         parseConfiguration(children.item(0));
      }
   }

   /**
    * Parse the JMS Configuration XML
    */
   public void parseConfiguration(final Node rootnode) throws Exception {

      Element e = (Element) rootnode;

      String[] elements = new String[]{QUEUE_NODE_NAME, TOPIC_NODE_NAME};
      for (String element : elements) {
         NodeList children = e.getElementsByTagName(element);
         for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            Node keyNode = node.getAttributes().getNamedItem(NAME_ATTR);
            if (keyNode == null) {
               logger.warn("Configuration missing jms key {}", node);
               continue;
            }

            if (node.getNodeName().equals(TOPIC_NODE_NAME)) {
               parseTopicConfiguration(node);
            } else if (node.getNodeName().equals(QUEUE_NODE_NAME)) {
               parseQueueConfiguration(node);
            }
         }
      }
   }

   /**
    * Parse the topic node as a TopicConfiguration object
    */
   public void parseTopicConfiguration(final Node node) throws Exception {
      String topicName = node.getAttributes().getNamedItem(NAME_ATTR).getNodeValue();
      configuration.addAddressConfiguration(new CoreAddressConfiguration()
                                               .setName(topicName)
                                               .addRoutingType(RoutingType.MULTICAST));
   }

   /**
    * Parse the Queue Configuration node as a QueueConfiguration object
    */
   public void parseQueueConfiguration(final Node node) throws Exception {
      Element e = (Element) node;
      NamedNodeMap atts = node.getAttributes();
      String queueName = atts.getNamedItem(NAME_ATTR).getNodeValue();
      String selectorString = null;
      boolean durable = XMLConfigurationUtil.getBoolean(e, "durable", DEFAULT_QUEUE_DURABILITY);
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if (QUEUE_SELECTOR_NODE_NAME.equals(child.getNodeName())) {
            Node selectorNode = child;
            Node attNode = selectorNode.getAttributes().getNamedItem("string");
            selectorString = attNode.getNodeValue();
         }
      }

      configuration.addAddressConfiguration(new CoreAddressConfiguration()
                                               .setName(queueName)
                                               .addRoutingType(RoutingType.ANYCAST)
                                               .addQueueConfiguration(QueueConfiguration.of(queueName)
                                                                         .setFilterString(selectorString)
                                                                         .setDurable(durable)
                                                                         .setRoutingType(RoutingType.ANYCAST)));
   }


}
