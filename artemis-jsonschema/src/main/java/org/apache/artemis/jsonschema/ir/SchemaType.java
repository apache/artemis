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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JSON Schema type — either a single type or a union of types.
 *
 * <p>Emitted as a bare string when single ({@code "integer"}), or as a list when multiple ({@code
 * ["integer", "string"]}).
 */
public final class SchemaType {

   public enum Kind {
      STRING("string"),
      INTEGER("integer"),
      NUMBER("number"),
      BOOLEAN("boolean"),
      OBJECT("object"),
      ARRAY("array");

      private final String schemaName;

      Kind(String schemaName) {
         this.schemaName = schemaName;
      }

      public String schemaName() {
         return schemaName;
      }

      public static Kind fromSchema(String name) {
         for (Kind k : values()) {
            if (k.schemaName.equals(name)) {
               return k;
            }
         }
         throw new IllegalArgumentException("Unknown JSON Schema type: " + name);
      }
   }

   private final List<Kind> types;

   public SchemaType(Kind kind) {
      this.types = List.of(kind);
   }

   public static SchemaType of(Kind... kinds) {
      return new SchemaType(kinds);
   }

   private SchemaType(Kind[] kinds) {
      this.types = List.of(kinds);
   }

   /** The primary type (first in the list). */
   public Kind primary() {
      return types.get(0);
   }

   /** All types in the union. */
   public List<Kind> all() {
      return types;
   }

   public boolean isUnion() {
      return types.size() > 1;
   }

   public boolean isString() {
      return !isUnion() && primary() == Kind.STRING;
   }

   public boolean isInteger() {
      return !isUnion() && primary() == Kind.INTEGER;
   }

   public boolean isNumber() {
      return !isUnion() && primary() == Kind.NUMBER;
   }

   public boolean isBoolean() {
      return !isUnion() && primary() == Kind.BOOLEAN;
   }

   public boolean isObject() {
      return !isUnion() && primary() == Kind.OBJECT;
   }

   public boolean isArray() {
      return !isUnion() && primary() == Kind.ARRAY;
   }

   /**
    * Returns the value to put in the JSON Schema "type" field: a bare String for single types, a
    * List for union types.
    */
   public Object toSchemaValue() {
      if (types.size() == 1) {
         return types.get(0).schemaName();
      }
      List<String> names = new ArrayList<>(types.size());
      for (Kind k : types) {
         names.add(k.schemaName());
      }
      return names;
   }

   @Override
   public String toString() {
      if (types.size() == 1) {
         return types.get(0).schemaName();
      }
      List<String> names = new ArrayList<>(types.size());
      for (Kind k : types) {
         names.add(k.schemaName());
      }
      return names.toString();
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SchemaType that)) return false;
      return types.equals(that.types);
   }

   @Override
   public int hashCode() {
      return Objects.hash(types);
   }
}
