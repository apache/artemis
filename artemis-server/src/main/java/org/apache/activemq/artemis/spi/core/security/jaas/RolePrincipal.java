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
package org.apache.activemq.artemis.spi.core.security.jaas;

import java.security.Principal;
import java.util.Objects;

public class RolePrincipal implements Principal {

   private final String name;
   private transient int hash;

   public RolePrincipal(String name) {
      if (name == null) {
         throw new IllegalArgumentException("name cannot be null");
      }
      this.name = name;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof RolePrincipal other)) {
         return false;
      }

      return Objects.equals(name, other.name);
   }

   @Override
   public int hashCode() {
      if (hash == 0) {
         hash = name.hashCode();
      }
      return hash;
   }

   @Override
   public String toString() {
      return name;
   }
}
