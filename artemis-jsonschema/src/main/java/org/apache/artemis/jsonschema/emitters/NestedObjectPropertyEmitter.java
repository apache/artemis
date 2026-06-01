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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.artemis.jsonschema.ir.Location;
import org.apache.artemis.jsonschema.ir.SchemaIR;

/**
 * Emits JSON Schema for nested object properties.
 *
 * <p>Handles PropertyType.NESTED_OBJECT by either:
 *
 * <ul>
 *   <li>Emitting a $ref to $defs if the class is used multiple times
 *   <li>Inlining the class schema if it's used only once
 * </ul>
 */
public class NestedObjectPropertyEmitter extends PropertyEmitter {

   /** {@inheritDoc} */
   @Override
   public Map<String, Object> emit(
         SchemaIR.PropertyNode prop, SchemaIR ir, Location location, EmissionContext context) {
      Map<String, Object> schema = new LinkedHashMap<>();

      String targetClass = prop.getTargetClassName();
      if (targetClass != null && ir.shouldExtract(targetClass)) {
         // Use $ref for multi-use classes
         SchemaIR.ClassNode targetNode = ir.getOrCreateNode(targetClass);
         schema.put("$ref", "#/$defs/" + targetNode.getSimpleName());

         // Copy metadata (x-access, x-sources, etc.) but NOT schema structure
         for (Map.Entry<String, Object> entry : prop.getSchema().entrySet()) {
            if (entry.getKey().startsWith("x-")) {
               schema.put(entry.getKey(), entry.getValue());
            }
         }
      } else if (targetClass != null) {
         // Inline for single-use classes
         SchemaIR.ClassNode targetNode = ir.getOrCreateNode(targetClass);
         Map<String, Object> inlineSchema = context.emitClassSchema(targetNode, false, location);
         schema.putAll(inlineSchema);

         // Merge property-level metadata
         for (Map.Entry<String, Object> entry : prop.getSchema().entrySet()) {
            if (!schema.containsKey(entry.getKey())) {
               schema.put(entry.getKey(), entry.getValue());
            }
         }
      }

      // Apply enrichments
      applyEnrichments(schema, ir, location);

      // Apply fallback descriptions
      applyFallbackDescriptions(schema, location);

      return schema;
   }
}
