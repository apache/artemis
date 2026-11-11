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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.URIFactory;
import org.apache.activemq.artemis.utils.uri.URISchema;
import org.apache.activemq.artemis.utils.uri.URISupport;
import org.junit.jupiter.api.Test;

public class URIParserTest {

   /**
    * this is just a simple test to validate the model
    */
   @Test
   public void testSchemaFruit() throws Throwable {
      FruitParser parser = new FruitParser();
      Fruit fruit = (Fruit) parser.newObject(new URI("fruit://some:guy@fair-market:3030?color=green&fluentName=something"), null);

      assertEquals("fruit", fruit.getName());
      assertEquals(3030, fruit.getPort());
      assertEquals("fair-market", fruit.getHost());
      assertEquals("some:guy", fruit.getUserInfo());
      assertEquals("green", fruit.getColor());
      assertEquals("something", fruit.getFluentName());

   }

   /**
    * this is just a simple test to validate the model
    */
   @Test
   public void testGenerateWithEncoding() throws Throwable {
      FruitParser parser = new FruitParser();
      Fruit myFruit = new Fruit("tomato&fruit");
      myFruit.setHost("somehost&uui");
      // I'm trying to break things as you can see here with some weird encoding
      myFruit.setFluentName("apples&bananas with &host=3344");
      URI uri = parser.createSchema("fruit", myFruit);

      Fruit newFruit = (Fruit) parser.newObject(uri, "something");

      assertEquals(myFruit.getHost(), newFruit.getHost());
      assertEquals(myFruit.getFluentName(), newFruit.getFluentName());

   }

   /**
    * Even thought there's no host Property on FruitBase.. this should still work fine without throwing any exceptions
    */
   @Test
   public void testSchemaNoHosProperty() throws Throwable {
      FruitParser parser = new FruitParser();
      FruitBase fruit = parser.newObject(new URI("base://some:guy@fair-market:3030?color=green&fluentName=something"), null);
      assertEquals("base", fruit.getName());
      assertEquals("green", fruit.getColor());
      assertEquals("something", fruit.getFluentName());
   }

   /**
    * Even thought there's no host Property on FruitBase.. this should still work fine without throwing any exceptions
    */
   @Test
   public void testSchemaNoHostOnURL() throws Throwable {
      FruitParser parser = new FruitParser();
      Fruit fruit = (Fruit) parser.newObject(new URI("fruit://some:guy@port?color=green&fluentName=something"), null);

      assertEquals("fruit", fruit.getName());
      assertEquals("green", fruit.getColor());
      assertEquals("something", fruit.getFluentName());
   }

   @Test
   public void testQueryConversion() throws Exception {
      Map<String, String> query = new HashMap<>();
      String queryString = URISupport.createQueryString(query);
      assertTrue(queryString.isEmpty());

      query.put("key1", "value1");
      queryString = URISupport.createQueryString(query);
      assertEquals("key1=value1", queryString);

      query.put("key3", "value3");
      queryString = URISupport.createQueryString(query);
      assertEquals("key1=value1&key3=value3", queryString);

      query.put("key2", "value2");
      queryString = URISupport.createQueryString(query);
      assertEquals("key1=value1&key2=value2&key3=value3", queryString);

   }

   class FruitParser extends URIFactory<FruitBase, String> {

      FruitParser() {
         this.registerSchema(new FruitSchema());
         this.registerSchema(new FruitBaseSchema());
      }
   }

   class FruitSchema extends URISchema<FruitBase, String> {

      @Override
      public String getSchemaName() {
         return "fruit";
      }

      @Override
      public FruitBase internalNewObject(URI uri, Map<String, String> query, String fruitName) throws Exception {
         return BeanSupport.setData(uri, new Fruit(getSchemaName()), query);
      }

   }

   class FruitBaseSchema extends URISchema<FruitBase, String> {

      @Override
      public String getSchemaName() {
         return "base";
      }

      @Override
      public FruitBase internalNewObject(URI uri, Map<String, String> query, String fruitName) throws Exception {
         return BeanSupport.setData(uri, new FruitBase(getSchemaName()), query);
      }
   }

   public static class FruitBase {

      final String name;
      String fluentName;
      String color;

      FruitBase(final String name) {
         this.name = name;
      }

      public String getName() {
         return name;
      }

      public String getColor() {
         return color;
      }

      public void setColor(String color) {
         this.color = color;
      }

      public String getFluentName() {
         return fluentName;
      }

      public FruitBase setFluentName(String name) {
         this.fluentName = name;

         return this;
      }

      @Override
      public String toString() {
         return "FruitBase{" +
            "name='" + name + '\'' +
            ", fluentName='" + fluentName + '\'' +
            ", color='" + color + '\'' +
            '}';
      }
   }

   public static class Fruit extends FruitBase {

      public Fruit(String name) {
         super(name);
      }

      String host;
      int port;
      String userInfo;

      public void setHost(String host) {
         this.host = host;
      }

      public String getHost() {
         return host;
      }

      public void setPort(int port) {
         this.port = port;
      }

      public int getPort() {
         return port;
      }

      public void setUserInfo(String userInfo) {
         this.userInfo = userInfo;
      }

      public String getUserInfo() {
         return userInfo;
      }

      @Override
      public String toString() {
         return "Fruit{" +
            "host='" + host + '\'' +
            ", port=" + port +
            ", userInfo='" + userInfo + '\'' +
            "super=" + super.toString() + '}';
      }
   }
}
