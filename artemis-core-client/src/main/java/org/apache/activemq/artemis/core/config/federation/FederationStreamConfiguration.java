/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.config.federation;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

public abstract class FederationStreamConfiguration<T extends FederationStreamConfiguration<T>> implements Serializable {

   private String name;

   private FederationConnectionConfiguration connectionConfiguration = new FederationConnectionConfiguration();
   private Set<String> policyRefs = new HashSet<>();

   public String getName() {
      return name;
   }

   public T setName(String name) {
      this.name = name;
      return (T) this;
   }

   public Set<String> getPolicyRefs() {
      return policyRefs;
   }

   public T setPolicyRefs(Set<String> refs) {
      for (String ref : refs) {
         policyRefs.add(ref);
      }
      return (T) this;
   }

   public T addPolicyRef(String name) {
      policyRefs.add(name);
      return (T) this;
   }

   public T addPolicyRefs(Collection<String> names) {
      policyRefs.addAll(names);
      return (T) this;
   }

   public FederationConnectionConfiguration getConnectionConfiguration() {
      return connectionConfiguration;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof FederationStreamConfiguration other)) {
         return false;
      }

      return Objects.equals(name, other.name) &&
             Objects.equals(connectionConfiguration, other.connectionConfiguration) &&
             Objects.equals(policyRefs, other.policyRefs);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, connectionConfiguration, policyRefs);
   }

   public void encode(ActiveMQBuffer buffer) {
      buffer.writeString(name);
      connectionConfiguration.encode(buffer);

      buffer.writeInt(policyRefs == null ? 0 : policyRefs.size());
      for (String policyRef : policyRefs) {
         buffer.writeString(policyRef);
      }
   }

   public void decode(ActiveMQBuffer buffer) {
      name = buffer.readString();
      connectionConfiguration.decode(buffer);
      int policyRefNum = buffer.readInt();
      for (int i = 0; i < policyRefNum; i++) {
         policyRefs.add(buffer.readString());
      }
   }

   public T setStaticConnectors(List<String> connectors) {
      connectionConfiguration.setStaticConnectors(connectors);
      return (T) this;
   }
}
