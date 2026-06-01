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
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.artemis.jsonschema.emitters.*;

/**
 * Emits JSON Schema (Draft 7) from an enriched SchemaIR graph.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Traverse IR nodes and emit JSON Schema structures
 *   <li>Resolve $ref vs inline decisions based on usage counts
 *   <li>Apply enrichments during emission
 *   <li>Handle polymorphism (allOf, oneOf patterns)
 * </ul>
 *
 * <p>This class is stateless after construction and can be reused across multiple emissions.
 */
public class SchemaEmitter implements EmissionContext {

   private final SchemaIR ir;
   private final Map<SchemaIR.PropertyType, PropertyEmitter> emitterRegistry =
         new EnumMap<>(SchemaIR.PropertyType.class);

   /**
    * Initializes the emitter with a pre-built IR graph and registers a strategy emitter for each
    * {@link SchemaIR.PropertyType} so property emission is fully table-driven.
    *
    * @param ir enriched intermediate representation to emit from
    */
   public SchemaEmitter(SchemaIR ir) {
      this.ir = ir;
      PrimitivePropertyEmitter primitiveEmitter = new PrimitivePropertyEmitter();
      emitterRegistry.put(SchemaIR.PropertyType.PRIMITIVE, primitiveEmitter);
      emitterRegistry.put(SchemaIR.PropertyType.ENUM, primitiveEmitter);
      emitterRegistry.put(SchemaIR.PropertyType.NESTED_OBJECT, new NestedObjectPropertyEmitter());
      emitterRegistry.put(SchemaIR.PropertyType.MAP_VALUE, new MapValuePropertyEmitter());
      emitterRegistry.put(
            SchemaIR.PropertyType.COLLECTION_ELEMENT, new CollectionElementPropertyEmitter());
      emitterRegistry.put(
            SchemaIR.PropertyType.MAP_COLLECTION_VALUE, new MapCollectionValuePropertyEmitter());
   }

   /**
    * Emit a complete JSON Schema (Draft 7) document from the enriched IR graph.
    *
    * <p>Traverses the IR starting from ConfigurationImpl as root, extracting classes with usageCount
    * &gt; 1 into $defs with $ref pointers. Enrichments and polymorphism (allOf/oneOf) are resolved
    * during emission.
    *
    * @return Complete JSON Schema as a nested Map structure, ready for serialization
    */
   public Map<String, Object> emitSchema() {
      Map<String, Object> schema = new LinkedHashMap<>();
      schema.put("$schema", "http://json-schema.org/draft-07/schema#");
      schema.put("title", "Apache Artemis Broker Configuration");
      schema.put("type", new SchemaType(SchemaType.Kind.OBJECT).toSchemaValue());

      // Build $defs: classes used in multiple places get their own definition,
      // referenced via $ref elsewhere.
      Map<String, Object> defs = new LinkedHashMap<>();
      for (SchemaIR.ClassNode node : ir.getAllNodes()) {
         if (!ir.shouldExtract(node.getClassName())) {
            continue;
         }

         String contextPath =
               node.getClassMetadata().getContextPath() != null
                     ? node.getClassMetadata().getContextPath()
                     : ir.getBestUsageContext(node.getClassName());
         defs.put(node.getSimpleName(), emitClassSchema(node, true, Location.of(contextPath)));
      }

      // Root: ConfigurationImpl's properties become the top-level schema properties.
      SchemaIR.ClassNode rootNode = ir.getNode(ConfigurationImpl.class.getName());
      Map<String, Object> rootSchema = emitClassSchema(rootNode, false, Location.root());

      @SuppressWarnings("unchecked")
      Map<String, Object> properties = (Map<String, Object>) rootSchema.get("properties");

      if (properties == null || properties.isEmpty()) {
         throw new IllegalStateException(
               "ConfigurationImpl produced no properties — IR is empty or broken");
      }

      schema.put("properties", properties);
      schema.put("$defs", defs);

      return schema;
   }

