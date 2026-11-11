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
package org.apache.activemq.artemis.utils.critical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.apache.activemq.artemis.utils.ArtemisCloseable;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class CriticalAnalyzerTest extends ArtemisTestCase {

   private CriticalAnalyzer analyzer;

   @AfterEach
   public void tearDown() throws Exception {
      if (analyzer != null) {
         analyzer.stop();
      }
   }

   @Test
   public void testDummy() {

      analyzer = new CriticalAnalyzerImpl().setTimeout(100, TimeUnit.MILLISECONDS).setCheckTime(50, TimeUnit.MILLISECONDS);
      CriticalComponent component = new CriticalComponentImpl(analyzer, 2);
      analyzer.add(component);

      CriticalCloseable closeable1 = component.measureCritical(0);

      assertFalse(CriticalMeasure.isDummy(closeable1));

      ArtemisCloseable closeable2 = component.measureCritical(0);

      assertTrue(CriticalMeasure.isDummy(closeable2));

      closeable1.close();

      closeable2 = component.measureCritical(0);

      assertFalse(CriticalMeasure.isDummy(closeable2));
   }

   @Test
   public void testCall() {

      analyzer = new CriticalAnalyzerImpl().setTimeout(100, TimeUnit.MILLISECONDS).setCheckTime(50, TimeUnit.MILLISECONDS);
      CriticalComponent component = new CriticalComponentImpl(analyzer, 2);
      analyzer.add(component);

      CriticalCloseable closeable = component.measureCritical(0);
      assertFalse(CriticalMeasure.isDummy(closeable));

      CriticalCloseable dummy = component.measureCritical(0);

      boolean exception = false;
      try {
         dummy.beforeClose(() -> System.out.println("never hapening"));
      } catch (Throwable e) {
         exception = true;
      }

      assertTrue(exception);

      AtomicInteger value = new AtomicInteger(0);

      closeable.beforeClose(() -> value.set(1000));

      assertEquals(0, value.get());

      closeable.close();

      assertEquals(1000, value.get());
   }


   @Test
   public void testAction() throws Exception {
      analyzer = new CriticalAnalyzerImpl().setTimeout(100, TimeUnit.MILLISECONDS).setCheckTime(50, TimeUnit.MILLISECONDS);
      analyzer.add(new CriticalComponent() {
         @Override
         public CriticalAnalyzer getCriticalAnalyzer() {
            return null;
         }

         @Override
         public CriticalCloseable measureCritical(int path) {
            return null;
         }

         @Override
         public boolean checkExpiration(long timeout, boolean reset) {
            return true;
         }
      });

      CountDownLatch latch = new CountDownLatch(1);

      analyzer.start();

      analyzer.addAction((CriticalComponent comp) -> {
         latch.countDown();
      });

      assertTrue(latch.await(10, TimeUnit.SECONDS));

      analyzer.stop();
   }

   @Test
   public void testActionOnImpl() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      analyzer = new CriticalAnalyzerImpl().setTimeout(10, TimeUnit.MILLISECONDS).setCheckTime(5, TimeUnit.MILLISECONDS);

      analyzer.addAction((CriticalComponent comp) -> {
         latch.countDown();
      });

      CriticalComponent component = new CriticalComponentImpl(analyzer, 2);
      analyzer.add(component);

      component.measureCritical(0).close();
      component.measureCritical(1);


      analyzer.start();

      assertTrue(latch.await(10, TimeUnit.SECONDS));

      analyzer.stop();
   }

   @Test
   public void testEnterNoLeaveNoExpire() throws Exception {
      analyzer = new CriticalAnalyzerImpl().setTimeout(10, TimeUnit.MILLISECONDS).setCheckTime(5, TimeUnit.MILLISECONDS);
      CriticalComponent component = new CriticalComponentImpl(analyzer, 2);
      component.measureCritical(0);
      assertFalse(component.checkExpiration(TimeUnit.MINUTES.toNanos(1), false));
      analyzer.stop();

   }

   @Test
   public void testEnterNoLeaveExpire() throws Exception {
      analyzer = new CriticalAnalyzerImpl().setTimeout(10, TimeUnit.MILLISECONDS).setCheckTime(5, TimeUnit.MILLISECONDS);
      CriticalComponent component = new CriticalComponentImpl(analyzer, 2);
      component.measureCritical(0);
      Thread.sleep(50);
      assertTrue(component.checkExpiration(0, false));
      analyzer.stop();

   }

   @Test
   public void testNegative() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);

      analyzer = new CriticalAnalyzerImpl().setTimeout(10, TimeUnit.MILLISECONDS).setCheckTime(5, TimeUnit.MILLISECONDS);

      analyzer.addAction((CriticalComponent comp) -> {
         latch.countDown();
      });

      CriticalComponent component = new CriticalComponentImpl(analyzer, 1);
      analyzer.add(component);

      component.measureCritical(0).close();

      analyzer.start();

      assertFalse(latch.await(100, TimeUnit.MILLISECONDS));

      analyzer.stop();
   }

   @Test
   public void testPositive() throws Exception {
      ReusableLatch latch = new ReusableLatch(1);

      analyzer = new CriticalAnalyzerImpl().setTimeout(10, TimeUnit.MILLISECONDS).setCheckTime(5, TimeUnit.MILLISECONDS);

      analyzer.addAction((CriticalComponent comp) -> {
         latch.countDown();
      });

      CriticalComponent component = new CriticalComponentImpl(analyzer, 1);
      analyzer.add(component);

      AutoCloseable measure = component.measureCritical(0);
      Thread.sleep(50);

      analyzer.start();

      assertTrue(latch.await(100, TimeUnit.MILLISECONDS));

      measure.close();

      latch.setCount(1);

      assertFalse(latch.await(100, TimeUnit.MILLISECONDS));

      analyzer.stop();
   }
}
