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

import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.tests.integration.jms.RedeployTest;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.apache.qpid.protonj2.test.driver.ProtonTestClientOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Test connections via Web Sockets
 */
public class AmqpWebSocketCompressionConfigTest extends AmqpClientTestSupport {

   private static final int SERVER_PORT = 5678;

   final URL urlServerWSCoff = RedeployTest.class.getClassLoader().getResource("ws-compression-disabled.xml");
   final URL urlServerWSCon = RedeployTest.class.getClassLoader().getResource("ws-compression-enabled.xml");

   @Override
   protected ActiveMQServer createServer() throws Exception {
      return createServer(AMQP_PORT, false);
   }

   @Test
   @Timeout(20)
   public void testClientConnectsWithWebSocketCompressionOn() throws Exception {
      final EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();

      try {
         embeddedActiveMQ.setConfigResourcePath(urlServerWSCon.toURI().toString());
         embeddedActiveMQ.start();

         testClientConnectsWithWebSockets(true);
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   @Test
   @Timeout(20)
   public void testClientConnectsWithWebSocketCompressionOff() throws Exception {
      final EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();

      try {
         embeddedActiveMQ.setConfigResourcePath(urlServerWSCoff.toURI().toString());
         embeddedActiveMQ.start();

         testClientConnectsWithWebSockets(false);
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   private void testClientConnectsWithWebSockets(boolean serverCompressionOn) throws Exception {
      final ProtonTestClientOptions clientOpts = new ProtonTestClientOptions();

      clientOpts.setUseWebSockets(true);
      clientOpts.setWebSocketCompression(true);

      try (ProtonTestClient client = new ProtonTestClient(clientOpts)) {
         client.queueClientSaslAnonymousConnect();
         client.remoteOpen().queue();
         client.expectOpen();
         client.remoteBegin().queue();
         client.expectBegin();
         client.connect("localhost", SERVER_PORT);

         client.waitForScriptToComplete(5, TimeUnit.MINUTES);

         if (serverCompressionOn) {
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
