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
package org.apache.activemq.broker.artemiswrapper;

import static org.junit.Assert.fail;

import java.lang.ref.Reference;

import org.apache.activemq.artemis.tests.extensions.ThreadLeakCheckDelegate;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * This is useful to make sure you won't have leaking threads between tests
 */
public class ThreadLeakCheckRule extends TestWatcher {

   private ThreadLeakCheckDelegate delegate = new ThreadLeakCheckDelegate();
   private Description testDescription;
   private Throwable failure;

   @Override
   protected void starting(Description description) {
      delegate.beforeTest();
   }

   public void disable() {
      delegate.disable();
   }

   @Override
   protected void failed(Throwable t, Description description) {
      this.failure = t;
      this.testDescription = description;
   }

   @Override
   protected void succeeded(Description description) {
   }

   @Override
   protected void finished(Description description) {
      delegate.afterTest(failure, testDescription.getMethodName(), failMsg -> fail(failMsg));
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
