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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerMessagePlugin;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidateAddressSizeTest extends BasicOpenWireTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      // making data persistent makes it easier to debug it with print-data
      this.realStore = true;
      super.setUp();
   }


   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      Set<TransportConfiguration> acceptors = serverConfig.getAcceptorConfigurations();
      for (TransportConfiguration tc : acceptors) {
         if (tc.getName().equals("netty")) {
            tc.getExtraParams().put("virtualTopicConsumerWildcards", "Consumer.*.>;2,C.*.>;2;selectorAware=true");
         }
      }
   }

   @Test
   public void testValidateSizeAfterConsumption() throws Exception {
      internalTest(false);
   }

   @Test
   public void testValidateSizeChangeMessageEstimate() throws Exception {
      internalTest(true);
   }

   private void internalTest(boolean changeWithPlugin) throws Exception {
      ConnectionFactory amqpCF = CFUtil.createConnectionFactory("AMQP", "tcp://localhost:61616");
      ConnectionFactory owCF = CFUtil.createConnectionFactory("OPENWIRE", "tcp://localhost:61616");

      String topicName = "topicTest";

      server.addAddressInfo(new AddressInfo(topicName).addRoutingType(RoutingType.MULTICAST));

      int messageBodySize = 100;
      int largeMessageSize = 101 * 1024;
      String messageBody = "a".repeat(messageBodySize);
      String largeMessageBody = "a".repeat(largeMessageSize);

      int consumers = 200;
      int numberOfMessages = 10;
      int numberOfLargeMessages = 5;

      ExecutorService executorService = Executors.newFixedThreadPool(consumers);
      runAfter(executorService::shutdownNow);

      CyclicBarrier startFlag = new CyclicBarrier(consumers + 1);

      String endbody = "EndNow";

      CountDownLatch done = new CountDownLatch(consumers);

      AtomicInteger errors = new AtomicInteger(0);

      AtomicBoolean running = new AtomicBoolean(true);
      runAfter(() -> running.set(false));

      for (int i = 1; i <= consumers; i++) {
         final int consumerID = i;
         executorService.execute(() -> {
            try (Connection owConnection = owCF.createConnection()) {
               owConnection.setClientID("ow" + consumerID);
               Session owSession = owConnection.createSession(false, ActiveMQSession.CLIENT_ACKNOWLEDGE);
               owConnection.start();
               MessageConsumer consumer;
               Topic topic = owSession.createTopic(topicName);
               consumer = owSession.createDurableSubscriber(topic, "cons_" + consumerID);
               startFlag.await(10, TimeUnit.SECONDS);
               while (running.get()) {
                  TextMessage message = (TextMessage) consumer.receive(1000);
                  if (message != null) {
                     message.acknowledge();
                     if (message.getText().equals(endbody)) {
                        break;
                     }
                  }
               }
            } catch (Throwable e) {
               logger.warn(e.getMessage(), e);
               errors.incrementAndGet();
            } finally {
               done.countDown();
            }
         });
      }

      // aligning everybody after consumers are created
      startFlag.await(10, TimeUnit.SECONDS);

      PagingStoreImpl store = (PagingStoreImpl) server.getPagingManager().getPageStore(SimpleString.of(topicName));
      assertNotNull(store);

      if (changeWithPlugin) {
         server.getBrokerMessagePlugins().add(new ActiveMQServerMessagePlugin() {
            @Override
            public void beforeMessageRoute(Message message,
                                           RoutingContext context,
                                           boolean direct,
                                           boolean rejectDuplicates) throws ActiveMQException {
               // this is introducing a race that could happen
               message.getMemoryEstimate();
               message.setOwner(store);
               // to force lazy decode
               message.getPropertyNames();
               // it's meant to force a toString, what would happen on a debug message
               logger.debug("message {}", String.valueOf(message));
            }
         });
      }

      try (Connection amqpConnection = amqpCF.createConnection()) {
         Session amqpSession = amqpConnection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = amqpSession.createProducer(amqpSession.createTopic(topicName));
         for (int i = 0; i < numberOfMessages + numberOfLargeMessages; i++) {
            TextMessage message;
            if (i < numberOfMessages) {
               logger.info("message {}", i);
               message = amqpSession.createTextMessage(messageBody);
            } else {
               logger.info("largemessage {}", i);
               message = amqpSession.createTextMessage(largeMessageBody);
            }
            message.setIntProperty("i", i);
            message.setStringProperty("myvalue", "2");
            producer.send(message);
         }
         TextMessage endMessage = amqpSession.createTextMessage(endbody);
         endMessage.setStringProperty("end", "theEnd");
         endMessage.setStringProperty("myvalue", "2");
         producer.send(endMessage);
         amqpSession.commit();
      }

      assertTrue(done.await(1, TimeUnit.MINUTES));
      assertEquals(0, errors.get());
      running.set(false);

      try {
         Wait.assertEquals(0L, store::getAddressSize, 5000, 100);
      } catch (Throwable e) {
         logger.warn("error -> {}", e.getMessage(), e);
         throw e;
      }

   }
}
