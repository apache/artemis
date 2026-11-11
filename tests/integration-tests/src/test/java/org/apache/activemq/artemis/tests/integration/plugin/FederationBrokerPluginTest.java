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
package org.apache.activemq.artemis.tests.integration.plugin;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerFederationPlugin;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.integration.federation.FederatedTestBase;
import org.apache.activemq.artemis.tests.integration.federation.FederatedTestUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CLOSE_FEDERATED_QUEUE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_FEDERATED_QUEUE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CLOSE_FEDERATED_QUEUE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_FEDERATED_QUEUE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.FEDERATED_ADDRESS_CONDITIONAL_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.FEDERATED_QUEUE_CONDITIONAL_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.FEDERATION_STREAM_STARTED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.FEDERATION_STREAM_STOPPED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FederationBrokerPluginTest extends FederatedTestBase {

   private final Map<String, AtomicInteger> methodCalls = new ConcurrentHashMap<>();
   private final MethodCalledVerifier verifier0 = new MethodCalledVerifier(methodCalls);

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      getServer(0).registerBrokerPlugin(verifier0);
   }

   @Test
   public void testFederationStreamStartStop() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      verifier0.validatePluginMethodsEquals(1, 5000, 500, FEDERATION_STREAM_STARTED);
      getServer(0).getFederationManager().stop();
      verifier0.validatePluginMethodsEquals(1, 5000, 500, FEDERATION_STREAM_STOPPED);

   }

   @Test
   public void testFederationStreamConsumerAddressUpstream() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      testFederationStreamConsumerAddress(address);
   }

   @Test
   public void testFederationStreamConsumerAddressDownstream() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressDownstreamFederationConfiguration(
         "server0", address, "server1");
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(1).getFederationManager().deploy();

      testFederationStreamConsumerAddress(address);
   }


   private void testFederationStreamConsumerAddress(String address) throws Exception {
      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         connection0.start();

         Session session0 = connection0.createSession();
         Session session1 = connection1.createSession();

         Topic topic0 = session0.createTopic(address);
         Topic topic1 = session1.createTopic(address);

         MessageConsumer consumer0 = session0.createConsumer(topic0);
         MessageProducer producer1 = session1.createProducer(topic1);

         assertTrue(Wait.waitFor(() -> getServer(1).getPostOffice().getBindingsForAddress(
            SimpleString.of(address)).getBindings().size() == 1, 5000, 500));

         verifier0.validatePluginMethodsEquals(1, 5000, 500, BEFORE_CREATE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CREATE_FEDERATED_QUEUE_CONSUMER, FEDERATED_ADDRESS_CONDITIONAL_CREATE_CONSUMER);
         verifier0.validatePluginMethodsEquals(0, 5000, 500, BEFORE_CLOSE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CLOSE_FEDERATED_QUEUE_CONSUMER);

         producer1.send(session1.createTextMessage("hello"));
         assertNotNull(consumer0.receive(5000));

         consumer0.close();

         verifier0.validatePluginMethodsEquals(1, 5000, 500, BEFORE_CLOSE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CLOSE_FEDERATED_QUEUE_CONSUMER, BEFORE_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED,
                                               AFTER_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED);
      }
   }

   @Test
   public void testFederationStreamConsumerQueueUpstream() throws Exception {
      String queueName = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createQueueUpstreamFederationConfiguration("server1", queueName);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      testFederationStreamConsumerQueue(queueName);
   }

   @Test
   public void testFederationStreamConsumerQueueDownstream() throws Exception {
      String queueName = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createQueueDownstreamFederationConfiguration(
         "server0", queueName, "server1");
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(1).getFederationManager().deploy();

      testFederationStreamConsumerQueue(queueName);
   }

   private void testFederationStreamConsumerQueue(String queueName) throws Exception {
      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection0.start();
         connection1.start();

         Session session0 = connection0.createSession();
         Session session1 = connection1.createSession();

         Queue queue0 = session0.createQueue(queueName);
         Queue queue1 = session1.createQueue(queueName);

         MessageProducer producer1 = session1.createProducer(queue1);
         producer1.send(session1.createTextMessage("hello"));

         MessageConsumer consumer0 = session0.createConsumer(queue0);
         assertNotNull(consumer0.receive(1000));

         verifier0.validatePluginMethodsEquals(1, 5000, 500, BEFORE_CREATE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CREATE_FEDERATED_QUEUE_CONSUMER, FEDERATED_QUEUE_CONDITIONAL_CREATE_CONSUMER);
         verifier0.validatePluginMethodsEquals(0, 5000, 500, BEFORE_CLOSE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CLOSE_FEDERATED_QUEUE_CONSUMER);

         consumer0.close();

         verifier0.validatePluginMethodsEquals(1, 5000, 500, BEFORE_CLOSE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CLOSE_FEDERATED_QUEUE_CONSUMER, BEFORE_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED,
                                               AFTER_FEDERATED_QUEUE_CONSUMER_MESSAGE_HANDLED);

      }
   }

   @Test
   public void testFederatedAddressConditional() throws Exception {
      String address = getName();

      getServer(0).registerBrokerPlugin(new ActiveMQServerFederationPlugin() {
         @Override
         public boolean federatedAddressConditionalCreateConsumer(org.apache.activemq.artemis.core.server.Queue queue) {
            //always return false for test
            return false;
         }
      });

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         connection0.start();

         Session session0 = connection0.createSession();
         Session session1 = connection1.createSession();

         Topic topic0 = session0.createTopic(address);
         Topic topic1 = session1.createTopic(address);

         MessageConsumer consumer0 = session0.createConsumer(topic0);
         MessageProducer producer1 = session1.createProducer(topic1);

         assertFalse(Wait.waitFor(() -> !getServer(1).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().isEmpty(), 2000, 500));

         verifier0.validatePluginMethodsEquals(1, 5000, 500, FEDERATED_ADDRESS_CONDITIONAL_CREATE_CONSUMER);
         verifier0.validatePluginMethodsEquals(0, 5000, 500, BEFORE_CREATE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CREATE_FEDERATED_QUEUE_CONSUMER);

         producer1.send(session1.createTextMessage("hello"));
         assertNull(consumer0.receive(1000));
         consumer0.close();

         verifier0.validatePluginMethodsEquals(0, 5000, 500, BEFORE_CLOSE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CLOSE_FEDERATED_QUEUE_CONSUMER);

      }
   }

   @Test
   public void testFederatedQueueConditional() throws Exception {
      String queueName = getName();

      getServer(0).registerBrokerPlugin(new ActiveMQServerFederationPlugin() {
         @Override
         public boolean federatedQueueConditionalCreateConsumer(ServerConsumer consumer) {
            //always return false for test
            return false;
         }
      });

      FederationConfiguration federationConfiguration = FederatedTestUtil.createQueueUpstreamFederationConfiguration("server1", queueName);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection0.start();
         connection1.start();

         Session session0 = connection0.createSession();
         Session session1 = connection1.createSession();

         Queue queue0 = session0.createQueue(queueName);
         Queue queue1 = session1.createQueue(queueName);

         MessageProducer producer1 = session1.createProducer(queue1);
         producer1.send(session1.createTextMessage("hello"));

         MessageConsumer consumer0 = session0.createConsumer(queue0);
         assertNull(consumer0.receive(1000));

         verifier0.validatePluginMethodsEquals(1, 5000, 500, FEDERATED_QUEUE_CONDITIONAL_CREATE_CONSUMER);
         verifier0.validatePluginMethodsEquals(0, 5000, 500, BEFORE_CREATE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CREATE_FEDERATED_QUEUE_CONSUMER);
         consumer0.close();

         verifier0.validatePluginMethodsEquals(0, 5000, 500, BEFORE_CLOSE_FEDERATED_QUEUE_CONSUMER,
                                               AFTER_CLOSE_FEDERATED_QUEUE_CONSUMER);

      }
   }

   protected ConnectionFactory getCF(int i) throws Exception {
      return new ActiveMQConnectionFactory("vm://" + i);
   }
}
