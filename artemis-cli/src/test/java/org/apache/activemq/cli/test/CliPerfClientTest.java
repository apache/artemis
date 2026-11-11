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
package org.apache.activemq.cli.test;

import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.cli.commands.messages.perf.PerfClientCommand;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;

public class CliPerfClientTest extends CliTestBase {
   private Connection connection;
   private ActiveMQConnectionFactory cf;

   @BeforeEach
   @Override
   public void setup() throws Exception {
      setupAuth();
      super.setup();
      startServer();
      cf = getConnectionFactory(61616);
      connection = cf.createConnection("admin", "admin");
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      closeConnection(cf, connection);
      super.tearDown();
   }

   @Test
   public void testNonDurableStarts() throws Exception {
      new PerfClientCommand().setDurableSubscription(false).setMessageCount(1).setClientID("perfClientTest").setUser("admin").setPassword("admin").execute(new TestActionContext());
   }

   @Test
   public void testDurableStarts() throws Exception {
      new PerfClientCommand().setDurableSubscription(true).setMessageCount(1).setClientID("perfClientTest").setUser("admin").setPassword("admin").execute(new TestActionContext());
   }

   @Test
   public void testDurableNoClientIDSet() throws Exception {
      try {
         new PerfClientCommand().setDurableSubscription(true).setMessageCount(1).setUser("admin").setPassword("admin").execute(new TestActionContext());
         fail("Exception expected");
      } catch (IllegalArgumentException cliExpected) {
      }
   }
}