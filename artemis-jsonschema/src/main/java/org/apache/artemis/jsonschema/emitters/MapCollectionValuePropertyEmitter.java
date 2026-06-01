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
import org.apache.artemis.jsonschema.ir.SchemaType;

/**
 * Emits JSON Schema for map properties whose value type is one or more nested containers
 * (Collection, Map) wrapping a complex leaf class.
 *
 * <p>Each container layer contributes one level of {@code additionalProperties} nesting,
 * matching the broker.properties flat-key convention where each container key becomes a
 * path segment: {@code prop.<k1>.<k2>...<kN>.<field>=value}.
 *
 * <p>Example: {@code Map<String, Set<Role>>} with nesting depth 1 emits:
 * <pre>
 * {
 *   "type": "object",
 *   "additionalProperties": {
 *     "type": "object",
 *     "additionalProperties": {
 *       "type": "object",
 *       "properties": { ...Role fields... }
 *     }
 *   }
 * }
 * </pre>
 */
public class MapCollectionValuePropertyEmitter extends PropertyEmitter {

   @Override
   public Map<String, Object> emit(
         SchemaIR.PropertyNode prop, SchemaIR ir, Location location, EmissionContext context) {
      Map<String, Object> schema = new LinkedHashMap<>();

      schema.putAll(prop.getSchema());

      String targetClass = prop.getTargetClassName();
      if (targetClass != null) {
         SchemaIR.ClassNode targetNode = ir.getOrCreateNode(targetClass);

         int depth = prop.getCollectionNestingDepth();

         Location leafLocation = location.wildcard();
         for (int i = 0; i < depth; i++) {
            leafLocation = leafLocation.wildcard();
         }
         Map<String, Object> leafSchema = context.emitClassSchema(targetNode, false, leafLocation);

         Map<String, Object> wrapped = leafSchema;
         for (int i = 0; i < depth; i++) {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("type", new SchemaType(SchemaType.Kind.OBJECT).toSchemaValue());
            wrapper.put("additionalProperties", wrapped);
            wrapped = wrapper;
         }

         schema.put("additionalProperties", wrapped);
      }

      applyEnrichments(schema, ir, location);
      applyFallbackDescriptions(schema, location);
      applyNestedEnrichments(schema, ir, location);

      return schema;
   }
}
