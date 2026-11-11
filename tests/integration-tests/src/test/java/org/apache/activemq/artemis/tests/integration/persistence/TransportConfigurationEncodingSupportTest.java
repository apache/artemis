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
package org.apache.activemq.artemis.tests.integration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.server.config.impl.TransportConfigurationEncodingSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;

public class TransportConfigurationEncodingSupportTest {

   @Test
   public void testTransportConfiguration() throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PORT_PROP_NAME, 5665);
      params.put(TransportConstants.HOST_PROP_NAME, RandomUtil.randomUUIDString());
      TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getName(), params);

      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(TransportConfigurationEncodingSupport.getEncodeSize(config));
      TransportConfigurationEncodingSupport.encode(buffer, config);

      assertEquals(buffer.capacity(), buffer.writerIndex());
      buffer.readerIndex(0);

      TransportConfiguration decoded = TransportConfigurationEncodingSupport.decode(buffer);
      assertNotNull(decoded);

      assertEquals(config.getName(), decoded.getName());
      assertEquals(config.getFactoryClassName(), decoded.getFactoryClassName());
      assertEquals(config.getParams().size(), decoded.getParams().size());
      for (String key : config.getParams().keySet()) {
         assertEquals(config.getParams().get(key).toString(), decoded.getParams().get(key).toString());
      }
   }

   @Test
   public void testTransportConfigurations() throws Exception {
      List<Pair<TransportConfiguration, TransportConfiguration>> connectorConfigs = new ArrayList<>();
      Map<String, Object> primaryParams = new HashMap<>();
      primaryParams.put(TransportConstants.PORT_PROP_NAME, 5665);
      TransportConfiguration primary1 = new TransportConfiguration(NettyConnectorFactory.class.getName(), primaryParams);
      Map<String, Object> backupParams = new HashMap<>();
      backupParams.put(TransportConstants.PORT_PROP_NAME, 5775);
      TransportConfiguration backup1 = new TransportConfiguration(NettyConnectorFactory.class.getName(), backupParams);
      Map<String, Object> primaryParams2 = new HashMap<>();
      primaryParams2.put(TransportConstants.PORT_PROP_NAME, 6665);
      TransportConfiguration primary2 = new TransportConfiguration(NettyConnectorFactory.class.getName(), primaryParams2);

      connectorConfigs.add(new Pair<>(primary1, backup1));
      connectorConfigs.add(new Pair<TransportConfiguration, TransportConfiguration>(primary2, null));

      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(TransportConfigurationEncodingSupport.getEncodeSize(connectorConfigs));
      TransportConfigurationEncodingSupport.encodeConfigs(buffer, connectorConfigs);

      assertEquals(buffer.capacity(), buffer.writerIndex());
      buffer.readerIndex(0);

      List<Pair<TransportConfiguration, TransportConfiguration>> decodedConfigs = TransportConfigurationEncodingSupport.decodeConfigs(buffer);
      assertNotNull(decodedConfigs);
      assertEquals(2, decodedConfigs.size());

      assertEquivalent(connectorConfigs.get(0).getA(), decodedConfigs.get(0).getA());
      assertEquivalent(connectorConfigs.get(0).getB(), decodedConfigs.get(0).getB());
      assertEquivalent(connectorConfigs.get(1).getA(), decodedConfigs.get(1).getA());
      assertNull(decodedConfigs.get(1).getB());
   }

   // decoded TransportConfiguration have parameter values as String instead of primitive type
   private static void assertEquivalent(TransportConfiguration expected, TransportConfiguration actual) {
      assertEquals(expected.getFactoryClassName(), actual.getFactoryClassName());
      assertEquals(expected.getName(), actual.getName());
      assertEquals(expected.getParams().size(), actual.getParams().size());
      for (Map.Entry<String, Object> entry : expected.getParams().entrySet()) {
         String key = entry.getKey();
         assertEquals(expected.getParams().get(key).toString(), actual.getParams().get(key).toString());
      }
   }
}
