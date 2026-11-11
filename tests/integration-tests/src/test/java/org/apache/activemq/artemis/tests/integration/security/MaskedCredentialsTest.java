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
package org.apache.activemq.artemis.tests.integration.security;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.management.ManagementFactory;
import java.net.URL;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.SensitiveDataCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MaskedCredentialsTest extends ActiveMQTestBase {

   static {
      String path = System.getProperty("java.security.auth.login.config");
      if (path == null) {
         URL resource = MaskedCredentialsTest.class.getClassLoader().getResource("login.config");
         if (resource != null) {
            path = resource.getFile();
            System.setProperty("java.security.auth.login.config", path);
         }
      }
   }

   private ServerLocator locator;

   ClientSessionFactory cf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager("PropertiesLogin");
      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setSecurityEnabled(true), ManagementFactory.getPlatformMBeanServer(), securityManager, false));
      server.start();

      locator = createInVMNonHALocator();
      cf = createSessionFactory(locator);
   }

   @Test
   public void testMaskedCredentials() throws Exception {
      addClientSession(cf.createSession(getMaskedCredential("first"), getMaskedCredential("secret"), false, true, true, false, 0));
   }

   @Test
   public void testMaskedCredentialsWithCustomCodec() throws Exception {
      testMaskedCredentialsWithCustomCodec("secret");
   }

   @Test
   public void testMaskedCredentialsWithCustomCodecNegative() throws Exception {
      try {
         testMaskedCredentialsWithCustomCodec("xxx");
         fail();
      } catch (Exception e) {
         // expected
      }
   }

   private void testMaskedCredentialsWithCustomCodec(String password) throws Exception {
      ServerLocator locator = createInVMNonHALocator();
      locator.setPasswordCodec(DummyCodec.class.getName());
      testMaskedCredentialsWithCustomCodec(locator, password);
   }

   @Test
   public void testMaskedCredentialsWithCustomCodecURL() throws Exception {
      testMaskedCredentialsWithCustomCodecURL("secret");
   }

   @Test
   public void testMaskedCredentialsWithCustomCodecNegativeURL() throws Exception {
      try {
         testMaskedCredentialsWithCustomCodecURL("xxx");
         fail();
      } catch (Exception e) {
         // expected
      }
   }

   private void testMaskedCredentialsWithCustomCodecURL(String password) throws Exception {
      ServerLocator locator = ActiveMQClient.createServerLocator("vm://0?passwordCodec=org.apache.activemq.artemis.tests.integration.security.MaskedCredentialsTest.DummyCodec");
      locator.setPasswordCodec(DummyCodec.class.getName());
      testMaskedCredentialsWithCustomCodec(locator, password);
   }

   private void testMaskedCredentialsWithCustomCodec(ServerLocator locator, String password) throws Exception {
      cf = createSessionFactory(locator);
      String maskedPassword = PasswordMaskingUtil.wrap(PasswordMaskingUtil.resolveMask(password, DummyCodec.class.getName()));
      addClientSession(cf.createSession("first", maskedPassword, false, true, true, false, 0));
   }

   private String getMaskedCredential(String credential) throws Exception {
      return PasswordMaskingUtil.wrap(PasswordMaskingUtil.getDefaultCodec().encode(credential));
   }

   public static class DummyCodec implements SensitiveDataCodec<String> {

      private static final String MASK = "===";
      private static final String CLEARTEXT = "secret";

      @Override
      public String decode(Object mask) throws Exception {
         if (!MASK.equals(mask)) {
            return mask.toString();
         }
         return CLEARTEXT;
      }

      @Override
      public String encode(Object secret) throws Exception {
         if (!CLEARTEXT.equals(secret)) {
            return secret.toString();
         }
         return MASK;
      }
   }
}
