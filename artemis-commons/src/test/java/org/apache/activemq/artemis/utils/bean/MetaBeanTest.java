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

package org.apache.activemq.artemis.utils.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetaBeanTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testToJson() throws Exception {

      MYClass sourceObject = new MYClass();
      sourceObject.setA(RandomUtil.randomUUIDString());
      sourceObject.setB(RandomUtil.randomInt());
      sourceObject.setC(RandomUtil.randomInt());
      sourceObject.setD(null);
      sourceObject.setIdCacheSize(333);
      sourceObject.setSimpleString(SimpleString.of("mySimpleString"));
      sourceObject.setFloatValue(33.33f);
      sourceObject.setDoubleValue(11.11);
      sourceObject.setBoolValue(true);
      sourceObject.setMyEnum(MyEnum.TWO);


      JsonObject jsonObject = MYClass.metaBean.toJSON(sourceObject, false);
      assertFalse(jsonObject.containsKey("gated"));

      logger.debug("Result::" + jsonObject.toString());

      MYClass result = new MYClass();
      MYClass.metaBean.fromJSON(result, jsonObject.toString());
      assertEquals(sourceObject, result);


      assertNull(result.getD());
      assertNotNull(result.getIdCacheSize());
      assertEquals(333, result.getIdCacheSize().intValue());
      assertEquals(33.33f, result.getFloatValue().floatValue(), 0);
      assertEquals(11.11, result.getDoubleValue().doubleValue(), 0);
      assertEquals(MyEnum.TWO, result.getMyEnum());
      assertTrue(result.getBoolValue());

      sourceObject.setGated(SimpleString.of("gated-now-has-value"));
      jsonObject = MYClass.metaBean.toJSON(sourceObject, false);
      assertTrue(jsonObject.containsKey("gated"));
      assertEquals("gated-now-has-value", jsonObject.getString("gated"));
   }

   @Test
   public void testRandom() throws Exception {
      MYClass randomObject = new MYClass();
      MYClass.metaBean.setRandom(randomObject);
      String json = MYClass.metaBean.toJSON(randomObject, false).toString();
      MYClass target = new MYClass();
      MYClass.metaBean.fromJSON(target, json);
      assertEquals(randomObject, target);
      MYClass copy = new MYClass();
      MYClass.metaBean.copy(randomObject, copy);
      assertEquals(randomObject, copy);
   }

   public enum MyEnum {
      ONE, TWO, TRHEE
   }

   public static class MYClass {

      public static MetaBean<MYClass> metaBean = new MetaBean<>();

      static {
         metaBean.add(String.class, "a", (theInstance, parameter) -> theInstance.a = parameter, theInstance -> theInstance.a);
      }
      String a;

      static {
         metaBean.add(Integer.class, "b", (theInstance, parameter) -> theInstance.b = parameter, theInstance -> theInstance.b);
      }
      int b;

      static {
         metaBean.add(Integer.class, "c", (theInstance, parameter) -> theInstance.c = parameter, theInstance -> theInstance.c);
      }
      Integer c;

      static {
         metaBean.add(String.class, "d", (theInstance, parameter) -> theInstance.d = parameter, theInstance -> theInstance.d);
      }
      String d = "defaultString";

      static {
         metaBean.add(Integer.class, "IdCacheSize", (obj, value) -> obj.setIdCacheSize(value), obj -> obj.getIdCacheSize());
      }
      Integer idCacheSize;

      static {
         metaBean.add(SimpleString.class, "simpleString", (obj, value) -> obj.setSimpleString(value), obj -> obj.getSimpleString());
      }
      SimpleString simpleString;

      static {
         metaBean.add(SimpleString.class, "gated", (obj, value) -> obj.setGated((SimpleString) value), obj -> obj.getGated(), obj -> obj.gated != null);
      }
      SimpleString gated;

      static {
         metaBean.add(Long.class, "longValue", (obj, value) -> obj.setLongValue(value), obj -> obj.getLongValue());
      }
      Long longValue;
      static {
         metaBean.add(Double.class, "doubleValue", (obj, value) -> obj.setDoubleValue(value), obj -> obj.getDoubleValue());
      }
      Double doubleValue;

      static {
         metaBean.add(Float.class, "floatValue", (obj, value) -> obj.setFloatValue(value), obj -> obj.getFloatValue());
      }
      Float floatValue;

      static {
         metaBean.add(Boolean.class, "boolValue", (obj, value) -> obj.boolValue = value, obj -> obj.boolValue);
      }
      boolean boolValue;

      static {
         metaBean.add(MyEnum.class, "myEnum", (o, v) -> o.myEnum = v, o -> o.myEnum);
      }
      MyEnum myEnum;

      public MyEnum getMyEnum() {
         return myEnum;
      }

      public MYClass setMyEnum(MyEnum myEnum) {
         this.myEnum = myEnum;
         return this;
      }

      public String getA() {
         return a;
      }

      public MYClass setA(String a) {
         this.a = a;
         return this;
      }

      public int getB() {
         return b;
      }

      public MYClass setB(int b) {
         this.b = b;
         return this;
      }

      public Integer getC() {
         return c;
      }

      public MYClass setC(Integer c) {
         this.c = c;
         return this;
      }

      public String getD() {
         return d;
      }

      public MYClass setD(String d) {
         this.d = d;
         return this;
      }

      public Long getLongValue() {
         return longValue;
      }

      public MYClass setLongValue(Long longValue) {
         this.longValue = longValue;
         return this;
      }

      public Double getDoubleValue() {
         return doubleValue;
      }

      public MYClass setDoubleValue(Double doubleValue) {
         this.doubleValue = doubleValue;
         return this;
      }

      public Float getFloatValue() {
         return floatValue;
      }

      public MYClass setFloatValue(Float floatValue) {
         this.floatValue = floatValue;
         return this;
      }

      public Integer getIdCacheSize() {
         return idCacheSize;
      }

      public MYClass setIdCacheSize(Integer idCacheSize) {
         this.idCacheSize = idCacheSize;
         return this;
      }

      public SimpleString getSimpleString() {
         return simpleString;
      }

      public MYClass setSimpleString(SimpleString simpleString) {
         this.simpleString = simpleString;
         return this;
      }

      public SimpleString getGated() {
         return gated;
      }

      public MYClass setGated(SimpleString gated) {
         this.gated = gated;
         return this;
      }

      public boolean getBoolValue() {
         return boolValue;
      }

      public MYClass setBoolValue(boolean boolValue) {
         this.boolValue = boolValue;
         return this;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof MYClass other)) {
            return false;
         }

         return Objects.equals(a, other.a) &&
                b == other.b &&
                boolValue == other.boolValue &&
                Objects.equals(c, other.c) &&
                Objects.equals(d, other.d) &&
                Objects.equals(idCacheSize, other.idCacheSize) &&
                Objects.equals(simpleString, other.simpleString) &&
                Objects.equals(gated, other.gated) &&
                Objects.equals(longValue, other.longValue) &&
                Objects.equals(doubleValue, other.doubleValue) &&
                Objects.equals(floatValue, other.floatValue) &&
                myEnum == other.myEnum;
      }

      @Override
      public int hashCode() {
         return Objects.hash(a, b, c, d, idCacheSize, simpleString, gated, longValue, doubleValue, floatValue,
                             boolValue, myEnum);
      }

      @Override
      public String toString() {
         return "MYClass{" + "a='" + a + '\'' + ", b=" + b + ", c=" + c + ", d='" + d + '\'' + ", idCacheSize=" + idCacheSize + ", simpleString=" + simpleString + ", gated=" + gated + ", longValue=" + longValue + ", doubleValue=" + doubleValue + ", floatValue=" + floatValue + ", boolValue=" + boolValue + ", myEnum=" + myEnum + '}';
      }

   }

}
