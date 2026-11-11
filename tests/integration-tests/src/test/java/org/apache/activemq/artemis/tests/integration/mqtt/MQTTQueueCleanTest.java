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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class MQTTQueueCleanTest extends MQTTTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testQueueClean() throws Exception {
      testQueueClean(false);
   }

   @Test
   public void testManagedQueueClean() throws Exception {
      testQueueClean(true);
   }

   private void testQueueClean(boolean managed) throws Exception {
      String address = "clean/test";
      String clientId = "mqtt-client";
      String queueName = "::mqtt-client.clean.test";

      if (managed) {
         server.addAddressInfo(new AddressInfo(address)
                                  .addRoutingType(RoutingType.MULTICAST));

         server.createQueue(QueueConfiguration.of(queueName)
                               .setAddress(address)
                               .setRoutingType(RoutingType.MULTICAST)
                               .setConfigurationManaged(true));
      }

      MQTTClientProvider clientProvider = getMQTTClientProvider();
      clientProvider.setClientId(clientId);
      initializeConnection(clientProvider);
      clientProvider.subscribe(address, AT_LEAST_ONCE);
      clientProvider.disconnect();

      if (managed) {
         assertTrue(Wait.waitFor(() -> server.locateQueue(SimpleString.of(queueName)) != null &&
            server.locateQueue(SimpleString.of(queueName)).getConsumerCount() == 0, 5000, 10));
      } else {
         assertTrue(Wait.waitFor(() -> server.locateQueue(SimpleString.of(queueName)) == null, 5000, 10));
      }
   }

   @Test
   public void testQueueCleanOnRestart() throws Exception {
      String topic = "clean/test";
      String clientId = "mqtt-client";
      String queueName = "mqtt-client.clean.test";

      MQTTClientProvider clientProvider = getMQTTClientProvider();
      clientProvider.setClientId(clientId);
      initializeConnection(clientProvider);
      clientProvider.subscribe(topic, AT_LEAST_ONCE);
      server.stop();
      server.start();
      Wait.assertTrue(() -> server.locateQueue(SimpleString.of(queueName)) == null, 5000, 10);
   }

   @Test
   public void testQueueCleanWhenConnectionSynExeConnectAndDisconnect() throws Exception {
      Random random = new Random();
      Set<MQTTClientProvider> clientProviders = new HashSet<>(11);
      int repeatCount = 0;
      String address = "clean/test";
      String clientId = "sameClientId";
      String queueName = "::sameClientId.clean.test";
      //The abnormal scene does not necessarily occur, repeating 100 times to ensure the recurrence of the abnormality
      while (repeatCount < 100) {
         repeatCount++;
         int subConnectionCount = random.nextInt(50) + 1;
         int sC = 0;
         try {
            //Reconnect at least twice to reproduce the problem
            while (sC < subConnectionCount) {
               sC++;
               MQTTClientProvider clientProvider = getMQTTClientProvider();
               clientProvider.setClientId(clientId);
               initializeConnection(clientProvider);
               clientProviders.add(clientProvider);
               clientProvider.subscribe(address, AT_LEAST_ONCE);
            }
         } catch (Throwable e) {
            logger.error(e.getMessage(), e);
         } finally {
            for (MQTTClientProvider clientProvider : clientProviders) {
               clientProvider.disconnect();
            }
            clientProviders.clear();
            assertTrue(Wait.waitFor(() -> server.locateQueue(SimpleString.of(queueName)) == null, 5000, 10));
         }
      }
   }

}
