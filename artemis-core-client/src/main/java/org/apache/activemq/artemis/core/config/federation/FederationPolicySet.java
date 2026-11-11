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
package org.apache.activemq.artemis.core.config.federation;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

public class FederationPolicySet implements FederationPolicy<FederationPolicySet>, Serializable {

   private String name;
   private Set<String> policyRefs = new HashSet<>();

   @Override
   public String getName() {
      return name;
   }

   @Override
   public FederationPolicySet setName(String name) {
      this.name = name;
      return this;
   }

   public Set<String> getPolicyRefs() {
      return policyRefs;
   }

   public FederationPolicySet addPolicyRef(String name) {
      policyRefs.add(name);
      return this;
   }

   public FederationPolicySet setPolicyRefs(Set<String> refs) {
      for (String ref : refs) {
         policyRefs.add(ref);
      }
      return this;
   }

   public FederationPolicySet addPolicyRefs(Collection<String> name) {
      policyRefs.addAll(name);
      return this;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof FederationPolicySet other)) {
         return false;
      }

      return Objects.equals(name, other.name) &&
             Objects.equals(policyRefs, other.policyRefs);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, policyRefs);
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      Objects.requireNonNull(name, "name can not be null");
      buffer.writeString(name);

      buffer.writeInt(policyRefs == null ? 0 : policyRefs.size());
      if (policyRefs != null) {
         for (String policyRef : policyRefs) {
            buffer.writeString(policyRef);
         }
      }
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      name = buffer.readString();

      final int policyRefsSize = buffer.readInt();
      policyRefs = new HashSet<>();

      for (int i = 0; i < policyRefsSize; i++) {
         policyRefs.add(buffer.readString());
      }
   }
}
