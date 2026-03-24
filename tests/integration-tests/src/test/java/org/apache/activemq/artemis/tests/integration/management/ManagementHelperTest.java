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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

public class ManagementHelperTest extends Assert {


   private static final Logger log = Logger.getLogger(ManagementHelperTest.class);


   @Test
   public void testArrayOfStringParameter() throws Exception {
      String resource = RandomUtil.randomString();
      String operationName = RandomUtil.randomString();
      String param = RandomUtil.randomString();
      String[] params = new String[]{RandomUtil.randomString(), RandomUtil.randomString(), RandomUtil.randomString()};
      ClientMessage msg = new ClientMessageImpl((byte) 0, false, 0, 0, (byte) 4, 1000);
      ManagementHelper.putOperationInvocation(msg, resource, operationName, param, params);

      Object[] parameters = ManagementHelper.retrieveOperationParameters(msg);
      Assert.assertEquals(2, parameters.length);
      Assert.assertEquals(param, parameters[0]);
      Object parameter_2 = parameters[1];
      log.debug("type " + parameter_2);
      Assert.assertTrue(parameter_2 instanceof Object[]);
      Object[] retrievedParams = (Object[]) parameter_2;
      Assert.assertEquals(params.length, retrievedParams.length);
      for (int i = 0; i < retrievedParams.length; i++) {
         Assert.assertEquals(params[i], retrievedParams[i]);
      }
   }

   @Test
   public void testParams() throws Exception {
      String resource = RandomUtil.randomString();
      String operationName = RandomUtil.randomString();

      long i = RandomUtil.randomInt();
      String s = RandomUtil.randomString();
      double d = RandomUtil.randomDouble();
      boolean b = RandomUtil.randomBoolean();
      long l = RandomUtil.randomLong();
      Map<String, Object> map = new HashMap<>();
      String key1 = RandomUtil.randomString();
      int value1 = RandomUtil.randomInt();
      String key2 = RandomUtil.randomString();
      double value2 = RandomUtil.randomDouble();
      String key3 = RandomUtil.randomString();
      String value3 = RandomUtil.randomString();
      String key4 = RandomUtil.randomString();
      boolean value4 = RandomUtil.randomBoolean();
      String key5 = RandomUtil.randomString();
      long value5 = RandomUtil.randomLong();
      map.put(key1, value1);
      map.put(key2, value2);
      map.put(key3, value3);
      map.put(key4, value4);
      map.put(key5, value5);

      Map<String, Object> map2 = new HashMap<>();
      String key2_1 = RandomUtil.randomString();
      int value2_1 = RandomUtil.randomInt();
      String key2_2 = RandomUtil.randomString();
      double value2_2 = RandomUtil.randomDouble();
      String key2_3 = RandomUtil.randomString();
      String value2_3 = RandomUtil.randomString();
      String key2_4 = RandomUtil.randomString();
      boolean value2_4 = RandomUtil.randomBoolean();
      String key2_5 = RandomUtil.randomString();
      long value2_5 = RandomUtil.randomLong();
      map2.put(key2_1, value2_1);
      map2.put(key2_2, value2_2);
      map2.put(key2_3, value2_3);
      map2.put(key2_4, value2_4);
      map2.put(key2_5, value2_5);

      Map<String, Object> map3 = new HashMap<>();
      String key3_1 = RandomUtil.randomString();
      int value3_1 = RandomUtil.randomInt();
      String key3_2 = RandomUtil.randomString();
      double value3_2 = RandomUtil.randomDouble();
      String key3_3 = RandomUtil.randomString();
      String value3_3 = RandomUtil.randomString();
      String key3_4 = RandomUtil.randomString();
      boolean value3_4 = RandomUtil.randomBoolean();
      String key3_5 = RandomUtil.randomString();
      long value3_5 = RandomUtil.randomLong();
      map3.put(key3_1, value3_1);
      map3.put(key3_2, value3_2);
      map3.put(key3_3, value3_3);
      map3.put(key3_4, value3_4);
      map3.put(key3_5, value3_5);

      Map[] maps = new Map[]{map2, map3};

      String strElem0 = RandomUtil.randomString();
      String strElem1 = RandomUtil.randomString();
      String strElem2 = RandomUtil.randomString();

      String[] strArray = new String[]{strElem0, strElem1, strElem2};

      Object[] params = new Object[]{i, s, d, b, l, map, strArray, maps};

      ClientMessageImpl msg = new ClientMessageImpl((byte) 0, false, 0, 0, (byte) 4, 1000);
      ManagementHelper.putOperationInvocation(msg, resource, operationName, params);

      Object[] parameters = ManagementHelper.retrieveOperationParameters(msg);

      Assert.assertEquals(params.length, parameters.length);

      Assert.assertEquals(i, parameters[0]);
      Assert.assertEquals(s, parameters[1]);
      Assert.assertEquals(d, parameters[2]);
      Assert.assertEquals(b, parameters[3]);
      Assert.assertEquals(l, parameters[4]);
      Map mapRes = (Map) parameters[5];
      Assert.assertEquals(map.size(), mapRes.size());
      Assert.assertEquals((long) value1, mapRes.get(key1));
      Assert.assertEquals(value2, mapRes.get(key2));
      Assert.assertEquals(value3, mapRes.get(key3));
      Assert.assertEquals(value4, mapRes.get(key4));
      Assert.assertEquals(value5, mapRes.get(key5));

      Object[] strArr2 = (Object[]) parameters[6];
      Assert.assertEquals(strArray.length, strArr2.length);
      Assert.assertEquals(strElem0, strArr2[0]);
      Assert.assertEquals(strElem1, strArr2[1]);
      Assert.assertEquals(strElem2, strArr2[2]);

      Object[] mapArray = (Object[]) parameters[7];
      Assert.assertEquals(2, mapArray.length);
      Map mapRes2 = (Map) mapArray[0];
      Assert.assertEquals(map2.size(), mapRes2.size());
      Assert.assertEquals((long) value2_1, mapRes2.get(key2_1));
      Assert.assertEquals(value2_2, mapRes2.get(key2_2));
      Assert.assertEquals(value2_3, mapRes2.get(key2_3));
      Assert.assertEquals(value2_4, mapRes2.get(key2_4));
      Assert.assertEquals(value2_5, mapRes2.get(key2_5));

      Map mapRes3 = (Map) mapArray[1];
      Assert.assertEquals(map3.size(), mapRes3.size());
      Assert.assertEquals((long) value3_1, mapRes3.get(key3_1));
      Assert.assertEquals(value3_2, mapRes3.get(key3_2));
      Assert.assertEquals(value3_3, mapRes3.get(key3_3));
      Assert.assertEquals(value3_4, mapRes3.get(key3_4));
      Assert.assertEquals(value3_5, mapRes3.get(key3_5));
   }

