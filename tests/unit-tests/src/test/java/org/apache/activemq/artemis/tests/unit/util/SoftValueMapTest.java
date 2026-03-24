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
package org.apache.activemq.artemis.tests.unit.util;

import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.SoftValueLongObjectHashMap;
import org.jboss.logging.Logger;
import org.junit.Test;

public class SoftValueMapTest extends ActiveMQTestBase {
   private static final Logger log = Logger.getLogger(SoftValueMapTest.class);



   @Test
   public void testEvictions() {
      forceGC();
      long maxMemory = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().freeMemory();

      // each buffer will be 1/10th of the maxMemory
      int bufferSize = (int) (maxMemory / 100);

      SoftValueLongObjectHashMap<Value> softCache = new SoftValueLongObjectHashMap<>(100);

      final int MAX_ELEMENTS = 1000;

      for (long i = 0; i < MAX_ELEMENTS; i++) {
         softCache.put(i, new Value(new byte[bufferSize]));
      }

      assertTrue(softCache.size() < MAX_ELEMENTS);

      log.debug("SoftCache.size " + softCache.size());

      log.debug("Soft cache has " + softCache.size() + " elements");
   }

   @Test
   public void testEvictionsLeastUsed() {
      forceGC();

      SoftValueLongObjectHashMap<Value> softCache = new SoftValueLongObjectHashMap<>(200);

      for (long i = 0; i < 100; i++) {
         Value v = new Value(new byte[1]);
         v.setLive(true);
         softCache.put(i, v);
      }

      for (long i = 100; i < 200; i++) {
         Value v = new Value(new byte[1]);
         softCache.put(i, v);
      }

      assertNotNull(softCache.get(100L));

      softCache.put(300L, new Value(new byte[1]));

      // these are live, so they shouldn't go

      for (long i = 0; i < 100; i++) {
         assertNotNull(softCache.get(i));
      }

      // this was accessed, so it shouldn't go
      assertNotNull(softCache.get(100L));

      // this is the next one, so it should go
      assertNull(softCache.get(101L));

      log.debug("SoftCache.size " + softCache.size());

      log.debug("Soft cache has " + softCache.size() + " elements");
   }

   @Test
   public void testEvictOldestElement() {
      Value one = new Value(new byte[100]);
      Value two = new Value(new byte[100]);
      Value three = new Value(new byte[100]);

      SoftValueLongObjectHashMap<Value> softCache = new SoftValueLongObjectHashMap<>(2);
      softCache.put(3, three);
      softCache.put(2, two);
      softCache.put(1, one);

      assertNull(softCache.get(3));
      assertEquals(two, softCache.get(2));
      assertEquals(one, softCache.get(1));

   }

   class Value implements SoftValueLongObjectHashMap.ValueCache {

      byte[] payload;

      boolean live;

      Value(byte[] payload) {
         this.payload = payload;
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.utils.SoftValueLongObjectHashMap.ValueCache#isLive()
       */
      @Override
      public boolean isLive() {
         return live;
      }

      public void setLive(boolean live) {
         this.live = live;
      }
   }

}
