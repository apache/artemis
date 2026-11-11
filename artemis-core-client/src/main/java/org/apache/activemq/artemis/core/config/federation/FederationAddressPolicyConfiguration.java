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

public class FederationAddressPolicyConfiguration implements FederationPolicy<FederationAddressPolicyConfiguration>, Serializable {

   private String name;
   private Set<Matcher> includes = new HashSet<>();
   private Set<Matcher> excludes = new HashSet<>();
   private Boolean autoDelete;
   private Long autoDeleteDelay;
   private Long autoDeleteMessageCount;
   private int maxHops;
   private String transformerRef;
   private boolean enableDivertBindings;

   @Override
   public String getName() {
      return name;
   }

   @Override
   public FederationAddressPolicyConfiguration setName(String name) {
      this.name = name;
      return this;
   }

   public Set<Matcher> getIncludes() {
      return includes;
   }

   public Set<Matcher> getExcludes() {
      return excludes;
   }

   public FederationAddressPolicyConfiguration addInclude(Matcher include) {
      includes.add(include);
      return this;
   }

   public FederationAddressPolicyConfiguration addExclude(Matcher exclude) {
      excludes.add(exclude);
      return this;
   }

   public int getMaxHops() {
      return maxHops;
   }

   public FederationAddressPolicyConfiguration setMaxHops(int maxHops) {
      this.maxHops = maxHops;
      return this;
   }

   public Long getAutoDeleteMessageCount() {
      return autoDeleteMessageCount;
   }

   public FederationAddressPolicyConfiguration setAutoDeleteMessageCount(Long autoDeleteMessageCount) {
      this.autoDeleteMessageCount = autoDeleteMessageCount;
      return this;
   }

   public Long getAutoDeleteDelay() {
      return autoDeleteDelay;
   }

   public FederationAddressPolicyConfiguration setAutoDeleteDelay(Long autoDeleteDelay) {
      this.autoDeleteDelay = autoDeleteDelay;
      return this;
   }

   public Boolean getAutoDelete() {
      return autoDelete;
   }

   public FederationAddressPolicyConfiguration setAutoDelete(Boolean autoDelete) {
      this.autoDelete = autoDelete;
      return this;
   }

   public String getTransformerRef() {
      return transformerRef;
   }

   public FederationAddressPolicyConfiguration setTransformerRef(String transformerRef) {
      this.transformerRef = transformerRef;
      return this;
   }

   public Boolean isEnableDivertBindings() {
      return enableDivertBindings;
   }

   public FederationAddressPolicyConfiguration setEnableDivertBindings(Boolean enableDivertBindings) {
      this.enableDivertBindings = enableDivertBindings;
      return this;
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      Objects.requireNonNull(name, "name can not be null");
      buffer.writeString(name);
      buffer.writeNullableBoolean(autoDelete);
      buffer.writeNullableLong(autoDeleteDelay);
      buffer.writeNullableLong(autoDeleteMessageCount);
      buffer.writeInt(maxHops);
      buffer.writeNullableString(transformerRef);
      encodeMatchers(buffer, includes);
      encodeMatchers(buffer, excludes);
      buffer.writeBoolean(enableDivertBindings);
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      name = buffer.readString();
      autoDelete = buffer.readNullableBoolean();
      autoDeleteDelay = buffer.readNullableLong();
      autoDeleteMessageCount = buffer.readNullableLong();
      maxHops = buffer.readInt();
      transformerRef = buffer.readNullableString();
      includes = new HashSet<>();
      excludes = new HashSet<>();
      decodeMatchers(buffer, includes);
      decodeMatchers(buffer, excludes);

      if (buffer.readableBytes() > 0) {
         enableDivertBindings = buffer.readBoolean();
      }
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

   public static class Matcher implements Serializable {

      private String addressMatch;

      private String name;

      public String getName() {
         if (name == null) {
            return addressMatch;
         }
         return name;
      }

      public void setName(String name) {
         this.name = name;
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

         return Objects.equals(addressMatch, other.addressMatch);
      }

      @Override
      public int hashCode() {
         return Objects.hashCode(addressMatch);
      }

      public void encode(ActiveMQBuffer buffer) {
         Objects.requireNonNull(addressMatch, "addressMatch can not be null");
         buffer.writeString(addressMatch);
      }

      public void decode(ActiveMQBuffer buffer) {
         addressMatch = buffer.readString();
      }
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof FederationAddressPolicyConfiguration other)) {
         return false;
      }

      return maxHops == other.maxHops &&
             Objects.equals(name, other.name) &&
             Objects.equals(includes, other.includes) &&
             Objects.equals(excludes, other.excludes) &&
             Objects.equals(autoDelete, other.autoDelete) &&
             Objects.equals(autoDeleteDelay, other.autoDeleteDelay) &&
             Objects.equals(autoDeleteMessageCount, other.autoDeleteMessageCount) &&
             Objects.equals(transformerRef, other.transformerRef);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, includes, excludes, autoDelete, autoDeleteDelay, autoDeleteMessageCount, maxHops,
                          transformerRef);
   }
}
