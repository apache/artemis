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

import java.util.*;

/**
 * Intermediate Representation for schema generation.
 *
 * <p>Two-phase architecture:
 *
 * <ol>
 *   <li>Build IR graph during reflection traversal (track class usage)
 *   <li>Enrich IR nodes with XSD/JavaDoc metadata
 *   <li>Analyze usage to determine what to extract to $defs
 *   <li>Emit final JSON schema with proper $ref usage
 * </ol>
 *
 * <p>This approach prevents invalid schemas (mixing $ref + properties) because enrichments happen
 * on IR nodes before emission, not on generated JSON.
 */
public class SchemaIR {

   /**
    * The fully-qualified class name of TransportConfiguration.
    *
    * <p>TransportConfiguration is excluded from $defs extraction because it uses factory-based
    * polymorphism, which is handled inline via oneOf patterns in the emitted schema.
    */
   private final Set<String> factoryBaseClasses = new HashSet<>();

   /** All discovered class nodes, keyed by class name. */
   private final Map<String, ClassNode> nodes = new LinkedHashMap<>();

   /**
    * Tracks usage contexts for each class to determine extraction candidates. Key: class name,
    * Value: set of paths where this class is used
    */
   private final Map<String, Set<String>> usageContexts = new LinkedHashMap<>();

   /**
    * Stores enrichments (metadata from XSD, JavaDoc, etc.) keyed by Location. Applied during schema
    * emission.
    */
   private final Map<Location, Enrichment> enrichments = new LinkedHashMap<>();

   /**
    * Register a class usage at a specific location.
    *
    * @param className Fully qualified class name
    * @param location Property location where this class appears
    */
   public void recordUsage(String className, Location location) {
      usageContexts.computeIfAbsent(className, k -> new LinkedHashSet<>()).add(location.toDotted());
   }

   /**
    * Register a class usage with a raw string context (for non-Location contexts like
    * "factory-variant").
    *
    * @param className fully qualified class name
    * @param context free-form context string (e.g. "factory-variant")
    */
   public void recordUsage(String className, String context) {
      usageContexts.computeIfAbsent(className, k -> new LinkedHashSet<>()).add(context);
   }

   /**
    * Mark a class as a factory base class — it won't be extracted to $defs because its factory
    * variants replace it with oneOf.
    */
   public void markAsFactoryBase(String className) {
      factoryBaseClasses.add(className);
   }

   /**
    * Get or create a class node.
    *
    * @param className Fully qualified class name
    * @return The class node (created if doesn't exist)
    */
   public ClassNode getOrCreateNode(String className) {
      return nodes.computeIfAbsent(className, ClassNode::new);
   }

   /**
    * Get an existing class node, or null if not found. Use this in read-only contexts (emission)
    * where creating nodes would be a bug.
    *
    * @param className fully qualified class name
    * @return the existing node, or {@code null} if absent
    */
   public ClassNode getNode(String className) {
      return nodes.get(className);
   }

   /**
    * Get all class nodes.
    *
    * @return all registered class nodes in insertion order
    */
   public Collection<ClassNode> getAllNodes() {
      return nodes.values();
   }

   /**
    * Get usage count for a class.
    *
    * @param className Fully qualified class name
    * @return Number of distinct contexts where this class is used
    */
   public int getUsageCount(String className) {
      Set<String> contexts = usageContexts.get(className);
      return contexts == null ? 0 : contexts.size();
   }

   /**
    * Get the best usage context path for a class — the one that has the most enrichments
    * stored for its child properties. Falls back to the first recorded context.
    *
    * @param className fully qualified class name
    * @return best usage context path, or empty string if none recorded
    */
   public String getBestUsageContext(String className) {
      Set<String> contexts = usageContexts.get(className);
      if (contexts == null || contexts.isEmpty()) {
         return "";
      }

      ClassNode node = nodes.get(className);
      if (node == null) {
         return contexts.iterator().next();
      }

      String bestContext = "";
      int bestCount = -1;
      for (String ctx : contexts) {
         int count = 0;
         Location base = Location.of(ctx);
         for (String propName : node.getProperties().keySet()) {
            Location propLoc = base.child(propName);
            if (enrichments.containsKey(propLoc)) {
               count++;
            }
         }
         if (count > bestCount) {
            bestCount = count;
            bestContext = ctx;
         }
      }
      return bestContext;
   }

   /**
    * Determine if a class should be extracted to $defs.
    *
    * @param className Fully qualified class name
    * @return true if used in multiple contexts OR is a polymorphic base class OR has factory
    *     variants
    */
   public boolean shouldExtract(String className) {
      // Don't extract classes replaced by factory variants (they use inline oneOf)
      if (factoryBaseClasses.contains(className)) {
         return false;
      }

      // Extract if used multiple times
      if (getUsageCount(className) > 1) {
         return true;
      }

      // Extract if it's a polymorphic base class (has subclasses)
      ClassNode node = nodes.get(className);
      if (node != null && node.isPolymorphic()) {
         return true;
      }

      return false;
   }

