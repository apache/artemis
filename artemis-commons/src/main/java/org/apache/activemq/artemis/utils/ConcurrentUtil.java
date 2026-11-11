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
package org.apache.activemq.artemis.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

public class ConcurrentUtil {

   /**
    * Await for condition, handling
    * <a href="http://errorprone.info/bugpattern/WaitNotInLoop">spurious wakeups</a>.
    *
    * @param condition condition to await for
    * @param timeout   the maximum time to wait in milliseconds
    * @return value from {@link Condition#await(long, TimeUnit)}
    */
   public static boolean await(final Condition condition, final long timeout) throws InterruptedException {
      boolean awaited = false;
      long timeoutRemaining = timeout;
      long awaitStarted = System.currentTimeMillis();
      while (!awaited && timeoutRemaining > 0) {
         awaited = condition.await(timeoutRemaining, TimeUnit.MILLISECONDS);
         timeoutRemaining -= System.currentTimeMillis() - awaitStarted;
      }
      return awaited;
   }
}
