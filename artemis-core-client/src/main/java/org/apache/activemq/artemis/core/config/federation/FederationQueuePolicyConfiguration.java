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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

@Deprecated(forRemoval = true)
public class FederationQueuePolicyConfiguration implements FederationPolicy<FederationQueuePolicyConfiguration>, Serializable {

   private String name;
   private boolean includeFederated;
   private Set<Matcher> includes = new HashSet<>();
   private Set<Matcher> excludes = new HashSet<>();
   private Integer priorityAdjustment;
   private String transformerRef;

   @Deprecated(forRemoval = true)
   @Override
   public String getName() {
      return name;
   }

   @Deprecated(forRemoval = true)
   @Override
   public FederationQueuePolicyConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   @Deprecated(forRemoval = true)
   public Set<Matcher> getIncludes() {
      return includes;
   }

   @Deprecated(forRemoval = true)
   public Set<Matcher> getExcludes() {
      return excludes;
   }

   @Deprecated(forRemoval = true)
   public FederationQueuePolicyConfiguration addInclude(Matcher include) {
      includes.add(include);
      return this;
   }

   @Deprecated(forRemoval = true)
   public FederationQueuePolicyConfiguration addExclude(Matcher exclude) {
      excludes.add(exclude);
      return this;
   }

   @Deprecated(forRemoval = true)
   public boolean isIncludeFederated() {
      return includeFederated;
   }

   @Deprecated(forRemoval = true)
   public FederationQueuePolicyConfiguration setIncludeFederated(boolean includeFederated) {
      this.includeFederated = includeFederated;
      return this;
   }

   @Deprecated(forRemoval = true)
   public Integer getPriorityAdjustment() {
      return priorityAdjustment;
   }

   @Deprecated(forRemoval = true)
   public FederationQueuePolicyConfiguration setPriorityAdjustment(Integer priorityAdjustment) {
      this.priorityAdjustment = priorityAdjustment;
      return this;
   }

   @Deprecated(forRemoval = true)
   public String getTransformerRef() {
      return transformerRef;
   }

   @Deprecated(forRemoval = true)
   public FederationQueuePolicyConfiguration setTransformerRef(String transformerRef) {
      this.transformerRef = transformerRef;
      return this;
   }

   @Deprecated(forRemoval = true)
   @Override
   public void encode(ActiveMQBuffer buffer) {
      Objects.requireNonNull(name, "name can not be null");
      buffer.writeString(name);
      buffer.writeBoolean(includeFederated);
      buffer.writeNullableInt(priorityAdjustment);
      buffer.writeNullableString(transformerRef);
      encodeMatchers(buffer, includes);
      encodeMatchers(buffer, excludes);
   }

   @Deprecated(forRemoval = true)
   @Override
   public void decode(ActiveMQBuffer buffer) {
      name = buffer.readString();
      includeFederated = buffer.readBoolean();
      priorityAdjustment = buffer.readNullableInt();
      transformerRef = buffer.readNullableString();

      includes = new HashSet<>();
      excludes = new HashSet<>();
      decodeMatchers(buffer, includes);
      decodeMatchers(buffer, excludes);
   }

   private void encodeMatchers(final ActiveMQBuffer buffer, final Set<Matcher> matchers) {
      buffer.writeInt(matchers == null ? 0 : matchers.size());
      if (matchers != null) {
         for (Matcher matcher : matchers) {
            matcher.encode(buffer);
         }
      }
   }

   private void decodeMatchers(final ActiveMQBuffer buffer, final Set<Matcher> matchers) {
      final int size = buffer.readInt();

      for (int i = 0; i < size; i++) {
         Matcher matcher = new Matcher();
         matcher.decode(buffer);
         matchers.add(matcher);
      }
   }

   @Deprecated(forRemoval = true)
   public static class Matcher implements Serializable {

      private String queueMatch;
      private String addressMatch;
      private String name;

      public String getName() {
         if (name == null) {
            return addressMatch + queueMatch;
         }
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public String getQueueMatch() {
         return queueMatch;
      }

      public Matcher setQueueMatch(String queueMatch) {
         this.queueMatch = queueMatch;
         return this;
      }

      public String getAddressMatch() {
         return addressMatch;
      }

      public Matcher setAddressMatch(String addressMatch) {
         this.addressMatch = addressMatch;
         return this;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof Matcher other)) {
            return false;
         }

         return Objects.equals(queueMatch, other.queueMatch) &&
                Objects.equals(addressMatch, other.addressMatch);
      }

      @Override
      public int hashCode() {
         return Objects.hash(queueMatch, addressMatch);
      }

      public void encode(ActiveMQBuffer buffer) {
         buffer.writeNullableString(addressMatch);
         buffer.writeNullableString(queueMatch);
      }

      public void decode(ActiveMQBuffer buffer) {
         addressMatch = buffer.readNullableString();
         queueMatch = buffer.readNullableString();
      }
   }

   @Deprecated(forRemoval = true)
   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof FederationQueuePolicyConfiguration other)) {
         return false;
      }

      return includeFederated == other.includeFederated &&
             Objects.equals(name, other.name) &&
             Objects.equals(includes, other.includes) &&
             Objects.equals(excludes, other.excludes) &&
             Objects.equals(priorityAdjustment, other.priorityAdjustment) &&
             Objects.equals(transformerRef, other.transformerRef);
   }

   @Deprecated(forRemoval = true)
   @Override
   public int hashCode() {
      return Objects.hash(name, includeFederated, includes, excludes, priorityAdjustment, transformerRef);
   }
}
