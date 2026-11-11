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
package org.apache.activemq.artemis.core.config;

import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.utils.DataConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DivertConfigurationEncodingTest {

   @Test
   public void testEncodedBytesWithTransformer() {
      doEncodedBytesTestImpl(true, true);
   }

   @Test
   public void testEncodedBytesWithTransformerWithNoProperties() {
      doEncodedBytesTestImpl(true, false);
   }

   @Test
   public void testEncodedBytesWithoutTransformer() {
      doEncodedBytesTestImpl(false, false);
   }

   private void doEncodedBytesTestImpl(final boolean transformer, boolean properties) {
      if (properties && !transformer) {
         throw new IllegalArgumentException("Must have transformer to have properties for it");
      }

      final String name = "aa";
      final String address = "bb";
      final String forwardingAddress = "cc";
      final String routingName = "dd";
      final boolean exclusive = true;
      final String filterString = "ee";
      final ComponentConfigurationRoutingType routingType = ComponentConfigurationRoutingType.ANYCAST;

      DivertConfiguration configuration = new DivertConfiguration()
         .setName(name)
         .setAddress(address)
         .setForwardingAddress(forwardingAddress)
         .setFilterString(filterString)
         .setRoutingName(routingName)
         .setExclusive(exclusive)
         .setRoutingType(routingType);

      if (transformer) {
         final String transformerClass = "ff";
         final TransformerConfiguration myDivertTransformer = new TransformerConfiguration(transformerClass);

         if (properties) {
            final String transformerKey = "gg";
            final String transformerValue = "hh";
            myDivertTransformer.getProperties().put(transformerKey, transformerValue);
         }

         configuration.setTransformerConfiguration(myDivertTransformer);
      }

      int encodeSize = configuration.getEncodeSize();
      ActiveMQBuffer data = ActiveMQBuffers.fixedBuffer(encodeSize);
      configuration.encode(data);
      assertEquals(encodeSize, data.writerIndex());

      // name
      byte[] read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 97, 0, 97}, read);

      // address
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 98, 0, 98}, read);

      // forwardingAddress
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 99, 0, 99}, read);

      // routingName
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 100, 0, 100}, read);

      // exclusive
      assertEquals((byte) -1, data.readByte());

      // filterString
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 101, 0, 101}, read);

      // routingType
      assertEquals(ComponentConfigurationRoutingType.ANYCAST.getType(), data.readByte());

      if (transformer) {
         // transformerClassName
         read = new byte[9];
         data.readBytes(read, 0, 9);
         assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 102, 0, 102}, read);

         if (properties) {
            // number of transformer properties
            read = new byte[4];
            data.readBytes(read, 0, 4);
            assertArrayEquals(new byte[] {0, 0, 0, 1}, read);

            // transformer key name
            read = new byte[9];
            data.readBytes(read, 0, 9);
            assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 103, 0, 103}, read);

            // transformer key value
            read = new byte[9];
            data.readBytes(read, 0, 9);
            assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 104, 0, 104}, read);
         } else {
            // transformer properties not present (indicated via 0 count)
            read = new byte[4];
            data.readBytes(read, 0, 4);
            assertArrayEquals(new byte[] {0, 0, 0, 0}, read);
         }
      } else {
         // transformer not present
         assertEquals(DataConstants.NULL, data.readByte());
      }

      assertEquals(0, data.readableBytes());
   }

   @Test
   public void testEncodeDecodeWithTransformer() {
      TransformerConfiguration mytransformer = new TransformerConfiguration("myDivertTransformer");
      mytransformer.getProperties().put("key1", "prop1");
      mytransformer.getProperties().put("key2", "prop2");
      mytransformer.getProperties().put("key3", "prop3");

      DivertConfiguration configuration = new DivertConfiguration()
         .setName("name")
         .setAddress("address")
         .setForwardingAddress("forward")
         .setFilterString("foo='foo'")
         .setRoutingName("myDivertRoutingName")
         .setExclusive(true)
         .setRoutingType(ComponentConfigurationRoutingType.STRIP)
         .setTransformerConfiguration(mytransformer);

      int encodeSize = configuration.getEncodeSize();
      ActiveMQBuffer data = ActiveMQBuffers.fixedBuffer(encodeSize);
      configuration.encode(data);
      DivertConfiguration persistedDivertConfiguration = new DivertConfiguration();
      persistedDivertConfiguration.decode(data);

      assertEquals(configuration.getName(), persistedDivertConfiguration.getName());
      assertEquals(configuration.getAddress(), persistedDivertConfiguration.getAddress());
      assertEquals(configuration.getForwardingAddress(), persistedDivertConfiguration.getForwardingAddress());
      assertEquals(configuration.getFilterString(), persistedDivertConfiguration.getFilterString());
      assertEquals(configuration.getRoutingName(), persistedDivertConfiguration.getRoutingName());
      assertEquals(configuration.isExclusive(), persistedDivertConfiguration.isExclusive());
      assertEquals(configuration.getRoutingType(), persistedDivertConfiguration.getRoutingType());
      assertNotNull(persistedDivertConfiguration.getTransformerConfiguration());
      assertEquals("myDivertTransformer", persistedDivertConfiguration.getTransformerConfiguration().getClassName());
      Map<String, String> properties = persistedDivertConfiguration.getTransformerConfiguration().getProperties();
      assertEquals(3, properties.size());
      assertEquals("prop1", properties.get("key1"));
      assertEquals("prop2", properties.get("key2"));
      assertEquals("prop3", properties.get("key3"));
   }

   @Test
   public void testEncodeDecodeWithTransformerWithNoProperties() {
      TransformerConfiguration mytransformer = new TransformerConfiguration("myDivertTransformer");

      DivertConfiguration configuration = new DivertConfiguration()
         .setName("name")
         .setAddress("address")
         .setForwardingAddress("forward")
         .setFilterString("foo='foo'")
         .setRoutingName("myDivertRoutingName")
         .setExclusive(true)
         .setRoutingType(ComponentConfigurationRoutingType.MULTICAST)
         .setTransformerConfiguration(mytransformer);

      int encodeSize = configuration.getEncodeSize();
      ActiveMQBuffer data = ActiveMQBuffers.fixedBuffer(encodeSize);
      configuration.encode(data);
      DivertConfiguration persistedDivertConfiguration = new DivertConfiguration();
      persistedDivertConfiguration.decode(data);

      assertEquals(configuration.getName(), persistedDivertConfiguration.getName());
      assertEquals(configuration.getAddress(), persistedDivertConfiguration.getAddress());
      assertEquals(configuration.getForwardingAddress(), persistedDivertConfiguration.getForwardingAddress());
      assertEquals(configuration.getFilterString(), persistedDivertConfiguration.getFilterString());
      assertEquals(configuration.getRoutingName(), persistedDivertConfiguration.getRoutingName());
      assertEquals(configuration.isExclusive(), persistedDivertConfiguration.isExclusive());
      assertEquals(configuration.getRoutingType(), persistedDivertConfiguration.getRoutingType());
      assertNotNull(persistedDivertConfiguration.getTransformerConfiguration());
      assertEquals("myDivertTransformer", persistedDivertConfiguration.getTransformerConfiguration().getClassName());
      Map<String, String> properties = persistedDivertConfiguration.getTransformerConfiguration().getProperties();
      assertEquals(0, properties.size());
   }

   @Test
   public void testEncodeDecodeWithoutTransformer() {
      DivertConfiguration configuration = new DivertConfiguration()
         .setName("name")
         .setAddress("address")
         .setForwardingAddress("forward")
         .setFilterString("foo='foo'")
         .setExclusive(true)
         .setRoutingName("myDivertRoutingName")
         .setRoutingType(ComponentConfigurationRoutingType.PASS);

      int encodeSize = configuration.getEncodeSize();
      ActiveMQBuffer data = ActiveMQBuffers.fixedBuffer(encodeSize);
      configuration.encode(data);
      DivertConfiguration decodedDivertConfiguration = new DivertConfiguration();
      decodedDivertConfiguration.decode(data);

      assertEquals(configuration.getName(), decodedDivertConfiguration.getName());
      assertEquals(configuration.getAddress(), decodedDivertConfiguration.getAddress());
      assertEquals(configuration.getForwardingAddress(), decodedDivertConfiguration.getForwardingAddress());
      assertEquals(configuration.getFilterString(), decodedDivertConfiguration.getFilterString());
      assertEquals(configuration.getRoutingName(), decodedDivertConfiguration.getRoutingName());
      assertEquals(configuration.isExclusive(), decodedDivertConfiguration.isExclusive());
      assertEquals(configuration.getRoutingType(), decodedDivertConfiguration.getRoutingType());
      assertNull(decodedDivertConfiguration.getTransformerConfiguration());
   }
}
