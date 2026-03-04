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
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class PahoMQTTQOS2SecurityTest extends MQTTTestSupport {

   String user1 = "user1";
   String password1 = "password1";

   final String noSendUser = "noSendUser";
   final String noSendPassword = "noSendPassword";
   final String noSendRole = "noSendRole";

   @Override
   protected void configureBrokerSecurity(ActiveMQServer server) {
      super.configureBrokerSecurity(server);
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      // User additions
      securityManager.getConfiguration().addUser(user1, password1);
      securityManager.getConfiguration().addRole(user1, "addressOnly");

      securityManager.getConfiguration().addUser(noSendUser, noSendPassword);
      securityManager.getConfiguration().addRole(noSendUser, noSendRole);

      // Configure roles
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      Set<Role> value = new HashSet<>();
      value.add(new Role("addressOnly", true, true, true, true, false, false, false, false, true, true, false, false));
      value.add(new Role(noSendRole, false, true, true, true, true, true, true, true, true, true, true, true));

      securityRepository.addMatch(MQTTUtil.getCoreAddressFromMqttTopic(getQueueName(), server.getConfiguration().getWildcardConfiguration()), value);
   }

   @Override
   public boolean isSecurityEnabled() {
      return true;
   }

   @Test
   @Timeout(60)
   public void testSendAndReceiveMQTT() throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);

      MqttClient consumer = createPahoClient("consumerId");
      MqttClient producer = createPahoClient("producerId");
      MqttConnectOptions conOpt = new MqttConnectOptions();
      conOpt.setCleanSession(false);
      conOpt.setUserName(user1);
      conOpt.setPassword(password1.toCharArray());
      consumer.connect(conOpt);
      consumer.subscribe(getQueueName(), 2);
      final boolean[] failed = new boolean[1];
      consumer.setCallback(new MqttCallback() {


         @Override
         public void connectionLost(Throwable cause) {
            cause.printStackTrace();
            failed[0] = true;
            latch.countDown();
         }

         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            latch.countDown();
         }

         @Override
         public void deliveryComplete(IMqttDeliveryToken token) {

         }
      });

      producer.connect(conOpt);
      producer.publish(getQueueName(), "hello".getBytes(), 2, false);

      waitForLatch(latch);
      producer.disconnect();
      producer.close();
      assertFalse(failed[0]);
   }

   @Test
   @Timeout(60)
   public void testSendQoS2UnauthorizedNotStorePublish() throws Exception {
      final String clientID = "clientID";

      MqttClient producer = createPahoClient(clientID);
      MqttConnectOptions conOpt = new MqttConnectOptions();
      conOpt.setCleanSession(false);
      conOpt.setUserName(noSendUser);
      conOpt.setPassword(noSendPassword.toCharArray());

      producer.connect(conOpt);
      try {
         producer.publish(getQueueName(), "hello".getBytes(), 2, false);
      } catch (MqttException e) {
         // ignore
      }
      assertEquals(0, getSessions().get(clientID).getPubRec().size());
      producer.close();
   }

   private MqttClient createPahoClient(String clientId) throws MqttException {
      return new MqttClient("tcp://localhost:" + getPort(), clientId, new MemoryPersistence());
   }

}
