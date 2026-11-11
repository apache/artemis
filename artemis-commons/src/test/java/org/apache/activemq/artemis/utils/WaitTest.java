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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class WaitTest {

   @Test
   public void testWait() {
      AtomicInteger intValue = new AtomicInteger(0);

      assertFalse(Wait.waitFor(() -> intValue.get() == 1, 100, 10));
      intValue.set(1);
      assertTrue(Wait.waitFor(() -> intValue.get() == 1, 100, 10));
   }

   @Test
   public void testAssertThrowsIncorrectException() throws Exception {
      final String message = RandomUtil.randomUUIDString();
      final Class<IllegalArgumentException> clazz = IllegalArgumentException.class;
      try {
         Wait.assertThrows(IllegalStateException.class, () -> {
            throw clazz.getDeclaredConstructor().newInstance();
         }, 100, 10, () -> message);
         fail("Previous assertion should have failed!");
      } catch (AssertionError e) {
         assertTrue(e.getMessage().startsWith(message));
         assertTrue(clazz.isInstance(e.getCause()));
      }
   }

   @Test
   public void testAssertThrowsNoException() throws Exception {
      final String message = RandomUtil.randomUUIDString();
      try {
         Wait.assertThrows(IllegalStateException.class, () -> {
            return;
         }, 100, 10, () -> message);
         fail("Previous assertion should have failed!");
      } catch (AssertionError e) {
         assertTrue(e.getMessage().startsWith(message));
         assertNull(e.getCause());
      }
   }

   @Test
   public void testAssertThrowsCorrectException() throws Exception {
      Wait.assertThrows(IllegalArgumentException.class, () -> {
         throw new IllegalArgumentException();
      }, 100, 10, null);
   }
}
