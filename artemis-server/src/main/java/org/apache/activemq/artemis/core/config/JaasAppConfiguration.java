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

import javax.security.auth.login.AppConfigurationEntry;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JaasAppConfiguration implements Serializable {

   private static final long serialVersionUID = -651209063030767325L;

   private String name;

   private List<JaasAppConfigurationEntry> modules = new ArrayList<>();

   public JaasAppConfiguration() {
   }

   public String getName() {
      return name;
   }

   public JaasAppConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   public List<JaasAppConfigurationEntry> getModules() {
      return modules;
   }

   // help the properties setter
   public JaasAppConfiguration addModule(JaasAppConfigurationEntry entry) {
      modules.add(entry);
      return this;
   }

   public static AppConfigurationEntry[] asAppConfigurationEntry(JaasAppConfiguration jaasAppConfiguration) {
      if (jaasAppConfiguration == null) {
         return null;
      }
      AppConfigurationEntry[] entries = new AppConfigurationEntry[jaasAppConfiguration.getModules().size()];
      for (int i = 0; i < jaasAppConfiguration.getModules().size(); i++) {
         JaasAppConfigurationEntry jaasAppConfigurationEntry = jaasAppConfiguration.getModules().get(i);
         entries[i] = new AppConfigurationEntry(jaasAppConfigurationEntry.getLoginModuleClass(), jaasAppConfigurationEntry.getLoginModuleControlFlag(), jaasAppConfigurationEntry.getParams());
      }
      return entries;
   }

   @Override
   public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      JaasAppConfiguration that = (JaasAppConfiguration) o;
      return Objects.equals(name, that.name) && Objects.equals(modules, that.modules);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, modules);
   }
}
