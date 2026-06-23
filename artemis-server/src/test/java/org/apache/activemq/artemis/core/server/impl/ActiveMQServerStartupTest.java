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
package org.apache.activemq.artemis.core.server.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerAccessor;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzerPolicy;
import org.apache.activemq.artemis.utils.critical.CriticalComponentImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

public class ActiveMQServerStartupTest extends ServerTestBase {

   @Test
   public void testTooLongToStartHalt() throws Exception {
      testTooLongToStart(CriticalAnalyzerPolicy.HALT);
   }

   @Test
   public void testTooLongToStartShutdown() throws Exception {
      testTooLongToStart(CriticalAnalyzerPolicy.SHUTDOWN);
   }

   @Test
   public void testTooLongToStartLOG() throws Exception {
      testTooLongToStart(CriticalAnalyzerPolicy.LOG);
   }

   private void testTooLongToStart(CriticalAnalyzerPolicy policy) throws Exception {

      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {

         ConfigurationImpl configuration = new ConfigurationImpl();
         configuration.setCriticalAnalyzerPolicy(policy);
         configuration.setCriticalAnalyzer(true);
         configuration.setPersistenceEnabled(false);
         ActiveMQServerImpl server = new ActiveMQServerImpl(configuration);
         addServer(server);
         server.start();

         Field field = ActiveMQServerImpl.class.getDeclaredField("activationLatch");
         field.setAccessible(true);
         ReusableLatch activationLatch = (ReusableLatch) field.get(server);
         // fake server not being active by holding the activation latch the same way start() does
         activationLatch.setCount(1);

         CriticalAnalyzerAccessor.fireActions(server.getCriticalAnalyzer(), new CriticalComponentImpl(server.getCriticalAnalyzer(), 2));
         assertFalse(server.isActive());
         assertTrue(loggerHandler.findText("AMQ224116"));
         server.stop();
      }
   }

}
