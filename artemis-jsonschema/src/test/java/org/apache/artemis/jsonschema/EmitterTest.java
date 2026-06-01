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

package org.apache.artemis.jsonschema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.apache.artemis.jsonschema.emitters.*;
import org.apache.artemis.jsonschema.ir.Location;
import org.apache.artemis.jsonschema.ir.SchemaIR;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
public class EmitterTest {

   private static final EmissionContext NOOP_CONTEXT =
         new EmissionContext() {
            @Override
            public Map<String, Object> emitClassSchema(
                  SchemaIR.ClassNode node, boolean isDefEmission, Location location) {
               Map<String, Object> stub = new LinkedHashMap<>();
               stub.put("type", "object");
               stub.put("x-stub", node.getSimpleName());
               return stub;
            }
         };

   @Test
   public void primitiveEmitterCopiesTypeAndDefault() {
      SchemaIR ir = new SchemaIR();
      SchemaIR.PropertyNode prop = new SchemaIR.PropertyNode("name");
      prop.setSchemaField("type", "string");
      prop.setSchemaField("default", "hello");

      Map<String, Object> result =
            new PrimitivePropertyEmitter().emit(prop, ir, Location.of("name"), NOOP_CONTEXT);

      assertEquals("string", result.get("type"));
      assertEquals("hello", result.get("default"));
   }

   @Test
   public void primitiveEmitterPreservesEnumArray() {
      SchemaIR ir = new SchemaIR();
      SchemaIR.PropertyNode prop = new SchemaIR.PropertyNode("policy");
      prop.setSchemaField("type", "string");
      prop.setSchemaField("enum", List.of("FULL", "PAGE", "OFF"));

      Map<String, Object> result =
            new PrimitivePropertyEmitter().emit(prop, ir, Location.of("policy"), NOOP_CONTEXT);

      assertTrue(result.containsKey("enum"));
      List<String> enumValues = (List<String>) result.get("enum");
      assertEquals(3, enumValues.size());
      assertTrue(enumValues.contains("FULL"));
   }

   @Test
   public void nestedObjectEmitterEmitsRefForMultiUseClass() {
      SchemaIR ir = new SchemaIR();
      String className = "com.example.SharedConfig";
      ir.recordUsage(className, "path.a");
      ir.recordUsage(className, "path.b");

      SchemaIR.PropertyNode prop = new SchemaIR.PropertyNode("config");
      prop.setTargetClassName(className);

      Map<String, Object> result =
            new NestedObjectPropertyEmitter().emit(prop, ir, Location.of("config"), NOOP_CONTEXT);

      assertEquals("#/$defs/SharedConfig", result.get("$ref"));
      assertFalse(result.containsKey("type"));
   }

   @Test
   public void nestedObjectEmitterInlinesForSingleUseClass() {
      SchemaIR ir = new SchemaIR();
      String className = "com.example.UniqueConfig";
      ir.recordUsage(className, "path.only");

      SchemaIR.PropertyNode prop = new SchemaIR.PropertyNode("unique");
      prop.setTargetClassName(className);

      Map<String, Object> result =
            new NestedObjectPropertyEmitter().emit(prop, ir, Location.of("unique"), NOOP_CONTEXT);

      assertNull(result.get("$ref"));
      assertEquals("object", result.get("type"));
      assertEquals("UniqueConfig", result.get("x-stub"));
   }

   @Test
   public void mapValueEmitterAddsAdditionalProperties() {
      SchemaIR ir = new SchemaIR();
      String className = "com.example.MapTarget";
      ir.recordUsage(className, "map.only");

      SchemaIR.PropertyNode prop = new SchemaIR.PropertyNode("entries");
      prop.setTargetClassName(className);
      prop.setPropertyType(SchemaIR.PropertyType.MAP_VALUE);
      prop.setSchemaField("type", "object");

      Map<String, Object> result =
            new MapValuePropertyEmitter().emit(prop, ir, Location.of("entries"), NOOP_CONTEXT);

      assertTrue(result.containsKey("additionalProperties"));
      Map<String, Object> valueSchema = (Map<String, Object>) result.get("additionalProperties");
      assertEquals("object", valueSchema.get("type"));
   }

   @Test
   public void collectionElementEmitterEmitsOneOfForPolymorphicClass() {
      SchemaIR ir = new SchemaIR();
      String baseName = "com.example.BaseElement";
      SchemaIR.ClassNode baseNode = ir.getOrCreateNode(baseName);
      baseNode.addSubclass("com.example.SubA");
      baseNode.addSubclass("com.example.SubB");

      ir.getOrCreateNode("com.example.SubA");
      ir.getOrCreateNode("com.example.SubB");

      SchemaIR.PropertyNode prop = new SchemaIR.PropertyNode("elements");
      prop.setTargetClassName(baseName);
      prop.setPropertyType(SchemaIR.PropertyType.COLLECTION_ELEMENT);
      prop.setSchemaField("type", "object");

      Map<String, Object> result =
            new CollectionElementPropertyEmitter()
                  .emit(prop, ir, Location.of("elements"), NOOP_CONTEXT);

      assertTrue(result.containsKey("additionalProperties"));
      Map<String, Object> addProps = (Map<String, Object>) result.get("additionalProperties");
      assertTrue(addProps.containsKey("oneOf"));
      List<Map<String, Object>> oneOf = (List<Map<String, Object>>) addProps.get("oneOf");
      assertEquals(2, oneOf.size());
      assertEquals("#/$defs/SubA", oneOf.get(0).get("$ref"));
      assertEquals("#/$defs/SubB", oneOf.get(1).get("$ref"));
   }
}
