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

package org.apache.artemis.jsonschema.emitters;

import java.util.Map;
import org.apache.artemis.jsonschema.ir.Location;
import org.apache.artemis.jsonschema.ir.SchemaIR;

/**
 * Base class for property schema emitters.
 *
 * <p>Each subclass handles one {@link org.apache.artemis.jsonschema.SchemaIR.PropertyType},
 * emitting the appropriate JSON Schema structure and applying enrichments.
 *
 * <p>Shared behavior (like applying enrichments to pre-built nested properties) lives here to avoid
 * duplication across emitters.
 */
public abstract class PropertyEmitter {

   /**
    * Emit JSON Schema for a property node.
    *
    * @param prop PropertyNode containing type information and metadata
    * @param ir SchemaIR for context (usage counts, class nodes, enrichments)
    * @param location typed path for enrichment lookup
    * @param context EmissionContext providing access to recursive emission and helper methods
    * @return JSON Schema map for this property
    */
   public abstract Map<String, Object> emit(
         SchemaIR.PropertyNode prop, SchemaIR ir, Location location, EmissionContext context);

   /**
    * Merge enrichment metadata (descriptions, defaults, constraints) from the IR into the emitted
    * schema. Existing non-null values are preserved — earlier, more specific enrichments are never
    * overwritten by broader ones.
    *
    * @param schema mutable schema map to enrich
    * @param ir the IR graph containing enrichment data
    * @param location property location for enrichment lookup
    */
   protected void applyEnrichments(Map<String, Object> schema, SchemaIR ir, Location location) {
      Map<String, Object> enrichment = ir.getEnrichment(location);
      for (Map.Entry<String, Object> entry : enrichment.entrySet()) {
         if (!schema.containsKey(entry.getKey()) || entry.getValue() != null) {
            schema.put(entry.getKey(), entry.getValue());
         }
      }
   }

   /**
    * Last-resort descriptions for well-known property names (params, properties, extraParams). Only
    * applied if no enrichment provided a description.
    *
    * @param schema mutable schema map (may receive a "description" entry)
    * @param location property location whose leaf name is checked
    */
   protected void applyFallbackDescriptions(Map<String, Object> schema, Location location) {
      String propName = location.leafName();
      if ("properties".equals(propName) && !schema.containsKey("description")) {
         schema.put("description", "Configuration key-value pairs passed to the configured instance");
      } else if ("params".equals(propName) && !schema.containsKey("description")) {
         schema.put("description", "Transport-specific parameters (depends on factoryClassName)");
      } else if ("extraParams".equals(propName) && !schema.containsKey("description")) {
         schema.put("description", "Additional transport parameters");
      }
   }

   /**
    * Build a oneOf array with $ref pointers to each factory variant in $defs. Each variant has
    * {@code factoryClassName} as a required property with a {@code const} discriminator, ensuring
    * exactly one variant matches.
    *
    * @param prop property node carrying factory variant registrations
    * @return schema fragment containing the {@code oneOf} array
    */
   protected Map<String, Object> emitFactoryOneOf(SchemaIR.PropertyNode prop) {
      Map<String, Object> wrapper = new java.util.LinkedHashMap<>();
      java.util.List<Map<String, Object>> schemas = new java.util.ArrayList<>();

      for (String factoryClassName : prop.getFactoryVariants().keySet()) {
         int lastDot = factoryClassName.lastIndexOf('.');
         String simpleFactoryName =
               lastDot >= 0 ? factoryClassName.substring(lastDot + 1) : factoryClassName;

         Map<String, Object> refSchema = new java.util.LinkedHashMap<>();
         refSchema.put("$ref", "#/$defs/" + simpleFactoryName);
         schemas.add(refSchema);
      }

      wrapper.put("oneOf", schemas);
      return wrapper;
   }

   /**
    * Apply enrichments to pre-built nested properties inside a schema.
    *
    * <p>Factory params (host, port, sslEnabled...) are assembled as a pre-built "properties" block
    * in TransportFactoryVariantBuilder, bypassing the normal per-property emission path. This method
    * "catches up" by applying enrichments (descriptions, defaults) to those nested properties using
    * the path convention shared with extractors.
    *
    * <p>Only relevant for properties that have an inline "properties" map (factory params). No-op if
    * the schema has no "properties" key.
    *
    * @param schema mutable schema map potentially containing a "properties" sub-map
    * @param ir the IR graph containing enrichment data
    * @param location parent location used to derive child paths for each nested property
    */
   @SuppressWarnings("unchecked")
   protected void applyNestedEnrichments(
         Map<String, Object> schema, SchemaIR ir, Location location) {
      if (schema.containsKey("properties") && schema.get("properties") instanceof Map) {
         Map<String, Object> nestedProps = (Map<String, Object>) schema.get("properties");
         for (Map.Entry<String, Object> nestedEntry : nestedProps.entrySet()) {
            Location nestedLocation = location.child(nestedEntry.getKey());

            if (nestedEntry.getValue() instanceof Map) {
               Map<String, Object> nestedSchema = (Map<String, Object>) nestedEntry.getValue();

               Map<String, Object> nestedEnrichment = ir.getEnrichment(nestedLocation);
               for (Map.Entry<String, Object> enrichEntry : nestedEnrichment.entrySet()) {
                  if (!nestedSchema.containsKey(enrichEntry.getKey()) || enrichEntry.getValue() != null) {
                     nestedSchema.put(enrichEntry.getKey(), enrichEntry.getValue());
                  }
               }
            }
         }
      }
   }
}
