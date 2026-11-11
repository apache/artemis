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
package org.apache.activemq.artemis.logs.annotation.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.invoke.MethodHandles;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.karuslabs.elementary.Finder;
import com.karuslabs.elementary.Results;
import com.karuslabs.elementary.junit.JavacExtension;
import com.karuslabs.elementary.junit.annotations.Options;
import com.karuslabs.elementary.junit.annotations.Processors;
import com.karuslabs.elementary.junit.annotations.Resource;

@ExtendWith(JavacExtension.class)
@Options("-Werror")
@Processors(LogAnnotationProcessor.class)
public class LogAnnotationProcessorTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   @Resource("org/apache/activemq/artemis/logs/annotation/processor/cases/LAPTCase1_InvalidIDForRegex.java")
   public void testIDInvalidForGivenRegex(Results results) {
      final String expectedMessage = "org.apache.activemq.artemis.logs.annotation.processor.cases.LAPTCase1_InvalidIDForRegex: "
                                    + "Code 100 does not match regular expression specified on the LogBundle: [0-9]{1}";

      doCheckFailureErrorMessageTestImpl(results, expectedMessage);
   }

   @Test
   @Resource("org/apache/activemq/artemis/logs/annotation/processor/cases/LAPTCase2_InvalidIDReused.java")
   public void testIDInvalidReused(Results results) {
      final String expectedMessage = "org.apache.activemq.artemis.logs.annotation.processor.cases.LAPTCase2_InvalidIDReused: "
                                    + "ID 3 with message 'reusedID' was previously used already, to define message 'initialIDuse'. "
                                    + "Consider trying ID 5 which is the next unused value.";

      doCheckFailureErrorMessageTestImpl(results, expectedMessage);
   }

   @Test
   @Resource("org/apache/activemq/artemis/logs/annotation/processor/cases/LAPTCase3_InvalidIDRetired.java")
   public void testIDInvalidRetired(Results results) {
      final String expectedMessage = "org.apache.activemq.artemis.logs.annotation.processor.cases.LAPTCase3_InvalidIDRetired: "
                                    + "ID 2 was previously retired, another ID must be used. "
                                    + "Consider trying ID 7 which is the next unused value.";

      doCheckFailureErrorMessageTestImpl(results, expectedMessage);
   }

   @Test
   @Resource("org/apache/activemq/artemis/logs/annotation/processor/cases/LAPTCase4_InvalidIDRetiredWithGap.java")
   public void testIDInvalidRetiredWithGap(Results results) {
      final String expectedMessage = "org.apache.activemq.artemis.logs.annotation.processor.cases.LAPTCase4_InvalidIDRetiredWithGap: "
                                    + "ID 2 was previously retired, another ID must be used. "
                                    + "Consider trying ID 4 which is the next unused value.";

      doCheckFailureErrorMessageTestImpl(results, expectedMessage);
   }

   @Test
   @Resource("org/apache/activemq/artemis/logs/annotation/processor/cases/LAPTCase5_InvalidRetiredID.java")
   public void testInvalidRetiredID(Results results) {
      final String expectedMessage = "org.apache.activemq.artemis.logs.annotation.processor.cases.LAPTCase5_InvalidRetiredID: "
                                    + "The retiredIDs elements must each match the configured regexID. "
                                    + "The ID 10 does not match: [0-9]{1}";

      doCheckFailureErrorMessageTestImpl(results, expectedMessage);
   }

   @Test
   @Resource("org/apache/activemq/artemis/logs/annotation/processor/cases/LAPTCase6_UnsortedRetiredID.java")
   public void testUnsortedRetiredIDs(Results results) {
      final String expectedMessage = "org.apache.activemq.artemis.logs.annotation.processor.cases.LAPTCase6_UnsortedRetiredID: "
                                    + "The retiredIDs value must be sorted. Try using: {2, 4, 5}";

      doCheckFailureErrorMessageTestImpl(results, expectedMessage);
   }

   private void doCheckFailureErrorMessageTestImpl(Results results, String expectedMessage) {
      Finder errors = results.find().errors();
      List<String> errorMessages = errors.messages();

      logger.trace("Error result messages: {}", errorMessages);

      assertEquals(1, errors.count(), () -> "Expected 1 error result. Got : " + errorMessages);
      assertEquals(expectedMessage, errorMessages.get(0), "Did not get expected error message.");
   }
}
