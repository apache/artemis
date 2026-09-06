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

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class BrokerPluginConfiguration implements Serializable {

   private static final long serialVersionUID = 1L;

   private String className;

   private Map<String, String> properties;

   public BrokerPluginConfiguration() {
   }

   public BrokerPluginConfiguration(String className, Map<String, String> properties) {
      this.className = className;
      this.properties = properties;
   }

   public String getClassName() {
      return className;
   }

   public BrokerPluginConfiguration setClassName(String className) {
      this.className = className;
      return this;
   }

   public Map<String, String> getProperties() {
      return properties;
   }

   public BrokerPluginConfiguration setProperties(Map<String, String> properties) {
      this.properties = properties;
      return this;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof BrokerPluginConfiguration other)) {
         return false;
      }

      return Objects.equals(getClassName(), other.getClassName()) && Objects.equals(getProperties(), other.getProperties());
   }

   @Override
   public int hashCode() {
      return Objects.hash(getClassName(), getProperties());
   }

   @Override
   public String toString() {
      return "BrokerPluginConfiguration[className=" + className + ", properties=" + properties + "]";
   }
}
