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
package org.apache.activemq.artemis.tests.timing.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.UTF8Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class UTF8Test extends ActiveMQTestBase {

   private static final String str = "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5" + "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5" + "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5" + "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5" + "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5" + "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5" + "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5" + "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5" + "abcdef&^*&!^ghijkl\uB5E2\uCAC7\uB2BB\uB7DD\uB7C7\uB3A3\uBCE4\uB5A5";

   final int TIMES = 5;

   final int numberOfIteractions = 1000000;

   //It's needed to be sure that the JVM won't perform Dead Code Elimination
   //on String/ActiveMQBuffer operations
   private volatile Object blackHole;

   @Test
   public void testWriteUTF() throws Exception {
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(10 * 1024);

      for (int c = 0; c < TIMES; c++) {
         final long start = System.currentTimeMillis();
         for (long i = 0; i < numberOfIteractions; i++) {
            buffer.clear();
            buffer.writeUTF(str);
            blackHole = buffer;
         }
         final long spentTime = System.currentTimeMillis() - start;

         System.out.println("Time writeUTF = " + spentTime + " ms");
         System.out.println("Throughput writeUTF = " + numberOfIteractions / spentTime + " ops/ms");
      }
   }

   @Test
   public void testReadUTF() throws Exception {
      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(10 * 1024);

      buffer.writeUTF(str);

      for (int c = 0; c < TIMES; c++) {
         final long start = System.currentTimeMillis();
         for (long i = 0; i < numberOfIteractions; i++) {
            buffer.resetReaderIndex();
            String newstr = buffer.readUTF();
            assertEquals(str, newstr);
            blackHole = newstr;
         }
         final long spentTime = System.currentTimeMillis() - start;

         System.out.println("Time readUTF = " + spentTime + " ms");
         System.out.println("Throughput readUTF = " + numberOfIteractions / spentTime + " ops/ms");
      }

   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      UTF8Util.clearBuffer();
      super.tearDown();
   }
}
