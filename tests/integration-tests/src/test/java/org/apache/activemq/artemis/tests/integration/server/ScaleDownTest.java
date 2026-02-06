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
package org.apache.activemq.artemis.tests.integration.server;

import javax.management.MBeanServer;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.ha.PrimaryOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ServiceComponent;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.apache.activemq.artemis.utils.collections.IterableStream.iterableOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ParameterizedTestExtension.class)
public class ScaleDownTest extends ClusterTestBase {

   private static final String AMQP_ACCEPTOR_URI = "tcp://127.0.0.1:5672";

   private boolean useScaleDownGroupName;
   private final AtomicBoolean shutdownTrueCalledOnExternalComponent = new AtomicBoolean();

   // this will ensure that all tests in this class are run twice,
   // once with "true" passed to the class' constructor and once with "false"
   @Parameters(name = "useScaleDownGroupName={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   public ScaleDownTest(boolean useScaleDownGroupName) {
      this.useScaleDownGroupName = useScaleDownGroupName;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      setupPrimaryServer(0, isFileStorage(), isNetty(), true);
      setupPrimaryServer(1, isFileStorage(), isNetty(), true);
      servers[0].getConfiguration().addAcceptorConfiguration("amqp", AMQP_ACCEPTOR_URI + "?protocols=AMQP");
      PrimaryOnlyPolicyConfiguration haPolicyConfiguration0 = (PrimaryOnlyPolicyConfiguration) servers[0].getConfiguration().getHAPolicyConfiguration();
      haPolicyConfiguration0.setScaleDownConfiguration(new ScaleDownConfiguration());
      PrimaryOnlyPolicyConfiguration haPolicyConfiguration1 = (PrimaryOnlyPolicyConfiguration) servers[1].getConfiguration().getHAPolicyConfiguration();
      haPolicyConfiguration1.setScaleDownConfiguration(new ScaleDownConfiguration());
      if (useScaleDownGroupName) {
         haPolicyConfiguration0.getScaleDownConfiguration().setGroupName("bill");
         haPolicyConfiguration1.getScaleDownConfiguration().setGroupName("bill");
      }
      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster0", "testAddress", MessageLoadBalancingType.ON_DEMAND, 1, isNetty(), 1, 0);
      haPolicyConfiguration0.getScaleDownConfiguration().getConnectors().addAll(servers[0].getConfiguration().getClusterConfigurations().iterator().next().getStaticConnectors());
      haPolicyConfiguration1.getScaleDownConfiguration().getConnectors().addAll(servers[1].getConfiguration().getClusterConfigurations().iterator().next().getStaticConnectors());
      servers[0].getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      servers[1].getConfiguration().getAddressSettings().put("#", new AddressSettings().setRedistributionDelay(0));
      startServers(0, 1);
      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());

