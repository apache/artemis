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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSMessagePropertiesTest extends MultiprotocolJMSClientTestSupport {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Timeout(30)
   public void testMessagePropertiesAMQPProducerCoreConsumer() throws Exception {
      testMessageProperties(createConnection(), createCoreConnection());
   }

   @Test
   @Timeout(30)
   public void testMessagePropertiesAMQPProducerAMQPConsumer() throws Exception {
      testMessageProperties(createConnection(), createConnection());
   }

   @Test
   @Timeout(30)
   public void testMessagePropertiesAMQPProducerOpenWireConsumer() throws Exception {
      testMessageProperties(createConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(30)
   public void testMessagePropertiesCoreProducerAMQPConsumer() throws Exception {
      testMessageProperties(createCoreConnection(), createConnection());
   }

   @Test
   @Timeout(30)
   public void testMessagePropertiesCoreProducerCoreConsumer() throws Exception {
      testMessageProperties(createCoreConnection(), createCoreConnection());
   }

   @Test
   @Timeout(30)
   public void testMessagePropertiesCoreProducerOpenWireConsumer() throws Exception {
      testMessageProperties(createCoreConnection(), createOpenWireConnection());
   }

   @Test
   @Timeout(30)
   public void testMessagePropertiesOpenWireProducerAMQPConsumer() throws Exception {
      testMessageProperties(createOpenWireConnection(), createConnection());
   }

   @Test
   @Timeout(30)
   public void testMessagePropertiesOpenWireProducerCoreConsumer() throws Exception {
      testMessageProperties(createOpenWireConnection(), createCoreConnection());
   }

   @Test
   @Timeout(30)
   public void testMessagePropertiesOpenWireProducerOpenWireConsumer() throws Exception {
      testMessageProperties(createOpenWireConnection(), createOpenWireConnection());
   }

   private void testMessageProperties(Connection producerConnection, Connection consumerConnection) throws JMSException {
      final String stringPropertyName = "myStringProperty";
      final String stringPropertyValue = RandomUtil.randomUUIDString();
      final String intPropertyName = "myIntProperty";
      final int intPropertyValue = RandomUtil.randomInt();
      final String longPropertyName = "myLongProperty";
      final long longPropertyValue = RandomUtil.randomLong();
      final String shortPropertyName = "myShortProperty";
      final short shortPropertyValue = RandomUtil.randomShort();
      final String doublePropertyName = "myDoubleProperty";
      final double doublePropertyValue = RandomUtil.randomDouble();
      final String floatPropertyName = "myFloatProperty";
      final float floatPropertyValue = RandomUtil.randomFloat();
      final String bytePropertyName = "myByteProperty";
      final byte bytePropertyValue = RandomUtil.randomByte();
      final String booleanPropertyName = "myBooleanProperty";
      final boolean booleanPropertyValue = RandomUtil.randomBoolean();

      try {
         Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         final MessageConsumer consumer = consumerSession.createConsumer(consumerSession.createQueue(getQueueName()));

         MessageProducer producer = producerSession.createProducer(producerSession.createQueue(getQueueName()));
         producerConnection.start();

         Message message = producerSession.createMessage();
         message.setStringProperty(stringPropertyName, stringPropertyValue);
         message.setIntProperty(intPropertyName, intPropertyValue);
         message.setLongProperty(longPropertyName, longPropertyValue);
         message.setShortProperty(shortPropertyName, shortPropertyValue);
         message.setDoubleProperty(doublePropertyName, doublePropertyValue);
         message.setFloatProperty(floatPropertyName, floatPropertyValue);
         message.setByteProperty(bytePropertyName, bytePropertyValue);
         message.setBooleanProperty(booleanPropertyName, booleanPropertyValue);
         producer.send(message);

         Message received = consumer.receive(100);

         assertNotNull(received, "Should have received a message by now.");

         assertEquals(stringPropertyValue, received.getStringProperty(stringPropertyName));
         assertEquals(intPropertyValue, received.getIntProperty(intPropertyName));
         assertEquals(longPropertyValue, received.getLongProperty(longPropertyName));
         assertEquals(shortPropertyValue, received.getShortProperty(shortPropertyName));
         assertEquals(doublePropertyValue, received.getDoubleProperty(doublePropertyName), 0.0);
         assertEquals(floatPropertyValue, received.getFloatProperty(floatPropertyName), 0.0);
         assertEquals(bytePropertyValue, received.getByteProperty(bytePropertyName));
         assertEquals(booleanPropertyValue, received.getBooleanProperty(booleanPropertyName));
      } finally {
         producerConnection.close();
         consumerConnection.close();
      }
   }
}
