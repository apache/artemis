/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.api.core;

import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.activemq.artemis.utils.JsonLoader;
import org.junit.Assert;
import org.junit.Test;

public class JsonUtilTest {

   @Test
   public void testAddToObject() {
      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();

      JsonUtil.addToObject("not-null", "not-null", jsonObjectBuilder);
      JsonUtil.addToObject("null", null, jsonObjectBuilder);

      JsonObject jsonObject = jsonObjectBuilder.build();

      Assert.assertTrue(jsonObject.containsKey("not-null"));
      Assert.assertTrue(jsonObject.containsKey("null"));
      Assert.assertEquals(2, jsonObject.size());
   }

   @Test
   public void testAddToArray() {
      JsonArrayBuilder jsonArrayBuilder = JsonLoader.createArrayBuilder();

      JsonUtil.addToArray("hello", jsonArrayBuilder);
      JsonUtil.addToArray(null, jsonArrayBuilder);

      JsonArray jsonArray = jsonArrayBuilder.build();

      Assert.assertEquals(2, jsonArray.size());
   }

   @Test
   public void testAddByteArrayToJsonObject() {
      JsonObjectBuilder jsonObjectBuilder = JsonLoader.createObjectBuilder();
      byte[] bytes = {0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f};

      JsonUtil.addToObject("not-null", "not-null", jsonObjectBuilder);
      JsonUtil.addToObject("byteArray", bytes, jsonObjectBuilder);
      JsonUtil.addToObject("null", null, jsonObjectBuilder);

      JsonObject jsonObject = jsonObjectBuilder.build();

      Assert.assertTrue(jsonObject.containsKey("byteArray"));
      Assert.assertEquals(6, jsonObject.getJsonArray("byteArray").size());
   }

   @Test
   public void testAddByteArrayToJsonArray() {
      JsonArrayBuilder jsonArrayBuilder = JsonLoader.createArrayBuilder();
      byte[] bytes = {0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f};

      JsonUtil.addToArray(bytes, jsonArrayBuilder);

      JsonArray jsonArray = jsonArrayBuilder.build();

      Assert.assertEquals(1, jsonArray.size());
   }

   @Test
   public void testTruncateUsingStringWithValueSizeLimit() {
      String prefix = "12345";
      int valueSizeLimit = prefix.length();
      String remaining = "remaining";

      String truncated = (String) JsonUtil.truncate(prefix + remaining, valueSizeLimit);

      String expected = prefix + ", + " + String.valueOf(remaining.length()) + " more";
      Assert.assertEquals(expected, truncated);
   }

   @Test
   public void testTruncateUsingStringWithoutValueSizeLimit() {
      String input = "testTruncateUsingStringWithoutValueSizeLimit";
      String notTruncated = (String) JsonUtil.truncate(input, -1);

      Assert.assertEquals(input, notTruncated);
   }

   @Test
   public void testTruncateStringWithValueSizeLimit() {
      String prefix = "12345";
      int valueSizeLimit = prefix.length();
      String remaining = "remaining";

      String truncated = JsonUtil.truncateString(prefix + remaining, valueSizeLimit);

      String expected = prefix + ", + " + String.valueOf(remaining.length()) + " more";
      Assert.assertEquals(expected, truncated);
   }

   @Test
   public void testTruncateStringWithoutValueSizeLimit() {
      String input = "testTruncateStringWithoutValueSizeLimit";
      String notTruncated = JsonUtil.truncateString(input, -1);

      Assert.assertEquals(input, notTruncated);
   }
}
