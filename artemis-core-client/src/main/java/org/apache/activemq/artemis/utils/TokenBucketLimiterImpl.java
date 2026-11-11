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

import java.util.concurrent.TimeUnit;

public class TokenBucketLimiterImpl implements TokenBucketLimiter {

   private final int rate;

   private final long window;

   private final boolean spin;

   /**
    * Even thought we don't use TokenBucket in multiThread the implementation should keep this volatile for correctness
    */
   private volatile long last;

   /**
    * Even thought we don't use TokenBucket in multiThread the implementation should keep this volatile for correctness
    */
   private int tokens;

   public TokenBucketLimiterImpl(final int rate, final boolean spin) {
      this(rate, spin, TimeUnit.SECONDS, 1);
   }

   public TokenBucketLimiterImpl(final int rate, final boolean spin, TimeUnit unit, int unitAmount) {
      this.rate = rate;

      this.spin = spin;

      this.window = unit.toMillis(unitAmount);
   }

   @Override
   public int getRate() {
      return rate;
   }

   @Override
   public boolean isSpin() {
      return spin;
   }

   @Override
   public void limit() {
      while (!check()) {
         if (spin) {
            Thread.yield();
         } else {
            try {
               Thread.sleep(1);
            } catch (Exception e) {
               // Ignore
            }
         }
      }
   }

   private boolean check() {
      long now = System.currentTimeMillis();

      if (last == 0) {
         last = now;
      }

      long diff = now - last;

      if (diff >= window) {
         last = System.currentTimeMillis();

         tokens = rate;
      }

      if (tokens > 0) {
         tokens--;

         return true;
      } else {
         return false;
      }
   }
}
