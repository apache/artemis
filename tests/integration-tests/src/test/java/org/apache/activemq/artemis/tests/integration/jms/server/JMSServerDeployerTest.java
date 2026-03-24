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
package org.apache.activemq.artemis.tests.integration.jms.server;

import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.Context;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.registry.JndiBindingRegistry;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.server.JMSServerManager;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq.artemis.tests.unit.util.InVMNamingContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JMSServerDeployerTest extends ActiveMQTestBase {


   private static final Logger log = org.jboss.logging.Logger.getLogger(JMSServerDeployerTest.class);


   private JMSServerManager jmsServer;

   private Context context;

   private Configuration config;


   @Test
   public void testDeployUnusualQueueNames() throws Exception {
      doTestDeployQueuesWithUnusualNames("queue.with.dots.in.name", "/myqueue");

      doTestDeployQueuesWithUnusualNames("queue with spaces in name", "/myqueue2");

      doTestDeployQueuesWithUnusualNames("queue/with/slashes/in/name", "/myqueue3");

      doTestDeployQueuesWithUnusualNames("queue\\with\\backslashes\\in\\name", "/myqueue4");

      doTestDeployQueuesWithUnusualNames("queue with # chars and * chars in name", "/myqueue5");
   }

   @Test
   public void testDeployUnusualTopicNames() throws Exception {
      doTestDeployTopicsWithUnusualNames("topic.with.dots.in.name", "/mytopic");

      doTestDeployTopicsWithUnusualNames("topic with spaces in name", "/mytopic2");

      doTestDeployTopicsWithUnusualNames("topic/with/slashes/in/name", "/mytopic3");

      doTestDeployTopicsWithUnusualNames("topic\\with\\backslashes\\in\\name", "/mytopic4");

      doTestDeployTopicsWithUnusualNames("topic with # chars and * chars in name", "/mytopic5");

      doTestDeployTopicsWithUnusualNames("jms.topic.myTopic", "/mytopic6", "myTopic");
   }

   private void doTestDeployQueuesWithUnusualNames(final String queueName, final String jndiName) throws Exception {
      jmsServer.createQueue(false, queueName, null, false, jndiName);

      Queue queue = (Queue) context.lookup(jndiName);
      Assert.assertNotNull(queue);
      Assert.assertEquals(queueName, queue.getQueueName());
   }

   private void doTestDeployTopicsWithUnusualNames(final String topicName, final String jndiName) throws Exception {
      jmsServer.createTopic(false, topicName, jndiName);

      Topic topic = (Topic) context.lookup(jndiName);
      Assert.assertNotNull(topic);
      Assert.assertEquals(topicName, topic.getTopicName());
   }

   private void doTestDeployTopicsWithUnusualNames(final String topicName, final String jndiName, final String jmsTopicName) throws Exception {
      jmsServer.createTopic(topicName, false, jmsTopicName, jndiName);

      Topic topic = (Topic) context.lookup(jndiName);
      Assert.assertNotNull(topic);
      Assert.assertEquals(jmsTopicName, topic.getTopicName());
   }



   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      DiscoveryGroupConfiguration dcg = new DiscoveryGroupConfiguration().setName("mygroup").setRefreshTimeout(5432).setDiscoveryInitialWaitTimeout(5432).setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(getUDPDiscoveryAddress()).setGroupPort(getUDPDiscoveryPort()).setLocalBindAddress("172.16.8.10"));

      config = createBasicConfig().addConnectorConfiguration("netty", new TransportConfiguration(NettyConnectorFactory.class.getName())).addDiscoveryGroupConfiguration("mygroup", dcg);

      ActiveMQServer server = createServer(false, config);

      jmsServer = new JMSServerManagerImpl(server);
      context = new InVMNamingContext();
      jmsServer.setRegistry(new JndiBindingRegistry(context));
      jmsServer.start();
   }



}
