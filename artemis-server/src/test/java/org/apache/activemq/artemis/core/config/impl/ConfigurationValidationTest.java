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
package org.apache.activemq.artemis.core.config.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBridgeBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.apache.activemq.artemis.utils.XMLUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

public class ConfigurationValidationTest extends ServerTestBase {

   static {
      System.setProperty("a2Prop", "a2");
      System.setProperty("falseProp", "false");
      System.setProperty("trueProp", "true");
      System.setProperty("ninetyTwoProp", "92");
   }

   /**
    * test does not pass in eclipse (because it can not find artemis-configuration.xsd). It runs fine on the CLI with
    * the proper env setting.
    */
   @Test
   public void testMinimalConfiguration() throws Exception {
      String xml = "<core xmlns='urn:activemq:core'>" + "</core>";
      Element element = XMLUtil.stringToElement(xml);
      assertNotNull(element);
      XMLUtil.validate(element, "schema/artemis-configuration.xsd");
   }

   @Test
   public void testFullConfiguration() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("ConfigurationTest-full-config.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      assertTrue(fc.isPersistDeliveryCountBeforeDelivery());
   }

   @Test
   public void testAMQPConnectParsing() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("ConfigurationTest-full-config.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      assertEquals(6, fc.getAMQPConnection().size());

      AMQPBrokerConnectConfiguration amqpBrokerConnectConfiguration = fc.getAMQPConnection().get(0);
      assertEquals("testuser", amqpBrokerConnectConfiguration.getUser());
      assertEquals("testpassword", amqpBrokerConnectConfiguration.getPassword());
      assertEquals(33, amqpBrokerConnectConfiguration.getReconnectAttempts());
      assertEquals(333, amqpBrokerConnectConfiguration.getRetryInterval());
      assertEquals("test1", amqpBrokerConnectConfiguration.getName());
      assertEquals("tcp://test1:111", amqpBrokerConnectConfiguration.getUri());

      assertEquals("TEST-SENDER", amqpBrokerConnectConfiguration.getConnectionElements().get(0).getMatchAddress().toString());
      assertEquals(AMQPBrokerConnectionAddressType.SENDER, amqpBrokerConnectConfiguration.getConnectionElements().get(0).getType());
      assertEquals("TEST-RECEIVER", amqpBrokerConnectConfiguration.getConnectionElements().get(1).getMatchAddress().toString());
      assertEquals(AMQPBrokerConnectionAddressType.RECEIVER, amqpBrokerConnectConfiguration.getConnectionElements().get(1).getType());
      assertEquals("TEST-PEER", amqpBrokerConnectConfiguration.getConnectionElements().get(2).getMatchAddress().toString());
      assertEquals(AMQPBrokerConnectionAddressType.PEER, amqpBrokerConnectConfiguration.getConnectionElements().get(2).getType());
      assertEquals("TEST-WITH-QUEUE-NAME", amqpBrokerConnectConfiguration.getConnectionElements().get(3).getQueueName().toString());
      assertNull(amqpBrokerConnectConfiguration.getConnectionElements().get(3).getMatchAddress());
      assertEquals(AMQPBrokerConnectionAddressType.RECEIVER, amqpBrokerConnectConfiguration.getConnectionElements().get(3).getType());

      assertEquals(AMQPBrokerConnectionAddressType.MIRROR, amqpBrokerConnectConfiguration.getConnectionElements().get(4).getType());
      AMQPMirrorBrokerConnectionElement mirrorConnectionElement = (AMQPMirrorBrokerConnectionElement) amqpBrokerConnectConfiguration.getConnectionElements().get(4);
      assertFalse(mirrorConnectionElement.isMessageAcknowledgements());
      assertFalse(mirrorConnectionElement.isQueueCreation());
      assertFalse(mirrorConnectionElement.isQueueRemoval());
      assertFalse(mirrorConnectionElement.isDurable());
      assertTrue(mirrorConnectionElement.isSync());

      assertEquals(AMQPBrokerConnectionAddressType.FEDERATION, amqpBrokerConnectConfiguration.getConnectionElements().get(5).getType());
      AMQPFederatedBrokerConnectionElement federationConnectionElement = (AMQPFederatedBrokerConnectionElement) amqpBrokerConnectConfiguration.getConnectionElements().get(5);
      assertEquals("test1", federationConnectionElement.getName());
      assertEquals(1, federationConnectionElement.getLocalQueuePolicies().size());
      federationConnectionElement.getLocalQueuePolicies().forEach((p) -> {
         assertEquals("composite", p.getName());
         assertEquals(1, p.getIncludes().size());
         assertEquals(0, p.getExcludes().size());
         assertNull(p.getTransformerConfiguration());
      });

      assertEquals(AMQPBrokerConnectionAddressType.BRIDGE, amqpBrokerConnectConfiguration.getConnectionElements().get(6).getType());
      AMQPBridgeBrokerConnectionElement bridgeConnectionElement = (AMQPBridgeBrokerConnectionElement) amqpBrokerConnectConfiguration.getConnectionElements().get(6);
      assertEquals("test-bridge", bridgeConnectionElement.getName());
      assertEquals(1, bridgeConnectionElement.getBridgeFromQueuePolicies().size());
      bridgeConnectionElement.getBridgeFromQueuePolicies().forEach((p) -> {
         assertEquals("bridge-queue-1", p.getName());
         assertEquals(1, p.getIncludes().size());
         assertEquals(0, p.getExcludes().size());
         assertNull(p.getTransformerConfiguration());
      });

      amqpBrokerConnectConfiguration = fc.getAMQPConnection().get(1);
      assertNull(amqpBrokerConnectConfiguration.getUser());
      mirrorConnectionElement = (AMQPMirrorBrokerConnectionElement) amqpBrokerConnectConfiguration.getConnectionElements().get(0);
      assertNull(amqpBrokerConnectConfiguration.getPassword());
      assertEquals("test2", amqpBrokerConnectConfiguration.getName());
      assertEquals("tcp://test2:222", amqpBrokerConnectConfiguration.getUri());
      assertTrue(mirrorConnectionElement.isMessageAcknowledgements());
      assertFalse(mirrorConnectionElement.isDurable());
      assertTrue(mirrorConnectionElement.isQueueCreation());
      assertTrue(mirrorConnectionElement.isQueueRemoval());
      assertFalse(mirrorConnectionElement.isSync()); // checking the default

      amqpBrokerConnectConfiguration = fc.getAMQPConnection().get(2);
      assertFalse(amqpBrokerConnectConfiguration.isAutostart());

      amqpBrokerConnectConfiguration = fc.getAMQPConnection().get(3);
      assertFalse(amqpBrokerConnectConfiguration.isAutostart());
      AMQPFederatedBrokerConnectionElement federationElement = (AMQPFederatedBrokerConnectionElement) amqpBrokerConnectConfiguration.getConnectionElements().get(0);
      assertEquals(2, federationElement.getLocalAddressPolicies().size());
      assertEquals(3, federationElement.getLocalQueuePolicies().size());
      assertEquals(2, federationElement.getRemoteAddressPolicies().size());
      assertEquals(2, federationElement.getRemoteQueuePolicies().size());
      assertTrue(federationElement.getProperties().containsKey("amqpCredits"));
      assertEquals("7", federationElement.getProperties().get("amqpCredits"));
      assertTrue(federationElement.getProperties().containsKey("amqpLowCredits"));
      assertEquals("1", federationElement.getProperties().get("amqpLowCredits"));

      federationElement.getLocalAddressPolicies().forEach(p -> {
         if (p.getName().endsWith("lap1")) {
            assertEquals("lap1", p.getName());
            assertEquals(1, p.getIncludes().size());
            p.getIncludes().forEach(match -> assertEquals("orders", match.getAddressMatch()));
            assertEquals(1, p.getExcludes().size());
            p.getExcludes().forEach(match -> assertEquals("all.#", match.getAddressMatch()));
            assertNull(p.getAutoCreate());
            assertFalse(p.getAutoDelete());
            assertEquals(1L, (long) p.getAutoDeleteDelay());
            assertEquals(12L, (long) p.getAutoDeleteMessageCount());
            assertEquals(2, p.getMaxHops());
            assertNotNull(p.getTransformerConfiguration());
            assertEquals("class-name", p.getTransformerConfiguration().getClassName());
         } else if (p.getName().endsWith("lap2")) {
            assertEquals("lap2", p.getName());
            assertFalse(p.getAutoCreate());
         } else {
            fail("Should only be two local address policies");
         }
      });
      federationElement.getLocalQueuePolicies().forEach((p) -> {
         if (p.getName().endsWith("lqp1")) {
            assertEquals(1, (int) p.getPriorityAdjustment());
            assertFalse(p.isIncludeFederated());
            assertEquals("class-another", p.getTransformerConfiguration().getClassName());
            assertNotNull(p.getProperties());
            assertFalse(p.getProperties().isEmpty());
            assertTrue(p.getProperties().containsKey("amqpCredits"));
            assertEquals("1", p.getProperties().get("amqpCredits"));
            assertNull(p.getAutoCreate());
         } else if (p.getName().endsWith("lqp2")) {
            assertNull(p.getPriorityAdjustment());
            assertFalse(p.isIncludeFederated());
            assertNull(p.getAutoCreate());
         } else if (p.getName().endsWith("lqp3")) {
            assertNull(p.getPriorityAdjustment());
            assertFalse(p.isIncludeFederated());
            assertTrue(p.getAutoCreate());
         } else {
            fail("Should only be three local queue policies");
         }
      });
      federationElement.getRemoteAddressPolicies().forEach((p) -> {
         if (p.getName().endsWith("rap1")) {
            assertEquals("rap1", p.getName());
            assertEquals(1, p.getIncludes().size());
            p.getIncludes().forEach(match -> assertEquals("support", match.getAddressMatch()));
            assertEquals(0, p.getExcludes().size());
            assertTrue(p.getAutoDelete());
            assertEquals(2L, (long) p.getAutoDeleteDelay());
            assertEquals(42L, (long) p.getAutoDeleteMessageCount());
            assertEquals(1, p.getMaxHops());
            assertNotNull(p.getTransformerConfiguration());
            assertEquals("something", p.getTransformerConfiguration().getClassName());
            assertEquals(2, p.getTransformerConfiguration().getProperties().size());
            assertEquals("value1", p.getTransformerConfiguration().getProperties().get("key1"));
            assertEquals("value2", p.getTransformerConfiguration().getProperties().get("key2"));
            assertNotNull(p.getProperties());
            assertFalse(p.getProperties().isEmpty());
            assertTrue(p.getProperties().containsKey("amqpCredits"));
            assertEquals("2", p.getProperties().get("amqpCredits"));
            assertTrue(p.getProperties().containsKey("amqpLowCredits"));
            assertEquals("1", p.getProperties().get("amqpLowCredits"));
         } else if (p.getName().endsWith("rap2")) {
            assertEquals("rap2", p.getName());
            assertFalse(p.getAutoCreate());
         } else {
            fail("Should only be two remote address policies");
         }
      });
      federationElement.getRemoteQueuePolicies().forEach((p) -> {
         if (p.getName().endsWith("rqp1")) {
            assertEquals("rqp1", p.getName());
            assertEquals(-1, (int) p.getPriorityAdjustment());
            assertTrue(p.isIncludeFederated());
            p.getIncludes().forEach(match -> {
               assertEquals("#", match.getAddressMatch());
               assertEquals("tracking", match.getQueueMatch());
            });
            assertNotNull(p.getProperties());
            assertFalse(p.getProperties().isEmpty());
            assertTrue(p.getProperties().containsKey("amqpCredits"));
            assertEquals("2", p.getProperties().get("amqpCredits"));
            assertTrue(p.getProperties().containsKey("amqpLowCredits"));
            assertEquals("1", p.getProperties().get("amqpLowCredits"));
            assertNull(p.getAutoCreate());
         } else if (p.getName().endsWith("rqp2")) {
            assertEquals("rqp2", p.getName());
            assertTrue(p.getAutoCreate());
            assertTrue(p.getAutoDelete());
            assertEquals(2L, (long) p.getAutoDeleteDelay());
            assertEquals(42L, (long) p.getAutoDeleteMessageCount());
         } else {
            fail("Should only be two remote queue policies");
         }
      });

      amqpBrokerConnectConfiguration = fc.getAMQPConnection().get(4);
      assertEquals("test-property", amqpBrokerConnectConfiguration.getName());
      assertFalse(amqpBrokerConnectConfiguration.isAutostart());
      assertNotNull(amqpBrokerConnectConfiguration.getConnectionElements().get(0));
      mirrorConnectionElement = (AMQPMirrorBrokerConnectionElement) amqpBrokerConnectConfiguration.getConnectionElements().get(0);
      assertNotNull(mirrorConnectionElement.getProperties());
      assertFalse(mirrorConnectionElement.getProperties().isEmpty());
      assertFalse(Boolean.valueOf((String) mirrorConnectionElement.getProperties().get("tunnel-core-messages")));

      amqpBrokerConnectConfiguration = fc.getAMQPConnection().get(5);
      assertEquals("test-bridge-configuration", amqpBrokerConnectConfiguration.getName());
      assertFalse(amqpBrokerConnectConfiguration.isAutostart());
      assertNotNull(amqpBrokerConnectConfiguration.getConnectionElements().get(0));
      bridgeConnectionElement = (AMQPBridgeBrokerConnectionElement) amqpBrokerConnectConfiguration.getConnectionElements().get(0);
      assertNotNull(bridgeConnectionElement.getProperties());
      assertFalse(bridgeConnectionElement.getProperties().isEmpty());
      assertEquals(1, bridgeConnectionElement.getBridgeFromQueuePolicies().size());
      assertEquals(1, bridgeConnectionElement.getBridgeToQueuePolicies().size());
      assertEquals(1, bridgeConnectionElement.getBridgeFromAddressPolicies().size());
      assertEquals(1, bridgeConnectionElement.getBridgeToAddressPolicies().size());
      assertTrue(bridgeConnectionElement.getProperties().containsKey("amqpCredits"));
      assertEquals("7", bridgeConnectionElement.getProperties().get("amqpCredits"));
      assertTrue(bridgeConnectionElement.getProperties().containsKey("amqpLowCredits"));
      assertEquals("1", bridgeConnectionElement.getProperties().get("amqpLowCredits"));

      bridgeConnectionElement.getBridgeFromQueuePolicies().forEach(p -> {
         assertEquals("bridge-from-queue", p.getName());
         assertEquals(1, p.getIncludes().size());
         p.getIncludes().forEach(match -> {
            assertEquals("#", match.getAddressMatch());
            assertEquals("tracking", match.getQueueMatch());
         });
         assertEquals(0, p.getExcludes().size());
         p.getExcludes().forEach(match -> assertEquals("all.#", match.getAddressMatch()));
         assertNotNull(p.getTransformerConfiguration());
         assertEquals("something", p.getTransformerConfiguration().getClassName());
      });
      bridgeConnectionElement.getBridgeToQueuePolicies().forEach(p -> {
         assertEquals("bridge-to-queue", p.getName());
         assertEquals(1, p.getIncludes().size());
         p.getIncludes().forEach(match -> {
            assertEquals("test-queue", match.getAddressMatch());
            assertEquals("tracking", match.getQueueMatch());
         });
         assertEquals(0, p.getExcludes().size());
         p.getExcludes().forEach(match -> assertEquals("all.#", match.getAddressMatch()));
         assertNull(p.getTransformerConfiguration());
      });
      bridgeConnectionElement.getBridgeFromAddressPolicies().forEach(p -> {
         assertEquals("bridge-from-address", p.getName());
         assertEquals(1, p.getIncludes().size());
         p.getIncludes().forEach(match -> assertEquals("test-address", match.getAddressMatch()));
         assertEquals(1, p.getExcludes().size());
         p.getExcludes().forEach(match -> assertEquals("all.#", match.getAddressMatch()));
         assertNotNull(p.getTransformerConfiguration());
         assertEquals("class-name", p.getTransformerConfiguration().getClassName());
      });
      bridgeConnectionElement.getBridgeToAddressPolicies().forEach(p -> {
         assertEquals("bridge-to-address", p.getName());
         assertEquals(1, p.getIncludes().size());
         p.getIncludes().forEach(match -> assertEquals("test-address", match.getAddressMatch()));
         assertEquals(1, p.getExcludes().size());
         p.getExcludes().forEach(match -> assertEquals("all.#", match.getAddressMatch()));
         assertNull(p.getTransformerConfiguration());
         assertTrue(p.isUseDurableSubscriptions());
      });
   }

   @Test
   public void testChangeConfiguration() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("ConfigurationTest-full-config.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();
      deploymentManager = new FileDeploymentManager("ConfigurationTest-full-config-wrong-address.xml");
      deploymentManager.addDeployable(fc);

      try {
         deploymentManager.readConfiguration();
         fail("Exception expected");
      } catch (Exception ignored) {
      }
   }
}
