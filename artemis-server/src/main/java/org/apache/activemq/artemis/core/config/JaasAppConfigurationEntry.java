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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class JaasAppConfigurationEntry implements Serializable {

   private static final long serialVersionUID = -651209063030767725L;

   private String name;

   private String loginModuleClass;

   private String controlFlag;

   private Map<String, String> params = new HashMap<>();

   public JaasAppConfigurationEntry() {
   }

   public String getName() {
      return name;
   }

   public String getLoginModuleClass() {
      return loginModuleClass;
   }

   public Map<String, String> getParams() {
      return params;
   }

   public String getControlFlag() {
      return controlFlag;
   }

   public JaasAppConfigurationEntry setName(String name) {
      this.name = name;
      return this;
   }

   public JaasAppConfigurationEntry setLoginModuleClass(String loginModuleClass) {
      this.loginModuleClass = loginModuleClass;
      return this;
   }

   public JaasAppConfigurationEntry setParams(Map<String, String> params) {
      this.params = params;
      return this;
   }

   public void setControlFlag(String controlFlag) {
      this.controlFlag = controlFlag;
      getLoginModuleControlFlag();
   }

   AppConfigurationEntry.LoginModuleControlFlag getLoginModuleControlFlag() {
      if (this.controlFlag == null || this.controlFlag.isEmpty() || this.controlFlag.equals("required")) {
         return AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;
      } else if (this.controlFlag.equals("requisite")) {
         return AppConfigurationEntry.LoginModuleControlFlag.REQUISITE;
      } else if (this.controlFlag.equals("optional")) {
         return AppConfigurationEntry.LoginModuleControlFlag.OPTIONAL;
      } else if (this.controlFlag.equals("sufficient")) {
         return AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT;
      }
      throw new IllegalArgumentException("Unknown control flag: " + this.controlFlag);
   }

   @Override
   public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      JaasAppConfigurationEntry that = (JaasAppConfigurationEntry) o;
      return Objects.equals(name, that.name) && Objects.equals(loginModuleClass, that.loginModuleClass) && Objects.equals(controlFlag, that.controlFlag) && Objects.equals(params, that.params);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, loginModuleClass, controlFlag, params);
   }
}
