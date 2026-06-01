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
 * Emits JSON Schema for primitive and simple types (string, integer, boolean, etc.).
 *
 * <p>Handles PropertyType.PRIMITIVE and PropertyType.SIMPLE by copying the schema from the
 * PropertyNode (which was populated during IR generation) and applying enrichments.
 */
public class PrimitivePropertyEmitter extends PropertyEmitter {

   /** {@inheritDoc} */
   @Override
   public Map<String, Object> emit(
         SchemaIR.PropertyNode prop, SchemaIR ir, Location location, EmissionContext context) {
      Map<String, Object> schema = new LinkedHashMap<>();

      schema.putAll(prop.getSchema());
      applyEnrichments(schema, ir, location);
      applyFallbackDescriptions(schema, location);
      applyNestedEnrichments(schema, ir, location);

      return schema;
   }
}
