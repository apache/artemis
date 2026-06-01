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
 * Provides context and helper methods for property emitters.
 *
 * <p>This interface allows emitters to:
 *
 * <ul>
 *   <li>Recursively emit class schemas (for nested objects)
 *   <li>Apply enrichments from IR
 *   <li>Add fallback descriptions for common property patterns
 * </ul>
 */
public interface EmissionContext {

   /**
    * Emit schema for a class node (for nested objects).
    *
    * @param node ClassNode to emit
    * @param isDefEmission True if emitting to $defs, false if inline
    * @param location typed path for enrichment lookup
    * @return JSON Schema map for this class
    */
   Map<String, Object> emitClassSchema(
         SchemaIR.ClassNode node, boolean isDefEmission, Location location);
}
