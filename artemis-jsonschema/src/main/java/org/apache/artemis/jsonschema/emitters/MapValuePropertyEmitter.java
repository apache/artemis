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
 * Emits JSON Schema for Map value properties.
 *
 * <p>Handles PropertyType.MAP_VALUE by emitting:
 *
 * <pre>
 * {
 *   "type": "object",
 *   "additionalProperties": { ... value schema ... }
 * }
 * </pre>
 *
 * <p>The value schema is ALWAYS inlined (never $ref) because Maps represent broker.properties
 * format like {@code propName.key1.nestedProp=value}.
 *
 * <p>If the value type has factory variants (e.g., TransportConfiguration), emits a oneOf pattern
 * with factory-specific schemas.
 */
public class MapValuePropertyEmitter extends PropertyEmitter {

   /** {@inheritDoc} */
   @Override
   public Map<String, Object> emit(
         SchemaIR.PropertyNode prop, SchemaIR ir, Location location, EmissionContext context) {
      Map<String, Object> schema = new LinkedHashMap<>();

      schema.putAll(prop.getSchema());

      String targetClass = prop.getTargetClassName();
      if (targetClass != null) {
         SchemaIR.ClassNode targetNode = ir.getOrCreateNode(targetClass);

         if (prop.hasFactoryVariants()) {
            schema.put("additionalProperties", emitFactoryOneOf(prop));
         } else if (targetNode.isPolymorphic()) {
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
            Map<String, Object> valueSchema =
                  context.emitClassSchema(targetNode, false, location.wildcard());
            schema.put("additionalProperties", valueSchema);
         }
      }

      applyEnrichments(schema, ir, location);
      applyFallbackDescriptions(schema, location);
      applyNestedEnrichments(schema, ir, location);

      return schema;
   }
}
