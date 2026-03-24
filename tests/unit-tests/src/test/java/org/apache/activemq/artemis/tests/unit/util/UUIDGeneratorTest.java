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
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class UUIDGeneratorTest extends ActiveMQTestBase {



   @Test
   public void testFromJavaUUID() throws Exception {
      UUID javaId = UUID.randomUUID();
      UUIDGenerator gen = UUIDGenerator.getInstance();
      org.apache.activemq.artemis.utils.UUID nativeId = gen.fromJavaUUID(javaId);
      assertEquals(javaId.toString(), nativeId.toString());
   }


   @Test
   public void testDifferentInTightLoop() throws Exception {
      UUIDGenerator gen = UUIDGenerator.getInstance();

      final int numIterations = 10000;
      Set<org.apache.activemq.artemis.utils.UUID> uuidSet = new HashSet<>();
      for (int i = 0; i < numIterations; i++) {
         org.apache.activemq.artemis.utils.UUID nativeId = gen.generateUUID();
         uuidSet.add(nativeId);
      }
      assertEquals("All there", numIterations, uuidSet.size());
   }

   @Test
   public void testGetHardwareAddress() throws Exception {
      byte[] bytes = UUIDGenerator.getHardwareAddress();
      Assert.assertNotNull(bytes);
      Assert.assertTrue(bytes.length == 6);
   }

   @Test
   public void testZeroPaddedBytes() throws Exception {
      Assert.assertNull(UUIDGenerator.getZeroPaddedSixBytes(null));
      Assert.assertNull(UUIDGenerator.getZeroPaddedSixBytes(new byte[0]));
      Assert.assertNull(UUIDGenerator.getZeroPaddedSixBytes(new byte[7]));

      byte[] fiveBytes = new byte[]{1, 2, 3, 4, 5};
      byte[] zeroPaddedFiveBytes = UUIDGenerator.getZeroPaddedSixBytes(fiveBytes);
      ActiveMQTestBase.assertEqualsByteArrays(new byte[]{1, 2, 3, 4, 5, 0}, zeroPaddedFiveBytes);

      byte[] fourBytes = new byte[]{1, 2, 3, 4};
      byte[] zeroPaddedFourBytes = UUIDGenerator.getZeroPaddedSixBytes(fourBytes);
      ActiveMQTestBase.assertEqualsByteArrays(new byte[]{1, 2, 3, 4, 0, 0}, zeroPaddedFourBytes);

      byte[] threeBytes = new byte[]{1, 2, 3};
      byte[] zeroPaddedThreeBytes = UUIDGenerator.getZeroPaddedSixBytes(threeBytes);
      ActiveMQTestBase.assertEqualsByteArrays(new byte[]{1, 2, 3, 0, 0, 0}, zeroPaddedThreeBytes);
   }


}
