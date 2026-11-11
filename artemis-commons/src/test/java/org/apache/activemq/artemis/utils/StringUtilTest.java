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
package org.apache.activemq.artemis.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class StringUtilTest {

   @Test
   public void testJoinStringList() throws Exception {
      List<String> strList = new ArrayList<>();
      strList.add("a");
      strList.add("bc");
      strList.add("def");
      String result = StringUtil.joinStringList(strList, ",");
      assertEquals("a,bc,def", result);

      List<String> newList = StringUtil.splitStringList(result, ",");
      assertEquals(strList.size(), newList.size());
      String result2 = StringUtil.joinStringList(newList, ",");
      assertEquals(result, result2);
   }

   @Test
   public void testSplitStringList() throws Exception {
      String listStr = "white,blue,yellow,green";
      List<String> result = StringUtil.splitStringList(listStr, ",");
      assertEquals(4, result.size());
      assertEquals("white", result.get(0));
      assertEquals("blue", result.get(1));
      assertEquals("yellow", result.get(2));
      assertEquals("green", result.get(3));

      String result2 = StringUtil.joinStringList(result, ",");
      assertEquals(listStr, result2);
   }

   @Test
   public void testSplitStringListWithSpaces() throws Exception {
      String listStr = "white, blue, yellow, green";
      List<String> result = StringUtil.splitStringList(listStr, ",");
      assertEquals(4, result.size());
      assertEquals("white", result.get(0));
      assertEquals("blue", result.get(1));
      assertEquals("yellow", result.get(2));
      assertEquals("green", result.get(3));
   }

   @Test
   public void testPascalToKebab() throws Exception {
      assertEquals("pascal-to-kebab", StringUtil.convertPascalCaseToKebabCase("PascalToKebab"));
   }
}
