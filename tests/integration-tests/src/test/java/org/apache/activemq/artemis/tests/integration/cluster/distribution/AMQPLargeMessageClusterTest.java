/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.cluster.distribution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class AMQPLargeMessageClusterTest extends ClusterTestBase {

   private static final int RECEIVE_TIMEOUT_MILLIS = 20_000;
   private static final int MESSAGE_SIZE = 1024 * 1024;
   private static final int MESSAGES = 1;

   private final boolean persistenceEnabled;

   @Parameters(name = "persistenceEnabled = {0}")
   public static Iterable<? extends Object> persistenceEnabled() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   public AMQPLargeMessageClusterTest(boolean persistenceEnabled) {
      this.persistenceEnabled = persistenceEnabled;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      deleteDirectory(temporaryFolder);
      temporaryFolder.mkdirs();

      start();
   }

   private void start() throws Exception {
      setupServers();

      setRedistributionDelay(0);
   }

   protected boolean isNetty() {
      return true;
   }

   @TestTemplate
   @Timeout(value = RECEIVE_TIMEOUT_MILLIS * (MESSAGES + 1), unit = TimeUnit.MILLISECONDS)
   public void testSendReceiveLargeMessage() throws Exception {
      testSendReceiveLargeMessage(message -> { }, message -> { });
   }

   @TestTemplate
   @Timeout(value = RECEIVE_TIMEOUT_MILLIS * (MESSAGES + 1), unit = TimeUnit.MILLISECONDS)
   public void testSendReceiveLargeMessageWithJMSCorrelationID() throws Exception {
      final String jmsCorrelationID = "123456";
      testSendReceiveLargeMessage(message -> {
         try {
            message.setJMSCorrelationID(jmsCorrelationID);
         } catch (JMSException e) {
            fail("Exception not expected: " + e);
         }
      }, message -> {
         try {
            assertEquals(jmsCorrelationID, message.getJMSCorrelationID());
         } catch (JMSException e) {
            fail("Exception not expected: " + e);
         }
      });
   }


   private void testSendReceiveLargeMessage(Consumer<Message> beforeSending, Consumer<Message> afterReceiving) throws Exception {
      setupCluster(MessageLoadBalancingType.ON_DEMAND);

      startServers(0, 1);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(1, isNetty());
      final String queueName = "queues.0";

      createQueue(0, queueName, queueName, null, false, null, null, RoutingType.ANYCAST);
      createQueue(1, queueName, queueName, null, false, null, null, RoutingType.ANYCAST);

      waitForBindings(0, queueName, 1, 0, true);
      waitForBindings(1, queueName, 1, 0, true);

      waitForBindings(0, queueName, /**/1, 0, false);
      waitForBindings(1, queueName, 1, 0, false);

      String producerUri = "amqp://localhost:61616";
      final JmsConnectionFactory producerFactory = new JmsConnectionFactory(producerUri);
      try (Connection producerConnection = producerFactory.createConnection();
           Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
         producerConnection.start();
         final Destination queue = producerSession.createQueue(queueName);
         String consumerUri = "amqp://localhost:61617";
         final JmsConnectionFactory consumerConnectionFactory = new JmsConnectionFactory(consumerUri);
         try (Connection consumerConnection = consumerConnectionFactory.createConnection();
              Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
              MessageConsumer consumer = consumerSession.createConsumer(queue);
              MessageProducer producer = producerSession.createProducer(queue)) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            consumerConnection.start();
            final byte[] largeMessageContent = new byte[MESSAGE_SIZE];
            final byte[] receivedContent = new byte[largeMessageContent.length];
            ThreadLocalRandom.current().nextBytes(largeMessageContent);
            for (int i = 0; i < MESSAGES; i++) {
               final BytesMessage sentMessage = producerSession.createBytesMessage();
               sentMessage.writeBytes(largeMessageContent);
               beforeSending.accept(sentMessage);
               producer.send(sentMessage);
               final Message receivedMessage = consumer.receive(RECEIVE_TIMEOUT_MILLIS);
               assertNotNull(receivedMessage, "A message should be received in " + RECEIVE_TIMEOUT_MILLIS + " ms");
               assertThat(receivedMessage, IsInstanceOf.instanceOf(sentMessage.getClass()));
               try {
                  assertEquals(largeMessageContent.length, ((BytesMessage) receivedMessage).readBytes(receivedContent));
                  assertArrayEquals(largeMessageContent, receivedContent);
               } catch (Throwable e) {
                  e.printStackTrace();
                  System.exit(-1);
               }
               afterReceiving.accept(receivedMessage);
            }
         }
      }

      stopServers(0, 1);
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 0, 1);
      setupClusterConnection("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 1, 0);
   }

   protected void setRedistributionDelay(final long delay) {
      AddressSettings as = new AddressSettings().setRedistributionDelay(delay);

      getServer(0).getAddressSettingsRepository().addMatch("queues.*", as);
      getServer(1).getAddressSettingsRepository().addMatch("queues.*", as);
   }

   protected void setupServers() throws Exception {
      setupServer(0, persistenceEnabled, isNetty());
      setupServer(1, persistenceEnabled, isNetty());

      servers[0].addProtocolManagerFactory(new ProtonProtocolManagerFactory());
      servers[1].addProtocolManagerFactory(new ProtonProtocolManagerFactory());
   }

   protected void stopServers() throws Exception {
      closeAllConsumers();

      closeAllSessionFactories();

      closeAllServerLocatorsFactories();

      stopServers(0, 1);

      clearServer(0, 1);
   }

}
