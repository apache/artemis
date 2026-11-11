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
package org.apache.activemq.artemis.tests.unit.core.util;

import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RandomUtilTest {


   @Test
   public void testInterval() {
      int i = RandomUtil.randomInterval(0, 1000);
      assertTrue(i <= 1000);
      assertTrue(i >= 0);

      i = RandomUtil.randomInterval(0, 0);
      assertEquals(0, i);

      i = RandomUtil.randomInterval(10, 10);
      assertEquals(10, i);

   }

   @Test
   public void testRandomWords() {
      for (int i = 0; i < 1000; i++) {
         int wordCount = RandomUtil.randomPositiveInt() % 100 + 10;
         String[] words = RandomUtil.randomWords(wordCount);
         assertEquals(wordCount, words.length);
         for (String word : words) {
            assertTrue(word.length() > 0);
            assertTrue(word.length() <= 10);
         }
      }
   }
}
