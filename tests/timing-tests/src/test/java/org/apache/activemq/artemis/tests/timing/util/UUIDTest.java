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

import org.apache.activemq.artemis.utils.UUIDGenerator;

public class UUIDTest extends org.apache.activemq.artemis.tests.unit.util.UUIDTest {


   public static void main(String[] args) {
      long start = System.currentTimeMillis();
      int count = 10000;
      for (int i = 0; i < count; i++) {
         // System.out.println(i + " " + UUIDGenerator.asString(UUIDGenerator.getHardwareAddress()));
         byte[] address = UUIDGenerator.getHardwareAddress();
      }
      long end = System.currentTimeMillis();
      System.out.println("getHardwareAddress() => " + 1.0 * (end - start) / count + " ms");
   }

   @Override
   protected int getTimes() {
      return 1000000;
   }
}
