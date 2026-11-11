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
package org.apache.activemq.artemis.tests.integration.cluster.crossprotocol;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManagerFactory;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.cluster.distribution.ClusterTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class LargeHeadersClusterTest extends ClusterTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final SimpleString queueName = SimpleString.of("queues.0");

   @Parameters(name = "protocol={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{"AMQP"}, {"CORE"}, {"OPENWIRE"}});
   }

   @Parameter(index = 0)
   public String protocol;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
   }

   private void startServers(MessageLoadBalancingType loadBalancingType) throws Exception {
      setupServers();

      setRedistributionDelay(0);

      setupCluster(loadBalancingType);

      AddressSettings as = new AddressSettings().setRedistributionDelay(0).setExpiryAddress(SimpleString.of("queues.expiry"));

      getServer(0).getAddressSettingsRepository().addMatch("queues.*", as);
      getServer(1).getAddressSettingsRepository().addMatch("queues.*", as);

      startServers(0);
      startServers(1);

      createQueue(SimpleString.of("queues.expiry"));
      createQueue(queueName);
   }

   private void createQueue(SimpleString queueName) throws Exception {
      QueueConfiguration queueConfiguration = QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST);
      servers[0].createQueue(queueConfiguration);
      servers[1].createQueue(queueConfiguration);
   }

   protected boolean isNetty() {
      return true;
   }

   private ConnectionFactory getJmsConnectionFactory(int node) {
      if (protocol.equals("AMQP")) {
         return new JmsConnectionFactory("amqp://localhost:" + (61616 + node));
      } else if (protocol.equals("OPENWIRE")) {
         return new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:" + (61616 + node));
      } else if (protocol.equals("CORE")) {
         return new ActiveMQConnectionFactory("tcp://localhost:" + (61616 + node));
      } else {
         fail("Protocol " + protocol + " unknown");
         return null;
      }
   }

   @TestTemplate
   public void testGrowingHeaders() throws Exception {
      startServers(MessageLoadBalancingType.ON_DEMAND);

      ConnectionFactory cf0 = getJmsConnectionFactory(0);
      ConnectionFactory cf1 = getJmsConnectionFactory(1);
      try (Connection cn = cf0.createConnection()) {
         Session sn = cn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer pd = sn.createProducer(sn.createQueue(queueName.toString()));

         StringBuilder sb = new StringBuilder();
         sb.append("-".repeat(9_500));

         int i = 0;

         try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
            try {
               for (i = 0; i < 1_000; i++) {
                  if (i % 100 == 0) {
                     logger.info("Sent {} messages", i);
                  }
                  TextMessage message = sn.createTextMessage("hello " + i);
                  message.setStringProperty("large", sb.toString());
                  message.setBooleanProperty("newSender", false);
                  // we need to send two, one for each server to exercise the load balancing
                  pd.send(message);
                  pd.send(message);
                  sb.append("-"); // growing the header
               }
            } catch (Throwable e) {
               logger.warn("error at {}", i, e);
            }
            if (!protocol.equals("AMQP")) {
               assertTrue(loggerHandler.findText("AMQ144012"));
            }
         }
      }

      try (Connection connection1 = cf1.createConnection()) {
         Session session = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue("queues.0"));
         connection1.start();
         receiveAllMessages(consumer, 1, m -> logger.debug("received {}", m));
      }

      try (Connection cn = cf0.createConnection()) {
         Session sn = cn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer pd = sn.createProducer(sn.createQueue(queueName.toString()));

         try {
            for (int i = 0; i < 1_000; i++) {
               if (i % 100 == 0) {
                  logger.info("Sent {} messages", i);
               }
               TextMessage message = sn.createTextMessage("newSender " + i);
               message.setBooleanProperty("newSender", true);
               // we need to send two, one for each server to exercise the load balancing
               pd.send(message);
               pd.send(message);
            }
         } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
         }
      }

      AtomicBoolean newSenderFound = new AtomicBoolean(false);

      try (Connection connection1 = cf1.createConnection()) {
         Session session = connection1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(session.createQueue("queues.0"));
         connection1.start();

         receiveAllMessages(consumer, 1000, m -> {
            try {
               if (m.getBooleanProperty("newSender")) {
                  newSenderFound.set(true);
               }
            } catch (Exception ignored) {
            }
         });

      }


      // messages should still flow
      assertTrue(newSenderFound.get());
   }


   private int receiveAllMessages(MessageConsumer messageConsume, int minMessages, Consumer<Message> messageProcessor) throws JMSException {

      int msg = 0;

      for (;;) {
         Message message;

         if (msg < minMessages) {
            message = messageConsume.receive(10_000);
         } else {
            message = messageConsume.receive(1000);
         }
         if (message == null) {
            break;
         }

         msg++;

         if (messageProcessor != null) {
            messageProcessor.accept(message);
         }
      }

      return msg;
   }

   protected void setupCluster(final MessageLoadBalancingType messageLoadBalancingType) throws Exception {
      setupClusterConnection("cluster0", "queues", messageLoadBalancingType, 1, isNetty(), 0, 1);

      setupClusterConnection("cluster1", "queues", messageLoadBalancingType, 1, isNetty(), 1, 0);
   }

   protected void setRedistributionDelay(final long delay) {
   }

   protected void setupServers() throws Exception {
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());

      servers[0].addProtocolManagerFactory(new ProtonProtocolManagerFactory());
      servers[1].addProtocolManagerFactory(new ProtonProtocolManagerFactory());
      servers[0].addProtocolManagerFactory(new OpenWireProtocolManagerFactory());
      servers[1].addProtocolManagerFactory(new OpenWireProtocolManagerFactory());

      servers[0].getConfiguration().setJournalBufferSize_NIO(20 * 1024);
      servers[0].getConfiguration().setJournalBufferSize_AIO(20 * 1024);
      servers[1].getConfiguration().setJournalBufferSize_NIO(20 * 1024);
      servers[1].getConfiguration().setJournalBufferSize_AIO(20 * 1024);

      servers[0].getConfiguration().getAddressSettings().clear();
      servers[0].getConfiguration().addAddressSetting("#", new AddressSettings().setRedistributionDelay(10));

      servers[1].getConfiguration().getAddressSettings().clear();
      servers[1].getConfiguration().addAddressSetting("#", new AddressSettings().setRedistributionDelay(10));
   }

   protected void stopServers() throws Exception {
      closeAllConsumers();

      closeAllSessionFactories();

      closeAllServerLocatorsFactories();

      stopServers(0, 1);

      clearServer(0, 1);
   }

   @Override
   protected ConfigurationImpl createBasicConfig(final int serverID) {
      ConfigurationImpl configuration = super.createBasicConfig(serverID);
      configuration.setMessageExpiryScanPeriod(100);

      return configuration;
   }
}
