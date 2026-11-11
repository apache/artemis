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
package org.apache.activemq.artemis.tests.unit.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.protocol.amqp.broker.ActiveMQProtonRemotingConnection;
import org.apache.activemq.artemis.protocol.amqp.logger.ActiveMQAMQPProtocolLogger;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This will validate the AssertionLoggerHandler is working as expected. Even though the class belongs to
 * artemis-unit-test-support, this test has to be done here as we are validating the log4j2-tests-config.properties
 * files and the classloading of everything.
 */
public class AssertionLoggerTest {

   @Test
   public void testHandlingOnAMQP() throws Exception {
      validateLogging(ActiveMQProtonRemotingConnection.class);
   }

   @Test
   public void testHandlingOnClientCore() throws Exception {
      validateLogging(ServerLocatorImpl.class);
   }

   @Test
   public void testInfoAMQP() throws Exception {
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         ActiveMQAMQPProtocolLogger.LOGGER.retryConnection("test", "test", 1, 1);
         assertTrue(loggerHandler.findText("AMQ111002"));
      }
   }

   private void validateLogging(Class<?> clazz) throws Exception {
      String randomLogging = RandomUtil.randomUUIDString();
      Logger logging = LoggerFactory.getLogger(clazz);
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         logging.warn(randomLogging);
         assertTrue(loggerHandler.findText(randomLogging));
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         for (int i = 0; i < 10; i++) {
            logging.warn(randomLogging);
         }
         assertEquals(10, loggerHandler.countText(randomLogging));
      }

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         for (int i = 0; i < 10; i++) {
            logging.info(randomLogging);
         }
         assertEquals(10, loggerHandler.countText(randomLogging));
      }
   }
}
