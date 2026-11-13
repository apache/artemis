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
package org.apache.activemq.artemis.tests.integration.quota;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ResourceQuotaControl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ResourceQuota JMX management.
 */
public class ResourceQuotaJMXTest extends ActiveMQTestBase {

   @Test
   public void testResourceQuotaJMXRegistration() throws Exception {
      ActiveMQServer server = createServer(true, createDefaultInVMConfig().setJMXManagementEnabled(true));

      // Configure resource quotas
      ResourceQuotaConfig globalQuota = new ResourceQuotaConfig("global");
      globalQuota.setMaxAddresses(100);
      globalQuota.setMaxQueues(200);
      globalQuota.setMaxMessageBytes(1000000L);

      ResourceQuotaConfig tenantQuota = new ResourceQuotaConfig("tenant1");
      tenantQuota.setPartOf("global");
      tenantQuota.setMaxAddresses(30);
      tenantQuota.setMaxQueues(60);

      server.getConfiguration().addResourceQuota("global", globalQuota);
      server.getConfiguration().addResourceQuota("tenant1", tenantQuota);

      // Configure address settings to use quota
      server.getConfiguration().getAddressSettings().put("tenant1.#",
         new org.apache.activemq.artemis.core.settings.impl.AddressSettings()
            .setResourceQuota("tenant1"));

      server.start();

      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

         // Verify global quota is registered
         ObjectName globalObjectName = new ObjectName(
            "org.apache.activemq.artemis:broker=\"localhost\",component=quotas,name=\"global\"");
         assertTrue(mbeanServer.isRegistered(globalObjectName), "Global quota should be registered in JMX");

         ResourceQuotaControl globalControl = MBeanServerInvocationHandler
            .newProxyInstance(mbeanServer, globalObjectName, ResourceQuotaControl.class, false);

         assertEquals("global", globalControl.getName());
         assertNull(globalControl.getPartOf());
         assertEquals(100, globalControl.getMaxAddresses());
         assertEquals(200, globalControl.getMaxQueues());
         assertEquals(1000000L, globalControl.getMaxMessageBytes());
         assertEquals(0, globalControl.getCurrentAddressCount());
         assertEquals(0, globalControl.getCurrentQueueCount());
         assertEquals(0L, globalControl.getCurrentMessageBytes());
         assertTrue(globalControl.hasLimits());
         assertFalse(globalControl.isLimitReached());

         // Verify tenant quota is registered
         ObjectName tenantObjectName = new ObjectName(
            "org.apache.activemq.artemis:broker=\"localhost\",component=quotas,name=\"tenant1\"");
         assertTrue(mbeanServer.isRegistered(tenantObjectName), "Tenant quota should be registered in JMX");

         ResourceQuotaControl tenantControl = MBeanServerInvocationHandler
            .newProxyInstance(mbeanServer, tenantObjectName, ResourceQuotaControl.class, false);

         assertEquals("tenant1", tenantControl.getName());
         assertEquals("global", tenantControl.getPartOf());
         assertEquals(30, tenantControl.getMaxAddresses());
         assertEquals(60, tenantControl.getMaxQueues());
         assertEquals(-1, tenantControl.getMaxMessageBytes()); // No limit configured for bytes

         // Create some addresses and verify counters update
         AddressInfo addr1 = new AddressInfo(SimpleString.of("tenant1.app1"), RoutingType.ANYCAST);
         server.addAddressInfo(addr1);

         AddressInfo addr2 = new AddressInfo(SimpleString.of("tenant1.app2"), RoutingType.ANYCAST);
         server.addAddressInfo(addr2);

         // Counters should reflect the addresses
         assertEquals(2, tenantControl.getCurrentAddressCount());
         assertEquals(2, globalControl.getCurrentAddressCount()); // Parent tracks child's addresses

         // Check utilization percentages
         double tenantUtilization = tenantControl.getAddressUtilizationPercent();
         assertTrue(tenantUtilization > 0 && tenantUtilization < 100,
            "Tenant address utilization should be between 0 and 100");

         double globalUtilization = globalControl.getAddressUtilizationPercent();
         assertTrue(globalUtilization > 0 && globalUtilization < 100,
            "Global address utilization should be between 0 and 100");

      } finally {
         server.stop();
      }
   }

   @Test
   public void testResourceQuotaJMXUnregistration() throws Exception {
      ActiveMQServer server = createServer(true, createDefaultInVMConfig().setJMXManagementEnabled(true));

      // Configure a quota
      ResourceQuotaConfig testQuota = new ResourceQuotaConfig("test-quota");
      testQuota.setMaxAddresses(10);

      server.getConfiguration().addResourceQuota("test-quota", testQuota);
      server.start();

      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         ObjectName quotaObjectName = new ObjectName(
            "org.apache.activemq.artemis:broker=\"localhost\",component=quotas,name=\"test-quota\"");

         assertTrue(mbeanServer.isRegistered(quotaObjectName), "Quota should be registered");

         // Reload with empty config (removes quota)
         server.getConfiguration().getResourceQuotas().clear();
         server.getResourceQuotaService().reloadQuotas();

         // Small delay to allow async JMX operations
         Thread.sleep(100);

         assertFalse(mbeanServer.isRegistered(quotaObjectName), "Quota should be unregistered after reload");

      } finally {
         server.stop();
      }
   }
}
