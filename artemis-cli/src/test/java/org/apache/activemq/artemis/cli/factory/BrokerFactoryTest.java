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
package org.apache.activemq.artemis.cli.factory;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.dto.BrokerDTO;
import org.apache.activemq.artemis.dto.JaasSecurityDTO;
import org.apache.activemq.artemis.dto.PropertyDTO;
import org.apache.activemq.artemis.dto.SecurityDTO;
import org.apache.activemq.artemis.dto.SecurityManagerDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BrokerFactoryTest {

   private static final String testBrokerConfiguration = "test://config";

   private final String securityJaasPropertyPrefix = ActiveMQDefaultConfiguration.getDefaultSystemSecurityJaasPropertyPrefix();
   private final String securityManagerPropertyPrefix = ActiveMQDefaultConfiguration.getDefaultSystemSecurityManagerPropertyPrefix();
   private final List<String> systemPropertiesToClear = new ArrayList<>();

   @BeforeEach
   public void setUp() {
      TestBrokerFactoryHandler.clear();
   }

   @AfterEach
   public void tearDown() {
      TestBrokerFactoryHandler.clear();
      for (String property : systemPropertiesToClear) {
         System.clearProperty(property);
      }
      systemPropertiesToClear.clear();
   }

   private void setSystemProperty(String key, String value) {
      System.setProperty(key, value);
      systemPropertiesToClear.add(key);
   }

   @Test
   public void testCreateBrokerConfiguration() throws Exception {
      final String testArtemisHome = "test-home";
      final String testArtemisInstance = "test-instance";
      final URI testArtemisURIInstance = URI.create(testArtemisInstance);

      TestBrokerFactoryHandler.setBroker(new BrokerDTO());

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration(
         testBrokerConfiguration, testArtemisHome, testArtemisInstance, testArtemisURIInstance);

      assertNotNull(createdBroker);
      assertEquals(testBrokerConfiguration, TestBrokerFactoryHandler.getBrokerURI().toString());
      assertEquals(testArtemisHome, TestBrokerFactoryHandler.getArtemisHome());
      assertEquals(testArtemisInstance, TestBrokerFactoryHandler.getArtemisInstance());
      assertEquals(testArtemisURIInstance, TestBrokerFactoryHandler.getArtemisURIInstance());
   }

   @Test
   public void testCreateBrokerConfigurationWithJaasDomainFromSystemProperties() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "domain", "testDomain");

      TestBrokerFactoryHandler.setBroker(new BrokerDTO());

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testJaasSecurity(createdBroker.security, "testDomain", null);
   }

   @Test
   public void testCreateBrokerConfigurationWithJaasCertificateDomainFromSystemProperties() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "certificateDomain", "testCertificateDomain");

      TestBrokerFactoryHandler.setBroker(new BrokerDTO());

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      assertNull(createdBroker.security);
   }

   @Test
   public void testCreateBrokerConfigurationWithJaasDomainAndCertificateDomainFromSystemProperties() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "domain", "testDomain");
      setSystemProperty(securityJaasPropertyPrefix + "certificateDomain", "testCertificateDomain");

      TestBrokerFactoryHandler.setBroker(new BrokerDTO());

      BrokerDTO broker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(broker);
      testJaasSecurity(broker.security, "testDomain", "testCertificateDomain");
   }

   @Test
   public void testCreateBrokerConfigurationWithNewJaasDomainFromExistingJaasSecurityAndSystemProperties() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "domain", "newTestDomain");

      JaasSecurityDTO  security = new JaasSecurityDTO();
      security.domain = "testDomain";
      security.certificateDomain = "testCertificateDomain";
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testJaasSecurity(createdBroker.security, "newTestDomain", "testCertificateDomain");
   }

   @Test
   public void testCreateBrokerConfigurationWithNewJaasCertificateDomainFromExistingJaasSecurityAndSystemProperties() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "certificateDomain", "newTestCertificateDomain");

      JaasSecurityDTO  security = new JaasSecurityDTO();
      security.domain = "testDomain";
      security.certificateDomain = "testCertificateDomain";
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testJaasSecurity(createdBroker.security, "testDomain", "newTestCertificateDomain");
   }

   @Test
   public void testCreateBrokerConfigurationWithNewJaasDomainFromExistingSecurityManagerAndSystemProperties() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "domain", "newTestDomain");

      SecurityManagerDTO  security = new SecurityManagerDTO();
      security.className = "testClass";
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testJaasSecurity(createdBroker.security, "newTestDomain", null);
   }

   @Test
   public void testCreateBrokerConfigurationWithNewJaasCertificateDomainFromExistingSecurityManagerAndSystemProperties() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "certificateDomain", "newTestCertificateDomain");

      SecurityManagerDTO  security = new SecurityManagerDTO();
      security.className = "testClass";
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testSecurityManager(createdBroker.security, "testClass", Collections.emptyList());
   }

   @Test
   public void testCreateBrokerConfigurationWithNewJaasDomainAndCertificateDomainFromExistingJaasSecurityAndSystemProperties() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "domain", "newTestDomain");
      setSystemProperty(securityJaasPropertyPrefix + "certificateDomain", "newTestCertificateDomain");

      JaasSecurityDTO  security = new JaasSecurityDTO();
      security.domain = "testDomain";
      security.certificateDomain = "testCertificateDomain";
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testJaasSecurity(createdBroker.security, "newTestDomain", "newTestCertificateDomain");
   }

   @Test
   public void testCreateBrokerConfigurationWithNewJaasDomainAndCertificateDomainFromExistingSecurityManagerAndSystemProperties() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "domain", "newTestDomain");
      setSystemProperty(securityJaasPropertyPrefix + "certificateDomain", "newTestCertificateDomain");

      SecurityManagerDTO  security = new SecurityManagerDTO();
      security.className = "testClassName";
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testJaasSecurity(createdBroker.security, "newTestDomain", "newTestCertificateDomain");
   }

   private void testJaasSecurity(SecurityDTO security, String expectedDomain, String expectedCertificateDomain) throws Exception {
      assertNotNull(security);
      assertInstanceOf(JaasSecurityDTO.class, security);
      JaasSecurityDTO jaasSecurity = (JaasSecurityDTO) security;
      assertEquals(expectedDomain, jaasSecurity.domain);
      assertEquals(expectedCertificateDomain, jaasSecurity.certificateDomain);
   }

   @Test
   public void testCreateBrokerConfigurationWithSecurityManagerClassNameFromSystemProperties() throws Exception {
      setSystemProperty(securityManagerPropertyPrefix + "className", "testClassName");

      TestBrokerFactoryHandler.setBroker(new BrokerDTO());

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testSecurityManager(createdBroker.security, "testClassName", Collections.emptyList());
   }

   @Test
   public void testCreateBrokerConfigurationWithSecurityManagerPropertiesFromSystemProperties() throws Exception {
      setSystemProperty(securityManagerPropertyPrefix + "properties.testKey1", "testValue1");
      setSystemProperty(securityManagerPropertyPrefix + "properties.testKey2", "testValue2");

      TestBrokerFactoryHandler.setBroker(new BrokerDTO());

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      assertNull(createdBroker.security);
   }

   @Test
   public void testCreateBrokerConfigurationWithSecurityManagerClassNameAndPropertiesFromSystemProperties() throws Exception {
      setSystemProperty(securityManagerPropertyPrefix + "className", "testClassName");
      setSystemProperty(securityManagerPropertyPrefix + "properties.testKey1", "testValue1");
      setSystemProperty(securityManagerPropertyPrefix + "properties.testKey2", "testValue2");

      TestBrokerFactoryHandler.setBroker(new BrokerDTO());

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testSecurityManager(createdBroker.security, "testClassName", List.of(
         new PropertyDTO("testKey1", "testValue1"), new PropertyDTO("testKey2", "testValue2")));
   }

   @Test
   public void testCreateBrokerConfigurationWithNewSecurityManagerClassNameFromExistingSecurityManagerAndSystemProperties() throws Exception {
      setSystemProperty(securityManagerPropertyPrefix + "className", "newTestClassName");

      SecurityManagerDTO  security = new SecurityManagerDTO();
      security.className = "testClassName";
      security.properties = new ArrayList<>(List.of(
         new PropertyDTO("testKey1", "testValue1"),
         new PropertyDTO("testKey2", "testValue2")));
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testSecurityManager(createdBroker.security, "newTestClassName", List.of(
         new PropertyDTO("testKey1", "testValue1"), new PropertyDTO("testKey2", "testValue2")));
   }

   @Test
   public void testCreateBrokerConfigurationWithNewSecurityManagerPropertiesFromExistingSecurityManagerAndSystemProperties() throws Exception {
      setSystemProperty(securityManagerPropertyPrefix + "properties.testKey1", "newTestValue1");
      setSystemProperty(securityManagerPropertyPrefix + "properties.newTestKey2", "newTestValue2");

      SecurityManagerDTO  security = new SecurityManagerDTO();
      security.className = "testClassName";
      security.properties = new ArrayList<>(List.of(
         new PropertyDTO("testKey1", "testValue1"),
         new PropertyDTO("testKey2", "testValue2")));
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testSecurityManager(createdBroker.security, "testClassName", List.of(
         new PropertyDTO("testKey1", "testValue1"), new PropertyDTO("testKey2", "testValue2"),
         new PropertyDTO("testKey1", "newTestValue1"), new PropertyDTO("newTestKey2", "newTestValue2")));
   }

   @Test
   public void testCreateBrokerConfigurationWithNewSecurityManagerClassNameAndPropertiesFromExistingSecurityManagerAndSystemProperties() throws Exception {
      setSystemProperty(securityManagerPropertyPrefix + "className", "newTestClassName");
      setSystemProperty(securityManagerPropertyPrefix + "properties.testKey1", "newTestValue1");
      setSystemProperty(securityManagerPropertyPrefix + "properties.newTestKey2", "newTestValue2");

      SecurityManagerDTO  security = new SecurityManagerDTO();
      security.className = "testClassName";
      security.properties = new ArrayList<>(List.of(
         new PropertyDTO("testKey1", "testValue1"),
         new PropertyDTO("testKey2", "testValue2")));
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testSecurityManager(createdBroker.security, "newTestClassName", List.of(
         new PropertyDTO("testKey1", "testValue1"), new PropertyDTO("testKey2", "testValue2"),
         new PropertyDTO("testKey1", "newTestValue1"), new PropertyDTO("newTestKey2", "newTestValue2")));
   }

   @Test
   public void testCreateBrokerConfigurationWithNewSecurityManagerClassNameFromExistingJaasSecurityAndSystemProperties() throws Exception {
      setSystemProperty(securityManagerPropertyPrefix + "className", "newTestClassName");

      JaasSecurityDTO  security = new JaasSecurityDTO();
      security.domain = "testDomain";
      security.certificateDomain = "testCertificateDomain";
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testSecurityManager(createdBroker.security, "newTestClassName", Collections.emptyList());
   }

   @Test
   public void testCreateBrokerConfigurationWithNewSecurityManagerPropertiesFromExistingJaasSecurityAndSystemProperties() throws Exception {
      setSystemProperty(securityManagerPropertyPrefix + "properties.testKey1", "newTestValue1");
      setSystemProperty(securityManagerPropertyPrefix + "properties.newTestKey2", "newTestValue2");

      JaasSecurityDTO  security = new JaasSecurityDTO();
      security.domain = "testDomain";
      security.certificateDomain = "testCertificateDomain";
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testJaasSecurity(createdBroker.security, "testDomain", "testCertificateDomain");
   }

   @Test
   public void testCreateBrokerConfigurationWithNewSecurityManagerClassNameAndPropertiesFromExistingJaasSecurityAndSystemProperties() throws Exception {
      setSystemProperty(securityManagerPropertyPrefix + "className", "newTestClassName");
      setSystemProperty(securityManagerPropertyPrefix + "properties.testKey1", "newTestValue1");
      setSystemProperty(securityManagerPropertyPrefix + "properties.newTestKey2", "newTestValue2");

      JaasSecurityDTO  security = new JaasSecurityDTO();
      security.domain = "testDomain";
      security.certificateDomain = "testCertificateDomain";
      BrokerDTO broker = new BrokerDTO();
      broker.security = security;
      TestBrokerFactoryHandler.setBroker(broker);

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testSecurityManager(createdBroker.security, "newTestClassName", List.of(
         new PropertyDTO("testKey1", "newTestValue1"), new PropertyDTO("newTestKey2", "newTestValue2")));
   }

   private void testSecurityManager(SecurityDTO security, String expectedClassName, List<PropertyDTO> expectedProperties) throws Exception {
      assertNotNull(security);
      assertInstanceOf(SecurityManagerDTO.class, security);
      SecurityManagerDTO securityManager = (SecurityManagerDTO)security;
      assertEquals(expectedClassName, securityManager.className);

      if (expectedProperties != null) {
         assertEquals(expectedProperties.size(), securityManager.properties.size());
         assertTrue(expectedProperties.stream().allMatch(expectedProperty ->
            securityManager.properties.stream().anyMatch(property ->
               Objects.equals(expectedProperty.key, property.key) &&
                  Objects.equals(expectedProperty.value, property.value))));
      } else {
         assertNull(securityManager.properties);
      }
   }

   @Test
   public void testJaasSecurityTakesPrecedenceOverSecurityManager() throws Exception {
      setSystemProperty(securityJaasPropertyPrefix + "domain", "testDomain");
      setSystemProperty(securityManagerPropertyPrefix + "className", "testClassName");

      TestBrokerFactoryHandler.setBroker(new BrokerDTO());

      BrokerDTO createdBroker = BrokerFactory.createBrokerConfiguration("test://config", null, null, null);

      assertNotNull(createdBroker);
      testJaasSecurity(createdBroker.security, "testDomain", null);
   }
}
