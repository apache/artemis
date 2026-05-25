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
package org.apache.activemq.artemis.tests.integration.management;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.impl.DivertBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DivertManagementTest extends ManagementTestBase {

   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration conf = createDefaultNettyConfig().setJMXManagementEnabled(true);
      server = addServer(ActiveMQServers.newActiveMQServer(conf, mbeanServer));
      server.start();
   }

   @Test
   public void testBadJournalRecord() throws Exception {
      final String name = RandomUtil.randomUUIDString();
      String routingName = RandomUtil.randomUUIDString();
      String address = RandomUtil.randomUUIDString();
      String forwardingAddress = RandomUtil.randomUUIDString();
      ComponentConfigurationRoutingType routingType = ComponentConfigurationRoutingType.getType((byte) RandomUtil.randomMax(3));
      boolean exclusive = RandomUtil.randomBoolean();

      DivertConfiguration config = new DivertConfiguration()
         .setName(name);

      // store an invalid divert config (i.e. missing address)
      server.getStorageManager().storeDivertConfiguration(new PersistedDivertConfiguration(config));

      server.stop();
      waitForServerToStop(server);

      // start the broker and verify the invalid divert config failed to load
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.start();
         waitForServerToStart(server);
         assertTrue(loggerHandler.findText("AMQ224164", name));
      }
      assertNull(server.getPostOffice().getBinding(SimpleString.of(name)));

      // create a new divert with the same name
      ActiveMQServerControl serverControl = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      config.setAddress(address)
         .setRoutingType(routingType)
         .setRoutingName(routingName)
         .setForwardingAddress(forwardingAddress)
         .setExclusive(exclusive);

      serverControl.createDivert(config.toJSON());

      // verify divert was created
      assertInstanceOf(DivertBinding.class, server.getPostOffice().getBinding(SimpleString.of(name)));
      assertEquals(1, server.getPostOffice().getBindingsForAddress(SimpleString.of(address)).size());

      server.stop();
      waitForServerToStop(server);

      // start the broker and verify that the invalid divert config is no longer there
      try (AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler()) {
         server.start();
         waitForServerToStart(server);
         assertFalse(loggerHandler.findText("AMQ224164"));
      }

      Binding binding = server.getPostOffice().getBinding(SimpleString.of(name));
      assertInstanceOf(DivertBinding.class, binding);
      assertEquals(1, server.getPostOffice().getBindingsForAddress(SimpleString.of(address)).size());

      Divert divert = ((DivertBinding) binding).getDivert();
      assertEquals(name, divert.getUniqueName().toString());
      assertEquals(address, divert.getAddress().toString());
      assertEquals(routingType, divert.getRoutingType());
      assertEquals(routingName, divert.getRoutingName().toString());
      assertEquals(forwardingAddress, divert.getForwardAddress().toString());
      assertEquals(exclusive, divert.isExclusive());
   }

   @Test
   public void testPersistentUpdate() throws Exception {
      String name = RandomUtil.randomUUIDString();
      String routingName = RandomUtil.randomUUIDString();
      String address = RandomUtil.randomUUIDString();
      String forwardingAddress = RandomUtil.randomUUIDString();
      ComponentConfigurationRoutingType routingType = ComponentConfigurationRoutingType.getType((byte) RandomUtil.randomMax(3));
      boolean exclusive = RandomUtil.randomBoolean();

      ActiveMQServerControl serverControl = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      DivertConfiguration config = new DivertConfiguration()
         .setName(name)
         .setAddress(address)
         .setRoutingType(routingType)
         .setRoutingName(routingName)
         .setForwardingAddress(forwardingAddress)
         .setExclusive(exclusive);

      serverControl.createDivert(config.toJSON());

      String updatedForwardingAddress = RandomUtil.randomUUIDString();
      serverControl.updateDivert(new DivertConfiguration()
                                    .setName(name)
                                    .setForwardingAddress(updatedForwardingAddress)
                                    .setRoutingType(null) // must set to null to avoid updating since the default is non-null
                                    .toJSON());

      server.stop();
      waitForServerToStop(server);
      server.start();
      waitForServerToStart(server);

      Binding binding = server.getPostOffice().getBinding(SimpleString.of(name));
      assertInstanceOf(DivertBinding.class, binding);
      assertEquals(1, server.getPostOffice().getBindingsForAddress(SimpleString.of(address)).size());

      Divert divert = ((DivertBinding) binding).getDivert();
      assertEquals(name, divert.getUniqueName().toString());
      assertEquals(address, divert.getAddress().toString());
      assertEquals(routingType, divert.getRoutingType());
      assertEquals(routingName, divert.getRoutingName().toString());
      assertEquals(updatedForwardingAddress, divert.getForwardAddress().toString());
      assertEquals(exclusive, divert.isExclusive());
   }
}
