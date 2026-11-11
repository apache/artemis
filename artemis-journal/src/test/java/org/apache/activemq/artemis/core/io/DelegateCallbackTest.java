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
package org.apache.activemq.artemis.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.junit.jupiter.api.Test;

public class DelegateCallbackTest {

   private static final class CountingIOCallback implements IOCallback {

      long done = 0;
      long onError = 0;
      final boolean fail;

      private CountingIOCallback(boolean fail) {
         this.fail = fail;
      }

      @Override
      public void done() {
         done++;
         if (fail) {
            throw new IllegalStateException();
         }
      }

      @Override
      public void onError(int errorCode, String errorMessage) {
         onError++;
         if (fail) {
            throw new IllegalStateException();
         }
      }
   }

   @Test
   public void shouldCallDoneOnEachCallback() {
      final CountingIOCallback countingIOCallback = new CountingIOCallback(false);
      final DelegateCallback callback = DelegateCallback.wrap(Arrays.asList(countingIOCallback, countingIOCallback));
      callback.done();
      assertEquals(2, countingIOCallback.done);
      assertEquals(0, countingIOCallback.onError);
   }

   @Test
   public void shouldCallOnErrorOnEachCallback() {
      final CountingIOCallback countingIOCallback = new CountingIOCallback(false);
      final DelegateCallback callback = DelegateCallback.wrap(Arrays.asList(countingIOCallback, countingIOCallback));
      callback.onError(0, "not a real error");
      assertEquals(0, countingIOCallback.done);
      assertEquals(2, countingIOCallback.onError);
   }

   @Test
   public void shouldCallDoneOnEachCallbackWithExceptions() {
      final CountingIOCallback countingIOCallback = new CountingIOCallback(true);
      final DelegateCallback callback = DelegateCallback.wrap(Arrays.asList(countingIOCallback, countingIOCallback));
      callback.done();
      assertEquals(2, countingIOCallback.done);
      assertEquals(0, countingIOCallback.onError);
   }

   @Test
   public void shouldCallOnErrorOnEachCallbackWithExceptions() {
      final CountingIOCallback countingIOCallback = new CountingIOCallback(true);
      final DelegateCallback callback = DelegateCallback.wrap(Arrays.asList(countingIOCallback, countingIOCallback));
      callback.onError(0, "not a real error");
      assertEquals(0, countingIOCallback.done);
      assertEquals(2, countingIOCallback.onError);
   }

   @Test
   public void shouldLogOnDoneForEachExceptions() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         final CountingIOCallback countingIOCallback = new CountingIOCallback(true);
         final DelegateCallback callback = DelegateCallback.wrap(Collections.singleton(countingIOCallback));
         callback.done();
         assertTrue(loggerHandler.findText("AMQ142024"));
      }
   }

   @Test
   public void shouldLogOnErrorForEachExceptions() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         final CountingIOCallback countingIOCallback = new CountingIOCallback(true);
         final DelegateCallback callback = DelegateCallback.wrap(Collections.singleton(countingIOCallback));
         callback.onError(0, "not a real error");
         assertTrue(loggerHandler.findText("AMQ142025"));
      }
   }

}
