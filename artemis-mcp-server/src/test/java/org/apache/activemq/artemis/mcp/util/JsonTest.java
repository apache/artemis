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
package org.apache.activemq.artemis.mcp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class JsonTest {

   @Test
   public void writesPrimitives() {
      assertEquals("null", Json.write(null));
      assertEquals("42", Json.write(42));
      assertEquals("true", Json.write(true));
      assertEquals("\"hi\"", Json.write("hi"));
   }

   @Test
   public void escapesStrings() {
      assertEquals("\"a\\\"b\\n\"", Json.write("a\"b\n"));
   }

   @Test
   public void writesNestedStructures() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("name", "q1");
      map.put("count", 3);
      map.put("tags", List.of("a", "b"));
      assertEquals("{\"name\":\"q1\",\"count\":3,\"tags\":[\"a\",\"b\"]}", Json.write(map));
   }

   @Test
   public void writesArrays() {
      assertEquals("[\"x\",\"y\"]", Json.write(new Object[] {"x", "y"}));
   }
}
