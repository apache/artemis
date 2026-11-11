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
package org.apache.activemq.artemis.tests.unit.core.remoting.impl.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.junit.jupiter.api.Test;

public class NettyConnectorFactoryTest {

   @Test
   public void testCreateConnectorSetsDefaults() {
      // Test defaults are added when TransportConfig params are empty
      TransportConfiguration tc = new TransportConfiguration(NettyConnectorFactory.class.getName(), new HashMap<>());
      assertEquals(NettyConnector.DEFAULT_CONFIG, tc.getParams());

      // Test defaults are added when TransportConfig params are null
      tc = new TransportConfiguration(NettyConnectorFactory.class.getName(), null);
      assertEquals(NettyConnector.DEFAULT_CONFIG, tc.getParams());

      // Test defaults are added when TransportConfig params are null
      tc = new TransportConfiguration(NettyConnectorFactory.class.getName());
      assertEquals(NettyConnector.DEFAULT_CONFIG, tc.getParams());

      // Test defaults are not set when TransportConfig params are not empty
      Map<String, Object> params = new HashMap<>();
      params.put("Foo", "Bar");
      tc = new TransportConfiguration(NettyConnectorFactory.class.getName(), params);
      assertEquals(1, tc.getParams().size());
      assertTrue(tc.getParams().containsKey("Foo"));
   }
}
