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

public class ConnectorServiceConfiguration implements Serializable {

   private static final long serialVersionUID = -641207073030767325L;

   private String name;

   private String factoryClassName;

   private Map<String, Object> params;

   public ConnectorServiceConfiguration() {
   }

   public String getConnectorName() {
      return name;
   }

   public String getFactoryClassName() {
      return factoryClassName;
   }

   public Map<String, Object> getParams() {
      return params;
   }

   public String getName() {
      return name;
   }

   public ConnectorServiceConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   public ConnectorServiceConfiguration setFactoryClassName(String factoryClassName) {
      this.factoryClassName = factoryClassName;
      return this;
   }

   public ConnectorServiceConfiguration setParams(Map<String, Object> params) {
      this.params = params;
      return this;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof ConnectorServiceConfiguration other)) {
         return false;
      }

      return Objects.equals(getFactoryClassName(), other.getFactoryClassName()) &&
             Objects.equals(getConnectorName(), other.getConnectorName()) &&
             Objects.equals(getParams(), other.getParams());
   }

   @Override
   public int hashCode() {
      return Objects.hash(getConnectorName(), getFactoryClassName(), getParams());
   }
}
