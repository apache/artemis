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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.artemis.jsonschema.ir.Location;
import org.apache.artemis.jsonschema.ir.SchemaIR;

/**
 * Emits JSON Schema for Collection element properties.
 *
 * <p>Handles PropertyType.COLLECTION_ELEMENT by emitting:
 *
 * <pre>
 * {
 *   "type": "object",
 *   "additionalProperties": { ... element schema ... }
 * }
 * </pre>
 *
 * <p>The element schema is ALWAYS inlined (never $ref) because Collections represent
 * broker.properties format like {@code propName.0.nestedProp=value}.
 *
 * <p>Supports two types of polymorphism:
 *
 * <ul>
 *   <li>Factory-based (property-level): Uses factory variants to emit oneOf pattern
 *   <li>Class-based (class-level): Uses subclass discovery to emit oneOf with $refs
 * </ul>
 */
public class CollectionElementPropertyEmitter extends PropertyEmitter {

   /** {@inheritDoc} */
   @Override
   public Map<String, Object> emit(
         SchemaIR.PropertyNode prop, SchemaIR ir, Location location, EmissionContext context) {
      Map<String, Object> schema = new LinkedHashMap<>();

      // Copy type, x-access, etc.
      schema.putAll(prop.getSchema());

      String targetClass = prop.getTargetClassName();
      if (targetClass != null) {
         SchemaIR.ClassNode targetNode = ir.getOrCreateNode(targetClass);

         // Check for factory-based polymorphism FIRST (property-level)
         if (prop.hasFactoryVariants()) {
            // Emit oneOf with factory variants (inline, not $ref)
            schema.put("additionalProperties", emitFactoryOneOf(prop));
         }
         // Check if polymorphic via subclasses (class-level)
         else if (targetNode.isPolymorphic()) {
            // Emit oneOf with $refs to all subtypes (subclasses will be in $defs with allOf)
            List<Map<String, Object>> oneOfSchemas = new ArrayList<>();

            for (String subclassName : targetNode.getSubclasses()) {
               SchemaIR.ClassNode subNode = ir.getOrCreateNode(subclassName);
               Map<String, Object> subRef = new LinkedHashMap<>();
               subRef.put("$ref", "#/$defs/" + subNode.getSimpleName());
               oneOfSchemas.add(subRef);
            }

            Map<String, Object> polySchema = new LinkedHashMap<>();
            polySchema.put("oneOf", oneOfSchemas);
            schema.put("additionalProperties", polySchema);
         } else {
            Map<String, Object> elementSchema =
                  context.emitClassSchema(targetNode, false, location.wildcard());
            schema.put("additionalProperties", elementSchema);
         }
      }

      // Apply enrichments
      applyEnrichments(schema, ir, location);

      // Apply fallback descriptions
      applyFallbackDescriptions(schema, location);

      // Apply enrichments to nested properties (factory params)
      applyNestedEnrichments(schema, ir, location);

      return schema;
   }
}
