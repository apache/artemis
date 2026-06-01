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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Transport format carrying all known facts about a single broker configuration property.
 *
 * <p>Extractors populate instances with whatever they can discover; the pipeline then {@linkplain
 * #merge merges} them according to source-priority rules so that the best value for each field
 * survives into the final schema.
 */
public class PropertyMetadata {
   /** JSON Schema type — single or union. */
   private SchemaType type;

   /**
    * Compile-time or runtime default; stored as the natural Java type (String, Long, Boolean, …).
    */
   private Object defaultValue;

   /** Human-readable description emitted into the schema's {@code description} keyword. */
   private String description;

   /** Access mode: {@code "RW"}, {@code "R"}, or {@code "W"}. */
   private String access;

   /** Allowed values for enum-typed properties; {@code null} when unconstrained. */
   private List<String> enumValues;

   /** Lower bound (inclusive); must be a {@link Number} subtype or {@code null}. */
   private Object minimum;

   /** Upper bound (inclusive); must be a {@link Number} subtype or {@code null}. */
   private Object maximum;

   private Boolean required;
   private List<Object> exampleValues;

   /** Parallel to {@link #exampleValues} — records which extractor contributed each example. */
   private List<String> exampleSources;

   private Boolean deprecated;

   /** Factories that support this parameter (e.g. {@code ["NettyAcceptorFactory"]}). */
   private List<String> factorySpecific;

   /** Fully-qualified Java class for object-typed properties. */
   private String javaClass;

   /** Whether the property can be changed at runtime without a broker restart. */
   private Boolean hotReloadable;

   /** Whether the broker has explicit validation logic for this property. */
   private Boolean validated;

   /** Regex pattern for input validation (e.g. byte notation). */
   private String pattern;

   /** Name of the runtime converter method (e.g. "ByteUtil.convertTextBytes"). */
   private String converter;

   public PropertyMetadata() {}

   public SchemaType getType() {
      return type;
   }

   public void setType(SchemaType type) {
      this.type = type;
   }

   public Object getDefaultValue() {
      return defaultValue;
   }

   public void setDefaultValue(Object defaultValue) {
      this.defaultValue = defaultValue;
   }

   public String getDescription() {
      return description;
   }

   /**
    * Set the human-readable description; rejects blank strings as extractor bugs.
    *
    * @param description description text, or {@code null} to mean "unknown"
    * @throws IllegalArgumentException if {@code description} is non-null but blank
    */
   public void setDescription(String description) {
      if (description != null && description.trim().isEmpty()) {
         throw new IllegalArgumentException("Description cannot be empty string");
      }
      this.description = description;
   }

   public String getAccess() {
      return access;
   }

   public void setAccess(String access) {
      this.access = access;
   }

   /**
    * Returns an unmodifiable view, or {@code null} if unset.
    *
    * @return immutable enum value list, or {@code null}
    */
   public List<String> getEnumValues() {
      return enumValues == null ? null : Collections.unmodifiableList(enumValues);
   }

   /**
    * Defensively copies the list; callers may freely mutate the original after this call.
    *
    * @param enumValues allowed values, or {@code null} to clear
    */
   public void setEnumValues(List<String> enumValues) {
      if (enumValues != null) {
         this.enumValues = new ArrayList<>(enumValues);
      } else {
         this.enumValues = null;
      }
   }

   public Object getMinimum() {
      return minimum;
   }

   /**
    * Set the lower bound constraint for numeric properties.
    *
    * @param minimum a {@link Number} subtype, or {@code null} for unconstrained
    * @throws IllegalArgumentException if {@code minimum} is non-null and not a {@link Number}
    */
   public void setMinimum(Object minimum) {
      if (minimum != null && !(minimum instanceof Number)) {
         throw new IllegalArgumentException(
               "Minimum must be Number, got: " + minimum.getClass().getSimpleName());
      }
      this.minimum = minimum;
   }

   public Object getMaximum() {
      return maximum;
   }

   /**
    * Set the upper bound constraint for numeric properties.
    *
    * @param maximum a {@link Number} subtype, or {@code null} for unconstrained
    * @throws IllegalArgumentException if {@code maximum} is non-null and not a {@link Number}
    */
   public void setMaximum(Object maximum) {
      if (maximum != null && !(maximum instanceof Number)) {
         throw new IllegalArgumentException(
               "Maximum must be Number, got: " + maximum.getClass().getSimpleName());
      }
      this.maximum = maximum;
   }

   public Boolean getRequired() {
      return required;
   }

   public void setRequired(Boolean required) {
      this.required = required;
   }

   /**
    * Returns an unmodifiable view, or {@code null} if unset.
    *
    * @return immutable example values list, or {@code null}
    */
   public List<Object> getExampleValues() {
      return exampleValues == null ? null : Collections.unmodifiableList(exampleValues);
   }

   /**
    * Defensively copies the list; callers may freely mutate the original after this call.
    *
    * @param exampleValues example values, or {@code null} to clear
    */
   public void setExampleValues(List<Object> exampleValues) {
      if (exampleValues != null) {
         this.exampleValues = new ArrayList<>(exampleValues);
      } else {
         this.exampleValues = null;
      }
   }

   /**
    * Returns an unmodifiable view, or {@code null} if unset.
    *
    * @return immutable example source list, or {@code null}
    */
   public List<String> getExampleSources() {
      return exampleSources == null ? null : Collections.unmodifiableList(exampleSources);
   }

   /**
    * Defensively copies the list; callers may freely mutate the original after this call.
    *
    * @param exampleSources extractor names paralleling {@link #getExampleValues()}, or {@code null}
    *     to clear
    */
   public void setExampleSources(List<String> exampleSources) {
      if (exampleSources != null) {
         this.exampleSources = new ArrayList<>(exampleSources);
      } else {
         this.exampleSources = null;
      }
   }

   public Boolean getDeprecated() {
      return deprecated;
   }

   public void setDeprecated(Boolean deprecated) {
      this.deprecated = deprecated;
   }

   /**
    * Returns an unmodifiable view, or {@code null} if unset.
    *
    * @return immutable factory class name list, or {@code null}
    */
   public List<String> getFactorySpecific() {
      return factorySpecific == null ? null : Collections.unmodifiableList(factorySpecific);
   }

   /**
    * Defensively copies the list; callers may freely mutate the original after this call.
    *
    * @param factorySpecific factory class names this param applies to, or {@code null} to clear
    */
   public void setFactorySpecific(List<String> factorySpecific) {
      if (factorySpecific != null) {
         this.factorySpecific = new ArrayList<>(factorySpecific);
      } else {
         this.factorySpecific = null;
      }
   }

   public String getJavaClass() {
      return javaClass;
   }

   public void setJavaClass(String javaClass) {
      this.javaClass = javaClass;
   }

   public Boolean getHotReloadable() {
      return hotReloadable;
   }

   public void setHotReloadable(Boolean hotReloadable) {
      this.hotReloadable = hotReloadable;
   }

   public Boolean getValidated() {
      return validated;
   }

   public void setValidated(Boolean validated) {
      this.validated = validated;
   }

   public String getPattern() {
      return pattern;
   }

   public void setPattern(String pattern) {
      this.pattern = pattern;
   }

   public String getConverter() {
      return converter;
   }

   public void setConverter(String converter) {
      this.converter = converter;
   }

   /**
    * Merge metadata from another descriptor.
    *
    * <p><b>Precedence rules</b> (determines which source wins when both have data):
    *
    * <ul>
    *   <li><b>description</b>: XSD wins (most detailed documentation)
    *   <li><b>defaultValue</b>: first non-null wins
    *   <li><b>type</b>: REFLECTION wins, except XSD string type for unit notation properties (e.g.,
    *       "10K" for 10240 bytes)
    *   <li><b>access</b>: REFLECTION wins (runtime truth)
    *   <li><b>enumValues, minimum, maximum, required</b>: first non-null wins
    *   <li><b>hotReloadable, validated</b>: METADATA source wins (most authoritative)
    * </ul>
    *
    * <p><b>Why:</b> This allows later enrichers to fill gaps without overriding earlier high-quality
    * sources. For example, JavaDoc can add descriptions where XSD didn't have them, but XSD
    * descriptions take precedence when both exist.
    *
    * <p><b>How to apply:</b> This method is called during property descriptor merging in the
    * enrichment phase. Sources are merged in pipeline order: REFLECTION → JAVADOC → XSD → etc.
    *
    * @param other metadata to merge into this instance
    * @param otherSource primary extraction source of {@code other}
    * @param thisSource primary extraction source of this instance
    */
   public void merge(PropertyMetadata other, PropertySource otherSource, PropertySource thisSource) {
      // Description: prefer XSD (must be merged before type so unit detection can reference it)
      String mergedDescription = this.description;
      if (other.description != null
            && (this.description == null || otherSource == PropertySource.XSD)) {
         this.description = other.description;
         mergedDescription = this.description;
      }

      // Default: first non-null wins
      if (other.defaultValue != null && this.defaultValue == null) {
         this.defaultValue = other.defaultValue;
      }

      // Type: prefer REFLECTION, EXCEPT when XSD says "string" and property has units
      if (other.type != null) {
         if (this.type == null) {
            this.type = other.type;
         } else if (otherSource == PropertySource.REFLECTION) {
            this.type = other.type;
         } else if (otherSource == PropertySource.XSD && other.type.isString()) {
            if ((this.type.isInteger() || this.type.isNumber())
                  && mergedDescription != null
                  && (mergedDescription.contains("byte notation")
                        || mergedDescription.contains("K\"")
                        || mergedDescription.contains("MB\"")
                        || mergedDescription.contains("GB\""))) {
               this.type = other.type;
            }
         }
      }

      // Access: prefer REFLECTION
      if (other.access != null && (this.access == null || otherSource == PropertySource.REFLECTION)) {
         this.access = other.access;
      }

      // Enums: take first non-null
      if (other.enumValues != null && this.enumValues == null) {
         this.enumValues = other.enumValues;
      }

      // Constraints: take first non-null
      if (other.minimum != null && this.minimum == null) {
         this.minimum = other.minimum;
      }
      if (other.maximum != null && this.maximum == null) {
         this.maximum = other.maximum;
      }
      if (other.required != null && this.required == null) {
         this.required = other.required;
      }

      // Factory-specific: take first non-null
      if (other.factorySpecific != null && this.factorySpecific == null) {
         this.factorySpecific = other.factorySpecific;
      }

      // Java class: take first non-null
      if (other.javaClass != null && this.javaClass == null) {
         this.javaClass = other.javaClass;
      }

      // Hot-reloadable: prefer METADATA source (most authoritative for runtime behavior)
      if (other.hotReloadable != null
            && (this.hotReloadable == null || otherSource == PropertySource.METADATA)) {
         this.hotReloadable = other.hotReloadable;
      }

      // Validated: prefer METADATA source
      if (other.validated != null
            && (this.validated == null || otherSource == PropertySource.METADATA)) {
         this.validated = other.validated;
      }
   }

   /** Equality based on the four identity fields: type, defaultValue, description, access. */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PropertyMetadata that = (PropertyMetadata) o;
      return Objects.equals(type, that.type)
            && Objects.equals(defaultValue, that.defaultValue)
            && Objects.equals(description, that.description)
            && Objects.equals(access, that.access);
   }

   /** Consistent with {@link #equals} — hashes type, defaultValue, description, access only. */
   @Override
   public int hashCode() {
      return Objects.hash(type, defaultValue, description, access);
   }

   @Override
   public String toString() {
      return "PropertyMetadata{"
            + "type='"
            + type
            + '\''
            + ", default="
            + defaultValue
            + ", access='"
            + access
            + '\''
            + ", description='"
            + (description != null
                  ? description.substring(0, Math.min(50, description.length())) + "..."
                  : null)
            + '\''
            + '}';
   }
}
