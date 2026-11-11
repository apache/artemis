/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.cli.test;

import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;

/**
 * This is a simple *test* security manager used to verify configuration via bootstrap.xml.
 */
public class TestSecurityManager implements ActiveMQSecurityManager {
   public Map<String, String> properties;
   public boolean validateUser = false;
   public String validateUserName;
   public String validateUserPass;
   public boolean validateUserAndRole = false;
   public String validateUserAndRoleName;
   public String validateUserAndRolePass;
   public CheckType checkType;

   @Override
   public boolean validateUser(String user, String password) {
      this.validateUser = true;
      this.validateUserName = user;
      this.validateUserPass = password;
      return true;
   }

   @Override
   public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
      this.validateUserAndRole = true;
      this.validateUserAndRoleName = user;
      this.validateUserAndRolePass = password;
      this.checkType = checkType;
      return true;
   }

   @Override
   public ActiveMQSecurityManager init(Map<String, String> properties) {
      this.properties = properties;
      return this;
   }
}
