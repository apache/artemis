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
package org.apache.activemq.artemis.tests.integration.stomp;

import javax.jms.MessageConsumer;
import javax.jms.TextMessage;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StompWithSecurityTest extends StompTestBase {

   public StompWithSecurityTest() {
      super("tcp+v10.stomp");
   }

   @Override
   public boolean isSecurityEnabled() {
      return true;
   }

   @Test
   public void testJMSXUserID() throws Exception {
      server.getConfiguration().setPopulateValidatedUser(true);

      MessageConsumer consumer = session.createConsumer(queue);

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(defUser, defPass);

      ClientStompFrame frame = conn.createFrame("SEND");
      frame.addHeader("destination", getQueuePrefix() + getQueueName());
      frame.setBody("Hello World");
      conn.sendFrame(frame);

      conn.disconnect();

      TextMessage message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals("Hello World", message.getText());
      // Assert default priority 4 is used when priority header is not set
      assertEquals(4, message.getJMSPriority(), "getJMSPriority");
      assertEquals("brianm", message.getStringProperty("JMSXUserID"), "JMSXUserID");

      // Make sure that the timestamp is valid - should
      // be very close to the current time.
      long tnow = System.currentTimeMillis();
      long tmsg = message.getJMSTimestamp();
      assertTrue(Math.abs(tnow - tmsg) < 1000);
   }

   @Test
   public void testSendMessageWithDifferentRoutingType() throws Exception {
      // validate presuppositions
      SimpleString queueName = SimpleString.of(getQueuePrefix() + getQueueName());
      assertNotNull(server.locateQueue(queueName));
      assertTrue(server.getAddressInfo(queueName).getRoutingTypes().contains(RoutingType.ANYCAST));
      assertFalse(server.getAddressInfo(queueName).getRoutingTypes().contains(RoutingType.MULTICAST));

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(onlySendCredential, onlySendCredential);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SEND);
      frame.addHeader(Stomp.Headers.Send.DESTINATION, queueName.toString());
      frame.setBody("Hello World");
      frame.addHeader(Stomp.Headers.Send.DESTINATION_TYPE, RoutingType.MULTICAST.toString());
      frame.addHeader(Stomp.Headers.RECEIPT_REQUESTED, UUID.randomUUID().toString());
      ClientStompFrame result = conn.sendFrame(frame);
      assertNotNull(result);
      assertEquals(Stomp.Responses.ERROR, result.getCommand());
      assertTrue(result.getHeader(Stomp.Headers.Error.MESSAGE).contains("AMQ229032"));

      conn.disconnect();

      assertTrue(server.getAddressInfo(queueName).getRoutingTypes().contains(RoutingType.ANYCAST));
      assertFalse(server.getAddressInfo(queueName).getRoutingTypes().contains(RoutingType.MULTICAST));
   }

   @Test
   public void testSubscribeWithDifferentRoutingType() throws Exception {
      // validate presuppositions
      SimpleString queueName = SimpleString.of(getQueuePrefix() + getQueueName());
      assertNotNull(server.locateQueue(queueName));
      assertTrue(server.getAddressInfo(queueName).getRoutingTypes().contains(RoutingType.ANYCAST));
      assertFalse(server.getAddressInfo(queueName).getRoutingTypes().contains(RoutingType.MULTICAST));

      StompClientConnection conn = StompClientConnectionFactory.createClientConnection(uri);
      conn.connect(onlyConsumeCredential, onlyConsumeCredential);

      ClientStompFrame frame = conn.createFrame(Stomp.Commands.SUBSCRIBE);
      frame.addHeader(Stomp.Headers.Subscribe.DESTINATION, queueName.toString());
      frame.addHeader(Stomp.Headers.Subscribe.SUBSCRIPTION_TYPE, RoutingType.MULTICAST.toString());
      frame.addHeader(Stomp.Headers.RECEIPT_REQUESTED, UUID.randomUUID().toString());
      ClientStompFrame result = conn.sendFrame(frame);
      assertNotNull(result);
      assertEquals(Stomp.Responses.ERROR, result.getCommand());
      assertTrue(result.getHeader(Stomp.Headers.Error.MESSAGE).contains("AMQ229032"));

      conn.disconnect();

      assertTrue(server.getAddressInfo(queueName).getRoutingTypes().contains(RoutingType.ANYCAST));
      assertFalse(server.getAddressInfo(queueName).getRoutingTypes().contains(RoutingType.MULTICAST));
   }
}
