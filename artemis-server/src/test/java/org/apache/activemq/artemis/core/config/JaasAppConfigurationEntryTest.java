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

import org.junit.jupiter.api.Test;

import javax.security.auth.login.AppConfigurationEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JaasAppConfigurationEntryTest {

   @Test
   void testEquals() {
      JaasAppConfigurationEntry a = new JaasAppConfigurationEntry();
      JaasAppConfigurationEntry b = new JaasAppConfigurationEntry();

      assertTrue(a.equals(b));

      a.setName("test");
      assertFalse(a.equals(b));

      b.setName("test");
      assertTrue(a.equals(b));

      a.setControlFlag("optional");
      assertFalse(a.equals(b));

      b.setControlFlag("optional");
      assertTrue(a.equals(b));

      a.setLoginModuleClass("module");
      assertFalse(a.equals(b));

      b.setLoginModuleClass("module");
      assertTrue(a.equals(b));

      a.getParams().put("key1", "value1");
      assertFalse(a.equals(b));

      b.getParams().put("key1", "value1");
      assertTrue(a.equals(b));

      a.getParams().put("key2", "value1");
      assertFalse(a.equals(b));
      b.getParams().put("key2", "value2");
      assertFalse(a.equals(b));
   }

   @Test
   void testHashCode() {

      JaasAppConfigurationEntry a = new JaasAppConfigurationEntry();
      JaasAppConfigurationEntry b = new JaasAppConfigurationEntry();
      assertEquals(a.hashCode(), b.hashCode());

      a.setName("test");
      assertNotEquals(a.hashCode(), b.hashCode());

      b.setName("test");
      assertEquals(a.hashCode(), b.hashCode());

      a.setControlFlag("optional");
      assertNotEquals(a.hashCode(), b.hashCode());

      b.setControlFlag("optional");
      assertEquals(a.hashCode(), b.hashCode());

      a.setLoginModuleClass("module");
      assertNotEquals(a.hashCode(), b.hashCode());

      b.setLoginModuleClass("module");
      assertEquals(a.hashCode(), b.hashCode());

      a.getParams().put("key1", "value1");
      assertNotEquals(a.hashCode(), b.hashCode());

      b.getParams().put("key1", "value1");
      assertEquals(a.hashCode(), b.hashCode());

   }

   @Test
   void setControlFlag() {
      assertThrowsExactly(IllegalArgumentException.class, () -> new JaasAppConfigurationEntry().setControlFlag("null"));

      JaasAppConfigurationEntry a = new JaasAppConfigurationEntry();
      a.setControlFlag(null);
      assertEquals(AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, a.getLoginModuleControlFlag());

      a.setControlFlag("required");
      assertEquals(AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, a.getLoginModuleControlFlag());

      a.setControlFlag("optional");
      assertEquals(AppConfigurationEntry.LoginModuleControlFlag.OPTIONAL, a.getLoginModuleControlFlag());

      a.setControlFlag("requisite");
      assertEquals(AppConfigurationEntry.LoginModuleControlFlag.REQUISITE, a.getLoginModuleControlFlag());

      a.setControlFlag("sufficient");
      assertEquals(AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT, a.getLoginModuleControlFlag());
   }
}