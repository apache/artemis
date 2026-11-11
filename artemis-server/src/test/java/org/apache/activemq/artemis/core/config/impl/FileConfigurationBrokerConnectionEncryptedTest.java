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

import java.util.List;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.junit.jupiter.api.Test;

public class FileConfigurationBrokerConnectionEncryptedTest extends AbstractConfigurationTestBase {

   protected String getConfigurationName() {
      return "ConfigurationTest-broker-connection-encrypted-config.xml";
   }

   @Override
   protected Configuration createConfiguration() throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(getConfigurationName());
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();
      return fc;
   }

   @Test
   public void testAMQPBrokerConfigEncryptedUserAndPassword() {

      List<AMQPBrokerConnectConfiguration> brokerConnections = conf.getAMQPConnection();
      assertNotNull(brokerConnections, "brokerConnections is null");
      assertFalse(brokerConnections.isEmpty(), "brokerConnections is empty");

      boolean encTest = false;
      boolean plainTest = false;
      boolean emptyTest = false;

      for (AMQPBrokerConnectConfiguration brokerConnection : brokerConnections) {
         // Check each expected configuration is present
         encTest = encTest || "enc-test".equals(brokerConnection.getName());
         plainTest = plainTest || "plain-test".equals(brokerConnection.getName());
         emptyTest = emptyTest || "empty-test".equals(brokerConnection.getName());

         if ("empty-test".equals(brokerConnection.getName())) {

            // Empty configuration should have null user and password
            assertNull(brokerConnection.getUser());
            assertNull(brokerConnection.getPassword());

         } else {

            // Both the encrypted and plain user and password use the same expected value
            assertEquals("testuser", brokerConnection.getUser());
            assertEquals("testpassword", brokerConnection.getPassword());

         }
      }

      assertTrue(encTest, "enc-test configuration is not present");
      assertTrue(plainTest, "plain-test configuration is not present");
      assertTrue(emptyTest, "empty-test configuration is not present");
   }

   @Test
   public void testSetGetAttributes() throws Exception {
      doSetGetAttributesTestImpl(conf);
   }

   @Test
   public void testGetSetInterceptors() {
      doGetSetInterceptorsTestImpl(conf);
   }

   @Test
   public void testSerialize() throws Exception {
      doSerializeTestImpl(conf);
   }

   @Test
   public void testSetConnectionRoutersPolicyConfiguration() throws Throwable {
      doSetConnectionRoutersPolicyConfigurationTestImpl((ConfigurationImpl) conf);
   }
}
