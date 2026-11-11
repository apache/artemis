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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.apache.qpid.protonj2.test.driver.ProtonTestClientOptions;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test connections via Web Sockets
 */
@ExtendWith(ParameterizedTestExtension.class)
public class AmqpWebSocketConnectionTest extends AmqpClientTestSupport {

   @Parameter(index = 0)
   public boolean supportWSCompression;

   @Parameters(name = "supportWSCompression={0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {{true}, {false}});
   }

   @Override
   protected void configureAMQPAcceptorParameters(TransportConfiguration tc) {
      tc.getParams().put("webSocketCompressionSupported", supportWSCompression);
   }

   @TestTemplate
   public void testClientConnectsWithWebSocketCompressionOn() throws Exception {
      testClientConnectsWithWebSockets(true);
   }

   @TestTemplate
   public void testClientConnectsWithWebSocketCompressionOff() throws Exception {
      testClientConnectsWithWebSockets(false);
   }

   private void testClientConnectsWithWebSockets(boolean clientAsksForCompression) throws Exception {
      final ProtonTestClientOptions clientOpts = new ProtonTestClientOptions();

      clientOpts.setUseWebSockets(true);
      clientOpts.setWebSocketCompression(clientAsksForCompression);

      try (ProtonTestClient client = new ProtonTestClient(clientOpts)) {
         client.queueClientSaslAnonymousConnect();
         client.remoteOpen().queue();
         client.expectOpen();
         client.remoteBegin().queue();
         client.expectBegin();
         client.connect("localhost", AMQP_PORT);

         client.waitForScriptToComplete(5, TimeUnit.MINUTES);

         if (clientAsksForCompression && supportWSCompression) {
            assertTrue(client.isWSCompressionActive());
         } else {
            assertFalse(client.isWSCompressionActive());
         }

         client.expectAttach().ofSender();
         client.expectAttach().ofReceiver();
         client.expectFlow();

         // Attach a sender and receiver
         client.remoteAttach().ofReceiver()
                              .withName("ws-compression-test")
                              .withSource().withAddress(getQueueName())
                                           .withCapabilities("queue").also()
                              .withTarget().and()
                              .now();
         client.remoteFlow().withLinkCredit(10).now();
         client.remoteAttach().ofSender()
                              .withInitialDeliveryCount(0)
                              .withName("ws-compression-test")
                              .withTarget().withAddress(getQueueName())
                                           .withCapabilities("queue").also()
                              .withSource().and()
                              .now();

         client.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final String payload = "test-data:" + "A".repeat(1000);

         // Broker sends message to subscription and acknowledges to sender
         client.expectTransfer().withMessage().withValue(payload);
         client.expectDisposition().withSettled(true).withState().accepted();

         // Client sends message to queue with subscription
         client.remoteTransfer().withDeliveryId(0)
                                .withBody().withValue(payload).also()
                                .now();

         client.waitForScriptToComplete(5, TimeUnit.SECONDS);

         client.expectClose();
         client.remoteClose().now();

         client.waitForScriptToComplete(5, TimeUnit.SECONDS);
         client.close();
      }
   }
}
