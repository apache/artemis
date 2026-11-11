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
package org.apache.activemq.artemis.cli.commands.tools.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.ManagementContext;
import org.apache.activemq.cli.test.CliTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class XmlDataImporterTest extends CliTestBase {

   ActiveMQServer server;

   @BeforeEach
   @Override
   public void setup() throws Exception {
      super.setup();
      server = ((Pair<ManagementContext, ActiveMQServer>)startServer()).getB();
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      stopServer();
      super.tearDown();
   }

   @Test
   public void testBrokerUrl() throws Exception {
      XmlDataImporter importer = new XmlDataImporter();
      importer.setBrokerURL("tcp://localhost:61616");
      importer.setUser("admin");
      importer.setPassword("admin");
      assertEquals(0L, server.getActiveMQServerControl().getTotalConnectionCount());
      try {
         importer.process((InputStream) null, null, 0);
      } catch (NullPointerException npe) {
         /*
          * A NullPointerException is expected here since InputStream is null, but the connection should still be made
          * if the broker URL is input & used successfully.
          */
      }
      assertEquals(1L, server.getActiveMQServerControl().getTotalConnectionCount());
   }

   @Test
   public void testHostAndPort() throws Exception {
      XmlDataImporter importer = new XmlDataImporter();
      importer.setUser("admin");
      importer.setPassword("admin");
      assertEquals(0L, server.getActiveMQServerControl().getTotalConnectionCount());
      try {
         importer.process((InputStream) null, "localhost", 61616);
      } catch (NullPointerException npe) {
         /*
          * A NullPointerException is expected here since InputStream is null, but the connection should still be made
          * if the host & port are input & used successfully.
          */
      }
      assertEquals(1L, server.getActiveMQServerControl().getTotalConnectionCount());
   }
}