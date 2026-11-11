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
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class URIFactory<T, P> {

   private URI defaultURI;

   private final Map<String, URISchema<T, P>> schemas = new ConcurrentHashMap<>();

   public URI getDefaultURI() {
      return defaultURI;
   }

   public void setDefaultURI(URI uri) {
      this.defaultURI = uri;
   }

   public void registerSchema(URISchema<T, P> schemaFactory) {
      schemas.put(schemaFactory.getSchemaName(), schemaFactory);
      schemaFactory.setFactory(this);
   }

   public void removeSchema(final String schemaName) {
      schemas.remove(schemaName);
   }

   public URI expandURI(String uriString) throws Exception {
      return normalise(uriString);
   }

   public T newObject(URI uri, P param) throws Exception {
      return Objects.requireNonNull(schemas.get(uri.getScheme()), "Schema " + uri.getScheme() + " not found").newObject(uri, param);
   }

   public T newObject(URI uri, Map<String, String> overrides, P param) throws Exception {
      return Objects.requireNonNull(schemas.get(uri.getScheme()), "Schema " + uri.getScheme() + " not found").newObject(uri, overrides, param);
   }

   public T newObject(String uri, P param) throws Exception {
      return newObject(new URI(uri), param);
   }

   public void populateObject(URI uri, T bean) throws Exception {
      Objects.requireNonNull(schemas.get(uri.getScheme()), "Schema " + uri.getScheme() + " not found").populateObject(uri, bean);
   }

   public void populateObject(String uri, T bean) throws Exception {
      populateObject(new URI(uri), bean);
   }

   public URI createSchema(String scheme, T bean) throws Exception {
      return Objects.requireNonNull(schemas.get(scheme), "Schema " + scheme + " not found").newURI(bean);
   }

   /**
    * this method is used to change a string with multiple URI's in it into a valid URI. for instance it is possible to
    * have the following String {@literal (tcp://localhost:61616,tcp://localhost:5545,tcp://localhost:5555)?somequery}
    * This is an invalid URI so will be changed so that the first URI is used and the extra ones added as part of the
    * URI fragment, like so {@literal tcp://localhost:61616?someQuery#tcp://localhost:5545,tcp://localhost:5555}.
    * <p>
    * It is the job of the URISchema implementation to handle these fragments as needed.
    */
   private URI normalise(String uri) throws URISyntaxException {
      if (uri.startsWith("(")) {
         String[] split = uri.split("\\)", 2);
         String[] connectorURIS = split[0].substring(split[0].indexOf('(') + 1).split(",");
         String factoryQuery = split.length > 1 ? split[1] : "";
         StringBuilder builder = new StringBuilder(connectorURIS[0]);
         if (factoryQuery != null && !factoryQuery.isEmpty()) {
            if (connectorURIS[0].contains("?")) {
               builder.append("&").append(factoryQuery.substring(1));
            } else {
               builder.append(factoryQuery);
            }
         }
         if (connectorURIS.length > 1) {
            builder.append("#");
            for (int i = 1; i < connectorURIS.length; i++) {
               if (i > 1) {
                  builder.append(",");
               }
               builder.append(connectorURIS[i]);
            }
         }
         return new URI(builder.toString().replace(";", "&"));
      }
      return new URI(uri.replace(";", "&"));
   }
}
