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

import java.util.Map;

/**
 * Typed metadata for a ClassNode in the IR. Mirrors PropertyMetadata but for class-level
 * annotations that get emitted as sibling keys in the JSON Schema output.
 */
public class ClassMetadata {

   private String javaClass;
   private String description;
   private boolean factoryVariant;
   private String contextPath;

   public String getJavaClass() {
      return javaClass;
   }

   public void setJavaClass(String javaClass) {
      this.javaClass = javaClass;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public boolean isFactoryVariant() {
      return factoryVariant;
   }

   public void setFactoryVariant(boolean factoryVariant) {
      this.factoryVariant = factoryVariant;
   }

   public String getContextPath() {
      return contextPath;
   }

   public void setContextPath(String contextPath) {
      this.contextPath = contextPath;
   }

   /**
    * Emit all non-null fields into a schema map. Called by SchemaEmitter to inject class-level
    * annotations into the output.
    *
    * @param schema mutable map to populate with class-level annotation keys
    */
   public void emitInto(Map<String, Object> schema) {
      if (javaClass != null) {
         schema.put("x-java-class", javaClass);
      }
      if (description != null) {
         schema.put("description", description);
      }
   }
}
