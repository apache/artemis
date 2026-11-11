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
package org.apache.activemq.artemis.core.config.routing;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class NamedPropertyConfiguration implements Serializable {

   private String name;

   private Map<String, String> properties;

   public String getName() {
      return name;
   }

   public NamedPropertyConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   public Map<String, String> getProperties() {
      return properties;
   }

   public NamedPropertyConfiguration setProperties(Map<String, String> properties) {
      this.properties = properties;
      return this;
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, properties);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }

      if (obj instanceof NamedPropertyConfiguration other) {
         return Objects.equals(name, other.name) && Objects.equals(properties, other.properties);
      }

      return false;
   }
}