      // verify external service component like web gets shutdown on stop
      shutdownTrueCalledOnExternalComponent.set(false);
      servers[0].addExternalComponent(new ServiceComponent() {
         @Override
         public void start() throws Exception {
         }

         @Override
         public void stop() throws Exception {
         }

         @Override
         public boolean isStarted() {
            return false;
         }

         @Override
         public void stop(boolean shutdown) throws Exception {
            shutdownTrueCalledOnExternalComponent.set(shutdown);
         }

      }, false);
   }

   @AfterEach
   public void checkExternalComponentStoppedOnZero() {
      assertEquals(true, shutdownTrueCalledOnExternalComponent.get());
   }

   protected boolean isNetty() {
      return true;
   }

   @Override
   protected boolean isResolveProtocols() {
      return true;
   }

   @TestTemplate
   public void testBasicScaleDown() throws Exception {
      final int TEST_SIZE = 2;
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";
      final String queueName2 = "testQueue2";

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, true);
      createQueue(0, addressName, queueName2, null, true);
      createQueue(1, addressName, queueName1, null, true);
      createQueue(1, addressName, queueName2, null, true);

      // send messages to node 0
      send(0, addressName, TEST_SIZE, true, null);

      // consume a message from queue 2
      addConsumer(1, 0, queueName2, null, false);
      ClientMessage clientMessage = consumers[1].getConsumer().receive(1000);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      consumers[1].getSession().commit();
      //      removeConsumer(1);

      // at this point on node 0 there should be 2 messages in testQueue1 and 1 message in testQueue2
      assertEquals(TEST_SIZE, getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName1))).getQueue()));
      assertEquals(TEST_SIZE - 1, getMessageCount(((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName2))).getQueue()));

      // trigger scaleDown from node 0 to node 1
      ActiveMQServerControlImpl serverControl = (ActiveMQServerControlImpl) servers[0].getManagementService().getServerControl();
      serverControl.scaleDown(null);

      // get the 2 messages from queue 1
      addConsumer(0, 1, queueName1, null);
      clientMessage = consumers[0].getConsumer().receive(1000);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      clientMessage = consumers[0].getConsumer().receive(1000);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receiveImmediate();
      assertNull(clientMessage);
      removeConsumer(0);

      // get the 1 message from queue 2
      addConsumer(0, 1, queueName2, null);
      clientMessage = consumers[0].getConsumer().receive(1000);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receiveImmediate();
      assertNull(clientMessage);
      removeConsumer(0);
   }


   @TestTemplate
   public void testScaleDownNodeReconnect() throws Exception {

      try {
         ClusterController controller = servers[0].getClusterManager().getClusterController();

         Map<SimpleString, ServerLocatorInternal> locatorsMap = controller.getLocators();
         Iterator<Map.Entry<SimpleString, ServerLocatorInternal>> iter = locatorsMap.entrySet().iterator();
         assertTrue(iter.hasNext());
         Map.Entry<SimpleString, ServerLocatorInternal> entry = iter.next();
         ServerLocatorImpl locator = (ServerLocatorImpl) entry.getValue();

         waitForClusterConnected(locator);

         servers[1].stop();

         servers[1].start();

         //by this moment server0 is trying to reconnect to server1
         //In normal case server1 will check if the reconnection's scaleDown
         //server has been scaled down before granting the connection.
         //but if the scaleDown is server1 itself, it should grant
         //the connection without checking scaledown state against it,
         //Otherwise the connection will never be established, and more,
         //the repetitive reconnect attempts will cause
         //ClientSessionFactory's closeExecutor to be filled with
         //tasks that keep growing until OOM.
         checkClusterConnectionExecutorNotBlocking(locator);
      } finally {
         servers[1].stop();
         servers[0].stop();
      }
   }

   private void checkClusterConnectionExecutorNotBlocking(ServerLocatorImpl locator) throws NoSuchFieldException, IllegalAccessException {
      Field factoriesField = locator.getClass().getDeclaredField("factories");
      factoriesField.setAccessible(true);
      Set factories = (Set) factoriesField.get(locator);
      assertEquals(1, factories.size());

      ClientSessionFactoryImpl factory = (ClientSessionFactoryImpl) factories.iterator().next();

      Field executorField = factory.getClass().getDeclaredField("closeExecutor");
      executorField.setAccessible(true);
      Executor pool = (Executor) executorField.get(factory);
      final CountDownLatch latch = new CountDownLatch(1);
      pool.execute(()->
         latch.countDown()
      );
      boolean result = false;
      try {
         result = latch.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
      }
      assertTrue(result, "executor got blocked.");
   }

   private void waitForClusterConnected(ServerLocatorImpl locator) throws Exception {

      boolean result = Wait.waitFor(() -> !locator.getTopology().isEmpty(), 5000);

      assertTrue(result, "topology should not be empty");
   }

   @TestTemplate
   public void testStoreAndForward() throws Exception {
      final int TEST_SIZE = 50;
      final String addressName1 = "testAddress1";
      final String addressName2 = "testAddress2";
      final String queueName1 = "testQueue1";
      final String queueName2 = "testQueue2";

      // create queues on each node mapped to 2 addresses
      createQueue(0, addressName1, queueName1, null, false);
      createQueue(1, addressName1, queueName1, null, false);
      createQueue(0, addressName2, queueName2, null, false);
      createQueue(1, addressName2, queueName2, null, false);

      // find and pause the sf queue so no messages actually move from node 0 to node 1
      String sfQueueName = null;
      for (Binding binding : iterableOf(servers[0].getPostOffice().getAllBindings())) {
         String temp = binding.getAddress().toString();

         if (temp.startsWith(servers[1].getInternalNamingPrefix() + "sf.") && temp.endsWith(servers[1].getNodeID().toString())) {
            // we found the sf queue for the other node
            // need to pause the sfQueue here
            ((LocalQueueBinding) binding).getQueue().pause();
            sfQueueName = temp;
         }
      }

      assertNotNull(sfQueueName);

      // send messages to node 0
      send(0, addressName1, TEST_SIZE, false, null);
      send(0, addressName2, TEST_SIZE, false, null);

      // add consumers to node 1 to force messages to redistribute to node 2 through the paused sf queue
      addConsumer(0, 1, queueName1, null);
      addConsumer(1, 1, queueName2, null);

      LocalQueueBinding queue1Binding = ((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName1)));
      LocalQueueBinding queue2Binding = ((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName2)));
      LocalQueueBinding sfQueueBinding = ((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(sfQueueName)));

      long timeout = 5000;
      long start = System.currentTimeMillis();

      while (getMessageCount(queue1Binding.getQueue()) > 0 && System.currentTimeMillis() - start <= timeout) {
         Thread.sleep(50);
      }

      start = System.currentTimeMillis();

      while (getMessageCount(queue2Binding.getQueue()) > 0 && System.currentTimeMillis() - start <= timeout) {
         Thread.sleep(50);
      }

      start = System.currentTimeMillis();

      while (getMessageCount(sfQueueBinding.getQueue()) < TEST_SIZE * 2 && System.currentTimeMillis() - start <= timeout) {
         Thread.sleep(50);
      }

      // at this point on node 0 there should be 0 messages in test queues and TEST_SIZE * 2 messages in the sf queue
      assertEquals(0, getMessageCount(queue1Binding.getQueue()));
      assertEquals(0, getMessageCount(queue2Binding.getQueue()));
      assertEquals(TEST_SIZE * 2, getMessageCount(sfQueueBinding.getQueue()));

      removeConsumer(0);
      removeConsumer(1);

      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      // get the messages from node 1
      addConsumer(0, 1, queueName1, null);
      for (int i = 0; i < TEST_SIZE; i++) {
         ClientMessage clientMessage = consumers[0].getConsumer().receive(250);
         assertNotNull(clientMessage);
         clientMessage.acknowledge();
      }

      ClientMessage clientMessage = consumers[0].getConsumer().receiveImmediate();
      assertNull(clientMessage);
      removeConsumer(0);

      addConsumer(0, 1, queueName2, null);
      for (int i = 0; i < TEST_SIZE; i++) {
         clientMessage = consumers[0].getConsumer().receive(250);
         assertNotNull(clientMessage);
         clientMessage.acknowledge();
      }

      clientMessage = consumers[0].getConsumer().receiveImmediate();
      assertNull(clientMessage);
      removeConsumer(0);
   }

   @TestTemplate
   public void testScaleDownWithMissingQueue() throws Exception {
      final int TEST_SIZE = 2;
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";
      final String queueName2 = "testQueue2";

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, false);
      createQueue(0, addressName, queueName2, null, false);
      createQueue(1, addressName, queueName1, null, false);

      // send messages to node 0
      send(0, addressName, TEST_SIZE, false, null);

      // consume a message from node 0
      addConsumer(1, 0, queueName2, null, false);
      ClientMessage clientMessage = consumers[1].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      consumers[1].getSession().commit();

      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      // get the 2 messages from queue 1
      addConsumer(0, 1, queueName1, null);
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receiveImmediate();
      assertNull(clientMessage);
      removeConsumer(0);

      // get the 1 message from queue 2
      addConsumer(0, 1, queueName2, null);
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // ensure there are no more messages on queue 1
      clientMessage = consumers[0].getConsumer().receiveImmediate();
      assertNull(clientMessage);
      removeConsumer(0);
   }

   @TestTemplate
   public void testScaleDownWithMissingAnycastQueue() throws Exception {
      final int TEST_SIZE = 2;
      final String addressName = "testAddress";
      final String queueName = "testQueue";

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName, null, false, null, null, RoutingType.ANYCAST);

      // send messages to node 0
      send(0, addressName, TEST_SIZE, false, null);

      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      assertEquals(RoutingType.ANYCAST, ((QueueImpl)((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName))).getBindable()).getRoutingType());
      // get the 1 message from queue 2
      addConsumer(0, 1, queueName, null);
      ClientMessage clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

   }

   private void sendAMQPMessages(final String address, final int numMessages, final boolean durable) throws Exception {
      AmqpClient client = new AmqpClient(new URI(AMQP_ACCEPTOR_URI), "admin", "admin");
      AmqpConnection connection = client.connect();
      try {
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender(address);
         for (int i = 0; i < numMessages; ++i) {
            AmqpMessage message = new AmqpMessage();
            message.setMessageId("MessageID:" + i);
            message.setDurable(durable);
            sender.send(message);
         }
      } finally {
         connection.close();
      }
   }

   @TestTemplate
   public void testScaleDownAMQPMessagesWithMissingAnycastQueue() throws Exception {
      final int TEST_SIZE = 2;
      final String addressName = "testAddress";
      final String queueName = "testQueue";

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName, null, false, null, null, RoutingType.ANYCAST);

      // send messages to node 0
      sendAMQPMessages(addressName, TEST_SIZE, false);

      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      assertEquals(RoutingType.ANYCAST, ((QueueImpl)((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName))).getBindable()).getRoutingType());
      // get the 1 message from queue 2
      addConsumer(0, 1, queueName, null);
      ClientMessage clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
   }

   @TestTemplate
   public void testScaleDownAMQPMessagesWithMissingMulticastQueues() throws Exception {
      final int TEST_SIZE = 2;
      ClientMessage clientMessage;
      final String addressName = "testAddress";
      final String queueName1 = "testQueue1";
      final String queueName2 = "testQueue2";

      // create 2 queues on each node mapped to the same address
      createQueue(0, addressName, queueName1, null, false, null, null, RoutingType.MULTICAST);
      createQueue(0, addressName, queueName2, null, false, null, null, RoutingType.MULTICAST);

      // send messages to node 0
      sendAMQPMessages(addressName, TEST_SIZE, false);

      assertEquals(2, ((QueueImpl)((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName1))).getBindable()).getMessageCount());
      assertEquals(2, ((QueueImpl)((LocalQueueBinding) servers[0].getPostOffice().getBinding(SimpleString.of(queueName2))).getBindable()).getMessageCount());

      // trigger scaleDown from node 0 to node 1
      servers[0].stop();

      assertEquals(RoutingType.MULTICAST, ((QueueImpl)((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName1))).getBindable()).getRoutingType());
      assertEquals(RoutingType.MULTICAST, ((QueueImpl)((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName2))).getBindable()).getRoutingType());

      assertEquals(2, ((QueueImpl)((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName1))).getBindable()).getMessageCount());
      assertEquals(2, ((QueueImpl)((LocalQueueBinding) servers[1].getPostOffice().getBinding(SimpleString.of(queueName2))).getBindable()).getMessageCount());

      // get the 1 message from queue 1
      addConsumer(0, 1, queueName1, null);
      clientMessage = consumers[0].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();

      // get the 1 message from queue 2
      addConsumer(1, 1, queueName2, null);
      clientMessage = consumers[1].getConsumer().receive(250);
      assertNotNull(clientMessage);
      clientMessage.acknowledge();
   }


   @TestTemplate
   public void testMessageProperties() throws Exception {
      final int TEST_SIZE = 5;
      final String addressName = "testAddress";
      final String queueName = "testQueue";

      createQueue(0, addressName, queueName, null, false);
      createQueue(1, addressName, queueName, null, false);

      ClientSessionFactory sf = sfs[0];
      ClientSession session = addClientSession(sf.createSession(false, true, true));
      ClientProducer producer = addClientProducer(session.createProducer(addressName));

      StringBuilder international = new StringBuilder();
      for (char x = 800; x < 1200; x++) {
         international.append(x);
      }

      String special = "\"<>'&";

      for (int i = 0; i < TEST_SIZE; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.getBodyBuffer().writeString("Bob the giant pig " + i);
         msg.putBooleanProperty("myBooleanProperty", Boolean.TRUE);
         msg.putByteProperty("myByteProperty", Byte.parseByte("0"));
         msg.putBytesProperty("myBytesProperty", new byte[]{0, 1, 2, 3, 4});
         msg.putDoubleProperty("myDoubleProperty", i * 1.6);
         msg.putFloatProperty("myFloatProperty", i * 2.5F);
         msg.putIntProperty("myIntProperty", i);
         msg.putLongProperty("myLongProperty", Long.MAX_VALUE - i);
         msg.putObjectProperty("myObjectProperty", i);
         msg.putShortProperty("myShortProperty", Integer.valueOf(i).shortValue());
         msg.putStringProperty("myStringProperty", "myStringPropertyValue_" + i);
         msg.putStringProperty("myNonAsciiStringProperty", international.toString());
         msg.putStringProperty("mySpecialCharacters", special);
         producer.send(msg);
      }

      servers[0].stop();

      sf = sfs[1];
      session = addClientSession(sf.createSession(false, true, true));
      ClientConsumer consumer = addClientConsumer(session.createConsumer(queueName));
      session.start();

      for (int i = 0; i < 5; i++) {
         ClientMessage msg = consumer.receive(250);
         byte[] body = new byte[msg.getBodySize()];
         msg.getBodyBuffer().readBytes(body);
         assertTrue(new String(body).contains("Bob the giant pig " + i));
         assertEquals(Boolean.TRUE, msg.getBooleanProperty("myBooleanProperty"));
         assertEquals(msg.getByteProperty("myByteProperty"), Byte.valueOf("0"));
         byte[] bytes = msg.getBytesProperty("myBytesProperty");
         for (int j = 0; j < 5; j++) {
            assertEquals(j, bytes[j]);
         }
         assertEquals(i * 1.6, msg.getDoubleProperty("myDoubleProperty"), 0.000001);
         assertEquals(i * 2.5F, msg.getFloatProperty("myFloatProperty"), 0.000001);
         assertEquals(i, msg.getIntProperty("myIntProperty").intValue());
         assertEquals(Long.MAX_VALUE - i, msg.getLongProperty("myLongProperty").longValue());
         assertEquals(i, msg.getObjectProperty("myObjectProperty"));
         assertEquals(Integer.valueOf(i).shortValue(), msg.getShortProperty("myShortProperty").shortValue());
         assertEquals("myStringPropertyValue_" + i, msg.getStringProperty("myStringProperty"));
         assertEquals(international.toString(), msg.getStringProperty("myNonAsciiStringProperty"));
         assertEquals(special, msg.getStringProperty("mySpecialCharacters"));
      }
   }

   @TestTemplate
   public void testLargeMessage() throws Exception {
      final String addressName = "testAddress";
      final String queueName = "testQueue";

      createQueue(0, addressName, queueName, null, true);
      createQueue(1, addressName, queueName, null, true);

      ClientSessionFactory sf = sfs[0];
      ClientSession session = addClientSession(sf.createSession(false, false));
      ClientProducer producer = addClientProducer(session.createProducer(addressName));

      byte[] buffer = new byte[2 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE];
      for (int i = 0; i < buffer.length; i++) {
         buffer[i] = getSamplebyte(i);
      }

      for (int numMessages = 0; numMessages < 10; numMessages++) {
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(buffer);
         producer.send(message);
         session.commit();
      }

      servers[0].stop();

      sf = sfs[1];
      session = addClientSession(sf.createSession(false, true, true));
      ClientConsumer consumer = addClientConsumer(session.createConsumer(queueName));
      session.start();

      for (int numMessages = 0; numMessages < 10; numMessages++) {
         ClientMessage msg = consumer.receive(250);

         assertNotNull(msg);

         assertEquals(2 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, msg.getBodySize());

         for (int i = 0; i < 2 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE; i++) {
            byte byteRead = msg.getBodyBuffer().readByte();
            assertEquals(ActiveMQTestBase.getSamplebyte(i), byteRead, msg + " Is different");
         }

         msg.acknowledge();
         session.commit();
      }
   }

   @TestTemplate
   public void testPaging() throws Exception {
      final int CHUNK_SIZE = 50;
      int messageCount = 0;
      final String addressName = "testAddress";
      final String queueName = "testQueue";

      createQueue(0, addressName, queueName, null, false);
      createQueue(1, addressName, queueName, null, false);

      ClientSessionFactory sf = sfs[0];
      ClientSession session = addClientSession(sf.createSession(false, false));
      ClientProducer producer = addClientProducer(session.createProducer(addressName));

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(10 * 1024).setMaxSizeBytes(20 * 1024);
      servers[0].getAddressSettingsRepository().addMatch("#", defaultSetting);

      while (!servers[0].getPagingManager().getPageStore(SimpleString.of(addressName)).isPaging()) {
         for (int i = 0; i < CHUNK_SIZE; i++) {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[1024]);
            producer.send(message);
            messageCount++;
         }
         session.commit();
      }

      servers[0].stop();

      addConsumer(0, 1, queueName, null);
      for (int i = 0; i < messageCount; i++) {
         assertNotNull(consumers[0].getConsumer().receive(250));
      }

      assertNull(consumers[0].getConsumer().receiveImmediate());
      removeConsumer(0);
   }

   @TestTemplate
   public void testOrderWithPaging() throws Exception {
      final int CHUNK_SIZE = 50;
      int messageCount = 0;
      final String addressName = "testAddress";
      final String queueName = "testQueue";

      createQueue(0, addressName, queueName, null, false);
      createQueue(1, addressName, queueName, null, false);

      ClientSessionFactory sf = sfs[0];
      ClientSession session = addClientSession(sf.createSession(false, false));
      ClientProducer producer = addClientProducer(session.createProducer(addressName));

      AddressSettings defaultSetting = new AddressSettings().setPageSizeBytes(10 * 1024).setMaxSizeBytes(20 * 1024);
      servers[0].getAddressSettingsRepository().addMatch("#", defaultSetting);

      while (!servers[0].getPagingManager().getPageStore(SimpleString.of(addressName)).isPaging()) {
         for (int i = 0; i < CHUNK_SIZE; i++) {
            ClientMessage message = session.createMessage(true);
            message.getBodyBuffer().writeBytes(new byte[1024]);
            message.putIntProperty("order", i);
            producer.send(message);
            messageCount++;
         }
         session.commit();
      }

      servers[0].stop();

      addConsumer(0, 1, queueName, null);
      for (int i = 0; i < messageCount; i++) {
         assertEquals(i, consumers[0].getConsumer().receive(250).getIntProperty("order").intValue());
      }

      assertNull(consumers[0].getConsumer().receiveImmediate());
      removeConsumer(0);
   }

   @TestTemplate
   public void testFilters() throws Exception {
      final int TEST_SIZE = 50;
      final String addressName = "testAddress";
      final String evenQueue = "evenQueue";
      final String oddQueue = "oddQueue";

      createQueue(0, addressName, evenQueue, "0", false);
      createQueue(0, addressName, oddQueue, "1", false);
      createQueue(1, addressName, evenQueue, "0", false);
      createQueue(1, addressName, oddQueue, "1", false);

      ClientSessionFactory sf = sfs[0];
      ClientSession session = addClientSession(sf.createSession(false, false));
      ClientProducer producer = addClientProducer(session.createProducer(addressName));

      for (int i = 0; i < TEST_SIZE; i++) {
         Message message = session.createMessage(false);
         if (i % 2 == 0)
            message.putStringProperty(ClusterTestBase.FILTER_PROP, SimpleString.of("0"));
         else
            message.putStringProperty(ClusterTestBase.FILTER_PROP, SimpleString.of("1"));
         producer.send(message);
      }
      session.commit();

      servers[0].stop();

      addConsumer(0, 1, evenQueue, null);
      addConsumer(1, 1, oddQueue, null);
      for (int i = 0; i < TEST_SIZE; i++) {
         String compare;
         ClientMessage message;
         if (i % 2 == 0) {
            message = consumers[0].getConsumer().receive(250);
            compare = "0";
         } else {
            message = consumers[1].getConsumer().receive(250);
            compare = "1";
         }
         assertEquals(compare, message.getStringProperty(ClusterTestBase.FILTER_PROP));
      }

      assertNull(consumers[0].getConsumer().receiveImmediate());
      assertNull(consumers[1].getConsumer().receiveImmediate());
      removeConsumer(0);
      removeConsumer(1);
   }

   @TestTemplate
   public void testScaleDownMessageWithAutoCreatedDLAResources() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString queueName = SimpleString.of("q1");
      final SimpleString addressName = SimpleString.of("q1");
      final String sampleText = "Put me on DLA";

      //Set up resources for Auto-created DLAs
      AddressSettings addressSettings = servers[0].getAddressSettingsRepository().getMatch(addressName.toString());
      servers[0].getAddressSettingsRepository().addMatch(addressName.toString(), new AddressSettings().setMaxDeliveryAttempts(1).setDeadLetterAddress(dla).setAutoCreateDeadLetterResources(true));
      final SimpleString dlq = addressSettings.getDeadLetterQueuePrefix().concat(addressName).concat(addressSettings.getDeadLetterQueueSuffix());

      createQueue(0, addressName.toString(), queueName.toString(), null, false, null, null, RoutingType.ANYCAST);

      ClientSessionFactory sf = sfs[0];
      ClientSession session = addClientSession(sf.createSession(true, false));
      ClientProducer producer = addClientProducer(session.createProducer(addressName));

      // Send message to queue with RoutingType header
      ClientMessage m = createTextMessage(session, sampleText);
      m.putByteProperty(Message.HDR_ROUTING_TYPE, (byte) 1);
      producer.send(m);
      session.start();

      // Get message
      ClientConsumer consumer = session.createConsumer(queueName);
      ClientMessage message = consumer.receive(1000);
      assertNotNull(message);
      assertEquals(sampleText, message.getBodyBuffer().readString());
      assertEquals(RoutingType.ANYCAST, message.getRoutingType());
      message.acknowledge();

      // force a rollback to DLA
      session.rollback();
      message = consumer.receiveImmediate();
      assertNull(message);

      //Make sure it ends up on DLA
      consumer.close();
      consumer = session.createConsumer(dlq.toString());
      message = consumer.receive(1000);
      assertNotNull(message);
      assertNull(message.getRoutingType());

      //Scale-Down
      servers[0].stop();

      //Get message on seconds node
      sf = sfs[1];
      session = addClientSession(sf.createSession(false, true, true));
      consumer = session.createConsumer(dlq.toString());
      session.start();

      message = consumer.receive(1000);
      assertNotNull(message);
      message.acknowledge();
      assertEquals(sampleText, message.getBodyBuffer().readString());

      consumer.close();
   }

   @TestTemplate
   public void testScaleDownPagedMessageWithMultipleAutoCreatedDLAResources() throws Exception {
      final SimpleString dla = SimpleString.of("DLA");
      final SimpleString qName = SimpleString.of("Q");
      final SimpleString adName = SimpleString.of("ADDR");
      final String sampleText = "Put me on DLQ";
      final int messageCount = 10;
      final int numQueues = 2;

      AddressSettings addressSettings = new AddressSettings().setDeadLetterAddress(dla).setAutoCreateDeadLetterResources(true).setDeadLetterQueuePrefix(dla);
      AddressSettings dlaAddressSettings = new AddressSettings().setDeadLetterAddress(dla).setMaxSizeBytes(200L).setAutoCreateQueues(true);

      servers[0].getAddressSettingsRepository().addMatch("#", addressSettings);
      servers[0].getAddressSettingsRepository().addMatch(dla.toString(), dlaAddressSettings);

      ClientSessionFactory sf = sfs[0];
      ClientSession session = addClientSession(sf.createSession(true, true));
      MBeanServer mbeanServer = servers[0].getMBeanServer();

      for (int i = 0; i < numQueues; i++) {
         SimpleString curAddr = adName.concat(String.valueOf(i));
         SimpleString curQ = qName.concat(String.valueOf(i));
         SimpleString dlq = dla.concat(curAddr);

         session.createQueue(QueueConfiguration.of(curQ).setAddress(curAddr).setDurable(true));
         ClientProducer producer = session.createProducer(curAddr);

         for (int p = 0; p < messageCount; p++) {
            producer.send(createTextMessage(session, sampleText));
         }

         QueueControl queueControl = ManagementControlHelper.createQueueControl(curAddr, curQ, mbeanServer);
         assertEquals(messageCount, queueControl.sendMessagesToDeadLetterAddress(null));
         assertEquals(0, queueControl.getMessageCount());
         Wait.assertTrue(servers[0].locateQueue(dlq).getPagingStore()::isPaging);
      }

      servers[0].getActiveMQServerControl().scaleDown(servers[0].getConfiguration().getClusterConfigurations().iterator().next().getStaticConnectors().get(0));
      Wait.assertEquals((messageCount * numQueues), () -> servers[1].getTotalMessageCount());

   }

}
