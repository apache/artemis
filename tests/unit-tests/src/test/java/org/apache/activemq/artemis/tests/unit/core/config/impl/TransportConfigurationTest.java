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
package org.apache.activemq.artemis.tests.unit.core.config.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.TransportConfigurationUtil;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class TransportConfigurationTest extends ActiveMQTestBase {



   @Test
   public void testSplitNullAddress() throws Exception {
      String[] addresses = TransportConfiguration.splitHosts(null);

      assertNotNull(addresses);
      assertEquals(0, addresses.length);
   }

   @Test
   public void testSplitSingleAddress() throws Exception {
      String[] addresses = TransportConfiguration.splitHosts("localhost");

      assertNotNull(addresses);
      assertEquals(1, addresses.length);
      assertEquals("localhost", addresses[0]);
   }

   @Test
   public void testSplitManyAddresses() throws Exception {
      String[] addresses = TransportConfiguration.splitHosts("localhost, 127.0.0.1, 192.168.0.10");

      assertNotNull(addresses);
      assertEquals(3, addresses.length);
      assertEquals("localhost", addresses[0]);
      assertEquals("127.0.0.1", addresses[1]);
      assertEquals("192.168.0.10", addresses[2]);
   }

   @Test
   public void testSameHostNettyTrue() {
      Map<String, Object> params1 = new HashMap<>();
      params1.put("host", "blah");
      params1.put("port", "5467");
      TransportConfiguration tc1 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params1);
      Map<String, Object> params2 = new HashMap<>();
      params2.put("host", "blah");
      params2.put("port", "5467");
      TransportConfiguration tc2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params2);
      assertTrue(TransportConfigurationUtil.isSameHost(tc1, tc2));
   }

   @Test
   public void testSameHostNettyFalse() {
      Map<String, Object> params1 = new HashMap<>();
      params1.put("host", "blah");
      params1.put("port", "5467");
      TransportConfiguration tc1 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params1);
      Map<String, Object> params2 = new HashMap<>();
      params2.put("host", "blah2");
      params2.put("port", "5467");
      TransportConfiguration tc2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params2);
      assertFalse(TransportConfigurationUtil.isSameHost(tc1, tc2));
   }

   @Test
   public void testSameHostNettyTrueDefault() {
      Map<String, Object> params1 = new HashMap<>();
      params1.put("host", TransportConstants.DEFAULT_HOST);
      params1.put("port", TransportConstants.DEFAULT_PORT);
      TransportConfiguration tc1 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params1);
      Map<String, Object> params2 = new HashMap<>();
      TransportConfiguration tc2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params2);
      assertTrue(TransportConfigurationUtil.isSameHost(tc1, tc2));
   }

   @Test
   public void testSameHostInVMTrue() {
      Map<String, Object> params1 = new HashMap<>();
      params1.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "blah");
      TransportConfiguration tc1 = new TransportConfiguration(INVM_CONNECTOR_FACTORY, params1);
      Map<String, Object> params2 = new HashMap<>();
      params2.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "blah");
      TransportConfiguration tc2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params2);
      assertTrue(TransportConfigurationUtil.isSameHost(tc1, tc2));
   }

   @Test
   public void testSameHostInVMFalse() {
      Map<String, Object> params1 = new HashMap<>();
      params1.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "blah");
      TransportConfiguration tc1 = new TransportConfiguration(INVM_CONNECTOR_FACTORY, params1);
      Map<String, Object> params2 = new HashMap<>();
      params2.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "blah3");
      TransportConfiguration tc2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params2);
      assertFalse(TransportConfigurationUtil.isSameHost(tc1, tc2));
   }

   @Test
   public void testSameHostInVMTrueDefault() {
      Map<String, Object> params1 = new HashMap<>();
      params1.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, "0");
      TransportConfiguration tc1 = new TransportConfiguration(INVM_CONNECTOR_FACTORY, params1);
      Map<String, Object> params2 = new HashMap<>();
      TransportConfiguration tc2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), params2);
      assertTrue(TransportConfigurationUtil.isSameHost(tc1, tc2));
   }
}
