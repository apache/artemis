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

import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.XMLUtil;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Element;

public class ConfigurationValidationTest extends ActiveMQTestBase {

   static {
      System.setProperty("a2Prop", "a2");
      System.setProperty("falseProp", "false");
      System.setProperty("trueProp", "true");
      System.setProperty("ninetyTwoProp", "92");
   }



   /**
    * test does not pass in eclipse (because it can not find artemis-configuration.xsd).
    * It runs fine on the CLI with the proper env setting.
    */
   @Test
   public void testMinimalConfiguration() throws Exception {
      String xml = "<core xmlns='urn:activemq:core'>" + "</core>";
      Element element = XMLUtil.stringToElement(xml);
      Assert.assertNotNull(element);
      XMLUtil.validate(element, "schema/artemis-configuration.xsd");
   }

   @Test
   public void testFullConfiguration() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("ConfigurationTest-full-config.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      Assert.assertEquals(true, fc.isPersistDeliveryCountBeforeDelivery());
   }

   @Test
   public void testAMQPConnectParsing() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager("ConfigurationTest-full-config.xml");
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();

      Assert.assertEquals(3, fc.getAMQPConnection().size());

      AMQPBrokerConnectConfiguration amqpBrokerConnectConfiguration = fc.getAMQPConnection().get(0);
      Assert.assertEquals("testuser", amqpBrokerConnectConfiguration.getUser());
      Assert.assertEquals("testpassword", amqpBrokerConnectConfiguration.getPassword());
      Assert.assertEquals(33, amqpBrokerConnectConfiguration.getReconnectAttempts());
      Assert.assertEquals(333, amqpBrokerConnectConfiguration.getRetryInterval());
      Assert.assertEquals("test1", amqpBrokerConnectConfiguration.getName());
      Assert.assertEquals("tcp://test1:111", amqpBrokerConnectConfiguration.getUri());

      Assert.assertEquals("TEST-SENDER", amqpBrokerConnectConfiguration.getConnectionElements().get(0).getMatchAddress().toString());
      Assert.assertEquals(AMQPBrokerConnectionAddressType.SENDER, amqpBrokerConnectConfiguration.getConnectionElements().get(0).getType());
      Assert.assertEquals("TEST-RECEIVER", amqpBrokerConnectConfiguration.getConnectionElements().get(1).getMatchAddress().toString());
      Assert.assertEquals(AMQPBrokerConnectionAddressType.RECEIVER, amqpBrokerConnectConfiguration.getConnectionElements().get(1).getType());
      Assert.assertEquals("TEST-PEER", amqpBrokerConnectConfiguration.getConnectionElements().get(2).getMatchAddress().toString());
      Assert.assertEquals(AMQPBrokerConnectionAddressType.PEER, amqpBrokerConnectConfiguration.getConnectionElements().get(2).getType());

      Assert.assertEquals("TEST-WITH-QUEUE-NAME", amqpBrokerConnectConfiguration.getConnectionElements().get(3).getQueueName().toString());
      Assert.assertEquals(null, amqpBrokerConnectConfiguration.getConnectionElements().get(3).getMatchAddress());
      Assert.assertEquals(AMQPBrokerConnectionAddressType.RECEIVER, amqpBrokerConnectConfiguration.getConnectionElements().get(3).getType());

      Assert.assertEquals(AMQPBrokerConnectionAddressType.MIRROR, amqpBrokerConnectConfiguration.getConnectionElements().get(4).getType());
      AMQPMirrorBrokerConnectionElement mirrorConnectionElement = (AMQPMirrorBrokerConnectionElement) amqpBrokerConnectConfiguration.getConnectionElements().get(4);
      Assert.assertFalse(mirrorConnectionElement.isMessageAcknowledgements());
      Assert.assertFalse(mirrorConnectionElement.isQueueCreation());
      Assert.assertFalse(mirrorConnectionElement.isQueueRemoval());
      Assert.assertFalse(mirrorConnectionElement.isDurable());


      amqpBrokerConnectConfiguration = fc.getAMQPConnection().get(1);
      Assert.assertEquals(null, amqpBrokerConnectConfiguration.getUser());      mirrorConnectionElement = (AMQPMirrorBrokerConnectionElement) amqpBrokerConnectConfiguration.getConnectionElements().get(0);
      Assert.assertEquals(null, amqpBrokerConnectConfiguration.getPassword());      Assert.assertEquals("test2", amqpBrokerConnectConfiguration.getName());
      Assert.assertEquals("tcp://test2:222", amqpBrokerConnectConfiguration.getUri());
      Assert.assertTrue(mirrorConnectionElement.isMessageAcknowledgements());
      Assert.assertFalse(mirrorConnectionElement.isDurable());
      Assert.assertTrue(mirrorConnectionElement.isQueueCreation());
      Assert.assertTrue(mirrorConnectionElement.isQueueRemoval());

      amqpBrokerConnectConfiguration = fc.getAMQPConnection().get(2);
      Assert.assertFalse(amqpBrokerConnectConfiguration.isAutostart());
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
