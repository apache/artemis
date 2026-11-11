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

import static org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport.createMapMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.protocol.amqp.converter.coreWrapper.CoreMapMessageWrapper;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSender;
import org.apache.activemq.transport.amqp.client.AmqpSession;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.UnsignedByte;
import org.apache.qpid.proton.amqp.UnsignedInteger;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.UnsignedShort;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpManagementTest extends AmqpClientTestSupport {

   private static final Binary BINARY_CORRELATION_ID = new Binary("mystring".getBytes(StandardCharsets.UTF_8));

   @Test
   @Timeout(60)
   public void testManagementQueryOverAMQP() throws Throwable {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         String destinationAddress = getQueueName(1);
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender("activemq.management");
         AmqpReceiver receiver = session.createReceiver(destinationAddress);
         receiver.flow(10);

         // Create request message for getQueueNames query
         AmqpMessage request = new AmqpMessage();
         request.setApplicationProperty("_AMQ_ResourceName", ResourceNames.BROKER);
         request.setApplicationProperty("_AMQ_OperationName", "getQueueNames");
         request.setReplyToAddress(destinationAddress);
         request.setText("[]");

         sender.send(request);
         AmqpMessage response = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(response);
         assertNotNull(response);
         Object section = response.getWrappedMessage().getBody();
         assertInstanceOf(AmqpValue.class, section);
         Object value = ((AmqpValue) section).getValue();
         assertInstanceOf(String.class, value);
         assertFalse(((String) value).isEmpty());
         assertTrue(((String) value).contains(destinationAddress));
         response.accept();
      } finally {
         connection.close();
      }
   }

   /**
    * Some clients use Unsigned types from org.apache.qpid.proton.amqp
    */
   @Test
   @Timeout(60)
   public void testUnsignedValues() throws Exception {
      int sequence = 42;
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("sequence", new UnsignedInteger(sequence));
      CoreMapMessageWrapper msg = createMapMessage(1, map, null);
      assertEquals(sequence, msg.getInt("sequence"));

      map.clear();
      map.put("sequence", new UnsignedLong(sequence));
      msg = createMapMessage(1, map, null);
      assertEquals(sequence, msg.getLong("sequence"));

      map.clear();
      map.put("sequence", new UnsignedShort((short)sequence));
      msg = createMapMessage(1, map, null);
      assertEquals(sequence, msg.getShort("sequence"));

      map.clear();
      map.put("sequence", new UnsignedByte((byte) sequence));
      msg = createMapMessage(1, map, null);
      assertEquals(sequence, msg.getByte("sequence"));
   }

   @Test
   @Timeout(60)
   public void testCorrelationByMessageIDUUID() throws Throwable {
      doTestReplyCorrelation(UUID.randomUUID(), false);
   }

   @Test
   @Timeout(60)
   public void testCorrelationByMessageIDString() throws Throwable {
      doTestReplyCorrelation("mystring", false);
   }

   @Test
   @Timeout(60)
   public void testCorrelationByMessageIDBinary() throws Throwable {
      doTestReplyCorrelation(BINARY_CORRELATION_ID, false);
   }

   @Test
   @Timeout(60)
   public void testCorrelationByCorrelationIDUUID() throws Throwable {
      doTestReplyCorrelation(UUID.randomUUID(), true);
   }

   @Test
   @Timeout(60)
   public void testCorrelationByCorrelationIDString() throws Throwable {
      doTestReplyCorrelation("mystring", true);
   }

   @Test
   @Timeout(60)
   public void testCorrelationByCorrelationIDBinary() throws Throwable {
      doTestReplyCorrelation(BINARY_CORRELATION_ID, true);
   }

   private void doTestReplyCorrelation(final Object correlationId, final boolean sendCorrelAsCorrelation) throws Exception {
      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.connect());

      try {
         String destinationAddress = getQueueName(1);
         AmqpSession session = connection.createSession();
         AmqpSender sender = session.createSender("activemq.management");
         AmqpReceiver receiver = session.createReceiver(destinationAddress);
         receiver.flow(10);

         // Create request message for getQueueNames query
         AmqpMessage request = new AmqpMessage();
         request.setApplicationProperty("_AMQ_ResourceName", ResourceNames.BROKER);
         request.setApplicationProperty("_AMQ_OperationName", "getQueueNames");
         request.setReplyToAddress(destinationAddress);
         if (sendCorrelAsCorrelation) {
            request.setRawCorrelationId(correlationId);
         } else {
            request.setRawMessageId(correlationId);
         }
         request.setText("[]");

         sender.send(request);
         AmqpMessage response = receiver.receive(5, TimeUnit.SECONDS);
         assertNotNull(response);
         assertEquals(correlationId, response.getRawCorrelationId());
         response.accept();
      } finally {
         connection.close();
      }
   }
}
