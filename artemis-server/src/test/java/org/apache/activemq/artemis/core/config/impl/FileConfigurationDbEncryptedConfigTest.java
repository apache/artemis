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

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.SensitiveDataCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileConfigurationDbEncryptedConfigTest extends AbstractConfigurationTestBase {

   private static final String ENCODED_PASSWORD = RandomUtil.randomUUIDString();
   private static final String PASSWORD = RandomUtil.randomUUIDString();
   private static final String ENCODED_USER = RandomUtil.randomUUIDString();
   private static final String USER = RandomUtil.randomUUIDString();
   private static final String ENCODED_URL = RandomUtil.randomUUIDString();
   private static final String URL = RandomUtil.randomUUIDString();

   private static final Map<String, String> SENSITIVE_PROPERTIES = new HashMap<>();

   static {
      SENSITIVE_PROPERTIES.put(ENCODED_PASSWORD, PASSWORD);
      SENSITIVE_PROPERTIES.put(ENCODED_USER, USER);
      SENSITIVE_PROPERTIES.put(ENCODED_URL, URL);
   }

   protected String getConfigurationName() {
      return "ConfigurationTest-db-encrypted-config.xml";
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      System.setProperty("dbPass", ENCODED_PASSWORD);
      System.setProperty("dbUser", ENCODED_USER);
      System.setProperty("dbUrl", ENCODED_URL);
      runAfter(() -> {
         System.clearProperty("dbPass");
         System.clearProperty("dbUser");
         System.clearProperty("dbUrl");
      });
      super.setUp();
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
   public void testJdbcPasswordWithCustomCodec() {
      assertEquals(PASSWORD, ((DatabaseStorageConfiguration)conf.getStoreConfiguration()).getJdbcPassword());
   }

   @Test
   public void testJdbcUserWithCustomCodec() {
      assertEquals(USER, ((DatabaseStorageConfiguration)conf.getStoreConfiguration()).getJdbcUser());
   }

   @Test
   public void testJdbcUrlWithCustomCodec() {
      assertEquals(URL, ((DatabaseStorageConfiguration)conf.getStoreConfiguration()).getJdbcConnectionUrl());
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

   public static class MySensitiveStringCodec implements SensitiveDataCodec<String> {
      @Override
      public String decode(Object mask) throws Exception {
         return SENSITIVE_PROPERTIES.get(mask);
      }

      @Override
      public String encode(Object secret) throws Exception {
         return null;
      }
   }
}
