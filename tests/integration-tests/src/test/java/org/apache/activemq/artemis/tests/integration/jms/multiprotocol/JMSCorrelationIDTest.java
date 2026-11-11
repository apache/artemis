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
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.utils.RandomUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/*
 * JMS supports setting the correlation ID as a String or a byte[]. However, OpenWire only supports correlation ID as
 * a String. When it is set as a byte[] the OpenWire JMS client just converts it to a UTF-8 encoded String, and
 * therefore when it sends a JMS message with a correlation ID the broker can't tell if the value was set as a String
 * or a byte[]. Due to this ambiguity the broker is hard-coded to treat the incoming OpenWire value as a String. This
 * doesn't cause any problems if the consumer is also OpenWire, but if the consumer is Core or AMQP (which both
 * differentiate between String and binary values) then retrieving the correlation ID as a byte[] will fail and nothing
 * can be done about it aside from updating the OpenWire protocol.
 *
 * Therefore, all the tests which involve the OpenWire JMS client using Message.setJMSCorrelationIDAsBytes() on a
 * message sent to a different JMS implementation are ignored. The test are ignored rather that being completely
 * removed to make clear this was an explicit decision not to test & support this use-case.
 */
public class JMSCorrelationIDTest extends MultiprotocolJMSClientTestSupport {

   private void testCorrelationIDAsBytesSendReceive(Connection producerConnection, Connection consumerConnection) throws Throwable {
      Session session = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue(getQueueName());

      byte[] bytes = new byte[0xf + 1];
      for (int i = 0; i <= 0xf; i++) {
         bytes[i] = (byte) i;
      }

      MessageProducer producer = session.createProducer(queue);
      Message message = session.createMessage();
      message.setJMSCorrelationIDAsBytes(bytes);
      producer.send(message);
      producer.close();

      Session sessionConsumer = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue consumerQueue = sessionConsumer.createQueue(getQueueName());
      final MessageConsumer consumer = sessionConsumer.createConsumer(consumerQueue);

      Message m = consumer.receive(5000);
      assertNotNull(m, "Could not receive message on consumer");

      assertArrayEquals(bytes, m.getJMSCorrelationIDAsBytes());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsBytesSendReceiveFromAMQPToAMQP() throws Throwable {
      testCorrelationIDAsBytesSendReceive(createConnection(), createConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsBytesSendReceiveFromAMQPToCore() throws Throwable {
      testCorrelationIDAsBytesSendReceive(createConnection(), createCoreConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsBytesSendReceiveFromAMQPToOpenWire() throws Throwable {
      testCorrelationIDAsBytesSendReceive(createConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsBytesSendReceiveFromCoreToCore() throws Throwable {
      testCorrelationIDAsBytesSendReceive(createCoreConnection(), createCoreConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsBytesSendReceiveFromCoreToAMQP() throws Throwable {
      testCorrelationIDAsBytesSendReceive(createCoreConnection(), createConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsBytesSendReceiveFromCoreToOpenWire() throws Throwable {
      testCorrelationIDAsBytesSendReceive(createCoreConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsBytesSendReceiveFromOpenWireToOpenWire() throws Throwable {
      testCorrelationIDAsBytesSendReceive(createOpenWireConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(60)
   @Disabled
   public void testCorrelationIDAsBytesSendReceiveFromOpenWireToAMQP() throws Throwable {
      testCorrelationIDAsBytesSendReceive(createOpenWireConnection(), createConnection());
   }

   @Test
   @Timeout(60)
   @Disabled
   public void testCorrelationIDAsBytesSendReceiveFromOpenWireToCore() throws Throwable {
      testCorrelationIDAsBytesSendReceive(createOpenWireConnection(), createCoreConnection());
   }

   private void testCorrelationIDAsStringSendReceive(Connection producerConnection, Connection consumerConnection) throws Throwable {
      final String correlationId = RandomUtil.randomUUIDString();

      Session session = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue(getQueueName());

      MessageProducer producer = session.createProducer(queue);
      Message message = session.createMessage();
      message.setJMSCorrelationID(correlationId);
      producer.send(message);
      producer.close();

      Session sessionConsumer = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue consumerQueue = sessionConsumer.createQueue(getQueueName());
      final MessageConsumer consumer = sessionConsumer.createConsumer(consumerQueue);

      Message m = consumer.receive(5000);
      assertNotNull(m, "Could not receive message on consumer");

      assertEquals(correlationId, m.getJMSCorrelationID());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsStringSendReceiveFromAMQPToAMQP() throws Throwable {
      testCorrelationIDAsStringSendReceive(createConnection(), createConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsStringSendReceiveFromAMQPToCore() throws Throwable {
      testCorrelationIDAsStringSendReceive(createConnection(), createCoreConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsStringSendReceiveFromAMQPToOpenWire() throws Throwable {
      testCorrelationIDAsStringSendReceive(createConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsStringSendReceiveFromCoreToCore() throws Throwable {
      testCorrelationIDAsStringSendReceive(createCoreConnection(), createCoreConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsStringSendReceiveFromCoreToAMQP() throws Throwable {
      testCorrelationIDAsStringSendReceive(createCoreConnection(), createConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsStringSendReceiveFromCoreToOpenWire() throws Throwable {
      testCorrelationIDAsStringSendReceive(createCoreConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsStringSendReceiveFromOpenWireToOpenWire() throws Throwable {
      testCorrelationIDAsStringSendReceive(createOpenWireConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsStringSendReceiveFromOpenWireToAMQP() throws Throwable {
      testCorrelationIDAsStringSendReceive(createOpenWireConnection(), createConnection());
   }

   @Test
   @Timeout(60)
   public void testCorrelationIDAsStringSendReceiveFromOpenWireToCore() throws Throwable {
      testCorrelationIDAsStringSendReceive(createOpenWireConnection(), createCoreConnection());
   }
}