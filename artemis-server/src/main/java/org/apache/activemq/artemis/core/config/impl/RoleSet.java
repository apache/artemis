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

import org.apache.activemq.artemis.core.security.Role;

import java.util.HashSet;
import java.util.Set;

public class RoleSet extends HashSet<Role> {

   private String name;

   public RoleSet() {
      super();
   }

   public RoleSet(String key, Set<Role> value) {
      setName(key);
      if (value != null) {
         addAll(value);
      }
   }

   // provide a helper add method with the type
   public void add(String name, Role value) {
      super.add(value);
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }
}
