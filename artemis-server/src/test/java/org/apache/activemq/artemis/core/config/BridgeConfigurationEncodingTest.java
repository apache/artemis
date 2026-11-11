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

import java.util.List;
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

public class BridgeConfigurationEncodingTest {

   @Test
   public void testEncodedBytesWithTransformerWithPropertiesWithStaticConnectors() {
      doEncodedBytesTestImpl(true, true, true);
   }

   @Test
   public void testEncodedBytesWithTransformerWithoutPropertiesWithStaticConnectors() {
      doEncodedBytesTestImpl(true, false, true);
   }

   @Test
   public void testEncodedBytesWithTransformerWithPropertiesWithoutStaticConnectors() {
      doEncodedBytesTestImpl(true, true, false);
   }

   @Test
   public void testEncodedBytesWithTransformerWithoutPropertiesWithoutStaticConnectors() {
      doEncodedBytesTestImpl(true, false, false);
   }

   @Test
   public void testEncodedBytesWithoutTransformerWithStaticConnectors() {
      doEncodedBytesTestImpl(false, false, true);
   }

   @Test
   public void testEncodedBytesWithoutTransformerWithoutStaticConnectors() {
      doEncodedBytesTestImpl(false, false, false);
   }

   private void doEncodedBytesTestImpl(final boolean transformer, boolean properties, boolean staticConnectors) {
      if (properties && !transformer) {
         throw new IllegalArgumentException("Must have transformer to have properties for it");
      }

      final String name = "aa";
      final String parentName = "bb";
      final String queueName = "cc";
      final String forwardingAddress = "dd";
      final String filterString = "ee";
      final String discoveryGroup = "ff";
      final boolean ha = true;
      final long retryInterval = 0L;
      final double retryIntervalMultiplier = 0.0;
      final int initialConnectAttempts = 0;
      final int reconnectAttempts = 1;
      final int reconnectAttemptsOnSameNode = 2;
      final boolean useDuplicateDetection = false;
      final int confirmationWindowSize = 3;
      final int producerWindowSize = 4;
      final long clientFailureCheckPeriod = 1L;
      final String user = "gg";
      final String password = "hh";
      final long connectionTtl = 2L;
      final long maxRetryInterval = 3L;
      final int minLargeMessageSize = 5;
      final long callTimeout = 4L;
      final int concurrency = 6;
      final boolean configurationManaged = false;
      final ComponentConfigurationRoutingType routingType = ComponentConfigurationRoutingType.ANYCAST;
      final long pendingAckTimeout = 5L;
      final String staticConnector = "ii";
      final String clientId = "mm";

      BridgeConfiguration configuration = new BridgeConfiguration()
         .setName(name)
         .setParentName(parentName)
         .setQueueName(queueName)
         .setForwardingAddress(forwardingAddress)
         .setFilterString(filterString)
         .setDiscoveryGroupName(discoveryGroup)
         .setHA(ha)
         .setRetryInterval(retryInterval)
         .setRetryIntervalMultiplier(retryIntervalMultiplier)
         .setInitialConnectAttempts(initialConnectAttempts)
         .setReconnectAttempts(reconnectAttempts)
         .setReconnectAttemptsOnSameNode(reconnectAttemptsOnSameNode)
         .setUseDuplicateDetection(useDuplicateDetection)
         .setConfirmationWindowSize(confirmationWindowSize)
         .setProducerWindowSize(producerWindowSize)
         .setClientFailureCheckPeriod(clientFailureCheckPeriod)
         .setUser(user)
         .setPassword(password)
         .setConnectionTTL(connectionTtl)
         .setMaxRetryInterval(maxRetryInterval)
         .setMinLargeMessageSize(minLargeMessageSize)
         .setCallTimeout(callTimeout)
         .setConcurrency(concurrency)
         .setConfigurationManaged(configurationManaged)
         .setRoutingType(routingType)
         .setClientId(clientId);

      if (transformer) {
         final String transformerClass = "jj";
         final TransformerConfiguration myDivertTransformer = new TransformerConfiguration(transformerClass);

         if (properties) {
            final String transformerKey = "kk";
            final String transformerValue = "ll";
            myDivertTransformer.getProperties().put(transformerKey, transformerValue);
         }

         configuration.setTransformerConfiguration(myDivertTransformer);
      }

      if (staticConnectors) {
         configuration.setStaticConnectors(List.of(staticConnector));
      }
      configuration.setPendingAckTimeout(pendingAckTimeout);

      int encodeSize = configuration.getEncodeSize();
      ActiveMQBuffer data = ActiveMQBuffers.fixedBuffer(encodeSize);
      configuration.encode(data);
      assertEquals(encodeSize, data.writerIndex());

      // name
      byte[] read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 97, 0, 97}, read);

      // parentName
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 98, 0, 98}, read);

      // queueName
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 99, 0, 99}, read);

      // forwardingAddress
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 100, 0, 100}, read);

      // filterString
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 101, 0, 101}, read);

      // discoveryGroup
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 102, 0, 102}, read);

      // ha
      read = new byte[2];
      data.readBytes(read, 0, 2);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, -1}, read);

      // retryInterval
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 0, 0, 0, 0, 0}, read);

      // retryIntervalMultiplier
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {-1, 0, 0, 0, 0, 0, 0, 0, 0}, read);

      // initialConnectAttempts
      read = new byte[5];
      data.readBytes(read, 0, 5);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 0}, read);

      // reconnectAttempts
      read = new byte[5];
      data.readBytes(read, 0, 5);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 1}, read);

      // reconnectAttemptsOnSameNode
      read = new byte[5];
      data.readBytes(read, 0, 5);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2}, read);

      // useDuplicateDetection
      read = new byte[2];
      data.readBytes(read, 0, 2);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0}, read);

      // confirmationWindowSize
      read = new byte[5];
      data.readBytes(read, 0, 5);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 3}, read);

      // producerWindowSize
      read = new byte[5];
      data.readBytes(read, 0, 5);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 4}, read);

      // clientFailureCheckPeriod
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 0, 0, 0, 0, 1}, read);

      // user
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 103, 0, 103}, read);

      // password
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 104, 0, 104}, read);

      // connectionTtl
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 0, 0, 0, 0, 2}, read);

      // maxRetryInterval
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 0, 0, 0, 0, 3}, read);

      // minLargeMessageSize
      read = new byte[5];
      data.readBytes(read, 0, 5);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 5}, read);

      // callTimeout
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 0, 0, 0, 0, 4}, read);

      // concurrency
      read = new byte[5];
      data.readBytes(read, 0, 5);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 6}, read);

      // configurationManaged
      read = new byte[2];
      data.readBytes(read, 0, 2);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0}, read);

      // routingType
      assertEquals(ComponentConfigurationRoutingType.ANYCAST.getType(), data.readByte());

      if (transformer) {
         // transformerClassName
         read = new byte[9];
         data.readBytes(read, 0, 9);
         assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 106, 0, 106}, read);

         if (properties) {
            // number of transformer properties
            read = new byte[4];
            data.readBytes(read, 0, 4);
            assertArrayEquals(new byte[] {0, 0, 0, 1}, read);

            // transformer key name
            read = new byte[9];
            data.readBytes(read, 0, 9);
            assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 107, 0, 107}, read);

            // transformer key value
            read = new byte[9];
            data.readBytes(read, 0, 9);
            assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 108, 0, 108}, read);
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

      if (staticConnectors) {
         // staticConnector count
         read = new byte[4];
         data.readBytes(read, 0, 4);
         assertArrayEquals(new byte[]{0, 0, 0, 1}, read);

         // static connector
         read = new byte[9];
         data.readBytes(read, 0, 9);
         assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 105, 0, 105}, read);
      } else {
         // staticConnector count
         read = new byte[4];
         data.readBytes(read, 0, 4);
         assertArrayEquals(new byte[]{0, 0, 0, 0}, read);
      }

      // pendingAckTimeout
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 0, 0, 0, 0, 5}, read);

      // clientId
      read = new byte[9];
      data.readBytes(read, 0, 9);
      assertArrayEquals(new byte[] {DataConstants.NOT_NULL, 0, 0, 0, 2, 0, 109, 0, 109}, read);

      assertEquals(0, data.readableBytes());
   }

   @Test
   public void testEncodeDecodeWithTransformer() {
      TransformerConfiguration mytransformer = new TransformerConfiguration("myBridgeTransformer");
      mytransformer.getProperties().put("key1", "prop1");
      mytransformer.getProperties().put("key2", "prop2");
      mytransformer.getProperties().put("key3", "prop3");

      BridgeConfiguration configuration = new BridgeConfiguration()
         .setName("name")
         .setParentName("parentName")
         .setQueueName("queueName")
         .setForwardingAddress("forwardingAddress")
         .setFilterString("filterString")
         .setDiscoveryGroupName("discoveryGroup")
         .setHA(false)
         .setRetryInterval(0)
         .setRetryIntervalMultiplier(1)
         .setInitialConnectAttempts(2)
         .setReconnectAttempts(3)
         .setReconnectAttemptsOnSameNode(4)
         .setUseDuplicateDetection(false)
         .setConfirmationWindowSize(5)
         .setProducerWindowSize(6)
         .setClientFailureCheckPeriod(7)
         .setUser("user")
         .setPassword("password")
         .setConnectionTTL(8)
         .setMaxRetryInterval(9)
         .setMinLargeMessageSize(10)
         .setCallTimeout(11)
         .setConcurrency(12)
         .setConfigurationManaged(true)
         .setRoutingType(ComponentConfigurationRoutingType.ANYCAST)
         .setTransformerConfiguration(mytransformer)
         .setStaticConnectors(List.of("tcp://localhost:61616"))
         .setPendingAckTimeout(13)
         .setClientId("myClientID");

      int encodeSize = configuration.getEncodeSize();
      ActiveMQBuffer data = ActiveMQBuffers.fixedBuffer(encodeSize);
      configuration.encode(data);
      BridgeConfiguration persistedBridgeConfiguration = new BridgeConfiguration();
      persistedBridgeConfiguration.decode(data);

      assertEquals(configuration.getName(), persistedBridgeConfiguration.getName());
      assertEquals(configuration.getParentName(), persistedBridgeConfiguration.getParentName());
      assertEquals(configuration.getQueueName(), persistedBridgeConfiguration.getQueueName());
      assertEquals(configuration.getForwardingAddress(), persistedBridgeConfiguration.getForwardingAddress());
      assertEquals(configuration.getFilterString(), persistedBridgeConfiguration.getFilterString());
      assertEquals(configuration.getDiscoveryGroupName(), persistedBridgeConfiguration.getDiscoveryGroupName());
      assertEquals(configuration.isHA(), persistedBridgeConfiguration.isHA());
      assertEquals(configuration.getRetryInterval(), persistedBridgeConfiguration.getRetryInterval());
      assertEquals(configuration.getRetryIntervalMultiplier(), persistedBridgeConfiguration.getRetryIntervalMultiplier());
      assertEquals(configuration.getInitialConnectAttempts(), persistedBridgeConfiguration.getInitialConnectAttempts());
      assertEquals(configuration.getReconnectAttempts(), persistedBridgeConfiguration.getReconnectAttempts());
      assertEquals(configuration.getReconnectAttemptsOnSameNode(), persistedBridgeConfiguration.getReconnectAttemptsOnSameNode());
      assertEquals(configuration.isUseDuplicateDetection(), persistedBridgeConfiguration.isUseDuplicateDetection());
      assertEquals(configuration.getConfirmationWindowSize(), persistedBridgeConfiguration.getConfirmationWindowSize());
      assertEquals(configuration.getProducerWindowSize(), persistedBridgeConfiguration.getProducerWindowSize());
      assertEquals(configuration.getClientFailureCheckPeriod(), persistedBridgeConfiguration.getClientFailureCheckPeriod());
      assertEquals(configuration.getUser(), persistedBridgeConfiguration.getUser());
      assertEquals(configuration.getPassword(), persistedBridgeConfiguration.getPassword());
      assertEquals(configuration.getConnectionTTL(), persistedBridgeConfiguration.getConnectionTTL());
      assertEquals(configuration.getMaxRetryInterval(), persistedBridgeConfiguration.getMaxRetryInterval());
      assertEquals(configuration.getMinLargeMessageSize(), persistedBridgeConfiguration.getMinLargeMessageSize());
      assertEquals(configuration.getCallTimeout(), persistedBridgeConfiguration.getCallTimeout());
      assertEquals(configuration.getConcurrency(), persistedBridgeConfiguration.getConcurrency());
      assertEquals(configuration.isConfigurationManaged(), persistedBridgeConfiguration.isConfigurationManaged());
      assertEquals(configuration.getRoutingType(), persistedBridgeConfiguration.getRoutingType());
      assertNotNull(persistedBridgeConfiguration.getTransformerConfiguration());
      assertEquals("myBridgeTransformer", persistedBridgeConfiguration.getTransformerConfiguration().getClassName());
      Map<String, String> properties = persistedBridgeConfiguration.getTransformerConfiguration().getProperties();
      assertEquals(3, properties.size());
      assertEquals("prop1", properties.get("key1"));
      assertEquals("prop2", properties.get("key2"));
      assertEquals("prop3", properties.get("key3"));
      assertEquals(configuration.getStaticConnectors(), persistedBridgeConfiguration.getStaticConnectors());
      assertEquals(configuration.getPendingAckTimeout(), persistedBridgeConfiguration.getPendingAckTimeout());
      assertEquals(configuration.getClientId(), persistedBridgeConfiguration.getClientId());
   }

   @Test
   public void testEncodeDecodeWithTransformerWithNoProperties() {
      TransformerConfiguration mytransformer = new TransformerConfiguration("myBridgeTransformer");

      BridgeConfiguration configuration = new BridgeConfiguration()
         .setName("name")
         .setParentName("parentName")
         .setQueueName("queueName")
         .setForwardingAddress("forwardingAddress")
         .setFilterString("filterString")
         .setDiscoveryGroupName("discoveryGroup")
         .setHA(false)
         .setRetryInterval(0)
         .setRetryIntervalMultiplier(1)
         .setInitialConnectAttempts(2)
         .setReconnectAttempts(3)
         .setReconnectAttemptsOnSameNode(4)
         .setUseDuplicateDetection(false)
         .setConfirmationWindowSize(5)
         .setProducerWindowSize(6)
         .setClientFailureCheckPeriod(7)
         .setUser("user")
         .setPassword("password")
         .setConnectionTTL(8)
         .setMaxRetryInterval(9)
         .setMinLargeMessageSize(10)
         .setCallTimeout(11)
         .setConcurrency(12)
         .setConfigurationManaged(true)
         .setRoutingType(ComponentConfigurationRoutingType.ANYCAST)
         .setTransformerConfiguration(mytransformer)
         .setStaticConnectors(List.of("tcp://localhost:61616"))
         .setPendingAckTimeout(13)
         .setClientId("myClientID");

      int encodeSize = configuration.getEncodeSize();
      ActiveMQBuffer data = ActiveMQBuffers.fixedBuffer(encodeSize);
      configuration.encode(data);
      BridgeConfiguration persistedBridgeConfiguration = new BridgeConfiguration();
      persistedBridgeConfiguration.decode(data);

      assertEquals(configuration.getName(), persistedBridgeConfiguration.getName());
      assertEquals(configuration.getParentName(), persistedBridgeConfiguration.getParentName());
      assertEquals(configuration.getQueueName(), persistedBridgeConfiguration.getQueueName());
      assertEquals(configuration.getForwardingAddress(), persistedBridgeConfiguration.getForwardingAddress());
      assertEquals(configuration.getFilterString(), persistedBridgeConfiguration.getFilterString());
      assertEquals(configuration.getDiscoveryGroupName(), persistedBridgeConfiguration.getDiscoveryGroupName());
      assertEquals(configuration.isHA(), persistedBridgeConfiguration.isHA());
      assertEquals(configuration.getRetryInterval(), persistedBridgeConfiguration.getRetryInterval());
      assertEquals(configuration.getRetryIntervalMultiplier(), persistedBridgeConfiguration.getRetryIntervalMultiplier());
      assertEquals(configuration.getInitialConnectAttempts(), persistedBridgeConfiguration.getInitialConnectAttempts());
      assertEquals(configuration.getReconnectAttempts(), persistedBridgeConfiguration.getReconnectAttempts());
      assertEquals(configuration.getReconnectAttemptsOnSameNode(), persistedBridgeConfiguration.getReconnectAttemptsOnSameNode());
      assertEquals(configuration.isUseDuplicateDetection(), persistedBridgeConfiguration.isUseDuplicateDetection());
      assertEquals(configuration.getConfirmationWindowSize(), persistedBridgeConfiguration.getConfirmationWindowSize());
      assertEquals(configuration.getProducerWindowSize(), persistedBridgeConfiguration.getProducerWindowSize());
      assertEquals(configuration.getClientFailureCheckPeriod(), persistedBridgeConfiguration.getClientFailureCheckPeriod());
      assertEquals(configuration.getUser(), persistedBridgeConfiguration.getUser());
      assertEquals(configuration.getPassword(), persistedBridgeConfiguration.getPassword());
      assertEquals(configuration.getConnectionTTL(), persistedBridgeConfiguration.getConnectionTTL());
      assertEquals(configuration.getMaxRetryInterval(), persistedBridgeConfiguration.getMaxRetryInterval());
      assertEquals(configuration.getMinLargeMessageSize(), persistedBridgeConfiguration.getMinLargeMessageSize());
      assertEquals(configuration.getCallTimeout(), persistedBridgeConfiguration.getCallTimeout());
      assertEquals(configuration.getConcurrency(), persistedBridgeConfiguration.getConcurrency());
      assertEquals(configuration.isConfigurationManaged(), persistedBridgeConfiguration.isConfigurationManaged());
      assertEquals(configuration.getRoutingType(), persistedBridgeConfiguration.getRoutingType());
      assertNotNull(persistedBridgeConfiguration.getTransformerConfiguration());
      assertEquals("myBridgeTransformer", persistedBridgeConfiguration.getTransformerConfiguration().getClassName());
      Map<String, String> properties = persistedBridgeConfiguration.getTransformerConfiguration().getProperties();
      assertEquals(0, properties.size());
      assertEquals(configuration.getStaticConnectors(), persistedBridgeConfiguration.getStaticConnectors());
      assertEquals(configuration.getPendingAckTimeout(), persistedBridgeConfiguration.getPendingAckTimeout());
      assertEquals(configuration.getClientId(), persistedBridgeConfiguration.getClientId());
   }

   @Test
   public void testEncodeDecodeWithoutTransformer() {
      BridgeConfiguration configuration = new BridgeConfiguration()
         .setName("name")
         .setParentName("parentName")
         .setQueueName("queueName")
         .setForwardingAddress("forwardingAddress")
         .setFilterString("filterString")
         .setDiscoveryGroupName("discoveryGroup")
         .setHA(false)
         .setRetryInterval(0)
         .setRetryIntervalMultiplier(1)
         .setInitialConnectAttempts(2)
         .setReconnectAttempts(3)
         .setReconnectAttemptsOnSameNode(4)
         .setUseDuplicateDetection(false)
         .setConfirmationWindowSize(5)
         .setProducerWindowSize(6)
         .setClientFailureCheckPeriod(7)
         .setUser("user")
         .setPassword("password")
         .setConnectionTTL(8)
         .setMaxRetryInterval(9)
         .setMinLargeMessageSize(10)
         .setCallTimeout(11)
         .setConcurrency(12)
         .setConfigurationManaged(true)
         .setRoutingType(ComponentConfigurationRoutingType.ANYCAST)
         .setStaticConnectors(List.of("tcp://localhost:61616"))
         .setPendingAckTimeout(13)
         .setClientId("myClientID");

      int encodeSize = configuration.getEncodeSize();
      ActiveMQBuffer data = ActiveMQBuffers.fixedBuffer(encodeSize);
      configuration.encode(data);
      BridgeConfiguration persistedBridgeConfiguration = new BridgeConfiguration();
      persistedBridgeConfiguration.decode(data);

      assertEquals(configuration.getName(), persistedBridgeConfiguration.getName());
      assertEquals(configuration.getParentName(), persistedBridgeConfiguration.getParentName());
      assertEquals(configuration.getQueueName(), persistedBridgeConfiguration.getQueueName());
      assertEquals(configuration.getForwardingAddress(), persistedBridgeConfiguration.getForwardingAddress());
      assertEquals(configuration.getFilterString(), persistedBridgeConfiguration.getFilterString());
      assertEquals(configuration.getDiscoveryGroupName(), persistedBridgeConfiguration.getDiscoveryGroupName());
      assertEquals(configuration.isHA(), persistedBridgeConfiguration.isHA());
      assertEquals(configuration.getRetryInterval(), persistedBridgeConfiguration.getRetryInterval());
      assertEquals(configuration.getRetryIntervalMultiplier(), persistedBridgeConfiguration.getRetryIntervalMultiplier());
      assertEquals(configuration.getInitialConnectAttempts(), persistedBridgeConfiguration.getInitialConnectAttempts());
      assertEquals(configuration.getReconnectAttempts(), persistedBridgeConfiguration.getReconnectAttempts());
      assertEquals(configuration.getReconnectAttemptsOnSameNode(), persistedBridgeConfiguration.getReconnectAttemptsOnSameNode());
      assertEquals(configuration.isUseDuplicateDetection(), persistedBridgeConfiguration.isUseDuplicateDetection());
      assertEquals(configuration.getConfirmationWindowSize(), persistedBridgeConfiguration.getConfirmationWindowSize());
      assertEquals(configuration.getProducerWindowSize(), persistedBridgeConfiguration.getProducerWindowSize());
      assertEquals(configuration.getClientFailureCheckPeriod(), persistedBridgeConfiguration.getClientFailureCheckPeriod());
      assertEquals(configuration.getUser(), persistedBridgeConfiguration.getUser());
      assertEquals(configuration.getPassword(), persistedBridgeConfiguration.getPassword());
      assertEquals(configuration.getConnectionTTL(), persistedBridgeConfiguration.getConnectionTTL());
      assertEquals(configuration.getMaxRetryInterval(), persistedBridgeConfiguration.getMaxRetryInterval());
      assertEquals(configuration.getMinLargeMessageSize(), persistedBridgeConfiguration.getMinLargeMessageSize());
      assertEquals(configuration.getCallTimeout(), persistedBridgeConfiguration.getCallTimeout());
      assertEquals(configuration.getConcurrency(), persistedBridgeConfiguration.getConcurrency());
      assertEquals(configuration.isConfigurationManaged(), persistedBridgeConfiguration.isConfigurationManaged());
      assertEquals(configuration.getRoutingType(), persistedBridgeConfiguration.getRoutingType());
      assertNull(persistedBridgeConfiguration.getTransformerConfiguration());
      assertEquals(configuration.getStaticConnectors(), persistedBridgeConfiguration.getStaticConnectors());
      assertEquals(configuration.getPendingAckTimeout(), persistedBridgeConfiguration.getPendingAckTimeout());
      assertEquals(configuration.getClientId(), persistedBridgeConfiguration.getClientId());
   }
}
