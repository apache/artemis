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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class AmqpIngressTimestampTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public int amqpMinLargeMessageSize = ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE;

   @Parameters(name = "restart={0}, large={1}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
         {true, true},
         {false, false},
         {true, false},
         {false, true}
      });
   }

   @Parameter(index = 0)
   public boolean restart;

   @Parameter(index = 1)
   public boolean large;

   @TestTemplate
   @Timeout(60)
   public void testIngressTimestampSendCore() throws Exception {
      internalTestIngressTimestamp(Protocol.CORE);
   }

   @TestTemplate
   @Timeout(60)
   public void testIngressTimestampSendAMQP() throws Exception {
      internalTestIngressTimestamp(Protocol.AMQP);
   }

   @TestTemplate
   @Timeout(60)
   public void testIngressTimestampSendOpenWire() throws Exception {
      internalTestIngressTimestamp(Protocol.OPENWIRE);
   }

   private void internalTestIngressTimestamp(Protocol protocol) throws Exception {
      final String QUEUE_NAME = RandomUtil.randomUUIDString();
      server.createQueue(QueueConfiguration.of(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST));
      server.getAddressSettingsRepository().addMatch(QUEUE_NAME, new AddressSettings().setEnableIngressTimestamp(true));
      long beforeSend = System.currentTimeMillis();
      if (protocol == Protocol.CORE) {
         sendMessagesCore(QUEUE_NAME, 1, true, getMessagePayload());
      } else if (protocol == Protocol.OPENWIRE) {
         sendMessagesOpenWire(QUEUE_NAME, 1, true, getMessagePayload());
      } else {
         sendMessages(QUEUE_NAME, 1, true, getMessagePayload());
      }
      long afterSend = System.currentTimeMillis();

      if (restart) {
         server.stop();
         server.start();
         assertTrue(server.waitForActivation(3, TimeUnit.SECONDS));
      }

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());
      AmqpSession session = connection.createSession();

      AmqpReceiver receiver = session.createReceiver(QUEUE_NAME);

      Queue queueView = getProxyToQueue(QUEUE_NAME);
      Wait.assertEquals(1L, queueView::getMessageCount, 2000, 100, false);

      receiver.flow(1);
      AmqpMessage receive = receiver.receive(5, TimeUnit.SECONDS);
      assertNotNull(receive);
      logger.info("{}", receive);
      Object ingressTimestampHeader = receive.getMessageAnnotation(AMQPMessageSupport.X_OPT_INGRESS_TIME);
      assertNotNull(ingressTimestampHeader);
      assertInstanceOf(Long.class, ingressTimestampHeader);
      long ingressTimestamp = (Long) ingressTimestampHeader;
      assertTrue(ingressTimestamp >= beforeSend && ingressTimestamp <= afterSend, "Ingress timstamp " + ingressTimestamp + " should be >= " + beforeSend + " and <= " + afterSend);
      receiver.close();

      assertEquals(1, queueView.getMessageCount());

      connection.close();
   }

   @Override
   protected String getConfiguredProtocols() {
      return "AMQP,OPENWIRE,CORE";
   }

   private enum Protocol {
      CORE, AMQP, OPENWIRE
   }

   @Override
   protected void setData(AmqpMessage amqpMessage) throws Exception {
      amqpMessage.setBytes(getMessagePayload());
   }

   @Override
   protected void configureAMQPAcceptorParameters(Map<String, Object> params) {
      params.put("amqpMinLargeMessageSize", ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);
   }

   private byte[] getMessagePayload() {
      return large ? "AB".repeat(ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE * 20).getBytes() : "AB".getBytes();
   }
}
