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
package org.apache.activemq.artemis.tests.integration.management;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ManagementHelperTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testArrayOfStringParameter() throws Exception {
      String resource = RandomUtil.randomUUIDString();
      String operationName = RandomUtil.randomUUIDString();
      String param = RandomUtil.randomUUIDString();
      String[] params = new String[]{RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString()};
      ClientMessage msg = new ClientMessageImpl((byte) 0, false, 0, 0, (byte) 4, 1000);
      ManagementHelper.putOperationInvocation(msg, resource, operationName, param, params);

      Object[] parameters = ManagementHelper.retrieveOperationParameters(msg);
      assertEquals(2, parameters.length);
      assertEquals(param, parameters[0]);
      Object parameter_2 = parameters[1];
      logger.debug("type {}", parameter_2);
      assertInstanceOf(Object[].class, parameter_2);
      Object[] retrievedParams = (Object[]) parameter_2;
      assertEquals(params.length, retrievedParams.length);
      for (int i = 0; i < retrievedParams.length; i++) {
         assertEquals(params[i], retrievedParams[i]);
      }
   }

   @Test
   public void testParams() throws Exception {
      String resource = RandomUtil.randomUUIDString();
      String operationName = RandomUtil.randomUUIDString();

      long i = RandomUtil.randomInt();
      String s = RandomUtil.randomUUIDString();
      double d = RandomUtil.randomDouble();
      boolean b = RandomUtil.randomBoolean();
      long l = RandomUtil.randomLong();
      Map<String, Object> map = new HashMap<>();
      String key1 = RandomUtil.randomUUIDString();
      int value1 = RandomUtil.randomInt();
      String key2 = RandomUtil.randomUUIDString();
      double value2 = RandomUtil.randomDouble();
      String key3 = RandomUtil.randomUUIDString();
      String value3 = RandomUtil.randomUUIDString();
      String key4 = RandomUtil.randomUUIDString();
      boolean value4 = RandomUtil.randomBoolean();
      String key5 = RandomUtil.randomUUIDString();
      long value5 = RandomUtil.randomLong();
      map.put(key1, value1);
      map.put(key2, value2);
      map.put(key3, value3);
      map.put(key4, value4);
      map.put(key5, value5);

      Map<String, Object> map2 = new HashMap<>();
      String key2_1 = RandomUtil.randomUUIDString();
      int value2_1 = RandomUtil.randomInt();
      String key2_2 = RandomUtil.randomUUIDString();
      double value2_2 = RandomUtil.randomDouble();
      String key2_3 = RandomUtil.randomUUIDString();
      String value2_3 = RandomUtil.randomUUIDString();
      String key2_4 = RandomUtil.randomUUIDString();
      boolean value2_4 = RandomUtil.randomBoolean();
      String key2_5 = RandomUtil.randomUUIDString();
      long value2_5 = RandomUtil.randomLong();
      map2.put(key2_1, value2_1);
      map2.put(key2_2, value2_2);
      map2.put(key2_3, value2_3);
      map2.put(key2_4, value2_4);
      map2.put(key2_5, value2_5);

      Map<String, Object> map3 = new HashMap<>();
      String key3_1 = RandomUtil.randomUUIDString();
      int value3_1 = RandomUtil.randomInt();
      String key3_2 = RandomUtil.randomUUIDString();
      double value3_2 = RandomUtil.randomDouble();
      String key3_3 = RandomUtil.randomUUIDString();
      String value3_3 = RandomUtil.randomUUIDString();
      String key3_4 = RandomUtil.randomUUIDString();
      boolean value3_4 = RandomUtil.randomBoolean();
      String key3_5 = RandomUtil.randomUUIDString();
      long value3_5 = RandomUtil.randomLong();
      map3.put(key3_1, value3_1);
      map3.put(key3_2, value3_2);
      map3.put(key3_3, value3_3);
      map3.put(key3_4, value3_4);
      map3.put(key3_5, value3_5);

      Map[] maps = new Map[]{map2, map3};

      String strElem0 = RandomUtil.randomUUIDString();
      String strElem1 = RandomUtil.randomUUIDString();
      String strElem2 = RandomUtil.randomUUIDString();

      String[] strArray = new String[]{strElem0, strElem1, strElem2};

      Object[] params = new Object[]{i, s, d, b, l, map, strArray, maps};

      ClientMessageImpl msg = new ClientMessageImpl((byte) 0, false, 0, 0, (byte) 4, 1000);
      ManagementHelper.putOperationInvocation(msg, resource, operationName, params);

      Object[] parameters = ManagementHelper.retrieveOperationParameters(msg);

      assertEquals(params.length, parameters.length);

      assertEquals(i, parameters[0]);
      assertEquals(s, parameters[1]);
      assertEquals(d, parameters[2]);
      assertEquals(b, parameters[3]);
      assertEquals(l, parameters[4]);
      Map mapRes = (Map) parameters[5];
      assertEquals(map.size(), mapRes.size());
      assertEquals((long) value1, mapRes.get(key1));
      assertEquals(value2, mapRes.get(key2));
      assertEquals(value3, mapRes.get(key3));
      assertEquals(value4, mapRes.get(key4));
      assertEquals(value5, mapRes.get(key5));

      Object[] strArr2 = (Object[]) parameters[6];
      assertEquals(strArray.length, strArr2.length);
      assertEquals(strElem0, strArr2[0]);
      assertEquals(strElem1, strArr2[1]);
      assertEquals(strElem2, strArr2[2]);

      Object[] mapArray = (Object[]) parameters[7];
      assertEquals(2, mapArray.length);
      Map mapRes2 = (Map) mapArray[0];
      assertEquals(map2.size(), mapRes2.size());
      assertEquals((long) value2_1, mapRes2.get(key2_1));
      assertEquals(value2_2, mapRes2.get(key2_2));
      assertEquals(value2_3, mapRes2.get(key2_3));
      assertEquals(value2_4, mapRes2.get(key2_4));
      assertEquals(value2_5, mapRes2.get(key2_5));

      Map mapRes3 = (Map) mapArray[1];
      assertEquals(map3.size(), mapRes3.size());
      assertEquals((long) value3_1, mapRes3.get(key3_1));
      assertEquals(value3_2, mapRes3.get(key3_2));
      assertEquals(value3_3, mapRes3.get(key3_3));
      assertEquals(value3_4, mapRes3.get(key3_4));
      assertEquals(value3_5, mapRes3.get(key3_5));
   }

   @Test
   public void testMapWithArrayValues() throws Exception {
      String resource = RandomUtil.randomUUIDString();
      String operationName = RandomUtil.randomUUIDString();

      Map<String, Object> map = new HashMap<>();
      String key1 = RandomUtil.randomUUIDString();
      String[] val1 = new String[]{"a", "b", "c"};

      if (logger.isDebugEnabled()) {
         logger.debug("val1 type is {}", Arrays.toString(val1));
      }

      String key2 = RandomUtil.randomUUIDString();
      Long[] val2 = new Long[]{1L, 2L, 3L, 4L, 5L};

      if (logger.isDebugEnabled()) {
         logger.debug("val2 type is {}", Arrays.toString(val2));
      }

      map.put(key1, val1);
      map.put(key2, val2);

      Object[] params = new Object[]{"hello", map};

      ClientMessageImpl msg = new ClientMessageImpl((byte) 0, false, 0, 0, (byte) 4, 1000);
      ManagementHelper.putOperationInvocation(msg, resource, operationName, params);

      Object[] parameters = ManagementHelper.retrieveOperationParameters(msg);

      assertEquals(params.length, parameters.length);

      assertEquals("hello", parameters[0]);

      Map map2 = (Map) parameters[1];
      assertEquals(2, map2.size());

      Object[] arr1 = (Object[]) map2.get(key1);
      assertEquals(val1.length, arr1.length);
      assertEquals(arr1[0], val1[0]);
      assertEquals(arr1[1], val1[1]);
      assertEquals(arr1[2], val1[2]);

      Object[] arr2 = (Object[]) map2.get(key2);
      assertEquals(val2.length, arr2.length);
      assertEquals(arr2[0], val2[0]);
      assertEquals(arr2[1], val2[1]);
      assertEquals(arr2[2], val2[2]);

   }

}
