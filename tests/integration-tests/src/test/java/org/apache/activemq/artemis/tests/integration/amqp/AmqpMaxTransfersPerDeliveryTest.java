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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.qpid.proton.amqp.transport.LinkError;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for configuration of the max transfers per delivery option
 */
class AmqpMaxTransfersPerDeliveryTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP";
   }

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("maxTransfersPerDelivery", "3");
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, true);
   }

   @Test
   @Timeout(20)
   public void testFailureOnViolationOfPerDeliveryTransferLimit() throws Exception {
      byte[] first = "A".repeat(10).getBytes(StandardCharsets.UTF_8);
      byte[] second = "B".repeat(10).getBytes(StandardCharsets.UTF_8);
      byte[] third = "C".repeat(10).getBytes(StandardCharsets.UTF_8);
      byte[] fourth = "D".repeat(10).getBytes(StandardCharsets.UTF_8);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", AMQP_PORT);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow();
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("queue").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.remoteTransfer().withDeliveryId(0)
                              .withDeliveryTag(new byte[] {0})
                              .withMore(true)
                              .withMessageFormat(0)
                              .withBody().withData(first).also().now();
         peer.remoteTransfer().withDeliveryId(0)
                              .withMore(true)
                              .withBody().withData(second).also().now();
         peer.remoteTransfer().withDeliveryId(0)
                              .withMore(true)
                              .withBody().withData(third).also().now();
         peer.remoteTransfer().withDeliveryId(0)
                              .withMore(false)
                              .withBody().withData(fourth).also().now();

         peer.expectClose().withError(LinkError.TRANSFER_LIMIT_EXCEEDED.toString());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFailureOnViolationOfPerDeliveryTransferLimitWithEmptyTransfer() throws Exception {
      byte[] first = "A".repeat(10).getBytes(StandardCharsets.UTF_8);
      byte[] second = "B".repeat(10).getBytes(StandardCharsets.UTF_8);
      byte[] third = "C".repeat(10).getBytes(StandardCharsets.UTF_8);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", AMQP_PORT);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow();
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("queue").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.remoteTransfer().withDeliveryId(0)
                              .withDeliveryTag(new byte[] {0})
                              .withMore(true)
                              .withMessageFormat(0)
                              .withBody().withData(first).also().now();
         peer.remoteTransfer().withDeliveryId(0)
                              .withMore(true)
                              .now();
         peer.remoteTransfer().withDeliveryId(0)
                              .withMore(true)
                              .withBody().withData(second).also().now();
         peer.remoteTransfer().withDeliveryId(0)
                              .withMore(false)
                              .withBody().withData(third).also().now();

         peer.expectClose().withError(LinkError.TRANSFER_LIMIT_EXCEEDED.toString());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.close();
      }
   }

   @Test
   @Timeout(20)
   public void testFailureOnViolationOfPerDeliveryTransferLimitWithAllEmptyTransfers() throws Exception {
      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", AMQP_PORT);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow();
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("queue").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.remoteTransfer().withDeliveryId(0)
                              .withDeliveryTag(new byte[] {0})
                              .withMore(true)
                              .withMessageFormat(0)
                              .now();
         peer.remoteTransfer().withDeliveryId(0)
                              .withMore(true)
                              .now();
         peer.remoteTransfer().withDeliveryId(0)
                              .withMore(true)
                              .now();
         peer.remoteTransfer().withDeliveryId(0)
                              .withMore(false)
                              .now();

         peer.expectClose().withError(LinkError.TRANSFER_LIMIT_EXCEEDED.toString());

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.close();
      }
   }
}
