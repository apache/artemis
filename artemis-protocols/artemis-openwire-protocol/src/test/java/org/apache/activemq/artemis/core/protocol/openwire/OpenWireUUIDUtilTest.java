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
package org.apache.activemq.artemis.core.protocol.openwire;

import org.apache.activemq.artemis.utils.OpenWireUUIDUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.util.IdGenerator;
import org.apache.activemq.util.LongSequenceGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenWireUUIDUtilTest {
   @Test
   public void testInvalidOpenWireIds() throws Exception {
      // test an alphabetic character in various places where digits are expected
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-a4685-1745263255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-3a685-1745263255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34a85-1745263255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-346a5-1745263255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-3468a-1745263255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-a745263255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1a45263255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-17a5263255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-174a263255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745a63255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-17452a3255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-174526a255646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263a55646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-17452632a5646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-174526325a646-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255a46-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-17452632556a6-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-174526325564a-3:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255646-a:1:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255646-3:a:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255646-3:1:a"));

      assertFalse(OpenWireUUIDUtil.isUUID(null));
      assertFalse(OpenWireUUIDUtil.isUUID(""));
      assertFalse(OpenWireUUIDUtil.isUUID(" "));

      // test various missing groups
      assertFalse(OpenWireUUIDUtil.isUUID("ID"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255646"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255646-"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255646-3"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255646-3:"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255646-3:1"));
      assertFalse(OpenWireUUIDUtil.isUUID("ID:foo-34685-1745263255646-3:1:"));
   }

   /**
    * Test IDs generated as in {@code org.apache.activemq.command.ActiveMQTempDestination}
    *
    * @see org.apache.activemq.command.ActiveMQTempDestination#ActiveMQTempDestination(String, long)
    */
   @Test
   public void testOpenWireIdValidityWithGenerator() throws Exception {
      LongSequenceGenerator sequenceGenerator = new LongSequenceGenerator();
      for (int i = 0; i < 10000; i++) {
         String id = new IdGenerator(RandomUtil.randomAlphaNumericString(4)).generateId() + ":" + sequenceGenerator.getNextSequenceId();
         assertTrue(OpenWireUUIDUtil.isUUID(id), "Invalid ID: " + id);
      }
      for (int i = 0; i < 10000; i++) {
         String id = new IdGenerator().generateId() + ":" + sequenceGenerator.getNextSequenceId();
         System.out.println(id);
         assertTrue(OpenWireUUIDUtil.isUUID(id), "Invalid ID: " + id);
      }
   }

   @Test
   public void testOpenWireIdWithDashesInHostname() throws Exception {
      assertTrue(OpenWireUUIDUtil.isUUID("ID:hostname-with-dashes-37205-1745340475160-149:1:1"));
   }
}