   @Test
   public void testMapWithArrayValues() throws Exception {
      String resource = RandomUtil.randomString();
      String operationName = RandomUtil.randomString();

      Map<String, Object> map = new HashMap<>();
      String key1 = RandomUtil.randomString();
      String[] val1 = new String[]{"a", "b", "c"};

      log.debug("val1 type is " + Arrays.toString(val1));

      String key2 = RandomUtil.randomString();
      Long[] val2 = new Long[]{1L, 2L, 3L, 4L, 5L};

      log.debug("val2 type is " + Arrays.toString(val2));

      map.put(key1, val1);
      map.put(key2, val2);

      Object[] params = new Object[]{"hello", map};

      ClientMessageImpl msg = new ClientMessageImpl((byte) 0, false, 0, 0, (byte) 4, 1000);
      ManagementHelper.putOperationInvocation(msg, resource, operationName, params);

      Object[] parameters = ManagementHelper.retrieveOperationParameters(msg);

      Assert.assertEquals(params.length, parameters.length);

      Assert.assertEquals("hello", parameters[0]);

      Map map2 = (Map) parameters[1];
      Assert.assertEquals(2, map2.size());

      Object[] arr1 = (Object[]) map2.get(key1);
      Assert.assertEquals(val1.length, arr1.length);
      Assert.assertEquals(arr1[0], val1[0]);
      Assert.assertEquals(arr1[1], val1[1]);
      Assert.assertEquals(arr1[2], val1[2]);

      Object[] arr2 = (Object[]) map2.get(key2);
      Assert.assertEquals(val2.length, arr2.length);
      Assert.assertEquals(arr2[0], val2[0]);
      Assert.assertEquals(arr2[1], val2[1]);
      Assert.assertEquals(arr2[2], val2[2]);

   }

}
