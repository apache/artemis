/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.api.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;

import org.apache.activemq.artemis.utils.JsonLoader;
import org.junit.jupiter.api.Test;

public class JsonUtilTest {

   @Test
   public void testAddToObject() {
      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();

      JsonUtil.addToObject("not-null", "not-null", jsonObjectBuilder);
      JsonUtil.addToObject("null", null, jsonObjectBuilder);

      JsonObject jsonObject = jsonObjectBuilder.build();

      assertTrue(jsonObject.containsKey("not-null"));
      assertTrue(jsonObject.containsKey("null"));
      assertEquals(2, jsonObject.size());
   }

   @Test
   public void testAddToArray() {
      JsonArrayBuilder jsonArrayBuilder = JsonLoader.createArrayBuilder();

      JsonUtil.addToArray("hello", jsonArrayBuilder);
      JsonUtil.addToArray(null, jsonArrayBuilder);

      JsonArray jsonArray = jsonArrayBuilder.build();

      assertEquals(2, jsonArray.size());
   }

   @Test
   public void testAddByteArrayToJsonObject() {
      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();
      byte[] bytes = {0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f};

      JsonUtil.addToObject("not-null", "not-null", jsonObjectBuilder);
      JsonUtil.addToObject("byteArray", bytes, jsonObjectBuilder);
      JsonUtil.addToObject("null", null, jsonObjectBuilder);

      JsonObject jsonObject = jsonObjectBuilder.build();

      assertTrue(jsonObject.containsKey("byteArray"));
      assertEquals(6, jsonObject.getJsonArray("byteArray").size());
   }

   @Test
   public void testAddByteArrayToJsonArray() {
      JsonArrayBuilder jsonArrayBuilder = JsonLoader.createArrayBuilder();
      byte[] bytes = {0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f};

      JsonUtil.addToArray(bytes, jsonArrayBuilder);

      JsonArray jsonArray = jsonArrayBuilder.build();

      assertEquals(1, jsonArray.size());
   }

   @Test
   public void testTruncateUsingStringWithValueSizeLimit() {
      String prefix = "12345";
      int valueSizeLimit = prefix.length();
      String remaining = "remaining";

      String truncated = (String) JsonUtil.truncate(prefix + remaining, valueSizeLimit);

      String expected = prefix + ", + " + String.valueOf(remaining.length()) + " more";
      assertEquals(expected, truncated);
   }

   @Test
   public void testTruncateUsingStringWithoutValueSizeLimit() {
      String input = "testTruncateUsingStringWithoutValueSizeLimit";
      String notTruncated = (String) JsonUtil.truncate(input, -1);

      assertEquals(input, notTruncated);
   }

   @Test
   public void testTruncateWithoutNullValue() {
      Object result = JsonUtil.truncate(null, -1);

      assertEquals("", result);
   }

   @Test
   public void testTruncateStringWithValueSizeLimit() {
      String prefix = "12345";
      int valueSizeLimit = prefix.length();
      String remaining = "remaining";

      String truncated = JsonUtil.truncateString(prefix + remaining, valueSizeLimit);

      String expected = prefix + ", + " + String.valueOf(remaining.length()) + " more";
      assertEquals(expected, truncated);
   }

   @Test
   public void testTruncateStringWithoutValueSizeLimit() {
      String input = "testTruncateStringWithoutValueSizeLimit";
      String notTruncated = JsonUtil.truncateString(input, -1);

      assertEquals(input, notTruncated);
   }

