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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationAddressPolicyConfiguration;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FederatedAddressTest extends FederatedTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   protected ConnectionFactory getCF(int i) throws Exception {
      return new ActiveMQConnectionFactory("vm://" + i);
   }

   @Test
   public void testDownstreamFederatedAddressReplication() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressDownstreamFederationConfiguration("server1", address,
          getServer(0).getConfiguration().getTransportConfigurations("server0")[0]);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      FederationConfiguration federationConfiguration2 = FederatedTestUtil.createAddressDownstreamFederationConfiguration("server0", address,
          getServer(1).getConfiguration().getTransportConfigurations("server1")[0]);
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration2);
      getServer(1).getFederationManager().deploy();

      testFederatedAddressReplication(address);
   }

   @Test
   public void testDownstreamFederatedAddressReplicationRef() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressDownstreamFederationConfiguration("server1", address,
         "server0");
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      FederationConfiguration federationConfiguration2 = FederatedTestUtil.createAddressDownstreamFederationConfiguration("server0", address,
          "server1");
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration2);
      getServer(1).getFederationManager().deploy();

      testFederatedAddressReplication(address);
   }

   @Test
   public void testDownstreamFederatedAddressReplicationRefOneWay() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration2 = FederatedTestUtil.createAddressDownstreamFederationConfiguration("server0", address,
          "server1");
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration2);
      getServer(1).getFederationManager().deploy();

      testFederatedAddressReplication(address);
   }

   @Test
   public void testUpstreamFederatedAddressReplication() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      FederationConfiguration federationConfiguration2 = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server0", address);
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration2);
      getServer(1).getFederationManager().deploy();

      testFederatedAddressReplication(address);
   }

   @Test
   public void testDownstreamFederatedAddressReplicationRefOneWayTransformer() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration2 = FederatedTestUtil.createAddressDownstreamFederationConfiguration("server0", address, "server1");
      FederatedTestUtil.addAddressTransformerConfiguration(federationConfiguration2, address);
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration2);
      getServer(1).getFederationManager().deploy();

      verifyTransformer(address);
   }

   private void verifyTransformer(String address) throws Exception {
      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         connection0.start();

         Session session1 = connection1.createSession();
         Topic topic1 = session1.createTopic(address);
         MessageProducer producer1 = session1.createProducer(topic1);

         Session session0 = connection0.createSession();
         Topic topic0 = session0.createTopic(address);
         MessageConsumer consumer0 = session0.createConsumer(topic0);

         assertTrue(Wait.waitFor(() -> getServer(1).getPostOffice().getBindingsForAddress(
            SimpleString.of(address)).getBindings().size() == 1));

         producer1.send(session1.createTextMessage("hello"));
         Message message = consumer0.receive(1000);
         assertNotNull(message);
         assertTrue(message.getBooleanProperty(FederatedQueueTest.TestTransformer.TEST_PROPERTY));
      }
   }

   @Test
   public void testUpstreamFederatedAddressReplicationOneWay() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      testFederatedAddressReplication(address);
   }

   @Test
   public void testUpstreamFederatedAddressReplicationOneWayTransformer() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
      FederatedTestUtil.addAddressTransformerConfiguration(federationConfiguration, address);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      verifyTransformer(address);
   }

   /**
    * Test diverts for downstream configurations
    */
   //Test creating address first followed by divert
   //Test creating divert before consumer
   //Test destroy divert at end
   @Test
   public void testDownstreamDivertAddressFirstAndDivertFirstDestroyDivert() throws Exception {
      testFederatedAddressDivert(true, true, true, true);
   }

   //Test creating address first followed by divert
   //Test creating divert before consumer
   //Test destroy queue at end
   @Test
   public void testDownstreamDivertAddressFirstAndDivertFirstDestroyQueue() throws Exception {
      testFederatedAddressDivert(true, true, true, false);
   }

   //Test creating divert first followed by address
   //Test creating divert before consumer
   //Test destroy divert at end
   @Test
   public void testDownstreamDivertAddressSecondDivertFirstDestroyDivert() throws Exception {
      testFederatedAddressDivert(true, false, true, true);
   }

   //Test creating divert first followed by address
   //Test creating divert before consumer
   //Test destroy divert at end
   @Test
   public void testDownstreamDivertAddressSecondDivertFirstDestroyQueue() throws Exception {
      testFederatedAddressDivert(true, false, true, false);
   }

   //Test creating address first followed by divert
   //Test creating consumer before divert
   //Test destroy divert at end
   @Test
   public void testDownstreamDivertAddressFirstDivertSecondDestroyDivert() throws Exception {
      testFederatedAddressDivert(true, true, false, true);
   }

   //Test creating address first followed by divert
   //Test creating consumer before divert
   //Test destroy queue at end
   @Test
   public void testDownstreamDivertAddressFirstDivertSecondDestroyQueue() throws Exception {
      testFederatedAddressDivert(true, true, false, false);
   }

   //Test creating divert first followed by address
   //Test creating consumer before divert
   //Test destroy divert at end
   @Test
   public void testDownstreamDivertAddressAndDivertSecondDestroyDivert() throws Exception {
      testFederatedAddressDivert(true, false, false, true);
   }

   //Test creating divert first followed by address
   //Test creating consumer before divert
   //Test destroy queue at end
   @Test
   public void testDownstreamDivertAddressAndDivertSecondDestroyQueue() throws Exception {
      testFederatedAddressDivert(true, false, false, false);
   }

   /**
    * Test diverts for upstream configurations
    */
   //Test creating address first followed by divert
   //Test creating divert before consumer
   //Test destroy divert at end
   @Test
   public void testUpstreamDivertAddressAndDivertFirstDestroyDivert() throws Exception {
      testFederatedAddressDivert(false, true, true, true);
   }

   //Test creating address first followed by divert
   //Test creating divert before consumer
   //Test destroy queue at end
   @Test
   public void testUpstreamDivertAddressAndDivertFirstDestroyQueue() throws Exception {
      testFederatedAddressDivert(false, true, true, false);
   }

   //Test creating divert first followed by address
   //Test creating divert before consumer
   //Test destroy divert at end
   @Test
   public void testUpstreamDivertAddressSecondDivertFirstDestroyDivert() throws Exception {
      testFederatedAddressDivert(false, false, true, true);
   }

   //Test creating divert first followed by address
   //Test creating divert before consumer
   //Test destroy queue at end
   @Test
   public void testUpstreamDivertAddressSecondDivertFirstDestroyQueue() throws Exception {
      testFederatedAddressDivert(false, false, true, false);
   }

   //Test creating address first followed by divert
   //Test creating consumer before divert
   //Test destroy divert at end
   @Test
   public void testUpstreamDivertAddressFirstDivertSecondDestroyDivert() throws Exception {
      testFederatedAddressDivert(false, true, false, true);
   }

   //Test creating address first followed by divert
   //Test creating consumer before divert
   //Test destroy queue at end
   @Test
   public void testUpstreamDivertAddressFirstDivertSecondDestroyQueue() throws Exception {
      testFederatedAddressDivert(false, true, false, false);
   }

   //Test creating divert first followed by address
   //Test creating consumer before divert
   //Test destroy divert at end
   @Test
   public void testUpstreamsDivertAddressAndDivertSecondDestroyDivert() throws Exception {
      testFederatedAddressDivert(false, false, false, true);
   }

   //Test creating divert first followed by address
   //Test creating consumer before divert
   //Test destroy queue at end
   @Test
   public void testUpstreamDivertAddressAndDivertSecondDestroyQueue() throws Exception {
      testFederatedAddressDivert(false, false, false, false);
   }

   protected void testFederatedAddressDivert(boolean downstream, boolean addressFirst, boolean divertBeforeConsumer,
                                             boolean destroyDivert) throws Exception {
      String address = getName();
      String address2 = "fedOneWayDivertTest";

      if (addressFirst) {
         getServer(0).addAddressInfo(new AddressInfo(SimpleString.of(address), RoutingType.MULTICAST));
      }

      final FederationConfiguration federationConfiguration;
      final int deployServer;
      if (downstream) {
         federationConfiguration = FederatedTestUtil.createAddressDownstreamFederationConfiguration(
            "server0", address, getServer(1).getConfiguration().getTransportConfigurations("server1")[0]);
         deployServer = 1;
      } else {
         federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
         deployServer = 0;
      }

      FederationAddressPolicyConfiguration policy = (FederationAddressPolicyConfiguration) federationConfiguration.getFederationPolicyMap().get("AddressPolicy" + address);
      //enable listening for divert bindings
      policy.setEnableDivertBindings(true);
      getServer(deployServer).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(deployServer).getFederationManager().deploy();

      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         connection0.start();

         Session session1 = connection1.createSession();
         Topic topic1 = session1.createTopic(address);
         MessageProducer producer1 = session1.createProducer(topic1);

         if (divertBeforeConsumer) {
            getServer(0).deployDivert(new DivertConfiguration().setName(address + ":" + address2)
                   .setAddress(address).setExclusive(true).setForwardingAddress(address2)
                   .setRoutingType(ComponentConfigurationRoutingType.ANYCAST));
         }

         Session session0 = connection0.createSession();
         Queue queue0 = session0.createQueue(address2);
         MessageConsumer consumer0 = session0.createConsumer(queue0);

         if (!addressFirst) {
            getServer(0).addAddressInfo(new AddressInfo(SimpleString.of(address), RoutingType.MULTICAST));
         }

         if (!divertBeforeConsumer) {
            getServer(0).deployDivert(new DivertConfiguration().setName(address + ":" + address2)
                                         .setAddress(address).setExclusive(true).setForwardingAddress(address2)
                                         .setRoutingType(ComponentConfigurationRoutingType.ANYCAST));
         }

         Wait.assertEquals(1L, () -> getServer(1).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().size(), 10_000, 100);
         final QueueBinding remoteQueueBinding = (QueueBinding) getServer(1).getPostOffice().getBindingsForAddress(SimpleString.of(address))
            .getBindings().iterator().next();
         Wait.assertEquals(1, () -> remoteQueueBinding.getQueue().getConsumerCount());

         producer1.send(session1.createTextMessage("hello"));
         assertNotNull(consumer0.receive(1000));

         //Test consumer is cleaned up after divert destroyed
         if (destroyDivert) {
            getServer(0).destroyDivert(SimpleString.of(address + ":" + address2));
         //Test consumer is cleaned up after queue destroyed
         } else {
            getServer(0).destroyQueue(SimpleString.of(address2), null, false);
         }
         assertTrue(Wait.waitFor(() -> remoteQueueBinding.getQueue().getConsumerCount() == 0, 2000, 100));
      }
   }

   private void testFederatedAddressReplication(String address) throws Exception {

      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         connection0.start();

         Session session1 = connection1.createSession();
         Topic topic1 = session1.createTopic(address);
         MessageProducer producer = session1.createProducer(topic1);
         producer.send(session1.createTextMessage("hello"));


         Session session0 = connection0.createSession();
         Topic topic0 = session0.createTopic(address);
         MessageConsumer consumer0 = session0.createConsumer(topic0);

         assertTrue(Wait.waitFor(() -> getServer(1).getPostOffice().getBindingsForAddress(
            SimpleString.of(address)).getBindings().size() == 1, 2000, 100));

         producer.send(session1.createTextMessage("hello"));

         assertNotNull(consumer0.receive(1000));


         producer.send(session1.createTextMessage("hello"));

         assertNotNull(consumer0.receive(1000));

         MessageConsumer consumer1 = session1.createConsumer(topic1);

         producer.send(session1.createTextMessage("hello"));

         assertNotNull(consumer1.receive(1000));
         assertNotNull(consumer0.receive(1000));
         consumer1.close();

         //Groups
         producer.send(session1.createTextMessage("hello"));
         assertNotNull(consumer0.receive(1000));

         producer.send(createTextMessage(session1, "groupA"));

         assertNotNull(consumer0.receive(1000));
         consumer1 = session1.createConsumer(topic1);

         producer.send(createTextMessage(session1, "groupA"));
         assertNotNull(consumer1.receive(1000));
         assertNotNull(consumer0.receive(1000));

      }

   }

   @Test
   public void testFederatedAddressDeployAfterQueuesExist() throws Exception {
      String address = getName();

      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         connection0.start();

         Session session1 = connection1.createSession();
         Topic topic1 = session1.createTopic(address);
         MessageProducer producer = session1.createProducer(topic1);
         producer.send(session1.createTextMessage("hello"));


         Session session0 = connection0.createSession();
         Topic topic0 = session0.createTopic(address);
         MessageConsumer consumer0 = session0.createConsumer(topic0);


         producer.send(session1.createTextMessage("hello"));
         assertNull(consumer0.receive(100));

         FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
         getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
         getServer(0).getFederationManager().deploy();

         Wait.waitFor(() -> getServer(1).getPostOffice().getBindingsForAddress(
            SimpleString.of(address)).getBindings().size() == 1, 2000, 100);

         producer.send(session1.createTextMessage("hello"));
         assertNotNull(consumer0.receive(1000));
      }
   }

   @Test
   public void testFederatedAddressRemoteBrokerRestart() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         connection0.start();

         Session session1 = connection1.createSession();
         Topic topic1 = session1.createTopic(address);
         MessageProducer producer = session1.createProducer(topic1);
         producer.send(session1.createTextMessage("hello"));


         Session session0 = connection0.createSession();
         Topic topic0 = session0.createTopic(address);
         MessageConsumer consumer0 = session0.createConsumer(topic0);

         Wait.waitFor(() -> getServer(1).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().size() == 1);

         producer.send(session1.createTextMessage("hello"));

         assertNotNull(consumer0.receive(10000));


         producer.send(session1.createTextMessage("hello"));

         assertNotNull(consumer0.receive(1000));

         connection1.close();
         getServer(1).stop();
         Wait.waitFor(() -> !getServer(1).isStarted());

         assertNull(consumer0.receive(100));

         getServer(1).start();
         Wait.waitFor(() -> getServer(1).isActive());

         Connection c1 = cf1.createConnection();
         c1.start();
         Wait.waitFor(() -> getServer(1).isStarted());
         session1 = c1.createSession();
         topic1 = session1.createTopic(address);
         producer = session1.createProducer(topic1);

         Wait.waitFor(() -> getServer(1).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().size() == 1);
         producer.send(session1.createTextMessage("hello"));

         assertNotNull(consumer0.receive(1000));
      }
   }

   @Test
   public void testFederatedAddressLocalBrokerRestart() throws Exception {
      String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      ConnectionFactory cf1 = getCF(1);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         connection0.start();

         Session session1 = connection1.createSession();
         Topic topic1 = session1.createTopic(address);
         MessageProducer producer = session1.createProducer(topic1);
         producer.send(session1.createTextMessage("hello"));


         Session session0 = connection0.createSession();
         Topic topic0 = session0.createTopic(address);
         MessageConsumer consumer0 = session0.createConsumer(topic0);

         Wait.waitFor(() -> getServer(1).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().size() == 1);

         producer.send(session1.createTextMessage("hello"));

         assertNotNull(consumer0.receive(10000));


         producer.send(session1.createTextMessage("hello"));

         assertNotNull(consumer0.receive(1000));

         connection0.close();
         getServer(0).stop();
         Wait.waitFor(() -> !getServer(0).isStarted());

         producer.send(session1.createTextMessage("hello"));

         getServer(0).start();
         Wait.waitFor(() -> getServer(0).isActive());

         Connection newConnection = getCF(0).createConnection();
         newConnection.start();
         session0 = newConnection.createSession();
         topic0 = session0.createTopic(address);
         consumer0 = session0.createConsumer(topic0);

         Wait.waitFor(() -> getServer(1).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().size() == 1);
         producer.send(session1.createTextMessage("hello"));

         assertNotNull(consumer0.receive(1000));

         newConnection.close();
      }
   }

   @Test
   public void testFederatedAddressChainOfBrokers() throws Exception {
      String address = getName();

      //Set queue up on all three brokers
//      for (int i = 0; i < 3; i++) {
//         getServer(i).createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
//      }

      //Connect broker 0 (consumer will be here at end of chain) to broker 1
      FederationConfiguration federationConfiguration0 = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address, 2);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration0);
      getServer(0).getFederationManager().deploy();

      //Connect broker 1 (middle of chain) to broker 2
      FederationConfiguration federationConfiguration1 = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server2", address, 2);
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration1);
      getServer(1).getFederationManager().deploy();
      //Broker 2 we dont setup any federation as he is the upstream (head of the chain)

      //Now the test.


      ConnectionFactory cf2 = getCF(2);
      ConnectionFactory cf0 = getCF(0);
      try (Connection connection2 = cf2.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection0.start();
         Session session0 = connection0.createSession();
         Topic topic0 = session0.createTopic(address);

         connection2.start();
         Session session2 = connection2.createSession();
         Topic topic2 = session2.createTopic(address);

         MessageProducer producer2 = session2.createProducer(topic2);
         MessageConsumer consumer0 = session0.createConsumer(topic0);

         assertTrue(Wait.waitFor(() -> getServer(1).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().size() == 1));
         assertTrue(Wait.waitFor(() -> getServer(2).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().size() == 1));

         //Test producers being on broker 2 and consumer on broker 0, with broker 2 being in the middle of the chain.
         producer2.send(session2.createTextMessage("hello"));
         assertNotNull(consumer0.receive(1000));
      }
   }

   @Test
   public void testUpstreamFederatedAddressWithCompressedMessage() throws Exception {
      final String DATA_REGULAR = RandomUtil.randomAlphaNumericString(10);
      final String DATA_COMPRESSED_REGULAR = "Compresses easily".repeat(500 * 1024);
      final String DATA_COMPRESSED_LARGE = RandomUtil.randomAlphaNumericString(1024 * 1024);
      final String address = getName();

      FederationConfiguration federationConfiguration = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server1", address);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration);
      getServer(0).getFederationManager().deploy();

      FederationConfiguration federationConfiguration2 = FederatedTestUtil.createAddressUpstreamFederationConfiguration("server0", address);
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration2);
      getServer(1).getFederationManager().deploy();

      ActiveMQConnectionFactory cf0 = new ActiveMQConnectionFactory("vm://" + 0);
      ActiveMQConnectionFactory cf1 = new ActiveMQConnectionFactory("vm://" + 1);
      cf0.setCompressLargeMessage(true);
      cf1.setCompressLargeMessage(true);

      try (Connection connection1 = cf1.createConnection();
           Connection connection0 = cf0.createConnection()) {
         connection1.start();
         connection0.start();

         Session session0 = connection0.createSession();
         Session session1 = connection1.createSession();
         Topic topic0 = session0.createTopic(address);
         Topic topic1 = session1.createTopic(address);
         MessageConsumer consumer0 = session0.createConsumer(topic0);
         MessageConsumer consumer1 = session1.createConsumer(topic1);

         MessageProducer producer = session0.createProducer(topic0);

         Wait.assertEquals(2, () -> getServer(0).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().size(), 5000, 100);
         Wait.assertEquals(2, () -> getServer(1).getPostOffice().getBindingsForAddress(SimpleString.of(address)).getBindings().size(), 5000, 100);

         sendAndConsume(producer, session0, DATA_REGULAR, "regular", consumer0, consumer1);
         sendAndConsume(producer, session0, DATA_COMPRESSED_LARGE, "compressedLarge", consumer0, consumer1);
         sendAndConsume(producer, session0, DATA_COMPRESSED_REGULAR, "compressedRegular", consumer0, consumer1);
      }
   }

   private void sendAndConsume(MessageProducer producer,
                          Session session0,
                          String data,
                          String identification,
                          MessageConsumer consumer0,
                          MessageConsumer consumer1) throws Exception {
      {
         TextMessage message = session0.createTextMessage(data);
         message.setStringProperty("identification", identification);
         producer.send(message);
      }
      TextMessage message0 = (TextMessage) consumer0.receive(10_000);
      TextMessage message1 = (TextMessage) consumer1.receive(10_000);
      assertNotNull(message0);
      assertNotNull(message1);
      assertEquals(data, message0.getText());
      assertEquals(data, message1.getText());
   }

   private Message createTextMessage(Session session1, String group) throws JMSException {
      Message message = session1.createTextMessage("hello");
      message.setStringProperty("JMSXGroupID", group);
      return message;
   }

   public static class TestTransformer implements Transformer {

      static String TEST_PROPERTY = "transformed";

      @Override
      public org.apache.activemq.artemis.api.core.Message transform(org.apache.activemq.artemis.api.core.Message message) {
         message.putBooleanProperty(TEST_PROPERTY, true);
         return message;
      }
   }

}