   /**
    * Core class-level emission with two modes:
    *
    * <ul>
    *   <li><b>allOf (subclass)</b> — when emitting a $def that has a superclass, produces an {@code
    *       allOf} containing a {@code $ref} to the base type plus an inline object with only the
    *       derived properties, so inheritance composes cleanly.
    *   <li><b>flat object</b> — otherwise emits a plain {@code type: "object"} with all properties
    *       inlined, used for the root schema and leaf classes.
    * </ul>
    *
    * @param node the IR class node to emit
    * @param isDefEmission true when emitting into {@code $defs} (enables allOf inheritance)
    * @param location typed path for enrichment lookups
    * @return the emitted JSON Schema fragment
    */
   @Override
   public Map<String, Object> emitClassSchema(
         SchemaIR.ClassNode node, boolean isDefEmission, Location location) {
      Map<String, Object> schema = new LinkedHashMap<>();

      boolean isSubclass = isDefEmission && node.getSuperclass() != null;

      if (isSubclass) {
         // Subclass in $defs → use allOf pattern:
         //   allOf: [ {$ref: base}, {type: object, properties: {only derived props}} ]
         // This avoids duplicating base class properties in every subclass definition.
         // Example: AMQPMirrorBrokerConnectionElement allOf: [$ref AMQPBrokerConnectionElement,
         // {mirror-specific props}]
         List<Map<String, Object>> allOfSchemas = new ArrayList<>();

         // 1. Reference to base class
         SchemaIR.ClassNode superNode = ir.getNode(node.getSuperclass());
         Map<String, Object> baseRef = new LinkedHashMap<>();
         baseRef.put("$ref", "#/$defs/" + superNode.getSimpleName());
         allOfSchemas.add(baseRef);

         // 2. Only properties that this subclass adds (not inherited from base)
         Map<String, Object> derivedProps = new LinkedHashMap<>();

         for (SchemaIR.PropertyNode prop : node.getProperties().values()) {
            if (!superNode.getProperties().containsKey(prop.getName())) {
               derivedProps.put(prop.getName(), emitPropertySchema(prop, location));
            }
         }

         if (!derivedProps.isEmpty()) {
            Map<String, Object> derivedSchema = new LinkedHashMap<>();
            derivedSchema.put("type", new SchemaType(SchemaType.Kind.OBJECT).toSchemaValue());
            derivedSchema.put("properties", derivedProps);
            allOfSchemas.add(derivedSchema);
         }

         schema.put("allOf", allOfSchemas);
      } else {
         // Regular class (no inheritance) → flat object with all its properties.
         schema.put("type", new SchemaType(SchemaType.Kind.OBJECT).toSchemaValue());

         Map<String, Object> properties = new LinkedHashMap<>();
         for (SchemaIR.PropertyNode prop : node.getProperties().values()) {
            properties.put(prop.getName(), emitPropertySchema(prop, location));
         }
         schema.put("properties", properties);

         if (node.getRequired() != null && !node.getRequired().isEmpty()) {
            schema.put("required", node.getRequired());
         }
      }

      // Class-level metadata (x-java-class, x-factory-variant, etc.)
      // Injected as sibling keys — valid in Draft 7 for annotation purposes.
      node.getClassMetadata().emitInto(schema);

      return schema;
   }

   /**
    * Dispatches a single property to the strategy emitter registered for its {@link
    * SchemaIR.PropertyType}, keeping this class free of type-specific logic.
    *
    * @param prop the property IR node
    * @param location parent location (the property computes its full location from this)
    * @return the emitted property schema fragment
    */
   private Map<String, Object> emitPropertySchema(SchemaIR.PropertyNode prop, Location location) {
      if (prop == null) {
         throw new IllegalArgumentException("PropertyNode must not be null for location: " + location);
      }
      PropertyEmitter emitter = emitterRegistry.get(prop.getPropertyType());
      if (emitter == null) {
         throw new IllegalStateException(
               "No emitter registered for property type: " + prop.getPropertyType());
      }
      return emitter.emit(prop, ir, location.child(prop), this);
   }

}
