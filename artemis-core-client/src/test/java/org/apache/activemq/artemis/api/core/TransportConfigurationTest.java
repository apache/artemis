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
package org.apache.activemq.artemis.api.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.junit.jupiter.api.Test;

public class TransportConfigurationTest {

   @Test
   public void testEquals() {
      TransportConfiguration configuration = new TransportConfiguration("SomeClass", new HashMap<>(), null);
      TransportConfiguration configuration2 = new TransportConfiguration("SomeClass", new HashMap<>(), null);

      assertEquals(configuration, configuration2);
      assertEquals(configuration.hashCode(), configuration2.hashCode());

      Map<String, Object> configMap1 = new HashMap<>();
      configMap1.put("host", "localhost");
      Map<String, Object> configMap2 = new HashMap<>();
      configMap2.put("host", "localhost");

      configuration = new TransportConfiguration("SomeClass", configMap1, null);
      configuration2 = new TransportConfiguration("SomeClass", configMap2, null);
      assertEquals(configuration, configuration2);
      assertEquals(configuration.hashCode(), configuration2.hashCode());

      configuration = new TransportConfiguration("SomeClass", configMap1, "name1");
      configuration2 = new TransportConfiguration("SomeClass", configMap2, "name2");
      assertNotEquals(configuration, configuration2);
      assertNotEquals(configuration.hashCode(), configuration2.hashCode());
      assertTrue(configuration.isEquivalent(configuration2));

      configMap1 = new HashMap<>();
      configMap1.put("host", "localhost");
      configMap2 = new HashMap<>();
      configMap2.put("host", "localhost3");
      configuration = new TransportConfiguration("SomeClass", configMap1, null);
      configuration2 = new TransportConfiguration("SomeClass", configMap2, null);
      assertNotEquals(configuration, configuration2);
      assertNotEquals(configuration.hashCode(), configuration2.hashCode());

   }

   @Test
   public void testExtraParamsEquals() {
      final String name = "";
      final String className = this.getClass().getName();
      final Map<String, Object> params = Collections.emptyMap();
      final Map<String, Object> extraParams = Collections.singletonMap("key", "foo");

      assertEquals(new TransportConfiguration(className, params, name, null), new TransportConfiguration(className, params, name, null));
      assertEquals(new TransportConfiguration(className, params, name, null), new TransportConfiguration(className, params, name, Collections.emptyMap()));
      assertEquals(new TransportConfiguration(className, params, name, Collections.emptyMap()), new TransportConfiguration(className, params, name, null));
      assertEquals(new TransportConfiguration(className, params, name, extraParams), new TransportConfiguration(className, params, name, extraParams));
      assertEquals(new TransportConfiguration(className, params, name, extraParams), new TransportConfiguration(className, params, name, new HashMap<>(extraParams)));

      assertNotEquals(new TransportConfiguration(className, params, name, null), new TransportConfiguration(className, params, name, extraParams));
      assertNotEquals(new TransportConfiguration(className, params, name, Collections.emptyMap()), new TransportConfiguration(className, params, name, extraParams));
      assertNotEquals(new TransportConfiguration(className, params, name, extraParams), new TransportConfiguration(className, params, name, Collections.singletonMap("key", "other")));
      assertNotEquals(new TransportConfiguration(className, params, name, extraParams), new TransportConfiguration(className, params, name, null));
      assertNotEquals(new TransportConfiguration(className, params, name, extraParams), new TransportConfiguration(className, params, name, Collections.emptyMap()));
   }

   @Test
   public void testToStringObfuscatesPasswords() {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "secret_password");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "secret_password");

      TransportConfiguration configuration = new TransportConfiguration("SomeClass", params, null);

      assertFalse(configuration.toString().contains("secret_password"), "configuration contains secret_password");
   }

   @Test
   public void testEncodingDecoding() {
      Map<String, Object> params = new HashMap<>();
      params.put("BOOLEAN_PARAM", true);
      params.put("INT_PARAM", 0);
      params.put("LONG_PARAM", 1);
      params.put("STRING_PARAM", "A");

      Map<String, Object> extraProps = new HashMap<>();
      extraProps.put("EXTRA_BOOLEAN_PROP", false);
      extraProps.put("EXTRA_INT_PROP", 1);
      extraProps.put("EXTRA_LONG_PROP", 0);
      extraProps.put("EXTRA_STRING_PROP", "Z");

      testEncodingDecoding(new TransportConfiguration("SomeClass", params, "TEST", extraProps));
   }

   @Test
   public void testEncodingDecodingWithEmptyMaps() {
      testEncodingDecoding(new TransportConfiguration("SomeClass", Collections.emptyMap(), "TEST", Collections.emptyMap()));
   }

   @Test
   public void testEncodingDecodingWithNullMaps() {
      testEncodingDecoding(new TransportConfiguration("SomeClass", null, "TEST", null));
   }

   private void testEncodingDecoding(TransportConfiguration transportConfiguration) {
      ActiveMQBuffer buffer = new ChannelBufferWrapper(Unpooled.buffer(1024));

      transportConfiguration.encode(buffer);

      TransportConfiguration decodedTransportConfiguration = new TransportConfiguration();
      decodedTransportConfiguration.decode(buffer);

      assertFalse(buffer.readable());

      assertEquals(transportConfiguration.getParams(), decodedTransportConfiguration.getParams());

      assertTrue((transportConfiguration.getExtraParams() == null && (decodedTransportConfiguration.getExtraParams() == null || decodedTransportConfiguration.getExtraParams().isEmpty())) ||
            (decodedTransportConfiguration.getExtraParams() == null && (transportConfiguration.getExtraParams() == null || transportConfiguration.getExtraParams().isEmpty())) ||
            (transportConfiguration.getExtraParams() != null && decodedTransportConfiguration.getExtraParams() != null && transportConfiguration.getExtraParams().equals(decodedTransportConfiguration.getExtraParams())));

      assertEquals(transportConfiguration, decodedTransportConfiguration);
   }
}
