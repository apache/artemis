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
package org.apache.activemq.artemis.jms.server.config.impl;

import javax.management.MBeanServer;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.deployers.Deployable;
import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.server.ActiveMQJMSServerLogger;
import org.apache.activemq.artemis.jms.server.config.JMSQueueConfiguration;
import org.apache.activemq.artemis.jms.server.config.TopicConfiguration;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.utils.XMLConfigurationUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.apache.activemq.artemis.core.config.impl.Validators.NO_CHECK;

public class FileJMSConfiguration extends JMSConfigurationImpl implements Deployable {

   private static final String CONFIGURATION_SCHEMA_URL = "schema/artemis-jms.xsd";

   private static final String CONFIGURATION_SCHEMA_ROOT_ELEMENT = "jms";

   private static final String NAME_ATTR = "name";

   private static final String QUEUE_NODE_NAME = "queue";

   private static final String QUEUE_SELECTOR_NODE_NAME = "selector";

   private static final String TOPIC_NODE_NAME = "topic";

   private static final String JMX_DOMAIN_NAME = "jmx-domain";

   private static final boolean DEFAULT_QUEUE_DURABILITY = true;

   @Override
   public void parse(Element config, URL url) throws Exception {
      parseConfiguration(config);
      setConfigurationUrl(url);
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
      ActiveMQServerImpl server = (ActiveMQServerImpl) components.get("core");
      components.put(CONFIGURATION_SCHEMA_ROOT_ELEMENT, new JMSServerManagerImpl(server, this));
   }

   @Override
   public String getSchema() {
      return CONFIGURATION_SCHEMA_URL;
   }

   /**
    * Parse the JMS Configuration XML
    */
   public void parseConfiguration(final Node rootnode) throws Exception {

      List<JMSQueueConfiguration> queues = new ArrayList<>();
      List<TopicConfiguration> topics = new ArrayList<>();

      Element e = (Element) rootnode;

      String[] elements = new String[]{QUEUE_NODE_NAME, TOPIC_NODE_NAME};
      for (String element : elements) {
         NodeList children = e.getElementsByTagName(element);
         for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            Node keyNode = node.getAttributes().getNamedItem(NAME_ATTR);
            if (keyNode == null) {
               ActiveMQJMSServerLogger.LOGGER.jmsConfigMissingKey(node);
               continue;
            }

            if (node.getNodeName().equals(TOPIC_NODE_NAME)) {
               topics.add(parseTopicConfiguration(node));
            } else if (node.getNodeName().equals(QUEUE_NODE_NAME)) {
               queues.add(parseQueueConfiguration(node));
            }
         }
      }

      String domain = XMLConfigurationUtil.getString(e, JMX_DOMAIN_NAME, ActiveMQDefaultConfiguration.getDefaultJmxDomain(), NO_CHECK);

      newConfig(queues, topics, domain);
   }

   /**
    * Parse the topic node as a TopicConfiguration object
    *
    * @return topic configuration
    */
   public static TopicConfiguration parseTopicConfiguration(final Node node) throws Exception {
      String topicName = node.getAttributes().getNamedItem(NAME_ATTR).getNodeValue();

      return newTopic(topicName);
   }

   /**
    * Parse the Queue Configuration node as a QueueConfiguration object
    *
    * @return jms queue configuration
    */
   public static JMSQueueConfiguration parseQueueConfiguration(final Node node) throws Exception {
      Element e = (Element) node;
      NamedNodeMap atts = node.getAttributes();
      String queueName = atts.getNamedItem(NAME_ATTR).getNodeValue();
      String selectorString = null;
      boolean durable = XMLConfigurationUtil.getBoolean(e, "durable", DEFAULT_QUEUE_DURABILITY);
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
         Node child = children.item(i);

         if (QUEUE_SELECTOR_NODE_NAME.equals(children.item(i).getNodeName())) {
            Node selectorNode = children.item(i);
            Node attNode = selectorNode.getAttributes().getNamedItem("string");
            selectorString = attNode.getNodeValue();
         }
      }

      return newQueue(queueName, selectorString, durable);
   }

   protected static TopicConfiguration newTopic(final String topicName) {
      return new TopicConfigurationImpl().setName(topicName);
   }

   protected static JMSQueueConfiguration newQueue(final String queueName,
                                                   final String selectorString,
                                                   final boolean durable) {
      return new JMSQueueConfigurationImpl().
         setName(queueName).
         setSelector(selectorString).
         setDurable(durable);
   }

   protected void newConfig(final List<JMSQueueConfiguration> queues,
                            final List<TopicConfiguration> topics,
                            String domain) {
      setQueueConfigurations(queues).setTopicConfigurations(topics).setDomain(domain);
   }
}
