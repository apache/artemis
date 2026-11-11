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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.jms.server.config.JMSConfiguration;
import org.apache.activemq.artemis.jms.server.config.JMSQueueConfiguration;
import org.apache.activemq.artemis.jms.server.config.TopicConfiguration;

public class JMSConfigurationImpl implements JMSConfiguration {

   private List<ConnectionFactoryConfiguration> connectionFactoryConfigurations = new ArrayList<>();

   private List<JMSQueueConfiguration> queueConfigurations = new ArrayList<>();

   private List<TopicConfiguration> topicConfigurations = new ArrayList<>();

   private String domain = ActiveMQDefaultConfiguration.getDefaultJmxDomain();

   private URL configurationUrl;

   // JMSConfiguration implementation -------------------------------

   public JMSConfigurationImpl() {
   }

   @Override
   public List<ConnectionFactoryConfiguration> getConnectionFactoryConfigurations() {
      return connectionFactoryConfigurations;
   }

   @Override
   public JMSConfigurationImpl setConnectionFactoryConfigurations(List<ConnectionFactoryConfiguration> connectionFactoryConfigurations) {
      this.connectionFactoryConfigurations = connectionFactoryConfigurations;
      return this;
   }

   @Override
   public List<JMSQueueConfiguration> getQueueConfigurations() {
      return queueConfigurations;
   }

   @Override
   public JMSConfigurationImpl setQueueConfigurations(List<JMSQueueConfiguration> queueConfigurations) {
      this.queueConfigurations = queueConfigurations;
      return this;
   }

   @Override
   public List<TopicConfiguration> getTopicConfigurations() {
      return topicConfigurations;
   }

   @Override
   public JMSConfigurationImpl setTopicConfigurations(List<TopicConfiguration> topicConfigurations) {
      this.topicConfigurations = topicConfigurations;
      return this;
   }

   @Override
   public String getDomain() {
      return domain;
   }

   @Override
   public JMSConfigurationImpl setDomain(final String domain) {
      this.domain = domain;
      return this;
   }

   @Override
   public URL getConfigurationUrl() {
      return configurationUrl;
   }

   @Override
   public JMSConfiguration setConfigurationUrl(URL configurationUrl) {
      this.configurationUrl = configurationUrl;
      return this;
   }
}