   /**
    * Apply enrichment to IR. Stores metadata that will be merged during schema emission.
    *
    * @param location Property location
    * @param metadata Metadata to merge into the property
    */
   public void enrich(Location location, Map<String, Object> metadata) {
      enrichments.merge(
            location, new Enrichment(metadata), (existing, incoming) -> existing.merge(incoming));
   }

   /**
    * Get enrichment for a location.
    *
    * @param location Property location
    * @return Enrichment at this location, or empty enrichment if none
    */
   public Enrichment getEnrichment(Location location) {
      return enrichments.getOrDefault(location, new Enrichment());
   }

   /** Represents a discovered class with its properties and metadata. */
   public static class ClassNode {
      private final String className;
      private final Map<String, PropertyNode> properties = new LinkedHashMap<>();
      private final ClassMetadata metadata = new ClassMetadata();
      private final List<String> subclassNames = new ArrayList<>();
      private String superclassName = null;
      private List<String> requiredProperties = null;

      /**
       * @param className fully qualified class name for this IR node
       */
      public ClassNode(String className) {
         this.className = className;
      }

      /**
       * Returns the fully-qualified class name.
       *
       * @return fully qualified class name
       */
      public String getClassName() {
         return className;
      }

      /**
       * Extracts the simple class name from the fully-qualified name. E.g.
       * "org.apache.activemq.artemis.core.config.impl.ConfigurationImpl" yields "ConfigurationImpl".
       *
       * @return unqualified class name
       */
      public String getSimpleName() {
         int lastDot = className.lastIndexOf('.');
         return lastDot >= 0 ? className.substring(lastDot + 1) : className;
      }

      /**
       * Returns the mutable property map. Extractors add properties to this map during IR building.
       *
       * @return live map of property name to node (insertion-ordered)
       */
      public Map<String, PropertyNode> getProperties() {
         return properties;
      }

      /**
       * Gets an existing property node or creates a new one. Used during IR building to accumulate
       * schema fields across extractors.
       *
       * @param name property name
       * @return existing or newly created property node
       */
      public PropertyNode getOrCreateProperty(String name) {
         return properties.computeIfAbsent(name, PropertyNode::new);
      }

      /**
       * Returns the typed class-level metadata.
       *
       * @return mutable class metadata (never null)
       */
      public ClassMetadata getClassMetadata() {
         return metadata;
      }

      public void setRequired(List<String> required) {
         this.requiredProperties = required;
      }

      public List<String> getRequired() {
         return requiredProperties;
      }

      /**
       * Registers a discovered polymorphic subclass (deduplicated).
       *
       * @param subclassName fully qualified name of the subclass to register
       */
      public void addSubclass(String subclassName) {
         if (!subclassNames.contains(subclassName)) {
            subclassNames.add(subclassName);
         }
      }

      /**
       * Marks this class as inheriting from a polymorphic base class.
       *
       * @param superclassName fully qualified name of the base class
       */
      public void setSuperclass(String superclassName) {
         this.superclassName = superclassName;
      }

      /**
       * Returns the list of registered polymorphic subclass names.
       *
       * @return subclass names in discovery order
       */
      public List<String> getSubclasses() {
         return subclassNames;
      }

      /**
       * Returns the superclass name, or {@code null} if this class has no registered superclass.
       *
       * @return fully qualified superclass name, or {@code null}
       */
      public String getSuperclass() {
         return superclassName;
      }

      /**
       * Returns {@code true} if this class has registered subclasses (polymorphic base).
       *
       * @return {@code true} if at least one subclass has been registered
       */
      public boolean isPolymorphic() {
         return !subclassNames.isEmpty();
      }
   }

   /** Represents a single broker configuration property in the IR graph. */
   public static class PropertyNode {

      /** Property name — becomes the JSON key in the emitted schema. */
      private final String name;

      /**
       * Raw schema fields for emission (enum, const, x-access, x-sources, etc.). The "type" entry is
       * kept in sync by {@link #setSchemaType} — do not write "type" directly into this map.
       */
      private final Map<String, Object> schema = new LinkedHashMap<>();

      /** Typed JSON Schema type — single ({@code INTEGER}) or union ({@code [INTEGER, STRING]}). */
      private SchemaType schemaType;

      /**
       * For nested/map/collection properties, the fully qualified Java class of the value type. Used
       * by emitters to resolve {@code $ref} targets and by factory variant builders to detect
       * polymorphic types. {@code null} for primitives and enums.
       */
      private String targetClassName;