   @Test
   public void testMergeEqual() {

      final byte[] bytesA = {0x0a, 0x0b};
      final byte[] bytesB = {0x1a, 0x1b};

      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("a", "a", jsonObjectBuilder);
      JsonUtil.addToObject("byteArray", bytesA, jsonObjectBuilder);
      JsonUtil.addToObject("null", null, jsonObjectBuilder);

      JsonObject sourceOne = jsonObjectBuilder.build();

      JsonObjectBuilder jsonTargetObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("a", "a", jsonTargetObjectBuilder);
      JsonUtil.addToObject("byteArray", bytesB, jsonTargetObjectBuilder);
      JsonUtil.addToObject("null", null, jsonTargetObjectBuilder);

      JsonObject sourceTwo = jsonTargetObjectBuilder.build();

      JsonObjectBuilder jsonMergedObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("a", "a", jsonMergedObjectBuilder);
      // update wins
      JsonUtil.addToObject("byteArray", bytesB, jsonMergedObjectBuilder);
      JsonUtil.addToObject("null", null, jsonMergedObjectBuilder);

      JsonObject mergedExpected = jsonMergedObjectBuilder.build();

      assertEquals(mergedExpected, JsonUtil.mergeAndUpdate(sourceOne, sourceTwo));
   }

   @Test
   public void testMergeArray() {

      final byte[] bytesA = {0x0a, 0x0b};
      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("byteArray", bytesA, jsonObjectBuilder);

      JsonObject sourceOne = jsonObjectBuilder.build();

      JsonObjectBuilder jsonTargetObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("byteArray", bytesA, jsonTargetObjectBuilder);

      JsonObject sourceTwo = jsonTargetObjectBuilder.build();

      JsonObjectBuilder jsonMergedObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("byteArray", bytesA, jsonMergedObjectBuilder);

      JsonObject mergedExpected = jsonMergedObjectBuilder.build();

      assertEquals(mergedExpected, JsonUtil.mergeAndUpdate(sourceOne, sourceTwo));

   }

   @Test
   public void testMergeDuplicate() {

      // merge duplicate attribute value, two wins
      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("dup", "a", jsonObjectBuilder);
      JsonObject sourceOne = jsonObjectBuilder.build();

      JsonObjectBuilder jsonTargetObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("dup", "b", jsonTargetObjectBuilder);
      JsonObject sourceTwo = jsonTargetObjectBuilder.build();

      assertEquals(sourceTwo, JsonUtil.mergeAndUpdate(sourceOne, sourceTwo));
   }

   @Test
   public void testMergeEmpty() {

      // merge into empty
      JsonObject sourceOne = JsonLoader.createObjectBuilder().build();

      JsonObjectBuilder jsonTargetObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("dup", "b", jsonTargetObjectBuilder);
      JsonObject sourceTwo = jsonTargetObjectBuilder.build();

      assertEquals(sourceTwo, JsonUtil.mergeAndUpdate(sourceOne, sourceTwo));
   }

   @Test
   public void testBuilderWithValueAtPath() {

      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();
      JsonUtil.addToObject("x", "y", jsonObjectBuilder);
      JsonObject target = jsonObjectBuilder.build();

      JsonObjectBuilder nested = JsonUtil.objectBuilderWithValueAtPath("a/b/c", target);
      JsonObject inserted = nested.build();
      assertTrue(inserted.containsKey("a"));

      assertEquals(target, inserted.getJsonObject("a").getJsonObject("b").getJsonObject("c"));

      nested = JsonUtil.objectBuilderWithValueAtPath("c", target);
      inserted = nested.build();
      assertTrue(inserted.containsKey("c"));
      assertEquals(target, inserted.getJsonObject("c"));
   }

   @Test
   public void testArrayToString() {
      final String keyName = "test";

      // test wrong type (i.e. not an array)
      assertTrue(JsonUtil.arrayToString(JsonLoader.createObjectBuilder().add(keyName, 0L).build(), keyName).isEmpty());

      // test empty array
      assertTrue(JsonUtil.arrayToString(JsonLoader.createObjectBuilder().build(), keyName).isEmpty());

      // test array with 2 elements
      assertEquals("a, b", JsonUtil.arrayToString(JsonLoader.createObjectBuilder()
                                                     .add(keyName, JsonLoader.createArrayBuilder()
                                                        .add("a")
                                                        .add("b")
                                                        .build())
                                                     .build(), keyName));
   }
}
