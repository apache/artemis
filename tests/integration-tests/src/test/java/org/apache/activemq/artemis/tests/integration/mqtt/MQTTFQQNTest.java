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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MQTTFQQNTest extends MQTTTestSupport {

   @Test
   public void testMQTTSubNames() throws Exception {
      final MQTTClientProvider subscriptionProvider = getMQTTClientProvider();
      initializeConnection(subscriptionProvider);

      try {
         subscriptionProvider.subscribe("foo/bah", AT_MOST_ONCE);

         Bindings bindings = server.getPostOffice().getBindingsForAddress(SimpleString.of("foo.bah"));
         assertEquals(1, bindings.size());
         Binding b = bindings.getBindings().iterator().next();
         //check that query using bare queue name works as before
         QueueQueryResult result = server.queueQuery(b.getUniqueName());
         assertTrue(result.isExists());
         assertEquals(result.getAddress(), SimpleString.of("foo.bah"));
         assertEquals(b.getUniqueName(), result.getName());
         //check that queue query using FQQN returns FQQN
         result = server.queueQuery(SimpleString.of("foo.bah::" + b.getUniqueName()));
         assertTrue(result.isExists());
         assertEquals(SimpleString.of("foo.bah"), result.getAddress());
         assertEquals(b.getUniqueName(), result.getName());
      } finally {
         subscriptionProvider.disconnect();
      }
   }

   @Test
   @Timeout(60)
   public void testSendAndReceiveMQTTSpecial1() throws Exception {
      final MQTTClientProvider subscriptionProvider = getMQTTClientProvider();
      initializeConnection(subscriptionProvider);

      subscriptionProvider.subscribe("foo/bah::", AT_MOST_ONCE);

      final CountDownLatch latch = new CountDownLatch(NUM_MESSAGES);

      Thread thread = new Thread(() -> {
         for (int i = 0; i < NUM_MESSAGES; i++) {
            try {
               byte[] payload = subscriptionProvider.receive(10000);
               assertNotNull(payload, "Should get a message");
               latch.countDown();
            } catch (Exception e) {
               e.printStackTrace();
               break;
            }

         }
      });
      thread.start();

      final MQTTClientProvider publishProvider = getMQTTClientProvider();
      initializeConnection(publishProvider);

      for (int i = 0; i < NUM_MESSAGES; i++) {
         String payload = "Message " + i;
         publishProvider.publish("foo/bah", payload.getBytes(), AT_LEAST_ONCE);
      }

      latch.await(10, TimeUnit.SECONDS);
      assertEquals(0, latch.getCount());
      subscriptionProvider.disconnect();
      publishProvider.disconnect();
   }

   @Test
   @Timeout(60)
   public void testSendAndReceiveMQTTSpecial2() throws Exception {
      final MQTTClientProvider subscriptionProvider = getMQTTClientProvider();
      initializeConnection(subscriptionProvider);

      try {
         subscriptionProvider.subscribe("::foo/bah", AT_MOST_ONCE);
         fail("should get exception!");
      } catch (Exception e) {
         //expected
      } finally {
         subscriptionProvider.disconnect();
      }

      //::
      initializeConnection(subscriptionProvider);
      try {
         subscriptionProvider.subscribe("::", AT_MOST_ONCE);
         fail("should get exception!");
      } catch (Exception e) {
         //expected
      } finally {
         subscriptionProvider.disconnect();
      }
   }

   @Test
   public void testMQTTSubNamesSpecial() throws Exception {
      final MQTTClientProvider subscriptionProvider = getMQTTClientProvider();
      initializeConnection(subscriptionProvider);

      try {
         subscriptionProvider.subscribe("foo/bah", AT_MOST_ONCE);

         Bindings bindings = server.getPostOffice().getBindingsForAddress(SimpleString.of("foo.bah"));
         assertEquals(1, bindings.size());
         Binding b = bindings.getBindings().iterator().next();

         //check ::queue
         QueueQueryResult result = server.queueQuery(SimpleString.of("::" + b.getUniqueName()));
         assertTrue(result.isExists());
         assertEquals(SimpleString.of("foo.bah"), result.getAddress());
         assertEquals(b.getUniqueName(), result.getName());

         //check queue::
         result = server.queueQuery(SimpleString.of(b.getUniqueName() + "::"));
         assertFalse(result.isExists());

         //check ::
         result = server.queueQuery(SimpleString.of("::"));
         assertFalse(result.isExists());
      } finally {
         subscriptionProvider.disconnect();
      }
   }
}
