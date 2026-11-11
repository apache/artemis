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
package org.apache.activemq.artemis.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ConcurrentModificationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.junit.jupiter.api.Test;

public class TypedPropertiesConcurrencyTest {

   @Test
   public void testClearAndToString() throws Exception {
      TypedProperties props = new TypedProperties();

      ExecutorService executorService = Executors.newFixedThreadPool(1000);

      AtomicBoolean hasError = new AtomicBoolean();
      CountDownLatch countDownLatch = new CountDownLatch(1);
      for (int i = 0; i < 10000; i++) {
         executorService.submit(() -> {
            try {
               countDownLatch.await();
               for (int h = 0; h < 100; h++) {
                  props.putSimpleStringProperty(SimpleString.of("S" + h), SimpleString.of("hello"));
               }
               props.clear();
            } catch (ConcurrentModificationException t) {
               hasError.set(true);
               t.printStackTrace();
            } catch (InterruptedException e) {
            }
         });
      }
      for (int i = 0; i < 10; i++) {
         executorService.submit(() -> {
            try {
               countDownLatch.await();
               for (int k = 0; k < 1000; k++) {
                  assertNotNull(props.toString());
               }
            } catch (ConcurrentModificationException t) {
               hasError.set(true);
               t.printStackTrace();
            } catch (InterruptedException e) {
            }

         });
      }

      countDownLatch.countDown();
      Thread.sleep(1000);
      executorService.shutdown();
      executorService.awaitTermination(10, TimeUnit.SECONDS);
      executorService.shutdown();
      assertFalse(hasError.get());
   }

   @Test
   public void testGetPropertyNamesClearAndToString() throws Exception {
      TypedProperties props = new TypedProperties();

      ExecutorService executorService = Executors.newFixedThreadPool(1000);

      AtomicBoolean hasError = new AtomicBoolean();
      CountDownLatch countDownLatch = new CountDownLatch(1);
      for (int i = 0; i < 10000; i++) {
         executorService.submit(() -> {
            try {
               countDownLatch.await();
               for (int h = 0; h < 100; h++) {
                  props.putSimpleStringProperty(SimpleString.of("S" + h), SimpleString.of("hello"));
               }
               props.getPropertyNames().clear();
            } catch (UnsupportedOperationException uoe) {
               //Catch this as this would be acceptable, as the set is meant to be like an enumeration so a user should not modify and should expect an implementation to protect itself..
            } catch (ConcurrentModificationException t) {
               hasError.set(true);
               t.printStackTrace();
            } catch (InterruptedException e) {
            }
         });
      }
      for (int i = 0; i < 10; i++) {
         executorService.submit(() -> {
            try {
               countDownLatch.await();
               for (int k = 0; k < 1000; k++) {
                  assertNotNull(props.toString());
               }
            } catch (ConcurrentModificationException t) {
               hasError.set(true);
               t.printStackTrace();
            } catch (InterruptedException e) {
            }

         });
      }

      countDownLatch.countDown();
      Thread.sleep(1000);
      executorService.shutdown();
      executorService.awaitTermination(10, TimeUnit.SECONDS);
      executorService.shutdown();
      assertFalse(hasError.get());
   }

   @Test
   public void testEncodedSizeAfterClearIsSameAsNewTypedProperties() throws Exception {
      TypedProperties props = new TypedProperties();
      props.putSimpleStringProperty(SimpleString.of("helllllloooooo"), SimpleString.of("raaaaaaaaaaaaaaaaaaaaaaaa"));

      props.clear();

      assertEquals(new TypedProperties().getEncodeSize(), props.getEncodeSize());

   }

   @Test
   public void testMemoryOffsetAfterClearIsSameAsNewTypedProperties() throws Exception {
      TypedProperties props = new TypedProperties();
      props.putSimpleStringProperty(SimpleString.of("helllllloooooo"), SimpleString.of("raaaaaaaaaaaaaaaaaaaaaaaa"));

      props.clear();

      assertEquals(new TypedProperties().getMemoryOffset(), props.getMemoryOffset());

   }
}
