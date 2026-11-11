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
package org.apache.activemq.artemis.tests.integration.federation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.Collections;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationUpstreamConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FederatedQueuePullConsumerTest extends FederatedTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      getServer(0).getConfiguration().addConnectorConfiguration("server-pull-1", "tcp://localhost:" + 61617 + "?consumerWindowSize=0;ackBatchSize=10");
   }

   @Override
   protected boolean isNetty() {
      // such that url params can be used for the server-pull-1 connector, vm urls don't propagate url params!
      return true;
   }

   @Override
   protected void configureQueues(ActiveMQServer server) throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoCreateAddresses(false).setAutoCreateQueues(false)
         .setDefaultConsumerWindowSize(20 * 300));
      createSimpleQueue(server, getName());
   }

   protected ConnectionFactory getCF(int i) throws Exception {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://" + i);
      // pull consumers to allow deterministic message consumption
      factory.setConsumerWindowSize(0);
      return factory;
   }

   @Test
   public void testAddressFederatedConfiguredWithPullQueueConsumerEnabledNotAnOption() throws Exception {
      String connector = "server-pull-1";

      getServer(0).getAddressSettingsRepository().getMatch("#").setAutoCreateAddresses(true).setAutoCreateQueues(true);
      getServer(1).getAddressSettingsRepository().getMatch("#").setAutoCreateAddresses(true).setAutoCreateQueues(true);

      getServer(0).addAddressInfo(new AddressInfo(SimpleString.of("source"), RoutingType.MULTICAST));
      getServer(1).addAddressInfo(new AddressInfo(SimpleString.of("source"), RoutingType.MULTICAST));

      getServer(0).getConfiguration().getFederationConfigurations().add(new FederationConfiguration().setName("default").addFederationPolicy(new FederationAddressPolicyConfiguration().setName("myAddressPolicy").addInclude(new FederationAddressPolicyConfiguration.Matcher().setAddressMatch("#"))).addUpstreamConfiguration(new FederationUpstreamConfiguration().setName("server1-upstream").addPolicyRef("myAddressPolicy").setStaticConnectors(Collections.singletonList(connector))));

      getServer(0).getFederationManager().deploy();

      final ConnectionFactory cf1 = getCF(0);
      final ConnectionFactory cf2 = getCF(1);

      try (Connection consumer1Connection = cf1.createConnection(); Connection producerConnection = cf2.createConnection()) {
         consumer1Connection.start();
         final Session session1 = consumer1Connection.createSession();
         final Topic topic1 = session1.createTopic("source");
         final MessageConsumer consumer1 = session1.createConsumer(topic1);

         // Remote
         final Session session2 = producerConnection.createSession();
         final Topic topic2 = session2.createTopic("source");
         final MessageProducer producer = session2.createProducer(topic2);

         producer.send(session2.createTextMessage("hello"));

         // no federation of this address
         // consumer visible on local
         assertTrue(waitForBindings(getServer(0), "source", true, 1, 1, 1000));
         // federation consumer not visible on remote
         assertFalse(waitForBindings(getServer(1), "source", true, 1, 1, 100));
      }
   }

   @Test
   public void testFederatedQueuePullFromUpstream() throws Exception {
      String queueName = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createQueueUpstreamFederationConfiguration("server-pull-1", queueName);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      testFederatedQueuePullFromUpstream(queueName);
   }

   @Test
   public void testMultipleFederatedQueueRemoteConsumersUpstream() throws Exception {
      String connector = "server-pull-1";

      getServer(0).getAddressSettingsRepository().getMatch("#").setAutoCreateAddresses(true).setAutoCreateQueues(true);
      getServer(1).getAddressSettingsRepository().getMatch("#").setAutoCreateAddresses(true).setAutoCreateQueues(true);

      getServer(1).createQueue(QueueConfiguration.of("Test.Q.1").setRoutingType(RoutingType.ANYCAST));
      getServer(1).createQueue(QueueConfiguration.of("Test.Q.2").setRoutingType(RoutingType.ANYCAST));

      getServer(0).getConfiguration().getFederationConfigurations().add(new FederationConfiguration()
                                                                           .setName("default")
                                                                           .addFederationPolicy(new FederationQueuePolicyConfiguration()
                                                                                                   .setName("myQueuePolicy")
                                                                                                   .addInclude(new FederationQueuePolicyConfiguration.Matcher()
                                                                                                                  .setQueueMatch("#")
                                                                                                                  .setAddressMatch("Test.#")))
                                                                           .addUpstreamConfiguration(new FederationUpstreamConfiguration()
                                                                                                        .setName("server1-upstream")
                                                                                                        .addPolicyRef("myQueuePolicy")
                                                                                                        .setStaticConnectors(Collections.singletonList(connector))));
      getServer(0).getFederationManager().deploy();

      ConnectionFactory cf1 = getCF(0);
      ConnectionFactory cf2 = getCF(0);
      ConnectionFactory cf3 = getCF(1);
      try (Connection consumer1Connection = cf1.createConnection();
           Connection consumer2Connection = cf2.createConnection();
           Connection producerConnection = cf3.createConnection()) {
         consumer1Connection.start();
         Session session1 = consumer1Connection.createSession();
         Queue queue1 = session1.createQueue("Test.Q.1");
         MessageConsumer consumer1 = session1.createConsumer(queue1);

         consumer2Connection.start();
         Session session2 = consumer2Connection.createSession();
         Queue queue2 = session2.createQueue("Test.Q.2");
         MessageConsumer consumer2 = session2.createConsumer(queue2);

         Session session3 = producerConnection.createSession();
         MessageProducer producer = session3.createProducer(queue2);
         producer.send(session3.createTextMessage("hello"));

         assertNotNull(consumer2.receive(1000));

         consumer1Connection.close();

         producer.send(session3.createTextMessage("hello"));

         assertNotNull(consumer2.receive(1000));
      }
   }

   private void testFederatedQueuePullFromUpstream(final String queueName) throws Exception {

      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         Session session1 = connection1.createSession();
         Queue queue1 = session1.createQueue(queueName);
         MessageProducer producer = session1.createProducer(queue1);
         producer.send(session1.createTextMessage("1"));

         connection0.start();
         Session session0 = connection0.createSession();
         Queue queue0 = session0.createQueue(queueName);
         MessageProducer producer0 = session0.createProducer(queue1);
         producer0.send(session0.createTextMessage("0"));

         // no consumer upstream, messages locally, no pull
         MessageConsumer consumer0 = session0.createConsumer(queue0);

         // verify federated
         waitForBindings(getServer(1), queueName, true, 1, 1, 2000);

         // verify no federation of messages
         Wait.assertEquals(1, () -> getMessageCount(getServer(0), queueName), 2000, 100);
         Wait.assertEquals(1, () -> getMessageCount(getServer(1), queueName), 2000, 100);

         // drain local queue
         assertNotNull(consumer0.receive(1000));

         // no messages locally, expect message federation now of a batch
         // 4s b/c local queue size check is in seconds
         assertNotNull(consumer0.receive(4000));

         // verify all messages consumed
         Wait.assertEquals(0L, () -> getMessageCount(getServer(0), queueName), 2000, 100);
         Wait.assertEquals(0L, () -> getMessageCount(getServer(1), queueName), 2000, 100);

         assertNull(consumer0.receiveNoWait());

         // verify batch end
         final int mumMessages = 150;
         for (int i = 0; i < mumMessages; i++) {
            producer.send(session1.createTextMessage("1-" + i));
         }

         // upstream has most
         Wait.assertTrue(() -> getMessageCount(getServer(1), queueName) > 100, 2000, 200);
         Wait.assertTrue(() -> getMessageCount(getServer(1), queueName) < mumMessages, 2000, 200);

         // only a batch has been federated
         Wait.assertTrue(() -> getMessageCount(getServer(0), queueName) > 10, 2000, 100);
         Wait.assertTrue(() -> getMessageCount(getServer(0), queueName) < 100, 2000, 100);

         // verify all available
         for (int i = 0; i < mumMessages; i++) {
            assertNotNull(consumer0.receive(4000));
         }

         assertNull(consumer0.receiveNoWait());
         consumer0.close();
      }
   }
}
