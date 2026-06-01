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

package org.apache.artemis.jsonschema.annotation;

import java.lang.annotation.*;

/**
 * Marks a configuration property for JSON Schema extraction.
 *
 * <p>When present on a getter/setter or field, the schema generator will:
 *
 * <ul>
 *   <li>Use the annotation's description instead of relying on JavaDoc parsing
 *   <li>Apply explicit type, constraints, and deprecation metadata
 *   <li>Override any heuristic-based extraction for this property
 * </ul>
 *
 * <p>This annotation is optional. Properties are still discovered via reflection even without it.
 * The annotation provides an explicit, compiler-checked way to document properties that survives
 * refactoring better than JavaDoc conventions.
 *
 * <p>Usage:
 *
 * <pre>
 * &#64;ConfigProperty(description = "Maximum disk usage percentage before the broker blocks producers")
 * public int getMaxDiskUsage() { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigProperty {

   /**
    * Human-readable description of this configuration property. If empty, falls back to JavaDoc
    * extraction.
    */
   String description() default "";

   /** Whether this property can be changed without broker restart. */
   boolean hotReloadable() default false;

   /** Whether this property is deprecated. */
   boolean deprecated() default false;

   /** Replacement property path if deprecated (e.g., "addressSettings.*.maxSizeBytes"). */
   String replacedBy() default "";

   /** Minimum value constraint (for numeric types). Use Long.MIN_VALUE for "no minimum". */
   long min() default Long.MIN_VALUE;

   /** Maximum value constraint (for numeric types). Use Long.MAX_VALUE for "no maximum". */
   long max() default Long.MAX_VALUE;
}
