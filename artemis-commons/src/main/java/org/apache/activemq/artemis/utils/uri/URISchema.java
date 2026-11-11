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
package org.apache.activemq.artemis.utils.uri;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public abstract class URISchema<T, P> {

   public abstract String getSchemaName();

   public T newObject(URI uri, P param) throws Exception {
      return newObject(uri, null, param);
   }

   public void populateObject(URI uri, T bean) throws Exception {
      internalPopulateObject(uri, parseQuery(uri.getQuery(), null), bean);
   }

   public URI newURI(T bean) throws Exception {
      return internalNewURI(bean);
   }

   private URIFactory<T, P> parentFactory;

   void setFactory(URIFactory<T, P> factory) {
      this.parentFactory = factory;
   }

   protected URIFactory<T, P> getFactory() {
      return parentFactory;
   }

   protected String getHost(URI uri) {
      URI defaultFactory = getDefaultURI();
      if (defaultFactory != null && uri.getHost() == null && defaultFactory.getScheme() != null && defaultFactory.getScheme().equals(uri.getScheme())) {
         uri = defaultFactory;
      }
      return uri.getHost();
   }

   protected URI getDefaultURI() {
      URIFactory<T, P> factory = getFactory();
      if (factory == null) {
         return null;
      } else {
         return factory.getDefaultURI();
      }
   }

   protected int getPort(URI uri) {
      URI defaultFactory = getDefaultURI();
      if (defaultFactory != null && uri.getPort() < 0 && defaultFactory.getScheme().equals(uri.getScheme())) {
         uri = defaultFactory;
      }
      return uri.getPort();
   }

   /**
    * It will create a new Object for the URI selected schema. the propertyOverrides is used to replace whatever was
    * defined on the URL string
    *
    * @param uri               The URI
    * @param propertyOverrides used to replace whatever was defined on the URL string
    * @return new Object
    * @throws Exception On error
    */
   public T newObject(URI uri, Map<String, String> propertyOverrides, P param) throws Exception {
      return internalNewObject(uri, parseQuery(uri.getQuery(), propertyOverrides), param);
   }

   protected abstract T internalNewObject(URI uri, Map<String, String> query, P param) throws Exception;

   /**
    * This is the default implementation. Sub classes are should provide a proper implementation for their schemas.
    */
   protected URI internalNewURI(T bean) throws Exception {
      String query = BeanSupport.getData(null, bean);

      return new URI(getSchemaName(), null, "//", query, null);

   }

   protected void internalPopulateObject(URI uri, Map<String, String> query, T bean) throws Exception {
      BeanSupport.setData(uri, bean, query);
   }

   public static Map<String, String> parseQuery(String uri,
                                                Map<String, String> propertyOverrides) {
      Map<String, String> rc = new HashMap<>();
      if (uri != null && !uri.isEmpty()) {
         String[] parameters = uri.split("&");
         for (String parameter : parameters) {
            int p = parameter.indexOf("=");
            if (p >= 0) {
               String name = BeanSupport.decodeURI(parameter.substring(0, p));
               String value = BeanSupport.decodeURI(parameter.substring(p + 1));
               rc.put(name, value);
            } else {
               if (!parameter.trim().isEmpty()) {
                  rc.put(parameter, null);
               }
            }
         }
      }

      if (propertyOverrides != null) {
         for (Map.Entry<String, String> entry : propertyOverrides.entrySet()) {
            rc.put(entry.getKey(), entry.getValue());
         }
      }
      return rc;
   }

   protected String printQuery(Map<String, String> query) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> entry : query.entrySet()) {
         sb.append(entry.getKey() + "=" + entry.getValue());
         sb.append("\n");
      }

      return sb.toString();
   }
}
