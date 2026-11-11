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
package org.apache.activemq.artemis.tests.smoke.mqtt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MQTTLeakTest extends SmokeTestBase {

   public static final String SERVER_NAME_0 = "mqtt";

   private static Process server0;

   /*
                 <execution>
                  <phase>test-compile</phase>
                  <id>create-mqtt</id>
                  <goals>
                     <goal>create</goal>
                  </goals>
                  <configuration>
                     <!-- this makes it easier in certain envs -->
                     <configuration>${basedir}/target/classes/servers/mqtt</configuration>
                     <allowAnonymous>true</allowAnonymous>
                     <user>admin</user>
                     <password>admin</password>
                     <instance>${basedir}/target/mqtt</instance>
                     <configuration>${basedir}/target/classes/servers/mqtt</configuration>
                  </configuration>
               </execution>

    */

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location);
            //setConfiguration("./src/main/resources/servers/mqtt");
         cliCreateServer.createServer();
      }
   }


   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      disableCheckThread();
   }

   @AfterEach
   @Override
   public void after() throws Exception {
      super.after();
      cleanupData(SERVER_NAME_0);
   }

   @Test
   public void testMQTTLeak() throws Throwable {

      try {
         server0 = startServer(SERVER_NAME_0, 0, 30000);
         MQTTRunner.run();
      } finally {

         ServerUtil.killServer(server0, true);
      }
   }



   private static class MQTTRunner implements MqttCallback {

      private MqttAsyncClient mqttClient;
      private MqttConnectOptions connOpts;
      protected static MQTTRunner publisherClient;
      protected static MQTTRunner consumerClient;

      private static String topicPaho1 = "State/PRN/";
      private static String topicPaho2 = "Soap/PRN/";
      public String name;

      private static final Semaphore semaphore = new Semaphore(2);

      public static void run() throws Exception {
         publisherClient = new MQTTRunner();
         publisherClient.connect();
         publisherClient.name = "Pub";
         consumerClient = new MQTTRunner();
         consumerClient.connect();
         consumerClient.name = "Consumer";
         byte[] content = buildContent();

         for (int idx = 0; idx < 500; idx++) {
            if (idx % 100 == 0) {
               System.out.println("Sent " + idx + " messages");
            }
            MqttMessage msg = new MqttMessage(content);
            semaphore.acquire(2);
            publisherClient.mqttClient.publish(topicPaho1, msg);
         }
      }

      public void connect() {
         // create a new Paho MqttClient
         MemoryPersistence persistence = new MemoryPersistence();
         // establish the client ID for the life of this DPI publisherClient
         String clientId = UUID.randomUUID().toString();
         try {
            mqttClient = new MqttAsyncClient("tcp://localhost:1883", clientId, persistence);
            // Create a set of connection options
            connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            mqttClient.connect(connOpts);
         } catch (MqttException e) {
            e.printStackTrace();
         }
         // pause a moment to get connected (prevents the race condition)
         try {
            Thread.sleep(1000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }

         // subscribe
         try {
            String[] topicsPaho = new String[]{topicPaho1, topicPaho2};
            int[] qos = new int[]{0, 0};
            mqttClient.subscribe(topicsPaho, qos);
         } catch (MqttException e) {
            e.printStackTrace();
         }

         mqttClient.setCallback(this);
      }

      @Override
      public void connectionLost(Throwable throwable) {
      }

      int count = 0;
      @Override
      public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {

         count++;

         if (count % 100 == 0) {
            System.out.println("Received " + count);
         }

         semaphore.release();
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
      }

      public static byte[] buildContent() {

         List<String> stringval2 = buildContentArray();
         int size = 0;
         for (String value : stringval2) {
            size += value.length();
         }
         System.out.println();
         StringBuilder builder = new StringBuilder(size);
         for (String value : stringval2) {
            builder.append(value);
         }
         String msgContent = builder.toString();

         return msgContent.getBytes();
      }

      public static List<String> buildContentArray() {
         List<String> val = new ArrayList<>();
         String msgHdr = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><ns5:ExchangeMessage xmlns:ns5=\"urn:dpcl:wsdl:2011-09-02\" xmlns:ns3=\"http://www.w3.org/2004/08/xop/include\" xmlns:ns6=\"urn:dpcl:wsdl:2010-01-19\" xmlns:xmime=\"http://www.w3.org/2005/05/xmlmime\" xmlns=\"\"><ExchangeMessageInput><data xmime:contentType=\"application/vnd.dpcl.update_transfer+xml\"><base64>";
         String msgChunk = "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8bnMyOlRyYW5zZmVyIHhtbG5zOm5zMj0idXJuOmRwY2w6dXBkYXRlOjIwMTEtMTAtMTkiPgogICAgPGltYWdlU2VnbWVudD4KICAgICAgICA8Ym9hcmQ+MjU5PC9ib2FyZD4KICAgICAgICA8Y2F0ZWdvcnk+MjwvY2F0ZWdvcnk+CiAgICAgICAgPHZlcnNpb24+Mjg1NDA5Mjg1PC92ZXJzaW9uPgogICAgICAgIDxpZD4yNjwvaWQ+CiAgICAgICAgPHNpemU+MjA5NzE1Mjwvc2l6ZT4KICAgICAgICA8Y2hlY2tzdW0+NTE0ODI3MGJmZTM2ZmYzNmIyZTNmMjc0NWJlNmYyMGY8L2NoZWNrc3VtPgogICAgICAgIDxkYXRhPm5OQUJ1WHQvWG0xYlhGeC9aallZbEJ1K2NrWU1ncHBTMnZpTVZoOUxjTENjTFlTL1Z6YUxlSWNnWmtlMjI5Z1dlS1p6czlSclBrdVlsSHYvaWNlSldJeTUxaGFpVUx3NTY0NWtTTUlhMEhjNnZoYTB5UC91OEVNUEcvck9LL1JhVXpuS0tRdXF5WVNDVlZ3TWROS25IWjZ5Sm91TkdMcVJ3a0MvVDZUdStrTWxKak9TcjV6MUNYWDdtZWdvSGpLdkFuU1AyOFJWY0F3MWVXTUtIY0pQU0Z0bFZXSkFYVXErZjFzbE9HWXlNSGhiN2haV0VnMWc4TlRlVUJ2NHJGL0RtUitKRjRmbjlWdkRJSkJYanJpeE5CNWFyc1RKOTR3dEF2YWxVM28vVzVnODltbURNNHp0VlVuaHZvSlRTSlZ6bXlqTGpJMWQ5OExVVTVWU3dqWE5KMjZ2d0F4R1ptVmwrVGlMU0JaeWNYak45NlYxVUZ6eldOMStPN2h5SHRMZnMvOE9kRjVMK1ArbjZXOXNqNVA3aDdGZUU4UFVHbGpLcXhxWmFGbFZ4aXJPRjYrUExGTHFFMzAzUzVodzJPeDFBQjA5Sjl4VThjVXNtUVI0dlJBS3B0Y3ZpbXkzb1VncmxWQTBwNG83cFdlYkduak1kT1N6ZGR2M01uMi9rMldlOVRHNzI3OEhkdTdLQlNtVW95VTJSM0l6TitITXhXeGQ4";

         val.add(msgHdr);
         for (int idx = 0; idx < 300; idx++) {
            val.add(msgChunk);
            val.add(msgChunk);
            val.add(msgChunk);
            val.add(msgChunk);
            val.add(msgChunk);
            val.add(msgChunk);
            val.add(msgChunk);
            val.add(msgChunk);
            val.add(msgChunk);
            val.add(msgChunk);
         }
         return val;
      }
   }
}
