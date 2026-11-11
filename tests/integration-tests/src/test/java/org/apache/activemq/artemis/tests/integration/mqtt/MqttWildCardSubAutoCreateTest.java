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
package org.apache.activemq.artemis.tests.integration.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.LinkedList;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.Wait;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class MqttWildCardSubAutoCreateTest extends MQTTTestSupport {

   private int lastId;
   private MqttClient subscriber;
   private MqttClient sender;
   private volatile LinkedList<String> topics = new LinkedList<>();

   @AfterEach
   public void clean() throws MqttException {
      topics.clear();
      if (subscriber != null && subscriber.isConnected()) {
         subscriber.disconnect();
         subscriber.close();
      }
      if (sender != null && sender.isConnected()) {
         sender.disconnect();
         sender.close();
      }
   }

   @Override
   protected ActiveMQServer createServer(final boolean realFiles, final Configuration configuration) {
      configuration.setGlobalMaxSize(15);
      return createServer(realFiles, configuration, AddressSettings.DEFAULT_PAGE_SIZE, 10);
   }

   @Test
   public void testWildcardSubAutoCreateDoesNotPageToWildcardAddress() throws Exception {

      server.getManagementService().enableNotifications(false);

      String subscriberId = UUID.randomUUID().toString();
      String senderId = UUID.randomUUID().toString();
      String subscribeTo = "A/+";
      String publishTo = "A/a";

      subscriber = createMqttClient(subscriberId);
      subscriber.subscribe(subscribeTo, 2);

      subscriber.disconnect();

      sender = createMqttClient(senderId);
      sender.publish(publishTo, UUID.randomUUID().toString().getBytes(), 2, false);
      sender.publish(publishTo, UUID.randomUUID().toString().getBytes(), 2, false);

      assertTrue(server.getPagingManager().getPageStore(SimpleString.of(MQTTUtil.getCoreAddressFromMqttTopic(subscribeTo, server.getConfiguration().getWildcardConfiguration()))).isPaging());

      subscriber = createMqttClient(subscriberId);
      subscriber.subscribe(subscribeTo, 2);

      boolean satisfied = Wait.waitFor(() -> topics.size() == 2, 5_000);
      if (!satisfied) {
         fail();
      }

      subscriber.messageArrivedComplete(lastId, 2);
      subscriber.disconnect();
      subscriber.close();

      for (String topic : topics) {
         assertEquals("A/a", topic);
      }

   }

   private MqttClient createMqttClient(String clientId) throws MqttException {
      MqttClient client = new MqttClient("tcp://localhost:" + getPort(), clientId, new MemoryPersistence());
      client.setCallback(createCallback());
      client.setManualAcks(true);
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(false);
      client.connect(options);
      return client;
   }

   private MqttCallback createCallback() {
      return new MqttCallback() {

         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            topics.add(topic);
            lastId = message.getId();
         }

         @Override
         public void deliveryComplete(IMqttDeliveryToken token) {
         }

         @Override
         public void connectionLost(Throwable cause) {
         }
      };
   }

   @Test
   public void testCoreHierarchicalTopic() throws Exception {

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         ConnectionFactory cf = new ActiveMQConnectionFactory();

         Connection connection = cf.createConnection();
         connection.setClientID("CLI-ID");

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Topic topicSubscribe = ActiveMQJMSClient.createTopic("news.europe.#");

         MessageConsumer messageConsumer = session.createDurableConsumer(topicSubscribe, "news-eu");

         MessageProducer producer = session.createProducer(null);

         Topic topicNewsUsaWrestling = ActiveMQJMSClient.createTopic("news.usa.wrestling");
         Topic topicNewsEuropeSport = ActiveMQJMSClient.createTopic("news.europe.sport");
         Topic topicNewsEuropeEntertainment = ActiveMQJMSClient.createTopic("news.europe.entertainment");

         TextMessage messageWrestlingNews = session.createTextMessage("Hulk Hogan starts ballet classes");
         addSizeProp(messageWrestlingNews);
         producer.send(topicNewsUsaWrestling, messageWrestlingNews);

         TextMessage messageEuropeSport = session.createTextMessage("Lewis Hamilton joins European synchronized swimming team");
         producer.send(topicNewsEuropeSport, messageEuropeSport);

         TextMessage messageEuropeEntertainment = session.createTextMessage("John Lennon resurrected from dead");
         producer.send(topicNewsEuropeEntertainment, messageEuropeEntertainment);

         connection.start();

         // second consumer to page to different address
         Topic topicSubscribeAllNews = ActiveMQJMSClient.createTopic("news.#");

         MessageConsumer messageConsumerAllNews = session.createDurableConsumer(topicSubscribeAllNews, "news-all");

         producer.send(topicNewsUsaWrestling, messageWrestlingNews);
         producer.send(topicNewsEuropeEntertainment, messageEuropeEntertainment);

         MessageConsumer messageConsumerEuEnt = session.createDurableConsumer(topicNewsEuropeEntertainment, "news-eu-ent");

         producer.send(topicNewsUsaWrestling, messageWrestlingNews);
         producer.send(topicNewsEuropeEntertainment, messageEuropeEntertainment);

         System.out.println("Usage " + server.getPagingManager().getGlobalSize());

         TextMessage msg = (TextMessage) messageConsumerAllNews.receive(5000);

         System.out.println("1 All received message: " + msg.getText() + ", dest: " + msg.getJMSDestination());

         msg = (TextMessage) messageConsumerAllNews.receive(5000);

         System.out.println("2 All received message: " + msg.getText() + ", dest: " + msg.getJMSDestination());

         msg = (TextMessage) messageConsumerEuEnt.receive(5000);

         System.out.println("3 EuEnt received message: " + msg.getText() + ", dest: " + msg.getJMSDestination());

         TextMessage messageReceived1 = (TextMessage) messageConsumer.receive(5000);

         System.out.println("4 Received message: " + messageReceived1.getText() + ", dest: " + messageReceived1.getJMSDestination());

         TextMessage messageReceived2 = (TextMessage) messageConsumer.receive(5000);

         System.out.println("5 Received message: " + messageReceived2.getText() + ", dest: " + messageReceived2.getJMSDestination());

         // verify messageConsumer gets messageEuropeEntertainment
         msg = (TextMessage) messageConsumer.receive(5000);

         System.out.println("6 Eu received message: " + msg.getText() + ", dest: " + msg.getJMSDestination());

         assertEquals(topicNewsEuropeSport, messageReceived1.getJMSDestination());
         assertEquals(topicNewsEuropeEntertainment, messageReceived2.getJMSDestination());
         assertEquals(topicNewsEuropeEntertainment, msg.getJMSDestination());

         messageConsumer.close();
         messageConsumerAllNews.close();

         int countOfPageStores = 0;
         SimpleString[] storeNames = server.getPagingManager().getStoreNames();
         for (int i = 0; i < storeNames.length; i++) {
            if (!storeNames[i].equals(SimpleString.of(MQTTUtil.MQTT_SESSION_STORE))) {
               countOfPageStores++;
            }
         }

         assertEquals(5, countOfPageStores, "there should be 5");

         connection.close();

         assertFalse(loggerHandler.findText("222295"));
      }
   }

   private void addSizeProp(TextMessage messageWrestlingNews) throws JMSException {
      messageWrestlingNews.setStringProperty("stuff", new String(new byte[1024]));
   }
}