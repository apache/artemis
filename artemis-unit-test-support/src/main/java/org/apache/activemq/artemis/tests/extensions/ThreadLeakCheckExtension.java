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
package org.apache.activemq.artemis.tests.extensions;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.ref.Reference;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This is useful to make sure you won't have leaking threads between tests
 */
public class ThreadLeakCheckExtension implements BeforeAllCallback, AfterAllCallback, Extension {

   private ThreadLeakCheckDelegate delegate = new ThreadLeakCheckDelegate();

   @Override
   public void beforeAll(ExtensionContext context) throws Exception {
      delegate.beforeTest();
   }

   public void disable() {
      delegate.disable();
   }

   @Override
   public void afterAll(ExtensionContext context) throws Exception {
      Throwable executionException = context.getExecutionException().orElse(null);

      delegate.afterTest(executionException, context.getDisplayName(), failMsg -> fail(failMsg));
   }


   public static void forceGC() {
      ThreadLeakCheckDelegate.forceGC();
   }

   public static void forceGC(final Reference<?> ref, final long timeout) {
      ThreadLeakCheckDelegate.forceGC(ref, timeout);
   }

   public static void removeKownThread(String name) {
      ThreadLeakCheckDelegate.removeKownThread(name);
   }

   public static void addKownThread(String name) {
      ThreadLeakCheckDelegate.addKownThread(name);
   }
}
