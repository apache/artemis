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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

@ExtendWith(ParameterizedTestExtension.class)
public class PahoMQTTTest extends MQTTTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Parameters(name = "protocol={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][] {{"tcp"}, {"ws"}});
   }

   public String protocol;

   public PahoMQTTTest(String protocol) {
      this.protocol = protocol;
   }

   @TestTemplate
   @Timeout(60)
   public void testLotsOfClients() throws Exception {

      final int CLIENTS = Integer.getInteger("PahoMQTTTest.CLIENTS", 100);
      logger.debug("Using: {} clients: ", CLIENTS);

      final AtomicInteger receiveCounter = new AtomicInteger();
      MqttClient client = createPahoClient("consumer");
      client.setCallback(new MqttCallback() {
         @Override
         public void connectionLost(Throwable cause) {
         }

         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            receiveCounter.incrementAndGet();
         }

         @Override
         public void deliveryComplete(IMqttDeliveryToken token) {
         }
      });
      client.connect();
      client.subscribe("test");

      final AtomicReference<Throwable> asyncError = new AtomicReference<>();
      final CountDownLatch connectedDoneLatch = new CountDownLatch(CLIENTS);
      final CountDownLatch disconnectDoneLatch = new CountDownLatch(CLIENTS);
      final CountDownLatch sendBarrier = new CountDownLatch(1);

      for (int i = 0; i < CLIENTS; i++) {
         Thread.sleep(10);
         new Thread(null, null, "client:" + i) {
            @Override
            public void run() {
               try {
                  MqttClient client = createPahoClient(Thread.currentThread().getName());
                  client.connect();
                  connectedDoneLatch.countDown();
                  sendBarrier.await();
                  for (int i = 0; i < 10; i++) {
                     Thread.sleep(1000);
                     client.publish("test", "hello".getBytes(), 1, false);
                  }
                  client.disconnect();
                  client.close();
               } catch (Throwable e) {
                  e.printStackTrace();
                  asyncError.set(e);
               } finally {
                  disconnectDoneLatch.countDown();
               }
            }
         }.start();
      }

      connectedDoneLatch.await();
      assertNull(asyncError.get(), "Async error: " + asyncError.get());
      sendBarrier.countDown();

      logger.debug("All clients connected... waiting to receive sent messages...");

      // We should eventually get all the messages.
      within(30, TimeUnit.SECONDS, () -> assertEquals(receiveCounter.get(), CLIENTS * 10));

      logger.debug("All messages received.");

      disconnectDoneLatch.await();
      assertNull(asyncError.get(), "Async error: " + asyncError.get());
   }

   @TestTemplate
   @Timeout(60)
   public void testSendAndReceiveMQTT() throws Exception {
      final CountDownLatch latch = new CountDownLatch(1);

      MqttClient consumer = createPahoClient("consumerId");
      MqttClient producer = createPahoClient("producerId");

      consumer.connect();
      consumer.subscribe("test");
      consumer.setCallback(new MqttCallback() {
         @Override
         public void connectionLost(Throwable cause) {

         }

         @Override
         public void messageArrived(String topic, MqttMessage message) throws Exception {
            latch.countDown();
         }

         @Override
         public void deliveryComplete(IMqttDeliveryToken token) {

         }
      });

      producer.connect();
      producer.publish("test", "hello".getBytes(), 1, false);

      waitForLatch(latch);
      producer.disconnect();
      producer.close();
   }

   @TestTemplate
   @Timeout(60)
   public void testSessionPresentWithCleanSession() throws Exception {
      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      IMqttToken result = client.connectWithResult(options);
      assertFalse(result.getSessionPresent());
      client.disconnect();
   }

   @TestTemplate
   @Timeout(60)
   public void testSessionPresent() throws Exception {
      MqttClient client = createPahoClient(RandomUtil.randomUUIDString());
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(false);
      IMqttToken result = client.connectWithResult(options);
      assertFalse(result.getSessionPresent());
      client.disconnect();
      result = client.connectWithResult(options);
      assertTrue(result.getSessionPresent());
   }

   private MqttClient createPahoClient(String clientId) throws MqttException {
      return new MqttClient(protocol + "://localhost:" + getPort(), clientId, new MemoryPersistence());
   }

   // This test was adapted from a test from Eclipse Kapua submitted by a community member.
   @TestTemplate
   @Timeout(60)
   public void testDollarAndHashSubscriptions() throws Exception {
      final String CLIENT_ID_ADMIN = "test-client-admin";
      final String CLIENT_ID_1 = "test-client-1";
      final String CLIENT_ID_2 = "test-client-2";

      CountDownLatch clientAdminLatch = new CountDownLatch(3);
      CountDownLatch client1Latch = new CountDownLatch(2);
      CountDownLatch client2Latch = new CountDownLatch(1);

      MqttClient clientAdmin = createPahoClient(CLIENT_ID_ADMIN);
      MqttClient client1 = createPahoClient(CLIENT_ID_1);
      MqttClient client2 = createPahoClient(CLIENT_ID_2);

      clientAdmin.setCallback(new TestMqttClientCallback(clientAdminLatch));
      client1.setCallback(new TestMqttClientCallback(client1Latch));
      client2.setCallback(new TestMqttClientCallback(client2Latch));

      clientAdmin.connect();
      client1.connect();
      client2.connect();

      client1.subscribe("$dollar/" + CLIENT_ID_1 + "/#");
      client2.subscribe("$dollar/" + CLIENT_ID_2 + "/#");
      clientAdmin.subscribe("#");

      MqttMessage m = new MqttMessage("test".getBytes());

      client1.publish("$dollar/" + CLIENT_ID_1 + "/foo", m);
      client2.publish("$dollar/" + CLIENT_ID_2 + "/foo", m);
      clientAdmin.publish("$dollar/" + CLIENT_ID_1 + "/bar", m);
      clientAdmin.publish("$dollar/" + CLIENT_ID_1 + "/bar", m);

      client1.publish("$dollar/" + CLIENT_ID_1 + "/baz", m);
      client2.publish("$dollar/" + CLIENT_ID_2 + "/baz", m);
      clientAdmin.publish("$dollar/" + CLIENT_ID_1 + "/baz", m);
      clientAdmin.publish("$dollar/" + CLIENT_ID_2 + "/baz", m);

      assertTrue(client1Latch.await(2, TimeUnit.SECONDS));
      assertTrue(client2Latch.await(2, TimeUnit.SECONDS));
      assertFalse(clientAdminLatch.await(1, TimeUnit.SECONDS));
      assertEquals(3, clientAdminLatch.getCount());

      clientAdmin.disconnect();
      clientAdmin.close();
      client1.disconnect();
      client1.close();
      client2.disconnect();
      client2.close();
   }

   private class TestMqttClientCallback implements MqttCallback {

      private CountDownLatch latch;

      TestMqttClientCallback(CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public void messageArrived(String topic, MqttMessage message) throws Exception {
         latch.countDown();
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
      }

      @Override
      public void connectionLost(Throwable cause) {
      }
   }

}
