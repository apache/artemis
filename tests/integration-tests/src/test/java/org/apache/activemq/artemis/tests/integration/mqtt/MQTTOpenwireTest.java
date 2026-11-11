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
package org.apache.activemq.artemis.tests.integration.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.util.ByteSequence;
import org.junit.jupiter.api.Test;

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

public class MQTTOpenwireTest extends MQTTTestSupport {

   protected static final int NUM_MESSAGES = 1;

   @Override
   public void configureBroker() throws Exception {
      super.configureBroker();
      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
      wildcardConfiguration.setDelimiter('.');
      wildcardConfiguration.setSingleWord('*');
      wildcardConfiguration.setAnyWords('>');
      server.getConfiguration().setWildCardConfiguration(wildcardConfiguration);
   }

   @Override
   public void createJMSConnection() throws Exception {
      cf = new ActiveMQConnectionFactory("tcp://localhost:61616?wireFormat.cacheEnabled=true");
   }

   @Test
   public void testWildcards() throws Exception {
      doTestSendJMSReceiveMQTT("foo.bar", "foo/+");
      doTestSendJMSReceiveMQTT("foo.bar", "foo/#");
      doTestSendJMSReceiveMQTT("foo.bar.har", "foo/#");
      doTestSendJMSReceiveMQTT("foo.bar.har", "foo/+/+");
      doTestSendMQTTReceiveJMS("foo/bah", "foo.*");
      doTestSendMQTTReceiveJMS("foo/bah", "foo.>");
      doTestSendMQTTReceiveJMS("foo/bah/hah", "foo.*.*");
      doTestSendMQTTReceiveJMS("foo/bah/har", "foo.>");
   }

   public void doTestSendMQTTReceiveJMS(String mqttTopic, String jmsDestination) throws Exception {
      final MQTTClientProvider provider = getMQTTClientProvider();
      initializeConnection(provider);

      ActiveMQConnection activeMQConnection = (ActiveMQConnection) cf.createConnection();

      try {
         // MUST set to true to receive retained messages
         activeMQConnection.setUseRetroactiveConsumer(true);
         activeMQConnection.start();
         Session s = activeMQConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Topic jmsTopic = s.createTopic(jmsDestination);
         MessageConsumer consumer = s.createConsumer(jmsTopic);

         // send retained message
         final String RETAINED = "RETAINED";
         provider.publish(mqttTopic, RETAINED.getBytes(), AT_LEAST_ONCE, true);

         // check whether we received retained message on JMS subscribe
         ActiveMQMessage message = (ActiveMQMessage) consumer.receive(2000);
         assertNotNull(message, "Should get retained message " + mqttTopic + "->" + jmsDestination);
         ByteSequence bs = message.getContent();
         assertEquals(RETAINED, new String(bs.data, bs.offset, bs.length));

         for (int i = 0; i < 1; i++) {
            String payload = "Test Message: " + i;
            provider.publish(mqttTopic, payload.getBytes(), AT_LEAST_ONCE);
            message = (ActiveMQMessage) consumer.receive(1000);
            assertNotNull(message, "Should get a message " + mqttTopic + "->" + jmsDestination);
            bs = message.getContent();
            assertEquals(payload, new String(bs.data, bs.offset, bs.length));
         }
      } finally {
         activeMQConnection.close();
         provider.disconnect();
      }
   }

   public void doTestSendJMSReceiveMQTT(String jmsDestination, String mqttTopic) throws Exception {
      final MQTTClientProvider provider = getMQTTClientProvider();
      initializeConnection(provider);

      ActiveMQConnection activeMQConnection = (ActiveMQConnection) cf.createConnection();
      try {
         activeMQConnection.setUseRetroactiveConsumer(true);
         activeMQConnection.start();
         Session s = activeMQConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Topic jmsTopic = s.createTopic(jmsDestination);
         MessageProducer producer = s.createProducer(jmsTopic);

         final String RETAINED = "RETAINED";
         provider.subscribe(mqttTopic, AT_MOST_ONCE);

         // send retained message from JMS
         TextMessage sendMessage = s.createTextMessage(RETAINED);
         // mark the message to be retained
         sendMessage.setBooleanProperty("ActiveMQ.Retain", true);
         // MQTT QoS can be set using MQTTProtocolConverter.QOS_PROPERTY_NAME property
         sendMessage.setIntProperty("ActiveMQ.MQTT.QoS", 0);
         producer.send(sendMessage);

         byte[] message = provider.receive(2000);
         assertNotNull(message, "Should get retained message " + jmsDestination + "->" + mqttTopic);
         assertEquals(RETAINED, new String(message));

         for (int i = 0; i < 1; i++) {
            String payload = "This is Test Message: " + i;
            sendMessage = s.createTextMessage(payload);
            producer.send(sendMessage);
            message = provider.receive(1000);
            assertNotNull(message, "Should get a message " + jmsDestination + "->" + mqttTopic);

            assertEquals(payload, new String(message));
         }
      } finally {
         activeMQConnection.close();
         provider.disconnect();
      }
   }
}
