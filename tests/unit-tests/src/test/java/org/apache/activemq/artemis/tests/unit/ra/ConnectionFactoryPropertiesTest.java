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
package org.apache.activemq.artemis.tests.unit.ra;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.ra.ConnectionFactoryProperties;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static java.beans.Introspector.getBeanInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ConnectionFactoryPropertiesTest extends ActiveMQTestBase {

   private static final SortedSet<String> UNSUPPORTED_CF_PROPERTIES;
   private static final SortedSet<String> UNSUPPORTED_RA_PROPERTIES;

   static {
      UNSUPPORTED_CF_PROPERTIES = new TreeSet<>();
      UNSUPPORTED_CF_PROPERTIES.add("discoveryGroupName");
      UNSUPPORTED_CF_PROPERTIES.add("brokerURL");
      UNSUPPORTED_CF_PROPERTIES.add("incomingInterceptorList");
      UNSUPPORTED_CF_PROPERTIES.add("outgoingInterceptorList");
      UNSUPPORTED_CF_PROPERTIES.add("user");
      UNSUPPORTED_CF_PROPERTIES.add("userName");
      UNSUPPORTED_CF_PROPERTIES.add("password");
      UNSUPPORTED_CF_PROPERTIES.add("passwordCodec");
      UNSUPPORTED_CF_PROPERTIES.add("enableSharedClientID");

      UNSUPPORTED_RA_PROPERTIES = new TreeSet<>();
      UNSUPPORTED_RA_PROPERTIES.add("HA");
      UNSUPPORTED_RA_PROPERTIES.add("jgroupsChannelName");
      UNSUPPORTED_RA_PROPERTIES.add("jgroupsFile");
      UNSUPPORTED_RA_PROPERTIES.add("discoveryAddress");
      UNSUPPORTED_RA_PROPERTIES.add("discoveryPort");
      UNSUPPORTED_RA_PROPERTIES.add("discoveryLocalBindAddress");
      UNSUPPORTED_RA_PROPERTIES.add("discoveryRefreshTimeout");
      UNSUPPORTED_RA_PROPERTIES.add("discoveryInitialWaitTimeout");
      UNSUPPORTED_RA_PROPERTIES.add("connectionParameters");
      UNSUPPORTED_RA_PROPERTIES.add("connectorClassName");
      UNSUPPORTED_RA_PROPERTIES.add("managedConnectionFactory");
      UNSUPPORTED_RA_PROPERTIES.add("jndiParams");
      UNSUPPORTED_RA_PROPERTIES.add("password");
      UNSUPPORTED_RA_PROPERTIES.add("passwordCodec");
      UNSUPPORTED_RA_PROPERTIES.add("useMaskedPassword");
      UNSUPPORTED_RA_PROPERTIES.add("useAutoRecovery");
      UNSUPPORTED_RA_PROPERTIES.add("useLocalTx");
      UNSUPPORTED_RA_PROPERTIES.add("user");
      UNSUPPORTED_RA_PROPERTIES.add("userName");
      UNSUPPORTED_RA_PROPERTIES.add("jgroupsChannelLocatorClass");
      UNSUPPORTED_RA_PROPERTIES.add("jgroupsChannelRefName");
      UNSUPPORTED_RA_PROPERTIES.add("entries");

      // TODO: shouldn't this be also set on the ActiveMQConnectionFactory?
      UNSUPPORTED_RA_PROPERTIES.add("connectionPoolName");
   }

   @Test
   public void testCompareConnectionFactoryAndResourceAdapterProperties() throws Exception {
      SortedSet<String> connectionFactoryProperties = findAllPropertyNames(ActiveMQConnectionFactory.class);
      assertTrue(connectionFactoryProperties.contains("useTopologyForLoadBalancing"));
      connectionFactoryProperties.removeAll(UNSUPPORTED_CF_PROPERTIES);
      SortedSet<String> raProperties = findAllPropertyNames(ActiveMQResourceAdapter.class);
      raProperties.removeAll(UNSUPPORTED_RA_PROPERTIES);

      compare("ActiveMQ Connection Factory", connectionFactoryProperties, "ActiveMQ Resource Adapter", raProperties);
   }

   private static void compare(String name1, SortedSet<String> set1, String name2, SortedSet<String> set2) {
      Set<String> onlyInSet1 = new TreeSet<>(set1);
      onlyInSet1.removeAll(set2);

      Set<String> onlyInSet2 = new TreeSet<>(set2);
      onlyInSet2.removeAll(set1);

      if (!onlyInSet1.isEmpty() || !onlyInSet2.isEmpty()) {
         fail(String.format("in %s only: %s\nin %s only: %s", name1, onlyInSet1, name2, onlyInSet2));
      }

      assertEquals(set2, set1);
   }

   private SortedSet<String> findAllPropertyNames(Class<?> clazz) throws Exception {
      SortedSet<String> names = new TreeSet<>();
      for (PropertyDescriptor propDesc : getBeanInfo(clazz).getPropertyDescriptors()) {
         if (propDesc == null || propDesc.getWriteMethod() == null) {
            continue;
         }
         names.add(propDesc.getDisplayName());
      }
      return names;
   }

   @Test
   public void testEquality() throws Exception {
      ConnectionFactoryProperties cfp1 = new ConnectionFactoryProperties();
      List<String> connectorClassNames1 = new ArrayList<>();
      connectorClassNames1.add("myConnector");
      cfp1.setParsedConnectorClassNames(connectorClassNames1);
      List<Map<String, Object>> connectionParameters1 = new ArrayList<>();
      Map<String, Object> params1 = new HashMap<>();
      params1.put("port", "0");
      connectionParameters1.add(params1);
      cfp1.setParsedConnectionParameters(connectionParameters1);
      cfp1.setAutoGroup(true);

      ConnectionFactoryProperties cfp2 = new ConnectionFactoryProperties();
      List<String> connectorClassNames2 = new ArrayList<>();
      connectorClassNames2.add("myConnector");
      cfp2.setParsedConnectorClassNames(connectorClassNames2);
      List<Map<String, Object>> connectionParameters2 = new ArrayList<>();
      Map<String, Object> params2 = new HashMap<>();
      params2.put("port", "0");
      connectionParameters2.add(params2);
      cfp2.setParsedConnectionParameters(connectionParameters2);
      cfp2.setAutoGroup(true);

      assertTrue(cfp1.equals(cfp2));
   }

   @Test
   public void testInequality() throws Exception {
      ConnectionFactoryProperties cfp1 = new ConnectionFactoryProperties();
      List<String> connectorClassNames1 = new ArrayList<>();
      connectorClassNames1.add("myConnector");
      cfp1.setParsedConnectorClassNames(connectorClassNames1);
      List<Map<String, Object>> connectionParameters1 = new ArrayList<>();
      Map<String, Object> params1 = new HashMap<>();
      params1.put("port", "0");
      connectionParameters1.add(params1);
      cfp1.setParsedConnectionParameters(connectionParameters1);
      cfp1.setAutoGroup(true);

      ConnectionFactoryProperties cfp2 = new ConnectionFactoryProperties();
      List<String> connectorClassNames2 = new ArrayList<>();
      connectorClassNames2.add("myConnector");
      cfp2.setParsedConnectorClassNames(connectorClassNames2);
      List<Map<String, Object>> connectionParameters2 = new ArrayList<>();
      Map<String, Object> params2 = new HashMap<>();
      params2.put("port", "1");
      connectionParameters2.add(params2);
      cfp2.setParsedConnectionParameters(connectionParameters2);
      cfp2.setAutoGroup(true);

      assertFalse(cfp1.equals(cfp2));
   }

   @Test
   public void testInequality2() throws Exception {
      ConnectionFactoryProperties cfp1 = new ConnectionFactoryProperties();
      List<String> connectorClassNames1 = new ArrayList<>();
      connectorClassNames1.add("myConnector");
      cfp1.setParsedConnectorClassNames(connectorClassNames1);
      List<Map<String, Object>> connectionParameters1 = new ArrayList<>();
      Map<String, Object> params1 = new HashMap<>();
      params1.put("port", "0");
      connectionParameters1.add(params1);
      cfp1.setParsedConnectionParameters(connectionParameters1);
      cfp1.setAutoGroup(true);

      ConnectionFactoryProperties cfp2 = new ConnectionFactoryProperties();
      List<String> connectorClassNames2 = new ArrayList<>();
      connectorClassNames2.add("myConnector2");
      cfp2.setParsedConnectorClassNames(connectorClassNames2);
      List<Map<String, Object>> connectionParameters2 = new ArrayList<>();
      Map<String, Object> params2 = new HashMap<>();
      params2.put("port", "0");
      connectionParameters2.add(params2);
      cfp2.setParsedConnectionParameters(connectionParameters2);
      cfp2.setAutoGroup(true);

      assertFalse(cfp1.equals(cfp2));
   }
}
