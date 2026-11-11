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
package org.apache.activemq.artemis.tests.integration.jms.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.jms.server.config.JMSQueueConfiguration;
import org.apache.activemq.artemis.jms.server.config.TopicConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.FileJMSConfiguration;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class JMSServerConfigParserTest extends ActiveMQTestBase {

   @Test
   public void testParsing() throws Exception {
      Configuration config = createDefaultInVMConfig()
         // anything so the parsing will work
         .addConnectorConfiguration("netty", new TransportConfiguration());

      String conf = "activemq-jms-for-JMSServerDeployerTest.xml";

      FileJMSConfiguration jmsconfig = new FileJMSConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(conf);
      deploymentManager.addDeployable(jmsconfig);
      deploymentManager.readConfiguration();

      assertEquals(1, jmsconfig.getQueueConfigurations().size());

      JMSQueueConfiguration queueConfig = jmsconfig.getQueueConfigurations().get(0);
      assertEquals("fullConfigurationQueue", queueConfig.getName());

      assertEquals(1, jmsconfig.getTopicConfigurations().size());
      TopicConfiguration topicConfig = jmsconfig.getTopicConfigurations().get(0);
      assertEquals("fullConfigurationTopic", topicConfig.getName());

   }

}
