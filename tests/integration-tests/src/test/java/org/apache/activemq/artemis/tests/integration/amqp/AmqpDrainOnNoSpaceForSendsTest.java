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

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.proton.amqp.transport.LinkError;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.junit.jupiter.api.Test;

public class AmqpDrainOnNoSpaceForSendsTest extends AmqpClientTestSupport {

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP";
   }

   @Override
   protected void configureAMQPAcceptorParameters(TransportConfiguration tc) {
      tc.getParams().put("amqpCredits", "3");
      tc.getParams().put("amqpLowCredits", "1");
   }

   @Override
   protected void configureAddressPolicy(ActiveMQServer server) {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch("#");
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings.setMaxSizeBytes(500);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      // Creates the broker used to make the outgoing connection. The port passed is for
      // that brokers acceptor. The test server connected to by the broker binds to a random port.
      return createServer(AMQP_PORT, false);
   }

   @Test
   public void testDrainCreditOnClientSendFailForMulticastAddress() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists(), 5000, 100);

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(100);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().rejected();
         peer.expectDetach();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "3").also()
                              .withBody().withString("Third Message: ")
                              .also()
                              .withDeliveryId(3)
                              .later(10);
         peer.remoteDetach().later(15);

         // Should be no new flow since the address remains full
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testDrainCreditOnClientSendFailForAnyCastAddress() throws Exception {
      server.start();
      server.createQueue(QueueConfiguration.of(getTestName()).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(getTestName())
                                                             .setAutoCreated(false));

      Wait.assertTrue(() -> server.queueQuery(SimpleString.of(getTestName())).isExists(), 5000, 100);

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

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
         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(100);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().rejected();
         peer.expectDetach();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "3").also()
                              .withBody().withString("Third Message: ")
                              .also()
                              .withDeliveryId(3)
                              .later(10);
         peer.remoteDetach().later(15);

         // Should be no new flow since the address remains full
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testDoesDrainCreditOnClientSendFailForAnonymousRelaySenderForMatchedAddressPolicy() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender")
                          .withDesiredCapabilities(AmqpSupport.ANONYMOUS_RELAY.toString()).now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists(), 5000, 100);

         final Queue queue1 = server.locateQueue(SimpleString.of("queue1"));
         final Queue queue2 = server.locateQueue(SimpleString.of("queue2"));

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(100);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "3").also()
                              .withBody().withString("Third Message: ")
                              .also()
                              .withDeliveryId(3)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(3);

         // Make capacity again which should now replenish credit.
         queue1.deleteQueue();
         queue2.deleteQueue();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().accepted();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "4").also()
                              .withBody().withString("Fourth Message: ")
                              .also()
                              .withDeliveryId(4)
                              .later(10);

         // Should be no new flow since the address remains full
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testDrainedAnonymousRelaySenderCanBurnCreditSendingToOtherAddresses() throws Exception {
      final String queueNameA = getTestName() + "-A";
      final String queueNameB = getTestName() + "-B";

      AddressSettings addressSettings1 = server.getAddressSettingsRepository().getMatch(queueNameA);
      addressSettings1.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings1.setMaxSizeBytes(500);
      server.getAddressSettingsRepository().addMatch(queueNameA, addressSettings1);

      AddressSettings addressSettings2 = server.getAddressSettingsRepository().getMatch(queueNameB);
      addressSettings2.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings2.setMaxSizeBytes(500);
      server.getAddressSettingsRepository().addMatch(queueNameB, addressSettings2);

      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender")
                          .withDesiredCapabilities(AmqpSupport.ANONYMOUS_RELAY.toString()).now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of(queueNameA).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(queueNameA)
                                                             .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of(queueNameB).setRoutingType(RoutingType.MULTICAST)
                                                             .setAddress(queueNameB)
                                                             .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(queueNameA)).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(queueNameB)).isExists(), 5000, 100);

         final Queue queue1 = server.locateQueue(SimpleString.of(queueNameA));

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(100);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(queueNameA).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(queueNameA).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().accepted();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(queueNameB).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "3").also()
                              .withBody().withString("Third Message: ")
                              .also()
                              .withDeliveryId(3)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(3);

         // Make capacity again which should now replenish credit.
         queue1.deleteQueue();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().accepted();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(queueNameA).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "4").also()
                              .withBody().withString("Fourth Message: ")
                              .also()
                              .withDeliveryId(4)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testDrainedAnonymousRelaySenderCreditNotReplenishedOnBurnOfExistingCredit() throws Exception {
      final String queueNameA = getTestName() + "-A";
      final String queueNameB = getTestName() + "-B";

      AddressSettings addressSettings1 = server.getAddressSettingsRepository().getMatch(queueNameA);
      addressSettings1.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings1.setMaxSizeBytes(500);
      server.getAddressSettingsRepository().addMatch(queueNameA, addressSettings1);

      AddressSettings addressSettings2 = server.getAddressSettingsRepository().getMatch(queueNameB);
      addressSettings2.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);
      addressSettings2.setMaxSizeBytes(500);
      server.getAddressSettingsRepository().addMatch(queueNameB, addressSettings2);

      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen().withOfferedCapability(AmqpSupport.ANONYMOUS_RELAY.toString());
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender")
                          .withDesiredCapabilities(AmqpSupport.ANONYMOUS_RELAY.toString()).now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of(queueNameA).setRoutingType(RoutingType.ANYCAST)
                                                             .setAddress(queueNameA)
                                                             .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of(queueNameB).setRoutingType(RoutingType.MULTICAST)
                                                             .setAddress(queueNameB)
                                                             .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(queueNameA)).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(queueNameB)).isExists(), 5000, 100);

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(100);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(queueNameA).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(queueNameA).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().accepted();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(queueNameB).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "3").also()
                              .withBody().withString("Third Message: ")
                              .also()
                              .withDeliveryId(3)
                              .later(10);

         // Create some traffic to ensure we don't see unexpected flow frames
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().respond();
         peer.remoteAttach().ofReceiver()
                            .withName("receiving-peer")
                            .withSource().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withTarget().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();
         peer.remoteDetach().later(1);

         // Should be no new flow since the address remains full
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testDrainAnonymousRelayOnceBlockPolicyHitsRejectMaxSizeThreshold() throws Exception {
      final String queueNameA = getTestName() + "A";
      final String queueNameB = getTestName() + "B";

      AddressSettings addressSettings1 = server.getAddressSettingsRepository().getMatch(queueNameA);
      addressSettings1.setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
      addressSettings1.setMaxSizeBytes(500);
      addressSettings1.setMaxSizeBytesRejectThreshold(1000);
      server.getAddressSettingsRepository().addMatch(queueNameA, addressSettings1);

      AddressSettings addressSettings2 = server.getAddressSettingsRepository().getMatch(queueNameB);
      addressSettings2.setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
      addressSettings2.setMaxSizeBytes(500);
      addressSettings2.setMaxSizeBytesRejectThreshold(1000);
      server.getAddressSettingsRepository().addMatch(queueNameB, addressSettings2);

      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen().withOfferedCapability(AmqpSupport.ANONYMOUS_RELAY.toString());
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender")
                          .withDesiredCapabilities(AmqpSupport.ANONYMOUS_RELAY.toString()).now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of(queueNameA).setRoutingType(RoutingType.MULTICAST)
                                                             .setAddress(queueNameA)
                                                             .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of(queueNameB).setRoutingType(RoutingType.MULTICAST)
                                                             .setAddress(queueNameB)
                                                             .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(queueNameA)).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of(queueNameB)).isExists(), 5000, 100);

         Queue queue1 = server.locateQueue(SimpleString.of(queueNameA));

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(1024);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(queueNameA).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(queueNameA).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: " + payload)
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         // Make capacity again which should now replenish credit once remote send drain response.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender();
         peer.expectDetach();
         peer.remoteAttach().ofReceiver()
                            .withName("receiving-peer")
                            .withSource().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withTarget().also()
                            .now();
         peer.remoteDetach().later(1);

         queue1.deleteQueue();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(3).withDeliveryCount(3);

         // Respond drained with one credit outstanding, remote should then grant a new batch
         peer.remoteFlow().withLinkCredit(0).withDeliveryCount(3).withDrain(true).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testDoesNotDrainCreditOnClientSendFailIfAcceptorConfiguredNotTo() throws Exception {
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().addAcceptorConfiguration("server",
         "tcp://localhost:" + AMQP_PORT + "?amqpCredits=3&amqpLowCredits=0&amqpDrainOnTransientDeliveryErrors=false");
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists(), 5000, 100);

         final Queue queue1 = server.locateQueue(SimpleString.of("queue1"));
         final Queue queue2 = server.locateQueue(SimpleString.of("queue2"));

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(100);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "3").also()
                              .withBody().withString("Third Message: ")
                              .also()
                              .withDeliveryId(3)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(3);

         // Make capacity again which should now replenish credit.
         queue1.deleteQueue();
         queue2.deleteQueue();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDisposition().withState().accepted();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "4").also()
                              .withBody().withString("Fourth Message: ")
                              .also()
                              .withDeliveryId(4)
                              .later(10);

         // Should be no new flow since the address remains full
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testDrainCreditOnClientSendFailPeerAnsweresDrainedButFlowsOnlyAfterSpaceCleared() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists(), 5000, 100);

         final Queue queue1 = server.locateQueue(SimpleString.of("queue1"));
         final Queue queue2 = server.locateQueue(SimpleString.of("queue2"));

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(100);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.remoteFlow().withLinkCredit(0).withDeliveryCount(3).withDrain(true).now();

         // The address should still be full so nothing expected until the queues are deleted
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(3).withDeliveryCount(3);

         // Make capacity again which should now replenish credit.
         queue1.deleteQueue();
         queue2.deleteQueue();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testDrainCreditOnClientSendFailNoNewCreditUntilPeerAnswersDrained() throws Exception {
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);

         Queue queue1 = server.locateQueue(SimpleString.of("queue1"));

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(100);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         // Make capacity again which should now replenish credit once remote send drain response.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         queue1.deleteQueue();

         peer.expectFlow().withLinkCredit(3).withDeliveryCount(3);

         // Respond drained with one credit outstanding, remote should then grant a new batch
         peer.remoteFlow().withLinkCredit(0).withDeliveryCount(3).withDrain(true).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testSenderClosedIfRemoteTimesOutWaitingForDrainToComplete() throws Exception {
      server.getConfiguration().getAcceptorConfigurations().clear();
      server.getConfiguration().addAcceptorConfiguration("server",
         "tcp://localhost:" + AMQP_PORT + "?amqpCredits=3&amqpLowCredits=0&amqpLinkQuiesceTimeout=1");
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));
         server.createQueue(QueueConfiguration.of("queue2").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);
         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue2")).isExists(), 5000, 100);

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(100);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();
         peer.expectDetach().withError(LinkError.DETACH_FORCED.toString());

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withProperties().withTo(getTestName()).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         // Create some traffic to ensure we don't see unexpected flow frames
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender().respond();
         peer.remoteAttach().ofReceiver()
                            .withName("receiving-peer")
                            .withSource().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withTarget().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectDetach().respond();
         peer.remoteDetach().later(1);

         // Should be no new flow since the address remains full
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   public void testDrainOnceBlockPolicyHitsRejectMaxSizeThreshold() throws Exception {
      AddressSettings addressSettings = server.getAddressSettingsRepository().getMatch("#");
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
      addressSettings.setMaxSizeBytes(500);
      addressSettings.setMaxSizeBytesRejectThreshold(1000);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);
      server.start();

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", AMQP_PORT);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow().withLinkCredit(3);

         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         server.createQueue(QueueConfiguration.of("queue1").setRoutingType(RoutingType.MULTICAST)
                                                           .setAddress(getTestName())
                                                           .setAutoCreated(false));

         Wait.assertTrue(() -> server.queueQuery(SimpleString.of("queue1")).isExists(), 5000, 100);

         Queue queue1 = server.locateQueue(SimpleString.of("queue1"));

         peer.expectDisposition().withState().accepted();

         final String payload = "A".repeat(1024);

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "1").also()
                              .withBody().withString("First Message: " + payload)
                              .also()
                              .withDeliveryId(1)
                              .now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(1).withDrain(true);
         peer.expectDisposition().withState().rejected();

         peer.remoteTransfer().withHeader().withDurability(true).also()
                              .withApplicationProperties().withProperty("color", "red").also()
                              .withMessageAnnotations().withAnnotation("x-opt-test", "2").also()
                              .withBody().withString("Second Message: ")
                              .also()
                              .withDeliveryId(2)
                              .later(10);

         // Make capacity again which should now replenish credit once remote sends drain response.
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectAttach().ofSender();
         peer.expectDetach();
         peer.remoteAttach().ofReceiver()
                            .withName("receiving-peer")
                            .withSource().withAddress(getTestName())
                                         .withCapabilities("topic").also()
                            .withTarget().also()
                            .now();
         peer.remoteDetach().later(1);

         queue1.deleteQueue();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectFlow().withLinkCredit(3).withDeliveryCount(3);

         // Respond drained with one credit outstanding, remote should then grant a new batch
         peer.remoteFlow().withLinkCredit(0).withDeliveryCount(3).withDrain(true).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.expectClose();
         peer.remoteClose().now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }
}
