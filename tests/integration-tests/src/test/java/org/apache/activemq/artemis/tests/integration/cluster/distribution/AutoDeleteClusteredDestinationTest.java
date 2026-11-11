/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.server.impl.QueueManagerImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class AutoDeleteClusteredDestinationTest extends ClusterTestBase {
   @Parameter(index = 0)
   public MessageLoadBalancingType loadBalancingType = MessageLoadBalancingType.OFF;

   @Parameters(name = "loadBalancingType = {0}")
   public static Iterable<? extends Object> loadBalancingType() {
      return Arrays.asList(new Object[][]{
         {MessageLoadBalancingType.OFF},
         {MessageLoadBalancingType.STRICT},
         {MessageLoadBalancingType.ON_DEMAND},
         {MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION}});
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());
   }

   @TestTemplate
   public void testConnectionLoadBalancingAnonRun() throws Exception {
      final String queueName = "queue";
      final String url0 = "tcp://localhost:61616?useTopologyForLoadBalancing=false";
      final String url1 = "tcp://localhost:61617?useTopologyForLoadBalancing=false";
      final SimpleString simpleName = SimpleString.of(queueName);
      final int messageCount = 10;
      final int TIMEOUT = 5000;

      CountDownLatch latch = new CountDownLatch(messageCount);

      MessageListener listener0 = message -> {
         latch.countDown();
      };

      MessageListener listener1 = message -> {
         latch.countDown();
      };

      setupClusterConnection("cluster0", queueName, loadBalancingType, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", queueName, loadBalancingType, 1, isNetty(), 1, 0);

      startServers(0, 1);
      waitForServerToStart(servers[0]);
      waitForServerToStart(servers[1]);

      AddressSettings settings = new AddressSettings().setRedistributionDelay(0).setAutoCreateAddresses(true).setAutoCreateQueues(true).setAutoDeleteQueues(true);

      servers[0].getAddressSettingsRepository().addMatch(queueName, settings);
      servers[1].getAddressSettingsRepository().addMatch(queueName, settings);

      try (
         ActiveMQConnectionFactory connectionFactory0 = new ActiveMQConnectionFactory(url0);
         ActiveMQConnectionFactory connectionFactory1 = new ActiveMQConnectionFactory(url1);
         Connection connection0 = connectionFactory0.createConnection();
         Connection connection1 = connectionFactory1.createConnection();
         Session consumerSession0 = connection0.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session consumerSession1 = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session producerSession0 = connection0.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

         Queue queue = consumerSession0.createQueue(queueName);
         MessageProducer producer0 = producerSession0.createProducer(queue);
         MessageConsumer consumer0 = consumerSession0.createConsumer(queue);
         MessageConsumer consumer1 = consumerSession1.createConsumer(queue);

         consumer0.setMessageListener(listener0);
         consumer1.setMessageListener(listener1);
         connection0.start();
         connection1.start();

         for (int i = 0; i < messageCount; i++) {
            producer0.send(producerSession0.createTextMessage("Message"));

            if (i == 2) {
               QueueImpl serverQueue = (QueueImpl) servers[0].locateQueue(simpleName);
               Wait.assertTrue(() -> serverQueue.getMessageCount() == 0, TIMEOUT, 100);
               consumer0.close();
               //Trigger an auto-delete to not have to wait
               QueueManagerImpl.performAutoDeleteQueue(servers[0], serverQueue);
               Wait.assertTrue(() -> servers[0].getPostOffice().getAddressInfo(simpleName).getBindingRemovedTimestamp() != -1, TIMEOUT, 100);
            }

            if (i == 6) {
               consumer0 = consumerSession0.createConsumer(queue);
               consumer0.setMessageListener(listener0);
               QueueImpl serverQueue = (QueueImpl) servers[0].locateQueue(simpleName);
               Wait.assertTrue(() -> serverQueue.getConsumerCount() == 1, TIMEOUT, 100);
            }
         }

         assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

      }
   }

   protected boolean isNetty() {
      return true;
   }

   @Override
   protected boolean isFileStorage() {
      return false;
   }

}
