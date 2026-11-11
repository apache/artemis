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
package org.apache.activemq.artemis.core.management.impl.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ViewTest {

   @Test
   public void testPageSizeAndPageNumber() {
      ActiveMQAbstractView myView = new ActiveMQAbstractView() {
         @Override
         Object getField(Object o, String fieldName) {
            return null;
         }

         @Override
         public Class getClassT() {
            return null;
         }

         @Override
         public JsonObjectBuilder toJson(Object obj) {
            return null;
         }

         @Override
         public String getDefaultOrderColumn() {
            return "";
         }
      };

      List<Integer> list = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
         list.add(i);
      }

      myView.setCollection(list);

      // one or more inputs is -1
      assertEquals(list.size(), myView.getPagedResult(-1, -1).size());
      assertEquals(list.size(), myView.getPagedResult(123, -1).size());
      assertEquals(list.size(), myView.getPagedResult(-1, 123).size());

      // page 0 - not really valid but still "works"
      assertEquals(0, myView.getPagedResult(0, 123).size());


      assertEquals(123, myView.getPagedResult(1, 123).size());

      // last page
      assertEquals(100, myView.getPagedResult(10, 100).size());

      // past the last page
      assertEquals(0, myView.getPagedResult(11, 100).size());
   }

   protected String createJsonFilter(String fieldName, String operationName, String value) {
      Map<String, Object> filterMap = new HashMap<>();
      filterMap.put("field", fieldName);
      filterMap.put("operation", operationName);
      filterMap.put("value", value);
      JsonObject jsonFilterObject = JsonUtil.toJsonObject(filterMap);
      return jsonFilterObject.toString();
   }

   protected String createLegacyJsonFilter(String fieldName, String operationName, String value, String sortColumn, String sortOrder) {
      Map<String, Object> filterMap = new HashMap<>();
      filterMap.put("field", fieldName);
      filterMap.put("operation", operationName);
      filterMap.put("value", value);
      // in newer versions this is "sortField"
      filterMap.put("sortColumn", sortColumn);
      filterMap.put("sortOrder", sortOrder);
      JsonObject jsonFilterObject = JsonUtil.toJsonObject(filterMap);
      return jsonFilterObject.toString();
   }
}
