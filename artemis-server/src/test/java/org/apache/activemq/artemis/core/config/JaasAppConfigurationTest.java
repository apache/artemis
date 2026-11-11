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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JaasAppConfigurationTest {

   @Test
   void asAppConfigurationEntry() {
      JaasAppConfiguration underTest = new JaasAppConfiguration();
      underTest.setName("test");
      JaasAppConfigurationEntry entry = new JaasAppConfigurationEntry();
      entry.setName("test");
      entry.setLoginModuleClass("a");

      underTest.addModule(entry);
      underTest.addModule(entry);

      assertEquals(2, JaasAppConfiguration.asAppConfigurationEntry(underTest).length);
      assertEquals(AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, JaasAppConfiguration.asAppConfigurationEntry(underTest)[0].getControlFlag());
   }

   @Test
   void testEquals() {
      JaasAppConfiguration a = new JaasAppConfiguration();
      JaasAppConfiguration b = new JaasAppConfiguration();
      assertEquals(a, b);

      a.setName(this.getClass().getName());
      assertNotEquals(a, b);
      b.setName(this.getClass().getName());
      assertEquals(a, b);
      JaasAppConfigurationEntry entry = new JaasAppConfigurationEntry();
      entry.setName(this.getClass().getName());
      a.getModules().add(entry);
      assertNotEquals(a, b);
      b.getModules().add(entry);
      assertEquals(a, b);
   }
}