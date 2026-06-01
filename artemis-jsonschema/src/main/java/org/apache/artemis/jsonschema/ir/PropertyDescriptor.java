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

package org.apache.artemis.jsonschema.ir;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Unified property descriptor representing a single broker configuration property.
 *
 * <p>Aggregates metadata from multiple extraction sources (reflection, XSD, JavaDoc, etc.) for a
 * single property path in the broker.properties format.
 */
public class PropertyDescriptor {
   /** Property path in broker.properties format (e.g., "acceptorConfigurations.*.params.host") */
   private Location path;

   /** Aggregated metadata from all sources */
   private PropertyMetadata metadata;

   /** Which extractors contributed to this property (insertion order preserved) */
   private Set<PropertySource> sources;

   /** Enrichment pattern if this was enriched (e.g., "acceptorConfigurations.*") */
   private String enrichmentPattern;

   /** Construct an empty descriptor with no path or sources. */
   public PropertyDescriptor() {
      this.metadata = new PropertyMetadata();
      this.sources = new LinkedHashSet<>();
   }

   /**
    * Construct a descriptor for the given dotted property path.
    *
    * @param path dot-separated property path (e.g. "acceptorConfigurations.*.params.host")
    */
   public PropertyDescriptor(String path) {
      this();
      this.path = Location.of(path);
   }

   /**
    * Construct a descriptor for the given path, attributed to a single extraction source.
    *
    * @param path dot-separated property path
    * @param source the extractor that produced this descriptor
    */
   public PropertyDescriptor(String path, PropertySource source) {
      this(path);
      this.sources.add(source);
   }

   public Location getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = Location.of(path);
   }

   public PropertyMetadata getMetadata() {
      return metadata;
   }

   public void setMetadata(PropertyMetadata metadata) {
      this.metadata = metadata;
   }

   public Set<PropertySource> getSources() {
      return sources;
   }

   public void setSources(Set<PropertySource> sources) {
      this.sources = sources;
   }

   public void addSource(PropertySource source) {
      this.sources.add(source);
   }

   public String getEnrichmentPattern() {
      return enrichmentPattern;
   }

   public void setEnrichmentPattern(String enrichmentPattern) {
      this.enrichmentPattern = enrichmentPattern;
   }

   /**
    * Merge another descriptor into this one. Combines sources and merges metadata according to
    * precedence rules.
    *
    * @param other descriptor to merge; must share the same path
    * @throws IllegalArgumentException if paths differ
    */
   public void merge(PropertyDescriptor other) {
      if (!this.path.equals(other.path)) {
         throw new IllegalArgumentException(
               "Cannot merge descriptors with different paths: " + this.path + " vs " + other.path);
      }

      // Merge sources
      this.sources.addAll(other.sources);

      // Merge metadata - determine which source has precedence
      PropertySource thisPrimary = getPrimarySource(this.sources);
      PropertySource otherPrimary = getPrimarySource(other.sources);
      this.metadata.merge(other.metadata, otherPrimary, thisPrimary);

      // Enrichment pattern: take first non-null
      if (other.enrichmentPattern != null && this.enrichmentPattern == null) {
         this.enrichmentPattern = other.enrichmentPattern;
      }
   }

   /**
    * Determine the primary source for precedence rules. Order: REFLECTION > XSD > XML_PARSER >
    * ENRICHMENT > METADATA
    *
    * @param sources set of sources to evaluate
    * @return the highest-priority source present in the set
    */
   private PropertySource getPrimarySource(Set<PropertySource> sources) {
      if (sources.contains(PropertySource.REFLECTION)) return PropertySource.REFLECTION;
      if (sources.contains(PropertySource.XSD)) return PropertySource.XSD;
      if (sources.contains(PropertySource.XML_PARSER)) return PropertySource.XML_PARSER;
      if (sources.contains(PropertySource.ENRICHMENT)) return PropertySource.ENRICHMENT;
      return PropertySource.METADATA;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PropertyDescriptor that = (PropertyDescriptor) o;
      return Objects.equals(path, that.path);
   }

   @Override
   public int hashCode() {
      return Objects.hash(path);
   }

   @Override
   public String toString() {
      return "PropertyDescriptor{"
            + "path='"
            + path
            + '\''
            + ", sources="
            + sources
            + ", metadata="
            + metadata
            + '}';
   }
}
