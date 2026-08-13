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
package org.apache.artemis.nativo.jlibaio.test;

import org.apache.artemis.nativo.jlibaio.SubmitInfo;
import org.apache.artemis.nativo.jlibaio.util.CallbackCache;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;

public class CallbackCachelTest {

   @Test
   public void testPartiallyInitialized() {
      CallbackCache<MyPool> pool = new CallbackCache(100);

      for (int i = 0; i < 50; i++) {
         pool.put(new MyPool(i));
      }

      MyPool value = pool.get();

      Assert.assertNotNull(value);

      pool.put(value);

      // add and remove immediately
      for (int i = 0; i < 777; i++) {
         pool.put(pool.get());
      }

      HashSet<MyPool> hashValues = new HashSet<>();

      MyPool getValue;
      while ((getValue = pool.get()) != null) {
         hashValues.add(getValue);
      }

      Assert.assertEquals(50, hashValues.size());
   }

   static class MyPool implements SubmitInfo {

      public final int i;

      MyPool(int i) {
         this.i = i;
      }

      public int getI() {
         return i;
      }

      @Override
      public void onError(int errno, String message) {
      }

      @Override
      public void done() {

      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         }
         if (o == null || getClass() != o.getClass()) {
            return false;
         }

         MyPool myPool = (MyPool) o;

         if (i != myPool.i) {
            return false;
         }

         return true;
      }

      @Override
      public int hashCode() {
         return i;
      }
   }
}
