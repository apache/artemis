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
package org.apache.activemq.artemis.tests.integration.amqp;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests that validate that an AMQP protocol level error is handled and connections are closed.
 */
public class AMQPProtocolErrorHandlingTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(30)
   public void testBrokerHandlesOutOfOrderDeliveryIdInTransfer() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                      .setAddress("test")
                                                      .setAutoCreated(false));

      try (ProtonTestClient receivingPeer = new ProtonTestClient()) {
         receivingPeer.queueClientSaslAnonymousConnect();
         receivingPeer.connect("localhost", AMQP_PORT);
         receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Broker response
         receivingPeer.expectOpen();
         receivingPeer.expectBegin();
         receivingPeer.expectAttach().ofSender();
         receivingPeer.expectAttach().ofReceiver();
         receivingPeer.expectFlow();

         // Attach a sender and receiver
         receivingPeer.remoteOpen().withContainerId("test-sender").now();
         receivingPeer.remoteBegin().withNextOutgoingId(100).now();
         receivingPeer.remoteAttach().ofReceiver()
                                     .withName("transfer-test")
                                     .withSource().withAddress("test")
                                                  .withCapabilities("queue").also()
                                     .withTarget().and()
                                     .now();
         receivingPeer.remoteFlow().withLinkCredit(10).now();
         receivingPeer.remoteAttach().ofSender()
                                     .withInitialDeliveryCount(0)
                                     .withName("transfer-test")
                                     .withTarget().withAddress("test")
                                                  .withCapabilities("queue").also()
                                     .withSource().and()
                                     .now();

         receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists(), 5000, 100);
         Wait.assertEquals(1, () -> server.locateQueue(SimpleString.of("test")).getConsumerCount(), 5000, 100);

         // Broker response
         receivingPeer.expectTransfer();
         receivingPeer.expectDisposition().withSettled(true).withState().accepted();

         // Initial message with correct delivery ID
         receivingPeer.remoteTransfer().withDeliveryId(100)
                                       .withBody().withValue("test").also()
                                       .withMessageAnnotations().withAnnotation("x-opt-jms-dest", 0).also()
                                       .now();

         receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         receivingPeer.expectClose().withError(AmqpError.INTERNAL_ERROR.toString()).respond();

         logger.info("Sent transfer with delivery ID:100, now sending incorrect delivery ID:99");

         Wait.assertEquals(1, () -> server.locateQueue(SimpleString.of("test")).getDeliveringCount(), 5000, 100);

         // Next message with incorrect delivery ID (should be 101 but send as 99)
         receivingPeer.remoteTransfer().withDeliveryId(99)
                                       .withBody().withValue("test").also()
                                       .withMessageAnnotations().withAnnotation("x-opt-jms-dest", 0).also()
                                       .now();

         Wait.assertEquals(0, () -> server.locateQueue(SimpleString.of("test")).getConsumerCount(), 5000, 100);
         Wait.assertEquals(0, () -> server.locateQueue(SimpleString.of("test")).getDeliveringCount(), 5000, 100);

         receivingPeer.waitForScriptToComplete();
      }
   }

   @Test
   @Timeout(30)
   public void testBrokerHandlesNewTransferSentBeforeLastTransferCompleted() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST)
                                                      .setAddress("test")
                                                      .setAutoCreated(false));

      try (ProtonTestClient receivingPeer = new ProtonTestClient()) {
         receivingPeer.queueClientSaslAnonymousConnect();
         receivingPeer.connect("localhost", AMQP_PORT);
         receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Broker response
         receivingPeer.expectOpen();
         receivingPeer.expectBegin();
         receivingPeer.expectAttach().ofSender();
         receivingPeer.expectAttach().ofReceiver();
         receivingPeer.expectFlow();

         // Attach a sender and receiver
         receivingPeer.remoteOpen().withContainerId("test-sender").now();
         receivingPeer.remoteBegin().withNextOutgoingId(100).now();
         receivingPeer.remoteAttach().ofReceiver()
                                     .withName("transfer-test")
                                     .withSource().withAddress("test")
                                                  .withCapabilities("queue").also()
                                     .withTarget().and()
                                     .now();
         receivingPeer.remoteFlow().withLinkCredit(10).now();
         receivingPeer.remoteAttach().ofSender()
                                     .withInitialDeliveryCount(0)
                                     .withName("transfer-test")
                                     .withTarget().withAddress("test")
                                                  .withCapabilities("queue").also()
                                     .withSource().and()
                                     .now();

         receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("test")).isExists(), 5000, 100);
         Wait.assertEquals(1, () -> server.locateQueue(SimpleString.of("test")).getConsumerCount(), 5000, 100);

         // Initial message with correct delivery ID indicating more bytes to come
         receivingPeer.remoteTransfer().withDeliveryId(100)
                                       .withBody().withValue("test").also()
                                       .withMessageAnnotations().withAnnotation("x-opt-jms-dest", 0).also()
                                       .withMore(true)
                                       .now();

         receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         receivingPeer.expectClose().withError(AmqpError.INTERNAL_ERROR.toString()).respond();

         logger.info("Sent transfer with delivery ID:100, now sending incorrect delivery ID:101");

         // Last delivery indicated more transfer frames incoming, but we start a new transfer ID:101
         receivingPeer.remoteTransfer().withDeliveryId(101)
                                       .withBody().withValue("test").also()
                                       .withMessageAnnotations().withAnnotation("x-opt-jms-dest", 0).also()
                                       .now();

         Wait.assertEquals(0, () -> server.locateQueue(SimpleString.of("test")).getConsumerCount(), 5000, 100);
         Wait.assertEquals(0, () -> server.locateQueue(SimpleString.of("test")).getDeliveringCount(), 5000, 100);

         receivingPeer.waitForScriptToComplete();
      }
   }
}