      /**
       * Classification that determines which {@link org.apache.artemis.jsonschema.emitters.PropertyEmitter}
       * strategy handles this property during schema emission.
       */
      private PropertyType propertyType = PropertyType.PRIMITIVE;

      /**
       * Factory class name to param list — populated by {@link
       * org.apache.artemis.jsonschema.factories.FactoryVariantBuilder} for properties whose value type
       * has polymorphic implementations (e.g. acceptorConfigurations → Netty/InVM variants). Empty for
       * non-polymorphic properties.
       */
      private final Map<String, List<String>> factoryVariants = new LinkedHashMap<>();

      /**
       * Full dotted path of this property in the configuration tree (e.g. {@code
       * acceptorConfigurations}). Set during IR building; used by factory variant builders in pass 2
       * to determine the property context.
       */
      private Location location;

      /**
       * Number of intermediate Collection/Map nesting layers between the top-level Map and the
       * leaf element class. Each layer adds one level of {@code additionalProperties} wrapping.
       * Zero for non-nested map/collection properties.
       */
      private int collectionNestingDepth = 0;

      /**
       * @param name property name (used as the JSON key in the emitted schema)
       */
      public PropertyNode(String name) {
         this.name = name;
      }

      public String getName() {
         return name;
      }

      public SchemaType getSchemaType() {
         return schemaType;
      }

      public void setSchemaType(SchemaType schemaType) {
         this.schemaType = schemaType;
         schema.put("type", schemaType.toSchemaValue());
      }

      /**
       * Returns the raw schema fields map (type, enum, const, x-access, etc.).
       * The "type" entry is kept in sync by {@link #setSchemaType}.
       *
       * @return mutable schema field map
       */
      public Map<String, Object> getSchema() {
         return schema;
      }

      /**
       * Adds or overwrites a single schema field (e.g. "type", "default", "enum").
       *
       * @param key JSON Schema keyword
       * @param value the value to set for this keyword
       */
      public void setSchemaField(String key, Object value) {
         schema.put(key, value);
      }

      /**
       * For nested objects, returns the fully-qualified name of the target {@link ClassNode}.
       *
       * @return target class name, or {@code null} for primitives/enums
       */
      public String getTargetClassName() {
         return targetClassName;
      }

      /**
       * Sets the target class for nested object resolution. Side effect: also forces {@code
       * propertyType} to {@link PropertyType#NESTED_OBJECT}.
       *
       * @param targetClassName fully qualified class name of the nested type
       */
      public void setTargetClassName(String targetClassName) {
         this.targetClassName = targetClassName;
         this.propertyType = PropertyType.NESTED_OBJECT;
      }

      /**
       * Returns the property type classification used for emitter dispatch.
       *
       * @return current property type classification
       */
      public PropertyType getPropertyType() {
         return propertyType;
      }

      public void setLocation(Location location) {
         this.location = location;
      }

      public Location getLocation() {
         return location;
      }

      /**
       * Overrides the property type. Typically called after {@link #setTargetClassName} to correct
       * the classification to {@link PropertyType#MAP_VALUE} or {@link
       * PropertyType#COLLECTION_ELEMENT}.
       *
       * @param type the corrected property type classification
       */
      public void setPropertyType(PropertyType type) {
         this.propertyType = type;
      }

      public int getCollectionNestingDepth() {
         return collectionNestingDepth;
      }

      public void setCollectionNestingDepth(int depth) {
         this.collectionNestingDepth = depth;
      }

      /**
       * Registers a factory variant (e.g. InVMConnectorFactory) with its parameter names.
       *
       * @param factoryClassName fully qualified factory class name
       * @param paramNames parameter names this factory supports
       */
      public void addFactoryVariant(String factoryClassName, List<String> paramNames) {
         factoryVariants.put(factoryClassName, paramNames);
      }

      /**
       * Returns the factory variant map: factory class name → list of parameter names.
       *
       * @return live variant map (insertion-ordered)
       */
      public Map<String, List<String>> getFactoryVariants() {
         return factoryVariants;
      }

      /**
       * Returns {@code true} if this property has registered factory variants.
       *
       * @return {@code true} if at least one factory variant exists
       */
      public boolean hasFactoryVariants() {
         return !factoryVariants.isEmpty();
      }
   }

   /** Property type classification for emission logic. */
   public enum PropertyType {
      PRIMITIVE, // string, integer, boolean, number
      ENUM, // string with enum constraint (emitted same as PRIMITIVE)
      NESTED_OBJECT, // Direct nested object (can use $ref)
      MAP_VALUE, // Value type in Map<String, T> (must inline)
      COLLECTION_ELEMENT, // Element type in Collection<T> (must inline)
      MAP_COLLECTION_VALUE // Map value is nested container(s) — N extra additionalProperties levels
   }
}
