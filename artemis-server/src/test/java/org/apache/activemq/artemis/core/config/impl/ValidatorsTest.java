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
package org.apache.activemq.artemis.core.config.impl;

import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class ValidatorsTest {

   private static void success(final Validators.Validator validator, final Object value) {
      validator.validate(RandomUtil.randomUUIDString(), value);
   }

   private static void failure(final Validators.Validator validator, final Object value) {
      try {
         validator.validate(RandomUtil.randomUUIDString(), value);
         fail(validator + " must not validate '" + value + "'");
      } catch (IllegalArgumentException e) {

      }
   }



   @Test
   public void testGE_ZERO() throws Exception {
      ValidatorsTest.failure(Validators.GE_ZERO, -1);
      ValidatorsTest.success(Validators.GE_ZERO, 0);
      ValidatorsTest.success(Validators.GE_ZERO, 0.1);
      ValidatorsTest.success(Validators.GE_ZERO, 1);
   }

   @Test
   public void testGT_ZERO() throws Exception {
      ValidatorsTest.failure(Validators.GT_ZERO, -1);
      ValidatorsTest.failure(Validators.GT_ZERO, 0);
      ValidatorsTest.success(Validators.GT_ZERO, 0.1);
      ValidatorsTest.success(Validators.GT_ZERO, 1);
   }

   @Test
   public void testMINUS_ONE_OR_GE_ZERO() throws Exception {
      ValidatorsTest.failure(Validators.MINUS_ONE_OR_GE_ZERO, -2);
      ValidatorsTest.success(Validators.MINUS_ONE_OR_GE_ZERO, -1);
      ValidatorsTest.success(Validators.MINUS_ONE_OR_GE_ZERO, 0);
      ValidatorsTest.success(Validators.MINUS_ONE_OR_GE_ZERO, 0.1);
      ValidatorsTest.success(Validators.MINUS_ONE_OR_GE_ZERO, 1);
   }

   @Test
   public void testMINUS_ONE_OR_GT_ZERO() throws Exception {
      ValidatorsTest.failure(Validators.MINUS_ONE_OR_GT_ZERO, -2);
      ValidatorsTest.success(Validators.MINUS_ONE_OR_GT_ZERO, -1);
      ValidatorsTest.failure(Validators.MINUS_ONE_OR_GT_ZERO, 0);
      ValidatorsTest.success(Validators.MINUS_ONE_OR_GT_ZERO, 0.1);
      ValidatorsTest.success(Validators.MINUS_ONE_OR_GT_ZERO, 1);
   }

   @Test
   public void testNO_CHECK() throws Exception {
      ValidatorsTest.success(Validators.NO_CHECK, -1);
      ValidatorsTest.success(Validators.NO_CHECK, null);
      ValidatorsTest.success(Validators.NO_CHECK, "");
      ValidatorsTest.success(Validators.NO_CHECK, true);
      ValidatorsTest.success(Validators.NO_CHECK, false);
   }

   @Test
   public void testNOT_NULL_OR_EMPTY() throws Exception {
      ValidatorsTest.failure(Validators.NOT_NULL_OR_EMPTY, null);
      ValidatorsTest.failure(Validators.NOT_NULL_OR_EMPTY, "");
      ValidatorsTest.success(Validators.NOT_NULL_OR_EMPTY, RandomUtil.randomUUIDString());
   }

   @Test
   public void testJOURNAL_TYPE() throws Exception {
      for (JournalType type : JournalType.values()) {
         ValidatorsTest.success(Validators.JOURNAL_TYPE, type.toString());
      }
      ValidatorsTest.failure(Validators.JOURNAL_TYPE, null);
      ValidatorsTest.failure(Validators.JOURNAL_TYPE, "");
      ValidatorsTest.failure(Validators.JOURNAL_TYPE, RandomUtil.randomUUIDString());
   }

   @Test
   public void testPERCENTAGE() {
      ValidatorsTest.success(Validators.PERCENTAGE, 99);
      ValidatorsTest.success(Validators.PERCENTAGE, 100);
      ValidatorsTest.success(Validators.PERCENTAGE, 0);
      ValidatorsTest.failure(Validators.PERCENTAGE, -1);
      ValidatorsTest.failure(Validators.PERCENTAGE, 101);
      ValidatorsTest.failure(Validators.PERCENTAGE, null);
   }

   @Test
   public void testPERCENTAGE_OR_MINUS_ONE() {
      ValidatorsTest.success(Validators.PERCENTAGE_OR_MINUS_ONE, 99);
      ValidatorsTest.success(Validators.PERCENTAGE_OR_MINUS_ONE, 100);
      ValidatorsTest.success(Validators.PERCENTAGE_OR_MINUS_ONE, 0);
      ValidatorsTest.success(Validators.PERCENTAGE_OR_MINUS_ONE, -1);
      ValidatorsTest.failure(Validators.PERCENTAGE_OR_MINUS_ONE, 101);
      ValidatorsTest.failure(Validators.PERCENTAGE_OR_MINUS_ONE, null);
   }

   @Test
   public void testPOSITIVE_INT() {
      ValidatorsTest.failure(Validators.POSITIVE_INT, -1);
      ValidatorsTest.failure(Validators.POSITIVE_INT, 0);
      ValidatorsTest.failure(Validators.POSITIVE_INT, 0.1);
      ValidatorsTest.success(Validators.POSITIVE_INT, 1);

      ValidatorsTest.success(Validators.POSITIVE_INT, Integer.MAX_VALUE);
      ValidatorsTest.failure(Validators.POSITIVE_INT, Integer.MAX_VALUE + 1);
   }

   @Test
   public void testMINUS_ONE_OR_POSITIVE_INT() {
      ValidatorsTest.failure(Validators.MINUS_ONE_OR_POSITIVE_INT, -2);
      ValidatorsTest.success(Validators.MINUS_ONE_OR_POSITIVE_INT, -1);
      ValidatorsTest.failure(Validators.MINUS_ONE_OR_POSITIVE_INT, 0);
      ValidatorsTest.failure(Validators.MINUS_ONE_OR_POSITIVE_INT, 0.1);
      ValidatorsTest.success(Validators.MINUS_ONE_OR_POSITIVE_INT, 1);

      ValidatorsTest.success(Validators.MINUS_ONE_OR_POSITIVE_INT, Integer.MAX_VALUE);
      ValidatorsTest.failure(Validators.MINUS_ONE_OR_POSITIVE_INT, Integer.MAX_VALUE + 1);
   }

   @Test
   public void testTWO_CHARACTERS() {
      ValidatorsTest.failure(Validators.NULL_OR_TWO_CHARACTERS, "1234");
      ValidatorsTest.failure(Validators.NULL_OR_TWO_CHARACTERS, "123");
      ValidatorsTest.failure(Validators.NULL_OR_TWO_CHARACTERS, "1");

      ValidatorsTest.success(Validators.NULL_OR_TWO_CHARACTERS, "12");
      ValidatorsTest.success(Validators.NULL_OR_TWO_CHARACTERS, null);
   }

   @Test
   public void testPOSITIVE_POWER_OF_TWO() {
      ValidatorsTest.failure(Validators.POSITIVE_POWER_OF_TWO, 0);
      ValidatorsTest.failure(Validators.POSITIVE_POWER_OF_TWO, -10);
      ValidatorsTest.failure(Validators.POSITIVE_POWER_OF_TWO, 127);

      ValidatorsTest.success(Validators.POSITIVE_POWER_OF_TWO, 2);
      ValidatorsTest.success(Validators.POSITIVE_POWER_OF_TWO, 64);
      ValidatorsTest.success(Validators.POSITIVE_POWER_OF_TWO, 1024);
      ValidatorsTest.success(Validators.POSITIVE_POWER_OF_TWO, 16777216);
   }

}
